package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpFailure

/**
 * A failure of a feed document: the exchange and the policy were fine, the bytes were not a feed this module will
 * read. Returned by a [FeedParser] and by `FeedFetcher.fetch` inside a `Result`.
 *
 * **Experimental (subclass).** Subclassing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; creating a
 * leaf, catching, matching and reading one are Stable Core. The leaves [NotAFeed], [Malformed], [Refused] and
 * [LimitExceeded] are final with public constructors, so a parser of your own creates them. Match the leaves you care
 * about and keep an `else`: new leaves may be added in a feature release.
 *
 * Every message is constant text plus a reason's name; it never holds document content, a header value or a URL,
 * and a leaf never carries a cause. Equality is identity.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class FeedFailure : HttpFailure {
    /**
     * Creates a failure without a cause.
     *
     * @param message the constant text of the type plus its reason's name.
     */
    protected constructor(message: String) : super(message)

    /**
     * Creates a failure with a lower-level cause. No leaf of this module uses it.
     *
     * @param message the constant text of the type plus its reason's name.
     * @param cause the lower-level throwable, or `null`.
     */
    protected constructor(message: String, cause: Throwable?) : super(message, cause)

    /**
     * The document is well-formed or empty or something else, but not a feed.
     *
     * **Stable Core.** Message: `The document is not a feed: <reason>`.
     *
     * @param reason why it is not a feed.
     */
    public class NotAFeed(
        /** Why it is not a feed. */
        public val reason: NotAFeedReason,
    ) : FeedFailure("The document is not a feed: ${reason.name}")

    /**
     * The document is not well-formed XML, or the parser rejected it for a reason that is not one of the others.
     *
     * **Stable Core.** Message: `The feed document is malformed`.
     */
    public class Malformed : FeedFailure("The feed document is malformed")

    /**
     * The document was refused before or while it was read: it carries a DOCTYPE, or its prolog cannot be read the
     * way the parser reads it.
     *
     * **Stable Core.** Message: `The feed document was refused: <reason>`.
     *
     * @param reason why it was refused.
     */
    public class Refused(
        /** Why it was refused. */
        public val reason: FeedRefusal,
    ) : FeedFailure("The feed document was refused: ${reason.name}")

    /**
     * The document exceeds a [FeedLimits] bound that fails the parse.
     *
     * **Stable Core.** Message: `The feed document exceeds a limit: <limit>`.
     *
     * @param limit the bound that was exceeded.
     */
    public class LimitExceeded(
        /** The bound that was exceeded. */
        public val limit: FeedLimit,
    ) : FeedFailure("The feed document exceeds a limit: ${limit.name}")
}
