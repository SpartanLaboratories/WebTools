package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpRequest
import kotlin.time.Duration

/**
 * One call a [FakeHttpTransport] received: the request, when it arrived and whether a stub matched it.
 *
 * **Stable Core, source compatibility only.** Built by the fake only; immutable. Equality is identity.
 * `toString()` prints the method, the redacted URL (never the query), the stamp and the flag; no header
 * value or body.
 *
 * @property request the request as the fake received it.
 * @property receivedAt the virtual time elapsed on the fake's `TimeSource` since the fake was built, taken
 *   before any step delay (a request cancelled during its delay still counts: it was on the wire).
 * @property matched whether a stub accepted the request.
 */
public class RecordedCall internal constructor(
    /** The request as the fake received it. */
    public val request: HttpRequest,
    /** When the call arrived: elapsed on the fake's time source since construction, before any delay. */
    public val receivedAt: Duration,
    /** Whether a stub accepted the request; `false` for an unmatched one. */
    public val matched: Boolean,
) {
    override fun toString(): String = "RecordedCall(${request.method} ${request.url.redacted()}, receivedAt=$receivedAt, matched=$matched)"
}
