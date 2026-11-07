# Memory, CPU and stack cost of a Rome parse of a hostile 2 MiB document, and the fixed-heap gate

## Question

How much memory, CPU time and stack does a Rome 2.1.0 parse take for a hostile (well-formed, DOCTYPE-free) document at the `Untrusted` decoded-body cap of 2 MiB (2,097,152 bytes), at the fetcher's default parse concurrency of 2 (and at 8, the `Untrusted` request concurrency); and how should the Level 4c hardening suite gate it under a fixed heap, in a developer's `./gradlew build`, without the network and without flaking?

Requirements the answer meets: the defaults (2 MiB cap, `maxConcurrentParses` 2, `FeedLimits.maxEntries` 1,000) are what is measured and stated plainly; the build JDK is Oracle JDK 23.0.2 and Gradle 9.7.1; limits the parent design does not describe are recommended plainly with what each adds; a changed default is not proposed here.

Measurement setup (all numbers below are measured unless a finding says otherwise): Oracle JDK 23.0.2 on Windows 11, 32 logical cores; `SyndFeedInput.build(XmlReader(bytes, "application/xml", true))` on Rome 2.1.0 with JDOM 2.0.6.1; each parse thread holds its own 2 MiB `byte[]`; "min heap" is the smallest `-Xmx` (binary search in child JVMs, resolution 2 to 3 percent) at which every parse thread completes with no `OutOfMemoryError`, and it includes the document arrays. Two parses are run at once on two threads that start together.

## Bottom line

