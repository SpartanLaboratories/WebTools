package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpMethod

/**
 * A [FakeHttpTransport] under [Unmatched.Fail] received a request no stub matches. It is an
 * `AssertionError`, so it is a test failure that no `Result` or cache loader swallows: through a loader it
 * surfaces as the fatal `Error` rethrown to each waiter.
 *
 * **Stable Core, source compatibility only.** The message is
 * `No stub of the FakeHttpTransport matches <METHOD> <redacted URL> (<n> stubs)`: the URL is
 * `Url.redacted()`, so no query, header or body is ever in it. Only the fake builds it. Equality is identity.
 *
 * @property method the request's method.
 * @property redactedUrl the request's URL with its query replaced by `?...`.
 * @property stubCount how many stubs the fake held.
 */
public class UnmatchedRequestError internal constructor(
    /** The request's method. */
    public val method: HttpMethod,
    /** The request's URL as `Url.redacted()` prints it. */
    public val redactedUrl: String,
    /** How many stubs the fake held when the request arrived. */
    public val stubCount: Int,
) : AssertionError(unmatchedMessage(method, redactedUrl, stubCount))

private fun unmatchedMessage(method: HttpMethod, redactedUrl: String, stubCount: Int): String =
    "No stub of the FakeHttpTransport matches $method $redactedUrl ($stubCount stubs)"
