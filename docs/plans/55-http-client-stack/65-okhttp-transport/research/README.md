# Research: the OkHttp transport and its loopback test factories (`okhttp-transport`)

[SpartanLaboratories/WebTools#65](https://github.com/SpartanLaboratories/WebTools/issues/65), unit 6
(`okhttp-transport`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.3.2, with §6.2, §6.3.1, §6.6, §6.7, §7.3, §7.4, §9 and
§11 unit 6) and the systems-level consequences [`../../consequences.md`](../../consequences.md) (§7 and
§8). This folder researches the implementation details that design leaves to the unit and that need
evidence from outside this repository.

## The work

`webtools-http` gains its supplied transport and the fixtures that run it over loopback:

- **Public, in `jvmMain`** (package `com.spartanlabs.webtools.http`, Stable Core, usable without the
  opt-in): `OkHttpTransport`, a final `HttpTransport` built by the factory
  `OkHttpTransport(userAgent, policy)` over an `internal` constructor that also takes a `UrlGuard` (the
  policy and its relaxation), a base `okhttp3.Dns` and an optional donor `OkHttpClient`.
- **Inside it** (`internal`):
  - the client: a new `Dispatcher` and `ConnectionPool` per transport, even when derived from a donor;
    inherited interceptors, cache, cookie jar and authenticators cleared; `retryOnConnectionFailure(false)`,
    `followRedirects(false)`, `followSslRedirects(false)`; the timeouts of the policy's
    `TransportSettings`; and the policy's resolver, socket factory and proxy choice
    (`UrlGuard.enforcement(baseDns)`);
  - `ExchangeGuard`, a network interceptor that refuses a second wire exchange for one hop, and hides a
    `503`'s `Retry-After` from OkHttp's follow-up logic, restoring it in the response;
  - the redirect loop: `UrlGuard.checkRequest` before hop 1 and `UrlGuard.admitRedirect` before every later
    hop; one `Call` per hop under one deadline shared by every hop; `GET` and `HEAD` only; the sensitive
    headers stripped on a cross-origin hop; each followed hop recorded in `HttpResponse.redirectChain`;
  - the exchange: a cancellable suspend bridge over `Call.enqueue` whose callback reads the capped, decoded
    body itself, so that cancelling the calling coroutine stops a body read at once;
  - the failure mapping onto `TransportFailure`, `close()`, and the transport's logging.
- **Public, in the test fixtures** (package `com.spartanlabs.webtools.http.testing`, Stable Core with source
  compatibility only): `LoopbackTransports.untrusted` and `trusted`, which build the real transport for a
  loopback `mockwebserver3` server (`untrusted` with the loopback relaxation and every other rule live,
  `trusted` with cleartext allowed); `MockWebServer.webtoolsUrl(path)`; `Fixture.toMockResponse()`; and
  `MockWebServer.enqueue(fixture)`.
- **Tests:** Level 3 against `mockwebserver3` 5.5.0 and Level 4c, as listed under question 1.

What shapes the questions below:

- **The port's contract** (`HttpTransport`, built). Exactly one request per hop, never a retry. The
  documented caps are reported as `BodyTooLarge`, `TooManyRedirects` or `Timeout`, whatever the status. A
  `304` is a response. A status outside 100..599 is `Protocol`. A received header whose name is not a token
  is dropped, and CR, LF and NUL in a received value become SP. Cancellation is thrown, never wrapped.
  After `close()`, which is idempotent and does not throw, every call returns `Closed("transport")`. Calls
  may be concurrent, and `execute` is main-safe.
- **Request headers the transport controls.** A request carrying a header the transport itself must
  control is refused with `TransportFailure.InvalidRequest` before anything is sent: no socket is opened,
  and the rule text is constant and names the header. The set holds `User-Agent`, `Accept-Encoding`,
  `Host`, `Connection`, `Content-Length` and `Transfer-Encoding`, and any other name whose caller-supplied
  value would change the exchange's framing, its decoding or the host the request reaches. The transport
  sends its own `User-Agent`, the factory's required, validated value.
- **Redirects.** Only `GET` and `HEAD` are followed. `https` to `http` is followed only when the settings
  allow it; the policy judges that. Every entry `HttpHeaders.Entry.isSensitive` reports (flagged entries,
  and `Authorization`, `Proxy-Authorization`, `Cookie` and `Set-Cookie`) is removed on a hop whose scheme,
  host or effective port differs. At most `maxRedirects` hops are followed, and a followable redirect
  beyond that is `TooManyRedirects(maxRedirects)`, so 0 follows none. A `3xx` the transport does not
  follow is returned as the response. A `Location` is resolved with OkHttp's `HttpUrl.resolve`, then judged
  by `UrlGuard.admitRedirect`, which parses it with `Url.parse`.
- **The body.** The cap counts decoded bytes. A declared length over the cap is refused before reading.
  No body is read for `HEAD`, `1xx`, `204`, `304` or a followed redirect, and the published fake
  (`FakeHttpTransport`) applies its cap by the same no-body rule.
- **Failures** (built). `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`, `Io`,
  `InvalidRequest(rule)`, `RefusedByPolicy(reason)`, `BodyTooLarge(limit, declared)`,
  `TooManyRedirects(limit)` and `RetryRefused`, each with and without a `cause`. Messages are constant.
  OkHttp's exception becomes a `cause` only where what its message holds is acceptable, and its message is
  never logged above `DEBUG`. A policy refusal is found with `policyRefusal()` (the cause chain and the
  suppressed lists) before any other mapping, and is logged once at `WARN` with `Url.redacted()`, the
  reason name and the range class.
- **Logging** (architecture §6.6). `INFO` on create and close, with the settings' numbers only; `DEBUG`
  one line per call and per redirect hop; `WARN` for a policy refusal and for `RetryRefused`. Never
  headers, bodies, query strings, credentials, resolved addresses or OkHttp objects.
- **The fixtures** keep exactly their published dependencies (`webtools-http`, `kotlinx-coroutines-test`
  1.10.2, `mockwebserver3` 5.5.0); the publication check asserts the list. A library a test needs, such as
  OkHttp's TLS test helper, may be added to the module's own tests only.
- **Toolchain.** Kotlin 2.2.0 with `explicitApi()`, and `-Xexplicit-api=strict` on the fixtures. A single
  `jvm()` target compiled with `jvmTarget = 11` and `-Xjdk-release=11`, so only the Java 11 API is
  available, tests included. `okhttp-jvm` 5.5.0 and `kotlinx-coroutines-core` 1.10.2. JUnit Jupiter
  5.10.1 through `kotlin("test-junit5")`. The level tests run on the build JDK (17 to 26) under Gradle
  9.7.1, selected by package (`com.spartanlabs.testing.<level>`). Level 3 runs on real time under
  `runBlocking`, never `runTest`.

## Existing findings this research builds on

These are current: the same OkHttp, `mockwebserver3`, coroutines and Kotlin versions, and nothing newer
contradicts them; the url-policy unit measured several of them again as built. They are cited, not
researched again.

- **[`../../research/http-transport.md`](../../research/http-transport.md).** The two re-send paths no
  client flag disables, with measured request counts per scenario (F1); stale pooled connections under
  `retryOnConnectionFailure(false)` and the idle TTL (F2); the exchange counter and the hiding of
  `Retry-After` (F3); interceptor order, OkHttp's own redirect follow-up, and why the transport follows
  redirects itself (F4); the suspend bridge with the body read inside `onResponse` (F5); the cap's reader
  and its measured cases, gzip, `HEAD` and `304` included (F6); timeouts and the exceptions OkHttp throws
  (F7); request validation (F8); lifecycle, sharing and shutdown (F9); credentials, logging and the
  `User-Agent` (F10).
- **[`../../research/test-support-and-time.md`](../../research/test-support-and-time.md).** Real I/O under
  `runTest` (F3). `mockwebserver3` 5.5.0 facts (F6): loopback binding and port 0, non-daemon threads, the
  blocking queue dispatcher, `Content-Length` rewritten from the body, and `@StartStop` needing JUnit 5.13.
- **[`../../64-url-policy/research/jvm-enforcement.md`](../../64-url-policy/research/jvm-enforcement.md).** The
  resolver and socket guards' hooks (F1 to F6), and how a refusal surfaces through an OkHttp 5.5.0 call,
  the call timeout's disguise included (F8).
- **[`../../64-url-policy/research/bypass-vectors.md`](../../64-url-policy/research/bypass-vectors.md) §5.** The
  transport's own vectors, T01 to T08 and T10.
- **[`../../62-http-core/research/header-and-method-validation.md`](../../62-http-core/research/header-and-method-validation.md).**
  OkHttp's request and response header rules for HTTP/1.1 and HTTP/2, and the JDK client's restricted
  request headers (F12); what `mockwebserver3` can write (F13).
- **[`../../62-http-core/research/url-parsing.md`](../../62-http-core/research/url-parsing.md).**
  `HttpUrl.resolve` throwing for `//[1:2:3:4:5:6:7::8]/` (F6), and where `Url.parse` is stricter than
  `HttpUrl` (F4, F5).

The codebase blast radius is small and is surveyed directly in the summary below: the unit adds new files
to `webtools-http`, extends its ABI dump, changes the published fake's cap rule and the README, and has no
caller in the repository yet.

## Questions

### 1. How is each required Level 3 and Level 4c scenario scripted and asserted against `mockwebserver3` 5.5.0, and what must the four loopback fixtures do?

- **Scenarios.** For each, the evidence must give the server script, the client call and the assertion.
  - Level 3:
    - `requestCount == 1` per call for a `503` with `Retry-After: 0`, a `408`, a `429`, a pooled
      connection the server drops after reading the next request, and each redirect hop;
    - a `304` carrying `Content-Length` and no body, returning promptly;
    - the body cap with and without a declared length, and a gzip bomb;
    - cancelling a call while an endless body streams;
    - `close()` letting the JVM exit;
    - a redirect's second hop refused, with the second server at 0 requests;
    - cross-origin stripping, read from the second server's recorded headers;
    - the unrelaxed `Untrusted` policy refusing a loopback server;
    - DNS rebinding across new connections, and a mixed resolver answer, through a stub resolver;
    - `Untrusted` ignoring the JVM-wide proxy settings (`http.proxyHost`, `https.proxyHost`,
      `socksProxyHost`) while `Trusted` uses them.
  - Level 4c: a slow drip ended by the call timeout; an endless chunked body ended by the cap; a redirect
    chain longer than the cap.
- **Options.**
  - Per scenario, the `mockwebserver3` 5.5.0 mechanism: `MockResponse.Builder` settings, `SocketEffect`,
    `MockResponseBody` (a streaming or endless body), `throttleBody`, `headersDelay` and `bodyDelay`, the
    queue dispatcher or a custom `Dispatcher`, two servers.
  - Per scenario, the assertion: `requestCount`, `takeRequest` with a timeout, `RecordedRequest.headers`.
  - For "`close()` lets the JVM exit": a child JVM launched from the test, or a check of the live
    non-daemon threads the transport started.
- **The four fixtures.**
  - `MockWebServer.webtoolsUrl(path)`: which host the `Url` names, so that the server is listening there
    and `LoopbackTransports.untrusted`'s relaxation admits it on every OS. The relaxation exempts exactly
    the host `localhost`, the addresses `127.0.0.0/8` and `::1`, and the port rule for the hosts
    `localhost`, `127.0.0.1` and `::1`.
  - `Fixture.toMockResponse()` and `MockWebServer.enqueue(fixture)`: which of a fixture's headers
    `mockwebserver3` writes as given, and which framing or encoding headers (`Content-Length`,
    `Transfer-Encoding`, `Content-Encoding`, `Connection`) must be dropped or rewritten so that a fixture
    cannot hang or corrupt the exchange; how a `204`, a `304` and a response to `HEAD` are written.
  - `LoopbackTransports.untrusted(userAgent, policy, resolve)` and `trusted(userAgent, policy, resolve)`:
    the shape a consumer's test reads best in Kotlin (an object with functions, defaulted parameters, the
    stub resolver's type).
- **Requirements.** The Java 11 API; no new fixtures dependency; real time under `runBlocking`; no fixed
  port; every server closed; tests that pass on Windows, Linux and macOS; nothing that needs the network.
- **Evidence that settles it.** `mockwebserver3` 5.5.0 and `okhttp-jvm` 5.5.0 source at tag
  `parent-5.5.0`; OkHttp's own tests that script the same situations; small probes run outside the
  repository.
- **Findings file:** [`loopback-test-mechanics.md`](loopback-test-mechanics.md).

### 2. Which request header names does the transport refuse, and what does OkHttp 5.5.0 do with each if it is sent?

- **Options.**
  - The six names above alone.
  - Those six plus the connection-specific names of RFC 9110 §7.6.1 and RFC 9113 §8.2.2 (`Keep-Alive`,
    `Proxy-Connection`, `TE`, `Trailer`, `Upgrade`), and `Expect`.
  - The forbidden request-header names of the WHATWG Fetch standard as a model, with or without its
    `Proxy-` and `Sec-` prefixes.
  - The restricted headers of the JDK's `java.net.http` client (`connection`, `content-length`, `expect`,
    `host`, `upgrade`), and Ktor's unsafe headers.
