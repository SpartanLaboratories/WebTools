# Which request headers the transport controls, and what OkHttp 5.5.0 does with each

## Question

Which request header names does `OkHttpTransport` refuse with `TransportFailure.InvalidRequest(rule)` before any
socket is opened, and what does OkHttp 5.5.0 do with each name if a caller sends it, on HTTP/1.1 and on HTTP/2?

An answer has to meet these requirements:

- The set includes at least `User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length` and
  `Transfer-Encoding`, and every other name whose caller-supplied value would change the exchange's framing, its
  decoding, or the host or protocol the request reaches.
- Names compare with the module's ASCII-only fold (`HttpSyntax.equalsName`); the rule text is constant and names
  the header.
- The set behaves the same on HTTP/1.1 and HTTP/2.
- Credential names are not part of it: the `Untrusted` policy refuses them and `Trusted` sends them.
- The transport sends its own `User-Agent` and lets OkHttp add `Accept-Encoding: gzip` and decode it, because the
  body cap counts decoded bytes.

## Bottom line

- **Refuse thirteen names:** `User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length`,
  `Transfer-Encoding` (the six given), plus `Keep-Alive`, `Proxy-Connection`, `TE`, `Trailer`, `Upgrade`, `Expect`
  and `Range`. Every one of them is either sent verbatim by OkHttp on one protocol and dropped silently on the other,
  or changes framing, decoding, the target or the protocol on at least one of them (measured, F1 and F2).
- **Leave out** `Content-Encoding`, `Via`, `Origin`, `Referer`, `Date`, `Accept-Charset`, the other `Proxy-` names, the
  `Sec-` prefix, `Cookie2`, `DNT` and method-override names. OkHttp sends them verbatim on both protocols
  with no effect on framing, decoding, target or protocol (measured). The Fetch standard forbids them to protect a
  browser page's own identity, which does not apply to a library (F4).
- **`Range` is the one contested name.** OkHttp adds `Accept-Encoding: gzip` only when the caller sets neither
  `Accept-Encoding` nor `Range`; with `Range` it sends no `Accept-Encoding` at all, and a server may then legally
  answer with any content coding, which reaches the caller undecoded (measured). Refusing it keeps "the body is
  decoded" true; admitting it later, together with an explicit `Accept-Encoding: identity`, is additive.
- **Refuse by name, regardless of value, body or method**, from a private list compared with
  `HttpSyntax.equalsName`. OkHttp's behaviour for `Content-Length`, `Transfer-Encoding`, `Connection` and `Host`
  depends on whether a body exists and on the protocol, so a name-only rule is the one that is uniform.
- **Order and scope:** after the `Closed` check and before `UrlGuard.checkRequest`; both complete before any
  socket. The check runs once, on the request as passed to `execute`. A redirect hop carries a subset of those
  validated headers plus names the transport itself sets, so it needs no second check.
- **Rule text:** one constant per name, `header <Name> is controlled by the transport`, using the list's canonical
  spelling, never the caller's spelling or value.

## Findings

All OkHttp findings are for 5.5.0 (`parent-5.5.0`). "Measured" means a probe run in this research against OkHttp
5.5.0 on a loopback server (a raw `ServerSocket` for HTTP/1.1; `mockwebserver3` 5.5.0 with TLS from `okhttp-tls`
5.5.0 and ALPN for HTTP/2), JDK 23. Both protocols ran with `retryOnConnectionFailure(false)`, a 2 s read timeout and
a 4 s call timeout.

### F1. What OkHttp does with each candidate name (documented in source and measured)

`BridgeInterceptor` fills in `Host`, `Connection`, `Accept-Encoding` and `User-Agent` only when the caller left them
out, and sets `Content-Length` or `Transfer-Encoding` only when the request has a body. The exchange codecs write what
is left. HTTP/1.1 writes every name and value as given. HTTP/2 lower-cases the name and drops a fixed list.

