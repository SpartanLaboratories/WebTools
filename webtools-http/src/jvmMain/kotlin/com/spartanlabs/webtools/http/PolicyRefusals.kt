package com.spartanlabs.webtools.http

import java.io.IOException
import java.net.UnknownHostException
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

internal const val POLICY_REFUSED_HOST_MESSAGE: String = "The host was refused by the URL policy."
internal const val POLICY_REFUSED_IO_MESSAGE: String = "The connection was refused by the URL policy."
private const val MAX_THROWABLES_SEARCHED: Int = 100

/**
 * Thrown by [GuardedDns] when a resolver answer is refused. An `UnknownHostException`, so OkHttp rethrows it unwrapped. The
 * message is a constant and never holds a host, an address or a range; the stack is skipped (a refusal is an expected,
 * attacker-triggerable event and the stack adds nothing).
 */
internal class PolicyRefusedUnknownHost(val refusal: GuardRefusal) : UnknownHostException(POLICY_REFUSED_HOST_MESSAGE) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Thrown by [GuardedSocket] when a connect is refused. An `IOException` and deliberately never a `ConnectException`, which
 * OkHttp re-wraps as `ConnectException("Failed to connect to <address>")`. Constant message; no stack.
 */
internal class PolicyRefusedIo(val refusal: GuardRefusal) : IOException(POLICY_REFUSED_IO_MESSAGE) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Finds the first policy marker in this throwable, its cause chain and its suppressed lists, depth first (a throwable's
 * cause before its suppressed, suppressed in order). Bounded to 100 throwables and cycle-safe.
 *
 * @return the refusal the marker carries, or `null` when there is none. Never throws.
 */
internal fun Throwable.policyRefusal(): GuardRefusal? {
    val seen: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
    val pending = ArrayDeque<Throwable>()
    pending.push(this)
    while (pending.isNotEmpty() && seen.size < MAX_THROWABLES_SEARCHED) {
        val current = pending.pop()
        if (!seen.add(current)) continue
        when (current) {
            is PolicyRefusedUnknownHost -> return current.refusal
            is PolicyRefusedIo -> return current.refusal
        }
        current.suppressedExceptions.asReversed().forEach { pending.push(it) }   // pushed first, popped after the cause
        current.cause?.let { pending.push(it) }
    }
    return null
}
