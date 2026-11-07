package com.spartanlabs.testing.support.webtools.feeds

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/** A dispatcher that counts what is dispatched to it and runs it on [delegate]; proves a parse ran on `parseDispatcher`. */
class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private val count = AtomicInteger()

    /** How many blocks were dispatched here. */
    val dispatched: Int get() = count.get()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        count.incrementAndGet()
        delegate.dispatch(context, block)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true
}
