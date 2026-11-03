# `Retry-After` and HTTP-date parsing: grammar, two-digit years, deltas and a `java.time` differential

## 1. Question

Which inputs should `RetryAfter.parse(value, nowEpochMillis)` and `parse(value, nowEpochMillis,
serverDateEpochMillis)` accept and reject (the same date grammar also reads the response's `Date` in
`RateSignalsReader.Standard`), and how does a `jvmTest` differential against `java.time` compare the parser
without false mismatches?

An answer has to meet these requirements:

- Common Kotlin only (the code is also compiled for `js`; no `java.*`, no regex needed).
- Text from outside the process returns `Result`; a failure carries a constant message and never echoes the
  value (the module's convention in `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlProblem.kt`:
  an `internal` enum of problems with constant messages and an `internal` `IllegalArgumentException` subclass,
  returned through `Result.failure`).
- The result is never a negative `Duration` (`HttpFailure.BudgetExhausted` and `UpstreamFailure.RateLimited`
  both `require(!x.isNegative())`); a date in the past gives `Duration.ZERO`.
- The pause a parsed value causes is capped elsewhere (`maxPause`), so the parser only has to be safe to add
  to a clock reading.
- A Level 4a table (the three forms, the two-digit-year rule, the leap second, the rejections) and a `jvmTest`
  differential against `java.time`.

## 2. Bottom line

- **Accept exactly RFC 9110's grammar, with three deliberate liberties:** ASCII-case-insensitive day names,
  month names and `GMT`; SP/HTAB trimmed around the whole value; a weekday that disagrees with the date is
  ignored (the instant never depends on it). Reject everything else: other zones (`UTC`, `+0000`), unpadded
  days, a four-digit year in the RFC 850 shape, a two-digit year elsewhere, fractions, signs, Unicode digits.
  Mature clients (OkHttp, Go, curl, CPython) accept far more; every extra shape costs a test row and an oracle
  exception for no known provider that sends it.
- **Numbers: `1*DIGIT` in ASCII, leading zeros allowed, saturating at 253,402,300,799 s** (the instant
  `9999-12-31T23:59:59Z` read as seconds from the epoch, which is also the largest delay a date form can
  express). A number too large for a `Long` is a very long wait, not a malformed header; failing it would
  turn "wait for ever" into "no hint at all". The result is always finite, so `now + delay` cannot overflow.
- **Two-digit years (RFC 850 only):** the year is the greatest year with those last two digits that is not
  after `referenceYear + 50`, where the reference is the clock the delay is measured against: `now` in the
  two-argument overload, the server's `Date` in the three-argument one. It is year-granular (at most one year
  of divergence from the RFC's "more than 50 years in the future" at the single boundary year) and it equals
  `java.time`'s reduced-year window `[referenceYear - 49, referenceYear + 50]`.
- **Delay = `max(0, date - reference)`** for dates, in whole milliseconds, with `serverDate` as the reference
  when the response carried a parseable `Date` (two server readings, so a skewed local clock cancels out);
  when `Date` is missing or malformed the reader calls the two-argument overload, which is what RFC 9110 §6.6.1
  says a recipient does (it uses the time it received the response).
- **Differential oracle:** `java.time` used as formatter and as strict parser, built with
  `DateTimeFormatterBuilder` and explicit name maps, never `RFC_1123_DATE_TIME` (it accepts hour 24, a missing
  weekday, one-digit days, numeric offsets and no seconds) and never `yyyy` (no year 0, needs an era) or
  `Locale`-dependent names (`Locale.UK` prints `Sept`). Measured: the parser and `java.time` agreed on every
  generated input (about 2 million checks per seed, three seeds, 0 mismatches), with the leap second,
  the ignored weekday, case and whitespace handled by input normalisation rather than by exceptions to the
  comparison.

## 3. Findings

Evidence labels: **documented** (a standard or a project's own text), **common practice** (what mature
libraries visibly do), **measured** (run in this research; versions given), **inference** (reasoned from code
or documents, not run).

### F1. RFC 9110's grammar is small and fixed (documented; RFC 9110, June 2022, §5.6.7 and §10.2.3)

- `Retry-After = HTTP-date / delay-seconds`, `delay-seconds = 1*DIGIT`, "a non-negative decimal integer".
- `HTTP-date = IMF-fixdate / obs-date`; a recipient "MUST accept all three":
  - `IMF-fixdate = day-name "," SP date1 SP time-of-day SP GMT`, `date1 = day SP month SP year`, `day = 2DIGIT`,
    `year = 4DIGIT`, e.g. `Sun, 06 Nov 1994 08:49:37 GMT`;
  - `rfc850-date = day-name-l "," SP date2 SP time-of-day SP GMT`, `date2 = day "-" month "-" 2DIGIT`, e.g.
    `Sunday, 06-Nov-94 08:49:37 GMT`;
  - `asctime-date = day-name SP date3 SP time-of-day SP year`, `date3 = month SP ( 2DIGIT / ( SP 1DIGIT ))`,
    e.g. `Sun Nov  6 08:49:37 1994`. So `Nov  6` (two spaces) and `Nov 06` are the two day spellings; `Nov 6`
    (one space, one digit) is not.
- `time-of-day = hour ":" minute ":" second ; 00:00:00 - 23:59:60 (leap second)`; `GMT = %s"GMT"`;
  "HTTP-date is case sensitive"; asctime values "are assumed to be in UTC".
- "Recipients of timestamp values are encouraged to be robust in parsing timestamps unless otherwise
  restricted by the field definition." `Retry-After`'s definition does not restrict it.

### F2. Case: the RFC says sensitive, the cache RFC says match insensitively, mature parsers ignore case (documented + common practice)

- RFC 9111 §4.2: "Although all date formats are specified to be case-sensitive, a cache recipient SHOULD match
  the field value case-insensitively."
- Go's `time.Parse` day and month lookup uses `match`, which "reports whether s1 and s2 match ignoring case";
  CPython's `parsedate_tz` lower-cases the month (`mm = mm.lower()`) and the day name.
- Folding must be ASCII-only. The module already has `HttpSyntax.asciiLower`, and
  `HttpSyntax.kt` documents why `lowercase()` is not used on protocol text (U+212A and U+0131).

### F3. The weekday is redundant; RFC 5322 requires it to match, nearly every client ignores it (documented + common practice)

- RFC 5322 §3.3: "the day-of-week (if included) MUST be the day implied by the date". RFC 9110 §5.6.7 says the
  semantics of day-name "are the same as those defined for the Internet Message Format constructs".
- Go: `case stdWeekDay: // Ignore weekday except for error checking.` (the name must be a day name; the value
  is discarded). Ktor's patterns use `***` wildcards for the day name. curl's `parsedate` records the weekday
  (`wdaynum = checkday(date, len)`) and never compares it with the date.
