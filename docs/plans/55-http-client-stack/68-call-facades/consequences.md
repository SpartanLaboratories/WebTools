# Issue #68 — `call-facades` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#68`, *"webtools-http: `UpstreamClient` and `UrlFetcher` composing cache, budget and transport"*. It is unit 9,
  `call-facades`, of [#55's architecture](../architecture.md) (§6.6, §7.2, §7.4, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this unit, and what it
  leaves behind. The file-level detail is in [`plan.md`](plan.md); the evidence is in [`research/README.md`](research/README.md) with its three findings files.
- **Status:** planned. Once the unit is built, this document is reconciled with the build, and the as-built record, with every measurement and deviation, is
  `final-implementation.md` beside it.
- **Baseline:** `master` `5f221ce`; branch `feat/issue-68-call-facades`, with the research commits `98f7eb7` (the questions, the verification and the summary) and `3d9d9ff` (the three findings files).
- **Related docs:** [`plan.md`](plan.md); [`research/README.md`](research/README.md); the systems-level [`../consequences.md`](../consequences.md); the as-built records
  of the units this one composes, [`../65-okhttp-transport/`](../65-okhttp-transport/final-implementation.md), [`../66-response-cache/`](../66-response-cache/final-implementation.md)
  and [`../67-call-budget/`](../67-call-budget/final-implementation.md), and of the units under them, [`../62-http-core/`](../62-http-core/final-implementation.md),
  [`../63-test-support/`](../63-test-support/final-implementation.md) and [`../64-url-policy/`](../64-url-policy/final-implementation.md).
- **The system:** the call facades, registered for the state boards as `webtools-http-facades` (planned until this unit lands).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the call facades. In common code: `Endpoint` (with its builder and factory), `CallSpec`, `FetchSpec`, `UpstreamClientConfig`, `UpstreamClient` and its factory over a transport, `UrlFetcherConfig`, `UrlFetcher` and its factory over a transport. On the JVM: the owning factories `UpstreamClient(baseUrl, userAgent) { }`, `UrlFetcher(userAgent) { }` and `UrlFetcher(userAgent, policy) { }`. All of them are Stable Core and usable without the opt-in; no seam is added. `UpstreamFailure.RateLimited` gets its first producer and a reworded KDoc for `retryAfter`; its shape does not change. The ABI dump grows by exactly the classes of [`plan.md`](plan.md) §7.16. The test-fixtures variant gains `LoopbackFetchers` (Stable Core, source compatibility only) and no dependency. No dependency of either main variant changes. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **One call for an upstream's endpoint.** `UpstreamClient.call(endpoint) { path(…); query(…) }` builds the request from a base URL, checks it against the
  client's outbound-URL policy, serves a `GET` from the response cache, takes a budget permit only when it loads, sends one request, reads the rate signals,
  and returns `Result<Lookup<T>>`: the decoded value, a confirmed miss, or a typed failure.
- **N callers, one load, one permit.** Concurrent `GET`s of one key share one load and one permit; a hit costs nothing.
- **Throttles reported once, never retried.** A `429` or a reader-reported throttle pauses the budget and returns `RateLimited` with how long to wait before
  calling again: the longer of the server's `Retry-After` and the budget's pause in force.
- **A front door for administrator-supplied URLs.** `UrlFetcher.fetch(request)` refuses a URL its policy refuses before it spends anything, and returns every
  status but a throttle as a response, a `304` included, for a caller's conditional GET.
- **Batteries on the JVM.** The owning factories build and own an `OkHttpTransport`, a `CaffeineResponseCache` and a `WindowedCallBudget`, size the transport
  so a granted permit never waits in OkHttp's queue, and close them in the order cache, budget, transport; a component a consumer passes in is never closed.
- **Failures as values.** Every expected failure is in the `Result`: `InvalidRequest` for a call that breaks a rule, before anything is spent;
  `RefusedByPolicy`; `BudgetExhausted`; the transport's failures; `RateLimited`, `UpstreamError` and `DecodeFailure`; `Closed`.
- **Virtual time under test:** the clocks and the dispatcher are configuration properties, so the published `VirtualClock` drives the cache, the budget and
  the fake transport together; `LoopbackFetchers` runs `UrlFetcher` over a loopback `mockwebserver3` server with every `Untrusted` rule but the loopback ones live.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`plan.md`](plan.md) §7 lists them): the facades and their internal request assembly, loader and classification in
  `commonMain` (compiled for `js` by the check module like every common file); the owning factories and the transport sizing in `jvmMain`; `LoopbackFetchers` in
  the fixtures; their tests in `commonTest` and `jvmTest`, in the level packages, with four new test-support files.
- **Changed files:** `UpstreamFailure.kt` (KDoc of `RateLimited`); `WebToolsDsl.kt` (KDoc list) and `WebToolsDslComponentTest` (its list), each gaining five
  builders; `webtools-http/api/webtools-http.api`.
