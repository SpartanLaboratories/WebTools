# OkHttp 5.5.0 failure surface: the exceptions the transport receives, their mapping, the bridge and `close()`

## Question

Which throwables can `okhttp-jvm` 5.5.0 hand `OkHttpTransport` (to `Callback.onFailure`, or out of the body read inside
`Callback.onResponse`) for an HTTP/1.1 or an HTTP/2 over TLS call; what does each message hold; which failure leaf and which
`cause` does each map to; how are a `close()` cancellation, a caller cancellation, a call timeout, a socket timeout and a
connect timeout told apart; how does the `ExchangeGuard` marker surface; what is the coroutine bridge on
kotlinx-coroutines 1.10.2 that resumes exactly once on every path; and what do `close()` and the dispatcher's `ExecutorService` do.

Requirements any answer must meet: the failure model is fixed (`Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`,
`Protocol`, `Io`, `InvalidRequest(rule)`, `RefusedByPolicy(reason)`, `BodyTooLarge`, `TooManyRedirects`, `RetryRefused`, and
`HttpFailure.Closed("transport")` after `close()`); messages are constant; an OkHttp exception is a `cause` only where its message
holds nothing the failure's own fields do not already disclose, and its message is never logged above `DEBUG`; a policy refusal
(`policyRefusal()`) wins over every other mapping; cancellation of the calling coroutine is thrown, never returned; every path
resumes the continuation once; the mapping is total; nothing OkHttp-typed leaves `jvmMain`; there is no `runCatching` around a
suspend call; the client has `retryOnConnectionFailure(false)`, `followRedirects(false)`, `followSslRedirects(false)`; Java 11 API only.

## Bottom line

- **Map by type and by who cancelled, not by message.** Order, first match wins: policy marker (`policyRefusal()`), `ExchangeGuard`
  marker (found by the same cause/suppressed walk), the `closed` flag, caller cancellation (`continuation.isCancelled`, checked
  before any mapping and which makes the mapping irrelevant), then the exception's type. A call timeout is
  `InterruptedIOException` that is not a `SocketTimeoutException`, with `call.isCanceled()` true and neither cancellation
  flag set; it carries the real failure as its `cause`, so the marker walks must run first. No branch needs a message test except
  one optional exact-text refinement (a truncated fixed-length body, `ProtocolException("unexpected end of stream")`, as `Io`).
