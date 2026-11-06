# Driving Rome 2.1.0 from `RomeFeedParser`: entry point, failures, limits and bean mapping

Measured on 2026-11-06 with `rome` 2.1.0 (commit `7e5cf653d8a9e64f484efec35f457615a686f2ff`), `rome-utils` 2.1.0, `jdom2` 2.0.6.1 (tag `JDOM-2.0.6.1`, commit `c6065f684bdff5cebf5f23b2859c5e4c89fd89db`) and `slf4j` 2.0.13, on Oracle JDK 23.0.2 (Windows x64). Where a result could depend on the JDK it was repeated on OpenJDK 11.0.32.1, 17.0.20 and 21.0.10; each such finding says which JDKs it covers. "Measured" means a probe was run in this work and its output read; "code reading" means the source at the named tag was read and nothing was run.

## Question

How should the supplied parser (`RomeFeedParser`, JVM only) drive Rome 2.1.0 (which entry point, which hooks, which settings), what does each step throw for a bad or hostile document and how does that map onto `Malformed`, `NotAFeed`, `Refused` and `LimitExceeded`, and how do Rome's `SyndFeed` and `SyndEntry` beans map onto the feed model (`Feed`, `FeedEntry`, `FeedEnclosure`) for RSS 0.90 to 2.0, RSS 1.0, Atom 0.3 and Atom 1.0? The answer has to hold under: no Rome type in a public signature; `parse` a plain non-suspending function called from several threads; the module's own prolog scan running first and Rome's DOCTYPE ban as the second lock; a Java 11 bytecode floor with tests on the build JDK; no failure message carrying document content.

## Bottom line

