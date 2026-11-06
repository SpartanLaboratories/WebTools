package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.CallSpec
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.FetchSpec
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.TransportSettings
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.WebToolsDsl
import com.spartanlabs.webtools.http.WindowedCallBudget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 2 - every builder the module passes to a consumer's lambda carries the `WebToolsDsl` marker, read by reflection
 * (the marker is retained at runtime for this). The ABI dump does not record a class's annotations, so this is the check
 * that fails when a builder loses the marker; a failure names the builder. A builder added later joins the list.
 */
class WebToolsDslComponentTest {

    private val builders: List<Class<*>> = listOf(
        HttpHeaders.Builder::class.java,
        HttpRequest.Builder::class.java,
        HttpResponse.Builder::class.java,
        TransportSettings.Builder::class.java,
        OutboundUrlPolicy.Trusted.Builder::class.java,
        OutboundUrlPolicy.Untrusted.Builder::class.java,
        CachePolicy.Builder::class.java,
        CaffeineResponseCache.Builder::class.java,
        Endpoint.Builder::class.java,
        CallSpec::class.java,
        FetchSpec::class.java,
        UpstreamClientConfig::class.java,
        UrlFetcherConfig::class.java,
        RateWindow.Builder::class.java,
        RateSignals.Builder::class.java,
        WindowedCallBudget.Builder::class.java,
    )

    @Test
    fun `every builder carries the WebToolsDsl marker`() {
        val missing = builders.filter { !it.isAnnotationPresent(WebToolsDsl::class.java) }.map { it.name }
        assertEquals(emptyList<String>(), missing)
    }
}
