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
- **[`../64-url-policy/research/jvm-enforcement.md`](../64-url-policy/research/jvm-enforcement.md).** The
  resolver and socket guards' hooks (F1 to F6), and how a refusal surfaces through an OkHttp 5.5.0 call,
  the call timeout's disguise included (F8).
- **[`../64-url-policy/research/bypass-vectors.md`](../64-url-policy/research/bypass-vectors.md) §5.** The
  transport's own vectors, T01 to T08 and T10.
- **[`../62-http-core/research/header-and-method-validation.md`](../62-http-core/research/header-and-method-validation.md).**
  OkHttp's request and response header rules for HTTP/1.1 and HTTP/2, and the JDK client's restricted
  request headers (F12); what `mockwebserver3` can write (F13).
- **[`../62-http-core/research/url-parsing.md`](../62-http-core/research/url-parsing.md).**
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
