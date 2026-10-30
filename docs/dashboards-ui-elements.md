# Board UI elements: a catalogue for a UI library

Every UI element the repository's boards use, with its anatomy, variants, states, tokens and behaviour. It was taken from the
committed boards, so a component library built from it can redraw them as they are:

| Board type | File(s) | Template meta |
|---|---|---|
| **Plan board** (issue board; a level-4 issue gets a *tracking board*) | `docs/plans/<issue>/dashboard.html`, data `plan.json` | `plan/template/dashboard.html` |
| **Work board** (every run in the repository) | `docs/plans/index.html`, data `board.json` (`docs/plans/board.js`) | plan set §5 |
| **System / state boards** (supersystem and per-module) | `docs/dashboards/index.html`, `docs/dashboards/<module>/index.html`, data `state.json`, `systems.json`, `workspace.dsl` | `system/template/supersystem.html` |

All three are single self-contained HTML files. They hold no framework and load nothing from outside. The data sits in an embedded
`<script type="application/json">` block, and vanilla JS renders the page from it when it loads.

---

## 0. Design rules the components must enforce

The boards depend on these rules. Treat them as library invariants, not as style tips.

1. **Colour never carries meaning alone.** Every status colour sits beside a **glyph and a word**, for example `✓ landed` or `⊘ blocking`.
2. **Colour families do one job each and never mix:**
   - `--sys-1..6`: *which system* (categorical, in registry order). The 7th top-level system and later ones fall back to `--muted`. A child system takes its top-level ancestor's slot.
   - `--good / --warning / --serious / --critical`: *outcome/status* only.
   - `--accent` (violet): *work in progress now* and *up next*, and nothing else.
   - `--ph-1..3`: the *phase ramp* (start / steady / teardown), Lifecycles only.
   - `--wk-<family>-a/-b`: the *kind of work* (planning, build, checking, landing, other) on the Timeline tile only.
   - Neutral ink ramps (`color-mix(var(--ink) N%, var(--surface))`) cover ordinal scales that must not spend a hue: test levels L1→L5 and dependency locality near→far.
3. **Dashed means planned, pending or not started, and nothing else.** Lifelines, return arrows and frames stay solid.
4. **Every view carries a legend** (`.key`). It lists exactly the encodings drawn in that view and nothing more, and it is generated from what was drawn.
5. **The "live" dot is ink**, never a status or accent colour.
6. **Reduced motion:** every animation has a still fallback that keeps the middle strength of the effect, so the meaning stays visible.
7. **Two themes** (light and night), with tokens redefined for each. No component hard-codes a colour.

---

## 1. Design tokens

### 1.1 Colour (light / dark)

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg` | `#f3f5f8` | `#10141b` | page background |
| `--surface` | `#fcfcfd` | `#171c25` | cards, panels |
| `--surface-2` | `#eef1f6` | `#1f2530` | wells, hovers, tracks, `pre` |
| `--ink` | `#0e1420` | `#eef1f6` | primary text, strong strokes |
| `--ink-2` | `#4a5468` | `#b8c0ce` | secondary text, connectors |
| `--muted` | `#7d879a` | `#8a93a5` | labels, kickers, pending strokes |
| `--line` | `#dfe4ec` | `#2a313d` | borders |
| `--hair` | `rgba(14,20,32,.10)` | `rgba(255,255,255,.10)` | row separators |
| `--good` | `#0ca30c` | same | status: success |
| `--warning` | `#fab219` | same | status: caution / waiting on the user |
| `--serious` | `#ec835a` | same | status: escalated / deviated |
| `--critical` | `#d03b3b` | same | status: failure / declined / breaking |
| `--accent` | `#6d47e0` | `#a58bff` | in progress, up next |
| `--on` | `#0e1420` | `#eef1f6` | selection outline / "on" stroke |
| `--focus` | `#2a78d6` | `#3987e5` | `:focus-visible` ring |
| `--link` | `#2a78d6` | `#6aa6f0` | reference links (`a.sref`, `a.more`) |
| `--sys-1..6` | `#2a78d6 #653184 #0d7680 #1d2660 #9656aa #1a9ec7` | `#3987e5 #8a44d8 #1f8089 #b4bef5 #a86fb8 #31b5f4` | system slots |
| `--wk-planning-a/b` | `#5596de / #1d5cab` | `#3a7fd0 / #80b6f0` | Timeline tile |
| `--wk-build-a/b` | `#1f9294 / #0c5c61` | `#1a8a8c / #4cc4c2` | 〃 |
| `--wk-checking-a/b` | `#b67ad0 / #84409f` | `#a35cbd / #d095e6` | 〃 |
| `--wk-landing-a/b` | `#2e3d99 / #1d2870` | `#b8c4fa / #d4dcfd` | 〃 |
| `--wk-other-a/b` | `mix(ink 40%, surface) / ink` | same | 〃 |
| `--ph-1/2/3` (system boards) | `#a49bed #6250d6 #352a91` | `#5546c0 #8a7ee6 #c9c2f7` | lifecycle phases |
| `--server / --client / --wire` (+`-soft`) | `#2a78d6 #653184 #0d7680` (`#e2edfa #eae3ee #deecee`) | `#3987e5 #8a44d8 #1f8089` (20 % alpha) | per-module boards: the roles |
| `--rel` | `#6b7382` | lighter | C4 relationship strokes |
| `--dim` | `.28` | same | opacity of faded flow nodes |

