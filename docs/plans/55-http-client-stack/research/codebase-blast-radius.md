# What the HTTP client stack touches in this repository, and what existing code moves onto it

## Question

Which files do the new modules (`webtools-http`, a feeds module), the `build-logic` restructure, the Java 11 floor and the multiplatform build change or create; what do the README and `docs/` need; what do the repository's history and rules imply for new code; which tests pass today; which existing classes, if any, move onto the new stack; and which tests in this repository prove the checks BotTools publishes for its adapter (KotlinBottools#9 and #15)?

Requirements the answer meets: `Connector` and the legacy modules are not reworked by this change; `webtools-udp` keeps its public API; the first inter-module dependency (feeds on `webtools-http`) is accounted for; tests follow the five-level hierarchy in packages `com.spartanlabs.testing.<level>.webtools.<module>`.

Evidence tiers: **repo** (read in this repository at `master` 806ce8e; HEAD d72adae differs from `master` only by documents under `docs/plans/55-http-client-stack/`), **measured** (run in a throwaway probe outside the repository), **documented** (source at a tag, official documentation), **prior research** (the three sibling findings in this issue folder, cited and not redone).

## Bottom line

- **No requirement fails, but two points need a call before execution.**
  - **Literal location of the Java floor.** The floor is set once, as the constant `WebToolsBuild.JAVA_FLOOR` in the included `build-logic` build, not in the root `build.gradle.kts`, which keeps only `group` and `version`. That meets "set once" and is the placement prior research measured in a consumer's composite build; it differs from the words "in the root build" (Consideration 3).
  - **`Untrusted` cannot be exercised end to end against `mockwebserver3` as stated.** `MockWebServer.start()` binds `localhost`, a loopback address, and `Untrusted` rejects loopback by design. The `Untrusted` refusals check works (the mock server is the refused target). The size-cap and conditional-GET checks in KotlinBottools#9/#15 only reach a response if tests can run the `Untrusted` rules with loopback permitted. The stack needs a deliberate test seam for that (Consideration 1).
- **The change edits `settings.gradle.kts`, the root `build.gradle.kts`, the three module build files and `README.md`, and creates `build-logic/`, `webtools-http/` and the feeds module (full inventory in Finding 1).** `gradle.properties` and `.gitignore` need nothing.
  - No source file of `webtools-udp`, `webtools-scraping` or `webtools-browser` has to change. The Java 11 floor prior research measured identical signatures, so the clause allowing legacy modules to break their API is not exercised.
  - `.gitignore` needs no edit: `build-logic/build`, `.gradle` and `.kotlin` are already ignored (checked with `git check-ignore`). `kotlin-js-store/` is not ignored and is meant to be committed; it appears only if a js target runs tests.
  - The `webtools-udp` port lock (`webtools-udp/build.gradle.kts:103`–`:117`) moves unchanged. It survives because the convention plugin is applied in the module's `plugins {}` block, so the level tasks exist before line 115 runs (prior research, measured).
- **Adoption: nothing existing moves onto the new stack in this change.** `Connector` (`open`/`next` line reader, `get`, `skrape`, `download`) is not HTTP-stack material as it stands (blocking, stateful, global side effects, no timeouts or caps). `WebViewer` and `resolveLocalAddress()` are not HTTP clients. `Connector.get` and `skrape` are the easiest later move; `open`/`next` and `download` are the hardest (Finding 6).
- **`webtools-feeds` cannot use `webtools-http`'s `flatMap`.** It is `internal`, and Kotlin `internal` stops at the Gradle-source-set module boundary. Feeds either carries its own copy (the repository's established stance, now a third and fourth copy) or `webtools-http` publishes a public helper (rejected: it turns two lines into Stable Core surface). Recommended: own copy, only if feeds needs one.
- **Baseline: every level of every module passes on an unmodified `master` tree** (measured below): 642 tests executed and 0 failures across `webtools-udp` (628), `webtools-scraping` (11) and `webtools-browser` (3), plus 15 tests that are `@Disabled` by design at Level 5.
- **Logging tension: facts and options only.** The repository rule asks for structured slf4j logging of lifecycle events, data flows and failure states; slf4j is a JVM-only API. Whatever code lands in `commonMain` has no logger unless an internal seam is added. Three options are laid out in Consideration 2; it is not settled here.

## Findings

### 1. The file inventory

**Existing files that change**

