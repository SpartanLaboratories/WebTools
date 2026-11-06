# Finding a DOCTYPE in a byte document before any parser runs

## Question

How should an `internal`, common-code (pure Kotlin) prolog scan, a function of the document bytes and the response's
`Content-Type` value, find a DOCTYPE before Rome 2.1.0 (`XmlReader`, then `XmlFixerReader`, then the JDK's SAX parser)
sees the document? An answer has to give the algorithm with its byte bound, the encodings it reads and the ones it
refuses, an outcome type that maps onto the fetch failures (`NotAFeed(Html)`, `Refused`, `Malformed`), and a Level 4a
vector catalogue, and it must never read a prolog differently from the parser pipeline behind it.

## Bottom line

- **Do not pick one encoding and then scan.** Rome's lenient `XmlReader` lets the XML declaration beat the byte-order
  mark and the `Content-Type`, ignores a UTF-16 mark under `text/xml`, `text/plain`, `text/html` or no header, and
  consumes a second mark on its `text/html` retry. A scan that follows RFC 7303 or XML Appendix F is bypassed by all of
  these (vectors D17 to D21, D24, D26, D27 below). Scan the prolog in **three readings at once** (ASCII-compatible bytes,
  UTF-16BE units, UTF-16LE units) after skipping the whole leading run of byte-order marks, and return the **worst**
  outcome. No encoding is chosen, so no parser-side choice can read the bytes differently.
- **Refuse what cannot be read.** UCS-4/UTF-32 prefixes and marks, the EBCDIC prefix `4C 6F A7 94`, and any encoding
  name found in the `Content-Type` (every `charset=`) or in any `encoding=` of the declaration-shaped start that is not
  on a short allowlist (UTF-8, US-ASCII, ISO-8859-N, windows-125x, KOI8, Shift_JIS/windows-31j, EUC-KR, GBK, GB2312,
  Big5, UTF-16 family) give `Undecodable`, which maps to `Refused`. UTF-7, UTF-32, EBCDIC code pages and ISO-2022-JP
  are therefore never scanned as ASCII.
- **A decoder can make the parser see less than the scan sees, and an attacker can use that.** The JDK's UTF-16 decoder
  drops the unit after an unpaired high surrogate, and its EUC-JP and GB18030 decoders drop the ASCII byte after a
  malformed lead byte. A comment `<!-- D800 -->x<!-- --><!DOCTYPE ...>` then ends for the scan but not for the parser,
  and the scan stops at the `x` while the parser reads on to the DOCTYPE (vectors U23, U24, U27, measured against the
  real pipeline). The scan must not return `NoDoctype` when its examined region holds an unpaired surrogate (16-bit
  readings) or, for EUC-JP/GB18030 only, any byte at or above 0x80.
- **Walk the prolog, do not search for the text.** Skip white space, processing instructions and comments; at `<!` +
  `DOCTYPE` (ASCII case-insensitive, a superset of XML) read the root name (`html`, any case, gives `HtmlDocument`,
  anything else `Doctype`); `<!` + any other letter is `Doctype`; the first start tag stops the walk (`html` root gives
  `HtmlDocument`). A `--` inside a comment, or a comment or processing instruction that never ends, gives `IllFormed`
  (mapped to `Malformed`, no parser runs), because Rome's repair reader trims leading comments with a grammar that
  differs from the specification and exposes a DOCTYPE the specification hides (M03, M06).
- **Bound: 64 KiB per reading** (a reasoned figure; the longest prolog in 70 fetched documents was 149 bytes). Beyond it
  with the prolog unfinished gives `Undecodable(PrologTooLong)`. Work is at most three linear passes over 64 KiB; a 2 MiB
  body scans in under 0.25 ms (measured, warm JVM).
- A tested pure-Kotlin implementation (compiled with Kotlin 2.2.0 for the JVM and as a Kotlin/JS klib, 125 vectors, 0
  disagreements with the pipeline-reaches-DOCTYPE invariant over 1.8 million random cases plus 0.8 million targeted
  16-bit cases) is in the Recommendation.

## Findings

Evidence tiers: **measured** (a probe run against the real Rome 2.1.0 / JDK 23.0.2 pipeline), **code reading**
(source at a pinned tag), **documented** (specification or vendor text), **single source** (one write-up),
**reasoned** (an argument, no external source).

### F1. Rome's lenient `XmlReader` chooses the encoding in an order that differs from XML and RFC 7303 (code reading, Rome 2.1.0)

`XmlReader.calculateHttpEncoding` begins `if (lenient && xmlEnc != null) { encoding = xmlEnc; }`, so a declaration's
`encoding` wins over a byte-order mark and over the `Content-Type` charset. `getBOMEncoding` knows only `EF BB BF`,
`FE FF` and `FF FE` and consumes them; `getXMLGuessEncoding` knows only `00 3C 00 3F`, `3C 00 3F 00`, `3C 3F 78 6D`
and `4C 6F A7 94` (read after the mark); `getXmlProlog` decodes the first 4096 bytes in the guessed encoding, takes
the text before the first `>` and applies the case-sensitive pattern `<\?xml.*encoding[\s]*=[\s]*(...)`, which also
matches `<?xml-stylesheet encoding="..."?>`. The charset pattern is `charset=([.[^; ]]*)`, lower case, after the first
`;`. A mime type that is not an XML type throws `XmlReaderException`; the lenient fallback rewrites `text/html` to
`text/xml` and re-runs detection on the stream that already lost its first mark, so a second mark is consumed. Any
JDK charset name from the header or the declaration reaches `new InputStreamReader(is, encoding)`; a name the JDK does
not know throws `UnsupportedEncodingException` from the `XmlReader` constructor. Source:
[`XmlReader`](https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/XmlReader.java).

### F2. The orders the specifications give are not the parser's, so a one-encoding scan is bypassable (measured, documented)

RFC 7303 §3.2 makes a BOM authoritative, then the charset parameter; XML Appendix F reads the declaration after
detecting the family; neither is what F1 does. Measured against Rome 2.1.0 + `XmlFixerReader` + JDK SAX with
`disallow-doctype-decl` (the message "DOCTYPE is disallowed" shows a DOCTYPE reached the parser), across six
constructor shapes (`(is, ct)` lenient and strict, `(is, true)`, `(is, false)`, and the two four-argument forms with
default `UTF-8`):

- an ASCII/UTF-8 body behind `FE FF` or `FF FE` reaches the DOCTYPE under `text/xml`, `text/plain`, `text/html` and no
  header (D18 to D21): Rome ignores the mark, a scan that trusts it reads garbage;
- two UTF-8 marks reach the DOCTYPE under `text/html` only (D17), one mark is consumed per detection pass;
- UTF-16 with no mark reaches it under a header charset (D24), a declaration `UTF-16` in either byte order (D26, D27);
- UTF-32 under `charset=utf-32` reaches it with or without a mark (U01, U03), IBM037/IBM1047 under a header or a
  declaration (U05, U06), ISO-2022-JP under a declaration (U09);
- a declaration inside a processing instruction other than the XML declaration steers the decoder
  (`<?xml-stylesheet encoding="UTF-16"?>`, D30, U15).

### F3. Xerces receives a `Reader` and ignores the declared encoding (code reading, JDK 23 GA)

`WireFeedInput.build(Reader)` hands the JDK parser a character stream. `XMLEntityManager` takes
`xmlInputSource.getCharacterStream()` first and fetches a byte stream only `if (reader == null)`;
`XMLEntityScanner.setEncoding` re-reads only `if (fCurrentEntity.stream != null)`. So on the Rome path the only
encoding decisions are `XmlReader`'s (F1); Xerces' own byte-level detection (UTF-8, UTF-16, UCS-4, EBCDIC) is not in play.
If any caller ever gives Xerces a byte stream, F2's list is incomplete. Sources:
[`XMLEntityManager`](https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/impl/XMLEntityManager.java),
[`XMLEntityScanner`](https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/impl/XMLEntityScanner.java).

### F4. Rome's repair reader trims leading comments with its own grammar and cannot create a DOCTYPE (code reading, Rome 2.1.0; measured)

`WireFeedInput.build(Reader)` wraps the reader in `XmlFixerReader` when `xmlHealerOn`. Its `trimStream()` drops leading
white space and leading `<!-- ... -->` comments before the parser sees anything, through a state machine in which a
comment closes only after two dashes followed by `>` and three dashes then `>` does not close, which differs from XML
1.0 (a comment must not contain `--`, and `--->` is not a comment end). Documents with such comments are read
differently by the specification and by the pipeline: `<!-- a -----><!DOCTYPE a>` reaches the parser's DOCTYPE
(M03), and a polyglot of unclosed comments (M06) does too. The reader's entity logic only inserts `amp;` after a
stray `&` and replaces named entities by numeric references; it reads every `<` and `!` from the stream and deletes
nothing, so it cannot turn bytes the scan passed into a DOCTYPE (vector N20).
Sources: [`XmlFixerReader`](https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/XmlFixerReader.java),
[`WireFeedInput`](https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/WireFeedInput.java).

### F5. Decoders can drop the unit after a malformed one, so the parser's text can differ from the scan's (measured, code reading)

With `REPLACE` actions (what `InputStreamReader` uses) the JDK's UTF-16 decoder returns
`CoderResult.malformedForLength(4)` for a high surrogate followed by anything but a low surrogate, which discards the
following unit as well; measured: `D800 003E` decodes to one U+FFFD, `D800 002D 002D 003E` to `FFFD 002D 003E`. An
unpaired low surrogate costs only itself. The EUC-JP and GB18030 decoders drop the ASCII byte after a malformed lead
byte (`8F 2D` loses the dash). Exhaustive tests (every first byte 0x80 to 0xFF, every second and third byte, followed
by each of 13 structural ASCII bytes: 109 million decodes per charset) found no such loss in UTF-8, Shift_JIS,
windows-31j, Big5, GBK, GB2312 and EUC-KR; a randomised test over 1.5 million mixed strings per charset also found
no ASCII character created from high bytes in any of them. Single-byte charsets cannot lose a byte. The
consequence is an attack, not a curiosity: `<!-- D800 -->x<!-- --><!DOCTYPE rss SYSTEM "x"><rss/>` in UTF-16 (with
`FF FE`, no header, `application/xml` or `text/xml; charset=utf-16le`) and the EUC-JP analogue with `8F` reach the
parser's DOCTYPE while a scan that stops at the junk `x` says no DOCTYPE (U23, U24, U27: pipeline column). With the
decoder-divergence rule disabled the same generators produced 13 violations in 400,000 targeted 16-bit cases and 4 to
7 in 600,000 general cases. Source:
[`UnicodeDecoder`](https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.base/share/classes/sun/nio/cs/UnicodeDecoder.java).
The loss happens only after a malformed unit, which gives the rule: it matters only if a malformed unit lies in the
region the scan examined, because the parser and the scan then agree up to the stop point.

### F6. What XML says about the prolog and about encodings (documented, XML 1.0 Fifth Edition)

`prolog ::= XMLDecl? Misc* (doctypedecl Misc*)?` with `Misc ::= Comment | PI | S`: a DOCTYPE can follow only the
declaration, comments, processing instructions and white space, and "MUST appear before the first element", so the
scan may stop at the first start tag (a DOCTYPE inside content makes Xerces fail; measured: `Scanner State 24 not
Recognized`, never a DTD). The keyword `<!DOCTYPE` is case-sensitive in XML, so a lower-case form is a parse error
(D07, D36); the scan treats it as a DOCTYPE anyway, because refusing a superset costs nothing. Appendix F.1's table
lists the byte patterns for UCS-4, UTF-16, UTF-8 and EBCDIC and says of `3C 3F 78 6D` that UTF-8, ISO 646, ASCII,
some ISO 8859, Shift-JIS, EUC "or any other 7-bit, 8-bit, or mixed-width encoding which ensures that the characters
of ASCII have their normal positions, width, and values" share the pattern, and warns that encodings such as UTF-7
"may fail to be reliably detected". That is the basis for scanning an ASCII-compatible reading and refusing the rest.
Source: [XML 1.0 (Fifth Edition)](https://www.w3.org/TR/2008/REC-xml-20081126/) §2.5, §2.8, §4.3.3, Appendix F.

### F7. RFC 7303 says the charset/BOM/declaration conflict is the consumer's problem (documented, RFC 7303, July 2014)

§3.2 sets BOM over charset parameter, and states that the XML specification "does not specify which is authoritative"
when in-band information conflicts with the charset parameter and "does _not_ make it an error" for the charset and
the declaration to be inconsistent. Parsers therefore legitimately differ, which is why a scan cannot lean on a
standard order. Source: [RFC 7303](https://www.rfc-editor.org/rfc/rfc7303) §3.2.

### F8. Encoding-based filter bypasses of XXE are an established, repeated technique (single source each; consensus on the class)

OWASP's current guidance is to configure the parser: "Reject DOCTYPE declarations if the parser supports it", with
`disallow-doctype-decl` shown for `DocumentBuilderFactory` and `SAXParserFactory`; it gives no pre-filter. Filters in
front of a parser are the documented weak point: a 2016 XXE cheat sheet says "Some simple blacklisting
countermeasures can probably bypassed by changing the default XML charset (which is UTF-8), to a different one, for
example, UTF-16" and "The same trick can be applied to UTF-7 as-well"; a 2019 CTF write-up of the 2015 PHDays WAF
bypass contest records "if we converted the character encoding of UTF-8 to UTF-16 Big Endian, we could easily bypass
the WAF". These are two independent descriptions of the same class (a filter reading one encoding, a parser another),
the class F2 and F5 reproduce against this pipeline. Sources:
[OWASP XXE Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html),
[web-in-security XXE cheat sheet](https://web-in-security.blogspot.com/2016/03/xxe-cheat-sheet.html),
[CSAW 2019 write-up](https://ctftime.org/writeup/16446).

### F9. The prolog of real feeds is tiny and plain (measured, 70 documents fetched in November 2026)

Of 70 fetched documents the scan reported 65 `NoDoctype` (the feeds) and 5 `HtmlDocument` (error and landing pages
returned for feed URLs: `<!DOCTYPE html>` twice, `<!DOCTYPE HTML>`, an XHTML 1.0 DOCTYPE, and a 150-byte `<html>` 502
page with no DOCTYPE). The root element started at byte 149 at most; declared encodings were
UTF-8 (52 documents, in three spellings) and ISO-8859-1 (1). None was refused. Rome itself accepts a Netscape RSS 0.91
document only with its DOCTYPE (`RSS091NetscapeParser.isMyType` requires `docType != null` with the Netscape public
and system ids), so with every DOCTYPE refused that variant is unreachable; documents carrying that DOCTYPE are
refused.

## How comparable projects do it

- **defusedxml 0.7.1** (Python): no byte pre-scan. It installs `parser.StartDoctypeDeclHandler =
  self.defused_start_doctype_decl`, which raises `DTDForbidden(name, sysid, pubid)`; expat has already decoded the
  document, so encoding differentials cannot arise.
  ([`expatreader.py`](https://github.com/tiran/defusedxml/blob/v0.7.1/defusedxml/expatreader.py)).
- **feedparser 6.0.11** (Python): decodes first, scans second. `api.parse` calls `convert_to_utf8(...)` and then
  `replace_doctype(data)`, which "Divide[s] the document into two groups by finding the location of the first element
  that doesn't begin with '<?' or '<!'", removes entity declarations from the head, and keeps only a whitelist of safe
  ones in a rewritten DOCTYPE. Its scan is a regex over the converted bytes, so its safety rests on its own decoding
  being the one the parser sees; it keeps DOCTYPEs where this module refuses them.
  ([`sanitizer.py`](https://github.com/kurtmckee/feedparser/blob/6.0.11/feedparser/sanitizer.py),
  [`api.py`](https://github.com/kurtmckee/feedparser/blob/6.0.11/feedparser/api.py)).
- **Rome 2.1.0** itself: `WireFeedInput` sets `http://apache.org/xml/features/disallow-doctype-decl` unless
  `allowDoctypes` (default false) and ignores the failure when the provider does not know the feature. This is the
  second lock; the scan is the first and does not depend on the feature being honoured.
- **JDK Xerces**: encoding detection is internal to the parser and is bypassed for a `Reader` (F3). Nothing in the
  JDK offers a pre-parse DOCTYPE probe.
- **OWASP / WAF practice**: configure the parser; filters in front of a parser are bypassed by re-encoding (F8). The
  one design that survives is to decode the same way the parser does, or to refuse what cannot be decoded identically.
  This scan does the second by reading every candidate decoding and refusing the undecidable.

## Pitfalls

1. **Choosing an encoding by RFC 7303 or Appendix F order.** Bypassed by lenient declaration-wins, mark-ignoring
   `text/*` paths and the second mark (F1, F2).
2. **Stopping at the first byte that cannot be markup and trusting it.** A decoder that drops a unit lets the parser
   still be inside a comment where the scan has left it (F5). Junk is a safe stop only where no malformed unit lies in
   the examined region.
3. **Closing comments the way the specification does.** Rome's repair reader closes them differently (F4). Treat
   any `--` inside a comment, and any unterminated comment or processing instruction, as ill-formed instead of
   modelling the repair reader's state machine.
4. **Searching for `<!DOCTYPE` as text.** It occurs legitimately inside comments, processing instructions and CDATA
   (N10, N11, N32). A walk that stops at the first start tag has neither the false positives nor the unbounded cost.
5. **Matching case-sensitively or requiring white space after the keyword.** `<!doctype`, `<!DocType` and
   `<!DOCTYPErss>` all appear in the vectors; a superset costs nothing.
6. **Reading the declaration only when it is first.** Rome reads it after any marks and only if the first four
   bytes are a guess pattern; the scan reads any declaration-shaped start (`<?` at the boundary, up to the first `>`
   inside 4096 bytes), including `<?xml-stylesheet ... encoding=...?>`, which Rome's pattern also matches.
7. **A default encoding the scan did not read.** `XmlReader` falls back to a default encoding (`US-ASCII` for a bare
   `text/xml`, `UTF-8` otherwise, or the caller's default in the four-argument form). The scan assumes the default is
   ASCII-compatible and allow-listed; a caller that passes another default voids the guarantee.
8. **Exception messages.** `XmlReader`'s `UnsupportedEncodingException` message echoes the hostile charset name; the
   scan refuses such names first (U11) and the refusal carries a constant message and an enum reason, never document
   or header content.
9. **Costs, stated.** Refused or reclassified though Rome would parse: a leading comment containing `--` (M01, M09:
   Rome's repair reader drops it; the scan says `IllFormed`); EUC-JP or GB18030 documents with non-ASCII bytes in the
   prolog (U29, U30); any name off the allowlist even if the JDK knows it (for example `ISO-2022-JP`, `Big5-HKSCS`,
   `TIS-620`, `ks_c_5601-1987`, `x-windows-949`); an RSS 0.91 document with the Netscape DOCTYPE. None occurs in the
   70 sampled documents.
10. **Platform dependence of the swallow list.** The two encodings flagged are those whose JDK 23 decoders swallow;
    another JDK could differ. The rule is stated in terms of the hazard (a high byte in the examined region), so the
    list can only grow safely.

## Recommendation for this repository

Implement the scan as an `internal object PrologScan { fun scan(body: ByteArray, contentType: String?):
PrologScanResult }` in `commonMain` of `webtools-feeds`, with the sealed result below, call it before any parser, and
never run a parser on anything but `NoDoctype`.

**Algorithm** (`Reading` = ASCII-compatible bytes, UTF-16BE units, UTF-16LE units):

1. Find the leading run of byte-order marks (`EF BB BF`, `FE FF`, `FF FE`), recording the offset after each; start
   the walk after the whole run. A run reaching 64 KiB gives `Undecodable(PrologTooLong)`.
2. `Undecodable(WideOrEbcdicPrefix)` if the document starts with a UCS-4 mark (`00 00 FE FF`, `FF FE 00 00`,
   `00 00 FF FE`, `FE FF 00 00`) or if any recorded offset starts `00 00 00 3C`, `3C 00 00 00`, `00 00 3C 00`,
   `00 3C 00 00` or `4C 6F A7 94`.
3. Collect encoding names: every `charset=` in the `Content-Type` value (case-insensitive, optional blanks, optional
   quote, ending at `;`, `,`, blank or quote; wherever it occurs, including inside other parameters), and, per reading
   and per recorded offset, every `encoding=` quoted value in the text before the first `>` within the first 4096 bytes
   when the units there begin `<?`. Any name off the allowlist gives `Undecodable(UnlistedEncodingName)`. Allowlist
   (safe): `utf-8, utf8, us-ascii, ascii, latin1, iso-8859-N, iso8859-N, iso_8859-N, windows-125x, cp125x, koi8-r,
   koi8-u, shift_jis, sjis, windows-31j, euc-kr, gbk, gb2312, big5, utf-16, utf-16be, utf-16le`; allowlisted but
   swallow-prone: `euc-jp, gb18030`.
4. Walk each reading (UTF-16 readings skip one leading U+FEFF) over at most 64 KiB: skip `20 09 0A 0D`; `<?` skips to
   `?>`; `<!--` finds the first `--`, which must be followed by `>` (else `IllFormed`); `<!DOCTYPE` (any ASCII case)
   reads the name up to white space, `>` or `[`: `html` (any case) is `HtmlDocument`, anything else `Doctype`; `<!` +
   ASCII letter is `Doctype`; `<` + name start reads the element name: `html` (any case) is `HtmlDocument`, else
   `NoDoctype`; any other character is `NoDoctype`. An unterminated comment or processing instruction is `IllFormed`;
   running out of the 64 KiB with the prolog unfinished is `Undecodable(PrologTooLong)`.
5. **Decoder-divergence rule.** A reading that ends `NoDoctype` becomes `Undecodable(MalformedSequence)` when the units
   it read include an unpaired surrogate (16-bit readings), or, if a swallow-prone name was collected, any byte at or
   above 0x80 (byte reading).
6. Return the worst of the three readings: `Undecodable > Doctype > IllFormed > HtmlDocument > NoDoctype`.

**Mapping onto the fetch result.**

| Scan result | Fetch result |
|---|---|
| `NoDoctype` | parse proceeds |
| `HtmlDocument` | `NotAFeed(Html)`; no parser runs (so a DOCTYPE named `html` with an internal subset, H09, is also `NotAFeed(Html)`) |
| `Doctype` | `Refused`, constant message |
| `Undecodable(reason)` | `Refused`, constant message; the enum reason is for diagnostics only |
| `IllFormed` | `Malformed`, no document content in the message |

**Choices that the sources do not settle, with reasons.**

- *64 KiB.* The prolog of every sampled document ended by byte 149, Rome's own declaration window is 4096 bytes, and
  the 2 MiB body cap is 32 times larger; 64 KiB leaves more than 400 times the observed need and keeps the scan to
  at most three 64 KiB passes. The bound is a constant in one place (`MAX_PROLOG_BYTES`).
- *A DOCTYPE named `html` with an internal subset is `HtmlDocument`.* No parser runs on either outcome, so safety is
  identical; the name is the better classification of a web page.
- *Unterminated comment or processing instruction is `IllFormed`, not `NoDoctype`.* Such a prolog is not well-formed,
  and returning `NoDoctype` would hand a document to the parser on the strength of an assumption about how the repair
  reader treats the unfinished construct.
- *`--` inside a comment is `IllFormed`.* The cost is the leading-comment case in pitfall 9; the alternative needs a
  model of the repair reader's comment grammar (F4), which the scan is meant to avoid.
- *Junk (any non-white-space, non-`<` character) is `NoDoctype`, not `IllFormed`.* It is the parser's to reject and
  JSON or plain text should reach whichever classification the module applies afterwards; the decoder-divergence rule
  keeps this safe.

**Reference implementation** (compiled and run as shown; make the declarations `internal` and add the module's
package, which are the only changes):

```kotlin
sealed interface PrologScanResult {
    data object NoDoctype : PrologScanResult
    data object HtmlDocument : PrologScanResult
    data object IllFormed : PrologScanResult
    data object Doctype : PrologScanResult
    data class Undecodable(val reason: UndecodableReason) : PrologScanResult
}

enum class UndecodableReason { WideOrEbcdicPrefix, UnlistedEncodingName, PrologTooLong, MalformedSequence }

object PrologScan {
    const val MAX_PROLOG_BYTES: Int = 64 * 1024
    private const val DECLARATION_WINDOW_BYTES = 4096

    fun scan(body: ByteArray, contentType: String?): PrologScanResult {
        val boundaries = bomBoundaries(body) ?: return PrologScanResult.Undecodable(UndecodableReason.PrologTooLong)
        val start = boundaries.last()
        if (hasUcs4Bom(body) || boundaries.any { startsWideOrEbcdic(body, it) }) {
            return PrologScanResult.Undecodable(UndecodableReason.WideOrEbcdicPrefix)
        }
        val names = buildList {
            addAll(contentTypeCharsets(contentType))
            for (reading in Reading.entries) for (boundary in boundaries) addAll(declaredEncodings(Units(body, boundary, reading)))
        }
        val listings = names.map(::listing)
        if (Listing.Unlisted in listings) return PrologScanResult.Undecodable(UndecodableReason.UnlistedEncodingName)
        val swallowProne = Listing.SwallowProne in listings
        return Reading.entries.map { scanReading(Units(body, start, it), swallowProne) }.reduce(::worse)
    }

    private fun rank(r: PrologScanResult): Int = when (r) {
        PrologScanResult.NoDoctype -> 0
        PrologScanResult.HtmlDocument -> 1
        PrologScanResult.IllFormed -> 2
        PrologScanResult.Doctype -> 3
        is PrologScanResult.Undecodable -> 4
    }

    private fun worse(a: PrologScanResult, b: PrologScanResult): PrologScanResult = if (rank(b) > rank(a)) b else a

    //region byte-level prefixes
    /** Offsets after each byte-order mark in the leading run (0 first); null when the run reaches the byte bound. */
    private fun bomBoundaries(b: ByteArray): List<Int>? {
        val out = mutableListOf(0)
        var p = 0
        while (p < MAX_PROLOG_BYTES) {
            val n = when {
                b.matches(p, 0xFE, 0xFF) || b.matches(p, 0xFF, 0xFE) -> 2
                b.matches(p, 0xEF, 0xBB, 0xBF) -> 3
                else -> 0
            }
            if (n == 0) return out
            p += n
            out += p
        }
        return null
    }

    private fun hasUcs4Bom(b: ByteArray): Boolean =
        b.matches(0, 0x00, 0x00, 0xFE, 0xFF) || b.matches(0, 0xFF, 0xFE, 0x00, 0x00) ||
            b.matches(0, 0x00, 0x00, 0xFF, 0xFE) || b.matches(0, 0xFE, 0xFF, 0x00, 0x00)

    private fun startsWideOrEbcdic(b: ByteArray, at: Int): Boolean =
        b.matches(at, 0, 0, 0, 0x3C) || b.matches(at, 0x3C, 0, 0, 0) || b.matches(at, 0, 0, 0x3C, 0) ||
            b.matches(at, 0, 0x3C, 0, 0) || b.matches(at, 0x4C, 0x6F, 0xA7, 0x94)

    private fun ByteArray.matches(at: Int, vararg expected: Int): Boolean {
        if (at + expected.size > size) return false
        for (k in expected.indices) if ((this[at + k].toInt() and 0xFF) != expected[k]) return false
        return true
    }
    //endregion

    //region encoding names
    private fun contentTypeCharsets(contentType: String?): List<String> {
        if (contentType == null) return emptyList()
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = contentType.indexOf("charset", from, ignoreCase = true)
            if (at < 0) return out
            var i = at + "charset".length
            from = i
            while (i < contentType.length && (contentType[i] == ' ' || contentType[i] == '\t')) i++
            if (i >= contentType.length || contentType[i] != '=') continue
            i++
            while (i < contentType.length && (contentType[i] == ' ' || contentType[i] == '\t')) i++
            if (i < contentType.length && (contentType[i] == '"' || contentType[i] == '\'')) i++
            val nameStart = i
            while (i < contentType.length && contentType[i] !in ";, \t\"'") i++
            if (i > nameStart) out += contentType.substring(nameStart, i)
        }
    }

    /** Every `encoding="..."` before the first `>` of a declaration-shaped start (`<?`), within the first 4096 bytes. */
    private fun declaredEncodings(u: Units): List<String> {
        val n = minOf(u.size, DECLARATION_WINDOW_BYTES / u.reading.stride)
        if (n < 2 || u[0] != '<'.code || u[1] != '?'.code) return emptyList()
        var gt = -1
        for (i in 0 until n) if (u[i] == '>'.code) { gt = i; break }
        if (gt < 0) return emptyList()
        val text = buildString(gt) { for (i in 0 until gt) append(if (u[i] < 0x80) u[i].toChar() else '�') }
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = text.indexOf("encoding", from, ignoreCase = true)
            if (at < 0) return out
            var i = at + "encoding".length
            from = i
            while (i < text.length && isRegexSpace(text[i])) i++
            if (i >= text.length || text[i] != '=') continue
            i++
            while (i < text.length && isRegexSpace(text[i])) i++
            if (i >= text.length || (text[i] != '"' && text[i] != '\'')) continue
            val close = text.indexOf(text[i], i + 1)
            if (close < 0) continue
            out += text.substring(i + 1, close).trim()
        }
    }

    private fun isRegexSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    private enum class Listing { Safe, SwallowProne, Unlisted }

    private val SAFE_EXACT = setOf(
        "utf-8", "utf8", "us-ascii", "ascii", "latin1", "koi8-r", "koi8-u",
        "shift_jis", "sjis", "windows-31j", "euc-kr", "gbk", "gb2312", "big5",
        "utf-16", "utf-16be", "utf-16le",
    )

    /** Encodings whose JDK decoder drops the ASCII byte that follows a malformed lead byte. */
    private val SWALLOW_PRONE = setOf("euc-jp", "gb18030")

    private fun listing(raw: String): Listing {
        val n = raw.trim().lowercase()
        if (n in SAFE_EXACT) return Listing.Safe
        if (n in SWALLOW_PRONE) return Listing.SwallowProne
        fun digits(prefix: String, min: Int, max: Int) = n.startsWith(prefix) && n.length - prefix.length in min..max &&
            n.substring(prefix.length).all { it in '0'..'9' }
        val single = digits("iso-8859-", 1, 2) || digits("iso8859-", 1, 2) || digits("iso_8859-", 1, 2) ||
            (n.startsWith("windows-125") && n.length == 12 && n[11] in '0'..'9') ||
            (n.startsWith("cp125") && n.length == 6 && n[5] in '0'..'9')
        return if (single) Listing.Safe else Listing.Unlisted
    }
    //endregion

    //region the prolog walk
    private enum class Reading(val stride: Int, val littleEndian: Boolean) {
        AsciiCompatible(1, false), Utf16Be(2, false), Utf16Le(2, true),
    }

    private class Units(private val bytes: ByteArray, private val start: Int, val reading: Reading) {
        val size: Int = maxOf(0, (bytes.size - start) / reading.stride)

        /** The highest unit index the walk has read. */
        var reach: Int = -1
            private set

        operator fun get(i: Int): Int {
            if (i > reach) reach = i
            return peek(i)
        }

        fun peek(i: Int): Int {
            val p = start + i * reading.stride
            if (reading.stride == 1) return bytes[p].toInt() and 0xFF
            val first = bytes[p].toInt() and 0xFF
            val second = bytes[p + 1].toInt() and 0xFF
            return if (reading.littleEndian) (second shl 8) or first else (first shl 8) or second
        }
    }

    private fun isSpace(c: Int) = c == 0x20 || c == 0x09 || c == 0x0A || c == 0x0D

    private fun isNameStart(c: Int) = c == ':'.code || c == '_'.code || c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c >= 0x80

    private fun scanReading(u: Units, swallowProne: Boolean): PrologScanResult {
        val result = walk(u)
        return if (result == PrologScanResult.NoDoctype && decoderMayHaveDiverged(u, swallowProne)) {
            PrologScanResult.Undecodable(UndecodableReason.MalformedSequence)
        } else {
            result
        }
    }

    /**
     * True when a decoder could have dropped or replaced a unit inside the region the walk read: an unpaired surrogate
     * in a 16-bit reading, or (for the two encodings whose JDK decoder swallows the byte after a malformed lead byte)
     * any byte at or above 0x80 in the byte reading.
     */
    private fun decoderMayHaveDiverged(u: Units, swallowProne: Boolean): Boolean {
        val last = minOf(u.reach, u.size - 1)
        if (u.reading.stride == 1) {
            return swallowProne && (0..last).any { u.peek(it) >= 0x80 }
        }
        var i = 0
        while (i <= last) {
            val c = u.peek(i)
            when {
                c in 0xD800..0xDBFF -> if (i + 1 < u.size && u.peek(i + 1) in 0xDC00..0xDFFF) i++ else return true
                c in 0xDC00..0xDFFF -> return true
            }
            i++
        }
        return false
    }

    private fun walk(u: Units): PrologScanResult {
        val limit = MAX_PROLOG_BYTES / u.reading.stride
        val end = minOf(u.size, limit)
        val capped = u.size > limit
        val tooLong = PrologScanResult.Undecodable(UndecodableReason.PrologTooLong)
        var i = if (u.reading.stride == 2 && end > 0 && u[0] == 0xFEFF) 1 else 0
        while (true) {
            if (i >= end) return if (capped) tooLong else PrologScanResult.NoDoctype
            val c = u[i]
            if (isSpace(c)) { i++; continue }
            if (c != '<'.code) return PrologScanResult.NoDoctype
            if (i + 1 >= end) return if (capped) tooLong else PrologScanResult.NoDoctype
            val c1 = u[i + 1]
            when {
                c1 == '?'.code -> {
                    val j = u.find(i + 2, end, "?>")
                    if (j < 0) return if (capped) tooLong else PrologScanResult.IllFormed
                    i = j + 2
                }
                c1 == '!'.code -> {
                    if (i + 3 < end && u[i + 2] == '-'.code && u[i + 3] == '-'.code) {
                        val j = u.find(i + 4, end, "--")
                        if (j < 0 || j + 2 >= end) return if (capped) tooLong else PrologScanResult.IllFormed
                        if (u[j + 2] != '>'.code) return PrologScanResult.IllFormed
                        i = j + 3
                    } else if (u.matchesIgnoreCase(i + 2, end, "DOCTYPE")) {
                        var k = i + 9
                        while (k < end && isSpace(u[k])) k++
                        val name = StringBuilder()
                        while (k < end && !isSpace(u[k]) && u[k] != '>'.code && u[k] != '['.code) {
                            name.append(if (u[k] < 0x80) u[k].toChar() else '�')
                            k++
                        }
                        return if (name.toString().equals("html", ignoreCase = true)) PrologScanResult.HtmlDocument else PrologScanResult.Doctype
                    } else {
                        val c2 = if (i + 2 < end) u[i + 2] else -1
                        return if (c2 in 'a'.code..'z'.code || c2 in 'A'.code..'Z'.code) PrologScanResult.Doctype else PrologScanResult.NoDoctype
                    }
                }
                isNameStart(c1) -> {
                    var k = i + 1
                    val name = StringBuilder()
                    while (k < end && !isSpace(u[k]) && u[k] != '/'.code && u[k] != '>'.code) {
                        name.append(if (u[k] < 0x80) u[k].toChar() else '�')
                        k++
                    }
                    return if (name.toString().equals("html", ignoreCase = true)) PrologScanResult.HtmlDocument else PrologScanResult.NoDoctype
                }
                else -> return PrologScanResult.NoDoctype
            }
        }
    }

    private fun Units.find(from: Int, end: Int, pattern: String): Int {
        var i = from
        while (i + pattern.length <= end) {
            var k = 0
            while (k < pattern.length && this[i + k] == pattern[k].code) k++
            if (k == pattern.length) return i
            i++
        }
        return -1
    }

    private fun Units.matchesIgnoreCase(at: Int, end: Int, upperPattern: String): Boolean {
        if (at + upperPattern.length > end) return false
        for (k in upperPattern.indices) {
            var c = this[at + k]
            if (c in 'a'.code..'z'.code) c -= 32
            if (c != upperPattern[k].code) return false
        }
        return true
    }
    //endregion
}
```

It uses only common Kotlin (`indexOf(..., ignoreCase = true)`, `lowercase()`, `buildList`, `Enum.entries`, `data
object`); it compiled for the JVM with the Kotlin 2.2.0 compiler and as a Kotlin/JS klib against the 2.2.0 JS standard
library, and nothing in it touches `java.nio.charset`.

**Tests.**

- *Level 4a (`commonTest`)*: the table below, one row per vector, asserting the scan result. Build the bytes with small
  helpers (`utf8(...)`, `utf16le(...)`, raw `byteArrayOf(...)` for marks, surrogates and legacy bytes); keep the
  70,000-byte and 64 KiB-boundary vectors generated, not stored.
- *`jvmTest` differential*: for every row, run Rome's `XmlReader` (the constructor shapes the module uses, plus the
  four-argument form with `UTF-8` default) + `XmlFixerReader` + the JDK SAX parser with `disallow-doctype-decl`, and
  assert the invariant **if the pipeline reaches "DOCTYPE is disallowed" the scan result is not `NoDoctype`**; add a
  seeded generator mixing marks, comments, processing instructions, DOCTYPEs, surrogates, legacy lead bytes, header
  charsets and declaration encodings (the one used for these findings reached the invariant 49,700 times in 1.8 million
  cases and 41,500 times in 0.8 million 16-bit cases with no violation).
- *Contract*: pass `UTF-8` as the explicit default encoding to `XmlReader` so the scan's allowlist covers the fallback.

### Level 4a vector catalogue

Outcome names are the scan results; the last column records what the Rome 2.1.0 + repair reader + JDK SAX pipeline did
across six `XmlReader` constructor shapes ("reaches DOCTYPE" if any shape reached it, else "parses" if any parsed, else
"error"). "none" under Content-Type means the scan is called with `null`. Byte values are hexadecimal. Every row's scan result was produced by running the reference implementation above, and no row has a
pipeline of "reaches DOCTYPE" with scan `NoDoctype`.

| ID | Bytes (recipe) | Content-Type | Scan result | Pipeline |
|---|---|---|---|---|
| N01 | UTF-8: rss root, no declaration | application/xml | NoDoctype | parses |
| N02 | UTF-8: declaration, rss root | application/xml | NoDoctype | parses |
| N03 | UTF-8: EF BB BF, declaration, atom root | application/atom+xml | NoDoctype | parses |
| N04 | UTF-16LE: FF FE, declaration, rss root | application/xml | NoDoctype | parses |
| N05 | UTF-16BE: FE FF, declaration, rss root | application/xml | NoDoctype | parses |
| N06 | UTF-16LE, no BOM, no declaration, rss root | text/xml; charset=utf-16le | NoDoctype | parses |
| N07 | UTF-8: blanks and a line feed, declaration, rss root | application/xml | NoDoctype | parses |
| N08 | UTF-8: comment, declaration, rss root | application/xml | NoDoctype | parses |
| N09 | UTF-8: declaration, comment, xml-stylesheet PI, rss root | application/xml | NoDoctype | parses |
| N10 | UTF-8: the text `<!DOCTYPE a>` only inside a comment | application/xml | NoDoctype | parses |
| N11 | UTF-8: the text `<!DOCTYPE a>` only inside a processing instruction | application/xml | NoDoctype | parses |
| N12 | ISO-8859-1: declaration encoding="ISO-8859-1", comment holding E9, rss root | application/xml; charset=iso-8859-1 | NoDoctype | parses |
| N13 | UTF-8 body, header charset=windows-1252, no declaration | text/xml; charset=windows-1252 | NoDoctype | parses |
| N14 | UTF-8: upper-case CHARSET="UTF-8" in quotes in the header | application/rss+xml; CHARSET="UTF-8" | NoDoctype | parses |
| N15 | Shift_JIS: declaration, comment holding 83 5C (a trail byte equal to a backslash) before the root | application/xml | NoDoctype | parses |
| N16 | UTF-8: no Content-Type at all | none | NoDoctype | parses |
| N17 | empty body | application/xml | NoDoctype | error |
| N18 | UTF-8: JSON text (junk is the parser's to reject) | application/json | NoDoctype | error |
| N19 | UTF-8: element `<htmlx/>`, not an html root | application/xml | NoDoctype | parses |
| N20 | UTF-8: ampersand reference before the DOCTYPE (junk the parser rejects) | application/xml | NoDoctype | error |
| N21 | UTF-8: the word junk before the DOCTYPE (junk the parser rejects) | application/xml | NoDoctype | error |
| N22 | UTF-8: an empty comment `<!---->` then rss root | application/xml | NoDoctype | parses |
| N23 | UTF-8: rss body served as text/html | text/html | NoDoctype | parses |
| N24 | UTF-8: declaration padded with 4100 blanks so that encoding="ibm037" lies beyond the 4096-byte window | application/xml | NoDoctype | error |
| N25 | UTF-16LE: FF FE, comment holding U+1F600 as a valid surrogate pair, rss root | application/xml | NoDoctype | parses |
| N26 | EUC-JP header, ASCII-only prolog, rss root | application/xml; charset=euc-jp | NoDoctype | parses |
| N27 | GB18030 header, ASCII-only prolog, rss root | application/xml; charset=gb18030 | NoDoctype | parses |
| N28 | windows-1252 header, comment holding E9 and 96, rss root | application/xml; charset=windows-1252 | NoDoctype | parses |
| N29 | Big5 header, comment holding A4 A4 (a two-byte character), rss root | application/xml; charset=big5 | NoDoctype | parses |
| N30 | UTF-8: five byte-order marks (EF BB BF x5), rss root | application/xml | NoDoctype | error |
| N31 | UTF-8: root element starting at byte offset 65534 (the last offset that fits in the 65536-byte bound) | application/xml | NoDoctype | parses |
| N32 | UTF-8: `<!DOCTYPE html>` only inside a comment, rss root | application/xml | NoDoctype | parses |
| D01 | UTF-8: DOCTYPE rss SYSTEM, rss root | application/xml | Doctype | reaches DOCTYPE |
| D02 | UTF-8: declaration, DOCTYPE rss SYSTEM | application/xml | Doctype | reaches DOCTYPE |
| D03 | UTF-8: DOCTYPE with an internal subset declaring an entity | application/xml | Doctype | reaches DOCTYPE |
| D04 | UTF-8: DOCTYPE rss [`<!ENTITY x SYSTEM "file:///etc/passwd">`] | application/xml | Doctype | reaches DOCTYPE |
| D05 | UTF-8: declaration, comment, PI, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D06 | UTF-8: blanks, carriage return, tab, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D07 | UTF-8: lower-case `<!doctype rss>` | application/xml | Doctype | error |
| D08 | UTF-8: mixed-case `<!DocType rss>` | application/xml | Doctype | error |
| D09 | UTF-8: `<!DOCTYPE>` with no name | application/xml | Doctype | reaches DOCTYPE |
| D10 | UTF-8: `<!DOCTYPErss>` with no blank | application/xml | Doctype | reaches DOCTYPE |
| D11 | UTF-8: bare `<!ENTITY a "b">` before the root | application/xml | Doctype | error |
| D12 | UTF-8: bare `<!ELEMENT rss ANY>` before the root | application/xml | Doctype | error |
| D13 | UTF-8: RSS 0.91 DOCTYPE naming the Netscape DTD | application/xml | Doctype | reaches DOCTYPE |
| D14 | UTF-8: DOCTYPE text inside a comment, then a real DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D15 | UTF-8: EF BB BF, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D16 | UTF-8: EF BB BF EF BB BF, declaration, DOCTYPE (two byte-order marks) | application/xml | Doctype | error |
| D17 | UTF-8: EF BB BF EF BB BF, declaration, DOCTYPE (Rome's retry consumes the second mark) | text/html | Doctype | reaches DOCTYPE |
| D18 | UTF-8 body behind FE FF (Rome reads ASCII and ignores the mark) | text/xml | Doctype | reaches DOCTYPE |
| D19 | UTF-8 body behind FF FE | text/html | Doctype | reaches DOCTYPE |
| D20 | UTF-8 body behind FF FE | none | Doctype | reaches DOCTYPE |
| D21 | UTF-8 body behind FE FF | text/plain | Doctype | reaches DOCTYPE |
| D22 | UTF-16LE: FF FE, declaration, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D23 | UTF-16BE: FE FF, declaration, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D24 | UTF-16LE, no BOM, DOCTYPE | text/xml; charset=utf-16le | Doctype | reaches DOCTYPE |
| D25 | UTF-16LE, no BOM, DOCTYPE, capital C in `Charset=` | text/xml; Charset=utf-16le | Doctype | error |
| D26 | UTF-16BE, no BOM, declaration encoding="UTF-16", DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D27 | UTF-16LE, no BOM, declaration encoding="UTF-16", DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D28 | UTF-16LE, no BOM, declaration without encoding, DOCTYPE | application/xml | Doctype | error |
| D29 | UTF-16BE, no BOM, declaration without encoding, DOCTYPE | application/xml | Doctype | error |
| D30 | UTF-8: `<?xml-stylesheet encoding="UTF-16"?>` then an ASCII DOCTYPE | application/xml | Doctype | error |
| D31 | UTF-8: EF BB BF, declaration encoding="ISO-8859-1", DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D32 | UTF-8: DOCTYPE with tab, carriage return and line feed between keyword and name | application/xml | Doctype | reaches DOCTYPE |
| D33 | UTF-8: declaration, 60000 bytes of comment, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D34 | UTF-8: XML 1.1 declaration, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D35 | Shift_JIS: declaration, comment holding 83 5C, DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D36 | UTF-16LE: FF FE, lower-case `<!doctype rss>` | application/xml | Doctype | error |
| D37 | UTF-8: declaration with single-quoted encoding='utf-8', DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D38 | EUC-JP header, comment holding 8F then dash dash close, then DOCTYPE (refused although the parser, which drops a dash, errors) | application/xml; charset=euc-jp | Doctype | reaches DOCTYPE |
| D39 | UTF-16LE: FF FE, comment holding a valid surrogate pair, then DOCTYPE | application/xml | Doctype | reaches DOCTYPE |
| D40 | UTF-8: `<!DOCTYPE htmlx>` (not html) | text/html | Doctype | reaches DOCTYPE |
| H01 | UTF-8: `<!DOCTYPE html>` | text/html | HtmlDocument | reaches DOCTYPE |
| H02 | UTF-8: `<!doctype html>` | text/html; charset=utf-8 | HtmlDocument | error |
| H03 | UTF-8: `<!DOCTYPE HTML PUBLIC ...>` (HTML 4.01) | text/html | HtmlDocument | reaches DOCTYPE |
| H04 | UTF-8: XHTML 1.0 `<!DOCTYPE html PUBLIC ...>` | text/html | HtmlDocument | reaches DOCTYPE |
| H05 | UTF-8: html root, no DOCTYPE | text/html | HtmlDocument | parses |
| H06 | UTF-8: declaration, upper-case `<HTML>` root | text/html | HtmlDocument | parses |
| H07 | UTF-8: EF BB BF, `<!DOCTYPE html>` | text/html | HtmlDocument | reaches DOCTYPE |
| H08 | UTF-16LE: FF FE, `<!DOCTYPE html>` | text/html | HtmlDocument | reaches DOCTYPE |
| H09 | UTF-8: `<!DOCTYPE html [<!ENTITY x SYSTEM "file:///etc/passwd">]>` (internal subset, root name html; no parser runs) | text/html | HtmlDocument | reaches DOCTYPE |
| H10 | UTF-8: comment, then `<!DOCTYPE html>` | text/html | HtmlDocument | reaches DOCTYPE |
| H11 | UTF-8: a 502 error page, `<html>` with no DOCTYPE | text/html | HtmlDocument | parses |
| H12 | UTF-8: `<!DOCTYPE   HtMl   >` (mixed-case name, extra blanks) | text/html | HtmlDocument | reaches DOCTYPE |
| M01 | UTF-8: comment containing dash dash | application/xml | IllFormed | parses |
| M02 | UTF-8: comment ending in three dashes then DOCTYPE | application/xml | IllFormed | error |
| M03 | UTF-8: comment ending in five dashes then DOCTYPE (Rome's repair reader closes it; the parser reaches the DOCTYPE) | application/xml | IllFormed | reaches DOCTYPE |
| M04 | UTF-8: comment never closed, holding a DOCTYPE | application/xml | IllFormed | error |
| M05 | UTF-8: processing instruction never closed, holding a DOCTYPE | application/xml | IllFormed | error |
| M06 | UTF-8: the polyglot `<!-- a --- ><?xml version="1.0"?><!----><!--><!DOCTYPE rss SYSTEM "x">` (an ill-formed leading comment the repair reader trims differently from the specification) | application/xml | IllFormed | reaches DOCTYPE |
| M07 | UTF-8: `<!-->` then DOCTYPE | application/xml | IllFormed | error |
| M08 | UTF-8: `<!--->` then DOCTYPE | application/xml | IllFormed | error |
| M09 | UTF-16LE: FF FE, comment containing dash dash | application/xml | IllFormed | parses |
| M10 | UTF-8: declaration never closed (no `?>`) | application/xml | IllFormed | error |
| U01 | UTF-32BE, no BOM (00 00 00 3C ...) | application/xml; charset=utf-32 | Undecodable/WideOrEbcdicPrefix | reaches DOCTYPE |
| U02 | UTF-32LE, no BOM (3C 00 00 00 ...) | application/xml | Undecodable/WideOrEbcdicPrefix | error |
| U03 | UTF-32BE with 00 00 FE FF | application/xml; charset=utf-32 | Undecodable/WideOrEbcdicPrefix | reaches DOCTYPE |
| U04 | UTF-32LE with FF FE 00 00 | application/xml; charset=utf-32 | Undecodable/WideOrEbcdicPrefix | error |
| U05 | EBCDIC (IBM1047) starting 4C 6F A7 94, declaration encoding="cp1047", DOCTYPE | application/xml | Undecodable/WideOrEbcdicPrefix | reaches DOCTYPE |
| U06 | EBCDIC (IBM037), no declaration, DOCTYPE | application/xml; charset=ibm037 | Undecodable/UnlistedEncodingName | reaches DOCTYPE |
| U07 | UTF-8 body, text `+ADw-!DOCTYPE ...+AD4-` | application/xml; charset=utf-7 | Undecodable/UnlistedEncodingName | error |
| U08 | UTF-8 body, declaration encoding="UTF-7" | application/xml | Undecodable/UnlistedEncodingName | error |
| U09 | UTF-8 body, declaration encoding="ISO-2022-JP", DOCTYPE | application/xml | Undecodable/UnlistedEncodingName | reaches DOCTYPE |
| U10 | UTF-8 body, declaration encoding="ibm037", DOCTYPE | application/xml | Undecodable/UnlistedEncodingName | error |
| U11 | UTF-8 body, header charset the JDK does not know | application/xml; charset=bogus-9 | Undecodable/UnlistedEncodingName | parses |
| U12 | UTF-8 body, header charset=utf-32 over ASCII bytes | application/xml; charset=utf-32 | Undecodable/UnlistedEncodingName | parses |
| U13 | UTF-8 body, two charset parameters, the second ibm037 | application/xml; charset=utf-8; charset=ibm037 | Undecodable/UnlistedEncodingName | parses |
| U14 | UTF-8 body, charset hidden inside a quoted parameter | application/xml; foo="charset=ibm037" | Undecodable/UnlistedEncodingName | parses |
| U15 | UTF-8: `<?xml-stylesheet encoding="UTF-7"?>` then an ASCII DOCTYPE | application/xml | Undecodable/UnlistedEncodingName | error |
| U16 | UTF-8: EF BB BF, declaration naming UTF-32 | application/xml | Undecodable/UnlistedEncodingName | error |
| U17 | UTF-8: 70000 bytes of comment before the root | application/xml | Undecodable/PrologTooLong | parses |
| U18 | UTF-8: DOCTYPE after 70000 bytes of comment | application/xml | Undecodable/PrologTooLong | reaches DOCTYPE |
| U19 | UTF-8: root element starting at byte offset 65535 (`<r` needs two bytes inside the bound) | application/xml | Undecodable/PrologTooLong | parses |
| U20 | UTF-8: comment of 70000 bytes never closed | application/xml | Undecodable/PrologTooLong | error |
| U21 | UTF-16LE: FF FE, 40000 units of comment before the root (80000 bytes) | application/xml | Undecodable/PrologTooLong | parses |
| U22 | 70000 bytes of FE FF byte-order marks before the document | application/xml | Undecodable/PrologTooLong | error |
| U23 | UTF-16LE: FF FE, `<!-- `, lone high surrogate D800, `-->`, x, `<!-- -->`, DOCTYPE (the decoder drops the first dash after the surrogate) | application/xml | Undecodable/MalformedSequence | reaches DOCTYPE |
| U24 | UTF-16BE: FE FF, the same document in big-endian order | application/xml | Undecodable/MalformedSequence | reaches DOCTYPE |
| U25 | UTF-16LE: FF FE, lone low surrogate DC00 inside a comment | application/xml | Undecodable/MalformedSequence | parses |
| U26 | UTF-16LE: FF FE, high surrogate D83D followed by a letter, inside a comment | application/xml | Undecodable/MalformedSequence | parses |
| U27 | EUC-JP header: `<!-- `, 8F, `-->`, x, `<!-- -->`, DOCTYPE (the EUC-JP decoder drops the first dash) | application/xml; charset=euc-jp | Undecodable/MalformedSequence | reaches DOCTYPE |
| U28 | EUC-JP declaration, comment holding 8F then dash dash close, rss root | application/xml | Undecodable/MalformedSequence | error |
| U29 | EUC-JP header, comment holding the valid character A4 A2 (a non-ASCII prolog is refused for the two swallowing encodings) | application/xml; charset=euc-jp | Undecodable/MalformedSequence | parses |
| U30 | GB18030 header, comment holding the valid character C4 E3 | application/xml; charset=gb18030 | Undecodable/MalformedSequence | parses |
| U31 | GB18030 declaration, comment holding 81 30 then dash dash close (four-byte lead), then x, comment, DOCTYPE | application/xml | Undecodable/MalformedSequence | error |

(Recipes build a document from the listed parts in the stated encoding; `rss root` is
`<rss version="2.0"><channel><title>t</title></channel></rss>`, `DOCTYPE` is
`<!DOCTYPE rss SYSTEM "http://127.0.0.1:1/x.dtd">`, and "declaration" is `<?xml version="1.0"?>` unless stated.)

## Load-bearing claims

1. **A DOCTYPE can occur only in the prolog, before the first element.** XML 1.0 (Fifth Edition), W3C Recommendation 26
   November 2008, §2.8: "The document type declaration MUST appear before the first element in the document." and
   `prolog ::= XMLDecl? Misc* (doctypedecl Misc*)?`, `Misc ::= Comment | PI | S`.
   <https://www.w3.org/TR/2008/REC-xml-20081126/>
2. **A comment must not contain a double hyphen.** Same document, §2.5: "For compatibility, the string "--"
   (double-hyphen) MUST NOT occur within comments."
3. **Appendix F's byte patterns, and why ASCII-compatible encodings may be read with ASCII bytes.** Same document,
   Appendix F.1: "since all of these encodings use the same bit patterns for the relevant ASCII characters, the
   encoding declaration itself may be read reliably" (for `3C 3F 78 6D`, listing "UTF-8, ISO 646, ASCII, some part
   of ISO 8859, Shift-JIS, EUC, or any other 7-bit, 8-bit, or mixed-width encoding which ensures that the characters
   of ASCII have their normal positions, width, and values").
4. **UTF-7 and similar encodings defeat detection.** Same document, Appendix F.1: "Character encodings such as UTF-7
   that make overloaded usage of ASCII-valued bytes may fail to be reliably detected."
5. **The byte-order mark is a signature, UTF-16 must carry it, UTF-8 may.** Same document, §4.3.3: "Entities encoded
   in UTF-16 MUST and entities encoded in UTF-8 MAY begin with the Byte Order Mark".
6. **RFC 7303 gives BOM then charset, and leaves conflicts with the declaration to consumers.** RFC 7303, July 2014,
   §3.2: "A BOM (Section 3.3) is authoritative if it is present in an XML MIME entity;" "In the absence of a BOM
   (Section 3.3), the charset parameter is authoritative if it is present." and "For XML-aware consumers, note that
   Section 4.3.3 of [XML] does _not_ make it an error for the charset parameter and the XML encoding declaration (or
   the UTF-8 default in the absence of encoding declaration and BOM) to be inconsistent".
   <https://www.rfc-editor.org/rfc/rfc7303>
7. **OWASP's guidance is to reject DOCTYPE in the parser.** OWASP XML External Entity Prevention Cheat Sheet (read 6
   November 2026): "Disable document type definitions (DTDs) whenever possible. Reject DOCTYPE declarations if the
   parser supports it." <https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html>
8. **Re-encoding bypasses blocklist filters (UTF-16, UTF-7).** web-in-security, "XXE cheat sheet", March 2016: "Some
   simple blacklisting countermeasures can probably bypassed by changing the default XML charset (which is UTF-8), to
   a different one, for example, UTF-16" and "The same trick can be applied to UTF-7 as-well."
   <https://web-in-security.blogspot.com/2016/03/xxe-cheat-sheet.html>
9. **A WAF was bypassed by UTF-16BE.** CSAW 2019 "Unagi" write-up (CTFtime writeup 16446), citing the PHDays VI WAF
   bypass contest: "The author found out that if we converted the character encoding of UTF-8 to UTF-16 Big Endian, we
   could easily bypass the WAF." <https://ctftime.org/writeup/16446>
10. **Rome's lenient mode lets the declaration win.** Rome 2.1.0, `XmlReader.calculateHttpEncoding`:
    `if (lenient && xmlEnc != null) {` followed by `encoding = xmlEnc;`.
    <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/XmlReader.java>
11. **Rome's `text/html` retry re-runs detection on the consumed stream.** Same file, `doLenientDetection`:
    `if (httpContentType.startsWith("text/html")) {` ... `httpContentType = "text/xml" + httpContentType;` ...
    `doHttpStream(ex.getInputStream(), httpContentType, true);`; and `getBOMEncoding`'s comment: "returns the BOM in
    the stream, NULL if not present, if there was BOM the in the stream it is consumed".
12. **Rome reads a 4096-byte declaration window and a lower-case charset pattern.** Same file:
    `private static final int BUFFER_SIZE = 4096;` and
    `private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([.[^; ]]*)");`, and
    `"XML prolog or ROOT element not found on first " + offset + " bytes"`.
13. **Rome passes any JDK charset name to `InputStreamReader`.** Same file, `prepareReader`:
    `reader = new InputStreamReader(is, encoding);`.
14. **Rome's repair reader wraps every `build(Reader)` and trims leading comments.** Rome 2.1.0, `WireFeedInput.build(Reader)`:
    `if (xmlHealerOn) {` `reader = new XmlFixerReader(reader);`; `XmlFixerReader`: `private boolean trimStream() throws
    IOException {` (states 3 to 6 implement its comment close).
    <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/WireFeedInput.java>,
    <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/XmlFixerReader.java>
15. **Rome disables DOCTYPE unless allowed.** `WireFeedInput.createSAXBuilder`: `if(!allowDoctypes) {` `setFeature(saxBuilder,
    parser, "http://apache.org/xml/features/disallow-doctype-decl", true);`, with `private boolean allowDoctypes = false;`.
16. **Xerces ignores the declared encoding on a character stream.** JDK jdk-23-ga, `XMLEntityManager`:
    `Reader reader = xmlInputSource.getCharacterStream();` ... `if (reader == null) {` `stream = xmlInputSource.getByteStream();`;
    `XMLEntityScanner.setEncoding`: `if (fCurrentEntity.stream != null) {`.
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/impl/XMLEntityManager.java>,
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/impl/XMLEntityScanner.java>
17. **The JDK UTF-16 decoder discards four bytes for a high surrogate not followed by a low one.** JDK jdk-23-ga,
    `sun.nio.cs.UnicodeDecoder`: `if (!Character.isLowSurrogate(c2))` `return CoderResult.malformedForLength(4);` and
    `// Unpaired low surrogate` `return CoderResult.malformedForLength(2);`.
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.base/share/classes/sun/nio/cs/UnicodeDecoder.java>
18. **defusedxml rejects DTDs from a parser callback.** defusedxml v0.7.1, `expatreader.py`:
    `parser.StartDoctypeDeclHandler = self.defused_start_doctype_decl` with `raise DTDForbidden(name, sysid, pubid)`.
    <https://github.com/tiran/defusedxml/blob/v0.7.1/defusedxml/expatreader.py>
19. **feedparser decodes, then scans for the first element.** feedparser 6.0.11, `sanitizer.replace_doctype`: "Divide
    the document into two groups by finding the location of the first element that doesn't begin with '<?' or '<!'.";
    `api.parse`: `data = convert_to_utf8(result['headers'], data, result)` then
    `result['version'], data, entities = replace_doctype(data)`.
    <https://github.com/kurtmckee/feedparser/blob/6.0.11/feedparser/sanitizer.py>,
    <https://github.com/kurtmckee/feedparser/blob/6.0.11/feedparser/api.py>
20. **Rome's Netscape RSS 0.91 variant requires the DOCTYPE.** Rome 2.1.0, `RSS091NetscapeParser.isMyType`: `&& docType !=
    null` `&& ELEMENT_NAME.equals(docType.getElementName()) && PUBLIC_ID.equals(docType.getPublicID()) &&
    SYSTEM_ID.equals(docType.getSystemID());`.
    <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/RSS091NetscapeParser.java>

## Gaps

- **JDK 23.0.2 only.** The decoder behaviours in F5 (which decoders swallow the following unit) were measured on one
  JDK. The decoder-divergence rule is written in terms of the hazard, so it errs safe, but the swallow-prone list
  (EUC-JP, GB18030) and the exhaustive "no loss" results for UTF-8, Shift_JIS, windows-31j, Big5, GBK, GB2312 and
  EUC-KR hold for that JDK; any other JDK the module supports needs the same probe. The 3-byte exhaustive prefix test
  does not cover every four-byte sequence of those charsets (a 1.5-million-string random test covers longer ones).
- **Byte-stream Xerces path.** The guarantee assumes the parser receives a `Reader` built by `XmlReader` (F3). If any
  caller gives the JDK parser a byte stream, Xerces' own detection applies and is not covered.
- **Constructor shapes and defaults.** Six `XmlReader` shapes were tested, with `UTF-8` as the default encoding where
  one is passed. A different explicit default encoding, or a different Rome version, voids the differential result; the
  `jvmTest` differential is the guard.
- **The 64 KiB bound** is a reasoned figure (observed need 149 bytes; Rome's window 4096 bytes), not taken from a
  source; the sample was 70 fetched documents.
- **Allowlist breadth.** Names the JDK knows but the list omits (for example `ks_c_5601-1987`, `x-windows-949`,
  `Big5-HKSCS`, `TIS-620`, `ISO-2022-KR`) are refused, a false refusal rather than a hole. They were not measured for
  swallowing and can be added after the same probe.
- **HTML classification is narrow.** Only `<!DOCTYPE html>` (any case, any ids) and an unprefixed `html` root (any
  case) are `HtmlDocument`. A prefixed root (`<h:html>`), an HTML fragment with no `html` root (`<head>...`, `<body>...`),
  and text before the markup fall through to `NoDoctype` and the parser's rejection.
- **Wallarm's original XXE-WAF write-up** could not be retrieved (the site redirects to an empty page); the bypass class
  rests on the cheat sheet and the CTF write-up, both single sources, plus the measurements here.
- **JS target:** the scan compiled to a Kotlin/JS klib; it was not executed on a JS runtime.
- **Leading comments with `--`** are reported `Malformed` although Rome parses them (M01, M09); no sampled feed has one,
  and how often real feeds do is unknown.
