package com.spartanlabs.testing.support.webtools.feeds

/** One pinned digest-id vector: the inputs of `digestId` and the id they must give. */
class DigestVector(
    val name: String,
    val title: String?,
    val summary: String?,
    val content: String?,
    val enclosureUrl: String?,
    val publishedMillis: Long?,
    val expectedId: String,
)

/** The fourteen digest vectors, reproduced by an independent implementation of the message layout `digestId` documents. */
object DigestVectors {
    private const val P = "webtools-feeds:v1:"

    /** The id of an entry with nothing to hash: the digest of the domain string alone. */
    const val NOTHING_ID: String = P + "b2025d24e4ca603fe646f1d2df2ea6e5"

    val all: List<DigestVector> = listOf(
        DigestVector("title Hello, published 1700000000000", "Hello", null, null, null, 1_700_000_000_000L, P + "ee7d21b287ec8fde2600e24ba184edb7"),
        DigestVector("title Hello only", "Hello", null, null, null, null, P + "606a3e59bef6bab41ee0dc4fb55c6a91"),
        DigestVector("published 1700000000000 only", null, null, null, null, 1_700_000_000_000L, P + "af8884cd9f65043ea519a839ae35e5b1"),
        DigestVector("summary only", null, "<p>Only a description</p>", null, null, null, P + "90fd9202293c92ac61daf3e57745fb0c"),
        DigestVector("content, published 0", null, null, "<p>Only a description</p>", null, 0L, P + "284baaab7f01943e412980249d696f09"),
        DigestVector("first enclosure URL only", null, null, null, "https://cdn.example/a.mp3", null, P + "86aa708c73f6e3fc1b4b4e17b2a4d3ef"),
        DigestVector("nothing", null, null, null, null, null, NOTHING_ID),
        DigestVector("title ab", "ab", null, null, null, null, P + "de945a20e41d0f43a4d8b7a2e763a1ba"),
        DigestVector("title e-acute precomposed", "é", null, null, null, null, P + "40ddd7a069f7780b2723331137b35874"),
        DigestVector("title e-acute decomposed", "é", null, null, null, null, P + "b67b156cf285b5bbeac4361c43602ccd"),
        DigestVector("title grinning face, published -1", "😀", null, null, null, -1L, P + "b6f7bc39c6113932f56840b0937abe2f"),
        DigestVector("title lone high surrogate then x", "\uD800x", null, null, null, null, P + "c51027fc4e7d3fe5a98627c7879e584b"),
        DigestVector("title x then lone low surrogate", "x\uDC00", null, null, null, null, P + "3af1540093033c6bb15856e8f7ac51b8"),
        DigestVector("title 4095 t then grinning face", "t".repeat(4_095) + "😀", null, null, null, null, P + "ebcad2304f198474c84e3bfbe451c7ca"),
    )
}