| Path | Change |
|---|---|
| `settings.gradle.kts:1`–`:3` | Add `pluginManagement { includeBuild("build-logic") }` above the `plugins {}` block (must come first in a settings script). The foojay 0.8.0 plugin stays; Java 11 prior research found a bump is not needed unless a JDK toolchain or a JDK 11 test launcher is requested. |
| `settings.gradle.kts:7` | `include(":webtools-udp", ":webtools-scraping", ":webtools-browser")` gains `":webtools-http"` and the feeds module. Conditional on Consideration "common-code guarantee" (prior research): `":webtools-http-commoncheck"`. |
| `build.gradle.kts:1`–`:5` | The `plugins {}` block (`kotlin("jvm")` 2.2.0, vanniktech 0.36.0, both `apply false`) is deleted; both versions move once into `build-logic/build.gradle.kts`. |
| `build.gradle.kts:7`–`:11` | The comment ("configured here once; each module's own build.gradle.kts adds only its dependencies and its coordinates") is reworded to say the shared configuration lives in `build-logic`. The intent is unchanged. |
| `build.gradle.kts:12`–`:15` | `allprojects { group; version }` stays. It is what the modules inherit; `webtools-udp` already overrides `version`. |
| `build.gradle.kts:17`–`:92` | The whole `subprojects {}` block is deleted. Its contents move to `build-logic`: plugin application (`:18`–`:19`), `repositories` (`:21`), the shared dependencies (`:25`–`:33`), `useJUnitPlatform` (`:35`), the seven level tasks (`:44`–`:62`) and the `mavenPublishing` skeleton and POM (`:64`–`:91`). |
| `gradle.properties` | No change needed. It holds only `kotlin.code.style=official`. Whether to enable the configuration cache repository-wide (the requirement is compatibility, not enablement) is a Known gap. |
| `.gitignore` | No change. Already covers `.gradle` (`:1`), `build/` (`:2`, any depth, so `build-logic/build`), `.kotlin`. `kotlin-js-store/` is deliberately not ignored. |
| `webtools-udp/build.gradle.kts:1`–`:2` | Header says "slf4j-api only (inherited from the root build)"; no longer true. Rewritten. |
| `webtools-udp/build.gradle.kts` (top) | Add `plugins { id("webtools.kotlin-jvm-library") }` and `dependencies { api("org.slf4j:slf4j-api:2.0.13") }`. The `api` declaration moves into the module so the published POM and Module Metadata stay identical (prior research, measured). |
| `webtools-udp/build.gradle.kts:92`–`:101` | The `2.0.0a` note gains the floor (bytecode and Module Metadata now Java 11; no API change). `version = "2.0.0a"` (`:101`) is unchanged; the floor rides in the unreleased `2.0.0a`. The 97-line comment block is this module's change history (see Finding 8). |
| `webtools-udp/build.gradle.kts:103`–`:117` | **Unchanged.** `CommonUdpPortLock` (`:108`), `registerIfAbsent` (`:110`) and `tasks.named<Test>(name) { usesService(...) }` for `test`, `integrationTest`, `e2eTest`, `nonfunctionalTest` (`:115`–`:117`). They keep working because the plugin that registers those tasks is applied in `plugins {}` (prior research measured this). The lock stays here: only this module binds the fixed UDP port 9998 (comment `:103`–`:107`). The new modules need no lock: `MockWebServer.start()` defaults to port `0`. |
| `webtools-udp/build.gradle.kts:119`–`:125` | Unchanged (`coordinates`, `pom { name; description }`). |
| `webtools-scraping/build.gradle.kts` | Add `plugins { id("webtools.kotlin-jvm-library") }` and `api("org.slf4j:slf4j-api:2.0.13")`. Dependencies at `:5`–`:7` unchanged. A `version = "1.0.0a"` line if the release version is set at merge (prior research, Considerations of the Java 11 floor). |
| `webtools-browser/build.gradle.kts` | Same shape: plugin, `api(slf4j-api)`, optional `version`. |
| `README.md` | Finding 2. |
| `webtools-*/src/**` of the three legacy modules | **No change.** Sources compile unchanged at the floor with identical `javap -protected` signatures (Java 11 prior research). The `@Tag` annotations on the 99 test files can stay: the package filter selects the same classes (prior research, measured). |

**New files and folders**

| Path | Purpose |
|---|---|
| `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts` | `kotlin-dsl`; `implementation` of `kotlin-gradle-plugin:2.2.0` and the vanniktech plugin 0.36.0 (the only place plugin versions are pinned). |
| `build-logic/src/main/kotlin/webtools.published-library.gradle.kts` | vanniktech, `repositories`, `publishToMavenCentral()`, `signAllPublications()`, shared `pom {}` (today `build.gradle.kts:64`–`:91`). No credentials in any file. |
| `build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts` | `jvmTarget` and `-Xjdk-release` plus `java {}` compatibility from `JAVA_FLOOR`; `kotlin("test")` and logback test dependencies; `useJUnitPlatform()`; level tasks; `verifyJavaFloor` wired into `check`. |
| `build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts` | `jvm()` with `jvmTarget`, `-Xjdk-release`, `TargetJvmVersion`; level tasks over the JVM test compilation; a `test` task depending on `jvmTest`; `configure(KotlinMultiplatform(...))`. |
| `build-logic/src/main/kotlin/WebToolsBuild.kt` (+ the `verifyJavaFloor` task class) | `JAVA_FLOOR = 11`, the level list and `registerLevelTasks(...)`. |
| `webtools-http/build.gradle.kts` | `plugins { id("webtools.kotlin-multiplatform-library") }`; `kotlin { explicitApi() }`; dependencies (`okhttp-jvm` 5.5.0, Caffeine 3.3.1, kotlinx-coroutines 1.10.2 as `implementation`; slf4j-api in `jvmMain`); the `testFixtures` compilation and its two outgoing variants (test-fixtures prior research, recipe B); `mavenPublishing { coordinates(...); pom { name; description } }`. `version` (if overridden) must be set before the fixtures capability line, which reads `$version` eagerly. |
| `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/` | Ports, failure model, request/response types, facade (as far as it is common). |
| `webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/` | OkHttp transport, Caffeine cache, URL policy resolver; slf4j. |
| `webtools-http/src/jvmTestFixtures/kotlin/...` and `.../resources/` | The published test support: fake transport, shared time source, real-transport-over-`mockwebserver3` helper, recorder. Nothing here is covered by `explicitApi()` (prior research). **No `logback-test.xml` here**: it would be published and would override a consumer's logback configuration. |
| `webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/` | Level tests that need no JVM type. |
| `webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/`, `.../support/webtools/http/` | Level tests that use the published fakes (the fixtures compilation is JVM-only, so any test using them must sit in `jvmTest`) or JVM types; the module's private helpers (the analogue of `webtools-udp`'s `testing/support/webtools/udp/`). |
| `webtools-http/src/jvmTest/resources/logback-test.xml` | Same content as the three existing copies (byte-identical, INFO root, console appender). |
| `webtools-feeds/build.gradle.kts` and `src/` tree | **If JVM-only:** `webtools.kotlin-jvm-library`, `src/main/kotlin/com/spartanlabs/webtools/feeds/`, `src/test/kotlin/com/spartanlabs/testing/<level>/webtools/feeds/`, `src/test/resources/logback-test.xml`; `api(project(":webtools-http"))` and `testImplementation(testFixtures(project(":webtools-http")))`. **If multiplatform:** `commonMain`/`jvmMain`, fakes-using tests in `jvmTest`, `"jvmTestImplementation"(testFixtures(project(":webtools-http")))`, two coordinates (`webtools-feeds`, `webtools-feeds-jvm`). Conditional on the open feeds-shape question (prior research). |
| `webtools-http-commoncheck/build.gradle.kts` | **Conditional** on the open common-code question: an unpublished module compiling the same `commonMain` for js. If an internal `expect` logging seam is chosen (Consideration 2), this module needs a stub `actual`. |
| `kotlin-js-store/yarn.lock` | **Conditional:** only if a published js target runs tests (written by the Kotlin plugin; meant to be committed). |
| `webtools-http/api/*.api` | **Conditional:** only if ABI tooling is adopted (research question 9). |
| A build-level publication check | **Recommended by the test-fixtures research** (publish to a file repository, resolve `testFixtures(...)` once from it and once through `includeBuild`; assert the capability, `org.gradle.jvm.version == 11`, and that the main variants list neither `kotlinx-coroutines-test` nor `mockwebserver3`). Its location (a `build-logic` test, a TestKit test, or a verification task) is a Known gap. |
| `docs/webtools-http-architecture.md` | Recommended evergreen reference (Finding 2). |
| `docs/plans/55-http-client-stack/` documents; `docs/plans/55-http-client-stack/47-java-23-bytecode/` documents | #55's `architecture.md`, `plan*.md`, `impact.md`, `final-implementation.md` sit in the #55 folder. #47's own plan, impact and as-built record sit in `47-java-23-bytecode/`, which already holds `research/`. |