- **Requirements.**
  - A refused name is one whose caller-supplied value would change the exchange's framing, its decoding,
    the host or protocol the request reaches, or the transport's own `User-Agent`. A name that is merely
    unusual is not refused.
  - The refusal happens before any socket. It compares names with an ASCII-only fold, as `HttpHeaders`
    does, and its rule text is constant and names the header.
  - The set holds for HTTP/1.1 and HTTP/2 alike: a name OkHttp drops silently on one protocol and sends on
    the other is a candidate.
  - Credential names are not part of this set: `Untrusted` already refuses a sensitive header, and
    `Trusted` sends one.
- **Evidence that settles it.** OkHttp 5.5.0's `BridgeInterceptor`, `CallServerInterceptor`,
  `Http1ExchangeCodec` and `Http2ExchangeCodec` at `parent-5.5.0`: what each name does when a caller sets
  it (kept, overridden, dropped, or able to break the framing). RFC 9110 and RFC 9113; the Fetch standard;
  the JDK, Ktor, Go `net/http` and Apache HttpClient 5 sources; probes against a loopback server for the
  cases the source leaves unclear.
- **Findings file:** [`transport-controlled-headers.md`](transport-controlled-headers.md).

### 3. Hop by hop, when does the transport follow a `3xx`, return it, or fail, and what does a followed hop carry?

