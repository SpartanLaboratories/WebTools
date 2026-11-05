# Final implementation: `okhttp-transport` (Issue #65)

The as-built record of `SpartanLaboratories/WebTools#65`, unit 6 (`okhttp-transport`) of [#55's architecture](../architecture.md). It states what was built,
where the work departs from [`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for the repository, its published
artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in [`consequences.md`](consequences.md).

- **Status:** built through stage S6a of [`plan.md`](plan.md) §5: S1 to S6 as planned and S6a as the plan's amendment of 2026-11-02 gives it, with the deviations
  of §2. Procedures P1 to P11 and P13 ran (§4); the Level 5 pass of P12 is §5.
- **Branch and commits:** `feat/issue-65-okhttp-transport`, cut from `master` at `53965c8`, with the research commits `0a64484`, `1b9aabf` and `aac2a81` and the
  code commits `962bbe3`, `dd4097e`, `357554a` and `300fb67` (commits 1 to 4 of plan §11), then `db82470` (`test: declare the in-flight close case Unit so
  JUnit runs it (Issue #65)`, D10), the code-landing commit. The `README.md` and `CHANGELOG.md` delta and the reconciled documents are committed after it.
- **Architecture:** [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-02) for this unit at §6.3.2, §6.6, §6.7, §7.3 and §13;
  [`consequences.md`](consequences.md) §5 states each refinement.
- **Host of the measurements:** Windows 11, JDK 23.0.2 (launcher and daemon), Gradle 9.7.1, Kotlin 2.2.0, `okhttp-jvm` and `mockwebserver3` 5.5.0. Linux and
  macOS were not exercised.
- **Versions moved:** none. `webtools-http` 1.0.0 stays unreleased until #55's stack is complete, and this landing is not a release candidate.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with the plan's amendment of 2026-11-02 (the deadline seam) and the deviations of §2. Paths are under
`webtools-http/src/`.

- **The public surface** (`jvmMain`, package `com.spartanlabs.webtools.http`, Stable Core, usable without the opt-in): the final class `OkHttpTransport`, with the
  members `execute`, `close` and `toString` over an `internal` constructor `(userAgent, guard, baseDns, donor, timeSource = TimeSource.Monotonic,
  callFactory = null)`, and the top-level factory `OkHttpTransport(userAgent, policy)`. The ABI dump `webtools-http/api/webtools-http.api` gains exactly these: the
  class with its three members, and `OkHttpTransportKt` with the factory (§4, P5 and P13).
- **The transport's parts** (`internal`):
  - `commonMain`: `TransportControlledHeaders.kt`, the twelve names the transport controls and `ruleFor`, JVM-free and compiled for `js` by the check module;
  - `jvmMain`, the pure or near-pure parts: `ThrowableSearch.kt` (`firstInChain`, the bounded walk of the cause chain and the suppressed lists, on which the changed
    `PolicyRefusals.kt` now runs `policyRefusal()`), `RedirectDecision.kt`, `FailureMapping.kt`, `HopRequest.kt`, `ReceivedResponse.kt`, `CappedBody.kt`,
    `ExchangeGuard.kt` and `CallDeadline.kt`;
  - `jvmMain`, the wiring: `TransportClient.kt` (the client, with a new dispatcher whose own `ExecutorService` runs daemon threads named
    `webtools-http-dispatcher-<n>`), `HopResponse.kt` (`settleResponse`), `CallBridge.kt` (`awaitHop`) and `OkHttpTransport.kt` (the preflight, the hop loop under one
    deadline, `close()` and the logging);
  - the deadline seam of the amendment (plan §5 S6a and §7.3): the constructor's two trailing parameters. `fetch` builds `CallDeadline(settings.callTimeout,
    timeSource)`, and every hop's `Call` comes from `hopCalls` (`callFactory ?: http.client`). Only `OkHttpTransportDeadlineComponentTest` passes them; the factory,
    `LoopbackTransports` and every other caller pass four arguments;
  - `PolicyEnforcement.kt`: its KDoc names `OkHttpTransport`.
- **The fixtures** (`jvmTestFixtures`, package `com.spartanlabs.webtools.http.testing`, Stable Core with source compatibility only): `LoopbackTransports.kt`
  (`untrusted` and `trusted`, each `(userAgent, policy, resolve)` with the last two defaulted), `FunctionDns.kt` (`internal`), `MockWebServerUrls.kt`
  (`MockWebServer.webtoolsUrl(path)` and the `internal` `loopbackUrlText`) and `FixtureMockResponses.kt` (`Fixture.toMockResponse(method)` and
  `MockWebServer.enqueue(fixture, method)`). `FakeHttpTransport.kt` skips its cap where `bodyExpected` says the transport reads no body: a response to `HEAD`, and a
  `1xx`, `204` or `304`. The fixtures' dependency list is unchanged.
- **The build file:** `okhttp-tls` 5.5.0 for `jvmTest` only, and the fixtures' comment about `mockwebserver3` in the present tense (plan §7.1); nothing else.
- **Files:** 84 new under `webtools-http/` (1 in `commonMain`, 12 in `jvmMain`, 4 fixtures, 1 test class in `commonTest`, and in `jvmTest` the 10 support files of
  plan §9.1 and 56 test classes); 6 changed (the dump, the build file, `PolicyEnforcement.kt`, `PolicyRefusals.kt`, `FakeHttpTransport.kt` and
  `FakeHttpTransportComponentTest.kt`, which gains one test).
- **Unchanged:** `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic` and `webtools-http-commoncheck` (§4, P11), and every public type of
  `webtools-http` other than the additions above.

**The tests** are in the level packages of plan §9, with the classes D7 and D8 move or add and without the case D6 leaves out. Level 4b has none, as
plan §9 says. At the final run (§4):

| Level | Classes, baseline → final | Tests, baseline → final |
|---|---|---|
| Level 1, gating | 5 → 7 | 17 → 19 |
| Level 2, component | 38 → 57 | 298 → 397 |
| Level 3, integration | 4 → 24 | 26 → 94 |
| Level 4a, deterministic | 33 → 44 | 141 → 195 |
| Level 4c, non-functional | 10 → 15 | 22 → 31 |

## 2. Deviations

Each entry names what differs from the plan, where, and why; it cites the plan section it departs from and links secondarily to what that section was based on.
The plan's amendment of 2026-11-02 (plan §5 S6a, §7.3, §9 Level 2 and §9.2 P13) is part of the plan this record is measured against: the seam it adds is in §1
and its procedure in §4, and neither departs from it. D7 and D8 come from the two test-coverage reviews after implementation and D10 from the as-built
reconciliation; all three changed tests only.

### D1. `UserAgentDeterministicTest` is written with stage S3, not S2

- **Plan:** §5 (staging table, S2) and §9.2 P3 step 2 list `UserAgentDeterministicTest` among the Level 4a tests of S2, and §11 commit 1 carries S2's tests; §7.3
  places `isValidUserAgent`, the function it tests, in `OkHttpTransport.kt`, which belongs to S3 and commit 2.
- **As built:** the test is written and run in S3, beside `OkHttpTransport.kt`. Nothing about the test or the function changes; only the stage (and so the commit
  it belongs with) moves, so that commit 1 builds on its own as §11 requires.
- **Based on:** §11 (commit 1 "builds on its own: nothing refers to the client or the transport").

### D2. The Level 2 guard test cannot build a response header whose name is not a token

- **Plan:** §9 Level 2, `ExchangeGuardComponentTest`: "a `503` that also carries a non-token entry name and a non-ASCII value does not throw and keeps both
  entries".
- **As built:** the Level 2 test covers the non-ASCII value (`Headers.Builder.addUnsafeNonAscii`) only. OkHttp 5.5.0's public `Headers` builders validate every
  name (`add`, `addUnsafeNonAscii`, `headersOf`); the lenient path is `internal`, and the module never references `okhttp3.internal`. A non-token name reaches
  `hideForFollowUp` only from the wire, which the Level 3 `ReceivedHeadersIntegrationTest` (an obs-fold continuation, written by
  `MockResponse.Builder.addHeaderLenient`) exercises against the real transport.
- **Based on:** §2 "Verified outside-world facts" (`Headers.Builder.add` validates; `newBuilder`, `removeAll` and `build` copy without validation), which is why
  the production code is unaffected: it never re-validates an existing entry.

### D3. The call-timeout `Timeout` keeps a `SocketException` cause only for the exact class

- **Plan:** §6.6 row 6 keeps the `InterruptedIOException` as `Timeout`'s cause when "its cause is `null` or a `SocketException` with no cause of its own (a type
  test; the JDK's socket texts hold no address)"; §7.11 writes the test as `cause is SocketException`. §9 Level 4a, `FailureMappingDeterministicTest`, requires the
  same wrapper "with any other cause (a `ConnectException`, a marker-free `IOException` chain)" to give `Timeout(cause = null)`.
- **As built:** `hasBenignCause` tests the exact class (`cause.javaClass == SocketException::class.java`) with no cause of its own. The two plan texts agree only
  under that reading: `java.net.ConnectException` is a `SocketException` subclass, and it is the one OkHttp re-wraps as `ConnectException("Failed to connect to
  <address>")`, so an `is SocketException` test would keep an address-bearing text as the cause, which is what the row's parenthetical rules out.
  `NoRouteToHostException`, `PortUnreachableException` and `BindException` causes are dropped too under the exact-class test; §6.6 names no case that needs them
  kept.
- **Based on:** §6.6 (the `cause` is kept only where the text holds no address) and
  [`research/okhttp-failure-surface.md`](research/okhttp-failure-surface.md) F4 (the call timeout wraps the real failure as its cause).

### D4. The close-on-accept TLS case accepts `TlsFailed` or `Io`

- **Plan:** §9 Level 3, `TlsFailureIntegrationTest`: "a plain server that closes the socket on accept (`onRequestStart(CloseSocket())`) hit by an `https` URL:
  `TlsFailed` (the JDK's `SSLHandshakeException: Remote host terminated the handshake`; recorded as an assumption)". §3 states the assumption and its fallback: "If
  a JDK in 17 to 26 raised a plain `SocketException`, that Level 3 test would see `Io` ... the affected Level 3 test is adjusted, not the production code."
- **Measured:** on this host (Windows 11, JDK 23.0.2) a TLS hello to a listener that accepts and closes at once, through the transport's own client
  configuration, fails with `java.net.SocketException: An established connection was aborted by the software in your host machine` (no cause, nothing
  suppressed), both through `MockWebServer`'s `onRequestStart(CloseSocket())` and through a raw `ServerSocket` that closes on accept. That is a plain
  `SocketException`, which §6.6 row 12 maps to `Io`.
- **As built:** the test uses the raw listener (the measured set-up) and asserts that the call ends at once with `TlsFailed` **or** `Io` and keeps no cause; the
  test's comment names both exceptions. The production mapping is unchanged. The untrusted-certificate case beside it (a certain `SSLHandshakeException`) keeps the
  `TlsFailed` mapping covered, as §3 foresaw.
- **Based on:** §3 (the assumption and its fallback) and [`research/okhttp-failure-surface.md`](research/okhttp-failure-surface.md) F1 row 6, the measured
  TLS-to-plain failure (`SSLHandshakeException: Remote host terminated the handshake`, also in [`../research/http-transport.md`](../research/http-transport.md) F7),
  which the assumption carried over to a listener that closes on accept; [`research/README.md`](research/README.md) notes under Verification that it does not
  extend to one.

### D5. Mutation 4 of P9 was not run

- **Plan:** §9.2 P9 item 4: "`OkHttpTransport.kt`: make `handleResponse` return an `HopOutcome.Done` whose body is read after the await (move the read out of
  `settleResponse`) → `CancellationIntegrationTest` is slow or fails".
- **As built:** not run. The other twelve mutations are a one-line edit each, applied to a scratch copy and reverted; this one is a rewrite of the bridge (the
  `Response` is closed in the bridge's `finally` and never leaves the callback, so a body read "after the await" needs a different `HopOutcome` and a different
  `awaitHop`), and a rewrite of that size is not a mutation of the shipped code but a second design. The property it would check, that cancelling the caller stops
  the read at once, is held by `CancellationIntegrationTest` (the join returns within a second and the server's writer stops) and by mutation 3, which removed the
  cancellation handler and made that test fail.
- **Based on:** §6.8 (the body is read inside `onResponse`, and the `Response` is closed there).

### D6. The close-step failure case of `OkHttpTransportCloseComponentTest` is not built

- **Plan:** §9 Level 2, `OkHttpTransportCloseComponentTest`: "a close step that throws is caught (a transport whose dispatcher is already shut down closes without
  throwing)".
- **As built:** the class holds the other three cases: `close()` twice gives one `INFO` "closed" line and no exception, `execute` after `close()` is
  `Closed("transport")`, and `close()` does not block. No test can shut a transport's dispatcher down before `close()` runs: the transport builds its own
  `Dispatcher` and `ExecutorService`, even from a donor client, and holds them privately. `close()` runs each of its three steps inside `catchNonFatal` and logs a
  failed step's class name at `DEBUG`; [`consequences.md`](consequences.md) §8 names what covers the case instead.
- **Based on:** R3 and §6.9 (each transport owns a new dispatcher, so `close()` cannot stop someone else's threads) and R8 (each step of `close()` inside
  `catchNonFatal`).

### D7. Four pure tables are tested at Level 4a, not at Level 2

- **Plan:** §9 Level 2 tests four pure functions in component classes: `TransportControlledHeaders.ruleFor` and `names` in
  `TransportControlledHeadersComponentTest` (`<ct>/component/webtools/http/`), `restoredStatus` in `ExchangeGuardComponentTest`, and `receiveHeaders` and
  `bodyExpected` in `ReceivedResponseComponentTest`. §5 (S1), §9.2 P2 and P3 and Acceptance 2 name `TransportControlledHeadersComponentTest`.
- **As built:** the cases, with their assertions unchanged, are in four Level 4a classes in `deterministic/webtools/http/`:
  `TransportControlledHeadersDeterministicTest` (in `commonTest`; the Level 2 file is deleted), `RestoredStatusDeterministicTest`,
  `BodyExpectedDeterministicTest` and `ReceiveHeadersDeterministicTest` (in `jvmTest`). `ExchangeGuardComponentTest` keeps the interceptor and
  `hideForFollowUp` cases, and `ReceivedResponseComponentTest` keeps `headerPairs`, which needs an `okhttp3.Response`. P2 and P3 ran before the move, so §4 records
  them under the Level 2 name.
- **Why:** the test-coverage review found each of the four a pure input-to-output table over primitives, placed at Level 2 by the file it lives in rather than by
  what it is; Level 4a holds the deterministic mappings. The first review moved three of them; the second moved `receiveHeaders`.
- **Based on:** §9 (one level per package; the plan placed each table beside the component tests of its file).

### D8. Three tests the plan does not list are added

- **Plan:** the Level 2 and Level 4a tables of §9.
- **As built,** after the test-coverage reviews, with no production code changed:
  - `HopResponseComponentTest` (Level 2, `component/webtools/http/`): `settleResponse` over hand-built `okhttp3.Response`s with a read-counting body and a guard over
    the `Untrusted` defaults, one test per branch of §6.3 and §7.12: `Protocol` before any body read for a status outside 100..599 (the restored status included),
    the hidden status restored and the private header never surfaced, `Follow` without a body read, `TooManyRedirects` with the cap, `RefusedByPolicy` at the
    `RedirectTarget` stage, `BodyTooLarge` (declared and undeclared) without the body mapper, an I/O failure of the body read through the body mapper, the no-body
    statuses and `HEAD` with an empty body whatever the declared length, and the delivered response's fields;
  - `ThrowableSearchDeterministicTest` (Level 4a, `deterministic/webtools/http/`): `Throwable.firstInChain` with an arbitrary selector, mirroring the cases
    `PolicyRefusalMarkersComponentTest` pins through `policyRefusal()`: the first match, cause and suppressed cycles, the bound of 100 throwables (the 100th found,
    the 101st not, exactly 100 selector calls over a longer chain), the cause before the suppressed list and the suppressed list in order, and a throwing selector
    propagating;
  - `WebtoolsUrlComponentTest` (Level 2, `component/webtools/http/testing/`): the published `MockWebServer.webtoolsUrl(path)`. An unstarted server is refused with
    an `IllegalStateException`, a path that does not start with `/` (`"x"`, `""`) with an `IllegalArgumentException` carrying the fixture's message, and a started
    `127.0.0.1` server's URL is the text `loopbackUrlText` writes for its address and port.
- **Why:** the first test-coverage review found no isolated test of `settleResponse` or of `firstInChain`, which §9 reaches only through the whole transport and
  through `policyRefusal()`; the second found no test of `webtoolsUrl`'s refusals. What these classes still cannot reach is in
  [`consequences.md`](consequences.md) §8.
- **Based on:** §7.12 (`settleResponse`), §7.4 (`firstInChain`) and §7.14 (`webtoolsUrl`).

### D9. `LogCapture.kt` is in commit 2, not commit 4

- **Plan:** §11 gives commit 4 "the Level 3 and Level 4c classes and the remaining support classes of §9.1", which takes in `LogCapture.kt`: commit 1 names the
  support it carries, and commit 2 names none.
- **As built:** `support/webtools/http/LogCapture.kt` (`captureTransportLogs`) is in commit 2, `dd4097e`. Five Level 2 classes of that commit capture the
  transport's log lines through it (`DispatcherExecutorComponentTest`, `OkHttpTransportConstructionComponentTest`, `OkHttpTransportPreflightComponentTest`,
  `OkHttpTransportCloseComponentTest` and `OkHttpTransportDeadlineComponentTest`), so the commit compiles on its own only with it. Every other file is in the commit
  §11 gives it (commit 1 `962bbe3`, commit 3 `357554a`, commit 4 `300fb67`): the classes D7 and D8 move or add ride with the code they test, in commits 1, 2 and 3,
  and `UserAgentDeterministicTest` with commit 2 (D1).
- **Based on:** §11 ("Each leaves the build green").

### D10. One case of `CancellationIntegrationTest` ran only once its result type was declared

- **Plan:** §9 Level 3, `CancellationIntegrationTest`: "`close()` during an in-flight call (a slow drip) ends it with `Closed("transport")`, and every
  later call is `Closed`"; §11 lists four code commits.
- **As built:** the case was written as `fun … = runBlocking { … }` ending in `assertIs<HttpFailure.Closed>(…)`, so the function returned that value, and
  JUnit Jupiter does not run a test method that returns one: every run of §4 before the fix, P7's and the first two forced runs included, executed 3 of the
  class's 4 cases (93 of the 94 Level 3 methods), with no failure or skip to show for it. The as-built reconciliation found it by comparing each class's test
  methods with its result XML. The case now declares its result type `Unit` and runs, in a fifth code commit, `db82470` (`test: declare the in-flight close
  case Unit so JUnit runs it (Issue #65)`), made after the four of §11 and before the landing; no production code changed. The forced Level 3 run after it
  passed all 94 (§4).
- **Based on:** §9 Level 3 and R8 (a call in flight when `close()` runs ends with `Closed("transport")`).

## 3. Acceptance

The plan's acceptance items, with the runs that show them (§4):

1. **Met.** `:webtools-http:compileKotlinJvm` and `:webtools-http-commoncheck:compileKotlinJs` pass; with the `js` canary in `commonMain`, `compileKotlinJs` failed
   with `Unresolved reference 'java'`, and the canary is gone (P2, P4).
2. **Met.** The five level tasks pass (the run of record: 736 tests, 0 failed, 0 skipped), and the result XML lists every test class of §9 under the names
   and levels D7 and D8 give, with every test method of each class executed since D10's fix.
3. **Met.** `apiDump`'s diff is exactly the `OkHttpTransport` class (`close`, `execute`, `toString`) and `OkHttpTransportKt.OkHttpTransport(String,
   OutboundUrlPolicy)`, both `public final`, read against §7.16 and the architecture's §9 tier table, which lists `OkHttpTransport` among the final supplied defaults
   (Stable Core); nothing `internal` is in the dump, and `apiCheck` passes (P5; after the seam, P13).
4. **Met.** `verifyJavaFloor` passes, and `:webtools-http:check :webtools-http-commoncheck:check` passes with `:build-logic:fixturesPublicationCheck` (P10);
   `:webtools-http:check` passes again after the seam (P13). The fixtures' dependency list is unchanged (P6).
5. **Met for twelve of P9's thirteen mutations and for P13's mutations 14 to 16;** mutation 4 was not run (D5). Each source was restored byte-identical.
6. **Met.** `git diff --stat` for `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic` and `webtools-http-commoncheck` is empty (P11).
7. **Met for the mechanical rules** (P8, and P13 for the seam's files). The `README.md` and `CHANGELOG.md` delta of §8 lands with these documents.
8. **Met.** The child JVM exits with code 0 in 0.63 s both with `close()` and without it (P7 (iii)); with non-daemon dispatcher threads (mutation 7) the `noclose`
   child was still alive at 20 s (P9).
9. **Met.** `OkHttpTransportDeadlineComponentTest` passes its three cases; `apiCheck` passed against the dump as it stood before the seam, a fresh `apiDump` left the
   dump byte-identical, `LoopbackTransports.kt` is unchanged, and mutations 14 to 16 each failed the cases P13 names (P13).

## 4. Measurements

Procedure results, in the order they ran.

- **P1.** Branch `feat/issue-65-okhttp-transport` at `aac2a81` on `53965c8`; the working tree held only plan-board files and this issue's documents. Gradle 9.7.1,
  launcher and daemon JVM 23.0.2 (Windows 11). Baseline `./gradlew :webtools-http:apiCheck :webtools-http:gatingTest :webtools-http:componentTest
  :webtools-http:deterministicTest :webtools-http:nonfunctionalTest :webtools-http:integrationTest :webtools-http-commoncheck:compileKotlinJs`: `BUILD SUCCESSFUL`
  (49 s). Counts: gating 5 classes / 17 tests; component 38 / 298; integration 4 / 26; deterministic 33 / 141; nonfunctional 10 / 22; 0 failures, 0 skipped.
- **P2.** `compileKotlinJvm` and `:webtools-http-commoncheck:compileKotlinJs`: `BUILD SUCCESSFUL`, the only warning `JS Environment Not Selected`.
  `TransportControlledHeadersComponentTest` (moved to Level 4a later, D7): 5 tests pass. The `js` canary: with `Canary.kt` present `compileKotlinJvm` passed and
  `compileKotlinJs` failed with `Unresolved reference 'java'`; after its removal both passed; `git status` shows no `Canary.kt`.
- **P3.** `compileKotlinJvm`: `BUILD SUCCESSFUL`, no compiler warning. `componentTest` and `deterministicTest`: `BUILD SUCCESSFUL` (11 s); component 45 classes / 342
  tests, deterministic 37 / 170, 0 failures. The result XML lists `ExchangeGuardComponentTest`, `HopRequestComponentTest`, `ReceivedResponseComponentTest`,
  `CappedBodyComponentTest`, `FailureReportComponentTest`, `CallDeadlineComponentTest`, `TransportControlledHeadersComponentTest`,
  `RedirectDecisionDeterministicTest`, `HeadersForHopDeterministicTest`, `FailureMappingDeterministicTest` and `BodyRequiredMethodDifferentialDeterministicTest`
  (`UserAgentDeterministicTest` moves to S3, D1; the pure-table cases move to Level 4a later, D7); `PolicyRefusalMarkersComponentTest` and
  `GuardedDnsComponentTest` pass unchanged over the extracted walk. `BodyRequiredMethodDifferentialDeterministicTest`: OkHttp 5.5.0 requires a body for exactly
  `POST`, `PUT`, `PATCH`, `PROPPATCH`, `REPORT` and `QUERY` among the twenty methods probed; the list in `HopRequest.kt` stands. First run of the two tasks: two
  failures, both in the tests as first written (the `ConnectException`-cause row, settled by D3; and the group-5 resolution cases, which needed
  `allowHttpsToHttp = true` on their `Trusted` guard because `HTTP://H.EXAMPLE/X` is an `https`-to-`http` hop).
- **P4.** `compileKotlinJvm` and `:webtools-http-commoncheck:compileKotlinJs`: `BUILD SUCCESSFUL`; `tailrec` on the suspend `follow` was accepted, so the loop
  rewrite of §7.3 was not needed. `gatingTest`, `componentTest`, `deterministicTest`: `BUILD SUCCESSFUL`; gating 6 classes / 18 tests, component green with
  `TransportClientComponentTest`, `DispatcherExecutorComponentTest`, `OkHttpTransportConstructionComponentTest`, `OkHttpTransportPreflightComponentTest`,
  `OkHttpTransportCloseComponentTest` and `CallBridgeComponentTest` added, deterministic with `UserAgentDeterministicTest` added (D1). In
  `CallBridgeComponentTest` the `-ea` guard held (`OkHttpTransport::class.java.desiredAssertionStatus()` is `true` on the Gradle test JVM). One first-run failure
  was in the test as first written (`SSLContext.socketFactory` returns a new instance per call; the donor test now holds the factory it hands the donor).
- **P5.** `apiDump`: the diff of `webtools-http/api/webtools-http.api` is exactly the `OkHttpTransport` class (`close`, `execute-gIAlu-s`, `toString`) and
  `OkHttpTransportKt.OkHttpTransport(String, OutboundUrlPolicy)` of §7.16, in alphabetical position, nothing else; `apiCheck`: `BUILD SUCCESSFUL`; the grep for the
  internal names prints nothing.
- **P6.** `compileTestFixturesKotlinJvm`, `gatingTest`, `componentTest`, `deterministicTest`: `BUILD SUCCESSFUL`; the fixtures compile under
  `-Xexplicit-api=strict` with no warning. Gating 7 classes / 19 tests, component 55 / 392, deterministic 39 / 176, 0 failures; the XML adds
  `LoopbackTransportsConsumerGatingTest`, `LoopbackTransportsComponentTest`, `FunctionDnsComponentTest`, `ToMockResponseComponentTest`,
  `MockWebServerEnqueueComponentTest`, `LoopbackUrlTextDeterministicTest` and the new test of `FakeHttpTransportComponentTest`. One first-run failure was in the new
  fake test as first written (a `HEAD` request against a `RequestMatch.get` stub). `git diff webtools-http/build.gradle.kts` showed the comment edit alone; the
  `"jvmTestFixtures…"` dependency lines are unchanged.
- **P7.** `integrationTest`: `BUILD SUCCESSFUL` twice (the second with `--rerun`), 24 classes / 93 tests, 0 failures; the XML lists every Level 3 class of §9 (20
  new). The classes hold 94 test methods; one case of `CancellationIntegrationTest` was not executed in these runs (D10). `tasklist` showed the same two `java.exe`
  processes (the Gradle and Kotlin daemons) before and after: no stray child JVM. `ProxySettingsIntegrationTest` and `HostileStatusLineNonFunctionalTest` pass
  alone. First run: one failure, the close-on-accept TLS case (D4). `nonfunctionalTest`: `BUILD SUCCESSFUL`, 15 classes / 31 tests, 0 failures. Measurements the
  tests settle: **(i)** `503` + `Retry-After: 0`, `408` + `Connection: close`, `429` + `Retry-After: 0`: one request each, the status returned; the dropped pooled
  connection: two requests on one connection (`exchangeIndex` 0 and 1), call 2 `Io`; each redirect hop one request. **(ii)** the proxy A/B: `trusted` proxy 1 /
  target 0 with an absolute target, `untrusted` proxy 0 / target 1; the SOCKS control held (the `trusted` call reached the listener, the `untrusted` call did not
  and reached the origin). **(iii)** the child JVM exited with code 0 in 0.63 s both with and without `close()`. **(iv)** `TlsFailed` from the untrusted
  certificate (an `SSLHandshakeException`); the close-on-accept case raised a plain `SocketException` on this host (D4). **(v)** the 1 s drip ends in 0.9 to 3 s and
  the 700 ms headers delay in 0.6 to 2.5 s (the asserted windows); the cancel joins within 1 s and the server's writer stops within 5 s; the redirect chain of
  `maxRedirects = 3` stops after 4 requests.
- **P8.** Every mechanical rule prints nothing (the `catch` grep prints the two `NonFatal.kt` lines; the `logger(` grep prints `Log.jvm.kt`, `GuardedDns.kt` and
  `OkHttpTransport.kt`), after one line comment in `OkHttpTransport.kt` was reworded so the `shutdownNow` grep, which the plan runs without the comment filter, does
  not match a comment.
- **P9.** Twelve of the thirteen mutations were run (item 4 was not, D5), each on a scratch copy, the named tasks run with `--tests` for the named classes, the
  original restored and `diff`ed (no difference, every time): 1 (`503` not hidden) failed `ExchangeCountIntegrationTest` (the `503` cases) and
  `ExchangeGuardComponentTest`; 2 (`retryOnConnectionFailure(true)`) failed `ExchangeCountIntegrationTest` and `TransportClientComponentTest`; 3 (no cancellation
  handler) failed `CancellationIntegrationTest` (the endless body) and `CallBridgeComponentTest`; 5 (`headersForHop` returns the headers) failed
  `CrossOriginStripIntegrationTest` and `HeadersForHopDeterministicTest`; 6 (no controlled-header check) failed `ControlledHeadersIntegrationTest` and
  `OkHttpTransportPreflightComponentTest`; 7 (non-daemon threads) failed `JvmExitIntegrationTest`'s `noclose` case, the child still alive at 20 s; 8 (every leaf
  keeps its cause) failed `FailureMappingDeterministicTest` (and `TransportSecrecyNonFunctionalTest` was in the same run); 9 (no `finally` in `settle`) failed the
  `Error` case of `CallBridgeComponentTest`; 10 (admission before the cap) failed group 10 of `RedirectDecisionDeterministicTest`; 11 (no `identity` for `Range`)
  failed `ControlledHeadersIntegrationTest`; 12 (`enter()` always true) failed `ExchangeGuardWireIntegrationTest` and `ExchangeGuardComponentTest`; 13 (`407` not
  hidden) failed `ExchangeGuardStatusIntegrationTest` and `ExchangeGuardComponentTest`.