- **First caller of the three parts.** Nothing in the repository composed a cache, a budget and a transport before; the facades are the first production caller
  of `ResponseCache.getOrLoad`, `CallBudget.acquire` and `HttpTransport.execute`.

### Build

- **No build file changes.**
- **`./gradlew build` does more work:** the new tests of `webtools-http` (Level 3 with loopback servers among them); the fixtures-publication check runs again,
  because `commonMain`, `jvmMain` and `jvmTestFixtures` are among its inputs; `apiCheck` against the regenerated dump; the check module's `js` compile of the new
  common files.

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the facades; a new "HTTP client stack" how-to; the test-fixtures paragraph names
  `LoopbackFetchers`. The `### webtools-http` Components table and the final README pass stay #70's.
- **`CHANGELOG.md`:** `### Added` entries under `## [Unreleased]`.
- **KDoc** on every new public declaration states its tier and contract.
- **[`../architecture.md`](../architecture.md)** gains dated corrections at §6.6, §7.2, §7.4's row and §13 once the unit is built (§5 below lists what they record).
- **[`../consequences.md`](../consequences.md)**, the systems-level document, gets its unit-9 row of §7 settled and one line for this unit, linking to §4.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class calls an upstream through the stack**, so nothing is moved onto the facades. The `Connector` follow-ups of
  [`../architecture.md`](../architecture.md) §8 (`connector-get-skrape-on-webtools-http`, `connector-download-fetch-half`) can build on `UrlFetcher` once
  `webtools-http` 1.0.0 is released.
- **`RateLimited`** gets its producer; nothing else in the module creates it.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts. In short:

