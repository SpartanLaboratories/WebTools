# Entry identity, link and text rules for the feed model

Researched 2026-11-06. Code was read at pinned tags or commits; the figures called "measured" come from probes run on the build JDK (23.0.2) against the Kotlin 2.2.0 compiler and standard library, and from a sample of 57 feeds fetched on that one day.

## Question

What rules should the `webtools-feeds` model apply to the raw field values a parser returns, so that `FeedEntry.id`, every `link` and every text field are well defined, bounded and testable? Concretely:

- **Identity:** the guid or Atom id and how it is trimmed; the length cap and its fallbacks; the link fallback; the digest fallback (inputs, normalisation, delimiting, algorithm, output form); what happens to an entry with nothing at all.
- **Links:** what counts as an absolute `http` or `https` link; how a relative reference resolves against the response's final URL; what is refused.
- **Text:** truncation, trimming, the category and enclosure caps, an enclosure `length`, the author string; and the default numbers of `FeedLimits`.

The answer must hold in pure Kotlin in `commonMain` (compiled for the JVM and for `js`), be testable at Level 4a against fixed vectors, do bounded work on hostile input (a 5 MB id, a 2 MiB text node), keep `id` never blank and identical across releases for the same entry, and turn an unsafe or unusable link into null.

## Bottom line

- **Id rule, three steps, frozen.** (1) the guid or Atom id trimmed of ASCII whitespace, if non-empty and at most `maxIdChars`; (2) else the entry's link text exactly as written (trimmed of C0 controls and space, no whitespace/control/backslash character left, scheme absent or `http`/`https`), if at most `maxIdChars`; (3) else a digest. `isPermaLink` plays no part. No Unicode normalisation, no case folding, no dependence on the base URL (RFC 4287 §4.2.6 forbids an id changing when a document is relocated).
- **Digest = SHA-256 over a length-prefixed message, first 16 bytes, prefixed.** Message: the domain string `webtools-feeds/entry-id/1`, then (each present field as `"\n" + tag + utf16Length + ":" + value`) the first available of title (`t`), summary-or-content (`b`), first enclosure URL (`e`), then the published date as whole epoch seconds (`d`, floor division). Fields are ASCII-trimmed, blank is absent, cut to 4,096 UTF-16 units, and unpaired surrogates become U+FFFD (the JVM and `js` encoders otherwise disagree, measured and read in source). Output `webtools-feeds:v1:` + 32 lower-case hex digits. An entry with no field at all is kept and gets the constant digest of the domain string (`b2025d24e4ca603fe646f1d2df2ea6e5`). All fourteen vectors in the table below were reproduced by an independent Python implementation of the same specification.
- **Link rule.** Trim C0 controls and space, check the length before any scan, refuse any remaining character at or below U+0020, U+007F to U+009F, or a backslash; accept only `http`/`https` (case-insensitive scheme, output lower-cased, the rest as written) with a valid authority (no user-info, bracketed IPv6, port at most 65535); a `#fragment` is kept; percent-encoding is not touched. A relative reference resolves by RFC 3986 §5.2 (strict) against a valid absolute base with its fragment dropped, else null; a blank reference is null, not the base. The implementation reproduces 39 of the 42 rows of RFC 3986 §5.4 exactly; the other three (`g:h`, `http:g`, the empty reference) are null by rule. Dot-segment removal is done in linear time.
- **Rome gives no help with resolution** (`Atom10Parser.resolveURIs` is a static flag, default `false`; the RSS parsers resolve nothing), so the adapter resolves. A link Rome derives from a permalink guid must be accepted only if it is already absolute, never resolved (a UUID guid would otherwise become `https://host/dir/<uuid>`).
- **Default numbers:** `maxIdChars` 2,048; `maxLinkChars` 8,000 (RFC 9110's interoperability floor, also enclosure URLs); `maxTextChars` 262,144 (the largest text node in the sample is 174,346; the parent sketch's 65,536 would truncate 2 of 57 feeds); `maxCategoriesPerEntry` 32 (sample maximum 25); `maxEnclosuresPerEntry` 8 (sample maximum 1 distinct URL, 13 repeats). A default can only be tightened by a builder, so the generous side is the safe side; the id cap is the exception because it is part of the frozen identity.

## Findings

### F1. Readers that do not require a guid derive an id in four incompatible ways (code reading)

All sources are pinned in "How comparable projects do it". The pattern, for an item with no guid:

| Reader | Fallback chain | Delimiter | Output |
|---|---|---|---|
| NetNewsWire `mac-7.2.1` | first available of permalink+date, link+date, title+date, date, permalink, link, title, body | none (date is epoch seconds, `%.0f`) | MD5 hex |
| Miniflux `ba40f99` | RSS: guid (or guid + `\|` + URL, or guid + `\|` + position on a repeat), else URL, else title+content; Atom: id, else link | `\|` only between guid and suffix | SHA-256 hex |
| SimplePie `9a64876` | atom:id, guid, dc:identifier, rdf:about, else permalink+title+content | none | MD5 hex |
| FreshRSS `1.30.4` | configurable criteria; fallback entry id, else sha1(permalink + date), else sha1(permalink + date + title), else sha1(permalink + date + title + content) | none | SHA-1 hex; a blank result counts as an invalid guid |
| Feedbin feedkit `fbac4ff` | feed URL + entry id (stripped) + (only when no id) url, `published.iso8601`, title | none | SHA-1 hex; a second `public_id_alt` for http/https moves; a normalised `guid` that drops protocol and host |
| Tiny Tiny RSS `c02520f` | id, else link, else lower-cased title with punctuation replaced by `-`; **entry skipped if all three are empty** | none | the string itself, stored cut to 245 characters |
| gofeed `6014e99`, feedparser `a22c552`, Feedjira `v4.0.1` | gofeed and feedparser copy the guid and never synthesise one; Feedjira's `id` is `entry_id` or `url` | n/a | n/a |
| feed-rs `17ddc9e` | SipHash-128 of the first link then the title; else of the document URI and title; else a random v4 UUID | none (two `write` calls) | `format!("{:x}{:x}", h1, h2)` (no zero padding) |
| Rome 2.1.0 | RSS: `uri` = untrimmed guid text, else the link; Atom: id, else link | n/a | no digest |

None of the hashing readers delimits its fields, none normalises Unicode, and each picks different inputs, so ids are not portable between readers. Only the guid (and the Atom id) is a cross-reader identity.

### F2. The specifications treat the guid and the id as opaque, case-sensitive strings (documented)

- RSS 2.0.11: guid "There are no rules for the syntax of a guid. Aggregators must view them as a string."; `isPermaLink` "is optional, its default value is true. If its value is false, the guid may not be assumed to be a url, or a url to anything in particular."; and "All elements of an item are optional, however at least one of title or description must be present." An item may therefore carry only a description (or nothing useful at all).
- RFC 4287 §4.2.6.1: "Processors MUST compare atom:id elements on a character-by-character basis (in a case-sensitive fashion)." So case folding, Unicode normalisation and URL canonicalisation of an id are all wrong.
- RFC 4287 §4.2.6: "When an Atom Document is relocated, migrated, syndicated, republished, exported, or imported, the content of its atom:id element MUST NOT change." The id rule therefore must not depend on where the document was fetched from, which rules out resolving a link against the base URL before using it as an id.
- RFC 4287 §8.4 warns that an attacker can publish an entry with another feed's id, so an id is scoped to its feed; the digest needs collision resistance mainly so that ids inside one feed do not collide by accident or by crafting.

### F3. What the readers regret or paid for (code reading; documented)

- NetNewsWire, `RSSItem.uniqueID`: "Don't change this without a super-good reason! It would mean article IDs would no longer match, and people would have many duplicates." That is the reason the digest here is specified to the byte and versioned in its output.
- feed-rs generated ids were unstable and unpadded. `generate_id` ends with "Generate a UUID as last resort" (a random id per parse), and the hash is printed as `{:x}{:x}` of two 64-bit halves without padding, so different halves can print the same text. Pull request #189 was opened because "I need these to be stable", and #219 ("Pluggable ID generation") added `id_generator` and `id_generator_v0_2` so the older scheme can be reproduced.
- Miniflux documents the duplicate-guid hazard in code: "some feeds ship the same GUID for every entry", and disambiguates by URL or position. FreshRSS measures it and degrades its criteria when too many guids are blank or repeated.
- Feedbin keeps a second id for the other protocol and strips protocol and host in its normalised guid, because a site moving from `http` to `https` re-announces every entry otherwise.

