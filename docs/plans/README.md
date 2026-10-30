# Plans

One folder per issue, `docs/plans/<issue>-<slug>/`, nested under its tracking issue's folder. Each holds the research the change rests on, the design, the plan and the as-built record, as Markdown, and from Issue #38 on a **plan board** beside them: one page that shows the change's planning and development at a glance.

## The work board

[**`index.html`**](index.html) lists every planning and development run in the repository: one row per issue that has a plan board or a `level:` label, grouped by tracking issue in landing order, then the rest by age. Each row shows the run's state and pipeline step, what it is doing now, its qa passes, what it waits on and what it blocks, its branch and its board. The runs being worked on glow. Its Context lists the roadmap and the open issues no roadmap item claims. It is regenerated from every board and the issue tracker each time a change lands, so between landings a run's live state is on its own board, on its branch.

## The questions inbox

[**`inbox/`**](inbox/README.md) holds the source and the protocol of the published page where the pipeline asks you its questions and you answer them, from any device. Each card says which run asked, what the run recommends and whether the run waits for you.

## Reading a plan board

A board is `dashboard.html` in the issue's folder, rendered from `plan.json` beside it. Under the header, the **WIP strip** shows where the run is: its step on the pipeline, what it is doing, its qa passes and its blockers, or, once merged, the landing. Below it, the **Timeline** has one row per part of the change and four columns:

1. **Gap** — what was wrong or missing at the baseline commit, with evidence.
2. **Considered** — every option weighed, each with its verdict: accepted (green), declined (red, with the trade-off that ruled it out), partial or left open (yellow).
3. **Verdict** — the decision and its defence, the steps named, the tests by level; or why the gap was left alone (the **Left for later** rows).
4. **As built** — what exists and every change from the verdict: as planned (green), amended (yellow), deviated (orange), dropped (red), or pending (dashed).

The first three columns are written once and never rewritten; the fourth records every difference, so the board reads as history: the plan as it was, beside the change as it is. The cell being worked on glows. A board is a summary with little code: each cell links the Markdown section that holds the detail, `plan.md` for the steps and `final-implementation.md` for the as-built record. The **Decisions** tab lists every option, the **As built** tab every change; click any cell for its detail, or link one as `dashboard.html#<row>.<column>`.

A **tracking board** (a level-4 issue) has one row per unit, each a sub-issue planned and built at its own level on its own board; its **Chain** tab draws the units and what blocks what, and a unit's row links its board once it has one.

The boards are kept by the presenter agent from the events of each `/resolve-issue` run, with the global `dashboards` skill's plan set (its templates, rules and checks). The system dashboards in [`docs/dashboards/`](../dashboards/index.html) show the repository as built and link these boards.

## Boards

| Issue | Board | Notes |
|---|---|---|
| #55 | [`55-http-client-stack/dashboard.html`](55-http-client-stack/dashboard.html) | tracking board: the eleven units of the HTTP client stack in landing order, with `architecture.md`, `impact.md` and the research |
| #38 | [`38-handshake-empty-name/dashboard.html`](38-handshake-empty-name/dashboard.html) | the malformed `Iam` refusal, written after the fact as the worked example; its `plan.md` and `final-implementation.md` hold the detail |

## Earlier folders

#1, #3, #5, #8–#15, #34 and #40 keep their Markdown plans (`plan*.md`), with `architecture.md` and `final-implementation.md` where the run produced them. A run that reopens one adds a board beside the Markdown rather than converting it.