**Tint recipes** (use these instead of new tokens):

- status tint behind a pill: `color-mix(in srgb, var(--sc) 14%, var(--surface))`
- halted card wash: `color-mix(var(--warning) 12%, var(--surface))`
- accent wash: 7 % (cell glow), 4 % (soft glow), 16 % (glowing pill)
- info-card fill: `color-mix(var(--ink) 3%, var(--surface))`
- column band: `color-mix(var(--ink) 4.5%, var(--surface))`
- system fill in SVG: `color-mix(var(--c) 12%, var(--surface))`, or 5 % for a container

**Ink ramps:**

- test level `--tl`: L1 20 %, L2 34 %, L3 48 %, L4a 60 %, L4b 70 %, L4c 80 %, L5 100 % ink, none `--line`
- locality `--lc`: l1 22 %, l2 45 %, l3 70 %, l4 100 % ink

### 1.2 Typography

- `--sans: system-ui, -apple-system, "Segoe UI", sans-serif`
- `--mono: ui-monospace, "Cascadia Mono", Consolas, "SF Mono", monospace`
- Body text is `14px/1.45` sans. Tabular numbers use `font-variant-numeric: tabular-nums`.
- **The mono label style** (used everywhere): `700 10–10.5px mono`, `letter-spacing .07–.1em`, `uppercase`, `--muted`.
- Scale:

| Size | Use |
|---|---|
| 28px | h1, in mono |
| 22px bold | KPI value |
| 15px | function line, view h2, inspector name |
| 13px | body in cards |
| 12.5px | dense body |
| 12px | chips, legend |
| 11–11.5px | mono meta |
| 10.5px | pills |
| 9.5px | tags and small pills |

- `h1, h2, h3 { text-wrap: balance }`. Long code and paths get `overflow-wrap: anywhere`.

### 1.3 Shape, spacing, layout

- Radii: 3px (pills, badges), 4px (chips, the chain), 5px (segmented controls), 6px (cards, panels), 8px (column bands), 10–12px (round "pill buttons": theme, live, back).
- Border: 1px `--line`. The **left status bar** is 3–5px in a meaning colour (`--c`, `--sc`, `--tl`, `--lc`). The **top system bar** is 3px (system cards, phase cards).
- Gaps: 6, 8, 10, 12 and 14px. Page gutter: `max(16px, env(safe-area-inset-*))`.
- `.page`: `max-width 1280px`. From **1500px** wide it grows to `min(2400px, 100%)`.
- **Breakpoints:** 430, 520, 560, 600, 700, 760, 880, 900, 940, 1100 and 1500px. The main one is **940px**: below it the header stacks, the inspector stacks and the WIP grids go to one column.

### 1.4 Motion

| Keyframe | Period | What |
|---|---|---|
| `glow` | 2s ease-in-out ∞ | inset accent ring plus a breathing inner glow (6px→22px) |
| `glowsoft` | 2s | lighter version, for a whole column |
| `glowdot` | 2s | outer accent halo on a dot |
| `livepulse` | 1.6s | live dot: opacity 1→.3, scale 1→.65 |
| `charge` | 1.8s `steps(8,end)` | battery-segment arrow filling left to right |
| `svgglow` | 2s | SVG rect fill 8 %→30 % accent |

Under `prefers-reduced-motion: reduce`, every one of these stops at a fixed middle-strength frame, and transitions are turned off.

---

## 2. Page shell

| Element | Notes |
|---|---|
| `body` | `--bg` background, the gutter above, 40px bottom padding |
| `.page` | the centred column (above) |
| **Section order (plan board)** | header → WIP strip → tabs → stage (view + inspector) → Context → footer |
| **Section order (work board)** | header → KPI row → tabs → view → Context → footer |
| **Section order (system board)** | header → tabs → stage (view + inspector) → Guarantees → Context → footer |
| **Theme bootstrap** | an inline `<script>` in `<head>` reads `localStorage["dashboards-theme"]` and sets `html[data-theme]` before the first paint |
| `<meta name="dashboard">`, `<meta name="template">`, `<meta name="template-divergence">` | machine-readable lineage, plus one entry per divergence from the template |

---

## 3. Header

| Element | Class | Anatomy / behaviour |
|---|---|---|
| **Top bar** | `.topbar` | flex row: breadcrumb on the left, `.tools` on the right (`margin-left:auto`), wraps |
| **Breadcrumb** | `.crumbs` | 12px `--muted`; links have no underline until hover |
| **Live indicator** | `.livechip` (+`.ld` dot) | round chip, 600 11px mono. States: `.on.pulse` (dot pulses: `LIVE · updated 12 s ago`), `.on` (still: `LIVE · no change for 6 min`), `.on.na` (grey dot: `LIVE · data unavailable`), `.off` (hollow dot: `not live · as of <local time>`). Hidden when empty. Has a `title` tooltip that explains the state. Refreshes every second. |
| **Theme switch** | `.themebtn` | round chip button reading `☀ light` / `☾ night`, with `aria-pressed` = dark. It follows the OS setting until clicked, then remembers the choice (`localStorage`, shared across boards). It listens for OS changes. |
| **Title** | `h1` | 28px mono. On a plan board the `#n` is `a.ghn`, a link to the GitHub issue with a 2px bottom border. |
| **Issue line** | `.issueline` | 12.5px mono `--ink-2`. The issue's GitHub title. Hidden when empty. |
| **Function line** | `.function` | 15px `--ink-2`, max 78ch. One sentence on what the subject does. |
| **Header chips** | `.chips > .chip` | key–value tags (§5.3). From 940px they become **cards in the right half** (`display:block; flex:1 1 190px`), label over value; `.wide` spans the half. |
| **Two-half layout** | `header` grid ≥ 940px | left column: h1, issue line, function. Right column: the chips, spanning every row. Top bar across both. |

