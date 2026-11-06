package com.spartanlabs.webtools.http

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The configuration of an [UpstreamClient]: the receiver of the factory's lambda (Ktor's `HttpClientConfig` is the precedent). **Stable Core.** Not thread-safe; use it only inside the lambda.
 */
@WebToolsDsl
public class UpstreamClientConfig internal constructor(private val baseUrl: Url) {
    /**
     * The outbound-URL policy the client pre-checks every request with and the owning factory's transport enforces. Default: `OutboundUrlPolicy.trusted { pinnedHosts = setOf(baseUrl.host) }`, which
     * allows only `https` on the base URL's host. Under an `Untrusted` policy a sensitive default header is refused when the client is built.
     */
    // The host is read into a local first: this class carries the DSL marker, so inside the `trusted { }` lambda its own `baseUrl` is out of reach.
    public var policy: OutboundUrlPolicy = baseUrl.host.let { pinned -> OutboundUrlPolicy.trusted { pinnedHosts = setOf(pinned) } }

    /** Measures every wait and duration: the owned cache's lifetimes, the owned budget and the per-call log figures. [TimeSource.Monotonic] by default; a test passes `VirtualClock.timeSource`. */
    public var timeSource: TimeSource = TimeSource.Monotonic

    /** The wall clock the owned budget indexes its windows with and the signal reader resolves dates with. [WallClock.System] by default. */
    public var wallClock: WallClock = WallClock.System

    /** Where the owned cache's loads run. [Dispatchers.Default] by default. Unused by the common factory, which owns no cache, and when `cache(...)` was called. */
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /** Reads rate signals from each response. [RateSignalsReader.Standard] by default; a per-provider reader implements the (opt-in) port. */
    public var rateSignals: RateSignalsReader = RateSignalsReader.Standard

    /**
     * The longest a call waits for a budget permit before it fails with `HttpFailure.BudgetExhausted`, which carries the wait the call would have needed. Defaults to
     * 5 seconds ([DEFAULT_MAX_WAIT]), long enough to ride out a burst over a window of a few seconds and short enough that a person waiting on the call sees the failure before the
     * transport's call timeout is added to it: a call's worst-case latency is this value plus the call timeout. A wait for a window of a minute or longer, or for a server-requested pause,
     * is normally longer and fails at once. Use `Duration.ZERO` to never wait and `Duration.INFINITE` to wait as long as the plan needs (up to the budget's pause cap). A response-cache load shared by
     * several callers waits with the cap of the caller that started it. Meaningful only with a budget that waits; `CallBudget.Unlimited` grants at once. A per-call `CallSpec.maxWait`
     * overrides it. Not negative.
     */
    public var defaultMaxWait: Duration = DEFAULT_MAX_WAIT

    /**
     * The default request headers, copied into every call's headers (`CallSpec.headers { }` edits them). Put credentials here with `addSensitive`: they are never printed, an `Untrusted` policy refuses
     * them, and the transport strips them on a cross-origin redirect. A header the transport controls (`User-Agent`, `Host`, …) is refused when the client is built. Empty by default.
     */
    public var headers: HttpHeaders = HttpHeaders()

    private var chosenCache: ResponseCache? = null
    private var chosenBudget: BudgetChoice = BudgetChoice.Unset

    /**
     * Uses [cache], which the caller owns: the client never closes it. Without this call the common factory uses `ResponseCache.Disabled` and the JVM owning factory builds and owns a
     * `CaffeineResponseCache`. A later call replaces an earlier one.
     *
     * @param cache the cache to store answers in.
     */
    public fun cache(cache: ResponseCache) { chosenCache = cache }

    /**
     * Uses [budget], which the caller owns: the client never closes it. A later `budget(...)` call replaces an earlier one. Without any, the client uses `CallBudget.Unlimited`.
     *
     * @param budget the budget calls are charged to.
     */
    public fun budget(budget: CallBudget) { chosenBudget = BudgetChoice.Supplied(budget) }

