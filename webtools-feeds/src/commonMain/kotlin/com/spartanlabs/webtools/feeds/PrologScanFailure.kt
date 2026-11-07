package com.spartanlabs.webtools.feeds

/** `null` when a parser may run (the scan found no DOCTYPE and the document starts with markup); otherwise the failure that ends the parse. */
internal fun PrologScanResult.toFailureOrNull(): FeedFailure? = when (this) {
    PrologScanResult.NoDoctype -> null
    PrologScanResult.Empty -> FeedFailure.NotAFeed(NotAFeedReason.Empty)
    PrologScanResult.NotMarkup -> FeedFailure.NotAFeed(NotAFeedReason.NotXml)
    PrologScanResult.HtmlDocument -> FeedFailure.NotAFeed(NotAFeedReason.Html)
    PrologScanResult.IllFormed -> FeedFailure.Malformed()
    PrologScanResult.Doctype -> FeedFailure.Refused(FeedRefusal.Doctype)
    is PrologScanResult.Undecodable -> FeedFailure.Refused(
        when (reason) {
            UndecodableReason.WideOrEbcdicPrefix, UndecodableReason.UnlistedEncodingName -> FeedRefusal.UnsupportedEncoding
            UndecodableReason.PrologTooLong -> FeedRefusal.PrologTooLong
            UndecodableReason.MalformedSequence -> FeedRefusal.AmbiguousProlog
        },
    )
}
