package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The configuration of a [UrlFetcher]: the receiver of the factory's lambda. **Stable Core.** Not thread-safe; use it only inside the lambda.
 */
@WebToolsDsl
public class UrlFetcherConfig internal constructor() {
    /** The call budget fetches are charged to; [CallBudget.Unlimited] by default. Caller-owned: the fetcher never closes it, and owns none. */
    public var budget: CallBudget = CallBudget.Unlimited

    /** Reads rate signals from each response; [RateSignalsReader.Standard] by default. */
    public var rateSignals: RateSignalsReader = RateSignalsReader.Standard

    /** The wall clock read for each response's signals; [WallClock.System] by default. */
    public var wallClock: WallClock = WallClock.System

    /** Measures the per-fetch waited and elapsed figures in the log; [TimeSource.Monotonic] by default. */
    public var timeSource: TimeSource = TimeSource.Monotonic

    /**
     * The longest a fetch waits for a budget permit before it fails with `HttpFailure.BudgetExhausted`, which carries the wait the fetch would have needed. Defaults to
     * 5 seconds ([DEFAULT_MAX_WAIT]), long enough to ride out a burst over a window of a few seconds and short enough that a person waiting on the fetch sees the failure before the
     * transport's call timeout is added to it: a fetch's worst-case latency is this value plus the call timeout. A wait for a window of a minute or longer, or for a server-requested pause,
     * is normally longer and fails at once. Use `Duration.ZERO` to never wait and `Duration.INFINITE` to wait as long as the plan needs (up to the budget's pause cap).
     * Meaningful only with a budget that waits; `CallBudget.Unlimited` grants at once. A per-fetch `FetchSpec.maxWait` overrides it. Not negative.
     */
    public var defaultMaxWait: Duration = DEFAULT_MAX_WAIT

    /** The default of [defaultMaxWait]. */
    public companion object {
        /** 5 seconds (the same value as [UpstreamClientConfig.DEFAULT_MAX_WAIT]). */
        public val DEFAULT_MAX_WAIT: Duration = 5.seconds
    }

    /** @throws IllegalArgumentException if [defaultMaxWait] is negative. */
    internal fun resolve(): ResolvedFetcher {
        require(!defaultMaxWait.isNegative()) { MSG_DEFAULT_MAX_WAIT }
        return ResolvedFetcher(budget, rateSignals, wallClock, timeSource, defaultMaxWait)
    }
}

/** The validated configuration of a fetcher. */
internal class ResolvedFetcher(val budget: CallBudget, val rateSignals: RateSignalsReader, val wallClock: WallClock, val timeSource: TimeSource, val defaultMaxWait: Duration)

/** What a fetcher is made of; the factories (and the fixtures) build one, so no consumer-shaped constructor exists. */
internal class FetcherParts(val transport: HttpTransport, val guard: UrlGuard, val resolved: ResolvedFetcher, val ownsTransport: Boolean)