- **P10.** `verifyJavaFloor`: `BUILD SUCCESSFUL`; its report reads "java floor 11 (class-file major <= 55): 226 classes in 2 jar(s), 6 library variants in 2
  metadata file(s), 3671 classes on the runtime classpath" (`okhttp-tls` is test scope and outside the scan). `:webtools-http:check
  :webtools-http-commoncheck:check`: `BUILD SUCCESSFUL` in 2 min 9 s, `:build-logic:fixturesPublicationCheck` included; the `jvmTest` run inside it: 140 classes /
  711 tests, 0 failures. Then the five level tasks in one invocation on the restored sources (12 s): gating 7 classes / 19 tests (baseline 5 / 17), component 55 /
  392 (38 / 298), integration 24 / 93 (4 / 26), deterministic 39 / 176 (33 / 141), nonfunctional 15 / 31 (10 / 22); 0 failures, 0 skipped. Gradle applies
  `--rerun` only to the task it follows, so that invocation executed one task again and the other four counts are their tasks' last results; the `jvmTest` run
  before it had executed every test. The forced runs of record close this section.
- **P11.** `git diff --stat -- webtools-udp webtools-scraping webtools-browser build-logic webtools-http-commoncheck` is empty; `git diff
  webtools-http/build.gradle.kts` shows the two edits of §7.1 only; `git status --short` shows no `Canary.kt`, no scratch copy, no probe and no `build/` directory.
  Changed tracked files: `webtools-http/api/webtools-http.api`, `webtools-http/build.gradle.kts`, `PolicyEnforcement.kt`, `PolicyRefusals.kt`,
  `FakeHttpTransport.kt`, `FakeHttpTransportComponentTest.kt`; 77 new files under `webtools-http/` at that point (1 `commonMain`, 12 `jvmMain`, 4 fixtures, 1
  `commonTest`, 10 test support, 49 test classes; §1 has the final count).
- **P13** (the amendment's procedure, after P11):
  1. Before the edit: `webtools-http.api` `90efca31adc73a2dffd8ba59053e37af3fc59c61`, `LoopbackTransports.kt` `14d86e83894d7e3e76d7e2dbe26b78b14bf250b4`.
  2. `compileKotlinJvm compileTestFixturesKotlinJvm`: `BUILD SUCCESSFUL`, no compiler `w:` beyond the known `JS Environment Not Selected`.
  3. `OkHttpTransportDeadlineComponentTest`: 3 tests, 0 failures. Its cases run over a `ManualTimeSource` and a `Call.Factory` of `FakeCall`s whose hop 1
     (`GET https://example.com/start`) sets the clock inside `enqueue` and answers `302 Location: /next`, with a resolver that is never asked: the clock at the whole
     `callTimeout` gives `Timeout` with `cause == null`, one `Call`, hop 1 enqueued and never cancelled, one `DEBUG` redirect line and one `DEBUG`
     `Timeout failed for https://example.com/next` line, and no `WARN`; the clock 999 µs short gives `Timeout` and one `Call`; the clock 1 ms short creates hop 2
     with `timeoutNanos() == 1_000_000` and returns the `200` with `url == https://example.com/next` and `redirectChain == [RedirectHop(https://example.com/start,
     302)]`, hop 1's limit the whole timeout in milliseconds.
  4. `apiCheck` before any `apiDump`: passes; after `apiDump` the dump's hash is unchanged (`90efca31…`), and the grep for `Lkotlin/time/TimeSource;` and
     `Lokhttp3/Call$Factory;` prints nothing.
  5. `LoopbackTransports.kt`'s hash is unchanged (`14d86e83…`), both `OkHttpTransport(` calls pass four arguments, and no `callFactory` or `hopCalls` is under the
     fixtures.
  6. The P8 rules hold for `OkHttpTransport.kt` (KDoc coverage lists nothing; no `catch`, `runCatching`, `okhttp3.internal`, `data class`, `TODO` or
     `Thread.sleep`; `http.client.newCall` prints nothing) and for the test file (package and path agree, one class, LF, no `Thread.sleep`, no `OptIn`).
  7. Mutations, each on a scratch copy outside the repository and restored byte-identical (`diff` empty), the class green again afterwards: 14 (the pre-hop check
     deleted) failed cases 1 and 2 (the result is the `200`) and passed case 3; 15 (`< 1` → `<= 1`) failed case 3 only (a `Timeout` with one millisecond left); 16
     (`timeSource` dropped from `CallDeadline(...)`) failed all three (cases 1 and 2 reach the `200`; case 3's hop-1 limit reads the monotonic clock, 14 999 ms, not
     15 000).
  8. `:webtools-http:check`: `BUILD SUCCESSFUL` in 1 min 11 s, `:build-logic:fixturesPublicationCheck` included; `jvmTest` 145 classes / 732 tests, 0 failures
     (P10 recorded 140 / 711; the difference is the first review's part of D7 and D8, and this class). Then the five level tasks in one invocation (12 s): gating 7
     classes / 19 tests, component 56 / 398, deterministic 43 / 191, integration 24 / 93, nonfunctional 15 / 31; 0 failed, 0 skipped. As in P10, only the task the
     `--rerun` followed executed again; the `jvmTest` run before it had executed every test. `webtools-http-commoncheck` was not run: `commonMain` did not change.
     `check` did not run again after the second review's changes and D10's fix, which are test sources only (D7, D8, D10) and which the forced runs below
     execute.
- **The forced runs of record**, each level task with its own `--rerun` in one invocation (Gradle runs the five tasks concurrently):
  - the test-coverage review's: 731 passed, 1 failed. The failure is `UrlParseLinearTimeNonFunctionalTest`, landed with #62, which exceeded its bound while the
    five tasks ran concurrently and passes on its own; it is WebTools#87 ([`consequences.md`](consequences.md) §7);
  - after the second review's changes: 735 passed, 0 failed, 0 skipped: gating 7 classes / 19 tests, component 57 / 397, integration 24 / 93,
    deterministic 44 / 195, nonfunctional 15 / 31;
  - after D10's fix (`db82470`), the run of record: integration 24 classes / 94 tests and the other four levels as before, so 736 passed, 0 failed, 0 skipped (gating
    19, component 397, integration 94, deterministic 195, nonfunctional 31).

## 5. Level 5 pass

Plan §9 (Level 4b and Level 5) and P12:

1. **The dump against the tier table:** done with P5 (§3 item 3). The dump adds `public final class OkHttpTransport : HttpTransport` with `close`, `execute` and
   `toString`, and `OkHttpTransportKt.OkHttpTransport(String, OutboundUrlPolicy)`; the architecture's §9 lists `OkHttpTransport` among the final supplied defaults,
   Stable Core and usable without the opt-in; nothing `internal` is in the dump.
2. **Quick Documentation** of `OkHttpTransport`, the factory, `LoopbackTransports.untrusted` and `webtoolsUrl`: not performed in this run;
   [`consequences.md`](consequences.md) §8 has its owner and trigger.
3. **The optional real-endpoint fetch, and the read of its log:** not run, so real networks stay unexercised ([`consequences.md`](consequences.md) §8).

The rest of P12: the dated corrections of plan §8, ring 4, are made in [`../architecture.md`](../architecture.md) (§6.3.2, §6.6, §6.7, §7.3 and §13), and this
record is reconciled; the `README.md` and `CHANGELOG.md` delta of plan §8 lands with it.
