package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.Url

/**
 * One redirect-chain row: the requested URL, the response (its `redirectChain` and final `url`) and the permanent
 * move the module must report for it, or `null`.
 */
class ChainRow(val name: String, val requested: Url, val response: HttpResponse, val expectedMove: Url?)

/** The redirect-chain table: which chains report a permanent move. */
object ChainFixtures {
    val a: Url = url("https://feeds.example.test/a")
    val b: Url = url("https://feeds.example.test/b")
    val c: Url = url("https://feeds.example.test/c")
    val d: Url = url("https://feeds.example.test/d")
    val httpF: Url = url("http://x.test/f")
    val httpsF: Url = url("https://x.test/f")

    /** Builds a response with the given hops (each `url -status->`) ending at [finalUrl] with [finalStatus]. */
    fun chain(finalUrl: Url, finalStatus: Int = 200, vararg hops: Pair<Url, Int>): HttpResponse =
        HttpResponse(finalStatus, finalUrl) { redirectChain = hops.map { RedirectHop(it.first, it.second) } }

    val rows: List<ChainRow> = listOf(
        ChainRow("none", a, chain(a), null),
        ChainRow("A -301-> B", a, chain(b, 200, a to 301), b),
        ChainRow("A -308-> B -302-> C", a, chain(c, 200, a to 308, b to 302), b),
        ChainRow("A -302-> B -301-> C", a, chain(c, 200, a to 302, b to 301), null),
        ChainRow("A -301-> B -301-> C -307-> D", a, chain(d, 200, a to 301, b to 301, c to 307), c),
        ChainRow("http://x/f -301-> https://x/f", httpF, chain(httpsF, 200, httpF to 301), httpsF),
    )

    private fun url(text: String): Url = Url.parse(text).getOrThrow()
}
