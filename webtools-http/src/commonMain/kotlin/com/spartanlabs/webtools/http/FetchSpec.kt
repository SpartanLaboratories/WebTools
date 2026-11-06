package com.spartanlabs.webtools.http

import kotlin.time.Duration

/** The per-call settings of [UrlFetcher.fetch]: the receiver of its lambda. **Stable Core.** Seeded; not thread-safe. */
@WebToolsDsl
public class FetchSpec internal constructor(maxWait: Duration) {
    /** The budget units this fetch charges; 1 by default. At least 1. */
    public var cost: Int = 1

    /** The lane this fetch is charged to; [Lane.Interactive] by default. */
    public var lane: Lane = Lane.Interactive

    /** The longest the fetch waits for a budget permit; the fetcher's `defaultMaxWait` by default. Not negative. */
    public var maxWait: Duration = maxWait
}
