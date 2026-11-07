package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import java.util.Collections

/**
 * A parser that records every input it is given (the bytes, the content type, the base URL and the thread) and answers with
 * [answer]: a success holding [feed] by default. Thread-safe.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
class RecordingParser(
    private val feed: Feed = Feed { title = "recorded" },
    private val answer: (FeedInput) -> Result<Feed> = { Result.success(feed) },
) : FeedParser {
    /** One recorded parse. */
    class Call(val document: ByteArray, val contentType: String?, val baseUrl: String?, val thread: String)

    private val log = Collections.synchronizedList(ArrayList<Call>())

    /** Every parse so far, in order. */
    val calls: List<Call> get() = log.toList()

    override fun parse(input: FeedInput): Result<Feed> {
        log += Call(input.document(), input.contentType, input.baseUrl?.toString(), Thread.currentThread().name)
        return answer(input)
    }
}
