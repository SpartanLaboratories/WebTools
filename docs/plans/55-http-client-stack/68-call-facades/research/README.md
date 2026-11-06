# Research: the call facades (`call-facades`)

[SpartanLaboratories/WebTools#68](https://github.com/SpartanLaboratories/WebTools/issues/68), unit 9
(`call-facades`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.6, with §6.2, §6.3, §6.4, §6.5, §7.2, §7.4, §9, §12,
§13 and §11 unit 9) and the systems-level consequences [`../../consequences.md`](../../consequences.md) (§7
and §8). This folder researches the implementation details that design leaves to the unit and that need
evidence from outside this repository.

## The work

`webtools-http` gains its two call facades, in `commonMain` with owning factories in `jvmMain` (package
`com.spartanlabs.webtools.http`):

- **`Endpoint<T>`:** a stable id, a decoder from `HttpResponse` to `Result<T>` that travels with the
  endpoint (the cache key cannot see a decoder), and builder properties `name`, `ttl`, `missTtl`, `cost`,
  `lane` and `misses(statuses)`.
- **`UpstreamClient`:** calls the endpoints of one upstream from a base URL. A call takes a `CallSpec`
  (path segments, query pairs, headers, method, body, lane, `maxWait`, `maxAge`) and returns
  `Result<Lookup<T>>`. A `GET` goes through `ResponseCache.getOrLoad`, with the budget's permit taken inside
  the cache's loader, so a hit costs nothing and N coalesced callers cost one permit; any other method goes
  straight to the loader.
- **`UrlFetcher`:** fetches administrator-supplied URLs. A fetch takes an `HttpRequest` and a `FetchSpec`
  (cost, lane, `maxWait`) and returns `Result<HttpResponse>`. The outbound-URL policy's request check runs
  first, so a refusal reaches neither the budget nor the transport; then the budget, the transport and the
  rate signals. No decode and no cache.
- **What a response becomes.** The rate signals are read with the configured `RateSignalsReader` and handed
  to `permit.complete(signals)`; a throttle (a `429`, or what the reader reports as throttled) becomes
  `UpstreamFailure.RateLimited(status, retryAfter)` with no retry. In `UpstreamClient`, a `2xx` is decoded (a
  decoder's failure becomes `HttpFailure.DecodeFailure`), a declared miss status becomes `Lookup.Missing`,
  and any other status `UpstreamFailure.UpstreamError`. Failures are never cached; a miss is cached for its
  `missTtl`. In `UrlFetcher` every status but a throttle is a response.
- **Factories.** A common factory for each facade takes an `HttpTransport`; without a cache the client uses
  `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`. A JVM owning factory for each builds and
  owns an `OkHttpTransport` (and, for the client, a `CaffeineResponseCache` and, when windows are set, a
  `WindowedCallBudget`), and sizes the transport's concurrency from `WindowedCallBudget.maxBurst`.
- **Lifecycle.** A facade is a `kotlin.AutoCloseable` with a non-suspending, idempotent `close()` that
  closes what it built, in the order cache, budget, transport, and nothing it was handed.
- **Tests:** Level 2 on the published fake transport and virtual clock (a refusal costs nothing, a `429`
  with `Retry-After` pauses the budget so the next call fails fast, a decode failure is never stored, a miss
  status is stored, a non-`GET` bypasses the cache), Level 3 for a real call end to end over loopback.

What shapes the questions below:

- **The contracts of the parts, as built.** `CallBudget.acquire(cost, lane, maxWait)` returns a refusal
  (`HttpFailure.BudgetExhausted(wait, reason, window)`) and a closed budget (`HttpFailure.Closed("budget")`)
  as values, and throws `IllegalArgumentException` for a `cost` below 1 or a negative `maxWait`
  ([`../../67-call-budget/consequences.md`](../../67-call-budget/consequences.md) §4). After
  `permit.complete(signals)`, `utilisation().pausedFor` is the pause in force: at least
  `min(retryAfter, maxPause)` less the time elapsed, more when another hint or an earlier pause is longer,
  and less than `retryAfter` when `retryAfter` exceeds `maxPause` (24 hours by default); with no
  `Retry-After` and no counts, a throttle pauses until the next boundary of the windows whose current period
  is full, or of the shortest window when none is, plus the guard
  (`webtools-http/.../BudgetLedger.kt:136-141`). `CallBudget.Unlimited` never pauses. The cache's loader returns failures as values and runs in the
  cache's own load coroutine ([`../../66-response-cache/consequences.md`](../../66-response-cache/consequences.md)
  §4).
- **API conventions** ([`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md)
  F7 and the convention table): a configurable type is a final class with an `internal` constructor, built
  by a public factory function of the same name whose last parameter is a builder lambda defaulting to `{}`;
  no other defaulted parameter on public API; every builder passed to a consumer's lambda carries
  `@WebToolsDsl`; `explicitApi()`; `commonMain` is also compiled for `js` by `webtools-http-commoncheck`.
- **`UpstreamFailure.RateLimited`** already exists with an `internal` constructor `(status, retryAfter)`;
  `retryAfter` is a non-null, non-negative `Duration` (`webtools-http/.../UpstreamFailure.kt:38`).
- **Time.** The transport's call timeout (30 s for `Trusted`, 15 s for `Untrusted`) starts after the permit
  is granted; a shared cache load carries the first caller's lane and wait cap
  ([`../../architecture.md`](../../architecture.md) §12).

## Existing findings this research builds on

These are cited, not researched again.

- **[`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md):** F7 (defaulted
  parameters break binary compatibility; the factory-and-builder pattern), F9 (the facade is a composition;
  close what you built, leave what you were given), F10 (the decoder belongs to the endpoint), F11
  (`kotlin.AutoCloseable`), and its facade sketch and pipeline, which the architecture adopted.
- **[`../../research/http-transport.md`](../../research/http-transport.md)** F7 and F9: OkHttp's call timeout
  starts when a dispatcher thread runs the call, not when it is queued, so OkHttp's queue is invisible to
  the timeout and to the budget's wait cap; with one transport per upstream host the per-host limit is the
  one that binds.
- **[`../../research/call-budget.md`](../../research/call-budget.md)** F1 (providers count in fixed
  wall-clock windows and count refused requests), F6 (the pause rules), F8 (the permit's lifecycle: a permit
  never completed is spent; one permit per logical call).
- **[`../../65-okhttp-transport/plan.md`](../../65-okhttp-transport/plan.md) §12:** for each transport
  failure, whether a request was written to a server.

The codebase blast radius is small and is surveyed directly in the summary below.

## Questions

### 1. How should `Endpoint<T>` be built so that a consumer's call resolves to the public factory and infers `T` from the decoder, on Kotlin 2.2.0 in common code?

- **Options.**
  - A final generic class `Endpoint<T : Any>` with an `internal` constructor, a public top-level generic
    factory `fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure:
    Endpoint.Builder.() -> Unit = {}): Endpoint<T>`, and a non-generic nested `Endpoint.Builder` (the shape of
    every configurable type in the module, so far always non-generic).
  - A companion `operator fun <T : Any> invoke(...)` in place of the top-level factory.
  - A public constructor `Endpoint(id, decode)` with the optional properties on a separate builder.
- **What each must establish.** For a consumer in another module (no `internal` visibility) and for the
  module's own test compilations (which see `internal`): that `Endpoint("id", decode)`,
  `Endpoint("id", decode) { ttl = … }`, `Endpoint("id", ::decodeHero)` and `Endpoint<Hero>("id", decode)`
  resolve to the public factory without an ambiguity or an invisible-member error; that `T` infers from a
  lambda and from a function reference; that a call naming the `internal` constructor's arguments from
  outside the module fails to compile; that the factory and the class coexist in `commonMain` compiled for
  the JVM and for `js`; and what the ABI dump records for each shape.
- **Requirements.** No defaulted parameter on public API except the trailing `configure = {}`; the builder
  carries `@WebToolsDsl`; adding a builder property later must not break a compiled consumer; the decoder is
  fixed at construction.
- **Evidence that settles it.** A compile probe on Kotlin 2.2.0 with the module's multiplatform setup (a
  `jvm()` target and a `js()` compile), a separate consumer module and an associated test compilation; the
  Kotlin specification's overload-resolution rules for a constructor and a function of the same name.
- **Findings file:** [`endpoint-factory-resolution.md`](endpoint-factory-resolution.md).

### 2. What retry delay should a rate-limited failure carry once the budget has applied the response's signals?

- **Options.**
  - The server's `Retry-After` as read by the signal reader, or zero when there is none.
  - The pause the budget put in force, `utilisation().pausedFor`, read after `permit.complete(signals)`.
  - The longer of the two.
  - For each: what it reports for a `429` without `Retry-After` (the budget then pauses to the next boundary
    of its shortest window), for a `Retry-After` longer than the budget's `maxPause`, for an earlier pause
    longer than the server's ask, and under `CallBudget.Unlimited`, which never pauses.
- **Requirements.** `retryAfter` is a non-null, non-negative `Duration`; while a budget pause is in force,
  every call fails fast with `BudgetExhausted(Paused)` without reaching the transport, and a coalesced load
  hands one failure to every waiter; a pause never shortens; a caller backs off by the value without parsing
  `Retry-After` itself.
- **Evidence that settles it.** RFC 9110 §10.2.3 (`Retry-After`) and RFC 6585 §4 (`429`); what comparable
  HTTP clients and rate limiters report to a caller after a throttle or a refusal (for example JDA,
  Discord4J, Octokit's throttling plugin, the AWS SDK's retry strategy, Stripe's clients, .NET's
  `RateLimitLease` `RetryAfter` metadata and Polly, Resilience4j's `RequestNotPermitted`, Bucket4j's
  `ConsumptionProbe`), and whether any of them reports a client-side pause in place of, or beside, the
  server's ask.
- **Findings file:** [`rate-limited-retry-after.md`](rate-limited-retry-after.md).

### 3. How long should a call wait for a budget permit by default before it fails fast?

- **Options.** Zero (a call that does not fit at once fails with the wait it would have needed); a short
  finite cap, in seconds; `Duration.INFINITE` (wait as long as the plan needs, up to a pause of `maxPause`).
- **Requirements.** A wait beyond the cap fails at once with the exact wait; a caller can set its own cap
  per call; the first consumer has interactive calls (a person waits on the answer) and a background
  tracking lane that sets a short cap of its own ([`../../architecture.md`](../../architecture.md) §12); the
  transport's call timeout starts only after the permit is granted, so the cap adds to it; a shared cache
  load waits with the first caller's cap.
- **Evidence that settles it.** The defaults and documented guidance of comparable rate limiters and
  clients: Resilience4j's `RateLimiterConfig` (`timeoutDuration`), Bucket4j's blocking API, Guava's
  `RateLimiter`, .NET's `System.Threading.RateLimiting` queue options, Polly's rate-limiter strategy, and the
  rate-limit queues of Discord libraries (JDA, Discord4J, Kord) and of Octokit.
- **Findings file:** [`default-wait-cap.md`](default-wait-cap.md).

## Verification

### Outside-world claims

Every load-bearing claim of the three findings files here, and the two claims of the stack-level
[`../../research/http-transport.md`](../../research/http-transport.md) that the sizing below rests on, was re-opened at
its source on 2026-11-06.

**35 claims: all 35 hold, 4 of them in part; none fails and none is unclear.**

| Findings file | Claims | Result |
|---|---|---|
| [`endpoint-factory-resolution.md`](endpoint-factory-resolution.md) | 10 (1 to 10) | all hold; 4 and 6 in part |
| [`rate-limited-retry-after.md`](rate-limited-retry-after.md) | 12 (1 to 12) | all hold |
| [`default-wait-cap.md`](default-wait-cap.md) | 11 (1 to 11) | all hold; 9 in part |
| [`../../research/http-transport.md`](../../research/http-transport.md) | 2 (the call timeout starting when a dispatcher thread runs an enqueued call, F7; the dispatcher's defaults and its per-host queueing, F9) | both hold; the second in part |

**Claims that hold in part** (the wording is stronger than the source; no recommendation changes):

- `endpoint-factory-resolution.md` claim 4: the specification's tier list names top-level non-extension functions and
  never places a constructor in a set; that a constructor and a function of the same name compete in one tier is an
  inference from claim 1, which the probe's measurements (F1 to F10) carry.
- `endpoint-factory-resolution.md` claim 6: the specification says a property-like callable with `invoke` belongs to
  the lowest-priority set *of its own parts*, not that a companion's `invoke` is the lowest-priority candidate overall.
  The companion form is declined for its ABI cost, not for its priority.
- `default-wait-cap.md` claim 9: Octokit's request wrapper guarantees "at least 1000ms between writes" through a
  `schedule` call; that the scheduler is Bottleneck was not confirmed at the import. The default it supports is
  unaffected.
- The dispatcher claim: the defaults (`maxRequests` 64, `maxRequestsPerHost` 5) are exact at `parent-5.5.0`; that calls
  beyond the per-host limit wait in the ready queue is shown by `Dispatcher.promoteAndExecute`'s code
  (`if (asyncCall.callsPerHost.get() >= this.maxRequestsPerHost) continue`), not by the property's KDoc.

**Notes on claims that hold:** Ktor's `maxOf(block(it), retryAfter ?: 0)` (`rate-limited-retry-after.md` claim 4)
applies when `respectRetryAfterHeader` is true, its default; GitHub's "should not retry … until after that many
seconds" (claim 3) sits in the secondary-limit guidance; the AWS retry guide (claim 11) does not mention `Retry-After`,
as that file's Gaps say; Resilience4j's 5 s (`default-wait-cap.md` claim 1) was read at `main`, the release being
v2.4.0.

### Codebase facts

The repository facts the findings rest on were checked against `master` at `5f221ce`:

- `BudgetLedger.kt:124-144` (`pauseHint`): a throttled signal offers `Retry-After`, every exhausted reading's `resetIn`
  and, only when neither is present, the next boundary of the full windows (else the shortest) plus the guard, each
  capped by `maxPause`; a zero hint is dropped, so `Retry-After: 0` starts no pause. `:147-154` (`extendPause`) never
  shortens a pause; `:251` is the `pausedFor` that `utilisation()` reports.
- `WindowedCallBudget.kt:210-213` (`admitLocked`): a wait above `maxWait` is refused, `maxQueued` is checked next, and
  `Duration.INFINITE` sets no deadline.
- `CallBudget.kt:96`: `CallBudget.Unlimited.utilisation()` builds an empty reading with a zero pause on each call (a
  new instance, not a shared one as `rate-limited-retry-after.md` puts it; the recommendation's single line works for it
  either way).
- `UpstreamFailure.kt:38`: `RateLimited` has an `internal` constructor, and nothing in the module creates one yet.
- `webtools-http/api/webtools-http.api:132-135`: the dump records `CachePolicyKt` as the factory and its `$default`, the
  pair the probe found for a generic `Endpoint` factory.
- `RateSignals.kt:14-19`: `RateSignals`' `internal` constructor takes three parameters, so its same-named factory, with
  one, never competes with it inside the module.

### Did not hold in the build

No claim verified above failed in the build. One line of the summary below did not hold:

- **Building `Endpoint<T>`**, "so the KDoc and the Level 2 tests carry them": no test carries the `T : Any` bound or the
  invariance. A test cannot show that a form fails to compile, so `EndpointComponentTest` names the refused
  `Result<String?>` decoder in a comment only, and a change to the bound or the variance would pass `apiCheck` and every
  test ([`../consequences.md`](../consequences.md) §8).

The build contradicted nothing else here ([`../final-implementation.md`](../final-implementation.md) §3 and §4).

## Summary

### Building `Endpoint<T>`

- **What was found** ([`endpoint-factory-resolution.md`](endpoint-factory-resolution.md), measured on Kotlin 2.2.0
  and Gradle 9.7.1 with a `jvm()` target, a `js()` compile and a separate consumer module):
  - The module's own pair made generic — a final `Endpoint<T : Any>` with an `internal` constructor, a
    non-generic nested `@WebToolsDsl` `Builder` and a top-level `fun <T : Any> Endpoint(id, decode, configure =
    {})` — compiles for the JVM and for `js`, and every consumer call binds the factory:
    `Endpoint("id", decode)`, with a trailing configure lambda, with `::decodeHero`, with explicit
    `Endpoint<Hero>(…)`, with typed and untyped lambdas (F1).
  - `T` is fixed by the decoder alone, from a lambda, a callable reference or an expected type; a decoder of
    `Result<Hero?>` is refused by the `T : Any` bound (F2).
  - An invisible constructor never wins against the visible factory, even with the same argument list; inside
    the module, which sees both, a constructor whose parameter list matches the consumer's call would win, so the
    `internal` constructor takes the id, the decoder and every resolved setting (F3).
  - A miscall from a consumer (the decoder missing, or written as the trailing lambda) is reported against the
    `internal` constructor rather than against the factory, as for the module's existing pairs (F7).
  - The ABI dump records the factory and its `$default`, no `<init>`, and erased types only: it does not pin the
    `T : Any` bound or a variance, so the KDoc and the Level 2 tests carry them (F9).
  - `Endpoint<out T : Any>` compiles (F10); adding `out` later is source- and binary-compatible, removing it is not.
- **Recommended:** the module's pair made generic, as above, invariant in `T`, with an eight-parameter
  `internal` constructor (id, decoder, name, `ttl`, `missTtl`, cost, lane, misses). The KDoc says the decoder is
  the second argument and never the trailing lambda, that `T` is inferred from it, and what a miscall's
  diagnostic looks like. A Level 2 test calls the factory in each consumer form.
- **Declined:** a companion `operator fun invoke` (it resolves and infers too, explicit type arguments included,
  but adds a `Companion` class and field to the ABI and breaks the module's uniform shape); a public constructor
  with the trailing lambda (the clearest diagnostics, but its parameter list becomes binary API, so a later
  setting is a break); `out T` in 1.0.0 (nothing needs it yet, and it can be added compatibly later).
- **Where the sources disagree.** The stack-level research held that explicit type arguments on a class name
  resolve against the class and so rule out the companion form
  ([`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md), Gap 2). The probe shows
  Kotlin 2.2.0 routes them to `invoke`, as the specification's rule for property-like callables says (F4, F5).
  The measurement holds; it removes an objection to the companion form without changing the choice.

### What a rate-limited failure reports

- **What was found** ([`rate-limited-retry-after.md`](rate-limited-retry-after.md)):
  - `Retry-After` is the server's ask for the earliest follow-up and may be absent on a `429` (RFC 9110 §10.2.3,
    RFC 6585 §4); the practical reading is a minimum (F1, F2).
  - Server-facing clients report the server's number (JDA), client-side limiters report their own computed wait
    (.NET's lease metadata, Polly, Bucket4j), and where both meet the longer wins (Ktor's `HttpRequestRetry`
    delay); none reports a client pause in place of a real server ask without honouring the ask (F3, F4).
  - In this stack the budget's pause and the server's ask differ in both directions: the pause is longer when
    another hint or an earlier pause is, shorter when `Retry-After` exceeds `maxPause`, and zero under
    `CallBudget.Unlimited` (F6, F7).
- **Recommended:** `retryAfter = maxOf(signals.retryAfter ?: Duration.ZERO, budget.utilisation().pausedFor)`,
  read once, after `permit.complete(signals)`, in the loader that creates the failure, so every coalesced caller
  receives one instance. It is the only value for which "wait this long, then call" is neither refused by the
  budget's pause nor sent back to a server that asked for more time. `RateLimited`'s KDoc is reworded from "how
  long the server asked" to "how long to wait before calling again: the longer of the server's `Retry-After` and
  the budget's pause in force; zero when neither named a wait". A `utilisation()` that throws, which the port's
  contract does not rule out for a consumer's own budget, falls back to the server's value.
- **Declined:** the server's value alone (it sends a caller into `BudgetExhausted(Paused)` when a longer pause is
  in force, and reports zero for a `429` without `Retry-After` under a windowed budget); the pause alone (it drops
  a `Retry-After` above `maxPause` and is empty for any budget that does not pause).

### The default wait cap

- **What was found** ([`default-wait-cap.md`](default-wait-cap.md)):
  - The one comparable with a documented finite default, Resilience4j's rate limiter, waits up to 5 s; .NET and
    Polly queue nothing by default; Guava and Bucket4j take the cap as an argument; the Discord libraries and
    Octokit wait without a cap, against limits of seconds (F1 to F5).
  - Against an epoch-aligned window a refused call's wait is spread over the window, so a cap of a few seconds
    absorbs bursts over second-scale windows and short pauses and acts as fail-fast for minute and daily windows
    (F6, derived).
  - `Duration.INFINITE` would void two bounds the stack's design relies on — an abandoned shared load is bounded
    by the wait cap plus the call timeout, and the budget's re-plan by `maxQueued` and the wait cap — and would park
    a daily-window call until UTC midnight (F7). No cap can meet Discord's 3 s acknowledgement, because the
    transport's call timeout comes after the permit (F8).
- **Recommended:** `defaultMaxWait` of 5 seconds in both facades, a public `DEFAULT_MAX_WAIT` beside each
  configuration (the pattern of `CachePolicy.DEFAULT_MISS_TTL`), a negative value refused when the facade is built,
  and a KDoc that states the value, the worst-case latency (the cap plus the call timeout), the meaning of `ZERO`
  and `INFINITE`, and that a shared cache load waits with the cap of the caller that started it.
- **Declined:** zero (it leaves the budget's queue unused by default and fails waits that would clear in a
  moment); `INFINITE` (above). The value 5 s rather than 2, 3 or 10 s is a judgement anchored on Resilience4j's
  default and on clearing a one-second window plus the one-second guard.

### Settling a permit after a transport failure

No outside evidence is needed: the transport's own table
([`../../65-okhttp-transport/plan.md`](../../65-okhttp-transport/plan.md) §12) says, failure by failure, whether
a request was written. Only `InvalidRequest` proves that nothing was sent. `RefusedByPolicy` (a refused redirect
target, a later hop), `Closed` (a close while a call is in flight), and `DnsFailed` and `ConnectFailed` (at a later
hop) can each follow a request a server received, and a failure carries no hop number. The facades therefore
release the permit for `InvalidRequest` only and leave it spent for every other failure: the budget's windows guard
what the provider counts, providers count refused requests
([`../../research/call-budget.md`](../../research/call-budget.md) F1), and the over-count is one permit per such
failure, on the safe side. The common refusals cost no permit at all, because the facades' own checks run before
the budget.

### Sizing the transport from the budget's burst

Cited, not researched again: OkHttp's call timeout starts only when a dispatcher thread runs a queued call, and
with one transport per upstream host the per-host limit is the one that binds
([`../../research/http-transport.md`](../../research/http-transport.md) F7 and F9, both re-checked in the verification above). A
permit the budget granted that waits in OkHttp's queue would wait outside both the call timeout and the wait cap.
`UpstreamClient`'s owning factory therefore raises both `maxConcurrentRequests` and
`maxConcurrentRequestsPerHost` to at least the budget's `maxBurst`; raising the total alone would leave a
single-host client at its per-host default. `UrlFetcher`'s owning factory keeps its policy's settings: an
`Untrusted` policy's concurrency caps are part of what makes it untrusted.

### Codebase blast radius

Surveyed directly at `5f221ce`:

- **New:** `Endpoint`, `CallSpec`, `FetchSpec`, `UpstreamClientConfig`, `UpstreamClient`, `UrlFetcherConfig` and
  `UrlFetcher` with their internal request assembly, loader and classification in `commonMain`; the two
  facades' owning factories in `jvmMain`; the fixture `LoopbackFetchers`, which builds a `UrlFetcher` over
  `LoopbackTransports` whose own request check is relaxed for loopback the same way, so a Level 3 test can run
  `UrlFetcher` over `mockwebserver3` with every other `Untrusted` rule live.
- **Changed:** `UpstreamFailure.kt` (the KDoc of `RateLimited`, whose `internal` constructor
  `UpstreamFailure.kt:38` nothing calls yet); `WebToolsDsl.kt`'s KDoc list and `WebToolsDslComponentTest`'s list
  (five new builders); `api/webtools-http.api`; `README.md`; `CHANGELOG.md`.
- **No caller exists.** No class in the repository composes a cache, a budget and a transport today; no build
  file changes; the fixtures add no dependency.
- **Names are free.** None of `Endpoint`, `CallSpec`, `FetchSpec`, `UpstreamClient`, `UrlFetcher`, their
  configurations or `LoopbackFetchers` exists in the module or its fixtures, and none is reserved by an earlier
  unit's plan.
- **The parts as built:** `CallBudget.acquire` and `withPermit` (`CallBudget.kt:62`, `WithPermit.kt:23`), the
  argument checks they throw for (`CallBudget.kt:14-17`), `ResponseCache.getOrLoad` and its negative-`maxAge`
  check (`ResponseCache.kt:50`, `:109`), `CacheKey`'s own sort (`CacheKey.kt:30`), the encoders
  (`UrlEncoding.kt:94`, `:101`), `UrlGuard.checkRequest` and `checkHop` (`UrlGuard.kt:82`, `:71`),
  `OutboundUrlPolicy.withSettings` (`OutboundUrlPolicy.kt:274`), `TransportControlledHeaders.ruleFor`
  (`TransportControlledHeaders.kt:23`), `WindowedCallBudget.maxBurst` (`WindowedCallBudget.kt:136`) and the
  transport's factory (`OkHttpTransport.kt:232`).
