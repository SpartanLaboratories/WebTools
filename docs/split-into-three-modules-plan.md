# Split WebTools into three modules and artifacts

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#15` — a design-flaw cleanup:
  `WebTools` bundles three unrelated concerns (a UDP connection layer, HTML
  scraping, headless-browser screenshots) into one artifact, so every consumer
  that wants the UDP layer also drags in Selenium, skrapeit, jsoup and unirest.
- **Branch:** `refactor/split-into-three-modules` (off `master`).
- **Commit(s):** the 5-commit sequence in §8, landed. See §10 for deviations.
- **PR:** TBD.
- **Status:** implemented on the branch; `./gradlew build` green (135 tests: 119
  udp + 11 scraping + 5 browser; 0 failures, 5 `@Disabled` UAT). Awaiting PR.
- **Issue:** filed as `SpartanLaboratories/WebTools#15`.
- **Target artifacts / versions (settled):**
  - `io.github.spartanlaboratories:webtools-udp:1.0.0`
  - `io.github.spartanlaboratories:webtools-scraping:1.0.0`
  - `io.github.spartanlaboratories:webtools-browser:1.0.0`
  - `io.github.spartanlaboratories:WebTools` — frozen at `2.0.1a`, no further
    releases; README documents the migration.
- **Related:** `docs/issue-3-public-client-handshake-plan.md`,
  `docs/issue-5-prune-stale-registrations-plan.md` (both entirely within the
  future `webtools-udp` module).

### Settled decisions (from user)

| # | Decision | Choice |
|---|---|---|
| D1 | Repo layout | **One repo, Gradle multi-module** — `:webtools-udp`, `:webtools-scraping`, `:webtools-browser` subprojects under this repo, one `settings.gradle.kts`, shared config in the root build. |
| D2 | Coordinates | **`webtools-udp` / `webtools-scraping` / `webtools-browser`** under the existing `io.github.spartanlaboratories` group. Old `WebTools` artifact retired (not kept as an aggregator). |
| D3 | Starting version | **`1.0.0`** for all three — new artifact identities, fresh version lines. |
| D4 | Packages | **Split into sub-packages** — `com.spartanlabs.webtools.udp`, `.scraping`, `.browser`. No split packages across jars; consumers update imports. |

---

## 1. Context

### 1.1 Current state (one artifact, three concerns)

`src/main/kotlin/com/spartanlabs/webtools/` — 15 source files, one flat package,
one `build.gradle.kts` with this dependency set:

| Dependency | Scope | Used only by |
|---|---|---|
| `org.slf4j:slf4j-api:2.0.13` | `api` | all three concerns |
| `it.skrape:skrapeit:1.1.5` | `implementation` | `Connector` |
| `org.jsoup:jsoup:1.15.4` | `implementation` | `Connector` |
| `com.mashape.unirest:unirest-java:1.4.9` | `implementation` | `Connector` |
| `org.seleniumhq.selenium:selenium-java:4.0.0` | `implementation` | `WebViewer` |
| `io.github.spartanlaboratories:GeneralTools:2.0.1` | `api` | **nothing** — unused (grep-confirmed) |

Plus `src/main/resources/` carries **~30 MB of committed webdriver binaries**
(`chromedriver-win64/`, `chromedriver_win32/`, `firefoxdriver/`) that are packed
into the published jar and are not even referenced correctly (`WebViewer` points
`System.setProperty` at hardcoded absolute paths like
`D:/Documents/Programming/WebTools/chromedriver-win64/chromedriver.exe`).

### 1.2 Concern → file map

**UDP connection layer** (slf4j only):
`ClientChannel`, `CommonChannel`, `Connection`, `HandshakeCoordinator`,
`HandshakeProtocol`, `HandshakeWireFormat`, `MultiConnectionUDPClient`,
`MultiConnectionUDPServer`, `Registrations`, `UDPConnection`,
`UDPSendReceiveServer`, `General.kt` (`resolveLocalAddress()`).

**Scraping** (skrapeit + jsoup + unirest): `Connector`.

**Headless-browser screenshots** (selenium): `WebViewer`.

**Shared, non-concern-specific:** `ResultExtensions.kt` — one `internal inline
fun Result<T>.flatMap`, used by both the UDP code and `Connector`.

