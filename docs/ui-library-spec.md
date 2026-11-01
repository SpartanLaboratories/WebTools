# UI library capability specification

**Status:** draft for implementation
**Audience:** the team or agent building the library, and its reviewers

---

## 1. Purpose and scope

This spec defines what a general-purpose UI library must offer to build **information-dense, read-mostly, data-driven
interfaces**: status dashboards, process and pipeline views, inventories, reports, architecture explorers and similar. Many kinds
of consumer should be able to use it. No domain is assumed: the library ships no business vocabulary, only the means for a consumer
to declare their own.

It covers:

- design tokens and theming
- a **visual encoding system**: semantic channels, vocabularies and generated legends
- layout primitives
- components
- a stage-matrix component
- data visualisation and diagrams
- behaviour (data binding, live refresh, URL state, selection)
- responsiveness, accessibility, internationalisation, extension points and quality bars

§21 lists what a full library would also need but this spec does not require.

**Requirement language.** **MUST**, **SHOULD** and **MAY** are used as in RFC 2119. A capability marked MUST is required for
conformance.

---

## 2. Design principles (normative)

The components MUST uphold these principles by default. Where a consumer can break one, the library SHOULD warn in development mode.

1. **Meaning is never carried by colour alone.** Any status colour appears with a glyph or icon *and* a text label, or with an accessible name and a visible label nearby.
2. **One job per visual channel.** Each channel encodes one kind of information per application: categorical hue, status tone, emphasis, ordinal ramp, line style, fill mode, stroke weight and shape. Channels are declared once, in an encoding registry (§6).
3. **Reserved line styles.** Each line style (solid, dashed, dotted) maps to exactly one meaning per application. The usual default is *dashed = pending, planned or not yet real*. Structural lines (frames, separators, lifelines, return arrows) stay solid.
4. **Legends are generated, not hand-written.** A legend lists exactly the encodings drawn in its scope, and updates when the content changes.
5. **Motion conveys state, and always has a still equivalent.** Every animation has a reduced-motion form that keeps its meaning, usually the effect's middle-strength frame held still.
6. **Theme through tokens only.** No component hard-codes a colour, font, radius or spacing value.
7. **Deterministic, data-driven rendering.** The same data and options give the same output.
8. **No horizontal page scroll.** Content reflows. Sideways scrolling happens only inside explicit scroll containers.
9. **Accessible by default.** Native semantics first, ARIA patterns where native elements fall short.
10. **Self-contained.** No required runtime dependencies or network access. Output works from static hosting and from a local file.

---

## 3. Platform and packaging

1. The core MUST be framework-agnostic: CSS custom properties plus plain JavaScript (custom elements or render functions). Adapters for popular frameworks MAY be offered on top of the core.
2. The library MUST ship as tree-shakable ES modules **and** as a single-file bundle (CSS and JS) that can be **inlined into one self-contained HTML document**, with no build step and no network access.
3. Pages built with it MUST work over `file://` and from any static host.
4. Rendering SHOULD support string output (static or server-side rendering) and DOM output, and the DOM output SHOULD be able to hydrate string output.
5. Styles MUST NOT leak. Class names carry a configurable prefix, or components use shadow DOM with exposed parts. Either way, consumers can restyle through tokens.
6. Every data value inserted into markup MUST be escaped by default. Raw HTML insertion MUST be an explicit opt-in.
7. External links (`http(s)` to another origin) MUST get `rel="noopener"`, and SHOULD open in a new tab only when configured to.
8. Supported browsers are evergreen ones. The library MAY rely on CSS grid, `color-mix()`, `:focus-visible`, `ResizeObserver`, `MutationObserver` and `matchMedia`, and MUST document the baseline.
9. Any browser storage access MUST be wrapped so that blocked or unavailable storage (private mode, sandboxing) never throws to the consumer.

---

## 4. Foundations: design tokens

### 4.1 Token architecture

1. Tokens MUST be layered: **primitive** (raw values) → **semantic** (roles) → **component** (per-component overrides that default to semantic tokens).
2. Tokens MUST be CSS custom properties, overridable at the document root **and on any subtree** (scoped theming).
3. Every semantic colour token MUST have a value per theme (§5).
4. A token reference (names, roles, defaults per theme) MUST be generated from the source.

### 4.2 Semantic colour roles

| Role | Purpose |
|---|---|
| `canvas` | page background |
| `surface` | cards, panels, controls |
| `surface-alt` | sunken wells, hover rows, tracks, code blocks |
| `text` | primary text, strong strokes |
| `text-secondary` | secondary text, connectors, neutral marks |
| `text-muted` | labels, kickers, pending strokes, placeholders |
| `border` | component borders |
| `hairline` | row separators (translucent) |
| `focus` | focus ring |
| `link` | reference and "more" links |
| `selected` | selection outline and the "lit" stroke in diagrams |
| `inverse` | text on `text`-filled surfaces (pressed buttons, numbered discs) |

### 4.3 Status tones

1. There MUST be at least five tones: **success**, **warning**, **serious**, **critical** and **neutral**. Consumers MAY register more.
2. Each tone exposes a base colour and a derived **tint** (see §4.8).
3. Every tone MUST meet non-text contrast of 3:1 or more against `surface` in both themes, for the places it is used as an edge, ring or bar.
4. Status tones are reserved for outcomes and states. The validator (§6.5) SHOULD warn when a categorical slot sits too close to a status hue.

### 4.4 Emphasis tone

1. One **emphasis** hue MUST be reserved for "active now" and "queued next" states (§9.11), and nothing else.
2. The library derives washes from it at set strengths (§4.8).

### 4.5 Categorical palette

1. The palette is an ordered list of N slots (default N ≥ 6), with values per theme.
2. **Assignment from a registry:** keys are assigned slots in registration order, so a key keeps the same colour on every view and every page that shares the registry.
3. **Overflow:** keys beyond N fall back to a neutral (`text-muted`). The fallback MUST be reported in the legend.
4. **Hierarchy inheritance:** a child key MAY inherit its top-level ancestor's slot.
5. Consumers MAY supply their own palettes. A validator (§6.5) SHOULD check that slots are distinguishable from each other and from status and emphasis hues, and meet contrast against `surface` in both themes.

### 4.6 Ordinal ramps

1. **Neutral ramp:** N steps generated by mixing `text` into `surface` at configured percentages, for example 20, 34, 48, 60, 70, 80 and 100 %. It encodes ordered levels without spending a hue, and MUST stay ordered in both themes.
2. **Hue ramp:** a short ordered ramp in one hue (default 3 steps), for phases (start, steady state, end).
3. Ramps MUST be declarable per application, with any number of steps.

