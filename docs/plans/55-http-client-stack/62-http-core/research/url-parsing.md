# `Url`: parsing and canonicalising text so that every accepted URL reads the same in OkHttp 5.5.0's `HttpUrl`

## Question

How should the common-code `Url` of `webtools-http` parse and canonicalise text so that every URL it accepts has the same scheme, host, port, encoded path and encoded query in OkHttp 5.5.0's `HttpUrl`?

An answer has to settle:

- the parse algorithm and canonical form, rule by rule;
- the components `Url` exposes, its `toString()`, `redacted()` and equality;
- the `internal` encoders the facades use to build a URL from a base, path segments and query pairs;
- the Level 4a differential test: corpus, generator and exact assertion.

Requirements:

- `Url.parse(text): Result<Url>` rewrites accepted text exactly as `HttpUrl` does when the host name is ASCII: percent-encodes what needs it (non-ASCII path and query text as UTF-8), resolves `.` and `..` segments, drops the default port, gives an empty path `/`, trims surrounding whitespace and drops a `#fragment`.
- It rejects only what is ambiguous or unsafe: a non-ASCII or percent-encoded host name, a backslash anywhere, whitespace or a control character inside the URL, and user-info. Internationalised host names are refused in 1.0.0. Only `http` and `https` are accepted.
- Being stricter than `HttpUrl` is allowed; disagreeing is not. Nothing `HttpUrl` rejects may be accepted.
- `Url` is a plain common class: no JVM API, compiles for Kotlin/JS. A failure is a `Result` whose message is a constant per kind and never echoes the input, which may hold a key.
- `toString()` is the full canonical URL, query included. `redacted()` is scheme, host, port and path with a marker in place of a query. Equality and hash are on the canonical text.

Evidence labels: **documented** (a standard, official documentation or KDoc), **source-read** (OkHttp, Okio or Ktor source at a pinned tag), **measured** (run by me in a throwaway probe: Kotlin Gradle plugin 2.2.0, Gradle 9.7.1, JDK 23 with class files at the Java 11 floor, `com.squareup.okhttp3:okhttp-jvm:5.5.0`, `io.ktor:ktor-http-jvm:3.6.0`; every reading below was produced by `String.toHttpUrlOrNull()` unless it says otherwise). "OkHttp 5.5.0" means tag `parent-5.5.0` of `square/okhttp`.

## Bottom line

- **Accept-and-canonicalise exactly as `HttpUrl` does, with a short list of deliberate rejections, is implementable in about 320 lines of common Kotlin, and it agrees with `HttpUrl` on every input tried.** A prototype of the algorithm below (stdlib only; compiles for `js(IR)` and prints the same output on Node as on the JVM for the 20 sample inputs) was run against `HttpUrl` on 4.3 million inputs (generated, plus a 944-string corpus in each of three runs): 1.5 million accepted, **0 disagreements** on scheme, host, port, encoded path, encoded query, user-info, canonical text, idempotence and `redacted()`. Four deliberately broken variants of the prototype were each caught within 300,000 inputs.
- **The rejections the requirement lists are exactly where `HttpUrl` is lenient in ways other parsers are not**, and the probe found more places where the same applies, so reject these too: slash variants other than `://` (`http:host`, `http:/host`, `http:///host`), every `xn--` host label (OkHttp rewrites `xn--abc-` to `abc`), host characters outside letters, digits, `-`, `_` and `.`, a signed port (`:+80`), and Unicode whitespace and C1 controls. Each is stricter than `HttpUrl`, so none can disagree.
- **`HttpUrl` has a bug the differential test must treat as a rejection: `http://[1:2:3:4:5:6:7::8]/` throws `ArrayIndexOutOfBoundsException`**, which `toHttpUrlOrNull()` does not catch (it catches only `IllegalArgumentException`). `Url.parse` must reject it, and the harness must catch `Throwable` from `HttpUrl`. 3,849 of 2,000,944 inputs in one run hit it.
- **Canonical form follows `HttpUrl`, not RFC 3986 §6.2.2.** Existing `%XX` escapes keep their letter case and are never decoded (`%2f` and `%2F` are different URLs; so are `%41` and `A`), a malformed `%` stays verbatim, `127.1` and a trailing dot are kept as written, `+` in a query stays `+`, and `?` alone is a present-but-empty query (`""`, not `null`). Equality on canonical text therefore means "same bytes after canonicalisation", not "same resource".
- **Members: `scheme`, `host` (IPv6 without brackets, as `HttpUrl.host`), `port` (effective, 1..65535), `encodedPath` (starts with `/`), `encodedQuery: String?`.** `redacted()` is `toString()` with the query replaced by the marker **`?...`**, shown whenever a query is present (including a bare `?`). Failures are an `IllegalArgumentException` with one of ten constant messages; the kind is visible to tests through an internal subclass.
- **The builder encoders do not follow RFC 3986's unreserved set.** `addQueryParameter` leaves only `A-Za-z0-9*-._` raw (so `~` becomes `%7E` and `*` stays), and `addPathSegment` silently drops a `.` segment and pops on `..`; the internal encoders must match the first and refuse the second. This corrects one sentence of `docs/plans/55-http-client-stack/research/http-transport.md` F8 (it says everything outside `A-Za-z0-9-._~` is encoded).

## Findings

### F1. `HttpUrl` is one forgiving pass; this is its order (source-read)

`HttpUrl.Builder.parse` (`HttpUrl.kt`, `parent-5.5.0`) runs: trim → scheme → authority loop (user-info, host, port) → path → query → fragment. Every later stage is reachable only from the position the earlier stage left. What each stage does, and what this design does instead, is in F2 to F9. Two properties govern everything:

- Path, query and fragment are canonicalised with `alreadyEncoded = true`: a `%` is left alone (even when malformed), and the characters `\t`, `\n`, `\u000C` and `\r` are **silently dropped** (`-Url.kt`: "`// Skip this character.`"). So `http://h/p<TAB>q` becomes `/pq` (measured). Other parsers keep or encode the tab. This is a disagreement source, so any tab or newline inside the text is rejected here.
- The host is percent-decoded first and then canonicalised (`host = input.percentDecode(pos = pos, limit = portColonOffset).toCanonicalHost()`), so `%31%32%37.0.0.1` reads as `127.0.0.1`.

Version check: `HttpUrl.kt`, `-Url.kt` and `Punycode.kt` are byte-identical at `parent-5.5.1`, and `-HostnamesCommon.kt` is byte-identical on `master` (2026-10-22), so the readings below hold for 5.5.1 as well.

### F2. Trimming, scheme and the slashes after it (source-read + measured)

| Input | `HttpUrl` reading | Recommended |
|---|---|---|
| `  http://a.com/  `, TAB, LF, CR LF or FF at either end | `http://a.com/` | accept, trimmed |
| VT (`U+000B`) or NUL at either end; NBSP (`U+00A0`) at either end | rejected (not trimmed, then not a scheme or a bad host) | reject (whitespace or control) |
| `HTTP://EXAMPLE.COM/`, `HtTpS://Example.Com/` | scheme lower-cased | accept |
| `ftp://`, `httpx://`, `http+x://`, `//a.com`, `a.com`, `/x`, `file:///etc/passwd`, `javascript:alert(1)` | rejected | reject |
| `http:a.com`, `http:/a.com`, `http:///a.com`, `http:////a.com`, `http:\\a.com`, `http:\a.com` | **all** `http://a.com/` | reject all (only `://` exactly) |
| `http:` / `http://` / `http:///` | rejected (empty host) | reject |

- Trim set: `'\t', '\n', '\u000C', '\r', ' '` (`indexOfFirstNonAsciiWhitespace` in `-UtilCommon.kt`). Not VT, not NUL, not NBSP.
- Scheme detection: the text up to the first `:` must be an ASCII letter followed by ASCII letters, digits, `+`, `-`, `.`; then `http:` or `https:` is matched with `startsWith(..., ignoreCase = true)`. A Unicode letter before the colon makes it "no scheme", so `httpſ://[::1]` (U+017F) is rejected by `HttpUrl`. A reimplementation that uses Kotlin's `startsWith(ignoreCase = true)` on the raw text would accept it, because `ſ` upper-cases to `S` (measured: this is the first mutant in F15, caught immediately).
- Slashes: `val slashCount = input.slashCount(pos, limit)` counts `/` and `\`; with no base URL the authority is always read, after any number of slashes, including zero. WHATWG does the same for special schemes ("If c is neither U+002F (/) nor U+005C (\), then set state to authority state and decrease pointer by 1. Otherwise, special-scheme-missing-following-solidus validation error."), RFC 3986 and `java.net.URI` read `http:host` as an opaque URI and `http:/host` as a path with no authority. That is the "slashes confusion" class (F14). Genuinely ambiguous; reject (Consideration 1).

### F3. User-info (source-read + measured)

The authority loop splits at the first of `@ / \ ? #`; an `@` means "user info precedes", and the loop repeats for every further `@` (`http://a@b@c/` has user `a%40b`, host `c`). Measured: `http://u@h/`, `http://u:p@h/`, `http://a:b:c@h/`, `http://[::1]@h/` (user `%5B`, host `h`) are accepted by `HttpUrl`; **`http://@h/` and `http://:@h/` are accepted with an empty user and no `@` in the canonical text**, and `http://h@/` is rejected (empty host). An `@` after the first `/`, `?` or `#` is ordinary text (`http://h/@x`, `http://h?@x`, `http://h#@x`). **Recommended:** reject when the authority (the text between `://` and the first `/`, `?` or `#`) contains `@`, which also covers the empty forms. Because backslash is rejected everywhere, the authority end is unambiguous.

### F4. Host: ASCII canonicalisation (source-read + measured)

`toCanonicalHost` (`-HostnamesCommon.kt`) for a host with no `:`: IDNA-map each code point (ASCII upper case becomes lower case; every other ASCII character is unchanged), NFC, Punycode decode, validate, Punycode encode. Then `containsInvalidHostnameAsciiCodes` rejects `c <= '\u001f' || c >= '\u007f'` and ``" #%/:?@[\\]"``, and `containsInvalidLabelLengths` rejects a length outside `1..253` or a label outside `1..63` ("Trailing dots are okay").

