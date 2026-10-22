---
name: plan-dashboard
description: Create and keep an issue's plan dashboard, docs/plans/<issue>-<slug>/plan.json rendered by dashboard.html as a four-column timeline (gap, considered, verdict, as built) with one row per part of the change. Used by the /resolve-issue pipeline in place of plan.md and final-implementation.md. The planner creates it after research and architecture, the implementer fills the as-built column stage by stage, the presenter closes it at landing.
---

# Plan dashboard

A plan written as a Markdown file stops being true the moment the implementation deviates from it, and at the end of a run it is a stale artifact nobody wants. The plan dashboard keeps the plan's history and its outcome in one page that stays true: the decisions are written once and never rewritten, and what was actually built is recorded beside them, change by change, with the reason for each.

The page is a timeline. Every row is one part of the change. Its four cells are the phases the part passed through, left to right:

| | Column | Holds | Written by | When |
|---|---|---|---|---|
| 1 | **Gap** | What was missing or wrong at the baseline commit, with evidence | planner | after research |
| 2 | **Considered** | Every option weighed, the one accepted marked | planner | after architecture |
| 3 | **Verdict** | The decision and its defence, with the steps to build it; or why the gap stays | planner | at plan acceptance |
| 4 | **As built** | What exists at the as-built commit and every change from the verdict, each with its reason and when it was decided | implementer, then presenter | every stage, then at landing |

Two rules make the artifact durable:

1. **Columns 1 to 3 are written once.** After the plan is accepted they are not edited to match what happened. A factual error found later is corrected in place and recorded under `notes.corrections`; a change of mind is recorded in column 4, not by rewriting column 3.
2. **Column 4 records differences, never hides them.** Every change from the verdict is an entry with `what`, `why`, `when` (the stage or review round) and a `ref` (the commit). A plan that "grew in review" is `amended`; a part built differently from its verdict is `deviated`; neither is rewritten into the plan.

## Files

```
docs/plans/<issue>-<slug>/
  plan.json         the plan: source of truth, edited by hand
  dashboard.html    the page: the template with plan.json embedded verbatim; regenerated, never edited
  research/         unchanged: the research questions, findings and summary (evidence for column 1)
  architecture.md   unchanged: the systems design (evidence for columns 2 and 3)
  impact.md         unchanged
```

`plan.md` and `final-implementation.md` are no longer written. The verdict column and its inspector carry the steps the implementer follows; the as-built column carries the record that `final-implementation.md` used to hold.

Scripts, run from the repository root:

```bash
node .claude/skills/plan-dashboard/scripts/new.js <issue> <slug> --title "Short name" --issue-title "The issue's title" --baseline <sha> --date YYYY-MM-DD
node .claude/skills/plan-dashboard/scripts/embed.js docs/plans/<issue>-<slug>    # copy plan.json into dashboard.html
node .claude/skills/plan-dashboard/scripts/check.js docs/plans/<issue>-<slug>    # the rules below; exit 1 on a failure
```

Run `embed.js` then `check.js` before every commit that touches the folder. `check.js` fails on a placeholder left from the template, so a half-filled plan cannot be committed as finished.

A cell has a stable address: `dashboard.html#<row-id>.<column>` and `dashboard.html#<row-id>.considered.<option-id>`, columns `gap`, `considered`, `verdict`, `built`. Use these in commit messages, PR comments and review replies instead of quoting the plan.

## Who does what

**Planner, after research and architecture.**
1. `new.js` with the issue number, the slug, the baseline sha and date (the commit the research and the architecture were written against).
2. Fill `function` (one paragraph: what the issue asked, what the plan does), `systems` (the registry slugs the rows belong to, from `docs/dashboards/systems.json`), the `gap`, `considered` and `verdict` milestones with their dates, `documents` and the `followUps` already known.
3. Write the rows (below). Every row's `asBuilt` is `{"state": "pending"}`; `status` is `planned`; `asBuilt` at the top level is `null`.
4. `embed.js`, `check.js`, then commit the folder with `architecture.md` and `impact.md`. This commit is the `verdict` milestone's `ref`.

**Implementer, at every stage and commit.**
1. Set `status` to `building` at the first code commit.
2. For every row the commit touches, fill `asBuilt`: `state`, `summary` (what exists now, in the past tense), `files`, `tests`, `commits`, `verified`.
3. Each review finding that is adopted becomes a `changes[]` entry on the row it changes, with the round in `when` ("Stage C · first review") and the commit in `ref`. If a finding reverses a verdict, the state becomes `deviated` and the entry states the new decision; the verdict cell stays as written.
4. A part not built is `dropped` (reason in `summary`) or `deferred` (`issue` names where it went).
5. `embed.js`, `check.js`, and commit the folder in the same commit as the code it describes. The git history of `plan.json` is then the plan's own timeline.

**Presenter, at landing.**
1. `status` to `built`; top-level `asBuilt` with `ref`, `sha`, `date`, `pr` and `version`; the `built` milestone; the remaining `trail` events (reviews, the merge); `followUps` complete.
2. `check.js` must pass with no pending part.
3. Link the page from the system dashboard's Plans card (`docs/dashboards/index.html`) and set the system's `lastAmended` in `systems.json`, as the system-dashboard skill directs.

## Rows

A row is a part of the change a reader could point at: a class or file, a test suite, a living document, a build file, a release step. A scope boundary the plan deliberately leaves alone is also a row, under the group `Left for later`, so the decision not to act is on the record with its reason.