- **Options.**
  - The statuses followed for `GET` and `HEAD`: `301`, `302`, `303`, `307` and `308`, and whether `300`
    joins them (OkHttp follows it; the Fetch standard does not count it as a redirect status).
  - Each shape of `Location`: absent; empty; relative; scheme-relative; a scheme other than `http` or
    `https`; one `HttpUrl.resolve` returns `null` for; one it throws on; one `Url.parse` refuses; one the
    policy refuses; one equal to the current URL. The outcome of each: the `3xx` returned as the response,
    `RefusedByPolicy(reason)`, or another failure.
  - The order of the cap check and the target's admission when both apply.
  - What a followed hop carries: every request header except the stripped sensitive ones (conditional
    headers, `Range` and `Accept` included or not); whether a header stripped once stays stripped when a
    later hop returns to the first origin; the method for a `303` after `HEAD`.
- **Requirements.** The redirect rules above. The decision is made on the canonical `Url` (scheme, host,
  effective port), never on the `Location` text. Nothing the server sends can make the transport open a
  socket to a target the policy has not admitted, and no exception from resolving a `Location` escapes the
  loop.
- **Evidence that settles it.** RFC 9110 §15.4 and §10.2.2; the Fetch standard's HTTP-redirect fetch;
  OkHttp 5.5.0's `RetryAndFollowUpInterceptor` and `HttpMethod`; Ktor's `HttpRedirect`; the JDK
  `java.net.http` client's redirect handling; Go's `net/http` client; http4k's `FollowRedirects`; probes of
  `HttpUrl.resolve` on the `Location` shapes above.