### F4. What the sample says about the inputs (measured; a sample of 57 feeds fetched on one day)

12 Atom, 44 RSS, 1 RDF; 7,896 items. Figures:

| Quantity | Measured |
|---|---|
| Longest guid or Atom id | 528 characters |
| Longest item link | 570 |
| Longest enclosure URL | 382 |
| Longest title | 197 |
| Longest description or summary | 81,267 (Planet Python) |
| Longest content | 174,346 (Smashing Magazine) |
| Feeds with a text node over 65,536 / 131,072 / 262,144 characters | 2 / 1 / 0 |
| Most categories on one item | 25 |
| Blank / exact-duplicate category values | 15 / 31 |
| Most distinct enclosure URLs on one item | 1 (one WordPress.com feed repeats the same URL up to 13 times; 12 duplicate URLs among 6,805 enclosures) |
| Enclosure `length`: positive number / `0` / missing / non-numeric | 6,736 / 39 / 30 / 0 |
| Items with no guid or id | 45, in 2 feeds (Hacker News RSS, Slashdot RDF); every one has a link |
| Items with neither guid nor link | 0 |
| Relative or non-`http` item links; links with a space | 0; 0 |
| Guids or ids with surrounding whitespace; repeated within a feed | 0; 0 |
| `xml:base` on a feed, entry, channel, item or link element | 0 (2 feeds have it on content elements only) |
| Feeds over 2 MiB / with more than 1,000 entries | 6 / 2 (podcasts) |

So the link step of the id rule is exercised in real feeds (about 0.6% of items) and the digest step was not reached in this sample. The sample supports "values are short and well formed", not rates.

### F5. What Rome 2.1.0 hands over (code reading; `rome` tag 2.1.0, commit `7e5cf65`)

- `ConverterForRSS094` sets the entry `uri` to the guid value (`guid.getValue()`, untrimmed) or, with no guid, to the item link, and "if (itemLink == null && guid.isPermaLink()) { syndEntry.setLink(guidValue); }". `isPermaLink` defaults to true, so a guid with no `isPermaLink` attribute and no `<link>` becomes the entry's link whatever its text is. A UUID guid becomes a "link".
- `Atom10Parser.resolveURIs` is `private static boolean resolveURIs = false;` and `resolveURI` begins `if (!resolveURIs) { return url; }`. The RSS parsers do no resolution. The flag is global static state, so the adapter must not set it. Atom `Feed` and `Entry` beans carry `getXmlBase()`, filled from the `xml:base` attribute of the `<feed>` and `<entry>` elements (`eFeed.getAttributeValue("base", Namespace.XML_NAMESPACE)`, `eEntry.getAttributeValue("base", Namespace.XML_NAMESPACE)`); nothing else is stored.
- Enclosure length: the RSS parser stores `NumberParser.parseLong(length, 0L)` and the Atom link parser stores the parsed value only when it parses; both beans keep a primitive `long` that is `0` when absent. `parseLong` is `Long.valueOf(str.trim())`, so a negative value survives and an unparseable or overflowing one becomes `0`. A model that wants "unknown" must treat `0` and negatives as null.
- Rome's date parser has known gaps (issue 695, open: "Can't parse date with time zone offset without a colon (e.g. -0400)"). A date the parser cannot read is null, so a digest that includes the date differs from the one a fixed parser would produce later (see Pitfalls).

### F6. RFC 3986 resolution: reference implementation and measurements (measured; documented)

