# Published test support and the single injected time source for `webtools-http`

## Question

What should the `webtools-http` test-fixtures variant contain, and how should it be shaped, so that a consumer (BotTools' adapter first) tests its own providers with no network at Level 2 and against `mockwebserver3` 5.5.0 at Level 3?

An answer has to meet these requirements:

- The fixtures are a JVM-only compilation published as a Gradle test-fixtures variant of `webtools-http` (packaging is settled in `kmp-test-fixtures.md`). They contain a fake transport serving recorded fixtures with scripted statuses and headers, one injected time source shared by the cache ticker, the budget clock and coroutine `delay`, the real transport runnable against `mockwebserver3`, and an environment-variable-gated fixture recorder that strips keys and never runs in CI.
- No JSON library appears in a public signature, and the fixtures' own dependencies reach a consumer's test classpath, so they stay few.
- No `logback-test.xml` in the fixtures. The package sits under `com.spartanlabs.webtools.http`.
- Kotlin 2.2.0, kotlinx-coroutines 1.10.2, Gradle 9.7.1, Java 11 bytecode.
- Failures travel through `kotlin.Result`; cancellation propagates.

Evidence labels: **documented** (official docs or a spec), **observed practice** (what a mature library visibly does, read in its source at a pinned tag or commit), **single source** (one author or one thread), **measured** (run by me in a throwaway probe outside the repository: Kotlin 2.2.0, Gradle 9.7.1, kotlinx-coroutines(-test) 1.10.2, Caffeine 3.3.1, OkHttp and `mockwebserver3` 5.5.0, JDK 23 compiled with `-Xjdk-release=11`, Windows 11).

## Bottom line

- **The time bundle is a `VirtualClock` built on a `TestCoroutineScheduler`, and nothing else.** It exposes `timeSource` (the scheduler's `TimeSource`), `wallClock` (a start epoch plus virtual time elapsed), `dispatcher` (`StandardTestDispatcher(scheduler)`) and `maintenanceExecutor` (`Executor { it.run() }`). It uses only stable `kotlinx-coroutines-test` 1.10.2 API; the experimental `testTimeSource` and `currentTime` are not needed. Measured in one `runTest`: advancing virtual time expired a Caffeine entry at 10 min 10 s (still a hit at 9 min 50 s), rolled a budget window, resumed `delay(5.minutes)` and moved the wall clock by 25 hours, in 26 ms of real time. The production default is the pair `TimeSource.Monotonic` plus `WallClock.System`.
- **`delay` follows the virtual clock only where the coroutine runs on the test scheduler.** A library-owned scope on `Dispatchers.Default` took 302 ms of real time and 0 ms of virtual time inside `runTest`; on `StandardTestDispatcher(testScheduler)` it took 1 ms real and 300 ms virtual (measured). Every library-owned scope in the stack therefore needs an injectable dispatcher (the cache already has one).
- **The fake transport is a script engine, not a handler queue.** `FakeHttpTransport.on(match, vararg steps)`: the most recently added matching stub wins, its steps run in order, the last step repeats, each step has a virtual-time `delay` and either responds or fails with a library failure. Calls are recorded with their virtual-time stamp. An unmatched request throws an `AssertionError` subtype with the redacted URL. It is thread-safe, mirrors the real transport's `Closed` and body-cap behaviour, and needs no opt-in to use. Measured: 200 concurrent callers consumed a `429`-then-`200` script exactly once.
- **Fixtures are raw HTTP message files (`*.http`), parsed by about forty lines of hand-written code.** A status line, headers, one blank line, then the body bytes verbatim. No parser dependency, nothing JSON in any signature, exact bytes preserved, and the same file serves the fake and (converted) `mockwebserver3`. A file without the `.http` extension is a body-only fixture (status 200, content type from the extension). They load from the consumer's own classpath; the fixtures jar ships none, so no resource path can collide. Consumers need `*.http -text` in `.gitattributes` (measured: `core.autocrlf=true` rewrites them to CRLF otherwise).
- **The recorder is a decorating `HttpTransport` behind a gate.** `WEBTOOLS_HTTP_RECORD` (`1`/`new` or `all`) enables it and any of twelve CI variables (`CI`, `GITHUB_ACTIONS`, ...) disables it. It never writes request headers, redacts sensitive query parameters by name tokens, redacts sensitive response headers and drops `Set-Cookie`, scrubs caller-supplied secret values from the whole file, writes atomically to a deterministic file name, and leaves existing files alone unless told to overwrite. Pattern-based scrubbing of response bodies is out of scope. A Gradle `Test` task is `UP-TO-DATE` when only the environment variable changes (measured), so the consumer build must declare the variable as a task input.
- **`mockwebserver3` is used manually (`start()` / `close()`), not through `mockwebserver3-junit5`.** `@StartStop` closes the server only on JUnit Jupiter 5.13 or newer (measured: the port stayed open after the class on 5.10.1), and the artifact raises JUnit to 5.13.4. `okhttp-tls` is not a fixtures dependency. No binding requirement fails. Two details refine the packaging research: `-Xexplicit-api=strict` can be added to the fixtures compile task, which gives the fixtures the visibility and return-type check that `explicitApi()` does not (measured), and `@SubclassOptInRequired` binds implementers of the port but neither users of a fake nor subclasses of an `open` fake (measured), so the fake is final.

## Findings

### F1. The scheduler's `TimeSource` and `advanceTimeBy(Duration)` are stable; `testTimeSource` and `currentTime` are not (documented source, kotlinx-coroutines-test 1.10.2)

`TestCoroutineScheduler.timeSource` is declared `public val timeSource: TimeSource.WithComparableMarks = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) { override fun read(): Long = currentTime }` with no experimental annotation. `TestScope.testTimeSource` is `@ExperimentalCoroutinesApi`, and so is `currentTime`. `advanceTimeBy(Duration)` and `runCurrent()` on the scheduler are stable. `currentTime` reads under the scheduler's lock, so a Caffeine ticker calling it from another thread is safe. `advanceTimeBy` "doesn't run the tasks that are scheduled at exactly `currentTime + delayTime`", so a clock helper must follow it with `runCurrent()`.
Source: <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestCoroutineScheduler.kt>, <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestScope.kt>, tag 1.10.2.
Consequence: a `VirtualClock` constructed from a `TestCoroutineScheduler` compiles with no opt-in (measured: no opt-in warning from the main source set of the probe), and its wall clock is `start + origin.elapsedNow()` where `origin` is a mark taken from `scheduler.timeSource` at construction.

### F2. One virtual clock drives cache ticker, budget windows, wall clock and `delay` in a single `runTest` (measured)

Probe: `VirtualClock(scheduler)`; a Caffeine `AsyncCache` with `ticker(Ticker { origin.elapsedNow().inWholeNanoseconds })` from `clock.timeSource` and `expireAfterWrite(10, MINUTES)`; the earlier call-budget probe's windowed budget (3 calls per minute, `timeSource = clock.timeSource`, `wallClock = clock.wallClock`, wall start at 2026-01-01T00:00:00Z); a `launch { delay(5.minutes) }`; a budget waiter allowed 2 minutes.

| Step | Result |
|---|---|
| 3 acquires, then a 4th with `maxWait = 0` | granted, then refused with wait 1 min 1 s (the budget's 1 s boundary guard) |
| `advanceBy(9 min 50 s)` | waiter granted at virtual 61,000 ms; `delay` resumed at 300,000 ms; cache still a hit |
| `advanceBy(20 s)` | cache entry expired (reload) |
| `advanceBy(25 h)` | `wallClock` = start + elapsed; the UTC-day index moves |
| real time for the whole test | 23-26 ms |

Controls: a cache built on `TimeSource.Monotonic` beside a virtual budget did **not** expire its entry after 11 virtual minutes (the entry "stays fresh"), so a test that fakes one clock of three passes for the wrong reason. A loader `delay(300)` on `Dispatchers.Default` inside `runTest` cost 302 ms real and 0 ms virtual; on `StandardTestDispatcher(testScheduler)` it cost 1 ms real and 300 ms virtual.

### F3. Real I/O under `runTest` is a trap; Level 3 uses real time (measured)

`runTest { withTimeout(5.seconds) { withContext(Dispatchers.IO) { <real 300 ms call to mockwebserver3> } } }` threw `TimeoutCancellationException` with the virtual clock at 5,000 ms, because the scheduler jumps over idle virtual time while a real call is pending. The same block under `runBlocking` returned `200`. Level 3 therefore runs with the production clocks under `runBlocking`; only Level 2 uses `VirtualClock`.

### F4. `@SubclassOptInRequired` gates implementers of the port, not users of a fake (measured, Kotlin 2.2.0, JVM, three Gradle subprojects)

Setup: `@SubclassOptInRequired(SeamOptIn::class) interface HttpTransport` in one module; `@OptIn(SeamOptIn::class) open class FakeTransport : HttpTransport` in a second; a consumer module with no opt-in. Results: using `FakeTransport()` as an `HttpTransport` and calling `execute` compile; **`class MyFake : FakeTransport()` also compiles with no opt-in** (the requirement is not inherited through the fake); `class Mine : HttpTransport` fails with "This class or interface requires opt-in to be implemented". No warning about `@SubclassOptInRequired` itself on 2.2.0. So the fake needs `@OptIn(SeamOptIn::class)` in the fixtures source only, consumers need nothing, and an `open` fake would let a consumer implement the seam by override without opting in; make it `final`.

### F5. `explicitApi()` does not reach the fixtures, but one compiler flag does (measured, KGP 2.2.0, Gradle 9.7.1)

`kmp-test-fixtures.md` F11 found `explicitApi()` ignores the `testFixtures` compilation. Adding `compilations["testFixtures"].compileTaskProvider.configure { compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict") }` made `compileTestFixturesKotlinJvm` fail with "Visibility must be specified in explicit API mode" and "Return type must be specified in explicit API mode" on an undecorated class. This enforces visibility and return types for the fixtures, not KDoc; KDoc stays a review item.

### F6. `mockwebserver3` 5.5.0 facts that shape Level 3 (documented source plus measured)

- `start(port: Int = 0)` binds `InetAddress.getByName("localhost")`; "Automated tests should always use port 0". Twenty servers started in one JVM had twenty distinct ports (measured), so no port lock is needed, unlike `webtools-udp`'s fixed port 9998 (`webtools-udp/build.gradle.kts:103`-`:117`).
- The task runner's threads are created with `daemon = false`; a server that is never closed keeps its threads alive (the thread was listed with `daemon=false`, measured).
- The default `QueueDispatcher` serves responses in enqueue order and **blocks** on an empty queue (`responseQueue.take()`): a request to an empty queue made the client time out after 1,002 ms (measured); `setFailFast(true)` answers `404` instead. `takeRequest()` also blocks without bound; `takeRequest(timeout, unit)` and `requestCount` do not.
- A custom `Dispatcher` (`abstract fun dispatch(request: RecordedRequest): MockResponse`) handles conditional GET. Measured: `If-None-Match: "v1"` answered `304` with the ETag, a plain request `200`, `requestCount` 2, and the recorded request headers were readable.
- HTTPS: `useHttps(sslSocketFactory: SSLSocketFactory)` takes a JDK type. With `okhttp-tls` 5.5.0 (`HeldCertificate`, `HandshakeCertificates`) an HTTPS request to the server succeeded (measured). `okhttp-tls` is a separate artifact (it resolved to `okhttp-tls:5.5.0` plus `okhttp:5.5.0`).
- `MockResponse(code, headers, body: String)` and `MockResponse.Builder`; a `Content-Length` header on the response is overwritten by the actual body size (a header of 3 with a 6-byte body delivered 6 bytes, measured), so a recorded `Content-Length` cannot corrupt a replay.
- `@StartStop` (artifact `mockwebserver3-junit5`, `api(junit-jupiter-api)`): starts every annotated `MockWebServer` field and "puts the instance in the store, so JUnit closes it for us". JUnit Jupiter 5.13 made that work: "By default, `AutoCloseable` objects put into `ExtensionContext.Store` are now treated like instances of `CloseableResource` ... and are closed automatically when the store is closed". Measured: with Jupiter 5.13.4 the port refused connections after the test class; with 5.10.1 (what `kotlin("test-junit5")` 2.2.0 requests) the port **still accepted connections** after the class finished. Adding `mockwebserver3-junit5` to a graph also lifted Jupiter from 5.10.1 to 5.13.4.
Sources: <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver/src/main/kotlin/mockwebserver3/MockWebServer.kt>, `.../QueueDispatcher.kt`, `.../Dispatcher.kt`, `mockwebserver-junit5/.../StartStopExtension.kt`, `mockwebserver-junit5/build.gradle.kts`, tag `parent-5.5.0`; <https://docs.junit.org/5.13.0/release-notes/>.

### F7. A Gradle `Test` task does not rerun when only an environment variable changes (measured, Gradle 9.7.1)

A test printing `System.getenv("WEBTOOLS_HTTP_RECORD")` ran once with the variable unset (`null`). Running again with `WEBTOOLS_HTTP_RECORD=1` reported `:test UP-TO-DATE` and printed nothing, twice. After adding `tasks.test { inputs.property("webtoolsHttpRecord", providers.environmentVariable("WEBTOOLS_HTTP_RECORD").orElse("")) }`, the run with the variable set executed (`=1`) and an immediate repeat was `UP-TO-DATE`. The test JVM's working directory was the project directory, so a relative `src/test/resources/fixtures` resolves as expected.

### F8. Raw HTTP message files are the lowest-dependency format that keeps exact bytes (measured, plus documented comparison)

- **Prototype** (about 40 lines to parse, 15 to write): optional `#` comment lines, `HTTP/1.1 <code> <reason>`, `Name: value` lines, one blank line, then every remaining byte. Measured: round trip of a body containing `\0`, `\n\n` and `0xFF` was byte-exact; a CRLF-written `304` with no body parsed; a missing status line and an empty file each failed with a named error.
- **HAR** is JSON, "required to be saved in UTF-8", with binary bodies base64-encoded in `content.text` plus `encoding`, and it records timings, cookies and page structure the stack never uses. It would need a JSON parser. <https://github.com/ahmadnassri/har-spec/blob/master/versions/1.2.md>, commit `81adb2f` (read 2026-10-21).
- **WireMock mappings** are JSON files interpreted by WireMock's matcher model; adopting the format means adopting its semantics, and WireMock's own recorder documents no redaction. <https://wiremock.org/docs/record-playback/>.
- **OkReplay tapes** are YAML tied to an OkHttp interceptor (OkReplay's last release is v1.6.0, 2020-01-27; last push 2022-08-29).
- **Git line endings**: with `core.autocrlf=true`, a committed LF `*.http` file came back with `\r\n` after checkout; with `*.http -text` in `.gitattributes` it came back unchanged (measured). The repository's `.gitattributes` today covers only `*.kt` and `*.kts`.

### F9. A recorder is dangerous by default in the comparable tools (observed practice)

| Tool | Default behaviour | Redaction |
|---|---|---|
| OkReplay (README) | "The first time a test annotated with `@OkReplay` is run, any HTTP traffic is recorded to a tape" | no feature found (README has none; a code search for "redact" returned nothing) |
| Ruby VCR 6.4.0 | default record mode `:once`: "Record the HTTP interactions if the cassette has not already been recorded" | `filter_sensitive_data` inserts a placeholder on record and swaps it back on playback; opt-in per secret |
| http4k Servirtium (6.62.0.0) | `InteractionOptions.Defaults`: "By default, no modifications are made to the raw traffic before it gets output to disk." | a `modify(request)` / `modify(response)` hook; nothing by default |
| WireMock record/playback | recorded stubs persist and serve at once | none documented; `captureHeaders` only picks match criteria |

The two shapes worth copying are VCR's explicit record modes and its placeholder substitution of known secret values; the shape to avoid is "record on first miss", which silently reaches the network in CI, and "redaction is a hook you must remember".

### F10. Environment variables that identify a CI run (documented, plus observed practice)

GitHub Actions documents `CI`: "Always set to `true`." and `GITHUB_ACTIONS`: "Always set to `true` when GitHub Actions is running the workflow." The `ci-info` package (the de-facto detector) keys vendors on variables including `GITLAB_CI`, `BUILDKITE`, `CIRCLECI`, `TF_BUILD`, `TEAMCITY_VERSION`, `TRAVIS`, `APPVEYOR`, `CODEBUILD_BUILD_ARN` and `BITBUCKET_COMMIT`, and treats `CI=false` as an explicit "not CI". Sources: <https://docs.github.com/en/actions/reference/workflows-and-actions/variables>, <https://github.com/watson/ci-info/blob/master/vendors.json>, `index.js` (read 2026-10-21).

### F11. Query-parameter secrets are found by name tokens, not substrings (measured)

Prototype: split the name on non-alphanumerics and camelCase boundaries, lower-case, and test the tokens against `{key, apikey, token, secret, password, passwd, pwd, auth, authorization, sig, signature, session, sessionid, credential, credentials, jwt, bearer}` plus a few compounds. On a corpus of 23 sensitive names (`api_key`, `apiKey`, `apikey`, `KEY`, `access_token`, `accessToken`, `client_secret`, `X-Amz-Signature`, `X-Amz-Credential`, `session_id`, `x-api-key`, ...) none was missed; on 19 ordinary names (`keyword`, `limit`, `appid`, `steamid`, `lat`, `design`, `signal`, `monkey`, `country_code`, `code`, ...) none was redacted. `sort_key` is redacted and needs the keep list. The OAuth parameter `code` is deliberately not in the set.

## How comparable projects do it

| Project | Lookup | Sequencing | Unmatched | Recording of calls | Notable |
|---|---|---|---|---|---|
| **Ktor `MockEngine`** 3.6.0 (`ktor-client-mock`) | none: a list of handlers (`MockRequestHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData`) | by invocation count, not by request: "Responses are given back in order they were added to [requestHandlers]"; `reuseHandlers = true` cycles, `MockEngine.Queue` consumes | `error("Unhandled ${data.url}")` | `requestHistory: List<HttpRequestData>` returns the live list; entries are added after the handler returns | handler count read under a lock; a Kotlin Slack thread reports flaky "Unhandled" errors with 10 concurrent requests (single source, no answer shown) |
| **OkHttp `MockWebServer`** 5.5.0 | `Dispatcher.dispatch(RecordedRequest)`; default `QueueDispatcher` | enqueue order | queue empty: blocks (or `404` with fail-fast) | `takeRequest()` queue, `requestCount` | README: "Use a Dispatcher ... to handle requests using another policy" |
| **http4k** (6.62.0.0) | an `HttpHandler` is a function; no stubbing DSL | `TrafficFilters.ReplayFrom` replays recorded pairs in order | replay mismatch: `Response(BAD_REQUEST)`; the Servirtium extension converts `NOT_IMPLEMENTED` to `throw AssertionFailedError` | `RecordTo(sink)` filter | tapes are per test method (`"$baseName.${testMethod}"`); the handler-as-function idea is what makes a decorating recorder trivial |
| **WireMock** 3.x | declarative matchers with priority; "most recently added matching stub" wins | scenarios; recorded repeats become a scenario whose "last response will continue to be returned" | `404` HTML response | verification API | heavy; global state between tests; fixed and random delays, `Fault` values |
| **Retrofit `retrofit-mock`** 3.0.0 | `BehaviorDelegate` returns canned values per interface method | none | n/a | none | `NetworkBehavior` simulates delay with variance and failures by **percentage** using `java.util.Random`; the source comments that exceptions should have their stack trace removed |
| **Ruby VCR** 6.4.0 | match on method and URI by default (cassette) | recorded order | per record mode | cassette | `:once`, `:new_episodes`, `:none`, `:all` |

Sources: Ktor <https://github.com/ktorio/ktor/blob/3.6.0/ktor-client/ktor-client-mock/common/src/io/ktor/client/engine/mock/MockEngine.kt> and `MockEngineConfig.kt`; Slack <https://slack-chats.kotlinlang.org/t/462758/u4cp1j0qp-i-stumbled-on-a-file-where-you-have-done-some-work> (found by search; the page returned HTTP 429 to my fetch, so I did not read the thread myself); http4k <https://github.com/http4k/http4k/blob/6.62.0.0-ee/core/tools/traffic-capture/src/main/kotlin/org/http4k/filter/TrafficFilters.kt>, `.../core/testing/servirtium/src/main/kotlin/org/http4k/junit/junitExtensions.kt`, `.../servirtium/InteractionOptions.kt`; WireMock <https://wiremock.org/docs/stubbing/>, <https://wiremock.org/docs/record-playback/>, <https://wiremock.org/docs/simulating-faults/>; Retrofit <https://github.com/square/retrofit/blob/master/retrofit-mock/src/main/java/retrofit2/mock/NetworkBehavior.java>; VCR <https://github.com/vcr/vcr/blob/master/lib/vcr/cassette.rb>, `configuration.rb`.

What consumers visibly like (observed): a lambda for the odd case (Ktor), declarative matching with overrides (WireMock), "last response repeats" for open-ended scripts (WireMock recordings), a decorator instead of a hook (http4k), assertion errors for mismatch (http4k Servirtium). What the sources show as sharp edges: positional matching that breaks under concurrency (Ktor, `QueueDispatcher`), a blocking empty queue (`QueueDispatcher`), a mutable shared history (Ktor), randomness in failure injection (Retrofit), a silent 404 for a mismatch (WireMock). I found no survey of complaints; this is read from source, not reported by users.

## Pitfalls

1. **One fake clock of three** passes for the wrong reason (F2 control). Assert in the fixtures' own tests that the cache, the budget and `delay` move together.
2. **A library-owned scope on a real dispatcher** makes `delay` real inside `runTest` (F2). Any default dispatcher in the stack (`Dispatchers.Default` in the cache scope, in a facade scope) must be a constructor parameter that `VirtualClock.dispatcher` can replace.
3. **Real I/O under `runTest`** turns `withTimeout` into a spurious timeout (F3). Level 3 uses `runBlocking`.
4. **`advanceTimeBy` without `runCurrent`** leaves tasks due exactly at the target unrun (F1); `VirtualClock.advanceBy` does both.
5. **`mockwebserver3`:** an unclosed server holds a non-daemon thread; `@StartStop` leaks the server on JUnit before 5.13 (F6); an empty queue blocks until the client times out; `takeRequest()` has no timeout; `enqueue` throws `ClassCastException` once a custom dispatcher replaces the queue (its KDoc says so).
6. **An `open` fake** lets a consumer implement the Experimental port without the opt-in (F4). Keep it final.
7. **A recorder that does not rerun**: `UP-TO-DATE` hides an environment-only change (F7). A recorder that records on first miss reaches the network in CI (F9).
8. **Secrets travel by more routes than the query**: a response header named `X-Api-Key`, `Set-Cookie`, a body that echoes the key. Request headers are the largest leak surface, so they are never written. Redaction by name cannot see a key echoed in a body; only a caller-supplied exact value can (F11, Known gaps).
9. **Over-redaction changes matching**: `sort_key` becomes `REDACTED`. The explicit DSL matches on the live request, not on the recorded URL, so this only affects the file's comment and name; a keep list handles the rest.
10. **Real third-party data in fixtures**: OpenDota and Steam responses contain account ids and names. Recorded files need a human review before they are committed; the recorder logs every path it wrote.
11. **CRLF**: without `-text`, Windows checkouts rewrite fixtures (F8), which changes byte counts that size-cap tests depend on.
12. **A shared `Throwable` in a repeated failure step** carries one stack trace for many calls (Retrofit's comment makes the same point). `Step.fail` takes a factory overload for the repeating case.
13. **KDoc cannot be compiler-enforced on the fixtures** (F5 enforces visibility and return types only).
14. **A fake that silently grows leniency** (default `404` for unmatched, tolerant parsing) lets tests pass that the real stack fails. The fake mirrors the real transport only where the contract is stated: `Closed` after `close()`, `BodyTooLarge` over the cap, cancellation propagating.

## Recommendation for this repo

Ship the test support as one flat package, `com.spartanlabs.webtools.http.testing`, in `webtools-http/src/jvmTestFixtures/kotlin` (packaging per `kmp-test-fixtures.md`, recipe B), with `-Xexplicit-api=strict` added to the fixtures compile task. Everything is JVM; the pure logic (format codec, redaction, gate) is plain Kotlin that the module's own `jvmTest` exercises through the compilation association. Reasoning: the fixtures' job is to make the production stack testable without bending it, so they stay thin (a clock, a script engine, a codec, a decorator) and push every judgement into parameters with the current behaviour as default.

### Dependencies of the fixtures

`jvmTestFixturesApi`: the main module, `kotlinx-coroutines-test` 1.10.2 (the clock's constructor takes a `TestCoroutineScheduler`), `mockwebserver3` 5.5.0 (Level-3 helpers use `MockWebServer`, and consumers write against it directly). Nothing else: no `mockwebserver3-junit5`, no `okhttp-tls`, no JUnit, no JSON, no logback. The fixtures log through `slf4j-api` (already on the main variant) at `INFO` for recorder output only; the fake logs nothing.

### Public types (signatures, not bodies; names provisional where they depend on sibling research)

```kotlin
package com.spartanlabs.webtools.http.testing

// ---- time: the minimal pair plus the two things a test must also inject --------------------------
public class VirtualClock(
    scheduler: TestCoroutineScheduler,
    startEpochMillis: Long = DefaultStartEpochMillis,          // 2026-01-01T00:00:00Z, a UTC midnight
) {
    public val timeSource: TimeSource            // scheduler.timeSource: cache ticker, budget TimeSource, FakeHttpTransport stamps
    public val wallClock: WallClock              // startEpochMillis + virtual time since construction: budget UTC windows, Retry-After dates
    public val dispatcher: CoroutineDispatcher   // StandardTestDispatcher(scheduler): every library-owned scope (cache loads)
    public val maintenanceExecutor: Executor     // Executor { it.run() }: Caffeine maintenance on the caller's thread
    public val elapsed: Duration
    public fun advanceBy(duration: Duration)     // advanceTimeBy + runCurrent
    public companion object { public const val DefaultStartEpochMillis: Long }
}
public fun TestScope.virtualClock(startEpochMillis: Long = VirtualClock.DefaultStartEpochMillis): VirtualClock

// ---- the fake transport ----------------------------------------------------------------------------
public class FakeHttpTransport(                  // final; @OptIn(SeamOptIn::class) inside the fixtures only
    timeSource: TimeSource = TimeSource.Monotonic,        // stamps RecordedCall.receivedAt; pass clock.timeSource
    maxResponseBytes: Long? = null,                        // mirrors TransportSettings.maxResponseBytes: BodyTooLarge
    unmatched: Unmatched = Unmatched.Fail,
) : HttpTransport {
    public fun on(match: RequestMatch, vararg steps: Step): FakeHttpTransport  // later stubs override earlier; last step repeats
    public val calls: List<RecordedCall>                   // snapshot; includes calls cancelled during a delay
    public fun callsMatching(match: RequestMatch): List<RecordedCall>
    public val unmatchedCalls: List<RecordedCall>
    public val isClosed: Boolean
    override suspend fun execute(request: HttpRequest): Result<HttpResponse>
    override fun close()                                   // idempotent; later execute -> Result.failure(Closed)
}
public fun interface RequestMatch {
    public fun matches(request: HttpRequest): Boolean
    public companion object {
        public val Any: RequestMatch
        public fun get(path: String, vararg query: Pair<String, String>): RequestMatch    // query pairs must be present; order and extras ignored
        public fun path(method: HttpMethod, path: String, vararg query: Pair<String, String>): RequestMatch
        public fun header(name: String, value: String): RequestMatch
    }
    public infix fun and(other: RequestMatch): RequestMatch
}
public sealed class Step {
    public abstract val delay: Duration                    // virtual under runTest; cancellable
    public companion object {
        public fun respond(status: Int = 200, headers: HttpHeaders = HttpHeaders.Empty, body: String = "", delay: Duration = ZERO): Step
        public fun respond(status: Int, headers: HttpHeaders, body: ByteArray, delay: Duration = ZERO): Step
        public fun fixture(fixture: Fixture, delay: Duration = ZERO): Step
        public fun fail(failure: TransportFailure, delay: Duration = ZERO): Step          // any failure cause the failure model defines
        public fun fail(delay: Duration = ZERO, failure: () -> TransportFailure): Step    // fresh instance per call
    }
}
public class RecordedCall(public val request: HttpRequest, public val receivedAt: Duration)
public sealed interface Unmatched { public data object Fail : Unmatched; public class Respond(public val step: Step) : Unmatched }
public class UnmatchedRequestError : AssertionError    // message: method, Url.redacted(), stub count - never the query

// ---- fixtures ---------------------------------------------------------------------------------------
public class Fixture(public val status: Int, public val headers: HttpHeaders, public val body: ByteArray) {
    public fun bodyText(): String
    public fun toBytes(comments: List<String> = emptyList()): ByteArray     // the .http format
    public companion object { public fun parse(bytes: ByteArray, origin: String = "<fixture>"): Fixture }
}
public class FixtureLoader(classLoader: ClassLoader = /* context loader */, root: String = "fixtures") {
    public fun load(name: String): Fixture     // "opendota/player-1.http" or body-only "opendota/player-1.json"
}
public class FixtureError : AssertionError     // missing or malformed fixture: a test-authoring mistake, thrown

// ---- Level 3: the real transport over mockwebserver3 ---------------------------------------------------
public object LoopbackTransports {
    // Untrusted rules with only loopback exempted (reached through the internal GuardRelaxation.LoopbackForTests)
    public fun untrusted(userAgent: String, settings: TransportSettings = TransportSettings.forUntrusted(),
                         resolve: ((host: String) -> List<InetAddress>)? = null): OkHttpTransport
    // Trusted rules with cleartext allowed, for a plain mockwebserver3
    public fun trusted(userAgent: String, settings: TransportSettings = TransportSettings.forTrusted()): OkHttpTransport
}
public fun MockWebServer.webtoolsUrl(path: String = "/"): Url
public fun Fixture.toMockResponse(): MockResponse
public fun MockWebServer.enqueue(fixture: Fixture)

// ---- recorder -----------------------------------------------------------------------------------------
public sealed interface RecordingGate {
    public class Enabled(public val mode: RecordMode) : RecordingGate
    public class Disabled(public val reason: String) : RecordingGate
    public companion object {
        public const val Variable: String = "WEBTOOLS_HTTP_RECORD"
        public val CiVariables: List<String>
        public fun evaluate(env: Map<String, String> = System.getenv()): RecordingGate
    }
}
public enum class RecordMode { New, Overwrite }
public class Redaction(
    keepQueryParams: Set<String> = emptySet(), extraSensitiveQueryParams: Set<String> = emptySet(),
    secrets: List<String> = emptyList(),            // exact values (raw and form-encoded), scrubbed from the whole file; >= 6 chars
    placeholder: String = "REDACTED",
)
public class FixtureRecorder(directory: Path, gate: RecordingGate.Enabled, redaction: Redaction = Redaction(),
                             warnAboveBytes: Int = 262_144) {
    public fun wrap(delegate: HttpTransport, group: String, nameFor: (HttpRequest) -> String? = { null }): HttpTransport
    public val written: List<Path>
    public val kept: List<Path>
}

// ---- fakes of the other Experimental seams (worked examples; signatures follow the budget research) -------
public class RecordingBudgetObserver : BudgetObserver { public val events: List<BudgetEvent> }
public class FakeBudgetStore(initial: BudgetSnapshot = BudgetSnapshot.Empty) : BudgetStore {
    public val saves: List<BudgetSnapshot>
    public fun failNextSave(cause: Throwable); public fun failNextLoad(cause: Throwable)
}
```

Everything not listed is `internal`: the script cursor, the `.http` lexer, the token splitter. Worked examples of Experimental seams: `FakeHttpTransport` for `HttpTransport` (the port's contract written as code: `Closed`, cap, cancellation, `Result`), `FixtureRecorder.wrap` for the decorator pattern the transport research prefers to an observer hook, `RecordingBudgetObserver` for `BudgetObserver`, `FakeBudgetStore` for `BudgetStore`. Each carries Level-2 KDoc that states its thread-safety and virtual-time contract and says it is written to be read.

### Behaviour of the fake (what the KDoc pins)

- **Lookup:** the most recently added stub whose `RequestMatch` accepts the request. A shared setup registers defaults; a test overrides one path.
- **Script:** steps run in order per stub; the last repeats (WireMock's recorded-scenario rule). Cursor and call log are guarded by one lock that is released before any suspension; `delay` runs in the caller's context, so it is virtual under `runTest` and throws `CancellationException` when cancelled. A request cancelled during its delay still counts in `calls` (it was on the wire).
- **Unmatched:** recorded in `unmatchedCalls`, then `UnmatchedRequestError` (an `AssertionError`) is thrown. Through the cache this surfaces as the fatal `Error` rethrown to each waiter (`response-cache.md`), which fails the test with the right message.
- **Fidelity:** `maxResponseBytes` returns `BodyTooLarge(limit, declared)`; after `close()` every call returns `Closed` without consuming a step.
- **Not mirrored:** URL policy, DNS, TLS, redirects, timeouts. Those are the real transport's job and are tested at Level 3 and in `webtools-http`'s own Level 2 with a stub `Dns`.

### Fixture format (`*.http`)

```
# request: GET https://api.opendota.com/api/players/1/matches?api_key=REDACTED&limit=20
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "v1"
X-RateLimit-Remaining-Minute: 59

[{"match_id":1}]
```

- Optional leading `#` lines are provenance, ignored by the parser. No timestamp (a re-record of an unchanged response is byte-identical).
- Status line `HTTP/<d>[.<d>] <3 digits>[ reason]`. Header lines `Name: value`, UTF-8; no folding. A blank line ends the headers (LF or CRLF accepted on read; LF written). Every byte after it is the body. No blank line means an empty body.
- A resource without the `.http` extension is a body-only fixture: status 200, `Content-Type` from the extension (`json`, `xml`/`rss`/`atom`, `txt`, `html`, else `application/octet-stream`).
- Location: the consumer's own test resources, `fixtures/<provider>/<name>.http` (`src/test/resources` for JVM, `src/jvmTest/resources` for multiplatform consumers). The fixtures jar ships no fixture files. The loader root is a parameter.
- Encoding: UTF-8 for the head, bytes verbatim for the body, which is what the transport returned after transparent gzip decoding. The consumer adds `**/fixtures/**/*.http -text` to `.gitattributes`. Size: `warnAboveBytes` (a judgement) logs a warning for bodies over 256 KiB; prefer trimmed, hand-edited fixtures for large documents.
- `Fixture.toMockResponse()` copies status, headers and body; MockWebServer replaces `Content-Length` itself (F6), so one file serves Level 2 and Level 3.

### Recorder contract

1. **Gate.** `RecordingGate.evaluate(env)`: `Disabled` unless `WEBTOOLS_HTTP_RECORD` is `1`, `true`, `new` (mode `New`) or `all`, `overwrite` (mode `Overwrite`). Even then `Disabled("refusing to record: <VAR> is set")` when any of `CI`, `GITHUB_ACTIONS`, `GITLAB_CI`, `JENKINS_URL`, `BUILDKITE`, `CIRCLECI`, `TF_BUILD`, `TEAMCITY_VERSION`, `TRAVIS`, `APPVEYOR`, `CODEBUILD_BUILD_ARN`, `BITBUCKET_COMMIT` is set to a value other than empty, `false` or `0`. Constructing a `FixtureRecorder` needs an `Enabled`, so the live path cannot be reached without passing the gate. "Never runs in CI" holds for every CI that sets one of those variables, and the opt-in variable is never set by CI; it is not a guarantee against a CI that sets none of them and a developer who sets the variable there.
2. **Scope.** `wrap(delegate, group)` returns a decorating `HttpTransport`: it calls the delegate, records only `Result.success` responses (any status, so `404` and `429` fixtures are captured), returns the result untouched, and never changes cancellation behaviour. Failures are not recorded. Writing is blocking file I/O inside the call (the recorder runs live, under `runBlocking`).
3. **What is written.** One request comment (method, scheme, host, path, redacted and sorted query), the status line, redacted response headers and the body. **Request headers are never written.**
4. **URL keys.** Query parameters whose name tokenizes to a sensitive token (F11) or is in `extraSensitiveQueryParams` get the value `REDACTED`, unless in `keepQueryParams`.
5. **Header keys.** Response headers flagged sensitive, named like the transport's default set (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`), or whose name tokenizes as sensitive keep their name and get the value `REDACTED`; `Set-Cookie`, `Set-Cookie2`, `Transfer-Encoding`, `Connection` and `Keep-Alive` are dropped.
6. **Body.** Not pattern-scrubbed. `Redaction.secrets` (exact values, minimum six characters) are replaced by the placeholder, raw and form-encoded, across the **whole serialized file** including the body; after replacement the recorder checks that no secret survives and refuses to write otherwise.
7. **Where and idempotency.** `<directory>/<group>/<method>-<path-slug>-<sha256(method path sortedRedactedQuery) first 8 hex>.http`. A rotated key lands on the same file. `New` keeps an existing file (listed in `kept`); `Overwrite` rewrites it (measured: byte-identical for an unchanged response). Writes go to a temporary file in the same directory and are moved into place. `nameFor` lets the caller choose a readable name.
8. **Invocation.** From a consumer test tagged `uat` (Level 5, live network), self-skipping unless the gate is `Enabled`:

```kotlin
@Tag("uat")
class RecordOpenDotaFixtures {
    @Test fun record() = runBlocking {
        val gate = RecordingGate.evaluate()
        assumeTrue(gate is RecordingGate.Enabled, (gate as? RecordingGate.Disabled)?.reason)
        val recorder = FixtureRecorder(Path.of("src/test/resources/fixtures"), gate as RecordingGate.Enabled,
            Redaction(secrets = listOfNotNull(System.getenv("OPENDOTA_API_KEY"))))
        OkHttpTransport(userAgent, policy = OutboundUrlPolicy.Trusted(/* ... */)).use { real ->
            recorder.wrap(real, "opendota").execute(HttpRequest(HttpMethod.Get, Url.parse("https://api.opendota.com/api/players/1").getOrThrow()))
        }
    }
}
```

   and run with `WEBTOOLS_HTTP_RECORD=1 ./gradlew uatTest --tests '*RecordOpenDotaFixtures'`. The consumer build declares the variable as an input of its test tasks (F7):

```kotlin
tasks.withType<Test>().configureEach {
    inputs.property("webtoolsHttpRecord", providers.environmentVariable("WEBTOOLS_HTTP_RECORD").orElse(""))
}
```

### Consumer workflow (BotTools' adapter)

Build: `testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:1.0.0"))` (and, in the composite build, the same line; substitution carries the capability, `kmp-test-fixtures.md` F7).

Level 2, on the fakes, one virtual clock for everything:

```kotlin
@Tag("component")
class OpenDotaAdapterTest {
    private val fixtures = FixtureLoader()

    @Test fun `a 429 pauses the budget and the retry succeeds`() = runTest {
        val clock = virtualClock()
        val transport = FakeHttpTransport(clock.timeSource)
            .on(RequestMatch.get("/api/players/1"),
                Step.respond(429, HttpHeaders.of("Retry-After" to "30")), Step.fixture(fixtures.load("opendota/player-1.http")))
        val adapter = OpenDotaAdapter(transport,
            cache = CaffeineResponseCache(clock.timeSource, loadDispatcher = clock.dispatcher, maintenanceExecutor = clock.maintenanceExecutor),
            budget = WindowedCallBudget(windows, timeSource = clock.timeSource, wallClock = clock.wallClock))
        val first = async { adapter.player(1) }
        runCurrent()
        assertIs<HttpFailure.RateLimited>(first.await().exceptionOrNull())
        clock.advanceBy(30.seconds)
        assertTrue(adapter.player(1).isSuccess)
        assertEquals(2, transport.calls.size)          // the transport never retried by itself
    }
}
```

BotTools' own clock is a `java.time.InstantSource`. In tests the scheduler is the single master and the fake `InstantSource` reads it: `InstantSource { Instant.ofEpochMilli(clock.wallClock.nowEpochMillis()) }`. In production the adapter builds the pair from what it has: the monotonic reading from `TimeSource.Monotonic` and the wall reading from `instantSource::millis`. A `TimeSource` built from `InstantSource.millis()` is not monotonic (`TimeSource` "may not be monotonic"), so production should not derive the cache's `TimeSource` from wall time; if it must, `object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) { override fun read() = instantSource.millis() }` is the stable (Kotlin 1.9+) building block.

Level 3, the real stack over loopback:

```kotlin
@Tag("integration")
class OpenDotaAdapterIntegrationTest {
    private val server = MockWebServer()
    @BeforeEach fun up() = server.start()
    @AfterEach fun down() = server.close()

    @Test fun `size cap holds`() = runBlocking {                 // runBlocking: real time (F3)
        server.enqueue(MockResponse(body = "x".repeat(3_000_000)))
        LoopbackTransports.untrusted("BotTools-test/1.0").use { t ->
            assertIs<TransportFailure.BodyTooLarge>(t.execute(HttpRequest(HttpMethod.Get, server.webtoolsUrl("/feed"))).exceptionOrNull())
        }
        assertEquals(1, server.requestCount)
    }
    @Test fun `the production Untrusted policy refuses loopback`() = runBlocking {
        OkHttpTransport("BotTools-test/1.0", policy = OutboundUrlPolicy.Untrusted()).use { t ->
            assertIs<TransportFailure.RefusedByPolicy>(t.execute(HttpRequest(HttpMethod.Get, server.webtoolsUrl("/"))).exceptionOrNull())
        }
        assertEquals(0, server.requestCount)
    }
}
```

A conditional-GET `Dispatcher` is ten lines in the test (F6) and is not shipped in 1.0; `webtools-feeds` and BotTools each write theirs.

### Placement and tests of the fixtures themselves

All JVM, all in the fixtures compilation. Tests live in `webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/` (the producer's `jvmTest` sees the fixtures through the association): Level 2 for the fake (script order, concurrency, cancellation, `Closed`, cap) and for the clock bundle (cache, budget and `delay` together, plus the "one clock of three" control); Level 4a for `Fixture.parse`/`toBytes` round trips, the redaction corpus and the gate matrix; Level 3 for `LoopbackTransports` over `mockwebserver3`; Level 5 (self-skipping) for the recorder against a `mockwebserver3`. `testing/support/` stays the module's private helper folder.

### Considerations (a call is needed; recommendation given)

1. **Fixture format.** Options: (a) raw HTTP message `*.http` plus body-only files (recommended); (b) a body file plus a sidecar manifest of status and headers (two files per exchange, still hand-parsed, nicer editor support for the body); (c) HAR (needs a JSON parser; carries data the stack never uses); (d) WireMock mappings (adopts a framework's semantics). (a) wins on dependencies, exact bytes and one file per exchange. If the user prefers editor highlighting for JSON bodies, body-only `*.json` already gives it for the 200 case.
2. **Default for an unmatched request.** Options: throw `UnmatchedRequestError` (recommended; same as http4k's Servirtium replay and Ktor's `error`), return `404` (WireMock; can be cached as a confirmed miss and pass for the wrong reason), return a transport failure (looks like a flaky network). `Unmatched.Respond(step)` is the opt-in for lenient fakes.
3. **A clock bundle type in `webtools-http` itself.** Options: (a) none; the cache and budget each take `timeSource` (and `wallClock`) as they do now (recommended for the ports and defaults); (b) a tiny Stable Core `StackClock(timeSource = TimeSource.Monotonic, wallClock = WallClock.System)` that the facade's default builder hands to the cache and the budget so that two defaults cannot disagree. (b) is worth it only if the facade research has the facade construct both defaults itself; `VirtualClock` converts to either shape at no cost.
4. **Tier of the fixtures' public surface.** Options: (a) Stable Core for the small named set above, everything else `internal` (recommended: a consumer's tests compile against it, so a change breaks them like any API); (b) gate it behind the single `@RequiresOptIn` marker (would force every test to opt in, against "defaults usable without opting in"). The names of the Level-3 factories depend on `TransportSettings`, `OutboundUrlPolicy` and `OkHttpTransport` as the transport and policy research settle them.

## Known gaps

- **No production stack exists yet.** The time bundle was measured against the earlier call-budget probe's budget and a synchronous Caffeine cache (the cache's async loader path under `StandardTestDispatcher` was measured in `response-cache.md`, not re-run here). `OkHttpTransport`, `LoopbackTransports`, `CaffeineResponseCache` and `WindowedCallBudget` are not built, so the Level 2 and Level 3 sketches are written against provisional names and were not compiled.
- **Failure names are provisional.** `TransportFailure` and its causes follow `http-transport.md`; the failure-model research (question 9) may rename them, and `Step.fail` and the Level-3 sketches follow whatever it settles.
- **Unmatched inside the cache's library scope** (a fatal `Error` through the async cache) was not run end to end; the claim rests on `response-cache.md`.
- **Recorder against the real transport** was not run; the prototype records through the fake as the delegate. The dropped-header set, the sensitive-token set, `CiVariables` (taken from `ci-info`'s list, not exhaustive) and `warnAboveBytes = 262144` are judgement. Body redaction beyond exact supplied secrets is not done: a key that appears in a body in transformed form (base64, hashed, truncated) will not be caught. Personal data in recorded bodies is a review task, not a tool feature.
- **`@SubclassOptInRequired` was measured on Kotlin 2.2.0 JVM only**, not from common code.
- **The Gradle input-property remedy** was measured with the `kotlin("jvm")` `test` task; the multiplatform `jvmTest` task is the same `Test` type but was not run.
- **TLS** was measured with a plain `OkHttpClient` and `okhttp-tls`, not through the transport's donor-client path; whether any `webtools-http` test needs HTTPS (downgrade-redirect refusal, `TlsFailed`) depends on the transport research and would add `okhttp-tls` as a `jvmTestImplementation` of `webtools-http`, not of the fixtures.
- **KDoc enforcement on the fixtures** (Dokka `reportUndocumented`, detekt rules) was not evaluated; only visibility and return types are compiler-checked (F5).
- **Platform coverage:** one Windows host, JDK 23 compiling to a Java 11 target; nothing was run on a JDK 11, 17 or 21 runtime. Betamax was not examined. The Ktor/Slack concurrency report is a search snippet (the thread page returned HTTP 429 to my fetch). Comparable-library "regret" statements come from reading source and docs; I found no survey of user complaints.
- **IDE import** of the `jvmTestFixtures` source set remains untested (carried from the packaging research).
- **JUnit version facts are resolution results, not quotes.** In the probe's resolved graph `kotlin-test-junit5` 2.2.0 requested Jupiter 5.10.1 (`junit-jupiter-engine:5.10.1 -> 5.13.4`) and `mockwebserver3-junit5` 5.5.0 raised it to 5.13.4. Re-resolve both POMs to confirm the exact numbers; the leak on 5.10.1 was measured by running the subject class through the JUnit launcher and connecting to its port afterwards.

## Load-bearing claims

1. **The scheduler's `TimeSource` is stable API reading virtual time.** "Returns the [TimeSource] representation of the virtual time of this scheduler." and `public val timeSource: TimeSource.WithComparableMarks = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {` / `override fun read(): Long = currentTime`. <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestCoroutineScheduler.kt>, kotlinx-coroutines 1.10.2.
2. **`currentTime` and `testTimeSource` are experimental.** `@ExperimentalCoroutinesApi` / `public var currentTime: Long = 0` ("The current virtual time in milliseconds.") in the same file; `@ExperimentalCoroutinesApi` / `public val TestScope.testTimeSource: TimeSource.WithComparableMarks get() = testScheduler.timeSource` with "The [test scheduler][TestScope.testScheduler] as a [TimeSource]." <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestScope.kt>, 1.10.2.
3. **`advanceTimeBy` stops before tasks at the target instant.** "It doesn't run the tasks that are scheduled at exactly [currentTime] + [delayTimeMillis]." and "Runs the tasks that are scheduled to execute at this moment of virtual time." (`runCurrent`). TestCoroutineScheduler.kt, 1.10.2 (URL in claim 1).
4. **`AbstractLongTimeSource` is stable since Kotlin 1.9.** "An abstract class used to implement time sources that return their readings as Long values in the specified unit." tagged "Since Kotlin 1.9", no experimental marker. <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-abstract-long-time-source/>, read 2026-10-21.
5. **`TimeSource` need not be monotonic.** "In general, TimeSource may not be monotonic, which may result in a negative duration returned from TimeMark.elapsedNow for a TimeMark obtained from a particular TimeSource. The monotonicity is guaranteed for TimeSource.Monotonic." <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-time-source/>, Kotlin 2.2 (quoted in `response-cache.md` claim 18).
6. **`MockWebServer` 5.5.0 binds loopback, wants port 0, and uses non-daemon threads.** "Starts the server on the loopback interface for the given port." / "Automated tests should always use port 0 to avoid flakiness when a specific port is unavailable." and `threadFactory("MockWebServer TaskRunner", daemon = false)`. <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver/src/main/kotlin/mockwebserver3/MockWebServer.kt>, tag `parent-5.5.0`.
7. **The default dispatcher is a blocking queue.** `val result = responseQueue.take()` and `public open fun setFailFast(failFast: Boolean)`; README: "By default MockWebServer uses a queue to specify a series of responses. Use a Dispatcher (`import okhttp3.mockwebserver.Dispatcher`) to handle requests using another policy." <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver/src/main/kotlin/mockwebserver3/QueueDispatcher.kt> and `.../mockwebserver/README.md`, tag `parent-5.5.0`.
8. **`@StartStop` relies on JUnit closing store values.** "// Put the instance in the store, so JUnit closes it for us in afterEach." <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver-junit5/src/main/kotlin/mockwebserver3/junit5/internal/StartStopExtension.kt>, tag `parent-5.5.0`; and, for the JUnit side, "By default, `AutoCloseable` objects put into `ExtensionContext.Store` are now treated like instances of `CloseableResource`" ... "and are closed automatically when the store is closed at the end of the test lifecycle." <https://docs.junit.org/5.13.0/release-notes/>, JUnit 5.13.0. The artifact's `build.gradle.kts` declares `api(libs.junit.jupiter.api)`, same tag.
9. **`MockWebServer.useHttps` takes a JDK type.** `public fun useHttps(sslSocketFactory: SSLSocketFactory) {` / "Serve requests with HTTPS rather than otherwise." MockWebServer.kt, tag `parent-5.5.0` (URL in claim 6).
10. **`MockResponse` constructor.** `public constructor(code: Int = 200, headers: Headers = headersOf(), body: String = "")`. <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver/src/main/kotlin/mockwebserver3/MockResponse.kt>, tag `parent-5.5.0`.
11. **Ktor `MockEngine` matches handlers by order and fails on exhaustion.** "Responses are given back in order they were added to [requestHandlers]." (`MockEngineConfig.kt`) and `if (invocationCount >= config.requestHandlers.size) error("Unhandled ${data.url}")`, `public val requestHistory: List<HttpRequestData> get() = _requestHistory` (`MockEngine.kt`). <https://github.com/ktorio/ktor/blob/3.6.0/ktor-client/ktor-client-mock/common/src/io/ktor/client/engine/mock/MockEngine.kt>, Ktor 3.6.0.
12. **http4k's recorder redacts nothing by default and its replay turns mismatch into an assertion failure.** "By default, no modifications are made to the raw traffic before it gets output to disk." <https://github.com/http4k/http4k/blob/6.62.0.0-ee/core/testing/servirtium/src/main/kotlin/org/http4k/servirtium/InteractionOptions.kt>; `if (status == NOT_IMPLEMENTED) throw AssertionFailedError(bodyString())` in `.../org/http4k/junit/junitExtensions.kt`; `Response(Status.BAD_REQUEST)` for a replay mismatch in `.../core/tools/traffic-capture/.../TrafficFilters.kt`; tag `6.62.0.0-ee`.
13. **WireMock: most recent stub wins, 404 on no match, last recorded response repeats, no redaction in the recorder docs.** "By default, WireMock will use the most recently added matching stub to satisfy the request." and "When a request cannot be mapped to a response, Wiremock returns an HTML response with a 404 status code." <https://wiremock.org/docs/stubbing/>; "If more requests are made after the end of the series is reached, the last response will continue to be returned." <https://wiremock.org/docs/record-playback/>; both read 2026-10-21 (the record page documents `captureHeaders` as match criteria and describes no redaction).
14. **Retrofit's mock simulates failure by percentage with `Random`.** "Create an instance with default behavior which uses {@code random} to control variance and failure calculation." and "Set the percentage of calls to {@link #calculateIsFailure()} that return {@code true}." <https://github.com/square/retrofit/blob/master/retrofit-mock/src/main/java/retrofit2/mock/NetworkBehavior.java>, default branch read 2026-10-21; latest release 3.0.0.
15. **VCR record modes and placeholder filtering.** ":once -- Record the HTTP interactions if the cassette has not already been recorded; otherwise, playback the HTTP interactions." <https://github.com/vcr/vcr/blob/master/lib/vcr/cassette.rb>; "Sets up a {#before_record} and a {#before_playback} hook that will insert a placeholder string in the cassette in place of another string." and "It's particularly useful for unique sensitive strings like API keys and passwords." <https://github.com/vcr/vcr/blob/master/lib/vcr/configuration.rb> (whose default cassette options read `record:            :once,`); default branch read 2026-10-21, latest release v6.4.0 (2025-12-22).
16. **OkReplay records on first run.** "The first time a test annotated with `@OkReplay` is run, any HTTP traffic is recorded to a tape and subsequent test runs will play back the recorded HTTP response from the tape without actually connecting to the external server." <https://github.com/airbnb/okreplay/blob/master/readme.md>; latest release v1.6.0 (2020-01-27).
17. **CI variables.** `CI`: "Always set to `true`." and `GITHUB_ACTIONS`: "Always set to `true` when GitHub Actions is running the workflow." <https://docs.github.com/en/actions/reference/workflows-and-actions/variables>, read 2026-10-21. `ci-info` `index.js`: `env.CI !== 'false' && // Bypass all checks if CI env is explicitly set to 'false'`; `vendors.json` lists `"env": "GITLAB_CI"`, `"env": "BUILDKITE"`, `"env": "CIRCLECI"`, `"env": "TF_BUILD"`, `"env": "TEAMCITY_VERSION"`, and for Jenkins `"JENKINS_URL",` / `"BUILD_ID"`. <https://github.com/watson/ci-info>, default branch read 2026-10-21.
18. **HAR is UTF-8 JSON with base64 bodies.** "HAR files are required to be saved in `UTF-8` encoding, other encodings are forbidden." and "The `text` field is either HTTP decoded text or a encoded (e.g. "base64") representation of the response body". <https://github.com/ahmadnassri/har-spec/blob/master/versions/1.2.md>, commit `81adb2f`, HAR 1.2.
19. **A Gradle test-fixtures variant resolves only with Gradle Module Metadata; capability and variants are the packaging contract.** From `kmp-test-fixtures.md`: "if the external project is *not* publishing Gradle Module Metadata, then resolution will fail". <https://docs.gradle.org/9.7.1/userguide/java_testing.html>, Gradle 9.7.1.