### 4.7 Family palettes

1. A **family** is a named pair of endpoint colours per theme.
2. Item *i* of *k* in a family gets a shade interpolated between the endpoints. Items with the same key share a shade.
3. Families are for colouring kinds of segments in charts (for example, kinds of activity on a duration strip). Every family MUST keep clear of status and emphasis hues.

### 4.8 Derived colours

The library MUST provide a mixing utility, `mix(color, pct, base = surface)`, and named derived tokens for at least these recipes:

| Recipe | Default strength |
|---|---|
| status pill tint | 14 % tone into surface |
| warning or tone card wash | 9–12 % |
| emphasis wash, strong / soft / on a small pill | 7 % / 4 % / 16 % |
| info card fill | 3 % `text` into surface |
| background band | 4.5 % `text` into surface |
| diagram node fill, standard / container | 12 % / 5 % category into surface |

### 4.9 Typography

1. There are two families: **sans** (UI text) and **mono** (labels, identifiers, numbers, code). Defaults are system font stacks, and consumers can replace them.
2. Named text styles:

| Style | Default |
|---|---|
| display title | 28px |
| section title | 15px bold |
| body | 14px / 1.45 |
| dense body | 12.5–13px |
| caption | 12px |
| meta | mono 11px, muted |
| label | mono 10–11px, bold, uppercase, tracked .07–.1em, muted |
| value | 22px bold, tabular figures |
| micro | 9.5px, for tags |

3. Numeric content MUST support tabular figures.
4. Headings SHOULD use balanced wrapping. Long unbroken tokens (identifiers, paths, URLs) MUST wrap anywhere rather than overflow.
5. A **measure** utility caps line length (for example 78ch, 90ch or 110ch).

### 4.10 Shape and spacing

| Scale | Values |
|---|---|
| spacing | 2, 4, 6, 8, 10, 12, 14, 18, 24, 28px (configurable) |
| radius | xs 3 (pills), sm 4 (chips), md 6 (cards), lg 8 (bands), round 10–12 (pill buttons), full (dots) |
| border | hairline 1; ring 2; edge accent 3 / 4 / 5 |
| side gutter | ≥ 16px, plus safe-area insets |

The system is flat: no drop-shadow elevation. Glows are emphasis (§9.11), not elevation.

### 4.11 Breakpoints

1. Breakpoints are named tokens. Suggested defaults: `xs` 430, `sm` 560, `md` 760, `lg` 940, `xl` 1100, `wide` 1500.
2. Every responsive component MUST document which breakpoints it responds to, and consumers can change them.

### 4.12 Motion

1. Motion tokens: `pulse` 1.6s, `breathe` 2s, `fill` 1.8s (stepped, 8 steps), and the easing curves.
2. Under `prefers-reduced-motion: reduce`, every animation MUST stop on its documented still frame, and transitions MUST be turned off.

### 4.13 Glyphs and icons

1. Semantic glyph tokens with default Unicode values, replaceable by an SVG icon set:

| Token | Default | Token | Default |
|---|---|---|---|
| success | ✓ | failure / removed | ✕ |
| verified / complete | ✔ | complete, not finalised | ☑ |
| warning / deprecated | ⚠ | pending / running | … |
| active / playing | ▶ | paused / awaiting input | ⏸ |
| escalated | ⇧ | blocked | ⊘ |
| partial | ◐ | partly done | ◑ |
| deferred / moved / enables | → | skipped | ↷ |
| in progress (cyclic) | ↻ | under review | ◇ |
| drafting | ✎ | question | ? |
| added | + | changed / minor | ~ |
| differs | ≠ | critical change | ! |
| none | ○ | open slot | ◌ |
| pre-existing | ◼ | alert label | ▲ |
| local | ⌂ | nearby / sibling | ◇ |
| parent / upstream | △ | external | ↗ |

2. Glyphs are decorative (`aria-hidden`), and the label carries the meaning (§15).

---

## 5. Theming

1. **Light** and **dark** themes MUST be built in. Consumers MAY register more, and the library SHOULD respect forced-colours (high contrast) mode.
2. **Default:** follow the OS setting (`prefers-color-scheme`), and update live when it changes.
3. **Override:** an explicit choice is stored under a configurable storage key, shared by every page of the same origin that uses that key. An explicit attribute on the root (`data-theme`) wins over the OS.
4. **No flash:** the library MUST provide a tiny inline bootstrap snippet that applies the stored theme before the first paint.
5. **API:** `getTheme()`, `setTheme(name | "system")`, and a `themechange` event. Components react to root attribute changes.
6. Each theme also redefines the categorical, ramp and family palettes, not only the neutrals.

---

## 6. Visual encoding system

This is the library's core differentiator: consumers declare *what each visual channel means*, and components, legends and
accessible names all draw on that one declaration.

### 6.1 Encoding registry

A registry object, page-wide or scoped, declares:

| Channel | Declaration |
|---|---|
| categorical | ordered keys → palette slots, with optional parent links for inheritance |
| status vocabularies | named maps (§6.2) |
| emphasis states | which keys count as *active*, *held*, *queued* |
| ordinal scales | ordered keys → ramp steps |
| families | keys → family, with item order |
| line styles | `solid` / `dashed` / `dotted` → meaning label |
| fill modes | `filled` / `outlined` / `hollow` / `dotted-outline` → meaning label |
| stroke weights | named weights → meaning label |
| shapes | `box` / `hexagon` / `terminal` / `pill` / `circle` → meaning label |

### 6.2 State vocabularies

1. A vocabulary is a named map of states. Each state has: `tone` (status or emphasis), `glyph`, `label`, `description`, and optional flags `pending` (dashed), `hollow` (surface fill) and `activity` (`active` | `held` | `queued`).
2. Any component that shows a state takes a `(vocabulary, stateKey)` pair, or a resolved state object, and renders it consistently: pill, edge colour, legend entry and accessible name.
3. The library MUST ship **no domain vocabularies**, only examples in its documentation.

### 6.3 Usage tracking

1. Every component that draws an encoding MUST register the encoding with the nearest **legend scope** (§7).
2. Registration is idempotent and cleared when the scope re-renders.

### 6.4 Derived accessible text

The library builds accessible names and descriptions from vocabulary labels and descriptions, for example "Status: blocked —
waiting on a dependency".

### 6.5 Validation (development mode)

The library SHOULD warn when:

