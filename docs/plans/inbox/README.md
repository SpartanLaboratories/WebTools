# Questions inbox

The pipeline's questions for you, and your answers, on one published page:
[**WebTools Questions**](https://claude.ai/artifact/5hzqM3YmTnBcSyPaYbpbEW). It replaces the localhost server planned
earlier: the page keeps its data on claude.ai, so it works from any device with nothing running locally. The link opens
only for the repository's owner. [`index.html`](index.html) is the page's source as published; its send log starts empty.

## How it works

- A run asks by writing a document to the page's `questions` collection.
- You answer on the page. Each answer saves to `answers/<question id>` the moment you pick an option or stop typing.
- **Send to Claude** republishes the page with a send log. A Claude session watching the page is woken by the
  republish; a comment sent to Claude on the page wakes it too. Database writes on their own wake nothing.
- The run reads the answers, records them, and closes each question with what came of it, which the page shows under
  Closed.

## Decisions

Answered in the inbox on 2026-10-30:

| Question | Answer |
|---|---|
| Which runs share one inbox? | One inbox per repository; each card is tagged with its issue and step, and the work board links the inbox |
| Where are answers kept for the record? | Copied into the run's `plan.json` (`wip.interview` / `wip.questions`) and its research README or `plan.md`; the inbox is the working surface |
| What does a run do while a question waits? | Each question says: interview and plan-acceptance questions block the run; later ones go on with the recommended option, and the answer amends the work |
| What do the committed plan boards show? | The boards are also published as interactive pages, each with its run's questions built in and a link to the full inbox |
| How do you hear that a question is waiting? | One push notification per batch of questions, with the inbox link |

Open: where a published board keeps its answers (`board-store` in the inbox). Each published page has its own store and
cannot read another's, so a board published as a separate page cannot save into the inbox.

Not wired yet: the presenter agent and the global `dashboards` skill, which carry out the protocol below, live outside
this repository.

## The protocol, for the presenter

1. **Ask.** Write `questions/<id>` (fields below), `status: "open"`.
2. **Notify.** Send one push notification per batch: the issue, the step, how many questions wait, and the inbox link.
3. **Mirror.** Add each open question to the run's `plan.json` `wip.questions` with its inbox id, so the boards list it.
4. **Wait or go on.** A `blocking` question stops the run at its step. Otherwise the run goes on with `fallback`,
   recorded on the board as an assumption.
5. **Read.** On a wake, on resuming, and at each check-in while blocked, list `answers`. An answer is new when its `at`
   differs from the question's `seen`; set `seen` to that `at`, and the card reads "Claude has it".
6. **Record.** Copy the question, the choice, the note and the answer's `at` into `wip.interview` (interview step) or
   `wip.questions`, and into the run's Markdown.
7. **Close.** Set `status: "closed"`, `closedAt`, and `outcome`: what changed, with the commit.

Never rely on the wake alone. On 2026-10-30 the first Send republished the page but did not wake the watching session,
which had restarted in between. A run checks the answers itself on resuming and while it is blocked.

### Data

`questions/<id>`, writable only by the artifact's owner (`{path: "questions", read: "view", write: "owner"}`); Claude
writes as the owner. Ids are `<issue>-<slug>`, such as `64-config-shape`; setup questions have no issue.

| Field | Meaning |
|---|---|
| `title`, `prompt`, `context` | The card's text. Blank lines separate paragraphs and backticks mark code; `context` is folded |
| `kind` | `choice` (options and a note) or `text` (a written answer) |
| `options`, `recommended` | `[{key, label, detail}]`, and the key of the recommended option |
| `blocking`, `fallback` | Whether the run waits, and what it goes on with if it does not |
| `runLabel`, `link`, `step`, `order`, `asked` | The card's tags and place; `link` opens the run's board or issue |
| `status`, `seen`, `closedAt`, `outcome` | `open` or `closed`; the answer `at` Claude has read; when the question closed and what came of it |

`answers/<question id>` is `{choice, note, at, by}`, written by the page; `by` is the viewer's opaque id.

The send log lives in the page itself (the `inbox-state` script), so it changes only with a republish:
`{sends: [{at, ids}]}`, the last 100 sends.