---

## 4. Tabs, stage and inspector

| Element | Class | Anatomy / behaviour |
|---|---|---|
| **Tab bar** | `.tabs[role=tablist]` | flex row with a bottom border; scrolls sideways if it overflows |
| **Tab** | `.tab[role=tab]` | 600 weight, padding 10×14. Selected: ink text and a 2px ink underline. Keys: ←/→ wrap, Home/End. **Roving tabindex**: only the selected tab is in the tab order. Selecting writes `#<tab>` into the URL with `replaceState`. |
| **Model tab marker** | `.tab.model` | `::after` adds a superscript ` JSON` or ` DSL` (10px mono muted) to the tab that shows the raw source |
| **Sub-tabs** | `.seg.stabs > .stab[role=tab]` | segmented-style tablist (Tests: Added / Amended / Relevant / Other). The selected one is filled ink with `--bg` text. An optional `<small>` holds a count. Same keys as the tabs. URL `#tests.<sub>`. |
| **Stage** | `.stage` | grid `minmax(0,1fr) 320px` (360px from 1500px wide). Under 940px it is one column. Modifiers: `.below` (the inspector goes under the view and shows its sections as cards), `.solo` (no inspector). |
| **View panel** | `.view[role=tabpanel]` | surface card, padding 12. Holds `h2` (15px, optional `<small>` in mono muted), a **lede** (`.lede`, 13px `--ink-2`, max 90ch) and the content, and ends with its **legend**. Inactive panels are `hidden`. |
| **Inspector** | `aside.inspector[aria-live=polite]` | sticky (`top:12px + safe-area`), with its own scroll (`max-height: 100vh-24px`). Parts: `h2` label ("INSPECTOR"), `.kicker` (11px mono muted), `.name` (700 15px), `.vis` (a row of pills), `p` / `p.lead`, a `dl` of `dt` (uppercase 10.5px muted) and `dd` (lists allowed), `a` with a hairline underline. Empty state: "Select a cell, an option or a card…". **Esc** clears the selection. Each tab has a default thing to show. |
| **Inspector cards** | `.icards > .icard / .icard-col / .icard-head` | used when the stage is `.below`: a flex-wrap row of `flex:1 1 250px` cards (the head card is 1.6× wider), stacking under 560px |

---

## 5. Labels, pills and tags (the status primitives)

### 5.1 Status pill: `.vp` (the most-used element)

```
[▌✓ landed]   left bar = --sc, tint 14 %, 1px --line border, 600 10.5px mono, radius 3
```

- Children: an `<i>` holding the glyph (normal style, bold) and the word.
- Colour classes: `.good .warning .serious .critical .neutral` (no tint) `.accent .ink`.
- Modifiers:

| Modifier | Effect | Means |
|---|---|---|
| `.dashed` | dashed border | pending |
| `.hollow` | surface fill | complete, not merged |
| `.glow` | breathing accent | the run in progress |
| `.held` | still accent ring | waiting on the user |

- Small size inside a list: 9.5px, padding 1×5. Large size in the work-board table: 12px, padding 3×8.

### 5.2 Other inline labels

| Element | Class | Look | Used for |
|---|---|---|---|
| **Badge** | `.badge` (+`[data-sys]`, `.critical`, `.warning`, `.planned`, `.closed`, `.bug`) | 10px mono, 1px line, radius 3. `data-sys` adds a 3px system bar. Critical and warning add a bar, a tint and 600 weight. `.bug` adds a `▲ ` prefix (neutral). `.planned` is dashed. `.closed` is muted. | labels, system tags, compatibility |
| **Swatch** | `.sw` | 10×10 square, radius 2, `background: var(--c)` | a system's colour beside its name |
| **Kind chip** | `.kind` | 600 10px mono, neutral solid border | the kind of a cell (a fact, not an outcome) |
| **UP NEXT tag** | `.tag-next` | 700 9.5px mono, accent border and text on surface | the next unit up |
| **QA mark** | `.qamark` | neutral pill with a glyph | "✎ fixed in place · no 2nd pass" |
| **Dependency pill** | `.dep-pill` = `.vp` + `.loc` | two joined halves: build state (status) + locality (ink ramp `.l1–.l4`) | Depends on |
| **Locality chip** | `.loc.l1–l4` | neutral pill, left bar from the `--lc` ramp | ⌂ repo, ◇ org, △ parent org, ↗ external · name |
| **Thread start pill** | `.pill` (.eager filled, .lazy outlined, .cond dotted, .planned dashed) | 10px mono, 1.5px ink-2 border | when a thread starts |
| **Check chip** | `.chk.ok / .gapc / .none` | neutral pill, left bar good / serious / critical | trust-boundary checks |
| **Role tag** | `.role` | 700 10px mono, ink-2 border | role of a system |
| **Thread chip** | `.tchip.server / .client` | 11px mono, 3px role bar | server/client side |
| **Ordinal** | text | `1st 2nd 3rd 4th…` | QA pass numbers |

### 5.3 Header chip: `.chip`

