# Final implementation: `feeds` (Issue #69)

The as-built record of `SpartanLaboratories/WebTools#69`, unit 10 (`feeds`) of [#55's architecture](../architecture.md). It states what was built,
where the build departs from [`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for the repository, its published
artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in [`consequences.md`](consequences.md).

- **Status:** built through the four stages of [`plan.md`](plan.md) §5.3, with the deviations of §2. Procedures P1 to P5 of plan §9.3 ran (§4); P4's
  JDK 11 test is skipped on the build host, which has no JDK 11. The `README.md` and `CHANGELOG.md` text of plan §8 lands with this record.
- **Branch and commits:** `feat/issue-69-feeds`, cut from `master` at `0374c8d`, with the research commits `88af7dc`, `066bf17`, `5ae0bda` and `e89d86a`
  and the commits `e3ecfe2` (stages 1 to 3, with this folder's `plan.md` and `consequences.md` and the dated corrections of `../architecture.md`) and
  `25678cf` (stage 4). `25678cf` is the code-landing commit. The KDoc and comments of 23 source files under `webtools-feeds/src/` (12 main, 11 test),
  `README.md`, `CHANGELOG.md` and the documents under `docs/` are committed after it; no executable line of the main sources changes after it.
- **Architecture:** [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-06) for this unit: from the plan at the end of §6.10 and
  under §11 unit 10, and as built at §3, §12 and §13. [`consequences.md`](consequences.md) §5 states each refinement.
- **Host of the measurements:** Windows 11, JDK 23.0.2, Gradle 9.7.1, Kotlin 2.2.0, Rome 2.1.0 with `rome-utils` 2.1.0 and `jdom2` 2.0.6.1, 32 logical
  processors. JDK 11, Linux and macOS were not exercised.
- **Versions moved:** none. `webtools-feeds` 1.0.0 is new and stays unreleased until #55's stack is complete; this landing is not a release candidate.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with the deviations of §2. Every signature, constant, message and floor is as the plan states them, and the
identity, link and text rules are the verified reference with only the package, the file split and the `isNullOrBlank` tightening of `entryId` the plan
calls for.

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
  `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`).
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
- The regenerated ABI dump adds `FeedFetcher` and `FeedFetcherConfig`, neither with a constructor, their companions, `FeedFetcherKt` and
  `FeedFetcherOwningKt`; still no Rome, JDOM, SAX or slf4j type.
- Test support (`jvmTest`): `FeedFetcherSupport` (`FEED_URL`, `fetcherOn`, `rssBody`, `respondWith`, `validatorHeaders`, `captureFeedLogs`,
  `feedEvents`), `RecordingParser`, `GateParser`, `ThrowingParser`, `Boom`, `RecordingDispatcher`, `ConditionalGetDispatcher`.
- Tests, 17 classes and 93 tests: Level 1 `FeedFetcherConsumerGatingTest` (5); Level 2 `FeedFetcherConfigComponentTest` (6, `commonTest`),
  `FeedsDslMarkerComponentTest` (2), `FeedFetcherRequestComponentTest` (6), `FeedFetcherStatusComponentTest` (12),
  `FeedFetcherMovesComponentTest` (8), `FeedFetcherPassThroughComponentTest` (6), `FeedFetcherParseComponentTest` (10),
  `FeedFetcherLifecycleComponentTest` (6), `FeedFetcherOwningFactoryComponentTest` (4), `FeedFetcherLoggingComponentTest` (6),
  `FeedFetcherConfigDefaultsComponentTest` (2); Level 3 `FeedFetcherLoopbackIntegrationTest` (9), `FeedFetcherOwningFactoryIntegrationTest`
  (3); Level 4a `ConditionalHeadersDeterministicTest` (4), `ResponseVerdictDeterministicTest` (2), `PermanentMoveDeterministicTest` (2), the
  last three in `commonTest`.
- The two tests of `FeedFetcherParseComponentTest` that hold a parse at `GateParser`, `at most maxConcurrentParses parses run at once` and
  `cancelling a fetch that waits for a permit is thrown and starts no parse, and a running parse finishes`, run on a real dispatcher of four threads
  and prove their negatives on signals: `GateParser` reports each parse that begins and each that runs, and the fake transport counts every response it
  serves down on a latch, so each test knows that every fetch has passed the transport and reached the parse permit before it asserts that no further
  parse begins within 500 ms. Neither sleeps before it asserts: the 500 ms bounds the negative assertion's wait on the signal.

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

### Tests

| Level | Classes | Tests |
|---|---|---|
| 1 gating | 2 | 9 |
| 2 component | 25 | 157 |
| 3 integration | 2 | 12 |
| 4a deterministic | 15 | 97 |
| 4c non-functional | 6 | 53 |
| **Total** | **50** | **328** |

These are plan §9.1's totals, class for class: the two abstract contract classes are not counted, and their 39 and 39 tests run through
`RomeFeedParserConformanceTest` and `RomeFeedParserHardeningTest`. In the final run 327 tests pass and 1 is skipped,
`RomeFeedParserJdk11NonFunctionalTest`, because no `WEBTOOLS_JDK11_HOME` names a JDK 11 on the build host (§4, P4).

## 2. Deviations

Each states what was built where it departs from [`plan.md`](plan.md), the plan section it departs from, and why. None changes a public signature, a sibling
contract or a refinement of [`consequences.md`](consequences.md) §5.

### D1. The decoder-divergence rule also applies to a reading that ended `NotMarkup`

- **Departs from:** plan §7.11, change 4, which keeps `scanReading` as the reference has it, so that the decoder-divergence rule turns only a reading that
  ended `NoDoctype` into `Undecodable(MalformedSequence)`.
- **As built:** `PrologScan.scanReading` applies the rule to a reading that ended `NoDoctype` **or** `NotMarkup`.
- **Why:** change 2 of the same section makes a first significant unit that is not `<` return `NotMarkup` where the reference returned `NoDoctype`. The
  reference's safety for a junk unit after a closed comment rested on the divergence rule: in U23 and U24 the 16-bit decoder drops the dash after a lone
  surrogate, so the comment the scan saw as closed is open to the parser, and the `x` that follows is reached by the scan but not by the parser, which reads
  on to the DOCTYPE. With `NotMarkup` outside the rule, those two rows score `NotMarkup` in the 16-bit reading and `NoDoctype` in the byte reading (`3C 00`
  is `<` and a unit that cannot start a name), so the scan says `NoDoctype` and the pipeline reaches the DOCTYPE, which the differential test forbids; U27
  and U31 (the swallow-prone encodings) score `NotMarkup` instead of the catalogued `MalformedSequence`. Applying the rule to `NotMarkup` too restores the
  catalogue's expectation for all four rows and the invariant the differential test pins, and changes no other row: a `NotMarkup` reading of plain ASCII,
  JSON or white space holds no surrogate and no byte at or above `0x80` within the units it read. Basis: [`research/prolog-scan.md`](research/prolog-scan.md)
  Recommendation step 5 and the catalogue rows U23, U24, U27 and U31.

### D2. The isolation test's two limits are 50 and the default 100

- **Departs from:** plan §9, `RomeFeedParserIsolationNonFunctionalTest`, `two parsers with different limits give the right result under sixteen threads`:
  "limit 50 must reject a 60-attribute document, limit 5,000 must accept it".
- **As built:** the accepting parser runs on the default `maxAttributesPerElement` of 100 and the rejecting one on 50; 6,400 parses, no wrong outcome.
- **Why:** `FeedLimits` only tightens (plan R3): `maxAttributesPerElement` cannot be set above its default of 100, so a limit of 5,000 cannot be built.
  The test's purpose, that one parser's limit does not reach another's, holds with 50 against 100. Basis: [`research/parse-memory.md`](research/parse-memory.md)
  F7.

### D3. Two implementation commits instead of four

- **Departs from:** plan §5.3, where each stage ends in a commit, and §11, commits 1 to 4, one per stage, each leaving the build green with its own
  regenerated dump.
- **As built:** `e3ecfe2` carries stages 1 to 3, with the dump as stage 3 leaves it, `plan.md`, `consequences.md` and the dated corrections of
  `../architecture.md`; `25678cf` carries stage 4's test support and Level 4c classes. The `README.md` and `CHANGELOG.md` text that §5.3 places at the end
  of stage 4 lands with this record.
- **Why:** the working tree held the three code stages with only the final ABI dump. A commit of stage 1 or stage 2 on its own would have needed that
  stage's dump to pass `apiCheck`, so the code stages went into one commit; stage 4 adds no public declaration and passes `apiCheck` against the same dump.
  Each stage's procedures ran as the stage was built (§4, P1 and P2).

### D4. `RomeFeedParser`'s KDoc names the shared state a parse grows

- **Departs from:** plan R12 ("Everything is created per `parse` call; no static state is read or written. It logs nothing.") and the class KDoc of §7.13
  ("reads and writes no static state of Rome, JDOM or the JDK. It logs nothing.").
- **As built:** the code is §7.13's. The class KDoc says that every parse creates its own reader, input, builder and SAX parser and sets no process-wide
  configuration of Rome, JDOM or the JDK (no shared SAX factory, no system property, no default changed); that the one shared state a parse grows is
  JDOM's process-wide namespace cache, by at most `maxNamespaceUris` new URIs per document; and that the parser logs nothing of its own, while Rome logs
  rarely, under `com.rometools.*` loggers.
- **Why:** `org.jdom2.Namespace` keeps every namespace URI a parse declares in a static cache that never evicts, so a parse does write static state,
  bounded by the namespace-URI limit; and Rome's own classes hold slf4j loggers, none of them on this parser's path. The planned sentences were false for the
  first and too broad for the second. Basis: [`research/parse-memory.md`](research/parse-memory.md) F6 and [`research/rome-adapter.md`](research/rome-adapter.md)
  F11.

### D5. `FeedFetcher`'s KDoc names each `Closed` a fetch can meet around `close()`

- **Departs from:** plan §7.16, whose class KDoc gives the lifecycle as "a fetch after [close] returns `Closed`", and plan §6, where `Closed("feed fetcher")`
  is a component name of the fetcher's own "so a caller can tell it from the `Closed("fetcher")` of a `UrlFetcher` it closed itself".
- **As built:** the code is §7.16's: a fetch that starts after `close()` returns `Closed("feed fetcher")`. The class KDoc adds that closing a fetcher that
  owns its `UrlFetcher` closes the transport under it, so that each fetch still on the network returns `Closed("transport")`, and that a fetch racing
  `close()` may return the composed `UrlFetcher`'s `Closed("fetcher")`; it tells the caller to match `HttpFailure.Closed` rather than one component name.
  `FeedFetcherCloseRaceNonFunctionalTest` counts any `HttpFailure.Closed` as the closed outcome.
- **Why:** a fetch that passed the closed check before `close()` goes on into the `UrlFetcher` the fetcher has just closed, or is cancelled on the network
  by the transport's close, and every failure of the `UrlFetcher` passes through unchanged (R9). The component name tells a caller which part refused, not
  whether its own fetcher was closed.

### D6. The KDoc was not read as a consumer sees it

- **Departs from:** plan §9.3 P5: "Read the KDoc of each public type as a consumer sees it (hover text and the dump) and check the tier statements, the
  "caller owns" list, the logging statements and the heap figure."
- **As built:** P5's fetches ran (§4); the read was not made. It is open, with its owner and trigger, in [`consequences.md`](consequences.md) §8.
- **Why:** the read is of the KDoc as a consumer meets it, in the IDE's hover text beside the dump, so it goes with the IDE checks of plan §9.2 to the next
  IntelliJ session.

### D7. Test names in plain words

- **Departs from:** the test names of plan §9 that carry code punctuation, a list or a reference.
- **As built:** no test name holds `.`, `:`, `;`, `/`, `<`, `>`, `(`, `)`, `"` or `&`. `NotAFeed(UnknownFormat)` reads `NotAFeed UnknownFormat`,
  `Closed("feed fetcher")` reads `Closed feed fetcher`, `A -301-> B` reads `A 301 to B`, `xml:base` reads `xml base`, `rss 2.0` reads `rss 2 0` and
  `Dispatchers.Default` reads `Dispatchers Default`; parentheses, colons and semicolons give way to commas, ", so" or ", and". The 18 child-JVM cases'
  names end with their expected result (`an item flood is LimitExceeded Elements`, `400 realistic items are accepted with 400 entries`). Six names say
  less than the plan's or say it differently, each running the plan's cases:
  - `Doctype and each Undecodable reason map to Refused with their reasons` (the plan lists the four reasons);
  - `a 200 with unusable validators gives null` (the plan names non-ASCII, 300 characters and the star);
  - `invalid XML characters are Malformed` (the plan names U+0001 and `&#1;`);
  - `a seeded generator of 20,000 documents never reaches the DOCTYPE while the scan says NoDoctype` (the plan lists the generator's shapes);
  - `10,000 attributes on each element is LimitExceeded Attributes` (the plan says "on each of 36 elements");
  - `every catalogue row the real XML pipeline parses is read by RomeFeedParser when the scan says NoDoctype`, for the plan's
    `every catalogue row the findings record as parses is parsed by RomeFeedParser when the scan says NoDoctype`.
- **Why:** a JVM method name cannot hold `.`, `:`, `;`, `/`, `<` or `>`, so the names are written as plain words throughout, the other punctuation
  included; and a name that points at the research means nothing to a reader of the source.

## 3. Acceptance

The six criteria of plan §1 ("Acceptance"):

1. **The module's tasks pass.** Every level of tests (§1: 327 pass and the JDK 11 test is skipped by design), `:webtools-feeds:apiCheck` against the
   committed dump, `:webtools-feeds:verifyJavaFloor` with Rome, JDOM and their transitive jars on the runtime class path, and `:webtools-feeds-commoncheck:build`,
   the `js` compile of `commonMain` (§4, P2). Met.
2. **`RomeFeedParser` returns the expected `Feed` for each fixture, and both contracts pass.** `RomeFeedParserConformanceTest` (39 tests, the seven
   fixtures of RSS 0.90, 0.91, 0.92, 2.0, RSS 1.0, Atom 0.3 and Atom 1.0 among them) and `RomeFeedParserHardeningTest` (39 tests) pass. Met.
3. **Every hostile case of the fixed-heap gate exits with code 0 and the expected result kinds at `-Xmx128m`.** The 18 cases of §4, each with both result
   lines as expected; P3 shows the gate fails without the element cap. Met.
4. **`FeedFetcher` behaves as R9 states over `FakeHttpTransport` and over `LoopbackFetchers.untrusted`, and `FeedFetcher.untrusted` refuses a loopback URL
   with no request reaching the server.** The Level 2 fetcher classes of §1, `FeedFetcherLoopbackIntegrationTest` (9) and
   `FeedFetcherOwningFactoryIntegrationTest` (3), whose `a loopback URL is refused with RefusedByPolicy and no request reaches the server` passes. Met.
5. **The ABI dump holds no Rome, JDOM, SAX or slf4j type, and no `data class`.** P1. Met.
6. **`README.md` and `CHANGELOG.md` carry the entries of plan §8.** They land with this record.

## 4. Procedure results

### Differential results of stage 2

`PrologScanRomeDifferentialDeterministicTest` runs the scan against the real pipeline in seven `XmlReader` shapes. The 135 catalogue rows and 20,000
generated documents never reach the DOCTYPE in any shape while the scan says `NoDoctype`; no `Empty` or `NotMarkup` document parses in any shape; and the
26 catalogue rows the real XML pipeline parses are read by `RomeFeedParser` when the scan says `NoDoctype` (N19, `<htmlx/>`, a well-formed root that is no
feed format, as `NotAFeed(UnknownFormat)`).

### P1 — the digest and the dump (stage 1)

- `sha256("webtools-feeds/entry-id/1")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and
  `sha256("webtools-feeds/entry-id/1\nt5:Hello\nd10:1700000000")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and
  the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in
  `EntryDigestDeterministicTest`.
- After `:webtools-feeds:apiDump`, `grep -n "<init>" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure`
  leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, `FeedInput` and `FeedValidators`: none on
  `FeedLimits`, `Feed`, `FeedEntry` or `FeedEnclosure` (their constructors are `internal`). No line names `com/rometools`, `org/jdom2`,
  `org/xml/sax` or `org/slf4j`; no `copy` or `component` function appears.
- The dump at the code-landing commit (321 lines) adds only `RomeFeedParser`'s two public constructors to that list, none on `FeedFetcherConfig` or
  `FeedFetcher`, and still names none of those packages and no `copy` or `component` function.

### P2 — the `js` compile and the dump (each code stage)

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

### P3 — the gate's teeth (stage 4)

With the element cap of `CountingSaxHandler.startElement` disabled (a scratch edit, reverted), the child-JVM cases at `-Xmx128m` with two
concurrent parses gave: `item-flood` exit code 3 (`OutOfMemoryError: Java heap space`) after 1.28 s, `entry-flood` exit code 3 after 1.33 s,
`a-text-flood` exit code 3 after 0.65 s; `a-flood-channel` and `a-flood-item` parsed unbounded (`ok:0` and `ok:1`) instead of
`LimitExceeded:Elements`. With the cap restored every case passes again. The gate fails on the regression it exists to catch.

### The fixed-heap gate's cases

Stage 4; build JDK 23.0.2, `-Xmx128m`, two concurrent parses, wall time including JVM start.

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
assumption (`TestAbortedException`). The JDK 11 run this leaves open is in [`consequences.md`](consequences.md) §8.

### P5 — Level 5 acceptance pass (stage 4, manual)

Run on 2026-11-06 from the build host through `FeedFetcher.untrusted(…)` with a descriptive user agent and the production defaults, by a
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
`BodyTooLarge` when it does not, as the module's limits state. The redirect from `https` to `http` ran against Slashdot: the Planet Python chain the
research measured no longer passes through `http` ([`research/README.md`](research/README.md), "Did not hold in the build"). The IDE checks of plan §9.2
and the KDoc read of P5 (D6) are open in [`consequences.md`](consequences.md) §8.