- **Findings file:** [`redirect-decision-table.md`](redirect-decision-table.md).

### 4. Which exceptions can OkHttp 5.5.0 hand the transport, what does each carry, and how does each map to the failure model and its `cause`?

- **Options.**
  - The mapping, first match wins: the policy markers (through `policyRefusal()`); the exchange guard's own
    marker; `close()` and cancellation; `UnknownHostException`; the call, connect, read and write
    timeouts; TLS; connect; protocol; HTTP/2 stream and connection failures; a response status outside
    100..599; other `IOException`s; and a residual `IllegalArgumentException` from building the OkHttp
    request.
  - The `cause`: always attached, never attached, or attached only for exceptions whose message holds
    nothing beyond what the failure's own fields already disclose (no URL with its query, no resolved
    address, no header value).
  - Telling apart a call cancelled by `close()`, a call cancelled because the calling coroutine was
    cancelled, a call timeout and a socket timeout.
  - The bridge on kotlinx-coroutines 1.10.2: `suspendCancellableCoroutine`, `invokeOnCancellation`, which
    `resume` overloads exist and are stable, the prompt-cancellation guarantee, and how to resume exactly
    once on every path when the body is read inside `onResponse`.
  - `close()`: `Dispatcher.cancelAll()`, shutting down the dispatcher's `ExecutorService`,
    `ConnectionPool.evictAll()`, what a call enqueued after that shutdown receives, and whether the
    dispatcher's threads should be daemon threads with their own names.
- **Requirements.** Cancellation is thrown, never returned. Every path resumes the continuation exactly
  once. A failure's message stays constant. Nothing OkHttp-typed leaves `jvmMain`. The mapping is total:
  an unknown exception becomes `Io` or `Protocol`, never an escape. A policy refusal wins wherever it sits
  in the cause chain or the suppressed lists.
