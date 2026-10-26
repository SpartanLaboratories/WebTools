package com.spartanlabs.webtools.http

import java.io.IOException
import java.net.UnknownHostException
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

/** The constant message of [PolicyRefusedUnknownHost]; never a host, an address or a range. */
internal const val POLICY_REFUSED_HOST_MESSAGE: String = "The host was refused by the URL policy."

/** The constant message of [PolicyRefusedIo]; never an address or a range. */
internal const val POLICY_REFUSED_IO_MESSAGE: String = "The connection was refused by the URL policy."

// A cause chain or suppressed tree deeper than this is pathological; the cap keeps the walk bounded against a misbehaving
// Throwable subclass whose cause or suppressed list never ends.
private const val MAX_THROWABLES_SEARCHED: Int = 100

/**
 * Thrown by [GuardedDns] when a resolver answer is refused. An `UnknownHostException`, so OkHttp rethrows it unwrapped. The
 * message is a constant and never holds a host, an address or a range. It carries no stack trace (a refusal is an expected,
 * attacker-triggerable event and the stack adds nothing): log it by message and reason, and detect it with
 * [policyRefusal], which also finds it in the `suppressed` list fast fallback fills.
 *
 * @property refusal the reason and, for `DeniedAddress`, the range class; [policyRefusal] reads it.
 */
internal class PolicyRefusedUnknownHost(val refusal: GuardRefusal) : UnknownHostException(POLICY_REFUSED_HOST_MESSAGE) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Thrown by [GuardedSocket] when a connect is refused. An `IOException` and deliberately never a `ConnectException`, which
 * OkHttp re-wraps as `ConnectException("Failed to connect to <address>")`. The message is a constant, and it carries no
 * stack trace: log it by message and reason, and detect it with [policyRefusal], which also finds it in the `suppressed`
 * list fast fallback fills.
 *
 * @property refusal the reason and, for `DeniedAddress`, the range class (`unresolved` for a target without an address); [policyRefusal] reads it.
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
