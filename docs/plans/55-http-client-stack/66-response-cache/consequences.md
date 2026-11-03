# Issue #66 — `response-cache` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#66`, *"webtools-http: `ResponseCache` port and `CaffeineResponseCache` default"*. It is unit 7,
  `response-cache`, of [#55's architecture](../architecture.md) (§6.4, §6.9, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this
  unit, and what it leaves behind. The file-level detail is in [`plan.md`](plan.md); the evidence is in [`research/README.md`](research/README.md)
  with its two findings files.
- **Status:** planned. Once the unit is built, this document is reconciled with the build, and the as-built record, with every measurement and
  deviation, is `final-implementation.md` beside it.
- **Baseline:** `master` `e8f0a88`; branch `feat/issue-66-response-cache`, with the research commits `c19db19`, `97beb79`, `879abff` and
  `826200e`.
- **Related docs:** [`plan.md`](plan.md); [`research/README.md`](research/README.md); the systems-level [`../consequences.md`](../consequences.md);
  the as-built records of the units this one builds on, [`../62-http-core/`](../62-http-core/final-implementation.md),
  [`../63-test-support/`](../63-test-support/final-implementation.md), [`../64-url-policy/`](../64-url-policy/final-implementation.md) and
  [`../65-okhttp-transport/`](../65-okhttp-transport/final-implementation.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the response cache. In common code: the port `ResponseCache` (implementing it needs the opt-in) with its pass-through `ResponseCache.Disabled`, and the values `CacheKey`, `CachePolicy` (with its builder and factory), `Lookup` (`Found`, `Missing`) and `CacheStats`. On the JVM: `CaffeineResponseCache`, its builder and its factory. All of them except the port's implementation are Stable Core and usable without the opt-in. The ABI dump grows by exactly these ([`plan.md`](plan.md) §7.16). No dependency of either variant changes: Caffeine 3.3.1 was already a runtime dependency of `webtools-http-jvm`. The test-fixtures variant does not change. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **One load for any number of concurrent callers of one key.** Calls that arrive while a load is in flight join it; a call's own
  cancellation leaves the load and the other callers intact, and a load nobody waits for any more still completes and is stored.
- **A bounded store with per-entry lifetimes.** A value lives its policy's `ttl` and a confirmed miss its `missTtl`, both from the load's
  completion; `maxWeight` bounds what is stored at every instant, and a value heavier than the bound is delivered and never stored.
- **Freshness on demand.** A per-call `maxAge` refuses an older entry; N callers on one stale entry cost one load.
- **Failures never stored.** A loader's failure reaches every caller of that load as one instance and is never served to a later call; an
  `Error` is rethrown to every caller; a cancellation the loader throws on its own becomes an `IllegalStateException` failure, never a
  cancellation of the caller.
- **A clean close.** After `close()` every waiting call and every later call returns `Closed("cache")`, without waiting for a loader to
  unwind.
- **Statistics** with one meaning each: hits (joins included), misses (loads started), size evictions only, and failed loads.
- **Common code and an explicit opt-out:** `ResponseCache.Disabled` runs every loader, stores nothing and shares nothing.
- **Virtual time under test:** the cache's `timeSource`, `loadDispatcher` and `maintenanceExecutor` are builder properties, so the published
  `VirtualClock` drives its expiry together with `delay` and the wall clock.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`plan.md`](plan.md) §7 lists them):
  - `commonMain`: the port and `Disabled`, the four value types and the `internal` loader runner both caches share (compiled for `js` by the
    check module like every common file);
  - `jvmMain`: `CaffeineResponseCache` and its `internal` entry, rules and `Expiry`;
  - their tests in `commonTest` (the values, the key's canonical form, the consumer's view without the opt-in) and `jvmTest` (everything
    that suspends), in the level packages, with their support classes.
- **Changed files:**
  - `ExperimentalForInheritanceWebToolsApi.kt` and `WebToolsDsl.kt`: KDoc only, their lists gain `ResponseCache` and the two new builders;
  - `WebToolsDslComponentTest`: its list of builders gains the two new builders;
  - `VirtualClockThreeClocksComponentTest`: KDoc only;
  - `webtools-http/api/webtools-http.api`: the new classes.