### 1.3 Test inventory (all under `com.spartanlabs.testing.<level>.webtools`)

| Level | UDP | Scraping | Browser |
|---|---|---|---|
| gating | `HandshakeCoordinatorGatingTest`, `HandshakeProtocolGatingTest`, `HandshakeWireFormatGatingTest`, `UDPConnectionGatingTest` | — | — |
| component | `HandshakeCoordinatorTest`, `RegistrationsTest`, `UDPConnectionTest` | `ConnectorTest` | — |
| deterministic (4a) | `HandshakeProtocolTest`, `HandshakeWireFormatTest` | — | — |
| integration (3) | `CommonChannelTest`, `MultiConnectionUDPClientTest`, `MultiConnectionUDPServerTest`, `UDPSendReceiveServerTest` | — | — |
| e2e (4b) | `MultiConnectionUDPClientServerE2ETest`, `MultiConnectionUDPServerE2ETest` | — | — |
| nonfunctional (4c) | `HandshakeNonFunctionalTest`, `MultiConnectionUDPClientNonFunctionalTest` | — | — |
| uat (5) | `MultiConnectionUDPServerUatTest` (`@Disabled`) | — | — |
| support | `FakeClientChannel`, `FakeConnection` | — | — |

`webtools-browser` has **zero** tests today.

---

## 2. Target layout

```
WebTools/                        (repo root, unchanged name)
  settings.gradle.kts            include(":webtools-udp", ":webtools-scraping", ":webtools-browser")
  build.gradle.kts               allprojects/subprojects: kotlin jvm, repos, the 5-level
                                 test-task registration, the maven-publish POM skeleton
  gradle/…                       wrapper unchanged
  README.md                      rewritten: three artifacts + migration table
  docs/                          unchanged (this plan added)
  webtools-udp/
    build.gradle.kts             api(slf4j); the CommonUdpPortLock service + port-9998
                                 serialisation (only this module binds the port)
    src/main/kotlin/com/spartanlabs/webtools/udp/…       (12 files, moved via git mv)
    src/test/kotlin/com/spartanlabs/testing/<level>/webtools/udp/…
  webtools-scraping/
    build.gradle.kts             api(slf4j); implementation(skrapeit, jsoup, unirest)
    src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt
    src/main/kotlin/com/spartanlabs/webtools/scraping/internal/ResultExtensions.kt
    src/test/kotlin/com/spartanlabs/testing/component/webtools/scraping/ConnectorTest.kt
  webtools-browser/
    build.gradle.kts             api(slf4j); implementation(selenium-java)
    src/main/kotlin/com/spartanlabs/webtools/browser/WebViewer.kt
    src/test/kotlin/com/spartanlabs/testing/…/webtools/browser/…   (new, see §4.4)
```

The current top-level `src/` is deleted once every file has been `git mv`d into a
module (history preserved per file).

### 2.1 Module boundaries and dependencies

| Module | Package | Runtime deps | Notes |
|---|---|---|---|
| `webtools-udp` | `com.spartanlabs.webtools.udp` (flat — see §10; the five collaborators stay `internal`, which already hides them from consumers) | `api(slf4j-api)` | No third-party runtime deps at all. Drops the unused `GeneralTools` `api` dep. |
| `webtools-scraping` | `com.spartanlabs.webtools.scraping` | `api(slf4j-api)`, `implementation(skrapeit 1.1.5, jsoup 1.15.4, unirest-java 1.4.9)` | `Connector` unchanged apart from its package + its own copy of `flatMap`. |
| `webtools-browser` | `com.spartanlabs.webtools.browser` | `api(slf4j-api)`, `implementation(selenium-java)` | See D5 (Selenium version) and D6 (`WebViewer` cleanup). **No** committed driver binaries. |

### 2.2 The shared `flatMap` helper (no fourth module)

`ResultExtensions.flatMap` is `internal`, `inline`, two lines. D3 fixes three
artifacts, not four, and a `webtools-core` for one internal function is not worth
a fourth coordinate. **Copy it** into each module that uses it:

- `webtools-udp`: `com.spartanlabs.webtools.udp.internal.flatMap`
- `webtools-scraping`: `com.spartanlabs.webtools.scraping.internal.flatMap`