- a status tone is drawn without a glyph and label
- a line style, fill mode or shape is used for a meaning that is not registered
- two keys share a categorical slot without being related
- the emphasis hue is used outside an emphasis state
- palette contrast or distinguishability checks fail in either theme

---

## 7. Legend system

1. **Legend scope:** any container can be a scope. A scope collects the encodings drawn inside it.
2. **Legend component:** renders the scope's entries, deduplicated, in a canonical order: categorical, then status, then ordinal, then line, fill and shape, then emphasis. It only lists what was drawn. Manual entries MAY be added, and entries MAY carry a description.
3. **Swatch primitives** (each themed, each following its encoding):

| Group | Swatches |
|---|---|
| boxes | outline box; filled box; edge-bar box (left accent); top-bar box; dashed box; dotted box; ring box |
| emphasis | inner-glow box; outer-glow box; held-ring box; wash box |
| lines | solid; dashed; weighted (per stroke weight); thin return line with arrowhead |
| dots | filled; hollow dashed; emphasis; selected-ring |
| bar segments | filled; dashed outline; empty slot; hollow outline; timed segment; outlined timed segment |
| other | mini progress bar; animated-fill arrow (still in legends); shapes (hexagon, terminal, pill); edge-weight lines |

4. **Component-as-swatch:** a legend entry MAY render a real pill, badge, chip or tag as its swatch.
5. **Layout variants:**
   - inline: wrapping row
   - boxed: on a surface card
   - compact: inside a stat tile
   - multi-column: CSS columns, entries never split across columns
   - aligned grid: swatch | name | detail | right-aligned value, for timed lists
6. Legends MUST update when their scope re-renders.

---

## 8. Layout primitives

1. **Page container:** centred, with a max width (default 1280px) that grows at the `wide` breakpoint (default to `min(2400px, 100%)`), side gutters of at least 16px plus safe-area insets, and bottom padding.
2. **Stack and cluster:** flex column or row with gap tokens, wrapping.
3. **Auto-fit grid:** columns of a configurable minimum width that never cause overflow, using `minmax(min(100%, X), 1fr)`.
4. **Share grid:** at or above a breakpoint, fixed fractional columns (for example 35 / 25 / 40) with explicit row placement and row spanning (one item spans every row beside a list of rows). Below the breakpoint it stacks in a documented order.
5. **Split row:** equal columns that stack below a breakpoint.
6. **Master–detail stage:** a main area plus a detail panel (§9.20) of configurable width (default 320px, wider at `wide`). Modes:
   - `side`: the panel is sticky
   - `below`: the panel goes under the main area and its sections reflow into a row of cards
   - `hidden`
   It collapses to one column below `lg`.
7. **Scroll container:** an explicit horizontal-scroll wrapper. It is the only place sideways scrolling is allowed, for example for a table with a minimum width or a wide diagram.
8. **Sticky helper:** sticky offsets that take safe-area insets into account. Sticky elements MAY keep their own scroll (`max-height: 100vh − offset; overflow: auto`).
9. **Overflow discipline:** every grid and flex child MUST default to `min-width: 0`. Text wraps rather than overflowing.
10. **Section:** a titled block with a small uppercase heading, its content, and an optional boxed legend.

---

## 9. Components

Each component's documentation MUST cover its anatomy, props/attributes, variants, states, events, keyboard behaviour, ARIA,
tokens and examples in both themes.

### 9.1 Text

| Component | Notes |
|---|---|
| heading | levels; optional inline `small` muted mono suffix |
| lede | secondary-colour intro paragraph with a measure cap |
| kicker | mono, muted, small, above a title |
| meta | mono, small, muted; inline groups wrap with gaps |
| label | the uppercase tracked label style |
| inline code | mono at .92em, wraps anywhere |
| formatters | ordinal (1st, 2nd, 3rd, 11th…); relative time ("12 s", "6 min", "2 h"); UTC ISO → viewer local time; compact counts "x of y" |

All formatters MUST be locale-aware (§16).

### 9.2 Links

1. **Variants:**

| Variant | Look |
|---|---|
| inline | hairline underline, strong underline on hover |
| quiet | no underline until hover |
| more | label followed by a chevron (›), link colour |
| subtle secondary | small, muted, shown beside a primary link (for example "source") |
| citation | compact reference marker, link colour, wraps |
| title-number pair | an identifier and a title as two separate links, or plain text where a target is missing |

2. **Link resolver hook:** a consumer function can rewrite hrefs when rendering and on dynamically inserted content (via `MutationObserver`), for example to point at a local copy when one exists, keeping the original in a data attribute and noting it in the tooltip.

### 9.3 Status pill

1. **Anatomy:** an inline-flex label made of a glyph `<i aria-hidden>` and a word. It has a 1px border, a 3px left edge in the tone, and a tint fill.
2. **Inputs:** a vocabulary state or a `(tone, glyph, label)` triple.
3. **Tones:** success, warning, serious, critical, neutral (no tint), emphasis, ink (strong neutral).
4. **Fill modes:** tinted (default), plain, hollow (surface fill), dashed (pending).
5. **Activity:** `active` (breathing glow, §9.11) and `held` (still ring).
6. **Sizes:** sm (9.5px), md (10.5px), lg (12px).
7. **Wrapping:** no-wrap by default. It MAY wrap in narrow containers, with `max-width: 100%`.

### 9.4 Composite pill

1. Two or more segments joined into one pill, for example a state segment followed by a scope segment.
2. Inner corners are squared and borders collapsed. Each segment keeps its own edge colour, from a tone or an ordinal ramp.
3. The pill has a tooltip and an accessible description.

### 9.5 Badge and tag

1. **Badge:** a small mono label (10px), with variants:
   - neutral
   - category edge (3px)
   - tone (edge, tint and weight)
   - dashed (pending)
   - muted (closed or inactive)
   - prefixed (a glyph before the text)
2. **Flag tag:** a small uppercase tag in the emphasis colour, for example "NEXT", marking a queued item.
3. **Kind chip:** a neutral solid mini-chip that names a type. It is a fact, not a status.
4. **Mark:** a neutral pill with a glyph, used to annotate another pill (for example "✎ edited").
5. **Thread / start-mode pill:** encodes how something starts through the fill mode (filled, outlined, dotted, dashed).
6. **Check chip:** a neutral chip with a tone edge.
7. **Role tag:** a bold neutral tag with an ink border.

### 9.6 Key–value chip

