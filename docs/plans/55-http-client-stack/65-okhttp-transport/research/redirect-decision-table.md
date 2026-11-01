# Redirect decision table: when the transport follows, returns or fails on a `3xx`, and what a followed hop carries

## 1. Question

For `OkHttpTransport` in `webtools-http` (OkHttp `okhttp-jvm` 5.5.0, OkHttp's own redirect following switched off, one `Call` per hop
under one shared deadline): **hop by hop, when does the transport follow a `3xx`, return it as the response, or fail, and what does a
followed hop carry?**

An answer has to meet these requirements, which are facts about the work:

- Only `GET` and `HEAD` are followed; a `3xx` to any other method is the response.
- `UrlGuard.checkRequest` judges hop 1. For every later hop the transport resolves `Location` with `HttpUrl.resolve` (catching whatever
  it throws) and passes the printed result to `UrlGuard.admitRedirect(target, previous)`, which parses with `Url.parse`. A refusal is
  `TransportFailure.RefusedByPolicy(reason)`, with zero sockets opened for the target.
- `https` to `http` is followed only when `TransportSettings.allowHttpsToHttp` is true (the policy judges it: `InsecureRedirect`).
- Every `HttpHeaders.Entry` with `isSensitive` is removed on a hop whose canonical scheme, host or effective port differs from the hop
  that returned the redirect.
- `TransportSettings.maxRedirects` is the most redirects followed; a followable redirect beyond it is `TooManyRedirects(maxRedirects)`;
  0 follows none.
- A request carrying a transport-controlled header is refused before hop 1, so a hop never carries one.
- Nothing the server sends may open a socket to a target the policy has not admitted, and no exception from resolving a `Location`
  escapes the loop.

## 2. Bottom line

- **Follow exactly `301`, `302`, `303`, `307`, `308`, for `GET` and `HEAD`.** `300` (with or without `Location`), `304`, `305`, `306` and
  every other `3xx` are returned as the response. This is the Fetch standard's "redirect status" list and the set the JDK client, Go and
  Ktor follow; OkHttp and http4k also follow `300`, which the transport does not.
- **A `3xx` that names no single usable target is the response; one that names a target the policy cannot admit is a refusal.** Absent,
  empty, whitespace-only and several differing `Location` fields are returned. A `Location` that `HttpUrl.resolve` rejects (null or
  exception), that has a scheme other than `http`/`https`, that `Url.parse` refuses or that the policy refuses is
  `RefusedByPolicy(reason)`: `Scheme` for a foreign scheme, otherwise the reason `admitRedirect` maps, and `HostForm` for an
  unresolvable one. Every branch opens zero sockets to the target.
- **Check order (first match wins):** method and status (not followable: return) -> no single `Location` (return) -> cap
  (`TooManyRedirects`) -> resolve and admit (`RefusedByPolicy`) -> shared deadline (`Timeout`) -> follow. The cap comes before admission so
  that `maxRedirects = 0` means "no redirects" whatever the `Location` says; the deadline comes last so every deterministic outcome is
  independent of the clock.
- **A followed hop carries the original request's headers minus the sensitive ones removed so far.** Conditional headers, `Range` and
  `Accept` go along (every compared client keeps them); removal is permanent once made; the method is unchanged (`HEAD` stays `HEAD`
  after a `303`); no body, no `Referer`. The next request is built from the **admitted `Url`'s text**, so what is judged is what is
  connected.
- **The result:** `HttpResponse.url` is the last requested URL; `redirectChain` lists `RedirectHop(requestedUrl, status)` for each followed
  hop; a `Retry-After` on a `3xx` is not honoured (no client in the comparison honours it); the deadline is `callTimeout` measured from
  the start of the fetch, passed to each `Call`, and the transport itself fails with `Timeout` when less than 1 ms remains (Okio treats
  a timeout of 0 as "no timeout").
- **Shape:** a pure `internal` function in `jvmMain` beside the loop returning a small sealed outcome (`Deliver`, `TooMany`, `Refused`,
  `Follow`), with `HttpUrl.resolve` injected as a function so a Level 4a test can drive every row without a network or an OkHttp client.

## 3. Findings

Evidence tiers: **consensus** (a standard or several independent mature clients), **common practice** (what mature libraries visibly do,
not unanimous), **single source**, **measured** (my probe).

### F1. Which statuses are followed (consensus for the five; common practice against `300`)

| Source (version) | `300` | `301` `302` `303` `307` `308` | `304` | `305` `306` | other `3xx` |
|---|---|---|---|---|---|
| RFC 9110 §15.4 | MAY follow with `Location`, "even if the specific status code is not understood" | follow if `Location` | "redirection to a previously stored result", no target | `305` deprecated, `306` unused | MAY follow with `Location` |
| Fetch (Living Standard, 19 Oct 2026) | not a redirect status | redirect statuses | no | no | no |
| OkHttp 5.5.0 `followUpRequest` | **yes** (`HTTP_MULT_CHOICE`) | yes | no | no | no |
| Ktor 3.6.1 `HttpRedirect` | no | yes | no | no | no |
| JDK 21 `RedirectFilter.isRedirecting` | no ("don't follow") | yes | no | no | no (309-399 "Unassigned => don't follow") |
| Go 1.25 `redirectBehavior` | no | yes (`switch` on those five) | no | no | no |
| http4k 6.62 `FollowRedirects` | **yes** | yes | **yes** | yes | yes (any `3xx` with a non-blank `Location`) |

- `300` is "Multiple Choices": the body is the list of alternatives and `Location` is only "a preferred choice" the client "MAY use".
  Following it hands the server one more hop it chose on a response that is not a redirect in the Fetch sense. Returning it loses
  nothing a caller cannot do itself (it sees the `300`, its `Location` and its body).
- `304` is a response by the port's contract and never has a target. `305` was "Use Proxy" (the `Location` names a proxy, not a
  resource) and `306` is reserved; following either would be wrong, and unknown `3xx` codes have no defined `Location` semantics.
- OkHttp's own follow-up never changes `GET`/`HEAD` (`permitsRequestBody` is false for them, so `redirectsToGet` is never consulted),
  which is why the transport's `GET`/`HEAD`-only rule needs no method rewriting at all.

### F2. How each client treats a missing `Location` (common practice: return the `3xx`)