`webtools-browser` does not use it (`WebViewer` does no `Result` chaining today).
The two copies are identical and each `internal`, so they never collide on a
consumer classpath. A one-line comment in each notes the deliberate duplication.

### 2.3 Root build — shared configuration

Everything currently in `build.gradle.kts` that is not dependency-specific moves
to a `subprojects { }` block in the root build:

- `kotlin("jvm") version "2.2.0"` (applied to each subproject; root just declares
  it `apply false` in `plugins`).
- `repositories { mavenCentral() }`.
- `java { toolchain… }` / the foojay resolver (already in `settings.gradle.kts`).
- The **5-level test-task registration** loop (`gatingTest`, `componentTest`, …,
  `uatTest`) — identical for every module; only `webtools-udp` additionally wires
  `usesService(commonUdpPortLock)` onto `integrationTest`/`e2eTest`/
  `nonfunctionalTest`/`test`, so that part stays in `webtools-udp/build.gradle.kts`.
- `com.vanniktech.maven.publish` config: `publishToMavenCentral()`,
  `signAllPublications()`, and the shared POM fields (license, developer, SCM →
  all point at the one `SpartanLaboratories/WebTools` repo). Only
  `coordinates(group, "<module-artifact-id>", version)`, `pom.name`, and
  `pom.description` differ per module.
- Drop `kotlin("kapt")` entirely — no annotation processor is configured or used
  (grep-confirmed; `kaptKotlin` has always been `SKIPPED`).

`version` is declared once in the root build (`"1.0.0"`) and inherited by all
three subprojects (D3 — they start aligned; they may diverge later).

---

## 3. Module-by-module changes

### 3.1 `webtools-udp`

- `git mv` the 12 UDP source files into
  `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/`; change each
  `package com.spartanlabs.webtools` → `com.spartanlabs.webtools.udp`
  (or `.udp.internal` for the five `internal` collaborators — keeps the public
  surface of the module visibly small).
- Add `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/internal/ResultExtensions.kt`
  (copied, repackaged, comment noting the duplication).
- `General.kt` (`resolveLocalAddress()`) → `com.spartanlabs.webtools.udp`. It is
  UDP-adjacent (probes via `DatagramSocket`) and has no other home. **Open note:**
  its KDoc example and the README currently present it as a general utility; it is
  now explicitly part of the UDP artifact.
- `webtools-udp/build.gradle.kts`:
  ```kotlin
  dependencies {
      api("org.slf4j:slf4j-api:2.0.13")
      testImplementation(kotlin("test-junit5"))
      testImplementation(kotlin("test"))
      testImplementation("ch.qos.logback:logback-classic:1.5.6")
  }
  ```
  plus the `CommonUdpPortLock` build service + `usesService` wiring (moved
  verbatim from the current root build), and
  `mavenPublishing { coordinates(group.toString(), "webtools-udp", version.toString()) … }`.
- `git mv` all UDP test files into
  `webtools-udp/src/test/kotlin/com/spartanlabs/testing/<level>/webtools/udp/`;
  repackage to `…testing.<level>.webtools.udp`; update imports of the moved
  production types. `FakeClientChannel` / `FakeConnection` →
  `…testing.support.webtools.udp`.

### 3.2 `webtools-scraping`

- `git mv src/main/kotlin/com/spartanlabs/webtools/Connector.kt
  webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt`;
  `package … .scraping`; its `flatMap` calls now resolve to the local
  `…scraping.internal.flatMap`.
- Add `…scraping/internal/ResultExtensions.kt` (copied).
- `webtools-scraping/build.gradle.kts`:
  ```kotlin
  dependencies {
      api("org.slf4j:slf4j-api:2.0.13")
      implementation("it.skrape:skrapeit:1.1.5")
      implementation("org.jsoup:jsoup:1.15.4")
      implementation("com.mashape.unirest:unirest-java:1.4.9")
      testImplementation(kotlin("test-junit5"))
      testImplementation(kotlin("test"))
      testImplementation("ch.qos.logback:logback-classic:1.5.6")
  }
  ```
  plus per-module `mavenPublishing` coordinates/name/description.
- `git mv` `ConnectorTest.kt` →
  `webtools-scraping/src/test/kotlin/com/spartanlabs/testing/component/webtools/scraping/`;
  repackage.