- **Evidence that settles it.** OkHttp 5.5.0 source at `parent-5.5.0` (`RealCall` and its `AsyncCall`,
  `Dispatcher`, `RealConnectionPool`, the exchange finders, `Http1ExchangeCodec`, `Http2Stream`,
  `StatusLine`, `RetryAndFollowUpInterceptor`); kotlinx-coroutines 1.10.2 source and KDoc; the bridges of
  `okhttp-coroutines` 5.5.0, Retrofit 3.0.0, Coil 3 and Ktor's OkHttp engine; probes for the messages and
  the surfacing the source leaves unclear.
- **Findings file:** [`okhttp-failure-surface.md`](okhttp-failure-surface.md).

## Verification

Every load-bearing claim of the four findings files was re-opened at its source on 2026-11-01: 85 claims,
plus two that the summary below rests on (X1, X2). **All hold; none fails and none is unclear.** The
measured results (the closing claim of [`loopback-test-mechanics.md`](loopback-test-mechanics.md) and the
closing paragraph of [`okhttp-failure-surface.md`](okhttp-failure-surface.md)) are probe output with no source
to re-open.

| File | Claims | Result |
|---|---|---|
| [`transport-controlled-headers.md`](transport-controlled-headers.md) | 1 to 20 | hold; 3 and 10 qualified |
| [`redirect-decision-table.md`](redirect-decision-table.md) | 1 to 19, with 18a | hold; 1 qualified |
| [`loopback-test-mechanics.md`](loopback-test-mechanics.md) | 1 to 20 | hold (21 is measured) |
| [`okhttp-failure-surface.md`](okhttp-failure-surface.md) | 1 to 27 | hold; 1 qualified |
| X1, RFC 9110 §12.5.3 | `Accept-Encoding: identity` | holds, qualified |
| X2, OkHttp 5.5.0 `BridgeInterceptor` | a request's own `Accept-Encoding` | holds |

The qualifications, none of which changes a conclusion:

- **transport-controlled-headers 3.** OkHttp removes `Transfer-Encoding` only for a body of known length; a
  body of unknown length gets `Transfer-Encoding: chunked` and loses `Content-Length`. The transport's
  bodies are byte arrays of known length.
- **transport-controlled-headers 10.** Ktor's quoted message is in `HttpHeaders.kt` (`UnsafeHeaderException`),
  which `HttpClientEngine.kt` throws.
- **redirect-decision-table 1.** `followUpRequest` also acts on `401`, `407`, `408`, `421` and `503`; "nothing
  else" holds for the redirect statuses.
- **okhttp-failure-surface 1.** An `InterruptedException` in the chain is not rethrown on the dispatcher
  thread (the interrupt flag is set again instead); every other `Throwable` is.
- **X1.** RFC 9110 §12.5.3: "An "identity" token is used as a synonym for "no encoding" in order to
  communicate when no encoding is preferred." A request that lists only `identity` makes every other
  content coding unacceptable, and a server that cannot honour the list "SHOULD send a response without any
  content coding". It is a SHOULD: a server that sends a coding anyway is answered with the bytes as
  received, `Content-Encoding` included.
- **X2.** `BridgeInterceptor` reads a request's `Accept-Encoding` and `Range` only in its condition and never
  rewrites a request's own `Accept-Encoding`; with either present it neither adds `gzip` nor decodes.

**Source movement.** `github.com/square/okhttp` now redirects to `github.com/lysine-dev/okhttp`; the cited
`parent-5.5.0` URLs still resolve and the tag is intact. OkHttp 5.5.1 changes this area only by one more
`SSLPeerUnverifiedException` message of the same type.

**Did not hold in the build.** The TLS-to-plain result of [`okhttp-failure-surface.md`](okhttp-failure-surface.md) F1
(row 6, `SSLHandshakeException: Remote host terminated the handshake`; the same row is in
[`../../research/http-transport.md`](../../research/http-transport.md) F7) does not extend to a plain listener that
closes the connection on accept. [`../final-implementation.md`](../final-implementation.md) D4 has what the build
measured and the deviation it caused.

## Summary

What the four findings establish, the approach each one leads to, and where the sources disagree. The
findings files hold the evidence, the per-scenario detail and the quoted sources.

### 1. The loopback tests and the four fixtures

[`loopback-test-mechanics.md`](loopback-test-mechanics.md).

- **Every required scenario fits `mockwebserver3` 5.5.0 as it is.** The default queue dispatcher with
  `MockResponse.Builder` covers them: `onResponseStart(CloseSocket())` for a pooled connection dropped
  after the server read the next request, `throttleBody` for the slow drip, `chunkedBody` and a hand-written
  endless `MockResponseBody` for the caps and the cancel test, and a gzip body built in the test for the
  bomb. Only the redirect chain longer than the cap needs a small custom `Dispatcher`.