    /**
     * Builds a [WindowedCallBudget] over [windows] when the factory runs, after this whole lambda, and owns it: the client closes it. Its builder is seeded with this configuration's final
     * [timeSource] and [wallClock]; then [configure] runs, so an explicit assignment there wins. A later `budget(...)` call replaces an earlier one.
     *
     * @param windows the provider's rate windows; see [WindowedCallBudget].
     * @param configure assigns the budget's optional properties; empty by default.
     */
    public fun budget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {}) {
        chosenBudget = BudgetChoice.Windows(windows.toList(), configure)
    }

    /** The default of [defaultMaxWait]. */
    public companion object {
        /** 5 seconds. */
        public val DEFAULT_MAX_WAIT: Duration = 5.seconds
    }

    /**
     * The pure construction checks, in this order: `defaultMaxWait` is not negative; the base URL has no query; the policy allows the base URL; no default header is one the
     * transport controls; under an `Untrusted` policy no default header is sensitive.
     *
     * @throws IllegalArgumentException for a refusal; the message is constant and never echoes a value.
     */
    internal fun resolve(): ResolvedClient {
        require(!defaultMaxWait.isNegative()) { MSG_DEFAULT_MAX_WAIT }
        require(baseUrl.encodedQuery == null) { MSG_BASE_URL_QUERY }
        val refusal = UrlGuard(policy).checkHop(baseUrl, null)
        require(refusal == null) { MSG_BASE_URL_REFUSED + refusal?.reason?.name }
        val controlled = TransportControlledHeaders.ruleFor(headers)
        require(controlled == null) { MSG_DEFAULT_HEADER + controlled }
        require(policy !is OutboundUrlPolicy.Untrusted || headers.entries().none { it.isSensitive }) { MSG_DEFAULT_HEADER_SENSITIVE }
        return ResolvedClient(baseUrl, policy, timeSource, wallClock, dispatcher, rateSignals, defaultMaxWait, headers, chosenCache, chosenBudget)
    }
}

/** How the client's budget is chosen. */
internal sealed interface BudgetChoice {
    object Unset : BudgetChoice
    class Supplied(val budget: CallBudget) : BudgetChoice
    class Windows(val windows: List<RateWindow>, val configure: WindowedCallBudget.Builder.() -> Unit) : BudgetChoice
}

/** A budget the client will use and whether it built (and so owns) it. */
internal class BuiltBudget(val budget: CallBudget, val owned: Boolean)

/** The validated configuration of a client; everything the factories and the constructor need. */
internal class ResolvedClient(
    val baseUrl: Url, val policy: OutboundUrlPolicy, val timeSource: TimeSource, val wallClock: WallClock, val dispatcher: CoroutineDispatcher,
    val rateSignals: RateSignalsReader, val defaultMaxWait: Duration, val headers: HttpHeaders, val cache: ResponseCache?, private val budgetChoice: BudgetChoice,
) {
    /**
     * The client's budget: `Unlimited`, the supplied one, or a `WindowedCallBudget` built now from the windows with the client's clocks, then the consumer's block.
     *
     * @throws IllegalArgumentException if the windows or a number in the block are refused by `WindowedCallBudget`.
     */
    fun buildBudget(): BuiltBudget = when (val choice = budgetChoice) {
        BudgetChoice.Unset -> BuiltBudget(CallBudget.Unlimited, owned = false)
        is BudgetChoice.Supplied -> BuiltBudget(choice.budget, owned = false)
        is BudgetChoice.Windows -> {
            // Locals, because inside the budget builder's lambda `timeSource` and `wallClock` name the builder's own properties.
            val clock = timeSource
            val wall = wallClock
            BuiltBudget(WindowedCallBudget(choice.windows) { timeSource = clock; wallClock = wall; choice.configure(this) }, owned = true)
        }
    }
}

/** What an `UpstreamClient` is made of: the factories build one, so no consumer-shaped constructor exists. */
internal class ClientParts(val resolved: ResolvedClient, val transport: HttpTransport, val cache: ResponseCache, val budget: CallBudget, val owned: Owned)
