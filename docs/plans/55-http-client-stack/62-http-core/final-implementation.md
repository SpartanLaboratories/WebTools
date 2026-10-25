# Final implementation: `http-core` (Issue #62)

The as-built record of `SpartanLaboratories/WebTools#62`, unit 3 (`http-core`) of
[#55's architecture](../architecture.md). It states what was built, where the work departs from
[`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for
the repository and for the sibling units is in [`impact.md`](impact.md).

- **Status:** built through stage S7 of the plan (§5), with qa pass 1's 27 minor fixes applied (§5).
  This is the final as-built record.
- **Branch and PR:** `feat/issue-62-http-core`, cut from `master` at `f72b115`, above the research
  commits; PR TBD (it closes #62).
- **Host of the measurements:** the maintainer's Windows 11 host; build JDK 23; Gradle 9.7.1; Kotlin Gradle
  plugin 2.2.0; `core.autocrlf=true`.
- **Versions moved:** none. `webtools-http` 1.0.0 is new, inherited from the root default, and stays
  unreleased until #55's stack is complete. It is not a release candidate at this landing.

## 1. What was built

Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.

- **`build-logic`:** the precompiled script plugin `webtools.kotlin-common-check` (multiplatform plugin,
  `mavenCentral()`, `js()` with no environment, `check` → `compileKotlinJs`); `SampleProject.commonCheck`
  with its four constants and four templates; `CommonCheckPluginIntegrationTest` (Level 3, seven tests);
  `CommonCheckConfigurationCacheReuseNonFunctionalTest` (Level 4c); the fourth id in
  `BuildLogicPluginsGatingTest`. `build-logic/build.gradle.kts` unchanged.
- **The build:** `settings.gradle.kts` includes `:webtools-http` and `:webtools-http-commoncheck`; the root
  `build.gradle.kts` header comment names the four artifacts and the four plugins; the root project gained
  no plugin, extension or task.
- **`webtools-http`** (Kotlin Multiplatform on `webtools.kotlin-multiplatform-library`, `explicitApi()`,
  the opt-in as a language setting of `commonMain` and `jvmMain`, `kotlinx-coroutines-core` 1.10.2 `api`,
  `okhttp-jvm` 5.5.0, `caffeine` 3.3.1 and `slf4j-api` 2.0.13 `implementation` in `jvmMain`, coordinates
  and POM): in `commonMain`, one flat package `com.spartanlabs.webtools.http` holding the marker
  `ExperimentalForInheritanceWebToolsApi`; the gated ports `HttpTransport` and `WallClock` with
  `WallClock.System`; the failure model `HttpFailure` (`Closed`, `BudgetExhausted`, `DecodeFailure`),
  `TransportFailure` (eleven leaves) and `UpstreamFailure` (`RateLimited` with an `internal` constructor,
  `UpstreamError`); `RefusalReason` (nine constants) and `ExhaustedReason` (four); `HttpMethod`,
  `HttpHeaders` (`Entry`, `Builder`, the factory), `RequestBody`, `HttpRequest` and `HttpResponse` (each
  with a `Builder` and a factory), `RedirectHop`; `Url` with `UrlProblem`, `UrlParseException`,
  `urlFailure`, the parse pipeline (`UrlParsing.kt`), host canonicalisation and IPv6 (`UrlHost.kt`), the
  path and query canonicalisers and the two `internal` encoders (`UrlEncoding.kt`); the `internal` `Log`
  seam with `expect fun logger`, `expect fun epochMillisNow`, `HttpSyntax`, `flatMap` and `catchNonFatal`.
  In `jvmMain`, `Log.jvm.kt` (slf4j) and `EpochMillis.jvm.kt`. Every public declaration carries KDoc
  naming its tier and contract (the P9 coverage check is empty).
- **`webtools-http-commoncheck`:** the build file (the plugin, the sibling's `commonMain` as a relative
  `srcDir`, the opt-in on `commonMain` and `jsMain`, `kotlinx-coroutines-core`) and `JsActuals.kt` (a
  no-op `Log`, `Date.now()`).
- **Tests of `webtools-http`** (131 on the JVM, all green): Level 1 the two permanent consumer classes
  (5 tests); Level 2 fourteen classes (69); Level 4a sixteen classes (48), among them the three `jvmTest`
  differentials against OkHttp 5.5.0 (`Url` versus `HttpUrl` over the corpus and 200,000 generated inputs
  with the coverage floors, the encoders versus `HttpUrl.Builder`, the header rules versus
  `Request.Builder`); Level 4c four classes (9). Test support: `UrlCorpus` (34 accepted, about 110
  rejected, about 55 confusion, 6 crash inputs) and `UrlInputGenerator`. `logback-test.xml` is the fourth
  byte-identical copy. No Level 3 or 4b test in the module (plan §9); Level 5 is §6.
- **`webtools-http/api/webtools-http.api`:** the ABI dump, 294 lines, read against plan §7.8 (§5, P6).
- **README:** the nine edits of plan §8, plus one more (§2, deviation 1).

## 2. Deviations from the plan

Three. None changes a rule of the design, a signature, a message or a name. Plan Amendment 1 (the
media-type test row) and Amendment 2 (two test-scope positions in §9) are part of the plan and were
followed.

1. **The README has one edit beyond plan §8.** The plan's `readme` row is amended.
   - **Why.** The command block's `verifyJavaFloor` comment said "the Java 11 floor of every module's
     jars". `webtools-http-commoncheck` is a module with no JVM jar, and `verifyJavaFloor` does not
     check it, so the sentence became inaccurate. Acceptance criterion 8 ("no sentence the module makes
     false remains") rules that out.
   - **The fix.** The comment now reads "the Java 11 floor of every published module's jars, …".
   - **Why it was missed.** Plan §8 lists the other sentences the module makes false, but not this one.
   - **Left as is:** line 60 ("whichever of the three modules you actually use"). It is the migration
     note for the retired combined artifact, whose successors are exactly those three modules.
2. **`HttpTransport`'s contract has an eighth point.** The plan's `transport-port` row is amended.
   - **The point:** "`execute` is main-safe: an implementation moves blocking I/O off the caller's
     thread and documents which dispatcher it uses."
   - **Why.** Plan §6.6 lists seven points. A `suspend` function that blocks its caller's thread breaks
     the convention every coroutine caller relies on. The implementations to come (the OkHttp
     transport, the fixtures' fake) therefore state where their work runs.
   - **Scope.** KDoc only; no signature changes.
3. **`IncludedBuildEndToEndTest`'s KDoc has one more sentence.** Plan §7.2 does not list that file.
   - **The sentence:** the `webtools.kotlin-common-check` plugin is not part of the composite, and is
     covered at Level 3 by `CommonCheckPluginIntegrationTest`.
   - **Why.** It states in the test the position that plan Amendment 2 records.

**Mechanical fixes** change no rule and are listed where they happened, in §5:
- a compile error;
- a test expectation that contradicted §6.5's order;
- a nonsense assertion, and an illegal JVM method name;
- a string-template boundary;
- one redundant assertion that produced a compiler warning;
- `UrlCorpus` as an `internal object`;
- the rendering of `\uXXXX` escapes in the test sources.

## 3. The compile proof

Procedure P3 of [`plan.md`](plan.md) §9.2, run in the real module after the S2 files existed and before
S3, on the maintainer's Windows host (build JDK 23, Kotlin Gradle plugin 2.2.0, Gradle 9.7.1). Every
step gave the result the plan predicts; no deviation. File paths below are repository-relative (the
compiler prints them as absolute `file:///` URLs). The class-file and dump readings (P6) follow in S5.

### Step 1 — main compiles

`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` →
`BUILD SUCCESSFUL in 8s`, both tasks executed, no gate diagnostic. The only warnings: at
`Configure project :webtools-http-commoncheck`, `w: JS Environment Not Selected` (the Kotlin Gradle
plugin's text, with its `browser()`/`nodejs()` suggestion and "will be an error in the future releases"),
and Gradle's "Deprecated Gradle features were used in this build, making it incompatible with Gradle 10"
(the `archives` deprecation). The `commonMain {}`, `jvmMain {}` and `jsMain {}` accessors resolved in both
module build files, so the `getByName` fallback of plan §3 was not needed.

### Step 2 — the wiring canary

Three throwaway top-level implementers were added, exactly as the plan writes them: `ProofClock.kt`
(`commonMain`, `: WallClock`), `ProofTransport.kt` (`jvmMain`, `: HttpTransport`) and `ProofJsClock.kt`
(the check module's `jsMain`, `: WallClock`).

- **2a, with the wiring:** the same two compile tasks → `BUILD SUCCESSFUL in 2s`.
- **2b, without the wiring:** the four `languageSettings.optIn(gateMarker)` lines removed (two per build
  file; scratch copies kept), then
  `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs --continue` →
  `BUILD FAILED in 3s`, both tasks `FAILED`, exactly two errors each, in-module text **carrying the
  marker's message**:

  ```text
  > Task :webtools-http-commoncheck:compileKotlinJs FAILED
  e: webtools-http-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/http/ProofJsClock.kt:3:29 This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
  e: webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ProofClock.kt:3:27 This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
  > Task :webtools-http:compileKotlinJvm FAILED
  e: webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ProofClock.kt:3:27 This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
  e: webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/ProofTransport.kt:3:31 This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
  ```

  Each error sits at the supertype reference (column 27/29/31 is the start of `WallClock` /
  `HttpTransport` after the `:`). The in-module text is the marker's literal, not the compiler's default,
  so research F10 holds and no deviation is recorded.
- **2c, restored:** both build files copied back (`diff` against the scratch copies empty), the three
  `Proof*` files deleted (`git status` shows none), the two compile tasks → `BUILD SUCCESSFUL in 3s`.

### Step 3 — the positive consumer cases

- `./gradlew :webtools-http:compileTestKotlinJvm` → `BUILD SUCCESSFUL in 7s`; no `e:` line, no gate
  warning, no compiler `w:` (the only `w:` is the configuration-time `JS Environment Not Selected`).
  `commonTest` and `jvmTest` compile without any opt-in while naming the gated types in signatures, using
  `WallClock.System` as a default and constructing every public leaf.
- `./gradlew :webtools-http:gatingTest` → `BUILD SUCCESSFUL in 2s`;
  `GatedTypesConsumerGatingTest` 3 tests and `GatedTypesJvmConsumerGatingTest` 2 tests, 0 failures.

### Step 4 — the eight negative cases

`NegativeCases.kt` (the eight declarations of plan §9.2 P3.4, `import com.spartanlabs.webtools.http.*`)
was placed in `commonTest` under `com.spartanlabs.testing.gating.webtools.http`, then in `jvmTest`.

- **`commonTest`, no opt-in:** `./gradlew :webtools-http:compileTestKotlinJvm` → exit 1, **exactly 8**
  `e:` lines (`grep -c '^e: '`), no other diagnostic, each at the supertype reference
  (`NegativeCases.kt:5:17`, `6:36`, `7:19`, `8:19`, `9:21`, `10:20`, `11:19`, `12:21`, one per
  declaration N1..N8), each with the consumer text of plan §6.2:

  ```text
  This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
  ```

- **`commonTest`, every declaration prefixed `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`:**
  exit 0, 0 `e:`, 0 compiler `w:`.
- **`jvmTest`, no opt-in:** exit 1, exactly 8 `e:` lines, the same positions and the same text.
- **`jvmTest`, with `@OptIn`:** exit 0, 0 `e:`, 0 compiler `w:`.
- The file was deleted after each pass; `git status` shows no `NegativeCases`.

### Step 5 — verdict

The proof's every result equals the plan's expectation (§6.2, §9.2 P3). S3 may begin.

## 4. Acceptance

Against plan §1 "Acceptance", from the measurements in §5:

1. `:webtools-http:check` passes: `jvmTest` 131 tests at every level (130 before qa pass 1 added one),
   `apiCheck`, `verifyJavaFloor` — **met**.
2. `:webtools-http-commoncheck:check` passes; `commonMain` compiles for `js` — **met**.
3. `:build-logic:test`, `:build-logic:integrationTest` (46), `:build-logic:e2eTest` (5) and
   `:build-logic:nonfunctionalTest` (3) pass — **met**.
4. The compile proof ran in the real module and its output is in §3: the canary compiled with the wiring
   and failed without it (two errors per task, the marker's message carried in-module); eight consumer
   errors, then none with `@OptIn`, in `commonTest` and in `jvmTest` — **met**.
5. The POM and the Gradle Module Metadata show `kotlinx-coroutines-core` on the compile classpath (API
   variant) and `okhttp-jvm`, `caffeine`, `slf4j-api` at runtime only — **met**.
6. `Url.parse` agrees with `HttpUrl` on the whole corpus and on 200,000 generated inputs (and on
   2,000,000 with seeds 101 and 202), with no disagreement and every coverage floor met — **met**.
7. The root project has no `check` task; no Node.js, Yarn or `kotlin-js-store/` appears; the three
   existing modules compile unchanged — **met**.
8. The README states the module; the sentences plan §8 lists are repaired, and the one further
   inaccurate sentence (`verifyJavaFloor`'s "every module") is tightened (§2, deviation 1). Line 60's
   "three modules" stays as correct history of the retired combined artifact — **met**.

## 5. Measurements

The procedures of `plan.md` §9.2, in the order they ran.

### P1 — baseline and branch

- `git branch --show-current` → `feat/issue-62-http-core`; `git log --oneline` → `865e6e4`, `4c0cc60`,
  `8dfd315` (the research commits) on top of `f72b115`.
- `git status --short` → only plan-board files (`dashboard.html`, `plan.json`) and this issue's
  untracked planning documents.
- `./gradlew :build-logic:test` → `BUILD SUCCESSFUL`.

### P2 — the plugin (S1)

1. `./gradlew :build-logic:test` (with `webtools.kotlin-common-check` in the gating list) →
   `BUILD SUCCESSFUL in 35s`.
2. `./gradlew :build-logic:integrationTest --tests "*CommonCheckPluginIntegrationTest*"` →
   `BUILD SUCCESSFUL in 28s`; 7 tests, 0 failures.
3. `./gradlew :build-logic:nonfunctionalTest --tests "*CommonCheckConfigurationCacheReuseNonFunctionalTest*"`
   → `BUILD SUCCESSFUL in 14s`; 1 test, 0 failures.
4. Full suites, one after another: `./gradlew :build-logic:integrationTest` → `BUILD SUCCESSFUL in 2m 6s`,
   46 tests; `./gradlew :build-logic:e2eTest` → `BUILD SUCCESSFUL in 58s`, 5 tests;
   `./gradlew :build-logic:nonfunctionalTest` → `BUILD SUCCESSFUL in 28s`, 3 tests. 0 failures, 0 errors.
5. `git status --short build-logic` → the new plugin script, the two new test classes, and the edits to
   `SampleProject.kt` (80 inserted lines in three hunks) and `BuildLogicPluginsGatingTest.kt` (the fourth
   id and "four convention plugins"); nothing under `build-logic/build/`; no `kotlin-js-store/`.

### P3 — the compile proof (S2)

In §3. Every step matched the plan.

### P5 — the `commonTest` levels after S3

`./gradlew :webtools-http:gatingTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` →
`BUILD SUCCESSFUL`; 0 failures:
- `gatingTest`: `GatedTypesConsumerGatingTest` 3, `GatedTypesJvmConsumerGatingTest` 2 (5 tests).
- `deterministicTest`: `UrlAcceptDeterministicTest` 2, `UrlBoundaryDeterministicTest` 4,
  `UrlConfusionDeterministicTest` 1, `UrlEncodersDeterministicTest` 5, `UrlIpv6DeterministicTest` 9,
  `UrlPrintingDeterministicTest` 3, `UrlRejectDeterministicTest` 3 (27 tests).
- `nonfunctionalTest`: `UrlParseGarbageNonFunctionalTest` 1 (50,000 inputs, seed 11),
  `UrlParseLinearTimeNonFunctionalTest` 2, `UrlParseSecrecyNonFunctionalTest` 3 (6 tests).

Two mechanical fixes on the way, neither a change to a rule: `UrlCorpus` is an `internal object` (its
`Case` carries the `internal` `UrlProblem`, which a public class may not expose); and the corpus's
expected kind for `http:///a.com` and `http:////a.com` is `Host`, not `Scheme` — the text begins with
`http://`, so the authority is empty and §6.5's order reaches the host step (the plan's own reject list
already has `http:///h` as `Host`; "where they differ §6.5 governs").

`./gradlew :webtools-http-commoncheck:compileKotlinJs` with the S3 files in `commonMain` →
`BUILD SUCCESSFUL` (no JVM API in `Url`, `UrlParsing`, `UrlHost`, `UrlEncoding`, `UrlProblem`).
`grep -rn TODO webtools-http/src webtools-http-commoncheck/src` → nothing (the S2 `Url` shell is gone).

### P5 — the fast loop after S4

`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest`
→ `BUILD SUCCESSFUL in 10s`, 0 failures:
- `gatingTest`: 5 tests in 2 classes.
- `componentTest`: 68 tests in 14 classes (11 `commonTest`, 3 `jvmTest`: `Slf4jLogComponentTest`,
  `CatchNonFatalComponentTest`, `EpochMillisJvmComponentTest`).
- `deterministicTest`: 48 tests in 16 classes (13 `commonTest`, 3 `jvmTest` differentials).
- `nonfunctionalTest`: 9 tests in 4 classes.

`MediaTypeDeterministicTest` follows plan Amendment 1: `text/plain;;`, `text/plain ;`, `text/plain;` and
`text/plain ; charset=x` accepted; `text/plain; ` refused at index 11.

`webtools-http/src/jvmTest/resources/logback-test.xml`: `cp` of `webtools-udp`'s copy;
`git hash-object` → `04ccc7a97c1b603955cbc6b955a6f84182e488c6`, equal to `HEAD:webtools-udp/src/test/resources/logback-test.xml`.

Mechanical fixes on the way (test code only): a nonsense assertion in `HttpHeadersComponentTest`
(`entries is a copy`) replaced by a reference-inequality check; a backticked test name that held a `:`
(not a legal JVM method name) reworded; `"$canary\u00E9"` written as `"${canary}\u00E9"`: the tool that wrote
the test files rendered printable `\uXXXX` escapes as literal UTF-8 characters, so the `é` had joined
the identifier. The same rendering had left literal non-ASCII characters in string literals of the
test and test-support files; control characters and lone surrogates stayed `\uXXXX` escapes. S5 and
qa pass 1 turned every invisible or look-alike code point back into an escape (below).

### P4 — the `Url` differential (S4)

Default size, inside `deterministicTest` above (seed 7, 200,000 generated inputs plus the 203 corpus
inputs): 0 disagreements. Coverage: `inputs=200203 accepted=61564` (30.7%), `httpUrlThrew=498`,
accepted inputs with `[` 32,102, with a dot segment 23,523, with `?` 27,161, with a percent escape
33,988, with non-ASCII 12,619; `rejectedByKind={Empty=343, Backslash=20248, WhitespaceOrControl=35640,
NotUnicode=16913, Scheme=50672, UserInfo=1108, HostEncoding=3482, HostIdn=345, Host=8178, Port=1710}`.
Running time of the differential test method: about 3.1 s.

Extended size, `WEBTOOLS_DIFFERENTIAL_INPUTS=2000000 ./gradlew :webtools-http:deterministicTest --tests "*UrlOkHttpDifferentialDeterministicTest*" --rerun`,
the seed constant changed for each run and restored to 7 afterwards (the environment variable reached the
test JVM: the summary shows `inputs=2000203`):
- seed 101: `BUILD SUCCESSFUL in 22s`, 0 disagreements; `accepted=613213 httpUrlThrew=5330 bracket=321818
  dot=235048 query=269009 percent=338601 nonAscii=126646 rejectedByKind={Empty=3457, Backslash=202211,
  WhitespaceOrControl=358109, NotUnicode=170560, Scheme=507315, UserInfo=10741, HostEncoding=34520,
  HostIdn=3404, Host=80321, Port=16352}`.
- seed 202: `BUILD SUCCESSFUL in 25s`, 0 disagreements; `accepted=614644 httpUrlThrew=5332 bracket=321926
  dot=235282 query=269147 percent=339606 nonAscii=126357 rejectedByKind={Empty=3435, Backslash=202132,
  WhitespaceOrControl=358242, NotUnicode=170489, Scheme=506548, UserInfo=10702, HostEncoding=34384,
  HostIdn=3335, Host=79598, Port=16694}`.

Mutation sensitivity (each variant applied to the live file from a scratch backup, the differential run
with `WEBTOOLS_DIFFERENTIAL_INPUTS=300000`, the file restored and `cmp`-identical to the backup afterwards;
every mutant was caught on a corpus input, before any generated input):
1. `startsWith(ignoreCase = true)` for the scheme → `BUILD FAILED`: "corpus #44: Url accepted
   `http\u017f://h/` as `https://h/`, HttpUrl rejected it".
2. `xn--` labels kept as written → `BUILD FAILED`: "corpus #128: host of `http://xn--abc-/` expected `abc`
   but was `xn--abc-`".
3. eight IPv6 groups allowed with `::` → `BUILD FAILED`: "corpus #56: Url accepted
   `http://[1:2:3:4:5:6:7::8]/` as `http://[1:2:3:4:5:6:7:8]/`, HttpUrl rejected it".
4. `.%2e` removed from the double-dot forms → `BUILD FAILED`: "corpus #24: path of
   `https://example.com/.%2e` expected `/` but was `/.%2e`".

A final default run on the restored sources → `BUILD SUCCESSFUL`, the seed-7 summary above unchanged.

### S5 — mechanical changes before the proofs

- **Escapes in test sources.** The tool that wrote the test files had rendered printable `\uXXXX`
  escapes as literal characters.
  - Visible non-ASCII text (`é`, `日本`, fullwidth and circled letters, `。`) stays literal, so a
    reader sees the character a test feeds.
  - Every invisible or whitespace-like code point was converted back to its escape, so that none hides
    in the source: U+00A0 NBSP, U+2028 LINE SEPARATOR, U+3000 IDEOGRAPHIC SPACE, U+200B ZWSP and
    U+FEFF BOM. U+0085 NEL, every control and every lone surrogate had stayed escaped.
  - U+212A KELVIN SIGN, a look-alike of `K`, became an escape in qa pass 1. Touched:
  `HttpSyntaxDeterministicTest` (the `trimOws` NBSP row), `UrlBoundaryDeterministicTest` (the NBSP end
  row), `UrlInputGenerator` (the surrounding-whitespace and Unicode pools) and
  `UrlEncoderDifferentialDeterministicTest` (its pool); each such line carries a short comment naming
  the code points. No main source held a literal non-ASCII character.
- **One compiler warning removed.** The first `check` printed `w: … FailureLeavesComponentTest.kt:106:62
  Check for instance is always 'true'` (a redundant `it is Exception` over a `List<HttpFailure>`); the
  line was removed. P5 allows no warning but the two of P3 step 1; the recheck below prints only
  `JS Environment Not Selected`.

### P6 — the dump, the class files, the published shape (S5)

1. `./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has
   294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line).
   `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds:
   `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`,
   a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun
   close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; the groups
   as `public abstract class` with `protected fun <init>` (two on `HttpFailure` and `TransportFailure`,
   `(ILjava/lang/String;)V` on `UpstreamFailure`); every leaf nested (`HttpFailure$Closed`,
   `TransportFailure$Timeout`, `UpstreamFailure$RateLimited`, …); `parse-IoAF18A` on `Url$Companion` and
   `of-IoAF18A` on `HttpMethod$Companion`; exactly three `$default` entries (`HttpHeaders$default`,
   `HttpRequest$default`, `HttpResponse$default`); the marker as `public abstract interface annotation
   class …ExperimentalForInheritanceWebToolsApi : java/lang/annotation/Annotation`; no
   `SubclassOptInRequired`; none of `Log`, `HttpSyntax`, `UrlProblem`, `UrlParseException`, `ParsedUrl`,
   `LogKt`, `Slf4jLog`.
2. `javap -v -p -cp webtools-http/build/classes/kotlin/jvm/main …`: `WallClock`, `HttpTransport`,
   `HttpFailure`, `TransportFailure` and `UpstreamFailure` each show `RuntimeInvisibleAnnotations:
   kotlin.SubclassOptInRequired(markerClass=…ExperimentalForInheritanceWebToolsApi)`; `WallClock$System`
   and `HttpFailure$Closed` show no `SubclassOptInRequired`; the marker class shows
   `kotlin.RequiresOptIn(message="Implementing or substituting this WebTools seam is Experimental and may
   change in a minor release. Using the supplied implementations needs no opt-in. Opt in with
   @OptIn(ExperimentalForInheritanceWebToolsApi::class).", level=…ERROR)`; `major version: 55` on every
   class read.
3. `./gradlew :webtools-http:generatePomFileForJvmPublication :webtools-http:generateMetadataFileForJvmPublication`
   → `BUILD SUCCESSFUL`. `pom-default.xml` (`webtools-http-jvm` 1.0.0): `kotlinx-coroutines-core-jvm`
   1.10.2 `compile`, `kotlin-stdlib` 2.2.0 `compile`, `okhttp-jvm` 5.5.0 `runtime`, `caffeine` 3.3.1
   `runtime`, `slf4j-api` 2.0.13 `runtime`. `module.json`: `jvmApiElements-published` lists
   `kotlinx-coroutines-core` and `kotlin-stdlib` only; `jvmRuntimeElements-published` adds `okhttp-jvm`,
   `caffeine`, `slf4j-api`; `org.gradle.jvm.version` is 11 on both (and on the sources variant).
   `./gradlew :webtools-http:verifyJavaFloor` → `BUILD SUCCESSFUL`.

### P5 — `check` of both modules (S5)

`./gradlew :webtools-http:check :webtools-http-commoncheck:check` → `BUILD SUCCESSFUL in 9s`: `jvmTest`
130 tests (gating 5 + component 68 + deterministic 48 + nonfunctional 9), 0 failures; `apiCheck`,
`verifyJavaFloor` and the check module's `compileKotlinJs` ran. After the warning fix, `./gradlew
:webtools-http:compileTestKotlinJvm :webtools-http:check :webtools-http-commoncheck:check` → `BUILD
SUCCESSFUL`, the only `w:` being `JS Environment Not Selected`.

### P7 — the check module and the root

- `./gradlew :webtools-http-commoncheck:build --configuration-cache` twice → both `BUILD SUCCESSFUL`; the
  second prints `Reusing configuration cache.` The build ran `compileKotlinJs`, `jsJar`, `allMetadataJar`;
  `jsTest` and `allTests` are `NO-SOURCE`.
- `ls kotlin-js-store` → "No such file or directory"; `git status --short` lists no `kotlin-js-store/`.
- `./gradlew :webtools-http-commoncheck:build --dry-run` lists twelve `:webtools-http-commoncheck:` tasks
  and no `NodeJs`, `Yarn`, `Npm` or `PackageJson` task; no root task.
- `./gradlew :check` → `BUILD FAILED`: "Cannot locate tasks that match ':check' as task 'check' not found
  in root project 'WebTools'."
- A throwaway `ProofLeak.kt` in `webtools-http`'s `commonMain` (`internal val proofLeak = java.io.File("x")`):
  `:webtools-http:compileKotlinJvm` → `BUILD SUCCESSFUL`; `:webtools-http-commoncheck:compileKotlinJs` →
  `BUILD FAILED`, `e: …/ProofLeak.kt:3:26 Unresolved reference 'java'.` The file was removed; `git status`
  shows no `Proof*`.

### P8 — the unaffected modules

`git diff --stat -- webtools-udp webtools-scraping webtools-browser` → empty. `git status --short
build-logic/src/main` → only `?? build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts`.
`./gradlew :webtools-udp:compileKotlin :webtools-scraping:compileKotlin :webtools-browser:compileKotlin`
→ `BUILD SUCCESSFUL in 5s`.

### S6 — the README

The nine edits of plan §8, in order: the "three independent" sentence; the `webtools-http` artifact row;
the install line with its "not yet on Maven Central" comment; the Multiplatform/Maven/`kotlin-stdlib`
2.2.21 paragraph after "Requires JDK 11 or newer"; the module list in "Build & test"; the
`:webtools-http-commoncheck:build` command line and the `*-commoncheck` sentence under the block; the
test-layout paragraph; "Every JVM module" and "every library module"; "four convention plugins", "(used
by `webtools-http`)" and the `webtools.kotlin-common-check` row. `git diff --stat README.md` → 29
insertions, 14 deletions, no other line. The working-tree file was LF (not CRLF as plan §2 recorded) and
stays LF. Not changed, because plan §8 does not list them: line 60 ("whichever of the three modules you
actually use", the migration note from the retired combined artifact, whose successors are those three)
and the `verifyJavaFloor` comment in the command block ("every module's jars"), which the check module,
having no JVM jar, makes slightly loose.

**S7 addition (§2, deviation 1):** the `verifyJavaFloor` command comment now reads "the
Java 11 floor of every published module's jars, …"; line 60's "three modules" stays as correct history of
the retired combined artifact. `git diff --stat README.md` → 30 insertions, 15 deletions; still LF.

### qa pass 1 fixes

Twenty-seven minor fixes, applied on the spot. None changes behaviour or a signature. One adds the
eighth point of `HttpTransport`'s documented contract (§2, deviation 2).
- KDoc and comments in production code: `HttpFailure` class KDoc states the plan's message rule (constant
  text plus named non-sensitive fields; never free caller text, a header value or a body);
  `HttpHeaders.Builder.addSensitive` no longer promises the cross-origin strip (it marks the entry; the
  redirect policy of a later unit reads the flag); `HttpTransport` gained contract point 8 (`execute` is
  main-safe; the implementation moves blocking I/O off the caller's thread and documents its dispatcher);
  `HttpSyntax.mediaTypeProblemIndex` carries the Amendment 1 rule above its `semi + 1` line;
  `UrlEncoding.kt` names the OkHttp sets each encode set mirrors and notes "`~` is encoded, `*` is not";
  `UrlHost.kt` names the DNS limits and the no-octal leading-zero rule.
- Test sources: every U+212A KELVIN SIGN literal is now a `\u212A` escape with a naming comment
  (`HttpHeaderNameDeterministicTest`, `HttpHeaderValueDeterministicTest`, `HttpSyntaxDeterministicTest`
  (all four occurrences), `UrlInputGenerator`, `UrlEncoderDifferentialDeterministicTest`);
  `UrlCorpus.whitespaceInside`/`whitespaceAtEnds` name their code points and now hold every `isUrlSpace`
  code point (U+1680, U+2000..U+200A, U+2028, U+2029, U+202F, U+205F added; all `WhitespaceOrControl`);
  `UrlCorpus.accept` gained the four dot-segment folds; `UrlInputGenerator` gained `@param`/`@return` on
  `generate` and `next`, the one-`Random` sentence, the draw-rate comments on `structured` and `raw`, and
  the port, group-count and surrogate-pair comments; `UrlCorpus` gained `@property` tags and the
  exactly-one-of invariant on `Case`, `@param`/`@return` on `visible`, and one-line KDoc on `accept`,
  `reject`, `confusion` and each length constant; `HttpHeadersComponentTest` gained `set and setSensitive
  on an absent name append one entry` and the renamed `get returns the last value …` test; behaviour names
  in `HttpHeaderNameDeterministicTest`, `HttpMethodTokenDeterministicTest`, `StatusRangeDeterministicTest`
  and `UrlParseSecrecyNonFunctionalTest` (KDoc aligned); explanatory comments in
  `UrlOkHttpDifferentialDeterministicTest` (floors), `UrlEncoderDifferentialDeterministicTest` (0 to 5
  pieces), `WallClockSystemComponentTest` (the epoch constant) and `GatedTypesConsumerGatingTest`
  (`4 + 22 + 1`); `CommonCheckPluginIntegrationTest` holds its two regexes in `//language=RegExp`-annotated
  locals with a comment on the root-task line; `IncludedBuildEndToEndTest`'s KDoc says the common-check
  plugin is covered at Level 3 by `CommonCheckPluginIntegrationTest` and is not part of the composite.
- Re-run after the fixes: `./gradlew :webtools-http:check :webtools-http-commoncheck:check` and
  `./gradlew :build-logic:integrationTest --tests "*CommonCheckPluginIntegrationTest*" :build-logic:e2eTest`.
  Results: the first → `BUILD SUCCESSFUL in 10s`, `jvmTest` 131 tests (the new `HttpHeadersComponentTest`
  case added one), 0 failures, `apiCheck` and the check module's compile green, the only `w:` still `JS
  Environment Not Selected`; the second → `BUILD SUCCESSFUL in 1m 15s`, `CommonCheckPluginIntegrationTest`
  7 tests and `IncludedBuildEndToEndTest` 5 tests, 0 failures.

### P9 — mechanical rules

Each command printed nothing except where the plan says otherwise: the forbidden-API grep over
`commonMain` (`java.`, `javax.`, `@Jvm`, `runCatching`, `lowercase(`, `uppercase(`, `ignoreCase`,
`removeFirst`, `removeLast`, `String.format`, `Regex(`) → nothing; `catch` → `NonFatal.kt` only (lines
15, 18, 20); `data class` → nothing; `TODO` → nothing; `OptIn` in `commonTest`/`jvmTest` → nothing; the
internal names in the dump → nothing; `grep -c '\$default'` → 3; the KDoc-coverage `awk` over
`commonMain` and `jvmMain` → nothing; `git hash-object` of the new `logback-test.xml` →
`04ccc7a97c1b603955cbc6b955a6f84182e488c6` = `HEAD:webtools-udp/src/test/resources/logback-test.xml`.

## 6. Level 5 — the manual pass

Procedure P10 of plan §9.2, run from the command line. The three IDE items (1, 2 and 4) need an
IntelliJ session and were **not performed**.
- **Owner:** the maintainer.
- **Trigger:** the next IntelliJ import of the repository after this unit lands, and at the latest
  before unit 4 (#63) starts. That unit's fixtures compilation adds another source set the IDE must
  import ([`impact.md`](impact.md) §7).

1. IDE import of the repository, the check module's resolution of the shared directory and the `jsMain`
   actuals, no spurious red in `jsMain` — **not performed** (IDE). What the command line shows: Gradle
   resolves the shared directory (`:webtools-http-commoncheck:compileKotlinJs` compiles the sibling's
   `commonMain` with the two `js` actuals), so the model the IDE imports is consistent.
2. A scratch class implementing `WallClock` without the opt-in shows the gate error and the `@OptIn` quick
   fix — **not performed** (IDE). The compiler's text for exactly that case is recorded in §3 (P3 step 2b
   and step 4); the IDE renders the same diagnostic.
3. The dump read against plan §7.8 and the architecture §9 tier table — **done** (§5, P6.1): every
   expected entry is present, nothing `internal` or gated-by-annotation leaks into it; the five
   Experimental types are the only interfaces and abstract classes a consumer can implement or subclass,
   and every other public declaration is final or a factory, as the tier table says.
4. Quick Documentation of `Url.parse`, `Url.toString`, `HttpTransport`, `HttpFailure` and
   `ExperimentalForInheritanceWebToolsApi` as a consumer sees them — **not performed** (IDE). The KDoc of
   each names its tier, states its contract, and `Url.toString`'s carries the query warning (read in the
   source; the P9 coverage check confirms every public declaration has a block).
5. `git status` / `git diff --stat` before the commits: no `Proof*`, no `NegativeCases`, no
   `kotlin-js-store/`, no `build-logic/build/`, no IDE files — **done**: a search of the full untracked
   list for those names returns nothing. The working tree also holds the admin team's presenter's board
   files (`dashboard.html`, `plan.json`, `plan.js` under `docs/plans/`), which are not part of this change.

## 7. Research that didn't hold

No research claim was contradicted. Every load-bearing claim the implementation leaned on held as
measured: the opt-in gate's behaviour and in-module text (research F1, F2, F3, F5, F6, F10); `js()` with no
environment and its warning (common-check F2–F5); `HttpUrl`'s canonicalisation on the whole corpus and on
4,200,000 generated inputs, the IPv6 crash included (url-parsing F1–F10, F16); OkHttp's header and
`MediaType` rules (header-and-method F4, F10, and the content-type table, which plan Amendment 1 brought
the test row into line with).

One statement of plan §2 (not research) did not hold: `README.md` was LF in the working tree, not CRLF.
It cost nothing; the file was edited and left LF.

## 8. Known gaps

- **IDE behaviour is untested** (§6 items 1, 2 and 4). Not tried: IntelliJ's import of the check
  module, its reading of the per-source-set opt-ins, and the shape of its gate diagnostic and quick fix.
  The owner and the trigger are in §6 and [`impact.md`](impact.md) §7.
- **The gate has no permanent compile-fail check** (plan §13). The eight negative cases were run once
  (§3).
- **Visible non-ASCII characters are literal in the test sources** (`é`, `日本`, fullwidth and circled
  letters), so a reader sees what a test feeds. Invisible and look-alike code points (U+212A, NBSP,
  U+2028, U+3000, ZWSP, BOM) are escapes with naming comments. The compiler reads the files as UTF-8.
- **The ABI dump is CRLF in the working tree,** as `apiDump` writes it on this host. Git stores it LF,
  and `apiCheck` compares line by line, so a checkout on another host is unaffected.
- **Only this host** (Windows, JDK 23) ran the build. Linux and macOS are unexercised (plan §14).