- 12px mono, padding 6×8, radius 4. Children: `.ck` (uppercase label) and `b` (value).
- Variants:
  - `.chip.status` (3px left bar: `.built` good, `.abandoned` critical, `.planned` dashed)
  - `.chip[data-sys]` (4px system bar)
  - `.chip.progress` (with an embedded **`.cbar`** progress bar; `.done` gives a green bar)
  - `.chip .sysl` (an inline swatch and name, repeatable)
  - `.chip[hidden]` (hidden until data fills it)

---

## 6. Progress and quantity

| Element | Class | Anatomy |
|---|---|---|
| **Segmented bar** | `.seg-bar > i` | 6px flex track with 2px gaps, radius 3. Segment classes: `.good .warning .serious .critical .neutral .accent .ink .left` (muted, 50 % opacity), `.dash` (dashed outline, pending), `.slot` (empty outline, an unused slot), `.hollow` (green outline), `.pln` (dashed muted), `.tp` (Timeline step, `--tp` colour, min-width 4px). Each segment's width is its share. |
| **Chain progress bar** | `.cbar > i` | 4–5px track, green fill = units landed / all units, with a 1px line border. Max width 180–420px. |
| **Chain summary** | `.chainsum` / `.chainprog` | "**3** of 11 units landed" plus a `.cbar` |
| **Horizontal bar chart** | `.bars` (`.lbl .trk .fil .val`) | 3-column grid. 10px track, neutral `--ink-2` fill (one series means no hue), tabular value. |
| **Pipeline stepper** | `ul.steps > li(.todo/.cur/.sel) > .dot` | inline steps joined by 16px rules. Done: solid ink-2 dot. `.todo`: dashed hollow dot, dashed rule, muted text. `.cur`: accent dot with the `glowdot` halo, bold. A finished step's label is a **`.stepbtn`** (underlined, opens that step in the step panel). `.sel` rings it in ink. |
| **Numbered steps** (system boards) | `ol.steps > li.ph1/2/3` | CSS counter prefix. Bordered mono boxes with a phase-coloured left bar. `.planned` is dashed. |
| **Unit chain** | `ul.chain > li(.done/.pending/.glow/.held/.upnext) > a / span.u` | row of mono unit chips with a 4px system bar. `.pending` is dashed. `.glow` and `.upnext` take the accent marks. |

---

## 7. Accent marks (work-in-progress signalling)

| Mark | Class | Look | Meaning |
|---|---|---|---|
| **In progress** | `.glow` | inset 2px accent ring, breathing inner glow, 7 % accent wash | the work happening now |
| **Column at work** | `.glow-soft` / `.colglow` / `.colon` | 1.5px ring, lighter breathing, 4 % wash. The column head gets a 9 % wash and an accent underline. | a whole column at work |
| **Held** | `.held` | the ring alone, still | in progress but waiting on the user |
| **Up next** | `.upnext` + `.tag-next` | mild **outer** glow, never animated | the next unit to start |
| **Current dot** | `.dot` with `glowdot` | surface gap ring, accent ring, halo | current step or sub-step |
| **Charging arrow** | `.cell.verdict.charging::before` | arrow-shaped clip-path filled with accent segments that step left to right (`.still` stops it) | a verdict is being written |
| **SVG in progress** | `.chainsvg .unit.glow` | 2.5px accent stroke, fill breathing 8→30 % | unit in progress in the graph |

---

## 8. Cards and containers