1. **Anatomy:** a label (uppercase micro) and a bold value. It wraps anywhere.
2. **Variants:**
   - status edge
   - category edge (4px)
   - dashed (pending)
   - **progress chip**, with an embedded progress bar (§9.8)
   - **multi-value chip**, an inline list of swatch-and-name items
3. **Hidden until populated:** the chip stays hidden on every layout until data fills it, and is removed if it has nothing to show.

### 9.7 Swatch and edge accent

1. **Swatch:** a 10×10 square (radius 2) in a categorical, ramp or family colour, placed before a name.
2. **Edge-accent utility:** any container (card, row, chip, cell, table row, list item) MAY take a coloured edge.
   - Side: left (default) or top.
   - Width: 3, 4, 5 or 6px.
   - Colour source: category, tone, ramp step or family.
   - The dashed variant dashes the body border and keeps the edge solid.

### 9.8 Progress and quantity

1. **Segmented bar:** a 6px track with gaps between segments, each segment sized by its share.
   - Segment kinds: filled (any tone, category, family or ink), dashed outline (pending), empty slot (unused capacity, solid outline), hollow outline (complete but not finalised), faded remainder (50 %), and timed (minimum width, so zero-length items stay visible as a sliver).
   - Every segment kind registers with the legend.
2. **Progress bar:** a 4–6px track with a 1px border and a success fill, with configurable max width.
3. **Fraction meter:** "**3** of 11 done" text plus a progress bar.
4. **Single-series bar chart:** rows of label | track | value. One neutral fill, because one series must not spend a hue. Values in tabular figures.
5. **Duration strip:** a full-width segmented bar of timed spans coloured by family and shade, with two legends: families (with their shades) and a multi-column aligned list (mark | name | when | duration). Estimated values carry a marker. Spans the bar does not draw get an outlined mark.

### 9.9 Stat tile

1. **Anatomy:** a label (uppercase), a value (large, tabular, optional small unit or suffix), an optional sub-line (it may contain links), an optional segmented bar, and **its own compact legend** inside the tile.
2. **Tile row:** an auto-fit grid of tiles. It MAY switch to fixed column counts by breakpoint (5 → 3 → 2 → 1).
3. **Wide tile:** spans the full row (for example a duration strip).

### 9.10 Steppers

1. **Inline stepper:** a horizontal list of steps joined by short rules.
   - Done: solid neutral dot and solid rule.
   - Upcoming: hollow dashed dot, dashed rule and muted text.
   - Current: emphasis dot with an animated halo, and bold text.
   - **Interactive done steps:** a done step's label MAY be a button that emits `stepselect`, and the selected step gets an ink ring (not the emphasis colour).
   - Steps wrap onto more lines.
2. **Vertical step list:** each row has a dot, the text and a small meta line, with the same states. A row MAY nest a sub-list (for example the options under a question) and MAY carry a result box (a tone-edged box with a heading row and a meta line; dashed while running).
3. **Numbered phase steps:** bordered mono chips with a counter prefix and a ramp-coloured left edge. The pending one is dashed.
4. **Linked item chain:** a wrapping row of mono chips, each with a category edge, linking to its item. Done items are strong, pending ones dashed, and items can take emphasis states.

### 9.11 Emphasis effects

These are generic states that apply to any surface: card, cell, pill, row, chip, column, dot or SVG shape.

| State | Look | Reduced motion |
|---|---|---|
| **active** | inset 2px emphasis ring, an inner glow that breathes (~2s), emphasis wash | glow held at middle strength |
| **active-soft** | the same, lighter (1.5px ring, weaker glow), for a whole group or region | held still |
| **held** | the inset ring alone, still: active but paused, for example waiting for input | (already still) |
| **queued** | a mild **outer** glow, never animated, usually with a flag tag | (already still) |
| **selected** | 2px outline in `selected`, offset 1px | — |
| **highlighted / dimmed** | stronger stroke on neighbours; reduced opacity on everything else (configurable, default .28 for flows and .12–.3 for diagrams) | — |
| **halo dot** | a surface gap ring, an emphasis ring and a breathing outer halo | still halo |
| **filling connector** | an arrow-shaped connector whose segmented fill advances in steps, for "being produced". It has a still mode (for example while held). | filled and still |

Rules:

1. Emphasis backgrounds MUST win over the surface's own background.
2. **Group-level emphasis:** emphasis set on a region (for example a column) is drawn once, on the region. If the region isn't rendered (for example in a narrow layout), it falls back to each member item.
3. SVG equivalents: a stroke plus a breathing fill for active, a drop-shadow filter for queued.
4. The emphasis hue MUST NOT be used for any other purpose (§4.4).

### 9.12 Cards

1. **Base card:** surface, 1px border, radius 6 and padding, with optional header row, body and mono meta footer.
2. **Modifiers:**
   - edge accent (§9.7)
   - pending (dashed)
   - clickable (pointer, hover border, keyboard operable)
   - selected
   - not interactive
   - stale (faded to ~55 %)
   - past (ink edge)
3. **Tone card:** a tone edge and a wash; the border is mixed toward the tone. It keeps any category as a badge.
4. **Info card:** a compact label-before-value fact with a 3 % ink fill. A row of info cards wraps.
5. **Notice:** an empty-state or explanatory box with a neutral edge and an optional bold lead line. The pending variant is dashed.
6. **Callout:** a sunken box with an edge in a ramp or ink colour, with bold emphasis allowed inside.
7. **Outcome line:** a sunken box with an ink edge that states the result of a selection.
8. **Quote / excerpt:** a blockquote with an edge on a sunken background, for quoting a source passage.
9. **Link card:** the whole card is a link, with a top category edge, a mono name, a description, a row of mini-chips, an uppercase state label and an optional role tag. The pending variant is dashed and faded.
10. **Change list:** inside a card, a list of items, each a bold title with a small mono meta line.
11. **Card grid:** auto-fit, with optional **group headings** (uppercase, small note) between groups.

### 9.13 Status banner

1. A prominent container for the current state of a process or entity.
2. **Anatomy:**
   - headline: glyph and state word, with a timestamp aligned right
   - detail text (capped measure)
   - an emphasised "awaiting input" message
   - labelled sub-sections
   - an inline stepper (§9.10)
   - a row of info cards
   - an embedded legend, separated by a hairline
3. **Tone variants:** active (emphasis edge, the default), success, warning (edge and wash), serious, critical.
4. **Composite layouts:**
   - **banner + detail panel:** a split row (§8.5), with the legend in a boxed card underneath
   - **banner card grid:** a share grid (§8.4) with a summary card spanning every row, one card per active item, each paired with its detail panel, plus a queued card (dashed) and an awaiting-input card (warning wash, list with sub-lines), stacking in a documented order below the breakpoint

