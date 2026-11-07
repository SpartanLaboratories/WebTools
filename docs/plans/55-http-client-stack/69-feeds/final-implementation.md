# As built: `feeds` — `webtools-feeds` (Issue #69)

- **Plan:** [`plan.md`](plan.md). Consequences: [`consequences.md`](consequences.md).
- **Status:** in progress. Stages 1 (skeleton and vocabulary), 2 (prolog scan and `RomeFeedParser`), 3 (the fetcher) and the hardening
  suite of stage 4 are built; the README and CHANGELOG entries of stage 4 follow.

## What was built

### Stage 1 — skeleton and vocabulary

- `settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`.
- `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of
  `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical
  `webtools-feeds/src/jvmTest/resources/logback-test.xml`.
- `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`,
  `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; `FeedParser.kt`, `FeedInput.kt`; `Feed.kt`, `FeedEntry.kt`,
  `FeedEnclosure.kt`; `FeedValidators.kt`, `FeedFetch.kt`; `TextRules.kt`, `Sha256.kt`, `LinkRules.kt`, `EntryIdentity.kt`.
  `jvmMain`: `Log.jvm.kt`.
- The first ABI dump `webtools-feeds/api/webtools-feeds.api`.
- Test support (`com.spartanlabs.testing.support.webtools.feeds`, `commonTest`): `ByteDocuments`, `DigestVectors`, `LinkVectors`,
  `ChainFixtures`, `FeedModelFixtures`.
- Tests, 16 classes and 79 tests, all in `commonTest`: Level 1 `FeedsConsumerGatingTest` (4); Level 2 `FeedLimitsComponentTest` (8),
  `FeedValidatorsComponentTest` (4), `FeedFetchComponentTest` (4), `FeedComponentTest` (5), `FeedEntryComponentTest` (8),
  `FeedEnclosureComponentTest` (5), `FeedFailureComponentTest` (5), `FeedInputComponentTest` (5); Level 4a `Sha256DeterministicTest` (6),
  `EntryDigestDeterministicTest` (3), `EntryIdDeterministicTest` (4), `LinkRuleDeterministicTest` (8), `TextRulesDeterministicTest` (5),
  `FeedValidatorsDeterministicTest` (2), `UrlOriginDeterministicTest` (3).

### Stage 2 — prolog scan and `RomeFeedParser`

- `commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`,
  `PrologScan` with the three readings and the combination rule of the plan), `PrologScanFailure.kt` (`toFailureOrNull`).
- `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`,
  `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`),
  `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` (`RomeRead`, `readWithRome`, `toFeed` and the
  entry and enclosure conversion), `RomeFeedParser.kt`.
- `.gitattributes`: the `webtools-feeds/src/jvmTest/resources/feeds/** -text` line. Fixtures `webtools-feeds/src/jvmTest/resources/feeds/`:
  `rss090.rdf`, `rss091u.xml`, `rss092.xml`, `rss20.xml`, `rss10.rdf`, `atom03.xml`, `atom10.xml` (synthetic, UTF-8, LF, under 2 KB).
- The regenerated ABI dump: `RomeFeedParser` with its two constructors and `parse` is the only addition.
- Test support: `PrologVectors` (`commonTest`, the 135-row catalogue), `ParserFactory` and `RomeParserFactory`, `FeedFixtures`,
  `FeedExpectations`, `RomePipelineProbe` (`jvmTest`).
- Tests, 11 classes and 103 tests: Level 2 `PrologScanFailureComponentTest` (4, `commonTest`), `RomeFailureMappingComponentTest` (12),
  `CountingSaxHandlerComponentTest` (5), `RomeXmlReaderComponentTest` (6), `RomeConversionComponentTest` (14),
  `RomeFeedParserComponentTest` (4); Level 4a `PrologScanVectorsDeterministicTest` (7, `commonTest`),
  `PrologScanReadingsDeterministicTest` (6, `commonTest`), `RomeFeedParserConformanceTest` (39, through the abstract
  `FeedParserConformanceContract`), `Sha256DifferentialDeterministicTest` (2), `PrologScanRomeDifferentialDeterministicTest` (4).

### Stage 3 — the fetcher

- `commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`,
  `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`,
  `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt`
  (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers).
- `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), `FeedFetcherOwning.kt` (`FeedFetcher.untrusted(userAgent)` and
  `untrusted(userAgent, policy)`). The check module's `JsActuals.kt` gains the `NoParser` actual of `defaultFeedParser`.
- The regenerated ABI dump adds `FeedFetcher` (with `FeedFetcher$Companion`, no constructor), `FeedFetcherConfig` (no constructor),
  `FeedFetcherKt` and `FeedFetcherOwningKt`; still no Rome, JDOM, SAX or slf4j type.
- Test support (`jvmTest`): `FeedFetcherSupport` (`FEED_URL`, `fetcherOn`, `rssBody`, `respondWith`, `validatorHeaders`, `captureFeedLogs`,
  `feedEvents`), `RecordingParser`, `GateParser`, `ThrowingParser`, `Boom`, `RecordingDispatcher`, `ConditionalGetDispatcher`.
- Tests, 17 classes and 93 tests: Level 1 `FeedFetcherConsumerGatingTest` (5); Level 2 `FeedFetcherConfigComponentTest` (6, `commonTest`),
  `FeedsDslMarkerComponentTest` (2), `FeedFetcherRequestComponentTest` (6), `FeedFetcherStatusComponentTest` (12),
  `FeedFetcherMovesComponentTest` (8), `FeedFetcherPassThroughComponentTest` (6), `FeedFetcherParseComponentTest` (10),
  `FeedFetcherLifecycleComponentTest` (6), `FeedFetcherOwningFactoryComponentTest` (4), `FeedFetcherLoggingComponentTest` (6),
  `FeedFetcherConfigDefaultsComponentTest` (2); Level 3 `FeedFetcherLoopbackIntegrationTest` (9), `FeedFetcherOwningFactoryIntegrationTest`
  (3); Level 4a `ConditionalHeadersDeterministicTest` (4), `ResponseVerdictDeterministicTest` (2), `PermanentMoveDeterministicTest` (2), the
  last three in `commonTest`. After stage 3 the module holds 44 classes and 275 tests: gating 2/9, component 25/157, integration 2/12,
  deterministic 15/97.

### Stage 4 — the hardening suite

- Test support (`jvmTest`): `HostileDocuments` (the 2 MiB shapes by a fixed-capacity writer, the small structural documents, and
  `byName`, the child-JVM case table with each case's expected result line), `HostileParseJvm` (the launcher: `-Xmx<heap>m`,
  `-XX:+ExitOnOutOfMemoryError`, the worker's class path or an `@argfile` above 24,000 characters, output to a temporary file, a budget
  with `destroyForcibly`), `HostileParseMain` (builds the parser by factory name, generates the document, parses on a barrier, prints
  one result line per parse, exits 0 / 11 / 12; the launcher's own `smoke`, `oom` and `hang` cases), `Jdk11SmokeMain`.
- Tests, 6 classes and 53 tests, all Level 4c in `jvmTest`: `RomeFeedParserHardeningTest` (39, through the abstract
  `FeedParserHardeningContract`: 21 in process, 18 in a child JVM at `-Xmx128m` with two concurrent parses),
  `HostileParseJvmNonFunctionalTest` (3), `FeedFetcherSecrecyNonFunctionalTest` (5), `RomeFeedParserIsolationNonFunctionalTest` (3),
  `FeedFetcherCloseRaceNonFunctionalTest` (2), `RomeFeedParserJdk11NonFunctionalTest` (1, skipped by its assumption unless
  `WEBTOOLS_JDK11_HOME` is set). No main code changed: the ABI dump is the one of stage 3.
- The module now holds 50 classes and 328 tests: gating 2/9, component 25/157, integration 2/12, deterministic 15/97, non-functional 6/53.

## Procedure results

### P1 — the digest and the dump (stage 1)

- `sha256("webtools-feeds/entry-id/1")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and
  `sha256("webtools-feeds/entry-id/1\nt5:Hello\nd10:1700000000")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and
  the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in
  `EntryDigestDeterministicTest`.
- After `:webtools-feeds:apiDump`, `grep -n "<init>" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure`
  leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, `FeedInput` and `FeedValidators`: none on
  `FeedLimits`, `Feed`, `FeedEntry` or `FeedEnclosure` (their constructors are `internal`). No line names `com/rometools`, `org/jdom2`,
  `org/xml/sax` or `org/slf4j`; no `copy` or `component` function appears.

### P2 — the js compile and the dump (each code stage)

- Stage 1: `:webtools-feeds-commoncheck:build` passes with every `commonMain` file of stage 1 compiled for `js`
  (`String.encodeToByteArray`, `Long.floorDiv`, `StringBuilder.deleteRange` and `CharSequence.lastIndexOf` included).
  `:webtools-feeds:apiCheck` and `:webtools-feeds:verifyJavaFloor` pass after the dump.
- Stage 2: `:webtools-feeds-commoncheck:build` passes with `PrologScan.kt`, `PrologScanFailure.kt` and `ResultExtensions.kt` added
  (`buildList`, `Enum.entries`, `data object` and `indexOf(…, ignoreCase = true)` compile for `js`); `apiDump` then `apiCheck` pass, the
  dump diff being `RomeFeedParser` alone; `verifyJavaFloor` passes with Rome 2.1.0, `rome-utils` 2.1.0 and `jdom2` 2.0.6.1 on the runtime
  classpath (every class at or below Java 11 bytecode).
- Stage 3: `:webtools-feeds-commoncheck:build` passes with `FeedFetcherConfig.kt`, `FeedFetcher.kt`, the conditional-GET rules and the
  `defaultFeedParser` `expect` added (`Semaphore`, `withPermit`, `Job()`, `Dispatchers.Default`, `TimeSource.Monotonic` and
  `KClass.simpleName` compile for `js`; the `js` actual of `defaultFeedParser` lives in the check module); `apiDump` then `apiCheck` pass,
  the dump diff being `FeedFetcher`, `FeedFetcher$Companion`, `FeedFetcherConfig`, `FeedFetcherConfig$Companion`, `FeedFetcherKt` and
  `FeedFetcherOwningKt`; `verifyJavaFloor` passes.
- Stage 4: no `commonMain` change; `:webtools-feeds-commoncheck:build`, `apiCheck` (the dump unchanged) and `verifyJavaFloor` pass.
- Differential results of stage 2: the 135 catalogue rows and 20,000 generated documents never reach the DOCTYPE in any of the seven
  `XmlReader` shapes while the scan says `NoDoctype`; no `Empty` or `NotMarkup` document parses in any shape; the 26 catalogue rows the
  research records as parsing are read by `RomeFeedParser` (N19, `<htmlx/>`, as a well-formed non-feed, `NotAFeed(UnknownFormat)`).

## Deviations

Stage 1 builds the plan's letter: every signature, constant, message and floor is as the plan states them, and the rules are the
verified reference with only the package, the file split and the `isNullOrBlank` tightening of `entryId` the plan calls for.

### D1. The decoder-divergence rule also applies to a reading that ended `NotMarkup` (`PrologScan.scanReading`; plan §7.11, change 4)

- **What differs.** Plan §7.11 change 4 keeps `scanReading` unchanged, so the decoder-divergence rule turns only a `NoDoctype`
  reading into `Undecodable(MalformedSequence)`. As built, the rule applies to a reading that ended `NoDoctype` **or** `NotMarkup`.
- **Why.** Change 2 of the same section makes a first significant unit that is not `<` return `NotMarkup` where the reference
  returned `NoDoctype`. The reference's safety for a junk unit after a closed comment rested on the divergence rule: in U23 and
  U24 the 16-bit decoder drops the dash after a lone surrogate, so the comment the scan saw as closed is open to the parser, and
  the `x` that follows is reached by the scan but not by the parser, which reads on to the DOCTYPE. With `NotMarkup` outside the
  rule, those two rows score `NotMarkup` in the 16-bit reading and `NoDoctype` in the byte reading (`3C 00` is `<` and a unit
  that cannot start a name), so the scan says `NoDoctype` and the pipeline reaches the DOCTYPE, which the differential test
  forbids; U27 and U31 (the swallow-prone encodings) score `NotMarkup` instead of the catalogued `MalformedSequence`. Applying
  the rule to `NotMarkup` too restores the catalogue's expectation for all four rows and the invariant the differential test
  pins, and changes no other row: a `NotMarkup` reading of plain ASCII, JSON or white space holds no surrogate and no byte at or
  above `0x80` within the units it read. Basis: [`research/prolog-scan.md`](research/prolog-scan.md) Recommendation step 5 and
  the catalogue rows U23, U24, U27, U31.

### D2. The isolation test's two limits are 50 and the default 100, not 50 and 5,000 (`RomeFeedParserIsolationNonFunctionalTest`; plan §9, Level 4c)

- **What differs.** The plan's test "two parsers with different limits give the right result under sixteen threads" says "limit 50 must
  reject a 60-attribute document, limit 5,000 must accept it". As built, the accepting parser runs on the default `maxAttributesPerElement`
  of 100 and the rejecting one on 50; 6,400 parses, zero wrong outcomes.
- **Why.** `FeedLimits` tightens only (plan §1 R3): `maxAttributesPerElement` cannot be set above its default of 100, so a limit of 5,000
  cannot be built. The test's purpose, that one parser's limit does not reach another's, holds with 50 against 100. Basis:
  [`research/parse-memory.md`](research/parse-memory.md) F7.

### P3 — the gate's teeth (stage 4, scratch)

With the element cap of `CountingSaxHandler.startElement` disabled (a scratch edit, reverted), the child-JVM cases at `-Xmx128m` with two
concurrent parses gave: `item-flood` exit code 3 (`OutOfMemoryError: Java heap space`) after 1.28 s, `entry-flood` exit code 3 after 1.33 s,
`a-text-flood` exit code 3 after 0.65 s; `a-flood-channel` and `a-flood-item` parsed unbounded (`ok:0` and `ok:1`) instead of
`LimitExceeded:Elements`. With the cap restored every case passes again. The gate fails on the regression it exists to catch.

### Child-JVM gate measurements (stage 4; build JDK 23.0.2, `-Xmx128m`, two concurrent parses, wall time including JVM start)

| Case | Result line | Wall time |
|---|---|---|
| `item-flood` | `LimitExceeded:Elements` | 399 ms |
| `entry-flood` | `LimitExceeded:Elements` | 350 ms |
| `a-flood-channel` | `LimitExceeded:Elements` | 351 ms |
| `a-flood-item` | `LimitExceeded:Elements` | 364 ms |
| `a-text-flood` | `LimitExceeded:Elements` | 374 ms |
| `attributes-10000` | `LimitExceeded:Attributes` | 253 ms |
| `attributes-100` | `ok:0` | 661 ms |
| `attributes-101` | `LimitExceeded:Attributes` | 238 ms |
| `rss-nested-290000` | `LimitExceeded:Depth` | 256 ms |
| `atom-xhtml-5000` | `LimitExceeded:Depth` | 251 ms |
| `rss-description-5000` | `LimitExceeded:Depth` | 251 ms |
| `xmlns-distinct` | `LimitExceeded:NamespaceUris` | 226 ms |
| `xmlns-repeated` | `ok:0` | 701 ms |
| `text-2mib` | `ok:1` | 487 ms |
| `realistic-400` | `ok:400` | 770 ms |
| `items-1000` | `ok:1000` | 587 ms |
| `items-1001` | `LimitExceeded:Entries` | 563 ms |
| `id-2mib` | `ok:1` | 537 ms |

Every child exited 0 with both result lines as expected; the whole hardening class runs in about 9 s. The figures are the hang-detection
bound's context, not a performance claim.

### P4 — JDK 11 (stage 4)

No JDK 11 is installed on the build host and `WEBTOOLS_JDK11_HOME` is unset: `RomeFeedParserJdk11NonFunctionalTest` is skipped by its
assumption (`TestAbortedException`). The claim that `webtools-feeds` runs on JDK 11 rests on the research's measurement until the test has
run on a host with a JDK 11.

### P5 — Level 5 acceptance pass (stage 4, manual)

Run on 2026-11-07 from the build host through `FeedFetcher.untrusted(…)` with a descriptive user agent and the production defaults, by a
scratch `main` in `jvmTest` that printed outcome names, entry counts and presence flags only; the scratch file was deleted afterwards. URLs
are given by origin.

| Case | Origin | Outcome | Entries | Validators (ETag / Last-Modified) | `permanentlyMovedTo` |
|---|---|---|---|---|---|
| RSS 2.0 news feed | `https://feeds.bbci.co.uk` | `Fetched` | 36 | absent / absent | absent |
| Atom 1.0 blog | `https://go.dev` | `Fetched` | 10 | absent / absent | absent |
| Conditional request, first fetch | `https://blog.rust-lang.org` | `Fetched` | 10 | present / present | absent |
| Conditional request, second fetch with the returned validators | `https://blog.rust-lang.org` | `NotModified` | — | present / present | absent |
| Feed behind a `301` (`http` to `https` on the same host) | `http://blog.rust-lang.org` | `Fetched`, `finalUrl` on `https://blog.rust-lang.org` | 10 | present / present | present |
| Podcast with more than 1,000 entries (1,113 items) | `https://feed.theskepticsguide.org` | `LimitExceeded(Entries)` | — | — | — |
| Podcast with more than 1,000 entries (1,018 items, over 2 MiB decoded) | `https://changelog.com` | `BodyTooLarge` | — | — | — |
| Feed served over a redirect from `https` to `http` | `https://slashdot.org` | `RefusedByPolicy(InsecureRedirect)` | — | — | — |
| URL that serves a web page | `https://www.example.com` | `NotAFeed(Html)` | — | — | — |

Every case gave the expected outcome. A long podcast fails with `LimitExceeded(Entries)` when its document fits the 2 MiB cap and with
`BodyTooLarge` when it does not, as the module's limits state. The IDE checks of §9.2 (completion of the factories, `@WebToolsDsl` scoping,
Quick Documentation, the opt-in diagnostic) and the read of each public type's KDoc as a consumer sees it are left to the maintainer.