- The reference in the Recommendation reproduces 39 of the 42 `"reference" = "target"` rows of RFC 3986 §5.4 (the rows were extracted by script from the RFC text and fed to `resolveLink` against the RFC's base `http://a/b/c/d;p?q`). The three that differ are deliberate: `g:h` (a non-`http` scheme), `http:g` (an `http` scheme with no authority) and the empty reference (the RFC resolves it to the base; a blank link field is better read as no link).
- RFC 3986 §5.1.3: "if the retrieval was the result of a redirected request, the last URI used ... is the base URI." That is the response's final URL, which is what `FeedInput.baseUrl` carries. §5.1: a base "must be converted to absolute form and stripped of any fragment component prior to its use as a base URI". §4.2: "A path segment that contains a colon character (e.g., "this:that") cannot be used as the first segment of a relative-path reference, as it would be mistaken for a scheme name." So a colon in the first segment means a scheme, and `localhost:8080/x` is a refused scheme, not a relative path.
- RFC 3986 §7.6 shows why user-info is refused: `ftp://cnn.example.com&story=breaking_news@10.0.0.1/top_story.htm` "might lead a human user to assume that the host is 'cnn.example.com', whereas it is actually '10.0.0.1'." Refusing `@` in the authority removes the whole class.
- WHATWG URL's basic parser begins "Remove any leading and trailing C0 control or space from input." The same trim is used here, but the WHATWG parser then also removes tab and newline inside the input and repairs backslashes; this rule refuses them instead (a link containing them is null), which is the stricter and additive-safe choice.
- RFC 9110 §4.1: "It is RECOMMENDED that all senders and recipients support, at a minimum, URIs with lengths of 8000 octets in protocol elements." A link a server must accept should not be nulled by a library default.
- Work is linear in the (capped) length: the length check runs before any scan, and dot-segment removal uses one output buffer and an index into the input. A quadratic version (string concatenation per segment) would cost seconds for a few hundred worst-case links in a 2 MiB document (reasoned; the shipped reference is the linear one, and 300 worst-case resolves took 85 ms).

### F7. The digest must hash bytes that are the same on every platform (measured; code reading)

- Pure-Kotlin SHA-256 (FIPS 180-4) matches RFC 6234's vectors for `abc`, the 448-bit message and one million `a`, FIPS/NIST's empty-message and 896-bit messages, and 313 random inputs (lengths 0 to 300 and 1,000 to 2 MiB) against `MessageDigest`. Hashing 2 MiB took 10 to 30 ms cold on the JVM, so a bounded 4,096-unit field is negligible.
- `String.encodeToByteArray()` is documented as "Any malformed char sequence is replaced by the replacement byte sequence", but the two implementations differ. Kotlin 2.2.0's JVM actual is `this.toByteArray(Charsets.UTF_8)`, which replaces an unpaired surrogate with `?`; measured here: `"\uD800".encodeToByteArray()` is `3f` and `"\uDC00x"` is `3f 78`. The `js` actual (`utf8Encoding.kt`) replaces with `EF BF BD`. A frozen digest must therefore not rely on either; the rule replaces unpaired surrogates by U+FFFD before hashing. XML parsers never return one, but the port is open to other parsers.
- Two surrogate-related cut points exist and both are fixed by the rule: the 4,096-unit cut drops a dangling high surrogate (a pair that straddles the cut), and the replacement runs after the cut.
- Why not a 64-bit hash such as FNV-1a: it is not collision resistant, so a feed's author can craft two entries with the same id inside a feed (reasoned; RFC 4287 §8.4 describes the attack). Why not a random fallback: it changes on every parse (feed-rs, F3).

### F8. Every API the rules use is in the Kotlin 2.2.0 common standard library (documented; measured)

`String.encodeToByteArray()`, `String.startsWith(prefix, startIndex, ignoreCase)` and `Long.floorDiv(Long)` are declared in common code (claim 28); `String.lowercase()` and the `StringBuilder` calls used (`appendRange`, `deleteRange`, `lastIndexOf`) are established by the `js` klib compile alone. The reference source was compiled for the JVM (tests run) and, separately, to a `js` klib against `kotlin-stdlib-js` 2.2.0 with no error; the klib was not executed. `webtools-http`'s own `commonMain` already uses `floorDiv` and `encodeToByteArray` and is compiled for `js` by its check module.

### F9. Text, category and enclosure rules (reasoned from F4 and F5; common practice)

- Trim ASCII whitespace (TAB, LF, FF, CR, SPACE) and treat blank as absent. No-break space and other Unicode spaces are not trimmed. Truncation is by UTF-16 units and drops a trailing high surrogate; grapheme clusters are not considered (a cut may separate a base letter from a following combining mark).
- Categories: trim, drop blank, drop exact duplicates (first kept), then cap, so blanks and duplicates do not consume the cap. 15 blank and 31 duplicate values occur in the sample.
- Enclosures: apply the link rule to the URL (null drops the enclosure), drop a repeated URL (first kept), then cap. `length` is the parsed value when positive, else null (`0` is Rome's "unknown", and 39 `0` and 30 missing values occur against 6,736 positive ones). `mimeType` is trimmed, blank is null.
- Author: as written, trimmed. RSS `author` is an e-mail address, often with the name in parentheses, so it is not parsed.

### F10. An entry with nothing at all (reasoned; practice split)

Keep it, with the constant digest. Tiny Tiny RSS skips such items, FreshRSS counts a blank guid as invalid and degrades its criteria, feedparser and gofeed pass them through. A model that silently drops entries changes `entries.size`, loses author, category or enclosure-only items, and hides the fact from a caller who then cannot tell a short feed from a filtered one; a caller who wants them gone can filter on the fields. Several such entries in one feed share one id; so do several entries with the same title and no date. Both are documented consequences, not repaired.

## How comparable projects do it

Pinned references; symbols, not line numbers.

| Project | Pinned at | Symbol | Rule |
|---|---|---|---|
| NetNewsWire | [`mac-7.2.1`](https://github.com/Ranchero-Software/NetNewsWire/blob/mac-7.2.1/Modules/RSParser/Sources/RSParser/Feeds/XML/RSSItem.swift) | `RSSItem.uniqueID` | guid, else MD5 of the first present of permalink+date, link+date, title+date, date, permalink, link, title, body |
| Miniflux | [`ba40f99`](https://github.com/miniflux/v2/blob/ba40f99f66ccd8362628d7140abb0ab8a82e9f5f/internal/reader/rss/adapter.go) | RSS adapter, "Generate the entry hash"; [Atom adapter](https://github.com/miniflux/v2/blob/ba40f99f66ccd8362628d7140abb0ab8a82e9f5f/internal/reader/atom/atom_10_adapter.go) | `crypto.SHA256` of guid, guid + `\|` + URL, guid + `\|` + n, URL, or title + content; Atom: id else link |
| SimplePie | [`9a64876`](https://github.com/simplepie/simplepie/blob/9a648760e4c2472c30281d02f9c7a6ffa840bfe7/src/Item.php) | `Item::get_id` | atom:id, guid, dc:identifier, rdf:about, else `md5(permalink . title . content)` |
| FreshRSS | [`1.30.4`](https://github.com/FreshRSS/FreshRSS/blob/1.30.4/app/Models/Feed.php) | `Feed::decideEntryGuid` | sha1 fallbacks over permalink, `get_date('U')`, title, content; guid column `VARCHAR(767)` in [`install.sql.mysql.php`](https://github.com/FreshRSS/FreshRSS/blob/1.30.4/app/SQL/install.sql.mysql.php) |
| Feedbin feedkit | [`fbac4ff`](https://github.com/feedbin/feedkit/blob/fbac4ffe3a68114716279759e638025ed18c425b/lib/feedkit/parser/entry.rb) | `Entry#build_id`, `#public_id_alt`, `#remove_protocol_and_host` | SHA-1 of feed URL + id (+ url, `published.iso8601`, title when no id); alternate id for http/https |
| Tiny Tiny RSS | [`c02520f`](https://github.com/tt-rss/tt-rss/blob/c02520ff1c26a0aab0c81ada1f42d377f02ac29c/classes/RSSUtils.php) | `RSSUtils::make_guid_from_title` and the `$entry_guid` chain | id, link, lower-cased title; skip if empty |
| feedparser | [`a22c552`](https://github.com/kurtmckee/feedparser/blob/a22c5521cbb109871f1a2318948581901bd47e26/feedparser/namespaces/_base.py) | `_start_guid` / `_end_guid` | `guidislink` makes the guid the link when `isPermaLink` is not `false` and there is no link; no synthesised id |
| gofeed | [`6014e99`](https://github.com/mmcdole/gofeed/blob/6014e99c09f70ccc83bccf7ea4ee772fb1bc7061/translator.go) | RSS translator | `item.GUID = rssItem.GUID.Value` only |
| Feedjira | [`v4.0.1`](https://github.com/feedjira/feedjira/blob/v4.0.1/lib/feedjira/feed_entry_utilities.rb) | `FeedEntryUtilities#id` | `@entry_id ||= @url` |
| feed-rs | [`17ddc9e`](https://github.com/feed-rs/feed-rs/blob/17ddc9e257f7a512f8a4573e6ff65bcad041512b/feed-rs/src/parser/mod.rs) | `generate_id`, `generate_id_from_link_and_title`, `ParseFeedBuilder::id_generator` | keyed SipHash-128 of link + title, else URI + title, else a random UUID; the generator became pluggable |
| Rome | [`2.1.0`](https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/feed/synd/impl/ConverterForRSS094.java) | `ConverterForRSS094.createSyndEntry`, `ConverterForAtom10` | RSS: `uri` = guid text else link; link = guid text for a permalink guid with no link; Atom: `uri` = id, else the entry link |

Where this design differs from all of them: delimited (length-prefixed) fields; a versioned, specified output; a stated rule for platform-independent bytes; a first-available field rather than a concatenation of everything, so an edited body never changes the id.

## Pitfalls

- **Undelimited concatenation** (NetNewsWire, SimplePie, FreshRSS, Feedbin, feed-rs): title `ab` with no date and title `a` followed by a field `b` hash alike. The reference prefixes each field with its tag and UTF-16 length.
- **Unpadded hex of two halves** (feed-rs `{:x}{:x}`) can print the same text for different halves. The reference prints exactly 32 digits of one SHA-256 prefix.
- **Using the body or `updated` in the id.** An edited body or a bumped modified date re-announces the entry. Only the first of title, body, enclosure URL is used, and only the published date.
- **A date-derived id depends on the date parser.** An entry with an unparseable date hashes without it; when the parser later learns that format (Rome issue 695) its guid-less entries get new ids. A parser swap can also change how title text is decoded. Both change digest ids; the rule cannot protect against them. Guid-less entries are about 0.6% of items in the sample, all with links, so most are protected by step 2.
- **Link-derived ids move with the site.** `http` to `https`, or a host change, changes the id of every guid-less entry (Feedbin keeps `public_id_alt` for this). A caller that needs stability across a move should key on the guid and treat link-derived ids (a non-guid id is recognisable: it equals the link) as weaker.
- **`maxIdChars` is part of identity.** Lowering it turns a long guid into a link or digest id; raising it later does the reverse. The default is frozen with the rule; a caller that tightens it owns the change.
- **Duplicate ids in one feed** (guid repeated, or entries with equal title and no date) are not repaired; Miniflux disambiguates and FreshRSS degrades its criteria. Callers should key on feed plus id and tolerate repeats.
- **A permalink guid is not a link.** Rome copies the guid text into the link; resolving a UUID guid against the feed URL produces a plausible but wrong URL (measured: `7f3a9c2e-1b4d-4e8a-9c3d-0a1b2c3d4e5f` against `https://example.org/feed/index.xml` gives `https://example.org/feed/7f3a9c2e-1b4d-4e8a-9c3d-0a1b2c3d4e5f`).
- **Platform-dependent bytes** (F7) in a frozen digest.
- **Quadratic dot-segment removal** by repeated string concatenation.
- **Keeping an invalid link for display.** `javascript:` and `data:` links are the classic cross-site-scripting vector for a reader that renders a link as an anchor; the rule returns null for them and for every non-`http` scheme.
- **Not refused, by design:** invisible and bidirectional formatting characters (U+200B to U+200F, U+202A to U+202E, U+2066 to U+2069) and private/loopback hosts. A consumer that renders a link as text or fetches it must apply its own policy; the stack's `Untrusted` fetcher already refuses loopback and private targets.
- **A non-ASCII host is kept as written**, but `Url.parse` in `webtools-http` refuses it, so a caller that passes `entry.link` straight to `Url.parse` gets a failure for an internationalised host and must handle it.

## Recommendation for this repository

Put all of it in one `internal` file of `commonMain` as top-level functions (or an `internal object FeedRules`): pure, no platform API, no state, `Result` not needed (every function is total; failure is a null or a different branch). The Rome adapter calls it with raw values; a consumer-written `FeedParser` can call the same public model constructors. Nothing here throws on hostile input.

### The id rule (frozen; document it as part of the compatibility promise)

Inputs, taken from the parser before `FeedLimits.maxTextChars` applies: `guid` (the RSS guid text or the Atom id, as the parser returned it), `link` (the raw text of the link the parser chose for the entry, as written), `title`, `summary`, `content`, the URL of the first enclosure as written, and `publishedAtEpochMillis`.

1. `t = trimAscii(guid)` (removes TAB, LF, FF, CR, SPACE at both ends only). If `t` is non-empty and `t.length <= maxIdChars`, the id is `t`.
2. Else `l = trimC0(link)` (removes every leading and trailing char up to U+0020). If `l` is non-empty, `l.length <= maxIdChars`, `l` has no char at or below U+0020, none from U+007F to U+009F, no backslash, and its scheme (a colon before any `/`, `?` or `#`) is absent or `http`/`https` case-insensitively, the id is `l` exactly as written. It is not resolved, so it does not depend on `baseUrl`; a relative link such as `/p/1` is a valid id.
3. Else the digest below.

`isPermaLink` is ignored. The id is never trimmed beyond ends, never case-folded, never normalised.

### The digest (exact specification)

1. For each of the candidate strings, in this order: take the value, `trimAscii`, treat empty as absent, cut to 4,096 UTF-16 units (if the unit at the cut is a high surrogate, cut one unit earlier; the cut string is not trimmed again), then replace each unpaired surrogate code unit with U+FFFD.
2. The text field is the first present of: title (tag `t`); summary, else content (tag `b`); the first enclosure URL (tag `e`).
3. The date field is present when `publishedAtEpochMillis` is non-null: `seconds = floorDiv(ms, 1000)` as a decimal string, no sign for non-negative values, `-` for negative (tag `d`). `updatedAt` is never used.
4. Message = `webtools-feeds/entry-id/1`; then, if the text field is present, `"\n"` + tag + decimal count of its UTF-16 units + `":"` + value; then, if the date field is present, `"\n"` + `d` + decimal count of the seconds string's characters + `":"` + the seconds string.
5. Bytes = UTF-8 of the message. Digest = SHA-256 (FIPS 180-4) of the bytes.
6. Id = `webtools-feeds:v1:` + the first 16 bytes of the digest as 32 lower-case hex digits. The prefix is a syntactically valid IRI scheme, so the id is also valid as an `atom:id` if a consumer re-emits it, and it cannot be mistaken for an id a feed carries itself in practice. Bumping the algorithm means a new prefix (`v2`), never a silent change.
7. No field present: the message is the domain string alone, and the id is the constant `webtools-feeds:v1:b2025d24e4ca603fe646f1d2df2ea6e5`. The entry is kept.

The digest does not depend on any `FeedLimits` value. Collision resistance: 128 bits of a cryptographic hash.

### The link rule and resolution algorithm

`resolveLink(raw, base, maxLinkChars, allowRelative = true)`:

1. `raw` null gives null. `s = trimC0(raw)`; empty gives null; `s.length > maxLinkChars` gives null (this check precedes every scan).
2. A char at or below U+0020 (so no space, tab or newline anywhere inside), U+007F to U+009F, or a backslash anywhere gives null.
3. Decompose by RFC 3986 Appendix B without a regular expression, treating a colon in the first segment as a scheme delimiter. A scheme that is not a valid scheme token gives null.
4. **Absolute input:** the scheme must be `http` or `https` and the reference must have an authority with: no `@`; a host of ASCII letters, digits, `-`, `.`, `_` or any char at or above U+00A0 (so `%`, `<` and spaces are refused), non-empty; or a bracketed IPv6 literal (hex digits, `:` and `.`, at least one colon); an optional port of at most five digits and at most 65535 (an empty port after `:` is allowed). The output is the scheme lower-cased followed by the rest exactly as written; no host lower-casing, no dot-segment removal, no percent-encoding change. The fragment is kept.
5. **Relative input:** if `allowRelative` is false, null. The base is trimmed, must be at most `maxLinkChars`, free of forbidden chars, and decompose to `http`/`https` with a valid authority; otherwise null. Then RFC 3986 §5.2.2 (strict): a reference with an authority (`//host/...`) takes the base scheme; an empty path takes the base path and, if the reference has no query, the base query; an absolute path is dot-normalised; otherwise merge with the base path (an authority with an empty path merges to `/` + the reference path) and dot-normalise (§5.2.4); query and fragment come from the reference; the base fragment is never used.
6. The result must be at most `maxLinkChars`.

`allowRelative = false` is for the link Rome derives from a permalink guid (the entry's link text equals its `uri` text and the item has no `<link>`): such a value is a link only if it is already absolute. The same function serves the feed's link, each entry link and each enclosure URL. For Atom, the base of an entry's links is `resolve(entry.xmlBase, resolve(feed.xmlBase, baseUrl))` with the same function (so a relative `xml:base` resolves against its parent's); a base that fails to resolve makes the entry's relative links null rather than falling back to the document URL, which could put the link on the wrong host. This is the only `xml:base` Rome exposes (F5); the sample has none on those elements, so it costs nothing to support and nothing is lost by ignoring other placements.

### The text rules

- Titles, summaries, contents, descriptions, authors, category terms, MIME types: `trimAscii`, blank is null, truncate to `maxTextChars` by UTF-16 units dropping a trailing high surrogate, null if that leaves nothing.
- Categories: apply the text rule, drop null, drop exact duplicates keeping the first, then keep at most `maxCategoriesPerEntry`.
- Enclosures: `url = resolveLink(url, base, maxLinkChars)`; null drops the enclosure; drop a repeated URL (first kept); keep at most `maxEnclosuresPerEntry`; `length` = parsed value if `> 0` else null; `mimeType` by the text rule.
- Dates, entry count and nesting depth are outside these rules.

### Default numbers (policy; the evidence is in F4 and F6)

| Limit | Default | Why |
|---|---|---|
| `maxEntries` | 1,000 (given) | 2 of 57 sample feeds exceed it, both podcasts; it fails loudly, never truncates |
| `maxTextChars` | 262,144 | max observed 174,346; 65,536 would cut 2 of 57 feeds and 131,072 would cut 1; a default can only be tightened; total text is bounded by the document cap anyway |
| `maxIdChars` | 2,048 | 3.9 times the longest observed id (528); an all-ASCII id of this size still fits the 3,072-byte InnoDB `DYNAMIC` key limit and the roughly 2,704-byte PostgreSQL btree limit; FreshRSS's own column is 767 bytes, so a caller with a narrower column tightens it |
| `maxLinkChars` | 8,000 | RFC 9110's interoperability floor; 14 times the longest observed link (570); an id never exceeds 2,048 even when the link is longer (a longer valid link yields a digest id) |
| `maxCategoriesPerEntry` | 32 | max observed 25 |
| `maxEnclosuresPerEntry` | 8 | max observed distinct 1; repeats are removed first |
| digest field cut | 4,096 units, a constant, not a `FeedLimits` value | part of the frozen digest |

A 2,048-character link cap (the parent sketch's number) is also defensible; it would null the links between 2,049 and 8,000 characters that servers must still accept. Nothing in the sample is above 570.

### Level 4a vectors to pin

Digest ids (Kotlin and an independent Python implementation of the specification agree on every one):

| Input | Id |
|---|---|
| title `Hello`, published `1700000000000` | `webtools-feeds:v1:ee7d21b287ec8fde2600e24ba184edb7` |
| title `Hello` only | `...:606a3e59bef6bab41ee0dc4fb55c6a91` |
| published `1700000000000` only | `...:af8884cd9f65043ea519a839ae35e5b1` |
| summary `<p>Only a description</p>` only | `...:90fd9202293c92ac61daf3e57745fb0c` |
| content `<p>Only a description</p>`, published `0` | `...:284baaab7f01943e412980249d696f09` |
| first enclosure URL `https://cdn.example/a.mp3` only | `...:86aa708c73f6e3fc1b4b4e17b2a4d3ef` |
| nothing | `...:b2025d24e4ca603fe646f1d2df2ea6e5` |
| title `ab` | `...:de945a20e41d0f43a4d8b7a2e763a1ba` |
| title `é` (precomposed) | `...:40ddd7a069f7780b2723331137b35874` |
| title `é` (decomposed; differs from the above: no normalisation) | `...:b67b156cf285b5bbeac4361c43602ccd` |
| title `😀`, published `-1` | `...:b6f7bc39c6113932f56840b0937abe2f` |
| title `\uD800x` (lone high surrogate) = title `�x` | `...:c51027fc4e7d3fe5a98627c7879e584b` |
| title `x\uDC00` (lone low surrogate) | `...:3af1540093033c6bb15856e8f7ac51b8` |
| title `t`x4,095 followed by `😀` = title `t`x4,095 | `...:ebcad2304f198474c84e3bfbe451c7ca` |

Also pin as equalities: surrounding whitespace is irrelevant; a title of 10,000 `t` equals one of 4,096 `t`; sub-second milliseconds are irrelevant while a whole second matters; `-1` and `-1000` ms agree (floor); a title beats a body; summary and content share a tag; a blank title, summary and content equals nothing; title `a` with a later field `b` differs from title `ab`.

Id rule (max 2,048): guid `"  tag:x,2026:1 \n"` with a link gives `tag:x,2026:1`; guid `AbC` stays `AbC`; blank guid with link ` http://l/x ` gives `http://l/x`; link `/p/1` with no guid gives `/p/1`; a 5,000,000-character guid with a link gives the link, with only a title gives a digest id; guid of exactly 2,048 is kept and 2,049 falls back; link `javascript:alert(1)` or `mailto:a@b.test` with a title gives a digest id; a valid 3,000-character link with no guid gives a digest id; `http://x.test/p` and `https://x.test/p` give different ids.

Link rule (base `https://example.org/blog/index.xml?x=1#frag`, cap 8,000): all 39 resolvable rows of RFC 3986 §5.4 against `http://a/b/c/d;p?q`, and `g:h`, `http:g`, the empty reference giving null; `https://example.org/a#top` kept with its fragment; `  HTTPS://Example.ORG/A  ` gives `https://Example.ORG/A`; `http://x.test` kept with no path; `%2f` and `%2F` untouched; `http://münchen.example/ü?ö#ä` kept; `http://[2001:db8::1]:8080/x` kept; port `65535` kept, `65536` and `80a` null; `http://user:pw@x.test/x` and `http://x.test@evil.test/` null; a backslash form, an inner space, an inner tab, `ht\ntp://x.test/`, U+0085 null; a trailing NUL trims away (`http://x.test/\u0000` gives `http://x.test/`) while an inner one is null; `javascript:`, `JaVaScRiPt:`, ` javascript:` with leading space, `java\nscript:`, `data:`, `file:`, `mailto:`, `ftp:`, `irc:`, `vbscript:`, `localhost:8080/x`, `:foo`, `http://`, `https:///x`, `http://:80/x`, `http://x y/`, `http://a%41.test/`, `http://[::1/x`, `http://exa<mple.test/` all null; `//cdn.example.net/x.js` gives `https://cdn.example.net/x.js` with the HTTPS base and null with no base; `/post/1` gives `https://example.org/post/1`; `post/1?x#y` gives `https://example.org/blog/post/1?x#y`; `#only` gives `https://example.org/blog/index.xml?x=1#only`; `../up` against `https://example.org/blog/` gives `https://example.org/up`; `x` against `https://example.org` gives `https://example.org/x`; a base of `ftp://example.org/`, `https://u@example.org/` or `https://example.org/a b/` gives null; `7f3a9c2e-1b4d-4e8a-9c3d-0a1b2c3d4e5f` with `allowRelative = false` gives null; the xml:base chain `/blog/` then `2026/` then `post-1.html#c` against `https://example.org/feeds/a.xml` gives `https://example.org/blog/2026/post-1.html#c`; the cap is exact (`http://x.test/` + `a` repeated `cap - 14` is kept, one more is null) and applies after resolution; a 5,000,000-character link is null.

Text: `truncate("ab😀cd", 2)` is `ab`, at 3 is `ab`, at 4 is `ab😀`, at 6 unchanged; `cleanText("😀x", 1)` is null; `trimAscii(" \t\r\n\u000Cx y\u000C ")` is `x y`; a no-break space is not trimmed; enclosure `length` 0, -5 and unparseable give null, `Long.MAX_VALUE` and 12216320 are kept.

### The verified Kotlin reference

Compiled with Kotlin 2.2.0 for the JVM (all vectors above and the RFC 3986 §5.4 table pass) and to a `js` klib against `kotlin-stdlib-js` 2.2.0. Names are illustrative; behaviour is the specification.

```kotlin
// ---- text ----
internal fun isAsciiSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000C' || c == '\r'

internal fun trimAscii(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && isAsciiSpace(s[start])) start++
    while (end > start && isAsciiSpace(s[end - 1])) end--
    return if (start == 0 && end == s.length) s else s.substring(start, end)
}

/** Cuts [s] to at most [max] UTF-16 code units without leaving a high surrogate at the cut. */
internal fun truncate(s: String, max: Int): String {
    if (s.length <= max) return s
    var end = max
    if (end > 0 && s[end - 1].code in 0xD800..0xDBFF) end--
    return s.substring(0, end)
}

internal fun cleanText(raw: String?, maxChars: Int): String? {
    if (raw == null) return null
    val t = trimAscii(raw)
    if (t.isEmpty()) return null
    return truncate(t, maxChars).ifEmpty { null }
}

// ---- SHA-256 (FIPS 180-4) ----
private val K = intArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
    0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
    0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
    0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
)

private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

internal fun sha256(message: ByteArray): ByteArray {
    val h = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )
    val bitLength = message.size.toLong() * 8
    val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    val w = IntArray(64)
    for (block in 0 until padded.size step 64) {
        for (t in 0 until 16) {
            val o = block + t * 4
            w[t] = ((padded[o].toInt() and 0xff) shl 24) or ((padded[o + 1].toInt() and 0xff) shl 16) or
                ((padded[o + 2].toInt() and 0xff) shl 8) or (padded[o + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = rotr(w[t - 15], 7) xor rotr(w[t - 15], 18) xor (w[t - 15] ushr 3)
            val s1 = rotr(w[t - 2], 17) xor rotr(w[t - 2], 19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 64) {
            val t1 = hh + (rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)) + ((e and f) xor (e.inv() and g)) + K[t] + w[t]
            val t2 = (rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)) + ((a and b) xor (a and c) xor (b and c))
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }
    val out = ByteArray(32)
    for (i in 0 until 8) for (j in 0 until 4) out[i * 4 + j] = (h[i] ushr (24 - 8 * j)).toByte()
    return out
}

private const val HEX = "0123456789abcdef"
internal fun ByteArray.toHex(count: Int = size): String = buildString(count * 2) {
    for (i in 0 until count) {
        val v = this@toHex[i].toInt() and 0xff
        append(HEX[v ushr 4]).append(HEX[v and 15])
    }
}

// ---- identity ----
private const val DIGEST_FIELD_CHARS = 4_096
private const val DIGEST_DOMAIN = "webtools-feeds/entry-id/1"
internal const val DIGEST_PREFIX = "webtools-feeds:v1:"

/** Replaces every unpaired surrogate by U+FFFD so that UTF-8 encoding is the same on every platform. */
internal fun replaceLoneSurrogates(s: String): String {
    var clean = true
    for (i in s.indices) {
        val n = s[i].code
        if (n in 0xD800..0xDBFF) {
            if (i + 1 >= s.length || s[i + 1].code !in 0xDC00..0xDFFF) { clean = false; break }
        } else if (n in 0xDC00..0xDFFF) {
            if (i == 0 || s[i - 1].code !in 0xD800..0xDBFF) { clean = false; break }
        }
    }
    if (clean) return s
    return buildString(s.length) {
        for (i in s.indices) {
            val n = s[i].code
            val ok = when {
                n in 0xD800..0xDBFF -> i + 1 < s.length && s[i + 1].code in 0xDC00..0xDFFF
                n in 0xDC00..0xDFFF -> i > 0 && s[i - 1].code in 0xD800..0xDBFF
                else -> true
            }
            append(if (ok) s[i] else '�')
        }
    }
}

private fun digestText(v: String?): String? {
    if (v == null) return null
    val t = trimAscii(v)
    if (t.isEmpty()) return null
    return truncate(t, DIGEST_FIELD_CHARS).ifEmpty { null }?.let { replaceLoneSurrogates(it) }
}

internal fun digestId(
    title: String?, summary: String?, content: String?, enclosureUrl: String?, publishedMillis: Long?,
): String {
    val text: Pair<Char, String>? = digestText(title)?.let { 't' to it }
        ?: (digestText(summary) ?: digestText(content))?.let { 'b' to it }
        ?: digestText(enclosureUrl)?.let { 'e' to it }
    val input = buildString {
        append(DIGEST_DOMAIN)
        if (text != null) append('\n').append(text.first).append(text.second.length).append(':').append(text.second)
        if (publishedMillis != null) {
            val seconds = publishedMillis.floorDiv(1000L).toString()
            append('\n').append('d').append(seconds.length).append(':').append(seconds)
        }
    }
    return DIGEST_PREFIX + sha256(input.encodeToByteArray()).toHex(16)
}

/** Id rule: guid, else the link as written, else the digest. */
internal fun entryId(
    guid: String?, rawLink: String?, title: String?, summary: String?, content: String?,
    enclosureUrl: String?, publishedMillis: Long?, maxIdChars: Int,
): String {
    val g = guid?.let { trimAscii(it) }
    if (!g.isNullOrEmpty() && g.length <= maxIdChars) return g
    val l = rawLink?.let { trimC0(it) }
    if (!l.isNullOrEmpty() && l.length <= maxIdChars && !hasForbiddenChars(l) && !hasRefusedScheme(l)) return l
    return digestId(title, summary, content, enclosureUrl, publishedMillis)
}

// ---- links ----
/** Trims every leading and trailing code unit up to U+0020 (WHATWG "C0 control or space"). */
internal fun trimC0(s: String): String {
    var start = 0
    var end = s.length
    while (start < end && s[start].code <= 0x20) start++
    while (end > start && s[end - 1].code <= 0x20) end--
    return if (start == 0 && end == s.length) s else s.substring(start, end)
}

internal fun hasForbiddenChars(s: String): Boolean {
    for (c in s) {
        val n = c.code
        if (n <= 0x20 || n in 0x7F..0x9F || c == '\\') return true
    }
    return false
}

private fun isSchemeStart(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
private fun isSchemeChar(c: Char) = isSchemeStart(c) || c in '0'..'9' || c == '+' || c == '-' || c == '.'

/** Index of the colon when [s] has one before any `/`, `?` or `#`, else -1. */
private fun schemeEnd(s: String): Int {
    for (i in s.indices) {
        val c = s[i]
        if (c == ':') return i
        if (c == '/' || c == '?' || c == '#') return -1
    }
    return -1
}

/** True when [s] has a colon in its first segment that is not the end of `http` or `https`. */
internal fun hasRefusedScheme(s: String): Boolean {
    val e = schemeEnd(s)
    if (e < 0) return false
    val scheme = s.substring(0, e)
    return !(scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true))
}

private fun validAuthority(a: String): Boolean {
    if (a.isEmpty() || a.indexOf('@') >= 0) return false
    val port: String?
    if (a[0] == '[') {
        val close = a.indexOf(']')
        if (close < 2) return false
        val lit = a.substring(1, close)
        if (lit.indexOf(':') < 0 ||
            !lit.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        ) return false
        val rest = a.substring(close + 1)
        if (rest.isNotEmpty() && rest[0] != ':') return false
        port = if (rest.isEmpty()) null else rest.substring(1)
    } else {
        val c = a.lastIndexOf(':')
        val host = if (c >= 0) a.substring(0, c) else a
        port = if (c >= 0) a.substring(c + 1) else null
        if (host.isEmpty() ||
            !host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '.' || it == '_' || it.code >= 0xA0 }
        ) return false
    }
    if (!port.isNullOrEmpty()) {
        if (port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() > 65_535) return false
    }
    return true
}

private class Ref(val scheme: String?, val authority: String?, val path: String, val query: String?, val fragment: String?)

/** RFC 3986 Appendix B decomposition without a regex; null when a colon in the first segment is not a scheme. */
private fun decompose(s: String): Ref? {
    var rest = s
    var fragment: String? = null
    val h = rest.indexOf('#')
    if (h >= 0) { fragment = rest.substring(h + 1); rest = rest.substring(0, h) }
    var query: String? = null
    val q = rest.indexOf('?')
    if (q >= 0) { query = rest.substring(q + 1); rest = rest.substring(0, q) }
    var scheme: String? = null
    val e = schemeEnd(rest)
    if (e >= 0) {
        if (e == 0 || !isSchemeStart(rest[0]) || !rest.substring(0, e).all { isSchemeChar(it) }) return null
        scheme = rest.substring(0, e).lowercase()
        rest = rest.substring(e + 1)
    }
    var authority: String? = null
    if (rest.startsWith("//")) {
        val end = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
        authority = rest.substring(2, end)
        rest = rest.substring(end)
    }
    return Ref(scheme, authority, rest, query, fragment)
}

/** RFC 3986 section 5.2.4, over an index into the input and a single output buffer (linear time). */
internal fun removeDotSegments(path: String): String {
    val out = StringBuilder(path.length)
    var i = 0
    val n = path.length
    // Drops the last segment of the output (and its leading slash), as `/..` does.
    fun dropLastSegment() {
        val cut = maxOf(out.lastIndexOf("/"), 0)
        out.deleteRange(cut, out.length)
    }
    while (i < n) {
        when {
            path.startsWith("../", i) -> i += 3
            path.startsWith("./", i) -> i += 2
            path.startsWith("/./", i) -> i += 2
            path.startsWith("/.", i) && i + 2 == n -> { out.append('/'); i = n }
            path.startsWith("/../", i) -> { i += 3; dropLastSegment() }
            path.startsWith("/..", i) && i + 3 == n -> { dropLastSegment(); out.append('/'); i = n }
            (path.startsWith(".", i) && i + 1 == n) || (path.startsWith("..", i) && i + 2 == n) -> i = n
            else -> {
                val next = path.indexOf('/', i + 1).let { if (it < 0) n else it }
                out.appendRange(path, i, next)
                i = next
            }
        }
    }
    return out.toString()
}

private fun merge(base: Ref, refPath: String): String {
    if (base.authority != null && base.path.isEmpty()) return "/$refPath"
    val i = base.path.lastIndexOf('/')
    return (if (i >= 0) base.path.substring(0, i + 1) else "") + refPath
}

private fun recompose(scheme: String, authority: String?, path: String, query: String?, fragment: String?): String =
    buildString {
        append(scheme).append(':')
        if (authority != null) append("//").append(authority)
        append(path)
        if (query != null) append('?').append(query)
        if (fragment != null) append('#').append(fragment)
    }

/**
 * The link rule: an absolute http or https link, else null. [raw] is resolved against [base] by RFC 3986 5.2
 * when it is a relative reference and [allowRelative] is true.
 */
internal fun resolveLink(raw: String?, base: String?, maxChars: Int, allowRelative: Boolean = true): String? {
    if (raw == null) return null
    val s = trimC0(raw)
    if (s.isEmpty() || s.length > maxChars || hasForbiddenChars(s)) return null
    val r = decompose(s) ?: return null
    val result: String
    if (r.scheme != null) {
        if ((r.scheme != "http" && r.scheme != "https") || r.authority == null || !validAuthority(r.authority)) return null
        result = r.scheme + s.substring(r.scheme.length)       // scheme lower-cased, the rest as written
    } else {
        if (!allowRelative) return null
        val b = base?.let { trimC0(it) }?.takeIf { it.length <= maxChars && !hasForbiddenChars(it) }?.let { decompose(it) }
        if (b == null || b.scheme == null || (b.scheme != "http" && b.scheme != "https") ||
            b.authority == null || !validAuthority(b.authority)
        ) return null
        if (r.authority != null) {
            if (!validAuthority(r.authority)) return null
            result = recompose(b.scheme, r.authority, removeDotSegments(r.path), r.query, r.fragment)
        } else if (r.path.isEmpty()) {
            result = recompose(b.scheme, b.authority, b.path, r.query ?: b.query, r.fragment)
        } else {
            val p = if (r.path.startsWith("/")) removeDotSegments(r.path) else removeDotSegments(merge(b, r.path))
            result = recompose(b.scheme, b.authority, p, r.query, r.fragment)
        }
    }
    return result.takeIf { it.length <= maxChars }
}

internal fun enclosureLength(parsed: Long): Long? = if (parsed > 0) parsed else null
```

Why the obvious option is rejected: resolving the link and using the resolved form as the id fallback. It looks tidier (one canonical absolute id), but it makes the id depend on the response's final URL, so a feed that moves host or protocol re-announces every guid-less entry, contrary to the principle RFC 4287 states for ids. Using the link as written costs one rule and keeps the id independent of the fetch. The other rejected options: a 64-bit FNV-1a (not collision resistant; cheap to craft within a feed), undelimited concatenation (the field-boundary collision), a random fallback (changes on every parse), and dropping entries with no usable field (changes counts and loses data silently).

## Load-bearing claims

1. **Atom ids compare case-sensitively, character by character.** "Instances of atom:id elements can be compared to determine whether an entry or feed is the same as one seen before. Processors MUST compare atom:id elements on a character-by-character basis (in a case-sensitive fashion)." RFC 4287 §4.2.6.1, December 2005. <https://www.rfc-editor.org/rfc/rfc4287.html#section-4.2.6.1>
2. **An Atom id must not change when a document moves.** "When an Atom Document is relocated, migrated, syndicated, republished, exported, or imported, the content of its atom:id element MUST NOT change." RFC 4287 §4.2.6. <https://www.rfc-editor.org/rfc/rfc4287.html#section-4.2.6>
3. **Spoofing by reusing another feed's id.** "Atom Processors should be aware of the potential for spoofing attacks where the attacker publishes an atom:entry with the atom:id value of an entry from another feed". RFC 4287 §8.4. <https://www.rfc-editor.org/rfc/rfc4287.html#section-8.4>
4. **`xml:base` sets the base for relative references.** "When xml:base is used in an Atom Document, it serves the function described in section 5.1.1 of [RFC3986], establishing the base URI (or IRI) for resolving any relative references found within the effective scope of the xml:base attribute." RFC 4287 §2. <https://www.rfc-editor.org/rfc/rfc4287.html#section-2>
5. **RSS 2.0 guid is an opaque string and `isPermaLink` defaults to true.** "There are no rules for the syntax of a guid. Aggregators must view them as a string." and "isPermaLink is optional, its default value is true. If its value is false, the guid may not be assumed to be a url, or a url to anything in particular." RSS 2.0.11 (RSS Advisory Board, 2009-03-30). <https://www.rssboard.org/rss-specification>
6. **An RSS item may have only a description.** "All elements of an item are optional, however at least one of title or description must be present." RSS 2.0.11, same page.
7. **Base URI after a redirect.** "Note that if the retrieval was the result of a redirected request, the last URI used (i.e., the URI that resulted in the actual retrieval of the representation) is the base URI." RFC 3986 §5.1.3. <https://www.rfc-editor.org/rfc/rfc3986.html#section-5.1.3>
8. **A base loses its fragment.** "If the base URI is obtained from a URI reference, then that reference must be converted to absolute form and stripped of any fragment component prior to its use as a base URI." RFC 3986 §5.1. <https://www.rfc-editor.org/rfc/rfc3986.html#section-5.1>
9. **A colon in the first segment is a scheme.** "A path segment that contains a colon character (e.g., "this:that") cannot be used as the first segment of a relative-path reference, as it would be mistaken for a scheme name." RFC 3986 §4.2. <https://www.rfc-editor.org/rfc/rfc3986.html#section-4.2>
10. **The reference-resolution examples used as vectors.** "Within a representation with a well defined base URI of http://a/b/c/d;p?q a relative reference is transformed to its target URI as follows." followed by the §5.4.1 and §5.4.2 tables (for example `"../../../g" = "http://a/g"`, `"g;x=1/../y" = "http://a/b/c/y"`, `"g?y/./x" = "http://a/b/c/g?y/./x"`). RFC 3986 §5.4. <https://www.rfc-editor.org/rfc/rfc3986.html#section-5.4>
11. **Strict resolution.** "A non-strict parser may ignore a scheme in the reference if it is identical to the base URI's scheme." (so a strict parser does not, and `http:g` is not `http://a/b/c/g`). RFC 3986 §5.2.2. <https://www.rfc-editor.org/rfc/rfc3986.html#section-5.2.2>
12. **User-info misleads.** "ftp://cnn.example.com&story=breaking_news@10.0.0.1/top_story.htm might lead a human user to assume that the host is 'cnn.example.com', whereas it is actually '10.0.0.1'." RFC 3986 §7.6. <https://www.rfc-editor.org/rfc/rfc3986.html#section-7.6>
13. **Strip leading and trailing C0 controls and space.** "Remove any leading and trailing C0 control or space from input." WHATWG URL Standard, basic URL parser (living standard, read 2026-11). <https://url.spec.whatwg.org/#concept-basic-url-parser>
14. **8000-octet URI floor.** "It is RECOMMENDED that all senders and recipients support, at a minimum, URIs with lengths of 8000 octets in protocol elements." RFC 9110 §4.1, June 2022. <https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1>
15. **SHA-256 test vectors.** `#define TEST1    "abc"` with `/* 1 */ { TEST1, length(TEST1), 1, 0, 0, "BA7816BF8F01CFEA4141" "40DE5DAE2223B00361A396177A9CB410FF61F20015AD" },` and the 448-bit message with `"248D6A61D20638B8" "E5C026930C3E6039A33CE45964FF2167F6ECEDD419DB06C1"`. RFC 6234 (May 2011), test driver. <https://www.rfc-editor.org/rfc/rfc6234.txt>
16. **NetNewsWire's frozen fallback.** "Returns `guid` if present. Otherwise MD5-hashes available properties to build the most likely stable ID. Don't change this without a super-good reason! It would mean article IDs would no longer match, and people would have many duplicates." NetNewsWire `mac-7.2.1`, `RSSItem.uniqueID`. <https://github.com/Ranchero-Software/NetNewsWire/blob/mac-7.2.1/Modules/RSParser/Sources/RSParser/Feeds/XML/RSSItem.swift>
17. **Miniflux's guid handling.** "some feeds ship the same GUID for every entry. Keep the first occurrence stable (so existing stored entries still match) and disambiguate later collisions using the entry URL or, as a last resort, the item position." and `entry.Hash = crypto.SHA256(item.GUID.Data + "|" + entry.URL)`; Atom: `for _, value := range []string{atomEntry.ID, atomEntry.Links.originalLink()} {`. Miniflux `ba40f99f66ccd8362628d7140abb0ab8a82e9f5f`, `internal/reader/rss/adapter.go` and `internal/reader/atom/atom_10_adapter.go`. <https://github.com/miniflux/v2/blob/ba40f99f66ccd8362628d7140abb0ab8a82e9f5f/internal/reader/rss/adapter.go>
18. **SimplePie's fallback.** `return call_user_func($fn, $this->get_permalink().$this->get_title().$this->get_content());` with `$fn = 'md5'`. SimplePie `9a648760e4c2472c30281d02f9c7a6ffa840bfe7`, `src/Item.php`. <https://github.com/simplepie/simplepie/blob/9a648760e4c2472c30281d02f9c7a6ffa840bfe7/src/Item.php>
19. **FreshRSS's fallback and column.** `$guid = sha1($item->get_permalink() . $item->get_date('U') . $item->get_title() . $item->get_content());` in `decideEntryGuid`; `` `guid` VARCHAR(767) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,	-- Maximum for UNIQUE is 767B ``. FreshRSS `1.30.4`, `app/Models/Feed.php` and `app/SQL/install.sql.mysql.php`. <https://github.com/FreshRSS/FreshRSS/blob/1.30.4/app/Models/Feed.php>
20. **Feedbin's id parts and protocol-agnostic guid.** `parts.push(@feed_url)`, `parts.push(base_entry_id)`, `parts.push(published.iso8601) if published.respond_to?(:iso8601)`, `parts.compact.join`; `result = [parsed.userinfo, parsed.path, parsed.query, parsed.fragment].join`. Feedkit `fbac4ffe3a68114716279759e638025ed18c425b`, `lib/feedkit/parser/entry.rb`. <https://github.com/feedbin/feedkit/blob/fbac4ffe3a68114716279759e638025ed18c425b/lib/feedkit/parser/entry.rb>
21. **Tiny Tiny RSS's chain.** `$entry_guid = strip_tags($item->get_id()); if (!$entry_guid) $entry_guid = strip_tags($item->get_link()); if (!$entry_guid) $entry_guid = self::make_guid_from_title($item->get_title());` then `if (!$entry_guid) { continue; }`. tt-rss `c02520ff1c26a0aab0c81ada1f42d377f02ac29c`, `classes/RSSUtils.php`. <https://github.com/tt-rss/tt-rss/blob/c02520ff1c26a0aab0c81ada1f42d377f02ac29c/classes/RSSUtils.php>
22. **feedparser never synthesises an id; a permalink guid becomes the link.** "guid acts as link, but only if 'ispermalink' is not present or is 'true', and only if the item doesn't already have a link element". feedparser `a22c5521cbb109871f1a2318948581901bd47e26`, `feedparser/namespaces/_base.py`. <https://github.com/kurtmckee/feedparser/blob/a22c5521cbb109871f1a2318948581901bd47e26/feedparser/namespaces/_base.py>
23. **gofeed and Feedjira.** gofeed: `item.GUID = rssItem.GUID.Value` (`translator.go` at `6014e99c09f70ccc83bccf7ea4ee772fb1bc7061`, <https://github.com/mmcdole/gofeed/blob/6014e99c09f70ccc83bccf7ea4ee772fb1bc7061/translator.go>). Feedjira: "Returns the id of the entry or its url if not id is present, as some formats don't support it" over `@entry_id ||= @url` (`v4.0.1`, <https://github.com/feedjira/feedjira/blob/v4.0.1/lib/feedjira/feed_entry_utilities.rb>).
24. **feed-rs's generated ids.** "// Generate a UUID as last resort" and `format!("{:x}{:x}", hash.h1, hash.h2)`; `pub fn id_generator_v0_2(self) -> Self` "Registers an ID generator compatible with v0.2 of feed-rs". feed-rs `17ddc9e257f7a512f8a4573e6ff65bcad041512b`, `feed-rs/src/parser/mod.rs`. <https://github.com/feed-rs/feed-rs/blob/17ddc9e257f7a512f8a4573e6ff65bcad041512b/feed-rs/src/parser/mod.rs>. Pull request 189: "I need these to be stable for feembox." <https://github.com/feed-rs/feed-rs/pull/189>. Pull request 219: "Adds pluggable ID generation for Feed and Entry instances that are missing IDs." <https://github.com/feed-rs/feed-rs/pull/219>
25. **Rome 2.1.0 id and link mapping.** `syndEntry.setUri(guidValue); if (itemLink == null && guid.isPermaLink()) { syndEntry.setLink(guidValue); }` `} else { syndEntry.setUri(itemLink); }`. `rometools/rome` tag 2.1.0, `ConverterForRSS094.createSyndEntry`. <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/feed/synd/impl/ConverterForRSS094.java>. Atom: `if (id != null) { syndEntry.setUri(entry.getId()); } else { syndEntry.setUri(syndEntry.getLink()); }`, `ConverterForAtom10`. <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/feed/synd/impl/ConverterForAtom10.java>
26. **Rome 2.1.0 resolves nothing by default; `xml:base` is stored on feed and entry only.** `private static boolean resolveURIs = false;` and in `resolveURI`: `if (!resolveURIs) { return url; }`; `final String xmlBase = eEntry.getAttributeValue("base", Namespace.XML_NAMESPACE);` followed by `entry.setXmlBase(xmlBase);` (and the same for `eFeed`). Tag 2.1.0, `Atom10Parser`. <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/Atom10Parser.java>
27. **Rome 2.1.0 enclosure length.** `enclosure.setLength(NumberParser.parseLong(length, 0L));` in `RSS092Parser` and `return Long.valueOf(str.trim());` in `NumberParser.parseLong`. Tag 2.1.0. <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/RSS092Parser.java> and <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/NumberParser.java>. Rome issue 695 (open): "Can't parse date with time zone offset without a colon (e.g. -0400)". <https://github.com/rometools/rome/issues/695>
28. **The Kotlin APIs are common in 2.2.0, and the platform encoders differ on malformed input.** Common: `public expect fun String.encodeToByteArray(): ByteArray` and `public expect fun String.startsWith(prefix: String, startIndex: Int, ignoreCase: Boolean = false): Boolean` (`libraries/stdlib/common/src/kotlin/TextH.kt`); `public inline fun Long.floorDiv(other: Long): Long {` (`libraries/stdlib/src/kotlin/util/FloorDivMod.kt`). JVM actual: `public actual fun String.encodeToByteArray(): ByteArray { return this.toByteArray(Charsets.UTF_8) }` (`libraries/stdlib/jvm/src/kotlin/text/StringsJVM.kt`). JS: `private val REPLACEMENT_BYTE_SEQUENCE: ByteArray = byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte())` (`libraries/stdlib/js/src/kotlin/text/utf8Encoding.kt`). Tag `v2.2.0` of <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/common/src/kotlin/TextH.kt>, <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/util/FloorDivMod.kt>, <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/jvm/src/kotlin/text/StringsJVM.kt>, <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/js/src/kotlin/text/utf8Encoding.kt>.
29. **Database index-key limits that bound a sensible id cap.** "The index key prefix length limit is 3072 bytes for InnoDB tables that use DYNAMIC or COMPRESSED row format." and "The index key prefix length limit is 767 bytes for InnoDB tables that use the REDUNDANT or COMPACT row format." MySQL 8.4 Reference Manual, InnoDB Limits. <https://dev.mysql.com/doc/refman/8.4/en/innodb-limits.html> PostgreSQL's btree limit (single source): the error text "index row size exceeds btree version 4 maximum 2704 for index" and "It needs to fit at least 3 B-tree tuples on a 8Kb page." Supabase troubleshooting, read 2026-11-06. <https://supabase.com/docs/guides/troubleshooting/error-index-row-size-exceeds-btree-version-4-maximum-for-index-LMmoeU.md>

## Gaps

- **Digest ids are specified here, not observed elsewhere.** The vectors were produced by the Kotlin reference and reproduced by a separate Python implementation of the same text; no third-party implementation of this scheme exists. The SHA-256 core is checked against RFC 6234's three vectors, FIPS/NIST's empty and 896-bit messages and 313 inputs against `MessageDigest`; the empty and 896-bit expected values were not quoted from NIST's PDF (the JDK agreed).
- **`js` was compiled, not executed.** The klib compile proves that every API used exists in the common standard library 2.2.0; it does not prove runtime equality of `Long.toString`, `floorDiv` of negative values or `Int` overflow behaviour on a JavaScript engine. The Level 4a vectors run on the JVM only; the check module only compiles `js`.
- **The text the digest hashes comes from the parser.** Entity decoding, the type of a title (`text`, `html`, `xhtml`) and whitespace handling are Rome's. A Rome upgrade or a different parser can change a title's text and so a digest id; this was not tested across Rome versions. Which Rome getter supplies `title`, `summary`, `content` and the entry link is the adapter's mapping, not settled here.
- **How the adapter recognises a guid-derived link** (entry link text equal to the entry `uri` text, no `<link>` element) was not run against Rome's beans; the rule only requires that such a candidate be passed with `allowRelative = false`.
- **`xml:base` beyond the feed and entry elements** is not exposed by Rome 2.1.0 (code reading), and no sampled feed uses it on a link-bearing element; the nested resolution (entry base against feed base against document URL) is reasoned from RFC 4287 §2 and not tested against a real feed.
- **Spaces inside URLs** are refused (null). The sample has none and no outside source on prevalence was found; accepting them later (percent-encoding U+0020) would be additive, whereas refusing after accepting would not be.
- **Invisible and bidirectional characters** and private or loopback hosts are not refused in links (Pitfalls). No source was consulted on a recommended policy.
- **The default numbers are judgement**, supported by one sample of 57 feeds fetched on one day (hand-gathered, mostly large publications and podcasts). `maxTextChars` 262,144 has no feed above 174,346 behind it; `maxLinkChars` 8,000 rests on RFC 9110's floor, not on observation (570 seen); the PostgreSQL figure rests on one source. The id cap interacts with a caller's column width, which is unknown here.
- **ASCII whitespace set.** TAB, LF, FF, CR and SPACE is the set chosen for trimming; it was not checked against a normative definition.
- **Prevalence of the digest step is unknown.** The sample reached it zero times (45 guid-less items, all with links). Its importance rests on the RSS 2.0 rule that an item may omit everything but a description, not on measured frequency.
- **Duplicate-id handling** (repair, detection or a flag on `Feed`) is not designed here; a caller sees the repeats as the parser returned them.