### 9.14 Step detail panel

A card that breaks the current or a selected step down. It holds:

- a title and a mono timestamp
- a summary or wait line (bold)
- a vertical step list (§9.10.2)
- result boxes
- info cards, including **progress notes** that fade when stale (§13.8)

When it shows a past state, it opens with a **context bar**: "Showing: <step> as it ended" and a **back** pill button. Repeated
visits of a step are sections with uppercase labels. The panel has a held variant (warning edge) and a past variant (ink edge).

### 9.15 Page header

1. **Anatomy:**
   - a top bar with a breadcrumb on the left and a toolbar slot (live indicator, theme toggle, consumer actions) on the right; it wraps
   - a title in display style, optionally with a linked identifier that has a 2px underline
   - an optional subtitle line (mono, secondary)
   - a description (15px, measure cap)
   - a set of key–value chips
2. **Responsive:** at or above `lg`, a two-column grid: the intro (title, subtitle, description) on the left, and the chips on the right spanning every intro row, as small **cards** with the label over the value that grow to fill each line (`flex: 1 1 190px`; a `wide` chip spans the half). Below `lg` it stacks, and the chips go back to inline (label before value).

### 9.16 Live status indicator

1. A round chip with a dot and text.

| State | Dot | Example text |
|---|---|---|
| live, changing | pulsing | "LIVE · updated 12 s ago" |
| live, quiet | still | "LIVE · no change for 6 min" (after a configurable quiet period, default 5 min) |
| live, unavailable | grey | "LIVE · data unavailable" |
| not live | hollow | "not live · as of <local time>" |

2. The dot is ink-coloured, never a tone or the emphasis colour.
3. It ticks every second, carries a tooltip that explains the state, and is hidden when empty.
4. The pulse MUST stop while the consumer marks the subject as paused, and under reduced motion.
5. Its state comes from the live-data controller (§13.2). The text strings are localisable.

### 9.17 Theme toggle

1. A round chip button showing the current theme ("☀ light" / "☾ night"), with `aria-pressed` set to dark.
2. Its tooltip names the theme and whether it follows the system. Its accessible label says it follows the system setting until clicked.
3. It is bound to §5.

### 9.18 Tabs

1. Follows the WAI-ARIA tabs pattern:
   - `tablist`, `tab` and `tabpanel`, with `aria-selected` and `aria-controls`
   - **roving tabindex** (only the selected tab is in the tab order)
   - ←/→ wrap, Home and End jump, and selection follows focus
2. Selected tab: ink text and a 2px underline. Other tabs: secondary text, ink on hover.
3. Tab labels don't wrap, and the bar scrolls sideways when it overflows.
4. **Suffix marker:** a tab MAY carry a superscript mono marker (for example "JSON" on a raw-data tab).
5. **Lazy panels:** a panel renders the first time it is shown, and re-measures on later shows (for layouts that depend on measurement).
6. **Sub-tabs:** a nested tablist styled as a segmented control, with optional counts. It has the same keyboard behaviour and its own URL state.
7. Selecting a tab updates the URL (§13.4) without adding a history entry.

### 9.19 Controls

1. **Segmented control:** a joined group of mono buttons with `aria-pressed`. The pressed button is ink-filled with inverse text. It supports `aria-disabled` (faded, not interactive) and wraps onto more rows at narrow widths. It works as a single-choice filter or picker.
2. **Button:** a mono button with a 1px border and radius 5, with hover and pressed states. A toggle button uses `aria-pressed`.
3. **Pill button:** a round, compact, inked-border button (for example "back").
4. **Text button:** an inline, underlined button that looks like text, used inside lists and steppers.
5. **Link button:** a button on a sunken background, used in panels.
6. **Playback controls:** prev, next and play/pause (a toggle) with a position readout. Play steps at a configurable interval and stops at the end. Each control is keyboard operable.
7. **Level navigator:** a row of large selectable rungs, each with a level number, a name and a scope line (ellipsis). The current rung has `aria-pressed`. Short connectors join consecutive rungs, and separators MAY split groups. It is used for drill-down levels.
8. **Zoom toggle:** a small segmented control in a view header.

### 9.20 Detail panel (inspector)

1. A sticky panel beside the main view (§8.6) with its own scroll and `aria-live="polite"`.
2. **Anatomy:**
   - an uppercase panel label
   - an empty-state message
   - for the selected item: kicker, name, a meta row of pills, a lead paragraph, paragraphs, and a definition list (uppercase `dt`, `dd` holding text, lists or links)
   - optional embedded components (stepper, step list, excerpt)
3. **Behaviour:**
   - shows whatever the selection model holds (§13.5)
   - clears on **Esc**
   - each view MAY declare a default item to show when nothing is selected
   - in `below` mode its sections reflow into cards (with a lead card 1.6× wider)
4. Content is supplied by consumer templates for each item type.

### 9.21 Definition list

A label–value list with an uppercase muted label and a secondary-colour value, which can hold lists and links. It is used in
panels and cards.

### 9.22 Tables

1. **Data table:**
   - uppercase micro headers; hairline row borders; top-aligned cells; values wrap anywhere
   - optional row edge accent (category, ramp or tone; dashed when pending)
   - clickable rows (hover background, keyboard selectable) that emit `rowselect`
   - sub-lines in cells; right-aligned tabular numeric columns
   - pending rows (faded, dashed borders)
   - colspan support
2. **Group rows:** full-width header rows (uppercase label, with an optional mono link) splitting the table into groups.
3. **Column shares:** declarative width percentages.
4. **Minimum width:** a table MAY declare a minimum width, and it then sits inside a scroll container (§8.7).
5. **Responsive collapse:** below a breakpoint (default 880px), each row becomes a **labelled card**:
   - labels come from column definitions
   - consumers can reorder the fields
   - empty cells are hidden
   - the edge accent moves to the card
   - between two breakpoints (default 600–880px) the card MAY lay its fields out in two columns, with chosen fields spanning both
6. **Size scale:** a large variant, for tables that are the main content.
7. **Matrix:** a cross-reference table with centred cells, where a dot marks a hit. Hovering highlights the row. The first column is left-aligned.
8. **Row-header table:** `tbody th` cells as mono row headers, with a compact density.

### 9.23 Lists