| Name | HTTP/1.1 (measured) | HTTP/2 over TLS (measured) | Effect if sent |
|---|---|---|---|
| `User-Agent` | Caller's value sent; two values sent as two lines | Caller's value sent | Overrides the transport's identity |
| `Accept-Encoding` | Caller's value sent; OkHttp adds no `gzip` and does not decode (`gzip` and `identity` both measured) | Same | Decoding: undecoded bytes reach the cap and the caller |
| `Host` | `Host: other.example` sent verbatim; request line still names the URL's path | Becomes the `:authority` pseudo-header (`other.example`) | Target: the application-level host differs from the URL the policy judged |
| `Connection` | `close` sent and honoured (connection not reused); `Upgrade` switches on upgrade handling | Not on the wire, but `close` still marks the connection no-new-exchanges: the next request opened a second connection | Protocol, and pool churn |
| `Content-Length` | With a body: replaced by the real length. No body (`GET`): sent verbatim, the probe server waited for 5 bytes that never came and the client timed out | With a body: replaced. No body: forwarded as `content-length: 5` | Framing; HTTP/2 malformed if it differs from the DATA total |
| `Transfer-Encoding` | With a body: removed and `Content-Length` used. No body: `Transfer-Encoding: chunked` sent verbatim, the probe server waited for a chunk, client timed out | Dropped silently | Framing on HTTP/1.1 |
| `Keep-Alive`, `Proxy-Connection` | Sent verbatim | Dropped silently | Connection-specific; the two protocols disagree |
| `TE` | Any value sent verbatim (`gzip, trailers`) | Only exactly `trailers` forwarded; `gzip, trailers` dropped | Decoding: invites a transfer coding OkHttp's HTTP/1.1 reader does not decode |
| `Trailer` | Sent verbatim | Forwarded (`trailer: X-T`) | Announces a trailer section the transport never sends |
| `Upgrade` | Sent verbatim. With a server answering `101` and no `Connection: Upgrade`, the `101` is returned as the response. With `Connection: Upgrade`, OkHttp upgrades: the response body is an `UnreadableResponseBody` and reading it throws `IllegalStateException` | Dropped silently | Protocol |
| `Expect` | `100-continue` honoured: with a `100` from the server the body follows; with a server that answers nothing until it has the body, the call stalled until the read timeout (2 s in the probe) | Honoured: with a final response and no `100`, the body is not sent and the call returns that response | Exchange flow; a stall on servers that ignore `Expect` |
| `Range` | No `Accept-Encoding` sent. A server that gzips anyway delivered 25 undecoded bytes with `Content-Encoding: gzip` | Same: no `accept-encoding` on the wire | Decoding |
| `Content-Encoding` | Sent verbatim next to a body; framing unchanged | n/a (same code path) | None to framing or response decoding |
| `Via`, `Origin`, `Referer`, `Date`, `Accept-Charset`, `Proxy-X`, `Sec-X` | Sent verbatim | Sent verbatim, lower-cased | None |

Source anchors (all at `parent-5.5.0`):

- `BridgeInterceptor.intercept`: Host/Connection/User-Agent defaults, `Accept-Encoding` and the `Range` condition,
  the body's `Content-Length` and the removal of `Transfer-Encoding`.
- `Http2ExchangeCodec.http2HeadersList`: `:authority` taken from the `Host` header, the skipped list (`connection`,
  `host`, `keep-alive`, `proxy-connection`, `te` unless exactly `trailers`, `transfer-encoding`, `encoding`,
  `upgrade`, the four pseudo-header names), every other name forwarded. `content-length` and `trailer` are not in it.
- `Http1ExchangeCodec.writeRequest`: names and values written as given.
- `CallServerInterceptor.intercept`: `Expect: 100-continue` handling, `Connection: upgrade` (`isUpgradeRequest`),
  and `Connection: close` calling `noNewExchangesOnConnection()`.

Tier: source documented; behaviour measured on both protocols.

### F2. Why the six given names and the seven added ones belong (reasoned from F1 and the RFCs)

