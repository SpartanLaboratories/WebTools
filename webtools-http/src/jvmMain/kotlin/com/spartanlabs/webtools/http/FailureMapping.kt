package com.spartanlabs.webtools.http

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/** Where a failure arose: the exchange (`onFailure`) or the body read inside `onResponse`. */
internal enum class Phase { Exchange, Body }

/** Where a policy refusal was decided; [label] is the word the `WARN` line uses. */
internal enum class RefusalStage(val label: String) { Request("request"), RedirectTarget("redirect target of"), Connection("connection") }

/**
 * What the mapper needs to know besides the throwable.
 *
 * @property host the hop URL's host.
 * @property https whether the hop URL's scheme is `https`.
 * @property closed the transport's closed flag at the time of the failure.
 * @property callCanceled `call.isCanceled()`: set by the call timeout and by a cancel.
 * @property phase the phase the throwable came from.
 */
internal class FailureContext(val host: String, val https: Boolean, val closed: Boolean, val callCanceled: Boolean, val phase: Phase)

/** What `report` logs for a failure; one value per failed call. */
internal sealed interface FailureEvent {
    /** A policy refusal at [stage]: one `WARN`. */
    class PolicyRefusal(val refusal: GuardRefusal, val stage: RefusalStage) : FailureEvent

    /** A second exchange was refused: one `WARN`. */
    object ExchangeRepeat : FailureEvent

    /** [exceptionClass] is `null` for a failure the transport itself decided; [keptMessage] is set only for a kept exception. */
    class Other(val exceptionClass: String?, val keptMessage: String?) : FailureEvent

    /** Nothing is logged (the transport was closed). */
    object Silent : FailureEvent
}

/** A failure with what is logged for it. */
internal class MappedFailure(val failure: HttpFailure, val event: FailureEvent)

/** A failure the transport decided without an exception: logged at `DEBUG` with the leaf only. */
internal fun plainFailure(failure: HttpFailure): MappedFailure = MappedFailure(failure, FailureEvent.Other(null, null))

/** A policy refusal at [stage], as `RefusedByPolicy(reason)` with its `WARN` event. */
internal fun refusalFailure(refusal: GuardRefusal, stage: RefusalStage): MappedFailure =
    MappedFailure(TransportFailure.RefusedByPolicy(refusal.reason), FailureEvent.PolicyRefusal(refusal, stage))

/**
 * The failure mapping, first match wins. Total over any `Throwable` that is not an `Error`; never reads a message except OkHttp's own `code < 0`.
 *
 * @param e the throwable OkHttp or the body read produced.
 * @param ctx the context the rows need.
 * @return the leaf and its event.
 */
internal fun mapFailure(e: Throwable, ctx: FailureContext): MappedFailure {
    // The markers come before any type test: the call timeout wraps the real failure in an InterruptedIOException whose cause is the marker.
    val refusal = e.policyRefusal()
    return when {
        refusal != null -> refusalFailure(refusal, RefusalStage.Connection)
        e.repeatsExchange() -> MappedFailure(TransportFailure.RetryRefused(), FailureEvent.ExchangeRepeat)
        ctx.closed -> MappedFailure(HttpFailure.Closed("transport"), FailureEvent.Silent)
        e is UnknownHostException -> kept(TransportFailure.DnsFailed(ctx.host, e.takeIf { it.cause == null }), e)
        e is SocketTimeoutException -> kept(TransportFailure.Timeout(e), e)
        e is InterruptedIOException && ctx.callCanceled -> dropped(TransportFailure.Timeout(e.takeIf { it.hasBenignCause() }), e)
        e is SSLException && ctx.phase == Phase.Exchange -> dropped(TransportFailure.TlsFailed(), e)
        e is UnknownServiceException -> dropped(if (ctx.https) TransportFailure.TlsFailed() else TransportFailure.Io(), e)
        e is ConnectException || e is NoRouteToHostException || e is PortUnreachableException -> connectFailure(e)
        e is ProtocolException -> dropped(TransportFailure.Protocol(), e)
        e.isCanceledWrapper() -> dropped(if (e.cause.isNegativeStatus()) TransportFailure.Protocol() else TransportFailure.Io(), e)
        else -> dropped(TransportFailure.Io(), e)
    }
}

private fun kept(failure: HttpFailure, e: Throwable) = MappedFailure(failure, FailureEvent.Other(e.javaClass.name, e.message))
private fun dropped(failure: HttpFailure, e: Throwable) = MappedFailure(failure, FailureEvent.Other(e.javaClass.name, null))

// OkHttp re-wraps every ConnectException as ConnectException("Failed to connect to <address>") with the JDK exception as the cause: keep that
// cause and never the wrapper (none when there is no such cause). NoRouteToHostException and PortUnreachableException arrive unwrapped from
// the JDK, whose texts hold no address.
private fun connectFailure(e: Throwable): MappedFailure {
    val kept: Throwable? = if (e is ConnectException) e.cause as? IOException else e
    return if (kept != null) kept(TransportFailure.ConnectFailed(kept), kept) else dropped(TransportFailure.ConnectFailed(), e)
}

// The call timeout wraps the real failure; its own text is constant. Keep it only when its chain is empty or one plain SocketException with no
// cause of its own (an exact-class test: the JDK's texts for it, "Socket closed" among them, hold no address, while OkHttp re-wraps a
// ConnectException, a subclass, with the address in its text).
private fun InterruptedIOException.hasBenignCause(): Boolean =
    cause == null || (cause?.javaClass == SocketException::class.java && cause?.cause == null)

// OkHttp's AsyncCall.run wraps every non-IOException Throwable as IOException("canceled due to ...") with that Throwable as the cause.
private fun Throwable.isCanceledWrapper(): Boolean = javaClass == IOException::class.java && cause.let { it != null && it !is IOException }
private fun Throwable?.isNegativeStatus(): Boolean = this is IllegalStateException && message?.startsWith("code < 0") == true

/**
 * Logs one failure by its event: `WARN` for a refusal and a repeat, `DEBUG` otherwise, nothing for `Silent`. Never a header, query or address.
 *
 * @param url the hop the failure belongs to; logged redacted.
 * @param mapped the failure and its event.
 */
internal fun Log.report(url: Url, mapped: MappedFailure) {
    when (val event = mapped.event) {
        is FailureEvent.PolicyRefusal ->
            warn("refused by the URL policy: reason={}, rangeClass={}, {} {}", event.refusal.reason.name, event.refusal.rangeClass ?: "-", event.stage.label, url.redacted())
        FailureEvent.ExchangeRepeat -> warn("the transport refused a second exchange for one hop of {}", url.redacted())
        is FailureEvent.Other ->
            debug("{} failed for {}: {} {}", mapped.failure.javaClass.simpleName, url.redacted(), event.exceptionClass ?: "-", event.keptMessage ?: "")
        FailureEvent.Silent -> Unit
    }
}