- **What a test asserts.** `requestCount` counts a request as soon as the server has read it, before it
  answers, so the count is final once the call has returned. Header checks read `takeRequest(1, SECONDS)`;
  connection reuse reads `connectionIndex` and `exchangeIndex`. An untimed `takeRequest()` is never used.
- **A new connection** is forced by the server's `Connection: close` (a second resolver lookup in 10 runs
  of 10); `CloseSocket` at the end of a response is a race that reuses a dead connection (0 of 10).
- **"`close()` lets the JVM exit"** is proved by a child JVM started from the test with `java.home` and
  `java.class.path`, both correct inside a Gradle 9.7.1 test worker; a check of live threads cannot tell a
  missed thread from one still exiting.
- **`MockWebServer.webtoolsUrl(path)`** names the bound address as an IP literal (`127.0.0.1`, or `[::1]`),
  never `hostName` or `url()`, which reverse-resolve an address created without a name. The literals are
  exactly the hosts the loopback relaxation exempts from the port rule, and a literal never reaches the
  resolver.
- **`Fixture.toMockResponse(method)`** derives the framing from the body instead of copying it: a fixture's
  `Content-Length` and `Transfer-Encoding` are never written as given; a `204` or `205` gets no body and no
  `Content-Length`; a `304`, and a response to `HEAD`, write no body and keep the declared length; a `1xx`
  fixture is refused, because it cannot be a final response. **`MockWebServer.enqueue(fixture, method)`**
  fails with a clear message when a custom dispatcher has replaced the queue. The request method is a
  defaulted parameter (`GET`), because only a response to `HEAD` is written differently.
- **`LoopbackTransports`** is an `object` with `untrusted(userAgent, policy, resolve)` and
  `trusted(userAgent, policy, resolve)`. The policy is a whole `OutboundUrlPolicy.Untrusted` or
  `OutboundUrlPolicy.Trusted` with a default, because the settings live on the policy and the rebinding and
  mixed-answer tests need `allowedPorts` and `mixedAnswers` through the factory; `trusted` turns cleartext
  on and keeps everything else. The resolver is a function type, `(host: String) -> List<InetAddress>`,
  last, defaulting to the system resolver, so a test writes a trailing lambda. Both return the
  `OkHttpTransport`, which the caller closes.
- **HTTPS loopback** for the one `https` to `http` hop uses `okhttp-tls` 5.5.0 (Java 8 bytecode) in the
  module's own tests only; the test builds the transport through its `internal` constructor with a donor
  client that carries the trust, and pins the server to HTTP/1.1.
- **Proxies.** A second `MockWebServer` stands in for an HTTP proxy, with `http.nonProxyHosts` set to `""`
  (its default skips every loopback literal); the SOCKS case reuses the counting raw listener of the
  existing SOCKS test. Both set process-wide properties, so each class is `@Isolated` and restores them.
