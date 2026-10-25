package com.spartanlabs.webtools.http

/**
 * Marks a WebTools seam whose implementation or subclassing is Experimental: the seam's shape has not
 * been proven by a consumer's own implementation yet, so implementing it may break in a minor release.
 *
 * **Stable Core.** The marker is never removed. It is applied only as the argument of
 * `@SubclassOptInRequired` on the gated ports and failure groups ([HttpTransport], [WallClock],
 * [HttpFailure], [TransportFailure] and [UpstreamFailure]) and of `@OptIn` in a consumer that implements,
 * delegates to or subclasses one of them; it is never applied to a declaration directly. Calling, passing
 * and matching the gated types, and using the supplied implementations (for example [WallClock.System]),
 * needs no opt-in. To implement or subclass a gated type, annotate the implementer with
 * `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).",
)
@Target()
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class ExperimentalForInheritanceWebToolsApi
