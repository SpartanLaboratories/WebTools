# Plans

One folder per issue, `docs/plans/<issue>-<slug>/`. Each holds the research the change rests on, the systems design, and the plan: what was missing, what was considered, what was decided and what was built.

## Reading a plan

From Issue #38 on, the plan is a page: **`dashboard.html`**, rendered from **`plan.json`** in the same folder. It is a timeline with four columns and one row per part of the change:

1. **Gap** — what was wrong or missing at the baseline commit, with evidence.
2. **Considered** — every option the design weighed; the filled mark is the one taken, the hollow ones were declined, each with the trade-off that ruled it out.
3. **Verdict** — the decision, its defence, and the steps that built it; or why the gap was left alone.
4. **As built** — what exists at the as-built commit and every change from the verdict, with its reason and the review round that decided it.

The first three columns are written once, when the plan is accepted, and never rewritten; the fourth is kept as the work lands. So the page reads as history: the plan as it was, beside the change as it is. A dashed fourth cell is not built yet. The **Decisions** tab lists every option across rows; the **As built** tab lists every change from the verdict. Click any cell for its detail.

The format, its rules and its scripts are in [`.claude/skills/plan-dashboard/SKILL.md`](../../.claude/skills/plan-dashboard/SKILL.md). The system dashboards in [`docs/dashboards/`](../dashboards/index.html) show the repository as built and link the plans that amended it.

## Folders

| Issue | Folder | Plan |
|---|---|---|
| #38 | [`38-handshake-empty-name/`](38-handshake-empty-name/) | [dashboard](38-handshake-empty-name/dashboard.html) — the first plan in the dashboard format, written after the fact as the worked example; its `plan.md` and `final-implementation.md` remain as history |
| #55 | [`55-http-client-stack/`](55-http-client-stack/) | `architecture.md`, `impact.md`, research; the sub-issues #61 to #70 plan their own units |
| #1, #3, #5, #8–#15, #34, #40 | the remaining folders | Markdown plans (`plan*.md`), with `architecture.md` and `final-implementation.md` where the run produced them |

Earlier folders keep their Markdown as written. A run that reopens one adds a dashboard beside it rather than converting it.
