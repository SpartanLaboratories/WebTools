package com.spartanlabs.testing.support.webtools.http

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.reflect.KClass

/** A request for a fake call or response: the URL never resolves, nothing here opens a socket. */
internal fun fakeRequest(url: String = "http://fake.test/"): Request = Request.Builder().url(url).build()

/**
 * An `okhttp3.Response` built by hand, for the bridge and guard tests.
 *
 * @param code the status code.
 * @param headers the headers; none by default.
 * @param body the body text; empty by default.
 * @param request the request it answers; a fake one by default.
 * @return the response.
 */
internal fun fakeResponse(code: Int, headers: Headers = headersOf(), body: String = "", request: Request = fakeRequest()): Response =
    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("").headers(headers).body(body.toResponseBody(null)).build()

/**
 * A hand-written [Call] that never touches the network: it records the order of `enqueue` and `cancel`, runs [onEnqueue] inside
 * `enqueue` (so a test can cancel the coroutine before the callback is stored), and lets the test deliver `onResponse` or `onFailure`
 * on demand. `execute()` is not supported.
 *
 * @param request the request the call carries.
 * @param onEnqueue runs inside `enqueue`, after the callback is stored.
 */
internal class FakeCall(private val request: Request = fakeRequest(), private val onEnqueue: (FakeCall) -> Unit = {}) : Call {
    /** `enqueue` and `cancel`, in the order they were called. */
    val events = CopyOnWriteArrayList<String>()

    private val callback = AtomicReference<Callback?>()
    private val canceled = AtomicBoolean()
    private val timeout = Timeout()

    /** How many times `cancel()` was called. */
    val cancelCount: Int get() = events.count { it == "cancel" }

    override fun request(): Request = request
    override fun execute(): Response = throw UnsupportedOperationException("FakeCall is enqueue-only")

    override fun enqueue(responseCallback: Callback) {
        events += "enqueue"
        callback.set(responseCallback)
        onEnqueue(this)
    }

    override fun cancel() {
        events += "cancel"
        canceled.set(true)
    }

    override fun isExecuted(): Boolean = callback.get() != null
    override fun isCanceled(): Boolean = canceled.get()
    override fun timeout(): Timeout = timeout
    override fun addEventListener(eventListener: EventListener) {}
    override fun <T : Any> tag(type: KClass<T>): T? = request.tag(type)
    override fun <T> tag(type: Class<out T>): T? = request.tag(type)
    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = request.tag(type) ?: computeIfAbsent()
    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = request.tag(type) ?: computeIfAbsent()
    override fun clone(): Call = FakeCall(request, onEnqueue)

    /** Delivers [response] to the enqueued callback, on the calling thread. */
    fun deliverResponse(response: Response) = checkNotNull(callback.get()) { "nothing enqueued" }.onResponse(this, response)

    /** Delivers [e] to the enqueued callback, on the calling thread. */
    fun deliverFailure(e: IOException) = checkNotNull(callback.get()) { "nothing enqueued" }.onFailure(this, e)
}

/**
 * A hand-written [Interceptor.Chain] for the guard test: `proceed` records the request and answers with [answer]; every other member
 * returns a plain default and is never read by the guard.
 *
 * @param request the request the chain carries.
 * @param answer builds the response `proceed` returns for a request.
 */
internal class FakeChain(private val request: Request, private val answer: (Request) -> Response) : Interceptor.Chain {
    /** Every request `proceed` was given, in order. */
    val proceeded = CopyOnWriteArrayList<Request>()

    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        proceeded += request
        return answer(request)
    }

    override fun connection(): Connection? = null
    override fun call(): Call = FakeCall(request)
    override fun connectTimeoutMillis(): Int = 0
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun readTimeoutMillis(): Int = 0
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun writeTimeoutMillis(): Int = 0
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override val followSslRedirects: Boolean get() = false
    override val followRedirects: Boolean get() = false
    override val dns: Dns get() = Dns.SYSTEM
    override fun withDns(dns: Dns): Interceptor.Chain = this
    override val socketFactory: SocketFactory get() = SocketFactory.getDefault()
    override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain = this
    override val retryOnConnectionFailure: Boolean get() = false
    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain = this
    override val authenticator: Authenticator get() = Authenticator.NONE
    override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain = this
    override val cookieJar: CookieJar get() = CookieJar.NO_COOKIES
    override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain = this
    override val cache: Cache? get() = null
    override fun withCache(cache: Cache?): Interceptor.Chain = this
    override val proxy: Proxy? get() = null
    override fun withProxy(proxy: Proxy?): Interceptor.Chain = this
    override val proxySelector: ProxySelector get() = ProxySelector.getDefault()
    override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain = this
    override val proxyAuthenticator: Authenticator get() = Authenticator.NONE
    override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain = this
    override val sslSocketFactoryOrNull: SSLSocketFactory? get() = null
    override fun withSslSocketFactory(sslSocketFactory: SSLSocketFactory?, x509TrustManager: X509TrustManager?): Interceptor.Chain = this
    override val x509TrustManagerOrNull: X509TrustManager? get() = null
    override val hostnameVerifier: HostnameVerifier get() = HostnameVerifier { _, _ -> false }
    override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain = this
    override val certificatePinner: CertificatePinner get() = CertificatePinner.DEFAULT
    override fun withCertificatePinner(certificatePinner: CertificatePinner): Interceptor.Chain = this
    override val connectionPool: ConnectionPool get() = ConnectionPool()
    override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain = this
    override val eventListener: EventListener get() = EventListener.NONE
}