### 3.3 `webtools-browser`

- `git mv src/main/kotlin/com/spartanlabs/webtools/WebViewer.kt
  webtools-browser/src/main/kotlin/com/spartanlabs/webtools/browser/WebViewer.kt`;
  `package … .browser`.
- **Delete** `src/main/resources/chromedriver-win64/`,
  `src/main/resources/chromedriver_win32/`, `src/main/resources/firefoxdriver/`
  (~30 MB) — `git rm -r`. They must not be inherited by the new module. See D6 for
  how `WebViewer` then finds a driver.
- `webtools-browser/build.gradle.kts`:
  ```kotlin
  dependencies {
      api("org.slf4j:slf4j-api:2.0.13")
      implementation("org.seleniumhq.selenium:selenium-java:4.48.0")
      testImplementation(kotlin("test-junit5"))
      testImplementation(kotlin("test"))
      testImplementation("ch.qos.logback:logback-classic:1.5.6")
  }
  ```
  plus per-module `mavenPublishing` coordinates/name/description.
- New tests — §4.4.

### 3.4 Root `build.gradle.kts` / `settings.gradle.kts`

- `settings.gradle.kts`: keep the foojay plugin; add
  `include(":webtools-udp", ":webtools-scraping", ":webtools-browser")`.
  `rootProject.name = "WebTools"` unchanged.
- Root `build.gradle.kts`: `plugins { kotlin("jvm") version "2.2.0" apply false;
  id("com.vanniktech.maven.publish") version "0.36.0" apply false }`, then
  `allprojects { group = "io.github.spartanlaboratories"; version = "1.0.0" }`
  and `subprojects { … }` with the shared config from §2.3.

### 3.5 `README.md`

Full rewrite of "Install" and "Components". New top section:

> WebTools is published as **three independent artifacts** — depend only on the
> one you need:
>
> | Artifact | Package | Purpose |
> |---|---|---|
> | `io.github.spartanlaboratories:webtools-udp:1.0.0` | `com.spartanlabs.webtools.udp` | Multi-client UDP connection layer + NAT-traversal handshake. slf4j-api only. |
> | `io.github.spartanlaboratories:webtools-scraping:1.0.0` | `com.spartanlabs.webtools.scraping` | `Connector` — open/read a URL, one-shot `get`/`skrape`/image `download`. |
> | `io.github.spartanlaboratories:webtools-browser:1.0.0` | `com.spartanlabs.webtools.browser` | `WebViewer` — headless-browser screenshots via Selenium. |
>
> ### Migrating from `io.github.spartanlaboratories:WebTools` (≤ 2.0.1a)
>
> The combined `WebTools` artifact is retired at `2.0.1a`. Replace it with
> whichever of the three you actually use, and update imports:
> `com.spartanlabs.webtools.Foo` → `com.spartanlabs.webtools.{udp,scraping,browser}.Foo`.

The "UDP handshake protocol" section is unchanged in content (moves under the
`webtools-udp` heading).

---

## 4. Test plan (5-level hierarchy)

The hierarchy is preserved **per module** — each module gets the full
`gatingTest … uatTest` task set from the shared root config, each test class keeps
its `@Tag`, packages mirror production (`…testing.<level>.webtools.<module>`).

### 4.1 `webtools-udp`

No behavioral change — every existing UDP test moves verbatim (package + import
edits only) and must stay green: **~120 of the current 130 tests**. `./gradlew
:webtools-udp:build` is the gate. The port-9998 serialisation service moves with
them so the integration/e2e/nonfunctional levels still can't collide.

### 4.2 `webtools-scraping`

`ConnectorTest` (11 cases) moves verbatim. `./gradlew :webtools-scraping:build`.

### 4.3 Cross-module

None — the three modules share no code and no test depends on another module.
`./gradlew build` at the root runs all three.

### 4.4 `webtools-browser` — new coverage (currently zero)

Publishing a `1.0.0` artifact with no tests is not acceptable under the testing
hierarchy. Minimum before release:

- **Level 1 gating** (`…testing.gating.webtools.browser.WebViewerGatingTest`) —
  no real browser: assert `WebViewer` constructs, and that `screenshot`/`getPage`
  reject a malformed URL as a failure (requires D6 — giving `WebViewer` a
  `Result` return and lazy driver init so construction touches no browser).