- **Do not use `SyndFeedInput`, and do not call `super.createSAXBuilder()`.** Subclass `WireFeedInput` (its `createSAXBuilder()` is a protected hook returning Rome's public `SAXBuilder`), build the `SAXBuilder` yourself over a private JDK SAX factory (`SAXParserFactory.newDefaultInstance()`, created per build), set Rome's four hardening features explicitly, and wrap the result with `SyndFeedImpl(wireFeed, false)`. Feed it with `build(Reader)` (an `XmlReader`) so the ampersand/entity repair and the encoding detection both apply. Rome's own `createSAXBuilder()` uses JDOM's process-wide `NONVALIDATING` SAX factory. On JDK 22 and later, JAXP limits set on a parser from that factory persist in the factory and alter every later Rome/JDOM parse in the JVM (measured: a plain `WireFeedInput` elsewhere started failing at 100 attributes), they race between concurrent builds (2,806 wrong outcomes in 6,400 parses with two limit settings in use), and with Xerces 2.12.2 on the classpath the JDK-specific limit properties are not recognised, so every document, valid ones included, becomes `Malformed`. The private-factory route had none of these faults.
- **Depth is a CPU limit before it is a stack limit, and the JDK can enforce it.** JDOM's builder is quadratic in nesting depth (150,000 levels: 64.7 s; 290,000 levels, the deepest a 2 MiB document holds: still running at the 420 s timeout; no cancellation exists), so two such documents pin both parse permits for minutes. Setting `maxElementDepth` 256 and `elementAttributeLimit` 100 on the private parser rejects the 290,000-level document in 64 ms. Use the property names `http://www.oracle.com/xml/jaxp/properties/maxElementDepth` and `.../elementAttributeLimit`: the `jdk.xml.*` spellings are not recognised on OpenJDK 11.0.32.1 (every parse fails there), while the URL spellings work on 11, 17, 21 and 23. Keep a `StackOverflowError` catch as a backstop only; with the cap it is unreachable down to a 128 KiB thread stack at depth 128 and to 256 KiB at depth 256.
- **`XmlReader` needs a small recipe around it.** The plain `XmlReader(is, contentType, true, "UTF-8")` fails or corrupts in seven of 26 encoding vectors (UTF-16 with a BOM or a conflicting charset, an unsupported charset in the header, a leading newline before an ISO-8859-1 declaration); a recipe of three steps (drop leading ASCII whitespace, use the raw constructor for UTF-16 input, retry without the header when the constructor throws `UnsupportedEncodingException`) passed all 26 on JDK 23 and JDK 11.
- **Map exceptions by type and by the documented JAXP code, never by message.** `ParsingFeedException` is the outer wrapper of every XML error; a `SAXParseException` in its cause chain starting `JAXP0001000x` is a limit; `IllegalArgumentException("Invalid document")` is `NotAFeed` only when the class is exactly `IllegalArgumentException` (a `NumberFormatException` for `<cloud port="abc">` is an `IllegalArgumentException` subclass that escapes unwrapped and carries document text). Every other `RuntimeException` out of the Rome region is `Malformed` with no cause kept.
- **The mapping needs more than `SyndEntry` getters.** Rome trims nothing, gives `""` for an empty guid or id, falls back to the guid as the link even when it is not a URL, returns the `rel="self"` URL as an Atom 1.0 feed link, leaves relative links and `xml:base` unresolved, drops `dc:subject` from `SyndEntry.getCategories()` (RSS 1.0 has no other categories), returns enclosure length 0 for absent or garbled values and a negative length as written, and lets `DateParser` return absurd years or microsecond misreads for some inputs. The conversion code owns each of these.
- **Against the architecture's description this changes:** the entry point (private `WireFeedInput` subclass, not `SyndFeedInput`); the encoding step (recipe, not the plain constructor); the exception table (adds `IOException`, exact-class `IllegalArgumentException`, JAXP codes, a broad `RuntimeException` rule); the depth rule (a JDK limit at 256 does the work, `StackOverflowError` becomes a backstop); two JDK limits and a per-build private factory; `dc:subject` as a category source. The prolog scan and the DOCTYPE ban stay as designed.

## Findings

### F1. Entry points and hooks (code reading + measured; Rome 2.1.0, JDK 23.0.2)

| Route | `XmlFixerReader` repair | `XmlReader` detection | Can set JAXP limits | What was measured |
|---|---|---|---|---|
| A. `SyndFeedInput.build(XmlReader)` | yes | yes | no: `SyndFeedInput` keeps a `private final WireFeedInput feedInput`, there is no hook | works; no limits beyond the JDK defaults (depth unlimited, 10,000 attributes) |
| B. subclass of `WireFeedInput` overriding `createSAXBuilder()` and calling `super`, then `build(XmlReader)` | yes | yes | yes, on Rome's `SAXBuilder` | works on JDK 17, 21, 23 with the `jdk.xml.*` names; fails on JDK 11 (F2, F3) |
| C. own JDOM `SAXBuilder` over the bytes, then `WireFeedInput.build(Document)` | no | no (the JDK parser's own detection: BOM, declaration, UTF-8; the header is ignored) | yes | a bare `&` is a `JDOMParseException`; `&nbsp;` is "referenced, but not declared"; a newline or comment before `<?xml` is "The processing instruction target matching "[xX][mM][lL]" is not allowed"; an ISO-8859-1 body with no declaration is an invalid-UTF-8 error |
| D. subclass of `WireFeedInput` with its own `SAXBuilder` over a private factory, then `build(XmlReader)` | yes | yes | yes | the recommended route (F3, F4) |

- The hook exists in 2.1.0: `WireFeedInput.createSAXBuilder()` is `protected`, documented "Creates and sets up a org.jdom2.input.SAXBuilder for parsing", and returns `com.rometools.rome.io.SAXBuilder`, a public class with a public constructor taking an `XMLReaderJDOMFactory`.
- The repair runs only inside `build(File)` and `build(Reader)`; the `WireFeedInput` Javadoc says so. `build(InputSource)` and `build(Document)` skip it (route C above shows the consequence).
- `WireFeedInput.build(Reader)` creates a new `SAXBuilder` on every call and wraps `JDOMParseException` as `ParsingFeedException("Invalid XML: " + message)`, rethrows `IllegalArgumentException` unchanged, and wraps any other `Exception` as `ParsingFeedException("Invalid XML", cause)`. `Error`s pass through.

### F2. Rome's own `createSAXBuilder()` shares a JDK security manager between builds on JDK 22+ (code reading + measured)

- Rome's `createSAXBuilder()` builds `new SAXBuilder(XMLReaders.NONVALIDATING)`. In JDOM 2.0.6.1 `XMLReaders.NONVALIDATING` is backed by an enum singleton holding one `SAXParserFactory` created by `SAXParserFactory.newInstance()`; every build calls `newSAXParser()` on it.
- In the JDK, `SAXParserImpl` takes `fSecurityManager = spf.fSecurityManager` from the factory in JDK 22 and 23 (`jdk-22-ga`, `jdk-23-ga`), and `JAXPSAXParser.setProperty` applies a limit property to that object. In JDK 11, 17, 19, 20 and 21 each parser creates its own (`new XMLSecurityManager(secureProcessing)`). So the sharing is a JDK 22+ behaviour.
- Measured on JDK 23.0.2, fresh JVM: a plain `WireFeedInput` parsed a 150-attribute document; one build through route B with `elementAttributeLimit` 100 failed with `JAXP00010002`; the next plain `WireFeedInput` build, which sets nothing, failed the same way ("limit 100"); a separate `SAXParserFactory.newInstance()` parser was unaffected. On JDK 17.0.20 and 21.0.10 the same sequence did not leak. The private-factory route (D) did not leak on any of them.
- Concurrency, JDK 23.0.2: 16 threads, 400 builds each, even threads with limit 50 (must reject a 60-attribute document), odd threads with limit 5,000 (must accept): route B gave 2,806 wrong outcomes of 6,400, route D 0 of 6,400. The adapter itself would use one setting, so its own builds would not disagree; the harm is to every other Rome or JDOM user in the same JVM, and to any second `RomeFeedParser` with different limits.
- Xerces on the classpath, JDK 23.0.2 with `xercesImpl` 2.12.2 and `xml-apis` 1.4.01 (jar SHA-1s checked against Maven Central): `SAXParserFactory.newInstance()` returned `org.apache.xerces.jaxp.SAXParserFactoryImpl`, `newDefaultInstance()` the JDK's. Route B then failed every document, valid ones included, with "jdk.xml.elementAttributeLimit property jdk.xml.elementAttributeLimit not recognized for SAX driver org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser" (JDOM fails closed on an unrecognised property, so it is `Malformed`, not a bypass). Route D parsed valid feeds and refused the DOCTYPE.
- A thread whose context class loader is `null`, or sees nothing (`URLClassLoader` with no URLs and no parent): route D and route B parsed correctly (the JDK falls back to its default provider). `SAXParserFactory.newInstance()` consults the context class loader through `ServiceLoader` for the provider; `newDefaultInstance()` does not look anything up.

### F3. The private-factory route and what a hostile document reaches (measured; JDK 23.0.2 unless stated)

Route D, in Java terms (Kotlin form in the recommendation): a `WireFeedInput` subclass whose `createSAXBuilder()` returns `new com.rometools.rome.io.SAXBuilder(factory)`, where `factory` is an `XMLReaderJDOMFactory` creating `SAXParserFactory.newDefaultInstance()` (namespace aware, not validating), with `disallow-doctype-decl` true, `external-general-entities` false, `external-parameter-entities` false, `load-external-dtd` false, `setExpandEntities(false)`, an entity resolver returning an empty `InputSource`, and the two limit properties.

- Setting `disallow-doctype-decl` directly (not through Rome's `isFeatureSupported` guard) means a provider that does not recognise it makes the build throw (`JDOMException` "feature ... not recognized"), so the second lock cannot be silently absent. Rome's guard skips an unrecognised feature.
- Loopback request count: a `ServerSocket` on 127.0.0.1 counted connections while routes B, D and a plain `WireFeedInput` parsed seven documents: `xi:include` (text and XML, in a title and as a child of the root), an `xml-stylesheet` processing instruction with `xsi:schemaLocation`, a DOCTYPE with an external DTD, a DOCTYPE with an external general entity, a DOCTYPE with an external parameter entity, and a feed whose `xml:base`, link and enclosure point at the loopback address. Connections: 0 in every case on every route (JDK 23.0.2). `xi:include` is not processed: the element is ignored and the surrounding title is `""`.
- A DOCTYPE of any kind (internal subset, external DTD, HTML, XHTML) is refused by the JDK parser with a `SAXParseException` whose text contains the feature URL `disallow-doctype-decl`; that URL stays in the text under German, Japanese and English locales (the sentence around it is localised).
- First use: 40 fresh JVMs, each starting 24 threads at a barrier that each parse once through route D: 960 of 960 parses succeeded. One `WireFeedInput` instance shared by 24 threads over 12,000 parses of RSS 2.0, Atom 1.0 and RSS 1.0 documents returned every document's own data (0 wrong). The instance fields are `xmlHealerOn` and `allowDoctypes`, set before use; creating an instance per parse costs nothing and removes any reliance on that.
- `SAXParserFactory.newDefaultInstance()` has `FEATURE_SECURE_PROCESSING` true by default (measured); setting it explicitly changes nothing and costs one call.

### F4. `XmlReader` encoding detection, and a recipe that fixes its failures (code reading + measured; JDK 23.0.2 and 11.0.32.1)

What `XmlReader` 2.1.0 does (source; confirmed by 26 byte vectors):

- In lenient mode (`lenient = true`, required: strict mode throws `XmlReaderException` "Invalid MIME" for a null, `text/html` or `application/json` content type) the XML declaration's encoding **wins over the BOM and the header**: `calculateHttpEncoding` begins `if (lenient && xmlEnc != null) { encoding = xmlEnc; }`. The order "BOM, then header, then declaration" holds only in strict mode. The declaration is read only if the first four bytes look like `<?xm` in a known encoding (UTF-8, UTF-16 LE/BE, EBCDIC), so a leading space, newline or comment hides it.
- `text/xml` with no charset and no declaration decodes as US-ASCII unless a default encoding is passed (the four-argument constructor takes one). `XmlReader.setDefaultEncoding` is a static global.
- A charset that the JDK does not know, in the header or in the declaration, makes the **constructor** throw `UnsupportedEncodingException` (message: the charset name, i.e. document content), even for a valid UTF-8 body. Other constructor `IOException`s: "XML prolog or ROOT element not found on first 4096 bytes", "Unexpected end of XML stream", and the BOM/guess mismatch message.
- UTF-16LE (with or without a BOM) declaring `UTF-16`, a UTF-16 BOM with `text/xml` and no charset or with a disagreeing charset, and `charset=UTF-16` on a UTF-8 body all fail with "Content is not allowed in prolog" in the header-aware lenient path (measured); UTF-16BE with a BOM and a `UTF-16` declaration passes. The raw constructor `XmlReader(is, lenient, defaultEncoding)` (BOM, then declaration, then default; header ignored) decodes every UTF-16 vector.
- A leading newline, spaces or a comment before the declaration, with a body declared `ISO-8859-1`: decoded as UTF-8, U+FFFD for each non-ASCII byte, no error (measured).

The recipe (Java in the probe; Kotlin form below), 26 vectors, all as expected on JDK 23.0.2 and 11.0.32.1, against 7 failures for the plain `XmlReader(is, contentType, true, "UTF-8")`:

1. If the bytes begin with ASCII whitespace (SP, TAB, CR, LF) followed by an ASCII `<`, drop that whitespace (the repair would trim it anyway).
2. If the first bytes are a UTF-16 BOM (`FE FF`, `FF FE`) or the UTF-16 `<?` pattern (`00 3C 00 3F`, `3C 00 3F 00`): `XmlReader(ByteArrayInputStream(b), true, "UTF-8")`.
3. Otherwise `XmlReader(ByteArrayInputStream(b), contentType, true, "UTF-8")`; on `UnsupportedEncodingException` retry once with the raw constructor (header ignored); a second `UnsupportedEncodingException` is a declared-encoding defect, `Malformed`.

The vectors that still produce non-obvious text are correct under the specs and are listed in Pitfalls (a header charset contradicting a UTF-8 body wins; a UTF-8 BOM with an ISO-8859-1 declaration gives mojibake). The module's prolog scan must read the prolog the way this recipe decodes it; the order above is the exact one to mirror.

### F5. Exceptions (code reading + measured)

Observed for ~45 odd or hostile documents through routes A and B (JDK 23.0.2; JDK 11.0.32.1 identical except the JDK-specific text of `NullPointerException` messages and the property-name fault):

| Document | Thrown | Notes |
|---|---|---|
| JSON, junk before `<?xml`, binary | `ParsingFeedException` ← `JDOMParseException` ← `SAXParseException` "Content is not allowed in prolog" | JSON and junk are indistinguishable by message |
| empty, whitespace only, truncated, two roots, invalid character (U+0001), `&#1;` | `ParsingFeedException` (same chain) | |
| well-formed HTML without DOCTYPE | `IllegalArgumentException("Invalid document")` | |
| not-well-formed HTML without DOCTYPE | `ParsingFeedException` ("Open quote is expected for attribute ...") | |
| any DOCTYPE (HTML, XHTML, internal, external) | `ParsingFeedException`, cause text names `disallow-doctype-decl` | the second lock |
| unknown root, `<rss version="3.0">`, Atom without its namespace | exactly `IllegalArgumentException("Invalid document")` | `<rss>` with no `version` parses as `rss_2.0` |
| `<rss version="2.0"></rss>` (no channel) | `ParsingFeedException("Invalid XML")` ← `NullPointerException` | a raw NPE through route C |
| Atom `<link rel="self"/>` without `href` | `ParsingFeedException("Invalid XML")` ← `FeedException` ← `NullPointerException` | |
| RSS 0.92 `<cloud port="abc">` | **`NumberFormatException`**, unwrapped | an `IllegalArgumentException` subclass; message "For input string: ..." |
| `encoding="bogus-9"` in the declaration, or `charset=bogus-9` or `;;; charset=` in the header | `UnsupportedEncodingException` from the `XmlReader` constructor | before any parsing |
| `<?xml` never closed, or not closed in 4,096 bytes | `IOException` from the constructor | |
| JDK limit exceeded (depth, attributes, name length) | `ParsingFeedException` ← `JDOMParseException` ← `SAXParseException` "JAXP0001000x: ..." | code is locale-independent (checked under German, Japanese, Arabic) |
| deep markup inside RSS `<description>` or Atom `xhtml`/`xml` text constructs | `StackOverflowError`, uncaught by Rome | F6 |
| empty `<channel/>`, Atom without entries | success: null title, zero entries | |

- The JDK's documented codes are `JAXP00010001` entity expansion, `JAXP00010002` element attribute limit, `JAXP00010003` max entity size, `JAXP00010004` total entity size, `JAXP00010005` max XML name length, `JAXP00010006` max element depth, `JAXP00010007` entity replacement; the guide says applications "may check the error code".
- Messages of limit errors carry document text (`Element "x" has more than ...`, `The element "x" has a depth of ...`), and `NumberFormatException` carries the attribute value. The XML-error messages name elements and attributes (`element type "meta"`). No exception message or cause from the Rome region may be placed in a failure or logged.
- A DOCTYPE inside a document is refused by the module's prolog scan first; the JDK parser's refusal is the backstop, and a document reaching it still fails closed.
- Rome cannot tell JSON, a binary file or junk from malformed XML, and cannot tell not-well-formed HTML from malformed XML. The `NotAFeed` classification of HTML, JSON and other non-XML bodies must therefore come from the prolog scan or a first-byte check, not from Rome. Well-formed HTML with no DOCTYPE does reach Rome and is exactly `IllegalArgumentException("Invalid document")`.

### F6. Depth: CPU first, stack second (measured; JDK 23.0.2; stack figures from threads created with an explicit stack size)

- **CPU.** JDOM `ContentList.checkPreConditions` calls `Element.isAncestor` for each element added, and `isAncestor` walks the new parent's ancestors, so building is quadratic in depth. Unknown elements nested in an RSS `<item>` (7 bytes per level): JDK SAX alone 34 to 86 ms at every size tried; the JDOM build took 286 ms at 20,000 levels, 1.9 s at 50,000, 12.4 s at 100,000 and 64.7 s at 150,000 (1.05 MB); a 290,000-level document (2.03 MB, the deepest that fits the cap) was still building when a 420 s timeout killed it. Rome's parse and `SyndFeedImpl` conversion took 30 ms and 9 ms for the 150,000-level tree. A parse cannot be cancelled, so with two permits, two documents of 150,000 levels pin both for over a minute each.
- **With the cap.** `maxElementDepth` 256: the 290,000-level 2 MiB document throws `JAXP00010006` after 64 ms (with 128: 69 ms; at 128, depth 129 throws and depth 100 parses).
- **Stack.** `StackOverflowError` arises in JDOM's `XMLOutputter` (frames `AbstractXMLOutputProcessor.printElement` / `printContent`, `NamespaceStack.push`) when Rome serialises markup back to a string: RSS 0.92+ `<description>` containing child elements (`RSS092Parser` builds the string with `xmlOut.outputString`), and Atom text constructs of type `xhtml` or a `+xml`/`/xml` type (`Atom10Parser`, and `Atom03Parser` similarly). With a 1 MiB thread stack: description markup survives 1,750 levels, overflows at 2,000; Atom `xhtml` content survives 2,000 and overflows at 5,000. Foreign (unknown) elements at channel, item or entry level nested to 20,000 caused no overflow (CPU only). The three `XMLOutputter` call sites are the only recursion over content depth in Rome's parsing and conversion code that was found (code reading); no overflow occurred in the JDOM build, a Rome converter or `SyndFeedImpl`.
- **Margin of a cap.** Depth 128 passed with a 128 KiB stack, depth 256 with 256 KiB, depth 512 with 512 KiB (RSS description) and failed Atom `xhtml` at 256 KiB; depth 1,000 overflowed at 256 KiB. A cap of 256 therefore keeps at least a 7-fold margin on a default 1 MiB stack and still works on a 256 KiB stack. The boundary depends on stack size and JIT state, so a test must use nesting far above it.
- **What legitimate feeds need.** 24 downloaded feed files from 25 URLs on 2026-11-06 (BBC, Smashing, Planet Python, Slashdot, CSS-Tricks, JetBrains, Spring, Engadget, Daring Fireball, BleepingComputer, WordPress.com, the Go blog twice, Rust, GitHub, Cloudflare, Ars Technica, The Verge, Martin Fowler, LWN, Reddit, Hacker News, Phoronix, xkcd; the Kotlin blog URL did not return XML): maximum element depth 5, maximum attributes (including `xmlns`) on one element 10. None carried Atom `xhtml` content.

### F7. Attribute, name and entity limits (measured; JDK 23.0.2)

- Under the JDK defaults a single element may carry 10,000 attributes (the guide's default); the JDK counts `xmlns` declarations as attributes. JDOM's per-element attribute list is quadratic, so the default is a CPU lever: a 2,074,755-byte document of 21 elements with 9,990 attributes each took 3,996 ms with the defaults and was rejected immediately with `elementAttributeLimit` 100; ten elements with 9,990 `xmlns` declarations each took 1,940 ms with the defaults. A single element with 96,317 `xmlns` declarations (2.1 MB) is rejected by the default limit (10,000) and by a limit of 100.
- Message of an attribute violation: `JAXP00010002:  Element "x" has more than "100" attributes, "100" is the limit imposed by the JDK.` At the limit (100) the document parses; one over fails.
- Name length: the default is 1,000 (`JAXP00010005: The length of entity "[xml]" is "1,500" that exceeds the "1,000" limit set by "jaxp.properties"`); no setting is needed.
- `totalEntitySizeLimit` and `entityExpansionLimit`: with no DOCTYPE the only entities are the five predefined ones and character references, and neither limit changed anything (5,000 `&amp;` or `&#65;` references parsed with `totalEntitySizeLimit` 1,000 and `entityExpansionLimit` 10). With the DOCTYPE refused they cannot be reached; setting them is dead configuration. Their messages were not reproduced for that reason.

### F8. What the repair does to content (measured; JDK 23.0.2)

- Bare `&` is kept as text ("Q & A &"); `&nbsp;` and other known HTML entity names become `&#160;` and so on, **including inside CDATA** (an RSS item description `<![CDATA[x &nbsp; y & z]]>` came back as `x &#160; y & z`; route C, with no repair, returned `x &nbsp; y & z`). An unknown named entity (`&bogus;`) still fails the parse. Leading whitespace and comments before the declaration are removed.
- The repair never emits `<` or `!`, so it cannot create a DOCTYPE; that is for the prolog scan to rely on.

### F9. Dates: Rome's `DateParser` (measured; JDK 23.0.2; identical under `de`/`America/New_York`, `th`/`Asia/Tokyo`, `ar`/`Pacific/Kiritimati`; 8 threads × 20,000 × 2 parses, 0 mismatches)

- `WireFeedInput(false, Locale.US)` (the default) passes the US locale to every parse; no `SimpleDateFormat` is shared. `parseDate(null)` throws a `NullPointerException`, but the parsers never pass null.
- Parsed: RFC 822 with `GMT`, `UT`, `Z`, `UTC`, `EST`, `EDT`, `PST`, `-0400`, `+0200`; two-digit years; no seconds; a wrong weekday; W3C `Z`, `+02:00`, `-04:00`, fractions, date only, year-month, year only (GMT midnight).
- `null`: RFC 822 with no zone or with `-04:00`; `GMT+0200`; a trailing `(comment)`; W3C with no zone; a space instead of `T`; lowercase `z`; W3C `-0400`, `+0200`, `-04` (an Atom `published="2003-06-10T04:00:00-0400"` is `null`; this is Rome issue #695); `20030610T040000Z`.
- Wrong values: `2003-02-30` is 2 March; `2003-13-01` is 2004-01-01; hour 25 rolls into the next day; `.123456Z` reads microseconds as milliseconds (04:02:03.456); `20030610` and a digit-only epoch-seconds string parse as years 20030610 and 113888161. The conversion must range-check the year (1 to 9999) and drop anything else; it cannot detect the rolled-over cases.

### F10. Mapping Rome's beans to the model (measured per format with synthetic documents; JDK 23.0.2)

All strings are as written: Rome trims nothing (title `"  Chan Title "`, guid `"\n   g-7  \n"`, category `"  Tech  "`, Atom id `"  urn:ws  "`). All getters below are on `SyndFeed` / `SyndEntry`.

**Feed**

| Model field | RSS 0.90 to 2.0, RSS 1.0 | Atom 1.0 | Atom 0.3 |
|---|---|---|---|
| `title` | `getTitle()` (CDATA and entities decoded; null if absent) | `getTitle()`; type `xhtml` gives the serialised markup including the wrapper `div` | `getTitle()` |
| `link` | `getLink()` = `<channel><link>` as written | `getLink()` is the **`rel="self"` href when a self link exists**, else the first alternate; use `getLinks()` and pick the first with `rel` null or `alternate` | `getLink()` = the alternate href |
| `description` | `getDescription()` = channel description | `getDescription()` = `subtitle` (type `html` unescaped; `xhtml` as markup) | `getDescription()` = `tagline` |

**Entry**

| Model field | RSS 0.90 | RSS 0.91 | RSS 0.92 and 0.93 | RSS 0.94 and 2.0 | RSS 1.0 | Atom 0.3 | Atom 1.0 |
|---|---|---|---|---|---|---|---|
| `getUri()` (input to `id`) | link, else null | link, else null | link, else null (the guid is ignored) | guid text (any `isPermaLink`), else link, else null | `rdf:about`, else link, else null | `id`, else the link when there is exactly one alternate | `id`, else the entry link, else null |
| `getUri()` of an empty element | | | | `<guid></guid>` gives `""`, not the link | | | `<id></id>` gives `""`, not the link |
| `getLink()` | `<link>` | `<link>` | `<link>` | `<link>`; if absent, **the guid text when `isPermaLink` is true or absent (even `"g-7"`)**; `isPermaLink="false"` gives null | `<link>` (an `rdf:about`-only item has null) | the alternate link only if there is exactly one (two alternates: null) | first `alternate` in document order (no `rel` counts; `type` is ignored: a PDF alternate beat the HTML one); only `self`/`replies`/`enclosure` links: null |
| `title` | text | text | text | text | text | text | text; `xhtml` gives markup with the wrapper `div` |
| `summary` (`getDescription()`) | none | `text/plain` | `text/html` | `text/html`; child elements inside are re-serialised | `text/plain` | `summary` (type as written) | `summary`, type as written or null |
| `content` (`getContents()`) | none | none | none measured | `content:encoded` as `html` (measured on 2.0) | `content:encoded` as `html` | `content` (type and `mode`) | `content`: `html` unescaped, `text`, `text/html`, `xhtml` (serialised markup with the wrapper `div`), `application/xml` (serialised), no type attribute (null type); `src=` without a body gives `""` and the `src` is lost |
| `author` (`getAuthor()`) | `""` | `""` | `""` | `dc:creator` wins over `<author>` when both exist (measured on 2.0); else `<author>` text; else `""` | `dc:creator` | first author's name | first author's name (null name gives `""`); entries do **not** inherit the feed author; `getAuthors()` carries name, email, uri |
| `publishedAtEpochMillis` (`getPublishedDate()`) | null | `pubDate` | `pubDate` | `pubDate`, **but `dc:date` wins when both exist** (measured on 2.0) | `dc:date` | `modified`, else `issued`, else `created` | `published` only |
| `updatedAtEpochMillis` (`getUpdatedDate()`) | null | null | null | null | null | always null | `updated` (an entry with only `updated` has published null) |
| `categories` | none | none | `<category>` text (untrimmed), `domain` as `taxonomyUri`; blank elements dropped by the parser | same | **none from `getCategories()`; `dc:subject` only (see below)** | none measured | `term` as name (null if absent), `label`, `scheme` as `taxonomyUri`; a label-only category has name null |
| `enclosures` | none | none | `<enclosure>` direct child of `<item>`: url (null if missing, entry kept), type (null if absent), length | same | none | `rel="enclosure"` links; length is 0 even when `length` is given | `rel="enclosure"` links (also still in `getLinks()`): href as written (relative stays relative), length 0 when absent |

- **Enclosure length** comes from `NumberParser.parseLong(value, 0L)`: absent, non-numeric and overflowing values give 0; a negative value is passed through (`-5`). Treat a length of 0 or less as unknown.
- **`dc:subject`.** `SyndEntry.getCategories()` never contains `dc:subject` for any format. The entry's `DCModule` (`entry.getModule(DCModule.URI)`, `com.rometools.rome.feed.module.DCModule` in the `rome` jar) holds them as `DCSubject` (`getValue()`, `getTaxonomyUri()`), blank ones included (`<dc:subject></dc:subject>` gave an empty value, an untrimmed `" s2 "` stayed). RSS 1.0 has no `<category>`, so for RSS 1.0 these are the only categories. In RSS 2.0 and Atom 1.0 documents that carry both, they are separate lists.
- **Relative links and `xml:base`.** Rome resolves nothing: `Atom10Parser.resolveURIs` is a static flag, default false, and `resolveURI` returns the input when it is false (hrefs such as `posts/e4` stayed relative under a feed-level `xml:base`, an entry-level `xml:base` and a link-level `xml:base`). The flag is JVM-global state: never set it. Only `Feed.getXmlBase()` and `Entry.getXmlBase()` exist on the Atom wire beans (reachable only with `SyndFeedImpl(wire, true)` or the `WireFeed` itself); `Link` has none. `FeedInput.baseUrl` is therefore the only base the conversion can apply, and `xml:base` is not honoured.
- `javascript:alert(1)`, relative paths and whitespace-padded URLs all pass through `getLink()` unchanged.
- RSS 0.91 Netscape (which requires a DOCTYPE) is refused by design; RSS 0.91 Userland (`rss_0.91U`) parses.

### F11. Logging and global state (code reading + measured)

- Rome 2.1.0 and rome-utils log only: `CloneableBean` (`LOG.error("Error while cloning bean", e)`), `ToStringBean` (`LOG.error`), `ModuleUtils` (`LOG.error` on module clone), `IO.close` (`LOG.warn("Unable to close resource")`), and `RSS092Parser` (`LOG.debug("Entity: {}", c.getValue())`, document text at debug, unreachable with DOCTYPEs refused). None is on the parse path of this adapter (no cloning, no `toString`). Running ~45 odd documents under `slf4j-simple` at trace level produced no output on stderr. Never call `toString`, `clone` or `equals` on a bean.
- Static mutable state in Rome: `Atom10Parser.setResolveURIs`, `XmlReader.setDefaultEncoding`, `ConfigurableClassLoader.setClassLoader` (default: Rome's own class loader; used for `rome.properties` plugin discovery with per-class-loader `WeakHashMap` caches under `synchronized (WireFeedInput.class)`). The adapter calls none of them and supplies its own default encoding to every `XmlReader`.
- `slf4j-api` is a transitive dependency of Rome; a consumer with no binding sees SLF4J's one-time "no provider" notice at first logger use.

### F12. Kotlin idiom for the conversion (reasoned; the repository's paradigm is OOP for domain modelling, FP for data transformation, `kotlin.Result` for expected failures)

- Fits: one `private`/`internal` extension function per bean (`SyndEntry.toFeedEntry(limits, base)`, `SyndEnclosure.toFeedEnclosure(...)`), each a pure function from a bean to an immutable model value; nullable chaining at the platform-type boundary (`title?.trim()?.takeIf(String::isNotEmpty)`, `categories.orEmpty()`), `mapNotNull` and `take(limit)` for list caps, a `when` over `feed.feedType.startsWith("atom")` where a format split is needed (Rome's type strings, `rss_2.0`, `atom_1.0`), and one private function `Throwable.toFeedFailure()` that classifies by a bounded walk of the cause chain (`generateSequence(this) { it.cause }.take(8)`).
- Cargo-cult here: `runCatching { }` around the Rome call (it "catch[es] any Throwable", so it would turn `OutOfMemoryError` and the module's own conversion bugs into a failure value; the contract keeps the latter as `DecodeFailure`); `kotlin.Result` as an internal return type; wrapper classes that expose a lazy view over the mutable Rome beans (they keep the beans, and Rome types, alive and leak platform-type nullability); builders or DSLs for a twenty-field mapping; coroutines or `Flow` inside a non-suspending port; `data class` for the Rome-facing internals (fine, but there is no gain over a plain class).
- The Rome region is the only place a broad catch is right, and it should be written as an explicit `try/catch` with named types, the conversion code outside it.

## How comparable projects do it

| Project (version) | How it drives its XML layer | Relevance |
|---|---|---|
| Rome 2.1.0 (`WireFeedInput`, `SyndFeedInput`) | `createSAXBuilder()` is the single, protected extension point; hardening set with a guard that skips features the provider does not recognise; healer only on the `Reader` entry points | the hook used here; the guard is why the second lock is set explicitly |
| Python feedparser 6.0.14 (`feedparser/api.py`, `feedparser/sanitizer.py`; tag `v6.0.14`, commit `f5a4db61242653d5a1d8d231119341bf8d5d9362`) | rewrites the DOCTYPE first (`replace_doctype` strips it and keeps only "safe" entity declarations), then a SAX parser with `feature_external_ges` 0, falling back to a loose parser on `SAXException` | same order as this module: neutralise the DOCTYPE before the parser; unlike this module it tolerates malformed XML by falling back |
| prof18 `RSS-Parser` 6.1.8 (`JvmXmlParser`, tag `6.1.8`) | `SAXParserFactory.newInstance().newSAXParser()` with no feature, no limit, no DOCTYPE refusal; on `RssParsingException` it re-parses after escaping invalid entities | the low end of Kotlin practice; the retry-with-escaping is Rome's repair done by hand |
| Miniflux 2.3.5 (`internal/reader/xml/decoder.go`) | Go `encoding/xml` decoder with `Strict = false`, `Entity = xml.HTMLEntity`, invalid XML characters filtered before decoding; Go has no DTD processing, and `encoding/xml` caps unmarshal depth at 10,000 (`maxUnmarshalDepth`, `go1.23.0`) | confirms tolerance of bare entities and control characters as normal reader behaviour; Rome fails on control characters (below) |
| OWASP XXE Prevention Cheat Sheet | `disallow-doctype-decl` true on the `SAXParserFactory`, `FEATURE_SECURE_PROCESSING` true, namespace aware, XInclude off | what route D sets |
| rssreader 3.12.0 (parent findings) | StAX with `SUPPORT_DTD` and external entities off | not used: no DOCTYPE refusal |

Nothing found drives a JAXP limit through the shared JDOM factory; the practice that scales is a factory owned by the caller.

## Pitfalls

- **The architecture's `SyndFeedInput` sketch cannot reach the limits, and the obvious fix (subclass, call `super`, add properties) leaks on JDK 22+, races, and fails on Xerces classpaths.** Measured; F2.
- **The `jdk.xml.*` property names are not recognised on JDK 11.** Every parse then fails as `Malformed` (JDOM fails closed), which no test on the build JDK 23 would show; a test that a valid feed parses must run on every JDK the module claims. The URL names (`http://www.oracle.com/xml/jaxp/properties/...`) work on 11, 17, 21 and 23; the JDK 23 source comment says both forms are accepted ("see JDK-8265248, accept both the URL and jdk.xml as prefix").
- **A header charset that contradicts a UTF-8 body wins** (UTF-8 body, no declaration, `charset=iso-8859-1`: "TÃ¼"), which is the specs' order and the serving site's fault; a UTF-8 BOM with an `ISO-8859-1` declaration gives mojibake on every path. Both silently produce wrong text, not failures.
- **`XmlReader` loses the declaration after leading whitespace** (silent U+FFFD) and throws from its constructor for an unknown charset; both are fixed only by the recipe.
- **Control characters are fatal.** A U+0001 in element text, or `&#1;`, is a `ParsingFeedException`; Miniflux strips such characters before decoding. If a tolerance policy is wanted, a `Reader` that drops XML-illegal characters between `XmlReader` and `build(Reader)` is the place; it changes the architecture's behaviour (`Malformed` for these documents) and was not built or measured here.
- **Hostile `NumberFormatException` text.** Any `IllegalArgumentException` subclass carries attribute text. Only the exact `IllegalArgumentException("Invalid document")` is a not-a-feed signal.
- **`StackOverflowError` is catchable but the cause is a JDOM serialiser, and Rome's `Error` passes the `catch (Exception)` in `build`.** Without the depth cap, a 14 KiB description with 2,000 nested elements overflows a 1 MiB stack.
- **Entry `getLink()` can be a non-URL** (the guid) and `getUri()` can be `""`, an untrimmed string, or null; Atom `getLink()` for the feed can be the feed's own URL. Each reaches the model only after the module's link and identity rules.
- **Rome's `DateParser` accepts nonsense** (year 20030610, a rolled-over `2003-02-30`) and drops common real forms (`-0400`, no-zone dates); both are permanent in 2.1.0.
- **`SyndEntry.getCategories()` hides `dc:subject`**; a conversion that reads only the getter leaves every RSS 1.0 entry without categories.
- **Atom `type="text"` and `type="html"` content both arrive as `String`;** the model has no type field, so plain text containing `<` and HTML are indistinguishable downstream (the model's "untrusted markup" wording covers the safe reading).
- **Helpful `NullPointerException` messages differ by JDK** (JDK 14+ names variables); never match or log them.
- **Parse-time CPU is not covered by the 2 MiB cap alone**: attributes at the JDK default (10,000) cost 4 s per 2 MiB, depth costs minutes.

## Recommendation for this repository

### Entry point and settings

Create these per `parse` call (all cheap; nothing shared, nothing static):

```kotlin
// jvmMain, internal to RomeFeedParser
private class HardenedWireFeedInput(private val maxDepth: Int) : WireFeedInput(false, Locale.US) {
    override fun createSAXBuilder(): com.rometools.rome.io.SAXBuilder =        // Rome's public SAXBuilder
        com.rometools.rome.io.SAXBuilder(PrivateJdkSaxReaders).apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)   // unguarded: fails closed
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setExpandEntities(false)
            setProperty("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", maxDepth)       // 256
            setProperty("http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit", 100)
        }
}

private object PrivateJdkSaxReaders : XMLReaderJDOMFactory {      // stateless; each call builds a fresh factory
    override fun createXMLReader(): XMLReader = try {
        SAXParserFactory.newDefaultInstance().apply {
            isNamespaceAware = true; isValidating = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }.newSAXParser().xmlReader
    } catch (e: Exception) { throw JDOMException("no JDK SAX parser", e) }
    override fun isValidating(): Boolean = false
}
```

Then, after the module's prolog scan has passed:

```kotlin
val bytes = stripLeadingAsciiWhitespace(input.document)                 // recipe step 1
val reader = openXmlReader(bytes, input.contentType)                    // recipe steps 2 and 3
val syndFeed = reader.use { SyndFeedImpl(HardenedWireFeedInput(maxDepth).build(it), false) }
```

with `openXmlReader` using `XmlReader(ByteArrayInputStream(bytes), true, "UTF-8")` for UTF-16-looking input, else `XmlReader(ByteArrayInputStream(bytes), contentType, true, "UTF-8")` with one retry on `UnsupportedEncodingException` through the raw constructor. Never call `SyndFeedInput`, `super.createSAXBuilder()`, `WireFeedInput.build(InputSource)` or `build(Document)`, `Atom10Parser.setResolveURIs`, `XmlReader.setDefaultEncoding` or `ConfigurableClassLoader.setClassLoader`. Constants: `maxElementDepth` 256, `elementAttributeLimit` 100 (24 downloaded feed files peaked at depth 5 and 10 attributes). The depth limit may be exposed as a tightening-only value of `FeedLimits`; the attribute limit stays fixed.

An element-count cap is not provided by any JDK property. A measured alternative that also types the failure: `setSAXHandlerFactory` with a `SAXHandler` subclass that counts depth, elements and attributes in `startElement` and throws a private `SAXException` subclass before calling `super` (290,000-level document rejected in 79 ms; 450,000 sibling elements stopped at a 100,000 cap in 112 ms; 5,000 attributes in 18 ms). It does not count `xmlns` declarations, so it complements the two JDK properties rather than replacing them. The memory question decides whether an element cap is wanted.

### Exception table (the Rome region is: `XmlReader` construction, `build`, `SyndFeedImpl` construction)

| Thrown in the Rome region | Result |
|---|---|
| `UnsupportedEncodingException` from the first `XmlReader` | retry once through the raw constructor; if it throws again, `Malformed` |
| any other `IOException` from `XmlReader` | `Malformed` |
| `ParsingFeedException` whose cause chain holds a `SAXParseException` with a message starting `JAXP00010006` | `LimitExceeded(depth)` |
| same, `JAXP00010002` | `LimitExceeded(attributes)` |
| same, `JAXP00010005` | `LimitExceeded(nameLength)`; codes `…01`, `…03`, `…04`, `…07` the same with an entity limit (unreachable without a DOCTYPE) |
| `ParsingFeedException` whose chain text contains `disallow-doctype-decl` | `Refused(Doctype)` (the second lock fired) |
| any other `ParsingFeedException` (every well-formedness error, invalid character, truncation, junk, an NPE inside Rome) | `Malformed` |
| exactly `IllegalArgumentException` (class equal, not a subclass) with message `"Invalid document"` | `NotAFeed(…)` |
| any other `IllegalArgumentException` (including `NumberFormatException`) | `Malformed` |
| `StackOverflowError` | `LimitExceeded(depth)` (backstop; unreachable under the depth cap) |
| any other `RuntimeException` | `Malformed` |
| `OutOfMemoryError`, other `Error`s | not caught |
| an exception from the module's own conversion code (outside the Rome region) | not caught; the contract turns it into `HttpFailure.DecodeFailure(cause)` |

No exception message and no cause from the Rome region is stored or logged; the failure carries its constant reason only. The broad `RuntimeException` rule is a departure from "a parser that throws against its contract yields `DecodeFailure`": Rome's own defects on hostile input (`NumberFormatException`, `NullPointerException`) are input-triggered and their messages hold document text, so they are `Malformed` with the cause dropped, while `DecodeFailure` remains for the module's own bugs. Classification of HTML, JSON and other non-XML bodies is the prolog scan's (F5): it must also detect an `html` root without a DOCTYPE, because Rome reports not-well-formed HTML as malformed XML.

### Mapping

Convert per F10. The conversion rules, to be fixed by the identity/link/text work and listed here only as what each Rome value needs:

- Titles, ids, links, authors, category names: trim; blank becomes null; apply the `FeedLimits` caps by code point.
- `id`: input is `getUri()`; `""`, blank and null all count as absent.
- `link` (entry): input is `getLink()`; for RSS 0.94/2.0 it may equal the guid text, so a non-URL there is rejected by the link rule, not trusted.
- `link` (feed): Atom, first of `getLinks()` with `rel` null or `alternate`, else null; RSS and RSS 1.0, `getLink()`.
- `summary` from `getDescription()?.value`, `content` from the first `getContents()` value; no type is kept.
- `author` from `getAuthor()`; blank becomes null. No feed-level fallback is applied by Rome for Atom.
- Dates: `getPublishedDate()?.time` and `getUpdatedDate()?.time`; drop values outside year 1 to 9999.
- Categories: the format's own categories (`name ?: label`), then `DCModule` subjects, de-duplicated, blank dropped, capped.
- Enclosures: `url` null dropped; `length` of 0 or less becomes unknown; `type` as written.

### Thread-safety rule

`RomeFeedParser` holds only its `FeedLimits` and is immutable; `parse` is safe from any number of threads because every call creates its own `XmlReader`, `WireFeedInput` subclass instance, `SAXBuilder`, `SAXParserFactory` and SAX parser, and nothing static is read or written. (One shared `WireFeedInput` was also measured safe over 12,000 parses on 24 threads, but its thread safety is not documented, so an instance per call is the rule.) The parse does not use the context class loader.

### What this changes against the architecture's description

1. `SyndFeedInput` with `allowDoctypes = false` becomes a private `WireFeedInput` subclass with its own `SAXBuilder` and `SyndFeedImpl(wire, false)`; `allowDoctypes` is never touched (the subclass never calls `super.createSAXBuilder()`, so the flag is not consulted, and the DOCTYPE feature is set directly).
2. `XmlReader` is not constructed plainly: the three-step recipe replaces it.
3. The exception table gains `IOException` and `UnsupportedEncodingException` from `XmlReader`, the exact-class `IllegalArgumentException` test, the JAXP code classification, a `disallow-doctype-decl` test for `Refused`, and the broad `RuntimeException` rule.
4. `StackOverflowError` to `LimitExceeded(depth)` stays but as a backstop: the JDK depth limit (256) is the primary control and is also the control against the quadratic JDOM build.
5. Two JDK limits (depth 256, attributes 100) and a per-build private factory are new; the JDK's name-length default (1,000) is relied on.
6. `dc:subject` is a category source.
7. Added tests: a valid feed parses on every JDK the module claims (the property-name fault is invisible on JDK 23); a 290,000-level document and a 10,000-attribute flood each fail in well under a second; the loopback-count case; a test that the limits do not leak into a plain `WireFeedInput` in the same JVM.

## Load-bearing claims

1. **`WireFeedInput.createSAXBuilder()` is a protected hook returning Rome's `SAXBuilder`.** Quote: "Creates and sets up a org.jdom2.input.SAXBuilder for parsing." and "protected SAXBuilder createSAXBuilder() {". Source: https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/WireFeedInput.java (tag 2.1.0).
2. **Rome's `SAXBuilder` is public and takes an `XMLReaderJDOMFactory`.** Quote: "public class SAXBuilder extends org.jdom2.input.SAXBuilder {" and "public SAXBuilder(final XMLReaderJDOMFactory factory) {". Source: https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/SAXBuilder.java (tag 2.1.0).
3. **The repair runs only for `build(File)` and `build(Reader)`.** Quote: "The healing is done only with the build(File) and build(Reader) signatures." Source: the `WireFeedInput.java` file in claim 1.
4. **`build(Reader)` wraps XML errors and rethrows `IllegalArgumentException` unchanged; a non-feed is `IllegalArgumentException("Invalid document")`.** Quote: "} catch (final IllegalArgumentException ex) {\n            throw ex;\n        } catch (final Exception ex) {\n            throw new ParsingFeedException(\"Invalid XML\", ex);" and "throw new IllegalArgumentException(\"Invalid document\");". Source: the file in claim 1.
5. **`SyndFeedInput` hides its `WireFeedInput`, and `SyndFeedImpl` has a public constructor taking a `WireFeed`.** Quote: "private final WireFeedInput feedInput;" (SyndFeedInput.java) and "public SyndFeedImpl(final WireFeed feed, final boolean preserveWireFeed) {" (SyndFeedImpl.java). Sources: https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/SyndFeedInput.java and https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/feed/synd/SyndFeedImpl.java (tag 2.1.0).
6. **In lenient mode `XmlReader` lets the XML declaration win.** Quote: "if (lenient && xmlEnc != null) {\n            encoding = xmlEnc;". Source: https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/XmlReader.java (tag 2.1.0).
7. **`XmlReader` has a static default encoding and a four-argument constructor taking a default.** Quote: "public static void setDefaultEncoding(final String encoding) {" and "public XmlReader(final InputStream is, final String httpContentType, final boolean lenient, final String defaultEncoding) throws IOException,". Source: the file in claim 6.
8. **`Atom10Parser` resolves URIs only under a JVM-global flag, default false.** Quote: "private static boolean resolveURIs = false;" and "if (!resolveURIs) {\n            return url;". Source: https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/Atom10Parser.java (tag 2.1.0).
9. **JDOM fails closed on an unrecognised parser property.** Quote: "displayName + \" property \" + property + \" not recognized for SAX driver \" + parser.getClass().getName());". Source: https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/input/SAXBuilder.java (tag JDOM-2.0.6.1).
10. **`XMLReaders.NONVALIDATING` is one shared `SAXParserFactory`.** Quote: "SAXParserFactory fac = SAXParserFactory.newInstance();" and "public SAXParserFactory supply() throws Exception {\n\t\t\treturn factory;". Source: https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/input/sax/XMLReaders.java (tag JDOM-2.0.6.1).
11. **JDOM's builder checks ancestry for every added element (the quadratic cost).** Quote: "// Detect if we have <a><b><c/></b></a> and c.add(a)" and "((Element) child).isAncestor((Element) parent)) {". Source: https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/ContentList.java (tag JDOM-2.0.6.1).
12. **JDK 22+ SAX parsers share the factory's security manager, and `setProperty` writes to it.** Quote: "fSecurityManager = spf.fSecurityManager;" and "!fSecurityManager.setLimit(name, JdkProperty.State.APIPROPERTY, value)) {". Source: https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/jaxp/SAXParserImpl.java (tag jdk-23-ga); and the factory field "XMLSecurityManager fSecurityManager = new XMLSecurityManager(true);" in https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/jaxp/SAXParserFactoryImpl.java. Contrast, JDK 21: "fSecurityManager = new XMLSecurityManager(secureProcessing);" in https://github.com/openjdk/jdk/blob/jdk-21-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/jaxp/SAXParserImpl.java (tag jdk-21-ga).
13. **On JDK 11 the API property names are the URL forms; JDK 23 accepts both.** Quote (jdk-11-ga `Constants.java`): "public static final String ORACLE_JAXP_PROPERTY_PREFIX =\n        \"http://www.oracle.com/xml/jaxp/properties/\";" and "ORACLE_JAXP_PROPERTY_PREFIX + \"maxElementDepth\";". Source: https://github.com/openjdk/jdk/blob/jdk-11-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/impl/Constants.java. Quote (jdk-23-ga `XMLSecurityManager.getIndex`): "// see JDK-8265248, accept both the URL and jdk.xml as prefix". Source: https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/jdk/xml/internal/XMLSecurityManager.java.
14. **The JDK enforces processing limits by default, with documented defaults and codes.** Quote: "The JDK XML parsers observe processing limits by default. Both DOM and SAX parsers have Feature for Secure Processing (FSP) turned on by default and therefore turn on the limits." and "A value less than or equal to 0 indicates no limit." and "When input files contain constructs that cause an over-the-limit exception, applications may check the error code to determine the nature of the failure." with the list "`ElementAttributeLimit`: JAXP00010002 ... `MaxXMLNameLimit`: JAXP00010005 ... `maxElementDepth`: JAXP00010006". Defaults in the same guide: `jdk.xml.elementAttributeLimit` 10000, `jdk.xml.maxElementDepth` 0, `jdk.xml.maxXMLNameLimit` 1000. Source: https://docs.oracle.com/en/java/javase/23/security/java-api-xml-processing-jaxp-security-guide.html (Java SE 23, read 2026-11-06).
15. **Limit properties can be set on a SAX parser with `setProperty`, and API settings take precedence.** Quote: "SAXParser parser = spf.newSAXParser();\nparser.setProperty(name, value);   // Method 1" (the SAX row of the Processor Support table) and "The configuration order of precedence for properties is as follows, from highest to lowest: * The APIs for factories or processors". Source: https://docs.oracle.com/en/java/javase/23/docs/api/java.xml/module-summary.html (Java SE 23, read 2026-11-06).
16. **`SAXParserFactory.newDefaultInstance()` returns the built-in implementation; `newInstance()` consults lookup mechanisms including the context class loader.** Quote: "Creates a new instance of the `SAXParserFactory` builtin system-default implementation." (SAXParserFactory Javadoc, Java SE 23, since 9) and "the service-provider loading facility will use the current thread's context class loader to attempt to load the service" (module summary, lookup mechanism). Sources: https://docs.oracle.com/en/java/javase/23/docs/api/java.xml/javax/xml/parsers/SAXParserFactory.html and the module summary in claim 15.
17. **OWASP: reject DOCTYPE on the SAX factory.** Quote: "Disable document type definitions (DTDs) whenever possible." and "For Simple API for XML (SAX) parsing, reject DOCTYPE declarations on the factory before creating the reader." Source: https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html (read 2026-11-06).
18. **`runCatching` catches every `Throwable`.** Quote: "catching any Throwable exception that was thrown from the block function execution and encapsulating it as a failure." Source: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/run-catching.html (Kotlin standard library, read 2026-11-06).
19. **`Error` marks conditions an application should not normally catch** (supports keeping the `StackOverflowError` catch as a backstop only). Quote: "An `Error` is a subclass of `Throwable` that indicates serious problems that a reasonable application should not try to catch." Source: https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/lang/Error.html.
20. **Comparable: feedparser neutralises the DOCTYPE before parsing and disables external general entities.** Quote: "\"\"\"Strips and replaces the DOCTYPE, returns (rss_version, stripped_data)" (sanitizer.py) and "saxparser.setFeature(xml.sax.handler.feature_external_ges, 0)" (api.py). Sources: https://github.com/kurtmckee/feedparser/blob/v6.0.14/feedparser/sanitizer.py and https://github.com/kurtmckee/feedparser/blob/v6.0.14/feedparser/api.py (tag v6.0.14).
21. **Comparable: prof18 `RSS-Parser` JVM parser is unhardened.** Quote: "val parser = SAXParserFactory.newInstance().newSAXParser()". Source: https://github.com/prof18/RSS-Parser/blob/6.1.8/rssparser/src/jvmMain/kotlin/com/prof18/rssparser/internal/JvmXmlParser.kt (tag 6.1.8).
22. **Comparable: Miniflux tolerates entities and non-strict XML.** Quote: "decoder.Entity = xml.HTMLEntity" and "decoder.Strict = false". Source: https://github.com/miniflux/v2/blob/2.3.5/internal/reader/xml/decoder.go (tag 2.3.5). Go depth cap: "maxUnmarshalDepth     = 10000" in https://github.com/golang/go/blob/go1.23.0/src/encoding/xml/read.go.

## Gaps

- **JDKs not run:** results were measured on 23.0.2 and, for the property names, the leak, the encoding recipe, the exception table and the loopback count where stated, on 11.0.32.1, 17.0.20 and 21.0.10. JDK 22 was not run; that the sharing starts at 22 rests on the source diff between `jdk-21-ga` and `jdk-22-ga` and on the measured 23 versus 17 and 21 behaviour. The CPU and stack figures (F6, F7) are from JDK 23 only; the stack margins in particular may differ on other JVMs.
- **No Atom `xhtml` or deep-markup feed was in the live sample**, so the depth cap of 256 and attribute cap of 100 are supported by the maximum over those 24 files (depth 5, 10 attributes) and the stack margins, not by an observed legitimate deep feed. An HTML-heavy Atom `xhtml` newsletter might nest further than usual; none was found.
- **Not measured:** the handler-based counting route inside the Kotlin adapter (measured as Java only); the cost of the per-build `SAXParserFactory` (not benchmarked; the parse of a small feed took under 100 ms in every run including class loading); any non-JDK SAX provider other than Xerces 2.12.2; Woodstox or other StAX providers (not used); `SyndFeedImpl(wire, true)` and `xml:base` extraction; RSS 0.91 Netscape (needs a DOCTYPE, refused by design).
- **Behaviour stated from source only:** the three `XMLOutputter` call sites being the only recursion over content depth in Rome's parse and conversion code (a full grep for `XMLOutputter` was done, not a path analysis); the exact set of JDOM and Rome statics beyond those listed.
- **Control-character tolerance** (drop illegal XML characters before Rome) is a policy the plan may want; it was not built or measured, and changes the `Malformed` outcome for such documents.
- **The model carries no content type**, so `type="text"` and `type="html"` content cannot be told apart by a consumer; this is recorded, not a defect of the adapter.
- **Rome 2.1.0 is dormant** (parent findings: last release 2023-03-12); the mapping and date behaviours above are permanent unless the dependency changes, and the `DateParser` faults (F9) are unfixed upstream (issue #695 was open at the last check).