- **Unit 10, `feeds` (#69):**
  - `FeedFetcher` composes a `UrlFetcher`: the common factory `UrlFetcher(transport, policy) { }` for its fake-based tests and `FeedFetcher.untrusted(userAgent)`
    through the owning `UrlFetcher(userAgent) { }` or `UrlFetcher(userAgent, policy) { }`;
  - `fetch(request) { cost; lane; maxWait }` returns every status but a throttle as a response (a `304` with its headers and no body), `RateLimited` for a throttle,
    and `RefusedByPolicy` with nothing spent for a refused URL;
  - its Level 3 runs over `LoopbackFetchers.untrusted(userAgent)` with a conditional-GET `mockwebserver3` dispatcher;
  - the fetcher owns the transport its owning factories built, and `close()` closes it.
- **Unit 11, `http-docs` (#70):** it documents the facades from this unit's KDoc, the README how-to and the CHANGELOG entries, and writes the `### webtools-http`
  Components table.
- **Units 6, 7 and 8 (#65, #66, #67):** nothing changes in them. The facades use their contracts as built: the transport's table of what each failure implies,
  the cache's loader rules, the budget's values-not-exceptions and `flush()` before `close()`.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md) §6.6 concrete against the code as built and the
research, and each becomes a dated correction there once built (at §6.6, §7.2, §7.4's row and §13). [`plan.md`](plan.md) §6.11 gives the reasons.

- **The permit after a transport failure** is released for `InvalidRequest` only; every other failure, `RefusedByPolicy` and `Closed` included, leaves it spent.
  §6.6 released it for `RefusedByPolicy`, `InvalidRequest` and `Closed`, and its correction of 2026-11-02 left the two to this unit.
- **`RateLimited.retryAfter`** is the longer of the server's `Retry-After` and the budget's `pausedFor`, read once after `complete(signals)`. This settles the
  point of §13 and the second half of this unit's row in [`../consequences.md`](../consequences.md) §7.
- **`Endpoint<T>`** is the module's internal-constructor-and-factory pair made generic, invariant in `T`, measured to resolve and infer on Kotlin 2.2.0 for the JVM
  and `js` ([`research/endpoint-factory-resolution.md`](research/endpoint-factory-resolution.md)). This settles §12's risk and the first half of the §7 row.
- **Classification:** a declared miss status is checked before a `2xx` is decoded (§6.6 decodes a `2xx` first), so a `204` declared a miss is a miss; a miss
  status is never `429`.
- **The client checks its own policy:** at construction against the base URL, and before anything is spent on each call; §6.6 gave the pre-check to `UrlFetcher`
  only.
- **The cache key** carries the full request path under a reserved name, then the encoded query pairs, and `CacheKey` sorts them.
- **The per-call `DEBUG` line** reports whether the call's own loader ran, where §6.6 lists "hit/miss/joined": the cache port cannot tell a hit from a join.
- **`UpstreamClient.flush()`** joins §6.6's member list: the exact final write of a client-owned budget with a persistent store.
- **Construction and sizing:** the owning client builds the budget first, then the transport, then the cache, because the transport's sizing needs the budget's
  `maxBurst`; sizing raises both `maxConcurrentRequests` and `maxConcurrentRequestsPerHost` (§6.3.2 and §7.4 name the first only), and only the client's owning
  factory sizes.
- **`UrlFetcher(userAgent, policy) { }`** joins `UrlFetcher(userAgent) { }`, so a deployment adds its own denied ranges or ports without leaving the owning factory.
- **Defaults §6.6 left open:** `defaultMaxWait` 5 s; an endpoint's lane `Interactive`, `ttl` zero and `missTtl` following `ttl`.
- **`LoopbackFetchers`** joins the published fixtures (§6.7's contributions from other units).

## 6. Neighbouring issues

- **#58** (CI and repository practices): its CI would be the first place these tests run on every change.
- **[WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87)**, the `UrlParseLinearTimeNonFunctionalTest` flake under concurrent level tasks:
  more Level 4c classes run beside it.
- **The IDE checks of #62 to #67** are still open; this unit's join them, at the next IntelliJ session.

## 7. Follow-ups this unit leaves

- **`client-cache-config`:** a hook on the owning factory for the owned cache's settings (`maxWeight` and the rest); today a consumer passes its own
  `CaffeineResponseCache`, which the client does not close.
- **`endpoint-weigher`:** a weigher on `Endpoint`, so the cache's bound can be bytes rather than entries; every `Found` weighs 1 today.
- **`client-invalidate`:** `invalidate(endpoint) { }` and `invalidateAll()` on the client, for a write that makes a cached read stale.
- **`call-deadline`:** a per-call deadline on `CallSpec` returning a new failure leaf (architecture §12); a caller wraps `call` in `withTimeoutOrNull` today.
- **`endpoint-miss-predicate`:** a `missWhen` predicate for a "200 with an empty object" confirmed miss (architecture §13).
- **`fetcher-owned-budget`** and **`fetcher-sizing`:** a budget built and owned by `UrlFetcher`, and sizing its transport from a windowed budget.
- **`endpoint-covariance`:** `Endpoint<out T>`, additive and compatible if a consumer needs a heterogeneous collection.
- **Seam graduation** is unchanged by this unit (it adds no seam).

## 8. Known gaps

These hold for the unit as planned; the reconciliation after the build adds what the build leaves open.

- **A refusal after the permit costs a permit.** Under `Untrusted` with a windowed budget, a name that resolves to a denied address, or a
  connection the socket guard refuses, fails at hop 1 with nothing sent, yet the permit stays spent: a `RefusedByPolicy` carries no hop
  number, so the facade cannot tell it from a refused redirect target, which follows a request the server received. The same holds for
  `Closed` from a transport a consumer closed before the call. The over-count is one permit per such call, on the side that never
  under-counts a provider's window; the facades' own checks keep the common refusals free.
- **The client's transport may open as many connections to one host as the budget's burst.** The owning factory raises both concurrency
  limits to `maxBurst` with no ceiling, so a budget with a large smallest window (a lone daily window of thousands) lets that many requests
  run at once, on as many dispatcher threads, with a worst-case memory of that many times the policy's `maxResponseBytes`; a consumer that wants
  fewer adds a short window to its budget or passes its own transport to the common factory.
- **A shared cache load carries the first caller's lane and wait cap** ([`../architecture.md`](../architecture.md) §12): a joining caller
  with a tighter deadline wraps its own call in `withTimeoutOrNull`, which frees that caller and leaves the load running.
- **The cache key holds no host.** Two clients sharing one cache, with endpoints of one id and the same path, read each other's entries;
  the KDoc requires one id per endpoint for a shared cache.
- **The ABI dump does not pin `Endpoint`'s `T : Any` bound or its variance**
  ([`research/endpoint-factory-resolution.md`](research/endpoint-factory-resolution.md) F9); the KDoc and the Level 2 tests do.
- **A consumer's miscall of the `Endpoint` factory is reported against the `internal` constructor** ("Cannot access … it is internal"),
  not as the missing or misplaced decoder (F7); the KDoc and the README example show the call's shape.
- **`RateLimited.retryAfter` may exceed the budget's longest pause**, up to what the server asked; a consumer caps its own wait. Zero means
  that neither the server nor the budget named a wait, and the server may still throttle again.
- **`dispatcher` has no effect when the client owns no cache** (the common factory, or a supplied cache); the KDoc says so.
- **The owned cache's bound is an entry count:** every `Found` weighs 1, so `maxWeight` 1,000 bounds entries, not bytes (§7,
  `endpoint-weigher`).
- **Judgement defaults, not measurements:** `defaultMaxWait` 5 s ([`research/default-wait-cap.md`](research/default-wait-cap.md)), the
  endpoint's `ttl` of zero and `missTtl` of the smaller of `ttl` and 60 s, the default lane `Interactive`.
- **The gate on implementers is untouched and still has no automated check** ([`../consequences.md`](../consequences.md) §7); this unit
  adds no seam.
- **IDE behaviour is untested:** completion of the factories and specs, the `@WebToolsDsl` scoping of the five new builders and Quick
  Documentation. Owner: the maintainer; trigger: the next IntelliJ session, together with the open IDE items of #62 to #67
  ([`../67-call-budget/consequences.md`](../67-call-budget/consequences.md) §8).
- **Measured only on the build host,** Windows 11 with JDK 23.0.2; Linux, macOS and other JDKs wait for #58's CI.
