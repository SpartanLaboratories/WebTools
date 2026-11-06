# Issue #68 — `call-facades` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#68`, *"webtools-http: `UpstreamClient` and `UrlFetcher` composing cache, budget and transport"*. It is unit 9,
  `call-facades`, of [#55's architecture](../architecture.md) (§6.6, §7.2, §7.4, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this unit, as built, and
  what it leaves behind. The file-level detail is in [`plan.md`](plan.md); the as-built record, with every measurement and deviation, is
  [`final-implementation.md`](final-implementation.md); the evidence is in [`research/README.md`](research/README.md) with its three findings files.
- **Status:** built at the code-landing commit `806b489`, with the deviations of [`final-implementation.md`](final-implementation.md) §2. None changes a public
  signature, a sibling contract or a refinement of §5. [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-06) for this unit at §6.3.2,
  §6.6, §6.7, §7.2, §7.4, §12 and §13.
- **Baseline:** `master` `5f221ce`; branch `feat/issue-68-call-facades`, with the research commits `98f7eb7` and `3d9d9ff`, the commits `ac3b61d`, `0985772`,
  `ab7cbbd`, `df7481a`, `391b2d9`, `f413a47` and `80b1c13`, then `806b489`, a merge of `master` at `8563e93` that brings only `docs/dashboards/`: the
  code-landing commit.
- **Related docs:** [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md); [`research/README.md`](research/README.md); the systems-level
  [`../consequences.md`](../consequences.md); the as-built records of the units this one composes, [`../65-okhttp-transport/`](../65-okhttp-transport/final-implementation.md),
  [`../66-response-cache/`](../66-response-cache/final-implementation.md) and [`../67-call-budget/`](../67-call-budget/final-implementation.md), and of the units under
  them, [`../62-http-core/`](../62-http-core/final-implementation.md), [`../63-test-support/`](../63-test-support/final-implementation.md) and
  [`../64-url-policy/`](../64-url-policy/final-implementation.md).
- **The system:** the call facades, `webtools-http-facades` in the state boards' registry; built with this landing.

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the call facades. In common code: `Endpoint` (with its builder and factory), `CallSpec`, `FetchSpec`, `UpstreamClientConfig`, `UpstreamClient` and its factory over a transport, `UrlFetcherConfig`, `UrlFetcher` and its factory over a transport. On the JVM: the owning factories `UpstreamClient(baseUrl, userAgent) { }`, `UrlFetcher(userAgent) { }` and `UrlFetcher(userAgent, policy) { }`. All of them are Stable Core and usable without the opt-in; no seam is added. `UpstreamFailure.RateLimited` gets its producer and a reworded KDoc for `retryAfter`; its shape does not change. The ABI dump grows by exactly the classes of [`plan.md`](plan.md) §7.16, from 892 to 1,031 lines with none removed ([`final-implementation.md`](final-implementation.md) §4). The test-fixtures variant gains `LoopbackFetchers` (Stable Core, source compatibility only) and no dependency. No dependency of either main variant changes. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **One call for an upstream's endpoint.** `UpstreamClient.call(endpoint) { path(…); query(…) }` builds the request from a base URL, checks it against the
  client's outbound-URL policy, serves a `GET` from the response cache, takes a budget permit only when it loads, sends one request, reads the rate signals,
  and returns `Result<Lookup<T>>`: the decoded value, a confirmed miss, or a typed failure.
- **N callers, one load, one permit.** Concurrent `GET`s of one key share one load and one permit; a hit costs nothing.
- **Throttles reported once, never retried.** A `429` or a reader-reported throttle pauses a windowed budget and returns `RateLimited` with how long to wait
  before calling again: the longer of the server's `Retry-After` and the budget's pause in force.
- **A front door for administrator-supplied URLs.** `UrlFetcher.fetch(request)` refuses a URL its policy refuses before it spends anything, and returns every
  status but a throttle as a response, a `304` included, for a caller's conditional GET.
- **Batteries on the JVM.** The client's owning factory builds and owns an `OkHttpTransport`, a `CaffeineResponseCache` unless one is passed in, and a
  `WindowedCallBudget` when windows are configured; it sizes its transport so a granted permit never waits in OkHttp's queue. The fetcher's owning factories
  build and own the transport alone. Each facade closes what it built, in the order cache, budget, transport; a component a consumer passes in is never closed.
- **Failures as values.** Every expected failure is in the `Result`: `InvalidRequest` for a call that breaks a rule, before anything is spent;
  `RefusedByPolicy`; `BudgetExhausted`; the transport's failures; `RateLimited`, `UpstreamError` and `DecodeFailure`; `Closed`.
- **Virtual time under test:** the clocks and the dispatcher are configuration properties, so the published `VirtualClock` drives the cache, the budget and
  the fake transport together; `LoopbackFetchers` runs `UrlFetcher` over a loopback `mockwebserver3` server with every `Untrusted` rule but the loopback ones live.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`final-implementation.md`](final-implementation.md) §1 lists them): twelve in `commonMain` (the facades and their
  internal request assembly, permit dispatch, classification and lifecycle; compiled for `js` by the check module like every common file), three in `jvmMain`
  (the owning factories and the transport sizing), `LoopbackFetchers` in the fixtures; 36 test classes in `commonTest` and `jvmTest`, in the level packages, and
  four test-support files.
