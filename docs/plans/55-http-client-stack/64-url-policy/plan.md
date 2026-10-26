# Plan: `url-policy` — the `Trusted`/`Untrusted` outbound-URL policies, `TransportSettings` and the common address classifier

- **Covers:** `SpartanLaboratories/WebTools#64` — *"webtools-http: outbound-URL policies `Trusted`/`Untrusted`,
  `TransportSettings` and the common address classifier"* (`type: feature`). Sub-issue of the tracking issue
  `SpartanLaboratories/WebTools#55` (HTTP client stack). Blocked by #62 (landed). It blocks #65 (unit 6,
  `okhttp-transport`) directly and, through it, #68 and #70; #69 takes the `Untrusted` factory it adds.
- **Architecture:** [`../architecture.md`](../architecture.md), unit 5 `url-policy` (§11 unit 5 and its scope, §6.3.1
  *Outbound-URL policy*, §6.3.2 for the seam to the transport, §6.2 for the logging seam and the reason
  vocabularies, §6.6 for the logging rules, §6.9 for the mechanism per type, §7.3 and §7.4 for the guard layers and the
  boundary table, §9 for the tiers, §10 for the alternatives, §12 and §13, and the dated corrections at §2, §6.1, §6.7,
  §6.8 and §12). Systems-level impact: [`../impact.md`](../impact.md) §7 and §8 (what units 1 to 4 left for this unit,
  as built).
- **Research:** [`research/README.md`](research/README.md) with [`research/jvm-enforcement.md`](research/jvm-enforcement.md),
  [`research/iana-special-purpose-rows.md`](research/iana-special-purpose-rows.md),
  [`research/bypass-vectors.md`](research/bypass-vectors.md) and
  [`research/config-api-shape.md`](research/config-api-shape.md); the parent research is
  [`../research/outbound-url-policy.md`](../research/outbound-url-policy.md) (the deny-table design, the host rules, the
  three layers, the F1 host-form catalogue, the F7 JDK-predicate gap, the F10 advisories).
  Where the code block in `config-api-shape.md` §6 differs from this plan (the names `maxResponseBodyBytes` and
  `idleConnectionLifetime`, `CidrBlock(IPv4/8)` as the printed form, a policy without
  `withSettings`), this plan is the specification.
- **Branch:** `feat/issue-64-url-policy` (exists; cut from `master` at `ad0ae63`; holds the research commits).
- **Commit:** `fd93ff9` (commit 1 of §11): this `plan.md` is committed in the same commit as the first stage of the
  implementation, so `git log --follow` binds the two.
