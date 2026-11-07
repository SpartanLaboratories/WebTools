package com.spartanlabs.webtools.feeds

import com.rometools.rome.feed.WireFeed
import com.rometools.rome.feed.module.DCModule
import com.rometools.rome.feed.synd.SyndEnclosure
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.spartanlabs.webtools.http.Url

/** What Rome read: the wire bean (for the Atom `xml:base` values) and its synthetic view. Nothing on either is cloned, printed or compared. */
internal class RomeRead(val wire: WireFeed, val feed: SyndFeed)

/** Runs the Rome region: the `XmlReader` recipe, the hardened build and the synthetic view; every exception it throws is mapped. */
internal fun readWithRome(document: ByteArray, contentType: String?, limits: FeedLimits): Result<RomeRead> =
    inRomeRegion {
        openXmlReader(document, contentType).use { reader ->
            val wire = HardenedWireFeedInput(limits).build(reader)
            RomeRead(wire, SyndFeedImpl(wire, false))
        }
    }

/**
 * `LimitExceeded(Entries)` when the document holds more entries than [FeedLimits.maxEntries], before any is converted;
 * otherwise the feed. A throw out of here is a defect of this module, not of the document.
 */
internal fun RomeRead.toFeed(limits: FeedLimits, baseUrl: Url?): Result<Feed> {
    val entries = feed.entries.orEmpty()
    if (entries.size > limits.maxEntries) return Result.failure(FeedFailure.LimitExceeded(FeedLimit.Entries))
    // Only the Atom wire beans expose xml:base, and only on the feed and the entry; each narrows its parent's base.
    val atom = wire as? com.rometools.rome.feed.atom.Feed
    val feedBase = narrowBase(baseUrl?.toString(), atom?.xmlBase, limits.maxLinkChars)
    val atomEntries = atom?.entries.orEmpty()
    // Read into locals before the builder lambda: its property names shadow the bean's getters.
    val titleRaw = feed.title
    val descriptionRaw = feed.description
    val linkRaw = feedLinkRaw(feed)
    val converted = entries.mapIndexed { i, entry ->
        entry.toFeedEntry(narrowBase(feedBase, atomEntries.getOrNull(i)?.xmlBase, limits.maxLinkChars), limits)
    }
    return Result.success(
        Feed {
            title = cleanText(titleRaw, limits.maxTextChars)
            link = resolveLink(linkRaw, feedBase, limits.maxLinkChars)
            description = cleanText(descriptionRaw, limits.maxTextChars)
            this.entries = converted
        },
    )
}

// For an Atom feed Rome's getLink() is the rel="self" URL when one exists, so the first alternate is read from getLinks().
private fun feedLinkRaw(feed: SyndFeed): String? =
    if (feed.feedType.orEmpty().startsWith("atom")) feed.links.orEmpty().firstOrNull { it.rel == null || it.rel == "alternate" }?.href
    else feed.link

private fun SyndEntry.toFeedEntry(entryBase: String?, limits: FeedLimits): FeedEntry {
    val guid = uri
    val rawLink = link
    val titleRaw = title
    val summaryRaw = description?.value
    val contentRaw = contents.orEmpty().firstOrNull()?.value
    val authorRaw = author
    val enclosureRaw = enclosures.orEmpty()
    val published = publishedDate?.time?.takeIf(::inSupportedYears)
    val updated = updatedDate?.time?.takeIf(::inSupportedYears)
    val dcSubjects = (getModule(DCModule.URI) as? DCModule)?.subjects.orEmpty().map { it.value }
    val categoriesRaw = categories.orEmpty().map { it.name ?: it.label } + dcSubjects
    val id = entryId(guid, rawLink, titleRaw, summaryRaw, contentRaw, enclosureRaw.firstOrNull()?.url, published, limits.maxIdChars)
    // A link equal to the guid text is the link Rome derives from a permalink guid: a link only when already absolute.
    val linkValue = resolveLink(rawLink, entryBase, limits.maxLinkChars, allowRelative = !(rawLink != null && rawLink == guid))
    return FeedEntry(id) {
        title = cleanText(titleRaw, limits.maxTextChars)
        link = linkValue
        summary = cleanText(summaryRaw, limits.maxTextChars)
        content = cleanText(contentRaw, limits.maxTextChars)
        author = cleanText(authorRaw, limits.maxTextChars)
        publishedAtEpochMillis = published
        updatedAtEpochMillis = updated
        categories = categoriesRaw.mapNotNull { cleanText(it, limits.maxTextChars) }.distinct().take(limits.maxCategoriesPerEntry)
        enclosures = enclosureRaw.mapNotNull { it.toFeedEnclosure(entryBase, limits) }.distinctBy { it.url }.take(limits.maxEnclosuresPerEntry)
    }
}

// Dropped when its URL fails the link rule; a length of zero or less is unknown.
private fun SyndEnclosure.toFeedEnclosure(entryBase: String?, limits: FeedLimits): FeedEnclosure? {
    val resolved = resolveLink(url, entryBase, limits.maxLinkChars) ?: return null
    val typeRaw = type
    val lengthValue = enclosureLength(length)
    return FeedEnclosure(resolved) {
        mimeType = cleanText(typeRaw, limits.maxTextChars)
        lengthBytes = lengthValue
    }
}