- **Changed files:** `UpstreamFailure.kt` (the KDoc of `RateLimited`); `WebToolsDsl.kt` (the marker's KDoc list) and `WebToolsDslComponentTest` (its list), each
  gaining five builders; `webtools-http/api/webtools-http.api`.
- **The one composition of the three parts.** The facades are the only code in the main variants that calls `ResponseCache.getOrLoad`,
  `CallBudget.withPermit` or `HttpTransport.execute`; in the fixtures, the recorder delegates `execute`.

### Build

- **No build file changes.**
- **`./gradlew build` does more work:** 36 more test classes of `webtools-http` with 232 tests, among them Level 3 classes with loopback servers and Level 4c
  classes on real threads; the fixtures-publication check runs again when `commonMain`, `jvmMain` or `jvmTestFixtures` change; `apiCheck` against the
  regenerated dump; the check module's `js` compile of the new common files.

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the facades; the new "HTTP client stack" section; the test-fixtures paragraph names
  `LoopbackFetchers`. The `### webtools-http` Components table and the final README pass stay #70's.
- **`CHANGELOG.md`:** two `### Added` entries under `## [Unreleased]`.
- **[`../architecture.md`](../architecture.md)** carries dated corrections (2026-11-06) at §6.3.2, §6.6, §6.7, §7.2, §7.4, §12 and §13 for the points of §5.
- **[`../consequences.md`](../consequences.md)**, the systems-level document, settles this unit's row of §7 and gains one line for this unit, linking to §4.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class calls an upstream through the stack**, so nothing is moved onto the facades. The `Connector` follow-ups of
  [`../architecture.md`](../architecture.md) §8 (`connector-get-skrape-on-webtools-http`, `connector-download-fetch-half`) can build on `UrlFetcher` once
  `webtools-http` 1.0.0 is released.
- **`RateLimited`** gets its producer; nothing else in the module creates it.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts, and the build holds them as written; the points below are as built.

- **Unit 10, `feeds` (#69):**
  - the factories: `UrlFetcher(transport, policy) { }` in common code, for fake-based tests, the transport staying the caller's; on the JVM,
    `UrlFetcher(userAgent) { }` (the `Untrusted` defaults) and `UrlFetcher(userAgent, policy) { }`, each owning the `OkHttpTransport` it builds with the policy's
    settings as they are, the second for `FeedFetcher.untrusted` to pass `OutboundUrlPolicy.untrusted { }`; the configuration `UrlFetcherConfig { budget;
    rateSignals; wallClock; timeSource; defaultMaxWait }`, whose budget (`CallBudget.Unlimited` by default) the fetcher never closes;
  - `fetch(request) { cost; lane; maxWait }: Result<HttpResponse>`, in this order: `Closed("fetcher")` after `close()`; `InvalidRequest` for an exception in
    the lambda, a `cost` below 1, a negative `maxWait` or a header the transport controls (`If-None-Match` and `If-Modified-Since` are not among them); the
    policy's pre-check, a refusal being `RefusedByPolicy` with no permit and no call; then the budget and the transport. A throttle (a `429`, or what the reader
    reports as throttled) is `RateLimited`; every other status, a `304` and a returned `3xx` included, is the transport's `HttpResponse`, unchanged, its
    `redirectChain` with each followed hop's status (`301` or `308` for a permanent move) included;
  - the pre-check applies the fetcher's policy to every URL, a fake transport's included: a test over `FakeHttpTransport` under an `Untrusted` policy uses a
    public-looking host, never `localhost` or an IP literal;
  - its Level 3 runs over `LoopbackFetchers.untrusted(userAgent, policy, resolve, configure)` (an `Untrusted` policy, the defaults when omitted, with every rule
    live but the loopback ones, in the transport and the pre-check alike) with a conditional-GET `mockwebserver3` dispatcher of its own;
    `LoopbackFetchers.trusted(userAgent, policy, resolve, configure)` forces cleartext on and keeps every other property of the policy, pinned hosts included;
  - `UrlFetcher.close()` closes the transport only when an owning factory or `LoopbackFetchers` built it.
- **Unit 11, `http-docs` (#70):** it documents the facades from this unit's KDoc, the README's "HTTP client stack" section and the CHANGELOG entries, and writes
  the `### webtools-http` Components table. The evergreen `docs/webtools-http-architecture.md` carries the two rules a consumer's code depends on, as the class
  KDoc of `UpstreamClient` and `UrlFetcher` states them: what a call spends (the facade's own checks spend nothing; once a permit is granted, a response of any
  status spends it, only the transport's `InvalidRequest` gives it back, and every other failure the transport returns, a cancellation and an `Error` leave it
  spent), and the logging contract (the loggers, the lines and their levels; no line carries a header value, a body, a query string or a decoded value, so no
  credential sent in a header or a query pair; URLs are printed through `Url.redacted()`, path shown and query not, and the endpoint id as given, so a secret
  carried in a path is printed).
- **Units 6, 7 and 8 (#65, #66, #67):** nothing changes in them. The facades use their contracts as built: the transport's table of what each failure implies,
  the cache's loader rules, the budget's values-not-exceptions and `flush()`.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md) concrete against the code as built, and each is a
dated correction (2026-11-06) there, at the section named. [`plan.md`](plan.md) §6.11 gives the reasons.

- **The permit after a failed send** (§6.6, §7.2) is released for the transport's `InvalidRequest` only; every other failure the transport returns,
  `RefusedByPolicy` and `Closed` included, leaves it spent. §6.6 released it for `RefusedByPolicy`, `InvalidRequest` and `Closed`, and its correction of
  2026-11-02 left the two to this unit.
- **`RateLimited.retryAfter`** (§13) is the longer of the server's `Retry-After` and the budget's `pausedFor`, read once after `complete(signals)`. This settles
  the point of §13 and the second half of this unit's row in [`../consequences.md`](../consequences.md) §7.
- **`Endpoint<T>`** (§12) is the module's internal-constructor-and-factory pair made generic, invariant in `T`. On Kotlin 2.2.0 every consumer-style call binds
  the factory and infers `T`, measured from a separate JVM module, and the shape compiles for `js`
  ([`research/endpoint-factory-resolution.md`](research/endpoint-factory-resolution.md) F1 to F3); the module's own tests bind the same way
  ([`final-implementation.md`](final-implementation.md) §4, P1). This settles §12's risk and the first half of the §7 row.
- **Classification** (§6.6, §7.2): a declared miss status is checked before a `2xx` is decoded, so a `204` declared a miss is a miss; a miss status is never `429`.
- **The client checks its own policy** (§6.6, §7.2): at construction against the base URL, and before anything is spent on each call; §6.6 gave the pre-check to
  `UrlFetcher` only.
- **The cache key** (§6.6) carries the full request path under a reserved name and the encoded query pairs, which `CacheKey` sorts; a credential sent as a
  query pair is part of the key, as every pair is, where §6.6 lists credentials among what the key never holds.
- **The per-call `DEBUG` line** (§6.6) reports whether the call's own loader ran, where §6.6 lists "hit/miss/joined": the cache port cannot tell a hit from a join.
  Of a cache key's parameters, only the request path appears in a line: the client's refusal line prints `Url.redacted()`, query hidden
  ([`final-implementation.md`](final-implementation.md) D14).
- **`UpstreamClient.flush()`** (§6.6) joins the client's members: the exact final write of a client-owned budget with a persistent store.
- **Construction and sizing** (§6.3.2, §7.4): the owning client builds the budget first, then the transport, then the cache, because the transport's sizing needs
  the budget's `maxBurst`; sizing raises both `maxConcurrentRequests` and `maxConcurrentRequestsPerHost`, from any `WindowedCallBudget` the client uses, built or
  supplied; only the client's owning factory sizes.
- **`UrlFetcher(userAgent, policy) { }`** (§6.6) joins `UrlFetcher(userAgent) { }`, and `UrlFetcherConfig` gains `timeSource`.
- **Defaults §6.6 left open:** `defaultMaxWait` 5 s; an endpoint's lane `Interactive`, `ttl` zero and `missTtl` following `ttl`.
- **`LoopbackFetchers`** (§6.7) joins the published fixtures.

## 6. Neighbouring issues

- **#58** (CI and repository practices): its CI would be the first place these tests run on every change.
- **[WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87)**, `UrlParseLinearTimeNonFunctionalTest` exceeding its bound when the level test
  tasks run in parallel: four more Level 4c classes run beside it; it passed in this unit's final run ([`final-implementation.md`](final-implementation.md) §4).
- **The IDE checks of #62 to #67** are still open; this unit's join them, at the next IntelliJ session (§8).

## 7. Follow-ups this unit leaves

Each is still open. The first nine are [`plan.md`](plan.md) §13's and §4's; the last three come from the build, against the plan section each would refine.

- **`client-cache-config`:** a hook on the owning client factory for the owned cache's settings (`maxWeight` and the rest); today a consumer passes its own
  `CaffeineResponseCache`, which the client does not close.
- **`endpoint-weigher`:** a weigher on `Endpoint`, so the cache's bound can be bytes rather than entries; every `Found` weighs 1 today.
- **`client-invalidate`:** `invalidate(endpoint) { }` and `invalidateAll()` on the client, for a write that makes a cached read stale.
- **`call-deadline`:** a per-call deadline on `CallSpec` returning a new failure leaf (architecture §12); a caller wraps `call` in `withTimeoutOrNull` today.
- **`endpoint-miss-predicate`:** a `missWhen` predicate for a "200 with an empty object" confirmed miss (architecture §13).
- **`fetcher-owned-budget`** and **`fetcher-sizing`:** a budget built and owned by `UrlFetcher`, and sizing its transport from a windowed budget.
- **`endpoint-covariance`:** `Endpoint<out T>`, additive and compatible if a consumer needs a heterogeneous collection.
- **`facades-conditional-get`:** a conditional-GET layer in the facades themselves; `FeedFetcher` (#69) carries its own on top of `UrlFetcher`.
- **A Linux and macOS run** of the Level 3 suite, once #58's CI exists, and the IDE checks of §8.
- **`facade-close-cancellation`** (plan §6.10): the closing step logs and goes on after a `CancellationException` a part's `close()` throws, as it does after
  any other exception, so the remaining parts still close (§8).
- **`dispatch-part-contract`** (plan §6.5, §6.9): the dispatcher turns what a consumer's part throws into a failure value in both facades: a non-fatal
  exception from the budget, and a cancellation that is not the caller's, which `fetch` would tell from the caller's own as the cache's loader rule does (§8).
- **`default-header-sendable`** (plan §6.10, R7): construction refuses a default header whose value is not sendable, as it refuses one the transport controls (§8).

## 8. Known gaps

What the build leaves open, as of the code-landing commit. Where the build departs from [`plan.md`](plan.md), the deviation is in
[`final-implementation.md`](final-implementation.md) §2.

- **A refusal after the permit costs a permit.** Under `Untrusted` with a windowed budget, a name that resolves to a denied address, or a connection the socket
  guard refuses, fails at hop 1 with nothing sent, yet the permit stays spent: a `RefusedByPolicy` carries no hop number, so the facade cannot tell it from a
  refused redirect target, which follows a request the server received. The same holds for `Closed` from a transport a consumer closed before the call. The
  over-count is one permit per such call, on the side that never under-counts a provider's window; the facades' own checks keep the common refusals free.
- **The client's transport may open as many connections to one host as the budget's burst.** The owning factory raises both concurrency limits to `maxBurst`
  with no ceiling, so a budget whose smallest window is large (a lone daily window of thousands) lets that many requests run at once, on as many dispatcher
  threads, with a worst-case memory of that many times the policy's `maxResponseBytes`; a consumer that wants fewer adds a short window to its budget or passes
  its own transport to the common factory.
- **The closing step stops at a `CancellationException`.** A part's `close()` that throws one ends the closing order there: the module's non-fatal helper
  rethrows a cancellation, so the parts after it stay open and the facade's `close()` throws. A facade closes only parts it built (`CaffeineResponseCache`,
  `WindowedCallBudget`, `OkHttpTransport`), and none of them throws from `close()`, so no path through the public API reaches this today. Follow-up
  `facade-close-cancellation` (§7).
- **What a consumer's part throws.** A budget that throws a non-fatal exception from `acquire`, or from a permit's `complete` or `release`, against the port's
  contract, makes `UrlFetcher.fetch` throw that exception and `UpstreamClient.call` return it as a failure that is not an `HttpFailure` (the cache's loader
  rule, which a non-`GET` follows too). A `CancellationException` that is not the caller's, thrown inside a consumer's transport, reader or budget (a
  `withTimeout` expiring there), is thrown out of `fetch` as if the caller were cancelled; `call` returns it as the `IllegalStateException` of
  [`final-implementation.md`](final-implementation.md) D12. None of the module's own parts throws either. Follow-up `dispatch-part-contract` (§7).
- **A default header that cannot be sent.** A default header whose value is a valid field value but not sendable (a non-ASCII character) passes construction;
  every call then returns `InvalidRequest` (the request type refuses the value). Follow-up `default-header-sendable` (§7).
- **A secret carried in a URL's path is logged.** `Url.redacted()` hides the query and shows the path, so a token in a path segment, in the client's base path
  or in an administrator-supplied URL (a webhook address) appears in the fetcher's per-fetch `DEBUG` line and its refusal and throttle `WARN` lines, and in the
  client's creation `INFO` line and refusal `WARN` line; the client also prints an endpoint id as given. Both facades' KDoc state it, and
  `CallFacadesSecrecyNonFunctionalTest` carries its sentinels in headers, query pairs, bodies and decoded values, not in a path. Whether redaction should cover
  the path is a question for `Url.redacted()` itself, which the transport's lines use too; it is not this unit's to change, and #93 tracks it.
- **A failure's `cause` is outside the secrecy suite.** `CallFacadesSecrecyNonFunctionalTest` scans log events, failure messages and every `toString()`, but not a
  failure's `cause`, which is the consumer's own exception (a decoder's, a reader's or a transport's).
- **A shared cache load carries the first caller's lane and wait cap** ([`../architecture.md`](../architecture.md) §12): a joining caller with a tighter deadline
  wraps its own call in `withTimeoutOrNull`, which frees that caller and leaves the load running.
- **The cache key holds no host.** Two clients sharing one cache, with endpoints of one id and the same path, read each other's entries; clients that share a
  cache need distinct endpoint ids.
- **The ABI dump does not pin `Endpoint`'s `T : Any` bound or its variance**
  ([`research/endpoint-factory-resolution.md`](research/endpoint-factory-resolution.md) F9): a change to either would pass `apiCheck` and every test.
- **A consumer's miscall of the `Endpoint` factory is reported against the `internal` constructor** ("Cannot access … it is internal"), not as the missing or
  misplaced decoder (F7). The module's own test compilations see `internal`, so no test here shows the diagnostic.
- **`RateLimited.retryAfter` may exceed the budget's longest pause**, up to what the server asked; a consumer caps its own wait. Zero means that neither the server
  nor the budget named a wait, and the server may still throttle again.
- **`dispatcher` has no effect when the client owns no cache** (the common factory, or a supplied cache).
- **The owned cache's bound is an entry count:** every `Found` weighs 1, so `maxWeight` 1,000 bounds entries, not bytes (follow-up `endpoint-weigher`).
- **Judgement defaults, not measurements:** `defaultMaxWait` 5 s ([`research/default-wait-cap.md`](research/default-wait-cap.md)), the endpoint's `ttl` of zero
  and `missTtl` of the smaller of `ttl` and 60 s, the default lane `Interactive`.
- **Real upstreams are not exercised:** behaviour against a real provider's headers and rate limits, and whether `RateLimited.retryAfter` suits a specific
  provider's signal reader, rest on the fakes and the loopback servers ([`plan.md`](plan.md) §9.2).
- **Measured only on the build host,** Windows 11 with JDK 23.0.2; Linux, macOS and other JDKs wait for #58's CI.
- **IDE behaviour is untested:** completion of the factories and specs, the `@WebToolsDsl` scoping of the five new builders, Quick Documentation and its links
  ([`final-implementation.md`](final-implementation.md) §5). Owner: the maintainer; trigger: the next IntelliJ session, together with the open IDE items of #62 to
  #67 ([`../67-call-budget/consequences.md`](../67-call-budget/consequences.md) §8).

## 9. The plan's assumptions, as built

No assumption of [`plan.md`](plan.md) §3 failed. As far as this build shows:

1. **A utilisation read after `complete(signals)` reflects the signals:** held. `UpstreamClientThrottleComponentTest` asserts `retryAfter` equal to the budget's
   `pausedFor` after a throttle with no `Retry-After` (61 s at the virtual clock's midnight start) and the remaining pause of a longer earlier one.
2. **OkHttp's dispatcher limits are the only place a granted permit queues:** held. `UpstreamClientBurstSizingIntegrationTest` puts eight calls in flight at
   once, and P3 shows the per-host limit is what holds them otherwise.
3. **`TimeSource` marks are enough for the per-call figures:** held; the logging tests read the figures on the virtual clock.
4. **The conflated channel carries the loader's trace to the call:** held. The logging tests read `loaded`, `waitedMs`, `status` and `bytes` from it.
5. **`Job().complete()` is a compare-and-set for the closed flag:** held. `UrlFetcherCloseRaceNonFunctionalTest` closes from 32 threads at once and logs
   `closed` exactly once per round, and the client's lifecycle test closes three times and logs it once.
6. **A failed owning-factory build is cleaned up for a non-fatal exception only:** as designed. `UpstreamClientOwningFactoryIntegrationTest` shows a refused
   `userAgent` closing the budget already built; an `Error` during construction was not exercised.
7. **The test server serves each connection on its own thread:** held (P3).
8. **The README row fits the table's style:** held; the row gained one clause.
9. **The research's two `Endpoint` measurements hold in this module:** held (P1). In the module's own tests, which see the `internal` constructor, every
   consumer form binds the factory.