- **Level 5 UAT** (`…testing.uat.webtools.browser.WebViewerUatTest`, `@Disabled`
  like the existing UDP UAT) — a manual "screenshot example.com and eyeball the
  PNG" check, run only when a browser is present.

Level 2/3 real-browser tests are explicitly **out of scope** for this split (they
need Selenium Manager to download a driver in CI — a separate follow-up).

---

## 5. Documentation impact (Audience-Reach rings)

| Ring | What moves |
|---|---|
| Inner Core | `//region` grouping unaffected; add the "deliberate duplication" comment on each copied `flatMap`. |
| Component Ring (KDoc) | Package-level KDoc (`package-info` equivalent — a `package.kt` doc or module `README`) for each of the three new packages stating the module's single concern and its dependency footprint. `resolveLocalAddress()` KDoc reworded (now explicitly part of `webtools-udp`). `WebViewer` KDoc rewritten (it currently describes a `download` method that does not exist) — part of D6. |
| Boundary Ring | `README.md` (§3.5) — artifact split + migration table. This is the primary consumer-facing change. |
| Architectural Outer Layer | This plan is the record of the split. No Structurizr/C4 model exists to update. |

README currency: the same PR that splits the build rewrites the README.

---

## 6. Risks & edge cases

- **Breaking for every consumer.** Coordinates change *and* packages change.
  There is exactly one known consumer (`SpartanLabsGaming/MyGameTools`, uses the
  UDP layer). Per standing guidance, downstream adoption is that repo's problem
  and is not tracked here beyond a one-line note in the release.
- **`git mv` + package change in one commit.** Git detects the rename by content
  similarity even with the `package` line changed, but a large simultaneous move
  can drop below the rename threshold. Mitigation: move files with `git mv` and
  commit the moves **before** the package/import edits, or commit per module (§8),
  and verify with `git log --follow` on a sample file post-merge.
- **`GeneralTools` was `api`.** Removing it is correct (unused) but is itself a
  visible POM change — any consumer relying transitively on `GeneralTools` via
  `WebTools` loses it. Called out in the migration notes.
- **Selenium `4.0.0` → `4.48.0`** is a large jump. `WebViewer` uses only the
  stable core API (`ChromeDriver`, `ChromeOptions.addArguments`,
  `getScreenshotAs`), unchanged across 4.x. The behavioral change that matters is
  positive: Selenium Manager resolves the driver, removing the hardcoded-path
  failure mode. New browser tests (§4.4) guard the surface.
- **The retired `WebTools` artifact stays on Central forever** (immutable). That
  is fine — `2.0.1a` keeps working; it just never gets another release.
- **CI:** no GitHub Actions workflow exists today, so nothing to migrate. If one
  is added later it runs `./gradlew build` at the root and covers all three.
- **`maven-publish` / vanniktech plugin with subprojects:** each subproject needs
  its own `mavenPublishing { }` block; the plugin does not aggregate. Verified
  this is the standard pattern for that plugin.

---

## 7. Versioning

Per D3: all three start at **`1.0.0`**. These are new artifact identities, so the
project's existing "letter suffix = bugfix" convention starts fresh for each —
the first release is a plain `1.0.0`, no suffix. The retired `WebTools` artifact
keeps its final `2.0.1a` and is not part of this scheme going forward.

---

## 8. Version control

- **Branch:** `refactor/split-into-three-modules`, off `master`.
- **Pre-existing working-tree noise:** `.gitignore` has an untracked `.ai/`
  addition — not part of this work, leave it out of every commit.