- **`cause`: attach only from a short allowlist, drop the rest.** Attach the OkHttp/JDK exception only for `Timeout`
  (`SocketTimeoutException`, and the call-timeout `InterruptedIOException("timeout")` when its whole chain is `Socket closed`),
  `DnsFailed` (`UnknownHostException`, whose message is the host the failure already carries), and `ConnectFailed` (the JDK
  exception *inside* OkHttp's `ConnectException("Failed to connect to <resolved address>")` wrapper, never the wrapper). Every
  `Protocol`, `TlsFailed`, `Io` and `InvalidRequest` leaf gets no cause: their messages hold server-sent text (status line,
  chunk header, hex dump of server bytes, certificate DN and SANs), the request host and port, a resolved address, or a header
  value. Measured on 5.5.0 for each.
- **Resume the continuation with a value, never with a throwable.** Under assertions (`-ea`, which Gradle test JVMs have) kotlinx-coroutines
  1.10.2 stack-trace recovery re-creates a thrown exception through its `(Throwable)` constructor with the original as its `cause`.
  Measured: a `Timeout(cause: Throwable?)`-shaped class resumed with `resumeWithException` arrives as `Timeout(cause = Timeout)`;
  resumed as a value it arrives untouched. So `suspendCancellableCoroutine<Result<HttpResponse>>`, `resume(result)` in both
  callbacks, `invokeOnCancellation { call.cancel() }` registered before `enqueue`. No `onCancellation` lambda is needed because the
  value holds decoded bytes, not an open `Response`.
- **Three OkHttp behaviours need handling in the callback and the interceptor.** (1) A throw out of `onResponse` leaves the
  continuation unresumed, and an `IOException` thrown there is only logged (INFO, through `java.util.logging`) with the call left
  open: resume in a `finally`, close the `Response`. (2) A non-`IOException` inside the chain (a negative status,
  `IllegalStateException("code < 0: -12")`, or any interceptor bug) reaches `onFailure` as
  `IOException("canceled due to <t>")` and is also rethrown on the dispatcher thread, where a stack trace goes to stderr and the worker
  thread dies. (3) A `407` from a direct origin throws `ProtocolException` instead of being returned, which breaks the port's
  "any status in 100..599 is a response" rule; `ExchangeGuard` can hide it as it hides `Retry-After`.
- **`close()`: flag, `cancelAll()`, `executorService.shutdown()`, `evictAll()`, each inside `catchNonFatal`; give the `Dispatcher` its
  own `ExecutorService`** with daemon threads, a recognisable name and an `UncaughtExceptionHandler`. With the default `ExecutorService` a
  forgotten `close()` keeps the JVM alive 61 s (earlier measurement); with a daemon `ExecutorService` the probe JVM exited in 0.4 s with or
  without `close()` (measured). `ConnectionPool(maxIdleConnections = maxConcurrentRequests, idleConnectionTtl, MILLISECONDS)`.

## Findings

Environment of every "measured" result: `okhttp-jvm` 5.5.0, `mockwebserver3` 5.5.0, `okhttp-tls` 5.5.0, Kotlin 2.2.0 for the
coroutine probe, kotlinx-coroutines-core 1.10.2, JDK 23.0.2 on Windows 11. JDK/OS message texts (`Connection refused: getsockopt`,
`No such host is known (host)`) are platform wording and differ on Linux and macOS; OkHttp's own texts and the exception types do
not. OkHttp 5.5.1 (tag `parent-5.5.1`) exists; its only code change in this area adds one more `SSLPeerUnverifiedException`
message (`Hostname <host> not verified (blocklisted by OkHttp)`), same type, so nothing below changes.

### F1. Where each throwable arises and what its message holds (documented from source, measured)

Source: [`RealCall`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt),
[`Http1ExchangeCodec`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http1/Http1ExchangeCodec.kt),
[`StatusLine`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/StatusLine.kt),
[`ConnectPlan`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/ConnectPlan.kt),
[`RealRoutePlanner`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealRoutePlanner.kt),
[`Http2Stream`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http2/Http2Stream.kt),
[`StreamResetException`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http2/StreamResetException.kt),
[`CallServerInterceptor`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/CallServerInterceptor.kt).
"Measured" lines are probe output (a small Java program against a raw socket server and `mockwebserver3`; HTTP/2 over TLS with an
`okhttp-tls` certificate). Content tiers used below: **none** (constant or platform text with nothing request- or server-specific),
**host** (the requested host, already a failure field), **addr** (a resolved address, or host plus port), **server** (server-sent
bytes), **request** (a header value or URL the caller supplied), **cert** (certificate DN, SANs, pin hash).

| # | Throwable (measured message) | Arises | Message holds | Leaf | `cause` |
|---|---|---|---|---|---|
| 1 | `SocketTimeoutException` `timeout` (header phase, okio timer; its cause `SocketException: Socket closed`), `Read timed out` (blocking body read, JDK), `Connect timed out` | read, write, connect timeouts; `Http2Stream` throws `SocketTimeoutException("timeout")` | none | `Timeout` | attach |
| 2 | `InterruptedIOException: timeout`, cause = the real failure | `RealCall.timeoutExit`, the call timeout, wraps *any* failure including a marker | none, but the cause chain can hold anything | `Timeout` | attach only if the chain is `Socket closed` |
| 3 | `UnknownHostException` (`No such host is known (nonexistent.invalid)`) | `Dns.SYSTEM` (the policy's `GuardedDns` passes it through); OkHttp's own `"<dns> returned no addresses for <host>"` cannot occur because `GuardedDns` never returns an empty list | host | `DnsFailed(host of the hop's URL)` | attach |
| 4 | `ConnectException: Failed to connect to /127.0.0.1:56544`, cause `ConnectException: Connection refused: getsockopt` | `ConnectPlan.connectSocket` re-wraps every `ConnectException` | addr (wrapper); none (inner) | `ConnectFailed` | the inner exception |
| 5 | `NoRouteToHostException`, `PortUnreachableException`, other `ConnectException`-family; the connect-timeout `SocketTimeoutException` is row 1 | JDK socket connect | none | `ConnectFailed` | attach (not produced in the probes; JDK hierarchy) |
| 6 | `SSLHandshakeException: (certificate_unknown) PKIX path building failed: ...`; `SSLHandshakeException: Remote host terminated the handshake` (cause `EOFException: SSL peer shut down incorrectly`) | TLS handshake | JDK text, can name subjects | `TlsFailed` | none |
| 7 | `SSLPeerUnverifiedException: Hostname 127.0.0.1 not verified:\n certificate: sha256/<pin>\n DN: CN=...\n subjectAltNames: [...]` | `ConnectPlan` hostname verification | host, cert | `TlsFailed` | none |
| 8 | `UnknownServiceException: CLEARTEXT communication not enabled for client` (a suppressed duplicate of itself), `Unable to find acceptable protocols. isFallback=..., modes=..., supported protocols=[...]` | `RealRoutePlanner.planConnectToRoute`, `ConnectPlan` | none / client configuration | `https`: `TlsFailed`; `http`: `Io`. Unreachable with the supplied client (its specs include cleartext and the policy refuses cleartext first) | none |
| 9 | `ProtocolException: Unexpected status line: <server line>` (a plain-HTTP request to a TLS port printed binary garbage after the colon) | `StatusLine.parse`, HTTP/1.1 and (`HTTP/1.1 $value`) HTTP/2 | server | `Protocol` | none |
| 10 | `ProtocolException: Received HTTP_PROXY_AUTH (407) code while not using proxy` | `RetryAndFollowUpInterceptor.followUpRequest` on any `407` through a direct route | none | `Protocol` unless hidden (F7) | none |
| 11 | `ProtocolException: HTTP 204 had non-zero Content-Length: 5`; `Unexpected 101 code on HTTP/2 connection`; `Expected ':status' header not present`; `expected N bytes but received M` | `CallServerInterceptor`, `Http2ExchangeCodec`, `Exchange` | numbers | `Protocol` | none |
| 12 | `ProtocolException: unexpected end of stream` | fixed-length body ended early (measured, body phase) | none | `Protocol`; the exact text may be mapped to `Io` (a cut-off body is an I/O event) | none |
| 13 | `ProtocolException: Expected leading [0-9a-fA-F] character but was 0x7a`; `expected chunk size and optional extensions but was "5junk-secret"` | chunk framing | server | `Protocol` | none |
| 14 | `EOFException` (null message) | chunked body ended early (measured) | none | `Io` | none |
| 15 | `IOException: unexpected end of stream on http://127.0.0.1:65231/...`, cause `EOFException: \n not found: limit=0 content=...` | EOF before a status line; also a header section above 256 KiB (`HeadersReader` throws `readUtf8LineStrict(limit)`'s `EOFException`, which `readResponseHeaders` re-wraps: measured, a 300 KB header line gave the same text) | addr (host and port of the request); the cause holds a hex dump of server bytes | `Io` (a header section too large is not distinguishable from an EOF) | none |
| 16 | gzip: `IOException: ID1ID2: actual 0x00006e6f != expected 0x00001f8b`, `EOFException: source exhausted prematurely`, `IOException: CRC: actual ... != expected ...` | okio `GzipSource` in the body read | server numbers | `Io` | none |
| 17 | `StreamResetException: stream was reset: CANCEL` / `REFUSED_STREAM` / `PROTOCOL_ERROR` / `INTERNAL_ERROR`, in the header phase and in the body phase (measured, HTTP/2); the same exception for *our own* cancel (measured: `CANCEL`) | `Http2Stream` | none (the `ErrorCode` constant) | `Io` | none (class is in an `internal` package, see F9) |
| 18 | `ConnectionShutdownException` (null message); HTTP/2 frame errors `IOException("PROTOCOL_ERROR ...")`, `FRAME_SIZE_ERROR: n`, `Expected a connection header but was <bytes>` | `Http2Connection`, `Http2Reader` | none / server | `Io` | none |
| 19 | `SocketException: Socket closed` (HTTP/1.1 cancel, measured) | our `Call.cancel()` | none | by flag (F3); otherwise `Io` | none |
| 20 | `IOException: Canceled` (cancel before `enqueue`, measured); `IOException: canceled` (`ConnectPlan`) | `RetryAndFollowUpInterceptor`, finders, `RealCall` | none | by flag (F3); otherwise `Io` | none |
| 21 | `InterruptedIOException: executor rejected` (cause null here; `RejectedExecutionException` when `executeOn` is rejected) | `AsyncCall.failRejected`, `ExecutorService` shut down | none | `Closed` after `close()`, otherwise `Io` | none |
| 22 | `IOException: canceled due to java.lang.IllegalStateException: code < 0: -12`, cause the `IllegalStateException` | `AsyncCall.run` for every non-`IOException` `Throwable` (measured for a status line `-12`, for a network interceptor throwing `IllegalStateException`, and, when an `okio` mismatch put a `NoSuchMethodError` in the chain, for an `Error`) | `$t` of the original: server digits, or anything | `Protocol` when the cause is an `IllegalStateException` whose text starts `code < 0`; otherwise `Io` | none |
| 23 | Response with code `99`, `600`, `999` (not an exception; OkHttp builds the `Response`) | `StatusLine.parse` accepts any three characters that parse as an `Int`; `Response.Builder.build` rejects only a negative code | n/a | `Protocol` (decided before the body is read) | none |
| 24 | `IllegalArgumentException` from `Request.Builder` (`Unexpected char 0x0a at 1 in X-A value: v\nsecret`, `Invalid URL host: ""`, `method POST must have a request body.`) | building the request, before any `Call` | request | `InvalidRequest(constant rule)` | none |

Further source-level facts (not probed):

- `RetryAndFollowUpInterceptor` throws `ProtocolException("Too many follow-up requests: N")` after 20 follow-ups. With the client flags above, the
  `ExchangeGuard`, and `Authenticator.NONE`, no follow-up path remains, so it is unreachable; if it ever arrives it maps to `Protocol`.
- Through a proxy (the `Trusted` policy with a `ProxySelector`), a tunnel failure is `IOException("Unexpected response code for CONNECT: <code>")`,
  `IOException("Failed to authenticate with proxy")` or `ProtocolException("Too many tunnel connections attempted: N")` (`ConnectPlan`). They map to
  `Io`, `Io`, `Protocol`, without a cause.
- Fast fallback keeps the *first* failure on top and adds later failures to `suppressed` (the url-policy finding F8 for the sources). Measured
  with `localhost` on a dual-stack machine: the call failed with `ConnectException: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:<port>` on top and the
  `SSLHandshakeException` for 127.0.0.1 in `suppressed`. The top is the route that failed first, not the most informative one.
- The thread that runs a call is renamed for its duration: `threadName("OkHttp ${redactedUrl()}") {` in `AsyncCall.run`. A thread-factory name is visible only
  while the thread is idle.

### F2. The `cause` policy: attach from an allowlist (design judgement on measured messages)

Three options existed. *Always* leaks server bytes (F1 rows 9, 13, 15, 16), certificate DNs (row 7), the resolved address (row 4) and, for
row 24, a header value; one stack trace printed at `WARN` by a consumer is enough. *Never* removes what a consumer needs most
often: which timeout, whether the connection was refused or timed out, which host did not resolve. *Allowlist* keeps both.

Attach, and nothing else:

| Leaf | Attached | What a consumer's `failure.cause` prints |
|---|---|---|
| `Timeout` | the `SocketTimeoutException` as thrown; the call-timeout `InterruptedIOException("timeout")` only when its chain is empty or `SocketException("Socket closed")` | `java.net.SocketTimeoutException: Connect timed out` / `timeout` / `Read timed out` |
| `DnsFailed` | the `UnknownHostException` when it has no cause (its message is the host) | `java.net.UnknownHostException: No such host is known (nonexistent.invalid)` (platform wording) |
| `ConnectFailed` | for the wrapper `ConnectException("Failed to connect to ...")`, its `cause` (a JDK exception with no address in its text); an unwrapped `NoRouteToHostException` or similar as is | `java.net.ConnectException: Connection refused: getsockopt` |
| every other leaf | nothing | `null` |

Consequences for the transport's logging: at `DEBUG` log the leaf, the OkHttp exception's class name, and its message only for an allowlisted
exception; for every other exception the class name alone. That makes "never logged above `DEBUG`" hold with room to spare, and a `RefusedByPolicy`
is logged once at `WARN` from the marker's reason and range class only (its message is constant by construction).

Tier: design judgement over measured evidence. Ktor and Apollo, in contrast, attach unconditionally (F8), which is acceptable for them because
their messages are not constrained.

### F3. Telling the cancellations and timeouts apart (documented, measured)

- **Caller cancellation.** `CancellableContinuationImpl.cancel` first swaps the state to `CancelledContinuation` and only then runs the
  `invokeOnCancellation` handler ([source](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CancellableContinuationImpl.kt)).
  So by the time `call.cancel()` has caused any failure callback, `continuation.isCancelled` is already `true`: it is a reliable flag, and the mapped
  result is irrelevant (the caller gets `CancellationException`; the first `resume` after cancellation is ignored). Skip the mapping and the logging
  in that case, as Ktor does (`if (continuation.isCancelled) { return }`).
- **`close()`.** A `closed` `AtomicBoolean` set *before* `cancelAll()`. Every callback checks it after the two marker walks. It also covers
  `executor rejected`, `Socket closed` and `Canceled` caused by `close()`. The same flag is checked before each hop.
- **Call timeout.** `RealCall.timeoutExit` creates `InterruptedIOException("timeout")` and `initCause`s the real failure; the timer's `timedOut()`
  calls `cancel()`, so `call.isCanceled()` is `true`. `SocketTimeoutException` is a subclass of `InterruptedIOException`, so test it first.
  Rule: `e is InterruptedIOException && e !is SocketTimeoutException && call.isCanceled()` with no other cancellation flag. Measured: the call timeout in
  the header phase and in the body phase (inside `onResponse`) both gave this shape with `isCanceled() == true`.
- **Read versus connect versus write timeout.** One leaf, `Timeout`; the phase is not needed in the model. The text differs by which timer wins
  (`timeout` in the header phase versus `Read timed out` in the body phase, measured), so a message test would be flaky. Ktor distinguishes connect
  by `message?.contains("connect", ignoreCase = true) == true`; this transport does not.
- **HTTP/2.** A caller cancel arrives as `StreamResetException: stream was reset: CANCEL` (measured, header and body phase), indistinguishable by
  type from a server-sent `CANCEL`; the flags decide, which is why they are the primary mechanism.
- **A cancelled call that never ran.** `cancelAll()` on a queued call (F6) marks it; when it is eventually promoted it fails with an `IOException`
  (`Socket closed` measured) or, if the executor was shut down, `executor rejected`. Both are `Closed` by the flag.

### F4. The `ExchangeGuard` marker surfaces unwrapped, unless the call timeout expired first (measured, documented)

`RetryAndFollowUpInterceptor.intercept` catches the `IOException` from `proceed`, `recover` returns `false` at its first check (`// The application layer
has forbidden retries.`), and `throw e.withSuppressed(recoveredFailures)` rethrows the same instance (`withSuppressed` is `apply { for (e in suppressed) addSuppressed(e) }`
over an empty list). `getResponseWithInterceptorChain` then calls `noMoreExchanges(e)` → `callDone` → `timeoutExit(e)`.

- Measured, marker thrown by a network interceptor: `onFailure` received the marker class itself, no wrapper, no suppressed.
- Measured, marker thrown after the call timeout had expired: `InterruptedIOException: timeout` with the marker as `cause`. The `policyRefusal()`-style walk
  (cause chain plus suppressed, bounded, cycle-safe) must therefore also find the guard's marker; a plain `is` test on the top exception would map a
  guard trip to `Timeout`.
- The marker must be an `IOException` (the interceptor chain declares `@Throws(IOException::class)`; a `RuntimeException` would be wrapped as in F1 row 22 and
  rethrown on the dispatcher thread). Give it a constant message and an overridden `fillInStackTrace`, like `PolicyRefusedIo`.
- A network interceptor that throws makes `exitNetworkInterceptorExchange(closeExchange = true)` detach the exchange violently, so the connection is closed and
  not returned to the pool.

### F5. Three OkHttp behaviours around the callback (documented, measured)

- **A throw out of `onResponse`.** `AsyncCall.run` sets `signalledCallback = true` before calling `onResponse`. An `IOException` thrown there goes to
  `Platform.get().log("Callback failure for ${toLoggableString()}", Platform.INFO, e)` with the comment `// Do not signal the callback twice!`: no
  `onFailure`, the call is *not* cancelled (measured `isCanceled() == false`), and the unread `Response` is left to its finalizer. A `RuntimeException`
  there makes OkHttp `cancel()` the call and rethrow on the dispatcher thread (measured: no `onFailure`, `isCanceled() == true`, stack trace on stderr).
  Both strand a continuation that was not resumed. The earlier finding for the bridge states the same; the new part is the `IOException` branch.
- **A non-`IOException` in the chain.** `AsyncCall.run`'s `catch (t: Throwable)` calls `cancel()`, and when the callback was not yet signalled calls
  `onFailure(IOException("canceled due to $t"))` with `initCause(t)`, then rethrows `t` (unless it is an `InterruptedException`). Measured: the
  `onFailure` delivery, then `Exception in thread "OkHttp Dispatcher" java.lang.IllegalStateException: code < 0: -12` printed by the default uncaught handler,
  from a *server-controlled* status line (`HTTP/1.1 -12 X`). With an `ExecutorService` whose thread factory sets an `UncaughtExceptionHandler`, the handler received
  the exception and nothing reached stderr (measured). `Response.Builder.build` is where the negative code throws (`check(code >= 0) { "code < 0: $code" }`).
- **`407` from a direct origin.** `followUpRequest` handles `HTTP_PROXY_AUTH` before the authenticator is consulted: with no HTTP proxy in the route it
  throws `ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy")` (measured: the call failed, no `Response`). The port says any
  status in 100..599 is a response, and an origin that sends `407` is common enough in the wild to matter. Measured: a network interceptor that rewrites a `407`
  to a placeholder status (599) and records the original in a private header makes the call deliver the response, and the transport restores the status; this is
  the mechanism `ExchangeGuard` already uses for `Retry-After`. Cost: one more branch in the interceptor and its test.

### F6. `Dispatcher` and the pool at `close()` (documented, measured)

- `Dispatcher.cancelAll()` cancels `readyAsyncCalls`, `runningAsyncCalls` and `runningSyncCalls`
  ([`Dispatcher`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt)). A call stays in `runningAsyncCalls`
  until `AsyncCall.run` returns, which is *after* `onResponse` returns, so a body read inside `onResponse` is cancelled too. Measured: `cancelAll()` 500 ms into a
  throttled body ended the read at 503 ms with `SocketException: Socket closed`.
- Queued calls are only marked; they are not removed and not failed. Measured with `maxRequests = 1`: after `cancelAll()` the running call failed at once
  (`SocketException: Socket closed`, 216 ms) and the queued one failed when the running one finished. With the `ExecutorService` shut down it failed with
  `InterruptedIOException: executor rejected` 3 ms after the running one (`promoteAndExecute` drains `readyAsyncCalls` into `failRejected()` when `executorIsShutdown`);
  without the shutdown it ran and failed with `Socket closed`. A queued call exists only while some call is running, so the drain always fires and every callback
  is invoked exactly once.
- A call enqueued after `executorService.shutdown()` receives `onFailure(InterruptedIOException("executor rejected"))` *synchronously on the enqueuing thread*
  (measured: it appeared immediately; source: `promoteAndExecute` calls `failRejected()` in the caller). The bridge's `invokeOnCancellation` must therefore be
  registered before `enqueue` (as every comparable bridge does), and the transport checks its own `closed` flag first, because a synchronous `execute` would still
  work on a shut-down dispatcher.
- Use `shutdown()`, not `shutdownNow()`: interrupting dispatcher threads makes `Http2Stream` throw a null-message `InterruptedIOException` and leaves the interrupt flag set.
- `ConnectionPool.evictAll()` closes and removes only idle connections (`if (connection.calls.isEmpty())` in `RealConnectionPool.evictAll`); connections in use
  belong to the cancelled calls and are closed as they release. Its cleanup runs on `TaskRunner.INSTANCE`, which is built with
  `threadFactory("$okHttpName TaskRunner", daemon = true)`: it never blocks JVM exit. OkHttp's docs: `Shutdown the dispatcher's executor service with [shutdown()][ExecutorService.shutdown]. This will also cause future calls to the client to be rejected.` and
  `Note that the connection pool's daemon thread may not exit immediately.`
- The default `ExecutorService` is `ThreadPoolExecutor(0, Int.MAX_VALUE, 60, SECONDS, SynchronousQueue(), threadFactory("$okHttpName Dispatcher", false))`: non-daemon threads with
  a 60 s idle life (the earlier lifecycle finding measured a 61 s JVM exit without `shutdown()`). `Dispatcher(executorService: ExecutorService?)` is public.
  Measured now with a daemon `ExecutorService` of the same shape, one completed call, no `close()`: the probe JVM exited in 0.4 s in total; with `close()` also 0.4 s.
- `ConnectionPool` in 5.5.0: the public constructor is `ConnectionPool(maxIdleConnections: Int, keepAliveDuration: Long, timeUnit: TimeUnit)`; the default is 5 idle
  connections for 5 minutes. The cap counts idle connections across all hosts; the keep-alive is what bounds staleness.

### F7. The coroutine bridge on kotlinx-coroutines 1.10.2 (documented, measured)

Source: [`CancellableContinuation`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CancellableContinuation.kt),
[`CancellableContinuationImpl`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CancellableContinuationImpl.kt).

- **Overloads.** Stable and used: the stdlib `Continuation.resume(value)`, `resumeWithException(e)`, `resumeWith(Result)`; `CancellableContinuation.resume(value, onCancellation: ((cause, value, context) -> Unit)?)`
  (the three-argument form; the one-argument-lambda `resume(value, (cause) -> Unit)` is `@Deprecated` at WARNING since 1.9.0); `isActive`, `isCompleted`, `isCancelled`,
  `cancel(cause)`, `invokeOnCancellation`. `@InternalCoroutinesApi`: `tryResume`, `tryResumeWithException`, `completeResume`, `initCancellability`. `@ExperimentalCoroutinesApi`:
  `resumeUndispatched*`. None of the last two are needed.
- **Prompt cancellation.** "If the [Job] of the current coroutine was cancelled while this function was suspended it will not resume successfully, even if
  [CancellableContinuation.resume] was already invoked." A value resumed after cancellation is dropped; the three-argument `resume` exists to close such a value if it is a resource.
  Here the value is a decoded `HttpResponse` (bytes); the okio `Response` is already closed inside `onResponse`, so no `onCancellation` lambda is needed. (`okhttp-coroutines`'s
  `executeAsync` hands out an open `Response`, which is why it has one.)
- **Resume once.** The first `resume` after a cancellation is ignored ("If continuation was cancelled, then resume attempt must be ignored, because cancellation is asynchronous and may race with resume"),
  a second one throws `IllegalStateException` ("Already resumed, but proposed with update ..."; the KDoc calls concurrent resumes "a programmatic error"). OkHttp invokes at most one of
  `onFailure`/`onResponse` (`signalledCallback`), so exactly-once is structural provided each callback ends in exactly one `resume`, outside any `try` that could resume again.
- **`invokeOnCancellation`.** "At most one [handler] can be installed", it "should not throw any exceptions" (they become a `CompletionHandlerException` handled as an uncaught exception), and it
  "must be fast, non-blocking, and thread-safe" with "no guarantee on the execution context". `Call.cancel()` meets that (it closes the socket, "from any thread"). Register it before `enqueue`.
- **Stack-trace recovery copies a thrown failure (measured).** With `-ea` the debug mode is on, and the documentation says stack-trace recovery "tries to create a copy of the original exception
  (with the original exception as the cause)". The copy is made through a reflective constructor in the order `(message, cause)`, `(message)`, `(cause)`, `()`, is skipped when the class
  has fields beyond `Throwable`'s, and is discarded if the message differs. Measured with a stand-in `class Timeout(cause: Throwable?) : Exception("The request timed out", cause) { constructor() : this(null) }`
  compiled with Kotlin 2.2.0: resumed through `resumeWith(Result.failure(Timeout()))` the awaiting code caught `Timeout` with `cause = Timeout: The request timed out` under `-ea` and `cause = null` without
  it; resumed as a value (`resume(Result.failure(Timeout()))` on a `CancellableContinuation<Result<String>>`) the cause stayed `null` in both runs; a class with an extra field (`DnsFailed(host, cause)`) is
  not copied. Of the leaves in `TransportFailure`, `Timeout`, `ConnectFailed`, `TlsFailed`, `Protocol`, `Io` and `RetryRefused` have the `(Throwable?)` shape and no extra field, so they are the ones that would be
  wrapped. `CopyableThrowable.createCopy` returning `null` is the documented opt-out, but it is `@ExperimentalCoroutinesApi` and a `commonMain` type change; resuming with a value needs neither.
- **The shape.** `suspendCancellableCoroutine<Result<HttpResponse>>`; both callbacks produce a finished `Result` and call `continuation.resume(result)`. Never `continuation.resumeWith(result)` (it would
  treat `Result.failure` as an exception and bring back the copy). The caller needs no `catchNonFatal` around the await for OkHttp failures, but keeps one around the whole `execute` body so that
  anything thrown while building the request or entering `enqueue` also becomes a failure leaf, and `CancellationException` is rethrown by `catchNonFatal` as it already does.

### F8. How comparable projects bridge and shut down (observed practice)

Citations are in the next section. In short: the bridge `suspendCancellableCoroutine` + `invokeOnCancellation { cancel() }` + `enqueue` is universal (OkHttp, Retrofit, Coil, Ktor);
only the bridges that return an open `Response` use `resume(value) { _, value, _ -> value.closeQuietly() }`; Retrofit alone parses the body inside `onResponse`, with `catch (Throwable e) { throwIfFatal(e); callFailure(e); return; }`;
Ktor drops a failure when `continuation.isCancelled`; shutdown is `connectionPool.evictAll()` then `dispatcher.executorService.shutdown()` (Ktor) or left to the consumer (Apollo, Coil, Retrofit); nobody
configures daemon threads or an uncaught-exception handler.

### F9. Do not reference OkHttp's `internal` classes (judgement on the dependency model)

`StreamResetException`, `ConnectionShutdownException` and `ErrorCode` live in `okhttp3.internal.http2`. They are visible to Kotlin but not API; the module declares `okhttp-jvm` 5.5.0 as an
ordinary requirement that a consumer's newer 5.x overrides, and a `catch` or `is` test on a class a later release moves would fail at link time with `NoClassDefFoundError`. Every HTTP/2 stream
and connection failure is an `IOException`, so the residual `IOException` → `Io` branch already covers them, with no type reference. The price is that a server `PROTOCOL_ERROR` reset is `Io`, not `Protocol`.

## How comparable projects do it

| Project (version) | What it does | Symbol |
|---|---|---|
| OkHttp `okhttp-coroutines` 5.5.0 | `suspendCancellableCoroutine`, `invokeOnCancellation { this.cancel() }`, `enqueue`; `onFailure` → `resumeWithException(e)`; `onResponse` → `continuation.resume(response) { _, value, _ -> value.closeQuietly() }` | `Call.executeAsync` in [`ExecuteAsync.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt) |
| Retrofit 3.0.0 | The callback parses the body inside `onResponse`: `response = parseResponse(rawResponse);` in a `try`, `catch (Throwable e) { throwIfFatal(e); callFailure(e); return; }`; the application callback is called in its own `try` | `OkHttpCall.enqueue` in [`OkHttpCall.java`](https://github.com/square/retrofit/blob/3.0.0/retrofit/src/main/java/retrofit2/OkHttpCall.java); `Call.await` in [`KotlinExtensions.kt`](https://github.com/square/retrofit/blob/3.0.0/retrofit/src/main/java/retrofit2/KotlinExtensions.kt) |
| Coil 3.6.3 | A copy of `executeAsync` ("Copied from https://github.com/square/okhttp/blob/master/okhttp-coroutines/...") because it supports OkHttp 4 | `Call.await` in [`calls.kt`](https://github.com/coil-kt/coil/blob/3.6.3/coil-network-okhttp/src/commonMain/kotlin/coil3/network/okhttp/internal/calls.kt) |
| Ktor 3.6.1 (OkHttp engine) | Cancels the call from the call context's job; `onFailure`: `if (continuation.isCancelled) { return }` then `resumeWithException(mapOkHttpException(...))`; `SocketTimeoutException` → connect or socket by `message?.contains("connect", ignoreCase = true) == true`; default `builder.dispatcher(Dispatcher())`; on shutdown `client.connectionPool.evictAll()` then `client.dispatcher.executorService.shutdown()` | `OkHttpCallback` in [`OkUtils.kt`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkUtils.kt); `OkHttpEngine.init`/`createOkHttpClient` in [`OkHttpEngine.kt`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkHttpEngine.kt) |
| Apollo Kotlin v5.2.0 | Counter-example: `suspendCancellableCoroutine` around a *blocking* `call.execute()` with `invokeOnCancellation { call.cancel() }` (cancellation reaches the call but the caller's thread is held); failures wrapped as `ApolloNetworkException(message = "Failed to execute GraphQL http network request", platformCause = exception)` (constant message, cause always kept); `close()` is empty and its termination test shuts the consumer's own `okHttpClient.dispatcher.executorService.shutdown()` and `connectionPool.evictAll()` | `OkHttpEngineImpl.execute` in [`DefaultHttpEngine.jvm.kt`](https://github.com/apollographql/apollo-kotlin/blob/v5.2.0/libraries/apollo-runtime/src/jvmCommonMain/kotlin/com/apollographql/apollo/network/http/DefaultHttpEngine.jvm.kt); [`termination/Main.kt`](https://github.com/apollographql/apollo-kotlin/blob/v5.2.0/tests/termination/src/main/kotlin/termination/Main.kt) |

No comparable client uses a daemon `ExecutorService` or a custom uncaught-exception handler, and none reads the body inside the callback except Retrofit, so the transport's combination is
its own; each element is justified by the measurements above, not by precedent.

## Pitfalls and regrets

1. **Resuming with a throwable under `-ea`.** The copy (F7) changes `failure.cause`, which this model treats as the transport's decision. It is invisible in production (no `-ea`) and shows in Level 3 tests, which run under `-ea`; resume with a value.
2. **`continuation.resumeWith(result)`** with a `Result<HttpResponse>` argument looks right and throws the failure instead of returning it.
3. **Mapping by message.** `timeout` versus `Read timed out` flips with timer timing; `unexpected end of stream` is two different exceptions; JDK/OS wording differs by platform. Only OkHttp-internal text is used, and only in a branch whose miss degrades safely (`Io`).
4. **Leaving `suppressed` to the top exception.** The first failing route is on top; measured, a dual-stack `localhost` reported `ConnectFailed` for an IPv6 refusal while the real failure on IPv4 was TLS. See the recommendation's optional rule.
5. **Reading the body after the call returns.** The earlier finding's endless-body measurement (29 s versus 0 ms) stands; the dispatcher slot is held during the body read, which also makes `cancelAll()` reach it.
6. **A hostile status line costs a stack trace and a thread.** `HTTP/1.1 -12 X` kills a dispatcher thread and prints to stderr on every request unless the `ExecutorService`'s thread factory installs a handler.
7. **`IOException` thrown from `onResponse`** is silently logged by OkHttp at INFO through `java.util.logging` with the call neither cancelled nor its body closed.
8. **A `407` from the origin** is a `ProtocolException`, not a response, until hidden.
9. **Ktor's `onResponse` returns without closing when the call is cancelled** (`if (!call.isCanceled()) { continuation.resume(response) }`), a leak this design avoids by closing in `finally`.
10. **`shutdownNow()`** interrupts the threads (null-message `InterruptedIOException` in HTTP/2, interrupt flag set).
11. **Passing OkHttp's exception to a consumer.** The wrapper `ConnectException` message holds the resolved address; the `UnknownHostException` is fine; `SSLPeerUnverifiedException` prints the certificate.

## Recommendation for this repository

### Mapping (first match wins). Inputs: the throwable `e`, the `call`, `phase` (`Exchange` for `onFailure`, `Body` for the read inside `onResponse`), the hop's `Url`, and the flags `closed`, `continuation.isCancelled`.

| # | Match | Leaf | `cause` | Log |
|---|---|---|---|---|
| 0 | `continuation.isCancelled` | none: close the response, return | n/a | none |
| 1 | `e.policyRefusal() != null` (walks cause and suppressed) | `RefusedByPolicy(reason)` | none | `WARN` once: redacted URL, reason name, range class |
| 2 | the guard marker found by the same walk | `RetryRefused` | none | `WARN` once |
| 3 | `closed` | `HttpFailure.Closed("transport")` | n/a | none |
| 4 | `e` is `UnknownHostException` | `DnsFailed(hop host)` | the exception, if it has no cause | `DEBUG` |
| 5 | `e is SocketTimeoutException`, or `e is InterruptedIOException && call.isCanceled()` (the call timeout) | `Timeout` | allowlist (F2) | `DEBUG`, class name |
| 6 | `e is SSLException`, `phase == Exchange`; or, when the top is a connect-phase failure (`ConnectException`, `NoRouteToHostException`, `SocketTimeoutException`), an `SSLException` anywhere in `suppressed` (optional refinement, Pitfall 4) | `TlsFailed` | none | `DEBUG` |
| 7 | `e is UnknownServiceException` | `TlsFailed` for `https`, `Io` for `http` | none | `DEBUG` |
| 8 | `e is ConnectException`, `NoRouteToHostException`, `PortUnreachableException` | `ConnectFailed` | the inner JDK exception | `DEBUG` |
| 9 | `e is ProtocolException` (message exactly `unexpected end of stream` → `Io`, optional) | `Protocol` | none | `DEBUG` |
| 10 | `e` is exactly `IOException` with a non-`IOException` cause (OkHttp's `canceled due to ...`): cause `IllegalStateException` starting `code < 0` → `Protocol`, anything else → `Io` | as stated | none | `DEBUG` |
| 11 | a received status outside 100..599 (decided in `onResponse`, before reading) | `Protocol` | none | `DEBUG` |
| 12 | any other `IOException` (includes `SocketException`, `EOFException`, HTTP/2 resets, `executor rejected` when not closed, `Canceled`, gzip errors, `SSLException` in `Body`) | `Io` | none | `DEBUG` |
| 13 | `IllegalArgumentException` while building the OkHttp request | `InvalidRequest("the request could not be built")` (constant) | none | `DEBUG` |
| 14 | any other `Exception` | `Io` | none | `DEBUG` |

`Error` is not mapped. `BodyTooLarge`, `TooManyRedirects` and the deadline `Timeout` are decided by the reader and the redirect loop, not by an exception.

### Bridge

```kotlin
// internal, jvmMain
private suspend fun Call.await(
    read: (Response) -> Result<HttpResponse>,          // total for Exception: reads the capped body, maps with phase = Body
    mapFailure: (IOException) -> Result<HttpResponse>, // total: the mapping above
): Result<HttpResponse> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }     // before enqueue; fast and non-throwing
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resume(mapFailure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            var outcome: Result<HttpResponse>? = null
            try {
                outcome = if (continuation.isCancelled) null else response.use(read)
            } finally {
                response.closeQuietly()                              // idempotent
                if (outcome != null) continuation.resume(outcome)     // an Error leaves outcome null:
                else if (!continuation.isCancelled) continuation.resume(Result.failure(TransportFailure.Io()))  // resume, then OkHttp rethrows it to the thread's handler
            }
        }
    })
}
```

`read` (the body read) uses `catchNonFatal` (so `CancellationException` and `Error` are not caught). The `finally` is what guarantees the single `resume` when an `Error` escapes; OkHttp
then cancels the call and rethrows to the dispatcher thread's uncaught-exception handler, which logs the class name at `DEBUG` and nothing else. `execute` wraps the whole call in `catchNonFatal`. A unit test with
`-ea` asserts that a `Timeout` returned from `execute` has `cause == null`.

### `ExchangeGuard`

Keep the marker an `IOException` with a constant message and no stack trace; throw it from the second exchange. Add the `407` hide/restore beside the `Retry-After` hide/restore (rewrite the status to a
placeholder the follow-up logic ignores and record the original in a private header that the interceptor first strips from the server's response; restore it when building `HttpResponse`). If the `407`
hide is not wanted, row 9 returns `Protocol` for it and the port's KDoc must carve `407` out of "any status in 100..599".

### Dispatcher, `close()`, pool

- `Dispatcher(executorService)` with `executorService = ThreadPoolExecutor(0, Int.MAX_VALUE, 60, SECONDS, SynchronousQueue(), factory)`: `Thread(r, "webtools-http-dispatcher-N")`, `isDaemon = true`, an
  `UncaughtExceptionHandler` that logs the class name at `DEBUG`. Same shape as OkHttp's default, so concurrency is still bounded by `maxRequests`/`maxRequestsPerHost` taken from the settings. The deviation from
  OkHttp's default and from Ktor is deliberate: this is a library whose callers' lifecycles are unknown, a forgotten `close()` should cost nothing at JVM exit, and a hostile server must not be able to print
  stack traces. `close()` is still the supported way to stop; daemon threads cannot finish an in-flight call when the JVM is exiting, which a caller that awaits its results never sees.
- `close()`: `if (!closed.compareAndSet(false, true)) return`; then `dispatcher.cancelAll()`; `dispatcher.executorService.shutdown()` (not `shutdownNow()`); `connectionPool.evictAll()`; each step in
  `catchNonFatal` that logs the class name at `DEBUG`. INFO "closed" once. `execute` checks `closed` before the first hop and before each later hop and returns `Closed("transport")`.
- `ConnectionPool(maxIdleConnections = settings.maxConcurrentRequests, keepAliveDuration = settings.idleConnectionTtl.inWholeMilliseconds, timeUnit = TimeUnit.MILLISECONDS)`. The 4 s TTL is the real
  bound on idle sockets, so the count should not be what evicts: with the defaults that is 16 (`Trusted`) or 8 (`Untrusted`) idle connections at most, against OkHttp's 5. `settings.maxConcurrentRequestsPerHost`
  is a poorer cap because a burst to many hosts would evict warm connections the TTL would have kept.

Rejected: always attaching the OkHttp exception (leaks server bytes, certificate details, the resolved address and a header value); resuming with `resumeWithException` (`-ea` copy); referencing
`StreamResetException` for finer HTTP/2 mapping (internal API); a message test for the connect timeout (flaky, no consumer need).

## Load-bearing claims

1. `AsyncCall.run` wraps any non-`IOException` `Throwable` as `IOException("canceled due to ...")`, cancels, delivers it to `onFailure` if no callback was signalled, and rethrows. Quote: `val canceledException = IOException("canceled due to $t")` and `if (t is InterruptedException) {` ... `throw t`. [`RealCall.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt), `AsyncCall.run`, `parent-5.5.0`.
2. An `IOException` thrown by `onResponse` is only logged. Quote: `// Do not signal the callback twice!` and `Platform.get().log("Callback failure for ${toLoggableString()}", Platform.INFO, e)`. Same file, `parent-5.5.0`.
3. The call timeout wraps the real failure. Quote: `val e = InterruptedIOException("timeout")` and `if (cause != null) e.initCause(cause)`. Same file, `RealCall.timeoutExit`; the timer's `timedOut()` calls `this@RealCall.cancel()`.
4. A shut-down executor makes the call fail with `executor rejected`. Quote: `val ioException = InterruptedIOException("executor rejected")`. Same file, `AsyncCall.failRejected`; and `Dispatcher.promoteAndExecute` calls `call.failRejected()` when `executorIsShutdown`. [`Dispatcher.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt), `parent-5.5.0`.
5. `cancelAll()` covers queued and running calls. Quote: "Cancel all calls currently enqueued or executing. Includes calls executed both [synchronously][Call.execute] and [asynchronously][Call.enqueue]." `Dispatcher.kt`, `parent-5.5.0`.
6. The default dispatcher threads are non-daemon, the shared task runner's are daemon. Quote: `threadFactory("$okHttpName Dispatcher", false),` ([`Dispatcher.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt)) and `val INSTANCE = TaskRunner(RealBackend(threadFactory("$okHttpName TaskRunner", daemon = true)))` ([`TaskRunner.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/concurrent/TaskRunner.kt)), `parent-5.5.0`.
7. `Dispatcher` accepts a caller's `ExecutorService`. Quote: `constructor(executorService: ExecutorService?) : this() {`. `Dispatcher.kt`, `parent-5.5.0`.
8. Shutdown guidance and pool daemon. Quote: "Shutdown the dispatcher's executor service with [shutdown()][ExecutorService.shutdown]. This will also cause future calls to the client to be rejected." and "Note that the connection pool's daemon thread may not exit immediately." [`OkHttpClient.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt), class KDoc, `parent-5.5.0`.
9. `ConnectionPool` constructor and defaults; `evictAll` evicts idle connections only. Quote: "Currently this pool holds up to 5 idle connections which will be evicted after 5 minutes of inactivity." and "Close and remove all idle connections in the pool." ([`ConnectionPool.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/ConnectionPool.kt)); `if (connection.calls.isEmpty()) {` in `evictAll` ([`RealConnectionPool.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealConnectionPool.kt)), `parent-5.5.0`.
10. With retries off the guard's marker is rethrown as is. Quote: `// The application layer has forbidden retries.` and `if (!isRecoverable) throw e.withSuppressed(recoveredFailures)`. [`RetryAndFollowUpInterceptor.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt), `parent-5.5.0`.
11. A `407` through a direct route throws. Quote: `throw ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy")`. Same file, `followUpRequest`, `parent-5.5.0`.
12. A negative status throws `IllegalStateException`; `StatusLine` accepts three characters that parse as an `Int`. Quote: `check(code >= 0) { "code < 0: $code" }` ([`Response.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Response.kt)); `throw ProtocolException("Unexpected status line: $statusLine")` ([`StatusLine.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/StatusLine.kt)), `parent-5.5.0`.
13. Message contents: `throw IOException("unexpected end of stream on $address", e)` ([`Http1ExchangeCodec.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http1/Http1ExchangeCodec.kt)); `throw ConnectException("Failed to connect to ${route.socketAddress}").apply {` ([`ConnectPlan.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/ConnectPlan.kt)); `) : IOException("stream was reset: $errorCode")` ([`StreamResetException.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http2/StreamResetException.kt)), `parent-5.5.0`.
14. A network interceptor may not repeat a request. Quote: "These interceptors must call [Interceptor.Chain.proceed] exactly once: it is an error for a network interceptor to short-circuit or repeat a network request." `OkHttpClient.kt`, `networkInterceptors` KDoc, `parent-5.5.0`.
15. `Call.cancel()` may be called from any thread. Quote: "Use this to interrupt an in-flight request from any thread." `RealCall.cancel` KDoc, `RealCall.kt`, `parent-5.5.0`.
16. OkHttp 5.5.1 only adds a blocklist message of the same exception type. Quote (patch of `ConnectPlan.kt`): `"Hostname ${address.url.host} not verified (blocklisted by OkHttp)",`. [compare `parent-5.5.0...parent-5.5.1`](https://github.com/square/okhttp/compare/parent-5.5.0...parent-5.5.1).
17. Prompt cancellation. Quote: "If the [Job] of the current coroutine was cancelled while this function was suspended it will not resume successfully, even if [CancellableContinuation.resume] was already invoked." [`CancellableContinuation.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CancellableContinuation.kt), `suspendCancellableCoroutine` KDoc, `1.10.2`.
18. Resume rules. Quote: "Concurrent invocations of [resume] methods lead to a [IllegalStateException] and are considered a programmatic error." (`CancellableContinuation.kt`); "If continuation was cancelled, then resume attempt must be ignored," ... "because cancellation is asynchronous and may race with resume." and `error("Already resumed, but proposed with update $proposedUpdate")` ([`CancellableContinuationImpl.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CancellableContinuationImpl.kt)), `1.10.2`.
19. `invokeOnCancellation` rules. Quote: "At most one [handler] can be installed on a continuation." and "The installed [handler] should not throw any exceptions." and "**Note**: Implementations of [CompletionHandler] must be fast, non-blocking, and thread-safe." `CancellableContinuation.kt`, `1.10.2`.
20. Overload status. Quote: `@Deprecated(` ... `"Use the overload that also accepts the `value` and the coroutine context in lambda",` ... `) // warning since 1.9.0, was experimental`; `public fun <R: T> resume(value: R, onCancellation: ((cause: Throwable, value: R, context: CoroutineContext) -> Unit)?)` carries no opt-in annotation, while `tryResume`, `completeResume` and `initCancellability` are `@InternalCoroutinesApi` and `resumeUndispatched` is `@ExperimentalCoroutinesApi`. `CancellableContinuation.kt`, `1.10.2`.
21. The state is cancelled before the handler runs. Quote: `val update = CancelledContinuation(this, cause, handled = state is CancelHandler || state is Segment<*>)` followed by `if (!_state.compareAndSet(state, update)) return@loop // retry on cas failure` and `is CancelHandler -> callCancelHandler(state, cause)`. `CancellableContinuationImpl.cancel`, `CancellableContinuationImpl.kt`, `1.10.2`.
22. Stack-trace recovery copies exceptions in debug mode. Quote: "stacktrace recovery machinery tries to create a copy of the original exception (with the original exception as the cause), then rewrite stacktrace of the copy" and "It can be enabled either by setting system property [DEBUG_PROPERTY_NAME] or by running Java with enabled assertions (`-ea` flag)." and "If the exception class has class-specific fields not inherited from Throwable, the exception is not copied." [`docs/topics/debugging.md`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/docs/topics/debugging.md), `1.10.2`. The constructor order: "Try to reflectively find constructor(message, cause), constructor(message), constructor(cause), or constructor(), in that order of priority." [`ExceptionsConstructor.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/internal/ExceptionsConstructor.kt), `1.10.2`.
23. `okhttp-coroutines` 5.5.0: `continuation.resume(response) { _, value, _ ->` followed by `value.closeQuietly()`. [`ExecuteAsync.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt), `parent-5.5.0`.
24. Retrofit 3.0.0 parses inside `onResponse`: `response = parseResponse(rawResponse);` then `throwIfFatal(e);` and `callFailure(e);`. [`OkHttpCall.java`](https://github.com/square/retrofit/blob/3.0.0/retrofit/src/main/java/retrofit2/OkHttpCall.java), `3.0.0`.
25. Ktor 3.6.1: `if (continuation.isCancelled) {` in `OkHttpCallback.onFailure`; `message?.contains("connect", ignoreCase = true) == true`; `client.dispatcher.executorService.shutdown()`; `builder.dispatcher(Dispatcher())`. [`OkUtils.kt`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkUtils.kt), [`OkHttpEngine.kt`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkHttpEngine.kt), `3.6.1`.
26. Apollo Kotlin v5.2.0: `platformCause = exception` in `ApolloNetworkException(...)`, `override fun close() {}`, `call.execute()` inside `suspendCancellableCoroutine`; test `okHttpClient.dispatcher.executorService.shutdown()`. [`DefaultHttpEngine.jvm.kt`](https://github.com/apollographql/apollo-kotlin/blob/v5.2.0/libraries/apollo-runtime/src/jvmCommonMain/kotlin/com/apollographql/apollo/network/http/DefaultHttpEngine.jvm.kt), [`termination/Main.kt`](https://github.com/apollographql/apollo-kotlin/blob/v5.2.0/tests/termination/src/main/kotlin/termination/Main.kt), `v5.2.0`.
27. Coil 3.6.3 copies `executeAsync`. Quote: `// Copied from https://github.com/square/okhttp/blob/master/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt`. [`calls.kt`](https://github.com/coil-kt/coil/blob/3.6.3/coil-network-okhttp/src/commonMain/kotlin/coil3/network/okhttp/internal/calls.kt), `3.6.3`.

Measured results (no outside quote; reproducible with the descriptions above): the table in F1, the cancel and dispatcher results in F3 and F6, the marker results in F4, the callback results in F5, the JVM-exit times and the stack-trace-recovery copy in F6 and F7.

## Gaps

- **Linux and macOS texts.** Every JDK/OS message (`Connection refused: getsockopt`, `No such host is known (...)`, `Read timed out`) was measured on Windows with JDK 23. The leaves do not depend on them, but the "none/host" classification of platform text for rows 3, 4 and 5 rests on the JDK's usual wording, not on a probe per OS. `NoRouteToHostException` and `PortUnreachableException` were not produced.
- **Proxies.** The tunnel failures (`Unexpected response code for CONNECT`, `Failed to authenticate with proxy`) are read from `ConnectPlan` only and were not probed.
- **TLS detail.** Only an untrusted certificate, a hostname mismatch, plain-to-TLS and TLS-to-plain were probed. A mid-body `SSLException` after the handshake was not produced; its `Io` mapping (row 12) follows from the phase rule, not from a probe. Whether a `TlsFailed` with no cause is acceptable to consumers (an expired certificate and an untrusted one look alike) is a product question; a library-written constant cause per cause type would be the next step.
- **The optional refinements** (`SSLException` in `suppressed` outranking a connect failure on top; the exact-text `unexpected end of stream` → `Io`) are judgements, not requirements; the first was measured only as the ordering artifact, not as a fix.
- **`Error` in the callback.** Resuming with `Io` and letting OkHttp rethrow (so the dispatcher's handler logs it) is a judgement; resuming the continuation with the `Error` would surface it to the caller instead. Not measured.
- **Stack-trace-recovery copy.** Measured with a stand-in class compiled with Kotlin 2.2.0 against kotlinx-coroutines 1.10.2, not with the module's own `TransportFailure` classes; their constructor shapes (read in the repository) match the reflective copy rules in `ExceptionsConstructor.kt`.
- **The `407` rewrite** was measured with a network interceptor and a placeholder status (599) on one raw response; the full hide/restore (private header stripped from server input first, status restored in `HttpResponse`, a `407` over a proxy tunnel) is not built. A `407` during `CONNECT` is a separate path (`IOException`), not this one.
- **Thread-name and handler behaviour** for the `ExecutorService` was measured on a direct `ThreadPoolExecutor` with OkHttp's `Dispatcher`; `AsyncCall.run` renames the thread while a call runs, so the factory's name shows only on idle threads.
- **Brief gaps:** none blocking. The brief did not state the supported OkHttp version range for consumers who override 5.5.0; only 5.5.0 and the 5.5.1 diff were checked.