- **A refused second hop** points the first server's `Location` at `second.test:<the second server's
  port>`, resolved by the stub resolver to `127.0.0.1`: the port rule refuses it (the relaxation exempts the
  port rule only for `localhost`, `127.0.0.1` and `::1`), and an `allowedPorts = null` policy is the
  positive control.

### 2. The request headers the transport controls

[`transport-controlled-headers.md`](transport-controlled-headers.md).

- **OkHttp 5.5.0 lets a caller's value win** for `Host`, `Connection`, `Accept-Encoding` and `User-Agent`,
  and for `Content-Length` and `Transfer-Encoding` on a request without a body; its HTTP/2 codec drops the
  connection-specific names its HTTP/1.1 writer sends (measured on both protocols).
- **The refused set is twelve names:** `User-Agent`, `Accept-Encoding`, `Host`, `Connection`,
  `Content-Length`, `Transfer-Encoding`, `Keep-Alive`, `Proxy-Connection`, `TE`, `Trailer`, `Upgrade` and
  `Expect`. Each changes the framing, the decoding, the target host, the protocol or the exchange's flow on
  at least one protocol, or behaves differently on HTTP/1.1 and HTTP/2. A request carrying one is refused
  before anything is sent with `InvalidRequest("header <Name> is controlled by the transport")`, the name in
  the list's spelling; the first such entry in request order is the one named.
- **`Range` is admitted.** With `Range` OkHttp adds no `Accept-Encoding` and decodes nothing, and a server
  may then answer with any content coding (measured). The transport therefore sends `Accept-Encoding:
  identity` itself on a request that carries `Range`, so the body stays the decoded representation without
  refusing a caller's range request. The findings file recommends refusing `Range`; the summary does not,
  because the refused set holds the names whose caller value the transport itself must control, and `Range`
  interferes only with the transport's own `Accept-Encoding`, which the transport sets.
- **Left out:** `Content-Encoding`, `Via`, `Origin`, `Referer`, `Date`, `Accept-Charset`, the other `Proxy-`
  names and the `Sec-` prefix. OkHttp sends them verbatim on both protocols with no effect on the exchange.
- **Where the check runs:** after the `Closed` check and before `UrlGuard.checkRequest`, once, on the
  request passed to `execute`. A redirect hop carries a subset of those headers plus what the transport sets
  itself, so it needs no second check. The list is plain text compared with `HttpSyntax.equalsName`, so it
  lives in `commonMain`, where the call facades can use it as well.

### 3. The redirect decision

[`redirect-decision-table.md`](redirect-decision-table.md).

- **Followed:** `301`, `302`, `303`, `307` and `308`, for `GET` and `HEAD` only, as the Fetch standard, the
  JDK client, Go and Ktor follow. `300`, `304`, `305`, `306` and every other `3xx` are returned as the
  response; OkHttp and http4k also follow `300`, which is not a redirect in the Fetch sense.
- **The checks, first match wins:** the method and the status (return the `3xx`); no single usable
  `Location`, meaning none, all empty or whitespace, or several different values (return the `3xx`); the
  cap, `TooManyRedirects(maxRedirects)`, so `maxRedirects = 0` never reports a policy refusal; resolving and
  admitting the target, `RefusedByPolicy` with `Scheme` for a scheme other than `http` or `https`,
  `HostForm` for a `Location` that `HttpUrl.resolve` returns `null` for or throws on, and otherwise the
  reason `UrlGuard.admitRedirect` gives; the shared deadline, `Timeout` when less than 1 ms remains (a
  timeout of 0 would lift the limit); then follow.
- **A followed hop** carries the original method (`HEAD` stays `HEAD` after a `303`), no body, no `Referer`,
  and the original headers minus every sensitive entry removed on a hop whose scheme, host or effective port
  differs. A removal is permanent: a later hop back to the first origin does not restore it. Conditional
  headers, `Range` and `Accept` go along. The next request is built from the admitted `Url`'s text, because
  `HttpUrl.resolve` has already rewritten backslashes, decoded a `%65` and dropped TAB and LF: what is
  judged is what is connected.
- **The result:** `HttpResponse.url` is the last URL requested and `redirectChain` lists each followed hop
  with its status; a `Retry-After` on a followed `3xx` is not honoured (no compared client honours it); a
  `3xx` that is returned keeps its headers.
- **Where it runs:** the decision is a pure `internal` function that takes `HttpUrl.resolve` as a parameter,
  so a Level 4a suite drives every row without a network. It runs inside `Callback.onResponse` before the
  body is read, so a followed `3xx`'s body is never read and a returned one is read under the cap.

### 4. The failure mapping, the bridge and `close()`

[`okhttp-failure-surface.md`](okhttp-failure-surface.md).

- **Mapping by type and by who cancelled, never by message.** First match wins: the calling coroutine
  cancelled (nothing is mapped or logged); a policy marker anywhere in the cause chain or the suppressed
  lists (`RefusedByPolicy`); the exchange guard's marker, found by the same walk (`RetryRefused`); the
  transport closed (`Closed("transport")`); `UnknownHostException` (`DnsFailed` with the hop's host); a
  socket timeout or the call timeout (`Timeout`); TLS (`TlsFailed`); connect (`ConnectFailed`);
  `ProtocolException` and a status outside 100..599 (`Protocol`); OkHttp's `canceled due to` wrapper
  (`Protocol` for a negative status, `Io` otherwise); every other `IOException` (`Io`); an
  `IllegalArgumentException` while building the request (`InvalidRequest` with a constant rule). The call
  timeout wraps the real failure, so both marker walks run before any type test. OkHttp's `internal` HTTP/2
  exception classes are never named; they are `IOException`s and map to `Io`.
- **The `cause`** is kept only for `Timeout`, `DnsFailed` and `ConnectFailed` (the JDK exception inside
  OkHttp's `Failed to connect to <address>` wrapper, never the wrapper). Every other leaf has none: their
  messages hold server bytes, certificate names, the resolved address or a header value.
- **The bridge** is `suspendCancellableCoroutine<Result<HttpResponse>>` with `invokeOnCancellation {
  call.cancel() }` registered before `enqueue`, and each callback resumes the continuation with a value,
  exactly once, from a `finally`. Resuming with an exception is avoided: under `-ea`, which the Gradle test
  JVMs use, kotlinx-coroutines 1.10.2 copies a thrown failure whose only constructor argument is its cause
  and sets the original as the copy's cause (measured).
- **Three OkHttp behaviours** need handling: a throw out of `onResponse` leaves the continuation unresumed
  (hence the `finally`); a non-`IOException` in the chain, such as the `IllegalStateException` a server-sent
  negative status causes, is rethrown on the dispatcher thread; and a `407` through a direct route throws
  `ProtocolException` instead of being returned, which the port's "any status in 100..599 is a response"
  rule does not allow. The exchange guard therefore hides a `407` as it hides a `503`'s `Retry-After` (a
  placeholder status, the original kept in a private header the guard first strips from what the server
  sent), and the response restores it.
- **`close()`** sets the closed flag, then cancels every call (`Dispatcher.cancelAll()` reaches a body read
  inside `onResponse`), shuts the dispatcher's `ExecutorService` down (not `shutdownNow()`), and evicts the
  idle connections, each step inside `catchNonFatal`. A call enqueued after the shutdown fails at once, on
  the enqueuing thread, which is why the cancellation handler is registered first and why `execute` checks
  the flag before every hop.
- **The dispatcher gets its own `ExecutorService`:** the shape of OkHttp's default, with daemon threads under
  a recognisable name and an uncaught-exception handler. A forgotten `close()` then costs nothing at JVM exit
  (0.4 s with or without `close()`, measured, against the 61 s linger of OkHttp's non-daemon default), and a
  hostile status line cannot print a stack trace or kill a thread. The connection pool keeps at most
  `maxConcurrentRequests` idle connections for `idleConnectionTtl`.

### 5. Codebase blast radius (surveyed directly)

- **No caller exists.** No production code calls an `HttpTransport` yet; its only implementations are the
  published fake and the recorder's decorator in the fixtures. OkHttp is used in `webtools-http`'s
  `jvmMain` only by the policy's resolver and socket guards.
- **New files:** the transport and its parts in `jvmMain`; the controlled-header list in `commonMain` (so the
  check module compiles it for `js` too); the four fixtures; the tests and their support classes.
- **Changed files:**
  - `FakeHttpTransport.kt`: its cap skips a response to `HEAD` and a `1xx`, `204` or `304`, as the transport
    reads no body for them, with one component test and a KDoc line;
  - `PolicyEnforcement.kt`: its KDoc names the transport instead of the issue that builds it;
  - `webtools-http/build.gradle.kts`: `okhttp-tls` 5.5.0 for `jvmTest` only, and the fixtures comment about
    `mockwebserver3` in the present tense;
  - `api/webtools-http.api`: the transport class and its factory;
  - `README.md`: the `webtools-http` row and the fixtures paragraph;
  - `CHANGELOG.md`, created with this change's entry under `## [Unreleased]`.
