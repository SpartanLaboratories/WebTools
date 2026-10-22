# The suspend transport port and its OkHttp default

## Question

What shape should the suspend transport port take in `commonMain` of `webtools-http`, and how does `OkHttpTransport` realise it on `okhttp-jvm` 5.5.0 with `kotlinx-coroutines` 1.10.2, Kotlin 2.2.0 and Java 11 bytecode?

An answer has to settle: the request and response model; the timeouts, the body cap and the typed failure causes; redirect mechanics (a configurable cap, a per-hop policy hook, stripping of credential headers, scheme downgrade); a "never retries" guarantee that holds against OkHttp's own re-send logic; the suspend bridge and cancellation; concurrency and lifecycle; `User-Agent`, credentials and logging; and whether an observation hook is warranted. Requirements: the port uses no JVM type and no OkHttp type appears in a public signature; the failure channel is `kotlin.Result` with cancellation propagating; a `304` is a response; the construction path must let tests permit loopback for `mockwebserver3`.

Evidence labels used below: **documented** (official docs, a spec, or source at a pinned tag), **observed practice** (what a mature library visibly does), **measured** (run by me in a throwaway probe outside the repository: OkHttp 5.5.0, `mockwebserver3` 5.5.0, kotlinx-coroutines 1.10.2, Kotlin 2.2.0, Gradle 9.7.1, JDK 23 on Windows), **single source** (one author's opinion).

## Bottom line

- **No requirement of the work fails.** Two are met only at a stated cost. "The transport never retries" needs three mechanisms together and costs spurious failures on stale pooled connections, which a short idle-connection TTL removes. "No OkHttp type in a public signature" rules out a public consumer-supplied `OkHttpClient`; a derived-client hook stays `internal`.
- **The port.** `HttpTransport : AutoCloseable { suspend fun execute(request: HttpRequest): Result<HttpResponse> }` in `commonMain`, over five library-owned types: `HttpMethod` (a final class with constants, not an enum), `Url` (parsed once, `http`/`https` only, no user-info, a `redacted()` form for logs), `HttpHeaders` (ordered, case-preserving, case-insensitive lookup, validated, with per-entry "sensitive" flags), a `ByteArray` body with a content type, and `HttpResponse(status, headers, body: ByteArray, url, redirectChain)`. There is no streaming, no clock, no charset and no OkHttp-shaped extension slot. A probe compiled these types unchanged for `jvm()` and `js(IR)` under Kotlin 2.2.0 (measured).
- **Never retries.** `retryOnConnectionFailure(false)` plus `followRedirects(false)` is not enough: OkHttp 5.5.0 still re-sends on `503` with `Retry-After: 0` (measured: 2 requests reached the server). The working set, measured at 1 request per hop in every scenario, is: `retryOnConnectionFailure(false)`; the transport follows redirects itself, one OkHttp `Call` per hop; and one network interceptor that hides `Retry-After` on a `503` from OkHttp's follow-up logic, restoring it in the response, and refuses any second network exchange for a hop (fail closed, so it also covers a `421` or a future 5.x re-send path).
- **Bridge and cap.** Copy the `Call.executeAsync` shape, but read the capped body inside `Callback.onResponse` on OkHttp's thread and resume the continuation with finished bytes. Measured: cancelling during an endless body returned in 0 ms that way, against 29.4 s (the call timeout) when the body is read after a literal `executeAsync`. The cap applies to decoded bytes: a 50 MB gzip bomb was refused at the cap with no `Content-Length` to rely on.
- **Redirects belong in the transport, one hop per `Call`.** OkHttp's network interceptors run after the TCP connection exists (measured), it strips only `Authorization` on a cross-host hop (measured: `X-Api-Key` and `Cookie` reached the second host), and its cap of 20 is hard-coded. A manual loop gives a policy check before any socket, a configurable cap, stripping of every sensitive header, and a deadline shared across hops.
- **Lifecycle.** The transport always owns the `Dispatcher` and `ConnectionPool` of the client it builds, even when derived from another client. `close()` cancels in-flight calls, shuts down the dispatcher executor and evicts the pool; without it the JVM lingered 61 s after the last call (measured). No `EventListener` mirror: a decorating `HttpTransport` is the observation hook.

## Findings

### F1. OkHttp 5.5.0 has two re-send paths that no client flag disables (documented + measured)

`RetryAndFollowUpInterceptor` (tag `parent-5.5.0`, also unchanged on the default branch on 2026-10-21) builds a follow-up request in `followUpRequest`:

- `HTTP_UNAVAILABLE`: re-sends when `retryAfter(userResponse, Integer.MAX_VALUE) == 0`, i.e. a `503` carrying `Retry-After: 0`. Nothing gates it.
- `HTTP_MISDIRECTED_REQUEST` (`421`): re-sends on a different connection when the exchange used a coalesced HTTP/2 connection. Nothing gates it.
- `HTTP_CLIENT_TIMEOUT` (`408`): gated by `chain.retryOnConnectionFailure`.
- `recover(...)` (IOException during an exchange): gated by `chain.retryOnConnectionFailure`.
- Redirects: gated by `followRedirects`; `401`/`407`: no-ops while the authenticators are `Authenticator.NONE`.
- `MAX_FOLLOW_UPS = 20` is a `private const`; a cross-host redirect removes only `Authorization`.

Measured request counts at the server (`mockwebserver3` `requestCount`):

| Scenario (script) | default client | `retryOnConnectionFailure(false)` | + `followRedirects(false)`, `followSslRedirects(false)` | + hide `Retry-After` on 503 | tripwire alone |
|---|---|---|---|---|---|
| `503` + `Retry-After: 0`, then `200` | 2 (returns the `200`) | 2 | 2 | **1** (returns the `503`, `Retry-After` intact) | **1** (`IOException`, `503` lost) |
| `503` + `Retry-After: 5` | 1 | | | | |
| `408`, then `200` | 2 | **1** | | | |
| pooled connection dies after the server read request 2 (GET), then `200` | **3** (silent success) | **2** (`IOException`) | | | **2** (`IOException`; with the flag left `true`) |
| same, `POST` with a body | **3** | | | | |
| connection dropped on a fresh connection (single route) | 1 | 1 | | | |
| `401`, then `200` | 1 | | | | |

So a default OkHttp client delivers one request to the server twice in the stale-connection case, `POST` included. A provider that counts arrivals (OpenDota counts refused requests) counts both. Connect-phase fallback to a second IP of the same host is unaffected by the flag and sends nothing before it succeeds: with DNS returning `[::1 (nothing listening), 127.0.0.1]`, `retryOnConnectionFailure(false)` still reached the server with one request (measured).

OkHttp's own documentation of the flag: "Set this to false to avoid retrying requests when doing so is destructive. In this case the calling application should do its own recovery of connectivity failures." (documented, `OkHttpClient.Builder.retryOnConnectionFailure`.)

### F2. `retryOnConnectionFailure(false)` makes stale pooled connections fail, and a short idle TTL removes that (documented + measured)

- Source: `RealRoutePlanner` sets `doExtensiveHealthChecks = request.method != "GET"`, and `RealConnection.isHealthy` runs its read-probe only when the connection has been idle for `IDLE_CONNECTION_HEALTHY_NS` (10 s). A `GET` therefore reuses a connection the server already closed, and relies on the retry that the flag removes.
- Measured against a server that closes every connection after each response without announcing it: with the flag `false`, 50% of sequential `GET`s failed (`unexpected end of stream`), at gaps of 0, 100 and 1000 ms. With `ConnectionPool(0, ...)` or a `Connection: close` request header, 0 of 40 failed. A server that sends `Connection: close` also gave 0 failures.
- Measured against a keep-alive server that closes a connection after 1.5 s idle, calls 2 s apart, flag `false`: pool keep-alive 60 s gave 3 of 6 failures (the failed requests never reached the server application); pool keep-alive 1 s gave 0 of 6 failures.
- Server idle defaults to sit under: Apache httpd `KeepAliveTimeout 5`, Node `server.keepAliveTimeout` 5000 ms (documented).
- Effect on the budget: a stale-connection failure did not reach the server application in the measurement, so it does not double-count. The caller sees a transport failure (`Io`) and retries on its own schedule.

### F3. Hiding `Retry-After` plus a fail-closed exchange counter guarantees one wire exchange per hop (measured)

A network interceptor runs once per wire exchange, between `ConnectInterceptor` and `CallServerInterceptor` (documented: "Able to operate on intermediate responses like redirects and retries."). It can (a) count exchanges per hop and throw on the second, and (b) rewrite the response before `RetryAndFollowUpInterceptor` sees it.

- The counter lives on a `Request` tag and the second exchange throws before reaching the wire. Pitfall found while probing: `Request.newBuilder()` copies tags, so a redirect hop built from the previous request shares the counter and the second hop was refused as a "second exchange". Each hop needs a fresh tag.
- Hiding: on `503` with `Retry-After`, move the value to a private header and remove `Retry-After`; `retryAfter(...)` then returns `Integer.MAX_VALUE` and no re-send happens. The transport restores `Retry-After` when it builds `HttpResponse`. The interceptor always removes a server-sent look-alike of the private header first, so a server cannot inject a `Retry-After`.
- A network interceptor must call `proceed` exactly once ("it is an error for a network interceptor to short-circuit or repeat a network request"), so the counter fails by throwing, and the failure is `IOException`; the hide step is what keeps the `503` deliverable as a response.
- The counter is the only mechanism that does not depend on which internal re-send paths 5.5.0 happens to have. The declared `okhttp-jvm` is an ordinary requirement that a consumer's newer 5.x overrides (`okhttp-jvm` 5.5.0 is the latest on Maven Central on 2026-10-21), so a guarantee that survives a newer interceptor is worth having.

### F4. Redirects: follow in the transport with `followRedirects(false)` (documented + measured)

- **Order.** `RealCall.getResponseWithInterceptorChain` builds `client.interceptors`, `RetryAndFollowUpInterceptor`, `BridgeInterceptor`, `CacheInterceptor`, `ConnectInterceptor`, then `client.networkInterceptors`, then `CallServerInterceptor`. Measured: a network interceptor is not invoked at all when the TCP connect fails, and `chain.connection()` is non-null inside it. A per-hop URL check there runs after the socket is open. Application interceptors run once per call, so they never see hops. The pre-connect hook is the `Dns` (names only) or a check in the code that issues each hop.
- **OkHttp's own follow-up.** 20 redirects succeeded, 21 failed with `ProtocolException: Too many follow-up requests: 21`. Across hosts it removed `Authorization` and nothing else: the second host received `X-Api-Key: KEY123` and `Cookie: s=1` (measured).
- **IP-literal forms skip `Dns`.** With a `Dns` that always throws, `http://2130706433:<port>/` and `http://127.1:<port>/` reached the server (`requestCount` 1 and 2, `Dns` not consulted), while `localhost` and `0x7f.0.0.1` were refused by the `Dns`. `HttpUrl.host` keeps `2130706433` as written (measured). The hop hook therefore receives the URL as OkHttp parsed it and must classify the host itself; the classification rules belong to the outbound-URL policy question.
- **Method and body rules** (OkHttp 5.5.0 `HttpMethod.redirectsToGet`, confirmed by a probe): `303` -> `GET`; `307`/`308` keep method and body; `301`/`302`/`300` -> `GET` with `Content-Type`/`Content-Length`/`Transfer-Encoding` removed. RFC 9110: a `307` user agent "MUST NOT change the request method if it performs an automatic redirection", and "Automatic redirection needs to be done with care for methods not known to be safe".
- **Manual loop, measured** (probe, 34 assertions in the combined run): per hop the policy check runs first (a refused second hop left the second server at 0 requests); a cap of 3 produced `TooManyRedirects` after exactly 4 requests; a missing or unresolvable `Location` hands the `3xx` back as a response; flagged-sensitive headers and the default set (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`) were stripped on a cross-origin hop and an ordinary header was kept; an `https` -> `http` redirect was not followed and the plain target received 0 requests; a `POST` `302` was returned, not followed.
- **Costs of the manual loop.** OkHttp's `priorResponse` is gone (replaced by `redirectChain`); the 303/307/308 rules are ours (kept small by following only `GET`/`HEAD`); each hop is a separate `Call`, so the call timeout is applied per hop from one shared deadline (measured: two hops of 600 ms each against a 1000 ms limit ended with `Timeout(Call)` at 1001 ms); the body of a followed `3xx` is never read.

### F5. The suspend bridge: read the body where cancellation reaches it (documented + measured)

- `okhttp3.coroutines.executeAsync` (5.5.0) is `suspendCancellableCoroutine`, `continuation.invokeOnCancellation { this.cancel() }`, `enqueue(Callback)`, resuming with the `Response` and closing it if the continuation was already cancelled. `okhttp-coroutines` 5.5.0 declares `kotlinx-coroutines-core-jvm` 1.11.0 at runtime scope, so it is copied (Coil 3 does the same, with a comment pointing at the OkHttp source).
- `invokeOnCancellation` is registered only until the continuation resumes. If the body is read after `executeAsync` returns, a coroutine cancel does nothing to the blocking `source.read`. Measured, endless body, cancel at 600 ms: literal `executeAsync` + read on `Dispatchers.IO` took 29 414 ms to return (until the call timeout), the server had written 992 KB; body read inside `onResponse`: 0 ms, `call.isCanceled() == true`, 0 pooled connections, the server stopped at 21 KB. Cancel while waiting for headers: 0 to 2 ms in both, 0 pooled connections.
- Retrofit 3.0.0 parses the body inside `okhttp3.Callback.onResponse` (`response = parseResponse(rawResponse)`), on OkHttp's thread, for the same reason.
- **A throw out of `onResponse` leaves the continuation unresumed.** `RealCall.AsyncCall.run` sets `signalledCallback = true` before calling `onResponse`; a throwable from it cancels the call, rethrows, and calls no `onFailure`. The callback must resume the continuation in every path, then rethrow only fatal errors.
- The body read holds a `Dispatcher` slot until it finishes, so `maxRequests` accounts for body time.
- `runCatching` must not wrap the suspend call (it swallows `CancellationException`). In the probe the only catch clauses are `TransportFailure`, `IOException` and a last `Throwable` that resumes and rethrows. Cancellation reaches the caller as a thrown `CancellationException`, never as a `Result.failure` (measured: `job.isCancelled`, 2 ms).

### F6. The body cap (measured)

Reader: refuse early when the response can have a body and `contentLength() > cap`; then read in 8 KiB steps of at most `cap + 1 - buffered`, failing when the buffer exceeds the cap. Cap 1 000 000 B:

| Response | Result |
|---|---|
| `Content-Length: 5000000` | early refusal, 0 bytes read; the server still wrote all 5 MB (OkHttp drains up to 100 ms of an unread fixed-length body on close, then keeps or drops the connection) |
| chunked, 5 MB | refused at 1 000 001 bytes; server had written 1.27 MB; connection dropped (pool 0) |
| chunked, endless | refused at 1 000 001 bytes; server had written 1.28 MB; connection dropped |
| `Content-Length` exactly 1 000 000 | accepted; 1 000 001 refused early |
| gzip, 50 MB of zeros in ~50 KB, transparent | refused at 1 000 001 decoded bytes; `contentLength()` is -1 and both `Content-Length` and `Content-Encoding` are stripped from the response headers |
| gzip, caller set `Accept-Encoding: gzip` | not decoded; raw gzip bytes returned with `Content-Encoding: gzip` and the compressed `Content-Length` |
| `HEAD`, `Content-Length: 5000000` | `contentLength()` 0, body empty |
| `304` with `Content-Length: 5000000` | **reading the body blocks until the timeout** (10 s, `InterruptedIOException: timeout`) |

- `BridgeInterceptor` decodes only when it added `Accept-Encoding: gzip` itself ("If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing the transfer stream."). The transport therefore owns `Accept-Encoding`, `User-Agent`, `Host`, `Connection`, `Content-Length` and `Transfer-Encoding` and drops caller values for them; otherwise a gzip body is returned undecoded and the cap meters compressed bytes.
- OkHttp's `promisesBody()` honours a `Content-Length` on `304`/`204` ("For best compatibility, we honor the headers"), and RFC 9110 section 8.6 lets a server send one on a `304`: "A server MAY send a Content-Length header field in a 304 (Not Modified) response to a conditional GET request". The transport must not read the body of `HEAD`, `1xx`, `204` or `304`; with that rule a `304` carrying `Content-Length: 5000000` returned in 111 ms with its `ETag` readable (measured). This matters most to the feeds module's conditional GET.
- A cap of C and N concurrent calls can hold N x C bytes; the defaults below keep that bounded.

### F7. Timeouts: what OkHttp throws, and what callTimeout covers (measured)

| Situation | Exception (top of chain) |
|---|---|
| read timeout, headers or body | `SocketTimeoutException: timeout` (cause `SocketException: Socket closed`) |
| call timeout, headers or body | `InterruptedIOException: timeout` (not a `SocketTimeoutException`) |
| connect timeout (non-routable address) | `SocketTimeoutException: Connect timed out` |
| connection refused | `ConnectException: Failed to connect to ...` |
| DNS failure | `UnknownHostException` |
| untrusted certificate | `SSLHandshakeException` |
| hostname not in the certificate | `SSLPeerUnverifiedException` |
| plain HTTP to a TLS port | `ProtocolException: Unexpected status line` |
| TLS to a plain port | `SSLHandshakeException: Remote host terminated the handshake` |
| enqueue after the dispatcher executor shut down | `onFailure(InterruptedIOException: executor rejected)` |
| server closes a reused connection | `IOException: unexpected end of stream on http://host/...` |

- Distinguishing connect from read timeouts needs the message ("Connect timed out", the JDK's text) or an `EventListener`; Ktor does it by testing `message.contains("connect")`. Distinguishing call timeout from socket timeouts is by type (`SocketTimeoutException` is a subclass of `InterruptedIOException`, so test it first). The failure model should expose one `Timeout` cause; the phase, where known, goes in the log line only.
- Slow drip (100 B per 100 ms) with `readTimeout` 500 ms completed in 5.3 s; with `callTimeout` 1000 ms it ended at 1002 ms. The read timeout does not bound a slow-drip body; the call timeout does.
- **`callTimeout` starts when a dispatcher thread runs the call, not when it is queued** (`AsyncCall.run` calls `timeout.enter()`). Measured with `maxRequestsPerHost = 1`, replies taking 1500 ms and `callTimeout` 1000 ms: the first call failed at 1003 ms, the second at 2018 ms. OkHttp's queue is therefore invisible to the timeout and to the budget's wait cap; size the dispatcher so nothing queues.
- `Call.timeout().timeout(...)` overrides the call timeout per call before `enqueue` (measured: 302 ms against a 10 s client setting). OkHttp defaults: `callTimeout` 0 (none), connect, read and write 10 s.

### F8. Request validation: OkHttp throws, so the library types must validate first (measured)

`Request.Builder` threw `IllegalArgumentException` for: a header name containing a space; a header value containing LF; a non-ASCII header value (`é`); a URL with no scheme or with `ftp`; `POST` without a body; `GET` with a body. A tab in a value is accepted. `"http://".toHttpUrlOrNull()` returns `null`. Consequences for the model:

- Header names are RFC 9110 tokens; request header values are `HTAB` and `0x20`-`0x7E`. Response headers are built leniently (a server's odd header must not fail the whole response).
- `Url.parse` returns `Result`, rejects user-info, any scheme but `http`/`https`, whitespace and control characters, and never echoes the input in its message (it may hold a key).
- `OkHttpTransport` still maps any residual `IllegalArgumentException` to an `InvalidRequest` failure instead of letting it escape.
- Query encoding: all bytes outside `A-Za-z0-9-._~` percent-encoded from UTF-8, space as `%20`; probe output equals OkHttp's `HttpUrl.Builder.addQueryParameter` for `a b&c=d/é+` (`a%20b%26c%3Dd%2F%C3%A9%2B`).

### F9. Lifecycle (documented + measured)

- `newBuilder()` shares the connection pool, thread pools and configuration (documented, and measured: `derived.dispatcher === original.dispatcher`).
- `Dispatcher.executorService` is a `ThreadPoolExecutor(0, Int.MAX_VALUE, 60 s, SynchronousQueue, threadFactory("... Dispatcher", false))`: non-daemon threads with a 60 s idle life. Measured: after one `enqueue`d call and no shutdown the process exited after 61 s; with `executorService.shutdown()` and `connectionPool.evictAll()` after 2 s. `Dispatcher.cancelAll()` cancels queued and running calls. After shutdown, `enqueue` reports `onFailure(InterruptedIOException("executor rejected"))`; a synchronous `execute` still works, so the transport checks its own closed flag first.
- OkHttp: "Shutdown the dispatcher's executor service with shutdown(). This will also cause future calls to the client to be rejected." and "Note that the connection pool's daemon thread may not exit immediately."
- Derived clients must get their own `Dispatcher` and `ConnectionPool`; otherwise `close()` stops the donor's threads. Ktor's engine does the opposite for a `preconfigured` client: it calls `newBuilder()` (sharing the dispatcher) and on shutdown runs `dispatcher.executorService.shutdown()` on every client it created.
- A client derived from a `mockwebserver3` TLS-trusting client kept its trust configuration (measured: `https` worked; an unconfigured transport got `TlsFailed`). The `shared` parameter must clear inherited interceptors, cache, cookie jar and authenticators so the "never retries" setup cannot be undone by a donor.
- `Dispatcher` defaults: `maxRequests` 64, `maxRequestsPerHost` 5. With a transport per upstream host or policy, the per-host limit is the one that bites.

### F10. Credentials, logging and the `User-Agent` (documented + measured)

- OkHttp's `Request.toString()` appends the full URL, query included, and redacts only the values of `Authorization`, `Cookie`, `Proxy-Authorization` and `Set-Cookie`. So an OkHttp `Request`, `HttpUrl` or `Response` is never passed to a log call; the transport logs `method`, `Url.redacted()` (scheme, host, port, path, and `?...` when a query exists), status, size, elapsed time and the failure class. Exception messages from OkHttp redact the path (`unexpected end of stream on http://localhost:54504/...`).
- The default `User-Agent` is `okhttp/5.5.0`; a request-level value is accepted (measured). RFC 9110: "A user agent SHOULD send a User-Agent header field in each request unless specifically configured not to do so." The constructor takes it as a required parameter, validated as non-blank printable ASCII.
- OpenDota's published OpenAPI document declares `api_key` as a query parameter and adds: "API key can also be sent using the authorization header "Authorization: Bearer YOUR-API-KEY"". Credentials in headers are therefore available for that provider (read 2026-10-21).
- Options for the credential concept are in Consideration 3.

### F11. Common-code validity (documented + measured)

- `kotlin.AutoCloseable` is a common declaration since Kotlin 2.0 (JVM actual: typealias to `java.lang.AutoCloseable`). `ByteArray.decodeToString()` is common and UTF-8 only (malformed sequences become U+FFFD); `kotlin.text.Charsets` is JVM only. A charset-aware `bodyAsText` cannot live in common code, and the XML and JSON consumers do not need one (an XML parser detects its encoding from the bytes; JSON is UTF-8).
- A public interface function `suspend fun execute(request): Result<HttpResponse>` compiles to `execute-gIAlu-s` (re-measured on the probe interface under Kotlin 2.2.0; the build-topology research recorded the same). Inline value classes mangle every function that takes them (Kotlin docs), so none is used in a signature that must stay stable. `kotlin.time.Duration` is a value class: settings constructors that take it carry a synthetic constructor on the JVM.
- The common types in the recommendation compiled for `jvm()` and `js(IR)` with Kotlin Gradle plugin 2.2.0 and used only `String`, `ByteArray`, `Result`, `Duration`, `List`, `Throwable` and `AutoCloseable`.

## How comparable projects do it

| Project (ref) | Port | What it does | Survived / regretted |
|---|---|---|---|
| **Apollo Kotlin** 4.4.1 (`HttpEngine`, `HttpRequest`, `HttpResponse`) | `interface HttpEngine : Closeable { suspend fun execute(request: HttpRequest): HttpResponse }` in common code; `DefaultHttpEngine` per platform | `url: String`, `method` an enum (`Get`, `Post`), `headers: List<HttpHeader(name, value)>`, a streaming `HttpBody`; HTTP errors "should not throw but instead return a `HttpResponse`"; network errors throw `ApolloNetworkException`. JVM engine calls `Call.execute()` inside `suspendCancellableCoroutine` and reads the body outside it. Public `DefaultHttpEngine(okHttpClient: OkHttpClient)` and a `Call.Factory` overload. Timeouts: one `timeoutMillis` applied to both connect and read; engine `close()` is empty. | Status-as-response and `close()` in the port survived. `HttpResponse.Builder.body(ByteString)` is deprecated "Use body(BufferedSource) instead" (they moved off buffered bodies; here a capped buffered body is the requirement). An OkHttp type in the public signature is exactly what this repository forbids. |
| **Ktor** 3.6.0 (`OkHttpEngine`, `OkHttpConfig`, `HttpRedirect`) | `HttpClientEngine.execute(HttpRequestData)` | The OkHttp engine builds clients with `followRedirects(false)`, `followSslRedirects(false)` and `retryOnConnectionFailure(true)`; redirects are a common-code plugin: only `GET`/`HEAD` by default, no HTTPS-to-HTTP unless `allowHttpsDowngrade`, `Authorization` removed when the authority changes. Bridge: `callContext[Job]!!.invokeOnCompletion(true) { call.cancel() }` (internal API). On shutdown: `connectionPool.evictAll()` and `dispatcher.executorService.shutdown()`. `OkHttpConfig.preconfigured: OkHttpClient?`. | Manual redirect-following over an engine with `followRedirects(false)` is the surviving pattern, and `GET`/`HEAD`-only following is Ktor's default. It keeps `retryOnConnectionFailure(true)`, so it does not give "never re-sends". |
| **Coil 3** (`NetworkClient`, `NetworkRequest`, `NetworkResponse`, `CallFactoryNetworkClient`) | `NetworkClient.executeRequest(request, block: suspend (NetworkResponse) -> T): T` | `url: String`, `method: String`, `NetworkHeaders` (lower-cased keys, so original case is lost), `extras: Extras`; response carries `requestMillis`/`responseMillis` from OkHttp's wall clock and `delegate: Any?` (the OkHttp response). `await()` is a copy of `executeAsync` with the comment "Copied from ... okhttp-coroutines". | Copying `executeAsync` is common practice. `NetworkRequest` carries two `@Deprecated(level = HIDDEN)` constructors and copies "Kept for binary compatibility" after fields were added: adding a field to a public request class costs hidden overloads. `delegate: Any?` leaks the engine type. Response times from the real clock would bypass an injected clock. |
| **http4k** (`HttpHandler`, `ClientFilters.FollowRedirects`) | `typealias HttpHandler = (Request) -> Response`; filters decorate it | Engines set `instanceFollowRedirects = false`; redirects are a filter with `crossOriginSensitiveHeaders = setOf("Authorization", "Cookie", "Proxy-Authorization")` as a constructor parameter, a hard cap of 10 that calls `error("Too many redirections")`, and no HTTPS-to-HTTP follow. | The decorator as the extension and observation mechanism, and a configurable cross-origin header set, are the parts worth copying. A cap that throws is the part not to copy. |
| **Retrofit** 3.0.0 (`OkHttpCall`) | `okhttp3.Call.Factory` | Parses the body in `Callback.onResponse` on OkHttp's thread. | Precedent for F5. |
| **OkHttp** 5.5.0 (`Call`, `Interceptor`, `EventListener`) | n/a | `retryDecision` and `followUpDecision` on `EventListener` observe but cannot veto the re-send; the network-interceptor chain can. | The observation API is large (dozens of callbacks) and grows in minors. |

## Pitfalls

1. **Believing the client flags give "never retries".** `retryOnConnectionFailure(false)` and `followRedirects(false)` left a `503` + `Retry-After: 0` re-sent (F1). Without a Level-3 test that asserts `requestCount == 1` for that script, the guarantee is not checked.
2. **Flag `false` with the default 5-minute pool.** Systematic failures on `GET`s after the server closes an idle connection (F2).
3. **Counter tag copied by `newBuilder()`** (F3): each redirect hop needs a fresh counter, or every redirect fails as a "second exchange".
4. **Reading the body after the bridge returns** (F5): cancellation waits for the read or call timeout.
5. **A callback that can throw before resuming** hangs the coroutine forever (F5).
6. **Reading the body of a `304` or `HEAD`** (F6): hangs until the timeout when a `Content-Length` is present.
7. **Letting callers set `Accept-Encoding`** (F6): undecoded gzip, cap meters compressed bytes.
8. **Trusting `Content-Length`** alone as the cap: absent for chunked and removed for gzip (F6).
9. **Policy check in a network interceptor** (F4): runs after the connect; IP-literal hosts skip `Dns`.
10. **Cross-host redirects forwarding custom credential headers** (F4): OkHttp strips only `Authorization`.
11. **`withTimeout` around `execute`** yields a `TimeoutCancellationException` (a cancellation), not the typed `Timeout`; the transport enforces its own call timeout and callers should not duplicate it. (Reasoning from the coroutines contract, not probed.)
12. **Dispatcher queueing is invisible** to `callTimeout` and the budget's wait cap (F7).
13. **No `close()`** keeps the JVM alive for 60 s and leaks pool threads (F9); a shared dispatcher shut down by the wrong owner stops someone else's client (Ktor's `preconfigured` path).
14. **Logging an OkHttp `Request`/`HttpUrl`** writes the query, and with it a key (F10).
15. **Evolvability.** Adding a defaulted constructor parameter to a public Kotlin class changes the JVM constructor signature; Coil carries hidden deprecated constructors for it. `HttpRequest`, `HttpResponse` and `TransportSettings` are the classes most likely to grow; the API-shape question owns the remedy (builders, ABI validation).
16. **Mangled `Result`-returning interface method** (`execute-gIAlu-s`): a later change of the return type renames the JVM method.
17. **Worst-case memory** is `maxConcurrentRequests x maxResponseBytes` (16 x 5 MiB = 80 MiB with the proposed defaults).

## Recommendation for this repository

Use a library-owned, OkHttp-free port with a manual-redirect OkHttp default that is built around the three "never re-send" mechanisms. Reject, in order: a public `OkHttpClient` parameter (breaks the signature rule; Apollo and Coil pay the ownership ambiguity), OkHttp-native redirects (post-connect hooks, partial credential stripping, fixed cap), and the literal `okhttp-coroutines` pattern (cancellation lag).

### Common types (`commonMain`, package `com.spartanlabs.webtools.http`; names provisional, shapes validated by the probe)

```kotlin
public interface HttpTransport : AutoCloseable {                  // Experimental seam: one opt-in marker
    public suspend fun execute(request: HttpRequest): Result<HttpResponse>
    override fun close() {}                                        // fakes need not override
}

public class HttpMethod private constructor(public val name: String) {   // final class, constants; not an enum, not a value class
    public val permitsBody: Boolean
    public companion object {
        public val Get: HttpMethod; public val Head: HttpMethod; public val Post: HttpMethod
        public val Put: HttpMethod; public val Patch: HttpMethod; public val Delete: HttpMethod; public val Options: HttpMethod
        public fun of(name: String): Result<HttpMethod>            // token-validated, upper-case
    }
}

public class Url private constructor(/* scheme, host, port, encodedPath, encodedQuery */) {
    public val scheme: String; public val host: String; public val port: Int
    public val encodedPath: String; public val encodedQuery: String?
    public fun withQuery(params: List<Pair<String, String>>): Url  // percent-encodes; caller orders (cache keys use the endpoint's canonical params, not this string)
    public fun redacted(): String                                  // never the query
    public companion object { public fun parse(text: String): Result<Url> }
}

public class HttpHeaders private constructor(/* ordered entries */) {
    public operator fun get(name: String): String?                 // case-insensitive, last value
    public fun getAll(name: String): List<String>
    public fun entries(): List<Entry>                              // Entry(name, value, sensitive)
    public fun newBuilder(): Builder
    public class Builder {
        public fun add(name: String, value: String): Builder       // precondition violation = IllegalArgumentException (programmer error)
        public fun addSensitive(name: String, value: String): Builder
        public fun set(name: String, value: String): Builder
        public fun remove(name: String): Builder
        public fun build(): HttpHeaders
    }
    public companion object { public val Empty: HttpHeaders }
}

public class RequestBody(bytes: ByteArray, public val contentType: String?)   // copies the array
public class HttpRequest(
    public val method: HttpMethod, public val url: Url,
    public val headers: HttpHeaders = HttpHeaders.Empty, public val body: RequestBody? = null,
)
public class HttpResponse(
    public val status: Int, public val headers: HttpHeaders, public val body: ByteArray,   // empty for HEAD/1xx/204/304
    public val url: Url,                                           // final URL
    public val redirectChain: List<Url> = emptyList(),             // URLs visited before `url`, in order
)

public class TransportSettings(                                    // policy numbers; defaults below are proposals
    public val callTimeout: Duration, public val connectTimeout: Duration, public val readTimeout: Duration,
    public val writeTimeout: Duration, public val maxResponseBytes: Long, public val maxRedirects: Int,
    public val allowHttpsToHttp: Boolean, public val maxConcurrentRequests: Int,
    public val maxConcurrentRequestsPerHost: Int, public val idleConnectionTtl: Duration,
)
```

`TransportFailure` causes the failure hierarchy (research question 9) must carry, each holding a `Throwable?` cause, which is common code: `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `RefusedByPolicy(reason)`, `BodyTooLarge(limit, declared)`, `TooManyRedirects(limit)`, `InvalidRequest`, `Protocol`, `Io`, `Closed`, `RetryRefused`. Cancellation is not a cause; it is thrown.

- **No received-at time on the response.** The facade stamps time from the injected clock; OkHttp's `receivedResponseAtMillis` is the real wall clock and would bypass it. `Retry-After` and `Date` stay readable as headers.
- **No charset on the response.** The caller's decode function receives the bytes; `decodeToString()` covers UTF-8 in common code.
- **No streaming bodies.** Both bodies are `ByteArray`; the cap is the contract. A streaming port is a separate, additive port.
- **Per-request overrides:** none. Timeouts and caps are per transport instance; one instance per upstream host or policy is the intended use. Adding `RequestOptions` later is possible (OkHttp's `Call.timeout()` supports a per-call timeout) but is a constructor change to a Stable Core class.

### OkHttp default (`jvmMain`)

```kotlin
public class OkHttpTransport(
    userAgent: String,                                             // required, no default; non-blank printable ASCII
    settings: TransportSettings = TransportSettings(),
    policy: OutboundUrlPolicy,                                     // from the URL-policy research
) : HttpTransport {
    internal constructor(                                          // reached by the fixtures-only factory (loopback for mockwebserver3)
        userAgent: String, settings: TransportSettings,
        hopCheck: HopCheck, dns: okhttp3.Dns, shared: okhttp3.OkHttpClient?,
    )
    override suspend fun execute(request: HttpRequest): Result<HttpResponse>
    override fun close()                                           // idempotent
}
internal fun interface HopCheck { fun check(url: okhttp3.HttpUrl): TransportFailure? }
```

Wiring, all exercised by the probe:

1. **Client.** `(shared?.newBuilder() ?: OkHttpClient.Builder())` then: new `Dispatcher` with `maxRequests`/`maxRequestsPerHost` from settings; `ConnectionPool(5, idleConnectionTtl, MILLISECONDS)`; clear inherited `interceptors()`/`networkInterceptors()`; `retryOnConnectionFailure(false)`; `followRedirects(false)`; `followSslRedirects(false)`; `Authenticator.NONE` for both; `CookieJar.NO_COOKIES`; `cache(null)`; connect/read/write timeouts from settings; `callTimeout(0)` (the deadline is applied per hop); the policy's `Dns`; `addNetworkInterceptor(ExchangeGuard)`.
2. **`ExchangeGuard`** (network interceptor): per-hop counter on a `Request` tag, throw on the second exchange; hide `Retry-After` on `503`; strip any server-sent copy of the private header.
3. **Request conversion.** `Url` -> `HttpUrl` (null -> `InvalidRequest`); drop caller values of `User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length`, `Transfer-Encoding`; set `User-Agent`; `GET`/`HEAD` with a body -> `InvalidRequest`; `POST`/`PUT`/`PATCH` without a body -> empty body; fresh counter tag per hop.
4. **Loop.** For each hop: `HopCheck` first; `remaining = deadline - now` (<= 0 -> `Timeout`); `call.timeout().timeout(remaining)`; `exchange(...)`. Follow only `300/301/302/303/307/308` on `GET`/`HEAD` with a resolvable `Location`; refuse `https` -> `http` unless allowed; the hop count above `maxRedirects` -> `TooManyRedirects`; on a cross-origin hop (scheme, host or port differs) remove the default sensitive names and every entry flagged sensitive on the original request. The `3xx` is returned as a response when it is not followed.
5. **`exchange`.** `suspendCancellableCoroutine`; `invokeOnCancellation { call.cancel() }`; `call.enqueue`; in `onResponse`: `response.use`, no body read for a followed redirect or for `HEAD`/`1xx`/`204`/`304`, early refusal on `contentLength() > cap`, bounded read otherwise; resume in every path; rethrow only after resuming.
6. **Failure mapping** (top of cause chain, first match): own marker exceptions for `RefusedByPolicy` (thrown from the policy's `Dns` as an `UnknownHostException` subclass) and `RetryRefused`; `closed && call.isCanceled()` -> `Closed`; `UnknownHostException` -> `DnsFailed`; `SocketTimeoutException` -> `Timeout`; `InterruptedIOException` with message `timeout` -> `Timeout`; message `executor rejected` -> `Closed`; `SSLException` -> `TlsFailed`; `ConnectException`/`NoRouteToHostException`/`PortUnreachableException` -> `ConnectFailed`; `java.net.ProtocolException` -> `Protocol`; any other `IOException` -> `Io`.
7. **`close()`.** `closed = true`; `dispatcher.cancelAll()`; `dispatcher.executorService.shutdown()`; `connectionPool.evictAll()`. `execute` after `close()` returns `Closed` without touching the dispatcher.
8. **Logging (slf4j, `jvmMain` only).** `INFO` on create and close (settings, never headers); `DEBUG` per hop `"http {} {} -> {} ({} B, {} ms)"` with `Url.redacted()`; `DEBUG` per redirect `from.redacted() -> to.redacted()`; `WARN` per failure with method, `Url.redacted()` and the failure class. Never headers, bodies, queries or OkHttp objects. The fake transport in the fixtures logs nothing.

### Proposed defaults (judgement; policy numbers, all constructor parameters)

| Parameter | Trusted | Untrusted | Basis |
|---|---|---|---|
| `callTimeout` | 30 s | 15 s | OkHttp default is none; deadline spans all hops |
| `connectTimeout` / `readTimeout` / `writeTimeout` | 10 s each | 5 s each | OkHttp's own default is 10 s |
| `maxResponseBytes` | 5 MiB | 2 MiB | N x cap memory bound (pitfall 17); feeds are mostly under 1 MiB |
| `maxRedirects` | 5 | 3 | below OkHttp's 20 |
| `allowHttpsToHttp` | false | false | downgrade refused by default (Ktor's default) |
| `maxConcurrentRequests` / `...PerHost` | 16 / 5 | 8 / 2 | at least the budget's maximum concurrent permits, so nothing queues invisibly (F7) |
| `idleConnectionTtl` | 4 s | 4 s | under the 5 s idle defaults of Apache and Node (F2) |

### Hook decision

No transport listener. `HttpTransport` is an interface, so a consumer decorates it to observe every call (http4k's filter idea) without a new API. What a decorator cannot see (DNS, connect and TLS timings) is OkHttp's `EventListener` territory, a large and growing surface that a port would freeze. A consumer who needs those gets them additively later (Consideration 4).

### Tests this implies (Level 3, `mockwebserver3`, real transport)

`requestCount` equals 1 for: `503`+`Retry-After: 0`, `408`, `429`, a pooled connection that dies, each redirect hop; `304` with `Content-Length` returns promptly; cap with/without `Content-Length` and with a gzip bomb; cancel during an endless body returns promptly; `close()` lets the JVM exit; the policy refusing hop 2 leaves the second server at 0 requests; cross-origin stripping. Level 2 (fake transport): refusal costs nothing, `Closed` after `close()`. Level 4a: `Url.parse`, header validation, query encoding, redirect decision table. All of these passed in the probe except where noted in Known gaps.

## Considerations

### 1. Idle-connection TTL against reuse (needs a call)

- **Options.** (a) `idleConnectionTtl` default 4 s, a parameter. (b) No idle pooling (`maxIdleConnections = 0`): zero stale-connection failures, a TCP and TLS handshake on every call. (c) OkHttp's default 5-minute keep-alive: rejected, it failed 50% of calls against a server that closes idle connections (F2).
- **Recommendation: (a).** Calls at 60 per minute to one host mostly arrive within the TTL; a server whose idle timeout is shorter than 4 s still causes an occasional `Io` failure, which the caller retries on its own schedule. Choose (b) for a provider where that failure is unacceptable.

### 2. Redirect scope (needs a call)

- **Options.** (a) Follow only `GET`/`HEAD`, return other methods' `3xx`; (b) follow `307`/`308` for any method with the buffered body.
- **Recommendation: (a).** RFC 9110 asks for care with unsafe methods, Ktor defaults to it, and a followed `POST` sends its body to a second origin. Every current consumer call is a `GET`.

### 3. The credential concept (needs a call)

- **Options.** (a) Flagged header entries: `addSensitive(name, value)`; `toString` redaction, cross-origin stripping and the recorder's key stripping all key off the flag plus the four default names. (b) A separate `Credential` type bound to an origin, applied by the transport and never present in the request. (c) Plain headers with a configured set of sensitive names.
- **Recommendation: (a).** One concept, no second configuration list to keep in step, and the request stays a plain value. (b) is stronger (a credential can never be attached to another origin) but is a new Stable Core type and does not cover a caller who sets `Authorization` themselves; it can be added later without breaking (a).

### 4. An observation hook (needs a call, low priority)

- **Options.** (a) None; decoration is the hook. (b) An Experimental `TransportObserver` with request, response and failure callbacks carrying redacted URLs, constructor-injected, no-op default.
- **Recommendation: (a).** A decorator already gives per-call observation. Take (b) only if the facade research settles on an observer for common code, since one observer would also remove logging from `commonMain`.

## Known gaps

- **`421` on a coalesced HTTP/2 connection was not exercised.** It is covered by the exchange counter, which was measured on the stale-pool re-send, but the `421` path itself is source reading. HTTP/2-specific failures (GOAWAY, `REFUSED_STREAM`) were not measured; the TLS probes used `mockwebserver3` defaults and the negotiated protocol was not asserted.
- **The 4 s TTL and every default in the table are judgement.** Real provider idle timeouts (OpenDota, Steam, Open-Meteo) were not measured; the idle server was a simulation.
- **Proxies.** OkHttp resolves the target through a configured proxy, which sidesteps `Dns`; whether the transport sets `Proxy.NO_PROXY` (at least for `Untrusted`) belongs to the URL-policy research and was not measured here.
- **`Retry-After` on a `3xx`** (RFC 9110: minimum wait before the redirected request) is not honoured by the redirect loop, as it is not by OkHttp.
- **Evolvability of the public classes** (builders, hidden overloads, ABI validation) and the `Result`-mangling choice are left to the API-shape research.
- **Not built at `-Xjdk-release 11`.** The probe ran with the default JDK 23 target; the sketch uses no API newer than Java 11 but that was not compiled.
- **Probe code lives outside the repository and is not committed.** The measurements are reproducible from the scripts in F1 to F10; the Level-3 tests above are their permanent form.
- **OpenDota's header support** comes from its live OpenAPI document, not from versioned documentation.
- **Only one Windows machine, JDK 23, Gradle 9.7.1.**

## Load-bearing claims

Numbers 1 to 25 are outside-world statements with sources. Measured results in Findings come from the probe and have no source to re-open.

1. **OkHttp re-sends a `503` that carries `Retry-After: 0`, with no client flag gating it.** "`if (retryAfter(userResponse, Integer.MAX_VALUE) == 0) {` / `// specifically received an instruction to retry without delay.` / `return userResponse.request`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt>, tag `parent-5.5.0` (OkHttp 5.5.0; the default branch had the same lines on 2026-10-21).
2. **`408` and `recover` are gated by `retryOnConnectionFailure`; `421` is not.** "`if (!chain.retryOnConnectionFailure) {` / `// The application layer has directed us not to retry the request.`" and "`HTTP_MISDIRECTED_REQUEST -> {` / `// OkHttp can coalesce HTTP/2 connections even if the domain names are different.`" Same file and tag.
3. **The follow-up cap is fixed and only `Authorization` is stripped.** "`private const val MAX_FOLLOW_UPS = 20`" and "`// When redirecting across hosts, drop all authentication headers.`" / "`requestBuilder.removeHeader("Authorization")`". Same file and tag.
4. **OkHttp documents the destructive-retry trade-off.** "Set this to false to avoid retrying requests when doing so is destructive. In this case the calling application should do its own recovery of connectivity failures." and "**Stale pooled connections.** The [ConnectionPool] reuses sockets to decrease request latency, but these connections will occasionally time out." <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt>, `OkHttpClient.Builder.retryOnConnectionFailure`, tag `parent-5.5.0`.
5. **A `GET` skips the extensive connection health check, and the check applies only after 10 s idle.** "`private val doExtensiveHealthChecks = request.method != "GET"`" in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealRoutePlanner.kt>; "`const val IDLE_CONNECTION_HEALTHY_NS = 10_000_000_000 // 10 seconds.`" in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealConnection.kt>. Tag `parent-5.5.0`.
6. **Network interceptors sit after `ConnectInterceptor` and see retries and redirects; they must call `proceed` once.** "`interceptors += ConnectInterceptor` / `if (!forWebSocket) {` / `interceptors += client.networkInterceptors`" in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt>; "Able to operate on intermediate responses like redirects and retries." in <https://github.com/square/okhttp/blob/parent-5.5.0/docs/features/interceptors.md>; "These interceptors must call [Interceptor.Chain.proceed] exactly once: it is an error for a network interceptor to short-circuit or repeat a network request." in `OkHttpClient.kt` (`networkInterceptors`). Tag `parent-5.5.0`.
7. **Transparent gzip only when OkHttp added the header, and it strips `Content-Length`.** "If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing the transfer stream." and ".removeAll("Content-Encoding")" / ".removeAll("Content-Length")" / "`RealResponseBody(contentType, -1L, gzipSource.buffer())`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/BridgeInterceptor.kt>, tag `parent-5.5.0`.
8. **`callTimeout` starts when a dispatcher thread runs the call; a throw out of `onResponse` signals nothing.** In `AsyncCall.run`: "`var signalledCallback = false` / `timeout.enter()`" and "`signalledCallback = true` / `responseCallback.onResponse(this@RealCall, response)`" ... "`if (!signalledCallback) {`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt>, tag `parent-5.5.0`.
9. **The 5.5.0 `executeAsync` pattern, and why it is copied.** "`continuation.invokeOnCancellation {` / `this.cancel()`" and "`continuation.resume(response) { _, value, _ ->` / `value.closeQuietly()`" in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt>; the POM lists `kotlinx-coroutines-core-jvm` version `1.11.0`, scope `runtime`: <https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-coroutines/5.5.0/okhttp-coroutines-5.5.0.pom>. OkHttp 5.5.0.
10. **Dispatcher threads are non-daemon with a 60 s idle life; shutdown and sharing semantics.** "`threadFactory("$okHttpName Dispatcher", false)`" with "`60,` / `TimeUnit.SECONDS,`" in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt>; "Shutdown the dispatcher's executor service with [shutdown()][ExecutorService.shutdown]. This will also cause future calls to the client to be rejected." and "This builds a client that shares the same connection pool, thread pools, and configuration." in `OkHttpClient.kt`. Tag `parent-5.5.0`.
11. **OkHttp honours `Content-Length` on a `304`; RFC 9110 permits it.** "If the Content-Length or Transfer-Encoding headers disagree with the response code, the response is malformed. For best compatibility, we honor the headers." in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/HttpHeaders.kt> (`promisesBody`); "A server MAY send a Content-Length header field in a 304 (Not Modified) response to a conditional GET request" in RFC 9110 section 8.6 <https://www.rfc-editor.org/rfc/rfc9110.txt>.
12. **RFC 9110 redirect rules.** "A 304 response is terminated by the end of the header section; it cannot contain content or trailers."; "the user agent MUST NOT change the request method if it performs an automatic redirection to that URI."; "Automatic redirection needs to be done with care for methods not known to be safe, as defined in Section 9.2.1, since the user might not wish to redirect an unsafe request."; "A user agent SHOULD send a User-Agent header field in each request unless specifically configured not to do so." <https://www.rfc-editor.org/rfc/rfc9110.txt> (sections 15.4.5, 15.4.8, 15.4, 10.1.5), RFC 9110.
13. **OkHttp's redirect method rules.** "`if (responseCode == HTTP_SEE_OTHER) return method != "PROPFIND"`" / "`if (responseCode == HTTP_TEMP_REDIRECT || responseCode == HTTP_PERM_REDIRECT) return false`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/HttpMethod.kt>, tag `parent-5.5.0`.
14. **OkHttp's `Request.toString()` writes the full URL and redacts four header names.** "`append(", url=")` / `append(url)`" and "`name.equals("Authorization", ignoreCase = true) ||` ... `name.equals("Set-Cookie", ignoreCase = true)`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Request.kt> and `.../internal/-UtilCommon.kt`, tag `parent-5.5.0`.
15. **`okhttp-jvm` 5.5.0 is the latest release on Maven Central.** `<release>5.5.0</release>`, `<lastUpdated>20260816161727</lastUpdated>`. <https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-jvm/maven-metadata.xml>, read 2026-10-21.
16. **Ktor's OkHttp engine keeps OkHttp redirects off and retries on; redirects are a plugin.** "`followRedirects(false)` / `followSslRedirects(false)` / `retryOnConnectionFailure(true)`" in <https://github.com/ktorio/ktor/blob/d96280165fc9890217196ed0dffd45228e35987d/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkHttpConfig.kt>; "`headers.remove(HttpHeaders.Authorization)`" and "`public var allowHttpsDowngrade: Boolean = false`" in <https://github.com/ktorio/ktor/blob/d96280165fc9890217196ed0dffd45228e35987d/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/HttpRedirect.kt>; "`client.connectionPool.evictAll()` / `client.dispatcher.executorService.shutdown()`" in `OkHttpEngine.kt` beside it. Commit `d962801` (`VERSION` 3.6.0).
17. **http4k's redirect filter.** "`private val crossOriginSensitiveHeaders: Set<String> = setOf("Authorization", "Cookie", "Proxy-Authorization")`" and "`if (attempt == 10) error("Too many redirections")`"; "`typealias HttpHandler = (request: Request) -> Response`". <https://github.com/http4k/http4k/blob/acb6a6517db0a471bbf4a4749957050020989b13/core/core/src/main/kotlin/org/http4k/filter/ClientFilters.kt> and `.../core/Http4k.kt`, commit `acb6a65`.
18. **Apollo Kotlin 4.4.1's engine contract and its OkHttp-typed overload.** "HTTP errors should not throw but instead return a [HttpResponse] indicating the status code" / "`suspend fun execute(request: HttpRequest): HttpResponse`" in <https://github.com/apollographql/apollo-kotlin/blob/v4.4.1/libraries/apollo-runtime/src/commonMain/kotlin/com/apollographql/apollo/network/http/HttpEngine.kt>; "`fun DefaultHttpEngine(okHttpClient: OkHttpClient): HttpEngine = JvmHttpEngine(okHttpClient)`" in `.../jvmCommonMain/.../DefaultHttpEngine.jvm.kt`; "`@Deprecated("Use body(BufferedSource) instead", ...)`" and "`enum class HttpMethod {` / `Get, Post`" in `libraries/apollo-api/src/commonMain/kotlin/com/apollographql/apollo/api/http/Http.kt`. Tag `v4.4.1`.
19. **Coil 3 copies `executeAsync` and carries hidden overloads for compatibility.** "`// Copied from https://github.com/square/okhttp/blob/master/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt`" in <https://github.com/coil-kt/coil/blob/ccd6696ee0bd2583d39022b2ee397dd9622d31c9/coil-network-okhttp/src/commonMain/kotlin/coil3/network/okhttp/internal/calls.kt>; "`@Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)`" in <https://github.com/coil-kt/coil/blob/ccd6696ee0bd2583d39022b2ee397dd9622d31c9/coil-network-core/src/commonMain/kotlin/coil3/network/NetworkClient.kt>. Commit `ccd6696`.
20. **Retrofit 3.0.0 parses the body inside `onResponse`.** "`public void onResponse(okhttp3.Call call, okhttp3.Response rawResponse) {` / `Response<T> response;` / `try {` / `response = parseResponse(rawResponse);`". <https://github.com/square/retrofit/blob/3.0.0/retrofit/src/main/java/retrofit2/OkHttpCall.java>, tag `3.0.0`.
21. **`kotlin.AutoCloseable` is common since Kotlin 2.0; `Charsets` is JVM-only; `decodeToString` is common and UTF-8.** AutoCloseable: "Since Kotlin 2.0", platforms Common, JS, JVM, Native, Wasm-JS, Wasm-WASI: <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-auto-closeable/>. `Charsets`: platform tag JVM only: <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-charsets/>. "Decodes a string from the bytes in UTF-8 encoding in this array. Malformed byte sequences are replaced by the replacement char `�`.": <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/decode-to-string.html>. Read 2026-10-21, Kotlin 2.2.
22. **Functions using inline classes are mangled.** "functions using inline classes are mangled by adding some stable hashcode to the function name. Therefore, `fun compute(x: UInt)` will be represented as `public final void compute-<hashcode>(int x)`". <https://kotlinlang.org/docs/inline-classes.html>, read 2026-10-21.
23. **Server idle defaults.** Apache httpd 2.4: "Amount of time the server will wait for subsequent requests on a persistent connection" with "Default: `KeepAliveTimeout 5`" <https://httpd.apache.org/docs/2.4/mod/core.html#keepalivetimeout>. Node.js: "`server.keepAliveTimeout` ... **Default:** `5000` (5 seconds)." <https://nodejs.org/api/http.html#serverkeepalivetimeout> (`http.md` of the current docs), read 2026-10-21.
24. **OpenDota accepts the key as a bearer header.** "API key can also be sent using the authorization header "Authorization: Bearer YOUR-API-KEY"". <https://api.opendota.com/api> (OpenAPI `components.securitySchemes.api_key.description`), read 2026-10-21.
25. **The prior research recorded the `Result` mangling.** "A public interface function `suspend fun execute(request): Result<Response>` is compiled with a hash-mangled JVM name (`execute-gIAlu-s`)." `docs/plans/55-http-client-stack/research/kmp-build-topology.md:213`, measured with Kotlin 2.2.0 (re-measured here on the probe interface).