| Element | Class | Anatomy / variants |
|---|---|---|
| **KPI tile** | `.kpis > .kpi` | auto-fit grid (min 150–168px). `.l` uppercase 11px label, `.v` 22px bold tabular value (+`small`), `.s` 12px sub-line, a **`.seg-bar`**, and **its own `.key`** legend inside the tile. |
| **Timeline tile** | `.tl-tile > .kpi` | full-width KPI: one bar segment per timed step visit, coloured by work family. Legends `.key.tl-fams` (families) and `.key.tl-steps` (a multi-column list: mark · step · when · duration, right-aligned). `.tl-approx` marks estimated times. |
| **Tracers** | `svg.tracers` | absolutely positioned SVG paths that join each KPI tile to its part of the Timeline tile. Drawn from 760px wide. Each tile has a `.tracer-lab` (dot, part name, time). |
| **WIP strip** | `section.wip` | surface card with a 4px accent left bar. State variants: `.landed` (good), `.halted` (warning bar and wash), `.escalated` (serious), `.cancelled` (critical). Parts: `.wip-head` (`.state` with its glyph, `.upd` timestamp on the right), `.wip-detail`, `.wip-wait` (bold), `.wip-sub` (uppercase sub-heading), a stepper, `.wip-facts` info cards, `.key`. |
| **Split WIP** (issue board) | `.wip.split > .wiprow > .wipmain + .sp` | two equal columns (strip / step panel), stacking under 940px. The legend card goes underneath. |
| **WIP cards** (tracking board) | `.wip.wipcards > .wipgrid > .wipcard(.phase/.unit/.next/.wait) + .sp` | ≥ 940px fixed shares **35 / 25 / 40 %**: the phase card spans every unit row (`--rows`), and each unit row (`--r`) holds a unit card and its step panel. `.next` is dashed with a system bar. `.wait` has a warning wash and lists items with `small` sub-lines. `.wipcard-t` is the title row with a `.k` label. `.chip-bar` is a progress bar. `.run` is a mono run line. |
| **Info cards** | `.wip-facts > span` (+`.l` label) | small rounded cards, label before value, 3 % ink fill. `.prog` (progress note, ink-2 bar) and `.prog.stale` (55 % opacity after 20 min idle). |
| **Step panel** | `.sp` (+`.sp-held`, `.past`) | card with a 4px ink-2 bar. `.sp-t` title, `.sp-when` (mono muted), `p.sp-sum` / `p.sp-wait` (bold). `.sp-list` is the sub-step list: dot, `.q` text, `small` meta; its states are `.todo` (dashed dot) and `.cur` (glowing dot). `.sp-opts` lists interview options. `.sp-pass` is a QA pass box (status left bar; `.run` dashed). `.sp-view` + **`.sp-back`** form the "viewing a past step · back to now" bar. `.sp-visit` repeats a step (h4 label). |
| **Generic card** | `.cards > .card` | auto-fit 280px grid, `h3` 13px (+`small` mono muted). Phase variants `.ph1–3` add a 3px top bar. |
| **Built card** | `.bcards > .bcard` | card with a 4px system bar, clickable. `.st-pending/.st-deferred` are dashed. `.bh` header (`.nm` name, `.wh` mono path, pills), `p`, `.changes` list (`b` title, `small` meta), `.meta` mono footer. `.ichg` is an impact-change card, not clickable. The grid is 520px columns from 1500px wide. |
| **Test card** | `.tcards > .tcard` | 340px auto-fill grid, 5px left bar from the **test-level ink ramp** `--tl`. `.th` (`.cn` class name, result pill), `.pk` package, `.tm/.tr/.tu` meta lines. `.sel` outline. |
| **Impact card** | `.icard.cmp(.critical/.warning)` | compatibility colouring: border, bar and 9 % tint. Its system becomes a badge. `.versions` mono line. |
| **System card** | `.syscards > a.syscard` | link card with a 3px **top** system bar. `.nm` (700 15px mono), `.fn` function, `.meta` mini-chips, `.st` uppercase state, `.role`. `.planned` is dashed at 70 % opacity. Grouped under `.sysgroup` headings. |
| **Guarantee card** | `.guarantee-cards > .guarantee-card` | compact titled list card. `.gap` (muted italic) marks a guarantee not met. |
| **Thread card** | `.thread(.server/.client/.wire/[data-sys]/.planned)` | 2-column grid: `.n` name (mono), a `.pill` on the right, `.r` role line. 4px coloured left bar, `role=button`, `tabindex=0`. `.k` holds the "process" note. |
| **Callout** | `.callout(.ph1–3)` | `--surface-2` box with a 3px phase bar, `b` for emphasis |
| **Outcome line** | `.outcome` | `--surface-2` box with a 3px ink bar. The result of a decision-path control. |
| **Empty / none notice** | `.inone`, `.isec .empty(.pend)`, `.tsub .empty` | bordered note. `.lead` is bold. `.pend` is dashed. |
| **Excerpt** | `blockquote.excerpt` | 3px ink-2 left bar on `--surface-2`. The quoted plan section, shown in the inspector. |
| **Source viewer** | `pre.src` (`.k` keys in link colour, `.s` strings ink-2) | 12px/1.5 mono, max height 70vh, scrolls, `tabindex=0` |

---

## 9. The Timeline grid (`.board`, plan boards' Design tab)

The plan board's signature component: rows are the parts of the change, and the four phase columns are **Gap → Considered →
Verdict → As built**.

