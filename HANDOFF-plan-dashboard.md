# Handoff: plan dashboards (work-in-progress draft)

**Status:** draft, not reviewed by the maintainer, no pull request opened. Everything described here is
pushed on branch `ccr-efec1b93-7rd994` (two commits on top of `master` `0e7b0f8`: `668109c` the skill,
`5f981bc` the worked example). The working tree was clean at handoff. Delete this file before the branch
is merged.

**Kickoff prompt for the next session** (paste as the first message):

> Check out `ccr-efec1b93-7rd994` in SpartanLaboratories/WebTools and read `HANDOFF-plan-dashboard.md`
> at the root. Continue the plan-dashboard work from its "Where to pick up" section. Ask me the open
> decisions listed there before changing the data model or the directions.

## 1. What the maintainer asked for

The maintainer runs a `/resolve-issue` pipeline (directions kept **outside this repository**; never seen
by the previous session) with a researcher, a planner, an implementer (`rookie-dev`), reviewers and a
presenter. The planner wrote `docs/plans/<issue>-<slug>/plan.md`, a file-by-file plan up to 3,600 lines,
and the implementer wrote `final-implementation.md` at the end. The complaint: at the end of a run the
plan no longer matches what was built, so it is a useless artifact that gets deleted.

The ask: the planner should create a **dashboard** instead of a Markdown plan. A timeline with four
columns, one row per part of the plan (subsystem, feature, subcomponent):

1. the **gap** that necessitated the change;
2. the **implementations considered**;
3. the **verdict**: the defence of the accepted implementation, or the justification for declining one;
4. the **as-built** state, including deviations from the plan.

"There are some templates for dashboards already": the only one in the repository is
`docs/dashboards/index.html`, the system dashboard built by the maintainer's `system-dashboard` skill
(also outside the repository). The new page follows its conventions; deviating was allowed where a plan
page needs something different.

## 2. What exists on the branch

| Path | What it is | Lines |
|---|---|---|
| `.claude/skills/plan-dashboard/SKILL.md` | The directions: who writes which column when, column rules, row granularity, state vocabulary, what `check.js` enforces, how to change the template, migration of old folders | 130 |
| `.claude/skills/plan-dashboard/template/dashboard.html` | The page. Renders at load from a `<script type="application/json" id="plan">` block that holds `plan.json` verbatim. Tabs: Timeline (the board), Decisions (every option as a log), As built (every change from the verdict), Source (the JSON). Inspector on the right, a legend (`.key`) on every tab, context cards (Trail, Follow-ups, Documents, Notes), footer | 545 |
| `.claude/skills/plan-dashboard/template/plan.json` | The data skeleton with `<placeholder>` values; `check.js` refuses a plan that still has any | 61 |
| `.claude/skills/plan-dashboard/scripts/new.js` | `node new.js <issue> <slug> [--title] [--issue-title] [--baseline <sha>] [--date] [--repo]` scaffolds `docs/plans/<issue>-<slug>/{plan.json,dashboard.html}`; refuses to overwrite. Resolves the repo root as four directories above itself, so it must stay at this path | 36 |
| `.claude/skills/plan-dashboard/scripts/embed.js` | `node embed.js <folder>` copies `plan.json` into the page's block (escapes `</script` as `<\/script`) | 30 |
| `.claude/skills/plan-dashboard/scripts/check.js` | `node check.js <folder>` enforces the rules (section 4); exit 1 on failure; warnings never fail | 148 |
| `docs/plans/38-handshake-empty-name/plan.json` | The worked example: Issue #38 rewritten after the fact into the format. 11 rows, 28 options, 19 as-built changes | 555 |
| `docs/plans/38-handshake-empty-name/dashboard.html` | The template with that data embedded (regenerated, never hand-edited) | 1081 |
| `docs/plans/README.md` | Reader-facing: how to read a plan page, which folders have which format | 26 |
| `docs/dashboards/index.html` | One `<li>` added to the "Plans and documents" card linking the #38 page and the plans README | edit |
| `README.md` | One line after the "System dashboards" line pointing at `docs/plans/README.md` | edit |