- OkHttp 5.5.0: `val location = userResponse.header("Location") ?: return null` -> the `3xx` is delivered.
- Ktor 3.6.1: logs "Location header missing from redirect response ... returning response as is" and returns it.
- Go 1.25: `if loc == "" { return resp, nil }` with the comment "3xx responses without a Location have been observed in the wild".
- http4k 6.62: a blank `Location` makes `isRedirection()` false, so the response is returned.
- Fetch: `If locationURL is null, then return response.` (an absent header); an **empty** header value is a value, parses against the
  response URL and so redirects to the same URL until the count of 20.
- JDK 21: the odd one out: an absent `Location` throws `IOException("Invalid redirection")` (wrapped unchecked).

An empty or whitespace-only `Location` is therefore treated as "no target" (Go, http4k) rather than as a redirect to the same URL
(Fetch), because `HttpUrl.resolve("")` returns the current URL (measured, F6) and following it would spend the whole cap on a hop that
names nothing.

### F3. Several `Location` fields (single source for Chrome; the rest is divergence)

- RFC 9110 §10.2.2: "A Location field value cannot allow a list of members because the comma list separator is a valid data character
  within a URI-reference" and recovery from several lines "is difficult and not interoperable across implementations".
- Fetch's `extract header list values` returns failure when "the ABNF for name allows a single header and list contains more than
  one"; HTTP-redirect fetch turns a failure `locationURL` into a network error (my reading of the two algorithms together).