| Input | `HttpUrl` reading |
|---|---|
| `http://A.B/`, `LOCALHOST`, `EXAMPLE.COM:8080` | lower-cased |
| `a.com.` / `localhost.` / `127.0.0.1.` | kept, trailing dot included |
| `a..com`, `.a.com`, `.`, `a.com..` | rejected (empty label) |
| `-a.com`, `a-.com`, `a_b.com`, `exa_mple.com` | accepted as written |
| 63-character label / 64-character label | accepted / rejected |
| host of 253 characters / 254 characters (the trailing dot counts) | accepted / rejected (measured for both with and without a trailing dot) |
| ASCII punctuation in a host | accepted by `HttpUrl`: ``! " $ & ' ( ) * + , ; < = > ^ ` { | } ~`` (so `http://a"b/`, `http://a<b/` and ``http://a`b/`` are valid hosts); rejected: controls, space, `%`, `[`, `]`; `/ ? # \ @ :` are delimiters |
| `127.1`, `2130706433`, `0x7f.0.0.1`, `0177.0.0.1`, `1.2.3.4.5`, `999.999.999.999`, `256.1.1.1`, `1.2.3`, `0`, `00`, `1e3` | all kept exactly as written |
| `%61.com`, `a%2eb`, `%31%32%37.0.0.1` | decoded: `a.com`, `a.b`, `127.0.0.1` |
| `a%00`, `%`, `a%zz`, `a%20b`, `a%25b`, `a%2fb`, `a%5cb`, `a%40b` | rejected |
| `☃.net`, `faß.de`, `café.com` | `xn--n3h.net`, `xn--fa-hia.de`, `xn--caf-dma.com` |
| fullwidth `ＥＸ`, circled `ⓛ`, fullwidth digits, `。` `．` `｡` | folded to `ex`, `l`, ASCII digits, `.` |
| soft hyphen or zero-width space inside a name | dropped (`a­b.com` becomes `ab.com`) |

Digits-and-dots hosts are not canonicalised to dotted-decimal (WHATWG would: "If asciiDomain ends in a number: ... Return the result of IPv4 parsing asciiDomain."). That is what the separate host-policy unit judges (`research/outbound-url-policy.md` Host rules); `Url` keeps them as written, and keeps `127.0.0.1.` with its dot.

**Recommended host rule for ASCII text:** reject any `%` or non-ASCII character (`HostEncoding`); lower-case ASCII letters; allow only `a-z 0-9 - _ .` (Consideration 2); length `1..253`, labels `1..63`, one trailing dot allowed; reject any label starting with `xn--` (F5). Everything OkHttp accepts inside that set is accepted with the same result, so no disagreement is possible.

### F5. `xn--` labels: accepting them can disagree (source-read + measured)

`idnToAscii` decodes every label that begins with `xn--` (case-insensitive: `string.regionMatches(pos, PREFIX_STRING, 0, 4, ignoreCase = true)`), then re-encodes the result, and `encodeLabel` writes a label that is pure ASCII after decoding **without** the prefix. So an ASCII `xn--` label is not kept as written:

| Input | `HttpUrl.host` |
|---|---|
| `xn--n3h.net`, `XN--N3H.net`, `xn--bcher-kva.example`, `xn--ls8h.la` | `xn--n3h.net`, `xn--n3h.net`, `xn--bcher-kva.example`, `xn--ls8h.la` (valid punycode round-trips) |
| `xn--abc-` | **`abc`** |
| `xn--n3h-` | **`n3h`** |
| `abc.xn--abc-.com` | **`abc.abc.com`** |
| `xn--a-` | **`a`** |
| `xn--a` | `xn--a` (kept) |
| `xn--`, `xn--zz`, `xn--ab-c`, `xn--0.com`, `xn---` | rejected (invalid punycode) |

A parser that keeps `xn--abc-` as written disagrees with `HttpUrl` (the first mutant-style check in F15 reproduces this). Getting every `xn--` case right needs RFC 3492 decode and encode, NFC and the UTS 46 mapping table in common code: far more than the whole rest of the parser, and OkHttp's own code carries "TODO: Must not begin with a combining mark" and CheckJoiners/CheckBidi TODOs. **Reject every label that starts with `xn--`** (`HostIdn`). The cost is that an administrator who pastes the punycode form of an internationalised name is refused with the same message as one who pastes the Unicode form, which is the stated 1.0.0 position.

### F6. IPv6 literals (source-read + measured)

Parse rules (`decodeIpv6`): the host is inside `[...]` (`portColonOffset` skips bracket contents); hex groups of 1 to 4 digits, case-insensitive; one `::` anywhere; an IPv4 dotted-quad is allowed only as the last 32 bits, each part decimal with no leading zero (`0` alone is fine) and at most 255; a zone (`%25eth0`, `%eth0`, `%`) is rejected because `%` is not a hex digit, so `HttpUrl` has no zone support. Output (`inet6AddressToAscii`, documented "in canonical form according to RFC 5952"): lower-case hex, no leading zeros, the longest run of at least two zero groups compressed to `::`, the first run on a tie. `canonicalizeInetAddress` turns `::ffff:a.b.c.d` (exactly ten zero bytes then `ffff`) into dotted IPv4. `HttpUrl.host` returns the address **without** brackets (its KDoc table: `http://[::1]/` gives `"::1"`), and `toString()` adds them when the host contains `:`.

| Input | `HttpUrl.host` | canonical `toString()` host part |
|---|---|---|
| `[::1]`, `[0:0:0:0:0:0:0:1]`, `[0000:...:0001]` | `::1` | `[::1]` |
| `[::]`, `[0::]`, `[::0:0]`, `[0:0:0:0:0:0:0:0]` | `::` | `[::]` |
| `[2001:DB8::1]` | `2001:db8::1` | `[2001:db8::1]` |
| `[2001:db8:0:0:1:0:0:1]` (tie, first run) | `2001:db8::1:0:0:1` | |
| `[2001:0:0:1:0:0:0:1]` (longest run) | `2001:0:0:1::1` | |
| `[1:2:3:4:5:6:7::]` (one zero group) | `1:2:3:4:5:6:7:0` | no `::` for a single group |
| `[::ffff:127.0.0.1]`, `[::ffff:7f00:1]`, `[::FFFF:7F00:1]` | `127.0.0.1` | `127.0.0.1` (no brackets) |
| `[::ffff:0:0]` | `0.0.0.0` | |
| `[::1.2.3.4]` (IPv4-compatible) | `::102:304` | stays IPv6 |
| `[64:ff9b::1.2.3.4]` (NAT64) | `64:ff9b::102:304` | stays IPv6 |
| `[fe80::1%25eth0]`, `[fe80::1%eth0]` | rejected | |
| `[::1`, `::1` (no brackets), `[]`, `[:::1]`, `[1::2::3]`, `[g::1]`, `[12345::]`, `[1.2.3.4]`, `[::1.2.3.256]`, `[::01.2.3.4]`, `[::ffff:1.2.3]`, `[1:2:3:4:5:6:7:8:9]`, `[::1]x`, `[v1.x]` | rejected | |
| **`[1:2:3:4:5:6:7::8]`** | **throws `ArrayIndexOutOfBoundsException`** | |
| `[::1]:`, `[::1]:8080` | `::1` with port 80 / 8080 | |

The exception: `decodeIpv6` checks `if (b == address.size) return null` only at the top of the loop; after reading `::` it does `b += 2` and then reads a group with no further check, so seven groups, `::`, then another group writes `address[16]`. Only the pattern "exactly seven groups before `::` and at least one group after" triggers it (3,849 hits in 2,000,944 generated inputs, all `ArrayIndexOutOfBoundsException`, no other exception class). Per RFC 4291 that text is invalid anyway (`::` must stand for at least one group, so eight explicit groups plus `::` is nine). A correct parser rejects "`::` present and more than seven explicit groups" and "no `::` and not exactly eight".

IPv4-mapped literals become plain dotted IPv4, so `Url.host` for `[::ffff:127.0.0.1]` is `127.0.0.1` (the outbound policy relies on this; `research/outbound-url-policy.md` F2). The mapped form is the only embedding OkHttp collapses.

### F7. Ports (source-read + measured)

`parsePort` canonicalises the port text with an empty encode set (so any control, space or non-ASCII character becomes a `%XX` and the next step fails), calls `toInt()` and accepts `1..65535`. An empty port (`h:` or `h:/`) falls into the "no port" branch and uses the default. The default port is dropped from the canonical text.

| Input | `HttpUrl` reading |
|---|---|
| `:80` on `http`, `:443` on `https` | port kept in `HttpUrl.port`, dropped from `toString()` |
| `https://h:80/`, `http://h:443/` | kept in `toString()` (`https://h:80/`) |
| `:0080`, `:08`, `:00000000000000000080` | 80, 8, 80 (leading zeros accepted) |
| `:`, `:/p` | default port |
| `:65535` / `:65536` / `:0` / `:99999999999` / `:2147483648` | accepted / rejected / rejected / rejected / rejected |
| `:-1`, `:-0`, `:abc`, `:0x50`, `:8 0`, `:80:80`, `:8%30`, `:1_0`, fullwidth `８０`, Arabic-Indic `٣` | rejected |
| **`:+80`** | **accepted, port 80** (Kotlin `"+80".toInt()`); `:+0` and `:+` rejected |

**Recommended:** digits only (`0-9`, ASCII), leading zeros accepted, more than five significant digits rejected, value `1..65535`; an empty port is the default; a sign is rejected (the one place `HttpUrl` is more lenient than any standard).

### F8. Paths (source-read + measured)

Splitting: on `/` and `\` (backslash is rejected here). Empty segments are preserved (`//a//b`). Each segment is canonicalised with `alreadyEncoded = true`, then dot segments are resolved with a stack that never pops above the root.