Not changed: `docs/dashboards/systems.json`, `workspace.dsl`, any Kotlin, any existing plan Markdown.
Issue #38's `plan.md` and `final-implementation.md` were kept and are marked `historical` in the page's
Documents card.

## 3. The data model (`plan.json`)

Top level: `schema` (`"plan-dashboard/1"`), `repo`, `issue`, `slug` (= folder name, starts with the
issue number), `title`, `issueTitle`, `function` (one paragraph), `status` (`planned | building | built |
abandoned`), `baseline {ref, sha, date}`, `asBuilt` (`null` until built; then `{ref, sha, date, pr, merge,
version}`), `systems [{slug, name}]` (registry slugs from `systems.json`; order gives the colour slots,
six available), `milestones [{column, label, date, ref, note}]` (one per column; they become the column
headers), `trail [{date, column, label, ref}]` (finer dated events; Context card), `rows`, `followUps
[{issue, note}]`, `documents [{label, path, note, historical}]`, `notes {key: text}`.

A row: `id` (kebab-case, stable; it is the deep-link address `dashboard.html#<id>.<column>`), `group`
(`Code | Tests | Documents | Release | Left for later`, rendered as section headings in that order of
first appearance), `name`, `system`, `where`, then the four columns:

- `gap {kind: bug|missing|debt|risk, summary, detail, evidence[], source}`
- `options [{id, name, summary, verdict: accepted|declined|deferred, why, wins, source}]`, exactly one
  `accepted` per row (for a part deliberately left alone, the accepted option is the one that leaves it)
- `verdict {kind: build|leave, summary, defense, steps [{id, what, files[], check, detail}], tests[],
  risks[], source}`
- `asBuilt {state, summary, changes [{what, why, when, ref}], files[], tests, commits[], verified, issue}`
  with `state` one of `pending | as-planned | amended | deviated | dropped | deferred`.

State semantics chosen (the maintainer has not confirmed them): `as-planned` built as the verdict says;
`amended` built as the verdict says plus changes decided in implementation or review (the decision
stands); `deviated` the decision itself changed; `dropped` not built, reason in `summary`; `deferred` not
built here, `issue` names where it went. In the #38 data every review-driven change is `amended`, none is
`deviated`, which is faithful to its `final-implementation.md` ("Deviations: none"; Stages C and D were
added by the reviews).

Text fields are plain text; the page turns `` `code` `` spans into `<code>` and `#123` into issue links
(`rich()`), and inside buttons uses code spans only (`codeOnly()`). A `ref` that is a 7–40 hex sha links
to the commit, `PR #n` to the pull request, `#n` to the issue, a relative `*.md|html|json|kt|kts|dsl` path
to the file (with an optional ` §x` suffix kept as text).

## 4. Rules `check.js` enforces

Fails on: bad or missing schema/repo/issue/slug/title/function/baseline/status; a `built` plan without
`asBuilt.sha` and `.date`; a `planned` plan with any non-pending part or a non-null `asBuilt`; a `built`
plan with a pending part; a row without id/name/system/gap.summary/options/verdict.summary; options not
exactly one `accepted`; a declined or deferred option without `why`; `amended`/`deviated` without
`changes[]` or a change without `what` and `why`; `dropped` without a `summary`; `deferred` without
`issue`; any non-pending part without `summary`; bad date (`YYYY-MM-DD`), sha, state, kind, verdict or
column values; any `<placeholder>` left; an embedded copy that differs from `plan.json`; a tab panel
without `class="key"`; a dead `href="#"`.

Warns on: a gap with no evidence, a build verdict with no steps, a row system not in `systems`, more than
six systems, a missing gap/considered/verdict/built milestone, no dark-mode block in the page.

## 5. Conventions followed, and where the page diverges

From `docs/dashboards/index.html` (its header comments are the spec that was available): the same
`:root` tokens and dark-mode blocks, mono `h1`, `.crumbs`, `.chips`, `.kpis`, `.tabs`/`.stage`/`.view`/
`.inspector`, a `.key` legend on every tab (its `check-page.js` fails a tab without one), dashed = planned
or pending, status colours (`--good/--warning/--serious/--critical`) only for outcomes and always beside a
glyph and label, one categorical colour slot per system in registry order, the data embedded verbatim,
a footer naming the sources and a footer comment listing divergences from the template. The maintainer's
`dataviz` skill was loaded and its rules were followed (status colours reserved, text in ink tokens, a
legend wherever two or more series appear, dark mode selected not flipped).

