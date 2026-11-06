# Final implementation: `call-facades` (Issue #68)

The as-built record of `SpartanLaboratories/WebTools#68`, unit 9 (`call-facades`) of [#55's architecture](../architecture.md). It states what was built,
where the build departs from [`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for the repository, its published
artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in [`consequences.md`](consequences.md).

- **Status:** built through stage 5 of [`plan.md`](plan.md) §5, with the deviations of §2. Procedures P1 to P3 of plan §9.3 ran (§4); the Level 5 pass of P4
  is §5.
- **Branch and commits:** `feat/issue-68-call-facades`, cut from `master` at `5f221ce`, with the research commits `98f7eb7` and `3d9d9ff` and the commits
  `ac3b61d` (stage 1, with this folder's `plan.md` and `consequences.md`), `0985772` (stage 2), `ab7cbbd` (stage 3), `df7481a` (stage 4), `391b2d9` (the two
  Level 4c classes of the plan), `f413a47` (the `README.md` and `CHANGELOG.md` text of plan §8), `80b1c13` (the tests of D9, D10 and D11, no main source) and
  `806b489`, a merge of `master` at `8563e93` that brings only `docs/dashboards/`. `806b489` is the code-landing commit; the reconciled documents are committed
  after it.
- **Architecture:** [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-06) for this unit at §6.3.2, §6.6, §6.7, §7.2, §7.4, §12 and
  §13; [`consequences.md`](consequences.md) §5 states each refinement.
- **Host of the measurements:** Windows 11, JDK 23.0.2, Gradle 9.7.1, Kotlin 2.2.0, kotlinx-coroutines 1.10.2, 32 logical processors. Linux and macOS were not
  exercised.
- **Versions moved:** none. `webtools-http` 1.0.0 stays unreleased until #55's stack is complete, and this landing is not a release candidate.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.

- **The public surface** (package `com.spartanlabs.webtools.http`; all of it final, Stable Core and usable without any opt-in; no seam is added):
  - in `commonMain`: `Endpoint<T : Any>` over an `internal` eight-parameter constructor (`id`, the decoder, `name`, `ttl`, `missTtl`, `cost`, `lane`,
    `misses`), with the public read-only properties `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses`, the non-generic nested `Endpoint.Builder` and
    the top-level factory `Endpoint(id, decode, configure)`; `CallSpec` and `FetchSpec`; `UpstreamClientConfig` with `DEFAULT_MAX_WAIT` (5 seconds),
    `UpstreamClient` (`baseUrl`, `call`, `utilisation`, `cacheStats`, `flush`, `close`) and the common factory `UpstreamClient(transport, baseUrl, configure)`;
    `UrlFetcherConfig` with `DEFAULT_MAX_WAIT` (the client's), `UrlFetcher` (`fetch`, `close`) and the common factory
    `UrlFetcher(transport, policy, configure)`;
  - in `jvmMain`: the owning factories `UpstreamClient(baseUrl, userAgent, configure)`, `UrlFetcher(userAgent, configure)` and
    `UrlFetcher(userAgent, policy, configure)`;
  - in the published fixtures (`jvmTestFixtures`, package `com.spartanlabs.webtools.http.testing`, Stable Core with source compatibility only):
    `LoopbackFetchers` with `untrusted` and `trusted`.

  The five builders a consumer's lambda receives, `Endpoint.Builder`, `CallSpec`, `FetchSpec`, `UpstreamClientConfig` and `UrlFetcherConfig`, carry
  `@WebToolsDsl`. The ABI dump `webtools-http/api/webtools-http.api` grows from 892 to 1,031 lines, 139 added and none removed, by exactly the classes of plan
  §7.16; it records no `<init>` for any of the new classes (§4, P1 and P2).
- **The parts** (`internal`): in `commonMain`, `FacadeMessages.kt` (the two component names, the two logger names, the ten `RULE_*` rules of an
  `InvalidRequest` the facades create and the eleven `MSG_*` messages of a construction refusal), `RequestAssembly.kt` (the URL assembled as text and parsed
  again, the cache-key parameters, the call's checks), `ResponseVerdict.kt` (the two classifications and the `retryAfter` rule), `FacadeLifecycle.kt` (ownership,
  the closing order, the per-call trace the loader passes to the call) and `CallDispatch.kt` (the one place that takes and settles a permit); in `jvmMain`, `TransportSizing.kt`
  (`sizedFor`) and the build-and-clean-up helper of `UpstreamClientOwning.kt`.
- **Changed files:** `UpstreamFailure.kt` (the KDoc of `RateLimited` and of its `retryAfter`, no code line), `WebToolsDsl.kt` (the marker's KDoc list) and
  `WebToolsDslComponentTest` (its list, sixteen builders).
- **One remedy plan §7.10 names is built.** Inside the `trusted { }` lambda of `UpstreamClientConfig.policy`'s initializer the compiler resolves `baseUrl` to the
  configuration's own property, which the `@WebToolsDsl` marker keeps out of reach, so the initializer reads the host into a local before the lambda.
- **Tests:** 36 new test classes, 9 in `commonTest` and 27 in `jvmTest`, in the level packages, and four new test-support files (`FacadeTestSupport.kt`,
  `CloseLog.kt`, `ThrowingParts.kt`, `ConcurrencyGateDispatcher.kt`); §4 counts them by level.
- **Documentation:** the `webtools-http` row of the README's artifacts table, its new "HTTP client stack" section and its test-fixtures paragraph; two `### Added`
  entries under `## [Unreleased]` in `CHANGELOG.md` (D6, D7 and D13 for where the wording departs from plan §8).
- **No build file changes.** `git diff --name-only 8563e93 806b489` lists 59 files under `webtools-http/src/`, the dump, six files of this folder,
  `README.md` and `CHANGELOG.md`, and nothing else (§3, criterion 4).

## 2. Deviations

Each states what was built where it departs from [`plan.md`](plan.md), the plan section it departs from, and why. None changes a public signature, a sibling
contract or a refinement of [`consequences.md`](consequences.md) §5.

### D1. The fetcher's commit names no type of the client's

- **Departs from:** plan §7.8 (`UrlFetcherConfig.Companion.DEFAULT_MAX_WAIT`), §7.15 (`FacadeTestSupport.kt`), §9 (`UrlFetcherConfigComponentTest`, its test
  `DEFAULT_MAX_WAIT equals the client's`) and §11 (commit 2), which put in the fetcher's commit three things that name `UpstreamClientConfig` or
  `UpstreamClient`, types §5 and §11 build in the next commit.
- **As built:** the fetcher's commit (`0985772`) initialises `UrlFetcherConfig.DEFAULT_MAX_WAIT` with the literal `5.seconds`, asserts only that value in the
  test, and leaves `clientOn` and `assertNothingSpent` out of `FacadeTestSupport.kt`; the client's commit (`ab7cbbd`) puts the plan's text in place. At the
  code-landing commit the constant reads `UpstreamClientConfig.DEFAULT_MAX_WAIT`, the test asserts that the two constants are equal and five seconds, and both
  helpers are in the support file.
- **Why:** each commit must compile and pass on its own (plan §5, §11), and a source cannot name a class that the same commit does not contain.

### D2. Two test names without a dot

- **Departs from:** plan §9, `RequestAssemblyDeterministicTest`'s `segments and pairs encode like HttpUrl.Builder` and `UpstreamClientThrottleComponentTest`'s
  `under CallBudget.Unlimited a 429 without Retry-After reports zero`.
- **As built:** `segments and pairs encode like HttpUrl Builder` and `under CallBudget Unlimited a 429 without Retry-After reports zero`; each asserts what the
  plan gives.
- **Why:** a function name on the JVM cannot contain a dot, backticked or not.

### D3. The internal KDoc states its rule instead of citing a plan section

- **Departs from:** the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10
  (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites "(§6.3)", "(R12)", "(R13)", "(R14)", "(§6.5)", "R7" and "§6.10", and the line
  comments of §7.11 that read "R8 steps 2 to 6", "R8 step 7" and "R8 step 8".
- **As built:** each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the
  construction checks in their order, what each private step of a call does). No executable line differs from the plan's.
- **Why:** a section or requirement number of the plan means nothing to a reader of the source.

### D4. Three tests of `UpstreamClientRequestComponentTest` assert a request that is sent

- **Departs from:** plan §9, `UpstreamClientRequestComponentTest`, whose heading has every test end with `assertNothingSpent` and assert an `InvalidRequest`,
  while three of its bullets describe a call that succeeds: `an absolute URL as a segment stays one encoded segment on the base host`, `a double-slash host form
  as a segment stays one encoded segment` and `a segment with a slash, a question mark and a percent is encoded`.
- **As built:** those three tests assert `Found` and the one request the fake receives (its full URL, its host, its encoded path); the other tests of the class
  end with `assertNothingSpent` and assert their rule.
- **Why:** the heading and the three bullets cannot both hold. The bullets state the behaviour of plan §6.3 (vector T09 of
  [`../64-url-policy/research/bypass-vectors.md`](../64-url-policy/research/bypass-vectors.md): the segment is encoded and sent to the base host).

### D5. Two tests for the fetcher's log lines

- **Departs from:** plan §9, which lists no test for two rows of plan §6.8 (requirement R17): the fetcher's `DEBUG` line `invalid request: rule={}` and its
  `WARN` line `throttled: subject={}, status={}, retryAfter={}`.
- **As built:** `UrlFetcherRequestComponentTest` has `an InvalidRequest is logged once at DEBUG with its rule and no value` (the lines
  `invalid request: rule=cost must be at least 1` and `invalid request: rule=header User-Agent is controlled by the transport`, nothing else from the fetcher but
  its creation and close, and neither the header's value nor the URL's query in any line); `UrlFetcherResponseComponentTest` has `a throttle is logged once at
  WARN with the redacted URL, the status and retryAfter` (the one line `throttled: subject=https://files.example.test/a?..., status=429, retryAfter=30s`, and no
  header value, query or body in any line of the fetcher).
- **Why:** R17 requires both lines, and plan §9 gives every requirement a test.

### D6. The README names the client's `rateSignals` reader

- **Departs from:** plan §8(c), the "Failures" bullet of the README's "HTTP client stack" section: "what the budget's signal reader reports as a throttle".
- **As built:** "what the client's `rateSignals` reader reports as a throttle".
- **Why:** the reader is `UpstreamClientConfig.rateSignals`, a setting of the client; a budget has no reader and receives only the signals the client read.

### D7. The CHANGELOG names the policy each owning `UrlFetcher` factory uses

- **Departs from:** plan §8, the first `CHANGELOG.md` entry: "`UrlFetcher(userAgent)` and `UrlFetcher(userAgent, policy)` own an `Untrusted` transport".
- **As built:** "built with `UrlFetcher(transport, policy) { … }`, or on the JVM with `UrlFetcher(userAgent)`, which owns a transport under the `Untrusted`
  defaults, and `UrlFetcher(userAgent, policy)`, one under the given policy".
- **Why:** the second overload takes any `OutboundUrlPolicy` (plan §7.12), a `Trusted` one included.

### D8. The decode-failure test asserts one `decode failed` line per load

- **Departs from:** plan §9, `UpstreamClientClassificationComponentTest`'s `a decode failure is wrapped in DecodeFailure and never stored`, which ends "one DEBUG
  line with the cause's class name".
- **As built:** the test asserts two `decode failed: endpoint=hero, cause=IllegalStateException` lines.
- **Why:** the same test calls twice to show that the failure is not stored; each load decodes, fails and logs its line.

### D9. Tests and assertions beyond plan §9's list

- **Departs from:** plan §9, which lists each class's tests and what each asserts, and §9.1's totals.
- **As built** (the class's test count, with the plan's where it differs):
  - `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport
    cannot apply them.
  - `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting;
    `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes `budget(listOf(RateWindow(…)))`; `UrlFetcher("agent") { … }`
    is fetched with `fetch(request) { … }` and prints `Untrusted`; a fetcher from `UrlFetcher(fake, OutboundUrlPolicy.untrusted())` fetches over the fake.
  - `UpstreamClientLoggingComponentTest` (11; plan 8): `a declared miss logs outcome Missing with its status`; `a call whose transport fails logs dashes for the
    wait, the status and the size` (`loaded=true`, `waitedMs=-`, `status=-`, `bytes=-`, `outcome=Timeout`); `a client over an anonymous budget logs its kind as
    custom`.
  - `UpstreamClientPolicyRefusalComponentTest` (4): the refusal-log test also asserts that a refused call logs no `call:` line.
  - `UpstreamClientRequestComponentTest` (16; plan 15): `the checks before anything is spent run in a fixed order`, a table of calls with two faults each,
    asserting the rule of the earlier check and that nothing was spent: `maxWait` before `maxAge`, the limits before the URL, the URL before a controlled
    header, a controlled header before the request build, an invalid request before the policy's refusal.
  - `UrlFetcherRequestComponentTest` (9; plan 7, one of the two more being D5's): the same table for the fetcher: the limits before a controlled header, an
    invalid request before the policy's refusal.
  - `UrlFetcherLifecycleComponentTest` (9; plan 7): `a negative defaultMaxWait through the owning factory builds no transport` (both JVM owning factories; no
    `created` line of any component); `a failed fetch logs dashes for the wait, the status and the size`.
  - `LoopbackFetchersComponentTest` (6; plan 3): `the trusted fetcher with pinned hosts refuses another host with HostNotPinned` (cleartext is forced on and
    the pin stays live); `the trusted fetcher keeps a policy that already allows cleartext` (the transport's `created` line shows the policy's own settings);
    `a negative defaultMaxWait is refused by each factory before any transport is built`.
  - `UrlFetcherLoopbackIntegrationTest` (8; plan 7): `the public owning factory under a Trusted cleartext policy fetches from the server and closes its
    transport` (the status, the body, the `User-Agent` at the server, the `closed` lines in the order transport, fetcher); the denied-range test supplies a
    budget and asserts that no unit was used.
  - `UpstreamClientLoopbackIntegrationTest` (6): the first test sends a query pair and asserts the query the server received.
  - `EndpointDeterministicTest` (6), `RequestAssemblyDeterministicTest` (9; plan 8) and `UpstreamClientConfigComponentTest` (11): the text of each of the 21
    `RULE_*` and `MSG_*` constants of `FacadeMessages.kt` is written out once as a literal: the six endpoint messages in the endpoint validation test, the ten
    rules in `every rule of an invalid request has its constant text`, the five construction messages in the configuration tests that assert them.
- **Why:** each addition holds something the requirements state and the listed tests leave open: the rows of plan §6.8 (R17), the order of the checks of R8
  and R13, which a single-fault test cannot show (the order asserted is the order of R8 and R13), the trusted path of `LoopbackFetchers` and its refusal that
  builds nothing (R19, R7), the public owning `UrlFetcher` factory over a real exchange (R6), and the texts of the constants, which a test comparing a message
  with its own constant cannot hold when a constant is reworded or two are swapped.

### D10. A close-race class for `UrlFetcher`

- **Departs from:** plan §9, Level 4c, which lists two classes, both for `UpstreamClient`.
- **As built:** `UrlFetcherCloseRaceNonFunctionalTest` (2 tests), shaped like `UpstreamClientCloseRaceNonFunctionalTest`. `close racing in-flight fetches ends
  every fetch with a value and hangs nothing`: over 50 rounds, 16 fetches of a slow fake start together while `close()` runs from another thread at a seeded
  random point; each returns the response or a `Closed` of the fetcher or the transport, never a thrown cancellation; a fetch after the close reaches nothing; the
  round's thread pool terminates. `close from many threads at once closes the transport once and never throws`: over 50 rounds, 32 threads call `close()` while
  others fetch and read `toString()`; the owned transport is closed exactly once and `closed` is logged exactly once per round.
- **Why:** R15 and plan §6.9 give both facades the same lifecycle and thread-safety contract, and the fetcher has its own closed flag and closing path, which
  the client's class does not exercise.

### D11. A secrecy class for the call facades

- **Departs from:** plan §9, Level 4c, which lists no secrecy suite; R17's "never a header value, a body, a query string, a credential, a cache-key value or a
  decoded value" is asserted at Level 2 on chosen lines only.
- **As built:** `CallFacadesSecrecyNonFunctionalTest` (2 tests), in the form of the module's other secrecy suites (`TransportSecrecyNonFunctionalTest`,
  `CaffeineResponseCacheSecrecyNonFunctionalTest`, `PolicySecrecyNonFunctionalTest`). Five sentinel strings (a header value, a query value, a response body, a
  credential, a decoded value) go through `UpstreamClient` (a found value, a cache hit, a declared miss, a decode failure, an upstream error, three kinds of
  invalid request, a policy refusal, a throttle, an exhausted budget, a close) and through `UrlFetcher` (two responses, three kinds of invalid request, two policy
  refusals, a throttle, an exhausted budget, a close). No sentinel appears in any log event of the module at `DEBUG` (formatted message, arguments,
  throwable), in any failure's message or `toString()`, or in the `toString()` of the client, the fetcher, the endpoint or a result. A failure's `cause` is not
  inspected: it is the consumer's own exception.
- **Why:** R17 is a statement about every line and every text the facades produce; only a suite that drives each outcome and scans everything holds it, as the
  transport, the cache and the policies each have.

### D12. A call can return an `IllegalStateException`

- **Departs from:** plan R18, "Every failure of a call is a `Result.failure` holding an `HttpFailure`".
- **As built:** `UpstreamClient.call` also returns a `Result.failure` holding an `IllegalStateException`, with a constant message and the escaped exception as its
  `cause`, when a `CancellationException` that is not the caller's escapes the load: one thrown inside a part the consumer supplies (the decoder, the reader, a
  transport or a budget), such as a `withTimeout` expiring there. The call's loader runs under the cache's loader rule (`LoaderRunner.kt`), which rethrows a
  cancellation only when the coroutine running the load is itself cancelled and otherwise returns this failure; a non-`GET` runs through the same rule on the
  caller's coroutine, so both paths return the same failure. The cache's own Level 2 tests (`LoaderRunnerComponentTest`,
  `CaffeineResponseCacheLoaderCancellationComponentTest`, `DisabledResponseCacheComponentTest`) pin the rule; no test of this unit drives it through `call`.
- **Why:** the rule is the response cache's as built ([`../66-response-cache/consequences.md`](../66-response-cache/consequences.md) §4, with its follow-up
  `cancelled-loader-failure-leaf` in §7; [`../architecture.md`](../architecture.md) §7.4, the row "facade → `ResponseCache`"), and plan §6.9's table already lists
  this `IllegalStateException` among `call`'s failures, so R18 and §6.9 disagree and the build follows §6.9. The facade passes the cache's result through as it
  does every other failure. `UrlFetcher.fetch` runs no loader rule; what such a cancellation does there is a known gap ([`consequences.md`](consequences.md) §8).

### D13. The README's Testing sample sets no `dispatcher`

- **Departs from:** plan §8(c), the sample of the README's "Testing" subsection: "`UpstreamClient(fake, baseUrl) { timeSource = clock.timeSource; wallClock =
  clock.wallClock; dispatcher = clock.dispatcher }`".
- **As built:** "`UpstreamClient(fake, baseUrl) { timeSource = clock.timeSource; wallClock = clock.wallClock }`".
- **Why:** the common factory owns no cache, so `dispatcher` reaches nothing there (`UpstreamClientConfig.dispatcher`), and a sample that sets it suggests it
  does. `UpstreamClientCallComponentTest` builds its clients in this form.

### D14. The client's refusal line prints the request path

- **Departs from:** plan R17, and the sentence under plan §6.8's table: "Never a header value, a body, a query string, a credential, a cache-key value or a
  decoded value".
- **As built:** the client's `WARN` line for a pre-check refusal prints `url=` with `Url.redacted()`, so the request path, a parameter of the cache key, appears
  in it; the query is never printed. No other per-call line of the client prints a URL.
- **Why:** plan §6.8's table gives that line's exact text, `refused by policy: endpoint={}, reason={}, rangeClass={}, url={}` with `Url.redacted()`, as
  [`../architecture.md`](../architecture.md) §6.6 does for every policy refusal, so the table and the sentence under it disagree, and the build follows the
  table. The path is what locates a refused request, as in the transport's own refusal line, and the query, the part of a key that can carry a credential,
  stays out. The logging contract of `UpstreamClient`'s class KDoc states the path as shown.

## 3. Acceptance

The four criteria of plan §1 ("Acceptance"):

1. **The issue's scope is met.**
   - The five types and their configs, the common and owning factories (§1).
   - The `maxBurst` sizing: `UpstreamClientBurstSizingIntegrationTest` puts eight calls in flight at once where the per-host default is five, and P3 shows the
     test fails without the per-host raise.
   - The closing order cache, budget, transport: `UpstreamClientLifecycleComponentTest` and `UpstreamClientOwningFactoryIntegrationTest`.
   - Level 2 on the fake transport:
     - a refusal by the facade's own checks (the policy's pre-check, and every `InvalidRequest` the facade raises) costs nothing, with zero calls, no permit and
       nothing cached: `UpstreamClientPolicyRefusalComponentTest`, `UpstreamClientRequestComponentTest`, `UrlFetcherPolicyRefusalComponentTest`,
       `UrlFetcherRequestComponentTest`. A refusal the transport makes once the permit is granted leaves the permit spent, by the permit rule of plan §6.5;
     - a `429` with `Retry-After` pauses the budget and the next call fails fast: `UpstreamClientThrottleComponentTest`;
     - a decode failure is never stored: `UpstreamClientClassificationComponentTest`;
     - a miss status is stored: `UpstreamClientClassificationComponentTest`;
     - a non-`GET` bypasses the cache: `UpstreamClientCachingComponentTest`.
   - Level 3 for a real call over loopback: the four integration classes of plan §9.
   - The README how-to: `README.md`'s "HTTP client stack" section, whose snippets ran (§5).

   Met.
2. **The two open points are settled as built.**
   - The `Endpoint<T>` resolution: P1 (the dump records the factory and its `$default` and no `<init>`) and `EndpointComponentTest` (every consumer form binds the
     factory; the eight-argument call binds the constructor).
   - `RateLimited.retryAfter` is the longer of the server's `Retry-After` and the budget's `pausedFor`: `UpstreamClientThrottleComponentTest`'s edge cases and the
     table of `ResponseVerdictDeterministicTest`.

   Met.
3. **The tasks pass.** The five level tasks, `:webtools-http:apiCheck`, `:webtools-http:verifyJavaFloor`, `:webtools-http-commoncheck:build` and
   `:webtools-http:check` (with the fixtures-publication check) pass (§4). Met.
4. **The diff touches only `webtools-http`, `docs/` and the two root documents.** `git diff --name-only 8563e93 806b489` (`8563e93` is the merge base) lists 59
   files under `webtools-http/src/`, `webtools-http/api/webtools-http.api`, six files under `docs/plans/55-http-client-stack/68-call-facades/`, `README.md` and
   `CHANGELOG.md`, and nothing else. Met.

## 4. Measurements

### The final run

On the sources of the code-landing commit `806b489` (identical to `80b1c13`'s: the merge brings only `docs/dashboards/`). Counts are from the XML reports under
`webtools-http/build/test-results/`.

| Command | Result |
|---|---|
| `./gradlew :webtools-http:gatingTest --rerun` | BUILD SUCCESSFUL; 40 tests in 14 classes, 0 failed, 0 skipped |
| `./gradlew :webtools-http:componentTest --rerun` | BUILD SUCCESSFUL; 920 tests in 133 classes, 0 failed, 0 skipped |
| `./gradlew :webtools-http:integrationTest --rerun` | BUILD SUCCESSFUL; 124 tests in 30 classes, 0 failed, 0 skipped |
| `./gradlew :webtools-http:deterministicTest --rerun` | BUILD SUCCESSFUL; 317 tests in 60 classes, 0 failed, 0 skipped |
| `./gradlew :webtools-http:nonfunctionalTest --rerun` | BUILD SUCCESSFUL; 46 tests in 26 classes, 0 failed, 0 skipped |
| `./gradlew :webtools-http:check` | BUILD SUCCESSFUL: `jvmTest` (1,447 tests in 263 classes, 0 failed, 0 skipped), `apiCheck` against the committed dump, `verifyJavaFloor` and `:build-logic:fixturesPublicationCheck` (the last two up to date: no input of theirs changed after `df7481a`, where the check ran) |
| `./gradlew :webtools-http-commoncheck:build` | BUILD SUCCESSFUL |

`check` runs every level as the one task `jvmTest`; the five level tasks split the same tests, and their sum is `jvmTest`'s total: 14 + 133 + 30 + 60 + 26 =
263 classes and 40 + 920 + 124 + 317 + 46 = 1,447 tests.

Plan §9.1 expects `jvmTest` to go from 1,215 tests in 227 classes to 1,429 in 261. As built it is 1,447 in 263: the plan's 214 tests in its 34 classes; 14 more
tests in eight of those classes (the two of D5 and the twelve of D9); and the two classes of D10 and D11 with two tests each. The new classes by level, each
the difference from the run of record of [`../67-call-budget/final-implementation.md`](../67-call-budget/final-implementation.md) §4 on the same baseline code:
gating 3 (8 tests), component 21 (172), integration 4 (20), deterministic 4 (24), non-functional 4 (8); 36 classes and 232 tests in all.

The classes whose count differs from plan §9 (every other class runs the count the plan gives):

| Class | Level | Executed | Failed | Skipped | Plan §9 | Deviation |
|---|---|---|---|---|---|---|
| `UrlFetcherRequestComponentTest` | 2 component | 9 | 0 | 0 | 7 | D5, D9 |
| `UrlFetcherResponseComponentTest` | 2 component | 9 | 0 | 0 | 8 | D5 |
| `UrlFetcherLifecycleComponentTest` | 2 component | 9 | 0 | 0 | 7 | D9 |
| `UpstreamClientLoggingComponentTest` | 2 component | 11 | 0 | 0 | 8 | D9 |
| `UpstreamClientRequestComponentTest` | 2 component | 16 | 0 | 0 | 15 | D9 |
| `LoopbackFetchersComponentTest` | 2 component | 6 | 0 | 0 | 3 | D9 |
| `UrlFetcherLoopbackIntegrationTest` | 3 integration | 8 | 0 | 0 | 7 | D9 |
| `RequestAssemblyDeterministicTest` | 4a deterministic | 9 | 0 | 0 | 8 | D9 |
| `UrlFetcherCloseRaceNonFunctionalTest` | 4c non-functional | 2 | 0 | 0 | not listed | D10 |
| `CallFacadesSecrecyNonFunctionalTest` | 4c non-functional | 2 | 0 | 0 | not listed | D11 |

### P1. The `Endpoint` resolution and ABI measurement

`grep -n "Endpoint" webtools-http/api/webtools-http.api` after `apiDump` shows `EndpointKt` with `Endpoint` and `Endpoint$default`, and the class `Endpoint` with
its getters and `toString` and no `<init>`:

```
public final class com/spartanlabs/webtools/http/Endpoint {
	public final fun getCost ()I
	public final fun getId ()Ljava/lang/String;
	public final fun getLane ()Lcom/spartanlabs/webtools/http/Lane;
	public final fun getMissTtl-UwyO8pc ()J
	public final fun getMisses ()Ljava/util/Set;
	public final fun getName ()Ljava/lang/String;
	public final fun getTtl-UwyO8pc ()J
	public fun toString ()Ljava/lang/String;
}
public final class com/spartanlabs/webtools/http/EndpointKt {
	public static final fun Endpoint (Ljava/lang/String;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)Lcom/spartanlabs/webtools/http/Endpoint;
	public static synthetic fun Endpoint$default (Ljava/lang/String;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;ILjava/lang/Object;)Lcom/spartanlabs/webtools/http/Endpoint;
}
```

`Endpoint$Builder` is present without an `<init>`, and no `<init>` appears for any other new class. `EndpointComponentTest` passes, 8 of 8: in the module's own
test compilation, which sees the `internal` constructor, every consumer form binds the factory (`Endpoint("id", decode)`,
`Endpoint("id", ::decodeString) { name = "x" }`, an untyped lambda, `Endpoint<String>("id", decode)`, an expected type), each typed `Endpoint<String>`, and the
eight-argument call binds the constructor. Both measurements of plan §3, Assumption 9, hold in this module
([`research/endpoint-factory-resolution.md`](research/endpoint-factory-resolution.md) F1 and F3).

### P2. The `js` compile and the dump

`:webtools-http-commoncheck:build` passes after each of the four code commits (`Channel`, `Job()` and `KClass.simpleName` compile for `js`), and `apiDump`
followed, in a separate invocation, by `apiCheck` and `verifyJavaFloor` passes after each. The dump grows by exactly plan §7.16, with no line removed:

| Commit | Lines added | Classes |
|---|---|---|
| `ac3b61d` | 55 | `CallSpec`, `Endpoint`, `Endpoint$Builder`, `EndpointKt`, `FetchSpec` |
| `0985772` | 30 | `UrlFetcher`, `UrlFetcherConfig`, `UrlFetcherConfig$Companion`, `UrlFetcherKt` |
| `ab7cbbd` | 42 | `UpstreamClient`, `UpstreamClientConfig`, `UpstreamClientConfig$Companion`, `UpstreamClientKt` |
| `df7481a` | 12 | `UpstreamClientOwningKt`, `UrlFetcherOwningKt` |
| **Total** | **139** | the dump goes from 892 to 1,031 lines |

`RateLimited`'s KDoc moves nothing in the dump; `sizedFor` and the build-and-clean-up helper are `internal`, and `LoopbackFetchers` is in the fixtures, which the
dump does not cover. The fixtures-publication check (`:build-logic:fixturesPublicationCheck`) passes at `df7481a`, where the fixtures gain `LoopbackFetchers` and
no dependency; its expectations are unchanged.

### P3. The sizing mutation

With the line `maxConcurrentRequestsPerHost = maxOf(maxConcurrentRequestsPerHost, burst)` removed from `sizedFor`, so that only the total limit is raised,
`./gradlew :webtools-http:integrationTest --tests "*UpstreamClientBurstSizingIntegrationTest" --rerun` ends BUILD FAILED, 2 tests, 1 failed: `a budget burst
above the per-host limit puts that many calls in flight at once` fails at its `maxHeld` assertion with `expected: <8> but was: <5>` after 5.49 s (the test server's
dispatcher held five requests until its timeout; every call still returned `Found`), and the control passes. With the line in place the class passes, 2 of 2: the sized test in
0.07 s with `maxHeld == 8`, the control in 5.02 s with `maxHeld == 5`. The source with the line restored is byte-identical to the source before the mutation
(the same SHA-256 and an empty `git diff --no-index`). Plan §3's Assumption 7 holds: the test server serves each connection on its own thread.

## 5. Level 5 pass

**The KDoc, read as a consumer sees it** (the KDoc of every new public declaration as it lands, and the dump):

- **Tiers.** "**Stable Core.**" on `Endpoint`, `Endpoint.Builder`, the `Endpoint` factory, `CallSpec`, `FetchSpec`, `UpstreamClientConfig`, `UpstreamClient`,
  both `UpstreamClient` factories, `UrlFetcherConfig`, `UrlFetcher` and its three factories; "**Stable Core, source compatibility only.**" on
  `LoopbackFetchers`. Using none of them needs an opt-in, and the dump shows no `<init>` for any of them.
- **Order and what a call spends.** The class KDoc of `UpstreamClient` and of `UrlFetcher` gives the fixed order (for the client: the policy's pre-check, the
  cache, the budget inside the cache's loader, the transport), that a closed facade, an `InvalidRequest` the facade raises and the pre-check's refusal spend
  nothing, and the permit rule of plan §6.5 once a permit is granted. The client's adds that a hit and a caller joining a load spend nothing, and the closing
  order with what is and is not owned; the fetcher's, that nothing is retried.
- **What is never printed.** Each facade's class KDoc gives its logger, its lines and their levels, and that no line carries a header value, a body or a query
  string (the client's adds a decoded value), so no credential sent in a header or a query pair. A URL is printed through `Url.redacted()`, path shown and query
  not, and the client prints the endpoint id as given; both KDocs say that a secret carried in a path is therefore printed. `Endpoint`'s
  `toString()` never prints the decoder, a refused setting's message names the property and never a value, the factories' messages are constant, and
  credentials added with `addSensitive` are never printed.
- **`retryAfter`.** `UpstreamFailure.RateLimited`'s `@param retryAfter` gives the longer of the server's `Retry-After` and the time the budget stays paused,
  measured when the failure was created, and zero when neither named a wait; `UrlFetcher`'s class KDoc, the README and the CHANGELOG say the same.

**The README how-to's snippets, run.** A scratch `jvmTest` class held the snippets of the "HTTP client stack" section and ran with
`./gradlew :webtools-http:integrationTest --tests "*ReadmeHowToScratchIntegrationTest"`: BUILD SUCCESSFUL, 7 tests, 0 failed. The class is not in the repository.

| Snippet | How it ran | Result |
|---|---|---|
| Calling an upstream, as printed | built against its own `https://api.example.com/v2/` URL; nothing sent | compiles; the client owns `[cache, budget, transport]` under a `Trusted` policy; the `User-Agent` value `my-app/1.0 (+https://example.com/contact)` is accepted |
| Calling an upstream | the same code against a loopback `MockWebServer`; only the base URL and a cleartext `policy` differ | a `200` reaches `show(Player("Ada"))`, a declared `404` reaches `showNotFound()`, a `429` with `Retry-After: 30` reaches `backOff(30s)`, the next call reaches `backOff(…)` through `BudgetExhausted`; the first key again is served from the cache; `flush()` succeeds; three requests reach the server, with the default `Authorization` header and the `User-Agent` value |
| "Your own cache or budget" and the common factory | `cache(CaffeineResponseCache { maxWeight = 5_000 })`, `budget(myBudget)`, `UpstreamClient(transport, baseUrl) { … }` over `LoopbackTransports.trusted(…)` | compiles and decodes; the cache is still usable after the client's `close()` |
| Fetching, as printed | `UrlFetcher("my-app/1.0 (+https://example.com/contact)").use { … }` with a loopback URL | the `Untrusted` policy refuses loopback, as the section says: `report(RefusedByPolicy)`, no request at the server |
| Fetching | the same code over `LoopbackFetchers.untrusted("test")` | a `200` and a `304` both reach `handle(response)` |
| A policy of your own and a shared budget | `UrlFetcher(userAgent, OutboundUrlPolicy.untrusted { extraDeniedRanges += … }) { budget = … }` | compiles; the added range is refused |
| Testing, as printed | under `runTest` with `virtualClock()` and `FakeHttpTransport`; `UpstreamClientCallComponentTest` builds its clients in the same form | passes: `Found(Player("Ada"))`, one transport call |

Every snippet ran as printed. What plan §9.2 leaves to a person (the IDE, the cross-module diagnostic, a real upstream, Linux and macOS) is in
[`consequences.md`](consequences.md) §8.