- **PR:** TBD (the PR closes #64).
- **What this plans:**
  - the public configuration types of `webtools-http` in the package `com.spartanlabs.webtools.http`:
    `OutboundUrlPolicy` (the sealed pair `Trusted` and `Untrusted`, each with a nested `Builder`, and the factories
    `OutboundUrlPolicy.trusted { }` and `untrusted { }` with their `from` overloads), `TransportSettings` (with
    `TrustedDefaults`, `UntrustedDefaults` and the factory `TransportSettings(from) { }`), `CidrBlock` and
    `MixedAnswers`;
  - the public `@DslMarker` annotation `WebToolsDsl` on every builder of the module (Amendment 3);
  - the `internal` classification core in `commonMain`: `StrictIp`, `HostRules` with `HostVerdict`, `AddressRanges`
    with `DeniedRange`, `GuardRelaxation`, and `UrlGuard` with `GuardRefusal`, `RedirectAdmission` and
    `AnswerVerdict`;
  - the `internal` JVM enforcement in `jvmMain`: `GuardedDns`, `GuardedSocketFactory`, `GuardedSocket`, the two marker
    exceptions with `policyRefusal()`, and the per-policy bundle `PolicyEnforcement`;
  - the tests of all of it (Level 1 (gating) to Level 4c (non-functional), and the Level 5 (user acceptance) pass), the
    regenerated ABI dump, and the `README.md` row.
- **Status:** built through S8; amendments above. The implementer appends each deviation from this plan, with its reasoning, to
  [`final-implementation.md`](final-implementation.md) §2 at the moment it is made, and records the procedures' results
  in its §4 and the Level 5 (user acceptance) pass in its §5.
- **Version moved:** none. `webtools-http` `1.0.0` stays unreleased until #55's stack is complete and is **not a release
  candidate** when this unit lands. No existing module moves a version.
- **Baseline:** `master` at `ad0ae63`. Line references are to it.

Code blocks in this plan are the intended implementation. They were written against the as-built sources of
`webtools-http` and the research's findings; the blocks themselves have not been compiled. The implementer fixes a
compile error mechanically and records any change to a rule (as opposed to a spelling) in `final-implementation.md` §2.

> **Amended during implementation (2026-10-25).** Amendment 1 makes §9's `CidrBlockParseDeterministicTest` refusal rows
> agree with `CidrBlock.parse` in §7.2, which governs. A shape failure is a text without exactly one `/`. An empty address
> (`/8`) is the address failure and an empty prefix (`10.0.0.0/`) the prefix failure, each with its own constant, so the
> test asserts those two constants exactly. §7.2's code is unchanged; its failure-path note now names what each kind covers.

> **Amended during implementation (2026-10-25).** Amendment 2 replaces the `declaredMethods` half of §9's structure check
> in `GuardedDnsComponentTest` with a behavioural one. Kotlin 2.2.0 declares a bridge `public newCall(Dns$Request)` in
> `GuardedDns` whose body is `invokespecial okhttp3/Dns.newCall`, because OkHttp compiles `Dns` with
> `-Xjvm-default=all-compatibility`; a method named `newCall` is therefore declared whether or not the source overrides it.
> The test keeps the `$$delegate` check and asserts that a delegate `Dns` whose own `newCall` counts its calls sees `lookup`
> once and `newCall` never. P9 mutation 1 now fails that test and the two inherited-`newCall` behaviour tests.
> `GuardedDns.kt` (§7.8) is unchanged.

> **Amended during implementation (2026-10-26).** Amendment 3 adds `WebToolsDsl`, a public `@DslMarker` annotation, and puts
> it on every builder the module passes to a consumer's lambda as its receiver: this unit's `TransportSettings.Builder`,
> `Trusted.Builder` and `Untrusted.Builder`, and the existing `HttpHeaders.Builder`, `HttpRequest.Builder` and
> `HttpResponse.Builder`. A nested builder block then reaches an outer builder only through its label
> (`this@untrusted.allowedPorts`). The code is in §7.11 and the dump lines in §7.9; stage S8 (§5) builds it, procedure P12
> (§9.2) proves it, `WebToolsDslConsumerGatingTest` and `WebToolsDslComponentTest` (§9) keep it, acceptance item 8 (§1) states
> it, and commit 3 (§11) carries it. R1 and §12 list the new public name; the marker is no longer a non-goal (§4), a
> follow-up (§13) or a known gap (§14).

> **Amended during implementation (2026-10-26).** Amendment 4 brings two facts of the as-built change into the plan.
> `../architecture.md` carries three dated corrections for this unit: at §6.3.1, the address table's two rules that are not
> registry rows, and the socket guard's own `Socket(Proxy.NO_PROXY)` sockets with the inherited `newCall`; at §7.4, a refusal
> that carries the range class beside the reason. §8, §11 and §13 say so; the other refinements of §6.3.1 stay listed in
> [`impact.md`](impact.md) §5. And `matchesPrefix` is `private` to `CidrBlock.kt`, whose `contains` is its only caller; §7.2
> and §12 show it so.

> **Amended during implementation (2026-10-26).** Amendment 5 leaves the retention of `WebToolsDsl` at its default instead
> of writing `@Retention(AnnotationRetention.RUNTIME)` (§7.11). Written out, that retention makes the `js` compile of the check
> module warn once per annotated builder ("Reflection is not supported in JavaScript target; therefore, annotations cannot
> be read at runtime."): Kotlin 2.2.0's JS checker, `FirJsRuntimeAnnotationChecker`, reports the warning only for a RUNTIME
> retention that is written out (`getExplicitAnnotationRetention`). The default retention is RUNTIME all the same: the JVM
> backend writes `@java.lang.annotation.Retention(RUNTIME)` when none is given (`AdditionalClassAnnotationLowering`), so
> `WebToolsDslComponentTest` finds the marker by reflection as before, and the dump does not change. No suppression is
> needed, and P12 step 2's expectation (only `w: JS Environment Not Selected`) holds. Applied to the built S8 before commit
> 3, P12 steps 2, 6 and 9 run again.

---

## 1. Requirements

### R1. Public surface and tier

`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, all **Stable Core**, each with
KDoc (`@param`, `@return`, `@throws`) that names its tier:

- `TransportSettings` (final, `internal` constructor, nested `Builder`, companion values `TrustedDefaults` and
  `UntrustedDefaults`) and the top-level factory `TransportSettings(from, configure)`;
- `OutboundUrlPolicy` (sealed, parameterless), its final nested `Trusted` and `Untrusted` (each with `internal`
  constructor and nested `Builder`) and the companion factories `trusted`/`untrusted` (four functions);
- `CidrBlock` (final, `private` constructor, `parse(text): Result<CidrBlock>`);
- `MixedAnswers` (`Reject`, `DropDenied`);
- `WebToolsDsl` (an annotation class marked `@DslMarker`; §7.11), on the three builders above and on the existing
  `HttpHeaders.Builder`, `HttpRequest.Builder` and `HttpResponse.Builder` (Amendment 3).

Nothing else becomes public. No seam is added: the policy pair is sealed, so nothing here is gated by
`ExperimentalForInheritanceWebToolsApi`, and no `@OptIn` is added to any source set. The module's existing rules hold:
`explicitApi()`, no public `data class`, no defaulted parameter on a public constructor or Stable Core function except a
trailing builder lambda `= {}`, hand-written `equals`/`hashCode`/`toString`.

### R2. `TransportSettings`

- Ten settings, as `public val`s: `callTimeout`, `connectTimeout`, `readTimeout`, `writeTimeout`
  (`kotlin.time.Duration`), `maxResponseBytes` (`Long`, decoded bytes), `maxRedirects` (`Int`), `allowHttpsToHttp`
  (`Boolean`), `maxConcurrentRequests`, `maxConcurrentRequestsPerHost` (`Int`), `idleConnectionTtl` (`Duration`).
- Two default sets, as companion values, and the factory `TransportSettings(from, configure)` as the only way to derive a
  modified copy:

  | Setting | `TrustedDefaults` | `UntrustedDefaults` |
  |---|---|---|
  | `callTimeout` (all hops together) | 30 s | 15 s |
  | `connectTimeout` | 10 s | 5 s |
  | `readTimeout` | 10 s | 5 s |
  | `writeTimeout` | 10 s | 5 s |
  | `maxResponseBytes` | 5 MiB = 5_242_880 | 2 MiB = 2_097_152 |
  | `maxRedirects` | 5 | 3 |
  | `allowHttpsToHttp` | false | false |
  | `maxConcurrentRequests` | 16 | 8 |
  | `maxConcurrentRequestsPerHost` | 5 | 2 |
  | `idleConnectionTtl` | 4 s | 4 s |

- Validation at `build()` with `IllegalArgumentException` and a constant message that names the property and never
  echoes a value: every `Duration` at least 1 ms and at most `Int.MAX_VALUE` ms; `maxResponseBytes >= 1`;
  `maxRedirects >= 0` (a followable redirect beyond the cap is `TooManyRedirects(maxRedirects)`, so `0` follows none);
  `maxConcurrentRequests >= 1`; `1 <= maxConcurrentRequestsPerHost <= maxConcurrentRequests`. These bounds guarantee that
  no value can trip OkHttp 5.5.0's own limits (§2).
- The KDoc states every default number, that `callTimeout` spans all hops, that `allowHttpsToHttp` is read by the URL
  policy for redirects, and that a changed default is a behaviour change.

### R3. The policies

- `Trusted`: `pinnedHosts: Set<String>` (empty = any host), `allowCleartext: Boolean` (false = `https` only),
  `settings`. No address filtering, no name rule, no port rule.
- `Untrusted`: `allowedPorts: Set<Int>?` (default `{80, 443, 8080, 8443}`, `null` = any port),
  `extraDeniedRanges: Set<CidrBlock>`, `extraDeniedHostSuffixes: Set<String>`, `mixedAnswers: MixedAnswers` (default
  `Reject`), `settings`. There is no parameter that lowers its address protection.
- Each is built by a nested `Builder` (`var`s of the read-only types seeded from a `from` value; `var settings` and
  `fun settings(configure)`), through `OutboundUrlPolicy.trusted { }`, `trusted(from) { }`, `untrusted { }`,
  `untrusted(from) { }`. The builders copy every collection and validate at `build()`.
- Value equality and a `toString()` that prints the kind, the counts of pinned hosts, denied ranges and suffixes (never
  their values), the port set, `mixedAnswers` and the settings.
- Validation (`IllegalArgumentException`, constant messages): `allowedPorts` non-empty when not `null` and every port in
  1..65535; every pinned host and every suffix canonicalised as §6.3 and §7 say, and refused when it fails.

### R4. `CidrBlock`

`CidrBlock.parse(text)` accepts exactly `<address>/<prefix>`: the address strict dotted-decimal or strict IPv6 (no
brackets, no zone); the prefix 0..32 or 0..128 in decimal with no sign and no leading zero (except `0`); a block with a
bit set after the prefix is refused, not masked. A failure is `Result.failure(IllegalArgumentException(...))` with one
constant message per kind of problem that never echoes `text`. `toString()` is the canonical block (`10.0.0.0/8`,
`fd00::/8`). Equality is on family, bytes and prefix.

### R5. The classification core (`commonMain`, `internal`, pure, JVM-free)

- `StrictIp.parseV4`, `parseV6`, `formatV6`.
- `HostRules.classify(host, extraSuffixes)`, total on any string.
- `AddressRanges.deny(address)`, built from the IANA registries exactly as §6.4 lists, with the dated snapshot comment
  and the refresh procedure, plus `224.0.0.0/4`, `168.63.129.16/32` and the ISATAP rule.
- `GuardRelaxation` with `None` and `LoopbackForTests`, the latter exempting exactly what §6.6 lists.
- `UrlGuard` with `checkHop`, `checkRequest`, `admitRedirect`, `checkAddress`, `judgeAnswer`, and the `internal` function
  `OutboundUrlPolicy.withSettings(settings)`.

### R6. The JVM enforcement (`jvmMain`, `internal`)

`GuardedDns`, `GuardedSocketFactory`, `GuardedSocket`, the markers `PolicyRefusedUnknownHost` and `PolicyRefusedIo` with
`Throwable.policyRefusal()`, and `PolicyEnforcement` with `UrlGuard.enforcement(baseDns)`, so that every address a name
resolves to and every connect attempt of an `Untrusted` fetch is judged, `Untrusted` never uses a proxy of any kind, and a
refusal reaches the transport as a marker at the top of the failure or in its cause chain or suppressed lists.

### R7. Logging, secrecy and documentation

- No classifier or guard logs a refusal; the site that creates the `RefusedByPolicy` failure logs it once: the transport
  (unit 6), or the call facade (unit 9) for its pre-check. The only log line this unit adds is
  `GuardedDns`'s `DEBUG` line under `DropDenied`.
- No exception message, `toString()` or log line of this unit holds an address, a range, a suffix or a pinned host. A
  refusal carries a coarse reason and, for `DeniedAddress` only, the range *class*.
- KDoc on every public declaration; line comments on the table's snapshot and on each non-obvious rule; `README.md` line
  11 names the policies.

### R8. Tests and runs

Tests at Level 1 (gating), Level 2 (component), Level 3 (integration), Level 4a (deterministic) and Level 4c
(non-functional) as §9 places them; Level 4b (end-to-end) has none in this unit; a Level 5 (user acceptance) manual pass.
Runs are limited to the tasks that exercise the change (§9.2). The Java 11 API only; no network beyond loopback.

### Acceptance

1. `:webtools-http:compileKotlinJvm` and `:webtools-http-commoncheck:compileKotlinJs` pass: the new `commonMain` files are
   JVM-free.
2. `:webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest
   :webtools-http:nonfunctionalTest :webtools-http:integrationTest` pass, and the result XML lists every test class of §9.
3. `:webtools-http:apiDump` writes the dump, read against §7.9 and the architecture §9 tier table; `apiCheck` then passes.
   The dump holds the public types of R1 and nothing `internal` (P8).
4. `:webtools-http:verifyJavaFloor` passes; `:webtools-http:check` passes, including the fixtures-publication check.
5. The six mutations of P9 each fail the test §9.2 names; the sources are restored and `git diff` is empty.
6. `git diff --stat` for `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic`,
   `webtools-http/build.gradle.kts`, `webtools-http/src/jvmTestFixtures` and `webtools-http-commoncheck` is empty.
7. The mechanical rules of P8 hold; `README.md` line 11 reads as §8 says.
8. `WebToolsDsl` is in `commonMain` as §7.11 writes it and on the six builders it names. The canary of P12 compiles before
   the marker and fails after it at each of its five statements, then is deleted. `apiDump` adds exactly the three lines of
   §7.9 and `apiCheck` passes; `WebToolsDslConsumerGatingTest` and `WebToolsDslComponentTest` pass;
   `:webtools-http-commoncheck:compileKotlinJs` passes with no compiler line but `w: JS Environment Not Selected`
   (Amendments 3 and 5).

---

## 2. Current state

**Baseline.** `master` at `ad0ae63`; the branch holds only the research commits. Units 1 (`build-logic`, #61), 2
(`java-floor`, #47), 3 (`http-core`, #62) and 4 (`test-support`, #63) have landed.

**What this unit plugs into** (`webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/`):
- `Url` (`Url.kt`): `http`/`https` only, never user-info. `host` is lower-case ASCII; an IPv6 host is RFC 5952 text
  without brackets; an IPv4-mapped literal is printed as dotted IPv4; digits-and-dots text, hex-looking text (`127.1`,
  `0x7f.1`, `2130706433`) and one trailing dot are kept as written; a name is `a-z 0-9 - _ .`, 1..253 characters in labels
  of 1..63, never percent-encoded, never non-ASCII, never an `xn--` label. `port` is the effective port 1..65535.
  `Url.parse(text): Result<Url>` fails with `UrlParseException(problem)`, where `problem` is the `internal enum
  UrlProblem` (`UrlProblem.kt:4`): `Empty`, `Backslash`, `WhitespaceOrControl`, `NotUnicode`, `Scheme`, `UserInfo`,
  `HostEncoding`, `HostIdn`, `Host`, `Port`, `DotSegment`.
- `canonicalHost(hostText): Result<String>` (`UrlHost.kt:7`), `internal`: a bracketed `[...]` IPv6 literal or a name,
  with the private IPv6 and dotted-quad parsers behind it (`UrlHost.kt:31`, `:69`, `:82`). This unit does not change
  `UrlHost.kt`: `StrictIp` is self-contained, a Level 4a (deterministic) test pins the two to the same IPv6 literal forms,
  and the policy builders canonicalise pinned hosts and suffixes by calling `canonicalHost`.
- `RefusalReason` (`RefusalReason.kt`): nine constants (`Scheme`, `UserInfo`, `HostForm`, `ReservedName`, `DeniedAddress`,
  `Port`, `HostNotPinned`, `InsecureRedirect`, `SensitiveHeader`); `TransportFailure.RefusedByPolicy(reason)` and
  `(reason, cause)` are public (`TransportFailure.kt:154`).
- `HttpHeaders.Entry.isSensitive` (`HttpHeaders.kt:78`) is `flagged || HttpSyntax.isCredentialName(name)`;
  `isCredentialName` (`HttpSyntax.kt:43`) covers `Authorization`, `Proxy-Authorization`, `Cookie` and `Set-Cookie`. The
  `SensitiveHeader` rule of `Untrusted` reads `isSensitive`, so it refuses all four names and every flagged entry.
- `HttpSyntax.asciiLower` (`HttpSyntax.kt:36`) is the ASCII-only fold the host rules use; protocol text never goes
  through `lowercase()`.
- `logger(name): Log` (`Log.kt:17`) with `debug`/`info`/`warn` and `{}` placeholders; loggers are named by the fully
  qualified class name (precedent: `logger("com.spartanlabs.webtools.http.testing.FixtureRecorder")`).
- `ResultExtensions.kt` (`flatMap`) and `NonFatal.kt` (`catchNonFatal`) are `internal` helpers; this unit needs neither.

**The module.** `webtools-http/build.gradle.kts`: `explicitApi()` (`:32`); the gate marker is opted into on `commonMain`
(`:43`) and `jvmMain` (`:51`) only, so tests stay consumers; `okhttp-jvm` 5.5.0 is an `implementation` dependency of
`jvmMain` (`:54`). No dependency changes. `jvmTest` sees OkHttp, and (through the fixtures association) `mockwebserver3`
5.5.0 and `kotlinx-coroutines-test`; JUnit Jupiter API 5.10.1 (`org.junit.jupiter.api.parallel.Isolated` included) reaches
`jvmTest` through `kotlin-test-junit5` (read with `:webtools-http:dependencies --configuration jvmTestCompileClasspath`).
**No MockK exists in this repository:** Level 2 (component) tests use hand-written stubs.

**The toolchain.** Every jvm compilation, tests included, uses `jvmTarget = 11` and `-Xjdk-release=11`: only the Java 11 API
(`InetAddress.ofLiteral`, Java 22, is out of reach). The build JDK is 17 to 26. `webtools-http-commoncheck` compiles
`webtools-http`'s `commonMain` for `js`, so every new `commonMain` file is JVM-free; this unit adds no `expect`. The level
test tasks select by package (`com.spartanlabs.testing.<level>.*`), so a test class is placed by its package, not by a tag.

**No code in the repository calls the policy yet:** the unit adds new files and extends the ABI dump and the README row.

**Verified outside-world facts this unit rests on** (details and sources in the research files):
- *OkHttp 5.5.0* (`parent-5.5.0`). `okhttp3.Dns` has the abstract `lookup`, a default `newCall` that returns a
  `LookupDnsCall` over `this`, and the record API; `RouteSelector` calls `lookup` only when `newCall` returns a
  `LookupDnsCall`, so a guard that forwards `newCall` is bypassed by a record-capable delegate and one that inherits it is
  routed through its own `lookup` (`jvm-enforcement.md` F1). An empty answer becomes
  `UnknownHostException("<Dns> returned no addresses for <host>")` (the host in the message). A host matching
  `([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\d.]+)` never reaches `Dns` (`okhttp3/internal/-HostnamesCommon.kt`). Only the
  parameterless `SocketFactory.createSocket()` is called, then `Socket.connect(SocketAddress, Int)`; only a
  `ConnectException` is re-wrapped with the address in its message (`jvm-enforcement.md` F2). Fast fallback is on by
  default and tries each address of the answer, first failure thrown with the rest in `suppressed`
  (`jvm-enforcement.md` F3, F8).
- *OkHttp 5.5.0's limits on the values the settings hand it.* `checkDuration` (`okhttp3/internal/-UtilJvm.kt`) rejects a
  timeout whose whole milliseconds exceed `Integer.MAX_VALUE` and a positive timeout under 1 ms; `Dispatcher` requires
  `maxRequests >= 1` and `maxRequestsPerHost >= 1`; `RealConnectionPool` requires `keepAliveDuration > 0`. R2's
  validation guarantees the transport never trips them.
- *JDK* (11.0.32, 17.0.21, 21.0.13, 27 read). `new Socket()` installs a `SocksSocketImpl` that consults
  `ProxySelector.getDefault()` and `socksProxyHost`; `new Socket(Proxy.NO_PROXY)` installs the platform implementation
  directly; `Socket.connect(SocketAddress)` is `connect(endpoint, 0)` (`jvm-enforcement.md` F4, F5). `InetAddress`'s
  predicates miss 16 of 26 non-public addresses (parent research F7). `InetAddress.getByName` parses a valid literal
  without a lookup and sends other unbracketed text that starts with a hex digit or `:` to the resolver
  (`jvm-enforcement.md` F7).
- *IANA* registries read 2026-10-25: IPv4 and IPv6 Special-Purpose Address Registries both "Last Updated 2025-10-09", 25
  rows each (the IPv4 `NAT64/DNS64 Discovery` row lists two prefixes, so 26); `2000::/3` is still the only block IANA
  allocates global unicast from (`iana-special-purpose-rows.md` F1 to F5).
- *Kotlin 2.2.0.* A sealed class cannot have an `internal` constructor (`protected` or `private` only); the sealed base is
  therefore parameterless and its subclasses carry the `internal` constructors (`config-api-shape.md` F5).

---

## 3. Assumptions

- **`apiDump` shows the shapes of §7.9.** The dump lines for the companion functions with a trailing default lambda
  (`OutboundUrlPolicy$Companion.trusted$default`) are inferred from the existing `HttpHeadersKt.HttpHeaders$default` entry;
  the first `apiDump` is the check. If a line differs, the dump is the as-built fact and §7.9 is reconciled.
- **`Socket.connect(SocketAddress)` calls `connect(endpoint, 0)` on every build JDK 17 to 26** (read on 11, 17, 21 and 27).
  If one JDK did not, a `connect(SocketAddress)` call would skip the guard; Level 2 (component) tests call both overloads, so
  the first run on that JDK fails.
- **`Socket(Proxy.NO_PROXY)` keeps a JVM-wide SOCKS selector away** (read from source, not run). The
  Level 3 (integration) SOCKS test measures it. If its control (the default factory's socket reaching the fake SOCKS listener) does
  not hold on the build JDK, the claim does not hold there: the implementer records the measurement in
  `final-implementation.md` §6 and keeps `Socket(Proxy.NO_PROXY)` as the base (it is the stricter choice).
- **A `Dns` stub that returns `InetAddress.getByAddress(bytes)` values triggers no lookup** (the JDK documents it). Every
  test builds addresses that way and never from a name.
- **`LookupDnsCall` surfaces a `lookup` failure through `Dns.Callback.onFailure`, possibly wrapped.** The test for the
  inherited `newCall` therefore looks for the marker with `policyRefusal()` instead of asserting an exception type.
- **The build host is the maintainer's** (Windows, JDK 17 to 26). Linux and macOS are not exercised.

---

## 4. Non-goals

- **No OkHttp transport, redirect loop, `ExchangeGuard` or failure mapping** (unit 6, `okhttp-transport`). This unit
  provides the checks and the enforcement types; it wires nothing into a client and logs no refusal.
- **No loopback test factories** (`LoopbackTransports`, `webtoolsUrl`, `toMockResponse`, `enqueue(fixture)`), and nothing
  under `src/jvmTestFixtures` (unit 6 adds them; the fixtures-publication check asserts the fixtures' dependency list).
- **No public loosening knob.** `GuardRelaxation.LoopbackForTests` is `internal` and reachable only from the fixtures and
  this module's tests; the main artifact has no way to switch the address guard off.
- **No public `CidrBlock.contains`, accessors or `of(...)` constructors** (additive later), no `Trusted` address rules, no
  proxy parameter for `Untrusted`, no callback or predicate seam for the verdict.
- **No change to `Url`, `UrlHost.kt`, `RefusalReason` or `TransportFailure`.**
- **No `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is unit 11's.
- **No version change, no CHANGELOG** (none exists), no new dependency.
- **Defence against a JVM-wide `Socket.setSocketImplFactory(...)` or a replaced `ProxySelector` by other code in the same
  JVM:** the first affects even `Socket(Proxy.NO_PROXY)`; neither is guardable from a library.

---

## 5. Approach and staging

The code lands in three commits (§11) and is built in an order that puts the pure, portable core first and the
JDK-and-OkHttp-facing code last, because the first can be proved without a network and the second needs the first's
`UrlGuard` to exist. After every stage the stage's procedure (§9.2) is run and a result that differs from this plan is
understood before anything is built on it.

```mermaid
flowchart TB
    subgraph C["commonMain (public)"]
        TS["TransportSettings<br/>MixedAnswers"]
        CB["CidrBlock"]
        POL["OutboundUrlPolicy<br/>Trusted · Untrusted · Builders"]
    end
    subgraph CI["commonMain (internal, pure)"]
        SIP["StrictIp"]
        HR["HostRules"]
        AR["AddressRanges"]
        UG["UrlGuard · GuardRelaxation<br/>GuardRefusal · RedirectAdmission · AnswerVerdict"]
    end
    subgraph J["jvmMain (internal)"]
        MK["PolicyRefusedUnknownHost · PolicyRefusedIo · policyRefusal()"]
        GD["GuardedDns"]
        GS["GuardedSocketFactory → GuardedSocket"]
        PE["PolicyEnforcement · UrlGuard.enforcement(baseDns)"]
    end
    SIP --> CB
    SIP --> HR
    CB --> AR
    CB --> POL
    TS --> POL
    HR --> UG
    AR --> UG
    POL --> UG
    UG --> GD
    UG --> GS
    MK --> GD
    MK --> GS
    GD --> PE
    GS --> PE
    PE -. "unit 6 applies it to an OkHttpClient.Builder" .-> T["OkHttpTransport (unit 6)"]
    UG -. "checkRequest · admitRedirect" .-> T
```

| Stage | Content | Verified by |
|---|---|---|
| S1 | `TransportSettings`, `MixedAnswers`, `StrictIp`, `CidrBlock`, the test support files (`PolicyTestSupport.kt` without its guard helpers, which need `UrlGuard`), their Level 2 (component) and Level 4a (deterministic) tests | P1, P2 |
| S2 | `OutboundUrlPolicy` with its builders and the canonicalisation helpers, their tests, the Level 1 (gating) consumer | P2 |
| S3 | `HostRules`, `AddressRanges`, `GuardRelaxation`, `UrlGuard`, the guard helpers of `PolicyTestSupport.kt`, the registry-row, bypass-vector and reason-order tables, the Level 2 (component) tests of the guard and the Level 4c (non-functional) tests | P3 |
| S4 | The dump (`apiDump`), the JS compile, commit 1 | P4 |
| S5 | The markers, `GuardedDns`, `GuardedSocketFactory`, `GuardedSocket`, `PolicyEnforcement` and their Level 2 (component) tests; then the Level 3 (integration) tests; then the JDK-based Level 4a (deterministic) tests | P5, P6 |
| S6 | `verifyJavaFloor`, the full `check`, the mechanical rules, the mutations, the unaffected-module proofs, commit 2 | P7, P8, P9, P10 |
| S7 | The README row (commit 4), the Level 5 (user acceptance) pass and the as-built reconciliation (commit 5) | P11 |
| S8 | `WebToolsDsl` on the six builders (Amendment 3): the canary before and after the marker, the two test classes, the dump, the floor and the full check, commit 3 (before the commits of S7) | P12 |

---

## 6. Design

### 6.1 Mechanism per type (architecture §9 and §6.9, for this unit's types)

| Type | Mechanism | Tier |
|---|---|---|
| `TransportSettings` | final class over an `internal` constructor; nested `Builder`; named default values; derivation through the top-level factory `TransportSettings(from) { }` | Stable Core |
| `OutboundUrlPolicy` (`Trusted`, `Untrusted`) | `sealed` base (the closed pair is the contract), final nested subclasses over `internal` constructors, nested `Builder`s, companion factories | Stable Core |
| `CidrBlock` | final class over a `private` constructor; companion `parse` returning `Result` | Stable Core |
| `MixedAnswers` | `enum class`: a consumer-chosen input, so growth never breaks a producer | Stable Core |
| `StrictIp`, `HostRules`, `AddressRanges` | `internal object`: stateless pure functions over strings and bytes | internal |
| `UrlGuard`, `GuardRelaxation` | `internal class`: immutable after construction, safe to share between threads | internal |
| `GuardedDns`, `GuardedSocketFactory`, `GuardedSocket`, the markers, `PolicyEnforcement` | `internal class`es implementing `okhttp3.Dns`, `javax.net.SocketFactory` and `java.net.Socket`'s connect; nothing OkHttp-typed leaves `jvmMain` | internal |

No type in this table is an extension seam: a consumer configures a policy and cannot implement one. A new setting is a new
builder property and a new `TransportSettings` property, never a changed signature.

### 6.2 The public configuration shape

- **Named defaults plus a required `from`.** `TransportSettings.TrustedDefaults` and `UntrustedDefaults` are companion
  values; `TransportSettings(from) { }` derives a modified copy. `from` has no default value, so the Stable Core signature
  has no `$default` bridge in the ABI; the one default in this unit is the trailing `= {}` of `trusted(configure)` and
  `untrusted(configure)`.
- **Four policy factories** in `OutboundUrlPolicy`'s companion: `trusted(configure = {})`, `trusted(from, configure)`,
  `untrusted(configure = {})`, `untrusted(from, configure)`. They return the precise `Trusted`/`Untrusted`, so a caller reads
  `.allowedPorts` without a cast.
- **The policy builders** hold `var`s of the read-only types, so `pinnedHosts += "a.example"` extends a set. They also hold
  `var settings: TransportSettings` and `fun settings(configure)`, which equals `settings = TransportSettings(settings,
  configure)`: the block starts from the builder's current value, so `settings = X` followed by `settings { }` modifies `X`
  and the order matters (the KDoc says so).
- **Collections** are copied once at `build()` (`toSet()`), exposed read-only. A builder `var` holds the caller's reference
  until `build()`; the builders are not thread-safe and the KDoc says to use them only inside the lambda (the
  `HttpHeaders.Builder` wording).
- **Equality** is hand-written, over every field, and each type's `toString()` prints nothing an operator would not want in
  a log line (§6.8). A Level 2 (component) test per property proves that a change of only that property breaks equality and
  changes `toString()`, because hand-written equality drifts when a property is added.
- **The sealed base is parameterless**, and no policy instance is held in its companion: a static field that holds a
  `Trusted` makes the base's class initialiser construct a subclass, and a second thread initialising a subclass first can
  deadlock on the two class-initialisation locks. The default seeds are built inside the factory functions; the default
  port set is a `private` top-level value of the file.
- **`internal fun OutboundUrlPolicy.withSettings(settings)`** (in `OutboundUrlPolicy.kt`) derives a policy with other
  settings through the `from` factories; it is the call facades' tool for raising concurrency to the budget's burst.

### 6.3 Pinned hosts and suffixes are canonicalised by `canonicalHost`

| Input to `pinnedHosts` (each entry) | Stored | Notes |
|---|---|---|
| `api.example.com`, `API.Example.COM` | `api.example.com` | lower-cased by `canonicalHost`; duplicates collapse after canonicalisation |
| `api.example.com.` | `api.example.com.` | the trailing dot is kept as written (it is a different host to `Url.host`, as `Url` equality says) |
| `10.0.0.5`, `127.1` | as written | digits-and-dots text is kept as written by `canonicalHost`; `Trusted` applies no host-form rule |
| `[::1]`, `::1`, `[::ffff:10.0.0.5]` | `::1`, `::1`, `10.0.0.5` | an entry containing `:` is an IPv6 literal, with or without brackets, stored without them, printed as `Url` prints a host |
| `""`, `a b`, `api.example.com:443`, `https://x`, `a/b`, `a@b`, `müller.de`, `xn--n3h.net`, `[x]`, `[::1`, `::1]`, `[]` | refused | `IllegalArgumentException(PINNED_HOST_MESSAGE)` |

A `Trusted` pin matches by exact string equality of the canonical entry with `Url.host`.

| Input to `extraDeniedHostSuffixes` (each entry) | Result |
|---|---|
| `corp.example`, `Corp.Example`, `corp` | stored lower-case; matches the name itself and any subdomain on a label boundary |
| `""`, `.corp`, `corp.`, `10.0`, `1.2.3.4`, `::1`, `[::1]`, `a b`, `xn--n3h.net`, `a..b` | refused: `IllegalArgumentException(SUFFIX_MESSAGE)` |

The rule: a suffix is refused when it is empty, contains `:`, `[` or `]`, starts or ends with `.`, is digits-and-dots only,
or fails `canonicalHost`.

### 6.4 The address table (`AddressRanges`)

`AddressRanges.deny(address: ByteArray): DeniedRange?` returns `null` (allowed) or the matched row's **class**. It is a pure
function over 4- or 16-byte addresses; any other length is denied (`unusual-length`).

**IPv4 (4 bytes).** First match over this list, nested rows first so each logs its own class (the 26 registry prefixes of
25 rows, then two rows that are not registry rows):

| # | Block | Class | Origin |
|---|---|---|---|
| 1 | `0.0.0.0/32` | `this-host` | registry row 2 |
| 2 | `192.0.0.8/32` | `ipv4-dummy-address` | row 10 |
| 3 | `192.0.0.9/32` | `pcp-anycast` | row 11 |
| 4 | `192.0.0.10/32` | `turn-anycast` | row 12 |
| 5 | `192.0.0.170/32` | `nat64-dns64-discovery` | row 13 |
| 6 | `192.0.0.171/32` | `nat64-dns64-discovery` | row 13 |
| 7 | `192.0.0.0/29` | `ipv4-service-continuity-prefix` | row 9 |
| 8 | `192.88.99.2/32` | `6a44-relay-anycast` | row 18 |
| 9 | `255.255.255.255/32` | `limited-broadcast` | row 25 |
| 10 | `0.0.0.0/8` | `this-network` | row 1 |
| 11 | `10.0.0.0/8` | `private-use` | row 3 |
| 12 | `100.64.0.0/10` | `shared-address-space` | row 4 |
| 13 | `127.0.0.0/8` | `loopback` | row 5 |
| 14 | `169.254.0.0/16` | `link-local` | row 6 |
| 15 | `172.16.0.0/12` | `private-use` | row 7 |
| 16 | `192.0.0.0/24` | `ietf-protocol-assignments` | row 8 |
| 17 | `192.0.2.0/24` | `documentation` | row 14 |
| 18 | `192.31.196.0/24` | `as112-v4` | row 15 |
| 19 | `192.52.193.0/24` | `amt` | row 16 |
| 20 | `192.88.99.0/24` | `deprecated-6to4-relay-anycast` | row 17 |
| 21 | `192.168.0.0/16` | `private-use` | row 19 |
| 22 | `192.175.48.0/24` | `direct-delegation-as112` | row 20 |
| 23 | `198.18.0.0/15` | `benchmarking` | row 21 |
| 24 | `198.51.100.0/24` | `documentation` | row 22 |
| 25 | `203.0.113.0/24` | `documentation` | row 23 |
| 26 | `240.0.0.0/4` | `reserved` | row 24 |
| 27 | `224.0.0.0/4` | `multicast` | IPv4 Multicast Address Space registry (RFC 5771), not a special-purpose row |
| 28 | `168.63.129.16/32` | `azure-wireserver` | not in any IANA registry: the Azure platform endpoint, a public address outside every row, listed by both SSRF catalogues as a target, and not a host that serves web content |

Rows 1 to 26 are the 26 registry prefixes. Every registry row is denied, including the five marked globally reachable
(`192.0.0.9`, `192.0.0.10`, AS112, AMT, direct-delegation AS112): each is an anycast service, a relay or a sink, and none
is a host that serves web content. `192.0.0.0/24` is denied whole (its footnote reads "Not useable unless by virtue of a more
specific reservation"), which also catches Oracle Cloud's `192.0.0.192`. `224.0.0.0/4` and `240.0.0.0/4` are adjacent; a
test that treats `239.255.255.255` as an allowed control is wrong.

**IPv6 (16 bytes).** In this order:

1. **IPv4-mapped** `::ffff:0:0/96` (bytes 0..9 zero, bytes 10 and 11 `0xFF`): return `denyV4(low 4 bytes)`, reporting the
   IPv4 row's class; `::ffff:8.8.8.8` stays allowed.
2. **NAT64 well-known** `64:ff9b::/96` (bytes `00 64 ff 9b`, then eight zero bytes): the same recursion;
   `64:ff9b::808:808` stays allowed, so a DNS64 network keeps working.
3. **Outside `2000::/3`** (`(address[0] and 0xE0) != 0x20`): denied, class `not-global-unicast`. This one rule denies
   `::1`, `::`, IPv4-compatible `::a.b.c.d`, SIIT `::ffff:0:a.b.c.d`, `64:ff9b:1::/48`, `100::/64`, `100:0:0:1::/64`,
   `5f00::/16`, `fc00::/7`, `fe80::/10`, `fec0::/10`, `ff00::/8` and all unallocated space, with no entry to forget.
4. **Inside `2000::/3`**, first match over these rows (the ten nested rows of `2001::/23` first):

   | # | Block | Class |
   |---|---|---|
   | 1 | `2001::/32` | `teredo` |
   | 2 | `2001:1::1/128` | `pcp-anycast` |
   | 3 | `2001:1::2/128` | `turn-anycast` |
   | 4 | `2001:1::3/128` | `dns-sd-srp-anycast` |
   | 5 | `2001:2::/48` | `benchmarking` |
   | 6 | `2001:3::/32` | `amt` |
   | 7 | `2001:4:112::/48` | `as112-v6` |
   | 8 | `2001:10::/28` | `deprecated-orchid` |
   | 9 | `2001:20::/28` | `orchidv2` |
   | 10 | `2001:30::/28` | `drone-remote-id-det` |
   | 11 | `2001::/23` | `ietf-protocol-assignments` |
   | 12 | `2001:db8::/32` | `documentation` |
   | 13 | `2002::/16` | `6to4` |
   | 14 | `2620:4f:8000::/48` | `direct-delegation-as112` |
   | 15 | `3fff::/20` | `documentation` |

   6to4 and Teredo are denied wholesale, not judged by their embedded IPv4: no public content host lives there, and
   extracting their embedded address buys nothing.
5. **ISATAP** anywhere else in `2000::/3`: `(group4 and 0xFCFF) == 0 && group5 == 0x5EFE`, groups numbered 0 to 7 (group 4 is
   bytes 8 and 9, group 5 bytes 10 and 11), denied wholesale, class `isatap`. The mask ignores the universal/local and group
   bits of the interface identifier's first byte, so `0000:5efe`, `0100:5efe`, `0200:5efe` and `0300:5efe` all match. An
   ISATAP address embeds an IPv4 address a host with an ISATAP interface tunnels to (RFC 5214); a 2026 advisory
   (openclaw GHSA-8cp7-rp8r-mg77) shows a shipped classifier missing it.
6. Otherwise allowed (`null`).

**Why this shape.** IPv4 is a deny list (every registry row plus multicast, plus one provider endpoint); IPv6 is
default-deny outside the one block IANA allocates global unicast from, so a new special block carved out of reserved space
(as `5f00::/16` was in 2024) is denied without a table edit; one carved *inside* `2000::/3` (as `3fff::/20` was) needs an
edit, which the refresh procedure covers.

**The snapshot comment** at the top of the table in `AddressRanges.kt` reads (verbatim, with the procedure):

```kotlin
// Source of this table: the IANA IPv4 and IPv6 Special-Purpose Address Registries, "Last Updated 2025-10-09" (read
// 2026-10-25); multicast 224.0.0.0/4 from the IPv4 Multicast Address Space registry (RFC 5771); global unicast 2000::/3
// from the IPv6 Address Space registry (Last Updated 2025-10-23). Two rules are NOT registry rows and a refresh must not
// delete them: 168.63.129.16/32 (Azure platform endpoint) and the ISATAP interface-identifier rule.
// Refresh: fetch iana-ipv4-special-registry-1.csv and iana-ipv6-special-registry-1.csv; strip the footnote markers of the
// Address Block cells ("192.0.0.0/24 [2]", "2002::/16 [3]") and split the one cell that holds two prefixes
// ("192.0.0.170/32, 192.0.0.171/32"); a quoted Name cell contains commas, so parse the CSV properly. Diff against the rows
// below. Add any IPv4 row and any IPv6 row INSIDE 2000::/3; a row outside 2000::/3 needs no edit. Check that the IPv6
// Address Space registry still limits unicast allocation to 2000::/3. Then update the row lists of
// AddressRangesRowsDeterministicTest and AddressRangesControlsDeterministicTest, and the date above.
```

### 6.5 The host rules and the per-hop check

**`HostRules.classify(host, extraSuffixes): HostVerdict`** is total on any string (it is called directly in tests with text
that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then,
in order, the first rule that applies decides:

| # | Condition on the folded text | Verdict |
|---|---|---|
| 1 | empty, or any character outside `a-z 0-9 - _ . :` | `Refused(HostForm)` |
| 2 | contains `:` | `Literal(parseV6)`, or `Refused(HostForm)` when `StrictIp.parseV6` returns `null` |
| 3 | only digits and dots | `Literal(parseV4)`, or `Refused(HostForm)` when `StrictIp.parseV4` returns `null` |
| 4 | after removing one trailing dot, an empty label (a leading dot, two dots, or more than one trailing dot) | `Refused(HostForm)` |
| 5 | the last label is all ASCII digits, or `0x` followed by zero or more hex digits | `Refused(HostForm)` |
| 6 | a single label; or `localhost`, `local`, `internal`, `home.arpa`, `localdomain`, or a subdomain of one; or a policy suffix or a subdomain of one (label boundary) | `Refused(ReservedName)` |
| 7 | anything else | `Name` (the resolver guard judges it) |

Rule 5 is the WHATWG "ends in a number" test (it also catches `0x7f.0.0.1`, which OkHttp sends to `Dns`); zero hex digits
count (`0x`, `127.0.0.0x`). Rules 2 and 3 together make every lenient numeric spelling (`127.1`, `2130706433`, `0177.0.0.1`,
`127.0.0.1.`) a `HostForm` refusal, which removes the parser-disagreement class of bypass: the JDK reads `0177` as decimal 177
where `inet_aton` reads octal, and refusing both removes the choice.

**`UrlGuard.checkHop(url, previous)`** applies the policy's rules in this order, the first refusal wins:

```mermaid
flowchart TD
    A["checkHop(url, previous)"] --> P{"policy"}
    P -->|Trusted| T1{"http and not allowCleartext?"}
    T1 -->|yes| RS["Scheme"]
    T1 -->|no| T2{"pinnedHosts non-empty and host not pinned?"}
    T2 -->|yes| RN["HostNotPinned"]
    T2 -->|no| D
    P -->|Untrusted| U1["HostRules.classify(host, suffixes)"]
    U1 -->|"Refused(r)"| RR["r: HostForm or ReservedName"]
    U1 -->|"Literal(address)"| U2{"checkAddress(address)"}
    U2 -->|denied| RD["DeniedAddress + range class"]
    U2 -->|allowed| U3
    U1 -->|Name| U3{"allowedPorts set and port outside it?"}
    U3 -->|yes| RP["Port"]
    U3 -->|no| D{"previous is https, url is http, not allowHttpsToHttp?"}
    D -->|yes| RI["InsecureRedirect"]
    D -->|no| OK["null (allowed)"]
```

The order, as one list: scheme, user-info, host form, IP literal, reserved name, port, pin, downgrade. User-info is a
documented no-op in `checkHop` because a `Url` never carries any; `admitRedirect` maps the parser's refusal of it. A request
is `http` or `https` by construction, so `Untrusted` never reports `Scheme` from `checkHop` (the HTTP scheme is allowed
there; cleartext is the `Trusted` policy's choice). Where two rules apply, the earlier wins: `http://127.0.0.1:6379/` is
`DeniedAddress`, not `Port`; `http://localhost:6379/` is `ReservedName`; a `Trusted` hop to an unpinned `http` host is
`Scheme`, not `HostNotPinned`; a `Trusted` hop with `allowCleartext` to an unpinned host after an `https` hop is
`HostNotPinned`, not `InsecureRedirect`.

**`checkRequest(request)`** is `checkHop(request.url, null)`, then, for `Untrusted` only, `SensitiveHeader` for any
`request.headers.entries()` entry with `isSensitive`. `Trusted` never refuses a header.

**`admitRedirect(target, previous)`** takes the text of the redirect target the transport resolved with `HttpUrl.resolve`
(not a relative reference). `Url.parse(target)` failure maps by problem: `UserInfo` → `UserInfo`, `Scheme` → `Scheme`,
`Port` → `Port`, every other problem → `HostForm`. Then `checkHop(url, previous)`.

**`checkAddress(address)`** — `Trusted`: always `null`. `Untrusted`: (the relaxation, §6.6), then `AddressRanges.deny`, then
`extraDeniedRanges`: a block matches an address of its own family by prefix; an IPv4 block also matches the IPv4 embedded in
an IPv4-mapped or NAT64-well-known 16-byte address; the class is `extra-denied-range`. The KDoc of `extraDeniedRanges` states
that, that an IPv6 block matches IPv6 addresses, and that a deployment behind its own NAT64 prefix (an operator-chosen RFC
6052 prefix) or with an internal service on a public address adds it there: neither is knowable to a library.

**`judgeAnswer(addresses)`** — `Reject` refuses the whole answer at its first denied address; `DropDenied` keeps the allowed
indices in order and refuses (with the first address's refusal) when none is left. `Trusted` allows every index. An empty list
is `Allowed(emptyList())`: it is not the guard's to judge (`GuardedDns` turns an empty delegate answer into
`UnknownHostException` before judging).

### 6.6 `GuardRelaxation`

`GuardRelaxation.None` changes nothing. `GuardRelaxation.LoopbackForTests` exempts exactly:

| Rule | Exemption | Stays live |
|---|---|---|
| reserved name | the host exactly `localhost` (not `localhost.`, not `foo.localhost`) | the policy's `extraDeniedHostSuffixes`: a suffix that matches `localhost` still refuses it |
| address table | `127.0.0.0/8`, `::1`, and an IPv4-mapped 16-byte address whose embedded IPv4 is in `127.0.0.0/8` | `extraDeniedRanges` (a `127.0.0.0/8` extra block still refuses `127.0.0.1`); NAT64 (`64:ff9b::7f00:1` stays denied); every other row |
| port rule | the hosts `localhost`, `127.0.0.1` and `::1` | the port rule for every other host, `127.0.0.2` included |

An IPv4-mapped 16-byte address whose embedded IPv4 is in `127.0.0.0/8` is that same loopback address: the table judges a
mapped address by its IPv4, and the JDK presents one as an `Inet4Address`, so it is exempt with `127.0.0.0/8`. A NAT64 address
names a loopback address behind a translator, not this host's, and is not exempt.

Every other rule stays live: host form (`0x7f.1` is still `HostForm`), the other reserved names (`printer.local`,
`intranet`), the downgrade rule, the header rule, `10.0.0.5`, `169.254.169.254`. The class has a `private` constructor and two
values, and is `internal`: it is reachable only from the test fixtures (unit 6's `LoopbackTransports`) and this module's
tests. `Trusted` ignores it.

### 6.7 The JVM enforcement

```mermaid
flowchart LR
    REQ["transport (unit 6)"] -->|"checkRequest / admitRedirect<br/>before any Call"| UG["UrlGuard"]
    REQ --> CL["OkHttpClient.Builder<br/>← UrlGuard.enforcement(baseDns)"]
    CL --> RS["RouteSelector"]
    RS -->|"name: dns.newCall → LookupDnsCall → lookup"| GD["GuardedDns.lookup<br/>judgeAnswer (Reject / DropDenied)"]
    GD -->|"denied"| M1["PolicyRefusedUnknownHost(refusal)"]
    RS -->|"per address (fast fallback)"| SF["GuardedSocketFactory.createSocket()"]
    SF --> GS["GuardedSocket.connect(endpoint, timeout)<br/>checkAddress(endpoint.address.address)"]
    GS -->|"denied · unresolved · non-Inet"| M2["PolicyRefusedIo(refusal)"]
    GS -->|"allowed"| NET["super.connect (Socket(Proxy.NO_PROXY))"]
    M1 & M2 --> MAP["transport: policyRefusal() → RefusedByPolicy(reason) + one WARN"]
```

- **`GuardedDns`** overrides `lookup` only. It never uses `by delegate` and never overrides `newCall`: the inherited default
  returns a `LookupDnsCall` over `this`, which `RouteSelector` resolves through the guard's own `lookup`; a forwarded `newCall`
  would let a record-capable delegate (DNS-over-HTTPS, Android's resolver) bypass the guard. Delegate exceptions pass through
  unchanged. An empty delegate answer becomes `UnknownHostException` with a constant message (OkHttp's own empty-answer message
  holds the host). It never returns an empty list. A refusal is `PolicyRefusedUnknownHost`, an `UnknownHostException` subclass:
  any other throwable from `lookup` would reach the caller as `IOException("canceled due to …")` with the refusal one level
  down.
- **`GuardedSocketFactory`** is not an `SSLSocketFactory` (OkHttp rejects one) and has no delegate: it must create the socket
  itself to choose the socket implementation. `createSocket()` returns a new `GuardedSocket`; the four abstract overloads throw
  `SocketException` with a constant message (OkHttp 5.5.0 never calls them; refusing fails closed, so a future OkHttp that
  calls one fails loudly instead of connecting unjudged).
- **`GuardedSocket`** extends `Socket(Proxy.NO_PROXY)` and overrides only `connect(SocketAddress?, Int)`. `new Socket()` (the
  default factory's socket) installs a `SocksSocketImpl` that consults `ProxySelector.getDefault()` and `socksProxyHost` on every
  connect, so `proxy(Proxy.NO_PROXY)` on the client would not keep a JVM-wide SOCKS setting away; `Socket(Proxy.NO_PROXY)`
  installs the platform implementation directly. A non-`InetSocketAddress` or unresolved target is refused (a `SocksSocketImpl`
  would hand a host name to a SOCKS5 proxy to resolve); a denied address is refused; then `super.connect`. It is stateless
  beyond `super`, so fast fallback's concurrent attempts, and a loser being closed while another thread connects, add no hazard.
  `connect(SocketAddress)` calls `connect(endpoint, 0)`, so one override covers both.
- **The markers** are `PolicyRefusedUnknownHost : UnknownHostException` and `PolicyRefusedIo : IOException` — never a
  `ConnectException`, which OkHttp re-wraps as `ConnectException("Failed to connect to <address>")`, putting the address in a
  message. Their messages are constants. `fillInStackTrace()` is overridden to skip the stack: a refusal is an expected,
  attacker-triggerable event and the stack adds nothing.
- **`policyRefusal()`** searches a throwable, its cause chain and its suppressed lists, depth first (cause before suppressed,
  suppressed in order), bounded to 100 throwables and cycle-safe (an identity set). Why the whole tree: with fast fallback the
  first failure is thrown with later ones in `suppressed`; an expired call timeout can carry either as the cause of
  `InterruptedIOException("timeout")`.

How a refusal surfaces through a real OkHttp 5.5.0 call (read from source; the Level 3 (integration) tests measure each row):

| Situation | What the caller sees |
|---|---|
| `Dns` answer with a denied address under `Reject` | `PolicyRefusedUnknownHost` at the top, unwrapped, `suppressed` empty |
| `Dns` answer with only denied addresses under `DropDenied` | the same |
| socket guard only, three denied addresses | `PolicyRefusedIo` at the top (the first attempt's), two more in `suppressed`; no `ConnectException` |
| a denied attempt before an allowed host's failed connect (socket guard only) | the marker first, the `ConnectException` suppressed: the transport treats a marker anywhere in the tree as a refusal, since a marker exists only when something was denied. Under `Reject` the resolver guard never lets a denied address reach the socket layer, so a socket-guard marker means something bypassed the resolver guard |
| call timeout expired during a refusal | `InterruptedIOException("timeout")` with the marker as its cause |

**The per-policy bundle.** `UrlGuard.enforcement(baseDns)`: `Untrusted` → `GuardedDns(baseDns, guard)`,
`GuardedSocketFactory(guard)`, `Proxy.NO_PROXY`. `Trusted` → `baseDns`, `null`, `null` (OkHttp's default proxy selection and
default socket factory stay). `baseDns` is `Dns.SYSTEM` or a test stub. Unit 6 applies the three values to its
`OkHttpClient.Builder` (`proxy`, `dns`, `socketFactory`) and keeps `fastFallback` at its default and
`retryOnConnectionFailure(false)`.

### 6.8 Error handling of the new signatures

Expected failures are values; developer-written configuration mistakes are `IllegalArgumentException`; the SPI callbacks that
the JDK and OkHttp define throw, because they must.

| Signature | Failure path | Kind | Where unwrapped |
|---|---|---|---|
| `TransportSettings.Builder.build()` (via `TransportSettings(from, configure)`) | `IllegalArgumentException(<constant naming the property>)`, never a value | programmer error | the caller of the factory (a developer's configuration); nothing catches it |
| `OutboundUrlPolicy.trusted/untrusted(...)` (the four) and their builders' `build()` | `IllegalArgumentException(<constant>)`: empty or out-of-range `allowedPorts`, a pinned host or suffix `canonicalHost` refuses, or (from a `settings { }` block) a settings rule | programmer error | the caller |
| `canonicalPinnedHost`, `canonicalHostSuffix` (internal) | `Result.failure(IllegalArgumentException)` | expected (text from a configuration) | the builder's `build()`: `getOrElse { throw IllegalArgumentException(<constant>) }`, one unwrap point |
| `CidrBlock.parse(text)` | `Result.failure(IllegalArgumentException(<one of four constants>))`, never echoing `text` | expected (text from outside the process) | the caller (`getOrThrow()` in a developer's own configuration, `fold`/`getOrElse` for operator input) |
| `StrictIp.parseV4/parseV6` | `null` | expected (a "not this grammar" answer, not an error) | the callers: `HostRules`, `CidrBlock.parse` |
| `StrictIp.formatV6` | `IllegalArgumentException` for a non-16-byte array | programmer error | none (internal callers pass 16 bytes) |
| `HostRules.classify` | never fails; `Refused(reason)` is a value | expected | `UrlGuard` |
| `AddressRanges.deny` | never fails; `null` allowed, or the denied range; any length other than 4 or 16 is denied | expected | `UrlGuard.checkAddress` |
| `GuardRefusal(reason, rangeClass)` | `IllegalArgumentException` when `rangeClass != null` and `reason != DeniedAddress` | programmer error | none |
| `UrlGuard.checkHop/checkRequest/checkAddress` | `GuardRefusal?` (`null` = allowed) | expected | unit 6: maps a refusal to `RefusedByPolicy(reason)` |
| `UrlGuard.admitRedirect` | `RedirectAdmission.Refused(refusal)` | expected | unit 6 |
| `UrlGuard.judgeAnswer` | `AnswerVerdict.Refused(refusal)` | expected | `GuardedDns.lookup`, which throws the marker (the SPI requires an exception) |
| `GuardedDns.lookup` | delegate's exception unchanged; `UnknownHostException(<constant>)` for an empty answer; `PolicyRefusedUnknownHost` for a refusal | SPI | OkHttp, then unit 6's mapper through `policyRefusal()` |
| `GuardedSocketFactory.createSocket(...)` four overloads | `SocketException(<constant>)` | SPI, programmer error (OkHttp 5.5.0 never calls them) | OkHttp |
| `GuardedSocket.connect` | `PolicyRefusedIo` for a denied, unresolved or non-Inet target; `super.connect`'s `IOException` otherwise | SPI | OkHttp, then unit 6's mapper |
| `Throwable.policyRefusal()` | `null` when none; never throws, never loops | expected | unit 6 |
| `UrlGuard.enforcement(baseDns)` | none (builds three objects) | — | — |

No new `try`/`catch` is added in `commonMain`. `jvmMain` adds none either: the SPI methods throw, they do not catch.

### 6.9 Logging

The classifier and the guards never log a refusal: the site that creates the `RefusedByPolicy` failure holds the `Url` and logs
the refusal once at `WARN` with `Url.redacted()`, the reason name and the range class, and nothing else: the transport (unit 6),
or the call facade's pre-check (unit 9). The events this unit adds:

| Event | Level | Logger | Message and arguments |
|---|---|---|---|
| `GuardedDns` drops entries under `DropDenied` | `DEBUG` | `com.spartanlabs.webtools.http.GuardedDns` | `"dropped {} of {} resolved addresses for {} (range classes: {})"` with the dropped count, the answer size, the host name and the sorted distinct range classes. **Never an address.** One line per lookup that dropped at least one entry; none when nothing was dropped, none under `Reject` |

Nothing is logged at `INFO` or above by this unit, and no `logback` configuration changes (`jvmTest` already has
`logback-test.xml` at `INFO`; the test raises the one logger it captures and restores it in `finally`).

---
## 7. File-by-file changes

Path shorthand: `<cm>` is `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/`, `<jm>` is
`webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/`. Every file below is **new** unless it says *changed*. No
file is removed. All code is in the package `com.spartanlabs.webtools.http`. The KDoc in the blocks is the KDoc to ship (the
implementer may tighten wording, never remove a tier statement, a `@param`/`@return`/`@throws` or a stated number).

### 7.1 `<cm>TransportSettings.kt`

```kotlin
package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The caps, timeouts and concurrency limits of a transport, one set per [OutboundUrlPolicy]: they belong to the policy
 * because `Untrusted`'s caps and timeouts are part of what makes it untrusted.
 *
 * **Stable Core.** Two default sets are supplied, [TrustedDefaults] and [UntrustedDefaults]; derive a modified copy with
 * the factory `TransportSettings(from) { ... }`. Every default number is a judgement, documented here, and **changing one
 * is a behaviour change** for every consumer that did not set it:
 *
 * | Setting | [TrustedDefaults] | [UntrustedDefaults] |
 * |---|---|---|
 * | [callTimeout] (all hops together) | 30 s | 15 s |
 * | [connectTimeout], [readTimeout], [writeTimeout] | 10 s each | 5 s each |
 * | [maxResponseBytes] (decoded bytes) | 5 MiB (5,242,880) | 2 MiB (2,097,152) |
 * | [maxRedirects] | 5 | 3 |
 * | [allowHttpsToHttp] | false | false |
 * | [maxConcurrentRequests], [maxConcurrentRequestsPerHost] | 16, 5 | 8, 2 |
 * | [idleConnectionTtl] | 4 s | 4 s |
 *
 * [callTimeout] spans every hop of a redirected fetch, not each hop. [allowHttpsToHttp] is read by the URL policy when it
 * judges a redirect. Equality is on every setting; `toString()` prints every setting (numbers only).
 */
public class TransportSettings internal constructor(
    /** The whole fetch, redirects included: at least 1 millisecond and at most `Int.MAX_VALUE` milliseconds. */
    public val callTimeout: Duration,
    /** The TCP connect timeout of one attempt; the same bounds as [callTimeout]. */
    public val connectTimeout: Duration,
    /** The socket read timeout; the same bounds as [callTimeout]. */
    public val readTimeout: Duration,
    /** The socket write timeout; the same bounds as [callTimeout]. */
    public val writeTimeout: Duration,
    /** The most decoded body bytes a transport reads; at least 1. */
    public val maxResponseBytes: Long,
    /** The most redirects followed; 0 follows none (a followable redirect beyond the cap is `TooManyRedirects`). */
    public val maxRedirects: Int,
    /** Whether a redirect from `https` to `http` is followed; read by the URL policy. */
    public val allowHttpsToHttp: Boolean,
    /** The most requests in flight; at least 1. */
    public val maxConcurrentRequests: Int,
    /** The most requests in flight to one host; at least 1 and at most [maxConcurrentRequests]. */
    public val maxConcurrentRequestsPerHost: Int,
    /** How long an idle pooled connection is kept; the same bounds as [callTimeout]. */
    public val idleConnectionTtl: Duration,
) {
    override fun equals(other: Any?): Boolean =
        other is TransportSettings &&
            other.callTimeout == callTimeout && other.connectTimeout == connectTimeout &&
            other.readTimeout == readTimeout && other.writeTimeout == writeTimeout &&
            other.maxResponseBytes == maxResponseBytes && other.maxRedirects == maxRedirects &&
            other.allowHttpsToHttp == allowHttpsToHttp && other.maxConcurrentRequests == maxConcurrentRequests &&
            other.maxConcurrentRequestsPerHost == maxConcurrentRequestsPerHost && other.idleConnectionTtl == idleConnectionTtl

    override fun hashCode(): Int {
        var hash = callTimeout.hashCode()
        hash = 31 * hash + connectTimeout.hashCode()
        hash = 31 * hash + readTimeout.hashCode()
        hash = 31 * hash + writeTimeout.hashCode()
        hash = 31 * hash + maxResponseBytes.hashCode()
        hash = 31 * hash + maxRedirects
        hash = 31 * hash + allowHttpsToHttp.hashCode()
        hash = 31 * hash + maxConcurrentRequests
        hash = 31 * hash + maxConcurrentRequestsPerHost
        hash = 31 * hash + idleConnectionTtl.hashCode()
        return hash
    }

    override fun toString(): String =
        "TransportSettings(callTimeout=$callTimeout, connectTimeout=$connectTimeout, readTimeout=$readTimeout, " +
            "writeTimeout=$writeTimeout, maxResponseBytes=$maxResponseBytes, maxRedirects=$maxRedirects, " +
            "allowHttpsToHttp=$allowHttpsToHttp, maxConcurrentRequests=$maxConcurrentRequests, " +
            "maxConcurrentRequestsPerHost=$maxConcurrentRequestsPerHost, idleConnectionTtl=$idleConnectionTtl)"

    /**
     * Collects the settings of a [TransportSettings]: one `var` per setting, seeded from the value the factory was given.
     * Not thread-safe; use it only inside the factory's lambda. A lambda that reads a property before it assigns sees the
     * seed (`maxConcurrentRequests = maxOf(maxConcurrentRequests, 32)`).
     *
     * **Stable Core.**
     */
    public class Builder internal constructor(from: TransportSettings) {
        /** See [TransportSettings.callTimeout]. */
        public var callTimeout: Duration = from.callTimeout
        /** See [TransportSettings.connectTimeout]. */
        public var connectTimeout: Duration = from.connectTimeout
        /** See [TransportSettings.readTimeout]. */
        public var readTimeout: Duration = from.readTimeout
        /** See [TransportSettings.writeTimeout]. */
        public var writeTimeout: Duration = from.writeTimeout
        /** See [TransportSettings.maxResponseBytes]. */
        public var maxResponseBytes: Long = from.maxResponseBytes
        /** See [TransportSettings.maxRedirects]. */
        public var maxRedirects: Int = from.maxRedirects
        /** See [TransportSettings.allowHttpsToHttp]. */
        public var allowHttpsToHttp: Boolean = from.allowHttpsToHttp
        /** See [TransportSettings.maxConcurrentRequests]. */
        public var maxConcurrentRequests: Int = from.maxConcurrentRequests
        /** See [TransportSettings.maxConcurrentRequestsPerHost]. */
        public var maxConcurrentRequestsPerHost: Int = from.maxConcurrentRequestsPerHost
        /** See [TransportSettings.idleConnectionTtl]. */
        public var idleConnectionTtl: Duration = from.idleConnectionTtl

        // Checked in the order of the properties; a message names the property and never a value.
        internal fun build(): TransportSettings {
            requireTimeout("callTimeout", callTimeout)
            requireTimeout("connectTimeout", connectTimeout)
            requireTimeout("readTimeout", readTimeout)
            requireTimeout("writeTimeout", writeTimeout)
            require(maxResponseBytes >= 1) { "maxResponseBytes must be at least 1" }
            require(maxRedirects >= 0) { "maxRedirects must not be negative" }
            require(maxConcurrentRequests >= 1) { "maxConcurrentRequests must be at least 1" }
            require(maxConcurrentRequestsPerHost in 1..maxConcurrentRequests) {
                "maxConcurrentRequestsPerHost must be at least 1 and at most maxConcurrentRequests"
            }
            requireTimeout("idleConnectionTtl", idleConnectionTtl)
            return TransportSettings(
                callTimeout, connectTimeout, readTimeout, writeTimeout, maxResponseBytes, maxRedirects,
                allowHttpsToHttp, maxConcurrentRequests, maxConcurrentRequestsPerHost, idleConnectionTtl,
            )
        }
    }

    /** The two supplied default sets. */
    public companion object {
        /** The defaults for a [OutboundUrlPolicy.Trusted] policy: 30 s, 10 s, 10 s, 10 s, 5 MiB, 5, false, 16, 5, 4 s. */
        public val TrustedDefaults: TransportSettings =
            TransportSettings(30.seconds, 10.seconds, 10.seconds, 10.seconds, 5_242_880L, 5, false, 16, 5, 4.seconds)

        /** The defaults for a [OutboundUrlPolicy.Untrusted] policy: 15 s, 5 s, 5 s, 5 s, 2 MiB, 3, false, 8, 2, 4 s. */
        public val UntrustedDefaults: TransportSettings =
            TransportSettings(15.seconds, 5.seconds, 5.seconds, 5.seconds, 2_097_152L, 3, false, 8, 2, 4.seconds)
    }
}

// 1 ms is the smallest value OkHttp 5.5.0 accepts as a non-zero timeout; Int.MAX_VALUE ms is the largest. A Duration
// compares by value, so Duration.INFINITE (Long.MAX_VALUE ms) and every negative value fail the same test.
private fun requireTimeout(name: String, value: Duration) {
    require(value >= 1.milliseconds && value.inWholeMilliseconds <= Int.MAX_VALUE) {
        "$name must be at least 1 millisecond and at most ${Int.MAX_VALUE} milliseconds"
    }
}

/**
 * Derives a [TransportSettings]: starts from [from], applies [configure], validates.
 *
 * **Stable Core.** `TransportSettings(TransportSettings.UntrustedDefaults) { callTimeout = 20.seconds }` is the
 * `Untrusted` defaults with a longer call timeout. [from] is never modified.
 *
 * @param from the value to start from, usually [TransportSettings.TrustedDefaults] or [TransportSettings.UntrustedDefaults].
 * @param configure assigns the settings to change.
 * @return the new, validated settings.
 * @throws IllegalArgumentException if a setting breaks its rule: a timeout under 1 millisecond or over `Int.MAX_VALUE`
 *   milliseconds; [TransportSettings.maxResponseBytes] under 1; a negative [TransportSettings.maxRedirects];
 *   [TransportSettings.maxConcurrentRequests] under 1; [TransportSettings.maxConcurrentRequestsPerHost] under 1 or over
 *   [TransportSettings.maxConcurrentRequests]. The message names the property and never a value.
 */
public fun TransportSettings(from: TransportSettings, configure: TransportSettings.Builder.() -> Unit): TransportSettings =
    TransportSettings.Builder(from).apply(configure).build()
```

*Failure path:* every rule throws `IllegalArgumentException` with a constant message from `build()` (a developer-written
configuration mistake). No `Result`: the factory is a configuration call, as `HttpRequest(...) { }` is.

### 7.2 `<cm>MixedAnswers.kt` and `<cm>CidrBlock.kt`

```kotlin
package com.spartanlabs.webtools.http

/**
 * What an `Untrusted` policy does with a resolver answer that holds both allowed and denied addresses.
 *
 * **Stable Core.** An enum because it is a consumer-chosen input: the library never branches on a value it did not
 * produce, so a future entry cannot break a producer. A consumer that reads `policy.mixedAnswers` in a `when` keeps an
 * `else`.
 */
public enum class MixedAnswers {
    /** The whole answer is refused when any address is denied (the default). */
    Reject,

    /** The denied addresses are dropped and the allowed ones are used in answer order; the answer is refused only when none is left. */
    DropDenied,
}
```

```kotlin
package com.spartanlabs.webtools.http

// Constant messages, one per kind of problem; none echoes the text.
private const val SHAPE_MESSAGE = "A CIDR block must be written <address>/<prefix>."
private const val ADDRESS_MESSAGE = "The CIDR block address must be a plain dotted-decimal IPv4 or an IPv6 address."
private const val PREFIX_MESSAGE =
    "The CIDR block prefix length must be a decimal number within the address family's width, without a sign or a leading zero."
private const val HOST_BITS_MESSAGE = "The CIDR block has bits set beyond its prefix length."

/**
 * An address block in CIDR notation, for `Untrusted`'s `extraDeniedRanges`.
 *
 * **Stable Core.** Built with [parse]. Equality is on the family, the address bytes and the prefix length;
 * `toString()` is the canonical block (`10.0.0.0/8`, `fd00::/8`). A policy's own `toString()` prints only how many blocks
 * it holds. This class exposes nothing else in this release.
 */
public class CidrBlock private constructor(
    private val bytes: ByteArray,
    private val prefixLength: Int,
) {
    internal val isIpv4: Boolean get() = bytes.size == 4

    // Same family (same width) and the first prefixLength bits equal; judged at the address's full width, so a prefix that
    // is not taken from the input can never shorten the match.
    internal fun contains(address: ByteArray): Boolean = address.size == bytes.size && matchesPrefix(bytes, address, prefixLength)

    override fun equals(other: Any?): Boolean = other is CidrBlock && other.prefixLength == prefixLength && other.bytes.contentEquals(bytes)
    override fun hashCode(): Int = bytes.contentHashCode() * 31 + prefixLength

    // IPv6 is printed by StrictIp.formatV6 (RFC 5952 hex groups; never a dotted IPv4 inside IPv6).
    override fun toString(): String =
        (if (isIpv4) bytes.joinToString(".") { (it.toInt() and 0xFF).toString() } else StrictIp.formatV6(bytes)) + "/" + prefixLength

    /** The parser. */
    public companion object {
        /**
         * Parses [text] as exactly `<address>/<prefix>`: the address strict dotted-decimal (four parts, no leading zero) or
         * strict IPv6 (no brackets, no zone); the prefix 0..32 or 0..128 in decimal without a sign or a leading zero (except
         * `0`). A block with a bit set after the prefix is refused, not masked: `10.0.0.5/8` is a failure.
         *
         * @param text the block, as an operator wrote it.
         * @return the block, or a failure holding an `IllegalArgumentException` with one constant message per kind of
         *   problem (shape, address, prefix, host bits); the message never echoes [text].
         */
        public fun parse(text: String): Result<CidrBlock> {
            val slash = text.indexOf('/')
            if (slash < 0 || text.indexOf('/', slash + 1) >= 0) return failure(SHAPE_MESSAGE)
            val addressText = text.substring(0, slash)
            val bytes = (if (':' in addressText) StrictIp.parseV6(addressText) else StrictIp.parseV4(addressText))
                ?: return failure(ADDRESS_MESSAGE)
            val prefix = parsePrefix(text.substring(slash + 1), bytes.size * 8) ?: return failure(PREFIX_MESSAGE)
            return if (hasBitsAfter(bytes, prefix)) failure(HOST_BITS_MESSAGE) else Result.success(CidrBlock(bytes, prefix))
        }

        private fun failure(message: String): Result<CidrBlock> = Result.failure(IllegalArgumentException(message))

        // 1..3 ASCII digits, no leading zero unless the text is "0", at most the family's width.
        private fun parsePrefix(text: String, width: Int): Int? {
            if (text.isEmpty() || text.length > 3 || !text.all { it in '0'..'9' } || (text.length > 1 && text[0] == '0')) return null
            return text.toInt().takeIf { it <= width }
        }

        private fun hasBitsAfter(bytes: ByteArray, prefix: Int): Boolean {
            for (bit in prefix until bytes.size * 8) {
                if (((bytes[bit / 8].toInt() shr (7 - bit % 8)) and 1) != 0) return true
            }
            return false
        }
    }
}

// The first `prefix` bits of a and b are equal (a and b have the same length, at least prefix / 8 + 1 when prefix % 8 != 0).
private fun matchesPrefix(a: ByteArray, b: ByteArray, prefix: Int): Boolean {
    val whole = prefix / 8
    for (i in 0 until whole) if (a[i] != b[i]) return false
    val rest = prefix % 8
    if (rest == 0) return true
    val mask = (0xFF shl (8 - rest)) and 0xFF
    return (a[whole].toInt() and mask) == (b[whole].toInt() and mask)
}
```

*Failure path:* `parse` returns `Result.failure(IllegalArgumentException(<constant>))` in the order shape (not exactly one
`/`), address (the text before the `/`, an empty one included), prefix (the text after it, an empty one included), host bits;
never throws. Unwrapped by the caller.

### 7.3 `<cm>OutboundUrlPolicy.kt`

```kotlin
package com.spartanlabs.webtools.http

// The default Untrusted port set. A top-level value of this file, not state of the sealed base's companion: a policy
// instance held there would make the base's class initialiser construct a subclass (a class-initialisation cycle).
private val DEFAULT_UNTRUSTED_PORTS: Set<Int> = setOf(80, 443, 8080, 8443)

// Constant messages naming the property; none echoes a value.
private const val PINNED_HOST_MESSAGE = "pinnedHosts must hold only host names or IP literals, without a port, path or user-info."
private const val SUFFIX_MESSAGE =
    "extraDeniedHostSuffixes must hold only dotted host-name suffixes (no IP literal, no leading or trailing dot)."
private const val PORTS_EMPTY_MESSAGE = "allowedPorts must not be empty; use null to allow any port."
private const val PORTS_RANGE_MESSAGE = "allowedPorts must hold only ports from 1 to 65535."

/**
 * Which URLs and addresses a fetch may reach, and the [TransportSettings] that go with that choice. A closed pair:
 * [Trusted] for a known upstream, [Untrusted] for a URL a third party supplied.
 *
 * **Stable Core.** `sealed` because the closed pair is the contract; build one with [trusted] or [untrusted]. There is no
 * parameter that lowers `Untrusted`'s protection: to fetch from a private address use [Trusted]. The policy decides; a
 * transport enforces. A `when` over a policy needs no `else`.
 */
public sealed class OutboundUrlPolicy {
    /** The caps, timeouts and concurrency limits that go with this policy. */
    public abstract val settings: TransportSettings

    /**
     * The policy for a known upstream: it checks the scheme, optionally pins the host, and applies the downgrade rule. It
     * does no address filtering, has no name rule and no port rule, so a private address is fetchable.
     *
     * **Stable Core.** Equality is on every property. `toString()` prints the number of pinned hosts, never a host.
     */
    public class Trusted internal constructor(
        /** The hosts a hop may reach, canonical (lower-case; IPv6 without brackets); empty means any host. Exact match with `Url.host`. */
        public val pinnedHosts: Set<String>,
        /** Whether `http` is allowed; false means `https` only. */
        public val allowCleartext: Boolean,
        override val settings: TransportSettings,
    ) : OutboundUrlPolicy() {
        override fun equals(other: Any?): Boolean =
            other is Trusted && other.pinnedHosts == pinnedHosts && other.allowCleartext == allowCleartext && other.settings == settings

        override fun hashCode(): Int = (pinnedHosts.hashCode() * 31 + allowCleartext.hashCode()) * 31 + settings.hashCode()

        override fun toString(): String = "Trusted(pinnedHosts=${pinnedHosts.size}, allowCleartext=$allowCleartext, settings=$settings)"

        /**
         * Collects the properties of a [Trusted]. Not thread-safe; use it only inside the factory's lambda. A property holds
         * the caller's reference until the policy is built, which copies it.
         *
         * **Stable Core.**
         */
        public class Builder internal constructor(from: Trusted) {
            /**
             * The hosts to pin, as host names or IP literals (an IPv6 literal with or without brackets); empty means any host.
             * Canonicalised when the policy is built: lower-cased, IPv6 without brackets.
             */
            public var pinnedHosts: Set<String> = from.pinnedHosts

            /** Whether `http` is allowed; false (the default) means `https` only. */
            public var allowCleartext: Boolean = from.allowCleartext

            /** The settings as a whole value; the default is [TransportSettings.TrustedDefaults]. */
            public var settings: TransportSettings = from.settings

            /**
             * Changes [settings] with a block that starts from its current value. The order matters: `settings = X` followed
             * by `settings { }` modifies `X`.
             *
             * @param configure assigns the settings to change.
             * @throws IllegalArgumentException as [TransportSettings] does.
             */
            public fun settings(configure: TransportSettings.Builder.() -> Unit) {
                settings = TransportSettings(settings, configure)
            }

            internal fun build(): Trusted {
                val hosts = pinnedHosts.map { canonicalPinnedHost(it).getOrElse { throw IllegalArgumentException(PINNED_HOST_MESSAGE) } }
                return Trusted(hosts.toSet(), allowCleartext, settings)
            }
        }
    }

    /**
     * The policy for a URL a third party supplied. Per hop it refuses a lenient or malformed host form, a reserved name, a
     * denied IP literal and a port outside [allowedPorts]; for every resolved address and every connect it refuses a denied
     * address; it never uses a proxy; and it refuses a request that carries a sensitive header (`Authorization`,
     * `Proxy-Authorization`, `Cookie`, `Set-Cookie` or any header added as sensitive).
     *
     * **Stable Core.** Every parameter can only add a denial. Equality is on every property. `toString()` prints the port
     * set and the number of denied ranges and suffixes, never their values.
     */
    public class Untrusted internal constructor(
        /** The ports a hop may use; `null` means any port. The default is 80, 443, 8080 and 8443. */
        public val allowedPorts: Set<Int>?,
        /**
         * Address blocks denied in addition to the built-in table. An IPv4 block also matches the IPv4 address embedded in
         * an IPv4-mapped or NAT64 well-known (`64:ff9b::/96`) address; an IPv6 block matches IPv6 addresses. A deployment
         * behind its own NAT64 prefix, or with an internal service on a public address, adds it here: neither is
         * knowable to a library.
         */
        public val extraDeniedRanges: Set<CidrBlock>,
        /** Host-name suffixes refused as a reserved name, canonical (lower-case): the name itself and its subdomains. */
        public val extraDeniedHostSuffixes: Set<String>,
        /** How a resolver answer with both allowed and denied addresses is treated; `Reject` by default. */
        public val mixedAnswers: MixedAnswers,
        override val settings: TransportSettings,
    ) : OutboundUrlPolicy() {
        override fun equals(other: Any?): Boolean =
            other is Untrusted && other.allowedPorts == allowedPorts && other.extraDeniedRanges == extraDeniedRanges &&
                other.extraDeniedHostSuffixes == extraDeniedHostSuffixes && other.mixedAnswers == mixedAnswers &&
                other.settings == settings

        override fun hashCode(): Int {
            var hash = allowedPorts.hashCode()
            hash = 31 * hash + extraDeniedRanges.hashCode()
            hash = 31 * hash + extraDeniedHostSuffixes.hashCode()
            hash = 31 * hash + mixedAnswers.hashCode()
            hash = 31 * hash + settings.hashCode()
            return hash
        }

        override fun toString(): String =
            "Untrusted(allowedPorts=${allowedPorts?.sorted() ?: "any"}, extraDeniedRanges=${extraDeniedRanges.size}, " +
                "extraDeniedHostSuffixes=${extraDeniedHostSuffixes.size}, mixedAnswers=$mixedAnswers, settings=$settings)"

        /**
         * Collects the properties of an [Untrusted]. Not thread-safe; use it only inside the factory's lambda. Extend a set
         * with `+=` (`extraDeniedRanges += block`).
         *
         * **Stable Core.**
         */
        public class Builder internal constructor(from: Untrusted) {
            /** The allowed ports: `null` for any port, otherwise a non-empty set of ports 1..65535. The default is 80, 443, 8080, 8443. */
            public var allowedPorts: Set<Int>? = from.allowedPorts

            /** Address blocks denied in addition to the built-in table; empty by default. See [Untrusted.extraDeniedRanges]. */
            public var extraDeniedRanges: Set<CidrBlock> = from.extraDeniedRanges

            /**
             * Host-name suffixes to refuse as reserved names (`corp.example` refuses `corp.example` and `a.corp.example`);
             * empty by default. Canonicalised when the policy is built: lower-case, no leading or trailing dot, not an IP literal.
             */
            public var extraDeniedHostSuffixes: Set<String> = from.extraDeniedHostSuffixes

            /** How a mixed resolver answer is treated; [MixedAnswers.Reject] by default. */
            public var mixedAnswers: MixedAnswers = from.mixedAnswers

            /** The settings as a whole value; the default is [TransportSettings.UntrustedDefaults]. */
            public var settings: TransportSettings = from.settings

            /**
             * Changes [settings] with a block that starts from its current value; the order matters, as in [Trusted.Builder.settings].
             *
             * @param configure assigns the settings to change.
             * @throws IllegalArgumentException as [TransportSettings] does.
             */
            public fun settings(configure: TransportSettings.Builder.() -> Unit) {
                settings = TransportSettings(settings, configure)
            }

            internal fun build(): Untrusted {
                val ports = allowedPorts?.let { checkedPorts(it) }
                val suffixes = extraDeniedHostSuffixes.map { canonicalHostSuffix(it).getOrElse { throw IllegalArgumentException(SUFFIX_MESSAGE) } }
                return Untrusted(ports, extraDeniedRanges.toSet(), suffixes.toSet(), mixedAnswers, settings)
            }
        }
    }

    /** The factories. */
    public companion object {
        /**
         * Builds a [Trusted] policy from the defaults (no pins, `https` only, [TransportSettings.TrustedDefaults]) and [configure].
         *
         * @param configure assigns the properties to change; empty by default, for the defaults.
         * @return the policy.
         * @throws IllegalArgumentException if a pinned host is not a host name or IP literal, or a `settings { }` block breaks a settings rule.
         */
        public fun trusted(configure: Trusted.Builder.() -> Unit = {}): Trusted =
            trusted(Trusted(emptySet(), false, TransportSettings.TrustedDefaults), configure)

        /**
         * Builds a [Trusted] policy that starts from [from].
         *
         * @param from the policy to start from; never modified.
         * @param configure assigns the properties to change.
         * @return the new policy.
         * @throws IllegalArgumentException as the other overload does.
         */
        public fun trusted(from: Trusted, configure: Trusted.Builder.() -> Unit): Trusted =
            Trusted.Builder(from).apply(configure).build()

        /**
         * Builds an [Untrusted] policy from the defaults (ports 80, 443, 8080, 8443; no extra ranges or suffixes;
         * [MixedAnswers.Reject]; [TransportSettings.UntrustedDefaults]) and [configure].
         *
         * @param configure assigns the properties to change; empty by default, for the defaults.
         * @return the policy.
         * @throws IllegalArgumentException if `allowedPorts` is empty or holds a port outside 1..65535, a suffix is not a dotted host-name suffix, or a `settings { }` block breaks a settings rule.
         */
        public fun untrusted(configure: Untrusted.Builder.() -> Unit = {}): Untrusted =
            untrusted(Untrusted(DEFAULT_UNTRUSTED_PORTS, emptySet(), emptySet(), MixedAnswers.Reject, TransportSettings.UntrustedDefaults), configure)

        /**
         * Builds an [Untrusted] policy that starts from [from].
         *
         * @param from the policy to start from; never modified.
         * @param configure assigns the properties to change.
         * @return the new policy.
         * @throws IllegalArgumentException as the other overload does.
         */
        public fun untrusted(from: Untrusted, configure: Untrusted.Builder.() -> Unit): Untrusted =
            Untrusted.Builder(from).apply(configure).build()
    }
}

private fun checkedPorts(ports: Set<Int>): Set<Int> {
    require(ports.isNotEmpty()) { PORTS_EMPTY_MESSAGE }
    require(ports.all { it in 1..65535 }) { PORTS_RANGE_MESSAGE }
    return ports.toSet()
}

// A pinned host as canonicalHost reads it. An entry holding ':' is an IPv6 literal, with or without brackets (a lone
// bracket is refused); anything else is a name. The result is what Url.host would print for the same host.
internal fun canonicalPinnedHost(entry: String): Result<String> {
    val opens = entry.startsWith('[')
    if (opens != entry.endsWith(']')) return Result.failure(IllegalArgumentException(PINNED_HOST_MESSAGE))
    val inner = if (opens) entry.substring(1, entry.length - 1) else entry
    return when {
        ':' in inner -> canonicalHost("[$inner]")
        opens -> Result.failure(IllegalArgumentException(PINNED_HOST_MESSAGE))
        else -> canonicalHost(inner)
    }
}

// A suffix is a dotted name: refused when empty, when it holds ':', '[' or ']', starts or ends with '.', is digits and dots
// only, or fails canonicalHost. A single label ("corp") is allowed.
internal fun canonicalHostSuffix(entry: String): Result<String> {
    val refused = entry.isEmpty() || entry.any { it == ':' || it == '[' || it == ']' } || entry.startsWith('.') ||
        entry.endsWith('.') || entry.all { it == '.' || it in '0'..'9' }
    return if (refused) Result.failure(IllegalArgumentException(SUFFIX_MESSAGE)) else canonicalHost(entry)
}

// Derives a policy with other settings through the from factories; the call facades use it to raise concurrency to the
// budget's burst. The parameter is copied so the lambdas' `settings` is the builder's property.
internal fun OutboundUrlPolicy.withSettings(settings: TransportSettings): OutboundUrlPolicy {
    val replacement = settings
    return when (this) {
        is OutboundUrlPolicy.Trusted -> OutboundUrlPolicy.trusted(this) { this.settings = replacement }
        is OutboundUrlPolicy.Untrusted -> OutboundUrlPolicy.untrusted(this) { this.settings = replacement }
    }
}
```

*Failure path:* every builder rule throws `IllegalArgumentException(<constant>)` from `build()`; `canonicalPinnedHost` and
`canonicalHostSuffix` return `Result.failure` and are unwrapped at one point each (`getOrElse { throw ... }`), so the rule
lives once and the throw once. Within `withSettings`, `this.settings` is the lambda's `Builder` receiver (its innermost `this`).

### 7.4 `<cm>StrictIp.kt`

```kotlin
package com.spartanlabs.webtools.http

/**
 * Strict IP-literal text to bytes and back. Strict means one spelling per address: no short or numeric IPv4 form (`127.1`,
 * `2130706433`), no leading zero, no hex or octal, no brackets, no zone. Pure, stateless, JVM-free.
 */
internal object StrictIp {
    /**
     * Exactly four decimal parts, each 1..3 ASCII digits, no leading zero unless the part is `0`, at most 255.
     *
     * @return the four bytes, or `null` when [text] is not that grammar.
     */
    fun parseV4(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for (i in 0 until 4) out[i] = (v4Part(parts[i]) ?: return null).toByte()
        return out
    }

    private fun v4Part(part: String): Int? {
        if (part.length !in 1..3 || !part.all { it in '0'..'9' } || (part.length > 1 && part[0] == '0')) return null
        return part.toInt().takeIf { it <= 255 }
    }

    /**
     * RFC 4291 text: groups of 1..4 hex digits (either case), at most one `::` standing for at least one group, an optional
     * strict dotted quad as the last 32 bits, no brackets, no zone.
     *
     * @return the 16 bytes (an IPv4-mapped literal stays 16 bytes), or `null` when [text] is not that grammar.
     */
    fun parseV6(text: String): ByteArray? {
        if (':' !in text || !text.all { it == ':' || it == '.' || isHex(it) }) return null
        val gap = text.indexOf("::")
        if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) return null
        val head = groupsOf(if (gap >= 0) text.substring(0, gap) else text, quadAllowedLast = gap < 0) ?: return null
        val tail = if (gap >= 0) { groupsOf(text.substring(gap + 2), quadAllowedLast = true) ?: return null } else emptyList()
        val explicit = head.size + tail.size
        val groups = when {
            gap >= 0 && explicit <= 7 -> head + List(8 - explicit) { 0 } + tail
            gap < 0 && explicit == 8 -> head
            else -> return null
        }
        return ByteArray(16) { i -> (if (i % 2 == 0) groups[i / 2] shr 8 else groups[i / 2]).toByte() }
    }

    // The groups of a ':'-separated run; a dotted quad may be the last part only when quadAllowedLast, and counts as two.
    private fun groupsOf(text: String, quadAllowedLast: Boolean): List<Int>? {
        if (text.isEmpty()) return emptyList()
        val parts = text.split(':')
        val out = ArrayList<Int>(parts.size + 1)
        for (index in parts.indices) {
            val part = parts[index]
            if (quadAllowedLast && index == parts.lastIndex && '.' in part) {
                val quad = parseV4(part) ?: return null
                out.add(((quad[0].toInt() and 0xFF) shl 8) or (quad[1].toInt() and 0xFF))
                out.add(((quad[2].toInt() and 0xFF) shl 8) or (quad[3].toInt() and 0xFF))
            } else {
                out.add(hexGroup(part) ?: return null)
            }
        }
        return out
    }

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun hexGroup(part: String): Int? = if (part.length in 1..4 && part.all { isHex(it) }) part.toInt(16) else null

    /**
     * RFC 5952: lower case, no leading zeros, the first longest run of two or more zero groups as `::`, never a dotted IPv4
     * (so `::ffff:7f00:1`, not `::ffff:127.0.0.1`).
     *
     * @param bytes exactly 16 bytes.
     * @return the text.
     * @throws IllegalArgumentException if [bytes] is not 16 bytes long (a programmer error).
     */
    fun formatV6(bytes: ByteArray): String {
        require(bytes.size == 16) { "An IPv6 address has 16 bytes." }
        val g = IntArray(8) { ((bytes[2 * it].toInt() and 0xFF) shl 8) or (bytes[2 * it + 1].toInt() and 0xFF) }
        var bestStart = -1
        var bestLength = 0
        var i = 0
        while (i < 8) {
            if (g[i] != 0) { i++; continue }
            var j = i
            while (j < 8 && g[j] == 0) j++
            if (j - i > bestLength) { bestStart = i; bestLength = j - i }
            i = j
        }
        fun hex(range: IntRange): String = range.joinToString(":") { g[it].toString(16) }
        return if (bestLength < 2) hex(0..7) else hex(0 until bestStart) + "::" + hex((bestStart + bestLength) until 8)
    }
}
```

### 7.5 `<cm>HostRules.kt`

```kotlin
package com.spartanlabs.webtools.http

/** What [HostRules.classify] says about a host. */
internal sealed class HostVerdict {
    /** The host is a strict IP literal; judge [address] (4 bytes, or 16 for IPv6) with the address table. */
    class Literal(val address: ByteArray) : HostVerdict()

    /** A name no host rule fires on; the resolver guard judges what it resolves to. */
    object Name : HostVerdict()

    /** The host is refused for [reason]: `HostForm` or `ReservedName`. */
    class Refused(val reason: RefusalReason) : HostVerdict()
}

/** The host rules of the `Untrusted` policy, as pure functions over strings. */
internal object HostRules {
    // localhost names are loopback by RFC 6761; .local is mDNS; .internal and home.arpa are reserved for private use. These
    // rules are fail-fast and diagnostic: the resolver guard denies whatever they resolve to.
    private val RESERVED: List<String> = listOf("localhost", "local", "internal", "home.arpa", "localdomain")

    /**
     * Classifies [host]; total on any string. Letters are folded ASCII-only first. In order: empty or a character outside
     * `a-z 0-9 - _ . :` is `HostForm`; with `:` a strict IPv6 literal or `HostForm`; digits and dots only a strict dotted
     * quad or `HostForm`; an empty label (after removing one trailing dot) is `HostForm`; a last label that is all digits or
     * `0x` plus zero or more hex digits is `HostForm`; a single label, a reserved name or subdomain, or a policy suffix or
     * subdomain is `ReservedName`; anything else is a [HostVerdict.Name].
     *
     * @param host the host text; `Url.host`, or any string a test feeds directly.
     * @param extraSuffixes the policy's canonical suffixes.
     * @return the verdict.
     */
    fun classify(host: String, extraSuffixes: Set<String>): HostVerdict {
        val text = buildString(host.length) { host.forEach { append(HttpSyntax.asciiLower(it)) } }
        if (text.isEmpty() || !text.all { isHostChar(it) }) return refused(RefusalReason.HostForm)
        if (':' in text) return StrictIp.parseV6(text)?.let { HostVerdict.Literal(it) } ?: refused(RefusalReason.HostForm)
        if (text.all { it == '.' || it in '0'..'9' }) {
            return StrictIp.parseV4(text)?.let { HostVerdict.Literal(it) } ?: refused(RefusalReason.HostForm)
        }
        val name = text.removeSuffix(".")
        val labels = name.split('.')
        if (labels.any { it.isEmpty() }) return refused(RefusalReason.HostForm)
        if (endsInNumber(labels.last())) return refused(RefusalReason.HostForm)
        val reserved = labels.size == 1 || RESERVED.any { matchesSuffix(name, it) } || extraSuffixes.any { matchesSuffix(name, it) }
        return if (reserved) refused(RefusalReason.ReservedName) else HostVerdict.Name
    }

    /** True when [name] is [suffix] or ends with `.` plus [suffix] (a label boundary). */
    fun matchesSuffix(name: String, suffix: String): Boolean = name == suffix || name.endsWith(".$suffix")

    private fun refused(reason: RefusalReason): HostVerdict = HostVerdict.Refused(reason)

    private fun isHostChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == ':'

    // The WHATWG "ends in a number" test: all ASCII digits, or "0x" followed by zero or more hex digits (zero counts).
    private fun endsInNumber(label: String): Boolean =
        label.all { it in '0'..'9' } || (label.startsWith("0x") && label.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
}
```

### 7.6 `<cm>AddressRanges.kt`

```kotlin
package com.spartanlabs.webtools.http

/** The class of a denied address: a short kebab-case name used only in logs and tests. */
internal class DeniedRange(val rangeClass: String)

// <the snapshot comment of §6.4, verbatim, goes here>

/** The address table: which 4- or 16-byte addresses the `Untrusted` policy denies. Pure, immutable, JVM-free. */
internal object AddressRanges {
    private class Row(val block: CidrBlock, val denied: DeniedRange)

    // A malformed literal in this table is a programmer error: getOrThrow fails the object's initialisation on the first use,
    // which the first test catches.
    private fun row(cidr: String, rangeClass: String): Row = Row(CidrBlock.parse(cidr).getOrThrow(), DeniedRange(rangeClass))

    // Most specific first, so a nested row logs its own class (a first-match table over the parent would log the parent's).
    private val V4_ROWS: List<Row> = listOf(
        row("0.0.0.0/32", "this-host"),
        row("192.0.0.8/32", "ipv4-dummy-address"),
        row("192.0.0.9/32", "pcp-anycast"),
        row("192.0.0.10/32", "turn-anycast"),
        row("192.0.0.170/32", "nat64-dns64-discovery"),
        row("192.0.0.171/32", "nat64-dns64-discovery"),
        row("192.0.0.0/29", "ipv4-service-continuity-prefix"),
        row("192.88.99.2/32", "6a44-relay-anycast"),
        row("255.255.255.255/32", "limited-broadcast"),
        row("0.0.0.0/8", "this-network"),
        row("10.0.0.0/8", "private-use"),
        row("100.64.0.0/10", "shared-address-space"),
        row("127.0.0.0/8", "loopback"),
        row("169.254.0.0/16", "link-local"),
        row("172.16.0.0/12", "private-use"),
        row("192.0.0.0/24", "ietf-protocol-assignments"),
        row("192.0.2.0/24", "documentation"),
        row("192.31.196.0/24", "as112-v4"),
        row("192.52.193.0/24", "amt"),
        row("192.88.99.0/24", "deprecated-6to4-relay-anycast"),
        row("192.168.0.0/16", "private-use"),
        row("192.175.48.0/24", "direct-delegation-as112"),
        row("198.18.0.0/15", "benchmarking"),
        row("198.51.100.0/24", "documentation"),
        row("203.0.113.0/24", "documentation"),
        row("240.0.0.0/4", "reserved"),
        // Not registry rows. Multicast: IPv4 Multicast Address Space registry (RFC 5771), adjacent to 240.0.0.0/4.
        row("224.0.0.0/4", "multicast"),
        // The Azure platform endpoint: a public address in no IANA row, port 80, listed by both SSRF catalogues. A refresh must keep it.
        row("168.63.129.16/32", "azure-wireserver"),
    )

    // Inside 2000::/3 only; the nested rows of 2001::/23 first.
    private val V6_ROWS: List<Row> = listOf(
        row("2001::/32", "teredo"),
        row("2001:1::1/128", "pcp-anycast"),
        row("2001:1::2/128", "turn-anycast"),
        row("2001:1::3/128", "dns-sd-srp-anycast"),
        row("2001:2::/48", "benchmarking"),
        row("2001:3::/32", "amt"),
        row("2001:4:112::/48", "as112-v6"),
        row("2001:10::/28", "deprecated-orchid"),
        row("2001:20::/28", "orchidv2"),
        row("2001:30::/28", "drone-remote-id-det"),
        row("2001::/23", "ietf-protocol-assignments"),
        row("2001:db8::/32", "documentation"),
        row("2002::/16", "6to4"),
        row("2620:4f:8000::/48", "direct-delegation-as112"),
        row("3fff::/20", "documentation"),
    )

    private val UNUSUAL_LENGTH = DeniedRange("unusual-length")
    private val NOT_GLOBAL_UNICAST = DeniedRange("not-global-unicast")
    private val ISATAP = DeniedRange("isatap")

    /**
     * Judges one address.
     *
     * @param address 4 bytes (IPv4) or 16 bytes (IPv6); any other length is denied (`unusual-length`): a custom `InetAddress`
     *   or a corrupt array fails closed.
     * @return `null` when allowed, otherwise the matched row's class. Never throws.
     */
    fun deny(address: ByteArray): DeniedRange? = when (address.size) {
        4 -> denyV4(address)
        16 -> denyV6(address)
        else -> UNUSUAL_LENGTH
    }

    /** True for a 16-byte IPv4-mapped address `::ffff:0:0/96`. */
    fun isMapped(a: ByteArray): Boolean =
        a.size == 16 && (0..9).all { a[it].toInt() == 0 } && (a[10].toInt() and 0xFF) == 0xFF && (a[11].toInt() and 0xFF) == 0xFF

    /** True for a 16-byte address in the NAT64 well-known prefix `64:ff9b::/96`. */
    fun isNat64WellKnown(a: ByteArray): Boolean =
        a.size == 16 && a[0].toInt() == 0 && a[1].toInt() == 0x64 && (a[2].toInt() and 0xFF) == 0xFF &&
            (a[3].toInt() and 0xFF) == 0x9B && (4..11).all { a[it].toInt() == 0 }

    /** The IPv4 address embedded in a mapped or NAT64 well-known address (its low 4 bytes), otherwise `null`. */
    fun embeddedV4(a: ByteArray): ByteArray? = if (isMapped(a) || isNat64WellKnown(a)) a.copyOfRange(12, 16) else null

    private fun denyV4(a: ByteArray): DeniedRange? = V4_ROWS.firstOrNull { it.block.contains(a) }?.denied

    private fun denyV6(a: ByteArray): DeniedRange? {
        embeddedV4(a)?.let { return denyV4(it) }
        if ((a[0].toInt() and 0xE0) != 0x20) return NOT_GLOBAL_UNICAST
        V6_ROWS.firstOrNull { it.block.contains(a) }?.let { return it.denied }
        return if (isIsatap(a)) ISATAP else null
    }

    // An ISATAP interface identifier (RFC 5214): groups 4 and 5 (bytes 8..11) are 0000:5efe or 0200:5efe, the mask ignoring the
    // universal/local and group bits. It embeds an IPv4 address a host with an ISATAP interface tunnels to.
    private fun isIsatap(a: ByteArray): Boolean {
        val group4 = ((a[8].toInt() and 0xFF) shl 8) or (a[9].toInt() and 0xFF)
        val group5 = ((a[10].toInt() and 0xFF) shl 8) or (a[11].toInt() and 0xFF)
        return (group4 and 0xFCFF) == 0 && group5 == 0x5EFE
    }
}
```

### 7.7 `<cm>GuardRelaxation.kt` and `<cm>UrlGuard.kt`

```kotlin
package com.spartanlabs.webtools.http

/**
 * A relaxation of the address and name rules for tests. [None] changes nothing; [LoopbackForTests] exempts loopback only:
 * the host `localhost` from the reserved-name rule, the addresses `127.0.0.0/8` and `::1` (and a mapped form of a
 * `127.0.0.0/8` address) from the address table, and the port rule for the hosts `localhost`, `127.0.0.1` and `::1`; every
 * other rule, `extraDeniedRanges` and the policy's suffixes included, stays live. `internal` and reachable only from the test
 * fixtures and this module's tests: the main artifact has no way to switch the address guard off.
 */
internal class GuardRelaxation private constructor(val allowLoopback: Boolean) {
    companion object {
        /** No relaxation: every rule applies. */
        val None: GuardRelaxation = GuardRelaxation(false)

        /** Exempts exactly the host `localhost`, the loopback addresses and the port rule for the hosts `localhost`, `127.0.0.1` and `::1`. */
        val LoopbackForTests: GuardRelaxation = GuardRelaxation(true)
    }
}
```

```kotlin
package com.spartanlabs.webtools.http

/**
 * A refusal: the coarse [reason] and, for `DeniedAddress` only, the matched range's [rangeClass] (for logs; never an
 * address or a range). Value equality; `toString()` prints the reason only.
 */
internal class GuardRefusal(val reason: RefusalReason, val rangeClass: String?) {
    init {
        require(rangeClass == null || reason == RefusalReason.DeniedAddress) { "A range class belongs to DeniedAddress only." }
    }

    override fun equals(other: Any?): Boolean = other is GuardRefusal && other.reason == reason && other.rangeClass == rangeClass
    override fun hashCode(): Int = reason.hashCode() * 31 + (rangeClass?.hashCode() ?: 0)
    override fun toString(): String = "GuardRefusal(${reason.name})"
}

/** The outcome of [UrlGuard.admitRedirect]. */
internal sealed class RedirectAdmission {
    /** The target parsed and passed every rule; follow [url]. */
    class Admitted(val url: Url) : RedirectAdmission()

    /** The target was refused for [refusal]. */
    class Refused(val refusal: GuardRefusal) : RedirectAdmission()
}

/** The outcome of [UrlGuard.judgeAnswer]. */
internal sealed class AnswerVerdict {
    /** Use the addresses at [indices], in answer order. Empty only for an empty answer. */
    class Allowed(val indices: List<Int>) : AnswerVerdict()

    /** The answer is refused for [refusal]. */
    class Refused(val refusal: GuardRefusal) : AnswerVerdict()
}

/**
 * Applies a policy to a hop, a request, a redirect target and a resolved address. Pure and immutable after construction: no
 * I/O, safe to share between threads, never logs.
 */
internal class UrlGuard(val policy: OutboundUrlPolicy, val relaxation: GuardRelaxation = GuardRelaxation.None) {

    /**
     * Judges one hop, in the order scheme, user-info, host form, IP literal, reserved name, port, pin, downgrade; the first
     * refusal wins. The user-info step is a no-op here because a [Url] never carries any.
     *
     * @param url the hop's URL.
     * @param previous the previous hop's URL, or `null` for the first hop.
     * @return the refusal, or `null` when the hop is allowed.
     */
    fun checkHop(url: Url, previous: Url?): GuardRefusal? = when (policy) {
        is OutboundUrlPolicy.Trusted -> checkTrustedHop(policy, url, previous)
        is OutboundUrlPolicy.Untrusted -> checkUntrustedHop(policy, url, previous)
    }

    /**
     * Judges a request before hop 1: [checkHop] of its URL, then, for `Untrusted`, any header with `isSensitive`.
     *
     * @return the refusal, or `null` when the request may be sent.
     */
    fun checkRequest(request: HttpRequest): GuardRefusal? =
        checkHop(request.url, null) ?: if (policy is OutboundUrlPolicy.Untrusted && request.headers.entries().any { it.isSensitive }) {
            GuardRefusal(RefusalReason.SensitiveHeader, null)
        } else {
            null
        }

    /**
     * Judges a redirect target the transport resolved.
     *
     * @param target the absolute target text, as `HttpUrl.resolve` printed it.
     * @param previous the hop that returned the redirect.
     * @return [RedirectAdmission.Admitted] with the parsed URL, or [RedirectAdmission.Refused]: a parse failure maps
     *   `UserInfo` to `UserInfo`, `Scheme` to `Scheme`, `Port` to `Port` and every other problem to `HostForm`.
     */
    fun admitRedirect(target: String, previous: Url): RedirectAdmission =
        Url.parse(target).fold(
            onSuccess = { url -> checkHop(url, previous)?.let { RedirectAdmission.Refused(it) } ?: RedirectAdmission.Admitted(url) },
            onFailure = { RedirectAdmission.Refused(GuardRefusal(parseRefusal(it), null)) },
        )

    /**
     * Judges one resolved or connected address.
     *
     * @param address the address bytes (4 or 16; any other length is denied).
     * @return `null` for `Trusted` (always) or an allowed address; otherwise `DeniedAddress` with the range class.
     */
    fun checkAddress(address: ByteArray): GuardRefusal? = when (policy) {
        is OutboundUrlPolicy.Trusted -> null
        is OutboundUrlPolicy.Untrusted -> checkUntrustedAddress(policy, address)
    }

    /**
     * Judges a whole resolver answer. `Reject` refuses it at its first denied address; `DropDenied` keeps the allowed
     * indices in order and refuses (with the first refusal) when none is left; `Trusted` allows every index. An empty list is
     * `Allowed(emptyList())`: not the guard's to judge.
     */
    fun judgeAnswer(addresses: List<ByteArray>): AnswerVerdict {
        if (addresses.isEmpty()) return AnswerVerdict.Allowed(emptyList())
        val refusals = addresses.map { checkAddress(it) }
        val allowed = refusals.indices.filter { refusals[it] == null }
        val first = refusals.firstOrNull { it != null }
        return when {
            first == null -> AnswerVerdict.Allowed(addresses.indices.toList())
            policy is OutboundUrlPolicy.Untrusted && policy.mixedAnswers == MixedAnswers.DropDenied && allowed.isNotEmpty() ->
                AnswerVerdict.Allowed(allowed)
            else -> AnswerVerdict.Refused(first)
        }
    }

    //region Trusted
    private fun checkTrustedHop(policy: OutboundUrlPolicy.Trusted, url: Url, previous: Url?): GuardRefusal? = when {
        url.scheme == "http" && !policy.allowCleartext -> GuardRefusal(RefusalReason.Scheme, null)
        policy.pinnedHosts.isNotEmpty() && url.host !in policy.pinnedHosts -> GuardRefusal(RefusalReason.HostNotPinned, null)
        else -> checkDowngrade(policy, url, previous)
    }
    //endregion

    //region Untrusted
    private fun checkUntrustedHop(policy: OutboundUrlPolicy.Untrusted, url: Url, previous: Url?): GuardRefusal? {
        // The relaxation exempts the host exactly "localhost" from the reserved-name rule, unless a policy suffix still matches it.
        val exemptLocalhost = relaxation.allowLoopback && url.host == LOCALHOST &&
            policy.extraDeniedHostSuffixes.none { HostRules.matchesSuffix(LOCALHOST, it) }
        val verdict = if (exemptLocalhost) HostVerdict.Name else HostRules.classify(url.host, policy.extraDeniedHostSuffixes)
        val byHost = when (verdict) {
            is HostVerdict.Refused -> GuardRefusal(verdict.reason, null)
            is HostVerdict.Literal -> checkUntrustedAddress(policy, verdict.address)
            is HostVerdict.Name -> null
        }
        return byHost ?: checkPort(policy, url) ?: checkDowngrade(policy, url, previous)
    }

    private fun checkPort(policy: OutboundUrlPolicy.Untrusted, url: Url): GuardRefusal? {
        val ports = policy.allowedPorts ?: return null
        if (url.port in ports) return null
        return if (relaxation.allowLoopback && url.host in LOOPBACK_HOSTS) null else GuardRefusal(RefusalReason.Port, null)
    }

    private fun checkUntrustedAddress(policy: OutboundUrlPolicy.Untrusted, address: ByteArray): GuardRefusal? {
        // The relaxation exempts the table for loopback only; extraDeniedRanges stay live.
        val byTable = if (relaxation.allowLoopback && isLoopback(address)) null else AddressRanges.deny(address)
        if (byTable != null) return GuardRefusal(RefusalReason.DeniedAddress, byTable.rangeClass)
        return if (policy.extraDeniedRanges.any { matchesExtra(it, address) }) GuardRefusal(RefusalReason.DeniedAddress, EXTRA_CLASS) else null
    }

    // 127.0.0.0/8, ::1, and a mapped address whose embedded IPv4 is in 127.0.0.0/8. NAT64 is not exempt.
    private fun isLoopback(a: ByteArray): Boolean = when {
        a.size == 4 -> a[0].toInt() == 127
        a.size == 16 && AddressRanges.isMapped(a) -> a[12].toInt() == 127
        a.size == 16 -> (0..14).all { a[it].toInt() == 0 } && a[15].toInt() == 1
        else -> false
    }

    // A block matches an address of its family; an IPv4 block also matches the IPv4 embedded in a mapped or NAT64 address.
    private fun matchesExtra(block: CidrBlock, address: ByteArray): Boolean =
        block.contains(address) || (block.isIpv4 && AddressRanges.embeddedV4(address)?.let { block.contains(it) } == true)
    //endregion

    private fun checkDowngrade(policy: OutboundUrlPolicy, url: Url, previous: Url?): GuardRefusal? =
        if (previous?.scheme == "https" && url.scheme == "http" && !policy.settings.allowHttpsToHttp) {
            GuardRefusal(RefusalReason.InsecureRedirect, null)
        } else {
            null
        }

    private fun parseRefusal(failure: Throwable): RefusalReason = when ((failure as? UrlParseException)?.problem) {
        UrlProblem.UserInfo -> RefusalReason.UserInfo
        UrlProblem.Scheme -> RefusalReason.Scheme
        UrlProblem.Port -> RefusalReason.Port
        else -> RefusalReason.HostForm
    }

    private companion object {
        const val LOCALHOST = "localhost"
        const val EXTRA_CLASS = "extra-denied-range"
        val LOOPBACK_HOSTS: Set<String> = setOf("localhost", "127.0.0.1", "::1")
    }
}
```

*(`withSettings` is in `OutboundUrlPolicy.kt`, §7.3.)*

*Failure path:* nothing in this file throws except `GuardRefusal`'s `init` (a programmer error: a range class on a reason
other than `DeniedAddress`). Every other outcome is a value.

### 7.8 `jvmMain`

**`<jm>PolicyRefusals.kt`** — the markers and the search.

```kotlin
package com.spartanlabs.webtools.http

import java.io.IOException
import java.net.UnknownHostException
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

internal const val POLICY_REFUSED_HOST_MESSAGE: String = "The host was refused by the URL policy."
internal const val POLICY_REFUSED_IO_MESSAGE: String = "The connection was refused by the URL policy."
private const val MAX_THROWABLES_SEARCHED: Int = 100

/**
 * Thrown by [GuardedDns] when a resolver answer is refused. An `UnknownHostException`, so OkHttp rethrows it unwrapped. The
 * message is a constant and never holds a host, an address or a range; the stack is skipped (a refusal is an expected,
 * attacker-triggerable event and the stack adds nothing).
 */
internal class PolicyRefusedUnknownHost(val refusal: GuardRefusal) : UnknownHostException(POLICY_REFUSED_HOST_MESSAGE) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Thrown by [GuardedSocket] when a connect is refused. An `IOException` and deliberately never a `ConnectException`, which
 * OkHttp re-wraps as `ConnectException("Failed to connect to <address>")`. Constant message; no stack.
 */
internal class PolicyRefusedIo(val refusal: GuardRefusal) : IOException(POLICY_REFUSED_IO_MESSAGE) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Finds the first policy marker in this throwable, its cause chain and its suppressed lists, depth first (a throwable's
 * cause before its suppressed, suppressed in order). Bounded to 100 throwables and cycle-safe.
 *
 * @return the refusal the marker carries, or `null` when there is none. Never throws.
 */
internal fun Throwable.policyRefusal(): GuardRefusal? {
    val seen: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
    val pending = ArrayDeque<Throwable>()
    pending.push(this)
    while (pending.isNotEmpty() && seen.size < MAX_THROWABLES_SEARCHED) {
        val current = pending.pop()
        if (!seen.add(current)) continue
        when (current) {
            is PolicyRefusedUnknownHost -> return current.refusal
            is PolicyRefusedIo -> return current.refusal
        }
        current.suppressedExceptions.asReversed().forEach { pending.push(it) }   // pushed first, popped after the cause
        current.cause?.let { pending.push(it) }
    }
    return null
}
```

**`<jm>GuardedDns.kt`**

```kotlin
package com.spartanlabs.webtools.http

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

private val log: Log = logger("com.spartanlabs.webtools.http.GuardedDns")
internal const val EMPTY_ANSWER_MESSAGE: String = "The resolver returned no addresses."

/**
 * An [okhttp3.Dns] that judges every address a name resolves to. It overrides `lookup` only and inherits `newCall`, whose
 * default routes record resolution through `lookup`; it never uses `by delegate` and never forwards `newCall`. Delegate
 * exceptions pass through; an empty delegate answer becomes an `UnknownHostException` with a constant message; it never
 * returns an empty list (OkHttp's message for one holds the host).
 */
internal class GuardedDns(private val delegate: Dns, private val guard: UrlGuard) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val answer = delegate.lookup(hostname)
        if (answer.isEmpty()) throw UnknownHostException(EMPTY_ANSWER_MESSAGE)
        val addresses = answer.map { it.address }
        return when (val verdict = guard.judgeAnswer(addresses)) {
            is AnswerVerdict.Refused -> throw PolicyRefusedUnknownHost(verdict.refusal)
            is AnswerVerdict.Allowed -> {
                if (verdict.indices.size < answer.size) logDropped(hostname, addresses, verdict.indices)
                verdict.indices.map { answer[it] }
            }
        }
    }

    // One DEBUG line: the host name, the counts and the distinct range classes; never an address.
    private fun logDropped(hostname: String, addresses: List<ByteArray>, kept: List<Int>) {
        val keptSet = kept.toSet()
        val classes = addresses.indices.filter { it !in keptSet }
            .mapNotNull { guard.checkAddress(addresses[it])?.rangeClass }.distinct().sorted()
        log.debug("dropped {} of {} resolved addresses for {} (range classes: {})", addresses.size - kept.size, addresses.size, hostname, classes)
    }

    override fun toString(): String = "GuardedDns"
}
```

**`<jm>GuardedSocketFactory.kt`** and **`<jm>GuardedSocket.kt`**

```kotlin
package com.spartanlabs.webtools.http

import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import javax.net.SocketFactory

internal const val NOT_A_GUARDED_ENTRY_POINT_MESSAGE: String =
    "This socket factory only creates unconnected sockets; connect through the guarded socket."

/**
 * A [SocketFactory] whose sockets judge every connect. OkHttp 5.5.0 calls only `createSocket()`; the four abstract overloads
 * refuse, so a future OkHttp that calls one fails loudly instead of connecting unjudged. Not an `SSLSocketFactory` (OkHttp
 * rejects one), and it has no delegate: it must create the socket itself to choose the socket implementation.
 */
internal class GuardedSocketFactory(private val guard: UrlGuard) : SocketFactory() {
    override fun createSocket(): Socket = GuardedSocket(guard)
    override fun createSocket(host: String?, port: Int): Socket = refuse()
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = refuse()
    override fun createSocket(host: InetAddress?, port: Int): Socket = refuse()
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = refuse()

    private fun refuse(): Nothing = throw SocketException(NOT_A_GUARDED_ENTRY_POINT_MESSAGE)

    override fun toString(): String = "GuardedSocketFactory"
}
```

```kotlin
package com.spartanlabs.webtools.http

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress

private val UNRESOLVED = GuardRefusal(RefusalReason.DeniedAddress, "unresolved")

/**
 * A [Socket] that judges the target of every connect. It extends `Socket(Proxy.NO_PROXY)`, which installs the platform socket
 * implementation directly: `new Socket()` installs a `SocksSocketImpl` that consults the JVM-wide SOCKS settings, which
 * `proxy(Proxy.NO_PROXY)` on an `OkHttpClient` does not close. Overrides only `connect(SocketAddress, Int)`
 * (`connect(SocketAddress)` calls it with 0). A non-`InetSocketAddress` or unresolved target is refused (a SOCKS
 * implementation would hand a host name to a proxy to resolve), a denied address is refused, both before any I/O. Stateless
 * beyond `super`, so fast fallback's concurrent attempts, and a loser being closed mid-connect, add no hazard.
 */
internal class GuardedSocket(private val guard: UrlGuard) : Socket(Proxy.NO_PROXY) {
    @Throws(IOException::class)
    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val refusal = refusalFor(endpoint)
        if (refusal != null) throw PolicyRefusedIo(refusal)
        super.connect(endpoint, timeout)
    }

    private fun refusalFor(endpoint: SocketAddress?): GuardRefusal? {
        if (endpoint !is InetSocketAddress || endpoint.isUnresolved) return UNRESOLVED
        val address = endpoint.address ?: return UNRESOLVED
        return guard.checkAddress(address.address)
    }
}
```

**`<jm>PolicyEnforcement.kt`**

```kotlin
package com.spartanlabs.webtools.http

import okhttp3.Dns
import java.net.Proxy
import javax.net.SocketFactory

/** The three client settings one policy implies; a `null` means "leave OkHttp's default". Unit 6 applies them to its `OkHttpClient.Builder`. */
internal class PolicyEnforcement(val dns: Dns, val socketFactory: SocketFactory?, val proxy: Proxy?)

/**
 * The bundle for this guard's policy. `Untrusted`: the guarded `Dns` over [baseDns], the guarded socket factory and
 * `Proxy.NO_PROXY` (a proxy resolves names itself and bypasses both guards). `Trusted`: [baseDns], no socket factory and no
 * proxy, so OkHttp's default selection stays.
 *
 * @param baseDns `Dns.SYSTEM`, or a test stub.
 */
internal fun UrlGuard.enforcement(baseDns: Dns): PolicyEnforcement = when (policy) {
    is OutboundUrlPolicy.Untrusted -> PolicyEnforcement(GuardedDns(baseDns, this), GuardedSocketFactory(this), Proxy.NO_PROXY)
    is OutboundUrlPolicy.Trusted -> PolicyEnforcement(baseDns, null, null)
}
```

### 7.9 `webtools-http/api/webtools-http.api` (changed, generated)

Not hand-edited: `./gradlew :webtools-http:apiDump` rewrites it, and the diff is read. Expected additions (the order BCV
prints: alphabetical by class):

- `CidrBlock` (final; `equals`, `hashCode`, `toString`; `Companion` field) and `CidrBlock$Companion` with `parse-IoAF18A
  (Ljava/lang/String;)Ljava/lang/Object;` (the `Result` return mangles the name, as `Url$Companion.parse-IoAF18A` does).
- `MixedAnswers` (an enum: `DropDenied`, `Reject`, `getEntries`, `valueOf`, `values`).
- `OutboundUrlPolicy` (abstract, the synthetic marker constructor, `getSettings`), `OutboundUrlPolicy$Companion` (`trusted`,
  `untrusted`, and the synthetic `trusted$default` and `untrusted$default` for the trailing lambda), `OutboundUrlPolicy$Trusted`
  and `$Untrusted` with their getters, `equals`/`hashCode`/`toString`, and `$Trusted$Builder`/`$Untrusted$Builder` with the
  getters and setters of their `var`s and `settings(Function1)`.
- `TransportSettings` (the ten mangled getters, `equals`/`hashCode`/`toString`, `Companion`), `TransportSettings$Builder`,
  `TransportSettings$Companion` (`getTrustedDefaults`, `getUntrustedDefaults`) and `TransportSettingsKt` with the static
  factory `TransportSettings (…Function1)`.
- `WebToolsDsl` (Amendment 3, S8): `public abstract interface annotation class com/spartanlabs/webtools/http/WebToolsDsl : java/lang/annotation/Annotation {`,
  then `}` and a blank line, last in the file (BCV sorts it after `WallClock$System`), printed as
  `ExperimentalForInheritanceWebToolsApi` is; `git diff --stat` of S8's `apiDump` is `1 file changed, 3 insertions(+)`. No
  builder's lines change: the dump does not record a class's annotations (the gated ports carry `@SubclassOptInRequired`, and
  their lines show none).
- `grep -c '\$default'` prints **5** (the three existing plus `trusted$default` and `untrusted$default`).
- Nothing `internal` appears: P8 greps the dump for every internal name of this plan (`StrictIp`, `HostRules`, `HostVerdict`,
  `AddressRanges`, `DeniedRange`, `GuardRelaxation`, `UrlGuard`, `GuardRefusal`, `RedirectAdmission`, `AnswerVerdict`,
  `GuardedDns`, `GuardedSocket`, `GuardedSocketFactory`, `PolicyRefused`, `PolicyEnforcement`, `PolicyRefusals`,
  `withSettings`, `canonicalPinnedHost`, `canonicalHostSuffix`, `matchesPrefix`).

The first `apiDump` is also the check of §3's first assumption. The dump is written CRLF on the maintainer's host (as the
existing one is); `apiCheck` compares line by line and is unaffected.

### 7.10 `README.md` (changed)

Line 11, the `webtools-http` row: the purpose cell becomes

> Kotlin Multiplatform (JVM target) — request/response types, a `Url` that reads like OkHttp's, a typed failure model, the transport and wall-clock ports of a suspend HTTP client stack, and the `Trusted`/`Untrusted` outbound-URL policies with their transport settings (timeouts, size and redirect caps, concurrency); **not yet released**

The other cells of the row (artifact, package, dependencies) do not change: no dependency is added. No other README line
changes (no command, build step, module or dependency moves).

### 7.11 `<cm>WebToolsDsl.kt` (new) and the six builders (changed) — Amendment 3

**`<cm>WebToolsDsl.kt`:**

```kotlin
package com.spartanlabs.webtools.http

/**
 * Marks the configuration builders of `webtools-http` as one DSL. Inside a lambda whose receiver is a builder carrying this
 * marker, only the innermost such builder is an implicit receiver, so a nested block cannot read or set an outer builder's
 * member by accident: in `OutboundUrlPolicy.untrusted { settings { allowedPorts = null } }` the assignment does not compile,
 * because `allowedPorts` belongs to the outer builder. The outer builder stays reachable through its label:
 * `this@untrusted.allowedPorts = null`.
 *
 * **Stable Core.** The marker is never removed, and a builder that carries it keeps it. The module applies it to every
 * builder whose instance it passes to a consumer's lambda as the receiver: [HttpHeaders.Builder], [HttpRequest.Builder],
 * [HttpResponse.Builder], [TransportSettings.Builder], [OutboundUrlPolicy.Trusted.Builder] and
 * [OutboundUrlPolicy.Untrusted.Builder].
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
// Retention is left at its default, RUNTIME: a test finds the marker on each builder by reflection (the ABI dump does
// not record a class's annotations), and Kotlin/JS warns about a RUNTIME retention only when it is written out.
@MustBeDocumented
public annotation class WebToolsDsl
```

The annotation is public because the scope check is meant for consumers' lambdas, and whether an `internal` marker on a
public builder scopes a consumer's lambda is not established (`research/config-api-shape.md` F4, gap 1); Ktor and
kotlinx.html both ship theirs public. It is JVM-free (`kotlin.DslMarker` and the meta-annotations are common), so the check
module compiles it, and with its retention left implicit it compiles without a warning (Amendment 5).

**The six builders.** One line, `@WebToolsDsl`, directly above each declaration and below its KDoc; nothing else in these
files changes:

| File | Declaration |
|---|---|
| `<cm>HttpHeaders.kt` | `public class Builder internal constructor()`, nested in `HttpHeaders` |
| `<cm>HttpRequest.kt` | `public class Builder internal constructor()`, nested in `HttpRequest` |
| `<cm>HttpResponse.kt` | `public class Builder internal constructor()`, nested in `HttpResponse` |
| `<cm>TransportSettings.kt` | `public class Builder internal constructor(from: TransportSettings)` |
| `<cm>OutboundUrlPolicy.kt` | `public class Builder internal constructor(from: Trusted)` and `public class Builder internal constructor(from: Untrusted)` |

These are the receiver types of every public lambda the module has: the factories `HttpHeaders { }`,
`HttpRequest(method, url) { }`, `HttpResponse(status, url) { }` and `TransportSettings(from) { }`, the four policy factories,
and the two `settings { }` functions. `jvmMain` has none, and the test fixtures expose no lambda with a receiver
(`Step.respond` and `Step.fail` take plain function types), so `src/jvmMain` and `src/jvmTestFixtures` do not change.

**Existing code.** No main source and no test reaches an outer builder implicitly from a nested block (the nested blocks in
`commonTest` and `jvmTest` set only their own receiver's members, for example `settings { maxRedirects = 1 }` inside a policy
block and `HttpHeaders { add(...) }` inside a request block), so the marker breaks no existing compile. If P12 step 3 names a
file other than the canary, that statement gets the explicit label (`this@<function>.<member>`, for example
`this@untrusted.allowedPorts`), recorded in `final-implementation.md` §4 as a mechanical fix.

**The canary (throwaway; P12; never committed):**
`webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/DslScopeCanary.kt`, exactly:

```kotlin
package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.Url

// Throwaway: each statement reaches an outer builder's member implicitly from a nested block.
internal fun dslScopeCanary(url: Url) {
    OutboundUrlPolicy.untrusted { settings { allowedPorts = null } }
    OutboundUrlPolicy.trusted { settings { allowCleartext = true } }
    OutboundUrlPolicy.untrusted { settings { settings { maxRedirects = 1 } } }
    HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { body = null } }
    HttpResponse(200, url) { headers = HttpHeaders { redirectChain = emptyList() } }
}
```

Lines 12 to 16 are its five statements, one per nesting the module's builders allow. Without the marker each compiles, and
each is the hazard: the assignment lands on the outer builder, and on line 14 the inner `settings { }` re-enters the outer
builder's `settings`. With the marker each is a compile error.

---
## 8. Documentation impact

Audience-Reach rings touched:

| Ring | What moves with this change |
|---|---|
| 1 Inner Core (in-editor) | `//region` markers in `UrlGuard.kt` (Trusted, Untrusted); the dated snapshot comment and the refresh procedure above `AddressRanges` (§6.4, verbatim); a line comment on every non-obvious rule (nested-first row order, the two non-registry rows, the ISATAP mask, why `Socket(Proxy.NO_PROXY)`, why `newCall` is not overridden, why the markers skip the stack, the table's `getOrThrow`). No `TODO` is left in the sources. |
| 2 Component (KDoc) | KDoc with `@param`/`@return`/`@throws` and a **Stable Core** statement on `TransportSettings` (the table of numbers, the `callTimeout`-spans-all-hops and `allowHttpsToHttp` statements, the behaviour-change warning), its `Builder` and companion values and the factory; `OutboundUrlPolicy` (the closed-pair statement, "to fetch from a private address use `Trusted`"), `Trusted`, `Untrusted` (every property, the `extraDeniedRanges` statement about mapped and NAT64 addresses and about a deployment's own NAT64 prefix), the builders and the four factories; `CidrBlock` (the grammar, the host-bits rule, the constant failure messages); `MixedAnswers` (a `when` keeps an `else`); `WebToolsDsl` (one DSL for the module's builders, the label form, the six builders it marks; Amendment 3). The internal classes carry KDoc stating their contract. |
| 3 Boundary (integration) | The contract between the policy and the transport, in the KDoc of `UrlGuard`, `PolicyEnforcement`, the markers and `policyRefusal()`, and pinned in §12: which call comes before any `Call`, what a marker is (constant message, no stack, never a `ConnectException`), where to look for it, what the bundle applies. The ABI dump is the machine-readable boundary of the public part. |
| 4 Architectural outer | The evergreen `docs/webtools-http-architecture.md` is unit 11's and does not change. `../architecture.md` carries three dated corrections (2026-10-26; Amendment 4): at §6.3.1, the address table's two rules that are not registry rows, and the socket guard's own `Socket(Proxy.NO_PROXY)` sockets with the inherited `newCall`; at §7.4, a refusal that carries the range class. The other refinements of §6.3.1 are listed in [`impact.md`](impact.md) §5. |

**README (same change, commit 4):** `README.md` line 11 as §7.10 says. No command, build step, module, dependency or
protocol changes, so no other README line moves.

**Not changed:** the documentation under `docs/` outside `docs/plans/`, the dashboards, `CHANGELOG` (none exists), any build file.

---

## 9. Test plan

**Conventions.** One test class per file; JUnit 5 through `kotlin.test` (`@Test` from `kotlin.test`, which
`kotlin-test-junit5` maps in `jvmTest`); backtick test names; no `@Tag` (the level tasks select by package); no MockK (none
exists in this repository): external calls are replaced by hand-written stubs. **No `@OptIn` anywhere in `commonTest` or
`jvmTest`**, so the tests stay consumers (this unit implements no gated seam, so none is needed). A test may read `internal`
declarations of the module through the test compilation's association with main. Path shorthand: `CT <level>/X.kt` is
`webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/X.kt` and `JT <level>/X.kt` is the same
under `src/jvmTest`; the package is `com.spartanlabs.testing.<level>.webtools.http`. Class names end in the level
(`GatingTest`, `ComponentTest`, `IntegrationTest`, `DeterministicTest`, `NonFunctionalTest`). Time never comes from the wall
clock except where a test waits for a loopback event with a timeout. Seeds and sizes are fixed in the source (no environment
variable). A failure message prints the seed or the vector id.

### 9.1 Test support (new files, not tests)

`CT support/PolicyTestSupport.kt`, `CT support/IpLiteralGenerator.kt`, `CT support/RegistryRows.kt`,
`CT support/BypassVectors.kt` (all in `commonTest`, package `com.spartanlabs.testing.support.webtools.http`, beside the existing
`UrlCorpus`) and `JT support/StubDns.kt` (in `jvmTest`, the same package). They are the only test files that hold more than a
test class's worth of data. `PolicyTestSupport.kt` is created in S1 with `url`, `addr` and `cidr` only; `untrustedGuard`, `trustedGuard` and `outcome` are added in S3, because they name `UrlGuard`, `GuardRelaxation` and `GuardRefusal`, which S3 creates (their imports move in with them).

**`PolicyTestSupport.kt`**

```kotlin
package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.GuardRefusal
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.StrictIp
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlGuard

/** Parses [text] or fails the test; for URLs the test itself wrote. */
internal fun url(text: String): Url = Url.parse(text).getOrElse { error("test URL rejected: $text") }

/** Address bytes through the strict parsers: dotted decimal, or IPv6 when [text] holds ':'. */
internal fun addr(text: String): ByteArray =
    (if (':' in text) StrictIp.parseV6(text) else StrictIp.parseV4(text)) ?: error("test address rejected: $text")

/** A block the test itself wrote. */
internal fun cidr(text: String): CidrBlock = CidrBlock.parse(text).getOrElse { error("test block rejected: $text") }

internal fun untrustedGuard(
    relaxation: GuardRelaxation = GuardRelaxation.None,
    configure: OutboundUrlPolicy.Untrusted.Builder.() -> Unit = {},
): UrlGuard = UrlGuard(OutboundUrlPolicy.untrusted(configure), relaxation)

internal fun trustedGuard(configure: OutboundUrlPolicy.Trusted.Builder.() -> Unit = {}): UrlGuard =
    UrlGuard(OutboundUrlPolicy.trusted(configure))

/** The reason name of a refusal, or "allowed" for `null`: the form the tables compare. */
internal fun GuardRefusal?.outcome(): String = this?.reason?.name ?: "allowed"
```

**`IpLiteralGenerator.kt`** (seeded; shared by `commonTest` and the `jvmTest` differential)

```kotlin
package com.spartanlabs.testing.support.webtools.http

import kotlin.random.Random

/** Seeded IP-literal-shaped strings, valid and invalid, for the agreement and differential tests. */
internal object IpLiteralGenerator {
    private const val HEX = "0123456789abcdef"

    /** [count] dotted-style strings: mostly four parts, sometimes one to five; parts with leading zeros, above 255, hex or empty. */
    fun ipv4(seed: Int, count: Int): List<String> {
        val random = Random(seed)
        return List(count) {
            val parts = if (random.nextInt(10) < 7) 4 else 1 + random.nextInt(5)
            (1..parts).joinToString(".") { ipv4Part(random) }
        }
    }

    private fun ipv4Part(random: Random): String {
        val roll = random.nextInt(100)
        return when {
            roll < 88 -> random.nextInt(256).toString()
            roll < 92 -> "0" + random.nextInt(100)
            roll < 95 -> (256 + random.nextInt(744)).toString()
            roll < 97 -> "0x" + random.nextInt(256).toString(16)
            else -> ""
        }
    }

    /** [count] IPv6-style strings without brackets: zero to nine groups, an optional "::", an optional dotted quad last, one in ten mutated. */
    fun ipv6(seed: Int, count: Int): List<String> {
        val random = Random(seed)
        return List(count) { mutate(random, shape(random), random.nextInt(10) == 0) }
    }

    private fun group(random: Random): String {
        if (random.nextInt(10) < 3) return listOf("0", "00", "000", "0000").random(random)
        val length = if (random.nextInt(20) == 0) 5 else 1 + random.nextInt(4)
        val digits = (1..length).map { HEX[random.nextInt(16)] }.joinToString("")
        return if (random.nextBoolean()) digits else digits.uppercase()
    }

    private fun quad(random: Random): String =
        (1..4).joinToString(".") { if (random.nextInt(20) == 0) "0" + random.nextInt(10) else random.nextInt(256).toString() }

    private fun shape(random: Random): String {
        val count = if (random.nextInt(10) < 8) 1 + random.nextInt(8) else random.nextInt(10)
        val parts = MutableList(count) { group(random) }
        if (count > 0 && random.nextInt(8) == 0) parts[count - 1] = quad(random)
        if (random.nextInt(10) >= 7) return parts.joinToString(":")
        val gapAt = random.nextInt(parts.size + 1)
        return parts.subList(0, gapAt).joinToString(":") + "::" + parts.subList(gapAt, parts.size).joinToString(":")
    }

    private fun mutate(random: Random, text: String, mutated: Boolean): String =
        if (!mutated) text else when (random.nextInt(8)) {
            0 -> ":$text"
            1 -> "$text:"
            2 -> "$text%eth0"
            3 -> text.replaceFirst("::", ":::")
            4 -> "[$text]"
            5 -> "$text "
            6 -> text.replace(':', ';')
            else -> text + "g"
        }
}
```

**`RegistryRows.kt`** (the IANA rows, one data table; the IPv4 and IPv6 first and last addresses are computed by the tests from the
block with independent bit arithmetic only for the *range* check; the two columns of expected classes are written out because a
nested row's first or last address belongs to a more specific row)

```kotlin
package com.spartanlabs.testing.support.webtools.http

/** The registry rows the address table must deny, with the class the table reports for each row's first and last address. */
internal object RegistryRows {
    /** A block, its first and last address and the class expected for each (a nested row's edge may belong to a more specific row). */
    class Row(val block: String, val first: String, val firstClass: String, val last: String, val lastClass: String)

    private fun same(block: String, first: String, last: String, rangeClass: String) = Row(block, first, rangeClass, last, rangeClass)

    /** The 26 IPv4 registry prefixes (25 rows), then the two rows that are not registry rows. */
    val ipv4: List<Row> = listOf(
        Row("0.0.0.0/8", "0.0.0.0", "this-host", "0.255.255.255", "this-network"),
        same("0.0.0.0/32", "0.0.0.0", "0.0.0.0", "this-host"),
        same("10.0.0.0/8", "10.0.0.0", "10.255.255.255", "private-use"),
        same("100.64.0.0/10", "100.64.0.0", "100.127.255.255", "shared-address-space"),
        same("127.0.0.0/8", "127.0.0.0", "127.255.255.255", "loopback"),
        same("169.254.0.0/16", "169.254.0.0", "169.254.255.255", "link-local"),
        same("172.16.0.0/12", "172.16.0.0", "172.31.255.255", "private-use"),
        Row("192.0.0.0/24", "192.0.0.0", "ipv4-service-continuity-prefix", "192.0.0.255", "ietf-protocol-assignments"),
        same("192.0.0.0/29", "192.0.0.0", "192.0.0.7", "ipv4-service-continuity-prefix"),
        same("192.0.0.8/32", "192.0.0.8", "192.0.0.8", "ipv4-dummy-address"),
        same("192.0.0.9/32", "192.0.0.9", "192.0.0.9", "pcp-anycast"),
        same("192.0.0.10/32", "192.0.0.10", "192.0.0.10", "turn-anycast"),
        same("192.0.0.170/32", "192.0.0.170", "192.0.0.170", "nat64-dns64-discovery"),
        same("192.0.0.171/32", "192.0.0.171", "192.0.0.171", "nat64-dns64-discovery"),
        same("192.0.2.0/24", "192.0.2.0", "192.0.2.255", "documentation"),
        same("192.31.196.0/24", "192.31.196.0", "192.31.196.255", "as112-v4"),
        same("192.52.193.0/24", "192.52.193.0", "192.52.193.255", "amt"),
        same("192.88.99.0/24", "192.88.99.0", "192.88.99.255", "deprecated-6to4-relay-anycast"),
        same("192.88.99.2/32", "192.88.99.2", "192.88.99.2", "6a44-relay-anycast"),
        same("192.168.0.0/16", "192.168.0.0", "192.168.255.255", "private-use"),
        same("192.175.48.0/24", "192.175.48.0", "192.175.48.255", "direct-delegation-as112"),
        same("198.18.0.0/15", "198.18.0.0", "198.19.255.255", "benchmarking"),
        same("198.51.100.0/24", "198.51.100.0", "198.51.100.255", "documentation"),
        same("203.0.113.0/24", "203.0.113.0", "203.0.113.255", "documentation"),
        Row("240.0.0.0/4", "240.0.0.0", "reserved", "255.255.255.255", "limited-broadcast"),
        same("255.255.255.255/32", "255.255.255.255", "255.255.255.255", "limited-broadcast"),
        same("224.0.0.0/4", "224.0.0.0", "239.255.255.255", "multicast"),
        same("168.63.129.16/32", "168.63.129.16", "168.63.129.16", "azure-wireserver"),
    )

    private const val NGU = "not-global-unicast"

    /** The 25 IPv6 registry rows, in the registry's order. The first and last addresses are in RFC 5952 form. */
    val ipv6: List<Row> = listOf(
        same("::1/128", "::1", "::1", NGU),
        same("::/128", "::", "::", NGU),
        Row("::ffff:0:0/96", "::ffff:0:0", "this-host", "::ffff:ffff:ffff", "limited-broadcast"),
        Row("64:ff9b::/96", "64:ff9b::", "this-host", "64:ff9b::ffff:ffff", "limited-broadcast"),
        same("64:ff9b:1::/48", "64:ff9b:1::", "64:ff9b:1:ffff:ffff:ffff:ffff:ffff", NGU),
        same("100::/64", "100::", "100::ffff:ffff:ffff:ffff", NGU),
        same("100:0:0:1::/64", "100:0:0:1::", "100::1:ffff:ffff:ffff:ffff", NGU),
        Row("2001::/23", "2001::", "teredo", "2001:1ff:ffff:ffff:ffff:ffff:ffff:ffff", "ietf-protocol-assignments"),
        same("2001::/32", "2001::", "2001:0:ffff:ffff:ffff:ffff:ffff:ffff", "teredo"),
        same("2001:1::1/128", "2001:1::1", "2001:1::1", "pcp-anycast"),
        same("2001:1::2/128", "2001:1::2", "2001:1::2", "turn-anycast"),
        same("2001:1::3/128", "2001:1::3", "2001:1::3", "dns-sd-srp-anycast"),
        same("2001:2::/48", "2001:2::", "2001:2:0:ffff:ffff:ffff:ffff:ffff", "benchmarking"),
        same("2001:3::/32", "2001:3::", "2001:3:ffff:ffff:ffff:ffff:ffff:ffff", "amt"),
        same("2001:4:112::/48", "2001:4:112::", "2001:4:112:ffff:ffff:ffff:ffff:ffff", "as112-v6"),
        same("2001:10::/28", "2001:10::", "2001:1f:ffff:ffff:ffff:ffff:ffff:ffff", "deprecated-orchid"),
        same("2001:20::/28", "2001:20::", "2001:2f:ffff:ffff:ffff:ffff:ffff:ffff", "orchidv2"),
        same("2001:30::/28", "2001:30::", "2001:3f:ffff:ffff:ffff:ffff:ffff:ffff", "drone-remote-id-det"),
        same("2001:db8::/32", "2001:db8::", "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", "documentation"),
        same("2002::/16", "2002::", "2002:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "6to4"),
        same("2620:4f:8000::/48", "2620:4f:8000::", "2620:4f:8000:ffff:ffff:ffff:ffff:ffff", "direct-delegation-as112"),
        same("3fff::/20", "3fff::", "3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff", "documentation"),
        same("5f00::/16", "5f00::", "5f00:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NGU),
        same("fc00::/7", "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NGU),
        same("fe80::/10", "fe80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NGU),
    )
}
```

**`BypassVectors.kt`** — the one data table of the published vectors (`research/bypass-vectors.md` §3, plus the parent research's F1
catalogue by tag), so nothing is entered twice. A vector is stage 1 (`Url.parse(text)` must fail with `problem`) or stage 2
(`Url.parse(text)` succeeds, its `host` is `host`, and an `Untrusted` default guard's `checkHop(url, null)` answers `outcome`, with
`rangeClass` for `DeniedAddress`). An IPv6 vector is fed in the printed form (`64:ff9b::7f00:1`, not `64:ff9b::127.0.0.1`) unless
the row is `spelled` as published. Source tags: **PATT** = PayloadsAllTheThings `Server Side Request Forgery/README.md` at
`3ac27901c711bdf3f5b65a7b1d1820a1f65bd09a`; **PATT-cloud** = the same commit's `SSRF-Cloud-Instances.md`; **HT** = HackTricks
`url-format-bypass.md` at `16689f85469c3a35946b6ad88e1357cb9ec5a776`; **HT-cloud** = the same commit's `cloud-ssrf.md`;
**Orange** = Orange Tsai, Black Hat USA 2017; **WHATWG** = URL Standard at `845bafca22fb0c5a42c4bf384a9bdea525a2f10d`; advisories by
CVE or GHSA id.

```kotlin
package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.UrlProblem

internal object BypassVectors {
    enum class Outcome { HostForm, ReservedName, DeniedAddress, Port, Allowed }

    class Vector(
        val id: String, val source: String, val parent: String?, val text: String, val problem: UrlProblem?,
        val host: String?, val outcome: Outcome?, val rangeClass: String?,
    ) {
        override fun toString(): String = "$id <${UrlCorpus.visible(text)}>"
    }

    val vectors: List<Vector> = buildList {
        fun refused(id: String, source: String, parent: String?, problem: UrlProblem, vararg texts: String) =
            texts.forEach { add(Vector(id, source, parent, it, problem, null, null, null)) }
        fun hostUrl(host: String) = "http://" + (if (':' in host) "[$host]" else host) + "/"
        fun hosts(id: String, source: String, parent: String?, outcome: Outcome, rangeClass: String?, vararg hosts: String) =
            hosts.forEach { add(Vector(id, source, parent, hostUrl(it), null, it, outcome, rangeClass)) }
        fun spelled(id: String, source: String, parent: String?, outcome: Outcome, rangeClass: String?, published: String, printed: String) =
            add(Vector(id, source, parent, hostUrl(published), null, printed, outcome, rangeClass))
        fun urls(id: String, source: String, parent: String?, outcome: Outcome, rangeClass: String?, host: String, vararg texts: String) =
            texts.forEach { add(Vector(id, source, parent, it, null, host, outcome, rangeClass)) }

        //region stage 1: Url.parse refuses (bypass-vectors.md 3.1)
        refused("P01", "Orange; PATT", null, UrlProblem.UserInfo, "http://foo@evil.com:80@google.com/", "http://foo@127.0.0.1:11211@google.com:80/")
        refused("P02", "Orange; PATT", null, UrlProblem.WhitespaceOrControl, "http://foo@127.0.0.1 @google.com/", "http://1.1.1.1 &@2.2.2.2# @3.3.3.3/")
        refused("P03", "Orange", null, UrlProblem.Port, "http://127.0.0.1:11211:80/")
        refused("P04", "PATT", null, UrlProblem.Backslash, "http://127.1.1.1:80\\@127.2.2.2:80/", "http://127.1.1.1:80\\@@127.2.2.2:80/",
            "http://127.1.1.1:80:\\@@127.2.2.2:80/", "http://127.1.1.1:80#\\@127.2.2.2:80/")
        refused("P05", "HT", null, UrlProblem.Backslash, "http://169.254.1698.254\\@attacker.com", "http://localhost:\\@google.com/../",
            "https://attacker.com\\{domain}/", "https://example.com\\[@attacker.com")
        refused("P06", "PATT; HT", "F1", UrlProblem.Scheme, "http:127.0.0.1/", "https:attacker.com", "https:/attacker.com", "http:@0/",
            "//attacker.com", "/attacker.com", "#attacker.com", "@attacker.com", "%0D%0A/attacker.com")
        refused("P07", "HT; PATT", null, UrlProblem.Backslash, "http:/\\/\\attacker.com", "https:/\\attacker.com", "\\\\/\\/attacker.com/", "file://\\/\\/etc/passwd")
        refused("P08", "PATT", null, UrlProblem.Scheme, "file:///etc/passwd", "gopher://127.0.0.1:6379/_PING", "dict://127.0.0.1:11211/stats",
            "ldap://127.0.0.1/", "sftp://127.0.0.1/", "tftp://127.0.0.1/x", "netdoc:///etc/passwd", "jar:http://127.0.0.1!/", "0://evil.com:80;http://google.com:80/")
        refused("P09", "Orange", null, UrlProblem.WhitespaceOrControl, "http://127.0.0.1\tfoo.google.com")
        refused("P10", "Orange", null, UrlProblem.HostEncoding, "http://127.0.0.1%09foo.google.com", "http://127.0.0.1%2509foo.google.com")
        refused("P11", "Orange", null, UrlProblem.WhitespaceOrControl, "http://127.0.0.1\r\nSLAVEOF orange.tw 6379\r\n:6379/",
            "https://127.0.0.1\r\nSET foo 0 60 5\r\n:443/", "http://0\r\n SLAVEOF orange.tw 6379\r\n :80")
        refused("P12", "Orange", null, UrlProblem.WhitespaceOrControl, "http://127.0.0.1:25/%0D%0AHELO orange.tw%0D%0AMAIL FROM...")
        urls("P12b", "Orange", null, Outcome.DeniedAddress, "loopback", "127.0.0.1", "http://127.0.0.1:25/%0D%0AHELO%20orange.tw%0D%0AMAIL%20FROM:%3Cx@y.com%3E")
        refused("P13", "Orange", null, UrlProblem.Backslash, "http://or\\097nge.tw/")
        refused("P14", "HT; PATT; Orange", null, UrlProblem.HostEncoding, "http://ⓔⓧⓐⓜⓟⓛⓔ.ⓒⓞⓜ/", "http://①②⑦.⓪.⓪.⓪/", "http://127。0。0。1/",
            "http://attacker。com/", "http://ⒶⓉⓉⒶⒸⓀⒺⓡ.Ⓒⓞⓜ/", "http://a\u200Db.example/", "http://wordpreß.com/")
        refused("P15", "HT; GHSA-v5mv-p594-2x33", "F1", UrlProblem.HostEncoding, "http://%31%32%37.0.0.1/", "http://127%2E0%2E0%2E1/",
            "http://127%E3%80%820%E3%80%820%E3%80%821/", "http://attacker%00.com/", "http://attacker%E3%80%82com/", "http://localhost%2e/", "http://127.0.0.%31/")
        refused("P16", "GHSA-v5mv-p594-2x33", null, UrlProblem.UserInfo, "http://blocked.example.com@127.0.0.1/")
        refused("P17", "HT", null, UrlProblem.Port, "http://localhost:+11211aaa/", "http://localhost:00011211aaaa/", "http://example.com:+80/",
            "http://example.com:0/", "http://example.com:65536/", "http://example.com:80:80/")
        refused("P18", "HT; GHSA-vmxc-h2x2-jmf3", "F1", UrlProblem.Host, "http://[fe80::1%25eth0]/", "http://[fe80::a9ff:fe00:1%25en0]/",
            "http://[fe80::1%eth0]/", "http://[fd00:ec2::254%251]/")
        refused("P19", "GHSA-f65p-4m7j-42xc; WHATWG", null, UrlProblem.Host, "http://[::not-valid]/", "http://[fc00::not-hex]/",
            "http://[fe80::not-hex]/", "http://[::1/", "http://[::ffff:0177.0.0.1]/", "http://[::ffff:127.1]/", "http://[1:2:3:4:5:6:7::8]/")
        //endregion

        //region stage 2: the port rule (3.1 P20)
        listOf(6379, 11211, 25, 22, 2375, 2379).forEach {
            add(Vector("P20", "Orange; PATT; PATT-cloud", null, "http://example.com:$it/", null, "example.com", Outcome.Port, null))
        }
        //endregion

        //region stage 2: HostForm (3.2)
        hosts("H01", "PATT; HT; GHSA-r46f-3rpw-hxrv", "F1", Outcome.HostForm, null, "2130706433", "3232235521", "3232235777", "2852039166", "0")
        hosts("H02", "PATT; HT; CVE-2024-29415; node-ip#150", "F1", Outcome.HostForm, null, "127.1", "127.0.1", "127.1.1", "169.254.43518")
        hosts("H03", "HT; CVE-2021-28918; CVE-2021-29418; CVE-2021-29921; CVE-2021-33571; CVE-2026-69192", "F1,F10", Outcome.HostForm, null,
            "0177.0.0.1", "0177.0000.0000.0001", "00000177.00000000.00000000.00000001", "012.1.2.3", "0127.0.0.1", "0127.0.0.01", "012.0.0.1", "031.5.5.5", "01.2.3.4")
        hosts("H04", "HT; CVE-2024-29415; CVE-2020-28360", "F1,F10", Outcome.HostForm, null,
            "017700000001", "01200034567", "127.000000000000.1", "127.000000000000000.1", "127.000.000.001", "127.0.0.01", "00000127.0.0.1",
            "08.0.0.1", "0000.0000.0000.0000", "0000.0000")
        hosts("H05", "HT; PATT; CVE-2023-42282", "F1,F10", Outcome.HostForm, null,
            "0x7f.1", "0x7f000001", "0xc0a80101", "0xa9fea9fe", "0xc0a80014", "0x7f.0x00.0x00.0x01", "0x0000007f.0x00000000.0x00000000.0x00000001")
        spelled("H05", "HT (published upper-case)", "F1,F10", Outcome.HostForm, null, "0xA9.254.0251.0376", "0xa9.254.0251.0376")
        hosts("H06", "PATT; node-ip#150", null, Outcome.HostForm, null, "o177.0.0.1", "0o177.0.0.1", "q177.0.0.1", "127.00.0x1", "127.0.0x0.1")
        hosts("H07", "WHATWG", "F1", Outcome.HostForm, null, "127.0.0.1.", "127.0.0.0x", "127.0.0.09", "0x", "1.2.3.4.5", "256.1.1.1", "999.999.999.999", "1.1")
        hosts("H08", "WHATWG", "F1", Outcome.HostForm, null, "example.0x7f", "a.b.1", "localhost.1", "x.0x")
        //endregion

        //region stage 2: DeniedAddress (3.3); the class is the most specific table row's
        hosts("D01", "PATT; HT", "F1", Outcome.DeniedAddress, "loopback", "127.0.0.1", "127.127.127.127", "127.0.1.3", "127.0.0.0")
        hosts("D02", "PATT; HT; GHSA-j5h6-vqc3-phqh", "F1", Outcome.DeniedAddress, "this-host", "0.0.0.0")
        hosts("D03", "PATT; CVE-2020-28360", "F1", Outcome.DeniedAddress, "private-use", "10.0.0.5", "172.16.0.1", "192.168.1.1", "192.168.0.1")
        hosts("D04", "PATT-cloud; HT-cloud", "F1", Outcome.DeniedAddress, "link-local", "169.254.169.254", "169.254.170.2", "169.254.170.23")
        hosts("D05", "PATT-cloud; Bifrost", "F1", Outcome.DeniedAddress, "shared-address-space", "100.64.0.1", "100.100.100.200")
        hosts("D06", "PATT-cloud (Oracle); CPython 113171", null, Outcome.DeniedAddress, "ietf-protocol-assignments", "192.0.0.192")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "ipv4-dummy-address", "192.0.0.8")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "pcp-anycast", "192.0.0.9")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "turn-anycast", "192.0.0.10")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "nat64-dns64-discovery", "192.0.0.170", "192.0.0.171")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "this-network", "0.0.0.7", "0.255.255.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "shared-address-space", "100.127.255.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "as112-v4", "192.31.196.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "amt", "192.52.193.0")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "deprecated-6to4-relay-anycast", "192.88.99.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "direct-delegation-as112", "192.175.48.1")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "benchmarking", "198.19.255.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "documentation", "198.51.100.0", "203.0.113.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "reserved", "240.0.0.1")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "limited-broadcast", "255.255.255.255")
        hosts("D08", "CVE-2025-8020; GHSA-p4hc-9pjh-55c8", null, Outcome.DeniedAddress, "multicast", "224.0.0.1", "239.255.255.255")
        hosts("D09", "Fedify", null, Outcome.DeniedAddress, "benchmarking", "198.18.0.1")
        hosts("D09", "Fedify", null, Outcome.DeniedAddress, "documentation", "192.0.2.1")
        spelled("D10", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "loopback", "::ffff:127.0.0.1", "127.0.0.1")
        spelled("D10", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "loopback", "::ffff:7f00:1", "127.0.0.1")
        spelled("D10", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "loopback", "0:0:0:0:0:ffff:7f00:1", "127.0.0.1")
        spelled("D10", "CVE-2024-29415", "F1,F10", Outcome.DeniedAddress, "loopback", "::fFFf:127.0.0.1", "127.0.0.1")
        spelled("D10", "PATT-cloud", "F1,F10", Outcome.DeniedAddress, "link-local", "::ffff:a9fe:a9fe", "169.254.169.254")
        spelled("D10", "HT", "F1,F10", Outcome.DeniedAddress, "this-host", "::ffff:0:0", "0.0.0.0")
        spelled("D10", "HT", "F1,F10", Outcome.DeniedAddress, "private-use", "::ffff:a00:1", "10.0.0.1")
        spelled("D11", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "0000::1", "::1")
        spelled("D11", "HT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "0:0:0:0:0:0:0:1", "::1")
        spelled("D11", "CVE-2024-29415", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "000:0:0000::01", "::1")
        spelled("D11", "PATT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "0:0:0:0:0:0:0:0", "::")
        hosts("D11", "PATT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "::1", "::")
        spelled("D12", "Symfony; pydantic-ai; Cloudreve", "F1", Outcome.DeniedAddress, "not-global-unicast", "::127.0.0.1", "::7f00:1")
        hosts("D12", "Symfony; pydantic-ai; Cloudreve", "F1", Outcome.DeniedAddress, "not-global-unicast", "::7f00:1")
        spelled("D13", "pydantic-ai CVE-2026-48782", null, Outcome.DeniedAddress, "not-global-unicast", "::ffff:0:127.0.0.1", "::ffff:0:7f00:1")
        spelled("D14", "CC-Tweaked; Open WebUI; Dozzle; Symfony", "F1", Outcome.DeniedAddress, "loopback", "64:ff9b::127.0.0.1", "64:ff9b::7f00:1")
        hosts("D14", "AVideo CVE-2026-82648", "F1", Outcome.DeniedAddress, "loopback", "64:ff9b::7f00:1")
        hosts("D14", "Dozzle", "F1", Outcome.DeniedAddress, "link-local", "64:ff9b::a9fe:a9fe")
        spelled("D14", "Symfony", "F1", Outcome.DeniedAddress, "private-use", "64:ff9b::c0a8:0101", "64:ff9b::c0a8:101")
        hosts("D15", "ToolHive; Symfony; pydantic-ai", null, Outcome.DeniedAddress, "not-global-unicast", "64:ff9b:1::7f00:1", "64:ff9b:1::a00:1")
        hosts("D16", "Symfony; Dozzle; Bifrost; Weblate", "F1", Outcome.DeniedAddress, "6to4", "2002:7f00:1::", "2002:a9fe:a9fe::", "2002:5db8:d822::")
        hosts("D17", "Symfony; Dozzle", null, Outcome.DeniedAddress, "teredo", "2001::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2")
        hosts("D18", "HT; HT-cloud; openclaw GHSA-h97f-6pqj-q452", "F1", Outcome.DeniedAddress, "not-global-unicast",
            "fd00::1", "fd00:ec2::254", "fc00::1", "fe80::1", "fec0::1", "ff02::1", "ff05::1:3")
        hosts("D19", "Python 3.13 notes; openclaw", null, Outcome.DeniedAddress, "documentation", "2001:db8::1", "3fff::1")
        hosts("D19", "openclaw", null, Outcome.DeniedAddress, "benchmarking", "2001:2::1")
        hosts("D19", "openclaw", null, Outcome.DeniedAddress, "direct-delegation-as112", "2620:4f:8000::1")
        //endregion

        //region design gaps closed by two rows that are not registry rows (4)
        urls("G1", "HT-cloud; Microsoft Learn", null, Outcome.DeniedAddress, "azure-wireserver", "168.63.129.16", "http://168.63.129.16/?comp=versions")
        hosts("G2", "openclaw GHSA-8cp7-rp8r-mg77; pydantic-ai GHSA-cg7w-rg45-pc59", null, Outcome.DeniedAddress, "isatap",
            "2a01:4f8:1:1:0:5efe:7f00:1", "2a01:4f8:1:1:200:5efe:7f00:1")
        //endregion

        //region stage 2: ReservedName (3.4)
        hosts("R01", "PATT; HT", "F1", Outcome.ReservedName, null, "localhost", "localhost.", "foo.localhost")
        spelled("R01", "PATT", "F1", Outcome.ReservedName, null, "PayloadsAllTheThings.localhost", "payloadsallthethings.localhost")
        hosts("R02", "PATT", null, Outcome.ReservedName, null, "ip6-localhost", "ip6-loopback")
        hosts("R03", "PATT-cloud; HT-cloud", null, Outcome.ReservedName, null, "metadata", "rancher-metadata", "metadata.google.internal")
        hosts("R04", "the rule's own members", null, Outcome.ReservedName, null, "printer.local", "router.home.arpa", "host.localdomain", "intranet")
        //endregion

        //region stage 2: names the resolver guard judges; no earlier rule fires (3.5) and controls (3.6)
        hosts("N01", "PATT; HT", null, Outcome.Allowed, null, "localtest.me", "localh.st", "bugbounty.dod.network", "mail.ebc.apple.com", "spoofed.burpcollaborator.net")
        hosts("N02", "PATT; HT; CVE-2026-55526", null, Outcome.Allowed, null, "127.0.0.1.nip.io", "169.254.169.254.nip.io", "company.127.0.0.1.nip.io",
            "customer1.app.localhost.my.company.127.0.0.1.nip.io", "1ynrnhl.xip.io")
        hosts("N03", "PATT", null, Outcome.Allowed, null, "make-1.2.3.4-rebind-169.254-169.254-rr.1u.ms")
        hosts("N04", "PATT-cloud", null, Outcome.Allowed, null, "metadata.packet.net")
        urls("N05", "Orange", null, Outcome.Allowed, null, "google.com", "http://google.com#@evil.com/")
        hosts("N06", "CVE-2026-25534", null, Outcome.Allowed, null, "evil_host.example")
        hosts("C01", "controls", null, Outcome.Allowed, null, "93.184.216.34", "1.1.1.1", "8.8.8.8", "64:ff9b::5db8:d822", "2606:4700:4700::1111")
        spelled("C01", "controls", null, Outcome.Allowed, null, "::ffff:93.184.216.34", "93.184.216.34")
        listOf(80, 443, 8080, 8443).forEach {
            add(Vector("C02", "controls", null, "http://example.com:$it/", null, "example.com", Outcome.Allowed, null))
        }
        //endregion
    }
}
```

*Code points.* Visible non-ASCII stays literal in the test sources, so a reader sees what a test feeds (`ⓔⓧⓐⓜⓟⓛⓔ`, `ß`,
`。`); every invisible or look-alike code point is written as a `\uXXXX` escape with a comment naming it: U+200D ZERO WIDTH
JOINER in `P14`, and U+212A KELVIN SIGN in `HostRulesDirectInputDeterministicTest`. This is the convention of `UrlCorpus` and
the existing `webtools-http` tests.

*Note on `P20`/`C02`:* their ids repeat across rows of the same group; a failure message prints `id <visible text>`, which is unique.

**`StubDns.kt`** (`jvmTest`)

```kotlin
package com.spartanlabs.testing.support.webtools.http

import okhttp3.Dns
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** An address for [text] built from bytes, so the JDK performs no lookup. A 16-byte IPv4-mapped literal collapses to an `Inet4Address`, as the JDK does. */
internal fun inet(text: String): InetAddress = InetAddress.getByAddress(addr(text))

/** A resolver that answers from [answer], counts its calls and records the names asked. Safe for concurrent use. */
internal class StubDns(private val answer: (String) -> List<InetAddress>) : Dns {
    val calls = AtomicInteger()
    val hostnames = CopyOnWriteArrayList<String>()

    override fun lookup(hostname: String): List<InetAddress> {
        calls.incrementAndGet()
        hostnames += hostname
        return answer(hostname)
    }

    companion object {
        /** A stub that answers the same [addresses] for every name. */
        fun of(vararg addresses: String): StubDns = StubDns { addresses.map { inet(it) } }
    }
}
```

### Level 1 — gating (`gatingTest`)

| File | Behaviours |
|---|---|
| `CT gating/OutboundUrlPolicyConsumerGatingTest.kt` | The permanent consumer of the new public API, compiled in `commonTest` **without any opt-in**. It builds `OutboundUrlPolicy.trusted { pinnedHosts = setOf("api.example.com") }`, `OutboundUrlPolicy.untrusted { allowedPorts = null; extraDeniedRanges += CidrBlock.parse("10.0.0.0/8").getOrThrow(); mixedAnswers = MixedAnswers.DropDenied; settings { callTimeout = 20.seconds } }`, both `from` factories, `TransportSettings.TrustedDefaults`, `TransportSettings.UntrustedDefaults` and `TransportSettings(TransportSettings.UntrustedDefaults) { maxRedirects = 1 }`; it unwraps a `CidrBlock.parse` failure with `fold`; it matches a policy with a `when` that has **no `else`** (the sealed pair is the contract) and matches `policy.mixedAnswers` with a `when` that **keeps an `else`**; it passes a policy and a `RefusalReason` through a function signature. Compiling it proves the new API is reachable without `ExperimentalForInheritanceWebToolsApi`; running it through `gatingTest` and finding its class in that task's result XML proves the level task reaches the commonTest source set. It is the pre-commit gate of this unit together with `componentTest` and `deterministicTest`. |
| `CT gating/WebToolsDslConsumerGatingTest.kt` | Amendment 3. The permanent consumer of the builder DSL, compiled in `commonTest` **without any opt-in** and using only public API (its URL is `Url.parse("https://api.example.com/").getOrThrow()`). Three tests. **Each block sets its own receiver:** `OutboundUrlPolicy.untrusted { allowedPorts = setOf(443); settings { maxRedirects = 1 } }` has `allowedPorts == setOf(443)` and `settings.maxRedirects == 1`; `HttpRequest(HttpMethod.Post, url) { headers = HttpHeaders { add("Accept", "text/plain") }; body = RequestBody(byteArrayOf(1, 2), null) }` has `headers.getAll("Accept") == listOf("text/plain")` and `body?.size == 2`. **An outer policy builder through its label:** `OutboundUrlPolicy.untrusted { settings { maxRedirects = 1; this@untrusted.allowedPorts = null } }` has `allowedPorts == null` and `settings.maxRedirects == 1`; `OutboundUrlPolicy.trusted { settings { maxRedirects = 2; this@trusted.allowCleartext = true } }` has `allowCleartext == true` and `settings.maxRedirects == 2`. **An outer request or response builder through its label:** `HttpRequest(HttpMethod.Post, url) { headers = HttpHeaders { add("Accept", "text/plain"); this@HttpRequest.body = RequestBody(byteArrayOf(1, 2), null) } }` has `body?.size == 2` and `headers.getAll("Accept") == listOf("text/plain")`; `HttpResponse(200, url) { headers = HttpHeaders { add("X-Trace", "t"); this@HttpResponse.body(byteArrayOf(3)) } }` has `bodySize == 1` and `headers.getAll("X-Trace") == listOf("t")`. The class KDoc states that an implicit reach into an outer builder is a compile error, which a passing test cannot show. |

### Level 2 — component (`componentTest`)

`commonTest` (stubs are the production types themselves; nothing external is called):

| File | Behaviours |
|---|---|
| `CT component/TransportSettingsComponentTest.kt` | The two default sets hold exactly the numbers of R2's table (each property asserted). `TransportSettings(from) { }` with an empty block equals `from` (a new instance) and never mutates `from`; a block that changes only `maxRedirects` equals the defaults with only that value changed; the builder is seeded from `from` (`maxConcurrentRequests = maxOf(maxConcurrentRequests, 20)` sees 8 on the `Untrusted` defaults). **One test per property** (ten): with `TrustedDefaults` as base, changing only that property (to 31 s, 11 s, 11 s, 11 s, `maxResponseBytes + 1`, 6, `true`, 17, 6, 5 s) gives a value that is `!=` the base, whose `toString()` differs, while two identical derivations are equal with equal `hashCode`. Validation, each rule with its boundary: a timeout of 1 ms and of `Int.MAX_VALUE` ms is accepted for each of the four timeouts and `idleConnectionTtl`; `Duration.ZERO`, `-1.seconds`, `999.microseconds`, `Duration.INFINITE` and `(Int.MAX_VALUE + 1L).milliseconds` are `IllegalArgumentException` whose message contains the property's name; `maxResponseBytes` 1 accepted, 0 refused; `maxRedirects` 0 accepted, -1 refused; `maxConcurrentRequests` 1 accepted, 0 refused; `maxConcurrentRequestsPerHost` 1 and `maxConcurrentRequests` accepted, 0 and `maxConcurrentRequests + 1` refused. **No message holds a value:** with `maxResponseBytes = -123456789L` the message does not contain `123456789`. `toString()` starts with `TransportSettings(` and prints all ten names. |
| `CT component/TrustedPolicyComponentTest.kt` | `OutboundUrlPolicy.trusted { }` and `trusted()` are equal: no pins, `allowCleartext` false, `settings == TrustedDefaults`. A pinned set is canonicalised and copied: `{"API.Example.com", "api.example.com"}` stores one `api.example.com`; `"[::1]"`, `"::1"` and `"[::ffff:10.0.0.5]"` store `::1`, `::1` and `10.0.0.5`; mutating the caller's `MutableSet` after the build changes nothing; `pinnedHosts += "b.example"` on the builder extends the set. Every refusal of §6.3's pinned-host table is an `IllegalArgumentException` whose message is `pinnedHosts must hold only host names or IP literals, without a port, path or user-info.` and never contains the offending text (the canary `SECRET-CANARY-7f3a` as an entry). `settings` as a property and as a block: `settings { callTimeout = 1.seconds }` keeps the other `TrustedDefaults` numbers; `settings = X` then `settings { maxRedirects = 0 }` modifies `X`; a bad `settings { }` block throws the settings message. `trusted(from) { }` keeps pins, cleartext and settings and changes only what the block sets; `from` is unchanged. **One test per property** (pins, cleartext, settings): changing only it breaks equality and changes `toString()`. `toString()` prints `pinnedHosts=<count>`, `allowCleartext` and the settings, and for a policy pinned to `secret-canary-7f3a.example` never contains the canary. A `Trusted` is not equal to an `Untrusted` with the same settings. |
| `CT component/UntrustedPolicyComponentTest.kt` | `untrusted { }` and `untrusted()` are equal: ports `{80, 443, 8080, 8443}`, no ranges, no suffixes, `MixedAnswers.Reject`, `settings == UntrustedDefaults`. `allowedPorts = null` is accepted (any port); an empty set, `{0}`, `{65536}` and `{-1}` are `IllegalArgumentException` with the empty and range messages respectively (`allowedPorts must not be empty; use null to allow any port.`, `allowedPorts must hold only ports from 1 to 65535.`); 1 and 65535 are accepted. Suffixes are canonicalised and copied (`"Corp.Example"` → `corp.example`; `"corp"` accepted); every refusal of §6.3's suffix table is an `IllegalArgumentException` with the suffix message and no echo. `extraDeniedRanges += cidr("10.0.0.0/8")` extends the set; the stored set is a copy. `mixedAnswers = DropDenied` is kept. `settings` property and block as in the `Trusted` test (the base is `UntrustedDefaults`). `untrusted(from) { }` keeps everything and changes only what the block sets. **One test per property** (ports `null`, ports `{80}`, one range, one suffix, `DropDenied`, settings): changing only it breaks equality and changes `toString()`. `toString()` prints the sorted ports (`[80, 443, 8080, 8443]`) or `any`, the counts, `mixedAnswers` and the settings; with a range `10.9.8.7/32` and a suffix `secret-canary-7f3a.example` the text holds neither. Equality is order-free for the sets. |
| `CT component/PolicyWithSettingsComponentTest.kt` | `withSettings` on a `Trusted` with pins, cleartext and custom settings returns a `Trusted` equal to the original except `settings`; on an `Untrusted` with ports, one range, one suffix and `DropDenied` the same; the original is unchanged; `withSettings(policy.settings)` equals the original; the facade's use `policy.withSettings(TransportSettings(policy.settings) { maxConcurrentRequests = maxOf(maxConcurrentRequests, 32) })` raises 8 to 32 on `Untrusted` and leaves the per-host limit at 2. |
| `CT component/CidrBlockComponentTest.kt` | `toString()` of `10.0.0.0/8`, `0.0.0.0/0`, `192.0.2.1/32`, `fd00::/8`, `::/0`, `2001:db8::/32`, `::1/128` and `::ffff:10.0.0.0/104` is the canonical text (`::ffff:a00:0/104` for the last: never a dotted quad inside IPv6). Equality and hash on family, bytes and prefix: `10.0.0.0/8 == 10.0.0.0/8`; `10.0.0.0/8 != 10.0.0.0/9`; `0.0.0.0/0 != ::/0`; `cidr("::ffff:a00:0/104") != cidr("10.0.0.0/8")` (different families). `contains` (internal): a /8 contains its first and last address and not the neighbours; /0 contains every address of its family and none of the other family; /32 contains exactly one; `fc00::/7` contains `fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` and `fc00::` and not `fe00::`; a 5-byte array is contained by nothing. |
| `CT component/TrustedGuardComponentTest.kt` | The `Trusted` rules of §6.5 through `checkHop`, each as a test: default `https://api.example.com/` allowed; `http://api.example.com/` `Scheme`; with `allowCleartext` allowed; **no host, address or port rule:** `https://10.0.0.5/`, `https://localhost:6379/`, `https://127.1/`, `https://[::1]/`, `https://metadata/` are all allowed; pins `{"api.example.com"}`: `https://api.example.com/` and `https://API.EXAMPLE.COM/` allowed, `https://api.example.com./`, `https://evilapi.example.com/`, `https://api.example.com.evil.test/`, `https://other.test/` are `HostNotPinned`; an IPv6 pin `[::1]` matches `https://[::1]/`; a pin `127.0.0.1` matches `https://[::ffff:127.0.0.1]/` (`Url.host` is `127.0.0.1`); downgrade: previous `https://api.example.com/` then `http://api.example.com/` with `allowCleartext` is `InsecureRedirect`, allowed when `settings { allowHttpsToHttp = true }`, and `Scheme` (not `InsecureRedirect`) without `allowCleartext`; `checkRequest` never refuses a header (`Authorization`, a sensitive entry); `checkAddress` is `null` for every address including `ByteArray(5)`; `judgeAnswer` allows every index of `[10.0.0.1, 127.0.0.1]`. |
| `CT component/UntrustedGuardComponentTest.kt` | The `Untrusted` rules of §6.5, each as a test. Ports: the defaults allow 80, 443, 8080, 8443 on `example.com` and refuse 6379 and 22 as `Port`; `allowedPorts = null` allows 6379; `{9000}` refuses 443 and allows 9000. Literals (`DeniedAddress` with the class): `10.0.0.5` `private-use`, `169.254.169.254` `link-local`, `[::1]` `not-global-unicast`, `[fd00::1]` `not-global-unicast`, `[64:ff9b::7f00:1]` `loopback`, `[2002:7f00:1::]` `6to4`; allowed: `8.8.8.8`, `[2606:4700:4700::1111]`. Host forms: `127.1`, `2130706433`, `0x7f.1`, `127.0.0.1.` are `HostForm`. Reserved names: `localhost`, `foo.localhost`, `printer.local`, `intranet`, `x.internal`, `router.home.arpa`, `host.localdomain` are `ReservedName`; `example.com`, `localhost.example.com` and `notlocal.example` are allowed (label boundary). Suffixes: with `corp.example`, `corp.example`, `a.corp.example` and `a.corp.example.` are `ReservedName` and `mycorp.example` is allowed. `extraDeniedRanges`: `http://203.0.114.7/` with `203.0.114.0/24` is `DeniedAddress` with class `extra-denied-range`; a name is never matched against ranges by `checkHop`. Downgrade: previous `https://example.com/a` then `http://example.com/b` is `InsecureRedirect`; allowed with `allowHttpsToHttp`; `http`→`http`, `https`→`https` and a first hop over `http` are allowed. `checkRequest`: `Authorization`, `authorization` (any case), `Proxy-Authorization`, `Cookie`, `Set-Cookie` and `addSensitive("X-Api-Key", "k")` are `SensitiveHeader`; `X-Trace` is allowed; a denied URL with a sensitive header is the URL's reason (`DeniedAddress`), not `SensitiveHeader`; the refusal's `toString()` holds no header value. `checkAddress`: the table's classes, an extra block, `ByteArray(0)` and `ByteArray(5)` are `DeniedAddress` with `unusual-length`. A guard built without a relaxation has `GuardRelaxation.None`. |
| `CT component/GuardRelaxationComponentTest.kt` | Under `LoopbackForTests` and the default `Untrusted` policy (§6.6's table, each row as a test): `http://localhost:9999/`, `http://127.0.0.1:9999/`, `http://[::1]:9999/` are allowed (name exemption, address exemption, port exemption); `http://127.0.0.2/` is allowed, `http://127.0.0.2:9999/` is `Port` (the port exemption names three hosts); `http://[::ffff:127.0.0.1]/` (host `127.0.0.1`) is allowed; `http://foo.localhost/` and `http://localhost./` stay `ReservedName`; `http://[64:ff9b::7f00:1]/`, `http://10.0.0.5/`, `http://169.254.169.254/` stay `DeniedAddress`; `http://0x7f.1/` stays `HostForm`; `http://printer.local/` and `http://intranet/` stay `ReservedName`; the downgrade rule stays live; a `127.0.0.0/8` extra range still refuses `127.0.0.1` with class `extra-denied-range`; an extra suffix `localhost` still refuses `http://localhost/` as `ReservedName`; `checkAddress` returns `null` for `127.0.0.1`, `::1` and the 16-byte mapped `127.0.0.1`, and a refusal for `64:ff9b::7f00:1`, `10.0.0.1` and `::2`. `GuardRelaxation.None` refuses all of the exempt cases. A `Trusted` policy is the same with and without the relaxation. The class is `internal` (`None !== LoopbackForTests`, `allowLoopback` differs). |
| `CT component/RedirectAdmissionComponentTest.kt` | `admitRedirect` on an `Untrusted` guard with previous `https://example.com/a`: `https://example.com/b` is `Admitted` and its `url` equals `Url.parse(...)`; `http://example.com/b` `Refused(InsecureRedirect)`; the parser mapping (§6.5): `http://u@example.com/` `UserInfo`, `ftp://x/` `Scheme`, `//x` `Scheme`, `http://x:99999/` `Port`, `http://x:0/` `Port`, `""`, `http://a\b/`, `http://a b/`, `http://a..b/`, `http://xn--n3h.net/`, `http://münchen.de/` and `http://[1:2:3:4:5:6:7::8]/` all `HostForm`; a parseable but denied target: `http://127.1/` `HostForm`, `http://169.254.169.254/` `DeniedAddress` with class `link-local`, `http://localhost/` `ReservedName`, `http://example.com:6379/` `Port`. A `Trusted` guard: `http://x/` is `Scheme`; `https://x/` is `Admitted`; with pins the unpinned target is `HostNotPinned`. No refusal's `toString()` holds the target text (canary in the host). |
| `CT component/AnswerJudgementComponentTest.kt` | `judgeAnswer` (§6.5): `Reject` with `[8.8.8.8, 10.0.0.1]` refuses with `DeniedAddress`/`private-use`; with `[10.0.0.1, 8.8.8.8]` the same; `DropDenied` with `[10.0.0.5, 8.8.8.8, 127.0.0.1, 1.1.1.1]` is `Allowed([1, 3])`; `DropDenied` with `[10.0.0.1, 127.0.0.1]` refuses with the first address's class (`private-use`); with `[8.8.8.8, 1.1.1.1]` both modes `Allowed([0, 1])`; the order is kept (`[1.1.1.1, 10.0.0.1, 8.8.8.8]` → `[0, 2]`); an empty list is `Allowed([])` under both modes and under `Trusted`; `Trusted` allows `[10.0.0.1, 127.0.0.1]` whole; a 5-byte address is denied (`unusual-length`) and drops under `DropDenied`; IPv6: `[fd00::1, 2606:4700:4700::1111]` under `DropDenied` is `Allowed([1])`. |

`jvmTest` (hand-written stubs over `Dns`, `Socket`, `SocketFactory`; no network; the `InetAddress` values come from `inet(...)`):

| File | Behaviours |
|---|---|
| `JT component/GuardedDnsComponentTest.kt` | Over `StubDns` and an `Untrusted` guard: an all-allowed answer `[8.8.8.8, 2606:4700:4700::1111]` is returned unchanged and in order; `Reject` with `[8.8.8.8, 10.0.0.5]` and with `[10.0.0.5, 8.8.8.8]` throws `PolicyRefusedUnknownHost` whose `refusal` is `DeniedAddress`/`private-use` and whose message equals `POLICY_REFUSED_HOST_MESSAGE`; `DropDenied` with `[10.0.0.5, 8.8.8.8, 127.0.0.1, 1.1.1.1]` returns `[8.8.8.8, 1.1.1.1]` in order; `DropDenied` with only denied addresses throws the marker (first refusal's class) and never returns an empty list; an empty delegate answer is a plain `UnknownHostException` with `EMPTY_ANSWER_MESSAGE` (not the marker, not containing the host) under both modes; a delegate `UnknownHostException` and a delegate `IllegalStateException` propagate as the same instances; an IPv6 `fd00::1` is denied, a 16-byte mapped `127.0.0.1` (the JDK collapses it to an `Inet4Address`) is denied, `64:ff9b::7f00:1` is denied and `64:ff9b::808:808` is allowed; **structure:** the class implements `Dns` without a `$$delegate` field (no `by`); **no forwarding:** over a delegate `Dns` whose own `newCall` counts its calls, a resolution through `GuardedDns`'s inherited `newCall(...)` calls the delegate's `lookup` once and its `newCall` never (a check of `declaredMethods` for `newCall` is not used: Kotlin 2.2.0 declares a bridge `public newCall(Dns$Request)` in `GuardedDns`, whose body is `invokespecial okhttp3/Dns.newCall`, whether or not the source overrides it, because OkHttp compiles `Dns` with `-Xjvm-default=all-compatibility`); **behaviour:** the inherited `newCall(Dns.Request("name.test"))` over a stub answering `[8.8.8.8, 10.0.0.5]` under `DropDenied` enqueues a callback (awaited on a `CountDownLatch` of 5 s) that receives only the `Dns.Record.IpAddress` for `8.8.8.8`, and under `Reject` receives a failure whose `policyRefusal()` is non-null; `toString()` is `GuardedDns`; a `Trusted` guard passes a private answer through whole; **DEBUG line:** with a logback `ListAppender` on `com.spartanlabs.webtools.http.GuardedDns` raised to `DEBUG` (restored in `finally`), a `DropDenied` lookup that drops two entries logs exactly one event whose formatted message contains `dropped 2 of 4`, the host name and the sorted classes `[loopback, private-use]` and contains none of the answer's address text; a lookup that drops nothing and a `Reject` refusal log nothing; **concurrency:** eight threads released together each run 500 lookups over a mixed answer under `DropDenied` and every result equals the expected list. The unusual-length case cannot be built here (`InetAddress.getByAddress` accepts only 4 or 16 bytes and the JDK's `InetAddress` cannot be subclassed outside its package); it is covered on the guard, in `AnswerJudgementComponentTest` and `UntrustedGuardComponentTest`. |
| `JT component/GuardedSocketFactoryComponentTest.kt` | `createSocket()` returns a `GuardedSocket`, a new instance each call, unconnected (`isConnected` false) and not closed, and is closed by the test; each of the four overloads (`(String, Int)`, `(String, Int, InetAddress, Int)`, `(InetAddress, Int)`, `(InetAddress, Int, InetAddress, Int)`) throws `SocketException` with `NOT_A_GUARDED_ENTRY_POINT_MESSAGE` and creates no socket; the factory is not an `SSLSocketFactory` (OkHttp rejects one); `toString()` is `GuardedSocketFactory`. |
| `JT component/GuardedSocketComponentTest.kt` | On an unconnected `GuardedSocket` of an `Untrusted` guard, each of `connect(SocketAddress, Int)` and `connect(SocketAddress)`: a denied `InetSocketAddress(inet("10.0.0.5"), 80)` throws `PolicyRefusedIo` with `DeniedAddress`/`private-use` (never a `ConnectException`; the message is the constant); the socket stays unconnected and unclosed; an unresolved `InetSocketAddress.createUnresolved("host.example", 80)` throws `PolicyRefusedIo` with class `unresolved`; a `SocketAddress` that is not an `InetSocketAddress` (a local subclass) the same; `inet("fd00::1")` and the 16-byte mapped `127.0.0.1` are denied; **before any I/O:** a marker is thrown for every denied or unresolved target (a real connect attempt can never produce it). The class declares exactly one public method, `connect` (reflection over `declaredMethods` filtered to public). Allowed targets reach `super.connect` only under the Level 3 (integration) tests. |
| `JT component/PolicyRefusalMarkersComponentTest.kt` | Both markers: constant messages `POLICY_REFUSED_HOST_MESSAGE` and `POLICY_REFUSED_IO_MESSAGE` for refusals built from different classes (the message never changes), `stackTrace` is empty, `PolicyRefusedUnknownHost` is an `UnknownHostException`, `PolicyRefusedIo` is an `IOException` and is not a `ConnectException`; `suppressed` can still be added to a marker. `policyRefusal()`: the marker itself returns its refusal; a marker as the `cause` of an `IOException` and as the cause's cause (depth 3); a marker in `suppressed` of an `IOException`; a marker in the `suppressed` of the cause; a plain `IOException` returns `null`; a cause cycle (`a.initCause(b)`, `b.initCause(a)`) returns `null` and terminates; a marker at depth 50 of a cause chain is found and at depth 150 is not (the 100-throwable bound); **order:** with the cause holding marker A and `suppressed` holding marker B, A is returned; with two markers in `suppressed`, the first. A canary placed in the refusal's class string never appears in a marker's `message`, `toString()` or `localizedMessage`. |
| `JT component/PolicyEnforcementComponentTest.kt` | `UrlGuard(untrusted).enforcement(StubDns.of("8.8.8.8"))`: `dns` is a `GuardedDns`, `socketFactory` is a `GuardedSocketFactory`, `proxy` is `Proxy.NO_PROXY`; applied to an `OkHttpClient.Builder` (`builder.dns(e.dns)`, `e.socketFactory?.let(builder::socketFactory)`, `e.proxy?.let(builder::proxy)`) and built (`build()` opens nothing): `client.proxy === Proxy.NO_PROXY`, `client.dns === e.dns`, `client.socketFactory === e.socketFactory`. For `trusted`: `dns` is the base stub itself, `socketFactory` and `proxy` are `null`; the built client has `proxy == null` and `socketFactory === SocketFactory.getDefault()`. The `Untrusted` bundle's `GuardedDns` judges: a lookup through `client.dns` of a private answer throws the marker. |
| `JT component/WebToolsDslComponentTest.kt` | Amendment 3. One test: each of `HttpHeaders.Builder::class.java`, `HttpRequest.Builder::class.java`, `HttpResponse.Builder::class.java`, `TransportSettings.Builder::class.java`, `OutboundUrlPolicy.Trusted.Builder::class.java` and `OutboundUrlPolicy.Untrusted.Builder::class.java` has `isAnnotationPresent(WebToolsDsl::class.java)`, asserted as `assertEquals(emptyList<String>(), missing)` over the names of the classes without it, so a failure names the builder. The ABI dump does not record a class's annotations, so this is the check that fails when a builder loses the marker; the marker's default retention, RUNTIME, keeps it visible to reflection (Amendment 5). The class KDoc says that a builder added later joins the list. |

### Level 3 — integration (`integrationTest`)

All in `jvmTest`, loopback only, no external network; every `MockWebServer` and `ServerSocket` is closed in `finally` or
`@AfterTest`; every wait has a timeout; no `Thread.sleep`. A loopback `ServerSocket` is bound to `127.0.0.1` explicitly
(`InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))`), never to `getLoopbackAddress()` (which may be IPv6).

| File | Behaviours |
|---|---|
| `JT integration/GuardedSocketLoopbackIntegrationTest.kt` | A `ServerSocket(0, 50, loopback)`. Under `UrlGuard(untrusted, GuardRelaxation.LoopbackForTests)`, `createSocket().connect(InetSocketAddress(loopback, port), 2000)` succeeds and the server's `accept()` (with `soTimeout` 2000) returns a connection. Under `GuardRelaxation.None` the same connect throws `PolicyRefusedIo` and a `soTimeout` 300 `accept()` throws `SocketTimeoutException` (no connection was made). Under the relaxation a non-loopback target (`10.0.0.5`) is still refused before any connect, and an unresolved target is refused. Both `connect` overloads are exercised. |
| `JT integration/GuardedSocketSocksIntegrationTest.kt` | `@Isolated`; the class sets and restores process-wide properties. A fake SOCKS listener (`ServerSocket` on loopback that counts and immediately closes accepted sockets) and a target `ServerSocket`. `socksProxyHost`=`127.0.0.1`, `socksProxyPort`=the listener's port and `socksNonProxyHosts`=`""` (the default list contains `127.*` and would hide the effect), each restored to its previous value (or cleared) in `finally`. **Control:** `SocketFactory.getDefault().createSocket().connect(target, 2000)` reaches the fake SOCKS listener (the listener counts at least one connection, awaited with a latch of 2 s; the connect fails with an `IOException`), so the path is reachable on this JDK. **The claim:** a `GuardedSocket` under the loopback relaxation connects straight to the target (the target `accept()` returns), and the listener's count did not move. A real `OkHttpClient` with `proxy(Proxy.NO_PROXY)` and the default factory behaves as the control (documented in the test's comment; asserted by the same counter). If the control fails on the build JDK, the test fails loudly: the research claim does not hold there (§3). |
| `JT integration/PolicyEnforcementOkHttpIntegrationTest.kt` | A real `OkHttpClient` built from a bundle, `retryOnConnectionFailure(false)`, `connectTimeout`/`callTimeout` of 10 s, fast fallback at its default, a `MockWebServer` on `127.0.0.1` (`enqueue(MockResponse(body = "ok"))`), requests to `http://stub.test:<port>/` and a counting `StubDns`. **(a)** Resolver guard, `Reject`: answer `[10.0.0.5, 10.0.0.6, fd00::1]` → the call throws an `IOException` that *is* a `PolicyRefusedUnknownHost` (marker at the top, unwrapped), `suppressed` empty, the stub called once, `server.requestCount == 0`. **(b)** Resolver guard, `DropDenied`, loopback relaxation, answer `[10.0.0.5, 127.0.0.1]` → status 200, body `ok`, `requestCount == 1`. **(c)** Socket guard only (`proxy(NO_PROXY)`, `GuardedSocketFactory`, the stub unguarded) with answer `[10.0.0.5, 10.0.0.6, fd00::1]` → the call throws; `policyRefusal()` is non-null; walking the top exception and its `suppressed` finds exactly three `PolicyRefusedIo` (every attempt was judged, fast fallback on) and no `ConnectException`; `requestCount == 0`. **(d)** Socket guard only with the loopback relaxation and answer `[10.0.0.5, 127.0.0.1]` → status 200, `requestCount == 1`, completed well inside the connect timeout (the denied attempt is refused instantly, not connected). **(e)** The stub is called once per new connection: two sequential calls (bodies read and closed) reuse the pooled connection (one lookup); after `client.connectionPool.evictAll()` a third call makes the second lookup. **(f)** `Trusted` bundle (`dns` the stub, no socket factory, no proxy) with answer `[127.0.0.1]` → 200, `requestCount == 1` (no address filtering). |

### Level 4a — deterministic

`commonTest` (runs on the JVM here; the code is JVM-free and compiled for `js` by the check module):

| File | Behaviours |
|---|---|
| `CT deterministic/AddressRangesRowsDeterministicTest.kt` | For every `RegistryRows.ipv4` and `RegistryRows.ipv6` row: the first and the last address are denied with exactly the row's `firstClass` and `lastClass`. The first and last address of each IPv4 block is also re-derived from the block with bit arithmetic independent of `CidrBlock` (a local helper) and equals the table's text. Each of the 25 IANA IPv4 rows and each of the 25 IPv6 rows appears (counts: 28 IPv4 entries = 26 prefixes + 2 non-registry; 25 IPv6 rows). `224.0.0.0` and `239.255.255.255` are `multicast`. |
| `CT deterministic/AddressRangesControlsDeterministicTest.kt` | **Allowed controls:** the 32 IPv4 boundary addresses (`1.0.0.0`, `9.255.255.255`, `11.0.0.0`, `100.63.255.255`, `100.128.0.0`, `126.255.255.255`, `128.0.0.0`, `169.253.255.255`, `169.255.0.0`, `172.15.255.255`, `172.32.0.0`, `191.255.255.255`, `192.0.1.0`, `192.0.1.255`, `192.0.3.0`, `192.31.195.255`, `192.31.197.0`, `192.52.192.255`, `192.52.194.0`, `192.88.98.255`, `192.88.100.0`, `192.167.255.255`, `192.169.0.0`, `192.175.47.255`, `192.175.49.0`, `198.17.255.255`, `198.20.0.0`, `198.51.99.255`, `198.51.101.0`, `203.0.112.255`, `203.0.114.0`, `223.255.255.255`), plus `8.8.8.8`, `1.1.1.1`, `93.184.216.34`, `168.63.129.15` and `168.63.129.17`; IPv6: `2000::`, `3fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, the ten neighbours of the five explicit blocks (`2000:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `2001:200::`, `2001:db7:ffff:ffff:ffff:ffff:ffff:ffff`, `2001:db9::`, `2001:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `2003::`, `2620:4f:7fff:ffff:ffff:ffff:ffff:ffff`, `2620:4f:8001::`, `3ffe:ffff:ffff:ffff:ffff:ffff:ffff:ffff`, `3fff:1000::`), `2606:4700:4700::1111`, `2001:4860:4860::8888`, `2a00:1450:4001::1`, `2001:200::1`, `::ffff:8.8.8.8` (`::ffff:808:808`) and `64:ff9b::808:808`. **Denied edges:** `1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` and `4000::` are `not-global-unicast`. **Embedded IPv4:** `::ffff:127.0.0.1` `loopback`, `64:ff9b::a00:1` `private-use`, `::ffff:a9fe:a9fe` `link-local`, `64:ff9b::7f00:1` `loopback`, `::ffff:0:0` and `64:ff9b::` `this-host`. **Non-registry rules:** `168.63.129.16` `azure-wireserver`; ISATAP `2a01:4f8:1:1:0:5efe:7f00:1`, `…:1:100:5efe:…`, `…:1:200:5efe:…`, `…:1:300:5efe:…` are `isatap`, `…:1:400:5efe:…` and `…:1:0:5eff:…` are allowed; an ISATAP-shaped address in `2001:db8::/32` is `documentation` (an explicit row wins); `fe80::5efe:7f00:1` is `not-global-unicast`. **Cloud endpoints:** `169.254.169.254` and `169.254.170.2` `link-local`, `100.100.100.200` `shared-address-space`, `192.0.0.192` `ietf-protocol-assignments`, `fd00:ec2::254` `not-global-unicast`, `168.63.129.16` `azure-wireserver`. Lengths 0, 1, 3, 5, 8 and 17 are `unusual-length`. |
| `CT deterministic/StrictIpDeterministicTest.kt` | `parseV4` accepts `0.0.0.0`, `1.2.3.4`, `10.20.30.40`, `100.64.0.1`, `203.0.113.255`, `255.255.255.255` (bytes asserted) and refuses `""`, `1`, `1.2`, `1.2.3`, `1.2.3.4.5`, `256.1.1.1`, `1.2.3.256`, `01.2.3.4`, `1.02.3.4`, `1.2.3.04`, `00.0.0.0`, `0x7f.0.0.1`, `1.2.3.4.`, `.1.2.3.4`, `1..2.3`, `1.2.3.`, `+1.2.3.4`, `-1.2.3.4`, ` 1.2.3.4`, `1.2.3.4 `, `1.2.3.4a`, `1.2.3.1000`, `1.2.3.4/8`, `１.2.3.4` (fullwidth), `1.2.3.٤` (Arabic-Indic digit). `parseV6` accepts (16 bytes asserted by hex) `::`, `::1`, `1::`, `1:2:3:4:5:6:7:8`, `1:2:3:4:5:6:7::`, `::2:3:4:5:6:7:8`, `2001:db8::1`, `2001:DB8::1`, `0:0:0:0:0:0:0:0`, `::ffff:1.2.3.4`, `::1.2.3.4`, `64:ff9b::1.2.3.4`, `1:2:3:4:5:6:1.2.3.4`, `0000:0000:0000:0000:0000:ffff:7f00:0001`, `1::2:3`, and refuses `""`, `:`, `:::`, `::::`, `1:2:3:4:5:6:7`, `1:2:3:4:5:6:7:8:9`, `1:2:3:4:5:6:7::8`, `1::2::3`, `:1:2:3:4:5:6:7`, `1:2:3:4:5:6:7:`, `12345::`, `g::1`, `::1%eth0`, `::1%25eth0`, `[::1]`, `::1/128`, `::1.2.3`, `::1.2.3.4.5`, `::01.2.3.4`, `::1.2.3.256`, `1.2.3.4::`, `::1.2.3.4:1`, `::ffff:1.2.3.4:5`, `1.2.3.4`, ` ::1`, `::1 `, `::fffg`, `::١`. `formatV6`: all zero → `::`; `::1`; `2001:db8::1`; `1:2:3:4:5:6:7:0` (a single zero group is not compressed); `2001:0:0:1::1` (from `2001:0:0:1:0:0:0:1`: the longest run, three groups, wins); `2001:0:0:1:0:0:1:1` prints `2001::1:0:0:1:1` (two runs of two: the first wins); `100::1:ffff:ffff:ffff:ffff`; an IPv4-mapped array prints `::ffff:7f00:1` (no dotted form); a 15-byte array is `IllegalArgumentException`. **Round trip:** for 2,000 seeded random 16-byte arrays (seed 7, with runs of zero groups forced in) `parseV6(formatV6(b))` equals `b`. |
| `CT deterministic/StrictIpUrlAgreementDeterministicTest.kt` | `StrictIp.parseV6` agrees with `Url`'s IPv6 canonicalisation (`UrlHost.kt`) on the IPv6 inputs of `UrlCorpus` (the bracket content of every `accept`, `reject`, `confusion` and `crash` input that starts `http://[`) and on 20,000 strings of `IpLiteralGenerator.ipv6(7, 20_000)`: for each literal `L`, `Url.parse("http://[$L]/")` and `StrictIp.parseV6(L)` — when `Url` refuses, `parseV6` returns `null`; when `Url` accepts, `parseV6` is non-null and its bytes equal the bytes of `Url.host` (parsed by `parseV6` when the host holds `:`, else the dotted host as the low 4 bytes of a mapped address). Floors: at least 2,000 of the generated literals are accepted by `Url`, so the generator exercises both outcomes. A disagreement prints the seed and the literal. |
| `CT deterministic/PublishedBypassVectorsDeterministicTest.kt` | Runs `BypassVectors.vectors` and nothing else is entered twice. **Stage 1:** every vector with a `problem` makes `Url.parse(text)` fail with exactly that `UrlProblem`. **Stage 2:** every other vector parses, its `Url.host` equals `host`, and an `Untrusted` default guard's `checkHop(url, null)` gives `outcome` (`HostForm`, `ReservedName`, `DeniedAddress` with `rangeClass`, `Port`, or allowed). Tests by group: stage 1 (P), the port rule, HostForm (H), DeniedAddress (D, G), ReservedName (R), names and controls (N, C). **Catalogue tags** (a row's `parent` is a comma-separated list; a row is tagged when the list contains the tag exactly, so `F1` does not match `F10`): the rows tagged `F1` (the parent research's host-form catalogue) number at least 40 and all hold; those tagged `F10` (the advisory vectors) at least 25 and all hold; a test fails if a tag is removed so a group shrinks. A failure prints the vector's id and its visible text. |
| `CT deterministic/HostRulesDirectInputDeterministicTest.kt` | `HostRules.classify` fed directly with text `Url.parse` never lets through (`bypass-vectors.md` §3.7): `127.0.0.1/0`, `127.0.0.1 foo`, `127.0.0.1\tfoo`, `fe80::1%eth0`, `０.0.0.0` (fullwidth zero), the empty string and a lone `-%` are `HostForm`; `LOCALHOST` is `ReservedName`; `0X7F.1` is `HostForm`; `LOCALHOST.` is `ReservedName`; `.`, `..`, `a..b`, `.a`, `a.`+`.` (two trailing dots) are `HostForm`; `A.B.C` folds to `a.b.c` and is a `Name`; a Kelvin sign (`\u212A`, KELVIN SIGN) in a name is `HostForm` (the fold is ASCII-only); a `Literal` carries 4 bytes for a dotted quad and 16 for IPv6 (`::ffff:1.2.3.4` is a 16-byte `Literal`, not collapsed); the policy suffix list is honoured at a label boundary only (`corp.example` refuses `x.corp.example`, not `xcorp.example`). |
| `CT deterministic/CidrBlockParseDeterministicTest.kt` | **Accept** (and the canonical `toString()`): `10.0.0.0/8`, `0.0.0.0/0`, `192.0.2.1/32`, `172.16.0.0/12`, `fd00::/8`, `::/0`, `2001:db8::/32`, `::1/128`, `FD00::/8` (→ `fd00::/8`), `2001:0db8:0000:0000:0000:0000:0000:0000/32` (→ `2001:db8::/32`). **Refuse, by kind:** shape (not exactly one `/`) — `""`, `10.0.0.0`, `10.0.0.0/8/9`; address — `/8` (an empty address), `10.0.0/8`, `010.0.0.0/8`, `256.0.0.0/8`, `127.1/8`, `[::1]/128`, `::1%eth0/128`, `fe80::1%25eth0/64`, `::g/8`, ` 10.0.0.0/8`; prefix — `10.0.0.0/` (an empty prefix), `10.0.0.0/33`, `::/129`, `10.0.0.0/08`, `10.0.0.0/+8`, `10.0.0.0/-1`, `10.0.0.0/8 `, `10.0.0.0/１`, `10.0.0.0/1000`; host bits — `10.0.0.5/8`, `192.0.2.1/24`, `fd00::1/8`, `::1/127`. Each failure's message is exactly one of the four constants and the right one; `10.0.0.5/8` is refused, not masked. |
| `CT deterministic/GuardReasonOrderDeterministicTest.kt` | The reason that wins when two rules apply, one row each (policy, URL, previous → reason): Untrusted `http://127.0.0.1:6379/` → `DeniedAddress`; `http://localhost:6379/` → `ReservedName`; `http://127.1:6379/` → `HostForm`; `http://example.com:6379/` → `Port`; `http://example.com:6379/` with previous `https://example.com/` → `Port` (port before downgrade); `http://127.0.0.1/` with previous `https://example.com/` → `DeniedAddress`; `http://example.com/` with previous `https://example.com/` → `InsecureRedirect`; Untrusted `checkRequest` of `http://127.0.0.1:6379/` with `Authorization` → `DeniedAddress` (URL before headers); `http://example.com/` with `Authorization` → `SensitiveHeader`. Trusted default `http://x.test/` with previous `https://x.test/` → `Scheme`; pinned `{"api.example.com"}` with `allowCleartext`, `http://other.test/`, previous `https://api.example.com/` → `HostNotPinned`; pinned, `allowCleartext`, `http://api.example.com/`, previous `https://api.example.com/` → `InsecureRedirect`. User-info has no row (a `Url` never carries any). |

`jvmTest` (the JDK is the oracle or the subject; Java 11 API only):

| File | Behaviours |
|---|---|
| `JT deterministic/JdkPredicateGapDeterministicTest.kt` | The 26 addresses of the parent research F7 are all denied by `AddressRanges.deny`: the ten the JDK predicates catch (`127.0.0.1`, `0.0.0.0`, `10.0.0.1`, `169.254.169.254`, `224.0.0.1`, `::1`, `::`, `fe80::1`, `fec0::1`, `ff02::1`) and the sixteen they miss (`100.64.0.1`, `192.0.0.1`, `198.18.0.1`, `192.0.2.1`, `240.0.0.1`, `255.255.255.255`, `fd00::1`, `fc00::1`, `::7f00:1`, `64:ff9b::7f00:1`, `2002:7f00:1::`, `2001:db8::1`, `100::1`, `::ffff:0:7f00:1`, `2001::1`, `5f00::1`). The test also records, without asserting the JDK's output, which of the 26 the JDK predicates (`isAnyLocalAddress`, `isLoopbackAddress`, `isLinkLocalAddress`, `isSiteLocalAddress`, `isMulticastAddress`) flag, as the documentation of the gap (an `assertTrue(missedByJdk.isNotEmpty())` is the only JDK-dependent assertion: the gap exists). |
| `JT deterministic/AddressRangesJdkSupersetDeterministicTest.kt` | Seeded (seed 7) random addresses, 200,000 four-byte and 200,000 sixteen-byte (the 16-byte ones biased: a forced first byte from `00 fe ff 20 26 fc` in half of them, a forced mapped prefix in a tenth), each built with `InetAddress.getByAddress`: **whenever any JDK predicate flags the address, `AddressRanges.deny(address.address)` is non-null** (a superset property: the table is never looser than the JDK's own check; no pin of the JDK's output). The address handed to `deny` is the JDK object's `address` (4 bytes for a collapsed mapped address), as the guards pass it. A failure prints the seed and the address. |
| `JT deterministic/StrictIpJdkDifferentialDeterministicTest.kt` | Seed 7; 100,000 strings of `IpLiteralGenerator.ipv4` and 100,000 of `ipv6`. For every string `StrictIp` accepts: IPv4 → `InetAddress.getByName(s)`; IPv6 → `InetAddress.getByName("[" + s + "]")` (never an unbracketed non-literal, which would reach the resolver); the result's `address` equals `StrictIp`'s bytes (a 16-byte mapped array compared with the JDK's 4-byte `Inet4Address` through its last four bytes), its length matches the family, and `toString()` starts with `/` (no host name, so no lookup happened). Floors: at least 25,000 accepted IPv4 and 10,000 accepted IPv6. For IPv6 strings `StrictIp` refuses, the bracketed form either parses (counted as "JDK looser") or throws `UnknownHostException`, and nothing else is thrown. The strict parser is a subset of the JDK's: whatever it accepts, the JDK connects to the same address. |
| `JT deterministic/OkHttpLiteralRegexDeterministicTest.kt` | OkHttp 5.5.0 sends a host that matches `([0-9a-fA-F]*:[0-9a-fA-F:.]*)\|([\d.]+)` straight to `InetAddress.getByName` and never to `Dns` (`VERIFY_AS_IP_ADDRESS` in `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HostnamesCommon.kt` at tag `parent-5.5.0`; copied into the test with that source tag). For every string of `IpLiteralGenerator` (both families, 50,000 each, seed 7) plus the 36 host forms of the parent research F1 (its table publishes 36; the figure 39 given earlier was the research's own count) and every host of `BypassVectors` that the regex matches: `HostRules.classify` is never a `Name` (it is a `Literal` or a `Refused`), so the resolver guard can never be the only layer between such a host and a socket. The converse (a name that does not match) is not asserted. |

### Level 4b — end-to-end

None in this unit. The end-to-end flows (a request through the guards to a socket, redirects, caps) arrive with the transport
(unit 6) and the call facades (unit 9). The Level 3 (integration) test `PolicyEnforcementOkHttpIntegrationTest` is the closest
this unit gets: a real `OkHttpClient` over loopback.

### Level 4c — non-functional

`commonTest`:

| File | Behaviours |
|---|---|
| `CT nonfunctional/ClassifierLinearTimeNonFunctionalTest.kt` | The classifier is linear in the input. Inputs of 64k and 1M characters in six shapes: a long name `a.` repeated, a long digit run, a long run of `:`, a long label with a final `x`, a long run of `0x1`-style parts joined by dots, a long `[`-free hex run; each through `HostRules.classify`, `StrictIp.parseV4`, `StrictIp.parseV6` and `CidrBlock.parse` (as `<shape>/8`) without an exception; the best of three runs at 16× the length is under 100× the time of 1× (a quadratic parse gives about 256×), after a warm-up; a failure on a loaded host is rerun once before it is treated as a defect. `AddressRanges.deny` is constant-time (a million calls over mixed addresses finish, with no ratio assertion). |
| `CT nonfunctional/ClassifierGarbageNonFunctionalTest.kt` | 50,000 seeded strings (seed 11) from `UrlInputGenerator.generate(11, 50_000)` plus 50,000 from `IpLiteralGenerator` plus 50,000 drawn from a pool of `0-9 a-f A-F : . % / [ ] - _`, space, TAB, NUL, `é`, `日`, `\uD800`, `x` (length 0 to 40): `HostRules.classify`, `StrictIp.parseV4/parseV6`, `CidrBlock.parse` and `UrlGuard.admitRedirect` never throw. Invariants on every success: `parseV4(t) != null` ⇒ the bytes joined with `.` equal `t`; `parseV6(t) != null` ⇒ `parseV6(formatV6(bytes))` equals the bytes; `CidrBlock.parse(t)` succeeds ⇒ `CidrBlock.parse(block.toString())` equals the block; `classify(t)` of a string with a character outside the host charset (after the ASCII fold) is `Refused(HostForm)`; a `Literal`'s address is 4 or 16 bytes; `admitRedirect(t, previous)` is `Admitted` only when `Url.parse(t)` succeeds. A failure prints the seed and the visible input. |
| `CT nonfunctional/PolicySecrecyNonFunctionalTest.kt` | A canary (`SECRET-CANARY-7f3a`, and its lower-case form `secret-canary-7f3a` where a host must be lower-case) is placed in every position that can fail or print: a pinned host (as `secret-canary-7f3a.example`, valid, then as `SECRET-CANARY-7f3a:443`, invalid), a suffix (valid and invalid), a `CidrBlock.parse` text in each of the four failure positions, an `Untrusted` policy's extra range and suffix, an `admitRedirect` target whose host holds it, a URL host held by a `GuardRefusal`. No builder message, `CidrBlock.parse` message, `toString()` of a `Trusted`, an `Untrusted`, a `TransportSettings` or a `GuardRefusal`, and no `AnswerVerdict`/`RedirectAdmission` refusal's `toString()` contains the canary in either case or an address or a range of the policy. Every builder and `CidrBlock.parse` message is one of the constants (collected into a set and compared). (`CidrBlock.toString()` prints the block by design and is excluded; a policy prints only counts.) |

`jvmTest`: none beyond the concurrency check inside `GuardedDnsComponentTest`; the markers' constancy is a Level 2 (component) test.

### Level 5 — user acceptance (manual; procedure P11)

No `testing.uat` package: nothing here is interactive product behaviour. The pass is a reading and an IDE check recorded in
`final-implementation.md` §5: the ABI dump read against §7.9 and the architecture §9 tier table; Quick Documentation of
`OutboundUrlPolicy.untrusted`, `TransportSettings`, `Untrusted.extraDeniedRanges`, `CidrBlock.parse`, `MixedAnswers` and `WebToolsDsl` (Amendment 3) as a
consumer sees them (tier, numbers and the `extraDeniedRanges` statement present); IntelliJ's import with the new files and no
spurious red in `commonMain` for the `js` check module (the maintainer's, with the other IDE items of #62 and #63); and the
Level 3 (integration) measurements read (§9.2 P6).

### What cannot be tested automatically

- **IDE behaviour** (P11): import, Quick Documentation, absence of false red.
- **The marker's compile-time effect** (Amendment 3): that an implicit reach into an outer builder does not compile is a
  compile error, which no passing test can show. The P12 canary shows it once; `WebToolsDslComponentTest` keeps the marker on
  every builder.
- **KDoc quality**, beyond the mechanical coverage check of P8. `-Xexplicit-api` is compiler-enforced for visibility and return
  types only.
- **Real DNS answers and real networks:** only stubs and loopback are used. A name that resolves to a public and a private
  address on a real resolver, a real NAT64 prefix, and HTTP/2 connection coalescing over real TLS are not exercised.
- **The reachability of `168.63.129.16` and of an ISATAP address from a given host**, and what answers there: the table denies
  them whatever the host's network is.
- **The deployer's own addresses:** a service on the deployer's public address or behind its own NAT64 prefix is not closable
  by a table; `extraDeniedRanges` is the seam and the KDoc says so.
- **JDKs 18 to 20 and 22 to 26:** read by inference from 11, 17, 21 and 27 (§3); only the build JDK is run.
- **A `Socket.setSocketImplFactory` or `ProxySelector.setDefault` by other code in the JVM.**

### 9.2 Procedures

Every command is run from the repository root. None runs a `webtools-udp`, `webtools-scraping`, `webtools-browser` or
`build-logic` suite on its own, none runs the root `build` or `test`, and none runs a `publish*` task. The one nested-build run,
`:build-logic:fixturesPublicationCheck`, is a dependency of `:webtools-http:check` and runs because its inputs include
`webtools-http`'s `commonMain` and `jvmMain`, which this unit changes: it exercises the change. Record each command's result in
`final-implementation.md` §4 (Measurements), in the order they ran.

**P1 — baseline and branch (before any edit).**
1. `git branch --show-current` is `feat/issue-64-url-policy`; `git log --oneline` shows the three research commits (the questions, the
   findings and their verification) on top of `ad0ae63`; `git status --short` shows nothing but plan-board files (`dashboard.html`, `plan.js`, `plan.json` under
   `docs/plans/`) and this issue's own untracked documents.
2. `./gradlew :webtools-http:apiCheck :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest
   :webtools-http:nonfunctionalTest :webtools-http:integrationTest :webtools-http-commoncheck:compileKotlinJs` is
   `BUILD SUCCESSFUL`. Record the test counts per level from `webtools-http/build/test-results/<level>Test/*.xml` (the baseline
   for the counts after S5).

**P2 — S1 and S2: settings, `CidrBlock`, `StrictIp`, the policies.**
1. `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL`. Expected
   warnings: the Gradle-10 `archives` deprecation and `JS Environment Not Selected`; no compiler `w:`, no opt-in diagnostic.
2. `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest
   :webtools-http:nonfunctionalTest` — all pass; the result XML lists `OutboundUrlPolicyConsumerGatingTest`,
   `TransportSettingsComponentTest`, `TrustedPolicyComponentTest`, `UntrustedPolicyComponentTest`,
   `PolicyWithSettingsComponentTest`, `CidrBlockComponentTest`, `StrictIpDeterministicTest`, `StrictIpUrlAgreementDeterministicTest`,
   `CidrBlockParseDeterministicTest` (the 4c classes arrive with S3, which they use).

**P3 — S3: the classification core.**
1. The same two commands as P2; the result XML now lists `AddressRangesRowsDeterministicTest`,
   `AddressRangesControlsDeterministicTest`, `PublishedBypassVectorsDeterministicTest`, `HostRulesDirectInputDeterministicTest`,
   `GuardReasonOrderDeterministicTest`, `TrustedGuardComponentTest`, `UntrustedGuardComponentTest`,
   `GuardRelaxationComponentTest`, `RedirectAdmissionComponentTest`, `AnswerJudgementComponentTest`, `ClassifierLinearTimeNonFunctionalTest`,
   `ClassifierGarbageNonFunctionalTest` and `PolicySecrecyNonFunctionalTest`. Record the count of vectors
   in `PublishedBypassVectorsDeterministicTest` and the two catalogue-tag floors.
2. *The `js` canary.* Add a throwaway `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Canary.kt` containing
   `internal val canary = java.io.File("x")`: `:webtools-http:compileKotlinJvm` succeeds and
   `:webtools-http-commoncheck:compileKotlinJs` fails with `Unresolved reference`; delete the file; both succeed again.

**P4 — S4: the dump and commit 1.**
1. `./gradlew :webtools-http:apiDump`, then read `git diff webtools-http/api/webtools-http.api` against §7.9 and the architecture
   §9 tier table: only additions; the public types of R1 and nothing else. `./gradlew :webtools-http:apiCheck` passes.
   `grep -c '\$default' webtools-http/api/webtools-http.api` prints `5`.
2. `grep -nE "StrictIp|HostRules|HostVerdict|AddressRanges|DeniedRange|GuardRelaxation|UrlGuard|GuardRefusal|RedirectAdmission|AnswerVerdict|Guarded|PolicyRefused|PolicyEnforcement|PolicyRefusals|withSettings|canonicalPinnedHost|canonicalHostSuffix|matchesPrefix" webtools-http/api/webtools-http.api`
   prints nothing.
3. Commit 1 (§11).

**P5 — S5: the JVM guards.**
1. `./gradlew :webtools-http:compileKotlinJvm` succeeds with no compiler `w:`.
2. `./gradlew :webtools-http:componentTest` passes; the XML lists `GuardedDnsComponentTest`, `GuardedSocketFactoryComponentTest`,
   `GuardedSocketComponentTest`, `PolicyRefusalMarkersComponentTest`, `PolicyEnforcementComponentTest`. In
   `GuardedDnsComponentTest` the inherited-`newCall` test passed (the first measurement of research gap 3): record it.

**P6 — Level 3 (integration), and the JDK-based Level 4a (deterministic) tests.**
1. `./gradlew :webtools-http:integrationTest :webtools-http:deterministicTest` passes. Record, for `final-implementation.md` §4,
   the three source-read claims these tests settle: **(i)** the SOCKS control — did the default factory's socket reach the fake
   SOCKS listener on this JDK (yes/no) and did `GuardedSocket` bypass it; **(ii)** marker surfacing with fast fallback — the
   top exception and the `suppressed` markers of cases (a) and (c); **(iii)** the inherited `newCall` routing (P5 step 2).
   A claim that does not hold is a deviation (§2 of the record) and a research claim that did not hold (§6), not a quiet fix.
2. Record the differential floors reached (accepted IPv4 and IPv6 counts) and the JDK build version.

**P7 — the floor and the full check.**
1. `./gradlew :webtools-http:verifyJavaFloor` passes; `webtools-http/build/reports/java-floor/verifyJavaFloor.txt` is read (the
   new classes are major 55).
2. `./gradlew :webtools-http:check :webtools-http-commoncheck:check` — `BUILD SUCCESSFUL`. `:build-logic:fixturesPublicationCheck`
   runs (about 45 to 52 s once). Record the durations and every level's test count against P1's.

**P8 — mechanical rules.** Each command prints nothing unless it says otherwise. A comment may name a forbidden API, so the first
commands drop comment lines with `| grep -vE ':[0-9]+:[[:space:]]*(//|\*|/\*)'` (shown once; append it to each).
- `grep -rnE "java\.|javax\.|@Jvm|runCatching|\.lowercase\(|\.uppercase\(|ignoreCase|removeFirst|removeLast|String\.format|Regex\(" webtools-http/src/commonMain | grep -vE ':[0-9]+:[[:space:]]*(//|\*|/\*)'`
- `grep -rn "catch" webtools-http/src/commonMain webtools-http/src/jvmMain` (comments dropped) prints only `NonFatal.kt`.
- `grep -rn "data class" webtools-http/src/commonMain webtools-http/src/jvmMain` (comments dropped) prints nothing.
- `grep -rn "TODO" webtools-http/src` prints nothing.
- `grep -rn "OptIn" webtools-http/src/commonTest webtools-http/src/jvmTest` (comments dropped) prints nothing.
- `grep -rn "newCall\|by delegate\|: Dns by\|SocketFactory.getDefault\|ConnectException\|InetAddress.getByName\|getAllByName" webtools-http/src/jvmMain webtools-http/src/commonMain`
  (comments dropped) prints nothing.
- `grep -rln "logger(" webtools-http/src/commonMain` prints only `Log.kt`; `grep -rn "logger(" webtools-http/src/jvmMain` prints
  `Log.jvm.kt` and `GuardedDns.kt` only.
- `grep -rn "Thread.sleep" webtools-http/src/commonTest webtools-http/src/jvmTest` prints nothing in the new files.
- The internal-name grep of P4 step 2 on the final dump prints nothing; `grep -c '\$default'` prints `5`.
- KDoc coverage: for each file under `webtools-http/src/commonMain` and `jvmMain`, list the `public` and `protected` declaration
  lines whose previous non-blank line is neither the end of a KDoc block (`*/`) nor an annotation; the list is empty:
  `awk 'FNR==1{prev=""} /^[ \t]*(public|protected)[ \t]/ && prev !~ /\*\/[ \t]*$/ && prev !~ /^[ \t]*@/ {print FILENAME ":" FNR ": " $0} $0 !~ /^[ \t]*$/ {prev=$0}' <files>`.
  (The constructor-parameter KDoc of `TransportSettings` and the policies is `/** ... */` above each parameter, which the
  awk accepts as the previous line.)
- Package and path agree for every new test file: `for f in $(git ls-files -o --exclude-standard webtools-http/src/commonTest webtools-http/src/jvmTest | grep '\.kt$'); do p=$(grep -m1 '^package ' "$f" | sed 's/package //'); d=$(dirname "$f" | sed 's#.*/kotlin/##; s#/#.#g'); [ "$p" = "$d" ] || echo "$f: $p != $d"; done`.
- One class per test file: each new file under a level folder (not `support/`) has exactly one top-level `class`.

**P9 — mutation sensitivity (once; not committed).** For each mutation: copy the file to a scratch directory outside the
repository, apply the edit, run the named task, expect the named class to fail, copy the file back, `diff` against the copy prints
nothing, and re-run the class green.
1. `GuardedDns.kt`: add `override fun newCall(request: Dns.Request): Dns.Call = delegate.newCall(request)` →
   `:webtools-http:componentTest --tests "*GuardedDnsComponentTest*"` fails: the no-forwarding test and the two
   inherited-`newCall` behaviour tests.
2. `GuardedSocket.kt`: `Socket(Proxy.NO_PROXY)` → `Socket()` → `:webtools-http:integrationTest --tests "*GuardedSocketSocksIntegrationTest*"`
   fails (the guarded socket reaches the fake SOCKS listener) — if P6's control held.
3. `AddressRanges.kt`: delete the `168.63.129.16/32` row → `AddressRangesControlsDeterministicTest` and
   `PublishedBypassVectorsDeterministicTest` (G1) fail in `:webtools-http:deterministicTest`.
4. `AddressRanges.kt`: the ISATAP mask `0xFCFF` → `0xFFFF` → `AddressRangesControlsDeterministicTest` fails (the `100`/`300` forms).
5. `TransportSettings.kt`: drop `idleConnectionTtl` from `equals` → `TransportSettingsComponentTest` (the `idleConnectionTtl`
   property test) fails.
6. `UrlGuard.kt`: return `null` for a loopback address before consulting `extraDeniedRanges` → `GuardRelaxationComponentTest` fails.

**P10 — the unaffected modules and the diff.** `git diff --stat -- webtools-udp webtools-scraping webtools-browser build-logic
webtools-http/build.gradle.kts webtools-http/src/jvmTestFixtures webtools-http-commoncheck` is empty. `git diff --stat README.md`
shows one insertion and one deletion. `git status --short` shows no `Canary.kt`, no scratch copy, no `build/` directory.
No other module's suite is run: those modules are proven unaffected by the empty diff (and, for the three legacy modules,
need no compile: nothing they depend on changed).

**P11 — the Level 5 (user acceptance) pass.** Read and record in `final-implementation.md` §5: the dump against §7.9 and the
architecture §9 tier table (every new type Stable Core; `OutboundUrlPolicy` sealed; nothing gated); Quick Documentation of the
six declarations named above (IDE; the maintainer's); the IntelliJ import (the maintainer's); the P6 measurements.

**P12 — S8: the `WebToolsDsl` marker (Amendment 3).** Record each step in `final-implementation.md` §4 under "S8", in order.
1. *The canary, before the marker.* Write `webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/DslScopeCanary.kt`
   exactly as §7.11 gives it. `./gradlew :webtools-http:compileTestKotlinJvm` — `BUILD SUCCESSFUL`: without the marker each of
   its five statements compiles, which is the hazard.
2. *The marker.* Add `<cm>WebToolsDsl.kt` and the six `@WebToolsDsl` lines (§7.11).
   `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL`; the only compiler
   line is `w: JS Environment Not Selected`.
3. *The canary, after the marker.* `./gradlew :webtools-http:compileTestKotlinJvm` fails with five `e:` lines, one at each of
   lines 12 to 16 of `DslScopeCanary.kt`, each a DSL-scope violation (the member cannot be called with an implicit receiver);
   record them verbatim. A different count is recorded verbatim with the lines it names: the step holds when each of lines 12
   to 16 is named and no `e:` line names another file. An `e:` line in another file is an existing implicit reach: give that
   statement its label (§7.11), record the fix, and repeat this step.
4. *Delete the canary.* `git status --short` shows no `DslScopeCanary.kt`; `./gradlew :webtools-http:compileTestKotlinJvm` —
   `BUILD SUCCESSFUL`.
5. *The tests and the dump.* Write `WebToolsDslConsumerGatingTest` and `WebToolsDslComponentTest` (§9).
   `./gradlew :webtools-http:apiDump`: `git diff --stat webtools-http/api/webtools-http.api` is `1 file changed, 3 insertions(+)`,
   the three lines of §7.9 at the end of the file, and no other line moves; a dump that differs is the as-built fact (§3),
   recorded with the difference. `grep -c '\$default'` still prints `5`, and the internal-name grep of P4 step 2, run
   word-bounded (`grep -w`), prints nothing.
6. *The level tasks.* `./gradlew :webtools-http:apiCheck :webtools-http:gatingTest :webtools-http:componentTest
   :webtools-http:deterministicTest :webtools-http:nonfunctionalTest :webtools-http:integrationTest
   :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL`; the result XML lists `WebToolsDslConsumerGatingTest`
   (3 tests) under `gatingTest` and `WebToolsDslComponentTest` (1 test) under `componentTest`; 0 failures.
7. *A mutation (once; not committed).* Copy `<cm>TransportSettings.kt` to a scratch directory outside the repository, delete its
   `@WebToolsDsl` line, and run `./gradlew :webtools-http:componentTest --tests "*WebToolsDslComponentTest*"`: it fails, and its
   message names `com.spartanlabs.webtools.http.TransportSettings$Builder`. Copy the file back; `diff` against the copy prints
   nothing; the class passes again.
8. *The mechanical rules.* For the new and changed files, P8's commands print what P8 says: the KDoc-coverage awk over
   `commonMain` and `jvmMain`, the `OptIn` grep over `commonTest` and `jvmTest`, and the package-and-path and
   one-class-per-file checks over the two new test files.
9. *The floor and the full check.* `./gradlew :webtools-http:verifyJavaFloor` passes, its report counting one class more than
   P7 (`WebToolsDsl`). `./gradlew :webtools-http:check :webtools-http-commoncheck:check` — `BUILD SUCCESSFUL`
   (`:build-logic:fixturesPublicationCheck` runs again, since `commonMain` changed). Record each level's count against P7's:
   `gatingTest` 4/14 → 5/17 and `componentTest` 37/297 → 38/298 (classes/tests), the others unchanged; `jvmTest` 90 classes
   and 504 tests.
10. *The unaffected modules.* P10's `git diff --stat` is still empty.
11. Commit 3 (§11). Then bring `final-implementation.md` up to date: its status line, the `WebToolsDsl` entry of §1 and item 8
    of §3.

---
## 10. Risks and edge cases

- **Stable Core from the first release.** The three public configuration types and the policy pair are Stable Core in an
  unreleased `1.0.0`: after the release a renamed property, a changed default number, a third `OutboundUrlPolicy` subclass or a
  changed signature is a major bump or a documented behaviour change. The mitigations are in the shape: required `from`, internal
  constructors, `sealed` with a parameterless base, no public `data class`, per-property equality tests, the numbers written in
  the KDoc. A `Duration` getter is name-mangled in the ABI (`getCallTimeout-<hash>`), so a property's type can never change
  from `Duration` without a break.
- **ABI dump details.** The `Result`-returning `CidrBlock.Companion.parse` is name-mangled (`parse-IoAF18A`), as `Url.Companion.parse`
  is. `trusted`/`untrusted` each add a synthetic `$default` bridge (the trailing `= {}`); nothing else in this unit defaults a
  parameter. Whether Kotlin 2.2.0 writes `PermittedSubclasses` for the sealed base at `jvmTarget = 11` is unconfirmed and
  matters only to Java callers (a non-goal).
- **Class initialisation.** No policy instance lives in the sealed base's companion or in any static field of the base. The
  default seeds are built inside the factory functions, and `TransportSettings`'s defaults live in `TransportSettings`'s own
  companion, which is a base of nothing. A static cycle between the base and its subclasses (and a two-thread deadlock on the
  class-initialisation locks) is thus impossible by construction; it cannot be reproduced by a unit test, so the design is the
  only defence.
- **IANA drift.** The table is a dated snapshot. A new special block carved out of reserved space outside `2000::/3` is denied
  without an edit; one inside `2000::/3`, or a new IPv4 row, needs the refresh procedure of §6.4. The two non-registry rows must
  survive a refresh (their comment says so).
- **False denies, by design.** `Untrusted` refuses: tailnet and carrier-grade-NAT addresses (`100.64.0.0/10`); a host reachable
  only through 6to4 or Teredo; a DNS64 network's `64:ff9b:1::/48` local-use prefix (an operator prefix inside global unicast space
  is indistinguishable and unaffected); the anycast service prefixes; and any request carrying `Authorization`, `Proxy-Authorization`,
  `Cookie`, `Set-Cookie` or a sensitive-flagged header. To fetch from a private address or with a credential, use `Trusted`.
  The KDoc says so.
- **Not closable by a table.** A network's own NAT64 prefix and a service on the deployer's own public address pass every rule;
  `extraDeniedRanges` is the seam, and its KDoc names both cases. The port rule narrows the second.
- **Pinned-host confusion.** `Trusted` pins match by exact string equality with `Url.host`: `api.example.com.` (trailing dot),
  `evilapi.example.com` and `api.example.com.evil.test` are `HostNotPinned`; the user-info forms never reach the check (`Url`
  refuses them). A `Trusted` pin on `127.1` matches only the host text `127.1` (`Trusted` applies no host-form rule).
- **Sub-millisecond durations.** A `Duration` of 1.5 ms is valid; a transport hands OkHttp `inWholeMilliseconds` (at least 1), never
  nanoseconds. A transport that subtracts elapsed time from `callTimeout` for a later hop must fail with `Timeout` itself when
  less than 1 ms remains, because OkHttp rejects a positive timeout under 1 ms (§12).
- **Stackless exceptions.** The markers skip the stack, so a stray marker has no trace; the transport's one `WARN` per refusal
  (reason name, range class, `Url.redacted()`) is the diagnostic. The marker's refusal object carries the reason and class.
- **A marker is "something was denied", not "the host is unreachable".** With the resolver guard under `Reject` a denied address
  never reaches a socket, so a socket marker means something bypassed the resolver guard. Under `DropDenied` a socket marker
  can appear only for an address the resolver guard let through. A transport that reports any marker in the tree as a refusal
  is correct in both cases.
- **Concurrency.** Everything shared is immutable after construction: `UrlGuard`, the policies, `TransportSettings`, the tables,
  `GuardedDns` (required by `okhttp3.Dns`: "safe for concurrent use"), `GuardedSocketFactory`. `GuardedSocket` is per connection
  and stateless beyond `super`; fast fallback closing a losing attempt while it is inside `connect` adds no state to race on. The
  builders are not thread-safe and say so.
- **Performance.** A hop costs one pass over the host (a handful of linear scans) and at most 28 four-byte comparisons for a
  literal; an address costs at most 28 or 16 comparisons. `GuardedDns` costs one pass per new connection (OkHttp does one lookup
  per new connection). The only allocation worth noting is the `split` of a host into labels; hosts are at most 253 characters
  when they come from `Url`.
- **JS.** The new `commonMain` files use `ByteArray`, `Int` bit operations, `String.split/substring/indexOf`, `Duration` and `Set`
  only; `:webtools-http-commoncheck:compileKotlinJs` is the proof, and P3 step 2 shows it would catch a JVM API.
- **Test hazards.** The SOCKS test changes process-wide properties (it is `@Isolated`, restores in `finally`, and no parallel mode
  is enabled). Loopback servers bind `127.0.0.1` explicitly. The fast-fallback timing is not asserted (each refused attempt is
  instantaneous); every wait has a timeout. The differential's floors fail loudly if the generator stops producing accepted strings.
  Windows firewall prompts do not occur for loopback-only listeners.
- **Build cost.** `:webtools-http:check` re-runs the fixtures-publication check (about 45 to 52 s) because this unit changes
  `commonMain` and `jvmMain`.
- **A builder added later without the marker** (Amendment 3). A nested block could reach an outer builder implicitly through
  it, and `WebToolsDslComponentTest` checks only the builders on its list. §12 makes every later builder carry the marker and
  join that list and the list in `WebToolsDsl`'s KDoc. A Kotlin/JS release that also warned about an implicit RUNTIME
  retention would print one warning per builder in the check module's compile again, and fail nothing (Amendment 5).
- **Cross-repo impact.** None in this repository. A downstream consumer adopts the policy through the transport and facades of
  later units.

---

## 11. Version control

- **Branch:** `feat/issue-64-url-policy`. It exists and holds the research commits; the implementer confirms it (P1) rather than
  cutting it again.
- **Commits.** Each leaves the build green. The type and the split are the final call at commit time; subject lines end with
  `(Issue #64)`; **no attribution trailers**.
  1. `feat: add the outbound-URL policies, transport settings and the common address classifier (Issue #64)` — this `plan.md`;
     `webtools-http/src/commonMain/**` (`TransportSettings.kt`, `MixedAnswers.kt`, `CidrBlock.kt`, `OutboundUrlPolicy.kt`,
     `StrictIp.kt`, `HostRules.kt`, `AddressRanges.kt`, `GuardRelaxation.kt`, `UrlGuard.kt`); every new file under
     `webtools-http/src/commonTest/**` (tests and support); the regenerated `webtools-http/api/webtools-http.api`. It builds on
     its own: the `jvmTest` compilation includes `commonTest`, and no `jvmMain` file is needed.
  2. `feat: add the JVM resolver and socket guards that enforce the outbound-URL policy (Issue #64)` — `webtools-http/src/jvmMain/**`
     (`PolicyRefusals.kt`, `GuardedDns.kt`, `GuardedSocketFactory.kt`, `GuardedSocket.kt`, `PolicyEnforcement.kt`); every new file
     under `webtools-http/src/jvmTest/**` (including the JDK-based Level 4a (deterministic) classes and `StubDns.kt`). The dump
     does not change in this commit (everything here is `internal`).
  3. `feat: mark the webtools-http builders with the WebToolsDsl DSL marker (Issue #64)` (Amendment 3, S8) — this `plan.md`
     (Amendments 3 to 5); `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WebToolsDsl.kt`; the
     `@WebToolsDsl` line in `HttpHeaders.kt`, `HttpRequest.kt`, `HttpResponse.kt`, `TransportSettings.kt` and
     `OutboundUrlPolicy.kt`; `WebToolsDslConsumerGatingTest.kt` and `WebToolsDslComponentTest.kt`; the regenerated
     `webtools-http/api/webtools-http.api` (three added lines).
  4. `docs: name the outbound-URL policies in the README's webtools-http row (Issue #64)` — `README.md` line 11 (§7.10).
  5. `docs: record the url-policy as-built results and impact (Issue #64)` — the reconciled `final-implementation.md`, this
     issue's `impact.md`, the parent's `impact.md` (its §1 row, §7 and §8), and the three dated corrections of
     `../architecture.md` (Amendment 4).
- **Never carried into these commits:** any plan-board file (`dashboard.html`, `plan.js`, `plan.json` under `docs/plans/`),
  whether modified (`55-http-client-stack/`) or untracked (`64-url-policy/`): they are committed with the landing, not with this
  work; also never `docs/plans/.live/`, IDE files, `build/` directories, a `Canary.kt`, the P12 canary `DslScopeCanary.kt`, or a scratch backup of the P9 and P12 mutations.
  The research files under `research/` are already committed.
- **No version bump.** `webtools-http` `1.0.0` is unreleased and **not a release candidate**; no existing module moves.
- **The PR** is based on `master`, its body states that `webtools-http` `1.0.0` is not a release candidate and stays unpublished
  until #55's stack is complete, that the landing adds the outbound-URL policy subsystem to `webtools-http` (new public types in
  its dump, an `internal` classifier and the `internal` JVM guards), and `Closes #64`. After the merge the branch is deleted,
  locally and on the remote.

---

## 12. Interfaces with sibling units

**Names this unit takes** (a sibling must not reuse them), in `com.spartanlabs.webtools.http`:
- *public* — `TransportSettings` (with `Builder`, `Companion.TrustedDefaults`, `Companion.UntrustedDefaults`) and the top-level
  function `TransportSettings(from, configure)`; `OutboundUrlPolicy` with `Trusted`, `Untrusted`, their `Builder`s and the
  companion functions `trusted` and `untrusted`; `CidrBlock`; `MixedAnswers`; `WebToolsDsl` (Amendment 3).
- *internal, `commonMain`* — `StrictIp`, `HostVerdict` (`Literal`, `Name`, `Refused`), `HostRules`, `DeniedRange`, `AddressRanges`,
  `GuardRelaxation`, `GuardRefusal`, `RedirectAdmission` (`Admitted`, `Refused`), `AnswerVerdict` (`Allowed`, `Refused`), `UrlGuard`,
  `OutboundUrlPolicy.withSettings`, `canonicalPinnedHost`, `canonicalHostSuffix`; and `matchesPrefix`, `private` to
  `CidrBlock.kt`.
- *internal, `jvmMain`* — `PolicyRefusedUnknownHost`, `PolicyRefusedIo`, `Throwable.policyRefusal()`, `GuardedDns`,
  `GuardedSocketFactory`, `GuardedSocket`, `PolicyEnforcement`, `UrlGuard.enforcement`, and the constants
  `POLICY_REFUSED_HOST_MESSAGE`, `POLICY_REFUSED_IO_MESSAGE`, `EMPTY_ANSWER_MESSAGE`, `NOT_A_GUARDED_ENTRY_POINT_MESSAGE`.
- *test support* (`com.spartanlabs.testing.support.webtools.http`) — `PolicyTestSupport.kt` (`url`, `addr`, `cidr`,
  `untrustedGuard`, `trustedGuard`, `outcome`), `IpLiteralGenerator`, `RegistryRows`, `BypassVectors`, `StubDns` and `inet`.
- *No name is added to* `com.spartanlabs.webtools.http.testing` (the fixtures).

**The builder DSL** (Amendment 3). Every builder that a later unit adds to `webtools-http` and passes to a consumer's lambda as
its receiver carries `@WebToolsDsl`, and joins the list of `WebToolsDslComponentTest` and the list in `WebToolsDsl`'s KDoc. A
nested block of the module's builders then reaches an outer builder only through its label.

**What this unit takes from unit 3 (`http-core`, #62, landed).**
- `Url` (`scheme`, `host`, `port`, `parse`), `UrlProblem` and `UrlParseException` (`internal`; `admitRedirect` maps `problem`), the
  `internal` `canonicalHost` (pins and suffixes are canonicalised by it), `RefusalReason` and its nine constants,
  `TransportFailure.RefusedByPolicy(reason)` (not built here; unit 6 builds it), `HttpRequest` (`url`, `headers`),
  `HttpHeaders.Entry.isSensitive`, the `internal` `logger`/`Log` seam and `HttpSyntax.asciiLower`.

**What this unit takes from unit 4 (`test-support`, #63).** Nothing, and it adds nothing to `src/jvmTestFixtures`.

**What this unit provides to unit 6 (`okhttp-transport`, #65).**
- **The policy and its settings.** Each setting's meaning for the transport and the OkHttp 5.5.0 limit it respects:

  | Setting | The transport uses it as | Respects |
  |---|---|---|
  | `callTimeout` | the deadline of the whole fetch across hops; each `Call` gets the remaining time | at least 1 ms, at most `Int.MAX_VALUE` ms; when less than 1 ms remains the transport fails with `Timeout` itself |
  | `connectTimeout`, `readTimeout`, `writeTimeout` | `OkHttpClient.Builder` timeouts, handed as `inWholeMilliseconds` | `checkDuration`: not above `Integer.MAX_VALUE` ms, not a positive value under 1 ms |
  | `maxResponseBytes` | the cap on decoded body bytes; `BodyTooLarge(limit, declared)` | at least 1 |
  | `maxRedirects` | a followable redirect beyond the cap is `TooManyRedirects(maxRedirects)`; 0 follows none | not negative |
  | `allowHttpsToHttp` | not read by the transport: `UrlGuard.checkHop` reads it for the downgrade rule | — |
  | `maxConcurrentRequests`, `maxConcurrentRequestsPerHost` | `Dispatcher.maxRequests` and `maxRequestsPerHost` | `Dispatcher` requires both at least 1; per-host never above the total |
  | `idleConnectionTtl` | the `ConnectionPool` keep-alive duration | `RealConnectionPool` requires it above 0 |
- **`UrlGuard.checkRequest(request)`** before hop 1 and **`UrlGuard.admitRedirect(target, previous)`** for each redirect target
  (the text of the target the transport resolved with `HttpUrl.resolve`), both before any `Call`; a `GuardRefusal` becomes
  `RefusedByPolicy(refusal.reason)`; `UrlGuard.checkHop(url, previous)` is the primitive both use. A refusal at hop 1 costs zero
  sockets.
- **`UrlGuard.enforcement(baseDns)`** for its `OkHttpClient.Builder`: apply `dns`, and `socketFactory` and `proxy` when non-null;
  keep `fastFallback` at its default and `retryOnConnectionFailure(false)`.
- **The markers and `policyRefusal()`** for its failure mapping: check `throwable.policyRefusal()` first, before
  `UnknownHostException`, timeouts, TLS and connect mapping; a non-null result becomes `RefusedByPolicy(reason)` and exactly one
  `WARN` carrying `Url.redacted()`, the reason name and `refusal.rangeClass`. The marker is found in the exception, its cause chain
  and its suppressed lists; a call timeout may carry it as the cause of `InterruptedIOException("timeout")`.
- **`GuardRelaxation.LoopbackForTests`** for `LoopbackTransports.untrusted`: `UrlGuard(OutboundUrlPolicy.untrusted { ... },
  GuardRelaxation.LoopbackForTests)`; and `OutboundUrlPolicy.trusted { allowCleartext = true }` for `LoopbackTransports.trusted`
  (loopback servers speak `http`).
- **What stays the transport's:** resolving `Location` with `HttpUrl.resolve` and catching what it throws (`//[1:2:3:4:5:6:7::8]/`
  throws `ArrayIndexOutOfBoundsException`, and `HttpUrl` catches only `IllegalArgumentException`); the cross-origin strip of
  sensitive headers; the redirect count and the shared deadline; the capped body read; the `ExchangeGuard`; the one `WARN` per
  refusal; and the transport vectors of `research/bypass-vectors.md` §5: T01 (redirects into private space: zero connects), T02
  (cross-protocol redirects), T03 (a `Location` that `HttpUrl.resolve` throws on), T04 (a `Location` `HttpUrl` accepts and `Url.parse`
  refuses), T05 (DNS rebinding: every new connection re-resolves through the guard), T06 (mixed answers), T07 (process proxies:
  `Untrusted` never uses one), T08 (credentials across an origin change, judged on the canonical `Url`), T10 (pinned-host
  confusion). T09 (an absolute URL in a path argument) belongs to unit 9.

**What this unit provides to unit 9 (`call-facades`, #68).** `UrlFetcher`'s pre-check through `UrlGuard.checkRequest` (a
refusal costs nothing: no permit, no call; the facade creates the `RefusedByPolicy(refusal.reason)` and so logs the one `WARN`
with `Url.redacted()`, the reason name and `refusal.rangeClass`, as the transport does for its own refusals); `withSettings` for raising concurrency to the budget's burst; and the client's default
policy `OutboundUrlPolicy.trusted { pinnedHosts = setOf(baseUrl.host) }` (`Url.host` is canonical and is accepted by the pin
canonicalisation, an IPv6 host included).

**What this unit provides to unit 10 (`feeds`, #69).** `OutboundUrlPolicy.untrusted { }` for its owning factory.

**Unit 11 (`http-docs`, #70).** It documents the policies from this unit's KDoc (tier, numbers, the `extraDeniedRanges` statement,
`MixedAnswers`) and the README row of §7.10.

**Units 7 and 8.** Nothing.

---

## 13. Sequencing and follow-ups

**Order of operations.** P1; S1 and P2; S2 and P2; S3 and P3 (including the `js` canary); S4 and P4, then commit 1; S5 with P5
and P6; S6 with P7, P8, P9 and P10, then commit 2; the README and P11 (S7); S8 with P12, then commit 3; the README's commit
(commit 4); the reconciliation, with the three dated corrections of `../architecture.md` (commit 5). The implementer
appends each deviation, with its reasoning, to `final-implementation.md` §2 the moment it makes it. A stage that fails its
procedure stops the next one.

**Follow-ups deliberately left.**
- **Additive later:** public `CidrBlock` accessors or `contains`, further `TransportSettings` properties, a refresh script for the
  IANA registries (the procedure is documented; a script is not built), a JDK-version matrix for the Level 3 (integration) SOCKS
  measurement (JDK 24 to 26).
- **The compile-fail check of the subclass gate** remains the follow-up recorded by #62 (not this unit's seam); the same
  check would hold the effect of `WebToolsDsl`, which the P12 canary shows once (Amendment 3).

---

## 14. Known gaps

- **The code blocks have not been compiled** (see the preface). The first compile is P2; the signatures were checked against the
  as-built sources, but Kotlin's overload and smart-cast resolution on the policy builders and the guard's `when` branches is
  the likeliest source of a mechanical fix.
- **Three source-read claims are unmeasured until P5 and P6:** a JVM-wide SOCKS setting reaching the default factory's socket and
  not a `Socket(Proxy.NO_PROXY)` subclass; the surfacing of a refusal through a real call with fast fallback on (marker at the top,
  the rest in `suppressed`); the inherited `newCall` routing through `lookup`. The tests are the measurement; a result that
  differs is a deviation.
- **Real DNS answers and real networks were not exercised;** nor HTTP/2 connection coalescing over TLS (a coalesced connection's
  address was admitted when it was made), nor a host with an ISATAP interface, nor the reachability of `168.63.129.16`.
- **Not closable by a table:** a network's own NAT64 prefix and the deployer's own public address (R3, §10).
- **JDKs 18 to 20 and 22 to 26 were read by inference** from 11.0.32, 17.0.21, 21.0.13 and 27; only the build JDK runs. The
  Windows-specific `DualStackPlainSocketImpl` of JDK 11 was not read (the shared `Socket(Proxy)` branch decides the SOCKS question).
- **The IANA "no change since 2025-10-09" rests on both registry pages' stamps,** not on a row-by-row diff against an archive.
  The cost-of-denying statements for the globally reachable rows are a reading of each row's purpose, not a measurement.
- **The `GuardedDns` unusual-length case is unreachable from a test:** `InetAddress.getByAddress` accepts only 4 or 16 bytes and the
  JDK's class cannot be subclassed outside its package. It is covered on the guard (`judgeAnswer`, `checkAddress`).
- **The four refusing `SocketFactory` overloads have no precedent** in a published guard (the search found none); refusal is a
  reasoned fail-closed choice. OkHttp 5.5.0 never calls them.
- **A `Socket.setSocketImplFactory` or `ProxySelector.setDefault` by other code** in the same JVM is not defended against.
- **IDE behaviour** (import, Quick Documentation, the `js` check module's resolution) is the maintainer's (P11).
- **The `$default` and sealed-class dump lines are inferred** until the first `apiDump` (§3).
- **Only the maintainer's Windows host runs the build;** Linux and macOS are unexercised. The dump is written CRLF here; `apiCheck`
  compares line by line.

---

## 15. Considerations

None. Every call this unit needs is made in the design above.
