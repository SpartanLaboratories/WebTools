package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.Url

/**
 * The URL as this module prints it: scheme, host and port only, the port always written, an IPv6 host
 * bracketed. Never the path or the query: a feed URL is administrator-supplied and its path can carry a
 * secret.
 */
internal fun Url.origin(): String = "$scheme://${if (':' in host) "[$host]" else host}:$port"

/**
 * The origin of a link text: the text before the first `/`, `?` or `#` that follows `://`, or `invalid`
 * when the text has no `://`. A link that passed the link rule has no user-info, so its authority is safe
 * to print.
 */
internal fun originOfLink(link: String): String {
    val separator = link.indexOf("://")
    if (separator < 0) return "invalid"
    val authorityStart = separator + 3
    var end = authorityStart
    while (end < link.length && link[end] != '/' && link[end] != '?' && link[end] != '#') end++
    return link.substring(0, end)
}