- Chromium (single source, a maintainer's mailing-list statement): "What Chrome actually does if it sees multiple differing Location
  headers is hard-fail the request.", while "If we have multiple identical headers we just ignore them.", added "as a mitigation against
  response splitting attacks".
- The libraries pick a value: OkHttp `Response.header` returns the **last** (`Headers.get`: "Returns the last value corresponding to the
  specified field"), Go `Header.Get` the first ("Get gets the first value associated with the given key"), the JDK `firstValue` the first.

A client that picks one value follows whichever an intermediary or an attacker placed there; the clients disagree on which, which is the
hazard. The transport returns the `3xx` unless the trimmed values are all equal (one distinct value).

### F4. `Location` shapes through `HttpUrl.resolve` and `Url.parse` (measured)

Probe: `okhttp-jvm` 5.5.0 `HttpUrl.resolve` against `https://example.com/dir/page?q=1`; the printed result then through the module's
`Url.parse` (the parser behind `UrlGuard.admitRedirect`), mapped to a reason by the same rule as `admitRedirect` (`UserInfo`, `Scheme`,
`Port`; anything else `HostForm`). "Printed" is `HttpUrl.toString()`.

| `Location` | `HttpUrl.resolve` | `Url.parse` of the printed text |
|---|---|---|
| `""`, `"   "`, TAB | the **current URL** | ok (a self-redirect) |
| `/x`, `x`, `../x`, `%2e%2e/x` | `/x`, `/dir/x`, `/x`, `/x` on the same host | ok |
| `?y=2` / `?` | `/dir/page?y=2` / `/dir/page?` | ok |
| `#frag`, `#` | printed **with** the fragment | ok; the fragment is dropped, so the result equals the current URL |
| `//other.example/x` | `https://other.example/x` | ok |
| `///x` | `https://x/` (host `x`) | ok |
| `http:/x`, `http:x`, `http:///x` | `http://x/` (scheme and host change) | ok |
| `https:x` | `https://example.com/dir/x` (relative: same scheme as the base) | ok |
| `https:` / `http:` | the current URL / **null** | ok / - |
| `////` | **null** | - |
| `ftp://h/x`, `gopher://h/`, `file:///etc/passwd`, `javascript:alert(1)`, `data:text/plain,hi`, `mailto:a@b`, `ws://h/x`, `wss://h/x` | **null** | - |
| `HTTP://H.EXAMPLE/X` | `http://h.example/X` | ok |
| `\\host\x`, `/\host`, `http:\\h\x`, `http:/\h/x` | `https://host/x`, `https://host/`, `http://h/x`, `http://h/x` (backslash read as slash) | ok |
| `http://a\@b/` | `http://a/@b/` (host `a`) | ok |
| `/a\b` | `/a/b` | ok |
| `//[1:2:3:4:5:6:7::8]/` | **throws `ArrayIndexOutOfBoundsException`** | - |
| `//[::1]/`, `//[::ffff:127.0.0.1]/` | `https://[::1]/`, `https://127.0.0.1/` | ok |
| `http://[::1`, `//[fe80::1%25eth0]/` (zone) | **null** | - |
| `http://a b/`, a 300-character host, `http://h:0/`, `http://h:99999/`, `http://` | **null** | - |
| `http://h:/x`, `http://h:+80/` | `http://h/x`, `http://h/` | ok |
| `http://user:pw@h/`, `//u:p@h/` | printed with user-info | **fails `UserInfo`** |
| `http://example.com` + U+FF0F + `@127.0.0.1/` | `http://example.com%EF%BC%8F@127.0.0.1/` (user-info, host `127.0.0.1`) | **fails `UserInfo`** |
| `http://xn--bcher-kva.example/`, `http://bücher.example/` | `http://xn--bcher-kva.example/` | **fails `HostIdn` -> `HostForm`** |
| `http://%65xample.com/` | `http://example.com/` (HttpUrl decodes) | ok |
| `http://127.1/`, `http://2130706433/`, `//0x7f.1/` | printed as written | ok (the policy's host-form and address rules judge them) |
| `/x` + NUL + `y`, `/x` TAB `y`, `/p` + lone surrogate, `/a%zz`, a 100 000-character path | `/x%00y`, `/xy`, `/p%3F`, `/a%zz`, accepted | ok |
| `page?q=1`, `https://example.com:443/dir/page?q=1` | the current URL, spelled identically | ok (a self-redirect) |

Consequences the table fixes:

1. **A control character, backslash or percent-encoded host never reaches `Url.parse`**: `HttpUrl` has already removed, slashed or
   decoded it. The refusals `admitRedirect` can actually produce for a redirect are `UserInfo`, `HostForm` (IDN) and the hop rules.
   The judged string and the connected string are the same only if the request is built from the **admitted `Url`**
   (`Url.toString()`), not from the `HttpUrl` that resolved it; the url-parsing research (F4) shows `Url.parse` only ever narrows
   `HttpUrl`, so the admitted text is also parseable by OkHttp.
2. **`resolve` returns null for two different things** that the printed-text interface of `admitRedirect` cannot tell apart: a foreign
   scheme (`ftp:`, `javascript:`, `localhost:8080/x` read as a scheme) and a malformed `http(s)`/scheme-relative authority (port 0,
   unclosed bracket, space, over-long host, IPv6 zone). The loop needs a small pure scheme test on the raw value to choose `Scheme` over
   `HostForm`.
3. **The empty `Location` and the self-redirect are the same thing to `resolve`**, so blank values must be removed before resolving.
4. A `Location` of `///x`, `http:/x` or `\\host\x` changes the **host** (to `x`, `x`, `host`) or the scheme in ways a string comparison on
   the raw text would miss; the admission and the cross-origin test must run on the canonical `Url` (as the existing bypass research
   T08 requires).
5. Because `Url` drops the fragment, a `#frag` `Location` and a differently spelled copy of the current URL both compare equal to it.

The throw is the known url-parsing F6 (`//[1:2:3:4:5:6:7::8]/`); no other probed shape threw.

### F5. Hostile targets: what mature clients do with a scheme the client cannot speak (common practice: an error, not the `3xx`)

- Fetch: `If locationURL’s scheme is not an HTTP(S) scheme, then return a network error.`
- Go: parses the `Location`, builds the request and fails in `send` ("unsupported protocol scheme").
- OkHttp 5.5.0 alone returns the `3xx`: `// Don't follow redirects to unsupported protocols.` followed by `... .resolve(location) ?: return null`.

OkHttp's choice is a side effect of `resolve` returning null, not a documented policy, and a transport that returns the `3xx` for
`file:///etc/passwd` and a refusal for `http://127.0.0.1/` gives a caller two different outcomes for two different attempts at the same
thing. The policy already owns a `Scheme` reason ("The URL's scheme is not allowed"), and the bypass research (T01) lists `gopher:` and
`file:` redirects as attempts the per-hop check must stop with zero connects.

### F6. The check order (common practice splits; semantics decide)

- Go: empty `Location` (return) -> `Parse` failure (error) -> `CheckRedirect`, whose default is `len(via) >= 10`. Target validity first.
- Fetch: location null (return) -> failure or non-HTTP(S) scheme (network error) -> `redirect count is 20` (network error). Validity first.
- OkHttp: builds the follow-up (a null `resolve` returns the `3xx`), then counts `++followUpCount > MAX_FOLLOW_UPS` (20). Validity first.
- http4k: `if (attempt == 10) error("Too many redirections")` before `request.newLocation(...)`. Cap first.
- JDK: `canRedirect(redir) && ++exchange.numberOfRedirects < max_redirects` in one condition; at the limit the `3xx` is returned, no error.

In Go, Fetch and OkHttp the two failures are the same kind (an error or a network error), so the order is not observable. Here it is:
`TooManyRedirects(limit)` and `RefusedByPolicy(reason)` are different failures, and `maxRedirects = 0` is documented as "follows none".
With the cap first, a caller who sets 0 gets `TooManyRedirects(0)` for every followable redirect, never a policy refusal for text they did
not ask to be resolved. With admission first, a chain whose hop `cap+1` points at a denied address reports the refusal, which is more
informative to a defender, but the target was never going to be contacted either way and the `WARN` log of a refusal is the only thing
lost. Cap-first also keeps the surplus hop's untrusted text away from `resolve`. A missing, empty or ambiguous `Location` precedes both
(Go and Fetch do the same), so a chain that ends in a `3xx` with no target beyond the cap is returned, not failed.

### F7. What a followed hop carries (consensus on keeping headers; the cross-origin rule differs in width)

| Source | Headers on the next hop | Cross-origin removal | Strip sticky? | Method |
|---|---|---|---|---|
| RFC 9110 §15.4 | remove implementation-generated: connection-specific, `Proxy-Authorization`, `Host`, **validators the implementation's cache added** (`If-None-Match`, `If-Modified-Since`), `Referer`, `Origin`, `Authorization`, `Cookie`; "consider removing" caller-supplied ones with security implications | `Authorization`, `Cookie` named | - | per status |
| Fetch HTTP-redirect fetch | the request's header list, unchanged (body headers deleted only when the method becomes `GET`) | `Authorization` ("the moment another origin is seen after the initial request"); origin = scheme + host + port | yes: deleted from the one list | `GET`/`HEAD` kept on `303` |
| OkHttp 5.5.0 | `userResponse.request.newBuilder()`: all headers (conditionals, `Range`, `Accept` included) | `Authorization` only, when `canReuseConnectionFor` is false (host, port or scheme differs) | yes (builder carries forward) | unchanged for `GET`/`HEAD` |
| Ktor 3.6.1 | `takeFromWithExecutionContext(requestBuilder)`: all | `Authorization`, when `authority` differs | yes | unchanged |
| JDK 21 | `this.userHeaders = other.userHeaders`: all | none in `RedirectFilter` | - | `303` -> **`GET`** (even for `HEAD`) |
| Go 1.25 | copies the **initial** request's headers each hop | `Authorization`, `Www-Authenticate`, `Cookie`, `Cookie2`, `Proxy-Authorization`, `Proxy-Authenticate`, unless the target is the same host or a subdomain | yes: `stripSensitiveHeaders` once true stays true | `HEAD` kept on `303`; adds `Referer` |
| http4k 6.62 | all | `Authorization`, `Cookie`, `Proxy-Authorization` (configurable), when host, scheme or port differ | yes (request carried forward) | non-`GET`/`HEAD` -> `GET` |

- **Conditional headers, `Range`, `Accept` go along** in every client. RFC 9110 removes only validators the *implementation's cache*
  added; a validator the caller supplied is "added by the calling context" and falls under "consider removing ... where there are
  security implications", which the sensitivity flag expresses per header. A caller's `If-None-Match` for the first URL therefore
  reaches the next origin; the worst case is a `200` instead of a `304`.
- **Stripped stays stripped**: Fetch (one mutated list), OkHttp and Ktor (the follow-up is built from the previous request) and Go
  (explicitly sticky). With a per-hop comparison the result equals Go's initial-vs-target comparison, because removal happens at the
  first hop that leaves the original origin and nothing is ever added back: `A -> B -> A` leaves the second `A` request without them.
- **`HEAD` stays `HEAD` after a `303`** in Fetch, OkHttp, Go, Ktor and http4k; RFC 9110 §15.4.4 says the retrieval request is "a GET or
  HEAD request if using HTTP". The JDK turns it into `GET`, which for a metadata-only caller costs a body read up to the cap.
- **Origin width.** The required rule (scheme, host or effective port) is Fetch's and OkHttp's, and it is narrower than Go's (a
  subdomain keeps its credentials) and wider than Ktor's (scheme ignored). http4k compares nullable ports, so `https://a` and
  `https://a:443` differ and it strips conservatively; the transport compares `Url.port`, which is always the effective port.
  Consequence: a same-host `http` to `https` upgrade drops the sensitive headers.
- **No `Referer`** is added by OkHttp, Ktor, the JDK or http4k; Go and browsers add one. A transport used for untrusted fetches adds
  none, and a caller who wants one sends it (flagged sensitive if it must not leave the origin).

### F8. `Retry-After` on a `3xx`, and the shared deadline

- RFC 9110 §10.2.3: "When sent with any 3xx (Redirection) response, Retry-After indicates the minimum time that the user agent is asked to
  wait before issuing the redirected request." OkHttp's follow-up logic reads `Retry-After` only for `408` and `503`; Go, Fetch, the JDK
  and Ktor do not read it for redirects. No compared client waits. A transport that slept for a server-chosen delay would let the target
  spend the caller's deadline (`Retry-After: 3600` ends in `Timeout`), so it is not honoured; a followed hop's headers are discarded,
  and a `3xx` that is returned keeps its `Retry-After` in `HttpResponse.headers` for a caller or the rate-signal reader.
- OkHttp's `Call.timeout()` is documented as "a timeout that spans the entire call ... If the call requires redirects or retries all
  must complete within one timeout period" -- for **one** `Call`. With one `Call` per hop, the shared deadline is the transport's job:
  it passes each `Call` `callTimeout` minus the time already spent (already measured: two hops of 600 ms against 1000 ms ended with
  `Timeout(Call)` at 1001 ms, http-transport F4). Okio's `Timeout.timeout`: "If `timeout == 0`, operations will run indefinitely", so a
  computed remainder that rounds to 0 ms would silently lift the limit; the transport must fail with `Timeout` itself when less than
  1 ms remains, which is also the smallest value `TransportSettings` accepts for a timeout.

### F9. The loop's cap and loops (common practice: the cap is the loop detector)

OkHttp stops at 20 follow-ups, Fetch at 20, Go at 10 (`len(via) >= 10`), http4k at 10, the JDK at a property-defined 5
(`jdk.httpclient.redirects.retrylimit`); Ktor 3.6.1's `HttpRedirect` has a `while (true)` loop and no counter. None of the others
detects a cycle: they bound it. RFC 9110 says a client "SHOULD detect and intervene in cyclical redirections"; a cap does that. With
`Untrusted`'s default of 3, a self-redirect costs 4 requests to the same origin. Detecting cycles would need a new failure kind (or a
misleading `TooManyRedirects`), and a legitimate server may redirect to the same URL once (for example after setting a cookie, which
this transport does not keep, so such a server loops anyway and the cap ends it).

## 4. How comparable projects do it

- **OkHttp 5.5.0** (`RetryAndFollowUpInterceptor.followUpRequest` and `buildRedirectRequest`, tag `parent-5.5.0`): follows `300 301 302
  303 307 308`; `userResponse.header("Location") ?: return null` (the last value); `resolve(location) ?: return null`; strips only
  `Authorization` across hosts; 20 follow-ups, then `ProtocolException("Too many follow-up requests: 21")`.
- **Ktor 3.6.1** (`HttpRedirect`, `ALLOWED_FOR_REDIRECT = setOf(HttpMethod.Get, HttpMethod.Head)`, `allowHttpsDowngrade = false`): five
  statuses, missing `Location` returns the response, an HTTPS downgrade returns the response, `Authorization` removed when the
  authority differs, no counter. Its 3.6.1 source added a dot-segment fix to its hand-rolled resolution, an argument for resolving with
  `HttpUrl.resolve` rather than by hand.
- **JDK 21** (`RedirectFilter`): five statuses, `numberOfRedirects` against `jdk.httpclient.redirects.retrylimit`, past the limit the `3xx`
  is returned (not an error), a missing `Location` is an exception, a `303` becomes `GET`.
- **Go 1.25** (`Client.do`, `redirectBehavior`, `makeHeadersCopier`, `defaultCheckRedirect`): five statuses, empty `Location` returns the
  response, parse error is an error, the cap fires in `CheckRedirect` after the target is built, sticky stripping of the six credential
  headers.
- **http4k 6.62** (`ClientFilters.FollowRedirects`): any `3xx` with a non-blank `Location`, cap 10 checked **before** the target is
  resolved, throws `error("Too many redirections")`, returns the `3xx` for an HTTPS downgrade, strips three headers when host, scheme or
  port differ.
- **Fetch (Living Standard)**: five statuses; null location returns the response; failure or non-HTTP(S) scheme is a network error;
  count of 20; `Authorization` removed on an origin change; `HEAD` kept on `303`.

## 5. Pitfalls and regrets

- **`Response.header("Location")` takes the last of several**; Go and the JDK take the first. Read all values (`Headers.values`).
- **Judging one string and connecting another.** `HttpUrl.resolve` has already slashed backslashes, decoded `%65`, removed TAB/LF and
  IDNA-mapped the host before anything prints it. Build the next request from `Url.toString()` of the admitted `Url`.
- **A timeout of 0 disables the limit.** `remaining.inWholeMilliseconds` of a sub-millisecond remainder is 0; check before setting it.
- **`Request.newBuilder()` copies tags**, so the per-hop exchange counter must be a fresh tag (http-transport F4 pitfall, restated
  because the loop builds each hop from the previous request).
- **A hand-rolled resolver** (Ktor before 3.6.1 needed a dot-segment fix) or a hand-rolled origin comparison (http4k's nullable ports)
  are repeating sources of redirect bugs. Use `HttpUrl.resolve` and compare `Url.scheme/host/port`.
- **A blank `Location` is not "the same URL"** for loop purposes; the empty value followed once per cap is wasted traffic and, under a
  tight dispatcher, queue pressure.
- **A followed hop's body is never read**, so the connection is not reusable; a long chain costs a new connection per hop (the price of
  not draining untrusted bytes).
- **Credentials dropped on a benign `http` to `https` upgrade** of the same host (the required rule). A `Trusted` caller with an
  `http://` URL and an `Authorization` header will see a `401` after the upgrade; the remedy is an `https://` request URL.
- **Time between hops.** Each hop re-enters OkHttp's dispatcher queue; the time a hop waits there is not part of OkHttp's call timeout
  (the architecture already sizes the dispatcher for this), so compute the remaining time immediately before creating each `Call`.
- **Retry-After on a followed `3xx` is lost** (its headers are discarded). Acceptable because no compared client uses it; flagged as a
  gap for the rate-signal consumer.

## 6. Recommendation for this repository

### 6.1 The behaviour table

Notation: `n` = redirects already followed on this fetch (0 on hop 1); `cap` = `TransportSettings.maxRedirects`; `U` = the URL of the
hop that returned the `3xx`. Rows are tried in order; the first that matches decides. "Deliver" means the `3xx` is the
`HttpResponse` (body read under the normal cap unless `HEAD`/`304`, `url = U`, `redirectChain` as recorded so far). "Fail" means no
response.

| # | Condition | Outcome |
|---|---|---|
| 1 | request method is not `GET` or `HEAD` | **Deliver** (any status) |
| 2 | status is not one of `301 302 303 307 308` (this covers `300`, `304`, `305`, `306`, `309`-`399` and every non-`3xx`) | **Deliver** |
| 3 | the `Location` values, each trimmed of ASCII whitespace (SP, HTAB, LF, FF, CR), are: none; or all empty; or more than one distinct value | **Deliver** |
| 4 | `n >= cap` | **Fail `TooManyRedirects(cap)`** (so `cap = 0` fails every row that reaches here) |
| 5 | `HttpUrl.resolve(location)` against `U` throws, or returns null, and the trimmed value starts with a scheme (`[A-Za-z][A-Za-z0-9+.-]*:`) other than `http`/`https` | **Fail `RefusedByPolicy(Scheme)`** |
| 6 | `resolve` throws, or returns null, otherwise | **Fail `RefusedByPolicy(HostForm)`** |
| 7 | `UrlGuard.admitRedirect(printed, U)` is `Refused(g)` (user-info, IDN `xn--`, port, the host and address rules, `HostNotPinned`, `InsecureRedirect` for `https` to `http` unless allowed, ...) | **Fail `RefusedByPolicy(g.reason)`** (log once at `WARN` with the reason name and range class, no URL query) |
| 8 | the shared deadline has less than 1 ms left | **Fail `Timeout`** |
| 9 | otherwise (a target equal to `U` is not special: the cap bounds a loop) | **Follow**: record `RedirectHop(U, status)`, build the next request from the admitted `Url`, same method, same headers minus the sensitive ones if scheme, host or port differs, then loop with `n + 1` |

Notes on the rows:

- Rows 1 and 2 are exchangeable (both deliver); the method is listed first because it is the stated binding rule.
- Row 3: "all empty" includes a single `Location: ` and any whitespace-only value. Several equal values count as one (Chromium accepts
  identical duplicates).
- Row 4 precedes 5 to 8 on purpose (F6). It follows row 3 on purpose: a `3xx` with no usable target is a response even beyond the cap.
- Rows 5 and 6 cover both a null and an exception from `resolve`; catch `RuntimeException` (or use the module's `catchNonFatal`), never
  `Throwable`. The reason for an unresolvable `http(s)`/scheme-relative value is `HostForm`, a port-0 or port-99999 `Location` included
  (the coarse reason is accepted; `admitRedirect` already folds every parse problem other than user-info, scheme and port into it).
- Row 7 includes a refusal that `Trusted` raises (`Scheme` when cleartext is not allowed, `HostNotPinned`, `InsecureRedirect`).
- Row 8 is last so that rows 1 to 7 never depend on the clock. Hop 1 never meets it (the remainder is `callTimeout`, at least 1 ms).

### 6.2 What a followed hop carries

1. **Method**: the original method, unchanged (`GET` stays `GET`; `HEAD` stays `HEAD`, including after a `303`). No body; no
   `Content-*` change is needed because the method never changes.
2. **URL**: the admitted `Url.toString()`. The fragment is gone (the `Url` type has none).
3. **Headers**: the original request's entries in order, spelling and duplicates preserved, minus every entry removed on an earlier hop;
   on a hop whose `scheme`, `host` or `port` differs from `U`, additionally minus every entry with `isSensitive` (flagged entries and
   `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`). Removal is permanent: `A -> B -> A` does not restore it. Everything
   else goes along, `If-None-Match`, `If-Modified-Since`, `If-Match`, `If-Unmodified-Since`, `If-Range`, `Range`, `Accept*` included.
   `User-Agent`, `Accept-Encoding` and `Host` are the transport's own on every hop; the request never carried another transport-controlled
   header.
4. **No `Referer`**, no cookie jar, no cached validators (the transport has no cache).
5. **A fresh `ExchangeGuard` tag** and a fresh `Call` per hop; `Call.timeout()` set to the remaining whole milliseconds.

### 6.3 What the response and the chain hold

- `HttpResponse.url` is the URL of the **last request sent** (`U` of the delivering hop).
- `redirectChain` is `[RedirectHop(U1, s1), ..., RedirectHop(Uk, sk)]` for the `k` followed hops, `k <= cap`. A delivered `3xx` is the
  response and is not in the chain. After a failure there is no response and no chain.
- `Retry-After` on a followed `3xx` is not honoured and not kept; on a delivered `3xx` it stays in `headers`.
- The deadline: `deadline = start + callTimeout` once per `execute`; each `Call` gets `deadline - now` in whole milliseconds; below 1 ms
  the transport returns `Timeout` without opening a socket.

### 6.4 The Kotlin shape

A pure function in `jvmMain` (internal), tested without a network. `HttpUrl.resolve` is passed in as a function so the Level 4a suite can
drive every row with the real resolver and with a stub that returns null or throws:

```kotlin
internal sealed class RedirectOutcome {
    /** The 3xx is the response. [why] names the row, for a DEBUG line only. */
    class Deliver(val why: DeliverReason) : RedirectOutcome()
    /** `n >= cap`. */
    object TooMany : RedirectOutcome()
    /** The target is not admitted; [refusal] holds the reason and, for DeniedAddress, the range class. */
    class Refused(val refusal: GuardRefusal) : RedirectOutcome()
    /** Request [next] as the next hop. */
    class Follow(val next: Url) : RedirectOutcome()
}
internal enum class DeliverReason { Method, Status, NoTarget, AmbiguousTarget }

internal fun decideRedirect(
    method: HttpMethod, status: Int, locations: List<String>, followed: Int, current: Url,
    maxRedirects: Int, guard: UrlGuard, resolve: (String) -> String?,   // HttpUrl.resolve(...)?.toString(), may throw
): RedirectOutcome
internal fun headersForHop(headers: HttpHeaders, from: Url, to: Url): HttpHeaders   // strips isSensitive when scheme/host/port differ
```

The deadline row stays in the I/O loop (it needs a clock), after `decideRedirect` returns `Follow`. The loop reads
`response.headers("Location")` (all values), never `header("Location")`.

### 6.5 Level 4a (deterministic) cases this implies

All are input to outcome with no network; the resolver is the real `HttpUrl.resolve` unless a row says "stub".

1. Follow: `GET` and `HEAD` x `301 302 303 307 308` with `/x` -> `Follow(https://example.com/x)` (10 cases); the method of the follow is the
   input method (`HEAD` after `303` stays `HEAD`).
2. Deliver (method): `POST PUT PATCH DELETE OPTIONS` and an unknown method x the five statuses with a valid `Location` -> `Deliver(Method)`.
3. Deliver (status): `GET` with `300`+`Location`, `304`, `305`, `306`, `309`, `399`, `200`, `404` -> `Deliver(Status)`.
4. Deliver (no target): no `Location`; `""`; `"   "`; TAB; two differing values; (`"/a"`, `"/b"`) -> `Deliver`; two equal values -> `Follow`;
   `["", "/x"]` -> `Deliver(AmbiguousTarget)`.
5. Resolution: `x`, `../x`, `?y=2`, `?`, `//other.example/x`, `///x`, `\\host\x`, `/\host`, `HTTP://H.EXAMPLE/X`, `%2e%2e/x`,
   `https:x` -> the `Url` text of the F4 table; `#frag` and `https://example.com:443/dir/page?q=1` -> `Follow(U)` (self-redirect).
6. Foreign scheme -> `Refused(Scheme)`: `ftp://h/x`, `gopher://h/`, `file:///etc/passwd`, `javascript:alert(1)`, `data:text/plain,hi`,
   `mailto:a@b`, `ws://h/x`, `wss://h/x`, `localhost:8080/x`.
7. Unresolvable -> `Refused(HostForm)`: `http://h:0/`, `http://h:99999/`, `http://[::1`, `http://a b/`, `////`, `//[fe80::1%25eth0]/`,
   a 300-character host, `http:`, `http://`; and **the throw**: `//[1:2:3:4:5:6:7::8]/` (real resolver) and a stub that throws
   `ArrayIndexOutOfBoundsException`, `IllegalArgumentException`, `IllegalStateException` -> `Refused(HostForm)`, never an exception.
8. `Url.parse` refusals -> `Refused`: `http://user:pw@h/`, `//u:p@h/`, `http://example.com` + U+FF0F + `@127.0.0.1/` -> `UserInfo`;
   `http://xn--bcher-kva.example/`, `http://bücher.example/` -> `HostForm`.
9. Policy refusals, `Untrusted` and `Trusted`: `http://127.0.0.1/`, `//2130706433/`, `//0x7f.1/`, `http://169.254.169.254/latest/`,
   `//[::ffff:127.0.0.1]/`, `http://localhost/` -> the reason the policy owns; a pinned-host mismatch -> `HostNotPinned`;
   `https` `U` to an `http` target -> `InsecureRedirect` with `allowHttpsToHttp = false`, `Follow` with `true`; `http` `U` to `https` -> `Follow`.
10. Cap: `followed = cap` with a valid target -> `TooMany`; `followed = cap - 1` -> `Follow`; `cap = 0` -> `TooMany` for a valid target, for
    `ftp://h/` and for `http://127.0.0.1/` (cap before admission); `followed >= cap` with no `Location`, a `300`, or a `POST` -> `Deliver`
    (rows 1 to 3 come before the cap).
11. Headers: cross host, cross scheme on one host, cross port (443 to 8443), subdomain -> sensitive entries removed; same host with the
    default port spelled out (`:443`) -> kept; entries removed: flagged `X-Api-Key`, `authorization` in any case, `Proxy-Authorization`,
    `Cookie`, `Set-Cookie`; kept and in order: `If-None-Match`, `If-Modified-Since`, `If-Range`, `Range`, `Accept`, a duplicated custom
    header; `A -> B -> A` keeps them removed (apply `headersForHop` twice with the carried result).
12. Chain and response (loop-level, with a fake `Call`): two followed hops then a `200` -> `redirectChain == [(U1, s1), (U2, s2)]`,
    `url == U3`; two followed hops then a `302` with no `Location` -> chain of 2, `url == U3`, `status == 302`.
13. Deadline (loop-level, injected clock): remaining `0`, `999 us` -> `Timeout`; exactly `1 ms` -> proceeds with `1 ms`; a `Follow` row that is
    also past the deadline -> `Timeout`; a `TooMany`/`Refused`/`Deliver` row past the deadline keeps its own outcome.

Level 3 (against `mockwebserver3`) additions that follow from this table and are not 4a: a `Retry-After: 3600` on a followed `302` does not
delay the next hop; the target server of a `Refused` row has `requestCount == 0`; the second server of a `Scheme` row (`ftp:`) is never
contacted; two `Location` fields (`/a`, `/b`) deliver the `302` with both headers readable.

### 6.6 What I would reject

- **Following `300`.** It is OkHttp's and http4k's behaviour and the RFC permits it, but the Fetch standard, the JDK, Go and Ktor do not,
  and it adds an attacker-selectable hop to a response that is not a redirect.
- **Returning the `3xx` for a foreign scheme or an unresolvable target** (OkHttp's side effect). It hides the attempt behind an ordinary
  status and treats `file:///etc/passwd` unlike `http://127.0.0.1/`.
- **Admission before the cap.** It makes `maxRedirects = 0` report policy refusals for text the caller disabled redirects to avoid.
- **Loop detection.** The cap bounds it, mature clients do the same, and a new failure kind is not worth the saved requests.
- **Choosing among several `Location` values.** The clients that choose disagree on which.

## 7. Load-bearing claims

1. **OkHttp 5.5.0 follows `300 301 302 303 307 308` and nothing else in `followUpRequest`.**
   Quote: `HTTP_PERM_REDIRECT, HTTP_TEMP_REDIRECT, HTTP_MULT_CHOICE, HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER -> {` / `return buildRedirectRequest(userResponse, method, chain)`.
   Source: [RetryAndFollowUpInterceptor.followUpRequest](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt), tag `parent-5.5.0`.
2. **OkHttp returns the `3xx` for a null `resolve` and strips only `Authorization` across hosts.**
   Quote: `val location = userResponse.header("Location") ?: return null` / `// Don't follow redirects to unsupported protocols.` / `val url = userResponse.request.url.resolve(location) ?: return null` / `// When redirecting across hosts, drop all authentication headers. This` `// is potentially annoying to the application layer since they have no` `// way to retain them.`
   Source: `buildRedirectRequest` in the same file, `parent-5.5.0`.
3. **OkHttp leaves `GET`/`HEAD` unchanged on a redirect.**
   Quote: `fun permitsRequestBody(method: String): Boolean = !(method == "GET" || method == "HEAD")`, guarding `if (HttpMethod.permitsRequestBody(method)) {` in `buildRedirectRequest`.
   Source: [HttpMethod.permitsRequestBody](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/HttpMethod.kt), `parent-5.5.0`.
4. **`Response.header` returns the last value.**
   Quote: `/** Returns the last value corresponding to the specified field, or null. */`
   Source: [Headers.get](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Headers.kt), `parent-5.5.0`; `Response.header(name)` is `headers[name] ?: defaultValue` in [Response.kt](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Response.kt).
5. **`HttpUrl.resolve` returns null for a URL that is not well-formed.**
   Quote: `Returns the URL that would be retrieved by following `link` from this URL, or null if the resulting URL is not well-formed.`
   Source: [HttpUrl.resolve](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/HttpUrl.kt), `parent-5.5.0`. (That it also throws `ArrayIndexOutOfBoundsException` for `//[1:2:3:4:5:6:7::8]/` is **measured**, F4, and is url-parsing F6.)
6. **A timeout of 0 means no timeout.**
   Quote: `If `timeout == 0`, operations will run indefinitely.`
   Source: [okio.Timeout.timeout](https://github.com/square/okio/blob/parent-3.18.1/okio/src/jvmMain/kotlin/okio/Timeout.kt), Okio 3.18.1 (the version `okhttp-jvm` 5.5.0 declares).
7. **OkHttp's call timeout is per `Call`, spanning that call's redirects and retries.**
   Quote: `Returns a timeout that spans the entire call: resolving DNS, connecting, writing the request body, server processing, and reading the response body. If the call requires redirects or retries all must complete within one timeout period.`
   Source: [Call.timeout](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt), `parent-5.5.0`.
8. **Fetch: the five redirect statuses.**
   Quote: `A redirect status is a status that is 301, 302, 303, 307, or 308.`
   Source: [Fetch Standard, redirect status](https://fetch.spec.whatwg.org/#redirect-status), Living Standard, last updated 19 October 2026.
9. **Fetch: HTTP-redirect fetch outcomes.**
   Quote: `If locationURL is null, then return response.` / `If locationURL is failure, then return a network error.` / `If locationURL’s scheme is not an HTTP(S) scheme, then return a network error.` / `If request’s redirect count is 20, then return a network error.` / `I.e., the moment another origin is seen after the initial request, the `Authorization` header is removed.`
   Source: [Fetch Standard, HTTP-redirect fetch](https://fetch.spec.whatwg.org/#http-redirect-fetch), 19 October 2026.
10. **Fetch: several single-valued headers are a failure.**
    Quote: `If the ABNF for name allows a single header and list contains more than one, then return failure.`
    Source: [Fetch Standard, extract header list values](https://fetch.spec.whatwg.org/#extract-header-list-values), 19 October 2026.
11. **RFC 9110: any status with a `Location` MAY be followed; caller-supplied headers are only "considered" for removal; validators removed are the cache's; loops SHOULD be detected.**
    Quote: `If a Location header field (Section 10.2.2) is provided, the user agent MAY automatically redirect its request to the URI referenced by the Location field value, even if the specific status code is not understood.` / `Validating header fields that were added by the implementation's cache (e.g., If-None-Match, If-Modified-Since), and` / `Consider removing header fields that were not automatically generated by the implementation (i.e., those present in the request because they were added by the calling context) where there are security implications; this includes but is not limited to Authorization and Cookie.` / `A client SHOULD detect and intervene in cyclical redirections (i.e., "infinite" redirection loops).`
    Source: [RFC 9110 §15.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4), June 2022.
12. **RFC 9110: `300`, `305`, `306`, `303`.**
    Quote: `The user agent MAY use the Location field value for automatic redirection.` (§15.4.1); `The 305 (Use Proxy) status code was defined in a previous version of this specification and is now deprecated` (§15.4.6); `The 306 status code was defined in a previous version of this specification, is no longer used, and the code is reserved.` (§15.4.7); `A user agent can perform a retrieval request targeting that URI (a GET or HEAD request if using HTTP)` (§15.4.4).
    Source: [RFC 9110 §15.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4), June 2022.
13. **RFC 9110: `Location` is a single value; several lines are not interoperable.**
    Quote: `A Location field value cannot allow a list of members because the comma list separator is a valid data character within a URI-reference.` / `Recovery of a valid Location field value from that situation is difficult and not interoperable across implementations.`
    Source: [RFC 9110 §10.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.2), June 2022.
14. **RFC 9110: `Retry-After` on a `3xx`.**
    Quote: `When sent with any 3xx (Redirection) response, Retry-After indicates the minimum time that the user agent is asked to wait before issuing the redirected request.`
    Source: [RFC 9110 §10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3), June 2022.
15. **Ktor 3.6.1: five statuses, `GET`/`HEAD` only, a missing `Location` returns the response, `Authorization` removed on a different authority, no counter.**
    Quote: `private val ALLOWED_FOR_REDIRECT: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head)` / `"Location header missing from redirect response ${call.request.url}; returning response as is"` / `if (previousAuthority != url.authority) {` / `headers.remove(HttpHeaders.Authorization)` / `HttpStatusCode.MovedPermanently.value, HttpStatusCode.Found.value, HttpStatusCode.TemporaryRedirect.value, HttpStatusCode.PermanentRedirect.value, HttpStatusCode.SeeOther.value -> true` (the quoted lines are on separate lines in the source).
    Source: [HttpRedirect.kt](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/HttpRedirect.kt), tag `3.6.1`.
16. **JDK 21: `300 304 305 306` not followed; `303` becomes `GET`; a missing `Location` throws; past the limit the `3xx` is returned.**
    Quote: `// 300: MultipleChoice => don't follow` / `// 304: Not Modified => don't follow` / `case 303        -> "GET";` / `.orElseThrow(() -> new UncheckedIOException(` `new IOException("Invalid redirection")));` / `if (canRedirect(redir) && ++exchange.numberOfRedirects < max_redirects) {` ... `Log.logTrace("not redirecting");` `return null;` / `this.userHeaders = other.userHeaders;` (in `HttpRequestImpl`).
    Source: [RedirectFilter.java](https://github.com/openjdk/jdk/blob/jdk-21%2B35/src/java.net.http/share/classes/jdk/internal/net/http/RedirectFilter.java) and [HttpRequestImpl.java](https://github.com/openjdk/jdk/blob/jdk-21%2B35/src/java.net.http/share/classes/jdk/internal/net/http/HttpRequestImpl.java), tag `jdk-21+35`.
17. **Go 1.25: five statuses, `HEAD` kept on `303`, empty `Location` returns the response, parse error is an error, sticky stripping, cap 10 after the target is built.**
    Quote: `case 301, 302, 303:` / `if reqMethod != "GET" && reqMethod != "HEAD" {` / `// While most 3xx responses include a Location, it is not` `// required and 3xx responses without a Location have been` `// observed in the wild.` / `if !stripSensitiveHeaders && reqs[0].URL.Host != req.URL.Host {` / `if len(via) >= 10 {` `return errors.New("stopped after 10 redirects")`.
    Source: [net/http/client.go](https://github.com/golang/go/blob/go1.25.0/src/net/http/client.go) (`redirectBehavior`, `Client.do`, `defaultCheckRedirect`), tag `go1.25.0`.
18a. **Go `Header.Get` returns the first value.**
    Quote: `Get gets the first value associated with the given key.`
    Source: [net/http/header.go](https://github.com/golang/go/blob/go1.25.0/src/net/http/header.go) (`Header.Get`), tag `go1.25.0`.
18. **http4k 6.62: any `3xx` with a non-blank `Location`, cap before resolving, `GET`/`HEAD` kept, cross-origin strip of three headers.**
    Quote: `if (attempt == 10) error("Too many redirections")` / `status.redirection && header("location")?.let(String::isNotBlank) == true` / `if (method == GET || method == HEAD) this else method(GET)` / `setOf("Authorization", "Cookie", "Proxy-Authorization")`.
    Source: [ClientFilters.kt, `FollowRedirects`](https://github.com/http4k/http4k/blob/6.62.0.0-ee/core/core/src/main/kotlin/org/http4k/filter/ClientFilters.kt), tag `6.62.0.0-ee`.
19. **Chromium rejects differing `Location` headers and accepts identical ones.**
    Quote: `What Chrome actually does if it sees multiple differing Location headers is hard-fail the request.` / `If we have multiple identical headers we just ignore them.` / `This was added as a mitigation against response splitting attacks, as I recall`.
    Source: [Chromium network engineer's message, public-webapps-github, March 2019](https://lists.w3.org/Archives/Public/public-webapps-github/2019Mar/0965.html). Single source (a recollection, not a spec).

## 8. Gaps

- **Chromium and Firefox status lists were not read from source.** The Chrome behaviour for multiple `Location` is one maintainer's
  statement (claim 19); Firefox's handling of `300` with `Location` is not established here and no claim rests on it.
- **curl's cap-versus-validity order was not established** (its source was not located at a pinned tag); it is not used.
- **The Fetch reading for several `Location` fields** (failure -> network error) is my reading of two algorithms, not a sentence in the
  standard; the Chromium behaviour agrees.
- **The policy-refusal cases (item 9 of the 4a list) name the policy's own reasons** (`DeniedAddress`, `ReservedName`, `HostForm`) as owned by
  the URL-policy unit; this file did not re-derive them. Only `Url.parse`'s own mapping (user-info, IDN, scheme, port) was probed.
- **`HttpResponse.headers` sanitisation of a received `Location`** (CR, LF and NUL in a value become SP) happens when the response is
  built, while the loop reads OkHttp's `Headers` first. Whether a `Location` value can reach the loop holding NUL, and whether the loop
  should apply the same replacement before trimming, was not probed against `mockwebserver3`; the table treats a value that is empty
  after trimming ASCII whitespace as no target.
- **Retry-After on a followed `3xx` is discarded**; whether the facade's rate-signal reader wants it from a chain is not decided here.
- **Time spent waiting in OkHttp's dispatcher queue between hops** is outside OkHttp's call timeout (claim 7 is about a `Call`'s own
  duration); the architecture's dispatcher sizing is relied on, and a Level 4c test that saturates the dispatcher mid-chain is not
  specified here.
- **Cap-first versus admission-first is a judgement** (F6); both avoid contacting the target. If the plan prefers the refusal to win, only
  row 4 and rows 5 to 7 swap, and the 4a cap cases in item 10 change accordingly.
- **Measured only against the `Url.parse` of the checked-out source and `okhttp-jvm` 5.5.0**; a consumer who overrides OkHttp to a newer
  5.x may see different `resolve` results for the odd shapes (null versus a value), which the table's rows 5 and 6 tolerate by judging
  the printed result.