- Take the rows from the architecture's system inventory and decomposition, then add the tests, the documents and the release. Four to twelve rows is the usual size; a bigger plan is usually several units, each with its own folder.
- `group` orders the board: `Code`, `Tests`, `Documents`, `Release`, `Left for later`, in that order. Rows without a group render first.
- `id` is kebab-case and never changes once committed; it is the cell's address.
- `system` is a registry slug and gives the row its colour, one colour per system on the whole page, in the order `systems` lists them.
- `where` is the path, abbreviated the way the repository's plan documents abbreviate it; state the abbreviation once in `notes.paths`.

## Column rules

### 1 · Gap

The hole, never the fix. State what is wrong or missing at the baseline, in terms a reader can verify: `summary` in one to three sentences, `detail` for what the summary cannot hold, `evidence` as `path:line — what it shows` at the baseline sha, and a `source` in the research. `kind` is one of `bug` (behaviour is wrong), `missing` (behaviour does not exist), `debt` (code or documents say the wrong thing), `risk` (behaviour is right but exposed). A sentence that names the solution belongs in column 3.

### 2 · Considered

Every option the design weighed, the one taken included, the issue's own proposals included, and "leave it as it is" when it was a real option. Exactly one option is `accepted`. A `declined` option carries `why` (the trade-off that ruled it out) and, when it is true, `wins` (the condition under which it would have been the right call). A `deferred` option was left open at plan time and is settled in column 4. Declined options are never deleted: they are what a later plan must argue against before it reopens the question. Each option names its `source` section.

### 3 · Verdict

`kind` is `build` or `leave`. `summary` states the decision in one or two sentences; `defense` says why it beats the options declined. `steps` are what the implementer follows: one entry per step with `id` (the plan's own label, such as `A-3`), `what` (a sentence naming the file, the anchor and the change), `files`, `check` (the command or observation that proves the step) and, only where exact wording matters, `detail` with the short snippet. Long code listings do not go in a plan: the code is written in the code. `tests` names the level and the test class for each claim; `risks` names each risk and what bounds it. A `leave` verdict says what pins the decision (a test, a document note) and which follow-up owns the gap.

### 4 · As built

`state` is one of:

| State | Meaning | Requires |
|---|---|---|
| `pending` | not built yet | nothing; the only state in a `planned` plan |
| `as-planned` | built as the verdict says | `summary` |
| `amended` | built as the verdict says, with changes decided during implementation or review | `summary`, `changes[]` |
| `deviated` | built differently from the verdict in some respect; the decision changed | `summary`, `changes[]` naming each difference and the new decision |
| `dropped` | not built | `summary` with the reason |
| `deferred` | not built here, moved to a named issue | `summary`, `issue` |

`summary` describes what exists at the as-built commit. `changes[]` entries have `what`, `why`, `when` and `ref`. `files`, `tests`, `commits` and `verified` say where it is and how it was proved. The as-built state colours are the page's only status colours and always sit beside their glyph and label; nothing else on the page uses them.

## Status, milestones, trail

`status` is `planned` (every part pending), `building`, `built` (no part pending; `asBuilt` filled) or `abandoned`. `baseline` is the commit the plan was written against; `asBuilt` the commit the fourth column describes. `milestones` carry one dated entry per column (`gap`, `considered`, `verdict`, `built`) with a `ref` to the document or commit; they become the column headers. `trail` holds the finer dated events (the issue opened, research commits, each review round, the merge) and renders in the Context section. `followUps` lists issues with how each relates; `documents` lists the folder's documents and the living documents it changed, with `historical: true` for anything superseded by the page.

## Writing

Write as the repository's dashboards write: sentences, not fragments; the present tense in columns 1 to 3 and the past tense in column 4; file paths and identifiers in `` `code` `` spans and issue numbers as `#n` (the page links both); numbers where they decide something (test counts, byte sizes) and nowhere else; no marketing. A cell's `summary` must stand alone at two to four lines; everything longer goes in `detail`, `defense`, `steps`, `changes`.

## What check.js enforces

Fails: a missing schema, repo, issue, slug, title, function, baseline or status; a slug that is not the folder name or does not start with the issue number; a `built` plan without `asBuilt.sha` and `asBuilt.date`; a `planned` plan with any part not `pending`; a `built` plan with a `pending` part; a row without an id, name, system, gap summary, options or verdict summary; a row whose options do not have exactly one `accepted`; a declined or deferred option without `why`; an `amended` or `deviated` part without changes, or a change without `what` and `why`; a `dropped` part without a reason; a `deferred` part without an issue; a bad date, sha, state, kind or verdict value; a placeholder left from the template; a page whose embedded copy differs from `plan.json`; a tab panel without a legend; a dead `href="#"`.

Warns: no evidence on a gap, a build verdict without steps, a system a row names that `systems` does not list, more than six systems, a missing milestone.

## The template, and changing it

`template/dashboard.html` shares its tokens, header, chips, KPIs, tab mechanics, inspector, legends and footer with the system dashboard pages and diverges where a plan is not a system: no Systems, Structure, C4, Threads, Lifecycles or Guarantees content, and three views of the plan's own rows (Timeline, Decisions, As built) plus the embedded Source. The divergences are recorded in the page's footer comment.

To change the template, edit `template/dashboard.html` and copy it over an existing page's `dashboard.html`, then run `embed.js` for that folder: the data lives in `plan.json`, so re-templating loses nothing. Never hand-edit a page's HTML; the next embed would keep the edit and the next template refresh would lose it.

## Existing plans

Folders written before this format keep their Markdown. Issue #38's folder carries the first dashboard, written after the fact from its research, architecture, plan, impact and as-built documents as the worked example; its `plan.md` and `final-implementation.md` remain, marked historical in the page's Documents card. A run that reopens an old folder adds the dashboard beside the Markdown rather than converting it.