| List | Shape |
|---|---|
| log / trail | time (mono muted) · optional micro tag · text (bold and links allowed) |
| item list | identifier (mono) + optional secondary link + title + right-aligned tags; nested children indented; tags drop under the title on narrow screens |
| grouped item list | group row (identifier · bold title · inline progress) with nested items and a "more" line |
| document list | hairline-separated links |
| step list | mono id · text · file/meta line (mono) · prefixed check line; optionally clickable with a current-row state |
| option list | full-width buttons, each with a tone edge and a 10 % tint: glyph · text, an optional uppercase decision word, the pending variant dashed, selectable (outline) |
| annotated list | bulleted or numbered statements, each ending in a citation link (§9.2); a "marked" variant puts a small pill before each statement; a click on the citation shows the cited passage in the detail panel |
| plain separated list | hairline-separated rows with pills |

### 9.24 Code and source viewer

1. A `pre` block in a sunken well with mono 12px / 1.5 text, a maximum height (default 70vh), scrolling, and `tabindex="0"` so it can be scrolled by keyboard.
2. It has minimal built-in **JSON highlighting** (keys and strings) and a plug-in highlighter interface.
3. It MAY leave out configured fields from the shown copy.

### 9.25 Tooltip

1. A tooltip MUST NOT be the only place essential information appears.
2. The library MUST support at least native `title` tooltips, and SHOULD provide an accessible tooltip that appears on hover and focus.

### 9.26 Footer and section headings

1. **Footer:** small muted text with a top border, holding code and link styles.
2. **Section heading:** uppercase, small, muted.

---

## 10. Stage matrix

A grid with a **label column** plus N **stage columns**. Each row is an entity and each cell holds that entity's state at one
stage. This is the main complex layout component.

1. **Grid:** the label column is `minmax(150px, 190px)` and the stages are `repeat(N, minmax(0, 1fr))`, with a configurable column gap (larger at `wide`).
2. **Column heads:** sticky. Each has a numbered disc (ink fill, inverse number), an uppercase stage name, a one-line question or description, and an optional mono date or link line, with a strong underline.
3. **Column bands:** rounded neutral bands drawn behind each stage column. Their geometry is **measured from the head positions** and recomputed on resize, re-render and tab show. Bands are layout, not an encoding, so they never appear in the legend.
4. **Group rows:** full-width uppercase headings between groups of rows.
5. **Row label:** a category edge (4px), a bold name (it may be a link), a mono location line and a category line. It can take emphasis.
6. **Cells:**
   - a card with a header row (uppercase label, kind chip, pill), summary text and a meta row
   - `role="button"` and keyboard operable, emitting `cellselect`
   - the selected cell gets an outline
7. **Connectors:** each cell after the first is joined to the previous column by a horizontal line and a node dot on its edge. The first stage has no incoming line.
8. **Cell states:**
   - **solid**
   - **pending:** dashed border, dashed connector, hollow dashed node
   - **toned:** a left edge in a tone, emphasis or ink
   - **ringed:** a 2px border in a consumer scale. It is never a fill, so an emphasis glow can sit inside it.
9. **Special connector:** a connector MAY be swapped for the *filling connector* (§9.11).
10. **Column emphasis:** set on a column, it lights the band and the head (wash and an emphasis underline), not each cell. In the linear layout it falls back to the cells.
11. **Cell content slots:** option lists, annotated lists, pills or free content.
12. **Narrow layout** (below a breakpoint, default 900px): the grid linearises into stacked row label → cells. Heads, bands and connectors are hidden.
13. **Deep links:** `#<row>.<column>[.<sub>]` selects and scrolls to the cell (§13.4).

---

## 11. Data visualisation and diagrams

### 11.1 Diagram engine

1. **Output:** SVG with a `viewBox`, fluid width, and `role="img"` plus an `aria-label`. Interactive nodes MUST be focusable and have accessible names.
2. **Model:** nodes (id, label, sublabel, category, state, shape, parent), edges (from, to, label, style, weight, number), groups and boundaries.
3. **Rendering:**
   - text measurement and wrapping in nodes
   - edge labels on a surface-coloured background
   - arrowhead markers
   - numbered edge markers (circle + number)
   - rounded orthogonal or curved routing
4. **Encodings:** node fill and stroke from category or tone; pending is dashed and faded (~60 %); edge weight and style come from the registry. Everything is themed by tokens.
5. **Interaction:**
   - click or Enter selects (`nodeselect`, `edgeselect`) and feeds the detail panel
   - hover or focus **highlights neighbours** and dims the rest
   - the selected node gets the `selected` stroke
6. **Canvas:** an optional dotted-grid background. A wide diagram scrolls inside a scroll container, with a minimum width.
7. **Emphasis:** active (stroke plus breathing fill), held, queued (glow filter) and a tag box above a node.

### 11.2 Layouts

| Layout | Requirements |
|---|---|
| **layered graph (DAG)** | columns by longest path or earliest start; nodes with a category bar and a state strip; dashed edges for pending dependencies |
| **containment map** | nested boxes for composition (containers lighter), plus dependency arrows between boxes; lanes or bands with labels and free notes |
| **dependency graph** | nodes for internal items (category colour) and external items (neutral); edge weight encodes the dependency kind |
| **decision flow** | choice, process and terminal nodes (box, hexagon, terminal shape); a segmented control picks the answers, the chosen path **lights up** in the `selected` stroke and the rest fades; an outcome line states the result |
| **sequence diagram** | lifelines with category-coloured heads; phase bands; numbered messages; thinner return messages; the current message highlighted and future ones faded (~30 %); **step-through playback** (§9.19.6) kept in sync with a step list |
| **state machine** | states (ramp-coloured by phase), transitions with labels |
| **multi-level drill-down** | a level navigator (§9.19.7) switches views of one model at different zoom levels; elements as cards with a header band, name, type, description and tags; labelled boundaries; labelled relationships; neighbour highlighting |

### 11.3 Model parsers

The engine MUST accept a plain-object model, and SHOULD provide a **parser plug-in interface** so diagrams can render from
textual modelling languages when the page loads.

### 11.4 Connector overlay

1. An absolutely positioned SVG overlay that draws paths **between arbitrary DOM elements** (for example from a stat tile to its segment in a duration strip), with per-connector colour and endpoint dots.
2. It is recomputed on resize, re-render and show, and hidden below a breakpoint (default 760px), where each element shows a text label instead.
3. It ignores pointer events.

---

## 12. Data binding and rendering

