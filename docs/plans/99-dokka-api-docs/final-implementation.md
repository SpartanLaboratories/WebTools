# Final implementation: Dokka API documentation, project site and the 1.0.0 release preparation (Issue #99)

The as-built record of `SpartanLaboratories/WebTools#99`. It states what was built, where the build departs from [`plan.md`](plan.md) and why, the measurements
that show it holds, and the acceptance of each requirement. What the change does to the repository and its published artifacts, and the known gaps and follow-ups
it leaves, are in [`consequences.md`](consequences.md).

The code landing is `2c634f4` on `feat/issue-99-dokka-api-docs`, five commits above `master` at `5ae9734`: `0cf4e2c`, `f2ca518`, `7d61d02`, `c3e3500` and
`2c634f4`.

## 1. What was built

- **Dokka on the five published modules.** Dokka 2.3.0 in V2 mode, applied by `webtools.published-library`, with `failOnWarning` and `reportUndocumented` on,
  the Spartan Laboratories theme and "view source" links to GitHub. `webtools-http` unsuppresses `jvmTestFixtures`, so its published test fixtures are part of the
  reference. The two `*-commoncheck` modules have no Dokka task.
- **Javadoc jars.** Both library convention plugins set `JavadocJar.Dokka(WebToolsBuild.DOKKA_PUBLICATION_TASK)`, so every published module's javadoc jar is its
  Dokka output.
- **One aggregated reference.** The root project applies Dokka, holds the Kotlin plugins on its classpath with `apply false`, and declares the five modules. The
  output is `build/dokka/html`, named `WebTools`, with no version of its own.
- **The KDoc.** The 8 broken links are fixed and the 155 undocumented public declarations documented; no signature changed.
- **CI and the site.** `.github/workflows/ci.yml` has one job, `docs`, which runs the Dokka task. `.github/workflows/pages.yml` assembles `website/`, `docs/brand/` and
  the reference at `/api/` and deploys them. `website/` is the landing page. The logo is `docs/brand/spartan-laboratories-logo.png` and the stylesheet
  `docs/dokka/styles/dokka-styles.css`, in navy and bronze.
- **The repository.** `gradlew` is tracked executable, `.gitattributes` pins LF for it and for the workflows, `.gitignore` holds the site's generated paths, and
  GitHub Pages is enabled with `build_type: workflow`.
- **The 1.0.0 preparation.** The "UNRELEASED" notes are gone from `webtools-http/build.gradle.kts` and `webtools-feeds/build.gradle.kts`; `CHANGELOG.md` and
  `README.md` follow [`plan.md`](plan.md), section 8. `webtools-udp` is at `2.0.1`, the only version that moved.
- **The checks.** In build-logic: `RootScriptsAgreementGatingTest` (new), `DokkaConventionIntegrationTest` (new), `DokkaAggregationEndToEndTest` (new),
  `DokkaConfigurationCacheReuseNonFunctionalTest` (new), and the changed `BuildLogicPluginsGatingTest`, `PublishingConventionIntegrationTest`,
  `CommonCheckPluginIntegrationTest`, `FixturesPublicationEndToEndTest` and `FixturesCheckWiringEndToEndTest`, with `ModuleSource.rootBlock` replacing
  `rootBuildFile`.

## 2. Deviations

Each departs from the section of [`plan.md`](plan.md) it names. No research or parent architecture stands behind these sections, so the plan is the only basis.

### D1. `toString` KDoc of the types whose class KDoc fixes the exact string

*Plan: section 6 D, writing guidance, `toString` row.* The member KDoc of these 17 overrides states the string the class already promises and omits the "format is
not part of the API and may change" clause, because the class KDoc makes the exact text part of the contract and the clause would contradict it: the eight name types
(`FeedLimit`, `FeedRefusal`, `NotAFeedReason`, `ExhaustedReason`, `PauseSource`, `RefusalReason`, `StoreOperation`, `HttpMethod`: `toString()` is the name), the
seven fixed literals (`BudgetObserver.None`, `CallBudget.Unlimited`, `InMemoryBudgetStore`, `Lookup.Missing`, `RateSignalsReader.Standard`, `ResponseCache.Disabled`,
`WallClock.System`), `CidrBlock` (the canonical block) and `HttpHeaders.Entry` (`Entry(<name>)` or `Entry(<name>, sensitive)`). Every other `toString` of Appendix A
carries the clause.

### D2. `UdpChannel.Companion` KDoc

*Plan: section 6 D, `Companion` row.* The KDoc names the reliable message-size constants the companion declares (`DEFAULT_MAX_RELIABLE_MESSAGE_BYTES`,
`MAX_RELIABLE_MESSAGE_BYTES`). The plan's example calls them channel-numbering constants, which the companion does not hold.

### D3. `BuildLogicPluginsGatingTest` class KDoc