### 2. README and documentation impact

`README.md` is 484 lines; lines 90–462 are the UDP protocol and are unaffected. Section by section:

| Lines | Today | Change |
|---|---|---|
| `:3`–`:4` | "A small Kotlin/JVM collection ... published as **three independent, single-concern artifacts**" | "five" (a check-only module is not published). "Kotlin/JVM" stays true while the only published target is `jvm`; a published js target (common-code Option B) would make it false. |
| `:6`–`:10` | Artifacts table with the column "Third-party runtime deps" | Two rows. `webtools-http`: `okhttp-jvm`, `caffeine`, `kotlinx-coroutines-core`, `slf4j-api` (the published runtime variant of `webtools-http-jvm` lists exactly these; prior research). `webtools-feeds`: `webtools-http` and its transitives, plus the parser library. Note that slf4j-api is `api` for the three existing modules and `implementation` for the new ones, so the udp cell "none (slf4j-api only)" keeps its meaning and the new cells list it differently. |
| `:12`–`:13` | "Every fallible operation returns `kotlin.Result` rather than throwing" | Still true. Add that the new modules' APIs are `suspend`. |
| `:15`–`:24` | Gradle-only install snippet | Add `webtools-http:1.0.0` and `webtools-feeds:1.0.0`. Add a Maven note: the root KMP coordinate carries no classes, so Maven consumers use `webtools-http-jvm` (prior research, measured; `webtools-feeds` stays a single coordinate if JVM-only, and its POM maps the dependency to `webtools-http-jvm`). Add the fixtures line `testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:1.0.0"))` and say it is Gradle-only (resolution fails without Gradle Module Metadata). Precedent for listing a not-yet-published version: `:19` shows `webtools-udp:2.0.0a`, which is not on Central. |
| `:26` | "Requires JDK 11 or newer. Built with Kotlin 2.2." | Becomes true as written once the floor lands. Add: the new modules need a kotlin-stdlib of at least 2.2.21 on the consumer classpath because `okhttp-jvm` 5.5.0 requires it (Java 11 prior research, measured), and that building the repository needs JDK 17 to 26 (Gradle 9.7.1). |
| `:28`–`:33` | UDP 2.0 wire break; links to the two `docs/` files | Unchanged. |
| `:35`–`:46` | Migration from the combined `WebTools` artifact ("whichever of the three modules") | Unchanged: it describes what the combined artifact contained. |
| `:48` | `## Components` | Add `### webtools-http` and `### webtools-feeds` after `:88`, in the existing "Type / Purpose" table form, each with an API-stability note in the style of `:70`–`:76`. |
| `:70`–`:76` | `webtools-udp` "API stability": whole surface Stable Core, no markers | Unchanged. The new notes say: what a consumer calls or configures is Stable Core (breaking changes only in a major); every seam a consumer implements or substitutes is Experimental behind the single `@RequiresOptIn` marker and may change in a minor; the supplied defaults work without opting in; a seam graduates to `@SupportedExtension` in a feature release once a consumer has built against it. No `@RequiresOptIn` or `@SupportedExtension` exists anywhere in the repository today (`webtools-udp`'s plans record deliberately shipping none), so `webtools-http` introduces the first. |
| `:78`–`:82` | `webtools-scraping` | Add one sentence: `Connector` is the legacy blocking helper; for suspend calls, caps, a URL policy, caching and a call budget use `webtools-http`. |
| `:84`–`:88` | `webtools-browser` | Unchanged. |
| new H2 | none | A short "HTTP client stack" section (build a client, the two policies, the failure model, using the fixtures) and a short "Feeds" section. Place directly after Components; no existing anchor moves. |
| `:463`–`:466` | "The repo is a Gradle multi-module build; the three modules are `:webtools-udp`, `:webtools-scraping`, `:webtools-browser`." | Five modules; mention `build-logic` as the included build holding shared configuration. |
| `:469`–`:471` | `./gradlew build` / `:webtools-udp:build` / `./gradlew test` ("every test level, one JVM per module") | Still correct if the KMP plugin registers a `test` task depending on `jvmTest`. Without it, `./gradlew test` silently skips `webtools-http` (prior research, measured). `build` now includes `verifyJavaFloor` through `check`. |
| `:472`–`:478` | The seven level commands | Unchanged; the names exist in every module, KMP or JVM. `:474` says Level 3 is "real sockets / external interfaces"; `mockwebserver3` fits. |
| `:480`–`:484` | Tests live under `<module>/src/test/kotlin/com/spartanlabs/testing/<level>/webtools/<module>/` and are "JUnit-tag[ged]"; "Shared fixtures live under `testing/support/`" | Three edits. (1) KMP modules keep tests in `src/commonTest/kotlin` and `src/jvmTest/kotlin` (same package rule). (2) "JUnit-tags them" becomes "selects them by package"; a `commonTest` class cannot carry a JUnit `@Tag`, and the package filter selects the same classes the tags select today. (3) `testing/support/` stays the private per-module helper folder; say it is distinct from the published `webtools-http` test-fixtures variant. |

**`docs/`.**
- `docs/webtools-udp-architecture.md` and `docs/webtools-udp-protocol.md` need **nothing**: a search for build, module, JDK, slf4j, Connector and root-build references finds none (checked).
- **Should the new modules get evergreen reference docs? Yes for `webtools-http`, no for a protocol doc, no separate doc for feeds.**
  - `webtools-udp` has two evergreen documents because its behaviour spans threads, wire bytes and lifecycles that KDoc per symbol cannot show. `webtools-http` has the same property in a different form: the layering (cache, then budget, then transport), the point at which each outbound-URL check runs, the ownership of the library scope and dispatcher, the failure-to-cause mapping, the tier of every seam, and how the test support is assembled. The org's documentation model puts sequence diagrams for coroutine flows in the outermost ring, and `docs/webtools-udp-architecture.md` already uses Mermaid in that role. Planning documents under `docs/plans/` are replaced as plans change; an evergreen `docs/webtools-http-architecture.md` is the maintained summary, linking to the plan as the UDP documents do.
  - No wire protocol exists to specify: HTTP is the external contract. A "protocol" document would only restate OkHttp.
  - `webtools-feeds` is thin (port, default parser, conditional-GET fetch). A README section and KDoc cover it; the parser's hardening posture belongs in the README section.

### 3. History and intent

- **The three-module split** (Issue #15; PR #16 merged 2026-09-05; `docs/plans/15-split-into-three-modules/plan.md` and `final-implementation.md`).
  - Motive: a consumer of the UDP layer should not drag in Selenium, skrapeit, jsoup and unirest (`plan.md` header).
  - The root build was the place for "everything that is not dependency-specific" (`plan.md` §2.3), and `build.gradle.kts:7`–`:11` states the rule: each module's build file "adds only its dependencies and its coordinates". `plan.md` §6 records that each subproject needs its own `mavenPublishing {}` block and calls this "the standard pattern for that plugin". `build-logic` convention plugins keep that rule (the module build file still adds only dependencies, coordinates and one plugin line) and replace the mechanism Gradle documents as improper cross-project configuration (prior research).
  - "Modules share no code" is explicit: `plan.md` §4.3 "the three modules share no code and no test depends on another module"; §1.3 lists `webtools-browser` with zero tests; §6 records exactly one known consumer of the UDP layer.
  - Deviation recorded in `final-implementation.md`: the planned `.internal` sub-package was dropped; sources stay in one flat package per module. That is why `ResultExtensions.kt` sits at `com.spartanlabs.webtools.<module>`.
- **The duplicated `flatMap`** (`webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/ResultExtensions.kt:3`–`:5`, twin at `webtools-udp/.../ResultExtensions.kt:3`–`:5`). Each file says: "Deliberately duplicated in [the other]. This is one internal two-line helper; a shared webtools-core artifact just to host it would not earn its keep. Keep the two copies identical." `plan.md` §2.2 gives the reason: a fourth coordinate for one internal function. Both are `internal inline`, so they never collide on a consumer classpath. `webtools-udp` uses it in five files (19 occurrences).
- **`Connector`'s libraries.** There is no recorded rationale. jsoup 1.15.4 and unirest-java 1.4.9 are in the initial commit (`ea72708`, 2023-08-11); skrapeit arrived in `1.1.3` (`a5cf296`, 2023-09-01); versions have not moved since. The 2026-08-27 commit (`4e6c9ff`, "Refactor APIs to Result-based error handling") reshaped the API around `Result` and states its purpose as making failures explicit, with no change of library. Issue #15 lists the three libraries as "Used only by `Connector`". The libraries are an accident of history, not a design to preserve; Java 11 prior research shows their transitive tree (Ktor 1.5.4, htmlunit 2.50.0, Apache HttpClient 4.5.13, Kotlin 1.5-era stdlib) is what the new stack must keep off a consumer's classpath.
- **What this means for the new modules.**
  - `webtools-feeds` on `webtools-http` is the repository's **first inter-module dependency**; it departs from the split's stance that modules share nothing (`plan.md` §4.3), for a real functional reason and not for a two-line helper, so the `flatMap` rationale (no fourth artifact for one internal function) is not contradicted. The README sentence "depend only on the one you need" stays true for the four independent artifacts; feeds brings `webtools-http` with it and the artifacts table must say so.
  - The published feeds POM references `webtools-http-jvm` (prior research, measured); which `webtools-http` version it names is the tree's version at publish time (not separately verified). #58 is where "which artifacts share a version" gets documented; that file does not exist yet.
  - **`flatMap`.** Kotlin `internal` means "visible within the same module" and a module is "a Gradle source set"; separate Gradle projects are separate modules. `webtools-feeds` therefore cannot call `webtools-http`'s `internal` helper.
    - Option A (recommended): feeds carries its own copy if it chains `Result`s, with a header naming the siblings. Legacy copies are left untouched; their comments ("the two copies") stay literally true for the pair.
    - Option B: `webtools-http` makes `flatMap` public. Rejected: a public `Result.flatMap` extension becomes Stable Core surface of an artifact whose consumers (BotTools) may define their own, and the explicit-API rule demands KDoc and a semver promise for two lines.
    - Option C: use the stdlib. It has no `Result.flatMap`; `fold` or `mapCatching` are not equivalent (`mapCatching` catches), so this is only an option where no catch-all is wanted.
    - The new `webtools-http` needs its own copy for the same reason; `commonMain` can host it (`Result` is common stdlib).
  - Two name-collision checks pass by construction: top-level `internal` functions in different packages produce different facade classes (`ResultExtensionsKt` per package), and nothing in `webtools-scraping` shares a package with the new modules.

### 4. Repository rules that bind new code

The tracked `.aiassistant/rules/CLAUDE.md` and `.aiassistant/rules/Manifesto.md` have identical content after the front matter (checked with `diff`). They contain four rules:

1. **Paradigm and style.** Idiomatic Kotlin; OOP for domain modelling, FP for data (`val`, `map`/`flatMap`/`filter`).
2. **Error handling.** Never throw for expected failures; encapsulate operational failures in `Result` (`Result.success`/`Result.failure`); prefer functional recovery to `try`/`catch`.
3. **Logging and documentation.** "Include structured logging statements for key lifecycle events, data flows, and failure states."; "For libraries use slf4j and for testing use a logback implementation"; formal KDoc on all public classes, interfaces and functions.
4. **Testing structure.** A standard framework; "separate test classes per file"; never bundle multiple test classes in one file.

House practice observed in the existing modules (repo), which new code is expected to follow:
- "Structured" logging here means parameterised slf4j messages with `{}` placeholders and a `private val log = LoggerFactory.getLogger(X::class.java)` (`Connector.kt:297`–`:299` in a companion; `General.kt:7` at file level). No MDC, key-value or fluent API is used anywhere. `webtools-udp` has 121 log calls in `src/main`.
- Tests assert log output through `captureLogsOf` and `hasWarnContaining` (`webtools-udp/src/test/.../testing/support/webtools/udp/LogCapture.kt`, 105 uses), which attach a logback `ListAppender`. That helper is `internal` to `webtools-udp`'s test source set, so each module keeps its own copy of any such helper under `testing/support/webtools/<module>/`.
- Level-2 tests use hand-written `Fake*` classes (`FakeConnection`, `FakeClientChannel`, `FakePeriodicSchedule`); MockK appears nowhere in the repository.
- Test names are backticked sentences; each class carries a header comment naming its level and a `@Tag` (the new modules' `commonTest` classes cannot carry JUnit tags).
- Existing code logs full URLs (`Connector.kt:86`, `:96`, `:98`). The new stack carries credentials in headers and strips keys from recorded fixtures; the logging rule for new code therefore needs an explicit "never log credentials, and log URLs without their query" line that the existing code has no precedent for.
- Level-2 KDoc with `@param`/`@return`, and `explicitApi()` on the new modules only.

The org-wide rules that also bind this repository (global guidelines): the three stability tiers and "interface plus supplied default" for systems, the audience-reach commenting model, the five-level test hierarchy with the `testing.<level>` package convention, and README currency. They are not restated here.

**The logging tension: facts.**
- slf4j-api is a Java library; it is usable only from `jvmMain` (prior research, measured: it appears only in the `-jvm` runtime variant).
- Which code lands in `commonMain` is decided by the sibling questions: the ports certainly; the budget default, the facade and the failure model possibly. The OkHttp transport, the Caffeine cache and the DNS-level URL guard are JVM by construction. The budget pause on `429`, the hard daily stop, the cache hit/miss/coalesce path and the facade's layering are the "lifecycle events, data flows and failure states" the rule is about; if they are common code, they have no logger today.
- With one `jvm()` target a JVM type in common code compiles silently, so such a seam is not enforced. With a published js target or a check-only js module, every `expect` in `commonMain` needs an `actual` for js, and a missing `actual` is a compile error. `internal expect fun` raises no Beta warning on KGP 2.2.0; `expect class` does (all prior research, measured).
- Tests of log output need logback on the test classpath, which is a `jvmTest` matter either way.

### 5. Baseline: these tests pass today

Measured on a copy of an unmodified `master` tree (via `git archive`), Gradle 9.7.1, JDK 23.0.2 launcher, Windows, command: all seven level tasks across all modules with `--continue`. **Result: `BUILD SUCCESSFUL in 5m 34s`, 33 tasks executed, 0 failures, 0 errors.**

| Module | Level | Test classes | Tests | Skipped |
|---|---|---|---|---|
| `webtools-udp` | 1 gating | 19 | 83 | 0 |
| | 2 component | 21 | 235 | 0 |
| | 3 integration | 20 | 115 | 0 |
| | 4a deterministic | 9 | 96 | 0 |
| | 4b e2e | 11 | 34 | 0 |
| | 4c nonfunctional | 11 | 65 | 0 |
| | 5 uat | 1 | 13 | 13 (`@Disabled`) |
| `webtools-scraping` | 2 component | 1 | 11 | 0 |
| `webtools-browser` | 1 gating | 1 | 3 | 0 |
| | 5 uat | 1 | 2 | 2 (`@Disabled`) |
| **Total** | | 96 | **642 executed**, 15 skipped | |

- No level other than the ones listed has tests: `webtools-scraping` has none at gating/integration/deterministic/e2e/nonfunctional/uat; `webtools-browser` has none at component through nonfunctional.
- Matches the static count of `@Test` methods per level directory (83, 235, 115, 96, 34, 65, 13, 11, 3, 2) and the earlier research: all udp levels and the other modules' gating/component/deterministic levels green with the floor applied (JDK 11 launcher), and gating/component/deterministic green on the restructured copy.
- Unrun: nothing is missing for "these tests pass today" at any level that has tests. The new modules have no tests yet.

### 6. The adoption sweep

Searched `webtools-udp/src/main`, `webtools-scraping/src/main` and `webtools-browser/src/main` for HTTP clients (`http(s)://`, `URLConnection`, `HttpClient`, `java.net.URL`/`URI`, Unirest, Jsoup, skrape, Selenium). `Connector` is the only HTTP client in the repository. Verdicts record that none of these classes is reworked by this change.

| Class / member | What it does | Verdict | Why, and what a later move would meet |
|---|---|---|---|
| `Connector.open` / `next` / `hasNext` / `iterator` / `currentLine` / `close` (`Connector.kt:85`, `:218`, `:229`, `:249`, `:255`, `:178`) | A stateful single-connection line reader over `java.net.URLConnection`. | **Not adopted; hardest to move.** | Blocking and stateful (a second `open` polls `isOpen` with `Thread.sleep(100)`, `:110`–`:121`, `:302`); sets the JVM-wide `http.agent` system property to a browser-spoofing `Mozilla/4.0 ...` string on every connect (`:150`, `:305`–`:306`), the opposite of the new stack's descriptive `User-Agent`; no connect or read timeout, no size cap, no URL policy. `webtools-http` is suspend-only with capped bodies, so there is no counterpart for a streaming line reader; a move means a new API, not a substitution. |
| `Connector.get` (`:263`–`:273`, Unirest) | One-shot GET returning the body as a `String`. | **Not adopted; the easiest later move.** | Equivalent to a raw `webtools-http` GET with the body decoded. Obstacles: `webtools-http` has no blocking variant, so `webtools-scraping` would wrap `runBlocking` or change shape; charset handling and non-2xx semantics must be chosen (`Connector.kt:267` reads `asString().body` with no status check, so a `404` body is a success today); `Connector`'s `init` calls `Unirest.setTimeouts(0, 0)` (`:65`), i.e. infinite timeouts on process-global state. Moving removes unirest 1.4.9 (class-file major 49) and its Apache HttpClient tree. |
| `Connector.skrape` (`:281`–`:295`, skrapeit `HttpFetcher`) | One-shot GET, returns `responseBody`. | **Not adopted; same move as `get`.** | The skrapeit DSL's parsing features are unused: the body is read and returned, so nothing is lost by replacing it. It carries the heaviest transitive tree (Ktor 1.5.4, htmlunit 2.50.0, Kotlin 1.5-era stdlib and coroutines). |
| `Connector.download` (`:198`–`:208`, jsoup + `ImageIO`) | GET bytes, decode to `BufferedImage`. | **Not adopted; partially movable.** | The fetch half equals a capped-bytes GET. The decode half uses `java.awt`/`ImageIO`, which cannot be in `commonMain`; it would stay a JVM helper in `webtools-scraping`. |
| `WebViewer.getPage` / `screenshot` (`WebViewer.kt:41`, `:50`; Selenium) | Drives headless Chrome; `driver.get(url)` at `:63`. | **Not adopted; not an HTTP client.** | Chrome resolves DNS and fetches by itself, so neither the transport nor the DNS-level `Untrusted` guard applies. `WebViewer` accepts any non-blank URL (`:42`); a consumer passing administrator-supplied URLs would need a URL-level pre-check, which is a separate future feature. |
| `resolveLocalAddress()` (`webtools-udp/.../General.kt:32`) | UDP `connect` to `8.8.8.8:80` to read the local address; sends nothing. | **Not adopted; not HTTP.** | Stays in `webtools-udp`. |

- **No legacy API break is forced by this change**: signatures are identical at the floor, and `api(slf4j-api)` is preserved in each module's own build file. The permission to break the legacy modules' API is not used.
- **Co-residence on one classpath** (a consumer using `webtools-scraping` and `webtools-http`): the packages differ (`.scraping` and `.http`), the `internal` helper facades differ, and `Connector`'s two global side effects (`http.agent`, `Unirest.setTimeouts`) do not touch OkHttp. Whether skrapeit's Kotlin 1.5-era coroutines survive a newer coroutines resolved by the new modules is untested (Known gaps).

### 7. The consumer's acceptance checks, mapped to tests here

BotTools publishes these "Done when" lists (quoted in Load-bearing claims):
- KotlinBottools#9 (the adapter): against `mockwebserver3`, the `Untrusted` policy's refusals, 429 handling, size caps; also "the adapter's Level 2 tests, on WebTools' fakes", the composite substituting WebTools with the configuration cache on, and OkHttp aligned with JDA's version.
- KotlinBottools#15 (feeds): against `mockwebserver3` (Level 3), the `Untrusted` policy's refusals, the size caps, conditional GETs.

BotTools' tests run the same behaviour through its adapter; the rows below are the WebTools tests that prove the underlying behaviour, so the first consumer's checks are green on this side first. Test classes are suggested names in the repository's one-class-per-file style, under `com.spartanlabs.testing.<level>.webtools.<module>`.

| Consumer check | Level and module | What the test asserts |
|---|---|---|
| `Untrusted`: scheme and address classification | 4a deterministic, `webtools-http` | Pure table: `http`/`https` allowed; other schemes refused; loopback, link-local, private, unique-local, CGNAT, unspecified, multicast, IPv4-mapped forms classify as refused. (Table content belongs to the URL-policy research.) |
| `Untrusted`: refusal is typed and costs nothing | 2 component, `webtools-http` (`jvmTest`, fake `Dns` into the real transport; fake transport for the facade) | The failure is the typed "URL refused by policy"; the fake transport records zero calls; the budget is not charged; nothing is cached. |
| `Untrusted`: refusal through a real socket stack | 3 integration, `webtools-http` | The real transport pointed at `mockwebserver3` (loopback) fails with the refusal and `server.requestCount == 0`. |
| `Untrusted`: bypass catalogue and redirects | 4c nonfunctional, `webtools-http` | IP-literal and alternate numeric forms, IPv4-mapped IPv6, a name that resolves to a private address, a redirect hop into a private range, rebinding between two resolutions. Redirect and rebinding cases need a first hop that is allowed: see Consideration 1. |
| 429 handling | 2 component, `webtools-http` | Fake transport scripts `429` with `Retry-After` in seconds and as an HTTP date, on the shared fake time: the call yields the typed rate-limited failure with the delay; the budget pauses until `Retry-After` or the window boundary; a call inside the pause fails fast as budget-exhausted without reaching the transport; after advancing fake time it proceeds; the fake records exactly one call (no retry). |
| 429: parsing and window arithmetic | 4a deterministic, `webtools-http` | `Retry-After` seconds, HTTP date, garbage and absent; minute and UTC-day boundaries; a server-reported remaining count only lowers the balance. |
| 429: real transport | 3 integration, `webtools-http` | `mockwebserver3` returns `429` with `Retry-After`; the transport hands back status and headers as a response (not a failure) and `requestCount == 1`. Also `503` with `Retry-After: 0`: OkHttp itself re-sends that request once, whatever `retryOnConnectionFailure` says (Load-bearing claim 2), so a "transport never retries" requirement needs this case to pass. |
| Size caps | 3 integration, `webtools-http` | A body above the cap with and without `Content-Length` (chunked) yields the typed body-too-large failure; the read stops at the cap. |
| Size caps: robustness | 4c nonfunctional, `webtools-http` | A slow-drip body, an endless chunked body and a redirect chain longer than the cap, each ending within the call timeout. |
| Size caps (facade) | 2 component, `webtools-http` | Fake transport serving a response over the cap: the facade surfaces body-too-large and does not call the decoder. |
| Conditional GET (transport side) | 3 integration, `webtools-http` | A `304` reaches the caller as a response with its headers (a mock `Dispatcher` answers `304` when it sees `If-None-Match`). |
| Conditional GET (feeds side) | 2 component, `webtools-feeds` | The request carries `If-None-Match`/`If-Modified-Since` from stored validators; `304` returns "not modified" and leaves the validators; `200` captures `ETag`/`Last-Modified`. |
| Conditional GET (feeds, real stack) | 3 integration, `webtools-feeds` | The same over `mockwebserver3` with a dispatcher that checks the request headers. Needs the loopback seam if it runs through `Untrusted`. |
| Parser hardening (not on the two lists, but part of #15's safeguards) | 2 component and 4c nonfunctional, `webtools-feeds` | DOCTYPE and external entities refused; oversized documents. Owned by the feed-parsing research. |
| "Adapter's Level 2 tests on WebTools' fakes" | Published fixtures | The fake transport, the shared time source and the real-transport-over-`mockwebserver3` helper are usable from a Gradle consumer's test classpath via `testFixtures(...)`. |
| Composite substitution with the configuration cache; OkHttp aligned with JDA | Build-level publication check, `webtools-http` | Substitution of `webtools-http` and its fixtures capability through `includeBuild`, a repeated invocation reusing the configuration cache entry, and `okhttp-jvm` 5.5.0 published as an ordinary requirement rather than `strictly` (prior research measured substitution and cache reuse; none of it is encoded as a repository check yet). |

### 8. Other things the plan must not miss

- **Package filter relies on exact level segments.** A test class outside `com.spartanlabs.testing.{gating,component,integration,deterministic,e2e,nonfunctional,uat}.*` never runs in a level task (it still runs in `test`/`jvmTest`) because `isFailOnNoMatchingTests = false`. A mis-named package is silent; a small check that every test class sits in a level package would close it (optional).
- **Published fixtures and `testing/support/` are different things.** `testing/support/webtools/http/` is private to the module's own tests (as in `webtools-udp`); `src/jvmTestFixtures` is published. Do not give the fixtures a `com.spartanlabs.testing.*` package; use a package under `com.spartanlabs.webtools.http`.
- **`logback-test.xml`**: today three byte-identical copies (`webtools-udp`, `webtools-scraping`, `webtools-browser` under `src/test/resources/`). Each new module gets its own in `jvmTest` resources (or `src/test/resources` for a JVM-only feeds). The fixtures jar must not carry one.
- **No CI exists** and no `CONTRIBUTING.md`, `CHANGELOG.md` or tags (WebTools#58). Nothing to update for CI. The organisation's rule that an Experimental promotion is recorded in the CHANGELOG has no file to land in until #58 creates one; the existing change history of a module lives in the comment block of its build file (`webtools-udp/build.gradle.kts:4`–`:100`).
- **#47's documents** go in `docs/plans/55-http-client-stack/47-java-23-bytecode/` (plan or plan units, `impact.md`, `final-implementation.md`), beside its existing `research/`.
- **Gradle runs on JDK 17 to 26** (documented); `Requires JDK 11` in the README is the consumer floor, not the build floor.

## Recommendation for this repository

1. **Scope.** Treat the change as three layers: (a) a build layer (`build-logic`, `settings.gradle.kts`, root `build.gradle.kts`, the three module build files), (b) two new module trees, and (c) `README.md` plus one new evergreen document. Leave every legacy source and test file alone; the legacy modules change only in their build files (plugin line, `api(slf4j-api)`, optional version).
2. **Move the `webtools-udp` port lock verbatim.** Apply the convention plugin in the module's `plugins {}` block and keep `:103`–`:117` as they are. Do not add a lock to the new modules.
3. **No existing class moves onto the stack in this change.** Record the verdicts in Finding 6 in the plan's impact document and the README note on `Connector`. A later change replaces `Connector.get`/`skrape` first, `download` second, and treats the line reader as a new API.
4. **`flatMap`: a private copy in `webtools-http` and, only if feeds chains `Result`s, a private copy in `webtools-feeds`.** Do not publish it. Name the sibling copies in the new headers; leave the two legacy files as they are.
5. **README and docs.** Make the edits in Finding 2. Add `docs/webtools-http-architecture.md` (Mermaid sequence diagram of cache, budget and transport; where each policy check runs; scope ownership; failure mapping; tier table; test-support assembly). No protocol document. Feeds documented in the README only.
6. **Map the consumer's checks to the rows in Finding 7** and write the `Untrusted` size-cap and conditional-GET tests only once the loopback seam of Consideration 1 is decided; write refusal, 429 and fake-transport tests first.
7. **Reasoning.** The build layer is where the blast radius is: it replaces the root `subprojects {}` that all three modules depend on, but prior research measured the three modules' published output unchanged. The legacy modules gain nothing from being reworked and the requirement forbids it; leaving them alone also keeps `webtools-udp`'s API and the 628 passing udp tests exactly as they are.

## Considerations

### 1. How tests run the `Untrusted` policy against a loopback mock server

- **Fact.** `MockWebServer.start()` binds `localhost`; `Untrusted` rejects loopback. A real-transport test through `Untrusted` is refused before any request. The refusals check is unaffected (it wants exactly that). Size caps, conditional GETs, redirect-hop refusal and rebinding through `Untrusted` cannot reach a response. KotlinBottools#15 lists conditional GETs and size caps under `Untrusted` against `mockwebserver3`; its feeds job fetches through an `Untrusted` client.
- **Options.**
  - **(A) The policy's address rules are a constructor parameter** (default: the full deny list), and the published fixtures provide a factory that builds `Untrusted` with loopback permitted. Consistent with "parameterise policy"; the main artifact gains a public knob that can switch the guard's rules off, which is a foot-gun that needs KDoc and perhaps the Experimental marker.
  - **(B) A fixtures-only factory that reaches an `internal` constructor** (the fixtures compilation is associated with `main`, so it can read `internal` declarations; prior research measured). The main artifact exposes no way to loosen the guard; the factory is public in the fixtures jar so a consumer's adapter tests can use it.
  - **(C) Test refusals only under `Untrusted`; test caps and conditional GETs under `Trusted` against the mock.** No seam, but the `Untrusted` cap and timeout values are then never proven end to end.
- **Recommendation: (B).** It keeps the production API closed, gives BotTools' adapter tests a way to reach a response, and leaves (A) available later. It depends on the URL-policy and API-shape research agreeing that the policy type has an `internal` construction path.

### 2. Logging from code in `commonMain`

- **Options** (not settled here; the right one depends on which code is common).
  - **(1) Every logging point in `jvmMain`.** Common code reports only through `Result` failures. Consistent with the build-topology recommendation (no logging library in `commonMain`). The budget pause, hard daily stop and cache path log nothing if they are common code; moving the facade to `jvmMain` contradicts "ports and facade in common".
  - **(2) An `internal expect fun` logging seam**, with a slf4j `actual` in `jvmMain` (and a stub in a check-only module, if one exists). Satisfies the rule for common code; `expect fun` raises no Beta warning on KGP 2.2.0 (`expect class` does). Cost: a function-shaped API with a logger-name argument and varargs.
  - **(3) A consumer-visible event or listener port in `commonMain`**, with a default `jvmMain` implementation that logs through slf4j. Gives consumers the "expose hooks" extension point and removes logging from common code; adds API surface that falls under the Experimental marker and tier rules.
- **Recommendation:** decide after the facade and budget placement is fixed. If they are in `commonMain`, (2) is the smallest option that meets the rule; if they sit in `jvmMain`, (1). Prefer not to add (3) unless a consumer is expected to want the events.

### 3. Where the Java floor constant lives

- **Fact.** The requirement reads "set once in the root build". The measured topology holds `WebToolsBuild.JAVA_FLOOR` in the included `build-logic` build and leaves the root build file with `group`/`version` only; a module build script can read it (prior research, measured, including from a consumer's nested composite).
- **Options.**
  - **(A) The constant in `build-logic`** (measured). One value, read by both convention plugins and by module scripts.
  - **(B) A property in the root `gradle.properties`**, read by the plugins through `providers.gradleProperty(...)`. Matches the words literally. Not measured in a consumer's nested composite, where it would first have to be established which build's `gradle.properties` the included build reads.
- **Recommendation: (A).** It is the measured shape and no consumer-side property resolution is involved.

## Known gaps

- **Configuration cache enablement.** The repository's `gradle.properties` does not enable it; whether to enable it repository-wide is not decided here. The requirement is compatibility, which prior research measured.
- **Location and mechanism of the build-level publication check** (a `build-logic` test, TestKit, or a verification task) is not established, and whether `build-logic` itself gets level tests under the five-level hierarchy.
- **The release version of `webtools-scraping` and `webtools-browser`** (`1.0.0a` or `1.0.1`) is an open Consideration in the Java 11 floor findings; this document only notes that a lettered version needs an explicit `version` line in each module's build file.
- **The shape of `Untrusted`, the facade and budget placement, and the common-code guarantee** are other questions' answers; Findings 4 and 7 depend on them and name the dependency.
- **Co-resident classpath of `webtools-scraping` and `webtools-http`** (skrapeit's Kotlin 1.5-era coroutines against the newer ones) was not tested.
- **The IDE experience** (importing `build-logic`, the `jvmTestFixtures` source set) is untested.
- **Where an Experimental-to-Supported promotion is recorded** before #58 creates a CHANGELOG is not decided; the build-file comment block is the only precedent.
- **The coroutine-cancellation interaction with the repository's `runCatching` idiom** (rule 2 prefers `Result` helpers; `runCatching` in a `suspend` function also catches cancellation) is stated from general knowledge of kotlinx.coroutines and not sourced here; it belongs to the API-shape research.
- **Not run:** the repository build with the new structure, since none of it exists yet; the baseline is the unmodified tree only. The earlier probes (sibling findings) cover the restructured copy.
- **Windows only; JDK 23 launcher for the baseline.**

## Load-bearing claims

1. **`mockwebserver3` 5.5.0 starts on `localhost` by default.** `public fun start(port: Int = 0) {` followed by `start(InetAddress.getByName("localhost"), port)` in `MockWebServer`. <https://github.com/square/okhttp/blob/parent-5.5.0/mockwebserver/src/main/kotlin/mockwebserver3/MockWebServer.kt>, tag `parent-5.5.0` (OkHttp 5.5.0).
2. **OkHttp 5.5.0 itself re-sends a request on `503` with `Retry-After: 0`, and gates its `408` re-send on `retryOnConnectionFailure`.** `HTTP_UNAVAILABLE -> {` ... `if (retryAfter(userResponse, Integer.MAX_VALUE) == 0) {` / `// specifically received an instruction to retry without delay.` / `return userResponse.request`; and under `HTTP_CLIENT_TIMEOUT -> {`: `if (!chain.retryOnConnectionFailure) {` / `// The application layer has directed us not to retry the request.` <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt>, tag `parent-5.5.0`.
3. **Kotlin `internal` stops at the module, and a Gradle source set is a module.** "The `internal` visibility modifier means that the member is visible within the same module. More specifically, a module is a set of Kotlin files compiled together, for example:" ... "A Gradle source set (with the exception that the `test` source set can access the internal declarations of `main`)." <https://kotlinlang.org/docs/visibility-modifiers.html>, read 2026-10-21.
4. **Gradle 9.7.1 needs JDK 17 to 26 to run.** "A JVM version between 17 and 26 is required to execute Gradle." <https://docs.gradle.org/9.7.1/userguide/compatibility.html>, Gradle 9.7.1.
5. **A Gradle test-fixtures variant resolves only with Gradle Module Metadata; it is published with a `test-fixtures` classifier artifact.** "if the external project is *not* publishing Gradle Module Metadata, then resolution will fail" and "By convention, test fixtures will be published with an artifact having the `test-fixtures` classifier." <https://docs.gradle.org/9.7.1/userguide/java_testing.html>, Gradle 9.7.1.
6. **BotTools' acceptance lists.** KotlinBottools#9 "Done when": "the adapter passes against `mockwebserver3`, covering:" / "the `Untrusted` policy's refusals" / "429 handling" / "size caps"; Scope: "the adapter's Level 2 tests, on WebTools' fakes"; "the composite substitutes WebTools". KotlinBottools#15 "Done when": "these pass against `mockwebserver3` (Level 3):" / "the `Untrusted` policy's refusals" / "the size caps" / "conditional GETs". <https://github.com/SpartanLaboratories/KotlinBottools/issues/9> and <https://github.com/SpartanLaboratories/KotlinBottools/issues/15> (private repository), read 2026-10-21.
7. **A Maven consumer of a KMP module needs the `-jvm` coordinate.** "A Maven consumer therefore needs `io.github.spartanlaboratories:webtools-http-jvm`." `docs/plans/55-http-client-stack/research/kmp-build-topology.md` (Finding F5), measured with vanniktech 0.36.0, KGP 2.2.0, Gradle 9.7.1, 2026-10-20.
8. **The Java floor and the three modules.** "No legacy module has to break its API for the floor." (Bottom line) and "Public and protected signatures (`javap -protected`) of every class of the three modules, floor build versus current build: zero differing lines". `docs/plans/55-http-client-stack/47-java-23-bytecode/research/java-11-floor.md` (Finding F5), measured 2026-10-20.
