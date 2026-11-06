# What retry delay a rate-limited failure carries

[SpartanLaboratories/WebTools#68](https://github.com/SpartanLaboratories/WebTools/issues/68), the call facades
of the HTTP client stack ([#55](https://github.com/SpartanLaboratories/WebTools/issues/55)). Question 2 of
[`README.md`](README.md).

## Question

What should `UpstreamFailure.RateLimited.retryAfter` report when `UpstreamClient.call` or `UrlFetcher.fetch`
meets a throttle (a `429`, or a response the configured `RateSignalsReader` reports as throttled), after the
facade has handed the response's `RateSignals` to `permit.complete(signals)` and can read
`budget.utilisation().pausedFor`?

Requirements an answer must meet: the value is a non-null, non-negative `Duration`; while a budget pause is in
force every call fails fast with `BudgetExhausted(Paused)` and never reaches the transport; one failure
instance reaches every coalesced waiter; a pause never shortens; the facades never retry; a caller backs off by
the value without parsing `Retry-After` itself; `CallBudget.Unlimited` never pauses.

Candidates: **(a)** the server's `Retry-After` as the reader parsed it (`signals.retryAfter`, zero when
absent); **(b)** the pause in force, `utilisation().pausedFor`, read after `complete`; **(c)** the longer of
the two.

## Bottom line

- **Report (c): `maxOf(signals.retryAfter ?: Duration.ZERO, budget.utilisation().pausedFor)`**, with
  `utilisation()` read once, after `complete(signals)`, only on the throttle path. It is the only candidate
  that is safe in every edge case below: a caller that waits exactly that long is neither refused by the
  budget's pause nor sent back to a server that said to wait longer.
- (a) fails when another call's earlier pause is longer than this response's ask (the caller waits, calls, and
  gets `BudgetExhausted(Paused)` without reaching the server) and it reports nothing for a `429` with no
  `Retry-After` under a windowed budget. (b) fails when `Retry-After` exceeds the budget's `maxPause` (the
  caller waits the capped pause, the budget lets it through, and the server throttles again) and when the
  budget is `Unlimited` or a consumer's own implementation that never reports a pause.
- (c) degrades gracefully: with a budget that reports no pause it equals (a), and with no server hint it equals
  (b). Mature clients that combine a client-side delay with the server's ask take the longer of the two
  (Ktor's `HttpRequestRetry.delayMillis`); the server's `Retry-After` is a lower bound the client may exceed
  but should not undercut.
- The name `retryAfter` and the message `retry in <retryAfter>` ask for "how long until a follow-up call is
  worth making". The KDoc should say that, not "how long the server asked": the field is the server's ask
  extended by the client's own pause.
- Cost is small and only on a throttle: one suspend `utilisation()` read under the budget's lock plus a small
  snapshot allocation. A concurrent extension of the pause between `complete` and the read can only make the
  value longer and still true.

## Findings

**F1 (documented). `Retry-After` is the server's ask for the earliest follow-up; HTTP says nothing about a
client waiting longer.** RFC 9110 §10.2.3: "Servers send the "Retry-After" header field to indicate how long
the user agent ought to wait before making a follow-up request." The value is "either an HTTP-date or a number
of seconds to delay after receiving the response". RFC 6585 §4 makes it optional on a `429`: responses "MAY
include a Retry-After header indicating how long to wait before making a new request", and "this specification
does not define how the origin server identifies the user, nor how it counts requests". Neither text discusses a
client that waits longer or shorter than the value. The practical reading, stated by GitHub's REST guidance, is
a minimum: "you should not retry your request until after that many seconds has elapsed", with a warning that
"Continuing to make requests while you are rate limited may result in the banning of your integration."
Applies to RFC 9110 (June 2022) and RFC 6585 (April 2012); GitHub documentation read 2026-11-06.

**F2 (documented). A `429` may carry no `Retry-After`, so the ask is absent in a normal case.** RFC 6585 §4
(F1). Octokit's throttling plugin substitutes a client-side value when GitHub's secondary-limit response lacks
the header ("make sure to set `retryAfter` to at least 60s by default") and derives a wait from
`x-ratelimit-reset` for the primary limit. A rate-limit error that reports only the server's header would be
empty exactly where callers most need a number.

**F3 (practice). Libraries that surface a wait report the delay after which a retry is worth making, not the
raw header.** JDA's `RateLimitedException.getRetryAfter()` is "the back-off delay in milliseconds that should
be respected before trying to query the route again"; Octokit's `onRateLimit(retryAfter, options, octokit,
retryCount)` receives a `retryAfter` it computes itself (reset time minus now, plus one second, floored at
zero); .NET's `MetadataName.RetryAfter` and Polly's `RateLimiterRejectedException.RetryAfter` carry a
limiter-computed wait ("the amount of time to wait before retrying again"); Bucket4j's
`ConsumptionProbe.getNanosToWaitForRefill()` is the computed wait until the request would fit. In none of them
is the field defined as a verbatim copy of an HTTP header. For limiters that sit on the client, the reported
number is the client's own computed wait, which is candidate (b)'s kind of value.

**F4 (practice). Where a client-side delay and the server's ask meet, the longer wins.** Ktor's
`HttpRequestRetryConfig.delayMillis` computes `maxOf(block(it), retryAfter ?: 0)` when
`respectRetryAfterHeader` is true, so the server's value raises the client's backoff but never lowers it. The
other direction (client undercuts the server) is what GitHub's guidance and RFC 9110 warn against. This is the
candidate-(c) rule, used by a mainstream Kotlin HTTP client. Applies to the Ktor source at the commit named
below.

**F5 (practice). Several clients never surface a computed throttle to the caller at all; they absorb it.**
Kord's `KtorRequestHandler` re-queues a `429` internally, Discord4J's `RateLimitRetryOperator` backs off by
`Retry-After` (or `X-RateLimit-Reset-After`) and retries, Octokit retries when `onRateLimit` returns `true`,
and AWS's retry strategies compute throttling backoff themselves (exponential, 1 s base and 20 s maximum in the
standard strategy). This stack's facades never retry, so they have no internal delay to apply; the failure is
where the delay is communicated. The closest analogues to an error that reports a wait are JDA, Polly and
.NET's lease metadata (F3).

**F6 (measured, from the code). The budget's pause and the server's ask differ in both directions.**
`BudgetLedger.pauseHint` takes the longest of `Retry-After`, every exhausted reading's `resetIn` and, with no
timing hint at all, the next window boundary plus the 1 s guard, each capped by `maxPause`; `extendPause` never
shortens. `WindowedCallBudget.utilisation()` reads `pause?.remaining(now)` under the budget's lock. So after
`complete(signals)`: `pausedFor` ≥ `min(retryAfter, maxPause)` less the time elapsed; `pausedFor` > `retryAfter`
when a reading's `resetIn`, an earlier pause or the boundary fallback is longer; `pausedFor` < `retryAfter`
when `retryAfter > maxPause`; and `pausedFor` is zero under `CallBudget.Unlimited` and in a consumer's budget
that reports none. This repository, commit 5f221ce.

**F7 (measured, from the code). The pause is a decaying remainder, the server's ask is a fixed length, and
both start at the moment of the response.** `pausedFor` is "the pause remaining", so reading it a few
microseconds after `complete` returns the pause's length minus that gap; `signals.retryAfter` is the length as
parsed. A caller that starts waiting after receiving the failure starts later than both, so waiting exactly the
reported value always outlasts a pause the value covers. A `Retry-After` given as an HTTP date is already
resolved to a `Duration` by `RetryAfter.parse` against the response's own `Date` (so local clock skew cancels),
and a `Retry-After` of zero yields no pause at all (a zero hint is dropped and, because the header was present,
the boundary fallback is skipped).

**F8 (derived). Which value avoids both a budget refusal and a second throttle.** A caller that waits exactly
`retryAfter` and calls again avoids a budget refusal only if `retryAfter` ≥ the pause remaining, and avoids a
second throttle only if `retryAfter` ≥ the server's ask. (a) satisfies the second always and the first only
when no longer pause is in force. (b) satisfies the first always and the second only when `Retry-After` ≤
`maxPause`. (c) satisfies both in every case in the table below. Neither condition guarantees the server then
accepts the call; the server may still be throttling for reasons it did not signal, so the KDoc promises a
minimum, not a success.

**F9 (derived). The reported value is a lower bound the consumer may exceed.** Because a `Retry-After` can be
as large as the reader's range allows (the parser's cap is year 9999) and the budget caps its pause at
`maxPause`, (c) may report a value far above `maxPause`. This is correct: the failure reports what a retry
needs, and the consumer decides whether to wait, give up or cap. Capping it inside the facade would reintroduce
the second-throttle case of (b), and the facade does not know the consumer's patience.

## How comparable projects do it

| Project (version or commit) | Type and member | What the value is | Source |
|---|---|---|---|
| JDA v6.7.0 | `RateLimitedException.getRetryAfter(): long` (ms) | The server's `Retry-After` seconds converted to ms (`Requester.parseRetry`: header, default `"0"`); zero when absent. Documented as the back-off that "should be respected". | [`RateLimitedException`](https://github.com/discord-jda/JDA/blob/v6.7.0/src/main/java/net/dv8tion/jda/api/exceptions/RateLimitedException.java), `Requester.parseRetry` in [`Requester.java`](https://github.com/discord-jda/JDA/blob/v6.7.0/src/main/java/net/dv8tion/jda/internal/requests/Requester.java) |
| Discord4J (commit 7c292f4) | `RateLimitRetryOperator.retry` | Server's `Retry-After` or `X-RateLimit-Reset-After`; used internally to delay the retry, not surfaced as a field. | [`RateLimitRetryOperator`](https://github.com/Discord4J/Discord4J/blob/7c292f43dfea8b880b531c235a080374335092cf/rest/src/main/java/discord4j/rest/request/RateLimitRetryOperator.java) |
| Kord (commit db459ec) | `KtorRequestHandler.handle`, `globalSuspensionPoint` | A `429` is handled internally (`response.isRateLimit -> handle(request)`); global suspension is `Retry-After` plus now. No wait reaches the caller. | [`KtorRequestHandler`](https://github.com/kordlib/kord/blob/db459ec8fc140e42c08997a2dcd21491660dba3d/rest/src/commonMain/kotlin/request/KtorRequestHandler.kt), [`HttpUtils`](https://github.com/kordlib/kord/blob/db459ec8fc140e42c08997a2dcd21491660dba3d/rest/src/commonMain/kotlin/request/HttpUtils.kt) |
| Octokit `plugin-throttling.js` (commit eb4215e) | `onRateLimit(retryAfter, options, octokit, retryCount)` | Computed: `ceil((x-ratelimit-reset - now)/1000) + 1`, floored at 0; for the secondary limit `Retry-After` else a 60 s fallback. "Return `true` to automatically retry the request after `retryAfter` seconds." | [`src/index.ts`](https://github.com/octokit/plugin-throttling.js/blob/eb4215edcd97f20ade800b18d964bf798e0d70b7/src/index.ts) |
| Ktor client (commit 6f3b9da) | `HttpRequestRetryConfig.delayMillis(respectRetryAfterHeader, block)` | `maxOf(block(retry), Retry-After in ms)`: the longer of the client backoff and the server's ask. | [`HttpRequestRetry.kt`](https://github.com/ktorio/ktor/blob/6f3b9da6d643ca1dea6ace1dcaf6f37f081daad3/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/HttpRequestRetry.kt) |
| .NET `System.Threading.RateLimiting` (docs for .NET 11, 2026-05) | `MetadataName.RetryAfter` read by `RateLimitLease.TryGetMetadata` | Limiter-computed: "Metadata put on a failed lease acquisition to specify when to retry acquiring a lease." The fixed-window limiter reports the next window boundary. | [`MetadataName.RetryAfter`](https://learn.microsoft.com/en-us/dotnet/api/system.threading.ratelimiting.metadataname.retryafter), [rate-limit middleware note](https://learn.microsoft.com/en-us/aspnet/core/performance/rate-limit) |
| Polly 8.9.0 | `RateLimiterRejectedException.RetryAfter: TimeSpan?` | Copied from the lease's `MetadataName.RetryAfter`; null by default. | [`RateLimiterRejectedException.cs`](https://github.com/App-vNext/Polly/blob/8.9.0/src/Polly.RateLimiting/RateLimiterRejectedException.cs) |
| Bucket4j (commit eca7297) | `ConsumptionProbe.getNanosToWaitForRefill(): long` | Zero when consumed, else the computed wait until the tokens are refilled. | [`ConsumptionProbe`](https://github.com/bucket4j/bucket4j/blob/eca7297808dbe2aa01127ba3a4d224786153b701/bucket4j-core/src/main/java/io/github/bucket4j/ConsumptionProbe.java) |
| Resilience4j (commit aa9aacb) | `RequestNotPermitted.getCausingRateLimiterName()` | No duration at all: the refusal names the limiter only. | [`RequestNotPermitted`](https://github.com/resilience4j/resilience4j/blob/aa9aacb8cb5333f2dee2590ff07f05f2c942d602/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/RequestNotPermitted.java) |
| AWS SDK for Java 2.x (docs, retry strategy page) | `StandardRetryStrategy.Builder#throttlingBackoffStrategy` | Client-side exponential backoff (1 s base, 20 s max) chosen by the strategy; the guide does not mention `Retry-After`. | [Retry behaviour guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/retry-strategy.html) |
| Stripe `stripe-java` (commit 02b8743) | none | No reference to `Retry-After` in `src/main`; retry decisions follow Stripe's own header. | searched `src/main` of the clone |
| OkHttp `RetryAndFollowUpInterceptor.retryAfter` | internal | Reads `Retry-After` only to decide whether a `503`/`408` retry is immediate (zero) or not; ignores an HTTP-date; reports nothing to a caller. | [`RetryAndFollowUpInterceptor.kt`](https://github.com/square/okhttp/blob/master/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt) |

Reading across the table: the server-facing clients (JDA, Discord4J, Octokit, Ktor) carry the server's number;
the client-side limiters (.NET, Polly, Bucket4j) carry the limiter's own computed wait; Octokit already blends
the two (a client fallback where the server is silent, a reset-derived value where the server only gives a
reset time); and Ktor takes the longer of a client delay and the server's ask. This stack's facade is both: the
`CallBudget` is a client-side limiter whose pause was just set from the server's signals. No surveyed project
reports a client-side pause in place of the server's ask on a real throttle without also honouring the ask
(F4).

## Edge cases

Values are what the failure reports, `R` = the server's `Retry-After` as the reader parsed it, `P` = `pausedFor`
read after `complete`. "Caller" = a consumer that waits exactly the reported value and then calls again.

| Case | (a) `R` or zero | (b) `P` | (c) `max(R, P)` | Outcome for a caller that waits the value |
|---|---|---|---|---|
| `429` with no `Retry-After`, `WindowedCallBudget` | zero | next boundary of the full windows (or the shortest window) plus guard | same as (b) | (a): called at once, refused with `BudgetExhausted(Paused)`; (b) and (c): lets the pause expire |
| `429` with no `Retry-After`, `CallBudget.Unlimited` | zero | zero | zero | no information anywhere; zero reads "no wait known", the caller applies its own backoff |
| `Retry-After` > `maxPause` (for example 48 h against 24 h) | 48 h | about 24 h (capped) | 48 h | (b): budget lets the call through after 24 h, the server throttles again; (a) and (c): waits the server's ask |
| An earlier pause (another call) longer than this response's ask | `R` | the earlier pause's remainder | the earlier pause's remainder | (a): waits `R`, refused with `BudgetExhausted(Paused)` and the transport is never reached; (b) and (c) cover it |
| `Retry-After` as an HTTP date | the date minus the response's `Date`, as a `Duration` | about `R` (or longer, as in the rows above) | the longer | all agree when nothing else is longer; a date in the past is zero and starts no pause |
| `Retry-After: 0` on a `429` | zero | zero, or an earlier pause's remainder | the same as (b) | no pause was started; (a) misses an earlier pause |
| Reader-reported throttle on a `503` with `Retry-After` | `R` (status stays 503) | at least `min(R, maxPause)` less elapsed | the longer | same as a `429` with the header |
| Coalesced load: many waiters share one failure | one value, fixed at creation | one value read once | one value read once | every waiter receives the same instance created at the moment of the throttle, so the value is exact for each; a waiter must not recompute it |
| Another call extends the pause between `complete` and the read | unaware | the longer remainder | the longer remainder | the value is still true at the read; a pause never shortens, so it cannot become an overstatement |
| Consumer's `CallBudget` that reports no pause | `R` | zero | `R` | (b) is empty and wrong for a real `Retry-After`; (c) falls back to (a) |
| `Retry-After` far beyond `maxPause` (up to the parser's cap) | the huge value | `maxPause` | the huge value | the consumer should cap its own wait; the failure reports what a retry needs |

Which value lets a caller that waits exactly the value avoid both a budget refusal and a second throttle:
only **(c)**. Which best matches the name `retryAfter`: also (c), read as "when a follow-up call is worth
making"; (a) matches the HTTP header's name literally but not what a caller needs after a pause is in force.

## Pitfalls and regrets

- **Reporting the header verbatim and discovering the local pause.** A caller trusting (a) gets a second
  failure of a different type (`BudgetExhausted(Paused)`) immediately after waiting, and its handler must then
  learn about a second wait. JDA, Discord4J and Kord avoid the situation only because their own rate limiter
  absorbs the `429` and holds later calls; this stack's budget produces refusals instead of holding callers.
- **Reporting only the pause and losing the ask.** (b) hides a `Retry-After` longer than `maxPause` and,
  worse, goes to zero for any budget that does not track pauses. A budget is replaceable (it is a port), so the
  facade cannot rely on its pause being faithful.
- **Fabricating a number for the silent case.** Octokit's 60 s fallback is a policy. This facade has no
  business inventing a delay beyond what the server and the configured budget say; the local budget's
  boundary-plus-guard pause already is the principled value when a window is configured, and zero otherwise.
- **Reading `pausedFor` before `complete`.** The pause is not yet in force; the value would miss the response's
  own signals. The read must follow `complete`.
- **Recomputing for coalesced waiters.** A waiter that reads `pausedFor` later gets a shorter value than the
  failure's creator; the value must be taken once in the loader where the failure is created.
- **A zero that means two things.** Zero means "no wait is known" (no `Retry-After`, no pause) and also "the
  server said zero". Both mean "no required wait", so one value serves both; the KDoc says so.
- **The extra read when cancelled.** `utilisation()` suspends on the budget's lock; a cancellation arriving
  during the read throws `CancellationException` from a call whose permit has already been completed. That is
  the correct outcome for a cancelled caller and costs nothing, because the permit is already settled.

## Recommendation for this repository

Report **(c)**. In both facades, on the throttle path only, after `permit.complete(signals)`:

```kotlin
val wait = maxOf(signals.retryAfter ?: Duration.ZERO, budget.utilisation().pausedFor)
UpstreamFailure.RateLimited(response.status, wait)
```

`CallBudget.Unlimited.utilisation()` returns a shared empty snapshot, so the same line serves the default
budget with no special case; a `429` without `Retry-After` under `Unlimited` reports zero. Take the value once
in the cache loader where the failure is built, so every coalesced waiter receives the same instance. Keep the
construction `internal`; the property stays a non-null, non-negative `Duration` (both operands are
non-negative, so `maxOf` is too).

Replace the KDoc on `RateLimited` (kept free of internal names):

```kotlin
/**
 * The server throttled the call.
 *
 * **Stable Core.** Created by the call facades only. Message:
 * `The server rate-limited the call (status <status>); retry in <retryAfter>`.
 *
 * @param status the response status, in 100..599.
 * @param retryAfter how long to wait before calling again: the longer of the server's `Retry-After` and the
 *   time the call budget stays paused because of this and earlier throttles, measured when the failure was
 *   created. A call made after waiting this long is not refused by the budget's pause, and does not go back
 *   to a server that asked for more time. Zero when neither the server nor the budget named a wait; the
 *   server may still throttle again. Not negative; may exceed the budget's longest pause.
 * @throws IllegalArgumentException if [status] is outside 100..599 or [retryAfter] is negative.
 */
```

and the property comment to `/** How long to wait before calling again; see the constructor's description. */`.
The class-level one-liner "The server throttled the call and said when to retry" should lose "and said when to
retry", since a `429` may carry no `Retry-After`.

Reasoning: the facade is the point where the server's ask and the client's own pause meet, and its callers
cannot see the pause or parse the header themselves. (c) is the only candidate for which "wait this long, then
call" is correct in every case, it is the same rule a mainstream Kotlin client applies when it merges a client
delay with `Retry-After`, and it never reports less than either source. (a) was rejected because it can
send a caller straight into a `BudgetExhausted(Paused)` refusal; (b) because it drops a server ask above
`maxPause` and is empty for any budget that does not pause. The cost is one lock acquisition and a snapshot
list of the windows on a path that already means a pause was just set and a server round trip was spent;
it is not measurable against the throttle itself. Tests (Level 2, virtual clock and fake transport) should
pin: `429` without header under a windowed budget; `Retry-After` larger than `maxPause`; a longer earlier pause;
a `503` with `Retry-After`; `Unlimited`; and a coalesced pair receiving one instance.

## Load-bearing claims

1. **Retry-After is the server's ask for a follow-up.** RFC 9110 §10.2.3: "Servers send the "Retry-After"
   header field to indicate how long the user agent ought to wait before making a follow-up request." Source:
   <https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3> (RFC 9110, June 2022).
2. **A `429` may omit it.** RFC 6585 §4: "The response representations SHOULD include details explaining the
   condition, and MAY include a Retry-After header indicating how long to wait before making a new request."
   Source: <https://www.rfc-editor.org/rfc/rfc6585.html#section-4> (RFC 6585, April 2012).
3. **Waiting the server's ask is a minimum, and ignoring it risks a ban.** GitHub REST rate-limit guidance:
   "If the `retry-after` response header is present, you should not retry your request until after that many
   seconds has elapsed." and "Continuing to make requests while you are rate limited may result in the banning
   of your integration." Source:
   <https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api> (read 2026-11-06).
4. **Ktor takes the longer of its backoff and `Retry-After`.** `HttpRequestRetry.kt`, `delayMillis`:
   `maxOf(block(it), retryAfter ?: 0)` where `retryAfter` is
   `response?.headers?.get(HttpHeaders.RetryAfter)?.toLongOrNull()?.times(1000)`. Source:
   <https://github.com/ktorio/ktor/blob/6f3b9da6d643ca1dea6ace1dcaf6f37f081daad3/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/HttpRequestRetry.kt>
   (commit 6f3b9da).
5. **JDA reports the server's value as the back-off to respect.** `RateLimitedException.getRetryAfter` javadoc:
   "The back-off delay in milliseconds that should be respected before trying to query the route again".
   Source:
   <https://github.com/discord-jda/JDA/blob/v6.7.0/src/main/java/net/dv8tion/jda/api/exceptions/RateLimitedException.java>
   (v6.7.0); the value comes from `Requester.parseRetry`, which reads
   `response.header(RestRateLimiter.RETRY_AFTER_HEADER, "0")` (same tag,
   `src/main/java/net/dv8tion/jda/internal/requests/Requester.java`).
6. **Octokit computes its `retryAfter` and substitutes a fallback.** `src/index.ts`: "// Add one second so we
   retry _after_ the reset time" and "// but is always present after 2-3s, so make sure to set `retryAfter` to
   at least 60s by default." Source:
   <https://github.com/octokit/plugin-throttling.js/blob/eb4215edcd97f20ade800b18d964bf798e0d70b7/src/index.ts>
   (commit eb4215e). README: "Return `true` to automatically retry the request after `retryAfter` seconds."
   Source: <https://github.com/octokit/plugin-throttling.js/blob/main/README.md> (read 2026-11-06).
7. **.NET and Polly carry the limiter's computed wait.** `MetadataName.RetryAfter`: "Metadata put on a failed
   lease acquisition to specify when to retry acquiring a lease. For example, used in TokenBucketRateLimiter
   which periodically replenishes leases." Source:
   <https://learn.microsoft.com/en-us/dotnet/api/system.threading.ratelimiting.metadataname.retryafter> (.NET 11
   docs, updated 2026-05-27). Polly `RateLimiterRejectedException.RetryAfter`: "Gets the amount of time to wait
   before retrying again." and "This value was retrieved from the RateLimitLease by reading the
   MetadataName.RetryAfter. Defaults to null." Source:
   <https://github.com/App-vNext/Polly/blob/8.9.0/src/Polly.RateLimiting/RateLimiterRejectedException.cs>
   (8.9.0).
8. **A fixed-window limiter reports the next boundary as its retry-after.** ASP.NET Core docs: "FixedWindowRateLimiter
   reports `MetadataName.RetryAfter` based on the next fixed-window boundary. Apps that copy this metadata to the
   `Retry-After` response header in `OnRejected` return an accurate retry interval automatically." Source:
   <https://learn.microsoft.com/en-us/aspnet/core/performance/rate-limit> (ASP.NET Core 11/12 note, updated
   2026-11-03).
9. **Bucket4j reports the computed wait.** `ConsumptionProbe.getNanosToWaitForRefill`: "Returns zero if
   {@link #isConsumed()} returns true, else time in nanos which need to wait until requested amount of tokens
   will be refilled". Source:
   <https://github.com/bucket4j/bucket4j/blob/eca7297808dbe2aa01127ba3a4d224786153b701/bucket4j-core/src/main/java/io/github/bucket4j/ConsumptionProbe.java>
   (commit eca7297).
10. **Resilience4j reports no duration.** `RequestNotPermitted` exposes `getCausingRateLimiterName()` only; the
    message is "RateLimiter '%s' does not permit further calls". Source:
    <https://github.com/resilience4j/resilience4j/blob/aa9aacb8cb5333f2dee2590ff07f05f2c942d602/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/RequestNotPermitted.java>
    (commit aa9aacb).
11. **AWS's standard strategy computes throttling backoff itself.** "For throttling exceptions, it uses the
    `BackoffStrategy#exponentialDelay` backoff strategy, with a base delay of 1 second and a max delay of 20
    seconds. You can adjust this with `StandardRetryStrategy.Builder#throttlingBackoffStrategy`." Source:
    <https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/retry-strategy.html> (read 2026-11-06,
    SDK 2.x).
12. **Discord4J backs off by the server's header and retries internally.** `RateLimitRetryOperator`: "The
    backoff is determined by the rate limit headers returned by Discord in the event of a 429." Source:
    <https://github.com/Discord4J/Discord4J/blob/7c292f43dfea8b880b531c235a080374335092cf/rest/src/main/java/discord4j/rest/request/RateLimitRetryOperator.java>
    (commit 7c292f4).

## Gaps

- **AWS and `Retry-After`.** The Java guide never mentions the header; whether the Java SDK reads
  `Retry-After` or `x-amz-retry-after` on a throttle was not established. Row in the table is limited to what
  the guide states.
- **Stripe's clients.** `stripe-java` has no `Retry-After` handling in `src/main`; whether other Stripe SDKs
  surface a wait was not checked. Stripe contributes no evidence either way.
- **Kord, Discord4J and OkHttp surface nothing to the caller**, so they show how a wait is computed and not how
  it is reported; only JDA reports a throttle's wait as a failure field among the Discord libraries.
- **Whether a caller that waits the reported value is then accepted by the server** depends on the server's
  counting (RFC 6585 §4 declines to define it); the recommendation promises only that the local pause and the
  server's stated ask are covered.
- **A consumer's own `CallBudget` that throws from `utilisation()`.** The port's contract (items 7 and 10 of
  the interface KDoc) describes a snapshot and concurrency but does not say that `utilisation()` never fails;
  the facade should let a thrown non-cancellation error from it become the call's failure or fall back to the
  server's ask. Which is for the plan to fix; this research did not read a consumer implementation.
- **Cost not measured.** The claim that one `utilisation()` read is negligible on a throttle is derived from
  the code (one lock acquisition and a list of one small value per window), not benchmarked.
- **JDA tag.** The clone reported `v6.7.0` at its head; the links use that tag, but the permalink to the file was
  not opened on the web.
