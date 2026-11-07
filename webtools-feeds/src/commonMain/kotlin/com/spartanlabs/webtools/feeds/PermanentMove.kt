package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Url

/**
 * The target of the last hop of the leading run of `301`/`308` hops, or `null` when there is no such hop or the target is
 * [requested]. A hop's target is the next hop's `url`; the last hop's is `response.url`. The leading-run rule keeps a `302`
 * at the head from moving the subscription: a permanent move is a fact about the chain, not an instruction.
 */
internal fun permanentMoveOf(requested: Url, response: HttpResponse): Url? {
    val chain = response.redirectChain
    val run = chain.takeWhile { it.status == 301 || it.status == 308 }
    if (run.isEmpty()) return null
    return (chain.getOrNull(run.size)?.url ?: response.url).takeIf { it != requested }
}
