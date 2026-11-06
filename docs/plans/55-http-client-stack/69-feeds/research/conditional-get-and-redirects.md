# Conditional GET and redirect rules for the feed fetcher

Researched 2026-11-06. Sources are pinned (tag or commit) where code was read; network measurements were taken on that date from one vantage point.

## Question

Which rules should a stateless feed fetcher follow when it sends a conditional `GET`, interprets the status, stores the validators it hands back, and reports a permanent move? Concretely: how `FeedValidators(etag, lastModified)` is sanitised (bounds, characters), what each status becomes, how `permanentlyMovedTo` is computed, and what `Accept` value the request carries. Requirements: the caller owns all state; a `304` is a success value; the stored validators are replaced after every response; the transport follows `GET` redirects itself, reports each hop as the URL requested plus the `3xx` status it answered, and sends one request per hop.

## Bottom line

- **Validators.** Send `If-None-Match` and `If-Modified-Since` together, each verbatim, whenever each is known (RFC 9110 makes the server ignore `If-Modified-Since` when `If-None-Match` is present, so sending both costs nothing). A stored value is accepted only if, after trimming SP and HTAB at the ends, it is non-empty, at most **256 characters** (ETag) or **64 characters** (Last-Modified), made only of **U+0020..U+007E**, and, for the ETag, not `*`. Anything else becomes `null`. Never parse, quote, unquote or reformat: measured, an unquoted ETag echoed verbatim gets a `304` and the same tag wrapped in quotes gets a `200`.
- **Character rule is a correctness rule, not a nicety.** RFC 9110 allows `obs-text` (0x80-0xFF) in an ETag, but the stack's `HttpRequest` throws on any header value outside HTAB and U+0020..U+007E, and OkHttp 5.5.0 (measured) refuses the same. A stored non-ASCII tag would make every later fetch of that feed fail. Dropping it costs one unconditional download.
- **Update rule.** A `200` replaces both validators with the ones it carries (none carried gives `FeedValidators.None`). A `304` replaces each validator it carries and keeps the ones it does not. A validator the `304` carries but that fails the rules above is set to `null`, not kept: the server has already replaced it, so the old value is known stale.
- **Statuses.** Parse `200` and `203`. `304` to a request that carried a validator is `NotModified`; `304` to an unconditional request is `UpstreamError(304)`. Every other `2xx` (`201`, `202`, `204`, `205`, `206`, `207`, `226`) is `UpstreamError`: none is a complete representation of the target for a plain `GET`, and `226` can only answer a request that sent `A-IM`, which the fetcher never does. This departs from "any `2xx` is parsed" and is the one place these findings change the stated design.
- **`permanentlyMovedTo`.** Compute it as the target of the last hop in the leading run of `301`/`308` hops. This matches SimplePie (hence FreshRSS) and is the stricter reading of RFC 9110 (a `302` at the head keeps the original URL canonical). Report it only with a success (`Fetched` or `NotModified`), never when the target equals the requested URL, and do not filter by host or scheme in the fetcher: the caller decides whether to adopt it, and the documentation should say that adopting a move seen over cleartext `http` is how a subscription gets hijacked.
- **`Accept`.** `application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1`. It is SimplePie's value minus `text/html` and the two `unknown` types; it deliberately omits `application/feed+json` because the parser port reads XML. Expose it as a builder default so a consumer who swaps the parser can change it.

## Findings

### F1. Send both validators, verbatim (consensus; measured)

- RFC 9110 §13.1.2: "A recipient MUST use the weak comparison function when comparing entity tags for If-None-Match". §13.1.3: "A recipient MUST ignore If-Modified-Since if the request contains an If-None-Match header field; ... the two are only combined for the sake of interoperating with older intermediaries that might not implement If-None-Match." So the pair is the interoperable form. RFC 9110, June 2022 (obsoletes RFC 7232).
- Practice agrees: feedparser 6.0.14, Miniflux 2.3.5, Feedbin, SimplePie 1.9.0 and NetNewsWire 7.2.1 send both; tt-rss sends only `If-Modified-Since` and never stores an ETag.
- Measured on `www.bleepingcomputer.com/feed/` (2026-11-06): a matching `If-None-Match` with an ancient `If-Modified-Since` gave `304`; a wrong `If-None-Match` with a current `If-Modified-Since` gave `200`. The server obeyed the precedence rule.
- A weak tag (`W/"x"`) and a field holding several tags (`"a", "b"`) are valid `If-None-Match` values (`If-None-Match = "*" / #entity-tag`), so a verbatim echo is correct. A stored `ETag` is one entity-tag; several tags can only come from a non-conforming response (for example duplicate header lines joined by a proxy), and echoing that list still matches if any member matches. Documented; the "list" case is reasoned.
- Never echo `*`. RFC 9110 §13.1.2: "If the field value is "*", the condition is false if the origin server has a current representation for the target resource", so a stored `*` returns `304` on every poll. Measured: `If-None-Match: *` gave `304` on the same feed.

### F2. What a `304` carries and how stored validators update (documented; measured)

