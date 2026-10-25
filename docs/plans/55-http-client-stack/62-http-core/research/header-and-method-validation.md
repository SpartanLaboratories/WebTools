# Header, method, status and content-type validity for the `webtools-http` value types

## Question

Which header names, header values, method names, statuses and content types are valid on the
request side and on the response side of `HttpHeaders`, `HttpRequest`, `HttpMethod`,
`HttpResponse`, `RedirectHop` and `RequestBody`, where does each predicate live, what do lookup
and mutation of `HttpHeaders` mean, and what must a transport (and the fixture parser) do with an
entry the types cannot hold?

Requirements an answer meets:

- One `HttpHeaders` type serves both directions: ordered, case-preserving, case-insensitive
  lookup, a per-entry sensitive flag set through `addSensitive`, a builder.
- A developer-written request is checked when it is built and throws `IllegalArgumentException`
  (header name and value syntax, a body on `GET` or `HEAD`). Text from outside the process is
  parsed into a `Result`.
- A server's odd but harmless response header stays representable; a transport never fails a
  whole response over one header.
- Whatever the types accept on the request side, OkHttp 5.5.0's `Request.Builder` sends without
  throwing.
- `HttpMethod.of(name): Result<HttpMethod>` accepts any RFC 9110 method token, case-sensitively,
  with constants for `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.
- `HttpHeaders`, `HttpRequest`, `HttpResponse` and `RequestBody` print structure only (names,
  method or status, the redacted URL, sizes), never header values or bodies.
- Plain common Kotlin.

## Bottom line

- **Names: one rule everywhere, the RFC 9110 token** (`1*tchar`, 77 ASCII characters), in
  `HttpHeaders.Builder`. It is a strict subset of what OkHttp 5.5.0 accepts (OkHttp accepts every
  character `0x21..0x7E`, including `:`, `(`, `/` and `"`), so every request name is sent; it
  also refuses `:`-prefixed names that would be HTTP/2 pseudo-headers. A response entry whose name
  is not a token (empty, with a space, `:`-prefixed, non-ASCII) is dropped by the transport; no
  response is failed.
- **Values: two rules.** The container (`HttpHeaders`, both directions) refuses only CR, LF and
  NUL, after stripping leading and trailing SP and HTAB. `HttpRequest` additionally requires
  every value it will send to be `HTAB` or `0x20..0x7E`, which is exactly the set OkHttp 5.5.0
  accepts. One value rule for both directions is not workable: real servers send UTF-8 in header
  values (OkHttp hands it back decoded), and OkHttp itself refuses non-ASCII on the request side.
  The transport replaces CR, LF and NUL in a received value with SP (RFC 9110 §5.5 permits it)
  and keeps everything else.
- **Methods:** `HttpMethod.of` accepts exactly `1*tchar`, case-sensitive, no upper-casing.
  OkHttp validates nothing about the method token and writes it raw into the request line
  (measured: a method string with CRLF injected a header), so this check is the only one.
  `GET` and `HEAD` take no body (the stated rule); `TRACE` takes none either (RFC 9110 MUST NOT);
  `POST`, `PUT` and `PATCH` may omit one (the transport sends an empty body where OkHttp requires
  one). `CONNECT` is a valid token but `HttpRequest` should refuse it (Consideration 3).
- **Statuses: 100..599** (RFC 9110 §15). OkHttp hands a transport any code from `000` to `999`
  (and `+NN`; `-NN` throws `IllegalStateException`), so the transport maps a code outside 100..599
  to `TransportFailure.Protocol`; the fixture parser throws `FixtureError` for it.
- **Content type:** `RequestBody.contentType` is validated as an RFC 9110 media type (visible-ASCII
  subset), and the transport sends it as a plain `Content-Type` header, never through OkHttp's
  `MediaType`, which rejects RFC-valid forms such as `text/plain ; charset=x`.
- **Lookup:** names compare by ASCII-only case folding (Kotlin's `equals(ignoreCase = true)` and
  `lowercase()` both fold U+212A to `k`, measured; OkHttp's `Headers.get` is affected).
  `get` returns the last value (OkHttp parity; Ktor and `java.net.http` return the first,
  Consideration 1); `getAll` returns every value in order; `set` replaces all entries of the name
  and appends at the end, keeping a sensitive flag already on the name; `Authorization`,
  `Proxy-Authorization`, `Cookie` and `Set-Cookie` are sensitive without the flag
  (Consideration 2).

## Findings

Evidence labels: **documented** (spec or official docs), **source-read** (code at a pinned tag),
**measured** (probe run against `okhttp-jvm` 5.5.0 on JDK 23, 2026-10-25, using a plain loopback
`ServerSocket` as the peer).

### F1. RFC 9110 grammar for names, values, tokens, whitespace (documented)

- `field-name = token`, `token = 1*tchar`, `tchar` = `! # $ % & ' * + - . ^ _ \` | ~`, DIGIT,
  ALPHA. 15 specials + 10 digits + 52 letters = 77 characters. Delimiters (DQUOTE and
  `(),/:;<=>?@[\]{}`) are excluded. Field names are case-insensitive (§5.1, §5.6.2).
- `field-value = *field-content`, `field-vchar = VCHAR / obs-text`, `obs-text = %x80-FF`. A value
  "does not include leading or trailing whitespace"; OWS is `*( SP / HTAB )` (§5.5, §5.6.3).
  "Specifications for newly defined fields SHOULD limit their values to visible US-ASCII octets
  (VCHAR), SP, and HTAB. A recipient SHOULD treat other allowed octets in field content (i.e.,
  obs-text) as opaque data."
- CR, LF and NUL are the only characters with a MUST: "a recipient of CR, LF, or NUL within a field
  value MUST either reject the message or replace each of those characters with SP". "Field values
  containing other CTL characters are also invalid; however, recipients MAY retain such characters
  for the sake of robustness".
- Same-named field lines are significant in order and may be combined with commas; `Set-Cookie` is
  the documented exception that cannot be combined (§5.3).
- RFC 9112 §5.1: "No whitespace is allowed between the field name and colon"; a proxy "MUST remove
  any such whitespace from a response message". RFC 9112 §5.2: a user agent receiving obs-fold in
  a response "MUST replace each received obs-fold with one or more SP octets".

### F2. RFC 9113 §8.2.1: HTTP/2 field validity (documented)

- Names must not contain `0x00-0x20`, `0x41-0x5a` (upper case) or `0x7f-0xff`, and, apart from
  pseudo-header fields, must not contain `:`. Values must not contain NUL, LF or CR and must not
  start or end with SP or HTAB. A message that violates these "MUST be treated as malformed".
- "An implementation that validates fields according to the definitions in Sections 5.1 and 5.5 of
  [HTTP] only needs an additional check that field names do not include uppercase characters."
  So the token rule plus lower-casing on the wire is the whole HTTP/2 rule; OkHttp lower-cases
  request names itself (F9).

### F3. RFC 9110 methods and status codes (documented)

- `method = token`; "The method token is case-sensitive ... By convention, standardized methods
  are defined in all-uppercase US-ASCII letters." (§9.1). `get` is therefore a different method
  from `GET`.
- Bodies: "A client SHOULD NOT generate content in a GET request unless it is made directly to an
  origin server that has previously indicated ... that such a request has a purpose" (§9.3.1; the
  same wording for `HEAD`); "A client MUST NOT send content in a TRACE request" (§9.3.8); "A
  CONNECT request message does not have content" and CONNECT's request target is host:port only
  (§9.3.6). `DELETE` content "has no generally defined semantics" but is not forbidden; an
  `OPTIONS` request with content "MUST send a valid Content-Type header field" (§9.3.7). No method
  requires a body: a `POST` with `Content-Length: 0` is valid.
- Status: "All valid status codes are within the range of 100 to 599, inclusive." "Values outside
  the range 100..599 are invalid. Implementations often use three-digit integer values outside of
  that range (i.e., 600..999) for internal communication of non-HTTP status ... A client that
  receives a response with an invalid status code SHOULD process the response as if it had a 5xx
  (Server Error) status code." (§15). RFC 9112 §4: `status-code = 3DIGIT`.
- Media type: `media-type = type "/" subtype parameters`, `type` and `subtype` tokens
  (case-insensitive), `parameters = *( OWS ";" OWS [ parameter ] )`, `parameter = token "="
  ( token / quoted-string )`, no whitespace around `=` (§8.3.1, §5.6.6). OWS before the `;` and an
  empty parameter after a `;` are both valid.

### F4. OkHttp 5.5.0 `Headers` request-side rules (source-read, measured)

- Name check: non-empty, every char in `'!'..'~'`. That is all visible ASCII, so `:path`,
  `a:b`, `a(b`, `a/b` and `a"b` are accepted; space, tab, DEL, non-ASCII and NUL are refused.
  Messages: `name is empty`, `Unexpected char 0x20 at 1 in header name: a b`.
- Value check: every char is `'\t'` or in `' '..'~'`. Refused (measured): LF, CR, NUL,
  `0x01`, DEL, `0x80`, `é`, NBSP, U+2028. Message `Unexpected char 0x.. at N in <name> value`
  **followed by `: <value>` unless the name is one of `Authorization`, `Cookie`,
  `Proxy-Authorization`, `Set-Cookie`**. A rejected value is echoed into the exception text for
  every other header name.
- Values are trimmed *after* the check, with Kotlin `String.trim()` (all Unicode whitespace);
  `add`, `set`, `headersOf` and `addLenient` all trim. Measured: `" v "` is stored as `v`, `"\tv\t"`
  as `v`, `"   "` as the empty string; an empty value is accepted.
- `get(name)` returns the **last** matching value (loop from the end); `values(name)` returns all
  in order; `set` is `removeAll` then append, so the entry moves to the end (measured:
  `A:1 B:2 a:3`, `set("A","9")` gives `B:2 A:9`). `names()` is a case-insensitive sorted set that
  loses order and case.
- Name comparison is Kotlin `equals(ignoreCase = true)`. Measured: `headers["Key"]` (Kelvin
  sign) returns the value of `key`, and `"İstanbul"` and `"ıstanbul"` return the value of
  `istanbul`.
- Escape hatches: `addUnsafeNonAscii` skips only the value check (measured: accepts `é`, LF and
  NUL too, despite the name); `addLenient` is `internal`.
- `Headers.toString()` and `Request.toString()` print `██` for exactly four names:
  `Authorization`, `Cookie`, `Proxy-Authorization`, `Set-Cookie`; every other value is printed
  (measured: `X-Api-Key: visible5`, `Cookie2: visible6`).

### F5. OkHttp 5.5.0 `Request.Builder.method` and the method helpers (source-read, measured)

- `method(method, body)`: `require(method.isNotEmpty())`; with a null body
  `require(!HttpMethod.requiresRequestBody(method))`; with a body
  `require(HttpMethod.permitsRequestBody(method))`. Nothing else is checked.
- `requiresRequestBody` is `POST`, `PUT`, `PATCH`, `PROPPATCH`, `QUERY`, `REPORT`;
  `permitsRequestBody` is everything except `GET` and `HEAD`. Measured: `TRACE` and `CONNECT` with
  and without a body, `DELETE`/`OPTIONS` with and without a body, `PROPFIND`, `MOVE` all build.
- **The token is not validated and is written raw.** `RequestLine.get` does
  `append(request.method)`. Measured on the wire, method string
  `GET /x HTTP/1.1\r\nInjected: yes\r\nX-Pad:` produced a request line followed by an `Injected: yes`
  header line. `get` (lower case), `A B`, `X(Y` and `é` all build and are sent. The same class of
  bug is a published advisory in another client (Mint, CVE-2026-48861: "The method field remains
  unvalidated").
- OkHttp's own `delete()` and an `OPTIONS` call with an empty body send `Content-Length: 0`
  (measured); `POST` with an empty body sends `Content-Length: 0`.
- The `requiresRequestBody` list is not stable: `QUERY`, `PROPPATCH` and `REPORT` are in it in
  5.5.0. A transport that copies it can go stale (F11).

### F6. OkHttp 5.5.0 response parsing, HTTP/1.1 (source-read, measured)

`HeadersReader.readHeaders` feeds each line to `Headers.Builder.addLenient(line)`: split at the
first `:` at index >= 1; a line starting with `:` becomes name `""` with the text after it; a line
with no colon becomes name `""` with the whole line as the value. `addLenient(name, value)` stores
the name **untrimmed and unvalidated** and the value trimmed (Kotlin `trim()`). Lines end at LF
(a preceding CR is stripped); a lone CR stays in the line. Text is decoded as UTF-8 with U+FFFD for
invalid sequences. Measured entries (name -> value):

| Raw line(s) | What `Headers` holds |
|---|---|
| `Foo Bar: v` | `Foo Bar` -> `v` |
| `Name : v` | `Name ` (trailing space) -> `v` |
| `: v` | `` (empty) -> `v` |
| `garbage` (no colon) | `` -> `garbage` |
| `X-A: one` + `  two` (obs-fold) | `X-A` -> `one`, then `` -> `two` |
| `X-A: one` + `  x: two` | `X-A` -> `one`, then `  x` -> `two` |
| `A(B: v`, `:foo: v` | `A(B` -> `v`, `:foo` -> `v` |
| `X-é: v` (UTF-8) | `X-é` -> `v` |
| value with NUL / lone CR / DEL | kept verbatim |
| value `\t a\tb \t` | `a\tb` |
| value `café` in UTF-8 / in Latin-1 / with byte `FF` | `café` / `caf�` / `a�b` |
| value with leading NBSP (UTF-8) | NBSP trimmed |
| `X-D: 1` + `x-d: 2` | two entries, `get` returns `2` |
| bare LF line ends | accepted |

Header section size is capped at 256 KiB (`HEADER_LIMIT`).

### F7. OkHttp 5.5.0 response parsing, HTTP/2 (source-read)

`Hpack.Reader` rejects an upper-case ASCII letter in a literal name (`checkLowercase`, "PROTOCOL_ERROR
response malformed: mixed case name"). Names and values are otherwise arbitrary bytes, decoded by
`List<Header>.toHeaders()` with `addLenient(name.utf8(), value.utf8())`. `readHttp2HeadersList`
takes `:status` (parsed with `StatusLine.parse("HTTP/1.1 $value")`), skips `connection`, `host`,
`keep-alive`, `proxy-connection`, `te`, `transfer-encoding`, `encoding` and `upgrade`, and adds
everything else, so another `:`-prefixed pseudo-header, an empty name, a name with a space, and a
value containing CR, LF or NUL all arrive in `Headers`. (Not measured: it needs a TLS peer.)

### F8. OkHttp 5.5.0 status handling (source-read, measured)

- `StatusLine.parse`: protocol `HTTP/1.0`, `HTTP/1.1`, `ICY` or `SOURCETABLE`; then exactly three
  characters passed to `toIntOrNull()`; then end of line or a space. Because `toIntOrNull` accepts
  signs, the accepted codes are `000..999`, `+NN` and `-NN`. Measured: `000` -> 0, `099` -> 99,
  `600` -> 600, `999` -> 999, `+12` -> 12; `2000`, `20`, `20x`, `0x1`, `HTTP/1.2`, `HTTP/2`,
  `HTTP/1.1  200` and `200<TAB>OK` -> `ProtocolException: Unexpected status line`; a missing reason
  phrase is accepted.
- `-12` passes `StatusLine.parse` and then fails `Response.Builder.build()` with
  `check(code >= 0)`. Through `enqueue` the callback receives
  `IOException("canceled due to java.lang.IllegalStateException: code < 0: -12")` (cause
  `IllegalStateException`) and the exception is also rethrown on the dispatcher thread (measured).
- `CallServerInterceptor` swallows `100` and `102..199` and reads on for the real response, so a
  transport never receives them; a lone `100` or `199` with nothing after it ended in
  `IOException: unexpected end of stream` (measured). `101` is returned as a response
  (measured: `code=101`). `204` or `205` with a positive `Content-Length` throws `ProtocolException`.
- A transport therefore sees final codes `0..99`, `101`, `200..999` (and `+NN` forms).

### F9. What OkHttp does to request headers on the wire (source-read, measured)

- HTTP/1.1: names and values are written as given. Measured: `:path: /evil` and `a:b: v` appear
  verbatim; the second reads to a server as header `a` with value `b: v` (a name that splits into a
  name and a value). A user `Content-Length: 5` on a `GET` with no body, and a user
  `Transfer-Encoding: chunked`, are sent verbatim (so the transport must own them, as designed).
- HTTP/2: `http2HeadersList` lower-cases every name (`Locale.US`), skips `connection`, `host`,
  `keep-alive`, `proxy-connection`, `te` (unless `trailers`), `transfer-encoding`, `encoding`,
  `upgrade` and the four request pseudo-header names, and forwards any other name unchecked, so
  `a:b` or `:foo` would reach an HTTP/2 server as a field that RFC 9113 §8.2.1 calls malformed.
- `BridgeInterceptor` sets `Content-Type` from the body only when `body.contentType() != null`.
  Measured: a body with no `MediaType` plus a user header `Content-Type: text/plain ; weird=1` is
  sent with exactly that header; a body that has a `MediaType` replaces the user header.

### F10. OkHttp 5.5.0 `MediaType` (source-read, measured)

`toMediaTypeOrNull` matches `TOKEN/TOKEN` at index 0 and then only `;\s*(token=(token|"quoted"))?`
repeats. Measured `null` for: `text/plain ; charset=x` (OWS before `;`, RFC-valid), `text/plain;
charset`, `text/plain; charset=`, `json`, `text/`, `/plain`, leading or trailing space, `text/plain,
text/html`, an unterminated quote. Accepted: `text/plain;`, `text/plain;;`, `*/*`, a quoted
`Charset="utf-8"`. Its grammar is narrower than RFC 9110 in one place (OWS before `;`) and looser
in others (`;`-only parameters). It is not a validator for what a library may send.

### F11. Ktor 3.6.0 (source-read)

- `HeadersBuilder` validates on `append`/`set`: `checkHeaderName` throws if the name contains
  `0x00..0x20` or one of `"(),/:;<=>?@[\]{}` ("characters above 0x7F stay allowed", so DEL and
  non-ASCII names pass); `checkHeaderValue` throws only for `ch < ' ' && ch != '\t'` (DEL and
  non-ASCII values pass). An empty name passes. The exception messages echo the whole name or
  value (`Header value '$headerValue' contains illegal character ...`).
- `StringValues.get` is `getAll(name)?.firstOrNull()`: **first** value. `getAll` returns
  `List<String>?` (null when absent).
- `HttpMethod` is `data class HttpMethod(val value: String)`; `HttpMethod.parse` returns a constant
  for the nine known names and otherwise `HttpMethod(method)` with no validation and no case
  change.
- The OkHttp engine's response headers (`OkHttpHeaders.fromOkHttp`) are an unvalidated view over
  OkHttp's `Headers`: validation is on the request builder only, and odd response entries are
  retained.

### F12. `java.net.http` (JDK 25+36, source-read)

- Request names must be tokens (`Utils.isValidName`: `tchar`, non-empty, chars <= 255); values may
  be SP, HTAB or `0x21..0xFF` except DEL (`Utils.isValidValue`, so Latin-1 obs-text is accepted).
  Restricted request headers: `connection`, `content-length`, `expect`, `host`, `upgrade`
  (overridable by a system property). Messages echo the value: `invalid header value for %s: "%s"`.
- `HttpRequest.Builder.method`: empty is refused, `CONNECT` is refused ("method CONNECT is not
  supported"), any other non-token is refused (`illegal method "..."`).
- `HttpHeaders.firstValue` returns the first value; `allValues` returns a list (empty, never null);
  the map is case-insensitive.
- The HTTP/1.1 response parser ignores a line with no colon or an empty name ("compatibility with
  HttpURLConnection"), but throws `ProtocolException` for a non-token name or an invalid value, and
  for a status code below 100, failing the whole response.

### F13. Mockwebserver3 5.5.0 can write what the types hold (source-read)

`MockResponse.Builder.addHeader(name, value)` validates like `Headers.add`;
`addHeaderLenient(name, value)` "does not validate the name and value". A fixture with a UTF-8
header value therefore converts with `addHeaderLenient`.

## How comparable projects do it

| Project (ref) | Request names | Request values | Response entries | `get` on repeat | Method |
|---|---|---|---|---|---|
| OkHttp 5.5.0 (`Headers`, `Request.Builder`) | `0x21..0x7E`, non-empty | `\t`, `0x20..0x7E`, trimmed; echoes value except 4 names | `addLenient`: anything, name untrimmed | last | non-empty only; written raw |
| Ktor 3.6.0 (`HeadersBuilder`, `HttpMethod`) | no `0x00..0x20`, no `"(),/:;<=>?@[\]{}` | no `< 0x20` except TAB | OkHttp engine: unvalidated view | first (`getAll` is nullable) | any string |
| `java.net.http` (JDK 25+36) | token, minus 5 restricted names | SP, HTAB, `0x21..0xFF` minus DEL | invalid name or value fails the response; empty-name lines ignored | `firstValue` (first) | token, `CONNECT` refused |
| This repository (proposed) | token | container: no CR/LF/NUL; request: `\t`, `0x20..0x7E`; never echoed | token names kept, others dropped; CR/LF/NUL -> SP | last | token, case-sensitive |

Ktor's split (strict builder, unvalidated view of what the engine returned) is the closest
precedent for a container that is also a response type. `java.net.http` is the cautionary precedent
for the opposite choice: one strict rule that aborts a response over one header.

## Pitfalls

1. **Delegating validation to OkHttp's `Headers.Builder.add`.** It accepts `:`-prefixed and
   colon-bearing names that split on the HTTP/1.1 wire (`a:b: v`) and are malformed HTTP/2.
2. **Echoing rejected text.** OkHttp prints the offending value for every header except four
   credential names, and the offending name always; Ktor and the JDK print both. A swapped
   `add(apiKey, "X-Api-Key")` would put the key into the exception text.
3. **`String.equals(ignoreCase = true)` / `lowercase()` for header names.** On the JVM both fold
   U+212A to `k` and U+0131 to `i` (measured), so a lookup with a Kelvin sign finds `key`. Common
   Kotlin offers no ASCII-only fold; it is a three-line loop.
4. **Trusting OkHttp's method handling.** No token check, raw into the request line, injection
   measured.
5. **Copying OkHttp's "requires a body" list into the transport.** It grew (`QUERY`, `REPORT`,
   `PROPPATCH`); a stale copy turns into an `IllegalArgumentException` from `Request.Builder` at
   send time. A Level 4a differential test over `Request.Builder.method(m, null)` catches it.
6. **Routing `RequestBody.contentType` through OkHttp's `MediaType`.** `text/plain ; charset=x` is
   valid HTTP and `null` in OkHttp; the body's `MediaType` also silently replaces a user
   `Content-Type`.
7. **A negative or oversized status.** `-12` throws on OkHttp's dispatcher thread and reaches the
   callback as a generic `IOException`; without a cause check the transport reports `Io`, not
   `Protocol`.
8. **Silent loss of obs-fold.** OkHttp turns a continuation line into an empty-name entry, which is
   indistinguishable from a colon-less garbage line; RFC 9112 §5.2 wants the text joined with SP.
   Obs-fold is deprecated; the entry is dropped.
9. **`Set-Cookie` and `get`.** With a last-wins `get`, a caller reading `Set-Cookie` sees one of
   several cookies; `getAll` is the right call and the KDoc says so.
10. **Header values in logs through `Entry`/`Headers` helpers.** OkHttp's own `Headers.toString()`
    prints every value but four; neither it nor a `data class` printout is used for any of the
    library's types.

## Recommendation for this repository

All predicates are `internal` functions of one `internal object HttpSyntax` in `commonMain`
(`isToken`, `firstNonTokenIndex`, `isFieldValue`, `isSendableValue`, `trimOws`,
`sanitizeReceivedValue`, `isMediaType`, `equalsName`, `isCredentialName`), written as `Char`
comparisons with no regex and no JVM API. The transport and the fixtures (which see `internal`
through the compilation association) use the same object.

### Predicates

| Predicate | Exact definition | Home | Fails with |
|---|---|---|---|
| Header name | non-empty, every char in `A-Z a-z 0-9 ! # $ % & ' * + - . ^ _ \` | ~` | `HttpHeaders.Builder.add/addSensitive/set` (and `HttpHeaders.of`) | `IllegalArgumentException` |
| Header value (container) | after removing leading and trailing SP and HTAB (only those two), contains no U+000A, U+000D, U+0000; every other UTF-16 unit is kept | `HttpHeaders.Builder` | `IllegalArgumentException` |
| Header value (sendable) | every char is U+0009 or in U+0020..U+007E | `HttpRequest` builder, over every entry of `headers` | `IllegalArgumentException` |
| Method | `HttpMethod.of(name)`: non-empty, every char a `tchar`; case-sensitive, never upper-cased; the seven names return the constants | `HttpMethod` | `Result.failure(IllegalArgumentException)` |
| Body permission | `GET`, `HEAD`, `TRACE` take no body | `HttpRequest` builder | `IllegalArgumentException` |
| Status | `100..599` | `HttpResponse` builder; `Fixture` | `IllegalArgumentException` (`FixtureError` from the parser) |
| Redirect status | `300..399` | `RedirectHop` | `IllegalArgumentException` |
| Content type | RFC 9110 media type, visible-ASCII subset (below); `null` means none; the empty string is refused | `RequestBody` | `IllegalArgumentException` |

Media-type grammar for `RequestBody.contentType`: `token "/" token { OWS ";" OWS [ token "="
( token | quoted ) ] }` with no leading or trailing whitespace, where `quoted` is `"` followed by
any of `HTAB`, `SP`, `0x21`, `0x23..0x5B`, `0x5D..0x7E` or `\` + (`HTAB` / `SP` / `0x21..0x7E`),
then `"`. It is a subset of the sendable-value set, so an accepted content type is always a sendable
header value.

### Names, values, trimming and the two rules

- Names: token everywhere, so a name is validated once, at `add`. The brief's alternative
  container rule (refuse only CR/LF/NUL, empty, colon, whitespace in a name) is not used: it
  admits names such as `a(b`, `x/y` and non-ASCII ones that no consumer can sensibly look up, needs
  a second name check in `HttpRequest`, and buys only the retention of entries that RFC 9113 §8.2.1
  calls malformed. Dropping them costs nothing a caller reads.
- Values: the container rule is deliberately wide so a received value round-trips (UTF-8 text,
  `U+FFFD`, DEL, other controls). Leading and trailing SP/HTAB are removed on every `add`/`set`, so
  `get` equals what OkHttp would send and what the response parser (`Name: value`, RFC 9112 §5.1)
  extracts. A developer typo such as `add("X-Name", "café")` is accepted by the builder and refused
  at `HttpRequest` build time, where the request is checked anyway.
- `HttpRequest` checks, in order, every entry's value (sendable), then the method/body rule, then
  the `Content-Type` consistency rule (Consideration 5). Because names are tokens, a name is
  safe to echo in any message that follows.

### `HttpHeaders` semantics

- `get(name): String?`: the last value of the name, or `null` (Consideration 1). `getAll(name):
  List<String>`: every value in insertion order; empty list when absent, never `null`.
  `contains(name)`, `size`, `isEmpty()`. `entries(): List<Entry>` in insertion order with
  `Entry(name, value, isSensitive)`; names keep the case they were added with.
- Name comparison for every lookup and mutation is ASCII-only: `A-Z` fold to `a-z`, nothing else.
- `Builder.add(name, value)` appends. `addSensitive(name, value)` appends with the flag.
  `set(name, value)` removes every entry of that name and appends one at the end (OkHttp's
  behaviour); the new entry is sensitive if any removed entry was sensitive (the flag cannot be
  dropped by overwriting), and a `setSensitive(name, value)` sets the flag explicitly.
  `remove(name)` removes every entry of the name and is a no-op when none exists. `addAll(headers)`
  copies entries with their flags. `build()` snapshots; `newBuilder()` copies entries and flags.
- Sensitivity: `Entry.isSensitive` is true when the flag is set **or** the name (ASCII-folded) is
  `Authorization`, `Proxy-Authorization`, `Cookie` or `Set-Cookie` (the four names OkHttp 5.5.0
  redacts); `isCredentialName` is the single list that the policy, the transport's cross-origin
  strip and the recorder consult (Consideration 2). `addSensitive` is for custom credential headers
  (`X-Api-Key`).
- Equality: same ordered entries with identical name spelling, value and flag; `hashCode`
  consistent. `toString()`: the count and the names in order, a marker on sensitive entries, no
  values. `Entry.toString()` prints the name and the flag only.
- HTTP/2 responses arrive with lower-case names, HTTP/1.1 with the server's case; lookups never
  depend on it, and the KDoc says so.

### Method semantics

- `HttpMethod.of("GET") === HttpMethod.Get`, `of("get")` is a distinct value, `equals` is by exact
  name, `toString()` is the name. The failure carries a constant message that does not echo the
  input.
- `permitsBody` is `internal` (`false` for `GET`, `HEAD`, `TRACE`). Nothing "requires" a body in
  the type: `HttpRequest(POST, url)` with a null body is valid. The transport hands OkHttp an empty
  body when `Request.Builder.method(name, null)` would throw. Implement this either with a
  conservative copy of OkHttp's list (`POST`, `PUT`, `PATCH`, `PROPPATCH`, `QUERY`, `REPORT`) pinned
  by a differential test, or by catching the `IllegalArgumentException` from `method(name, null)`
  and retrying once with an empty body.
- The transport sends `RequestBody.contentType` by setting the `Content-Type` header on the OkHttp
  request and giving the OkHttp body a null `MediaType` (measured: the header goes out verbatim).

### What the transport does with a response entry the types cannot hold

Applies to HTTP/1.1 and HTTP/2 alike; never fails the response.

| OkHttp gives | Action |
|---|---|
| name is a token | keep; value goes through `sanitizeReceivedValue` |
| name empty, or contains any non-token char (space, `:` including `:`-prefixed pseudo-headers, delimiter, control, non-ASCII) | drop the entry |
| value contains CR, LF or NUL | replace each with SP, then strip SP/HTAB (RFC 9110 §5.5) |
| value non-ASCII, `U+FFFD`, DEL, other controls | keep unchanged |
| obs-fold continuation (arrives as an empty-name entry) | dropped with the other empty-name entries |
| duplicate names | keep all, in order |

The transport logs one `DEBUG` line per response with the *count* of dropped entries, never a name
or a value, and exposes nothing on `HttpResponse`.

| OkHttp gives | Action |
|---|---|
| status `100..599` | `HttpResponse` (a `101` is returned like any other, with an empty body) |
| status `0..99` or `600..999` (and `+NN`) | `TransportFailure.Protocol` with a constant message ("invalid HTTP status code") |
| `IOException` whose cause is the `IllegalStateException` from `code < 0` | `Protocol` (check the cause type before the generic `Io` mapping) |

### Fixture parser (`*.http`)

A hand-authored or recorded fixture is a test-authoring input, so the parser throws `FixtureError`
rather than dropping: a status outside `100..599`; a header line with no colon, an empty name, a
non-token name, whitespace before the colon, or a leading SP/HTAB (no folding); a value containing
CR (outside the line terminator) or NUL. The parser reads values with the container rule so UTF-8
header values round-trip; `Fixture.toMockResponse()` uses `addHeaderLenient` so they reach
`mockwebserver3`. The `Fixture` constructor applies the same status and header rules as
`HttpResponse`.

### Constant `IllegalArgumentException` messages

A name is echoed only when it has already passed the token rule; a name that failed is reported by
character index, because a swapped argument can put a secret in the name position. A value is never
echoed.

| Rule | Message |
|---|---|
| name empty | `Header name must not be empty` |
| name not a token | `Header name is not an RFC 9110 token (first offending character at index <i>)` |
| container value | `Value of header '<name>' must not contain CR, LF or NUL` |
| sendable value | `Value of header '<name>' must be visible ASCII, space or tab to be sent` |
| body on a body-less method | `A <METHOD> request must not have a body` |
| content type | `Content type is not an RFC 9110 media type (first offending character at index <i>)` |
| status | `Status code must be in 100..599` |
| redirect status | `Redirect status must be in 300..399` |
| method token (`Result.failure`) | `Method is not an RFC 9110 token` |
| Content-Type conflict (Consideration 5) | `A request with a body content type must not also set a Content-Type header` |

### Level 4a validation tables (pure functions, `commonTest`; the OkHttp differential in `jvmTest`)

**Names.** Exhaustive sweep over U+0000..U+FFFF: a one-character name `c` is accepted iff `c` is
one of the 77 `tchar`. Fixed rows:

| Input | Result |
|---|---|
| `X-Test`, `a`, `A_b-c.d~e`, `123`, ``!#$%&'*+-.^_`|~`` | accepted |
| `` (empty), `a b`, `a:b`, `:path`, `a(b`, `a/b`, `a"b`, `a,b`, `a;b`, `a<b`, `a=b`, `a>b`, `a?b`, `a@b`, `a[b`, `a\b`, `a]b`, `a{b`, `a}b` | refused |
| `é`, `Key`, `a\tb`, `a\u007fb`, `a\u0000b`, `a\r\nb`, `Name ` | refused |

**Container values** (stored value shown):

| Input | Result |
|---|---|
| `` (empty), `v`, `a b`, `a\tb`, `café`, `日本語`, `a\u007fb`, `a\u0001b`, `K`, ` v` | accepted unchanged |
| ` v `, `\t v \t`, `   ` | accepted as `v`, `v`, `` |
| `a\nb`, `a\rb`, `a\u0000b`, `v\r\n`, `\nv` | refused |

**Sendable values.** Sweep over U+0000..U+FFFF in `"a" + c + "b"`: sendable iff `c == '\t'` or
`c in ' '..'~'`. Differential: sendable <=> `Request.Builder().addHeader("X", v)` does not throw,
and `header("X")` equals the stored value. A name sweep pins one direction: every accepted name is
accepted by `Request.Builder`. Also pinned as documentation of the gap: OkHttp accepts `:path`,
`a(b`, `a/b`, `a"b`, which the types refuse.

**Name lookup.** `get("CONTENT-TYPE")`, `get("content-type")` find `Content-Type`; `get("Key")`
does not find `key`; `get("ı")` does not find `i`; an empty-string lookup returns `null`.

**Mutation.**

| Sequence | Result |
|---|---|
| `add(A,1) add(B,2) add(a,3)`; `get(A)`; `getAll(a)` | `3`; `[1, 3]` |
| the same, then `set(A, 9)` | entries `B:2, A:9` (spelling `A`) |
| `addSensitive(X-K, s)`; `set(x-k, t)` | one entry, sensitive |
| `add(Authorization, v)` | `isSensitive` true without the flag |
| `add(X-Other, v)` | `isSensitive` false |
| `remove(none)` | no change |
| builder reused after `build()` | the built value does not change |

**Methods** (`HttpMethod.of`):

| Input | Result |
|---|---|
| `GET` (same instance as the constant), `get` (distinct, not equal to `GET`), `PATCH`, `M-SEARCH`, `PROPFIND`, `X_Y`, `a.b`, `~`, `123`, `CONNECT`, `TRACE` | accepted |
| ``, `A B`, `A\r\nB`, `GÉT`, `X(Y`, `GET/`, `A,B`, `a:b`, ` GET`, `GET ` | `Result.failure` |

`HttpRequest` rows: `GET`/`HEAD`/`TRACE` with a body refused; `POST`/`PUT`/`PATCH`/`DELETE`/
`OPTIONS` with and without a body accepted; `CONNECT` refused (Consideration 3). A differential row
set for the transport: for each of `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `QUERY`, `REPORT`,
`PROPPATCH`, `PROPFIND` with a null body, the converted OkHttp request builds.

**Statuses:** `99`, `0`, `-1`, `600`, `999`, `1000` refused; `100`, `101`, `199`, `200`, `204`, `299`,
`304`, `399`, `404`, `499`, `500`, `599` accepted. **Redirect hops:** `299` and `400` refused,
`300`, `301`, `302`, `303`, `307`, `308`, `399` accepted.

**Content types:**

| Input | Result |
|---|---|
| `text/plain`, `application/json; charset=utf-8`, `application/json;charset=UTF-8`, `Text/HTML;Charset="utf-8"`, `multipart/form-data; boundary=----abc`, `*/*`, `application/vnd.api+json`, `text/plain ; charset=x`, `text/plain;`, `text/plain; a="b c"`, `text/plain; a="q\"x"` | accepted |
| ``, `json`, `text/`, `/plain`, `text/plain charset=x`, `text/plain; charset`, `text/plain; charset=`, `text/plain; a=b c=d`, `text/plain, text/html`, `text/plain; a="unterminated`, `text/plain\r\nX: y`, `téxt/plain`, ` text/plain`, `text/plain ` | refused |

**Transport response entries** (Level 3, a raw loopback peer; the rows of F6 are the corpus): the
whole table of "What the transport does", including the dropped-entry count in the log and the
status rows (`000`, `099`, `600`, `999`, `+12`, `-12`).

## Considerations

### 1. Which value `get` returns when a name repeats

- **Last** (OkHttp 5.5.0). The transport reads bodies through OkHttp, whose framing decisions
  (`Content-Length`, `Transfer-Encoding`) go through `Headers.get`, which is last-wins, so the
  library and the transport read a repeated header the same way; the existing transport design
  already says "last value".
- **First** (Ktor 3.6.0, `java.net.http`). The convention of the other two libraries a JVM consumer
  is most likely to also use.
- **Neither:** make `get` fail when a name repeats; heavier for a lookup that is usually
  single-valued.
- **Recommendation:** last, stated first line of the KDoc together with "use `getAll` for
  list-valued fields and for `Set-Cookie`". The RFC defines no winner for a repeated singleton, so
  this is a documented choice, not a correctness question. If the cost of surprising Ktor users
  outweighs OkHttp parity, switching to first changes only `get`.

### 2. Credential names sensitive without the flag

- **Derived (recommended):** `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie` are
  sensitive by name; the flag marks custom credential headers. One predicate then serves the
  `Untrusted` refusal, the cross-origin strip, the recorder's redaction and printing, and no caller
  can forget the flag for the four universal names. Cost: `Entry.isSensitive` is true for entries a
  developer did not flag, and `Untrusted` also refuses a request carrying `Set-Cookie`.
- **Flag only:** the policy, the transport and the recorder each keep their own copy of the four
  names (the current design text), and a forgotten `addSensitive("Authorization", ...)` is still
  caught by the name list in each place.
- The choice changes what `Entry.isSensitive` documents in a Stable Core
  type, so it needs a decision before the entry type is frozen.

### 3. `CONNECT` and `TRACE` in `HttpRequest`

- **Refuse `CONNECT`, refuse a body on `TRACE` (recommended):** `HttpMethod.of("CONNECT")` and
  `of("TRACE")` succeed (any token); `HttpRequest` refuses `CONNECT` because its request target is
  host:port, which `Url` and the transport cannot express (OkHttp would send `CONNECT /path
  HTTP/1.1`; `java.net.http` refuses `CONNECT` too), and refuses a `TRACE` body because RFC 9110
  says MUST NOT.
- **Allow both:** OkHttp builds both; the type stays minimal and `CONNECT` is a server-side
  problem. The refusals can be added later as a check that makes previously accepted input fail,
  which is a breaking change; removing a refusal later is not.
- Either way `TRACE` itself is sendable. RFC 9110 §9.3.8 also tells clients not to put credentials
  in it; `Untrusted` already refuses credentials, `Trusted` does not.

### 4. A status outside 100..599 from OkHttp

- **`TransportFailure.Protocol` (recommended):** nothing usable and no honest status to report; the
  facade's `UpstreamFailure(status)` stays within the RFC range.
- **Coerce to a 5xx** as RFC 9110 §15 suggests with "SHOULD process the response as if it had a 5xx":
  the caller gets a response, but the status is invented and the original code is lost. The RFC
  requirement is a SHOULD on a client's processing, which a failure also satisfies in effect.

### 5. A `Content-Type` header next to a body that has a content type

- **Refuse at `HttpRequest` build (recommended):** one way to set it, no silent precedence.
- **Body wins, the header entry is dropped by the transport** (matches what OkHttp's bridge does
  with a typed body): never fails, but hides the loser.
- **Allow when equal:** more code for a case no caller needs.

## Known gaps

- HTTP/2 behaviour (F7, F9) is source-read only. A TLS peer with HTTP/2 was not run, so the
  `:`-prefixed, empty-name and CR/LF/NUL cases are not measured on h2.
- Ktor 3.6.0 and the JDK 25+36 client were source-read, not run.
- The exception type inside `HttpMethod.of`'s `Result.failure` is a recommendation
  (`IllegalArgumentException`); the failure model does not name one for parse functions.
- The transport's silent drop of caller values for `User-Agent`, `Accept-Encoding`, `Host`,
  `Connection`, `Content-Length` and `Transfer-Encoding` (`http-transport.md`) is untouched here;
  whether `HttpRequest` should refuse those names instead is not decided by this research.
- No limit is proposed for the length of a name, a value, a method or a content type, nor for the
  number of entries; OkHttp bounds only the response header section (256 KiB).
- OPTIONS-with-content "MUST send a valid Content-Type" (RFC 9110 §9.3.7) is not enforced by any
  rule above.
- The Mint advisory is a single source for the real-world consequence of an unvalidated method;
  the injection itself was measured against OkHttp 5.5.0.
- The ASCII-only fold was not measured on `js`, because the recommendation does not call a library
  case function.

## Load-bearing claims

1. Token grammar. "token          = 1*tchar" and "tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA". RFC 9110 §5.6.2 <https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.2> (June 2022).
2. Field name = token, case-insensitive. "field-name     = token" and "Field names are case-insensitive". RFC 9110 §5.1 <https://www.rfc-editor.org/rfc/rfc9110.html#section-5.1>.
3. Field value grammar and trimming. "field-vchar    = VCHAR / obs-text", "obs-text       = %x80-FF" and "A field value does not include leading or trailing whitespace." RFC 9110 §5.5 <https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5>.
4. CR, LF, NUL. "Field values containing CR, LF, or NUL characters are invalid and dangerous, due to the varying ways that implementations might parse and interpret those characters; a recipient of CR, LF, or NUL within a field value MUST either reject the message or replace each of those characters with SP before further processing or forwarding of that message." RFC 9110 §5.5.
5. Other controls may be retained. "Field values containing other CTL characters are also invalid; however, recipients MAY retain such characters for the sake of robustness when they appear within a safe context". RFC 9110 §5.5.
6. Opaque obs-text. "A recipient SHOULD treat other allowed octets in field content (i.e., obs-text) as opaque data." RFC 9110 §5.5.
7. OWS. "OWS            = *( SP / HTAB )". RFC 9110 §5.6.3 <https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.3>.
8. Repeated field lines. "The order in which field lines with the same name are received is therefore significant to the interpretation of the field value" and "Since it cannot be combined into a single field value, recipients ought to handle "Set-Cookie" as a special case while processing fields." RFC 9110 §5.3 <https://www.rfc-editor.org/rfc/rfc9110.html#section-5.3>.
9. Method token, case-sensitive. "method = token" and "The method token is case-sensitive because it might be used as a gateway to object-based systems with case-sensitive method names." RFC 9110 §9.1 <https://www.rfc-editor.org/rfc/rfc9110.html#section-9.1>.
10. Body on GET. "A client SHOULD NOT generate content in a GET request unless it is made directly to an origin server that has previously indicated, in or out of band, that such a request has a purpose and will be adequately supported." RFC 9110 §9.3.1 <https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.1>.
11. Body on TRACE. "A client MUST NOT send content in a TRACE request." RFC 9110 §9.3.8 <https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.8>.
12. CONNECT. "CONNECT uses a special form of request target, unique to this method, consisting of only the host and port number of the tunnel destination, separated by a colon." and "A CONNECT request message does not have content." RFC 9110 §9.3.6 <https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.6>.
13. OPTIONS with content. "A client that generates an OPTIONS request containing content MUST send a valid Content-Type header field describing the representation media type." RFC 9110 §9.3.7 <https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.7>.
14. Status range. "All valid status codes are within the range of 100 to 599, inclusive." and "Values outside the range 100..599 are invalid." and "A client that receives a response with an invalid status code SHOULD process the response as if it had a 5xx (Server Error) status code." RFC 9110 §15 <https://www.rfc-editor.org/rfc/rfc9110.html#section-15>.
15. Media type and parameters. "media-type = type "/" subtype parameters" and "parameters      = *( OWS ";" OWS [ parameter ] )". RFC 9110 §8.3.1 and §5.6.6 <https://www.rfc-editor.org/rfc/rfc9110.html#section-8.3.1>.
16. Whitespace before the colon. "No whitespace is allowed between the field name and colon." and "A proxy MUST remove any such whitespace from a response message before forwarding the message downstream." RFC 9112 §5.1 <https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1>.
17. Obs-fold in a response. "A user agent that receives an obs-fold in a response message that is not within a "message/http" container MUST replace each received obs-fold with one or more SP octets prior to interpreting the field value." RFC 9112 §5.2 <https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2>.
18. HTTP/2 field validity. "A field name MUST NOT contain characters in the ranges 0x00-0x20, 0x41-0x5a, or 0x7f-0xff (all ranges inclusive)." and "field names MUST NOT include a colon (ASCII COLON, 0x3a)" and "A field value MUST NOT contain the zero value (ASCII NUL, 0x00), line feed (ASCII LF, 0x0a), or carriage return (ASCII CR, 0x0d) at any position." RFC 9113 §8.2.1 <https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1>.
19. OkHttp name check. `require(c in '!'..'~') {` and `require(name.isNotEmpty()) { "name is empty" }` in `headersCheckName`. OkHttp 5.5.0, `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HeadersCommon.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HeadersCommon.kt>.
20. OkHttp value check and echo. `require(c == '\t' || c in ' '..'~') {` ... `(if (isSensitiveHeader(name)) "" else ": $value")` in `headersCheckValue`. Same file and tag.
21. OkHttp trims and `get` is last-wins. `namesAndValues.add(value.trim())` in `commonAddLenient` and `for (i in namesAndValues.size - 2 downTo 0 step 2) {` in `commonHeadersGet`; the `Headers.get` KDoc: "Returns the last value corresponding to the specified field, or null." and "This class trims whitespace from values. It never returns values with leading or trailing whitespace." Same file, and `okhttp/src/commonJvmAndroid/kotlin/okhttp3/Headers.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Headers.kt>.
22. OkHttp `set` replaces then appends. `removeAll(name)` followed by `commonAddLenient(name, value)` in `commonSet`. `-HeadersCommon.kt`, tag `parent-5.5.0`.
23. OkHttp `addLenient(line)` and `addUnsafeNonAscii`. "Add a header line without any validation. Only appropriate for headers from the remote peer or cache." and "Add a header with the specified name and value. Does validation of header names, allowing non-ASCII values." `Headers.kt`, tag `parent-5.5.0`.
24. OkHttp redacts four names. "Since OkHttp 5 this redacts these sensitive headers: * `Authorization` * `Cookie` * `Proxy-Authorization` * `Set-Cookie`". `Headers.kt`, tag `parent-5.5.0`.
25. OkHttp name comparison is `ignoreCase`. `if (name.equals(namesAndValues[i], ignoreCase = true)) {` in `commonHeadersGet`. `-HeadersCommon.kt`, tag `parent-5.5.0`. (The Kelvin-sign result is measured.)
26. OkHttp method checks. `require(method.isNotEmpty()) {` ... `require(!HttpMethod.requiresRequestBody(method)) {` ... `require(HttpMethod.permitsRequestBody(method)) {` in `Request.Builder.method`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/Request.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Request.kt>.
27. OkHttp body-requirement lists. `method == "POST" || method == "PUT" || method == "PATCH" || method == "PROPPATCH" || method == "QUERY" || // WebDAV method == "REPORT"` and `fun permitsRequestBody(method: String): Boolean = !(method == "GET" || method == "HEAD")`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/HttpMethod.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/HttpMethod.kt>.
28. OkHttp writes the method raw. `append(request.method)` in `RequestLine.get`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RequestLine.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RequestLine.kt>. (The injection is measured.)
29. OkHttp status parsing. "// Parse response code like "200". Always 3 digits." and `statusLine.substring(codeStart, codeStart + 3).toIntOrNull()`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/StatusLine.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/StatusLine.kt>.
30. OkHttp rejects negative codes late. `check(code >= 0) { "code < 0: $code" }` in `Response.Builder.build`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/Response.kt`, tag `parent-5.5.0`. And in `RealCall.AsyncCall.run`: `val canceledException = IOException("canceled due to $t")`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt`, tag `parent-5.5.0`.
31. OkHttp interim responses. "// Handle Processing (102) & Early Hints (103) and any new codes without failing" and `code in (102 until 200) -> true` and `code == 100 -> true` in `shouldIgnoreAndWaitForRealResponse`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/CallServerInterceptor.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/CallServerInterceptor.kt>.
32. OkHttp HTTP/1.1 header reading. "if (line.isEmpty()) break" / `result.addLenient(line)` in `HeadersReader.readHeaders`, and in `addLenient(line)`: `line[0] == ':' -> {` ... `addLenient("", line.substring(1)) // Empty header name.` and `// No header name.` `addLenient("", line)`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http1/HeadersReader.kt` and `Headers.kt`, tag `parent-5.5.0`.
33. OkHttp HTTP/2 reading. `builder.addLenient(name.utf8(), value.utf8())` in `List<Header>.toHeaders()` (`-UtilJvm.kt`); `statusLine = StatusLine.parse("HTTP/1.1 $value")` and `headersBuilder.addLenient(name, value)` in `readHttp2HeadersList` (`Http2ExchangeCodec.kt`); `"PROTOCOL_ERROR response malformed: mixed case name: ${name.utf8()}"` in `Hpack.checkLowercase`. Tag `parent-5.5.0`, under `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http2/Http2ExchangeCodec.kt>.
34. OkHttp HTTP/2 request names. `// header names must be lowercase.` / `val name = headers.name(i).lowercase(Locale.US)` in `http2HeadersList`. `Http2ExchangeCodec.kt`, tag `parent-5.5.0`.
35. OkHttp sets Content-Type only for a typed body. `val contentType = body.contentType()` / `if (contentType != null) {` / `requestBuilder.header("Content-Type", contentType.toString())` in `BridgeInterceptor.intercept`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/BridgeInterceptor.kt`, tag `parent-5.5.0`.
36. OkHttp `MediaType` grammar. `private val PARAMETER = Regex(";\\s*(?:$TOKEN=(?:$TOKEN|$QUOTED))?")` and `TYPE_SUBTYPE.matchAt(this, 0)`. `okhttp/src/commonJvmAndroid/kotlin/okhttp3/MediaType.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/MediaType.kt>.
37. Mockwebserver3 lenient header. "Adds a new header with the name and value. This may be used to add multiple headers with the same name. Unlike [addHeader] this does not validate the name and value." `mockwebserver/src/main/kotlin/mockwebserver3/MockResponse.kt`, tag `parent-5.5.0` <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver/src/main/kotlin/mockwebserver3/MockResponse.kt>.
38. Ktor header validation. "// Control characters (0x00..0x20) and RFC 7230 delimiters; characters above 0x7F stay allowed" and `if (ch < ' ' && ch != '\u0009') {`. Ktor 3.6.0, `ktor-http/common/src/io/ktor/http/HttpHeaders.kt` <https://github.com/ktorio/ktor/blob/3.6.0/ktor-http/common/src/io/ktor/http/HttpHeaders.kt>.
39. Ktor `get` is first. `public operator fun get(name: String): String? = getAll(name)?.firstOrNull()`. Ktor 3.6.0, `ktor-utils/common/src/io/ktor/util/StringValues.kt` <https://github.com/ktorio/ktor/blob/3.6.0/ktor-utils/common/src/io/ktor/util/StringValues.kt>.
40. Ktor method is unvalidated. `public data class HttpMethod(val value: String)` and `else -> HttpMethod(method)` in `parse`. Ktor 3.6.0, `ktor-http/common/src/io/ktor/http/HttpMethod.kt` <https://github.com/ktorio/ktor/blob/3.6.0/ktor-http/common/src/io/ktor/http/HttpMethod.kt>.
41. Ktor's OkHttp engine returns an unvalidated view. `internal fun OkHttpHeaders.fromOkHttp(): Headers = object : Headers {`. Ktor 3.6.0, `ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkUtils.kt` <https://github.com/ktorio/ktor/blob/3.6.0/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkUtils.kt>.
42. `java.net.http` validation. `headers.addAll(Set.of("connection", "content-length", "expect", "host", "upgrade"));` and `if (method.equals("CONNECT")) throw newIAE("method CONNECT is not supported");`. OpenJDK tag `jdk-25+36`, `src/java.net.http/share/classes/jdk/internal/net/http/common/Utils.java` and `.../HttpRequestBuilderImpl.java` <https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/java.net.http/share/classes/jdk/internal/net/http/common/Utils.java>.
43. `java.net.http` response parser. "// compatibility with HttpURLConnection;" / `if (name.isEmpty()) return;` and `throw protocolException("Invalid header name \"%s\"", name);`. OpenJDK tag `jdk-25+36`, `.../jdk/internal/net/http/Http1HeaderParser.java` <https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/java.net.http/share/classes/jdk/internal/net/http/Http1HeaderParser.java>.
44. `java.net.http` first value. `return allValues(name).stream().findFirst();` in `HttpHeaders.firstValue`. OpenJDK tag `jdk-25+36`, `src/java.net.http/share/classes/java/net/http/HttpHeaders.java` <https://github.com/openjdk/jdk/blob/jdk-25%2B36/src/java.net.http/share/classes/java/net/http/HttpHeaders.java>.
45. Unvalidated method in another client (supporting only). "The method field remains unvalidated." Mint, CVE-2026-48861, published 2026-06-02 <https://osv.dev/vulnerability/CVE-2026-48861>.

Repository claims cited, not load-bearing: the types and their validation contract at `docs/plans/55-http-client-stack/architecture.md:390-400`; the `Untrusted` refusal at `architecture.md:441-443`; the cross-origin strip at `architecture.md:497-499`; logging never prints headers at `architecture.md:652`; request validation measurements at `docs/plans/55-http-client-stack/research/http-transport.md:124-131`; the transport's request conversion at `http-transport.md:278`; the `Result`/`require` convention at `docs/plans/55-http-client-stack/research/api-shape-and-failures.md:259-260`; the fixture format at `docs/plans/55-http-client-stack/research/test-support-and-time.md:277`.