- **Commit trailers** (every commit):
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01W3FTxBoiCajVkEQfj9qyvx
  ```
- **Commit sequence** (each builds green before the next):
  1. `build: introduce Gradle multi-module skeleton` — root `build.gradle.kts` +
     `settings.gradle.kts` with the three empty subprojects and shared config; no
     source moved yet. `./gradlew projects` succeeds.
  2. `refactor: move the UDP layer into webtools-udp` — `git mv` the 12 sources +
     all UDP tests + support fakes; repackage to `…webtools.udp`; move the
     port-lock build service. `./gradlew :webtools-udp:build` green.
  3. `refactor: move scraping into webtools-scraping` — `Connector` + copied
     `flatMap` + `ConnectorTest`. `./gradlew :webtools-scraping:build` green.
  4. `refactor: move WebViewer into webtools-browser, drop bundled drivers` —
     `WebViewer` + `git rm -r` the 30 MB of driver binaries + D6 cleanup + new
     browser tests. `./gradlew :webtools-browser:build` green.
  5. `docs: rewrite README for the three-artifact split` — README + package KDoc +
     this plan's header fields filled in. `./gradlew build` green.
- **No push, no PR, no publish, no release** until the user asks.
- On the user's say-so: publish `webtools-udp`, `webtools-scraping`,
  `webtools-browser` each at `1.0.0`; if D0 = yes, open then close the tracking
  issue referencing the PR.

---

## 9. Decisions (all settled)

- **D0 — tracking issue: YES.** File `SpartanLaboratories/WebTools` issue "Split
  WebTools into three single-concern artifacts", reference it from the PR, close
  on merge. Its number backfills the header `Covers:` field and §3.5.
- **D5 — Selenium version: bump to `4.48.0`** (latest 4.x, released 2026-08-27).
  Selenium Manager auto-provisions drivers, which is what lets the committed
  binaries be deleted cleanly.
- **D6 — `WebViewer` cleanup: option (b).** During the move:
  - Delete the `init { System.setProperty(...) }` block entirely — Selenium
    Manager (bundled in 4.48) resolves the driver; no hardcoded paths, no
    committed binaries.
  - `screenshot(url)` → `Result<BufferedImage>`; `getPage(url)` →
    `Result<File>`; both `runCatching { … }` with an `onFailure` log, matching
    `Connector`/`UDPConnection` house style. `getChromePage` wrapped likewise;
    the driver is always `quit()`ed in a `finally`/`use`-style block (the current
    code leaks the `ChromeDriver` on the screenshot path).
  - Replace the fixed `Thread.sleep(4900)` with a constructor-injected
    `pageSettleMillis` (default a documented value, e.g. `2000`) — still a crude
    wait, but configurable and not a hidden 4.9 s stall. A proper
    `WebDriverWait` on `document.readyState` is noted as a follow-up, not done
    here.
  - Delete `getFirefoxPage` (dead — hardcoded `C:/Users/spartak/…` Firefox
    binary, `@org.apache.http.annotation.Experimental`, never reachable from the
    public API).
  - Rewrite the class KDoc — it currently describes a `download(url)` method that
    does not exist; document the real surface (`screenshot`, `getPage`) and the
    Selenium-Manager driver requirement (needs Chrome installed; network access
    on first run to fetch the driver).
  - New tests per §4.4.

---

## 10. Deviations from the plan as implemented

- **§2.1 / §3.1 — no `.udp.internal` sub-package.** All twelve `webtools-udp`
  sources sit flat in `com.spartanlabs.webtools.udp`. Adding a second package for
  the five `internal` collaborators would add cross-package imports and rename
  churn for no consumer-visible benefit — `internal` already hides them from the
  published API. The flat layout also matches how the code was structured before
  the split (flat package, `internal` markers).
- **§4.4 — `WebViewer` gating test shape.** `WebViewer` has no pure logic to
  unit-test (URL handling is Selenium's), so the gating test pins the one
  browser-free contract there is: `getPage`/`screenshot` reject a blank URL as a
  `Result.failure` *before* launching a driver (`require(url.isNotBlank())`),
  plus the settle-time constant. Real-render coverage is the `@Disabled` UAT.
- **`WebViewer` rewrite not tracked as a git rename.** The D6 cleanup changed
  enough of the file (58 lines removed, 85 added) that Git records it as a
  delete + add rather than a rename. Intentional — the commit message carries the
  provenance.
- **Three unreferenced test fixtures deleted** (`mirrorImage.png`, `test image
  file.png`, `testWebpageScreenshot.png`) — no `.kt` file referenced them; not
  worth relocating dead weight.
- **Driver binaries removed from HEAD only.** `git rm` drops them going forward
  but they remain in history (~30 MB). A history rewrite (`git filter-repo`) is
  out of scope for a published repo and was not done.
