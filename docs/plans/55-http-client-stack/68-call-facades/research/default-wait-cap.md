# How long should a call wait for a budget permit by default?

Research for [SpartanLaboratories/WebTools#68](https://github.com/SpartanLaboratories/WebTools/issues/68) (unit 9,
`call-facades`, of the HTTP client stack, [#55](https://github.com/SpartanLaboratories/WebTools/issues/55)).
Question 3 of [`README.md`](README.md).

## 1. Question

What should the default of `defaultMaxWait` be, the builder property of `UpstreamClient`'s and `UrlFetcher`'s
configuration that is passed as `maxWait` to `CallBudget.acquire(cost, lane, maxWait)` and that a caller overrides per call
(`CallSpec.maxWait`, `FetchSpec.maxWait`)? Candidates: `Duration.ZERO`, a short finite cap, `Duration.INFINITE`; and
whether the two facades share one default.

An answer has to meet these facts of the stack as built:

- `acquire` fails fast with `BudgetExhausted(wait, reason, window)` when the wait the call would need exceeds `maxWait`,
  charging nothing; `INFINITE` waits as long as the plan needs; zero fails unless the call fits now.
- With `WindowedCallBudget` the wait for a full window is the time to its next boundary plus the 1 s guard: up to ~60 s for
  a minute window, up to 24 h for a daily one; a server throttle pauses every lane for up to `maxPause` (24 h by default);
  at most `maxQueued` (1,000) waiters per lane.
- `CallBudget.Unlimited`, the facades' default budget, grants at once, so the default matters only with a windowed budget.
- The transport's call timeout (30 s `Trusted`, 15 s `Untrusted`) starts after the permit, so worst-case latency is
  `maxWait` plus the call timeout.
- A `GET` takes its permit inside the response cache's loader; N coalesced callers share one load, which waits with the
  first caller's `maxWait`, and the wasted work of an abandoned load is bounded by `maxWait` plus the call timeout
  ([`../../architecture.md`](../../architecture.md) §6.6, §12).

## 2. Bottom line

- **Default `defaultMaxWait` to a short finite cap: 5 seconds, the same value in both facades.** One shared public
  constant, stated in the KDoc of both builders, with the sentence that a caller who wants fail-fast sets
  `Duration.ZERO` and one who wants to queue sets a longer cap or `Duration.INFINITE`.
- **Why finite and not infinite.** An unbounded default parks a call (and, through the cache, a shared load nobody may
  still want) for as long as the plan needs: a full daily window is a wait to the next UTC midnight, a throttle pause may
  last `maxPause` (24 h). Comparable clients that wait without bound (Guava `acquire`, JDA, Discord4J, Kord, Octokit's
  throttling plugin) pace against limits measured in seconds; none of them has a daily window in front of its default.
- **Why not zero.** It makes the supplied budget's queue dead code by default, and it fails a call that would clear in
  milliseconds (a burst over a second-scale window) with an error the consumer must then retry by hand. The only
  comparable with a documented finite default, Resilience4j's `RateLimiterConfig.timeoutDuration`, uses 5 s.
- **Why 5 s changes little for minute and daily windows.** A call refused by a minute window waits a uniformly spread 0 to
  ~61 s, so a 5 s cap absorbs about 8 % of them and fails the rest at once with the exact wait; against a daily window it
  fails every time. The cap therefore does real work only against second-scale windows and short pauses, which is where
  waiting is the right behaviour, and it bounds an interactive call to `5 s + call timeout`.
- **One default serves both facades.** `UrlFetcher` defaults to `Unlimited`, where the value is never read; when a
  consumer supplies a windowed budget it is a politeness limiter for the same kind of wait.
- **Changing it later is a behaviour change, not a compile break.** Raise it and calls that failed fast now park; lower it
  and calls that were absorbed now fail. State the value in the KDoc as part of the contract and change it only with a
  CHANGELOG entry in a release that signals a behaviour change.

## 3. Findings

Tiers: **documented** (stated in an official doc or KDoc), **practice** (what the code of a mature library visibly does),
**measured** (read off this repository's code), **derived** (computed from the other findings).

### F1. Resilience4j's rate limiter blocks up to a finite default of 5 s, then rejects (documented)

`RateLimiterConfig.Builder` initialises `timeoutDuration = Duration.ofSeconds(5)` and documents "Configures the default wait
for permission duration. Default value is 5 seconds." `RateLimiter.acquirePermission()` "Maximum wait time is
`RateLimiterConfig.getTimeoutDuration()`" and returns `false` when the wait elapsed; the decorators then throw
`RequestNotPermitted`. The reference page lists `timeoutDuration` default `5 [s]` ("The default wait time a thread waits for
a permission"). `limitRefreshPeriod` defaults to 500 ns and `limitForPeriod` to 50, i.e. the default limiter's waits are
sub-microsecond to seconds, not window-long. Source: Resilience4j `main` at `aa9aacb` (2026-10-30; latest release v2.4.0).

### F2. Guava's `RateLimiter` has the same fail-fast shape as `CallBudget`, with the cap supplied by the caller (documented)

`acquire()` blocks "until the request can be granted"; `tryAcquire(Duration timeout)` "returns `false` immediately
(without waiting) if the permit would not have been granted before the timeout expired", and "Negative values are treated
as zero". No default timeout exists: the caller picks blocking or capped. Guava `main` at `ed0921b` (latest release
v33.8.0).

### F3. Bucket4j offers an unbounded and a capped blocking call and no default cap (documented)

`BlockingBucket.consume(numTokens, strategy)` reserves tokens and blocks "for a time required to close deficit";
`tryConsume(numTokens, maxWaitTimeNanos, strategy)` reserves and blocks only when "deficit can be closed in period of time
less then `maxWaitTimeNanos`", and returns `false` otherwise. The cap is a required argument. A thread interrupted while
blocked does not get its tokens back, whereas `CallBudget` refunds a cancelled wait. Bucket4j `main` at `eca7297` (latest
release 8.22.0).

### F4. .NET and Polly queue nothing by default, and bound a wait by queue size and cancellation, never by time (documented)

`System.Threading.RateLimiting` options (`ConcurrencyLimiterOptions`, `FixedWindowRateLimiterOptions`) have `QueueLimit` as
an `int` property with no initialiser (so `0`: no queued acquisition), `QueueProcessingOrder.OldestFirst` by default.
`RateLimiter.AcquireAsync(permitCount, cancellationToken)` takes no time cap: "Wait until the requested permits are
available or permits can no longer be acquired", the only abort being the token. Polly's rate-limiter strategy builds its
default limiter with `PermitLimit` 1000 and `QueueLimit` 0, and rejects with `RateLimiterRejectedException` (with an
optional `RetryAfter`). Both are server-side shapes (shed load, protect a service) rather than client-side shapes
(stay under someone else's quota). dotnet/runtime `main` at `9789d45`; Polly 8.9.0 (`main` at `289f298`).

### F5. Discord libraries wait without a cap and leave the deadline to the caller (practice)

- **JDA:** a `RestAction` queues behind its bucket and runs when the rate limit allows; the timeout is opt-in
  ("`setDefaultTimeout` ... This will use no timeout unless specified otherwise. If the request doesn't get executed within
  the specified timeout it will fail"; per action `timeout(10, TimeUnit.SECONDS)`). JDA `7c90001` (latest release 6.7.0).
- **Kord:** `RequestRateLimiter.await(request)` is a `suspend` function; the sequential limiter "will suspend to adhere to
  global and bucket rate limits" using `delay`; the caller bounds it with `withTimeout` if it wants. Kord `db459ec`
  (latest release 0.18.1).
- **Discord4J:** a reactive pipeline whose `RouterOptions`, `RequestStream` and `BucketGlobalRateLimiter` hold no
  wait-cap option (`BucketGlobalRateLimiter.create(capacity, refillPeriod, scheduler)`, default 50 per 1 s); backpressure is
  the Reactor subscriber's, cancellation the caller's. Discord4J `7c292f4` (latest release 3.3.3). That the code has no cap
  is read from absence in these files.
- **Octokit `plugin-throttling`:** paces with Bottleneck groups (`minTime` 1 s between writes, 2 s between searches, 3 s
  between notification-triggering writes) with no queue timeout, and for an actual rate-limit response hands
  `retryAfter` to a mandatory `onRateLimit` / `onSecondaryRateLimit` callback that returns whether to retry; the secondary
  limit falls back to `fallbackSecondaryRateRetryAfter` 60 s. `plugin-throttling.js` `eb4215e` (latest release v11.0.5).

The common property: these waits are bounded by construction (per-route buckets and global limits of seconds, with
Discord's global limit 50 per second), so an unbounded wait cannot become a day-long one. Octokit's long waits go through
a callback the consumer must write, not through a silent default.

### F6. A fixed-window refusal wait is spread uniformly over the window, so a cap's reach depends on the window length (derived)

A call refused by an epoch-aligned fixed window of period `P` arrives at a uniformly random point of the period (a burst
crosses the limit at an arbitrary moment), so its wait to the next boundary, plus the guard `g` (1 s), is spread over
`(g, P + g]`. A cap `C` absorbs the fraction about `(C - g) / P` of those: for `P` = 60 s, about 7 % at `C` = 5 s and 2 % at
2 s; for `P` = 1 s, all of them at any `C` of 2 s or more; for a daily window, none. A throttle pause of `Retry-After` `r`
is a wait of `r` minus the time elapsed. So a short finite cap behaves as fail-fast for windows of a minute and longer and
as a wait for second-scale windows and short pauses. Basis: the window rule in `WindowedCallBudget` (epoch-aligned fixed
windows, boundary guard, first come first served; `admitLocked` refuses when `wait > maxWait`).

### F7. `INFINITE` voids the bounds the stack's design relies on (measured)

`WindowedCallBudget.admitLocked` queues a call whose `wait` is within `maxWait` and, for `Duration.INFINITE`, sets no
deadline, bounded only by `maxQueued` (1,000 per lane) and `maxPause`. The architecture states two bounds that assume a
finite cap: the abandoned shared load is bounded by "`maxWait` plus the call timeout", and the budget's re-plan on cancel or
completion is "bounded by `maxQueued` and the wait cap" (§10 for the first, §12 for the second). With `INFINITE` the first is bounded by the longest
window or pause (up to 24 h) and a hard window leaves a coroutine parked until its boundary. A cancelled waiter is refunded,
so an impatient caller cleans up after itself, but a coalesced load run to completion by the cache is not the caller's
coroutine and is not cancelled by one joiner's `withTimeoutOrNull`.

### F8. The transport timeout does not cover the wait; the 3 s Discord acknowledgement cannot be met by a cap (documented, derived)

Discord: a bot "must send an initial response within 3 seconds of receiving the event"; a deferred response (callback type
5, "ACK an interaction and edit a response later, the user sees a loading state") then leaves the interaction token valid
for "15 minutes" for follow-ups. Docs at docs.discord.com, read 2026-11-05. A facade call is at least one network round
trip with a 15 to 30 s call timeout after the permit, so no `maxWait` default can promise the 3 s; an interactive command
that must be acknowledged defers first, and the cap then governs how long the follow-up waits. The 3 s is therefore no
reason to push the default below the second-scale window cases of F6.

### F9. One default for both facades is consistent (derived)

`UrlFetcher` fetches administrator-supplied URLs and defaults to `CallBudget.Unlimited`, so the default is read only when a
consumer supplies a budget, and then the budget's role is the same (stay under a quota or be polite to a host) and its
waits have the same shape as in `UpstreamClient`. Nothing in `FetchSpec` (`cost`, `lane`, `maxWait`) calls for a different
cap. Two defaults would be two numbers for a consumer to remember and for the KDoc to explain, for no case that a per-call
override does not already cover.

## 4. How comparable projects do it

| Project (version) | Blocking primitive | Default wait before giving up | Where the cap lives | Source |
|---|---|---|---|---|
| Resilience4j (`main` `aa9aacb`; release v2.4.0) | `RateLimiter.acquirePermission()` | **5 s** (`timeoutDuration`), then `RequestNotPermitted` | config, overridable per limiter | `RateLimiterConfig.Builder` |
| Guava `RateLimiter` (`ed0921b`; v33.8.0) | `acquire()` blocks; `tryAcquire(Duration)` | none: `acquire` unbounded; `tryAcquire()` zero | method argument | `RateLimiter.tryAcquire` |
| Bucket4j (`eca7297`; 8.22.0) | `BlockingBucket.consume` / `tryConsume(n, maxWait)` | none: `consume` unbounded; `tryConsume` takes a required cap | method argument | `BlockingBucket` |
| .NET `System.Threading.RateLimiting` (runtime `9789d45`) | `AcquireAsync(permits, ct)` | **no time cap**; queue size `QueueLimit` = 0, so no queueing | options (size) and `CancellationToken` | `ConcurrencyLimiterOptions`, `RateLimiter.AcquireAsync` |
| Polly rate limiter (8.9.0) | strategy over a `RateLimiter` | **no wait**: `QueueLimit` 0, rejects with `RateLimiterRejectedException` | limiter options | `RateLimiterStrategyOptions` |
| JDA (`7c90001`; 6.7.0) | `RestAction.queue()` behind a bucket | unbounded; opt-in `timeout(...)` / `setDefaultTimeout` | per action or global | `RestAction` |
| Kord (`db459ec`; 0.18.1) | `suspend RequestRateLimiter.await` | unbounded (`delay`); caller uses `withTimeout` | caller's coroutine | `ExclusionRequestRateLimiter` |
| Discord4J (`7c292f4`; 3.3.3) | reactive `RequestStream` | unbounded (no option found) | caller's subscription | `RouterOptions`, `RequestStream` |
| Octokit `plugin-throttling` (`eb4215e`; v11.0.5) | Bottleneck groups | unbounded pacing (seconds); on a real limit a mandatory callback decides the retry | callback | `README`, `src/index.ts` |

Reading across: finite default (Resilience4j), zero by default (.NET, Polly), unbounded by default with a caller-side
deadline (the Discord libraries, Octokit's pacing), and no default at all (Guava, Bucket4j). The axis is what bounds the
wait: libraries that wait unbounded do so because the limit they wait on is small and known; libraries that hold a
caller's thread or a service's capacity cap it. This stack is the second kind when the budget has minute or daily windows.

## 5. What each candidate means for this stack

`W` is the wait the budget would need. Cap values: zero, 5 s, `INFINITE`. Call timeout 30 s `Trusted` (15 s `Untrusted`).

| Scenario | `Duration.ZERO` | 5 s | `Duration.INFINITE` |
|---|---|---|---|
| Burst of N+1 into an N-per-minute window (`W` 0 to ~61 s, mean ~30 s) | the extra call fails at once with its exact `W`; the consumer decides | same, except when `W` <= 5 s (about 8 % of arrivals), where it waits and succeeds | waits up to ~61 s, then up to 30 s for the call: an interactive caller can wait 91 s and sees no signal |
| Burst over a second-scale window (`W` about 0.1 to 2 s) | fails although it would clear almost at once | waits and succeeds | waits and succeeds |
| Call during a 30 s server pause (`Retry-After: 30`) | fails at once with `Paused` and the remaining time | same unless 5 s or less remain | parks for the rest of the pause; a 24 h `Retry-After` parks for 24 h |
| Coalesced load whose first caller has a short cap | the load fails at once; every joiner receives the one failure | same, bounded by 5 s plus call timeout | the load parks; a joiner's `withTimeoutOrNull` frees only that joiner, and the load itself runs on for as long as the plan needs |
| Background tracker on its own short-cap lane | irrelevant: it sets its cap | irrelevant | a tracker call that forgets its cap parks (1,000 per lane) instead of skipping its cycle |
| Interactive command (a person waits) | nothing waits, so the person sees an error whenever the budget is momentarily full | worst case 5 s plus call; the exact wait of anything longer is reported at once | unbounded latency |
| Daily window exhausted | fails at once with `W` up to 24 h | same | parks until UTC midnight, or until `maxQueued` is reached and further calls fail with `QueueFull` |
| Easiest to reason about | highest: no call ever waits | high: one number, one sentence; behaves as fail-fast beyond it | lowest: the latency depends on every window and pause configured |

A call that does nothing but fail when the budget is momentarily full (zero) is the easiest default to describe and the one
that most needs the consumer to configure: every consumer with a per-second or per-burst limit writes the same
`maxWait = 2.seconds` on every call. A cap of 5 s keeps the same fail-fast behaviour for the windows where waiting is
long and takes the common micro-burst case out of the consumer's hands.

## 6. Pitfalls and regrets

- **A silent long wait on an interactive path.** `INFINITE` (or a cap of tens of seconds) combines with the call timeout
  to give a latency nobody configured; the Discord libraries avoid it by leaving the deadline to the caller (`withTimeout`,
  `timeout(...)`), which a default of `INFINITE` here would also require of every consumer, per call.
- **Zero hides the budget's queue.** If the default never queues, the supplied budget's first-come-first-served queue,
  lane floors and `maxQueued` are exercised only by consumers who found the per-call `maxWait`; the common case then needs
  per-call configuration.
- **Retrying a fail-fast error by hand reproduces the wait badly.** A consumer that catches `BudgetExhausted(wait)` and
  sleeps `wait` loses the budget's first-come-first-served order (the consumer's retry joins the back) and spends a permit
  attempt on a window that is still full. A small cap absorbing short waits inside the budget is the better place.
- **A cap shorter than the guard plus the window period is a fail-fast in disguise** (F6), so a consumer who configures a
  second-scale window and leaves a 1 s cap sees failures at every window edge. The default has to clear one second-scale
  window plus the guard.
- **A shared load takes the first caller's cap** (§12). A uniform default makes joiners agree; per-call overrides still let
  a joiner see a failure derived from another caller's smaller cap, so the KDoc of `CallSpec.maxWait` says the cap of the
  caller that starts the load applies to a shared one.
- **Interrupting a blocked call can lose the permit** in Bucket4j (F3); `CallBudget` refunds a cancelled wait, so nothing
  here needs a reminder in the default.

## 7. Recommendation for this repository

1. `defaultMaxWait` defaults to **5 seconds** in both `UpstreamClient`'s and `UrlFetcher`'s configuration, expressed as one
   public constant (the pattern of `CachePolicy.DEFAULT_MISS_TTL`, a documented public `val` beside the builder property
   it seeds), for example `DEFAULT_MAX_WAIT: Duration = 5.seconds`, with no defaulted parameter on public API: the builder
   property is `public var defaultMaxWait: Duration = DEFAULT_MAX_WAIT`.
2. The per-call `CallSpec.maxWait` / `FetchSpec.maxWait` falls back to `defaultMaxWait` when unset; a caller who wants
   fail-fast sets `Duration.ZERO`, a batch caller `Duration.INFINITE`. The facade validates a negative `defaultMaxWait`
   at build time with `IllegalArgumentException` (the budget would throw on the first call anyway).
3. KDoc, for the builder property: "The longest a call waits for a budget permit before it fails with
   `HttpFailure.BudgetExhausted`, which carries the wait the call would have needed. Defaults to 5 seconds
   (`DEFAULT_MAX_WAIT`), long enough to ride out a burst over a window of a few seconds and short enough that a person
   waiting on the call sees the failure before the transport's call timeout is added to it: a call's worst-case latency is
   this value plus the call timeout. A wait for a window of a minute or longer, or for a server-requested pause, is
   normally longer and fails at once. Use `Duration.ZERO` to never wait and `Duration.INFINITE` to wait as long as the plan
   needs (up to the budget's pause cap). A response-cache load shared by several callers waits with the cap of the caller
   that started it. Meaningful only with a budget that waits; `CallBudget.Unlimited` grants at once."
4. Reject `INFINITE` as the default because it removes the bound the abandoned-load argument depends on (F7) and parks a
   daily-window call until midnight. Reject zero because it leaves the budget's queue unused by default and the sub-second
   to second-scale micro-burst, the case a client-side limiter exists to smooth, to every consumer's per-call
   configuration. Prefer 5 s over 2 or 3 s because a cap must clear a one-second window plus the one-second guard with
   room (F6) and because no cap can promise Discord's 3 s acknowledgement (F8); a bot that needs it defers first and sets
   its own per-call cap.
5. A consumer that depended on the default sees a behaviour change when it moves: raising it turns fast failures into
   parked calls; lowering it turns absorbed waits into failures. It is source- and binary-compatible, so the value is
   stated in the KDoc and the CHANGELOG, covered by the module's compatibility rules like any documented default, and
   changed only in a release whose notes say so. A consumer that needs a stable behaviour sets `defaultMaxWait`
   explicitly.

## 8. Load-bearing claims

1. **Resilience4j's default wait is 5 s.** "Configures the default wait for permission duration. Default value is 5
   seconds." (`RateLimiterConfig.Builder.timeoutDuration`); the initialiser is `private Duration timeoutDuration =
   Duration.ofSeconds(5);`. Source:
   <https://github.com/resilience4j/resilience4j/blob/aa9aacb8cb5333f2dee2590ff07f05f2c942d602/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/RateLimiterConfig.java>
   (`main`, commit `aa9aacb`, 2026-10-30). Reference page: <https://resilience4j.readme.io/docs/ratelimiter>
   (`timeoutDuration`, default `5 [s]`, "The default wait time a thread waits for a permission"; read 2026-11-05).
2. **Resilience4j's `acquirePermission` is capped by that duration and returns `false` on expiry.** "Acquires a permission
   from this rate limiter, blocking until one is available, or the thread is interrupted. Maximum wait time is
   `RateLimiterConfig#getTimeoutDuration()`" ... "`false` if waiting timeoutDuration elapsed before a permit was
   acquired". Source: `RateLimiter.acquirePermission` in the same commit,
   <https://github.com/resilience4j/resilience4j/blob/aa9aacb8cb5333f2dee2590ff07f05f2c942d602/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/RateLimiter.java>.
3. **Guava `tryAcquire(timeout)` fails without waiting when the wait would exceed the timeout.** "Acquires a permit from
   this `RateLimiter` if it can be obtained without exceeding the specified `timeout`, or returns `false` immediately
   (without waiting) if the permit would not have been granted before the timeout expired." and `acquire()`: "blocking
   until the request can be granted". Source:
   <https://github.com/google/guava/blob/ed0921b/guava/src/com/google/common/util/concurrent/RateLimiter.java> (`main`;
   latest release v33.8.0).
4. **Bucket4j's capped blocking call reserves only when the deficit closes within the cap; the cap is an argument.**
   "If bucket has no enough tokens, but deficit can be closed in period of time less then `maxWaitTimeNanos` nanoseconds,
   then tokens consumed(reserved in fair manner) from bucket and current thread blocked for a time required to close
   deficit". Source:
   <https://github.com/bucket4j/bucket4j/blob/eca7297808dbe2aa01127ba3a4d224786153b701/bucket4j-core/src/main/java/io/github/bucket4j/BlockingBucket.java>
   (`main`, commit `eca7297`; latest release 8.22.0).
5. **.NET limiter options queue nothing by default and `AcquireAsync` has no time cap.** `QueueLimit`: "Maximum number of
   permits that can be queued concurrently. Must be set to a value >= 0 by the time these options are passed to the
   constructor" (an `int` property with no initialiser) and `AcquireAsync`: "Wait until the requested permits are available
   or permits can no longer be acquired." with the parameter "Optional token to allow canceling a queued request for
   permits." Sources:
   <https://github.com/dotnet/runtime/blob/9789d45f9adabc3bcfaf5aecd52c794f8eb9cae5/src/libraries/System.Threading.RateLimiting/src/System/Threading/RateLimiting/ConcurrencyLimiterOptions.cs>
   and `.../RateLimiter.cs` in the same directory (`main`, commit `9789d45`, read 2026-11-05).
6. **Polly's default limiter has `QueueLimit` 0 and rejects.** "`ConcurrencyLimiterOptions.PermitLimit` defaults to 1000.
   `ConcurrencyLimiterOptions.QueueLimit` defaults to 0." Source:
   <https://github.com/App-vNext/Polly/blob/289f29808f70f49d3d46de57f42fcd1139bc2bd0/src/Polly.RateLimiting/RateLimiterStrategyOptions.cs>
   (`main`; latest release 8.9.0); docs: <https://www.pollydocs.org/strategies/rate-limiter.html> ("The `OnRejected`
   user-provided delegate is called just before the strategy throws the `RateLimiterRejectedException`.").
7. **JDA waits without a timeout unless one is set.** "Default timeout to apply to every RestAction. This will use no
   timeout unless specified otherwise. If the request doesn't get executed within the specified timeout it will fail."
   Source:
   <https://github.com/discord-jda/JDA/blob/7c900019ca511a994087320ce98d19cd79a2c4b0/src/main/java/net/dv8tion/jda/api/requests/RestAction.java>
   (`master`, commit `7c90001`; latest release 6.7.0).
8. **Kord suspends for as long as the rate limit requires.** "A [RequestRateLimiter] that handles all [requests] in
   sequential order, minimizing the possibility of rate limits. Requests are handled in call order and will suspend to
   adhere to global and bucket rate limits." Source:
   <https://github.com/kordlib/kord/blob/db459ec8fc140e42c08997a2dcd21491660dba3d/rest/src/commonMain/kotlin/ratelimit/ExclusionRequestRateLimiter.kt>
   (commit `db459ec`; latest release 0.18.1).
9. **Octokit's throttling plugin hands the retry decision for a real limit to the consumer, and paces with Bottleneck.**
   "The `throttle.onSecondaryRateLimit` and `throttle.onRateLimit` options are required. Return `true` to automatically
   retry the request after `retryAfter` seconds." and "Guarantee at least 1000ms between writes". Sources:
   <https://github.com/octokit/plugin-throttling.js/blob/eb4215edcd97f20ade800b18d964bf798e0d70b7/README.md> and
   `src/wrap-request.ts` in the same commit (latest release v11.0.5).
10. **Discord requires the first response within 3 s and offers a deferred acknowledgement.** "you must send an initial
    response within 3 seconds of receiving the event"; callback type 5: "ACK an interaction and edit a response later, the
    user sees a loading state"; the token is valid for "15 minutes" for follow-up messages. Source:
    <https://docs.discord.com/developers/interactions/receiving-and-responding> (read 2026-11-05).
11. **Discord4J holds no wait-cap option in its REST rate limiter.** `BucketGlobalRateLimiter.create(int capacity, Duration
    refillPeriod, Scheduler delayScheduler)` with the default `new BucketGlobalRateLimiter(50, Duration.ofSeconds(1),
    Schedulers.parallel())`; no timeout parameter in `RouterOptions` or `RequestStream`. Source:
    <https://github.com/Discord4J/Discord4J/blob/7c292f43dfea8b880b531c235a080374335092cf/rest/src/main/java/discord4j/rest/request/BucketGlobalRateLimiter.java>
    (commit `7c292f4`; latest release 3.3.3). The "no cap" half of this claim is an absence read from the files (see Gaps).

## 9. Gaps

- **Discord4J has no wait-cap** is inferred from searching `RouterOptions`, `RequestStream` and `BucketGlobalRateLimiter`
  for `timeout`, `Duration` and capacity options and finding none; its reactive operators could carry a cap I did not trace.
- **Resilience4j's 5 s at release v2.4.0.** Read at `main` (`aa9aacb`, 2026-10-30), not at the v2.4.0 tag; the shallow clone
  carried no tags. The reference page shows the same default and states no version.
- **Library versions** in the table are the `main` commit I read plus the latest release name from the GitHub releases API;
  the code at the release tag may differ in places I did not read.
- **No measurement of real consumers' waits.** The 8 % / 2 % / 100 % figures of F6 are derived from the uniform-arrival
  assumption; a burst arriving just after a boundary (an hourly poller aligned to the minute) has a full window's wait and a
  poller that fires on the boundary has almost none. The recommendation does not depend on the exact figure, only on the
  window length relative to the cap.
- **5 s versus another small number** (2 s, 3 s, 10 s) is a judgement from F1, F6 and F8; no source states the best value
  for a client-side windowed budget. R4J's 5 s is the only documented finite default among the comparables.
- **The first consumer's tracker and interactive caps** are not stated in the stack's documents; the recommendation holds for any
  values they choose, since a per-call cap overrides the default.
- **Whether a Discord interaction in the first consumer defers before it calls a facade** is not established here; the
  recommendation does not rely on it.
- **A hard window under `INFINITE`:** read from the window code's `admitLocked` that a hard window plans a wait like any
  other and that `INFINITE` queues it; not exercised by a test during this research.
