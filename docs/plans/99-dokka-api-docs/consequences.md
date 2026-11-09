# Issue #99 — Dokka API documentation, project site and 1.0.0 preparation (consequences)

- **Covers:** `SpartanLaboratories/WebTools#99`, *"Generate API docs with Dokka: javadoc jars, GitHub Pages site and 1.0.0 release preparation"*.
- **What this covers:** what the repository, its published artifacts and the neighbouring issues gain or lose with this change, and what it leaves behind. The file-level detail is in [`plan.md`](plan.md).
- **Status:** built at the code-landing commit `2c634f4`. The as-built record, with its measurements and deviations, is [`final-implementation.md`](final-implementation.md).
- **Baseline:** `master` at `5ae9734`; branch `feat/issue-99-dokka-api-docs`.
- **Related docs:** [`plan.md`](plan.md); the organization standard [`documentation/dokka.md`](https://github.com/SpartanLaboratories/.github/blob/main/documentation/dokka.md); the worked example `SpartanLabsGaming/MyGameTools`.
- **The systems:** the registered `Gradle build` system gains Dokka and the aggregated publication. The two workflows and the site belong to no system in `docs/dashboards/systems.json` today.

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-udp` | The javadoc jar carries the Dokka reference in place of an empty placeholder. KDoc comments only in the source; no public API change. `2.0.1` is also the first release of what the never-published `2.0.0a` holds: the malformed-handshake refusal (#38) and the Java 11 floor (#47). | `2.0.0a` to `2.0.1`, a candidate for the release after the landing |
| `webtools-http`, `webtools-http-jvm` | The javadoc jars carry the Dokka reference, the published test fixtures included (package `com.spartanlabs.webtools.http.testing`). KDoc comments only in the source; the ABI dump is identical. The "UNRELEASED" build note goes. | `1.0.0`, unchanged; a candidate for the release after the landing |
| `webtools-feeds`, `webtools-feeds-jvm` | The javadoc jars carry the Dokka reference. KDoc comments only in the source; the ABI dump is identical. The "UNRELEASED" build note goes. The issue text names only `webtools-http`; `webtools-feeds` is prepared the same way because the two release together. | `1.0.0`, unchanged; a candidate for the release after the landing |
| `webtools-scraping`, `webtools-browser` | They get Dokka and appear at `/api/` like the others, and each gains one KDoc comment. Nothing is republished: Maven Central keeps `1.0.0`, with its Java 23 bytecode and placeholder javadoc jar. | `1.0.0a` in the build files, unchanged and never published; `1.0.0` on Maven Central; no release |
| `webtools-http-commoncheck`, `webtools-feeds-commoncheck` | Nothing: unpublished, and Dokka is not applied. | not published |

This change alters no published module's API or runtime dependencies. A consumer moving from Maven Central's `webtools-udp` 2.0.0 to 2.0.1 gets the populated javadoc jar and the two fixes of `2.0.0a`, listed in the CHANGELOG's `[2.0.1]` section; three malformed handshake shapes change on the wire.

## 2. Blast radius in this repository

### Build

- `settings.gradle.kts` gains `pluginManagement.plugins`; the root `build.gradle.kts` gains the Kotlin plugins with `apply false`, Dokka, `repositories`, the five `dokka(project(...))` lines and the theme.
- The Kotlin and Dokka versions now live in two places that must agree: `settings.gradle.kts` and `build-logic/build.gradle.kts`. A difference shows no symptom in the build; `RootScriptsAgreementGatingTest` compares them.
- `webtools.published-library` applies Dokka and turns on `failOnWarning` and `reportUndocumented`; both library plugins set the Dokka javadoc jar through `WebToolsBuild.DOKKA_PUBLICATION_TASK`. `webtools-http` unsuppresses its `jvmTestFixtures` source set.
- **Every future change is gated on KDoc.** A broken KDoc link or an undocumented public declaration now fails each module's Dokka tasks, the javadoc jar and so any publish, the CI job and, for `webtools-http`, `:build-logic:fixturesPublicationCheck`, which makes it fail `./gradlew build` and `check` as well. `assemble` and the module test tasks do not run Dokka.
- `docs/dokka/styles/dokka-styles.css` and `docs/brand/spartan-laboratories-logo.png` become required inputs of every Dokka task: moving either fails the build.

### Source

Comments only, in the five modules: 8 broken links fixed and 155 KDoc blocks added across 69 files (the inventory is in [`plan.md`](plan.md), Appendix A). No signature changes.

### build-logic tests

- `ModuleSource.rootBuildFile()` is replaced by `rootBlock(fileName, blockName)`; the generated builds no longer copy the root build file whole, because it now names modules they do not hold. `SampleProject` carries placeholder theme files and a documented `Greeter`.
- `PublishingConventionIntegrationTest` changes its contract: both library plugins now ship a Dokka javadoc jar.
- New: a gate on what the root scripts, build-logic and the two workflows must agree on, the Dokka convention integration test, the aggregation end-to-end test (built on the real root `pluginManagement`, `plugins` and `dokka` blocks) and a configuration-cache check.
- The nested-build tasks now also run Dokka, so a cold TestKit home also downloads Dokka's generator.

### Repository

- The repository's first workflows: `.github/workflows/ci.yml` and `pages.yml`. `ci.yml` runs one job.
- `gradlew` becomes executable (`100755`) in the index; `.gitattributes` pins LF for it and for the workflows.
- GitHub Pages is enabled once with GitHub Actions as its source, a repository setting outside the change set, before the merge. The site then lives at `https://spartanlaboratories.github.io/WebTools/`.
- `website/` holds hand-written version numbers (the Modules table and the Install snippets) that must be kept in step with the modules' build files at each release.

### Documentation

`README.md` gains the site and reference links and the Dokka command, and loses every "not yet on Maven Central" and "not yet released" line for `webtools-http` and `webtools-feeds`. `CHANGELOG.md` gets `## [2.0.1]` and `## [1.0.0]` sections; `[Unreleased]` empties.

## 3. Adoption

| Consumer of the old javadoc-jar mechanism | Verdict |
|---|---|
| `webtools.kotlin-jvm-library` (`webtools-udp`, `webtools-scraping`, `webtools-browser`), today the automatic `Javadoc()` jar | Moves to `JavadocJar.Dokka`, set explicitly. |
| `webtools.kotlin-multiplatform-library` (`webtools-http`, `webtools-feeds`), today pinned to `JavadocJar.Empty()` | Moves to `JavadocJar.Dokka`. |
| `webtools.kotlin-common-check` | Not applicable: unpublished. |

## 4. Sibling units

None: this issue has no parent and no sub-issues.

## 5. Neighbouring issues

- **#58 (repository practices):** `ci.yml` now exists with the `docs` job, so #58's build and test jobs extend that file instead of creating it. Its `CHANGELOG.md` request is already met; its `CONTRIBUTING.md`, tags, release workflow and foojay bump are untouched. #58 stays open.
- **#55 and #69 (closed):** the build notes that held `webtools-http` and `webtools-feeds` back until the stack was complete are removed.

## 6. Known gaps

- **Little asserts the generated output of the real root.** The CI job runs the real root task and fails on any Dokka warning, and `pages.yml` fails if `build/dokka/html/index.html` is missing, on `master` only and for that one file. Nothing asserts the rest of what the task wrote: the five modules, the fixtures package and the theme. Each way of losing part of that output has its own guard: a module dropped from the aggregate (the Level 1 gating test); modules rendering empty and the theme not reaching the aggregate (the Level 4b aggregation test, on the real root `plugins` and `dokka` blocks); the fixtures package leaving the reference (the fixtures-publication check reads it in the javadoc jar, and the aggregate is built from the same module configuration). A direct check could live in two places. Inside the build-logic suite it would take a nested Dokka run over a copy of the whole repository. In the CI job, where the real output is already on disk, it would be a few lines of shell, at the cost of a second copy of the module list and of a step that runs only on GitHub. Either would catch little those guards do not. The manual acceptance checks in [`plan.md`](plan.md), section 9, read the deployed site.
- **The two workflows are exercised only on GitHub.** Nothing local runs the `docs` job or the site flow of `pages.yml`: `Assemble _site`, the `api/` mount, the brand copy, `.nojekyll` and the `::error::` path. The Level 1 gating test holds both files to the Dokka task's name and nothing more. `ci.yml`'s single job is the state at this change: #58 adds build and test jobs to that file by design, so no test counts its jobs. The `docs` job first runs on this change's pull request, and `pages.yml` on the push to `master`, which is also the first deploy: until then the two URLs of R8 and the manual acceptance checks in [`plan.md`](plan.md), section 9, are unverified. GitHub Pages itself is enabled with `build_type: workflow`.

## 7. Follow-ups

All open. The first five are [`plan.md`](plan.md), section 12.

- A later release of `webtools-scraping` and `webtools-browser`, so Maven Central carries their Java 11 builds (`1.0.0a` in the build files, never published) and javadoc jars with the reference.
- CI build and test jobs: #58.
- A check that the site's displayed versions match the modules' build files.
- Tests of the site's HTML and CSS.
- Moving the fixtures' Dokka line into the convention plugin when a second module publishes fixtures.
- `pages.yml` grants `pages: write` and `id-token: write` at workflow level, so the `build` job that runs `./gradlew` inherits them. Scoping them to the `deploy` job would be least privilege. MyGameTools' workflow has the same shape.
- The organization standard's "a broken KDoc link fails it" holds only with `failOnWarning`, and MyGameTools does not set it: a default Dokka run with broken links succeeds (`SpartanLaboratories/.github#6`).

## 8. The plan's assumptions, as built

Of the assumptions in [`plan.md`](plan.md), section 3, one failed:

- **`README.md:793` is stale once the fixtures-publication check runs Dokka: did not hold.** The check takes 52 to 60 s on a warm run ([`final-implementation.md`](final-implementation.md), section 3), so "about a minute" still stands and the sentence does not change. It already says that the first run needs the network.

The rest:

- **The final Dokka task ends warning-free:** held ([`final-implementation.md`](final-implementation.md), section 3).
- **The releases follow straight after the landing:** not yet known; until then the Install snippets name versions that do not resolve.
- **The logo's navy lettering needs the light glow:** not yet seen on the deployed site; it is one of the manual acceptance checks.