| Name | Reason | Basis |
|---|---|---|
| `User-Agent` | The transport's identity; OkHttp lets the caller's value win, so two values or a spoofed one would be sent | F1; the factory's required, validated `userAgent` |
| `Accept-Encoding` | Decoding: a caller value stops OkHttp's transparent gzip, so the cap would count encoded bytes and the caller would get encoded bytes | Existing finding F6 (`../../research/http-transport.md`); F1 |
| `Host` | Target: HTTP routes on `Host`/`:authority` ([RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2) calls it "an application-level routing mechanism"). The policy judges the URL's host and address; a caller `Host` would send the request to another virtual host behind the admitted address, and on HTTP/2 it replaces the authority | F1 |
| `Connection` | Protocol and connection control: `close`, `Upgrade` and arbitrary hop-by-hop lists | F1; RFC 9110 §7.6.1 |
| `Content-Length` | Framing: sent verbatim on a body-less request on both protocols | F1; [RFC 9112 §6.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.2); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1) |
| `Transfer-Encoding` | Framing: sent verbatim on a body-less HTTP/1.1 request | F1; RFC 9112 §6.1 |
| `Keep-Alive`, `Proxy-Connection` | Connection-specific fields: RFC 9113 §8.2.2 forbids an HTTP/2 message from carrying them, OkHttp drops them there and sends them on HTTP/1.1 | F1 |
| `TE` | Transfer-coding negotiation and trailers; OkHttp's HTTP/1.1 reader recognises only the exact `Transfer-Encoding: chunked` (`Http1ExchangeCodec.isChunked`), so a server honouring `TE: gzip` would send a coding the transport cannot frame or decode. HTTP/2 allows only `trailers` | F1; RFC 9110 §10.1.4; RFC 9113 §8.2.2 |
| `Trailer` | Announces a trailer section; the transport only sends known-length bodies and never trailers. Sent on both protocols, so it is a claim about framing the transport cannot back. The weakest entry: no measured failure, kept for the framing family and for parity with Fetch and Go | F1; RFC 9110 §6.6.2 |
| `Upgrade` | Protocol switch; with `Connection: Upgrade` OkHttp hands back a hijacked socket and an unreadable body, which the transport's body reader would hit as an `IllegalStateException` | F1; RFC 9110 §7.8; RFC 9113 §8.2.2 |
| `Expect` | Exchange flow: OkHttp withholds the body until a `100` or a final response, and stalls to the read timeout against a server that sends neither | F1 |
| `Range` | Decoding: with it OkHttp adds no `Accept-Encoding`, and [RFC 9110 §12.5.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3) says "If no Accept-Encoding header field is in the request, any content coding is considered acceptable by the user agent" | F1 |

Tier: reasoned from measured behaviour and the RFCs.

### F3. Names deliberately left out

- **`Content-Encoding`** describes the request body the caller built. The transport never compresses a body, a caller
  may send bytes it already compressed, and the header changes neither framing nor response decoding (measured).
  Refusing it would also block a legitimate upload.
- **`Via`, `Origin`, `Referer`, `Date`, `Accept-Charset`, `Cookie2`, `DNT`**: sent verbatim on both protocols with no
  effect on the exchange (measured for all but `Cookie2` and `DNT`, which take the same path as any unlisted name).
