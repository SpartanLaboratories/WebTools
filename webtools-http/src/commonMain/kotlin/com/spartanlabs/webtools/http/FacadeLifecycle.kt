package com.spartanlabs.webtools.http

import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration

/** Which of the three components a facade built and must close. The facade never closes a component it was handed. */
internal class Owned(val cache: Boolean, val budget: Boolean, val transport: Boolean) {
    /** The owned parts, in the closing order, for the `created` log line. */
    fun names(): List<String> = listOfNotNull("cache".takeIf { cache }, "budget".takeIf { budget }, "transport".takeIf { transport })

    companion object {
        val None: Owned = Owned(cache = false, budget = false, transport = false)
    }
}

/**
 * Closes each present component in order, each inside [catchNonFatal]; a failure is logged at `DEBUG` with the step name and the exception's class name and the
 * next component still closes. Never throws (an `Error` aside).
 */
internal fun closeInOrder(steps: List<Pair<String, AutoCloseable?>>, log: Log?) {
    steps.forEach { (name, component) ->
        if (component != null) catchNonFatal { component.close() }.onFailure { log?.debug("close step {} failed: {}", name, it::class.simpleName) }
    }
}

/** What a loader that ran reports to the call's `DEBUG` line. */
internal class LoadTrace(val waited: Duration?, val status: Int?, val bytes: Int?)

/** A one-value hand-off from the loader (possibly on the cache's dispatcher) to the call; `Channel(CONFLATED)` is thread-safe and stable API in `commonMain`. */
internal class TraceSlot {
    private val channel = Channel<LoadTrace>(Channel.CONFLATED)
    fun put(trace: LoadTrace) { channel.trySend(trace) }
    fun take(): LoadTrace? = channel.tryReceive().getOrNull()
}

/** The kind name a log line prints for a component: its simple class name, or `custom` for an anonymous one. */
internal fun kindOf(component: Any): String = component::class.simpleName ?: "custom"

/** `Trusted` or `Untrusted`, for a log line and `toString()`. */
internal fun policyKind(policy: OutboundUrlPolicy): String = when (policy) {
    is OutboundUrlPolicy.Trusted -> "Trusted"
    is OutboundUrlPolicy.Untrusted -> "Untrusted"
}

/** A duration in whole milliseconds for a log line, or `-` when unknown. */
internal fun Duration?.millisOrDash(): String = this?.inWholeMilliseconds?.toString() ?: "-"
