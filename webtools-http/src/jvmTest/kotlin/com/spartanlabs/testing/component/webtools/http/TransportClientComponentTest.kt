package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.webtools.http.ExchangeGuard
import com.spartanlabs.webtools.http.GuardedDns
import com.spartanlabs.webtools.http.GuardedSocketFactory
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.TransportSettings
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.enforcement
import com.spartanlabs.webtools.http.newTransportClient
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.Proxy
import java.security.KeyStore
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the client `newTransportClient` builds: retries, redirects, authenticators, cookies, cache, event listener and the call timeout
 * are off; the socket timeouts and the dispatcher limits follow the settings; the pool and the dispatcher are new even from a donor, whose
 * own stay untouched; `ExchangeGuard` is the only network interceptor and there is no application interceptor even when the donor had
 * both; the donor's TLS configuration is kept; `Untrusted` gets the guarded resolver, the guarded socket factory and `Proxy.NO_PROXY`,
 * `Trusted` the base resolver, the default factory and no proxy; fast fallback stays on.
 */
class TransportClientComponentTest {

    private val stub = StubDns.of("127.0.0.1")
    private val settings = TransportSettings(TransportSettings.UntrustedDefaults) {
        connectTimeout = 2.seconds; readTimeout = 3.seconds; writeTimeout = 4.seconds; maxConcurrentRequests = 6; maxConcurrentRequestsPerHost = 3
    }
    private val untrusted = UrlGuard(OutboundUrlPolicy.untrusted { this.settings = this@TransportClientComponentTest.settings })
    private val trusted = UrlGuard(OutboundUrlPolicy.trusted { this.settings = this@TransportClientComponentTest.settings })
    private val built = mutableListOf<OkHttpClient>()

    private fun build(guard: UrlGuard, donor: OkHttpClient? = null) =
        newTransportClient(guard.policy.settings, guard.enforcement(stub), donor).also { built += it.client }

    @AfterTest
    fun stop() {
        built.forEach { it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
    }

    @Test
    fun `retries redirects authentication cookies cache events and the call timeout are off`() {
        val client = build(untrusted).client
        assertEquals(false, client.retryOnConnectionFailure)
        assertEquals(false, client.followRedirects)
        assertEquals(false, client.followSslRedirects)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertEquals(0, client.callTimeoutMillis)
        assertTrue(client.fastFallback)
    }

    @Test
    fun `the socket timeouts and the dispatcher limits follow the settings`() {
        val transportClient = build(untrusted)
        assertEquals(2_000, transportClient.client.connectTimeoutMillis)
        assertEquals(3_000, transportClient.client.readTimeoutMillis)
        assertEquals(4_000, transportClient.client.writeTimeoutMillis)
        assertEquals(6, transportClient.dispatcher.maxRequests)
        assertEquals(3, transportClient.dispatcher.maxRequestsPerHost)
        assertSame(transportClient.dispatcher, transportClient.client.dispatcher)
        assertSame(transportClient.pool, transportClient.client.connectionPool)
    }

    @Test
    fun `a donor contributes its TLS configuration and nothing that is set here`() {
        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }.trustManagers.first() as X509TrustManager
        val sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
        val noisy = Interceptor { chain -> chain.proceed(chain.request()) }
        val donor = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .addInterceptor(noisy)
            .addNetworkInterceptor(noisy)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .callTimeout(9, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        built += donor
        val derived = build(untrusted, donor)
        assertSame(sslSocketFactory, derived.client.sslSocketFactory)
        assertSame(trustManager, derived.client.x509TrustManager)
        assertEquals(listOf<Interceptor>(ExchangeGuard), derived.client.networkInterceptors)
        assertEquals(emptyList(), derived.client.interceptors)
        assertEquals(false, derived.client.followRedirects)
        assertEquals(false, derived.client.retryOnConnectionFailure)
        assertEquals(0, derived.client.callTimeoutMillis)
        assertNotSame(donor.dispatcher, derived.dispatcher)
        assertNotSame(donor.connectionPool, derived.pool)
        assertEquals(listOf(noisy), donor.interceptors)
        assertEquals(listOf(noisy), donor.networkInterceptors)
        assertEquals(64, donor.dispatcher.maxRequests)
        assertEquals(true, donor.followRedirects)
    }

    @Test
    fun `two clients never share a pool or a dispatcher`() {
        val first = build(untrusted)
        val second = build(untrusted)
        assertNotSame(first.dispatcher, second.dispatcher)
        assertNotSame(first.pool, second.pool)
    }

    @Test
    fun `Untrusted wires the guarded resolver the guarded socket factory and no proxy`() {
        val client = build(untrusted).client
        assertIs<GuardedDns>(client.dns)
        assertIs<GuardedSocketFactory>(client.socketFactory)
        assertEquals(Proxy.NO_PROXY, client.proxy)
        assertEquals(listOf<Interceptor>(ExchangeGuard), client.networkInterceptors)
    }

    @Test
    fun `Trusted keeps the base resolver the default socket factory and OkHttp's proxy selection`() {
        val client = build(trusted).client
        assertSame(stub, client.dns)
        assertSame(SocketFactory.getDefault(), client.socketFactory)
        assertNull(client.proxy)
        assertEquals(listOf<Interceptor>(ExchangeGuard), client.networkInterceptors)
    }
}