- **Unchanged:** the three legacy modules, `build-logic`, the check module's sources, and every public type
  of `webtools-http` other than the additions above.

### Where the sources disagree, and which view holds

- **`Range`:** refused in the findings, admitted here with `Accept-Encoding: identity` (topic 2).
- **An unresolvable or foreign-scheme `Location`:** the earlier transport probe
  ([`../../research/http-transport.md`](../../research/http-transport.md) F4) returned the `3xx`, as OkHttp
  does by side effect; the Fetch standard and Go treat it as an error. A refusal holds: a `file:` or
  `gopher:` target then fails like a denied address, the outcome the transport's bypass vectors require,
  and neither opens a socket.
- **`300`:** OkHttp and http4k follow it; Fetch, the JDK, Go and Ktor do not. Not following holds.
- **The cap before or after admission:** Go, Fetch and OkHttp check the target first, http4k the cap first.
  The order is observable here because the two failures differ, and cap-first keeps `maxRedirects = 0`
  meaning "no redirects".
- **Daemon dispatcher threads:** no compared client configures them; they hold because this is a library
  whose callers' lifecycles are unknown, and the measurements show both benefits.

### Options that do not fit

- Refusing `Range`, or admitting it without `identity` (topic 2).
- Following `300`; returning the `3xx` for a foreign or unresolvable target; admitting before the cap; loop
  detection beyond the cap (topic 3).
- Attaching every OkHttp exception as the `cause`; resuming the continuation with an exception; naming
  OkHttp's `internal` HTTP/2 classes; telling timeouts apart by message (topic 4).
- `localhost` as the loopback URL host; a thread check in place of the child JVM; `@StartStop`, which does
  not close servers on JUnit 5.10.1 (topic 1).