*Plan: section 6 I.* The class KDoc had no list of plugins to add "dokka" to, so it now names the plugins in a parenthesis ("Kotlin, vanniktech,
binary-compatibility-validator and dokka").

### D4. `DokkaConventionIntegrationTest` asserts the publication task in its failure cases

*Plan: section 9, the Level 3 row.* Each of the four failure tests asserts that `:dokkaGeneratePublicationHtml` is `FAILED`, not `:dokkaGenerateModuleHtml`: in these
single-project samples the publication task is the one that runs and fails, and `:dokkaGenerateModuleHtml` has no outcome. The module task is asserted where it runs,
in the multi-module composite of `DokkaAggregationEndToEndTest`.

### D5. `RootScriptsAgreementGatingTest` reads its files more strictly than the plan's text

*Plan: section 6 I, the class as given, and section 9, its Level 1 row.* The class drops `//` line comments before matching, through a private `uncommented(text)` that
steps over quoted strings so that a `//` inside a URL stays. `markerVersion` and `publishes` are anchored at the start of their line. `includedModules` takes each
`include(...)` statement at the start of a line or after a `;`, and each quoted argument with or without its leading `:`. The class KDoc says so, and states that block
comments are not handled; these scripts have none.

The plan's patterns could still count commented-out text: a marker dependency in a comment, a commented argument inside a multi-line `include(...)`, or a plugin id
mentioned in a module's comment. They also missed two statements on one line and a module named without its colon, both of which Gradle accepts. A version mismatch or a
dropped module could then pass, and this test is the only place either shows ([`plan.md`](plan.md), section 2).

### D6. The commits landed in a different grouping

*Plan: section 11, its commit list and "the stages are independently landable", and section 12, the order.* `0cf4e2c` holds the KDoc and the plan; `f2ca518` the Dokka
build, both workflows, the theme files and the test harness; `7d61d02` the executable bit of `gradlew` and `.gitattributes`; `c3e3500` `website/` and `.gitignore`;
`2c634f4` the `webtools-udp` bump. `pages.yml` is in `f2ca518` and not in the website commit, because `RootScriptsAgreementGatingTest` reads both workflows and fails on a
tree without one.

So the commits are not independently landable. `f2ca518` needs `0cf4e2c`, or its Dokka task fails on the undocumented declarations, and its workflows can run on Linux only
after `7d61d02` makes `gradlew` executable. They land together.

### D7. `README.md`, the site and the CHANGELOG state what Maven Central holds

*Plan: sections 6 F and 8.* The plan took the lettered versions in the build files for published ones. Maven Central holds `webtools-udp` up to `2.0.0`, and `webtools-scraping`
and `webtools-browser` at `1.0.0` only; `2.0.0a` and `1.0.0a` were never published. So the site's Modules table names `1.0.0` for those two modules where section 6 F says
`1.0.0a`; the README's Install note and Java floor paragraph say the floor starts with `webtools-udp` 2.0.1 and that the two archived modules' published builds need JDK 23 or
newer; and the CHANGELOG's `[2.0.1]` section lists what it carries from `2.0.0a`, which section 8 leaves out.

### D8. The README keeps "about a minute" for the fixtures-publication check

*Plan: section 8, `README.md` item 7.* The sentence stays instead of being replaced with a measured time: the check measured 52 to 60 s warm, so it is still true.

## 3. Measurements

All on the development machine (Windows, JDK 23, Gradle 9.7.1).

**The build-logic tests: 132, all passing.**

| Level | Tests |
|---|---|
| Level 1 gating | 6 |
| Level 2 component | 36 |
| Level 3 integration | 52 |
| Level 4a deterministic | 9 |
| Level 4b end-to-end | 25 |
| Level 4c non-functional | 4 |

No module's own test suite ran, because the five modules changed only in comments, two build notes, the fixtures' Dokka line and `webtools-udp`'s version. Three other
results stand in for them: `:webtools-http:apiCheck` and `:webtools-feeds:apiCheck` pass, so the two ABI dumps are unchanged; `./gradlew assemble` compiles every module;
and the Dokka task below reads every module's source.

**The Dokka task.** `./gradlew dokkaGeneratePublicationHtml --rerun-tasks` completes with no Dokka warning.

**The aggregated reference**, in `build/dokka/html`, with `styles/dokka-styles.css` and `images/spartan-laboratories-logo.png` at its root and
`webtools-http/com.spartanlabs.webtools.http.testing/` holding the fixtures:

| Module | HTML pages |
|---|---|
| `webtools-udp` | 158 |
| `webtools-scraping` | 15 |
| `webtools-browser` | 9 |
| `webtools-http` | 729 |
| `webtools-feeds` | 191 |

**`:build-logic:fixturesPublicationCheck`:** 52 to 60 s on a warm run whose inputs changed.

## 4. Acceptance

| Requirement ([`plan.md`](plan.md), section 1) | State |
|---|---|
| R1 Dokka on the five modules, not the check-only two | Met. `CommonCheckPluginIntegrationTest` shows no Dokka task on a check-only module. |
| R2 javadoc jars | Met. `PublishingConventionIntegrationTest` for both library plugins and check 11 of `FixturesPublicationEndToEndTest` for the real module. |
| R3 one aggregated publication | Met (section 3). |
| R4 CI job, nothing but a clean run passes | Met. `DokkaConventionIntegrationTest` and `DokkaAggregationEndToEndTest` hold the failure cases; the real run is clean (section 3). |
| R5 test fixtures in the reference | Met (section 3). |
| R6 scraping and browser: no version move, no release | Met. Each gains one KDoc comment and neither version line changed. |
| R7 one CI job | Met. `ci.yml` holds the one job, `docs`. |
| R8 landing page and Pages deployment | Built, and not yet observed: the two URLs answer only after the first deploy, which the merge triggers ([`consequences.md`](consequences.md), section 6). |
| R9 branding | Met (section 3); each module's own publication holds the same two theme files (`DokkaConventionIntegrationTest`). |
| R10 1.0.0 preparation | Met. |
| R11 `webtools-udp` 2.0.1 | Met. |
| R12 Pages enabled | Met. `build_type` is `workflow`. |
| R13 versions and aggregated modules agree | Met. `RootScriptsAgreementGatingTest`. |
