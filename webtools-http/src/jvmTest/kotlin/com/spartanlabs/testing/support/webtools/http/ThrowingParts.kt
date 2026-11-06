package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateSignalsReader

/**
 * A [RateSignalsReader] whose `read` throws [throwable]: a reader that breaks its "never throws" contract.
 *
 * @param throwable what the one member throws.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class ThrowingSignalsReader(private val throwable: Throwable) : RateSignalsReader {
    override fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals = throw throwable
}

/**
 * A [CallBudget] that passes every call to [delegate] except `utilisation()`, which throws [throwable].
 *
 * @param delegate the budget every other call is passed to.
 * @param throwable what the one member throws.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class UtilisationThrowingBudget(private val delegate: CallBudget, private val throwable: Throwable) : CallBudget by delegate {
    override suspend fun utilisation(): BudgetUtilisation = throw throwable
}

/**
 * An [HttpTransport] whose `execute` throws [throwable] instead of returning a `Result`: a transport that breaks its contract.
 *
 * @param throwable what the one member throws.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
internal class ThrowingTransport(private val throwable: Throwable) : HttpTransport {
    override suspend fun execute(request: HttpRequest): Result<HttpResponse> = throw throwable
}