1. **Entry point:** `render(container, data, options)` for each component and for whole compositions. It is pure with respect to the data.
2. **Embedded data:** a helper reads JSON from `<script type="application/json" id="…">` blocks, so a page can carry its own data.
3. **Validation hook:** consumers MAY supply a schema validator. Failures render a notice (§9.12.5), never a broken page.
4. **Incremental redraw:** `redraw(data)` re-renders in place (§13.2).
5. **Templates and slots:** consumers supply render functions for cell, card, panel and list item content, returning escaped strings or nodes.

---

## 13. Behaviour and state management

### 13.1 Lazy rendering

Hidden views render on first show. Components that depend on measurement (bands, connector overlays) re-measure when shown.

### 13.2 Live data controller

1. **Sources:**
   - **polling:** a configurable interval (default 5s), cache-busted loading of JSON, or of a script that calls a global callback. The script variant MUST work over `file://`.
   - **push:** the consumer calls `redraw(data)`.
2. **Activation predicate:** a consumer function decides whether live mode runs (for example only on a local origin, or only while the data says the subject is still active). The controller stops when the predicate turns false.
3. **Change detection:** compare serialized data, and redraw only when it changed.
4. **State preservation across a redraw:**
   - window scroll position
   - active tab and sub-tab
   - the selection, **resolved again by stable id**, falling back to the view's default when the id is gone
   - the scroll positions of registered inner scrollers (the detail panel, wide diagrams)
   - the URL hash
5. **Status output:** it feeds the live indicator (§9.16) with last change, quiet, unavailable, not live, and paused.
6. **Secondary probes:** optional extra data sources (for example "is a local copy of this link available?") feed hooks such as the link resolver (§9.2.2).

### 13.3 Events

The library MUST emit, at minimum:

| Event | When |
|---|---|
| `tabchange` | a tab or sub-tab is selected |
| `select` / `selectionclear` | the selection changes |
| `cellselect`, `rowselect`, `nodeselect`, `edgeselect`, `stepselect` | a cell, row, node, edge or step is chosen |
| `themechange` | the theme changes |
| `livestatus` | the live controller's status changes |
| `redraw` | a redraw finishes |

Events bubble and carry stable ids.

### 13.4 URL state and deep links

1. A hash router with declarative patterns, for example `#<view>`, `#<view>.<subview>`, `#<entity>.<part>[.<subpart>]` and `#<view>.<entity>`.
2. **Aliases:** old names redirect to current ones.
3. **Updates** use `history.replaceState`, so no history entries pile up.
4. When the page loads, the router opens the view, selects the target and scrolls it to the centre of the viewport.

### 13.5 Selection model

1. Single selection per page or per scope, held as `{type, id, …}`, with `select(ref)`, `clear()` and `current()`.
2. Selecting applies the selected style and feeds the detail panel. **Esc** clears it.
3. Each view MAY declare a default selection.
4. Clicking a citation inside a selection MAY swap the detail panel content to the cited passage.

### 13.6 Measurement-driven layout

A utility based on `ResizeObserver` that recomputes geometry (bands, overlays, sticky offsets) on resize, font load, re-render and
view show, debounced to animation frames.

### 13.7 Time

1. Data carries UTC ISO timestamps, and the display uses the viewer's locale and time zone.
2. Relative times update on a shared one-second ticker.

### 13.8 Staleness

1. Any item with an "updated" timestamp MAY declare a staleness threshold (default 20 min).
2. Past the threshold it gets the stale state (faded), and the legend explains it.

### 13.9 Preferences

1. Persisted per-viewer settings (theme, last tab and similar) go through guarded storage (§3.9).
2. They MUST NOT be used for shared or authoritative state.

### 13.10 Dynamic content hooks

1. Consumers MAY register transforms (for example link resolution) that run on initial render and on any content inserted later, via `MutationObserver`.

---

## 14. Responsiveness

The library MUST implement and document these reflows:

| Component | Reflow |
|---|---|
| page | widens at `wide`; never scrolls sideways |
| header | two halves at or above `lg`, stacked below; chips as cards ↔ inline |
| master–detail stage | side ↔ below ↔ stacked |
| share grid and split row | fixed shares ↔ stacked, in a documented order |
| stage matrix | grid ↔ linear stack; group emphasis falls back to items |
| table | table ↔ labelled cards (1 column, 2 columns at mid widths) |
| stat tile row | 5 → 3 → 2 → 1 columns (configurable) |
| connector overlay | shown ↔ hidden, with text labels instead |
| tabs | scroll sideways inside the tab bar |
| segmented controls, chips, legends, pill rows | wrap |
| card grids | auto-fit, never overflowing at 320px |

Every layout MUST be verified at 320, 375, 768, 1024, 1440 and 1920px widths.

---

## 15. Accessibility

1. Conformance target: **WCAG 2.2 AA** in both themes.
2. Contrast: text at 4.5:1 or more (3:1 for large text). Non-text marks that carry meaning (edges, rings, bar segments, dots) at 3:1 or more against their background.
3. **Focus:** `:focus-visible` gets a 2px focus ring with a 2px offset on every interactive element.
4. **Keyboard:** everything is operable by keyboard. Native buttons are preferred. Where a non-button element is interactive, it MUST have `role="button"` and `tabindex="0"`, and handle Enter and Space.
5. **ARIA patterns:** tabs (§9.18), toggle buttons (`aria-pressed`), current step (`aria-current="step"`), live regions (detail panel and live indicator), and `role="img"` with labels on diagrams.
6. **Non-visual equivalents:** every visual-only encoding (dashed, hollow, ring, glow, faded) MUST come with text: the legend, the label, or visually hidden text inside the item.
7. Glyphs are `aria-hidden`, and labels carry the meaning.
8. **Motion:** honour reduced motion (§4.12). No flashing more than three times a second.
9. **Forced colours:** borders, edges and states stay visible in high-contrast mode.

---

## 16. Internationalisation

1. Every built-in string is overridable through a message catalogue: live states, empty states, back buttons, table field labels in card mode, tooltips and ordinal suffixes.
2. Numbers, dates, relative times, ordinals and lists use `Intl` APIs and the active locale.
3. Layout SHOULD use logical CSS properties so right-to-left works. Edge accents follow inline-start.
4. Glyph tokens MUST NOT depend on the language. Labels carry the meaning.

---

## 17. Customisation and extension

Consumers MUST be able to:

1. override any token, globally or per subtree, and register themes
2. register palettes, ramps, families, vocabularies and the encoding registry
3. replace the glyph set with an icon set
4. add swatch types and legend entry renderers
5. supply content templates for cells, cards, panel sections and list items
6. add diagram node and edge renderers, layout algorithms and model parsers
7. add highlighters to the source viewer
8. set the class prefix, storage keys, breakpoints, motion durations and polling intervals
9. hook link resolution and dynamic content transforms

