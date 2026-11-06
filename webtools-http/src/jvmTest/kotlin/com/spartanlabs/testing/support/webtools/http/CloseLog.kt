package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.ResponseCache
import java.util.concurrent.CopyOnWriteArrayList

/** The order in which a facade closed its components: each logged delegate appends its name when its `close()` runs. Safe from any thread. */
internal class CloseLog {
    private val entries = CopyOnWriteArrayList<String>()

    /** The names appended so far, in order; a snapshot. */
    val names: List<String> get() = entries.toList()

    /**
     * Appends [name].
     *
     * @param name the component's name.
     */
    fun add(name: String) {
        entries += name
    }
}

/**
 * A [ResponseCache] that passes every call to [delegate] and, when `close()` runs, appends `cache` to [log], closes the delegate and then throws
 * [failure] when one is configured. The `@OptIn` is the line a consumer's own cache needs. Every `close()` call appends again, so the log shows how
 * often the component was closed. As safe to share between threads as [delegate]; the log is safe from any thread.
 *
 * @param delegate the component every call is passed to.
 * @param log receives the component's name on each `close()`.
 * @param failure thrown at the end of each `close()`; none by default.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class LoggedCache(private val delegate: ResponseCache, private val log: CloseLog, private val failure: Exception? = null) : ResponseCache by delegate {
    override fun close() {
        log.add("cache")
        delegate.close()
        failure?.let { throw it }
    }

    override fun toString(): String = "LoggedCache($delegate)"
}

/**
 * A [CallBudget] that passes every call to [delegate] and, when `close()` runs, appends `budget` to [log], closes the delegate and then throws
 * [failure] when one is configured. Every `close()` call appends again, so the log shows how often the component was closed. As safe to share
 * between threads as [delegate]; the log is safe from any thread.
 *
 * @param delegate the component every call is passed to.
 * @param log receives the component's name on each `close()`.
 * @param failure thrown at the end of each `close()`; none by default.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class LoggedBudget(private val delegate: CallBudget, private val log: CloseLog, private val failure: Exception? = null) : CallBudget by delegate {
    override fun close() {
        log.add("budget")
        delegate.close()
        failure?.let { throw it }
    }

    override fun toString(): String = "LoggedBudget($delegate)"
}

/**
 * An [HttpTransport] that passes every call to [delegate] and, when `close()` runs, appends `transport` to [log], closes the delegate and then throws
 * [failure] when one is configured. Every `close()` call appends again, so the log shows how often the component was closed. As safe to share
 * between threads as [delegate]; the log is safe from any thread.
 *
 * @param delegate the component every call is passed to.
 * @param log receives the component's name on each `close()`.
 * @param failure thrown at the end of each `close()`; none by default.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class LoggedTransport(private val delegate: HttpTransport, private val log: CloseLog, private val failure: Exception? = null) : HttpTransport by delegate {
    override fun close() {
        log.add("transport")
        delegate.close()
        failure?.let { throw it }
    }

    override fun toString(): String = "LoggedTransport($delegate)"
}