- Dot segment: `.` or `%2e` (any case). Double-dot: `..`, `%2e.`, `.%2e`, `%2e%2e` (any case) (`isDot` / `isDotDot`). `%2e%2e%2e`, `...`, `..;`, `.a`, `a.` and `%252e` are ordinary segments. WHATWG lists the same four double-dot forms.
- Resolution (measured): `/a/./b` gives `/a/b`; `/a/../b` gives `/b`; `/..` and `/../..` give `/`; `/a/b/.` gives `/a/b/`; `/a/b/..` gives `/a/`; `/a/.//b` gives `/a//b`; `/a/..//b` gives `//b`.
- Empty path becomes `/` (`http://h`, `http://h?q`, `http://h#f`).
- Percent-encode set (`PATH_SEGMENT_ENCODE_SET` in `-Url.kt`): space, `"`, `<`, `>`, `^`, backtick, `{`, `}`, `|`, `/`, `\`, `?`, `#`, plus every code point below `0x20`, `0x7F` and all non-ASCII. Measured against all 128 ASCII characters: of the printable ones only these are encoded (`/ \ ? #` are delimiters inside a parsed path); **raw** stay `! $ & ' ( ) * + , - . : ; = @ [ ] _ ~` and `%`. OkHttp's set is WHATWG's path set plus `|` (and the delimiters `/` and `\`), so `HttpUrl`, not WHATWG, is the model.
- Existing `%XX` is kept as typed: `%e9` stays lower-case, `%C3%A9` stays, `%41` is not decoded to `A`. A malformed `%` (`%`, `%z`, `%zz`, `%4`, `100%`, `%%41`) is kept verbatim (not encoded to `%25`).
- Non-ASCII is encoded as UTF-8 with **upper-case** hex (`é` gives `%C3%A9`, `日本語` gives `%E6%97%A5%E6%9C%AC%E8%AA%9E`, `😀` gives `%F0%9F%98%80`). An unpaired surrogate becomes `%3F` (Okio's `writeUtf8CodePoint`: "`// Emit a replacement character for a partial surrogate.`" then `writeByte('?'.code)`): silent data loss. The characters TAB, LF, FF and CR are deleted (F1); VT and NUL are encoded (`%0B`, `%00`); a space is `%20`.
- Non-ASCII whitespace and C1 controls are not special to `HttpUrl`: `U+00A0` becomes `%C2%A0`, `U+0085` becomes `%C2%85`, `U+2028` becomes `%E2%80%A8`, `U+3000` becomes `%E3%80%80`.

**Recommended:** because whitespace, controls and backslashes are rejected earlier, the segment encode set reduces to ``" < > ^ ` { | } `` plus non-ASCII; `%` is left untouched; dot segments are resolved by porting the same stack algorithm; unpaired surrogates are rejected. The exact rules are in the Recommendation.

### F9. Query and fragment (source-read + measured)

- Query: the text after the first `?` up to the first `#`. `QUERY_ENCODE_SET` is space, `"`, `'`, `<`, `>`, `#` plus controls, DEL and non-ASCII. Measured: `{ } | [ ] ^ `` ` `` \` `~ * + ! $ & ( ) , / : ; = ? @` stay raw; `' " < >` become `%27 %22 %3C %3E`; `%` is kept (`%zz`, `%41`, `%e9` unchanged). `+` stays `+` (`plusIsSpace` with `alreadyEncoded` writes `+`). `a=1&&b`, `&`, `=` and `a=b=c` round-trip through `HttpUrl`'s split-and-rejoin unchanged, so the query string needs no parsing into pairs.
- Empty query: `http://h?` and `http://h/?` give `encodedQuery == ""` and `toString()` `http://h/?` (documented: "This string may be null (for URLs with no query), empty (for URLs with an empty query) or non-empty"). So `""` and `null` are different and both survive.
- Fragment: everything after the first `#`. `HttpUrl` never rejects on fragment content (it is canonicalised with an empty encode set, `unicodeAllowed`), including unpaired surrogates, backslash, `%zz`. Dropping it can therefore never create a disagreement; the only reason a fragment can make `Url.parse` fail is the whole-text character check (F10).
- Query and fragment are searched in that order: `http://h#f?q` has fragment `f?q` and no query; `http://h?a#b?c` has query `a`.

### F10. Characters that no parser should be asked to agree on (measured)

For the text between trim and fragment, `HttpUrl` treats these as ordinary data or deletes them: TAB LF FF CR (deleted in path, query and fragment; rejected inside the host), backslash (a path separator, and an authority end: `http://evil.example\@127.0.0.1:80/` reads host `evil.example`, path `/@127.0.0.1:80/`), VT, NUL, other C0 controls and DEL (encoded as `%XX` in path, query and fragment), space (`%20`), `U+0080..U+00A0`, `U+1680`, `U+2000..U+200A`, `U+2028`, `U+2029`, `U+202F`, `U+205F`, `U+3000` (percent-encoded as UTF-8). Every other parser in the survey reads at least one of these differently (Ktor: F12; `java.net.URI`: F2 of the outbound-URL research). **Recommended:** one scan of the trimmed text, before any parsing, that rejects: `\`; any code unit `<= U+0020`, `U+007F..U+00A0`, and the Unicode `White_Space` code points `U+1680`, `U+2000..U+200A`, `U+2028`, `U+2029`, `U+202F`, `U+205F`, `U+3000` (the union of C0, DEL, C1 and `White_Space`, a property set that Unicode's stability policy freezes; checked against PropList 18.0.0); and any unpaired surrogate. The scan covers the fragment too, so `http://h/#a b` is rejected: one rule with no exceptions is easier to state and to test.

### F11. `HttpUrl`'s members, equality and `redact` (documented + source-read)

- `scheme`, `host` (IPv6 without brackets, KDoc table), `port` (effective, `1..65535`: "The explicitly-specified port if one was provided, or the default port for this URL's scheme"), `encodedPath` ("The returned path will start with `"/"`"), `encodedQuery: String?`, `encodedFragment: String?`, `encodedUsername`, `encodedPassword`, `pathSegments`, `queryParameter*`.
- `equals` is `other is HttpUrl && other.url == url` and `hashCode` is `url.hashCode()`: equality is on the canonical text, with no hex-case or unreserved normalisation (measured: `http://h/a%2fb != http://h/a%2Fb`, `http://h/%41 != http://h/A`, `http://H/ == http://h/`).
- `HttpUrl.redact()` returns scheme, host, port and the literal path `/...`: "Returns a string with containing this URL with its username, password, query, and fragment stripped, and its path replaced with `/...`." Its path-hiding is stricter than the required `redacted()`, which keeps the path.

### F12. Ktor 3.6.0's common `Url` (source-read + measured)

`Url` (`ktor-http/common`, tag `3.6.0`) holds `protocol`, `host`, `specifiedPort`/`port`, `rawSegments`/`segments`, `encodedPath`, `parameters`, `fragment`, `user`, `password`, `trailingQuery`; `toString()` returns the original-ish `urlString`; `equals` compares it. Its parser (`URLBuilder.takeFrom`) is a tolerant reader, not a canonicaliser:

| Input | Ktor `Url` |
|---|---|
| `HTTP://EXAMPLE.COM:80/a/./b/../c?x=1#frag` | host `EXAMPLE.COM` (not lower-cased), path `/a/./b/../c` (dots kept), fragment kept |
| `http://[::ffff:127.0.0.1]/` | host `[::ffff:127.0.0.1]` (OkHttp: `127.0.0.1`) |
| `http://%31%32%37.0.0.1/` | host `%31%32%37.0.0.1` (OkHttp: `127.0.0.1`) |
| `http://127.0.0.1%09.evil.example/` | host `127.0.0.1%09.evil.example` (OkHttp: rejected) |
| `http://evil.example\@127.0.0.1:80/` | host `evil.example`, path `/%5C@127.0.0.1:80/` (OkHttp: path `/@127.0.0.1:80/`) |
| `http:h`, `http:/h`, `/x`, `x` | host `localhost`, path `/h`, `/x`, `/x` (an absent host defaults to `localhost`) |
| `http://h:+80/` | port 80; `http://h:0/` accepted (port 0 is Ktor's "unspecified"); `http://h:99999/` throws |
| `http://h?` | `encodedQuery` `""`, `trailingQuery` true; with no query `encodedQuery` is also `""` |
| `http://h/\uD800` | throws `URLParserException` |
| `ftp://h/` | accepted (any scheme) |

Ktor's design goal is to represent and rebuild any URL a client may be handed, with `URLBuilder` for composition (`appendPathSegments(segments, encodeSlash)`, `encodedParameters`, `encodedFragment`). It is a different job from "one canonical form that must equal OkHttp's reading", and a validator that read Ktor's `Url` would see a different host from the one OkHttp connects to for at least the mapped-IPv6, percent-encoded and backslash rows above. Naming to borrow: `protocol`/`host`/`port`/`encodedPath`/`encodedQuery`/`fragment`. Choices not to borrow: `host` returned with brackets, an `""` that stands for both "no query" and "empty query", and port 0 as a sentinel.

### F13. What the standards say, and where `HttpUrl` departs (documented)

- RFC 3986 §6.2.2 (syntax-based normalisation): lower-case scheme and host (OkHttp does), upper-case hex in percent-encodings ("For consistency, URI producers and normalizers should use uppercase hexadecimal digits for all percent-encodings.": OkHttp does for what it **creates**, but never rewrites an existing `%e9`), decode escapes of unreserved characters (**OkHttp does not**: `%41` is kept), and remove dot segments (OkHttp does, including the `%2e` forms RFC 3986 does not mention). §3.2.3: "URI producers and normalizers should omit the port component and its ":" delimiter if port is empty or if its value would be the same as that of the scheme's default." (OkHttp does.) §3.2.2: "URI producing applications must not use percent-encoding in host unless it is used to represent a UTF-8 character sequence." (A percent-encoded host is the hazard the requirement rejects.)
- RFC 5952 §4.2.2, §4.2.3, §4.3: "The symbol "::" MUST NOT be used to shorten just one 16-bit 0 field."; "the first sequence of zero bits MUST be shortened"; hex letters lower-case. `inet6AddressToAscii` implements these.
- WHATWG URL Standard (living standard, read 2026-10): forbidden host code points include U+0000, TAB, LF, CR, space, `#`, `/`, `:`, `<`, `>`, `?`, `@`, `[`, `\`, `]`, `^`, `|`; hosts ending in a number are IPv4-parsed (so `127.1` is `127.0.0.1` in a browser but stays `127.1` in OkHttp); leading and trailing C0-control-or-space and all TAB/LF/CR are stripped from the input (OkHttp strips only five characters at the ends and deletes only inside path, query and fragment).
- Hence: OkHttp is neither RFC 3986's normaliser nor WHATWG's parser. It borrows from both, and the oracle for "reads the same" is OkHttp itself, which is what the differential test uses.

### F14. Parser-disagreement bugs in comparable libraries (documented consensus + measured)

- Orange Tsai (Black Hat USA 2017) and the OWASP rule "Treat parser disagreement as a rejection" are recorded in `docs/plans/55-http-client-stack/research/outbound-url-policy.md` F3 and F10.
- Team82 (Claroty) and Snyk examined 16 URL parsing libraries and found five classes of inconsistency: "scheme confusion, slashes confusion, backslash confusion, URL encoded data confusion, and scheme mixup" (<https://claroty.com/team82/research/exploiting-url-parsing-confusion>). Four of the five are rows of F2 to F10 (slashes, backslash, percent-encoded data, scheme).
- In OkHttp itself, measured on 5.5.0: the uncaught `ArrayIndexOutOfBoundsException` (F6); the silent deletion of TAB/LF/FF/CR in a path (F1); the `+80` port (F7); `xn--abc-` becoming `abc` (F5); an unpaired surrogate becoming `%3F` (F8).
- In implementations of "case-insensitive scheme match": Kotlin's `startsWith(ignoreCase = true)` folds `ſ` to `s`; a Kotlin reimplementation must compare ASCII letters by hand (F2, F15).

### F15. A prototype of the recommended algorithm (measured)

A prototype `Url` (stdlib only; 378 lines including comments and the four mutation switches, 317 non-blank non-comment lines; of those about 90 are the host and IPv6 code and about 50 the two encoders) was built in a scratch probe project and run through the Level 4a assertion in the Recommendation:

| Run | Inputs | `Url` accepted | `HttpUrl` rejected (null) | `HttpUrl` threw (all `ArrayIndexOutOfBounds`) | `HttpUrl` accepted, `Url` rejected | Disagreements |
|---|---|---|---|---|---|---|
| seed 7 | 300,944 | 106,056 | 160,403 | 542 | 33,943 | **0** |
| seed 101 | 2,000,944 | 705,500 | 1,065,472 | 3,849 | 226,123 | **0** |
| seed 202 | 2,000,944 | 705,490 | 1,064,240 | 3,852 | 227,362 | **0** |

Each run is the 944-string hand corpus (the F2/F3 confusion strings of the outbound-URL research, ordinary API URLs, and the tables above) plus the generated inputs. Where the prototype is stricter, the rejections by kind in the seed 101 run were: scheme or slash form 49,630; whitespace or control 46,549; backslash 46,321; host syntax 41,550; host encoding 17,679; not Unicode 12,148; user-info 8,020; `xn--` 3,933; port 293.

Generator sensitivity, by mutation: the harness was run against four deliberately broken prototypes, each caught within 300,000 inputs. (1) `startsWith(ignoreCase = true)` for the scheme: caught as "accepted but `HttpUrl` rejects" on `httpſ://[::1]`. (2) `xn--` labels accepted as written: caught on `xn--abc-` (`abc`). (3) `::` allowed with eight groups: caught on `[1:2:3:4:5:6:7::8]` (`HttpUrl` throws). (4) `.%2e` removed from the double-dot forms: caught on `/a/.%2e/b`. An encoder differential (internal `encodePathSegment` and `encodeQueryComponent` against `HttpUrl.Builder.addPathSegment` and `addQueryParameter`, 200,000 random strings over all 128 ASCII characters, the Unicode pool and unpaired surrogates) had 0 mismatches.

Compile and portability: the same file compiled for `js(IR)` with Kotlin Gradle plugin 2.2.0 (`compileDevelopmentExecutableKotlinJs`, no Node setup task in the task graph) and, run on Node v24.19.0, printed output identical to the JVM for 20 sample inputs (including the `ſ`, surrogate and IPv6 ones). On the JVM, `Url.Companion.parse` compiles to `parse-IoAF18A` (the `Result` return mangles the name; `research/http-transport.md` F11 records the same effect for `execute-gIAlu-s`).

### F16. The builder encoders `HttpUrl.Builder.addPathSegment` and `addQueryParameter` (source-read + measured)

`addPathSegment(s)` runs `push(s, 0, s.length, addTrailingSlash = false, alreadyEncoded = false)`; `addQueryParameter(n, v)` runs `canonicalize(encodeSet = QUERY_COMPONENT_ENCODE_SET, plusIsSpace = true)` on name and value (not `alreadyEncoded`, so `%` is encoded).

Measured over all 128 ASCII characters:

| Encoder | Left raw | Percent-encoded |
|---|---|---|
| path segment | ``A-Za-z0-9`` and ``! $ & ' ( ) * + , - . : ; = @ [ ] _ ~`` | controls `0x00..0x1F`, DEL, space, ``" # % / < > ? \ ^ ` { | } `` (all as `%XX`, upper-case hex), and every non-ASCII code point as UTF-8 |
| query name or value | `A-Za-z0-9`, `*`, `-`, `.`, `_` | **everything else**, including space (`%20`), `+` (`%2B`), `%` (`%25`), `~` (`%7E`), `!`, `'`, `(`, `)`, `,`, `/`, `:`, `;`, `=`, `?`, `@`, `[`, `]` |

Behaviours that matter to a facade:

- `addPathSegment(".")` appends nothing and `addPathSegment("..")` **pops the previous segment** (`http://h/api` plus `..` gives `http://h/`); `%2e` is encoded to `%252e` and is therefore harmless. `setPathSegment` throws for `.`/`..` but `addPathSegment` is silent. A facade must refuse `.` and `..` itself.
- `addPathSegment("")` is not an empty segment: it adds a trailing slash after a non-empty segment and is swallowed otherwise. `http://h/api` plus `["x", "", "y"]` gives `http://h/api/x/y` (the empty middle segment vanishes), plus `["", ""]` gives `http://h/api/`. Assembling by text would give `x//y`.
- Slash inside a segment is `%2F` (not a separator); `?` is `%3F`; `#` is `%23`; `+` stays `+`.
- Unpaired surrogates become `%3F`.
- A base URL's query is kept when path segments are added (`http://h/a?q=1` plus `b` plus `k=v` gives `http://h/a/b?q=1&k=v`).

## How comparable projects do it

| Project (ref) | Type | Parse and canonicalisation | Relevance |
|---|---|---|---|
| **OkHttp** 5.5.0 `okhttp3.HttpUrl` (`okhttp/src/commonJvmAndroid/kotlin/okhttp3/HttpUrl.kt`) | `HttpUrl` with `HttpUrl.Builder.parse`; `String.toHttpUrlOrNull()`; `Builder.addPathSegment`, `addQueryParameter` | One lenient pass that canonicalises as it reads (F1 to F9). JVM and Android only (`commonJvmAndroid`), so not usable from common code. | The oracle. Components `scheme`, `host`, `port`, `encodedPath`, `encodedQuery`, `encodedFragment`; equality on canonical text. |
| **Ktor** 3.6.0 common `io.ktor.http.Url`, `URLBuilder.takeFrom` | multiplatform `Url`, `URLBuilder` | Tolerant reader; no host canonicalisation, no dot-segment resolution (F12). | Naming comparison; shows a common-code `Url` that is not a canonical form. |
| **WHATWG URL Standard** | spec; browsers | Strips ends and deletes TAB/LF/CR, special-scheme slash tolerance, IPv4 number forms, forbidden host code points (F13). | The reference for "what browsers read". OkHttp follows it in several places, not all. |
| **RFC 3986** | spec | Generic syntax and syntax-based normalisation (F13). | The reference for the encode sets' intent; OkHttp does not decode unreserved escapes. |
| **`java.net.URI` / `java.net.URL`** | JDK | Return no host or a different host from OkHttp for 7 of 23 confusion strings and refuse 3 more that OkHttp accepts (`research/outbound-url-policy.md` F3). | Why a JVM `URI` is not the model for a type shared with OkHttp. |
| **`com.eygraber:uri-kmp`** 0.0.21 | KMP `Uri` | "Most of this work is derived from AOSP's `Uri`" (its README): an Android `Uri` parser, not OkHttp's. | Shows the shape of a KMP `Uri`; not canonical in OkHttp's sense. |
| **Apollo Kotlin** 4.4.1, **Coil 3** | `HttpRequest.url: String`, `NetworkRequest.url: String` | None: the request carries a string and the engine parses it (`research/http-transport.md` "How comparable projects do it"). | The alternative this design rejects: a string defers parsing to the transport, which is exactly where the validator and the connector would disagree. |

## Pitfalls

1. **Using Kotlin's case-insensitive `startsWith`/`equals` on the scheme or on `%2e`.** It folds Unicode (`ſ` to `s`, `K` KELVIN SIGN to `k`). Compare ASCII letters by hand; `HttpUrl` rejects a non-ASCII scheme letter (F2). The generator must put such characters in the scheme position.
2. **Lower-casing the host with `lowercase()`.** `U+0130` lower-cases to two code points. The host is ASCII-only by then, so lower-case by hand over `A..Z`.
3. **Copying OkHttp's IPv6 decoder without its bounds check.** The copy inherits the `ArrayIndexOutOfBoundsException` (F6), which is a crash on untrusted text, not a rejection. Write the decoder over split parts (as in the Recommendation) or add the check.
4. **Treating "`HttpUrl` returns null" as the only rejection in the differential test.** An uncaught `ArrayIndexOutOfBoundsException` aborts the test run with an exception instead of counting a rejection. Catch `Throwable` around the `HttpUrl` call and treat it as a rejection; do not catch around `Url.parse`, which must never throw.
5. **Normalising what `HttpUrl` leaves alone.** Upper-casing existing `%e9`, decoding `%41`, or encoding a lone `%` makes `encodedPath` differ from `HttpUrl.encodedPath`, so the differential fails. The equal-bytes definition of equality (`%2f` is not `%2F`) is the price. Cache keys built from `toString()` can hold two entries for one resource; the facades' cache key uses `encodedPath` and sorted parameters, and the encoders only ever emit upper-case hex, so URLs the library builds are uniform.
6. **Leaving user-info rejection to the host stage.** `http://u:p@h/` reads host `h`; the credential goes nowhere useful and the URL looks safe. Reject the `@` itself (F3).
7. **Echoing the input.** A message that quotes the URL leaks `api_key=...`. The messages are constants; a test passes a canary through every failure kind.
8. **`toString()` carries the query.** Any `"$url"` in a log line, exception message or `Result` payload leaks it; only `redacted()` is for logs. A Level 2 test over failure messages and a code-review rule ("log `redacted()`, never the `Url`") are the only guard. `redacted()` shows the **path**, which some APIs use for secrets (a bot token or webhook id in the path); `HttpUrl.redact()` hides it (F11).
9. **Port 0 and the `+` sign.** `HttpUrl` rejects `0` but accepts `+80`; a hand parser that calls `toInt()` inherits the sign.
10. **`Result` in a public signature mangles the JVM name** (`parse-IoAF18A`); Java callers cannot call it by name. The repository already accepts this for `HttpMethod.of` and `CidrBlock.parse` (`research/api-shape-and-failures.md` "Inputs and parsing").
11. **Assembling a URL from parts by joining strings.** The facades must use the internal encoders (F16) and then `Url.parse`; `HttpUrl.Builder.addPathSegment` drops empty and dot segments silently and `..` pops.

## Recommendation for this repository

Build `Url` as a hand-written, `final`, plain common class whose `parse` ports `HttpUrl`'s algorithm stage by stage, adds a small set of rejections that can only make it stricter, and is tested against `HttpUrl` by a seeded differential in `jvmTest`. Reject the alternatives: a JVM `actual typealias` to `HttpUrl` puts an OkHttp type in a public signature; wrapping `java.net.URI` reads hosts differently from OkHttp; a `String`-typed request defers the disagreement to the transport.

### Shape (`commonMain`, package `com.spartanlabs.webtools.http`, `explicitApi()`)

```kotlin
public class Url private constructor(
    public val scheme: String,        // "http" or "https"
    public val host: String,          // canonical; IPv6 without brackets (as HttpUrl.host); IPv4-mapped becomes dotted IPv4
    public val port: Int,             // effective port, 1..65535
    public val encodedPath: String,   // always starts with "/"
    public val encodedQuery: String?, // null: no "?"; "": bare "?"
) {
    public fun redacted(): String
    override fun toString(): String   // canonical URL, query included
    override fun equals(other: Any?): Boolean   // canonical text
    override fun hashCode(): Int
    public companion object { public fun parse(text: String): Result<Url> }
}
```

No `data class` (the `Not data` convention); the primary constructor is private so every instance is canonical; the canonical text is computed once in the constructor. A failure is `Result.failure(IllegalArgumentException(constantMessage))` created by an internal subclass `UrlParseException(problem)` so tests can assert the kind and callers see a plain `IllegalArgumentException`. Function decomposition follows the repository's `Result` idiom (`webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19`, `flatMap`): `parse` is a chain of small pure functions, each returning a value or a `Result`, with `getOrElse { return Result.failure(it) }` or `flatMap` at the joints; no function throws.

Suggested files: `Url.kt` (class, `parse`, `redacted`), `UrlHost.kt` (internal host and IPv6 functions), `UrlEncoding.kt` (internal canonicalisers, encoders, dot-segment fold), `UrlParseException.kt` (internal `UrlProblem` and the exception).

### The parse algorithm

Let `isUrlSpace(c)` be `c <= U+0020`, or `U+007F..U+00A0`, or `U+1680`, `U+2000..U+200A`, `U+2028`, `U+2029`, `U+202F`, `U+205F`, `U+3000`.

1. **Trim** `\t \n \u000C \r` and space from both ends (the exact set `HttpUrl` trims). If nothing is left: `Empty`.
2. **Scan once** left to right over the trimmed text; the first offending character decides the kind: `\` gives `Backslash`; `isUrlSpace(c)` gives `WhitespaceOrControl`; a high surrogate not followed by a low surrogate, or a low surrogate not preceded by one, gives `NotUnicode`.
3. **Scheme.** The text must start with `https://` or `http://`, letters compared ASCII case-insensitively by hand. Otherwise: `Scheme`. The scheme is stored lower-case.
4. **Authority** is the text from after `://` up to the first `/`, `?` or `#` (or the end). If it contains `@`: `UserInfo`.
5. **Split host and port.** If the authority starts with `[`, find the first `]` (none: `Host`); what follows must be empty or `:` and the port text. Otherwise split at the first `:`; the rest after it is the port text (a second `:` makes it non-digit, so `Port`).
6. **Port.** Absent or empty: the scheme's default (80 / 443). Otherwise ASCII digits only, strip leading zeros, more than five digits is out of range, value `1..65535`; else `Port`.
7. **Host.**
   - `[...]`: the IPv6 rules below; failure is `Host`.
   - Otherwise: any `%` or code point `>= 0x80` gives `HostEncoding`; lower-case `A..Z` by hand; every character must be `a-z 0-9 - _ .` (else `Host`); length `1..253` (counting a trailing dot); drop one trailing dot for the label check; each label `1..63` characters (else `Host`); a label that starts with `xn--` gives `HostIdn`. Digits-and-dots text is a host like any other and is kept as written.
8. **Split the rest** (from the end of the authority): everything from the first `#` is dropped; in what remains, the first `?` starts the query. Path text is up to the `?`, query text is after it (`null` if there was no `?`).
9. **Path.** Empty becomes `/`. Otherwise split the text after the leading `/` on `/` into segments; canonicalise each (encode set below); then fold with the stack below. Join as `"/" + segment` for each stack element.
10. **Query.** If present, canonicalise (encode set below). It is not split into pairs.
11. **Result.** `Url(scheme, host, port, path, query)`.

Dot-segment fold (a faithful port of `HttpUrl.Builder.push`/`pop`; the stack starts as `[""]`, and for segment *k* of *n* `trailing = k < n-1`):

```
dot            -> nothing
double-dot     -> removed = stack.removeLast();
                  if (removed.isEmpty() && stack.isNotEmpty()) stack[last] = "" else stack.add("")
otherwise      -> if (stack.last().isEmpty()) stack[last] = seg else stack.add(seg);
                  if (trailing) stack.add("")
```

with dot = `.` or `%2e` and double-dot = `..`, `%2e.`, `.%2e`, `%2e%2e`, all ASCII case-insensitive, tested on the canonicalised segment. A local `ArrayList` is fine (it never escapes).

IPv6 decoding over the text inside the brackets (independent of OkHttp's loop, so it cannot inherit the bounds bug):

1. It must contain `:`.
2. Find the first `::`. With one: `head` is the text before it split on `:` (empty if it starts the text), `tail` is the text after it split on `:` (empty if it ends the text). Without: `head` is the whole text split on `:`, `tail` is absent.
3. Every part must be 1 to 4 hex digits, except that the **last part of the address** (last of `tail`, or of `head` when there is no `::`) may be a dotted IPv4 quad: four decimal parts, each 1 to 3 digits, no leading zero unless it is `0`, each at most 255; it counts as two groups. An empty part (from `:::`, a trailing `:`, a second `::`) fails.
4. Total groups must be exactly 8 without `::`, and at most 7 with `::`. Zero groups fill the gap.
5. Print: if groups 0 to 4 are zero and group 5 is `ffff`, print groups 6 and 7 as dotted IPv4 (no brackets in `host`). Otherwise print lower-case hex without leading zeros, compress the first longest run of at least two zero groups to `::`, join the rest with `:`.

### Encode sets, written out

All percent-encoding is UTF-8 bytes in upper-case hex. `%` is never touched when parsing. The scan in step 2 has already removed controls, space, DEL and backslash, so the parse-time sets are short.

```
parse, one path segment : "  <  >  ^  `  {  |  }   and every non-ASCII code point
parse, query            : "  '  <  >                and every non-ASCII code point
parse, fragment         : (dropped; never encoded)
```

The full `HttpUrl` sets, for reference and for the encoders:

```
PATH_SEGMENT_ENCODE_SET  = space " < > ^ ` { } | / \ ? #   + controls < 0x20, DEL, non-ASCII
QUERY_ENCODE_SET         = space " ' < > #                  + controls < 0x20, DEL, non-ASCII
QUERY_COMPONENT (builder)= everything except A-Z a-z 0-9 * - . _
```

### Canonical-form rules

- Scheme and host lower-case; IPv6 in RFC 5952 form; IPv4-mapped IPv6 becomes dotted IPv4; digits-and-dots hosts and a trailing dot kept as written.
- The default port is dropped from `toString()`; `port` always holds the effective port.
- Empty path is `/`; `.`/`..` segments resolved (never above the root); empty segments kept.
- Existing `%XX` kept exactly, a malformed `%` kept; non-ASCII becomes UTF-8 `%XX` in upper-case hex.
- The query is kept as typed except for the query encode set; `+` stays; `""` and `null` are different.
- The fragment is dropped; there is never user-info.
- `toString()` is `scheme://host[:port]path[?query]` with `[`brackets`]` around a host that contains `:`. Re-parsing `toString()` yields an equal `Url` (idempotent; asserted by the test).

### `redacted()`, equality, `toString()`

- `redacted()` is `toString()` with `?...` in place of `?` plus the query, present exactly when `encodedQuery != null` (including a bare `?`, so that "had a query" is visible and a log reader cannot mistake a missing query for an empty one). The port is omitted when it is the default, as in `toString()`. The marker `?...` is ASCII, needs no escaping in log systems, mirrors OkHttp's `/...` and the `?...` already used in `research/http-transport.md` F10. The output is for logs: it is not meant to be parsed back (parsing `?...` would read a query of `...`).
- `equals` and `hashCode` are on the canonical text (a `String` field), so `HTTP://H:80/a/../b#x` equals `http://h/b`, while `%2f` and `%2F`, `%41` and `A`, `127.1` and `127.0.0.1`, `h` and `h.` are different URLs.
- `toString()` is the full canonical URL. KDoc must warn: "contains the query; use `redacted()` for logs".

### Failure kinds and their constant messages

| Kind | Message | Cause |
|---|---|---|
| `Empty` | `The URL is empty.` | nothing left after trimming |
| `Backslash` | `The URL contains a backslash.` | `\` anywhere |
| `WhitespaceOrControl` | `The URL contains whitespace or a control character.` | `isUrlSpace` anywhere in the trimmed text |
| `NotUnicode` | `The URL contains text that is not valid Unicode.` | unpaired surrogate |
| `Scheme` | `The URL must begin with http:// or https://.` | other scheme, no scheme, wrong slash count |
| `UserInfo` | `The URL contains user-info.` | `@` in the authority |
| `HostEncoding` | `The URL host must be plain ASCII, without percent-encoding.` | `%` or non-ASCII in the host |
| `HostIdn` | `The URL host contains an internationalised (xn--) label.` | a label starting `xn--` |
| `Host` | `The URL host is not a valid host name or IP address literal.` | empty host, bad character, bad label or length, bad IPv6 |
| `Port` | `The URL port must be a number from 1 to 65535.` | non-digit, sign, zero, out of range |

The encoders add `DotSegment` (`A path segment must not be "." or "..".`) and reuse `NotUnicode`. Messages never include any part of the input. The first offending character in step 2 decides between `Backslash`, `WhitespaceOrControl` and `NotUnicode`; steps after it run in the order above.

### The `internal` encoders for the facades

```kotlin
internal fun encodePathSegment(segment: String): Result<String>       // = HttpUrl.Builder.addPathSegment's output for one segment
internal fun encodeQueryComponent(component: String): Result<String>  // = addQueryParameter's output for one name or value
```

- `encodePathSegment` percent-encodes the `PATH_SEGMENT_ENCODE_SET` above (controls, DEL, space, ``" # % / < > ? \ ^ ` { | } ``, non-ASCII as UTF-8) and **fails with `DotSegment` for `.` and `..`** (OkHttp drops or pops them). It fails with `NotUnicode` for an unpaired surrogate (OkHttp writes `?`). `%2e` becomes `%252e`, which is safe.
- `encodeQueryComponent` leaves only `A-Za-z0-9*-._` raw and percent-encodes everything else (space `%20`, `+` `%2B`, `%` `%25`, `~` `%7E`), failing on an unpaired surrogate.
- Building a URL is then: take the base `Url`'s `toString()` without its query, append `/` plus each encoded segment (normalise a trailing `/` on the base so there is exactly one separator), append `?` plus `name=value` pairs joined with `&` (a pair with a null value is `name`), and pass the text to `Url.parse`. One parser and one canonical form decide the result, so the transport's conversion to `HttpUrl` is a re-read of the canonical text.
- Left to the facade unit: empty segments. `HttpUrl.Builder` collapses them (`x`, ``, `y` gives `x/y`), joining by text keeps them (`x//y`); a facade that treats an empty segment in `CallSpec` as an `InvalidRequest` avoids the question.

### The Level 4a differential test

Placement: corpus lists in `commonTest` (`testing/support/`) so Level 2 tests of `Url` alone can use them without OkHttp; the differential in `jvmTest` at `.../testing/deterministic/` (`UrlOkHttpDifferentialTest`, `UrlEncoderDifferentialTest`); `HttpUrl` is available there through `okhttp-jvm`.

**The assertion**, for every input `s` (the raw string, passed untrimmed to both parsers because they trim the same five characters):

1. `mine = Url.parse(s)`, **not** wrapped in `try`: a throw is a failure.
2. `theirs = try { s.toHttpUrlOrNull() } catch (t: Throwable) { null }`: `HttpUrl` rejecting by returning null or by throwing (the known `ArrayIndexOutOfBoundsException`) both mean "rejects".
3. If `mine` failed: nothing more to check (stricter is allowed); count it by kind.
4. If `mine` succeeded: `theirs` must not be null ("nothing `HttpUrl` rejects is accepted"), and
   - `u.scheme == theirs.scheme`, `u.host == theirs.host`, `u.port == theirs.port`, `u.encodedPath == theirs.encodedPath`, `u.encodedQuery == theirs.encodedQuery`;
   - `theirs.encodedUsername` and `encodedPassword` are empty;
   - `u.toString() == theirs.newBuilder().fragment(null).build().toString()` (the fragment is the only part `Url` drops, so it is removed from the oracle rather than from `Url`);
   - `Url.parse(u.toString())` succeeds and equals `u`;
   - `u.redacted()` equals `u.toString()` when `encodedQuery` is null, and otherwise ends with `?...` and has the length of `toString()` minus the query plus 3.

Trimming needs no special handling beyond passing the raw string; a separate explicit test covers the trim set (each of TAB, LF, FF, CR, space at either end, and that VT, NUL, NBSP at either end fail).

**The corpus** (three lists in `commonTest`, each entry with its expected canonical text or failure kind, plus the differential run over all of them):

- *Confusion strings* (F2/F3 of the outbound-URL research, 50 strings in the probe corpus): `http://evil.example\@127.0.0.1:80/`, `http://127.0.0.1:80\@evil.example/`, `http://127.0.0.1。evil.example/`, `http://１２７.0.0.1/`, `http://127.0.0.1%09.evil.example/`, `http://localhost%2e/`, `http://127.1/`, `http://0x7f.1/`, `http://127.0.0.1#@evil.example/`, `http://evil.example#@127.0.0.1/`, `http://evil.example?@127.0.0.1/`, `http://[::ffff:127.0.0.1]/`, `http://%31%32%37.0.0.1/`, `http://ｌｏｃａｌｈｏｓｔ/`, `http://ⓛocalhost/`, `http://localhost./`, `:0`, `:65536`, `:80:80`, `/%2e%2e/`, `http://foo@evil.com:80@google.com/`, `http://google.com#@evil.com/`, `http://[::1]%25eth0/`, `http://169.254.169.254/`, `http://[::ffff:a9fe:a9fe]/`, `http://example.com／@127.0.0.1/` (fullwidth solidus), and the slash, scheme and `xn--` rows of F2 to F5.
- *Must accept* (expected canonical text; 32 probe entries, all pass), for example: `https://api.opendota.com/api/matches/123?api_key=abc123` (unchanged); `https://api.opendota.com/api/players/1/matches?limit=20&offset=0&win=1` (unchanged); ``HTTP://EXAMPLE.COM:80/a/./b/../c?x=1#frag`` gives `http://example.com/a/c?x=1`; `https://example.com` gives `https://example.com/`; `https://example.com/?` unchanged; `https://example.com/päth/日本?q=ü` gives `https://example.com/p%C3%A4th/%E6%97%A5%E6%9C%AC?q=%C3%BC`; `https://example.com/a%2Fb%2fc` unchanged; `https://[2001:db8::1]:8443/a` unchanged; `http://[::ffff:127.0.0.1]/` gives `http://127.0.0.1/`; `http://[0:0:0:0:0:0:0:1]:3000/health` gives `http://[::1]:3000/health`; `http://[2001:0:0:1:0:0:0:1]/` gives `http://[2001:0:0:1::1]/`; `http://127.1/` unchanged; `https://example.com:443/` and `:0443` drop the port; `http://h:/p` gives `http://h/p`; `  https://example.com/x \n` gives `https://example.com/x`; `https://example.com/?filter[name]=x&sort=-date`, `?b={c}|d` (braces and pipe raw in a query) and `https://example.com/{x}|` (`%7Bx%7D%7C`) as set examples; `?q='x'` gives `?q=%27x%27`; `http://localhost./`; `https://exa_mple.com/`; `https://example.com/%zz` unchanged; `https://example.com/a/%2e%2E/b` gives `/b`; `https://example.com/.%2e` gives `/`; `//a//b` kept; `https://example.com/#only-fragment` gives `https://example.com/`.
- *Must reject* (expected kind; 79 probe entries, all pass): `""`/blanks (`Empty`); `example.com/x`, `//example.com`, `ftp://h/`, `file:///etc/passwd`, `javascript:alert(1)`, `http:/h`, `http:h`, `httpſ://h/`, `https` + KELVIN SIGN `K` + `://h/` (`Scheme`); `http:///h`, `http://`, `http://a..b/`, `http://.a/`, `http://a!b/`, `http://a"b/`, `http://a,b/`, 64-character label, 254-character host, `http://[::1/`, `http://::1/`, `http://[1:2:3:4:5:6:7::8]/`, `http://[::1]x/`, `http://[g::1]/`, `http://[]/`, `http://[1.2.3.4]/`, `http://[::1.2.3.256]/`, `http://[12345::]/`, `http://[fe80::1%25eth0]/` (`Host`); `u@h`, `u:p@h`, `@h`, `:@h`, `h@/`, `a@b@c` (`UserInfo`); `\` in the authority, path, query and fragment (`Backslash`); space, TAB, LF, NUL, VT, DEL, `U+0085`, `U+00A0`, `U+3000` in path, query, fragment or ends (`WhitespaceOrControl`); a lone `U+D800`, `U+DC00` in path, query, fragment (`NotUnicode`); `münchen.de`, `%31%32%37.0.0.1`, `127.0.0.1%09.evil.example`, `a%2eb`, fullwidth `ｌ`, `。`-separated and fullwidth-digit hosts (`HostEncoding`); `xn--n3h.net`, `XN--N3H.net`, `xn--abc-`, `a.xn--b.c` (`HostIdn`); `h:0`, `h:65536`, `h:+80`, `h:-1`, `h:8o`, `h:80:80`, fullwidth `８０`, `h:99999999999` (`Port`).
- *Boundaries*: host length 253 accepted and 254 rejected, with and without a trailing dot; label 63 accepted, 64 rejected; port 1, 65535 accepted, 0, 65536 rejected.
- *Known `HttpUrl` crash inputs*: `http://[1:2:3:4:5:6:7::8]/` and a few generated siblings must be rejected by `Url`.

**The generator** (`kotlin.random.Random(seed)`, fixed seeds, the seed printed in the failure message; about 0.5 s per 100,000 inputs against `HttpUrl`, so 200,000 in the build and an opt-in system property for a multi-million run):

- *Structured (about 70%)*, half "clean" (valid scheme, `://`, no user-info, valid host and port, so that deep path and query cases are reached), half "hostile": scheme from `http https HTTP HtTps ftp htps httpſ ＨＴＴＰ https`+`K`+`İttp` and empty; separator from `:// :/ : :/// ://// :\\ // :/\ ` and empty; user-info from `u@ u:p@ @ :@ a@b@ x:80@` or none; host from a pool (ordinary names, `127.1`, `0x7f.1`, `2130706433`, labels of 63 and 64, 250-character names, `xn--` forms, IDN, percent-encoded, fullwidth, every IPv6 form of F6 incl. the crash pattern) or a **random IPv6 generator** (random groups with zero runs, optional `::` at a random place, optional dotted IPv4 suffix with 10% out-of-range octets, mapped forms, random upper-case, random leading zeros); optional port from a valid list or `0..99999`, signs, empty; a path of 0 to 5 segments from a pool of `. .. %2e %2E .%2e %2e. %2e%2e a b %20 é 日本 😀 % %zz %41 %e9 {x} a|b ^ ` <> " ' [x] \uD800 ...a a.. ... ~ @ : & = + %2F %5C %252e 100%`; a query of 0 to 4 tokens from a pool containing every URL-significant character (`& = + % %zz é 😀 ' " < > { | } [ ] ^ ` \ space TAB # ? ~ * @ ; : / $ !` and unpaired surrogates); an optional fragment from the same pools; optional whitespace around (`space TAB LF CR LF FF VT NBSP`).
- *Mutation (about 20%)*: take a corpus string and apply 1 to 3 random inserts, deletes or replacements drawn from the URL-significant ASCII set (`/\?#@:[]%.&=+ -_~!$'()*,;<>^`{|}"`, TAB, LF, VT, NUL, DEL) and a Unicode pool (`é ß 日 本 😀 ☃ ſ K İ ＨＴＴＰ １ 。 ． ｡ ⓛ NBSP U+2028 U+3000 U+0085 ZWSP BOM`, lone surrogates, Arabic-Indic digits).
- *Raw random (about 10%)*: 0 to 30 characters drawn from the significant set, letters, digits, the Unicode pool, fragments like `xn--`, `%2e`, `::`, `/./`, `/../`, control characters, random BMP code units (surrogates included) and random supplementary code points, prefixed with `http://`, `https://`, `HTTP://` or `http:/` in 70% of cases.
- *Coverage floor* (so a generator regression cannot make the test vacuous): of the generated inputs, at least 25% accepted by `Url` (the probe saw 35%); at least 1,000 accepted inputs each containing `[`, a dot segment, `?`, a percent escape and a non-ASCII character; at least 100 inputs on which `HttpUrl` throws.

**Encoder differential** (`UrlEncoderDifferentialTest`): for 200,000 strings drawn from all 128 ASCII characters, the Unicode pool, `.`, `..`, `%2e`, `%41`, and concatenations: `encodePathSegment(s)` equals the single segment `HttpUrl.Builder().scheme("http").host("h").addPathSegment(s).build().encodedPath` minus its leading `/` (fails instead for `.`, `..`, and unpaired surrogates); `encodeQueryComponent(s)` equals the name part of `addQueryParameter(s, "v")` and the value part of `addQueryParameter("k", s)` (fails for unpaired surrogates).

## Considerations

### 1. Slash variants after the scheme (`http:host`, `http:/host`, `http:///host`)

- **Options:** (a) reject every form but `://`; (b) accept all three as `HttpUrl` does (all read `http://host/`).
- **Trade-off:** (b) is "exactly as `HttpUrl` does"; but RFC 3986 and `java.net.URI` read these as opaque or path-only URIs with no host, which is the "slashes confusion" class (F2, F14). (a) can never disagree and loses nothing a person types on purpose.
- **Recommendation:** (a). Reverting to (b) later is additive; the opposite is a breaking change.

### 2. Host character set

- **Options:** (a) letters, digits, `-`, `_`, `.` only; (b) everything `HttpUrl` accepts, which includes `! " $ & ' ( ) * + , ; < = > ^ ` { | } ~`.
- **Trade-off:** (b) is "no rejection beyond the listed ones"; (a) refuses hosts that cannot be DNS names and are hazardous in log lines and HTML (`"`, `<`, `>`, `` ` ``). A name with `_` is common (service names, `_dmarc`-style records), so it stays.
- **Recommendation:** (a).

### 3. Unicode whitespace and C1 controls in the path and query

- **Options:** (a) reject (`U+0080..U+00A0`, `U+1680`, `U+2000..U+200A`, `U+2028/9`, `U+202F`, `U+205F`, `U+3000`); (b) percent-encode as `HttpUrl` does.
- **Trade-off:** (b) accepts a pasted URL that holds a no-break space and sends `%C2%A0`, which a server reads as a different path from the one the reader sees; (a) refuses it with a clear message. Non-ASCII letters remain accepted either way.
- **Recommendation:** (a).

### 4. A malformed `%` escape (`%`, `%zz`, `100%`)

- **Options:** (a) keep verbatim, as `HttpUrl` and WHATWG do; (b) reject (`MalformedEscape`).
- **Trade-off:** (a) keeps a URL that a browser or `curl` would also send; servers differ on how they read it (400, literal, or partial decode). (b) is stricter and could never disagree, but turns a typo into a hard failure and adds an eleventh failure kind. It is not a host-confusion vector, because `%` is already refused in the host.
- **Recommendation:** (a) now; (b) is a one-condition addition in the path and query canonicalisers if a consumer wants it.

## Known gaps

- **Only OkHttp 5.5.0 was measured.** `HttpUrl.kt`, `-Url.kt` and `Punycode.kt` are byte-identical at 5.5.1 and `-HostnamesCommon.kt` on `master`; later releases were not read. A future OkHttp that fixes the IPv6 exception, or changes any canonicalisation, would show up as a differential failure and then needs a decision per change.
- **The differential is random coverage, not a proof.** It found no disagreement in 4.3 million inputs and caught four planted bugs; a disagreement that needs a pattern the pools cannot produce (a very long path, say) would escape. HttpUrl has no length limits (nothing in the source checks the URL length) and the prototype is linear; a 1 MB input was not run.
- **The prototype is not the shipped implementation.** The kind names and message wording are proposals. Its encoder for `.`/`..` reused a placeholder kind; the shipped one needs `DotSegment`.
- **Kotlin/JS was checked by compiling and running 20 samples on Node 24, not by running the differential or the corpus on JS.** `String.split`, `toString(16)`, `trimStart` and surrogate functions produced identical output on those samples; the full corpus on JS was not run (it needs the module and its test setup).
- **Ktor was measured on the JVM only** (3.6.0), through its common source; the Native and JS targets share that source but were not run.
- **Non-ASCII hosts were not explored beyond the table in F4** because they are refused; OkHttp's IDNA mapping table was not audited.
- **Facade assembly (empty segments, a base URL with a trailing slash, a query already present on the base) is sketched, not designed.** The unit that builds the facades decides; F16 lists what `HttpUrl.Builder` does for comparison.
- **Path-embedded secrets** appear in `redacted()` by requirement; no mitigation beyond documentation is proposed.
- **I did not look for open OkHttp issues about the IPv6 exception**; the repository search tool was unavailable for that query, so whether it is already reported or fixed on `master` beyond the file comparison above is unknown. This is a defect in a library outside Spartan Labs' organisations, so no issue is suggested here.
- **Probe environment:** Windows host, JDK 23 toolchain with `jvmTarget` 11, Node v24.19.0 already installed on the machine. The run times quoted (about 10 s for 2,000,000 inputs including Gradle start-up) are from this machine.

## Load-bearing claims

All sources were opened or measured on 2026-10-25. File links are at tag `parent-5.5.0` of <https://github.com/square/okhttp> unless stated. Measured claims give the setup so they can be re-run: a Kotlin/JVM application (Kotlin plugin 2.2.0, `jvmToolchain(23)`, `jvmTarget` 11) depending on `com.squareup.okhttp3:okhttp-jvm:5.5.0`, calling `import okhttp3.HttpUrl.Companion.toHttpUrlOrNull` on the input.

1. **`HttpUrl` trims exactly five ASCII characters at both ends.** "`'\t', '\n', '\u000C', '\r', ' ' -> Unit`" and, in `Builder.parse`, "`var pos = input.indexOfFirstNonAsciiWhitespace()`" / "`val limit = input.indexOfLastNonAsciiWhitespace(pos)`". `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-UtilCommon.kt` and `.../okhttp3/HttpUrl.kt`, 5.5.0. Measured: `"\u000Bhttp://a.com".toHttpUrlOrNull()` is `null`; `"\u000Chttp://a.com\u000C"` gives `http://a.com/`.
2. **Scheme letters must be ASCII and the match is case-insensitive.** "`in 'a'..'z', in 'A'..'Z', in '0'..'9', '+', '-', '.' -> continue@characters`" and "`input.startsWith("https:", ignoreCase = true, startIndex = pos) -> {`". `HttpUrl.kt`, 5.5.0. Measured: `"http\u017F://[::1]".toHttpUrlOrNull()` is `null`.
3. **The authority is read after any number of slashes, backslashes count, and the delimiters are `@ / \ ? #`.** "`val slashCount = input.slashCount(pos, limit)`", "`if (slashCount >= 2 || base == null || base.scheme != this.scheme) {`" and "`val componentDelimiterOffset = input.delimiterOffset("@/\\?#", pos, limit)`". `HttpUrl.kt`, 5.5.0. Measured: `http:a.com`, `http:/a.com`, `http:///a.com`, `http:\\a.com` all give `http://a.com/`; `http://@h/` gives `http://h/`; `http://h@/` is `null`.
4. **The host is percent-decoded, then canonicalised.** "`host = input.percentDecode(pos = pos, limit = portColonOffset).toCanonicalHost()`". `HttpUrl.kt`, 5.5.0. Measured: `http://%31%32%37.0.0.1/` gives host `127.0.0.1`.
5. **Host validity: ASCII range, forbidden characters, lengths.** "`if (c <= '\u001f' || c >= '\u007f') {`", "`if (" #%/:?@[\\]".indexOf(c) != -1) {`", "`if (length !in 1..253) return true`", "`if (labelLength !in 1..63) return true`", "`if (dot == length - 1) break // Trailing '.' is allowed.`". `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HostnamesCommon.kt`, 5.5.0. Measured: a 253-character host (with or without a trailing dot) accepted, 254 rejected; 63-character label accepted, 64 rejected; `http://a"b/`, `http://a<b/`, ``http://a`b/`` accepted.
6. **`xn--` labels are decoded and re-encoded, and an ASCII result drops the prefix.** "`val decoded = Punycode.decode(bufferA.readUtf8()) ?: return null`", "`return Punycode.encode(decoded)`" (`-HostnamesCommon.kt`), and "`if (!string.requiresEncode(pos, limit)) {` / `result.writeUtf8(string, pos, limit)`" (`okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/idn/Punycode.kt`), 5.5.0. Measured: `http://xn--abc-/` gives host `abc`; `http://xn--n3h-/` gives `n3h`; `http://abc.xn--abc-.com/` gives `abc.abc.com`; `http://xn--n3h.net/` gives `xn--n3h.net`.
7. **IPv6 text is printed per RFC 5952 and IPv4-mapped addresses become IPv4.** "`/** Encodes an IPv6 address in canonical form according to RFC 5952. */`", "`if (currentRunLength > longestRunLength && currentRunLength >= 4) {`", "`if (address.size == 4) return inet4AddressToAscii(address) // An IPv4-mapped IPv6 address.`". `-HostnamesCommon.kt`, 5.5.0. Measured: `http://[2001:db8:0:0:1:0:0:1]/` gives host `2001:db8::1:0:0:1`; `http://[2001:0:0:1:0:0:0:1]/` gives `2001:0:0:1::1`; `http://[::ffff:7f00:1]/` gives `127.0.0.1`; `http://[1:2:3:4:5:6:7::]/` gives `1:2:3:4:5:6:7:0`; `http://[::1.2.3.4]/` gives `::102:304`.
8. **`HttpUrl.host` has no brackets and `toString()` adds them.** "An IPv6 address, like `::1`. Note that there are no square braces." in the `host` KDoc, and "`// Host is an IPv6 address.`" / "`append('[')`" in `Builder.toString`. `HttpUrl.kt`, 5.5.0. Measured: `host` of `http://[::1]/` is `::1`, `toString()` is `http://[::1]/`.
9. **The IPv6 decoder can throw `ArrayIndexOutOfBoundsException`, which `toHttpUrlOrNull` does not catch.** In `decodeIpv6`: "`if (b == address.size) return null // Too many groups.`" at the top of the loop, then "`i += 2`" / "`b += 2`" / "`compress = b`" for `::`, then "`address[b++] = (value.ushr(8) and 0xff).toByte()`" with no re-check; and "`} catch (_: IllegalArgumentException) {`" in `toHttpUrlOrNull` (`HttpUrl.kt`). 5.5.0, unchanged on `master` for `-HostnamesCommon.kt` (compared 2026-10-22). Measured: `"http://[1:2:3:4:5:6:7::8]/".toHttpUrlOrNull()` throws `java.lang.ArrayIndexOutOfBoundsException`; the same builder call `HttpUrl.Builder().scheme("http").host("1:2:3:4:5:6:7::8")` also throws it.
10. **A zone identifier is rejected.** Measured: `http://[fe80::1%25eth0]/`, `http://[fe80::1%eth0]/` and `http://[fe80::1%]/` all give `null`. Source: the decoder accepts only hex digits, `:` and `.` (`parseHexDigit`, `decodeIpv4Suffix` in `-HostnamesCommon.kt`).
11. **Port parsing and the empty port.** "`val portString = input.canonicalize(pos = pos, limit = limit, encodeSet = "")`", "`val i = portString.toInt()`", "`if (i in 1..65535) i else -1`" and "`if (portColonOffset + 1 < componentDelimiterOffset) {`" (`HttpUrl.kt`), 5.5.0. Measured: `http://h:+80/` gives port 80; `http://h:0080/` gives 80; `http://h:/` gives 80; `http://h:0/`, `:65536`, `:-1`, `:+0` are `null`; `https://h:80/` keeps `:80` in `toString()`.
12. **Path and query encode sets.** "`internal const val PATH_SEGMENT_ENCODE_SET = " \"<>^`{}|/\\?#"`", "`internal const val QUERY_ENCODE_SET = " \"'<>#"`", "`internal const val QUERY_COMPONENT_ENCODE_SET = " !\"#$&'(),/:;<=>?@[]\\^`{|}~"`". `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/url/-Url.kt`, 5.5.0. Measured over all 128 ASCII characters (parse of `http://h/a<c>b` and `http://h/?a<c>b`, `addPathSegment`, `addQueryParameter("k", c)`): the raw and encoded sets listed in F8, F9 and F16.
13. **An existing `%` is left alone, malformed or not, and TAB/LF/FF/CR are deleted when `alreadyEncoded`.** "`codePoint == '%'.code &&`" / "`(!alreadyEncoded || strict && !input.isPercentEncoded(i, limit))`" and "`// Skip this character.`" in `writeCanonicalized`; KDoc "Tabs, newlines, form feeds and carriage returns are skipped." `-Url.kt`, 5.5.0. Measured: `http://h/p<TAB>q` gives `/pq`; `http://h/%zz` gives `/%zz`; `http://h/%e9` gives `/%e9`; `http://h/%41` gives `/%41`.
14. **Unpaired surrogates are written as `?` by Okio.** "`// Emit a replacement character for a partial surrogate.`" / "`writeByte('?'.code)`" in `Buffer.commonWriteUtf8CodePoint`, <https://github.com/square/okio/blob/parent-3.18.1/okio/src/commonMain/kotlin/okio/internal/Buffer.kt> (Okio 3.18.1, the version `gradle/libs.versions.toml` pins at `parent-5.5.0`). Measured: `http://h/\uD800` gives `/%3F`; `http://h?q=a\uDC00b` gives `q=a%3Fb`.
15. **Dot segments.** "`private fun isDot(input: String): Boolean = input == "." || input.equals("%2e", ignoreCase = true)`" and `isDotDot` = `".."`, `"%2e."`, `".%2e"`, `"%2e%2e"` (ignoring case); "`return // Skip '.' path segments.`". `HttpUrl.kt`, 5.5.0. Measured: `/a/%2E%2e/b` gives `/b`; `/a/.%2E/b` gives `/b`; `/%2e%2e%2e` stays; `/a/b/..` gives `/a/`; `/..` gives `/`.
16. **`addPathSegment` skips `.` and pops on `..` instead of failing, and `addQueryParameter` uses the component set.** "`push(pathSegment, 0, pathSegment.length, addTrailingSlash = false, alreadyEncoded = false)`" and "`/** Adds a path segment. If the input is ".." or equivalent, this pops a path segment. */`" (`HttpUrl.kt`). Measured (`"http://h/api".toHttpUrl().newBuilder().addPathSegment("..").build()` gives `http://h/`; `addPathSegment(".")` leaves `http://h/api`; `addQueryParameter("k", "a~b*c")` gives `k=a%7Eb*c`; `addPathSegment("")` twice after `/api` gives `/api/`; segments `x`, ``, `y` give `/api/x/y`).
17. **Query null versus empty, and equality on text.** "`This string may be null (for URLs with no query), empty (for URLs with an empty query) or non-empty (all other URLs).`" and "`override fun equals(other: Any?): Boolean = other is HttpUrl && other.url == url`". `HttpUrl.kt`, 5.5.0. Measured: `http://h/?` has `encodedQuery == ""`; `"http://h/a%2fb".toHttpUrl() == "http://h/a%2Fb".toHttpUrl()` is `false`; `"http://H/".toHttpUrl() == "http://h/".toHttpUrl()` is `true`.
18. **`HttpUrl.redact()` hides the path.** "`Returns a string with containing this URL with its username, password, query, and fragment stripped, and its path replaced with `/...`.`" `HttpUrl.kt`, 5.5.0.
19. **OkHttp 5.5.1 and `master` do not change these files.** Measured with `gh api` on `parent-5.5.1`: `HttpUrl.kt`, `internal/url/-Url.kt` and `internal/idn/Punycode.kt` are identical to `parent-5.5.0`; `internal/-HostnamesCommon.kt` on `master` (2026-10-22) is identical to `parent-5.5.0`.
20. **Ktor's `Url` does not canonicalise the host, resolve dots, or reject user-info, and returns bracketed IPv6.** Source: "`val delimiter = urlString.indexOfAny("@/\\?#".toCharArray(), startIndex).takeIf { it > 0 } ?: endIndex`", "`host = hostValue`" in `fillHost`, and "`override fun toString(): String = urlString`" in `Url`, <https://github.com/ktorio/ktor/blob/3.6.0/ktor-http/common/src/io/ktor/http/URLParser.kt> and `.../Url.kt`, Ktor 3.6.0. Measured with `io.ktor:ktor-http-jvm:3.6.0`: `Url("HTTP://EXAMPLE.COM:80/a/./b/../c?x=1#frag")` has host `EXAMPLE.COM` and path `/a/./b/../c`; `Url("http://[::ffff:127.0.0.1]/").host` is `[::ffff:127.0.0.1]`; `Url("http://%31%32%37.0.0.1/").host` is `%31%32%37.0.0.1`; `Url("http:h").host` is `localhost`; `Url("http://h/\uD800")` throws `URLParserException`.
21. **WHATWG: slash tolerance, the four double-dot forms, IPv4 number hosts, forbidden host code points.** "If c is neither U+002F (/) nor U+005C (\), then set state to authority state and decrease pointer by 1. Otherwise, special-scheme-missing-following-solidus validation error."; "A double-dot URL path segment is a URL path segment that is ".." or an ASCII case-insensitive match for ".%2e", "%2e.", or "%2e%2e"."; "If asciiDomain ends in a number: If domain is not an ASCII string, IPv4-non-ASCII-input validation error. Return the result of IPv4 parsing asciiDomain."; "A forbidden host code point is U+0000 NULL, U+0009 TAB, U+000A LF, U+000D CR, U+0020 SPACE, U+0023 (#), U+002F (/), U+003A (:), U+003C (<), U+003E (>), U+003F (?), U+0040 (@), U+005B ([), U+005C (\), U+005D (]), U+005E (^), or U+007C (|)." <https://url.spec.whatwg.org/> (living standard, read 2026-10-25).
22. **RFC 3986 normalisation and host rules.** "For consistency, URI producers and normalizers should use uppercase hexadecimal digits for all percent-encodings." (§2.1); "URI producers and normalizers should omit the port component and its ":" delimiter if port is empty or if its value would be the same as that of the scheme's default." (§3.2.3); "URI producing applications must not use percent-encoding in host unless it is used to represent a UTF-8 character sequence." (§3.2.2). <https://www.rfc-editor.org/rfc/rfc3986.txt>, RFC 3986 (2005).
23. **RFC 5952 canonical IPv6 text.** "The symbol "::" MUST NOT be used to shorten just one 16-bit 0 field."; "When the length of the consecutive 16-bit 0 fields are equal (i.e., 2001:db8:0:0:1:0:0:1), the first sequence of zero bits MUST be shortened."; hex letters "MUST be represented in lowercase." <https://www.rfc-editor.org/rfc/rfc5952.txt>, §4.2.2, §4.2.3, §4.3, RFC 5952 (2010).
24. **URL-parser confusion is a published, multi-library class of bug.** "The five types of inconsistencies are: scheme confusion, slashes confusion, backslash confusion, URL encoded data confusion, and scheme mixup." and "We examined 16 URL parsing libraries including:". <https://claroty.com/team82/research/exploiting-url-parsing-confusion>, Team82 and Snyk, read 2026-10-25 (the research dates from 2022).
25. **The Unicode `White_Space` property is this fixed set.** `0009..000D`, `0020`, `0085`, `00A0`, `1680`, `2000..200A`, `2028`, `2029`, `202F`, `205F`, `3000`, each on a line "`; White_Space`" of <https://www.unicode.org/Public/UCD/latest/ucd/PropList.txt> (header "`# PropList-18.0.0.txt`", dated 2026-08-07).
26. **`Result` returns mangle the JVM name of a companion function.** Measured with `javap -p` on the compiled prototype (Kotlin 2.2.0): `public final java.lang.Object parse-IoAF18A(java.lang.String);` in `Url$Companion`.
27. **Kotlin/JS compiles and runs the prototype.** Measured: Kotlin Gradle plugin 2.2.0, a multiplatform project with `jvm()` and `js(IR) { nodejs(); binaries.executable() }` and the prototype in `commonMain`; `gradlew compileDevelopmentExecutableKotlinJs` printed `BUILD SUCCESSFUL`; running the produced `.js` with Node v24.19.0 printed the same 20 lines as the JVM run of the same function (differing only in line endings).
28. **Differential results.** Measured with the harness described in the Recommendation (prototype of the algorithm, `HttpUrl` 5.5.0): seed 7 with 300,000 generated inputs + 944 corpus strings: 0 disagreements; seeds 101 and 202 with 2,000,000 + 944 each: 0 disagreements; with the four planted variants, seed 7 produced 40 or more disagreements each (`httpſ://[::1]` accepted; `xn--abc-` kept; `[1:2:3:4:5:6:7::8]` accepted while `HttpUrl` throws; `/a/.%2e/b` left unresolved).
29. **Repository claims used here** (not outside-world claims, listed for the reader): `Result` chaining convention in `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19`; `Result`-typed failures carrying an `Exception` subtype in `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ReliableSendFailure.kt:15`.