---

## 18. Quality requirements

1. **Documentation:** each component's page shows its anatomy, every variant and state in both themes, its keyboard and ARIA behaviour, its tokens and runnable examples.
2. **Visual regression:** every component state × {light, dark} × {motion, reduced motion} × the six reference widths.
3. **Automated accessibility tests** on every documentation example, with zero violations at AA.
4. **Performance:**
   - render a 500-row table or a 50 × 4 stage matrix in under 100 ms
   - lay out a 200-node diagram in under 300 ms on a mid-range laptop
   - a live redraw causes no visible flicker or scroll jump
   - the theme bootstrap causes no layout shift or flash
5. **Bundle budget:** the core (tokens, primitives, components) has a documented size budget, and diagrams load as a separate module.
6. **Determinism:** snapshot tests prove that the same input gives the same output.

---

## 19. Reference compositions

These recipes show that the capabilities combine into complete interfaces. Each MUST be built as an example in the documentation.

| Recipe | Composition |
|---|---|
| **Process status page** | header (live indicator, theme toggle, chips) → status banner + step detail panel → tabs → stage matrix with legend + detail panel → context card grid → footer |
| **Multi-item progress page** | header with a progress chip → banner card grid (summary, active items, queued, awaiting input) → tabs with a layered graph of items and their dependencies, the detail panel set to `below` |
| **Portfolio table** | header → stat tile row (segmented bars, in-tile legends) → tabs (table, raw source) → grouped data table with composite pills, ordinals and emphasis; collapses to cards |
| **Summary timeline** | a wide duration-strip tile above a stat tile row, joined by a connector overlay; aligned multi-column legend |
| **Architecture explorer** | header → tabs: link-card grid, containment map, multi-level drill-down, thread cards, decision flow, sequence playback, phase cards with callouts, dependency graph, matrix tables → an always-visible summary card grid → context → footer |
| **Review log** | tabs: decisions table with filters (segmented control); change cards with change lists; test cards grouped and edged by an ordinal ramp, with sub-tabs; impact cards toned by severity |

---

## 20. Acceptance checklist

The library conforms when every item below is implemented, documented and tested:

- [ ] Packaging: ES modules and an inlinable single-file bundle; works over `file://`; escaped by default (§3)
- [ ] Layered tokens, scoped overrides, generated token reference (§4.1)
- [ ] Semantic roles, status tones, emphasis tone, categorical, ramp and family palettes, derived tint recipes (§4.2–4.8)
- [ ] Typography styles, shape and spacing scales, breakpoints, motion tokens, glyph tokens (§4.9–4.13)
- [ ] Light and dark themes, follows the system, persisted override, no-flash bootstrap, API and event (§5)
- [ ] Encoding registry, vocabularies, usage tracking, derived accessible text, development-mode validation (§6)
- [ ] Legend scopes, every swatch primitive, component swatches, five layout variants (§7)
- [ ] Layout primitives, including share grid, master–detail stage modes and overflow discipline (§8)
- [ ] Every component in §9 (9.1–9.26) with all variants and states
- [ ] Stage matrix with measured bands, connectors, cell states, column emphasis, linear layout and deep links (§10)
- [ ] Diagram engine, the seven layouts, parser interface and connector overlay (§11)
- [ ] Data binding, embedded data, validation hook, templates (§12)
- [ ] Lazy views, live controller with state preservation, events, router, selection, measurement, time, staleness, preferences, dynamic hooks (§13)
- [ ] Every reflow in §14, verified at the six widths
- [ ] WCAG 2.2 AA, keyboard, ARIA, non-visual equivalents, reduced motion, forced colours (§15)
- [ ] Message catalogue, `Intl` formatting, logical properties (§16)
- [ ] Every extension point in §17
- [ ] Documentation, visual regression, accessibility tests, performance and bundle budgets, determinism (§18)
- [ ] All six reference compositions built as examples (§19)

---

## 21. Not covered

A complete general-purpose library will probably also need the items below. They are not required to reach the interface this
spec targets, so they are out of scope here:

- form inputs and validation
- dialogs and modals
- menus and popovers
- toasts
- date pickers
- virtualised lists
- line, area and pie charts
- drag and drop
- editable tables

---

## Appendix A. Default theme (reference values)

These defaults reproduce the target look. Consumers replace them through tokens.

| Token | Light | Dark |
|---|---|---|
| canvas | `#f3f5f8` | `#10141b` |
| surface | `#fcfcfd` | `#171c25` |
| surface-alt | `#eef1f6` | `#1f2530` |
| text | `#0e1420` | `#eef1f6` |
| text-secondary | `#4a5468` | `#b8c0ce` |
| text-muted | `#7d879a` | `#8a93a5` |
| border | `#dfe4ec` | `#2a313d` |
| hairline | `rgba(14,20,32,.10)` | `rgba(255,255,255,.10)` |
| success | `#0ca30c` | `#0ca30c` |
| warning | `#fab219` | `#fab219` |
| serious | `#ec835a` | `#ec835a` |
| critical | `#d03b3b` | `#d03b3b` |
| emphasis | `#6d47e0` | `#a58bff` |
| selected | `#0e1420` | `#eef1f6` |
| focus | `#2a78d6` | `#3987e5` |
| link | `#2a78d6` | `#6aa6f0` |
| categorical 1–6 | `#2a78d6` `#653184` `#0d7680` `#1d2660` `#9656aa` `#1a9ec7` | `#3987e5` `#8a44d8` `#1f8089` `#b4bef5` `#a86fb8` `#31b5f4` |
| hue ramp 1–3 | `#a49bed` `#6250d6` `#352a91` | `#5546c0` `#8a7ee6` `#c9c2f7` |
| family A (a / b) | `#5596de` / `#1d5cab` | `#3a7fd0` / `#80b6f0` |
| family B | `#1f9294` / `#0c5c61` | `#1a8a8c` / `#4cc4c2` |
| family C | `#b67ad0` / `#84409f` | `#a35cbd` / `#d095e6` |
| family D | `#2e3d99` / `#1d2870` | `#b8c4fa` / `#d4dcfd` |
| family neutral | mix(text 40 %, surface) / text | same |
| relation stroke | `#6b7382` | lighter neutral |
| sans | `system-ui, -apple-system, "Segoe UI", sans-serif` | |
| mono | `ui-monospace, "Cascadia Mono", Consolas, "SF Mono", monospace` | |