- **Memory.** An unguarded Rome parse of a 2 MiB hostile document needs a heap of 54 MB (element flood, `<a/>`) to 168 MB (a flood of empty `<item/>`, 299,000 items) for one parse, 102 to 318 MB for two, and up to 1.2 GB for eight. A realistic 1.4 MB feed needs 7 MB. G1 and Serial agree within about 10 percent, except a single 2 MiB text node at eight parses (G1 102 MB, Serial 61 MB). The defaults therefore hold for memory only if a deployment gives the parse step about 320 MB (two parses) or 1.2 GB (eight).
- **CPU and stack do not hold with the JDK's defaults.** A 2 MiB document nested 299,572 levels deep parses to success but takes 653 s (JDOM's per-add ancestor walk is quadratic in depth, and a parse cannot be cancelled), so two such documents pin both permits for 11 minutes. 10,000 attributes on an element (the JDK's default limit) costs about 5 s and 1.7 GB of allocation per parse. Atom `xhtml` or RSS `<description>` markup nested only 1,800 to 4,400 levels deep throws `StackOverflowError` on a default 1 MB stack (the band depends on JIT state).
- **Three JDK-or-handler limits fix all of it, with no byte pre-scan.** `jdk.xml.maxElementDepth` = 256, `jdk.xml.elementAttributeLimit` = 100, and a counting `SAXHandler` (element count 100,000; distinct namespace URIs 64), all set on a builder of the parser's own. With them every hostile shape above is rejected in 45 to 160 ms and at most 55 MB of allocation, the worst accepted shape needs 43 MB for two parses (141 MB for eight), and the deployment figure drops from about 320 MB to 128 MB (two parses). A realistic feed is unaffected. The limits must be set through a private `SAXParserFactory`: JDOM's shared default factory keeps a limit set on one builder for every later JDOM builder in the process (measured).
- **Gate: a child JVM per case with `-Xmx128m`**, started the way `ChildJvm` starts its child, running the two parses concurrently and reporting each parse's result kind, asserting the exit code and the result kinds plus a generous wall bound (hang detection only, measured maximum 1.15 s). 128 MB is 3x the worst accepted shape and OOMs the unguarded `<item/>` and Atom `<entry/>` floods in 12 of 12 runs, so the gate has teeth. A fixed `maxHeapSize` on `nonfunctionalTest` does not cover `jvmTest` and the `test` alias, which run every level in one 512 MB worker JVM; an in-process allocation bound measures garbage, not heap safety.
- **JDOM2's `Namespace` keeps a static, strong, never-evicted cache.** A flood of distinct `xmlns` URIs leaves about 290 bytes per URI retained for the life of the JVM (44 MB per 2 MiB document), and a hostile server varying the URIs per poll grows it without bound (174 MB after four documents). Counting distinct URIs per document and aborting at 64 stops a flood in 7 to 12 ms and leaves a residual of at most 64 entries per hostile poll. The cap must count distinct URIs, not declarations: a real feed repeated one declaration 507 times.

## Findings

### F1. Memory of an unguarded parse, by shape (measured)

Min heap in MB for all parse threads to complete. Serial GC; G1 in parentheses where measured. Document size in bytes in the first column. Where a cell holds a range, the case was searched twice.

| Shape (document bytes) | 1 parse | 2 parses | 8 parses |
|---|---|---|---|
| Realistic RSS, 400 items with HTML descriptions (1,372,118) | 7 (9) | 12 (13) | 39 (45) |
| Exactly 1,000 items, about 2 KB description each | 9 (9) | 15 (15) | 43 |
| 2 MiB text node, one `<description>` (2,097,152) | 18 (18) | 25 to 30 (31) | 61 to 63 (102) |
| 2 MiB text node of `&amp;` references | 7 | 10 | 28 |
| `<a/>` flood in the channel (2,097,150; 524,253 elements) | 54 (54) | 102 to 103 (103) | 396 (402) |
| `<a/>` flood in one item | 54 | 103 | 360 |
| `<a/>x` mixed text and element flood | 82 | 159 | 600 |
| Nesting in 1,000-level blocks (`<a>` x 1000, repeated) | 37 | 72 | 282 |
| `<item/>` x 299,000 (2,097,148) | 168 (165) | 288 to 312 (318) | 1,200 (1,104) |
| Atom `<entry/>` x 262,000 | 126 (123) | 228 (246) | 876 |
| Items of about 70 bytes each | not run | not run | 171 |
| One attribute per element (`<a a=""/>`) | 49 | 97 | 342 |
| 52 attributes per element | 24 (24) | 43 (43) | 141 |
| 36 elements with 10,000 attributes each (2,083,234) | 21 | 39 | completes at 512 MB; at 48 MB it neither completes nor throws within 150 s (GC thrash) |
| Processing-instruction flood `<?a?>` | 49 | 96 | 288 |
| Comment flood `<!---->` | 7 | 7 | 24 |
| `xmlns` flood, 150,000 distinct URIs | 61 | 90 | completes at 512 MB; thrash at 96 MB |

Amplification (min heap for one parse over document size): `<item/>` flood about 80x, Atom `<entry/>` flood about 60x, `<a/>` flood about 26x, one attribute per element about 24x, a text node about 9x, a realistic feed about 5x. Allocation (thread-allocated bytes, one parse) is much larger than the live set: 180 to 190 MB for the `<a/>` flood, 650 to 850 MB for the `<item/>` flood (30 percent spread between repetitions of the same parse), 100 to 116 MB for the realistic feed, 113 to 129 MB for the text node, and 1.70 GB per parse for the 10,000-attribute document (eight threads, each 5.0 to 5.3 s). Eight 10,000-attribute parses at 512 MB all complete.

The worst memory shape is not an attribute or text shape but the cheapest element that Rome turns into a bean: `<item/>` (or Atom `<entry/>`) repeated to the cap. Rome converts every item before any caller-side entry cap (`FeedLimits.maxEntries`, 1,000) can apply, so the cap does not bound Rome's memory.

Run-to-run spread of the min heap is 5 to 8 percent (the two-parse `<item/>` flood read 288 and 312). Treat these as headroom figures at the edge, not as provisioning figures: near the minimum the collector thrashes rather than failing fast (see Pitfalls).

### F2. Wall time per parse, unguarded (measured)

| Shape | cold (first parse in the JVM) | warm |
|---|---|---|
| Realistic feed | 401 ms | 78 to 114 ms |
| 2 MiB text node | 226 ms | 73 to 92 ms |
| `<a/>` flood | 633 ms | 400 to 441 ms |
| `<item/>` flood | 678 ms | 389 to 464 ms |
| 10,000 attributes per element | 5.0 to 5.3 s (eight at once) | not measured |
| Nesting 299,572 deep (RSS `<a>`, full 2 MiB) | 653 s (one parse, 139 MB allocated, succeeded) | not measured |

Ordinary shapes are cold-start dominated (0.2 to 0.7 s). The two outliers are CPU, not memory. Attribute cost is superlinear in attributes per element: with the 2 MiB document filled with elements of N attributes each, one parse takes (cold, then two warm repetitions) N=100 305, 183, 130 ms and 123 to 134 MB allocated; N=1,000 600, 440, 421 ms and 280 MB; N=2,000 924, 771, 759 ms and 453 MB; N=5,000 1.6, 1.5, 2.0 s and 915 MB; N=10,000 3.7, 4.8, 4.5 s and 1.65 GB.

### F3. Deep nesting is quadratic CPU in JDOM (code reading + measured)

`ContentList.checkPreConditions` calls `child.isAncestor(parent)` for every element added, and `Element.isAncestor` walks the parent chain, so building a tree costs O(depth) per element and O(depth squared) for a chain (JDOM 2.0.6.1, `ContentList.java` and `Element.java`; links under Load-bearing claims). Measured: the full 299,572-level RSS document completed after 653 s with 139 MB allocated; it was not killed by anything. The JDK SAX parser alone takes tens of milliseconds on the same bytes, and Rome's conversion is not involved. A running parse cannot be cancelled by `Thread.interrupt` or coroutine cancellation (it is a pure CPU loop with no check), so the semaphore of `maxConcurrentParses` permits stay held for the duration.

Setting `jdk.xml.maxElementDepth` rejects the same document in 107 ms cold and 8 to 9 ms warm (limit 1,000, `-D` system property through the Rome builder), with `ParsingFeedException` whose cause chain ends in a `SAXParseException` carrying `JAXP00010006`.

### F4. `StackOverflowError`: where it arises and what the threshold is (measured)

Rome calls JDOM's `XMLOutputter` recursively to serialise (a) RSS 0.92+ `<description>` that holds child elements, and (b) Atom 1.0 text constructs of type `xhtml` (or any `/xml`, `+xml` type). The recursion is `AbstractXMLOutputProcessor.printElement` and `printContent` (with `NamespaceStack.push` at the top) and occurs during `WireFeedInput.build`. Foreign elements, channel-level, item-level and entry-level nesting do not overflow.

The overflow depth is not a constant. On a thread with the JVM's default stack (1 MB; `Dispatchers.Default` workers are plain `Thread()` objects with no stack-size argument, so they use the same default), Atom `xhtml` content nested with `<b>` elements (the RSS `<description>` case is within 5 percent):

| JIT state at the parse | deepest OK / first overflow |
|---|---|
| cold JVM (three runs) | 2,437 / 2,476; 2,631 / 2,670; 2,748 / 2,787 (RSS description: 2,865 / 2,904) |
| after 3,000 small parses (C2 compiled) | 4,385 / 4,463 (RSS description: the same) |
| after 3,000 small parses, C1 only (`-XX:TieredStopAtLevel=1`) | 1,813 / 1,832 |
| cold, C1 only | 2,320 / 2,359 |
| interpreter only (`-Xint`) | 3,177 / 3,216 |
| cold, `-Xss512k` | 1,462 / 1,481 |
| cold, `-Xss256k` | 654 / 664 |
| cold, `-Xss8m` | 16,309 / 16,620 (warm: 36,882 / 37,506) |

Interpreted, C1 and C2 frames differ in size, so the threshold moves by a factor of 2.4 within one stack size depending on how much of the parse path has been compiled. Coarse-step samples (OK at 1,750 and overflow at 2,000; OK at 2,000 and overflow at 5,000) are both consistent with this band: the first matches a C1-compiled state, the second a cold or C2 state. A test that nests "a lot more than the boundary" (5,000 or more) overflows in every state; a test that sits at 2,000 passes or fails with JIT timing.

A nesting of 250 levels parses without overflow on a 256 KB stack (cold) and overflows at 128 KB. With a depth limit of 256 (F5) the overflow cannot occur on any thread with 256 KB or more; a `StackOverflowError` catch around the Rome call stays as a backstop for pool threads with a smaller stack.

### F5. JDK limits and a handler remove the CPU and stack exposure (measured)

Through `SAXBuilder.setProperty` on a builder obtained from a `WireFeedInput` subclass (`createSAXBuilder()` is `protected`; Rome's builder is `com.rometools.rome.io.SAXBuilder`, a JDOM `SAXBuilder` subclass, and keeps its DOCTYPE ban when the XML reader factory is replaced afterwards, measured):

- `jdk.xml.maxElementDepth`: rejects a 299,572-level RSS and 5,000-level Atom `xhtml` in about 55 to 110 ms cold (3 ms warm), with `JAXP00010006` ("The element ... has a depth of ... that exceeds the limit ... set by maxElementDepth"). A limit of N allows depth N (the root is depth 1).
- `jdk.xml.elementAttributeLimit`: 100 allows 100 attributes and rejects 101 (`JAXP00010002`). It counts `xmlns` declarations as attributes (200 `xmlns:pN` on one element is rejected at 100; 60 `xmlns:` plus 60 plain attributes is rejected at 100). It rejects the 10,000-attribute document in about 97 ms; the JDK default of 10,000 admits it at 5 s.
- A JDOM `SAXHandlerFactory` returning a `SAXHandler` subclass that counts `startElement` and aborts with `SAXException` past 100,000 elements. The count is exact and consistent with what the parser builds, costs no extra pass, and needs the same builder seam. `jdk.xml.*` has no element-count limit and no namespace-declaration limit.
- The same handler counts distinct namespace URIs in `startPrefixMapping` (a `HashSet` of at most 65 strings) and aborts past 64, before JDOM touches its static cache (F6).

With all of it, parses of the 2 MiB hostile documents give: `<item/>` flood rejected in 158 ms cold and 60 to 76 ms warm with 43 to 55 MB allocated; `<a/>` flood 123 / 45 to 64 ms and 33 to 35 MB; 10,000 attributes 50 / 2 ms and under 4 MB; 299,572-level nesting 54 / 3 ms; a document with 99 or 100 attributes per element and 2,096,000 bytes (the largest accepted attribute shape) parses in 145 to 357 ms with 122 to 131 MB allocated; the realistic feed is unchanged (318 ms cold, 73 to 110 ms warm).

Min heap with these limits (Serial; two to eight parses):

| Shape | 1 | 2 | 8 |
|---|---|---|---|
| `<item/>` flood (rejected) | 12 | 24 | 76 |
| `<a/>` flood (rejected) | 12 | 24 | 73 |
| Atom `<entry/>` flood (rejected) | 12 | 24 | 79 |
| 52 attributes per element (accepted) | 24 | 43 | 144 |
| 2 MiB text node (accepted) | 18 | 30 | 73 |
| Realistic feed (accepted) | 7 | 12 | 37 |

The worst accepted shape is the attribute-heavy document: 43 MB at two parses, 144 MB at eight. The structural caps bring the two-parse worst case from 318 MB to 43 MB, a factor of 7.4, and the eight-parse worst case from 1,200 MB to 144 MB, a factor of 8.3.

### F6. JDOM2's `Namespace` cache is process-wide, strong and never evicted (code reading + measured)

`org.jdom2.Namespace` holds `private static final ConcurrentMap<String, ConcurrentMap<String, Namespace>> namespacemap`, and `SAXHandler.startPrefixMapping` and the attribute path call `Namespace.getNamespace(prefix, uri)` for every declaration (JDOM 2.0.6.1). Nothing removes an entry. Measured on Rome 2.1.0: a 2 MiB document with 150,000 distinct `xmlns` URIs leaves 150,000 entries after the parse; three such documents with fresh URIs leave 450,012 entries; the same URIs again add nothing. Retained heap after a full collection, over a 0.6 MB baseline, was 44.2, 87.2, 132.4 and 174.0 MB after one to four documents with fresh URIs (about 290 bytes per URI, URIs of 8 to 12 characters), and unchanged when the same URIs came again.

Per-document cap, measured: a handler counting distinct URIs and aborting at 64 rejects the flood in 7 to 12 ms and adds 64 entries per hostile document; the same document with 150,000 repeats of one URI is accepted and adds one entry. A local sample of 61 feed files (provenance not verified) has at most 11 distinct namespace URIs per document but one file has 507 declaration occurrences, so a cap on occurrences would reject a real feed.

Prior reports: no JDOM issue (the 100 most recent of the project's issues were searched by title) reports unbounded growth of the JDOM2 cache. JDOM 1's mailing list raised the same shape in 2000 ("couldn't the hashmap eventually get really large?", "There is no way to remove a namespace or mapping"). The cap bounds the rate; it does not remove the leak (see Gaps).

### F7. JDK parser limits set through a shared factory are process-wide (measured + code reading)

A limit set on one parser through `XMLReader.setProperty` or `SAXParser.setProperty` persists in the `SAXParserFactory` that created it: with one factory, a parser created with limit 100 and then a parser created with no setting both enforce 100; a later parser given 5,000 makes the next unset parser enforce 5,000. A new factory is clean. The JDK's `SAXParserImpl` takes `fSecurityManager = spf.fSecurityManager`, the factory's own object, and `setProperty` mutates it. JDOM's `XMLReaders.NONVALIDATING` hands out one shared `SAXParserFactory` (`NONSingleton`), and Rome's `WireFeedInput.createSAXBuilder()` builds `new SAXBuilder(XMLReaders.NONVALIDATING)`. So setting `jdk.xml.maxElementDepth` on Rome's default builder mutates a process-wide object: the limit then applies to every JDOM `SAXBuilder` of the host application (last writer wins, races included), and another component's `setProperty(..., larger)` silently lifts ours.

The isolation works and is small: a `WireFeedInput` subclass whose `createSAXBuilder()` calls `super`, then `setXMLReaderFactory` with a private factory (an `XMLReaderJDOMFactory` holding its own `SAXParserFactory.newDefaultInstance()`, namespace-aware), then sets the properties and the handler factory. Measured: with the limits on the private factory, the default Rome input and a plain JDOM `SAXBuilder` in the same JVM keep parsing a 200-attribute element; the limited input rejects it; a DOCTYPE through the limited input is still refused by Rome's feature (`disallow-doctype-decl`). `SAXParserFactory.newDefaultInstance()` (since Java 9, inside the Java 11 floor) returns the JDK's built-in implementation, which also keeps `jdk.xml.*` meaningful if another SAX provider is on the consumer's class path.

### F8. The test worker has 512 MB, and the fixed-heap gate options (measured + documented)

A Gradle 9.7.1 `Test` task of a plain Java project, run offline in a scratch project, started its worker with `-Xmx512m` and `Runtime.maxMemory()` of 512 MB, with no `maxHeapSize` configured. The documentation of 9.7.1 says the same: the `Test` task's `maxHeapSize` is "The maximum heap size for the process, if any." with default `null`, and the upgrade notes for 5.0 say "All workers, including compilers and test executors, now start with 512MB of heap." The repository sets no `maxHeapSize` or `jvmArgs` on any test task. `jvmTest` and the lifecycle `test` alias (`webtools.kotlin-multiplatform-library.gradle.kts`) run every level in that one worker JVM; the level tasks of `registerLevelTasks` are separate `Test` tasks over the same classes, each with its own worker.

Options, evaluated against the measurements:

| Option | Verdict |
|---|---|
| Child JVM per case, `-Xmx` fixed, result by exit code and a line per parse | Fixes the heap whichever task runs the class; isolates an out-of-memory or hang from the worker; costs about 0.4 s of JVM start (measured in `loopback-test-mechanics.md` F6) plus the parse, 0.3 to 1.15 s per case in the repeat runs below. Recommended. |
| `maxHeapSize` on `nonfunctionalTest` | Fixes the heap for that one task only. `jvmTest` and `test` still run the class in a 512 MB worker with every other test, so the same class passes or fails by the task that ran it; setting 128m on `jvmTest` starves the rest of the suite. Rejected as the gate; useful only as a belt on top. |
| In-process bound on `ThreadMXBean.getThreadAllocatedBytes` | The allocation of one parse varies by 30 percent between repetitions (`<item/>` flood: 652, 800, 846 MB) and is mostly garbage (10,000-attribute document: 1.70 GB allocated, 39 MB live); it neither proves nor predicts heap safety. Rejected. |
| In-process `MemoryPoolMXBean` peak usage | Peaks include garbage and depend on collection timing; not deterministic. Rejected. |
| A hard assertion that a hostile case is slower than N | Not used: cold start dominates (0.2 to 0.7 s) and varies 2x with load. A wall bound is kept only for hang detection. |

### F9. The child-JVM gate is stable under load (measured)

Two concurrent parses of ten hostile shapes (`<item/>`, `<a/>`, 52-attribute, 99-attribute, Atom `<entry/>`, text node, full-depth RSS, 10,000-attribute, `xmlns` flood, `<a/>x`) with the F5 limits at `-Xmx128m`, ten runs per shape under each of G1 and Serial, six child JVMs at a time: 200 of 200 runs completed with no `OutOfMemoryError`, the same result kinds every time (accepted shapes `OK`, rejected shapes a `ParsingFeedException`), and a wall time of 316 ms to 1,149 ms per child including JVM start. `-XX:+ExitOnOutOfMemoryError` makes a heap exhaustion in any thread terminate the child with exit code 3 (measured on 23.0.2 with G1 and with Serial).

The negative control: the unguarded parse (no limits) of the `<item/>` flood and of the Atom `<entry/>` flood, two parses, at `-Xmx128m`: exit code 3 in 12 of 12 runs (three each, G1 and Serial). The gate has teeth against the exact regression it exists to catch.

### F10. A byte-level structural pre-scan is cheap but is not needed (measured + reasoned)

A single-pass scanner over the raw bytes tracking depth, element count and attributes per tag (comments, CDATA and processing instructions skipped, quotes honoured) took 5 to 9 ms cold and 1.0 to 1.5 ms warm on a 2 MiB document, and measured depth 299,572, 524,253 elements and 10,000 attributes correctly. It is a safe optimisation but not a boundary: a scanner and a parser can disagree about a malformed document, and where the scanner undercounts the parse is exactly as expensive as without the scan (F1). The handler of F5 counts what the parser actually builds, without a second pass, in the same seam as the namespace cap. The pre-scan is only needed if Rome's builder cannot be replaced.

## How comparable projects do it

- **Woodstox** (the StAX parser behind most JVM XML stacks) ships structural limits on by default: `ReaderConfig.DEFAULT_MAX_ELEMENT_DEPTH = 1000` and `DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT = 1000`, with `P_MAX_ELEMENT_COUNT`, `P_MAX_CHILDREN_PER_ELEMENT`, `P_MAX_ATTRIBUTE_SIZE` and `P_MAX_TEXT_LENGTH` available (`woodstox-core-7.1.0`, `com.ctc.wstx.api.ReaderConfig`, `WstxInputProperties`). Established practice that depth and attributes per element are bounded by the parser; element count is an opt-in there too.
- **Jackson** bounds JSON nesting at 1,000 by default (`StreamReadConstraints.DEFAULT_MAX_DEPTH = 1000`, jackson-core 2.18.2), as part of its guard against malicious input.
- **JAXP** itself provides `jdk.xml.maxElementDepth` (default 0, unlimited), `jdk.xml.elementAttributeLimit` (default 10,000) and entity limits (JDK 23 `java.xml` module summary); the depth default is the reason the CPU and stack exposure exists on the JDK parser. The JDK's attribute limit counts `xmlns` declarations (F5).
- **Rome** documents no limits; a maintainer answers a question about memory with large feeds (a user reports 1 GB inputs needing 14 GB machines) with "Rome has as main feature JDOM implementation, then it is obviously that needs a lot of heap memory size to execute using large RSS files" (Rome issue 629, 2023). The project's answer to large input is more heap.
- **Apache Tika** treats parse memory and runtime as a process-isolation problem: "Process isolation is the primary defense: a parser that OOMs, loops forever, crashes native code, or exhausts a file-descriptor pool takes down a forked JVM (a fork), not your application." That is the production analogue of the test gate's child JVM.
- **Within this repository**, `ChildJvm` in `webtools-http` is the precedent for the launcher: `java.home` for the `java` binary, `java.class.path` for the class path, a main class in `jvmTest`, `waitFor` with a budget, and `destroyForcibly` on timeout (the output is discarded there; the gate needs the child's result lines, so it redirects them to a temporary file).

## Pitfalls

- **Near-minimum heaps do not fail, they thrash.** At 48 MB (10,000 attributes, eight parses) and at 96 MB (`xmlns` flood, eight parses) the child neither threw nor finished in 150 s, while at 512 MB the same eight parses finished in 5 s and 1.2 s. A gate heap close to the minimum turns a regression into a hang that only the wall bound catches, and a hang is flaky on a loaded machine. Gate with 3x headroom.
- **Min heaps differ between collectors for humongous objects.** The 2 MiB text node at eight parses needs 102 MB under G1 and 61 MB under Serial; build agents without many cores run Serial, others G1. The gate heap must hold for both (the repeat runs above use both).
- **A limit set through the shared JDOM factory leaks to the whole JVM** (F7). Any recommendation to set `jdk.xml.*` on Rome's default builder is wrong for a library that lives in a host application.
- **The JDOM namespace cache is an unbounded leak by design** (F6); a per-document cap bounds the rate only.
- **The stack threshold is not a constant** (F4); a test at 2,000 levels is flaky, a test at 5,000 or more is not.
- **A parse is not cancellable.** Do not rely on a timeout or coroutine cancellation for a runaway parse; the depth, attribute and element limits are what bound it. A `withTimeout` around the parse releases the caller and leaves the thread, and its permit, spinning.
- **Asserting allocation or elapsed time is flaky** (F8); assert exit codes and result kinds.
- **Counting namespace declarations instead of distinct URIs rejects real feeds** (one sample file repeats a declaration 507 times).
- **The Gradle worker's 512 MB is shared.** A gate that parses in-process shares a worker heap with the entire suite; two unguarded `<item/>` parses need 288 to 318 MB of it.

## Recommendation for this repository

**Limits to ship in `RomeFeedParser`.** The defaults (2 MiB, two parses, 1,000 entries) hold for memory only with about 320 MB of parse headroom, and do not hold for CPU (653 s, 5 s) or stack (overflow at 1,800 to 4,400 levels). Recommend, on a `WireFeedInput` subclass that replaces the reader factory with a private one:

1. `jdk.xml.maxElementDepth` = 256. Adds one structural limit (reported as `LimitExceeded`, detected as a `SAXParseException` message starting `JAXP00010006` in the cause chain); a 61-file local sample of real feeds nests at most 6 levels; the margin to the stack threshold is 2.5x on a 256 KB stack.
2. `jdk.xml.elementAttributeLimit` = 100 (`JAXP00010002`). Real elements carry at most about 10 attributes (sample max 5); `xmlns` declarations count toward it.
3. A counting `SAXHandler` (through `SAXBuilder.setSAXHandlerFactory`): element cap 100,000 and distinct namespace URIs 64. The element cap is the single measure that removes the worst memory shapes (318 MB to 24 MB at two parses): it adds one limit; 100,000 is 1.7x the densest realistic 2 MiB document extrapolated from the sample (27.7 elements per KB gives 58,000). The namespace cap is the only mitigation for the static cache; a real feed has under a dozen distinct URIs.
4. A private `SAXParserFactory.newDefaultInstance()` behind an `XMLReaderJDOMFactory` (about ten lines) so limits 1 and 2 stay inside the parser.
5. A `StackOverflowError` catch around the Rome call, mapped to `LimitExceeded(depth)`, as a backstop for threads with stacks under 256 KB.

No byte-level pre-scan is needed: items 1 to 4 are exact where a pre-scan can only approximate (F10), and the prolog scan stays the module's DOCTYPE lock. The four structural numbers are constants of `RomeFeedParser`, or tightening-only members of `FeedLimits` if the configurable surface is wanted; either reuses the existing `LimitExceeded(limit)` leaf. If the supplied parser were to call `SyndFeedInput.build(XmlReader)` unchanged (no builder hook), none of this is available without process-wide system properties and the deployment figures are the unguarded ones.

**Deployment heap for the defaults.** With the limits: the parse step peaks at 43 MB for two parses (worst accepted shape; realistic feeds 12 MB), 144 MB for eight; provision about 128 MB of headroom beyond the application's live data at two parses. Without them: 288 to 318 MB at two parses and 1.2 GB at eight; provision about 640 MB at two. Headroom scales with the number of concurrent parses in the process: if each `FeedFetcher` instance owns its semaphore, two fetchers is four parses.

**The gate.**

- **Mechanism.** A child JVM per case, a `HostileParseJvm` launcher in `jvmTest` support beside the others (`com.spartanlabs.testing.support.webtools.feeds`), shaped like `ChildJvm`: `Path.of(java.home, "bin", "java")`, `-Xmx128m`, `-XX:+ExitOnOutOfMemoryError`, `-cp java.class.path`, a main class `HostileParseMain` in `jvmTest`, arguments case name and parse count. The child generates the document itself (so no 2 MiB array crosses a process boundary and the worker heap is not touched), runs the parses concurrently through the module's real parse path (the fetcher's dispatcher and semaphore with a fake `UrlFetcher`, or `RomeFeedParser` on two threads when the fetcher is not under test), and prints one line per parse: `ok`, or the `FeedFailure` leaf and its limit. Exit code 0 means every parse returned a value; 3 is the JVM's out-of-memory exit; the child exits 11 on an escaped `StackOverflowError` and 12 on any other throwable. Output goes to a temporary file read after exit (small, never an unread pipe). The parent waits 30 s per case and calls `destroyForcibly` on timeout, as `ChildJvm` does.
- **Kotlin shape.**

```kotlin
internal object HostileParseJvm {
    /** @property exitCode `null` when the child outlived the budget and was destroyed. */
    class Outcome(val exitCode: Int?, val elapsed: Duration, val lines: List<String>)

    fun run(case: String, parses: Int, heapMegabytes: Int = 128, budget: Duration = 30.seconds): Outcome {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val out = Files.createTempFile("hostile-parse", ".txt")
        try {
            val process = ProcessBuilder(
                java, "-Xmx${heapMegabytes}m", "-XX:+ExitOnOutOfMemoryError",
                "-cp", System.getProperty("java.class.path"), HostileParseMain::class.java.name, case, parses.toString(),
            ).redirectErrorStream(true).redirectOutput(out.toFile()).start()
            val mark = TimeSource.Monotonic.markNow()
            val exited = process.waitFor(budget.inWholeSeconds, TimeUnit.SECONDS)
            if (!exited) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
            return Outcome(if (exited) process.exitValue() else null, mark.elapsedNow(), Files.readAllLines(out))
        } finally { Files.deleteIfExists(out) }
    }
}
```

  The document generator is an `object HostileDocuments` of pure functions returning `ByteArray` (a fixed-capacity writer: head, repeated unit while it fits, tail), one function per shape; it lives in `jvmTest` support because only the child and the contract classes use it.
- **Heap.** `-Xmx128m` for the two-parse cases: 3x the worst accepted shape (43 MB), under the unguarded two-parse minimum for the `<item/>` flood (288 to 318 MB) and the Atom `<entry/>` flood (228 to 246 MB), so removing the element cap fails the gate with exit code 3. An optional eight-parse case at `-Xmx256m` (worst accepted 144 MB, 1.8x).
- **Cases (two concurrent parses each), expected results.** `<a/>` flood in the channel, `<a/>` flood in an item, `<item/>` flood, Atom `<entry/>` flood, `<a/>x` flood: `LimitExceeded(elements)`. 10,000 attributes on each of 36 elements: `LimitExceeded(attributes)`; one document with exactly 100 attributes per element (accepted) and one with 101 (rejected) as the boundary pair. RSS nested to the cap, Atom `xhtml` nested 5,000 deep, RSS `<description>` nested 5,000 deep: `LimitExceeded(depth)`. 150,000 distinct `xmlns` URIs: `LimitExceeded(namespaces)`; 150,000 repeats of one URI: accepted. 2 MiB text node: accepted, text truncated by `FeedLimits` (the truncation is the model's rule, not this gate's). Realistic control (400 items): accepted with 400 entries. Exactly 1,000 items: accepted with 1,000. 1,001 items: `LimitExceeded(entries)`.
- **Assertions.** Exit code 0; every line is the expected result kind; elapsed under 30 s (measured 0.3 to 1.15 s per child under six-way load; the bound detects hangs and is not a performance claim). No assertion on allocated bytes, peak usage or fine-grained time.
- **Run time.** About 12 to 15 children at 0.4 to 1.2 s each, sequential: 10 to 18 s in total, in `testing.nonfunctional`, so `nonfunctionalTest`, `jvmTest` and the `test` alias all run it identically because the heap belongs to the child, not the worker.
- **Rejected.** `maxHeapSize` on `nonfunctionalTest` (F8: leaves `jvmTest` unguarded), an in-process allocation bound (F8), and a timing assertion.

## Load-bearing claims

1. **A Gradle `Test` task's `maxHeapSize` defaults to `null`.** "The maximum heap size for the process, if any." Gradle 9.7.1, https://docs.gradle.org/9.7.1/dsl/org.gradle.api.tasks.testing.Test.html (the default is shown as `null`).
2. **Gradle test workers start with 512 MB.** "All workers, including compilers and test executors, now start with 512MB of heap." Gradle 9.7.1 documentation, upgrade guide, heading "[5.0] Default memory settings changed", https://docs.gradle.org/9.7.1/userguide/upgrading_version_4.html (also measured in a 9.7.1 scratch project: `-Xmx512m`).
3. **JDK 23 limits and defaults.** `jdk.xml.maxElementDepth`: "Limits the maximum element depth." default 0; `jdk.xml.elementAttributeLimit`: "Limits the number of attributes an element can have." default 10000. JDK 23 `java.xml` module summary, https://docs.oracle.com/en/java/javase/23/docs/api/java.xml/module-summary.html.
4. **`SAXParserFactory.newDefaultInstance()` exists since 9.** "Creates a new instance of the `SAXParserFactory` builtin system-default implementation." Since: 9. JDK 23 API, https://docs.oracle.com/en/java/javase/23/docs/api/java.xml/javax/xml/parsers/SAXParserFactory.html.
5. **A JDK SAX parser shares its factory's security manager.** `fSecurityManager = spf.fSecurityManager;` in `SAXParserImpl`, OpenJDK `jdk-23+37`, https://github.com/openjdk/jdk/blob/jdk-23%2B37/src/java.xml/share/classes/com/sun/org/apache/xerces/internal/jaxp/SAXParserImpl.java (reproduced on 23.0.2: the limit persists across parsers of one factory).
6. **JDOM's non-validating reader factory is one shared `SAXParserFactory`.** `private enum NONSingleton implements FactorySupplier {` ... `private final SAXParserFactory factory;` in `XMLReaders`, JDOM 2.0.6.1, https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/input/sax/XMLReaders.java.
7. **Rome's default builder uses that shared factory.** `saxBuilder = new SAXBuilder(XMLReaders.NONVALIDATING);` in `WireFeedInput.createSAXBuilder`, Rome 2.1.0, https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/WireFeedInput.java.
8. **JDOM's namespace cache is static and never evicted.** `namespacemap = new ConcurrentHashMap` (a `private static final ConcurrentMap<String, ConcurrentMap<String, Namespace>>`) in `Namespace`, and `final Namespace ns = Namespace.getNamespace(prefix, uri);` in `SAXHandler.startPrefixMapping`, JDOM 2.0.6.1, https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/Namespace.java and https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/input/sax/SAXHandler.java.
9. **JDOM checks ancestry on every add.** "Detect if we have <a><b><c/></b></a> and c.add(a)" followed by `((Element) child).isAncestor((Element) parent)` in `ContentList.checkPreConditions` (https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/ContentList.java), and `Element.isAncestor` walks `p = p.getParent()` (https://github.com/hunterhacker/jdom/blob/JDOM-2.0.6.1/core/src/java/org/jdom2/Element.java), JDOM 2.0.6.1.
10. **`Dispatchers.Default` workers use the JVM default stack.** `internal inner class Worker private constructor() : Thread() {` in `CoroutineScheduler`, kotlinx.coroutines 1.10.2 (the version `webtools-http` pins), https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/scheduling/CoroutineScheduler.kt. The default stack: "Linux/x64: 1024 KB" and for Windows "The default value depends on virtual memory", JDK 23 `java` tool page, https://docs.oracle.com/en/java/javase/23/docs/specs/man/java.html (`-Xss`).
11. **Woodstox defaults.** `public final static int DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT = 1000;` and `public final static int DEFAULT_MAX_ELEMENT_DEPTH = 1000;`, `woodstox-core-7.1.0`, https://github.com/FasterXML/woodstox/blob/woodstox-core-7.1.0/src/main/java/com/ctc/wstx/api/ReaderConfig.java.
12. **Jackson default nesting limit.** `public static final int DEFAULT_MAX_DEPTH = 1000;` in `StreamReadConstraints`, jackson-core 2.18.2, https://github.com/FasterXML/jackson-core/blob/jackson-core-2.18.2/src/main/java/com/fasterxml/jackson/core/StreamReadConstraints.java.
13. **Tika's position on parse isolation.** "Process isolation is the primary defense: a parser that OOMs, loops forever, crashes native code, or exhausts a file-descriptor pool takes down a forked JVM (a **fork**), not your application." Apache Tika 4.0.x, https://tika.apache.org/docs/4.0.x/advanced/robustness.html.
14. **Rome is a DOM build and large inputs need heap.** "Rome has as main feature JDOM implementation, then it is obviously that needs a lot of heap memory size to execute using large RSS files." Rome issue 629, February 2023, https://github.com/rometools/rome/issues/629.
15. **An older precedent for the unbounded namespace map (JDOM 1).** "couldn't the hashmap eventually get really large?" and "There is no way to remove a namespace or mapping", JDOM-interest mailing list, 25 September 2000, https://jdom.org/pipermail/jdom-interest/2000-September/002293.html.

## Gaps

- **What was measured is Rome's `SyndFeedInput`/`WireFeedInput` build, not `RomeFeedParser`.** The module's conversion to `Feed` (bounded by `FeedLimits`: 1,000 entries, text, id, link caps) and the transport's own buffering of a 2 MiB body (the `byte[]` is included here, but any decode copy is not) are not in the figures. They add per-parse memory of the order of a few MB; the gate, run through the real path, closes this.
- **The four limit values are judgement.** Depth 256, 100 attributes, 100,000 elements and 64 namespaces rest on a local sample of 61 feed files of unverified provenance (maxima: depth 6, 5 attributes per element, 11 distinct namespace URIs, 54,475 elements in the largest file, up to 27.7 elements per KB) and on Woodstox's 1,000/1,000 defaults. They are not tested against a representative corpus; the element cap in particular could reject a legitimate 2 MiB document of unusually dense markup (above about 50 elements per KB).
- **The namespace cap bounds the leak rate, not the leak.** A server that sends 64 fresh URIs per poll still adds up to 64 entries (about 12 KB) per poll for the life of the JVM. Nothing in JDOM 2.0.6.1 evicts or bounds the map; no JDOM issue or mailing-list report of this for JDOM2 was found (only the 2000 JDOM 1 post). A process-wide allow-list of URIs would close it and was not measured.
- **Eight-parse unguarded 10,000-attribute and `xmlns` flood minimum heaps** were not found (thrash below 48 MB and 96 MB; both complete at 512 MB). The minimum heap of the full-depth nested document was not searched (139 MB allocated, completed in 653 s at 1 GB).
- **Platform.** All numbers are Windows, JDK 23.0.2. The default thread stack on Linux x64 is documented as 1024 KB; the stack thresholds and the Windows default (not stated in the JDK page) were measured only here, and JIT-state dependence means a Linux CI agent will show different thresholds inside the same band logic. The gate's headroom (3x) and the limit of 256 do not depend on them.
- **Gradle default heap** was measured on a plain Java project's `Test` task; `jvmTest` of the Kotlin Multiplatform plugin is a `Test` subclass (not run in this repository, as no build was allowed), so 512 MB is expected, not observed, for it.
- **`-XX:+ExitOnOutOfMemoryError` exit code 3** is measured on 23.0.2; the option was not found in the fetched pages of the JDK 23 `java` man page, so there is no documentation quote for it. A child that catches `OutOfMemoryError` in its own worker threads and exits with its own code is the documented-behaviour alternative.
- **A non-JDK SAX provider on the class path** was not probed; `SAXParserFactory.newDefaultInstance()` is used to make the answer independent of it, but the combination with Rome's feature calls was not run with a second provider present.
- **Concurrent first use** (two threads building the first private factory at once) and `Thread` pools with a custom `ThreadFactory` stack size were not probed.
- **Whether each `FeedFetcher` instance owns its own semaphore** is a design point outside these findings; the deployment figure scales with total concurrent parses in the process.
