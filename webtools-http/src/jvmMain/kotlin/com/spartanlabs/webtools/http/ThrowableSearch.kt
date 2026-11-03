package com.spartanlabs.webtools.http

import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

// A cause chain or suppressed tree deeper than this is pathological; the cap keeps the walk bounded against a misbehaving
// Throwable subclass whose cause or suppressed list never ends.
private const val MAX_THROWABLES_SEARCHED: Int = 100

/**
 * Returns the first non-null result of [select] over this throwable, its cause chain and its suppressed lists, depth first (a throwable's
 * cause before its suppressed, suppressed in order). Bounded to 100 throwables and cycle-safe. Never throws unless [select] does.
 *
 * @param select reads one throwable; a non-null result ends the walk.
 * @return the first non-null result, or `null` when no throwable within the bound yields one.
 */
internal fun <T : Any> Throwable.firstInChain(select: (Throwable) -> T?): T? {
    val seen: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
    val pending = ArrayDeque<Throwable>()
    pending.push(this)
    while (pending.isNotEmpty() && seen.size < MAX_THROWABLES_SEARCHED) {
        val current = pending.pop()
        if (!seen.add(current)) continue
        select(current)?.let { return it }
        current.suppressedExceptions.asReversed().forEach { pending.push(it) }   // pushed first, popped after the cause
        current.cause?.let { pending.push(it) }
    }
    return null
}
