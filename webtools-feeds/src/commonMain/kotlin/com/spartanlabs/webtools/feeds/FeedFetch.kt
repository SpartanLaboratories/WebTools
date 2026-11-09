package com.spartanlabs.webtools.feeds

import com.spartanlabs.webtools.http.Url

/**
 * The outcome of a successful fetch: [Fetched] or [NotModified]. **Stable Core.** The two are the whole contract, so
 * a `when` over a [FeedFetch] needs no `else`. Whichever you got, **store [validators] first**, then adopt or ignore
 * [permanentlyMovedTo], then use the feed on [Fetched].
 *
 * What the caller owns: storing the validators after every response (a `200` replaces both, a `304` replaces each
 * one it carries); dropping its validators when [finalUrl] differs from the one it stored with them (a validator
 * from one server must not reach another: a coincidental match returns a false `304`); deciding whether to adopt
 * [permanentlyMovedTo] (a fact about the redirect chain, not an instruction: adopt it only when the chain's first
 * request was `https`, or after the same value was reported on several polls, because one injected `301` over
 * cleartext would re-point the subscription); polling and back-off; the meaning of `410`; what to show on the first
 * poll; and which entries are new (`entries.filter { it.id !in seen }`; never by date).
 */
public sealed interface FeedFetch {
    /** The validators to store: what the next `FeedFetcher.fetch` should send. */
    public val validators: FeedValidators

    /** The URL the response came from: the last URL requested. */
    public val finalUrl: Url

    /** The target of the last hop of the leading run of `301`/`308` redirects, or `null` when there is none or it equals the requested URL. */
    public val permanentlyMovedTo: Url?

    /**
     * A new representation (`200` or `203`), parsed.
     *
     * **Stable Core.** Equality is on every field; `toString()` prints the entry count and presence, and URLs as
     * scheme, host and port.
     *
     * @param feed the parsed feed.
     * @param validators the response's validators, [FeedValidators.None] when it carried neither.
     * @param finalUrl the URL the response came from.
     * @param permanentlyMovedTo the permanent move the chain reports, or `null`.
     */
    public class Fetched(
        /** The parsed feed. */
        public val feed: Feed,
        override val validators: FeedValidators,
        override val finalUrl: Url,
        override val permanentlyMovedTo: Url?,
    ) : FeedFetch {
        /** Two [Fetched]s are equal when their [feed], [validators], [finalUrl] and [permanentlyMovedTo] are equal. */
        override fun equals(other: Any?): Boolean =
            other is Fetched && other.feed == feed && other.validators == validators && other.finalUrl == finalUrl &&
                other.permanentlyMovedTo == permanentlyMovedTo

        /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
        override fun hashCode(): Int = listOf(feed, validators, finalUrl, permanentlyMovedTo).fold(0) { acc, v -> 31 * acc + v.hashCode() }

        /**
         * A diagnostic string for logs: the number of entries in [feed], the [validators] (presence only), and
         * [finalUrl] and [permanentlyMovedTo] as scheme, host and port. Its format is not part of the API and may
         * change.
         */
        override fun toString(): String =
            "Fetched(entries=${feed.entries.size}, validators=$validators, finalUrl=${finalUrl.origin()}, " +
                "permanentlyMovedTo=${permanentlyMovedTo?.origin() ?: "none"})"
    }

    /**
     * The stored representation is current (`304` to a conditional request). [validators] are the sent ones
     * refreshed by what the `304` carried.
     *
     * **Stable Core.** Equality is on every field; `toString()` prints presence and URLs as scheme, host and port.
     *
     * @param validators the sent validators, each replaced by the one the `304` carried.
     * @param finalUrl the URL the response came from.
     * @param permanentlyMovedTo the permanent move the chain reports, or `null`.
     */
    public class NotModified(
        override val validators: FeedValidators,
        override val finalUrl: Url,
        override val permanentlyMovedTo: Url?,
    ) : FeedFetch {
        /** Two [NotModified]s are equal when their [validators], [finalUrl] and [permanentlyMovedTo] are equal. */
        override fun equals(other: Any?): Boolean =
            other is NotModified && other.validators == validators && other.finalUrl == finalUrl && other.permanentlyMovedTo == permanentlyMovedTo

        /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
        override fun hashCode(): Int = listOf(validators, finalUrl, permanentlyMovedTo).fold(0) { acc, v -> 31 * acc + v.hashCode() }

        /**
         * A diagnostic string for logs: the [validators] (presence only), and [finalUrl] and [permanentlyMovedTo] as
         * scheme, host and port. Its format is not part of the API and may change.
         */
        override fun toString(): String =
            "NotModified(validators=$validators, finalUrl=${finalUrl.origin()}, permanentlyMovedTo=${permanentlyMovedTo?.origin() ?: "none"})"
    }
}