- **No caller yet.** Nothing in the repository calls a `ResponseCache`; the call facades (#68) are the first.

### Build

- **No build file changes.**
- **`./gradlew build` does more work:** the new tests of `webtools-http` (Level 3 with one loopback server, and Level 4c on real threads,
  the failure-identity check among them); the fixtures-publication check runs again, because `jvmMain` is among its inputs; `apiCheck`
  against the regenerated dump; the check module's `js` compile of the new common files.

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the response cache.
- **`CHANGELOG.md`:** one `### Added` entry under `## [Unreleased]`.
- **KDoc** on every new public declaration states its tier and contract.
- **[`../architecture.md`](../architecture.md)** gains dated corrections at §6.4 and at the §7.4 row for the cache once the unit is built (§5
  below lists what they record).
- **[`../consequences.md`](../consequences.md)**, the systems-level document, gains one line for this unit, linking to §4.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class caches HTTP responses**, so nothing is moved onto the cache. Its first users are the call facades (#68).
- **`VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache.** It proves the virtual clock apart from the response cache; the
  new `CaffeineResponseCacheThreeClocksComponentTest` proves the same with the real cache.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts. In short:

- **Unit 9, `call-facades` (#68):**
  - it calls `getOrLoad(CacheKey(endpoint.id, canonicalParams), CachePolicy(ttl) { missTtl = … }, maxAge) { loader }` for a `GET`, and the
    overload without `maxAge` when a call sets none;
  - `CacheKey` sorts its parameters by name itself, with a stable sort, so the facade passes the canonical encoded path and the query pairs
    in any order, repeated names in their own order; a key never carries a tenant, a header or a credential;
  - a negative `maxAge` throws `IllegalArgumentException`, so the facade checks `CallSpec.maxAge` first and returns `InvalidRequest` before
    anything is spent;
  - its loader returns failures as values; an `Exception` it throws is returned as the failure, an `Error` is rethrown to every caller, and a
    cancellation it throws while its caller is active comes back as an `IllegalStateException` failure;
  - `Lookup.Found(value)` weighs 1; `Found(value, weight)` needs a weight of at least 1;
  - `ResponseCache.Disabled` is the common factory's default, and its `close()` does nothing, so a client may close it safely;
  - the JVM owning factory builds `CaffeineResponseCache { timeSource = …; loadDispatcher = …; maintenanceExecutor = … }` from the client's
    clock and dispatcher, and closes it first of the three components it owns;
  - `cacheStats()` returns `stats()`;
  - a coalesced caller with a tighter deadline wraps its own `getOrLoad` in `withTimeoutOrNull`, which cancels only that caller.
- **Unit 8, `call-budget` (#67):** nothing from this unit.
- **Unit 10, `feeds` (#69):** nothing (`UrlFetcher` has no cache).
- **Unit 11, `http-docs` (#70):** it documents the cache from this unit's KDoc, README row and CHANGELOG entry.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md) §6.4 concrete against the
code and the sources, and each becomes a dated correction there once built. [`plan.md`](plan.md) §6.11 gives the reasons.

- **Statistics:** no `recordStats()`, and one `evictionListener` that counts size removals only, where §6.4 has `recordStats()` and no
  listeners: Caffeine's `evictionCount` also counts expiry and failure removals, and varies with callback order.
- **Outcomes:** a fatal `Error` is carried by value like every failure, never as an exceptional completion; a loader's own cancellation is an
  `IllegalStateException` failure; a completed entry of zero lifetime is treated as absent.
- **`close()`:** it also completes every waiting future itself and clears the map.
- **Weights:** `Found`'s weight is at least 1, and a value heavier than `maxWeight` is never stored.
- **The call:** `getOrLoad` checks the caller's cancellation first; the overload without `maxAge` has a body that passes `Duration.INFINITE`.
- **The load scope** carries a `CoroutineExceptionHandler` that logs a failure outside the load's body once at `WARN`.
- **`ResponseCache.Disabled.close()`** does nothing.
- **The stack-level research's `startLoad` sketch** ([`../research/response-cache.md`](../research/response-cache.md)) is replaced by the total
  load body and its completers ([`research/README.md`](research/README.md), "Where the sources disagree").

## 6. Neighbouring issues

- **#58** (CI and repository practices): its CI would be the first place these tests run on every change. The Level 4c failure-identity check
  needs at least two cores to open the window it guards; on one core it passes without exercising it.
- **The IDE checks of #62 to #65** are still open; this unit's (completion of `CaffeineResponseCache { }`, the opt-in error on an implementer
  of `ResponseCache`, the `@WebToolsDsl` scoping of the two builders) join them, at the next IntelliJ session.

## 7. Follow-ups this unit leaves

- **A count of joined calls, and size and weight gauges, in `CacheStats`:** each additive later, through a new constructor of the class.
- **A typed failure leaf for a loader's own cancellation:** the failure model is #62's; a leaf is additive in a feature release, with the
  `IllegalStateException` then its `cause` or replaced.
- **Invalidating every key of one endpoint.**
- **A cache fake in the published fixtures**, if a consumer needs one beyond `CaffeineResponseCache` on `VirtualClock`.
- **Stale-while-revalidate:** `staleFor` on the `CachePolicy` builder, additive (architecture §4 lists it as a non-goal of #55).
- **Seam graduation:** `ResponseCache` moves from Experimental to `@SupportedExtension` in a feature release once BotTools' adapter has built
  against it; the promotion is recorded in `CHANGELOG.md`.

## 8. Known gaps

These hold for the unit as planned; the reconciliation after the build adds what the build leaves open.

- **Caffeine behaviour read from source, not run:** that a zero `Expiry` result expires a just-completed entry at once, and that Caffeine never
  cancels a future it holds ([`plan.md`](plan.md) §3, assumptions 1 and 2). The design does not depend on the first for correctness, and a
  waiter's guard covers the second.
- **The close-versus-start window** rests on the insert-then-check and on weakly consistent iteration of the cache's map; only the
  probabilistic Level 4c close-race test probes it.
- **The failure-identity race** depends on `CompletableFuture`'s dependent order, read in JDK 11.0.25's source; on a single-core host, or a JDK
  whose order differs, the Level 4c check can pass without having opened the window.
- **The residue of a cancelled waiter** (one callback and one `CompletableDeferred` on the shared future until the load ends) is inferred, not
  measured.
- **Measured only on the build host,** Windows 11 with JDK 23, once built; Linux, macOS and other JDKs wait for #58's CI.
- **IDE behaviour is untested:** Quick Documentation of the new types and the opt-in quick fix, at the next IntelliJ session (§6).
