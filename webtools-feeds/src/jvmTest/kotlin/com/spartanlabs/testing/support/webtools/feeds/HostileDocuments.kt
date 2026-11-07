package com.spartanlabs.testing.support.webtools.feeds

/**
 * Hostile and control documents for the hardening tests, each a pure function returning a fresh `ByteArray`. The 2 MiB shapes are
 * built by a fixed-capacity writer (a head, a unit repeated while it fits in [CAPACITY], a tail), so the child JVM generates them
 * itself and no large array crosses a process boundary. [byName] maps the child-JVM case names to their documents.
 */
object HostileDocuments {
    /** The transport's decoded-body cap under an `Untrusted` policy: 2 MiB. */
    const val CAPACITY: Int = 2 * 1024 * 1024

    private const val RSS_HEAD = "<rss version=\"2.0\"><channel><title>t</title>"
    private const val RSS_TAIL = "</channel></rss>"
    private const val ATOM_HEAD = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>t</title>"
    private const val ATOM_TAIL = "</feed>"

    /** [head], then [unit] repeated while the whole stays within [capacity] bytes, then [tail]; ASCII throughout. */
    fun fill(head: String, unit: String, tail: String, capacity: Int = CAPACITY): ByteArray {
        val room = capacity - head.length - tail.length
        require(room >= 0) { "the head and the tail alone exceed the capacity" }
        val count = room / unit.length
        return build(head, unit, count, tail)
    }

    /** [head], [unit] exactly [count] times, [tail]; ASCII throughout. */
    fun build(head: String, unit: String, count: Int, tail: String): ByteArray {
        val out = ByteArray(head.length + unit.length * count + tail.length)
        var at = 0
        for (c in head) out[at++] = c.code.toByte()
        val unitBytes = ByteArray(unit.length) { unit[it].code.toByte() }
        repeat(count) { unitBytes.copyInto(out, at); at += unitBytes.size }
        for (c in tail) out[at++] = c.code.toByte()
        return out
    }

    //region element floods
    /** `<item/>` repeated to the cap: the worst memory shape, every item a Rome bean. */
    fun itemFlood(): ByteArray = fill(RSS_HEAD, "<item/>", RSS_TAIL)

    /** Atom `<entry/>` repeated to the cap. */
    fun entryFlood(): ByteArray = fill(ATOM_HEAD, "<entry/>", ATOM_TAIL)

    /** `<a/>` repeated to the cap as children of the channel. */
    fun aFloodChannel(): ByteArray = fill(RSS_HEAD, "<a/>", RSS_TAIL)

    /** `<a/>` repeated to the cap inside one item. */
    fun aFloodItem(): ByteArray = fill("$RSS_HEAD<item>", "<a/>", "</item>$RSS_TAIL")

    /** `<a/>x` (an element and a text node) repeated to the cap. */
    fun aTextFlood(): ByteArray = fill(RSS_HEAD, "<a/>x", RSS_TAIL)

    /** `rss`, `channel` and then `<e/>` so the document holds exactly [total] elements. */
    fun elements(total: Int): ByteArray = build("<rss version=\"2.0\"><channel>", "<e/>", total - 2, RSS_TAIL)
    //endregion

    //region attributes
    private fun element(attributes: Int, prefix: String = "a"): String = buildString {
        append("<e")
        for (i in 0 until attributes) append(' ').append(prefix).append(i).append("=\"\"")
        append("/>")
    }

    /** Elements of [perElement] attributes each, repeated to the cap. */
    fun attributesPerElement(perElement: Int): ByteArray = fill(RSS_HEAD, element(perElement), RSS_TAIL)

    /** One element with [count] attributes, in a small document. */
    fun attributesOnOneElement(count: Int): ByteArray = (RSS_HEAD + element(count) + RSS_TAIL).encodeToByteArray()

    /** One element with [count] `xmlns:pN` declarations: the JDK counts them as attributes. */
    fun xmlnsFloodOnOneElement(count: Int): ByteArray = buildString {
        append(RSS_HEAD).append("<e")
        for (i in 0 until count) append(" xmlns:p").append(i).append("=\"urn:p:").append(i).append('"')
        append("/>").append(RSS_TAIL)
    }.encodeToByteArray()
    //endregion

    //region nesting
    /** `rss`, `channel`, then `<a>` nested so the deepest element is at depth [depth] (the root is depth 1). */
    fun rssNested(depth: Int): ByteArray {
        val levels = depth - 2
        val head = "<rss version=\"2.0\"><channel>"
        return (head + "<a>".repeat(levels) + "</a>".repeat(levels) + RSS_TAIL).encodeToByteArray()
    }

    /** An RSS document nested 290,000 levels (2.03 MB): the deepest document that fits the cap. */
    fun rssNestedToCap(): ByteArray = rssNested(290_000)

    /** An Atom entry whose `xhtml` content nests `<b>` [depth] deep: the shape that recurses in JDOM's serialiser. */
    fun atomXhtmlNested(depth: Int): ByteArray = (
        ATOM_HEAD + "<entry><id>urn:e</id><title>t</title><content type=\"xhtml\"><div xmlns=\"http://www.w3.org/1999/xhtml\">" +
            "<b>".repeat(depth) + "x" + "</b>".repeat(depth) + "</div></content></entry>" + ATOM_TAIL
        ).encodeToByteArray()