- Measured (JDK 23.0.2): `java.time` rejects a mismatch under every `ResolverStyle` ("Conflict found: Field
  DayOfWeek 7 differs from DayOfWeek 1 derived from 1994-11-06"). A differential that keeps `EEE` in the
  oracle pattern therefore cannot compare a parser that ignores the weekday; the oracle pattern omits it
  (section 6.8).

### F4. Leap second (documented + measured)

- RFC 9110 and RFC 5322 §3.3 allow `23:59:60`. Go rejects second 60 (`if sec < 0 || 60 <= sec`); curl accepts up
  to 60 (`(secnum > 60)` is the failure).
- `java.time` cannot express it in a field-based formatter (measured: `Invalid value for SecondOfMinute (valid
  values 0 - 59): 60`). `DateTimeFormatter.ISO_INSTANT` maps it to `23:59:59` ("replacing the second-of-minute
  of 60 with 59", `DateTimeFormatter` Javadoc, jdk-23-ga).
- The linear reading (`hh:mm:60` is `hh:mm:00` of the next minute, so `23:59:60` is the next day's midnight) is
  the one a `days * 86400 + h * 3600 + m * 60 + s` conversion gives and the one curl's `time2epoch` gives.
  It never shortens a wait by a second, which the clamped reading would.

### F5. Past dates and the cap on waits (common practice)

- urllib3 2.5.0 `Retry.parse_retry_after`: `^\s*[0-9]+\s*$` is read as seconds, otherwise `email.utils.parsedate_tz`
  then `mktime_tz`; `seconds = max(seconds, 0)`.
- curl 8.16.0 `http_header_r`: "try it as a date first, because a date can otherwise start with and get
  treated as a number"; a date in the past leaves `retry_after` at 0; "limit to 6 hours max. this is not
  documented so that it can be changed in the future if necessary."
- OkHttp 5.5.0 `RetryAndFollowUpInterceptor.retryAfter`: "currently ignores a HTTP-date, and assumes any non int 0
  is a delay"; the guard is `header.matches("\\d+".toRegex())` followed by `Integer.valueOf(header)`.
  Inference: a digit string above `Int.MAX_VALUE` makes `Integer.valueOf` throw `NumberFormatException`
  inside the interceptor, because the regex bounds nothing.

### F6. Two-digit years: every mature parser uses a fixed pivot; none implements the RFC 9110 sliding rule (common practice)

| Parser | Rule | Source |
|---|---|---|
| Go `time.Parse` layout `06` | `>= 69` is 19yy, else 20yy | `stdYear` in `src/time/format.go`, go1.25.0 |
| curl `parsedate` | `> 70` is 19yy, else 20yy | `parsedate.c`, curl-8_16_0 |
| CPython `_parsedate_tz` | `> 68` is 19yy, else 20yy | `Lib/email/_parseaddr.py`, v3.13.0 |
| RFC 5322 §4.3 (obsolete year) | 00-49 is 20yy, 50-99 is 19yy | RFC 5322 |
| `SimpleDateFormat` `yy` | within 80 years before and 20 after the instance's creation time | Javadoc, jdk-23-ga |
| Ktor `fromHttpToGmtDate` | none: its RFC 850 pattern is `****, dd-MMM-YYYY hh:mm:ss zzz` (four year characters) | `DateUtils.kt`, tag 3.3.0 |

Inference for the Ktor row: reading the pattern, the RFC's own example `Sunday, 06-Nov-94 08:49:37 GMT` has two year
characters where the pattern takes four, so it falls through every pattern and fails; not run.

A fixed pivot silently drifts (Go's reads `00` as 2000 and `69` as 1969 for ever, while RFC 9110 moves the
window with the clock). RFC 9110 §5.6.7 is the only text that states the sliding rule: "MUST interpret a timestamp
that appears to be more than 50 years in the future as representing the most recent year in the past that had the
same last two digits."

### F7. The sliding rule reduces to one line, and it equals a `java.time` reduced-year window (documented + measured)

- Reading: among the years whose last two digits are `yy`, take the greatest one that is not more than 50 years
  after the reference. With `limit = referenceYear + 50`: `year = limit - floorMod(limit - yy, 100)`.
  The accepted window is `(referenceYear - 50, referenceYear + 50]`, exactly 100 years wide, so the answer is unique.
- Year-granular rather than timestamp-granular: a date in `referenceYear + 50` counts as "not more than 50 years
  ahead" up to the end of that year (at most 365 days later than the strict reading, and only for the one
  `yy` whose year is `referenceYear + 50`). A timestamp-granular version needs `plusYears(50)` semantics for a
  `Feb 29` reference (`java.time` clamps to `Feb 28`) and gains nothing a provider would see.
- `DateTimeFormatterBuilder.appendValueReduced(YEAR, 2, 2, base)` has the same window: "a base value of 1980 and
  a width of 2 will have valid values from 1980 to 2079". With `base = referenceYear - 49` the two are identical.
- Measured (JDK 23.0.2): **`appendValueReduced` is only usable for `base >= 1`.** With `base = 0`, `06` parses
  as year 94; with `base = -10`, `06` parses as year -6; with `base = 1`, `00` parses as 100 and `06` as 6
  (correct). The Javadoc does not mention the restriction. The parser therefore clamps the reference year to
  `50..9949` (so every result year is in `1..9999`), and the oracle's base is that clamped year minus 49.

### F8. The reference clock for the two-digit year (inference from F7 and RFC 9110 §6.6.1)

- The delay of a date form is `date - reference`. Interpreting `yy` against the same reference keeps the answer
  internally consistent: a two-digit year and the subtraction use one clock. For the three-argument overload
  that clock is the server's `Date`; for the two-argument one it is `now`; for the response's own `Date` it is
  `now` (there is no other clock). The choice only moves a boundary by the clock skew, one year of window edge
  in 100, so it has no practical effect; picking one is only needed to make the table and the oracle
  deterministic.

### F9. The server `Date` delta (documented + measured)

- RFC 9110 §6.6.1: `Date` "represents the date and time at which the message was originated"; "A recipient with a
  clock that receives a response message without a Date header field MUST record the time it was received";
  "A recipient with a clock that receives a response with an invalid Date header field value MAY replace that
  value with the time that response was received." So a missing or malformed `Date` means "the time of receipt",
  which is `now`: the two-argument overload.
- A server date minus a server `Date` is independent of the local clock: with the local clock reading
  `1999-12-31T23:00:00Z`, the server's `Date` at `23:10:00Z` (a client 10 minutes behind) and
  `Retry-After: Sat, 01 Jan 2000 00:00:00 GMT`, the delay is 3,000 s (measured, mirror), not the 3,600 s the
  local clock gives. A `Retry-After` date at `23:05:00Z` is before the server's reading, so `0`.
- `delay-seconds` is "seconds to delay after receiving the response" and is unaffected by `Date`.

### F10. `java.time` pitfalls for an oracle (measured, JDK 23.0.2, `DateTimeFormatter` Javadoc jdk-23-ga)

| Candidate | Measured behaviour | Use |
|---|---|---|
| `RFC_1123_DATE_TIME` | accepts `sun, 06 nov 1994 08:49:37 gmt`, `6 Nov 1994 08:49:37 GMT` (no weekday, one-digit day), `+0100`, `08:49 GMT` (no seconds), and `24:00:00` (rolls to the next day); rejects the leap second and a two-digit year | not an oracle: it accepts what the RFC grammar does not |
| `yyyy` (year-of-era), STRICT | `06 Nov 1994` fails ("Unable to obtain LocalDate ... YearOfEra=1994") because there is no era; SMART rejects year 0000 | not usable |
| `uuuu` | accepts a five-digit year (`06 Nov 12345`), variable width 4..19 | accepts what the grammar does not |
| `appendValue(ChronoField.YEAR, 4)` | exactly four digits; year `0000` and `9999` work | use |
| `ofPattern("MMM", Locale.UK)` | prints `Sept` | name text must come from `appendText(field, Map)` with explicit names |
| `ResolverStyle.STRICT` | `Feb 31`, `Feb 29` in 1900, hour 24, second 60/61, day 0 all rejected; SMART turns `31 Feb 1994` into `28 Feb` | use STRICT |
| `padNext(2).appendValue(DAY_OF_MONTH)` | accepts `Nov  6` and `Nov 06`, rejects `Nov 6` | exactly the asctime `date3` |
| `parseCaseInsensitive()` | matches names and the `GMT` literal in any case | the oracle for the case liberty |

### F11. Civil date to epoch day with no platform API (documented + measured)

Howard Hinnant's `days_from_civil` and `civil_from_days` (public domain; "The accuracy of the algorithms under
these rules is exact, until overflow occurs"; tested over -1,000,000-01-01 to 1,000,000-12-31) are integer-only
and need only floor division. A Java mirror of the sketch in section 6.6 agreed with `java.time` for every
year 0000 to 9999 generated (F12).

### F12. Differential result (measured; JDK 23.0.2, seeded `SplittableRandom`, a Java mirror of section 6.6)

The mirror is a line-for-line Java port of the sketch (no Kotlin compiler was available, so the Kotlin itself
was not run). Parts as in section 6.8, 200,000 iterations per part, seeds 7, 11 and 12345:

- **A (accept side):** 400,000 + 400,000 + 400,000 texts (IMF, asctime, RFC 850), each printed by a `java.time`
  formatter from a random instant (years 0000..9999; RFC 850 within the reference's window), each read back by
  the parser to the same instant and by `java.time`'s strict parser to the same instant: 0 mismatches.
- **B (field-random, valid and invalid):** about 152,000 accepted and 47,000 rejected per form per seed, random
  weekday names, random case, random SP/HTAB padding, day 0..32, hour 0..25, minute 0..61, second 0..62 with 60
  forced one time in six (about 30,500 accepted leap-second IMF texts per seed): 0 mismatches.
- **C (mutants):** 150,000 single-edit mutants (delete, insert, replace by a foreign character, truncate) of
  valid texts: the parser rejected all; the strict oracle rejected all.
- The first run, with a reduced-year base of 0 for small reference years, showed 762 mismatches, all RFC 850 rows
  with reference years below 50: the oracle's quirk in F7, not the parser.

## 4. How comparable projects do it

- **OkHttp 5.5.0**, `okhttp3.internal.http.toHttpDateOrNull` in
  [`DateFormatting.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/DateFormatting.kt)
  (identical at 5.3.2): a strict `SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)` with
  `isLenient = false` that must consume the whole text, then 15 "browser compatible" patterns (any zone, other
  separators, `yy` years) where "Something was parsed. It's possible the entire string was not consumed but we
  ignore that", so trailing junk is accepted. The `Retry-After` reader is separate and does not read dates
  (F5). The date parser is JVM-only, a shared `SimpleDateFormat` guarded by thread locals and a lock; it cannot move to common code.
- **Ktor 3.3.0**, `String.fromHttpToGmtDate` in
  [`DateUtils.kt`](https://github.com/ktorio/ktor/blob/3.3.0/ktor-http/common/src/io/ktor/http/DateUtils.kt):
  11 patterns tried in turn through `GMTDateParser`
  ([`GMTDateParser.kt`](https://github.com/ktorio/ktor/blob/3.3.0/ktor-utils/common/src/io/ktor/util/date/GMTDateParser.kt)),
  common code, `trim()`-ed input, `error("Failed to parse date: $this")` on failure (the message echoes the
  input); "Note that only GMT(UTC) date is valid http date." Field values go through `chunk.toInt()`, which
  accepts a leading `+` or `-` (inference, not run).
- **Go 1.25.0**, [`net/http.ParseTime`](https://github.com/golang/go/blob/go1.25.0/src/net/http/http.go):
  `TimeFormat` (`"Mon, 02 Jan 2006 15:04:05 GMT"`, `GMT` literal), `time.RFC850`, `time.ANSIC`, each tried with
  `time.Parse`; exact layouts, case-insensitive names, weekday ignored, second 60 rejected, fixed `69` pivot,
  `_2` accepts a one-digit day after a single space.
- **curl 8.16.0**, [`lib/parsedate.c`](https://github.com/curl/curl/blob/curl-8_16_0/lib/parsedate.c): a free-form
  tokenizer (any order, names anywhere, numeric zones and zone names), fixed `70` pivot, `secnum > 60` fails,
  the day-of-month range test is `mdaynum > 31` only, years before 1583 fail. Its
  `Retry-After` reader ([`lib/http.c`](https://github.com/curl/curl/blob/curl-8_16_0/lib/http.c), `http_header_r`)
  tries the date first, then a number, past dates give 0, and the wait is capped at 6 hours.
- **urllib3 2.5.0**, [`Retry.parse_retry_after`](https://github.com/urllib3/urllib3/blob/2.5.0/src/urllib3/util/retry.py):
  `^\s*[0-9]+\s*$` then `email.utils.parsedate_tz`; negative results clamp to 0; an unparseable header raises
  `InvalidHeader`.
- **CPython 3.13**, [`email.utils._parsedate_tz`](https://github.com/python/cpython/blob/v3.13.0/Lib/email/_parseaddr.py):
  free-form, case-insensitive, accepts many zones, fixed `68` pivot.
- **java.time** `DateTimeFormatter.RFC_1123_DATE_TIME`: lenient in the ways listed in F10; it is a convenient
  parser for mail-like dates and the wrong oracle for a strict HTTP grammar.

The shape that recurs: strict first, then a lenient superset (OkHttp, Ktor, curl), with the superset's width a
frequent source of defects (trailing junk accepted, `toInt()` signs, `Feb 31` passing). A parser that wants a
meaningful differential keeps one grammar and enumerates its liberties.

## 5. Pitfalls and regrets

- **A lenient superset is untestable against an oracle.** OkHttp's 15 fallbacks, curl's tokenizer and Python's
  free-form parser each accept shapes that `java.time` cannot confirm; there is no independent referee for
  "`Sun Nov 6 1994 08:49:37 UTC` is November 6".
- **Silent drift from a fixed pivot** (Go 69, curl 70, CPython 68): an RFC 850 date in 2070 is read as 1970.
  The sliding rule needs a reference clock, which a library takes as an argument anyway.
- **Overflow on the number form.** OkHttp's `Integer.valueOf` after `\d+` (F5, inference); urllib3's `int()`
  of an unbounded digit string is unbounded as well. Saturate at parse time.
- **Failing a huge number is the unsafe direction.** "Wait a very long time" read as "no hint" makes the budget
  hammer a server that asked to be left alone (Kord issue 1074, cited in the stack research, is the same class
  of regret: a `Retry-After` of about 600 s ignored).
- **`Duration` saturates to `INFINITE`, and `INFINITE.inWholeMilliseconds` is `Long.MAX_VALUE`** ("An infinite
  duration value is converted either to Long.MAX_VALUE or Long.MIN_VALUE"). `Long.toDuration` coerces to
  `MAX_MILLIS`, which is `INFINITE`, so an unbounded delay added to a clock reading overflows. A finite cap
  avoids the case.
- **Non-ASCII digits.** The parser compares with `in '0'..'9'` (the style of `HttpSyntax.isTokenChar`) rather than a
  character-class predicate, and the table carries Arabic-Indic and full-width digit rows so a refactor to a
  broader predicate fails a test.
- **Locale-dependent oracle names**: `Locale.UK` prints `Sept` (measured); names come from explicit maps.
- **`appendValueReduced` with a non-positive base** (F7), **`yyyy`** (no year 0, era required), **`uuuu`** (five
  digits) and **`RFC_1123_DATE_TIME`** (F10) each produce a false mismatch in a naive differential.
- **Echoing the value in a failure message** (Ktor's `error("Failed to parse date: $this")`). A `Retry-After`
  value is attacker-influenced text; the module's failures never carry it.
- **Duplicate and combined values.** `Retry-After` is not a list field; two header lines, or two values folded
  with a comma (`120, 60`, or `Sat, 01 Jan ...` twice), fail to parse. The reader, not the parser, decides what to do with
  several lines (section 6.9).

## 6. Recommendation for this repository

Build one internal date parser, `HttpDate`, in `commonMain`, and let `RetryAfter.parse` and the `Date` reading
in `RateSignalsReader.Standard` both call it. Keep the grammar to RFC 9110's three shapes plus the three
liberties below, because every other shape is a case the oracle cannot referee and no researched provider
emits. The oracle compares the parser's calendar arithmetic (the part that can silently be wrong) against an
independent implementation, and everything that is a policy, not arithmetic, is a table row.

### 6.1 The accepted grammar

Input is trimmed of leading and trailing SP and HTAB (`HttpSyntax.trimOws`) and of nothing else. All digits are
ASCII `0-9`. Letters compare through `HttpSyntax.asciiLower`.

| Form | Shape (after trimming) | Fields | Accepted | Rejected |
|---|---|---|---|---|
| delay-seconds | `1*DIGIT` (first character is a digit) | the whole value | any run of ASCII digits, leading zeros included (`0`, `007`, `120`); saturates at 253,402,300,799 | empty; a sign (`+1`, `-1`); a fraction or exponent (`1.5`, `1e3`); a unit (`12 s`); `0x10`; a list (`120, 60`); embedded space; any non-ASCII digit (U+0663, U+FF11); CR, LF, NUL |
| IMF-fixdate | `Day, DD Mon YYYY HH:MM:SS GMT`, 29 characters | day name: `Mon Tue Wed Thu Fri Sat Sun` | any ASCII case | `Sunday,` (long name), `Sept`, `November` |
| | | `DD` | exactly two digits, `01`..`31`, valid for the month and year (leap years by the Gregorian rule) | `6`, `00`, `32`, `31 Feb`, `30 Feb`, `31 Apr`, `29 Feb 1900` |
| | | `YYYY` | exactly four digits, `0000`..`9999` | two digits, three digits, five digits |
| | | `HH:MM:SS` | `00..23`, `00..59`, `00..60` (60 is the leap second); two digits each | `24:00:00`, `08:60:00`, `08:49:61`, `8:49:37`, `08:49`, fractions |
| | | `GMT` | any ASCII case | `UTC`, `UT`, `Z`, `+0000`, `-0000`, `EST`, no zone, text after it |
| | | separators | single SP after the comma, single SP between fields | two spaces, tabs, NBSP (U+00A0), no space after the comma |
| | | weekday vs date | ignored when it disagrees | (not checked, see 6.9) |
| RFC 850 | `Weekday, DD-Mon-YY HH:MM:SS GMT` | day name: `Monday` .. `Sunday` | any ASCII case | `Mon,` with this layout |
| | | `DD`, time, `GMT`, separators | as above, with `-` between day, month and year | `Sunday, 06-Nov-1994 ...` (four-digit year), `Sun, 06-Nov-94 ...` (short name) |
| | | `YY` | exactly two digits, resolved by 6.3 | one digit, three digits |
| asctime | `Dow Mon D HH:MM:SS YYYY` | day name: short, any ASCII case | | `Sunday Nov ...` |
| | | day | `Nov 06` (zero-padded) or `Nov  6` (two spaces, one digit), valid for the month | `Nov 6` (one space, one digit), `Nov  06` (two spaces, two digits), `Nov` followed by a tab |
| | | `YYYY` | exactly four digits, last field; no zone | `94`, text after the year, ` GMT` |

A text whose shape matches none of the three is `Format`. A text whose shape matches but whose day, hour,
minute or second is out of range, or whose day does not exist in that month, is `Range`. An empty (after trim)
text is `Empty`.

Dispatch needs no backtracking. Read the leading run of ASCII letters; if it is followed by `,` and has 3 letters
the form is IMF-fixdate; by `,` and 6 to 9 letters, RFC 850; by SP and 3 letters, asctime; anything else is
`Format`. For `Retry-After` only, a first character in `0-9` selects delay-seconds (all three date forms start
with a letter, so no date can be mistaken for a number; curl's "date first" ordering exists because its
tokenizer accepts digit-first dates).

### 6.2 Numeric rules

- `delay-seconds` accumulates as `acc = minOf(acc * 10 + digit, CAP)` with `CAP = 253_402_300_799L`
  (`acc <= 2.6e12`, no `Long` overflow). The remaining characters are still validated as digits, so
  `99999999999999999999999x` is `Format` and `99999999999999999999999` is `CAP` seconds. The `Duration` is
  `acc.seconds`.
- Zero is accepted (`0` and `000` give `Duration.ZERO`). It is a valid instruction to retry at once.
- A date form's delay is `dateMs - referenceMs` when positive, else `Duration.ZERO`; the subtraction is guarded
  (`if (d < 0) CAP_MS` after a wrap, then `minOf(d, CAP * 1000)`), so every result is in `[0, CAP s]`, finite
  and safe to add to a clock reading. Date values have whole-second precision; the delay is a whole number of
  seconds in milliseconds.
- `CAP` means a header cannot ask for more than the grammar can say. The caller still applies `maxPause`.

### 6.3 The two-digit year

`year = limit - (limit - yy).mod(100)` with `limit = clamp(yearOf(reference), 50, 9949) + 50`.

- **Reference:** the `now` of the two-argument overload, the `serverDate` of the three-argument one (the same
  clock the subtraction uses), and the `now` for the `Date` header itself. If the parse is called with an
  absurd reference (`Long.MIN_VALUE`, `Long.MAX_VALUE`) the clamp keeps every result year in `1..9999`, never an
  overflow.
- **Reading:** the greatest year with the last two digits `yy` that is not after `referenceYear + 50`. Examples
  at reference `2026-11-03T12:00:00Z` (limit 2076): `76` is 2076, `77` is 1977, `00` is 2000, `26` is 2026,
  `99` is 1999. At reference `1999-12-31T23:00:00Z` (limit 2049): `49` is 2049, `50` is 1950.
- **Granularity:** by year. `Thursday, 31-Dec-76 23:59:59 GMT` at the 2026 reference is read as 2076, 50 years and
  58 days ahead, which the RFC's wording ("more than 50 years in the future") strictly would send to 1976. The
  divergence exists for one `yy` value, within one year of the edge, and no clock-based test could tell a
  server's intent there.
- A `Feb 29` with `yy` is judged in the year the rule picks (`29-Feb-00` is valid in 2000, invalid when the rule
  picks 2100).

### 6.4 Past dates and the `Date` delta

- A date at or before the reference gives `Duration.ZERO`, never a negative duration.
- Two arguments: delay = `date - now`. Three arguments: delay = `date - serverDate`. `delay-seconds` ignores
  `serverDate`.
- `RateSignalsReader.Standard` reads `Date` with `HttpDate.parse(text, now)`; on success it calls the
  three-argument overload with that instant, on failure (missing, malformed, out of range) the two-argument one.
  The failure is not an error: RFC 9110 §6.6.1 tells a recipient to use the time of receipt.
- A `Date` that is far from `now` is still used as given. The delay is a difference of two server readings and
  does not depend on how wrong the server's clock is; `maxPause` bounds the effect of a broken pair.

### 6.5 Failures

```kotlin
internal enum class HttpDateProblem(val message: String) {
    Empty("The value is empty."),
    Format("The value is neither a whole number of seconds nor an HTTP-date."),
    Range("The HTTP-date has a field outside its range or a day its month does not have."),
}
internal class HttpDateParseException(val problem: HttpDateProblem) : IllegalArgumentException(problem.message)
```

`HttpDate.parse` returns `Result<Long>` (epoch milliseconds); `RetryAfter.parse` returns `Result<Duration>`.
The same enum serves both, so the Level 4a table asserts the problem, as the `Url` tests assert `UrlProblem`.
The exception type is `internal` and mirrors `UrlParseException`; none of the messages carries input.
A programmer error does not exist here: every `Long` clock reading is accepted.

### 6.6 Common-Kotlin sketch (unrun Kotlin; its Java mirror is what F12 measured)

```kotlin
internal object HttpDate {
    private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val LONG_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun parse(text: String, referenceEpochMillis: Long): Result<Long> { /* dispatch of 6.1, then finish() */ }

    // ASCII-only, allocation-free name match: HttpSyntax.asciiLower(a) == HttpSyntax.asciiLower(b) per char.
    private fun matches(s: String, from: Int, name: String): Boolean
    // exactly n ASCII digits at [from, from + n), else -1
    private fun digits(s: String, from: Int, n: Int): Int
    private fun month(s: String, from: Int): Int            // 1..12 or -1

    private fun finish(year: Long, month: Int, day: Int, h: Int, mi: Int, s: Int): Result<Long> {
        if (day < 1 || day > daysInMonth(year, month) || h > 23 || mi > 59 || s > 60) return failure(Range)
        // second 60 is linear: it lands on the next minute's :00
        return Result.success((daysFromCivil(year, month, day) * 86_400L + h * 3_600L + mi * 60L + s) * 1_000L)
    }

    fun isLeap(y: Long): Boolean = (y % 4 == 0L && y % 100 != 0L) || y % 400 == 0L
    fun daysInMonth(y: Long, m: Int): Int = when (m) { 2 -> if (isLeap(y)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }

    /** Hinnant days_from_civil: days since 1970-01-01 of the proleptic Gregorian date; no validation. */
    fun daysFromCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = y.floorDiv(400)                          // floor, so negative years work
        val yoe = y - era * 400                            // [0, 399]
        val doy = (153 * ((month + 9) % 12) + 2) / 5 + day - 1   // [0, 365], March is month 0
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy    // [0, 146096]
        return era * 146_097 + doe - 719_468
    }

    /** Hinnant civil_from_days, the year only. */
    fun yearFromDays(days: Long): Long {
        val z = days + 719_468
        val era = z.floorDiv(146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        return yoe + era * 400 + (if (mp >= 10) 1 else 0)   // m = mp < 10 ? mp + 3 : mp - 9; year + (m <= 2)
    }

    fun resolveTwoDigitYear(yy: Int, referenceEpochMillis: Long): Long {
        val referenceYear = yearFromDays(referenceEpochMillis.floorDiv(86_400_000L)).coerceIn(50L, 9949L)
        val limit = referenceYear + 50
        return limit - (limit - yy).mod(100L)
    }
}

public object RetryAfter {
    private const val CAP_SECONDS = 253_402_300_799L
    public fun parse(value: String, nowEpochMillis: Long): Result<Duration> = parse(value, nowEpochMillis, nowEpochMillis)
    public fun parse(value: String, nowEpochMillis: Long, serverDateEpochMillis: Long): Result<Duration> {
        val text = HttpSyntax.trimOws(value)
        if (text.isEmpty()) return failure(Empty)
        if (text[0] in '0'..'9') { /* acc = minOf(acc * 10 + d, CAP_SECONDS); any non-digit -> failure(Format) */ }
        return HttpDate.parse(text, serverDateEpochMillis).map { dateMs ->
            if (dateMs <= serverDateEpochMillis) Duration.ZERO
            else { val d = dateMs - serverDateEpochMillis; (if (d < 0) CAP_SECONDS * 1_000 else minOf(d, CAP_SECONDS * 1_000)).milliseconds }
        }
    }
}
```

Notes for the implementer:

- The two-argument overload is the three-argument one with `serverDate = now`; there is no second code path.
  In the three-argument overload `nowEpochMillis` is not read at all (the delay is a difference of two server
  readings); the parameter stays because the specified signature carries it.
- `Long.floorDiv(Int)`, `Long.mod(Long)` and `Duration`'s `Long.milliseconds`/`Long.seconds` are stable common stdlib
  (the module already targets Kotlin 2.2.0). `Long.milliseconds` for the capped value (at most 2.5e14 ms) is a
  finite `Duration`; an uncapped value would coerce to `INFINITE` (`Long.toDuration` coerces to `MAX_MILLIS`).
- IMF-fixdate is positional: with the dispatch done, the checks are `s[4] == ' '`, `digits(s, 5, 2)`, `s[7] == ' '`,
  `month(s, 8)`, `s[11] == ' '`, `digits(s, 12, 4)`, `s[16] == ' '`, time at 17, `' '` at 25, `GMT` at 26, length 29.
  RFC 850 offsets start at `L + 2` (`L` the name length) and asctime's day has the two spellings.
- No regex, no `String.format`, no `substring` allocation is required; the module's own `HttpSyntax` already has the
  scanning style (`skipOws`, `tokenEnd`).
- Hinnant's source places `era` with `(y >= 0 ? y : y-399) / 400`, C++ truncating division; the Kotlin line uses
  `floorDiv`, so the negative-year branch disappears.

### 6.7 Level 4a table

Fixed references: `NOW = 946_681_200_000` (`1999-12-31T23:00:00Z`) and `REF26 = 1_793_707_200_000`
(`2026-11-03T12:00:00Z`). Expected values were produced by the Java mirror and, for every accepted row, agree
with `java.time`. Tests live in `commonTest` under `com.spartanlabs.testing.deterministic.webtools.http`, one class
for `HttpDate` and one for `RetryAfter`, in the style of `HttpHeaderValueDeterministicTest`.

**`HttpDate.parse(text, reference)` (epoch ms, or the problem)**

| Text | Reference | Result |
|---|---|---|
| `Sun, 06 Nov 1994 08:49:37 GMT` (RFC 9110) | any | 784111777000 |
| `Sunday, 06-Nov-94 08:49:37 GMT` (RFC 9110) | REF26 | 784111777000 |
| `Sun Nov  6 08:49:37 1994` (RFC 9110) | any | 784111777000 |
| `Sun Nov 06 08:49:37 1994` | any | 784111777000 |
| `Thu, 01 Jan 1970 00:00:00 GMT` | any | 0 |
| `Wed, 21 Oct 2015 07:28:00 GMT` | any | 1445412480000 |
| `Fri, 31 Dec 1999 23:59:59 GMT` | any | 946684799000 |
| `Sat, 31 Dec 2016 23:59:60 GMT` (leap second) | any | 1483228800000 (equals `Sun, 01 Jan 2017 00:00:00 GMT`) |
| `Fri, 31 Dec 9999 23:59:59 GMT` (upper boundary) | any | 253402300799000 |
| `Sat, 01 Jan 0000 00:00:00 GMT` (lower boundary) | any | -62167219200000 |
| `Tue, 29 Feb 2000 00:00:00 GMT` | any | 951782400000 |
| `Thu, 29 Feb 2024 00:00:00 GMT` | any | 1709164800000 |
| `Thu, 29 Feb 1900 00:00:00 GMT` | any | Range |
| `sun, 06 nov 1994 08:49:37 gmt`, `SUN, 06 NOV 1994 08:49:37 GMT` | any | 784111777000 |
| `  Sun, 06 Nov 1994 08:49:37 GMT` + HTAB | any | 784111777000 |
| `Mon, 06 Nov 1994 08:49:37 GMT` (weekday disagrees) | any | 784111777000 |
| `Sunday, 06-Nov-76 08:49:37 GMT` | REF26 | 3371878177000 (2076-11-06) |
| `Sunday, 06-Nov-77 08:49:37 GMT` | REF26 | 247654177000 (1977-11-06) |
| `Thursday, 31-Dec-76 23:59:59 GMT` | REF26 | 3376684799000 (2076; year-granular edge) |
| `Friday, 01-Jan-77 00:00:00 GMT` | REF26 | 220924800000 (1977) |
| `Sunday, 06-Nov-00 08:49:37 GMT` / `-26` / `-99` | REF26 | 973500577000 (2000) / 2026-11-06 / 941878177000 (1999) |
| `Sunday, 06-Nov-49 08:49:37 GMT` | NOW | 2049-11-06 |
| `Monday, 06-Nov-50 08:49:37 GMT` | NOW | 1950-11-06 |
| `Monday, 29-Feb-00 00:00:00 GMT` | REF26 | 951782400000 (2000 is a leap year) |
| `Sunday, 06-Nov-94 08:49:37 GMT` | `Long.MIN_VALUE` / `Long.MAX_VALUE` | year 0094 / year 9994 (clamped reference, no overflow) |
| `Sun, 06 Nov 1994 08:49:37 UTC`, `... +0000`, no zone, `... GMT x` | any | Format |
| `Sun, 6 Nov 1994 ...`, `Sun,  06 Nov 1994 ...`, `Sun,\t06 Nov ...`, NBSP before `GMT` | any | Format |
| `Sun, 06 Nov 94 ...`, `Sun, 06 Nov 12345 ...`, `Sun, 06 November 1994 ...`, `Sun, 06 Sept 1994 ...` | any | Format |
| `Sun, 06-Nov-1994 ...`, `Sunday, 06-Nov-1994 ...`, `Sun, 06-Nov-94 ...` | any | Format |
| `Sun Nov 6 08:49:37 1994`, `Sun Nov  06 ...`, `Sun Nov  6 08:49:37 94`, `Sun Nov  6 08:49:37 1994 GMT` | any | Format |
| `Sun, 06 Nov 1994 8:49:37 GMT`, `... 08:49:37.5 GMT`, `Xyz, 06 Nov ...`, `Sun, 06 Foo 1994 ...` | any | Format |
| `Thu, 31 Feb 1994 ...`, `Sun, 30 Feb ...`, `Sun, 31 Apr ...`, `Sun, 00 Nov ...`, `Sun, 32 Nov ...` | any | Range |
| `... 24:00:00 GMT`, `... 08:60:00 GMT`, `... 08:49:61 GMT` | any | Range |
| `""`, `"   "` | any | Empty |
| `Sun`, `Sun,` | any | Format |

**`RetryAfter.parse(value, NOW)` and `parse(value, NOW, serverDate)`**

| Value | Server date | Result |
|---|---|---|
| `120` (RFC 9110 example) | none | 120 s |
| `0`, `000` | none | `Duration.ZERO` |
| `007` | none | 7 s |
| `  120` + HTAB | none | 120 s |
| `253402300799`, `253402300800`, `99999999999999999999999`, `9223372036854775807`, `9223372036854775808` | none | 253402300799 s each (cap) |
| `Fri, 31 Dec 1999 23:59:59 GMT` (RFC 9110 example) | none | 3599 s |
| `Fri, 31 Dec 1999 23:00:01 GMT` | none | 1 s |
| `Fri, 31 Dec 1999 23:00:00 GMT` (equal to now) | none | `Duration.ZERO` |
| `Fri, 31 Dec 1999 22:59:59 GMT`, `Sun, 06 Nov 1994 08:49:37 GMT` (past) | none | `Duration.ZERO` |
| `Saturday, 01-Jan-00 00:00:00 GMT`, `Sat Jan  1 00:00:00 2000`, `Sat Jan 01 00:00:00 2000` | none | 3600 s |
| `Fri, 31 Dec 1999 23:59:60 GMT` (leap second) | none | 3600 s |
| `sat, 01 jan 2000 00:00:00 gmt` | none | 3600 s |
| `Fri, 31 Dec 9999 23:59:59 GMT` at `now = 0` | none | 253402300799 s (equals the cap) |
| `Sat, 01 Jan 2000 00:00:00 GMT` | `1999-12-31T23:10:00Z` (946681800000) | 3000 s (local clock alone would give 3600 s) |
| `Fri, 31 Dec 1999 23:05:00 GMT` | `1999-12-31T23:10:00Z` | `Duration.ZERO` (before the server's reading) |
| `120` | `1999-12-31T23:10:00Z` | 120 s |
| `Fri, 31 Dec 9999 23:59:59 GMT` | `1999-12-31T23:10:00Z` | 252455618999 s |
| `""`, `"  "` | none | Empty |
| `-1`, `+1`, `1.5`, `1e3`, `12 s`, `0x10`, `120, 60`, `120\n`, U+0663, `１２` (full-width) | none | Format |
| `Fri, 31 Dec 1999 23:59:59 UTC`, `tomorrow`, two dates joined by a comma | none | Format |
| `99999999999999999999999x` | none | Format (the tail is still validated) |

Add: every failure's `message` equals `problem.message` and contains none of the input (assert with the longest
input in the table); the result of a success is never negative; and a sweep over `0..0xFFFF` in
`"1" + c + "2"` accepts only `c` in `'0'..'9'`, like the existing header-value sweep.

### 6.8 The `jvmTest` differential

File: `webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpDateJavaTimeDifferentialDeterministicTest.kt`,
in the style of `UrlOkHttpDifferentialDeterministicTest` (a fixed seed, `WEBTOOLS_DIFFERENTIAL_INPUTS` raising
the count, coverage floors scaled with it, labels and visible input in every failure message). The parser is
called unwrapped; a throw is a failure.

**Principle: the oracle referees calendar arithmetic and year windows; it is never asked to referee a policy.**
Every input fed to both sides is inside the grammar both implement (fixed-width fields, one separator style);
the policy liberties (case, padding, weekday, second 60) are removed from the oracle's input by a normalisation
step, and the shapes that only the parser rejects are table rows plus a mutation property.

Oracle formatters, built with `DateTimeFormatterBuilder` (all `ResolverStyle.STRICT`, `parseCaseInsensitive()`,
`.toFormatter(Locale.ROOT).withZone(ZoneOffset.UTC)`; `DN`/`DNL`/`MN` are `Map<Long, String>` of the RFC's
names, so no locale data is read):

```kotlin
private fun time(b: DateTimeFormatterBuilder) = b
    .appendValue(HOUR_OF_DAY, 2).appendLiteral(':').appendValue(MINUTE_OF_HOUR, 2).appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2)

private fun fin(b: DateTimeFormatterBuilder) = b.toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT).withZone(ZoneOffset.UTC)

// IMF with the day name (printing, and parsing text whose weekday is right)
val IMF = fin(time(DateTimeFormatterBuilder().parseCaseInsensitive()
    .appendText(DAY_OF_WEEK, DN).appendLiteral(", ").appendValue(DAY_OF_MONTH, 2).appendLiteral(' ')
    .appendText(MONTH_OF_YEAR, MN).appendLiteral(' ').appendValue(YEAR, 4).appendLiteral(' ')).appendLiteral(" GMT"))
// IMF without the day name (parsing, weekday stripped)
val IMF_NO_DAY = same without the .appendText(DAY_OF_WEEK, DN).appendLiteral(", ") prefix
// asctime: the padded day is exactly `date3`
val ASC = fin(time(DateTimeFormatterBuilder().parseCaseInsensitive()
    .appendText(DAY_OF_WEEK, DN).appendLiteral(' ').appendText(MONTH_OF_YEAR, MN).appendLiteral(' ')
    .padNext(2).appendValue(DAY_OF_MONTH).appendLiteral(' ')).appendLiteral(' ').appendValue(YEAR, 4))
// RFC 850: the base must be >= 1 (appendValueReduced misreads a non-positive base)
fun rfc850(withName: Boolean, base: Int) = fin(time(DateTimeFormatterBuilder().parseCaseInsensitive()
    .also { if (withName) it.appendText(DAY_OF_WEEK, DNL).appendLiteral(", ") }
    .appendValue(DAY_OF_MONTH, 2).appendLiteral('-').appendText(MONTH_OF_YEAR, MN).appendLiteral('-')
    .appendValueReduced(YEAR, 2, 2, base).appendLiteral(' ')).appendLiteral(" GMT"))
```

`Instant.from(formatter.parse(text))` is the oracle call; `DateTimeException` is "reject". Day-name text uses
explicit maps (`1 -> "Mon"` ... `7 -> "Sun"`), so `Locale.UK`'s `Sept` and JDK data versions are irrelevant.

**Four parts (200,000 iterations each by default, seed 7):**

1. **Accept side, `java.time` as the printer.** Per form, a random second in `[-62_167_219_200, 253_402_300_799]`
   printed with `IMF` / `ASC`; for RFC 850, a random reference year in `60..9859`, a random reference instant
   within it, and a random instant in `[referenceYear - 49, referenceYear + 50]` printed with `rfc850(true,
   referenceYear - 49)`. Assert `HttpDate.parse(text, reference)` equals the instant, and equals the `java.time`
   strict parse of the same text. Both must be accepted.
2. **Field-random, valid and invalid.** Day `0..32`, month name, year `0..9999` (biased to 1900, 2000, 2100, 0, 9999,
   2024, 1999 and to Feb 28-31), hour `0..25`, minute `0..61`, second `0..62` (second 60 at `23:59` one time in six),
   a random day name (right or wrong), random ASCII case per text, optional SP/HTAB padding. The oracle's input is
   normalised: weekday stripped (the no-day formatter), padding trimmed, second 60 replaced by 59 and 1000 ms
   added to the oracle's result, seconds above 60 left as they are (the oracle rejects them). Compare:
   both accept and agree, or both reject. For RFC 850 the reference year is random in `0..9999`; the oracle
   base is `clamp(referenceYear, 50, 9949) - 49`.
3. **Mutants.** Take printed valid texts of each form; apply one edit (delete a character, insert `x`, replace one
   by one of TAB, `~`, U+00E9, U+0660, U+0663, NUL, U+FF11, `z`, or truncate). The parser must reject every one,
   and the strict oracle (the full-name formatters) must reject too: this is a "both reject" comparison, and
   it holds for 150,000 of 150,000 measured mutants. A replacement by a letter is excluded because it can
   spell another valid name (`Mar` to `May`).
4. **Coverage floors** (scaled like the `Url` test): accepted and rejected counts per form in part 2 above
   20%, accepted leap-second texts above 5% of IMF, mutants run above 90% of attempted.

**Why no false mismatches:** (a) the oracle is a strict, fixed-width, locale-free reimplementation of the same
grammar, so its accept set is the parser's accept set once case, padding, weekday and second 60 are normalised
away; (b) the generator never feeds shapes `java.time` is lenient about (five-digit years, one-digit fields,
numeric zones), so the oracle's laxness cannot show up; (c) policy shapes the parser rejects and `java.time` would
accept (`6 Nov` single-digit days, `UTC`, four-digit RFC 850 years) are not generated in parts 1-2 and are table
rows; (d) the reduced-year base is kept at 1 or more; (e) names are not read from locale data.

### 6.9 Considerations (open trade-offs)

1. **Weekday agreement.** Options: ignore (recommended; Go, curl and Ktor do; the
   instant never depends on it; refusing would drop a usable `Retry-After` for a cosmetic error), or reject a
   mismatch (RFC 5322 §3.3 "MUST be the day implied by the date"; it needs only
   `(epochDay + 4).mod(7)` and is what `java.time` does natively, which would make the oracle simpler). The
   cost of the recommendation is one normalisation step in the differential and a table row.
2. **Extra shapes** (each additive later, with an oracle exception and rows): `UTC`/`UT` and numeric offsets as the
   zone (RFC 5322 forms), a four-digit year in the RFC 850 layout (Netscape cookie dates), `Nov 6` in asctime.
   Recommended: none now; no provider researched sends them.
3. **Several `Retry-After` lines.** `HttpHeaders.get` returns the last line, `getAll` every one. The budget already
   pauses until the maximum of every hint; recommended: the reader parses every line of `getAll("Retry-After")` and
   takes the longest wait among those that parse. A folded `120, 60` is `Format` and is ignored. For `Date`,
   `get` (the last line) is enough.
4. **Reference granularity.** Year-granular (recommended) against a timestamp-granular `plusYears(50)` rule: the
   difference is one year at one `yy`, and the strict rule needs the `Feb 29` clamp in both parser and oracle.
5. **A sanity bound on `Date`.** Using the delta only when `|serverDate - now|` is under a threshold would guard
   against a server with a broken clock but a correct `Retry-After`; not recommended, because `maxPause` already
   bounds the damage and the threshold would be a new policy parameter.

## 7. Load-bearing claims

1. **delay-seconds and the field's form.** "The Retry-After field value can be either an HTTP-date or a number of
   seconds to delay after receiving the response." and "A delay-seconds value is a non-negative decimal integer,
   representing time in seconds." with `delay-seconds  = 1*DIGIT`. RFC 9110 §10.2.3, June 2022,
   <https://www.rfc-editor.org/rfc/rfc9110.txt>.
2. **Three formats must be accepted.** "A recipient that parses a timestamp value in an HTTP field MUST accept all
   three HTTP-date formats." RFC 9110 §5.6.7, <https://www.rfc-editor.org/rfc/rfc9110.txt>.
3. **The grammar's shapes and range comment.** "date3        = month SP ( 2DIGIT / ( SP 1DIGIT ))", "date2        = day
   "-" month "-" 2DIGIT", "time-of-day  = hour ":" minute ":" second" with "; 00:00:00 - 23:59:60 (leap second)".
   RFC 9110 §5.6.7, <https://www.rfc-editor.org/rfc/rfc9110.txt>.
4. **Case sensitivity and robustness.** "HTTP-date is case sensitive.  Note that Section 4.2 of [CACHING] relaxes
   this for cache recipients." and "Recipients of timestamp values are encouraged to be robust in parsing
   timestamps unless otherwise restricted by the field definition." RFC 9110 §5.6.7,
   <https://www.rfc-editor.org/rfc/rfc9110.txt>.
5. **Case-insensitive matching for caches.** "Although all date formats are specified to be case-sensitive, a cache
   recipient SHOULD match the field value case-insensitively." RFC 9111 §4.2, June 2022,
   <https://www.rfc-editor.org/rfc/rfc9111.txt>.
6. **The two-digit-year rule.** "Recipients of a timestamp value in rfc850-date format, which uses a two-digit year,
   MUST interpret a timestamp that appears to be more than 50 years in the future as representing the most recent
   year in the past that had the same last two digits." RFC 9110 §5.6.7, <https://www.rfc-editor.org/rfc/rfc9110.txt>.
7. **Missing and invalid `Date`.** "A recipient with a clock that receives a response message without a Date header
   field MUST record the time it was received and append a corresponding Date header field to the message's header
   section if it is cached or forwarded downstream." and "A recipient with a clock that receives a response with an
   invalid Date header field value MAY replace that value with the time that response was received." RFC 9110
   §6.6.1, <https://www.rfc-editor.org/rfc/rfc9110.txt>.
8. **Semantic validity in the Internet Message Format.** "A date-time specification MUST be semantically valid.
   That is, the day-of-week (if included) MUST be the day implied by the date, the numeric day-of-month MUST be
   between 1 and the number of days allowed for the specified month (in the specified year), the time-of-day MUST
   be in the range 00:00:00 through 23:59:60". RFC 5322 §3.3, October 2008,
   <https://www.rfc-editor.org/rfc/rfc5322.txt>.
9. **RFC 5322's own two-digit-year pivot.** "If a two digit year is encountered whose value is between 00 and 49, the
   year is interpreted by adding 2000, ending up with a value between 2000 and 2049.  If a two digit year is
   encountered with a value between 50 and 99, or any three digit year is encountered, the year is interpreted by
   adding 1900." RFC 5322 §4.3, <https://www.rfc-editor.org/rfc/rfc5322.txt>.
10. **OkHttp's `Retry-After` reader ignores dates and is unbounded on digits.** "currently ignores a HTTP-date, and
    assumes any non int 0 is a delay" followed by `if (header.matches("\\d+".toRegex())) { return Integer.valueOf(header) }`.
    `RetryAndFollowUpInterceptor.retryAfter`, OkHttp tag parent-5.5.0,
    <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt>.
11. **OkHttp's date parser accepts trailing text in its fallbacks.** "Something was parsed. It's possible the entire
    string was not consumed but we ignore that." `toHttpDateOrNull`, OkHttp tag parent-5.5.0,
    <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/DateFormatting.kt>.
12. **Ktor's date parsing.** "Note that only GMT(UTC) date is valid http date." and `"****, dd-MMM-YYYY hh:mm:ss zzz"`
    among `HTTP_DATE_FORMATS`; failure is `error("Failed to parse date: $this")`. `DateUtils.kt`, Ktor tag 3.3.0,
    <https://github.com/ktorio/ktor/blob/3.3.0/ktor-http/common/src/io/ktor/http/DateUtils.kt>.
13. **Go's HTTP time parsing.** "ParseTime parses a time header (such as the Date: header), trying each of the three
    formats allowed by HTTP/1.1: [TimeFormat], [time.RFC850], and [time.ANSIC]." `net/http` go1.25.0,
    <https://github.com/golang/go/blob/go1.25.0/src/net/http/http.go>.
14. **Go's year pivot, weekday and name matching.** "if year >= 69 { // Unix time starts Dec 31 1969 in some time
    zones", "// Ignore weekday except for error checking.", "match reports whether s1 and s2 match ignoring case.",
    and `if sec < 0 || 60 <= sec {`. `time/format.go`, go1.25.0,
    <https://github.com/golang/go/blob/go1.25.0/src/time/format.go>.
15. **curl's `Retry-After` handling.** "try it as a date first, because a date can otherwise start with and get treated
    as a number" and "limit to 6 hours max. this is not documented so that it can be changed in the future if
    necessary." `http_header_r`, curl-8_16_0, <https://github.com/curl/curl/blob/curl-8_16_0/lib/http.c>.
16. **curl's date parser pivot and leap second.** `if(yearnum > 70)` / `yearnum += 1900;` and
    `(hournum > 23) || (minnum > 59) || (secnum > 60))` "clearly an illegal date". `parsedate`, curl-8_16_0,
    <https://github.com/curl/curl/blob/curl-8_16_0/lib/parsedate.c>.
17. **urllib3's `Retry-After`.** `if re.match(r"^\s*[0-9]+\s*$", retry_after):` then `email.utils.parsedate_tz`, and
    `seconds = max(seconds, 0)`. `Retry.parse_retry_after`, urllib3 2.5.0,
    <https://github.com/urllib3/urllib3/blob/2.5.0/src/urllib3/util/retry.py>.
18. **CPython's pivot.** `if yy < 100:` / "# The year is between 1969 and 1999 (inclusive)." / `if yy > 68:`.
    `Lib/email/_parseaddr.py`, CPython v3.13.0,
    <https://github.com/python/cpython/blob/v3.13.0/Lib/email/_parseaddr.py>.
19. **`SimpleDateFormat`'s two-digit-year window.** "It does this by adjusting dates to be within 80 years before and
    20 years after the time the SimpleDateFormat instance is created." `java.text.SimpleDateFormat`, jdk-23-ga,
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.base/share/classes/java/text/SimpleDateFormat.java>.
20. **`appendValueReduced` window.** "For example, a base value of 1980 and a width of 2 will have valid values from
    1980 to 2079." (`{@code}` markup omitted). `DateTimeFormatterBuilder.appendValueReduced`, jdk-23-ga,
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.base/share/classes/java/time/format/DateTimeFormatterBuilder.java>.
21. **`ResolverStyle.STRICT`.** "For example, resolving year-month and day-of-month in the ISO calendar system using
    strict mode will ensure that the day-of-month is valid for the year-month, rejecting invalid values."
    `ResolverStyle`, jdk-23-ga,
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.base/share/classes/java/time/format/ResolverStyle.java>.
22. **`java.time` and the leap second.** "If the time '23:59:60' is received, then a simple conversion is applied,
    replacing the second-of-minute of 60 with 59." `DateTimeFormatter.parsedLeapSecond`, jdk-23-ga,
    <https://github.com/openjdk/jdk/blob/jdk-23-ga/src/java.base/share/classes/java/time/format/DateTimeFormatter.java>.
23. **Hinnant's algorithms.** "The accuracy of the algorithms under these rules is exact, until overflow occurs."
    and "Consider these donated to the public domain." (the page also gives the source of `days_from_civil` and
    `civil_from_days` reproduced in the sketch). <https://howardhinnant.github.io/date_algorithms.html>, read
    2026-11-03.
24. **`Duration` saturates and `INFINITE` is `Long.MAX_VALUE` milliseconds.** `return durationOfMillis(millis.coerceIn(-MAX_MILLIS, MAX_MILLIS))`
    in `Long.toDuration`, `public val INFINITE: Duration = durationOfMillis(MAX_MILLIS)`, and "An infinite duration
    value is converted either to [Long.MAX_VALUE] or [Long.MIN_VALUE] depending on its sign." `Duration.kt`,
    Kotlin v2.2.0, <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/time/Duration.kt>.
25. **Stack-level carry-over.** RFC 9110 §10.2.3 and §5.6.7 as quoted in the stack research
    (`docs/plans/55-http-client-stack/research/call-budget.md`, load-bearing claims 1 and 2), the same sources as
    claims 1, 2 and 6 above.

## 8. Gaps

- **The Kotlin was not compiled or run.** The environment has no Kotlin compiler in the scratch directory; the
  differential measured a Java port of the sketch (JDK 23.0.2, source launcher), and the Kotlin in 6.6 is a
  translation. The repository's JDK 11 floor and its test toolchain were not exercised; every `java.time` API
  used (`DateTimeFormatterBuilder.appendText(TemporalField, Map)`, `padNext`, `appendValueReduced`, `ResolverStyle`)
  exists since Java 8, but the `appendValueReduced` non-positive-base behaviour was measured on JDK 23 only.
- **`Duration` saturation is reasoned from the stdlib source, not run.** The recommended cap keeps every value
  finite, so the claim only matters if the cap is dropped.
- **Ktor's behaviours** (a signed `toInt()`, the RFC 850 two-digit year falling through every pattern) are read
  from source at tag 3.3.0, not run.
- **No data on which `Retry-After` shapes real providers send** beyond the stack research's providers (seconds, and
  IMF-fixdate by the RFC). `UTC`/offset zones and four-digit RFC 850 years are excluded on the evidence that no
  researched provider emits them, which is absence of evidence.
- **The year-granular two-digit rule is a simplification of the RFC's wording** (one year at one `yy`, section 6.3).
  No source states the strict reading's boundary behaviour, so the table pins the chosen one.
- **Run time of the differential** (about 2.1 million parser calls per seed) was not timed against the repository's
  test budget; `WEBTOOLS_DIFFERENTIAL_INPUTS` scales it.
- **The three-argument overload's semantics for a `Date` very far from `now`** (a broken server clock) follows
  from "difference of two server readings" and is bounded by `maxPause`; no source says what clients do.
- **Chromium, Firefox and Rust `httpdate`** parsers were not read; the table of fixed pivots (F6) covers the five
  libraries above.