| Part | Class | Details |
|---|---|---|
| **Grid** | `.board` | `minmax(150px,190px) repeat(4, minmax(0,1fr))`, column gap `--cg` 18px (24px at ≥ 1500px wide) |
| **Column bands** | `.board > .band` | absolutely positioned neutral bands behind each phase column, laid out by JS from the heads' positions. Radius 8. Layout only, not an encoding, and absent from the legend. |
| **Column head** | `.colhead.phase` | **sticky**, with a 3px ink-2 underline. `.ph` holds a numbered ink disc (`b`, 20px, inverted) and an uppercase name. `.q` is the column's question. `.dt` is a mono date or link. |
| **Group heading** | `.grouphead` | spans the full width, uppercase 11px muted, `small` mono note |
| **Row label** | `.rowlab` | 4px system bar. `.nm` bold name (it can be a link to the unit's board), `.wh` mono where, `.sy` system. Takes `.glow` / `.held`. |
| **Cell** | `.cell` (`role=button`) | surface card, 12.5px. `.kick` header (uppercase mono label, `.kind` chip, pill), `.sum` text, `.meta` mono row. **Connector:** `::before` is a 2px line from the previous column and `::after` a 10px dot node. `.gap` has no incoming line. `.sel` is a 2px `--on` outline. |
| **Cell states** | `.built.(accent/ink/good/warning/serious/critical)`, `.pending`, `.deferred` | built: a 3px status left bar. Pending: dashed border, dashed connector, hollow dashed node. |
| **Research ring** | `.cell.rs.rs-(planning/researching/returned/verifying/complete)` | a 2px **ring** (never a fill) in the research scale: critical → serious → serious → warning → good |
| **Options list** | `ul.opts > li > button.opt(.good/.warning/.critical/.dashed/.sel)` | full-width buttons, 3px status bar, 10 % tint. `i` glyph, `span` text, `.vw` uppercase verdict word. |
| **Statements** | `ol/ul.stmts(.marked)(.num)` | the verdict's bullets or numbered list. Each ends with an **`a.sref`** "(§n)" link; a plain click shows the excerpt in the inspector. As-built `.marked` puts a small `.vp` before each statement. |
| **Narrow layout** | `@media ≤ 900px` | the grid becomes a block stack. Heads, bands and connectors are hidden, and a column glow moves onto each of the column's cells. |
| **Deep link** | `#<row>.<col>[.<opt>|.s<n>]` | selects the cell, option or statement and scrolls it into view |

---

## 10. Tables and lists

| Element | Class | Details |
|---|---|---|
| **Data table** | `table.tbl` in a `.scroll` | 12.5px. `th` uppercase 10.5px muted. Hairline row borders. `tr[data-sys]` gets a 4px system bar on its first cell. Hover gives a `--surface-2` row (rows are clickable). `.sub` is a sub-line. `tr.planned` is dashed at 60 % opacity. `tr.ph1–3` gets a phase bar. |
| **Work-board runs table** | `.tbl` + `tr.grp`, `tr.run(.nob)` | group header rows (uppercase, with a mono link to the tracking issue). Run rows have a system bar, dashed `.nob` when the run has no board. Set column shares. Large type (14.5–15px). **Under 880px each row becomes a labelled card** (`td::before` content "State", "Now", "QA", "Depends on", "Blocks · enables"; reordered; two columns between 600 and 880px). |
| **Decision table** | `.tbl.dec` | min width 720px, muted first column |
| **Matrix** | `table.matrix` | centred cells, `.dot` (10px ink circle) marks a hit, hover highlights the row |
| **Guarantees table** | `table.gtbl` | `tbody th` as mono row heads, `td.n` right-aligned numbers |
| **Counts table** | `.tbl.tother` | right-aligned tabular `td.n`, level key in the `th` |
| **Trail** | `ul.trail` | date (mono muted) · phase tag (`.ph`, 9.5px) · text. The history log. |
| **Issues list** | `ul.issues` | `#n` (mono, a link to the board) + a GitHub link `a.gh` + title + `.tags`. Nested `ul` for children. |
| **Roadmap** | `ul.roadmap > li > .ri` | item row (`#n` · bold title · chain progress) with nested `.issues` and a `.more` line |
| **Docs list** | `ul.docs` | hairline-separated document links |
| **Steps list** | `.steps-list` | `b` mono step id · text · `.files` (mono) · `.check` ("check: …" prefix). On system boards it is clickable and `.on` marks the current step. |
| **Test units list** | `ul.tunits` | hairline rows with pills |
| **Notes** | `.notes` | paragraphs and lists, 12.5px |

---

## 11. Controls

| Element | Class | Details |
|---|---|---|
| **Control row** | `.ctl` | flex-wrap row. `label` is uppercase 11px muted around a control. |
| **Segmented control** | `.seg > button[aria-pressed]` | joined mono buttons. Pressed: ink fill with `--bg` text. `[aria-disabled]` is at 35 % opacity. It wraps at phone width. Used for filters and the decision-path picker. |
| **Button** | `.btn[aria-pressed]` | mono, 1px line, radius 5. Pressed: ink fill. Used for sequence `‹ prev`, `next ›` and `play`. |
| **Link button** | `.linkbtn` | `--surface-2` mono button in the inspector |
| **Back pill** | `.sp-back` | round ink-bordered button: back to the current step |
| **Step button** | `.stepbtn` | a finished pipeline step as an underlined button |
| **C4 ladder** | `nav.ladder > button.rung[aria-pressed]` | zoom-level buttons. `.lv` big level number, `.nm` name, `.sc` scope (ellipsis). `.lvl::after` connectors between rungs. `.sep` adds a gap. |
| **Zoom toggle** | `.c4head .zoom > button[aria-pressed]` | small segmented control |
| **Links** | `a.more` ("detail ›", link colour), `a.gh` (small muted GitHub link, underlined on hover), `a.sref` ("(§n)"), hairline-underlined inline links | an outbound `a.more` gets `target=_blank rel=noopener` |

---

## 12. Legend (`.key`)

- A flex-wrap row of `span` entries, each a **swatch** `i` followed by the words, 12px `--ink-2`.
- Below a tab, panel or strip it is unboxed. Under the split strip, WIP cards, Context and Guarantees it is **boxed**: surface, border, radius 6, padding 6×12.
- Swatch vocabulary (`.key i.*`, base 16×11, radius 3, 1.5px ink-2 border):

| Swatch | Draws |
|---|---|
| `.sbar` | system left bar (`--c`) |
| `.sys` | system fill |
| `.pln` | dashed (planned) |
| `.gl` | in-progress glow |
| `.nx` | up-next glow |
| `.hold` | held ring |
| `.wait` | waiting wash |
| `.ring` | research ring (`--rc`) |
| `.chg` | charging arrow |
| `.node` / `.node.hollow` / `.node.acc` / `.node.sel` | step dots |
| `.ln` / `.ln.dash` / `.ln.ret` | lines, solid / dashed / return |
| `.lnapi` / `.lnimpl` / `.lncfg` | dependency line weights |
| `.segk` (+status, `.dash`, `.slot`, `.hollow`, `.pln`, `.tp`, `.tl-out`) | bar segments |
| `.bar` | progress bar |
| `.cbar` | chain bar |
| `.runbar` / `.runbar.halt` | run rule |
| `.tlk` | test level |
| `.cmpk` | compatibility |
| `.inb` | neutral impact |
| `.progk` / `.progk.stale` | progress notes |
| `.trk` | tracer |
| `.colk` | column glow |
| `.ph1–3` | phases |
| `.srv` / `.cli` / `.wir` (+`.pub`), `.bs` / `.bc` / `.bw` | roles |
| `.con`, `.ext`, `.lib`, `.std`, `.pub` | consumer, external, library, standalone, public |
| `.hex`, `.end`, `.band`, `.dot` | flow shapes |
| `.swk`, `.barsk`, `.gapk` | swatch, bar, gap text |
| `.ok` / `.gapc` / `.none` | check outcomes |

- Real pills (`.vp`, `.badge`, `.kind`, `.loc`, `.qamark`, `.pill`, `.role`) can appear in a legend as their own swatch.
- **C4 legend** `.c4key` is a variant: `.arrow`, `.arrow.dashed`, `.frame`.

---

## 13. SVG diagram kit

Every diagram is generated into an `<svg role="img" aria-label="…">` with a `viewBox`. Clickable nodes feed the inspector. Planned
elements use `stroke-dasharray: 6 4` and 60 % opacity.

| Diagram | Classes | Encodings |
|---|---|---|
| **Landing-order chain** (tracking board) | `.chainsvg .unit (rect.box, rect.bar, rect.st, text.t/.s, rect.tagbox, text.tagtx)`, `.edge(.todo)`, `.arrow` | a unit box with a system bar and a status strip. `.pending` dashed. `.glow` / `.held` / `.upnext` / `.sel`. Columns are the earliest start. Pending edges are dashed. |
| **Structure map** | `.snode(.container/.planned/.sel)`, `.sdep (path, text, .lbg)` (also `.node.server/.client/.wire/.standalone/.pub/.chip`, `.band`, `.lane`, `.note`) | a system box tinted with `--c`. Nesting means composition. Arrows are dependencies with labels on a surface background. |
| **Decision flow** | `.fnode(.t/.on/.off/.planned)` (rect/polygon, `rect.stripe`, `.lvl`), `.fedge(.on/.off)`, `.flabel` | the picked path lights up in `--on` (2.5px). Other paths fade to `--dim`. Hexagon and end shapes. |
| **Sequence diagram** | `.life`, `.lh[data-sys]` (lifeline head), `.phase`, `.msg(.ret/.cur/.future/.planned)` (`.num`, `.lbg`) | the current message is highlighted, future ones are at 30 % opacity, and returns are thin. Driven by prev/next/play. |
| **C4 model** | `.c4canvas` (dotted-grid background), `.c4el (.card, .hdr, .n, .m, .d, .tg)`, `.c4rel (path, text, .lbg, .num)`, `.c4bound`, `.c4boundl`, `.c4band`, `.c4life` | rendered from Structurizr DSL at load. Hovering highlights neighbours (`.hl`) and dims the rest (`.dim`). Numbered relationships. Boundaries. |
| **State machine** | `.states (rect.ph1–3, text, .lab, line/path)` | states in phase colours |
| **Dependency graph** | `.dnode(.lib/.planned)`, `.ddep(.api/.impl/.cfg/.planned)` | line weight = Gradle configuration (api 2.6, impl 1.2, cfg muted) |
| **Tracers** | `svg.tracers path/circle` | KPI tile ↔ timeline segment |

---

## 14. Context, Guarantees and footer

| Element | Class | Details |
|---|---|---|
| **Context** | `section.context` | `h2` uppercase 11px muted, then a `.cards` grid of: the roadmap, open issues no roadmap item claims, docs, trail, notes, and on system boards `.amended` (the last amendment line). Ends with a boxed `.key`. |
| **Guarantees** (system boards) | `section.guarantees` | always visible under the stage, whatever tab is open. A `.guarantee-cards` grid and a boxed `.key`. |
| **Footer** | `footer` | 12px muted, top border. Lineage, generator and divergence list, with `code` in ink-2. |

---

## 15. Status vocabularies (glyph · word · colour)

Render these as `.vp` pills. Never show the colour without the glyph and the word.

**As-built state** (row / unit)

| Glyph | Word | Colour | Notes |
|---|---|---|---|
| `…` | pending | neutral | dashed |
| `▶` | in progress | accent | |
| `◑` | partially built | ink | |
| `✓` | as planned | good | |
| `+` | amended | warning | |
| `≠` | deviated | serious | |
| `✕` | dropped | critical | |
| `→` | deferred | neutral | dashed |
| `☑` | complete | good | hollow: not merged yet |
| `✓` | landed | good | |

**Option verdict**

| Glyph | Word | Colour | Notes |
|---|---|---|---|
| `✓` | accepted | good | |
| `◐` | partial | warning | |
| `◌` | deferred | warning | dashed |
| `✕` | declined | critical | |

**Run step specials**

| Glyph | Word | Colour |
|---|---|---|
| `⏸` | Waiting on the user | halted (warning) |
| `⇧` | Escalated | serious |
| `✕` | Cancelled | critical |
| `✓` | Landed | good |

**Run line:** `▶` run · `⏸` waiting on the user · `⇧` escalated · `✕` run cancelled. **Blocked:** `⊘` blocked, warning.

**QA pass verdict:** `✓` approved (good) · `✕` changes requested (critical) · `…` in progress (dashed).
**QA result:** `✓` passed · `~` minor findings only (warning) · `✕` other findings · `…` running (accent).
**QA mark:** `✎` fixed in place / fixing in place, neutral.

**Research question**

| Glyph | Word | Colour |
|---|---|---|
| `✎` | planning | critical |
| `↻` | researching | serious |
| `✓` | returned | serious |
| `◇` | verifying | warning |
| `✔` | complete | good |

**Interview:** `✓` answered (neutral) · `?` waiting on the user (warning).

**Dependency build:** `⊘` blocking (warning) · `✓` clear (good) · `◼` pre-built (neutral).
**Locality:** `⌂` repo (l1) · `◇` org (l2) · `△` parent org (l3) · `↗` external · name (l4).
**Holds up:** `⊘` blocks (warning) · `→` enables (good).

**Test result:** `✓` passed · `✕` failed · `↷` skipped (neutral) · `…` not run yet (dashed) · `⊘` not run: untouched.

**Impact**

| Group | Values |
|---|---|
| compatibility | `✕` breaking (critical) · `⚠` worth noting (warning) · compatible (system colour, no pill) |
| effect | `○` no change · `+` additive · `~` behaviour change (warning) · `!` breaking (critical) |
| need | `○` nothing to change · `◇` optional · `!` required |
| API change | `+` added · `~` changed (warning) · `⚠` deprecated (warning) · `✕` removed (critical) |

**Work-board rows with no board:** not started (dashed) · in progress (solid, never glowing) · merged · awaiting close · landed ·
closed · not planned (neutral) · closed · duplicate (neutral).

**Pipeline steps (in order):** sizing → interview → research → architecture → plan → branch → implementation → qa → remediation →
landing → state-boards → merge → landed.

**Timeline columns:**

| Column | Question |
|---|---|
| Gap | What was missing or wrong at the baseline |
| Considered | Every option weighed, each with its verdict |
| Verdict | The decision and its defence, or why the gap stays |
| As built | What exists now, and every change from the verdict |

---

## 16. Behaviours the library must provide

1. **Theme switch:** OS-following by default, remembered after a click (`localStorage["dashboards-theme"]`), applied before the first paint, and kept in sync with OS changes and `data-theme` mutations.
2. **Live updates:** when the board is opened from disk or localhost and the run is still live, it re-reads its data script every 5s, redraws in place only when the data changed, and keeps the **scroll position, the open tab, the inspector selection and inner scroll offsets**. The live chip counts seconds since the last change and goes still after 5 min of quiet. A published copy never polls.
3. **Tabs:** ARIA tabs with a roving tabindex and ←/→/Home/End. A tab's content renders lazily the first time it is shown. URL hash sync uses `replaceState`.
4. **Deep links:** `#<tab>`, `#tests.<sub>`, `#impact.<entry>`, `#<row>.<col>[.<opt>|.s<n>]`, plus aliases for renamed tabs (for example `#timeline` → Design, `#chain` → Landing Order).
5. **Selection → inspector:** clicking or pressing Enter/Space on a cell, option, card, table row, SVG node or thread fills the inspector and marks the item `.sel`. Esc clears it. Each tab has a default selection.
6. **Hover/focus highlighting** on the C4 and structure diagrams: neighbours `.hl`, everything else `.dim`.
7. **Sequence player:** prev / next / play (a toggle, `aria-pressed`) with a position readout.
8. **Times** are written in UTC and shown in the viewer's local time.
9. **Stale notes:** a progress note goes muted after 20 min without an update.
10. **Responsive reflow** at the breakpoints listed in §1.3. Nothing scrolls sideways except an explicit `.scroll` container or the C4 canvas.

## 17. Accessibility checklist

- `:focus-visible` gets a 2px `--focus` outline with a 2px offset.
- Clickable `div`s carry `role="button"` and `tabindex="0"` and respond to Enter and Space.
- Decorative glyphs are `aria-hidden="true"` and the word carries the meaning. SVGs use `role="img"` with an `aria-label`.
- The inspector is `aria-live="polite"`. Toggles use `aria-pressed`. The current step uses `aria-current`.
- Status never depends on colour alone (§0.1), and dashed is reserved for planned (§0.3).
- Every animation has a reduced-motion still state (§1.4).

## 18. Which board uses what

| Element group | Plan board | Tracking board | Work board | System boards |
|---|---|---|---|---|
| Header (top bar, live, theme, h1, chips) | ✓ | ✓ (progress chip) | ✓ | ✓ (no live chip; `.systools` theme) |
| WIP strip | split + step panel | WIP cards grid | — | — |
| KPI tiles + Timeline tile + tracers | Timeline tab | Timeline tab | KPI row | KPI (module boards) |
| Tabs | Design, Timeline, Decisions, As built, Tests (sub-tabs), Impact, Source | + Landing Order | Runs, Source | Systems, Structure, C4 model, Threads, decision flow, sequence, Lifecycles, + optional tabs |
| Timeline grid (`.board`) | ✓ | ✓ (unit rows) | — | — |
| Inspector | ✓ | ✓ (cards under Landing Order) | — | ✓ |
| Tables | decisions, tests (counts) | 〃 | runs table (collapses into cards) | matrix, guarantees, version lines, environment, trust |
| SVG kit | tracers | + chain graph | — | structure, C4, flow, sequence, states, deps |
| Cards | built, test, impact | 〃 | — | system, thread, guarantee, callout |
| Context + footer | ✓ | ✓ | ✓ | ✓ (+ Guarantees, amended line) |