    /** An RSS item whose description holds markup nested [depth] deep: the other serialiser shape. */
    fun rssDescriptionNested(depth: Int): ByteArray =
        (RSS_HEAD + "<item><title>t</title><description>" + "<b>".repeat(depth) + "x" + "</b>".repeat(depth) + "</description></item>" + RSS_TAIL).encodeToByteArray()
    //endregion

    //region namespaces
    /** Elements each declaring a distinct namespace URI, repeated to the cap (far more than 64 URIs). */
    fun xmlnsDistinct(): ByteArray = buildString {
        append(RSS_HEAD)
        var i = 0
        while (length < CAPACITY - 64) { append("<e xmlns:n=\"urn:ns:").append(i).append("\"/>"); i++ }
        append(RSS_TAIL)
    }.encodeToByteArray()

    /** 99,000 elements all declaring one namespace URI: under the element cap and one distinct URI. */
    fun xmlnsRepeated(): ByteArray = build("<rss version=\"2.0\"><channel>", "<e xmlns:n=\"urn:ns:1\"/>", 99_000, RSS_TAIL)

    /** [count] elements each declaring its own URI, in a small document. */
    fun namespaces(count: Int): ByteArray = buildString {
        append(RSS_HEAD)
        for (i in 0 until count) append("<e xmlns:n").append(i).append("=\"urn:ns:").append(i).append("\"/>")
        append(RSS_TAIL)
    }.encodeToByteArray()
    //endregion

    //region text, ids and entries
    /** One description text node filling the document to the cap. */
    fun textNode(): ByteArray = fill("$RSS_HEAD<item><title>t</title><description>", "x", "</description></item>$RSS_TAIL")

    /** One item whose guid fills the document to the cap: far over `maxIdChars`, so the id falls back. */
    fun idToCap(): ByteArray = fill("$RSS_HEAD<item><guid>", "g", "</guid><title>t</title></item>$RSS_TAIL")

    /** [count] items with a guid, a title and a link. */
    fun items(count: Int): ByteArray = buildString {
        append(RSS_HEAD)
        for (i in 1..count) append("<item><guid isPermaLink=\"false\">urn:item:").append(i).append("</guid><title>Item ").append(i).append("</title><link>https://feeds.example.test/").append(i).append("</link></item>")
        append(RSS_TAIL)
    }.encodeToByteArray()

    /** [count] items with an HTML description of about 3 KB each: the realistic control (400 items is about 1.3 MB). */
    fun realistic(count: Int): ByteArray = buildString {
        append(RSS_HEAD)
        val paragraph = "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. <a href=\"https://feeds.example.test/x\">link</a></p>"
        for (i in 1..count) {
            append("<item><guid isPermaLink=\"false\">urn:item:").append(i).append("</guid><title>Item ").append(i).append("</title><link>https://feeds.example.test/").append(i)
                .append("</link><pubDate>Fri, 06 Nov 2026 04:25:27 GMT</pubDate><description>")
            repeat(18) { append(paragraph.replace("<", "&lt;").replace(">", "&gt;")) }
            append("</description></item>")
        }
        append(RSS_TAIL)
    }.encodeToByteArray()
    //endregion

    /** The child-JVM cases by name, each with the document and the result line every parse must print. */
    val byName: Map<String, Pair<() -> ByteArray, String>> = linkedMapOf(
        "item-flood" to Pair({ itemFlood() }, "LimitExceeded:Elements"),
        "entry-flood" to Pair({ entryFlood() }, "LimitExceeded:Elements"),
        "a-flood-channel" to Pair({ aFloodChannel() }, "LimitExceeded:Elements"),
        "a-flood-item" to Pair({ aFloodItem() }, "LimitExceeded:Elements"),
        "a-text-flood" to Pair({ aTextFlood() }, "LimitExceeded:Elements"),
        "attributes-10000" to Pair({ attributesPerElement(10_000) }, "LimitExceeded:Attributes"),
        "attributes-100" to Pair({ attributesPerElement(100) }, "ok:0"),
        "attributes-101" to Pair({ attributesPerElement(101) }, "LimitExceeded:Attributes"),
        "rss-nested-290000" to Pair({ rssNestedToCap() }, "LimitExceeded:Depth"),
        "atom-xhtml-5000" to Pair({ atomXhtmlNested(5_000) }, "LimitExceeded:Depth"),
        "rss-description-5000" to Pair({ rssDescriptionNested(5_000) }, "LimitExceeded:Depth"),
        "xmlns-distinct" to Pair({ xmlnsDistinct() }, "LimitExceeded:NamespaceUris"),
        "xmlns-repeated" to Pair({ xmlnsRepeated() }, "ok:0"),
        "text-2mib" to Pair({ textNode() }, "ok:1"),
        "realistic-400" to Pair({ realistic(400) }, "ok:400"),
        "items-1000" to Pair({ items(1_000) }, "ok:1000"),
        "items-1001" to Pair({ items(1_001) }, "LimitExceeded:Entries"),
        "id-2mib" to Pair({ idToCap() }, "ok:1"),
    )
}