- RFC 9110 §15.4.5: "The server generating a 304 response MUST generate any of the following header fields that would have been sent in a 200 (OK) response to the same request: * Content-Location, Date, ETag, and Vary". `Last-Modified` is optional: "Last-Modified might be useful if the response does not have an ETag field". A `304` has no content.
- RFC 9111 §3.2: "the cache MUST add each header field in the provided response to the stored response, replacing field values that are already present". Fields the `304` does not carry stay. §4.3.4 applies this to a `304` (a cache "MUST update its header fields with the header fields provided in the 304").
- That is the per-field rule: the `304`'s value where present, the sent value otherwise. Miniflux 2.3.5 implements the `Last-Modified` half and cites the same sections (its comment: "Last-Modified may be updated even if ETag is not. In this case, per RFC9111 sections 3.2 and 4.3.4, the stored response must be updated."). It does not adopt an ETag from a `304`.
- Measured shapes of real `304`s (45 feeds answering `200`; 30 of the 34 that sent a validator answered `304` to the echoed pair, 4 answered `200`):
  - most carry the `ETag` they matched and omit `Last-Modified` (xkcd, Engadget, JetBrains, Spring's blog); some carry both (Planet Python, Slashdot, CSS-Tricks);
  - `www.bleepingcomputer.com`'s `200` carried only `Last-Modified`; its `304` to an `If-Modified-Since`-only request carried an **unquoted** `ETag: 92f89e9513a8b2bfbb1d2361f41f8699` the `200` never had.

### F3. A `304` with a different ETag than the one sent (documented; measured; reasoned)

- It happens without any bug. A request that sent only `If-Modified-Since` has no tag to match, so the `304`'s ETag is simply the server's current one (bleepingcomputer, above). A request that sent `If-None-Match` is compared weakly, so the `304` may carry the same opaque tag with a different strength: Cloudflare's documentation says "However, in some situations Cloudflare will convert strong ETags to weak ETags", depending on the visitor's `Accept-Encoding`, and `daringfireball.net` (behind Cloudflare) sent `W/"c3d7...-gzip"`.
- A genuinely different opaque tag after a matching conditional is a server-farm inconsistency. RFC 9111 §4.3.4's first rule ("If none of the initial set contains at least one of the same strong validators, then the cache MUST NOT use the new response to update any stored responses") exists for a cache choosing among several stored variants. A single-slot client has no choice to make.
- Recommendation: store what the `304` carries. The `304` asserts the stored representation is current, and its ETag is the server's name for it. If it is wrong the cost is one extra full download that then converges; keeping the sent tag would repeat that mismatch on every poll while the server keeps naming the representation differently.

### F4. A `200` without validators; repeated headers (documented; reasoned)

- A `200` is a new representation, so its validators replace both stored ones; a `200` carrying none returns `FeedValidators.None`. Keeping old ones would send a validator that names a representation the server no longer serves (the stale-validator failure in the parent findings, `feed-parsing.md` F4).
- Measured: 11 of 45 feeds sent neither header (BBC, NYT, Hacker News, Google, Cloudflare blog, Netflix, NPR, Le Monde, Spiegel, Wired, go.dev). Each is an unconditional download on every poll; nothing the fetcher can do.
- `ETag` and `Last-Modified` are singleton fields. When a response repeats one, treat it as absent (reasoned: a wrong value costs at most one `200`, but a wrong value that matches could return a false `304`). `HttpHeaders.get` returns the last value, so use `getAll` and require exactly one.

### F5. `2xx` other than `200` (documented)

- `204`: RFC 9110 §15.3.5, "A 204 response is terminated by the end of the header section; it cannot contain content or trailers." There is nothing to parse; parsing an empty body would report `NotAFeed`, which blames the document for a server's non-answer.
- `205` carries no content either. `206`: "The 206 (Partial Content) status code indicates that the server is successfully fulfilling a range request for the target resource by transferring one or more parts of the selected representation", and a server answers `206` only to a request that carried `Range` (RFC 9110 §14.2), so a `206` to a plain `GET` is a server fault; its body is a fragment, and returning `Fetched` with the full-representation validators would tell the caller its entry list is complete.
- `226` (RFC 3229): "The request MUST have included an A-IM header field listing at least one instance-manipulation." feedparser sends `A-IM: feed` and Rome's old fetcher can; this fetcher sends neither, so a `226` is non-conforming, and a delta (new items only) read as a full feed would make the caller treat missing items as removed.
- `203` (non-authoritative; a transforming proxy modified a `200`) has a full body and is parsed. `201`, `202`, `207` and the rest do not describe a `GET` representation.
- Real readers differ. SimplePie 1.9.0 accepts `200` and `207..299` only (rejects `201..206`). Miniflux 2.3.5 accepts any status below `400` except `304` that has a non-empty body. Feedbin's Feedkit accepts any `2xx` or `304`. feedparser parses whatever arrives.

### F6. The character rule, and what refuses what (documented; code reading; measured)

- Grammar: RFC 9110 §8.8.3, `etagc = %x21 / %x23-7E / obs-text`, so `obs-text` (0x80-0xFF) is legal in an ETag and RFC 9110 §5.5 says "A recipient SHOULD treat other allowed octets in field content (i.e., obs-text) as opaque data." CR, LF and NUL "are invalid and dangerous".
- The stack: `HttpHeaders.Builder.add` refuses CR, LF and NUL only; `HttpRequest`'s constructor then requires every value to be sendable ("HTAB or U+0020..U+007E", `HttpSyntax.isSendableValue`) and throws `IllegalArgumentException` otherwise. OkHttp 5.5.0's `headersCheckValue` is the same rule: `require(c == '\t' || c in ' '..'~')`. Measured on the JDK 23.0.2 with `okhttp-jvm` 5.5.0: `"café"`, `"xÿ"`, DEL, `\u0001` and a CJK character each threw `IllegalArgumentException: Unexpected char ...`; a tab inside a value and the empty string were accepted.
- OkHttp decodes response header lines as UTF-8 (`HeadersReader` calls `source.readUtf8LineStrict`), so a Latin-1 byte in a server's ETag arrives as U+FFFD, which is also outside the sendable set.
- Conclusion: a value with any character outside U+0020..U+007E must not be stored. HTAB is sendable but cannot occur in an entity-tag or an HTTP-date, so reject it too. Do not enforce the entity-tag grammar (quotes, `W/`): measured, `www.bleepingcomputer.com` emits an unquoted tag, echoes it back successfully (`304`), and answers `200` to the quoted form.

### F7. Bounds seen in practice (measured; documented)

- **Measured survey**, 48 feed URLs, 2026-11-06, one vantage (45 answered `200`): ETag lengths 14, 16, 17, 20, 20, 21, 27, 34 (eleven times, a quoted MD5), 36, 36, 38, 38, 41, 66 (LWN: a quoted SHA-256); 24 feeds sent an ETag, 5 of them weak. Last-Modified lengths were 29 (IMF-fixdate) in all but one feed, which sent 28 characters (`Fri, 6 Nov 2026 04:25:27 GMT`, a single-digit day with no zero pad; Ars Technica, served by Google's ESF). 26 feeds sent `Last-Modified`.
- **Grammar ceilings.** An IMF-fixdate is 29 characters, asctime 24, and the longest RFC 850 form (`Wednesday, 09-Nov-99 23:12:40 GMT`) 33, so any real date fits well under 64.
- **Request-field ceilings.** nginx: "A request header field cannot exceed the size of one buffer as well, or the 400 (Bad Request) error is returned to the client", with `large_client_header_buffers 4 8k`. Apache 2.4: `LimitRequestFieldSize` default 8190 bytes. An unbounded stored tag over those limits would turn every poll of that feed into a `400`, permanently, until the caller cleared it.
- **Library limits.** Only tt-rss bounds a stored validator, by truncating (`substr(UrlHelper::$fetch_last_modified, 0, 245)`, a column width). Truncating is wrong for a validator: a cut date is not a valid HTTP-date, which servers ignore (RFC 9110 §13.1.3 "MUST ignore the If-Modified-Since header field if the received field value is not a valid HTTP-date"), and a cut tag matches nothing. Drop, never truncate.
- **Recommended**: ETag 256, Last-Modified 64. Both are about four times the largest value seen and over thirty times below the smallest common field limit. They are policy numbers; any bound between roughly 128 and 1,024 satisfies the evidence. Reasoned from the survey and the documented limits.

### F8. Never reformat `Last-Modified` (documented; measured)

- RFC 9110 §13.1.3: using the received `Last-Modified` as the `If-Modified-Since` value "is most interoperable for cases where clocks are poorly synchronized or when the server has chosen to only honor exact timestamp matches".
- Measured: one of 26 real servers sends a non-IMF date (`Fri, 6 Nov 2026 ...`, Ars Technica). A client that parses and reprints it (Rome's old fetcher stores a `Long` and calls `setIfModifiedSince`; feedparser parses a string `modified` and rebuilds an RFC 1123 string) sends bytes the server never issued. Miniflux, NetNewsWire, SimplePie, tt-rss and Feedbin echo the string.
- NetNewsWire adds one guard worth knowing: `// Bug seen in the wild: lastModified with last possible 32-bit date, which is in 2038. Ignore those.` A stored future date makes a server that compares `Last-Modified <= If-Modified-Since` answer `304` forever. RFC 9110 §8.8.2.1 forbids a future `Last-Modified` from a server with a clock, but a 2025 write-up measured Blogger answering `304` to `If-Modified-Since` in 2035 (single source). A future-date guard needs the response's `Date`; the fetcher has it but the value type does not. Left to the caller (Gaps).

### F9. Redirect statuses (documented)

- RFC 9110 §15.4.2 (`301`) and §15.4.9 (`308`): "the target resource has been assigned a new permanent URI and any future references to this resource ought to use one of the enclosed URIs." §15.4.3 (`302`): "Since the redirection might be altered on occasion, the client ought to continue to use the target URI for future requests." `307` is the temporary, method-preserving form ("the user agent MUST NOT change the request method").
- History: §15.4 note, "In HTTP/1.0, the status codes 301 (Moved Permanently) and 302 (Found) were originally defined as method-preserving ... Prevailing practice eventually converged on changing the method to GET. 307 ... and 308 ... were later added to unambiguously indicate method-preserving redirects". For a `GET` fetch the method question is moot; the permanent/temporary axis is the only one that matters, so `301` and `308` are permanent and `302`, `303`, `307` are not. `303` ("See Other") says the target is "not considered equivalent to the target URI", so it is the weakest candidate for a subscription move.
- Caution built into the RFC: for `301` and `308`, the link-editing suggestion "is usually ignored unless the user agent is actively editing references (e.g., engaged in authoring content), the connection is secured, and the origin server is a trusted authority for the content being edited."
- `308` is young (RFC 7538, 2014) and some clients ignore it: feedparser 6.0.14's redirect handler defines `http_error_301/300/302/303/307` and no `308`. Measured on a re-creation of that handler (Python 3.14.7): a pure `308` chain reports `status=200`, so a feedparser consumer never sees the move. Feedkit (Feedbin), SimplePie and Liferea all count `308`.

### F10. The rule for a chain mixing permanent and temporary hops (code reading; measured)

The proposed rule, "the target of the last hop in the leading run of `301`/`308` hops", is implemented verbatim by SimplePie. In `SimplePie\File`, `$this->permanentUrlMutable = $this->permanentUrlMutable && ($this->status_code == 301 || $this->status_code == 308);` runs before each hop is followed, and the constructor assigns `permanent_url = $url` only `if ($this->permanentUrlMutable)`; the first non-permanent hop freezes it. FreshRSS 1.30.4 uses it on every refresh: `//The case of HTTP 301 Moved Permanently` then `$subscribe_url = $simplePie->subscribe_url(true)`, and rewrites the feed's URL when it differs. (SimplePie's own KDoc says "the location of the first redirection", which is imprecise for a chain of several `301`s; the code gives the last hop of the run.)

How the readers treat the mixed chains:

| Chain | This rule | SimplePie / FreshRSS | Feedbin (Feedkit) | Liferea | feedparser `status`/`href` |
|---|---|---|---|---|---|
| `A -301-> B` | `B` | `B` | `B` (after the stability count) | `B` | `301` / `B` |
| `A -301-> B -302-> C` | `B` | `B` | none (not all hops permanent) | `B` | `301` / `C` |
| `A -302-> B -301-> C` | none | none | none | `C` (any permanent hop sets it) | `302` / `C` (first hop's status wins) |
| `A -301-> B -301-> C` | `C` | `C` | `C` | `C` | `301` / `C` |

- Why the leading run, not the last permanent hop: the first `302` says `A` is still the address clients should use ("the client ought to continue to use the target URI for future requests"). A later `301` from `B` to `C` moves `B`, not `A`. Adopting `C` would also skip `A`'s ability to redirect elsewhere next week.
- Why not "all hops permanent" (Feedbin): a chain `A -301-> B -302-> C` is common (a feed moved to a host that load-balances with a `302`); refusing to report `B` forces the caller to keep requesting `A` forever. Feedbin compensates with a very strict stability test (below), so its "all" is a hijack defence as much as a rule.
- feedparser's `status` is the **first** hop's code, not the last: `urllib` nests each redirect inside the previous handler call, so the outermost handler assigns `result.status = code` last. Measured (re-created handler, Python 3.14.7): `[301, 302]` gives `status=301, href=final`; `[302, 301]` gives `status=302, href=final`. A consumer following feedparser's documented advice ("you should update your database or configuration file" with `d.href` when `status` is `301`) adopts the temporary final URL after `301` then `302`. That is the failure the leading-run rule avoids.
- Example for the rule's boundary: `A -308-> B -307-> C -301-> D` gives `B`.

### F11. When to report a move (documented; reasoned; common practice)

- **After a `304`.** Report it. The move belongs to the redirect response, which is a different message from the `304` that ended the chain, and "not modified" is the common answer for an unchanged feed behind a permanent redirect. A reader that skips the move on a `304` never learns it while the feed stays unchanged: SimplePie's revalidation path returns at `if ($this->status_code === 304) {` before it copies the permanent URL (`$this->permanent_url = $file->get_permanent_uri();` runs later), so FreshRSS keeps polling the old address, through its redirect, until the feed next changes. Code reading.
- **When the chain ends in an error** (`301` then `404`/`410`/`5xx`). Do not report it. There is no `FeedFetch` to carry it: `UpstreamFailure.UpstreamError` holds the status, headers and body only, with no URL or chain. This is also what the readers do: FreshRSS throws on a failed fetch before it reads `subscribe_url`, Feedbin records redirects only in `download_success`, and a half-finished migration or a hijacked redirect to a dead host would otherwise cost the subscriber the feed. A `410` after a `301` is therefore the caller's `410` handling, applied to the original URL.
- **Cross-host and `http` to `https`.** Report both; do not filter in the fetcher. `http -> https` on the same host is the most common permanent redirect and the one a caller most wants. Cross-host moves are legitimate migrations (measured: `https://asahi.com/...rdf` `301` to `https://www.asahi.com/...rdf`; `https://planet.python.org/rss20.xml` `301` to `http://planetpython.org/...` `301` to `https://planetpython.org/...`). Whether to adopt is a trust decision RFC 9110 §15.4.2 assigns to the user agent ("the connection is secured, and the origin server is a trusted authority"), so the fetcher reports the fact and the documentation names the guard: do not rewrite a stored subscription from a chain whose first request was cleartext `http`, because one injected `301` would redirect the subscription permanently.
- **Target equals the requested URL** (a canonicalising redirect that lands on the same string, or a loop to itself): report nothing.
- **Default transport policy interacts.** `allowHttpsToHttp` is `false` for both `Trusted` and `Untrusted` defaults, so the first hop of the Planet Python chain (`https` to `http`) is refused as `RefusedByPolicy`; that real feed is unreachable under defaults. A permanent move from `https` to `http` can therefore never be reported unless the consumer enables the setting.

### F12. What feed owners are told (documented)

- RSS Advisory Board, "How to Redirect an RSS Feed": a `301` or an XML `<redirect><newLocation>` document at the old address; "RSS readers should be prepared to honor either method. In case of conflict, we recommend that the XML redirect take precedence, since it's the most accessible strategy." The page describes no `302`, no conditional GET and no `308`.
- Apple Podcasts ("Change the RSS feed URL"): a `301` from the old feed plus `<itunes:new-feed-url>` in the new one; "maintain the <itunes:new-feed-url> tag and 301 redirect response for at least four weeks."
- So the HTTP signal is the `301`; the in-document signals are two further, separate mechanisms that this fetcher's `permanentlyMovedTo` does not see: the RSS Board's XML redirect (a `200` whose root is `<redirect>`, which the parser reports as `NotAFeed`) and `itunes:new-feed-url` (not in the feed model). Neither is in scope here.
- The RSS Board itself cites "current uncertainty about which RSS readers support which strategy". Reader behaviour below confirms it: only some readers rewrite a stored subscription.

### F13. `Accept` values in use (code reading; documented)

| Reader (pinned) | `Accept` sent |
|---|---|
| feedparser 6.0.14, `ACCEPT_HEADER` | `application/atom+xml,application/rdf+xml,application/rss+xml,application/x-netcdf,application/xml;q=0.9,text/xml;q=0.2,*/*;q=0.1` |
| SimplePie 1.9.0, `DEFAULT_HTTP_ACCEPT_HEADER` (bundled in FreshRSS) | `application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, text/html;q=0.7, unknown/unknown;q=0.1, application/unknown;q=0.1, */*;q=0.1` |
| Miniflux 2.3.5, `defaultAcceptHeader` | `application/xml,application/atom+xml,application/rss+xml,application/rdf+xml,application/feed+json,text/html,*/*;q=0.9` |
| Liferea (`364b2ea`), `src/net.c` | `application/atom+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7` |
| FreshRSS 1.30.4, its own fetch of an XML document | `application/xml,application/xhtml+xml,text/xml;q=0.9,*/*;q=0.8` |
| NetNewsWire 7.2.1, Feedbin (Feedkit `fbac4ff`), tt-rss (`c02520f`), gofeed 1.5.0, Rome's fetcher | none set by the library (the HTTP client's default) |
| Feedly | not documented (Gaps) |

- Every reader that sets one ends with `*/*` at a low weight, so a server that only enumerates a fixed list of types and answers `406` to anything else still serves the feed, while a server that negotiates picks a feed type first.
- The grammar bounds the weights: "A sender of qvalue MUST NOT generate more than three digits after the decimal point." All values above comply.
- The recommended value follows SimplePie's ordering and weights: the two feed types registered by their own specifications at `1`; RSS 1.0 (`application/rdf+xml`, rarer) at `0.9`; the two generic XML types at `0.8` (`text/xml` is not demoted to 0.2 as feedparser does, because many feeds are still served that way and RFC 7303 treats it as equivalent to `application/xml`); `*/*` at `0.1`. `text/html` is omitted: the fetcher does not autodiscover, and a server that answers an HTML page to this request has not offered a feed. `application/feed+json` is omitted because the parser port reads XML; advertising it would make a content-negotiating server send a document the default parser reports as `NotAFeed`. The unknown-type entries in SimplePie are workarounds for particular servers and do not belong in a default.

### F14. What the transport as built does with conditional headers across hops (code reading)

- The two headers are ordinary request headers: not on `TransportControlledHeaders` (twelve names, none of them conditional), not credential names (`HttpSyntax.isCredentialName`: `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`), and not flagged sensitive.
- `headersForHop` keeps the headers unchanged when scheme, host and effective port stay the same and, when they differ, filters out only entries where `isSensitive` is true. So `If-None-Match` and `If-Modified-Since` are carried to every hop, **including a cross-origin one**, and reach the final server as written. A `304` can end the chain.
- Followed statuses are `301 302 303 307 308` for `GET` and `HEAD`; a hop's `redirectChain` entry is `RedirectHop(requestedUrl, status)`; `HttpResponse.url` is the last URL requested. Hop `i`'s target is therefore hop `i+1`'s `url`, and the last hop's target is `response.url`. Any other `3xx` (`300` included), or a `3xx` with no single usable `Location`, is returned as the response; the cap is 3 hops for `Untrusted` (`TransportFailure.TooManyRedirects` beyond it), 5 for `Trusted`.
- This matches common practice: FreshRSS strips only `Cookie` and `Authorization` across origins (its fetch loop keeps the other request headers), CPython's `urllib` (which feedparser's handler extends) copies every request header except `content-length` and `content-type` to the redirected request, and RFC 9110 §15.4 asks a client to remove validators only when "added by the implementation's cache" and to "Consider removing header fields that were not automatically generated by the implementation ... where there are security implications; this includes but is not limited to Authorization and Cookie".
- The remaining hazard is a changed target. A validator that came from final server `B` is sent through `A` to whatever `A` now redirects to. If that is a different server `C`, a coincidental match (an nginx `"<mtime>-<size>"` tag, or a `Last-Modified` older than `C`'s date) returns a false `304` and the subscriber never sees `C`'s feed. The fetcher cannot detect it (it keeps no state), but the result carries `finalUrl`, so the caller can: when `finalUrl` differs from the one it stored with the validators, discard them and poll once unconditionally. Reasoned; no reader does this explicitly (Feedbin requests its stored redirect target directly, with the stored validators, which narrows the window but does not close it).

### F15. Shape of the sealed result in Kotlin (code reading; reasoned)

- The three shared properties (`validators`, `finalUrl`, `permanentlyMovedTo`) are what a caller reads on every response before it branches: store the validators, adopt or ignore the move, then use `feed` only on `Fetched`. Declaring them as abstract `val`s on the sealed parent lets that path run without a `when`; declaring them only on the leaves makes each branch repeat the same three statements, and forgetting one on one branch is exactly the bug NetNewsWire shipped (validators updated on one path only; `feed-parsing.md` F4).
- Repository precedent: `Lookup` is a `public sealed interface` whose leaves are final public classes with hand-written `equals`, `hashCode` and `toString` (no `data class`), documented as closed because "the closed pair is the contract". `FeedFetch` is the same kind of closed pair. Because the closed set is the contract, the exhaustive `when` over `Fetched` and `NotModified` needs no `else`. A third outcome would be a source break, so the two-leaf set should stay closed and any later case should be a field.
- Sketch of the parent, not an API decision: `public sealed interface FeedFetch { public val validators: FeedValidators; public val finalUrl: Url; public val permanentlyMovedTo: Url? }`, with `Fetched` adding `feed: Feed` and each leaf's `toString()` printing no URL query (the stack redacts through `Url.redacted()`).

## How comparable projects do it

| Project (pinned) | Conditional state | Sends | `304` handling | Permanent move |
|---|---|---|---|---|
| feedparser 6.0.14 (`14425f6`), `feedparser/http.py` | Caller keeps `etag`, `modified`; result exposes both, `status`, `href` | `If-None-Match`; `If-Modified-Since` rebuilt from a parsed date | result flagged, no entries | `status` is the first hop's code; no `308`; documented as "update your database" |
| SimplePie 1.9.0 (`76cccb1`), `src/File.php`, `src/SimplePie.php` | Cache record keeps the response headers | both, verbatim | keeps the old cache data, does not adopt new validators | `permanentUrlMutable` leading-run rule; accepts only `200`, `207..299` |
| FreshRSS 1.30.4 (`08f97bd`), `app/Models/Feed.php`, `app/Utils/httpUtil.php` | SimplePie's | via SimplePie | via SimplePie | rewrites the feed URL from `subscribe_url(true)` on each refresh; strips only `Cookie`/`Authorization` cross-origin |
| Miniflux 2.3.5 (`faecfa2`), `internal/reader/handler/handler.go`, `fetcher/response_handler.go` | Stored `EtagHeader`, `LastModifiedHeader`; both ignored when `Expires: 0` | both, verbatim | adopts a carried `Last-Modified`, not a carried `ETag`; a `200` with an unchanged ETag counts as not modified | sets the feed URL to the final URL only at subscription; never on refresh |
| NetNewsWire 7.2.1 (`ea24616`), `RSWeb/HTTPConditionalGetInfo.swift`, `DownloadSession.swift` | Persisted per feed | both, verbatim, minus any `Last-Modified` containing `2038` | stale-validator lesson (parent findings) | in-memory per-session cache of every `301/302/307/308` target; a deny list of captive-portal URL fragments; no persistent rewrite |
| Feedbin (`ddc9a87`) and Feedkit (`fbac4ff`), `redirect_cache.rb`, `request.rb` | Stored crawl data | both, verbatim | body checksum plus `304` decide "not modified" | adopts a target only if **every** hop is `301`/`308` and the same chain was seen about 576 times (`PERSIST_AFTER = 4 * 24 * 6`), then requests it directly |
| Tiny Tiny RSS (`c02520f`), `UrlHelper.php`, `RSSUtils.php` | Stored `last_modified` only, truncated to 245 | `If-Modified-Since` | no ETag | none (the stored feed URL is not rewritten) |
| Liferea (`364b2ea`), `src/net.c`, `src/subscription.c` | Per subscription | both | n/a | any `301`/`308` hop sets the subscription's source to that hop's target and shows a toast; a later `302` does not undo it |
| Rome fetcher 1.5.1 (`51b2407`, repository archived) | Stateful cache (`SyndFeedInfo`) | `If-None-Match`, `If-Modified-Since` rebuilt from a `Long` | does not adopt a new ETag from a `304` | records the final URL only; cannot tell permanent from temporary |
| gofeed 1.5.0, feed-rs | none (pure parsers) | none | n/a | n/a |

The split on moves is a trust axis. FreshRSS, SimplePie and Liferea adopt on first sight; Feedbin adopts only after days of identical observation; Miniflux, NetNewsWire and tt-rss do not rewrite a stored subscription at all.

## Pitfalls

- **Keeping the sent validator when a `304` replaces it** (Miniflux for ETags, SimplePie for both): the stale value is re-sent until a `200` fixes it, and a feed that always answers `304` never gets fixed.
- **Reformatting a date or normalising a tag**: breaks servers that match exact strings (measured with the quoted form of an unquoted tag).
- **Truncating a validator to fit a column** (tt-rss): turns a valid value into one servers ignore; drop it.
- **Storing unbounded or non-ASCII values**: either every later request throws before leaving the process (non-ASCII, via `HttpRequest`) or every later request is a `400` (over the 8 KB field limits).
- **Treating a `2xx` that is not a full representation as one** (`204`, `206`, `226`): a fragment or delta stored as `Fetched` with fresh validators.
- **Adopting `href` or the final URL after a chain with a temporary hop**: the feedparser trap above.
- **Adopting a move seen over cleartext.** One injected `301` re-points the subscription permanently. Feedbin's 576-observation gate is the strongest known defence; at minimum, require the chain's first request to be `https`.
- **Validators surviving a change of final server** (F14).
- **Conditional requests that servers ignore.** 4 of 34 measured feeds answered `200` to a correct echo (FeedBurner's ESF, Google Frontend, a Cloudflare-fronted site), so the caller must still dedupe by entry id.
- **`<redirect><newLocation>` documents and `itunes:new-feed-url`** are moves the HTTP layer cannot see.

## Recommendation for this repository

**Request.** `GET` with `Accept: application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1` (a builder-level `accept` default, so a consumer who swaps the parser can change it, per the library rule that policy is parameterised), plus `If-None-Match` when `validators.etag != null` and `If-Modified-Since` when `validators.lastModified != null`, each exactly as stored.

**`FeedValidators` rules** (apply them in the constructor/factory, so values loaded from a caller's database are held to the same rules, and when reading a response):

| Rule | Value |
|---|---|
| Trim | SP and HTAB at both ends (the container rule `HttpHeaders` applies), then blank means `null` |
| Characters | every character U+0020..U+007E; otherwise `null` |
| ETag length | at most 256; otherwise `null` |
| Last-Modified length | at most 64; otherwise `null` |
| ETag value | not `*`; otherwise `null` |
| Grammar | no quote/`W/` check, no date parse, no case or format change |
| From a response | exactly one header line of that name; zero or several means absent |
| Over the bound | `null`; never truncated |

**Status handling.**

| Response | Result |
|---|---|
| `200`, `203` | parse; `Fetched(feed, validators = the response's two headers sanitised (`None` if neither), finalUrl = response.url, permanentlyMovedTo)` ; a parse failure is `NotAFeed` / `Malformed` / `Refused` / `LimitExceeded` |
| `304`, request carried at least one validator | `NotModified(validators = each validator the `304` carries replaces the sent one (an unusable one becomes `null`), the others kept, finalUrl, permanentlyMovedTo)` |
| `304`, request carried none | `UpstreamError(304)` |
| `201`, `202`, `204`, `205`, `206`, `207`, `226` and any other `2xx` | `UpstreamError(status)` |
| `3xx` the transport returns (`300`, no usable `Location`, ambiguous `Location`) | `UpstreamError(status)`; `Location` stays readable in its headers |
| more than the hop cap, or a refused hop | the transport's `TooManyRedirects` / `RefusedByPolicy`, unchanged |
| `429` or any throttle | `RateLimited` (from `UrlFetcher`) |
| `401`, `403`, `404`, `410`, other `4xx`, `5xx` | `UpstreamError(status)` (the `410` is the caller's unsubscribe signal) |

If the plan keeps "any `2xx` is parsed", the cost is: `204`/`205` surface as `NotAFeed` on an empty document; a `206` body fails as `Malformed` or, if it happens to be well-formed, is stored as a complete feed with full-representation validators; a `226` delta is read as the whole feed.

**`permanentlyMovedTo`.**

```kotlin
// chain: response.redirectChain, url: the requested Url, response: the final response
val run = chain.takeWhile { it.status == 301 || it.status == 308 }
val moved: Url? = if (run.isEmpty()) null else (chain.getOrNull(run.size)?.url ?: response.url)
val permanentlyMovedTo = moved?.takeIf { it != url }
```

| Chain | Final | `permanentlyMovedTo` |
|---|---|---|
| none | `200` | `null` |
| `A -301-> B` | `200` | `B` |
| `A -301-> B` | `304` | `B` |
| `A -308-> B -302-> C` | `200` | `B` (the `302` freezes it; `finalUrl` is `C`) |
| `A -302-> B -301-> C` | `200` | `null` |
| `A -301-> B -301-> C -307-> D` | `200` | `C` |
| `http://x/f -301-> https://x/f` | `200` | `https://x/f` |
| `A -301-> B` | `404` / `410` / `5xx` | no `FeedFetch`; the move is not reported |
| `A -301-> A` (same string) | `200` | `null` |

Document on `permanentlyMovedTo` that it is a fact about the chain, not an instruction: adopt it only after the chain's first request was `https`, or after the same value has been reported on several polls; keep requesting the old URL otherwise; and compare `finalUrl` with the one stored beside the validators, discarding the validators (one unconditional poll) when it changed.

**Transport as built, restated.** `If-None-Match` and `If-Modified-Since` are neither controlled nor sensitive, so `headersForHop` carries them to every hop, cross-origin included; a `304` can end a chain; the chain lists `(requested URL, status)` per followed hop and the response's `url` is the final URL; `https` to `http` hops are refused by default; the cap is 3 hops under `Untrusted`.

**Sealed result.** `FeedFetch` as a `sealed interface` declaring `validators`, `finalUrl` and `permanentlyMovedTo` on the parent, with `Fetched` and `NotModified` as final classes with hand-written equality (the repository's `Lookup` shape). Two leaves is the contract; do not add a third.

**Reasoning.** The leading-run rule is the only one of the four in use that is both what a `302` at the head means in RFC 9110 and implemented by a mature reader (SimplePie, hence FreshRSS) without a multi-day counter. The sanitiser is a correctness fix before it is hygiene: the stack refuses non-ASCII header values when it builds the request, so an unsanitised stored tag turns one odd server into a feed that can never be polled again. The obvious option this rejects is storing the response headers as received and re-validating at send time: it leaves the failure at the moment of the next poll, far from its cause, and gives the caller no way to see that a value was dropped.

## Load-bearing claims

1. **Weak comparison for `If-None-Match`.** "A recipient MUST use the weak comparison function when comparing entity tags for If-None-Match (Section 8.8.3.2), since weak entity tags can be used for cache validation even if there have been changes to the representation data." RFC 9110 §13.1.2, June 2022. <https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.2>
2. **`If-Modified-Since` is ignored beside `If-None-Match`.** "A recipient MUST ignore If-Modified-Since if the request contains an If-None-Match header field; the condition in If-None-Match is considered to be a more accurate replacement for the condition in If-Modified-Since, and the two are only combined for the sake of interoperating with older intermediaries that might not implement If-None-Match." RFC 9110 §13.1.3. <https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.3>
3. **Invalid `If-Modified-Since` is ignored.** "A recipient MUST ignore the If-Modified-Since header field if the received field value is not a valid HTTP-date, the field value has more than one member, or if the request method is neither GET nor HEAD." RFC 9110 §13.1.3.
4. **Echoing `Last-Modified` is the interoperable form.** "This behavior is most interoperable for cases where clocks are poorly synchronized or when the server has chosen to only honor exact timestamp matches". RFC 9110 §13.1.3.
5. **`*` matches any current representation.** "If the field value is "*", the condition is false if the origin server has a current representation for the target resource." RFC 9110 §13.1.2. <https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.2>
6. **A `304` must carry the `ETag`; `Last-Modified` is optional.** "The server generating a 304 response MUST generate any of the following header fields that would have been sent in a 200 (OK) response to the same request:" followed by "Content-Location, Date, ETag, and Vary"; and "Last-Modified might be useful if the response does not have an ETag field". RFC 9110 §15.4.5. <https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.5>
7. **Per-field update from a `304`.** "the cache MUST add each header field in the provided response to the stored response, replacing field values that are already present". RFC 9111 §3.2, June 2022. <https://www.rfc-editor.org/rfc/rfc9111.html#section-3.2>
8. **A cache chooses among several stored responses before updating.** "If none of the initial set contains at least one of the same strong validators, then the cache MUST NOT use the new response to update any stored responses." RFC 9111 §4.3.4. <https://www.rfc-editor.org/rfc/rfc9111.html#section-4.3.4>
9. **Entity-tag grammar allows `obs-text`.** `etagc = %x21 / %x23-7E / obs-text`. RFC 9110 §8.8.3. <https://www.rfc-editor.org/rfc/rfc9110.html#section-8.8.3>
10. **`obs-text` is opaque; CR, LF, NUL are dangerous.** "A recipient SHOULD treat other allowed octets in field content (i.e., obs-text) as opaque data." and "Field values containing CR, LF, or NUL characters are invalid and dangerous". RFC 9110 §5.5. <https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5>
11. **`301` and `308` are permanent; link-editing is usually ignored without a secured connection.** "The server is suggesting that a user agent with link-editing capability can permanently replace references to the target URI with one of the new references sent by the server. However, this suggestion is usually ignored unless the user agent is actively editing references (e.g., engaged in authoring content), the connection is secured, and the origin server is a trusted authority for the content being edited." RFC 9110 §15.4.2. <https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.2>
12. **`302` is temporary.** "Since the redirection might be altered on occasion, the client ought to continue to use the target URI for future requests." RFC 9110 §15.4.3. <https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.3>
13. **`301`/`302` method history; `307`/`308`.** "Prevailing practice eventually converged on changing the method to GET. 307 (Temporary Redirect) and 308 (Permanent Redirect) [RFC7538] were later added to unambiguously indicate method-preserving redirects". RFC 9110 §15.4. <https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4>
14. **Validator header handling when following a redirect.** "Validating header fields that were added by the implementation's cache (e.g., If-None-Match, If-Modified-Since)" are removed, and "Consider removing header fields that were not automatically generated by the implementation (i.e., those present in the request because they were added by the calling context) where there are security implications". RFC 9110 §15.4. <https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4>
15. **`204` has no content; `206` answers a range request.** "A 204 response is terminated by the end of the header section; it cannot contain content or trailers." and "The 206 (Partial Content) status code indicates that the server is successfully fulfilling a range request for the target resource". RFC 9110 §15.3.5 and §15.3.7. <https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.7>
16. **`226` needs `A-IM`.** "The request MUST have included an A-IM header field listing at least one instance-manipulation." RFC 3229 §10.4.1. <https://www.rfc-editor.org/rfc/rfc3229.html#section-10.4.1>
17. **Quality values.** "A sender of qvalue MUST NOT generate more than three digits after the decimal point." RFC 9110 §12.4.2. <https://www.rfc-editor.org/rfc/rfc9110.html#section-12.4.2>
18. **OkHttp 5.5.0 refuses non-printable-ASCII header values; response lines are decoded as UTF-8.** `require(c == '\t' || c in ' '..'~') {` in `headersCheckValue`; `val line = source.readUtf8LineStrict(headerLimit)` in `HeadersReader`. Tag `parent-5.5.0`: <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HeadersCommon.kt> and <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http1/HeadersReader.kt>
19. **SimplePie's permanent-URL rule.** `$this->permanentUrlMutable = $this->permanentUrlMutable && ($this->status_code == 301 || $this->status_code == 308);` and `if ($this->permanentUrlMutable) {` `$this->permanent_url = $url;`. SimplePie 1.9.0, `src/File.php`: <https://github.com/simplepie/simplepie/blob/1.9.0/src/File.php>
20. **SimplePie accepts `200` and `207..299` only.** `$file->get_status_code() === 200 || $file->get_status_code() > 206 && $file->get_status_code() < 300`. SimplePie 1.9.0, `src/SimplePie.php`: <https://github.com/simplepie/simplepie/blob/1.9.0/src/SimplePie.php>
21. **FreshRSS rewrites the feed URL from the permanent URL.** `//The case of HTTP 301 Moved Permanently` then `$subscribe_url = $simplePie->subscribe_url(true) ?? '';`. FreshRSS 1.30.4, `app/Models/Feed.php`: <https://github.com/FreshRSS/FreshRSS/blob/1.30.4/app/Models/Feed.php>
22. **Feedbin adopts a redirect only if every hop is permanent and stable.** `return false unless @redirects.all?(&:permanent?)` and `PERSIST_AFTER = 4 * 24 * 6`. Feedbin at `ddc9a879c71a11d6ae74c219496ff2db115157fe`, `app/jobs/feed_crawler/lib/redirect_cache.rb`: <https://github.com/feedbin/feedbin/blob/ddc9a879c71a11d6ae74c219496ff2db115157fe/app/jobs/feed_crawler/lib/redirect_cache.rb>; and Feedkit `PERMANENT_REDIRECTS = [301, 308].to_set.freeze`, `lib/feedkit/redirect.rb` at `fbac4ffe3a68114716279759e638025ed18c425b`: <https://github.com/feedbin/feedkit/blob/fbac4ffe3a68114716279759e638025ed18c425b/lib/feedkit/redirect.rb>
23. **Liferea updates the subscription on a `301`/`308` hop.** `if ((301 == result->httpstatus || 308 == result->httpstatus) && result->source && !g_str_equal (result->source, subscription->updateJob->request->source)) {`. Liferea at `364b2ea517c02ac2d899d87da78978623bc5dbc5`, `src/subscription.c`: <https://github.com/lwindolf/liferea/blob/364b2ea517c02ac2d899d87da78978623bc5dbc5/src/subscription.c>; its `Accept` is `application/atom+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7` in `src/net.c` at the same commit.
24. **Miniflux's `304` handling and `Accept`.** "Last-Modified may be updated even if ETag is not. In this case, per RFC9111 sections 3.2 and 4.3.4, the stored response must be updated."; `defaultAcceptHeader      = "application/xml,application/atom+xml,application/rss+xml,application/rdf+xml,application/feed+json,text/html,*/*;q=0.9"`. Miniflux v2.3.5, <https://github.com/miniflux/v2/blob/v2.3.5/internal/reader/handler/handler.go> and <https://github.com/miniflux/v2/blob/v2.3.5/internal/reader/fetcher/request_builder.go>
25. **feedparser's `Accept` and redirect status.** `ACCEPT_HEADER = "application/atom+xml,application/rdf+xml,application/rss+xml,application/x-netcdf,application/xml;q=0.9,text/xml;q=0.2,*/*;q=0.1"`; handlers `http_error_300 = http_error_301`, `http_error_302 = http_error_301`, `http_error_303 = http_error_301`, `http_error_307 = http_error_301`; `request.add_header('A-IM', 'feed')  # RFC 3229 support`. feedparser v6.0.14, `feedparser/http.py`: <https://github.com/kurtmckee/feedparser/blob/v6.0.14/feedparser/http.py>. Its redirect documentation: "If the feed has been permanently redirected, you should update your database or configuration file" (`docs/http-redirect.rst`, same tag).
26. **NetNewsWire's 2038 guard.** "// Bug seen in the wild: lastModified with last possible 32-bit date, which is in 2038. Ignore those." NetNewsWire `mac-7.2.1`, `Modules/RSWeb/Sources/RSWeb/HTTPConditionalGetInfo.swift`: <https://github.com/Ranchero-Software/NetNewsWire/blob/mac-7.2.1/Modules/RSWeb/Sources/RSWeb/HTTPConditionalGetInfo.swift>
27. **tt-rss truncates the stored `Last-Modified`.** `$feed_obj->last_modified = substr(UrlHelper::$fetch_last_modified, 0, 245);`. tt-rss at `c02520ff1c26a0aab0c81ada1f42d377f02ac29c`, `classes/RSSUtils.php`: <https://github.com/tt-rss/tt-rss/blob/c02520ff1c26a0aab0c81ada1f42d377f02ac29c/classes/RSSUtils.php>
28. **Server request-field limits.** nginx: "A request header field cannot exceed the size of one buffer as well, or the 400 (Bad Request) error is returned to the client." with `large_client_header_buffers 4 8k;` (<https://nginx.org/en/docs/http/ngx_http_core_module.html#large_client_header_buffers>); Apache 2.4 `LimitRequestFieldSize`, "Default: LimitRequestFieldSize 8190" (<https://httpd.apache.org/docs/2.4/mod/core.html#limitrequestfieldsize>). Both read 2026-11-06.
29. **CDNs change ETag strength.** "However, in some situations Cloudflare will convert strong ETags to weak ETags." Cloudflare, "Using ETag Headers with Cloudflare", last updated 2026-09-30: <https://developers.cloudflare.com/cache/reference/etag-headers/>
30. **Guidance to feed owners.** "RSS readers should be prepared to honor either method. In case of conflict, we recommend that the XML redirect take precedence, since it's the most accessible strategy." RSS Advisory Board, <https://www.rssboard.org/redirect-rss-feed> (read 2026-11-06); "To ensure followers receive your most recent episodes from the new feed URL, maintain the <itunes:new-feed-url> tag and 301 redirect response for at least four weeks." Apple Podcasts for Creators, <https://podcasters.apple.com/support/837-change-the-rss-feed-url> (read 2026-11-06).
31. **A server that ignores `If-Modified-Since` (single source).** "the server does not take into consideration the `If-Modified-Since` header" for a FeedBurner feed, and Blogger returning `304` for a future date. Joshua Rogers, 2025-02-01: <https://joshua.hu/google-feedburner-broken-caching-if-modified-since> (read via a summarising fetch; the second claim is not relied on for any rule).

## Gaps

- **Feedly's `Accept` and conditional behaviour are not documented anywhere found.** Not needed for the recommendation; the value above rests on four readers' code.
- **The network survey is one vantage, one day, one `User-Agent`, 48 URLs chosen by hand.** It supports "values are short" and "echoing works", not rates. CDN behaviour varies by region and by the `Accept-Encoding` sent (the fetcher's transport sends its own); a later measurement through the fetcher itself would be more faithful.
- **Python probe is a re-creation.** The redirect-status results come from re-typing feedparser's handler (`http_error_301` and aliases) on Python 3.14.7's `urllib`, not from running feedparser.
- **Feedbin's stability gate.** The code comment ("4 redirect/hr 24hrs a day for 6 days") gives `PERSIST_AFTER = 576`, while the counter's `expires_in` is 72 hours; whether `Cache.increment` refreshes the expiry, and so the effective requirement, was not established.
- **Liferea's subscription update** was read at the `subscription.c` condition only; whether it first checks the move's trust (scheme, host) was not traced.
- **The future-dated `Last-Modified` guard** (F8) needs the response's `Date` and a rule (for example, drop a `Last-Modified` more than a day after `Date`). Not recommended into `FeedValidators` (a value type with no clock); left open as a follow-up in the fetcher or the caller. Only NetNewsWire's string check and one 2025 blog measurement support the need.
- **Bounds 256 and 64** are chosen by reasoning from a 24-tag, 26-date sample plus server limits, not from a published standard.
- **Whether a `203` response deserves a different `Fetched` treatment** (its validators name the transformed representation along "the same request path") was not researched; treated as a `200`.
- **`UpstreamError` carries no redirect chain or URL.** A caller that wants the move on a chain that ended in `410` has no way to read it. Reporting it is not recommended (F11); if the consumer later needs it, that is a change to the stack's failure type, not to this fetcher.
- **Not researched:** `Retry-After` interplay on `301`/`308` hops, `Cache-Control`/`Expires` as a poll-interval hint, `Content-Location`, and HTTP/2 or HTTP/3 header-field limits beyond nginx's statement that the same limits apply.
