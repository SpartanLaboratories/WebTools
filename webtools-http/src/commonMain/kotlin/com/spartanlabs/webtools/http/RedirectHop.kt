package com.spartanlabs.webtools.http

/**
 * One followed redirect: the URL that was **requested** and the `3xx` status it answered with. A chain
 * `[(A, 301), (B, 302)]` with a response whose `url` is `C` means A answered 301, B answered 302 and C
 * answered the response.
 *
 * **Stable Core.** The constructor's shape is frozen. Equality is on [url] and [status]; `toString()`
 * uses the redacted URL.
 *
 * @param url the URL that was requested.
 * @param status the redirect status it answered with, in 300..399.
 * @throws IllegalArgumentException if [status] is outside 300..399.
 */
public class RedirectHop(
    /** The URL that was requested. */
    public val url: Url,
    /** The redirect status it answered with, in 300..399. */
    public val status: Int,
) {
    init {
        require(status in 300..399) { "Redirect status must be in 300..399" }
    }

    override fun equals(other: Any?): Boolean = other is RedirectHop && other.url == url && other.status == status
    override fun hashCode(): Int = url.hashCode() * 31 + status
    override fun toString(): String = "RedirectHop(${url.redacted()}, $status)"
}