- **The `Proxy-` and `Sec-` prefixes.** `Proxy-Authorization` is a credential name (the policy's business).
  `Proxy-Connection` is in the set. Other `Proxy-` names are inert toward an origin. `Sec-` exists so browser scripts
  cannot forge browser-minted fields; a library has no such page to protect. Measured sent verbatim, lower-cased on
  HTTP/2.
- **`Content-Type`**: the caller's. `HttpRequest` already refuses it beside a body content type, and a body's own type
  is sent as a plain header.
- **Method-override names** (`X-HTTP-Method`, `X-HTTP-Method-Override`, `X-Method-Override`). Fetch refuses them only when
  the value names `CONNECT`, `TRACE` or `TRACK`. They are interpreted by an application behind the server, not by the
  transport or the protocol, and the list of such names is open-ended.
- **`HTTP2-Settings`** belongs to the HTTP/2 upgrade from HTTP/1.1 that RFC 9113 dropped; it is inert without
  `Upgrade` and `Connection`, which are refused.

### F4. Comparable clients (common practice)

See the next section for each client's code. The pattern across them: every mature client that lets a caller set
headers keeps `Content-Length`, `Transfer-Encoding`, `Host` and `Connection` out of the caller's hands, and none of
them keeps the whole Fetch list outside a browser.

- Fetch refuses by ignoring (no error); the JDK and Ktor throw; Go ignores on HTTP/1.1 and errors or drops on HTTP/2;
  Apache throws on a duplicate framing header and lets the caller win on `Host` and `Connection`; OkHttp lets the
  caller win.
- A refusal that throws, with the name in the message, is the form the requirements ask for. A silent ignore is the
  form that the module's existing types avoid (they refuse a bad value rather than repair it).

### F5. HTTP/1.1 and HTTP/2 must be judged together (documented and measured)

Four of the added names (`Keep-Alive`, `Proxy-Connection`, `Transfer-Encoding`, `Upgrade`) reach an HTTP/1.1 server and
are dropped before an HTTP/2 server, so a request would behave differently by negotiated protocol if they were
allowed. `Host` means a different thing on each (a header on HTTP/1.1, the `:authority` on HTTP/2). `Content-Length` on a
body-less request is forwarded on both and is a malformed message on HTTP/2. A single name-based rule gives one
behaviour.

### F6. Where the check runs (reasoned; the checked inputs are in the repository)

`UrlGuard.checkRequest(request)` judges the URL (`checkHop(url, null)`) and, for `Untrusted`, any entry with
`isSensitive`. It returns a `GuardRefusal`, which the transport maps to `RefusedByPolicy`. The controlled-name check reads
only the header names: it does not depend on the policy, and it gives the same result under `Trusted` and `Untrusted`.
Running it first means a test written against either policy sees the same failure, and a caller sees the cheaper,
policy-independent error before a policy one. Both run before the first `Call`, so no socket opens for either.

Redirect hops: each hop's `Request` is built from the validated request's headers (minus the stripped sensitive
ones), and the transport sets `User-Agent` itself on every hop. OkHttp derives `Host`, `Connection`,
`Accept-Encoding` and the body framing from the hop's URL and body. No name that failed or could fail the check is
introduced after hop 1. A caller `Host` that survived to a cross-origin hop would name the wrong host; refusing it up
front removes that case.

## How comparable projects do it

- **Fetch standard** (living standard; `fetch.bs` at commit `e9460d1b22ead1c6a72eeac37fb2ec0dc01aeb1d`).
  [Forbidden request-header](https://fetch.spec.whatwg.org/#forbidden-request-header): `Accept-Charset`,
  `Accept-Encoding`, `Access-Control-Request-Headers`, `Access-Control-Request-Method`, `Connection`,
  `Content-Length`, `Cookie`, `Cookie2`, `Date`, `DNT`, `Expect`, `Host`, `Keep-Alive`, `Origin`, `Referer`,
  `Set-Cookie`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade`, `Via`; any name starting `proxy-` or `sec-`; and the
  three method-override names when the value is a forbidden method. `Headers.append` returns without adding: a
  forbidden header is silently ignored. The stated purpose: "These are forbidden so the user agent remains in full
  control over them." `User-Agent` and `Range` are not forbidden there: the browser's script may set them.
- **JDK `java.net.http`** (JDK 25+36, `jdk.internal.net.http.common.Utils`). `DISALLOWED_HEADERS_SET` holds
  `connection`, `content-length`, `expect`, `host`, `upgrade`; the validator `VALIDATE_USER_HEADER` throws
  `IllegalArgumentException` with `restricted header name: "<name>"` when the request is built. A system property
  `jdk.httpclient.allowRestrictedHeaders` removes names from the set. The list is case-insensitive
  (`String.CASE_INSENSITIVE_ORDER`). It does not list `Transfer-Encoding`, `Keep-Alive`, `TE` or `Trailer`: the client
  frames from its own `BodyPublisher` and is the only writer of those.
- **Ktor 3.6.1** (`io.ktor.http.HttpHeaders.UnsafeHeadersList`). The list is `Transfer-Encoding` and `Upgrade` only.
  `HttpClientEngine`'s private `validateHeaders` throws `UnsafeHeaderException` (an `IllegalArgumentException`) with
  `Header(s) [...] are controlled by the engine and cannot be set explicitly` when the call executes. It leaves
  `Host`, `Connection`, `Content-Length` and `Accept-Encoding` to the engine. The check reads
  `it in HttpHeaders.UnsafeHeadersList`, a `List<String>` lookup, which is case-sensitive, while the sibling
  `HttpHeaders.isUnsafe` uses `ignoreCase = true` (read, not run; see Gaps). It echoes the offending names, which come
  from the request.
- **Go `net/http` 1.25.0.** `reqWriteExcludeHeader` skips `Host`, `User-Agent`, `Content-Length`, `Transfer-Encoding`
  and `Trailer` when writing a client request, and `Request.Header`'s documentation says "certain headers such as
  Content-Length and Connection are automatically written when needed and values in Header may be ignored". The
  `Request.Host` field, not `Header["Host"]`, sets the host. Its HTTP/2 path (`golang.org/x/net` v0.46.0,
  `httpcommon.checkConnHeaders`) returns an error for `Upgrade` other than empty or `chunked`, `Transfer-Encoding` other
  than empty or `chunked`, and `Connection` other than `close` or `keep-alive`, then drops `host`, `content-length`,
  `connection`, `proxy-connection`, `transfer-encoding`, `upgrade` and `keep-alive`. `Expect: 100-continue` is a header
  the client honours, bounded by `Transport.ExpectContinueTimeout` (1 s in `DefaultTransport`), after which it sends
  the body anyway, unlike OkHttp's wait to the read timeout.
- **Apache HttpComponents** (`httpcore5` `rel/v5.4`). `RequestContent` throws `ProtocolException` ("Transfer-encoding
  header already present", "Content-Length header already present") unless built with `overwrite = true`, in which case
  it removes them. `RequestTargetHost` and `RequestConnControl` add `Host` and `Connection` only when absent, so a
  caller wins on those two.
- **OkHttp 5.5.0 itself:** the caller wins for `Host`, `Connection`, `Accept-Encoding` and `User-Agent`, and for
  `Content-Length` and `Transfer-Encoding` unless a body exists (F1). It validates only names and values, never the set.
- **RFC 9113 §8.2.2:** "An endpoint MUST NOT generate an HTTP/2 message containing connection-specific header fields.
  This includes the Connection header field and those listed as having connection-specific semantics in Section 7.6.1
  of [HTTP] (that is, Proxy-Connection, Keep-Alive, Transfer-Encoding, and Upgrade)." `TE` is the one exception, limited
  to `trailers`.
- **RFC 9110 §7.6.1** lists the fields an intermediary should remove before forwarding: `Proxy-Connection`,
  `Keep-Alive`, `TE`, `Transfer-Encoding`, `Upgrade`.

The union of the JDK's five, RFC 9113's connection-specific five plus `TE`, and the body-framing pair is the core
every client agrees on; the proposed thirteen are that core plus `User-Agent` (the transport's identity), `Trailer`, and
the two OkHttp-specific decode and flow switches (`Range`, `Expect`).

## Pitfalls & regrets

- **Silent drops differ by protocol.** OkHttp's HTTP/2 skip list hides `Transfer-Encoding`, `Upgrade`, `Keep-Alive`,
  `Proxy-Connection` and `Connection` that its HTTP/1.1 writer sends. A test over cleartext HTTP/1.1 alone proves
  nothing for HTTP/2, so the Level 3 test should run each name over both.
- **`Connection: close` on HTTP/2** leaves the wire clean but still stops the pooled connection being reused
  (measured: the next call opened a second connection). A caller could turn the transport's pool off by accident.
- **A body changes OkHttp's behaviour.** A refusal that depended on "no body" would pass `POST` and fail `GET`. Refuse by
  name.
- **Echoing the caller's spelling.** The rule text uses the list's canonical spelling, so it is a constant per name and
  holds no request data; Ktor and the JDK both echo names from the request.
- **Case-sensitive list lookups.** Ktor's check appears to compare with `List.contains`. `HttpSyntax.equalsName` folds
  ASCII letters only, never `lowercase()` or `equals(ignoreCase = true)`, which fold U+212A to `k` and U+0131 to `i`
  (the module's rule). Names reaching the check are already tokens, so they are ASCII, but the rule keeps the
  comparison uniform.
- **Upgrade through `Connection: Upgrade`** leaves a hijacked socket in `Response.socket` and an unreadable body. If the
  name were ever admitted, the body reader and `close()` would have to close that socket.
- **Reading `Expect` as a feature.** Go bounds the wait (1 s); OkHttp waits to the read timeout. A transport that
  admitted `Expect` would need its own bound.
- **Treating `Trailer` as harmless.** It is harmless in every probe, and is the entry a future reviewer will most want to
  drop. It stays because it is part of the framing family and costs a caller nothing.

## Recommendation for this repository

**The set.** A private list of thirteen canonical names in `jvmMain` (or in `commonMain` next to `HttpSyntax` if the
published fake should refuse the same names; this question does not settle that):

`User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length`, `Transfer-Encoding`, `Keep-Alive`,
`Proxy-Connection`, `TE`, `Trailer`, `Upgrade`, `Expect`, `Range`.

Shape, following `HttpSyntax.isCredentialName`:

```kotlin
private val CONTROLLED: List<String> = listOf(/* the thirteen */)

/** The first request entry whose name is controlled, as its rule text; null when none is. */
fun controlledHeaderRule(headers: HttpHeaders): String? =
    headers.entries().firstNotNullOfOrNull { entry ->
        CONTROLLED.firstOrNull { HttpSyntax.equalsName(it, entry.name) }
    }?.let { "header $it is controlled by the transport" }
```

- The name in the rule is the list's spelling (`TE`, `Keep-Alive`), not the caller's; the text holds no value. The failure
  reads `The request is invalid: header Host is controlled by the transport`.
- When several controlled names are present, the first in the request's header order is reported.
- Compare with `HttpSyntax.equalsName`; no regex, no `lowercase()`. Thirteen names with a linear scan per entry is
  cheaper than building a set, and a header block is short.

**Where it runs.** In `execute`: the `Closed` check, then this check, then `UrlGuard.checkRequest`, then hop 1's
`Call`. Nothing before it opens a socket or resolves a name. It is not repeated per hop.

**Why this and not the alternatives.**

- *The six names alone* leaves `Upgrade`, `Expect`, `TE` and `Range`, each of which changes the protocol, the flow or
  the decoding on HTTP/1.1 (measured), and `Keep-Alive`, `Proxy-Connection` and `Trailer`, which the two protocols
  treat differently.
- *The Fetch list* refuses `Origin`, `Referer`, `Date`, `Via`, `Accept-Charset`, `Proxy-` and `Sec-` names that a
  non-browser caller may legitimately set and that change nothing on the exchange; it protects a browser page, not a
  library.
- *The JDK list or Ktor's* are too short for the stated requirements: the JDK's list allows `Transfer-Encoding`, `TE`
  and `Keep-Alive`, and Ktor's allows `Host`, `Connection` and `Content-Length`.
- *Rejecting `Range` is the only judgement call.* Allowing it later is additive (a caller who set `Range` succeeded
  nowhere before); forbidding it later is breaking. If ranged downloads are wanted, the additive route is to admit
  `Range` and have the transport send `Accept-Encoding: identity` on that request, which OkHttp then leaves alone.

**Tests this implies** (Level 3 against `mockwebserver3`, both protocols): for each of the thirteen names and a mixed-case
spelling, `execute` returns `InvalidRequest` with the constant rule text, and the server's `requestCount` is 0. The
`Untrusted` loopback transport shows the same result as `Trusted`. A request with one of the thirteen and a sensitive
header under `Untrusted` returns `InvalidRequest`, not `RefusedByPolicy`. A request following a cross-origin redirect
shows the second server the transport's `User-Agent` and no caller-set `Host`. Level 2: the predicate over the names and
over near-misses (`Hosts`, `X-Host`, `Content-Encoding`, `Via`).

## Load-bearing claims

1. **OkHttp adds `Accept-Encoding: gzip` only when the caller sets neither `Accept-Encoding` nor `Range`, and decodes only then.**
   Quote: `if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {` and
   `// If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing`.
   [`BridgeInterceptor.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/BridgeInterceptor.kt), OkHttp 5.5.0.
2. **OkHttp supplies `Host`, `Connection` and `User-Agent` only when absent, so a caller's value wins.**
   Quote: `if (userRequest.header("Host") == null) {` / `if (userRequest.header("Connection") == null) {` /
   `if (userRequest.header("User-Agent") == null) {`. Same file and version.
3. **With a body, OkHttp overrides `Content-Length` and removes `Transfer-Encoding`; without one it does not touch them.**
   Quote: `requestBuilder.header("Content-Length", contentLength.toString())` followed by
   `requestBuilder.removeHeader("Transfer-Encoding")`, both inside `if (body != null) {`. Same file and version.
4. **On HTTP/2, `:authority` comes from the `Host` header, and a fixed list of names is dropped (including `transfer-encoding`,
   `upgrade`, `keep-alive`, `proxy-connection`, `connection`, `host`; `te` unless exactly `trailers`).**
   Quote: `val host = request.header("Host")` ... `result.add(Header(TARGET_AUTHORITY, host)) // Optional.` and
   `if (name !in HTTP_2_SKIPPED_REQUEST_HEADERS ||` `name == TE &&` `headers.value(i) == "trailers"`.
   [`Http2ExchangeCodec.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http2/Http2ExchangeCodec.kt), OkHttp 5.5.0.
5. **OkHttp's HTTP/1.1 writer sends every header name and value as given.**
   Quote: `.writeUtf8(headers.name(i))` `.writeUtf8(": ")` `.writeUtf8(headers.value(i))`.
   [`Http1ExchangeCodec.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http1/Http1ExchangeCodec.kt), OkHttp 5.5.0.
6. **OkHttp acts on a caller's `Expect: 100-continue`, `Connection: upgrade` and `Connection: close`.**
   Quote: `if ("100-continue".equals(request.header("Expect"), ignoreCase = true)) {`,
   `val isUpgradeRequest = "upgrade".equals(request.header("Connection"), ignoreCase = true)`,
   `if ("close".equals(response.request.header("Connection"), ignoreCase = true) ||`.
   [`CallServerInterceptor.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/CallServerInterceptor.kt), OkHttp 5.5.0.
7. **OkHttp's HTTP/1.1 body framing recognises only the exact `Transfer-Encoding: chunked`.**
   Quote: `"chunked".equals(header("Transfer-Encoding"), ignoreCase = true)` in `Response.isChunked`.
   `Http1ExchangeCodec.kt` (link in 5), OkHttp 5.5.0.
8. **Fetch forbids these request-header names and ignores them silently.**
   Quote: "If <var>name</var> when <a>byte-lowercased</a> <a for="byte sequence">starts with</a> `<code>proxy-</code>` or `<code>sec-</code>`, then return true." and
   "If <a for=Headers>validating</a> (<var>name</var>, <var>value</var>) for <var>headers</var> returns false, then return."
   and "These are forbidden so the user agent remains in full control over them."
   [`fetch.bs`](https://github.com/whatwg/fetch/blob/e9460d1b22ead1c6a72eeac37fb2ec0dc01aeb1d/fetch.bs) at that commit; rendered at <https://fetch.spec.whatwg.org/#forbidden-request-header>. Read 2026-11-01.
9. **The JDK client refuses `connection`, `content-length`, `expect`, `host`, `upgrade` with an `IllegalArgumentException` at build.**
   Quote: `headers.addAll(Set.of("connection", "content-length", "expect", "host", "upgrade"));` and
   `throw newIAE("restricted header name: \"%s\"", name);`.
   [`Utils.java`](https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/java.net.http/share/classes/jdk/internal/net/http/common/Utils.java), JDK 25+36.
10. **Ktor's unsafe-header list is `Transfer-Encoding` and `Upgrade`, and the client throws `UnsafeHeaderException`.**
    Quote: `private val UnsafeHeadersArray: Array<String> = arrayOf(TransferEncoding, Upgrade)` and
    `"Header(s) $header are controlled by the engine and " +` `"cannot be set explicitly"`.
    [`HttpHeaders.kt`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-http/common/src/io/ktor/http/HttpHeaders.kt) and
    [`HttpClientEngine.kt`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-core/common/src/io/ktor/client/engine/HttpClientEngine.kt), Ktor 3.6.1.
11. **Go's client ignores `Host`, `User-Agent`, `Content-Length`, `Transfer-Encoding` and `Trailer` from `Request.Header`.**
    Quote: `// Headers that Request.Write handles itself and should be skipped.` followed by
    `"Host": true, // not in Header map anyway`, `"User-Agent": true,`, `"Content-Length": true,`,
    `"Transfer-Encoding": true,`, `"Trailer": true,`; and "values in Header may be ignored".
    [`request.go`](https://github.com/golang/go/blob/go1.25.0/src/net/http/request.go), Go 1.25.0.
12. **Go's HTTP/2 client errors on an invalid `Connection`, `Upgrade` or `Transfer-Encoding` and drops the connection-specific names.**
    Quote: `return fmt.Errorf("invalid Connection request header: %q", vv)` and
    `// Per 8.1.2.2 Connection-Specific Header` `// Fields, don't send connection-specific`.
    [`internal/httpcommon/request.go`](https://github.com/golang/net/blob/v0.46.0/internal/httpcommon/request.go), golang.org/x/net v0.46.0.
13. **Apache's `RequestContent` throws if `Content-Length` or `Transfer-Encoding` is already present; `RequestTargetHost` adds `Host` only if absent.**
    Quote: `throw new ProtocolException("Content-Length header already present");` and
    `if (!request.containsHeader(HttpHeaders.HOST)) {`.
    [`RequestContent.java`](https://github.com/apache/httpcomponents-core/blob/rel/v5.4/httpcore5/src/main/java/org/apache/hc/core5/http/protocol/RequestContent.java) and
    [`RequestTargetHost.java`](https://github.com/apache/httpcomponents-core/blob/rel/v5.4/httpcore5/src/main/java/org/apache/hc/core5/http/protocol/RequestTargetHost.java), `rel/v5.4`.
14. **HTTP/2 messages must not carry connection-specific fields; `TE` only with `trailers`.**
    Quote: "An endpoint MUST NOT generate an HTTP/2 message containing connection-specific header fields. This includes the
    Connection header field and those listed as having connection-specific semantics in Section 7.6.1 of [HTTP] (that is,
    Proxy-Connection, Keep-Alive, Transfer-Encoding, and Upgrade)." and "when it is, it MUST NOT contain any value other than "trailers"."
    [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2), June 2022.
15. **An HTTP/2 request whose `content-length` differs from its DATA total is malformed.**
    Quote: "A request or response is also malformed if the value of a content-length header field does not equal the sum of the DATA frame payload lengths that form the content".
    [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1), June 2022.
16. **Intermediaries remove `Proxy-Connection`, `Keep-Alive`, `TE`, `Transfer-Encoding`, `Upgrade`.**
    Quote: "intermediaries SHOULD remove or replace fields that are known to require removal before forwarding, whether or not they appear as a connection-option".
    [RFC 9110 §7.6.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.6.1), June 2022.
17. **`Host` is an application-level routing mechanism.**
    Quote: "Since the host and port information acts as an application-level routing mechanism, it is a frequent target for malware seeking to poison a shared cache or redirect a request to an unintended server."
    [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2), June 2022.
18. **Without `Accept-Encoding` a server may use any content coding.**
    Quote: "If no Accept-Encoding header field is in the request, any content coding is considered acceptable by the user agent."
    [RFC 9110 §12.5.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3), June 2022.
19. **A sender must not send `Content-Length` beside `Transfer-Encoding`.**
    Quote: "A sender MUST NOT send a Content-Length header field in any message that contains a Transfer-Encoding header field."
    [RFC 9112 §6.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.2), June 2022.
20. **`Trailer` lists the fields a sender expects to send as trailers.**
    Quote: "The "Trailer" header field provides a list of field names that the sender anticipates sending as trailer fields within that message."
    [RFC 9110 §6.6.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-6.6.2), June 2022.

## Gaps

- **HTTP/2 was run only against `mockwebserver3`**, which does not validate request framing: a `GET` with
  `content-length: 5` and no DATA was accepted and answered. How production servers react (reset, 400, ignore) is not
  measured here; the claim that it is malformed rests on RFC 9113 §8.1.1.
- **Pool desync on HTTP/1.1 was not shown end to end.** A server that honours a body-less `GET`'s `Content-Length: 5`
  made the client time out (measured). A server that ignores it answered normally and parsed the next request intact
  (measured). The in-between case, a server that consumes five bytes of the next pooled request, follows from RFC 9112
  framing but was not run.
- **Ktor's case sensitivity** is from reading `validateHeaders` (`it in HttpHeaders.UnsafeHeadersList`), not from running
  it. If it matters to a decision, run a request with `transfer-encoding` on Ktor 3.6.1.
- **`Trailer` has no measured failure.** Its inclusion is a judgement from the framing family and from Fetch and Go
  excluding it too.
- **`Range` is a judgement:** the measured fact is that no `Accept-Encoding` is sent and an encoding server's reply
  arrives undecoded. Whether any real API in this stack answers a ranged request with a coding without being asked is
  unknown.
- **`Expect` against a real server that ignores it** was measured as a stall to the read timeout on a raw socket
  server; TLS and HTTP/1.1 together were not run.
- Only Windows with JDK 23 was used for the probes. The OkHttp code paths read are platform independent.
- Whether the published fake should refuse the same names is not settled here; it bears on where the list lives.
- **`TE`'s decoding risk is reasoned, not run.** The probe shows `TE: gzip, trailers` reaching an HTTP/1.1 server
  verbatim. That a server answering with `Transfer-Encoding: gzip, chunked` would defeat OkHttp's exact-match
  `isChunked` check follows from the source and was not exercised.
