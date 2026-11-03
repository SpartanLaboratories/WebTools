package com.spartanlabs.webtools.http

import okio.BufferedSource

/**
 * Reads the whole decoded body of [source] when it holds at most [limit] bytes. A [declared] length above [limit] fails at once, with no read;
 * otherwise `request(limit + 1)` asks whether even one byte more than the cap exists. Memory is bounded by the cap plus one okio segment.
 *
 * @param source the decoded body.
 * @param declared the server's declared length, or `null` when there is none (a chunked or transparently decoded body).
 * @param limit the cap in decoded bytes; at most `Int.MAX_VALUE - 8`, so `limit + 1` cannot overflow.
 * @return the bytes; `Result.failure(TransportFailure.BodyTooLarge(limit, declared))` past the cap; or the source's own exception (an I/O error).
 */
internal fun readCappedBody(source: BufferedSource, declared: Long?, limit: Long): Result<ByteArray> =
    if (declared != null && declared > limit) {
        Result.failure(TransportFailure.BodyTooLarge(limit, declared))
    } else {
        catchNonFatal { source.request(limit + 1) }.flatMap { exceeded ->
            if (exceeded) Result.failure(TransportFailure.BodyTooLarge(limit, declared)) else catchNonFatal { source.buffer.readByteArray() }
        }
    }
