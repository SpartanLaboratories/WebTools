package com.spartanlabs.webtools.http

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The client with the two objects `close()` must stop; both are this transport's own, never a donor's.
 *
 * @property client the configured client.
 * @property dispatcher the client's dispatcher, owned by the transport.
 * @property pool the client's connection pool, owned by the transport.
 */
internal class TransportClient(val client: OkHttpClient, val dispatcher: Dispatcher, val pool: ConnectionPool)

/**
 * Builds the client of one transport. A donor contributes its TLS configuration (and any property not set here); the dispatcher, the pool, the
 * interceptors, the redirect, retry, authentication, cookie, cache and event settings, the timeouts and the policy's resolver, socket factory and
 * proxy are the transport's, whatever the donor held.
 *
 * @param settings the policy's numbers.
 * @param enforcement `UrlGuard.enforcement(baseDns)`: `dns` always, `socketFactory` and `proxy` when non-null.
 * @param donor a client to derive from, or `null` for a default one.
 * @return the client with its dispatcher and pool.
 */
internal fun newTransportClient(settings: TransportSettings, enforcement: PolicyEnforcement, donor: OkHttpClient?): TransportClient {
    val dispatcher = Dispatcher(newDispatcherExecutor()).apply {
        maxRequests = settings.maxConcurrentRequests
        maxRequestsPerHost = settings.maxConcurrentRequestsPerHost
    }
    val pool = ConnectionPool(settings.maxConcurrentRequests, settings.idleConnectionTtl.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    val builder = donor?.newBuilder() ?: OkHttpClient.Builder()
    builder.interceptors().clear()
    builder.networkInterceptors().clear()
    val client = builder
        .dispatcher(dispatcher)
        .connectionPool(pool)
        .addNetworkInterceptor(ExchangeGuard)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .eventListener(EventListener.NONE)
        .callTimeout(0, TimeUnit.MILLISECONDS)            // each hop's Call gets its own limit from the shared deadline
        .connectTimeout(settings.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(settings.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .writeTimeout(settings.writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .dns(enforcement.dns)
        .apply {
            enforcement.socketFactory?.let { socketFactory(it) }
            enforcement.proxy?.let { proxy(it) }
        }
        .build()
    return TransportClient(client, dispatcher, pool)
}

/**
 * The dispatcher's pool: the shape of OkHttp's default (`ThreadPoolExecutor(0, Int.MAX_VALUE, 60 s, SynchronousQueue)`, so concurrency stays bounded
 * by `maxRequests`), with daemon threads named `webtools-http-dispatcher-<n>` and a handler that logs the throwable's class name at `DEBUG`.
 *
 * @return a new executor; each transport gets its own.
 */
internal fun newDispatcherExecutor(): ExecutorService =
    ThreadPoolExecutor(0, Int.MAX_VALUE, 60, TimeUnit.SECONDS, SynchronousQueue(), DispatcherThreads())

// Daemon, so a forgotten close() costs nothing at JVM exit; the handler, so a hostile status line cannot print a stack trace or kill the pool.
private class DispatcherThreads : ThreadFactory {
    private val sequence = AtomicInteger()

    override fun newThread(task: Runnable): Thread =
        Thread(task, "webtools-http-dispatcher-${sequence.incrementAndGet()}").apply {
            isDaemon = true
            setUncaughtExceptionHandler { _, throwable -> transportLog.debug("a dispatcher thread ended with {}", throwable.javaClass.name) }
        }
}
