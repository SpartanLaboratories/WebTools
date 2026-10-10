# Issue #15 — final implementation (as built)

**Covers:** `SpartanLaboratories/WebTools#15` — *"Split WebTools into three
single-concern artifacts (webtools-udp / -scraping / -browser)"*.

## What was built

One pull request into `master`, [#16](https://github.com/SpartanLaboratories/WebTools/pull/16), merged on 2026-09-05 as merge commit
`2a0dcb3`. It split WebTools into the Gradle modules `webtools-udp`,
`webtools-scraping` and `webtools-browser`, each at version `1.0.0`.

The section below is the deviation record moved here verbatim from
[`plan.md`](plan.md). Its heading names the source document and section; the source
keeps a pointer where the record was.

## plan.md §10 Deviations from the plan as implemented

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