Divergences (recorded in the page's footer comment): no Systems, Structure, C4, Threads, Lifecycles or
Guarantees content, because a plan is not a running system; the three views are the plan's own rows.

## 6. How it was verified

- `node .claude/skills/plan-dashboard/scripts/check.js docs/plans/38-handshake-empty-name` passes
  (11 parts, 28 options, embedded copy verbatim, 4 panels with legends). The blank template fails as
  designed (placeholders). `new.js` was exercised on a throwaway folder `999-scaffold-test`, then removed.
- Rendered in headless Chromium through the preinstalled Playwright (`NODE_PATH=/opt/node22/lib/node_modules`,
  `require("playwright")`, `chromium.launch()`, `file://` URLs): no page or console errors on the #38
  page, on the blank template, at 1280×900 light and dark, and at 390×844 where `scrollWidth ==
  clientWidth` (no horizontal scroll). Deep link `#protocol-doc.verdict` selects the right cell and
  fills the inspector. Screenshots were inspected by eye; three defects found and fixed (option names
  overflowing their cell, scrolled cell text showing through the column gaps under the sticky column
  headers, dates wrapping mid-date).
- The edited `docs/dashboards/index.html` still loads without errors and the new link resolves.
- No Gradle build or Kotlin test was run: nothing under `webtools-*` changed.

Render script, to recreate (the previous session's scratch copy is gone with its container):

```js
const { chromium } = require("playwright");
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.on("pageerror", e => console.log("pageerror", e.message));
  await page.goto("file:///<repo>/docs/plans/38-handshake-empty-name/dashboard.html");
  await page.screenshot({ path: "board.png", fullPage: true });
  await page.emulateMedia({ colorScheme: "dark" }); await page.screenshot({ path: "dark.png" });
  await browser.close();
})();
```

## 7. Open decisions for the maintainer (ask before changing)

1. **Wiring the pipeline.** The `/resolve-issue` directions live outside the repository and were not
   touched. `SKILL.md` is written to drop in: its "Who does what" section replaces the "write plan.md"
   and "write final-implementation.md" steps for the planner, implementer and presenter. Someone has to
   make that edit in the maintainer's own skill library, or the maintainer may prefer the directions to
   live only in the repo's `.claude/skills/` (Claude Code loads them there automatically).
2. **Code listings.** The old plans carried whole-file Kotlin listings for the rookie executor. The
   verdict column carries steps as sentences with a file anchor and a check each, plus an optional
   `detail` for a short exact snippet. If the listings must stay, the proposed shape is a disposable
   `steps.md` beside the dashboard, deleted at landing (not built).
3. **Scope of replacement.** Assumed: the dashboard replaces `plan.md` **and** `final-implementation.md`;
   `research/`, `architecture.md` and `impact.md` remain and are linked as evidence. The maintainer only
   said "instead of creating a .md file, the planner should create a dashboard".
4. **State vocabulary.** The six as-built states and the `amended` vs `deviated` distinction (section 3)
   are the previous session's design, not the maintainer's words. The maintainer said "deviations from
   the original implementation plan"; confirm the split is wanted.
5. **Multi-unit plans.** Issue #55 is a tracking issue with ten sub-issues and a nested folder
   (`55-http-client-stack/47-java-23-bytecode/`). `SKILL.md` says a bigger plan is usually several
   units, each with its own folder (so each sub-issue would get its own page). Not validated against how
   the maintainer wants #55 handled; nothing for #55 was created.
6. **Rows for things left alone.** The #38 example has two rows under `Left for later` (#53, #52) whose
   verdict is `leave`. The maintainer asked for "justification for why an implementation was declined";
   whether a *row* for a declined scope item is wanted, or only declined *options* inside rows, is
   unconfirmed.

## 8. Known gaps and candidate improvements (none started)

- `check.js` does not verify that `documents[].path`, `gap.source` or step `files[]` exist on disk, nor
  that `ref` shas exist in git. Easy to add with `fs.existsSync` and `git cat-file -e`.
- The template duplicates CSS from `index.html` rather than sharing a stylesheet (both pages are
  self-contained by convention). Drift between the two is a risk; a diff of the shared `:root` blocks
  is the quick check.
- Trail events sort by date string only; same-day events keep insertion order (milestones first). A
  `time` or `seq` field would fix ordering for busy days such as #38's 2026-10-20.
- `rich()` links `#n` even inside code spans (for example `FIXME(#49)` becomes a link inside `<code>`).
  Harmless, possibly unwanted.
- Accessibility: cells and option buttons are keyboard-operable (Enter/Space), decision-log rows take
  Enter, tabs take arrow keys; no screen-reader audit was done; the row connector nodes are decorative
  CSS with no text alternative (the legend explains them).
- The inherited `--sys-*` palette was not run through the `dataviz` skill's `validate_palette.js`; it is
  the maintainer's existing palette, used unchanged.
- The #38 `plan.json` content was written by the previous session from the issue's documents and
  should be proofread by someone who knows the change: in particular the single-option rows
  (`public-kdoc`, `test-housekeeping`, `architecture-doc-readme`, where no alternative was recorded in
  the architecture), the `amended` states, and the review-round attributions in each change's `when`.
- `index.html`'s "Plans and documents" card is hand-written HTML; the presenter will need a rule for
  adding each new plan page there (or generating the list from the plan folders).
- The phone layout was checked only for overflow and a glance at a screenshot; not reviewed in detail.
- No tests exist for the scripts themselves (they are small; a fixture-based test of `check.js` rules
  would be cheap).

## 9. Where to pick up

1. Put the open decisions (section 7) to the maintainer; adjust `SKILL.md`, `template/plan.json`,
   `check.js` and the #38 data together if the vocabulary or scope changes. The data model is in three
   places that must agree: `SKILL.md` (prose), `check.js` (rules), `template/dashboard.html` (`STATES`,
   `OPT`, `COLS` constants near the top of the script).
2. First live use: run the skill on a real new issue (the #55 roadmap's next units, #61 or #47, are the
   candidates) and see what the planner finds missing. Expect friction in `steps` (how much detail) and
   in the `Left for later` rows.
3. Proofread the #38 example against `architecture.md`, `plan.md` and `final-implementation.md` in the
   same folder.
4. Re-run `embed.js` and `check.js` after any edit; re-template existing pages by copying
   `template/dashboard.html` over the folder's `dashboard.html` and embedding again (the data lives in
   `plan.json`, so nothing is lost; never hand-edit a page).
5. When the maintainer is satisfied: delete this file, open the pull request from
   `ccr-efec1b93-7rd994` to `master`. Commit messages on this branch follow the repository's style
   (`docs: ... (Issue #N)` where an issue applies).

Commands from the repository root:

```bash
node .claude/skills/plan-dashboard/scripts/new.js <issue> <slug> --title "Short name" --issue-title "Title" --baseline <sha> --date YYYY-MM-DD
node .claude/skills/plan-dashboard/scripts/embed.js docs/plans/<issue>-<slug>
node .claude/skills/plan-dashboard/scripts/check.js docs/plans/<issue>-<slug>
```

## 10. Facts about #38 used in the example (so they need not be re-derived)

Baseline `master` `85cfbbe` (2026-10-19). Research commits `77b3786` … `9b1fff7` (2026-10-19). Commit 1
`56a2121` (housekeeping + plan documents), Commit 2 `1605bdd` (Stage A code/KDoc/tests), Commit 3
`d3807b0` (contracts, regions), Commit 4 `c6be0f2` (remaining tests), Commit 5 `16d6849` (Stage B
documents), version bump `ca57a57` (`webtools-udp` `2.0.0a`), as-built record `22ef138`, all
2026-10-20. PR #59 opened and merged 2026-10-20 (merge commit `806ce8e`). Issue #38 opened 2026-09-24
by SpaSinghOut, closed 2026-10-20. Two review rounds: 10 documentation + 8 test findings (Stage C),
then 5 LOW items (Stage D). Final test counts: gating 83, component 235, integration 115, deterministic
96, e2e 34, nonfunctional 65, uat 13.
