# Final implementation: `url-policy` (Issue #64)

The as-built record of `SpartanLaboratories/WebTools#64`, unit 5 (`url-policy`) of
[#55's architecture](../architecture.md). It states what was built, where the work departs from
[`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for
the repository and for the sibling units is in [`impact.md`](impact.md).

- **Status:** built through stage S8 of [`plan.md`](plan.md) §5, with plan Amendments 1 to 3 and 5 and the fixes of review
  pass 1 (§4); procedures P1 to P12 ran (P11's two IDE items are the maintainer's, §5). Commits 1 and 2 of plan §11 are on the
  branch; commit 3 (S8), 4 (the README row) and 5 (this record) follow.
- **Branch and PR:** `feat/issue-64-url-policy`, cut from `master` at `ad0ae63`, with the research commits and the
  implementation commits `fd93ff9` and `b46f58a` (commits 1 and 2 of plan §11) on it; PR TBD (it closes #64).
- **Architecture:** [`../architecture.md`](../architecture.md) carries three dated corrections (2026-10-26) for this unit,
  at §6.3.1 and §7.4 (plan Amendment 4).
- **Host of the measurements:** Windows 11, JDK 23.0.2 (the build JDK), Gradle 9.7.1, Kotlin 2.2.0; the maintainer's
  machine. Linux and macOS were not exercised.
- **Versions moved:** none. `webtools-http` 1.0.0 stays unreleased until #55's stack is complete. It is not a
  release candidate at this landing.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with plan Amendments 1, 2 and 5. The mechanical fixes made on the way and the
fixes of review pass 1 are in §4, and stage S8 (plan Amendment 3) under "S8" there.

- **The public surface** (`webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/`, package
  `com.spartanlabs.webtools.http`). Every type is Stable Core, and its KDoc names the tier.
  - `TransportSettings.kt`: the final `TransportSettings` over an `internal` constructor, with its ten settings and
    hand-written `equals`, `hashCode` and `toString`. Its `Builder` has one `var` per setting, seeded from the value it derives
    from. The companion values are `TrustedDefaults` (30 s call; 10 s connect, read and write; 5,242,880 bytes; 5 redirects;
    no `https` to `http`; 16 requests, 5 per host; a 4 s idle-connection TTL) and `UntrustedDefaults` (15 s; 5 s;
    2,097,152 bytes; 3 redirects; no `https` to `http`; 8 and 2; 4 s). The factory `TransportSettings(from) { }` validates
    every value when it builds.
  - `OutboundUrlPolicy.kt`: the sealed `OutboundUrlPolicy` with `settings`; the final `Trusted` (`pinnedHosts`,
    `allowCleartext`) and `Untrusted` (`allowedPorts`, `extraDeniedRanges`, `extraDeniedHostSuffixes`, `mixedAnswers`), each
    over an `internal` constructor and with a `Builder` that also has `settings { }`; the companion factories `trusted { }`,
    `trusted(from) { }`, `untrusted { }` and `untrusted(from) { }`. Beside them, `internal`: `canonicalPinnedHost`,
    `canonicalHostSuffix` and `withSettings`. The default port set is a value of the file, so the sealed base's class
    initialiser constructs no subclass.
  - `CidrBlock.kt`: the final `CidrBlock` over a `private` constructor; `CidrBlock.parse(text): Result<CidrBlock>` with four
    constant failure messages (shape, address, prefix, host bits); a canonical `toString`; `internal` `contains` and
    `isIpv4`; and the file-private `matchesPrefix`.
  - `MixedAnswers.kt`: `Reject` and `DropDenied`.
- **The classification core** (`commonMain`, `internal`, pure and JVM-free):
  - `StrictIp`: strict dotted-quad and RFC 4291 IPv6 parsing, and RFC 5952 formatting.
  - `HostRules` with `HostVerdict` (`Literal`, `Name`, `Refused`): total on any text; the lenient numeric forms, the
    reserved names and the policy's suffixes.
  - `AddressRanges` with `DeniedRange`: the 26 IPv4 registry prefixes, nested ones first, plus `224.0.0.0/4` and
    `168.63.129.16/32`; IPv4-mapped and NAT64 `64:ff9b::/96` addresses judged by their embedded IPv4; everything outside
    `2000::/3` denied; the 15 IPv6 rows inside it, nested ones first; the ISATAP rule; and the dated snapshot comment with
    its refresh procedure.
  - `GuardRelaxation` (`None`, `LoopbackForTests`).
  - `UrlGuard` with `checkHop`, `checkRequest`, `admitRedirect`, `checkAddress` and `judgeAnswer`, and its results
    `GuardRefusal` (the reason and, for `DeniedAddress`, the range class), `RedirectAdmission` and `AnswerVerdict`.
- **The JVM enforcement** (`webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/`, `internal`):
  - `PolicyRefusals.kt`: `PolicyRefusedUnknownHost` (an `UnknownHostException`) and `PolicyRefusedIo` (an `IOException`,
    never a `ConnectException`), each with a constant message and no stack trace; and `Throwable.policyRefusal()`, which
    searches the cause chain and the `suppressed` lists, at most 100 throwables.
  - `GuardedDns`: it overrides `lookup` only and inherits `newCall`. An empty answer becomes an `UnknownHostException` with a
    constant message; under `DropDenied` it writes one `DEBUG` line per lookup that dropped addresses.
  - `GuardedSocketFactory`: `createSocket()` returns a `GuardedSocket`; the four connecting overloads throw a
    `SocketException`.
  - `GuardedSocket`, a `Socket(Proxy.NO_PROXY)`: `connect(SocketAddress, Int)` judges the target before any I/O, and an
    unresolved or non-`Inet` target is refused as `DeniedAddress` with the class `unresolved`.
  - `PolicyEnforcement` and `UrlGuard.enforcement(baseDns)`: for `Untrusted` the guarded resolver, the guarded socket factory
    and `Proxy.NO_PROXY`; for `Trusted` the base resolver alone.

  Their KDoc carries the wiring, thread, logging and after-throw contracts (review pass 1, §4).
- **The ABI dump** `webtools-http/api/webtools-http.api`: 127 added lines at `fd93ff9`, the public types of R1 and nothing
  `internal`. Commit 2 and review pass 1 left it byte-identical.
- **The tests:** 34 classes and 228 tests, all green, in the level packages of plan §9.
  - Level 1 (gating): `OutboundUrlPolicyConsumerGatingTest` (5).
  - Level 2 (component): 15 classes (146). In `commonTest`: `TransportSettings` 23, `CidrBlock` 7, `TrustedPolicy` 13,
    `UntrustedPolicy` 18, `PolicyWithSettings` 4, `TrustedGuard` 9, `UntrustedGuard` 11, `GuardRelaxation` 14,
    `RedirectAdmission` 6, `AnswerJudgement` 8. In `jvmTest`: `GuardedDns` 14, `GuardedSocketFactory` 3, `GuardedSocket` 5,
    `PolicyRefusalMarkers` 8, `PolicyEnforcement` 3.
  - Level 3 (integration): three classes (10). `GuardedSocketLoopback` 3, `GuardedSocketSocks` 1,
    `PolicyEnforcementOkHttp` 6.
  - Level 4a (deterministic): 12 classes (60). In `commonTest`: `StrictIp` 6, `StrictIpUrlAgreement` 2, `CidrBlockParse` 6,
    `AddressRangesRows` 5, `AddressRangesControls` 8, `PublishedBypassVectors` 9, `HostRulesDirectInput` 5,
    `GuardReasonOrder` 11. In `jvmTest`: `JdkPredicateGap` 2, `AddressRangesJdkSuperset` 2, `StrictIpJdkDifferential` 2,
    `OkHttpLiteralRegex` 2.
  - Level 4c (non-functional): three classes (7). `ClassifierLinearTime` 3, `ClassifierGarbage` 1, `PolicySecrecy` 3.
  - Support, not tests: `PolicyTestSupport.kt`, `IpLiteralGenerator.kt`, `RegistryRows.kt` and `BypassVectors.kt` (249
    vectors; 115 tagged `F1`, 52 tagged `F10`) in `commonTest`, and `StubDns.kt` in `jvmTest`.

  With the earlier units' tests, `jvmTest` runs 500 tests in 88 classes.
- **`README.md`:** line 11, the purpose cell of the `webtools-http` row, as plan §7.10 writes it (S7).
- **`WebToolsDsl`** (`WebToolsDsl.kt`): the public `@DslMarker` annotation (`@Target(CLASS)`, `@MustBeDocumented`, the
  default retention, `RUNTIME`, left implicit (plan Amendment 5), Stable Core) on the six builders the module passes to a
  consumer's lambda: `HttpHeaders.Builder`,
  `HttpRequest.Builder`, `HttpResponse.Builder`, `TransportSettings.Builder`, `OutboundUrlPolicy.Trusted.Builder` and
  `OutboundUrlPolicy.Untrusted.Builder`, one annotation line each. A nested builder block reaches an outer builder only
  through its label (`this@untrusted.allowedPorts`); the P12 canary showed the five implicit reaches fail to compile once.
  Kept by `WebToolsDslConsumerGatingTest` (gating, 3 tests: each block sets its own receiver, the labels reach the outer
  policy, request and response builders) and `WebToolsDslComponentTest` (component, 1 test: the marker present on all six
  by reflection, a failure naming the builder). The dump grows by the annotation's three lines (plan §7.11, Amendment 3; S8).
- **Unchanged** (P10): `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic`,
  `webtools-http/build.gradle.kts`, `webtools-http/src/jvmTestFixtures` and `webtools-http-commoncheck`, and every source file
  of `webtools-http` that existed before this unit (`Url`, `UrlHost.kt`, `RefusalReason` and `TransportFailure` among them).
  S8 adds one annotation line to three of them: `HttpHeaders.kt`, `HttpRequest.kt` and `HttpResponse.kt`.

## 2. Deviations from the plan

Each entry: what differs from the plan, where, and why. Entries are appended at the moment they are made.

None. Plan Amendments 1, 2 and 5 are part of the plan and were followed: the refusal rows of
`CidrBlockParseDeterministicTest` (S1; §4, P2), the behavioural no-forwarding check of `GuardedDnsComponentTest` (S5; §4,
P5), and the retention of `WebToolsDsl` left at its default (S8; §4, S8 step 2). Amendment 3, stage S8, records its
results in §4.

Changes that alter no rule of the design, no signature and no message are listed in §4 where they happened:
- the two lines of the ABI dump that plan §7.9 inferred differently: a synthetic accessor constructor on `CidrBlock`, and no
  constructor line for the sealed base (P4; plan §3 makes the dump the as-built fact);
- the mechanical fixes to the S5 tests: `Dns.Record.IpAddress.address` read as a property, the `Dns.Callback.onRecords`
  parameter named `last`, and two `is` checks the compiler proves false written as `Class.isInstance` (P5);
- the fixes of review pass 1, all documentation and test hygiene. Among them, `matchesPrefix` narrowed from `internal` to
  `private`, since its only caller is `CidrBlock.contains` in the same file (plan Amendment 4 shows it so), and the time
  bound of case (d) of `PolicyEnforcementOkHttpIntegrationTest` tightened from 10 s to 2 s.

## 3. Acceptance

Against plan §1 "Acceptance", from the measurements in §4 and §5:

1. `:webtools-http:compileKotlinJvm` and `:webtools-http-commoncheck:compileKotlinJs` pass after S1, S2 and S3, with
   `w: JS Environment Not Selected` the only compiler line (P2, P3). The `js` canary fails with `Unresolved reference 'java'`,
   and both tasks pass once it is removed (P3 step 2). The check module compiles the nine new `commonMain` files (P4 step 4)
   — **met**.
2. The five level tasks pass, and their result XML lists every test class of plan §9: the S1 and S2 classes (P2), the S3
   classes (P3), the S5 classes (P5), and the Level 3 (integration) and JDK-based Level 4a (deterministic) classes (P6). At
   the end `jvmTest` runs 500 tests in 88 classes with 0 failures, against P1's 272 in 54 (P7) — **met**.
3. `apiDump` adds 127 lines, additions only. Read against plan §7.9 and the architecture §9 tier table, they hold the public
   types of R1 and nothing `internal`: the word-bounded internal-name grep prints nothing, `grep -c '$default'` prints `5`,
   every new type is Stable Core, and nothing is gated. `apiCheck` passes, and the dump is byte-identical after review
   pass 1 (P4, §5) — **met**.
4. `verifyJavaFloor` passes: "174 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3575 classes on the runtime
   classpath", every class at major 55 or below. `:webtools-http:check :webtools-http-commoncheck:check` passes in 1 min 12 s,
   `:build-logic:fixturesPublicationCheck` included (P7) — **met**.
5. The six mutations of P9 each fail the class plan §9.2 names: mutation 1 with Amendment 2's set (the no-forwarding test
   and the two inherited-`newCall` behaviour tests), and mutation 2 through the fake SOCKS listener. Each file was copied
   back, its `diff` against the copy is empty, and the three level tasks ran green together afterwards (P9) — **met**.
6. `git diff --stat` for `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic`,
   `webtools-http/build.gradle.kts`, `webtools-http/src/jvmTestFixtures` and `webtools-http-commoncheck` is empty, and no
   other module's suite ran (P10) — **met**.
7. The mechanical rules of P8 hold. The internal-name grep ran word-bounded, because the plan's pattern `DeniedRange` also
   matches the public `extraDeniedRanges` (P4 step 2). `README.md` line 11 reads as plan §7.10 writes it:
   `git diff --numstat README.md` is `1 1`, and the file keeps its CRLF line endings and its UTF-8 em dash (S7) — **met**.
8. `WebToolsDsl` is in `commonMain` as plan §7.11 writes it and on the six builders it names: the P12 canary compiled before
   the marker and failed after it with one DSL-scope error at each of its five statements and none elsewhere; `apiDump` added
   exactly the three lines of plan §7.9 and `apiCheck` passes; `WebToolsDslConsumerGatingTest` (3) and
   `WebToolsDslComponentTest` (1) pass; `:webtools-http-commoncheck:compileKotlinJs` passes with no compiler line but
   `w: JS Environment Not Selected` once the retention is implicit (§4 S8 step 2) (plan Amendments 3 and 5) — **met**.

## 4. Measurements

The output of each procedure of [`plan.md`](plan.md) §9.2, in the order they ran. Host: Windows 11, JDK 23.0.2
(the build JDK), Gradle 9.7.1.

### P1 — baseline and branch

1. `git branch --show-current` is `feat/issue-64-url-policy`; `git log --oneline` shows the three research commits
   (`9930a32` questions, `c3d3dc7` findings, `aa918ea` verification) on top of `ad0ae63`; `git status --short`
   shows only plan-board files under `docs/plans/` and this issue's own untracked documents.
2. `./gradlew :webtools-http:apiCheck :webtools-http:gatingTest :webtools-http:componentTest
   :webtools-http:deterministicTest :webtools-http:nonfunctionalTest :webtools-http:integrationTest
   :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL`; the one compiler line is
   `w: JS Environment Not Selected`. Baseline counts from `webtools-http/build/test-results/<level>Test/*.xml`:

   | Level task | Classes | Tests | Failures |
   |---|---|---|---|
   | `gatingTest` | 3 | 9 | 0 |
   | `componentTest` | 22 | 151 | 0 |
   | `deterministicTest` | 21 | 81 | 0 |
   | `nonfunctionalTest` | 7 | 15 | 0 |
   | `integrationTest` | 1 | 16 | 0 |

### P2 — S1: settings, `CidrBlock`, `StrictIp`

1. `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`;
   the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic.
2. `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest
   :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes:
   `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6),
   `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 failures.

**Plan inconsistency found during S1, resolved by plan Amendment 1 (not a deviation of the implementation).** Plan §9's
`CidrBlockParseDeterministicTest` listed `/8` and `10.0.0.0/` under the *shape* kind, while the `parse` of plan §7.2,
implemented as written, answers the *address* message for `/8` (the address text before the slash is empty) and the
*prefix* message for `10.0.0.0/` (the prefix text after the slash is empty). Amendment 1 rules that §7.2 governs: a shape
failure is a text without exactly one `/`; an empty address is the address failure; an empty prefix is the prefix failure.
The code did not change; the test asserts the two exact constants.

### P2 — S2: the policies

1. `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s`
   (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`).
2. `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest
   :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes:
   `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13),
   `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); `CidrBlockParseDeterministicTest` (6) with
   its two rows tightened under Amendment 1; 0 failures.

### P3 — S3: the classification core

1. `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest
   :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in
   14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures:
   `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8),
   `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5),
   `GuardReasonOrderDeterministicTest` (11), `TrustedGuardComponentTest` (9), `UntrustedGuardComponentTest` (11),
   `GuardRelaxationComponentTest` (14), `RedirectAdmissionComponentTest` (6), `AnswerJudgementComponentTest` (8),
   `ClassifierLinearTimeNonFunctionalTest` (3), `ClassifierGarbageNonFunctionalTest` (1, 150,000 inputs in 2.3 s),
   `PolicySecrecyNonFunctionalTest` (3). `BypassVectors` holds **249 vectors**; the catalogue tags count **115 rows
   tagged `F1`** (floor 40) and **52 rows tagged `F10`** (floor 25), all holding.
2. The `js` canary: with a throwaway `Canary.kt` holding `internal val canary = java.io.File("x")` in `commonMain`,
   `:webtools-http:compileKotlinJvm` is `BUILD SUCCESSFUL` and `:webtools-http-commoncheck:compileKotlinJs` fails with
   `e: ... Canary.kt:3:23 Unresolved reference 'java'.`; the file deleted, both tasks succeed again.

### P4 — S4: the dump

1. `./gradlew :webtools-http:apiDump` — `BUILD SUCCESSFUL`; `git diff --stat webtools-http/api/webtools-http.api` is
   `1 file changed, 127 insertions(+)`, additions only. Read against plan §7.9 and the architecture §9 tier table: the
   public types of R1 and nothing else (`CidrBlock` with `Companion.parse-IoAF18A`, `MixedAnswers`, `OutboundUrlPolicy`
   with `getSettings` and its `Companion` (`trusted`, `untrusted`, `trusted$default`, `untrusted$default`), `Trusted`,
   `Untrusted` and their `Builder`s, `TransportSettings` with the ten getters (`Duration` ones mangled `-UwyO8pc`), its
   `Builder`, `Companion` (`getTrustedDefaults`, `getUntrustedDefaults`) and `TransportSettingsKt.TransportSettings`).
   `./gradlew :webtools-http:apiCheck` passes. `grep -c '$default'` prints `5`.
   Two lines differ from §7.9's inference and the dump is the as-built fact (plan §3): `CidrBlock` carries
   `public synthetic fun <init> ([BILkotlin/jvm/internal/DefaultConstructorMarker;)V`, the synthetic accessor of its
   private constructor, which the companion's `parse` calls (the same shape as the existing `HttpFailure$BudgetExhausted`
   line); and the sealed base `OutboundUrlPolicy` shows no constructor line at all (§7.9 expected a synthetic marker
   constructor). Neither is a public entry point a consumer can call from Kotlin.
2. The internal-name grep of P4 step 2 prints three lines, all `getExtraDeniedRanges`/`setExtraDeniedRanges`: the
   pattern `DeniedRange` is a substring of the public property name `extraDeniedRanges`. No internal name is in the dump;
   the grep's pattern is imprecise for that one name (a word-boundary pattern, `DeniedRange`, prints nothing).
3. Observation: `apiDump` wrote the file with LF line endings on this host, where the committed file was CRLF in the
   working copy; `git diff` shows only the 127 added lines (the repository stores it normalised) and `apiCheck` is
   unaffected. The generated file was not hand-edited.
4. `:webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL` with the nine `commonMain` files.
5. Commit 1 (plan §11) follows; the file list is reported there.

### P5 — S5: the JVM guards

1. `./gradlew :webtools-http:compileKotlinJvm` (also `--rerun-tasks`) — `BUILD SUCCESSFUL`, no compiler `w:`.
2. `./gradlew :webtools-http:componentTest` — `BUILD SUCCESSFUL`; the XML lists `GuardedDnsComponentTest` (14 tests),
   `GuardedSocketFactoryComponentTest` (3), `GuardedSocketComponentTest` (5), `PolicyRefusalMarkersComponentTest` (8),
   `PolicyEnforcementComponentTest` (3); 0 failures. **The inherited-`newCall` tests passed** (the first measurement of
   research gap 3): the guard's inherited `newCall` over a stub answering `[8.8.8.8, 10.0.0.5]` delivered only the
   `8.8.8.8` record under `DropDenied` and a failure whose `policyRefusal()` is non-null under `Reject`; a delegate whose
   own `newCall` counts calls saw one `lookup` and no `newCall`. Two mechanical fixes to the tests on the way (not to the
   sources): `Dns.Record.IpAddress.address` is a Kotlin property, not a method; the `Dns.Callback.onRecords` parameter is
   named `last`; two `is` checks the compiler proves false were turned into `Class.isInstance` to keep the test compile
   warning-free. The first `componentTest` run failed the planned `declaredMethods` assertion on `newCall` (Amendment 2):
   `javap` of the compiled `GuardedDns` shows `public newCall(Dns$Request)` whose body is `invokespecial okhttp3/Dns.newCall`.

**Plan inconsistency found during S5, resolved by plan Amendment 2 (not a deviation of the implementation).** Plan §9's
`GuardedDnsComponentTest` "structure" row asked for a reflection test that `GuardedDns::class.java.declaredMethods` has no
method named `newCall`. The compiled class does declare one: `GuardedDns` overrides nothing in source, but Kotlin 2.2.0
emits a bridge `public newCall(Dns$Request)` whose body is `invokespecial okhttp3/Dns.newCall` (the interface's JVM
default, called as `super`), because OkHttp 5.5.0 compiles `Dns` with `-Xjvm-default=all-compatibility`; the assertion
would fail before and after the P9 mutation alike. Amendment 2 replaces the row with the behavioural check that was built:
over a delegate `Dns` whose own `newCall` counts its calls, a resolution through the guard's inherited `newCall(...)` calls
the delegate's `lookup` once and its `newCall` never; the `$$delegate`-field half is kept; `GuardedDns.kt` is unchanged.
P9 mutation 1's expected failing set is the no-forwarding test plus the two inherited-`newCall` behaviour tests.

### P6 — Level 3 (integration) and the JDK-based Level 4a (deterministic) tests

1. `./gradlew :webtools-http:componentTest :webtools-http:integrationTest :webtools-http:deterministicTest` — `BUILD
   SUCCESSFUL in 9s`; the XML lists `GuardedSocketLoopbackIntegrationTest` (3 tests), `GuardedSocketSocksIntegrationTest`
   (1), `PolicyEnforcementOkHttpIntegrationTest` (6), `JdkPredicateGapDeterministicTest` (2),
   `AddressRangesJdkSupersetDeterministicTest` (2), `StrictIpJdkDifferentialDeterministicTest` (2),
   `OkHttpLiteralRegexDeterministicTest` (2); 0 failures. The three source-read claims, on JDK 23.0.2:
   - **(i) SOCKS control:** yes — with `socksProxyHost=127.0.0.1`, `socksProxyPort=<listener>` and `socksNonProxyHosts=""`,
     `SocketFactory.getDefault().createSocket().connect(target)` reached the fake SOCKS listener (count moved, connect failed
     with an `IOException` as the listener closed the handshake); the `GuardedSocket` under the loopback relaxation connected
     straight to the target (`accept()` returned) and the listener's count did not move; an `OkHttpClient` with
     `proxy(Proxy.NO_PROXY)` and the default socket factory reached the listener as the control did. The claim holds here.
   - **(ii) marker surfacing with fast fallback:** case (a), resolver guard under `Reject`, answer `[10.0.0.5, 10.0.0.6,
     fd00::1]`: the call threw a `PolicyRefusedUnknownHost` at the top, unwrapped, `suppressed` empty, the stub called once,
     `requestCount == 0`. Case (c), socket guard only, the same answer: the call threw an `IOException` whose
     `policyRefusal()` is non-null; the top exception plus its `suppressed` hold exactly three `PolicyRefusedIo` and no
     `ConnectException` anywhere in the tree; `requestCount == 0`. Cases (b), (d), (e), (f) as planned: 200 through the
     relaxation, one lookup per new connection (1 after two calls, 2 after `evictAll()`), the `Trusted` bundle filtering nothing.
   - **(iii) inherited `newCall` routing:** passed (P5 step 2).
2. Differential floors reached (seed 7, 100,000 strings per family): **45,756 IPv4** strings accepted (floor 25,000) and
   **46,857 IPv6** (floor 10,000); the JDK accepted 1,221 IPv6 strings `StrictIp` refused (it is looser; none threw anything
   but `UnknownHostException`). JDK predicate gap: the predicates flag 10 of the 26 F7 addresses and miss 16, as the research
   lists. Build JDK: `java version "23.0.2"`.

### P7 — the floor and the full check

1. `./gradlew :webtools-http:verifyJavaFloor` — `BUILD SUCCESSFUL in 1s`; `webtools-http/build/reports/java-floor/verifyJavaFloor.txt`
   reads `java floor 11 (class-file major <= 55): 174 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3575
   classes on the runtime classpath` (every class, the new ones included, at major 55 or below).
2. `./gradlew :webtools-http:check :webtools-http-commoncheck:check` — `BUILD SUCCESSFUL in 1m 12s`;
   `:build-logic:fixturesPublicationCheck` ran once (the bulk of the 1m 12s). `jvmTest` (every level together): 88 classes,
   500 tests, 0 failures. Per level after S5 against P1's baseline (classes/tests): gating 3/9 → 4/14; component 22/151 →
   37/297; deterministic 21/81 → 33/141; nonfunctional 7/15 → 10/22; integration 1/16 → 4/26.

### P8 — mechanical rules

Every command printed nothing except where the rule says otherwise: no `java.`/`javax.`/`@Jvm`/`runCatching`/`lowercase`/
`uppercase`/`ignoreCase`/`removeFirst`/`removeLast`/`String.format`/`Regex(` in `commonMain` outside comments; `catch` only in
`NonFatal.kt`; no `data class`; no `TODO` under `webtools-http/src`; no `OptIn` in `commonTest` or `jvmTest`; no `newCall`,
`by delegate`, `: Dns by`, `SocketFactory.getDefault`, `ConnectException`, `InetAddress.getByName` or `getAllByName` in main
code outside comments; `logger(` only in `Log.kt` (commonMain) and in `Log.jvm.kt` and `GuardedDns.kt` (jvmMain); no
`Thread.sleep` in the tests; the KDoc-coverage awk over `commonMain` and `jvmMain` printed nothing; package and path agree
for every new test file; every new test file outside `support/` has exactly one top-level class. The internal-name grep on
the dump was run word-bounded (`grep -w`, see P4 step 2 for the `extraDeniedRanges` false positive of the plan's pattern) and
printed nothing; `grep -c '$default'` prints `5`.

### P9 — mutation sensitivity

Each file was copied to a scratch directory outside the repository, mutated, the named task run, the file copied back and
`diff` against the copy printed nothing; then `componentTest`, `integrationTest` and `deterministicTest` ran green together.

| # | Mutation | Task | Failed (as expected) |
|---|---|---|---|
| 1 | `GuardedDns.kt`: `override fun newCall(request) = delegate.newCall(request)` | `componentTest --tests "*GuardedDnsComponentTest*"` | the no-forwarding test and the two inherited-`newCall` behaviour tests (Amendment 2's set), nothing else |
| 2 | `GuardedSocket.kt`: `Socket(Proxy.NO_PROXY)` → `Socket()` | `integrationTest --tests "*GuardedSocketSocksIntegrationTest*"` | the SOCKS test, with `java.net.SocketException: Malformed reply from SOCKS server`: the mutated socket went through the fake SOCKS listener (P6's control held) |
| 3 | `AddressRanges.kt`: the `168.63.129.16/32` row deleted | `deterministicTest` | `AddressRangesControlsDeterministicTest` (2 tests), `PublishedBypassVectorsDeterministicTest` (G1) and `AddressRangesRowsDeterministicTest` (its table holds the row too) |
| 4 | `AddressRanges.kt`: ISATAP mask `0xFCFF` → `0xFFFF` | `deterministicTest` | `AddressRangesControlsDeterministicTest` (the `100`/`300` forms) and `PublishedBypassVectorsDeterministicTest` (G2's `200:5efe` form) |
| 5 | `TransportSettings.kt`: `idleConnectionTtl` dropped from `equals` | `componentTest --tests "*TransportSettingsComponentTest*"` | `idleConnectionTtl takes part in equality and toString` |
| 6 | `UrlGuard.kt`: `return null` for a loopback address before `extraDeniedRanges` | `componentTest --tests "*GuardRelaxationComponentTest*"` | `an extra range still refuses loopback with its own class` |

### P10 — the unaffected modules and the diff

`git diff --stat -- webtools-udp webtools-scraping webtools-browser build-logic webtools-http/build.gradle.kts
webtools-http/src/jvmTestFixtures webtools-http-commoncheck` is empty. `git status --short` shows no `Canary.kt`, no scratch
copy and no `build/` directory. `README.md` is unchanged at this point (S7 changes it). No other module's suite was run.

### S7 — the README row and P11

`README.md` line 11: the purpose cell of the `webtools-http` row now reads as plan §7.10 writes it; the other three cells are
unchanged. `git diff --numstat README.md` is `1 1`; the file keeps its CRLF line endings (575 of 575 lines) and its UTF-8 em
dash. No other README line changed (no command, build step, module or dependency moved). The P11 pass is in §5.

### Review pass 1 fixes

Every finding was minor and documentary; no behaviour, public API, ABI or design changed. Applied in one batch:

- `jvmMain` KDoc: `PolicyEnforcement` and `UrlGuard.enforcement` gain the wiring paragraph (which setting is applied when,
  the client preconditions `retryOnConnectionFailure(false)` and fast fallback at its default, the order of the three guards,
  why a proxy other than `NO_PROXY` routes around both) and `@property`/`@return`; `GuardedDns` states the thread `lookup`
  runs on, its statelessness, the one `DEBUG` line, what happens after it throws and that a literal host never reaches it,
  with `@property` and `@throws` on `lookup`; both markers say they carry no stack trace and are found by `policyRefusal()`
  in the `suppressed` list too, with `@property refusal`; `GuardedSocket` gains a thread-safety line, `@property`,
  `@param`/`@throws` on `connect` and a comment on why `UNRESOLVED` rides `DeniedAddress` with the pseudo-class
  `unresolved`; `GuardedSocketFactory` gains `@property` and a `@throws SocketException` line on each refusing overload.
  The four `internal const val` messages carry half-line KDoc.
- `commonMain`: KDoc with tags replaces the `//` comments on `withSettings`, `canonicalPinnedHost` and
  `canonicalHostSuffix`; `UrlGuard` gains `@property policy`/`relaxation` ("test-only loosening; `None` in every production
  path"), `@param request`, and `@param addresses`/`@return` on `judgeAnswer`; `StrictIp.parseV4`/`parseV6` gain
  `@param text`; `//region` groups in `OutboundUrlPolicy.kt` (defaults and messages / Trusted / Untrusted / companion /
  canonicalisation and derivation); `matchesPrefix` in `CidrBlock.kt` is `private` (used in that file only; it was never in
  the dump). Line comments: why `localdomain` is reserved (`HostRules.kt`), the 0xE0/0x20 global-unicast bit test with
  RFC 4291 §2.4 (`AddressRanges.kt`), the MSB-first bit numbering and the top-`rest`-bits mask (`CidrBlock.kt`), the
  port-rule exemption for exactly `localhost`, `127.0.0.1` and `::1` (`UrlGuard.kt`).
- Tests: KDoc on `untrustedGuard`/`trustedGuard`, on `BypassVectors.Vector` (every property, `parent` as the F1/F10 tag)
  and `Outcome`; `//language=RegExp` on the copied OkHttp regex; what each forced first byte probes in
  `AddressRangesJdkSupersetDeterministicTest`; the stray scheme assertion dropped from `TrustedGuardComponentTest`'s
  `judgeAnswer` test (and its unused import); the case (d) bound tightened from 10 s to 2 s with a comment on what it
  catches (an instant refusal, never a connect); a comment that the shared-logger capture in `GuardedDnsComponentTest`
  relies on sequential JUnit.
- This record: "(§2 item 1)" → "(Amendment 2)", "§11" → "plan §11" twice, "is the caller's" → "follows".

Re-run: `./gradlew :webtools-http:apiCheck :webtools-http:componentTest :webtools-http:integrationTest
:webtools-http:deterministicTest` — `BUILD SUCCESSFUL in 19s`, the only compiler line `w: JS Environment Not Selected`;
`webtools-http/api/webtools-http.api` is byte-identical to `HEAD` (`cmp` after line-ending normalisation).

### S8 — the `WebToolsDsl` marker (plan Amendment 3)

Procedure P12 of [`plan.md`](plan.md) §9.2, each step as it ran.

1. *The canary before the marker.* `DslScopeCanary.kt` written exactly as plan §7.11 gives it;
   `./gradlew :webtools-http:compileTestKotlinJvm` — `BUILD SUCCESSFUL in 6s`: without the marker each of its five statements
   compiled, which is the hazard.
2. *The marker.* `WebToolsDsl.kt` as plan §7.11 writes it, and one `@WebToolsDsl` line directly above each of the six builder
   declarations (`HttpHeaders.kt:93`, `HttpRequest.kt:46`, `HttpResponse.kt:51`, `TransportSettings.kt:85`,
   `OutboundUrlPolicy.kt:55` and `:138`), nothing else in those files. `./gradlew :webtools-http:compileKotlinJvm
   :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL`. **The predicted compiler output did not hold:** beside
   `w: JS Environment Not Selected`, the `js` compile of the check module printed six warnings, one per annotated builder,
   `w: ... Reflection is not supported in JavaScript target; therefore, annotations cannot be read at runtime.` — the
   consequence of `@Retention(AnnotationRetention.RUNTIME)` (which the reflection test needs) on a target without runtime
   reflection. A warning, not an error; the JVM compile printed no `w:`.
   **Plan Amendment 5 (2026-10-26)** leaves the retention at its default: Kotlin 2.2.0's JS checker warns only when a
   `RUNTIME` retention is written out, and the JVM backend writes `RUNTIME` when none is given, so the reflection test keeps
   working and the dump does not change. Applied: the comment line and `@Retention(AnnotationRetention.RUNTIME)` replaced by
   the two comment lines the amendment gives; nothing else changed. Re-run of steps 2, 6 and 9 as the plan now states them:
   `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 2s`, no
   compiler `w:` at all (the `JS Environment Not Selected` line is printed at configuration and did not appear with the
   configuration cache reused; the six retention warnings are gone); `./gradlew :webtools-http:apiCheck
   :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest
   :webtools-http:integrationTest :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 9s`,
   `WebToolsDslComponentTest` 1 test, 0 failures (the implicit retention is `RUNTIME` on the JVM), the dump still
   `1 file changed, 3 insertions(+)`; `./gradlew :webtools-http:verifyJavaFloor` — `BUILD SUCCESSFUL`, 175 classes;
   `./gradlew :webtools-http:check :webtools-http-commoncheck:check` — `BUILD SUCCESSFUL in 57s`; counts unchanged:
   `gatingTest` 5/17, `componentTest` 38/298, `deterministicTest` 33/141, `nonfunctionalTest` 10/22, `integrationTest` 4/26;
   `jvmTest` 90 classes, 504 tests, 0 failures.
3. *The canary after the marker.* `./gradlew :webtools-http:compileTestKotlinJvm` — `BUILD FAILED in 4s` with exactly five
   `e:` lines, one per canary statement and none in any other file (so no existing statement needed a label):
   - `DslScopeCanary.kt:12:46 'var allowedPorts: Set<Int>?' cannot be called in this context with an implicit receiver. Use an explicit receiver if necessary.`
   - `DslScopeCanary.kt:13:44 'var allowCleartext: Boolean' cannot be called in this context with an implicit receiver. Use an explicit receiver if necessary.`
   - `DslScopeCanary.kt:14:46 'fun settings(configure: TransportSettings.Builder.() -> Unit): Unit' cannot be called in this context with an implicit receiver. Use an explicit receiver if necessary.`
   - `DslScopeCanary.kt:15:64 'var body: RequestBody?' cannot be called in this context with an implicit receiver. Use an explicit receiver if necessary.`
   - `DslScopeCanary.kt:16:54 'var redirectChain: List<RedirectHop>' cannot be called in this context with an implicit receiver. Use an explicit receiver if necessary.`
4. *The canary deleted.* `git status --short` shows no `DslScopeCanary.kt`; `./gradlew :webtools-http:compileTestKotlinJvm` —
   `BUILD SUCCESSFUL in 5s`.
5. *The two test classes and the dump.* `WebToolsDslConsumerGatingTest.kt` (`commonTest`, gating; three tests) and
   `WebToolsDslComponentTest.kt` (`jvmTest`, component; one test) written as the plan §9 rows say. `./gradlew :webtools-http:apiDump`:
   `git diff --stat` is `1 file changed, 3 insertions(+)`, the three lines being `public abstract interface annotation class
   com/spartanlabs/webtools/http/WebToolsDsl : java/lang/annotation/Annotation {`, `}` and a blank line, last in the file; no
   other line moved. `grep -c '$default'` prints `5`; the word-bounded internal-name grep prints nothing.
6. *The level tasks and the check module.* `./gradlew :webtools-http:apiCheck :webtools-http:gatingTest
   :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest
   :webtools-http:integrationTest :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 11s`; the XML lists
   `WebToolsDslConsumerGatingTest` (3 tests) under `gatingTest` and `WebToolsDslComponentTest` (1) under `componentTest`;
   0 failures.
7. *The mutation.* `TransportSettings.kt` copied to a scratch directory outside the repository, its `@WebToolsDsl` line deleted:
   `./gradlew :webtools-http:componentTest --tests "*WebToolsDslComponentTest*"` fails with
   `expected: <[]> but was: <[com.spartanlabs.webtools.http.TransportSettings$Builder]>`. The file copied back, `diff` against
   the copy prints nothing, the class passes again.
8. *The mechanical rules.* The KDoc-coverage awk over `commonMain` and `jvmMain`, the `OptIn` grep over the tests, the
   forbidden-API grep over `commonMain` and the `TODO` grep print nothing; the two new test files have their package equal
   to their path and one top-level class each; `WebToolsDsl.kt` and both test files are LF.
9. *The floor and the full check.* `./gradlew :webtools-http:verifyJavaFloor` — `BUILD SUCCESSFUL`; the report reads
   `java floor 11 (class-file major <= 55): 175 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3577 classes on
   the runtime classpath` (one class more than P7's 174: `WebToolsDsl`). `./gradlew :webtools-http:check
   :webtools-http-commoncheck:check` — `BUILD SUCCESSFUL in 59s`, `:build-logic:fixturesPublicationCheck` ran again. Counts
   against P7 (classes/tests): `gatingTest` 4/14 → 5/17, `componentTest` 37/297 → 38/298, `deterministicTest` 33/141,
   `nonfunctionalTest` 10/22 and `integrationTest` 4/26 unchanged; `jvmTest` 90 classes, 504 tests, 0 failures — as P12
   step 9 predicts.
10. *The unaffected modules.* `git diff --stat -- webtools-udp webtools-scraping webtools-browser build-logic
    webtools-http/build.gradle.kts webtools-http/src/jvmTestFixtures webtools-http-commoncheck` is empty.
11. Commit 3 (plan §11) follows; its file list is reported with this stage.

### Review pass 2 fixes

Correctness passed; the documentation and advisory test items were applied in one batch, with no behaviour, API or dump
change:

- `HttpHeaders.Builder.addSensitive` names the flag's readers (an `Untrusted` policy refuses the request; the transport
  strips the header on a cross-origin hop) instead of "a later unit". `GuardedSocket` gains the thread and after-the-throw
  paragraph that `GuardedDns` has. `withSettings`'s implementation sentence moved from its KDoc to a line comment on
  `val replacement`. The two "no stack" sentences of each marker merged into one.
- Tags on the test fixtures: `inet`, `StubDns` (`@property answer`, KDoc on `calls` and `hostnames`, tags on `of`),
  `url`/`addr`/`cidr` (`@param`, `@return`, `@throws IllegalStateException`), `outcome` (`@return`),
  `IpLiteralGenerator.ipv4`/`ipv6` (`@param seed`/`count`, `@return`, the same seed and count give the same list),
  `RegistryRows.Row` (`@property` per field) with the `ipv4` count explained (26 prefixes in the registry's 25 rows, one
  row listing two, plus the two non-registry rows: 28 entries), `BypassVectors.vectors`; `@property` on the `internal`
  constructor properties of `GuardRefusal`, `RedirectAdmission.Admitted`/`Refused`, `AnswerVerdict.Allowed`/`Refused`,
  `HostVerdict.Literal`/`Refused` and `DeniedRange`; tagged KDoc on `CidrBlock.isIpv4`/`contains`,
  `AddressRanges.isMapped`/`isNat64WellKnown`/`embeddedV4` and `HostRules.matchesSuffix`.
- Line comments: why the search bound is 100 (`PolicyRefusals.kt`), the 300 ms and 2 s accept timeouts
  (`GuardedSocketLoopbackIntegrationTest`), the forced mapped prefix (`AddressRangesJdkSupersetDeterministicTest`), the
  depths 50 and 150 straddling the bound (`PolicyRefusalMarkersComponentTest`), the over-long group and the ninth group of
  `IpLiteralGenerator`; the case (d) bound's comment rewritten for what it does and does not catch under fast fallback
  (`PolicyEnforcementOkHttpIntegrationTest`).
- Plan vocabulary in test comments replaced by plain words or a pointer to the code (`WebToolsDslConsumerGatingTest`,
  `GuardReasonOrderDeterministicTest`, `CidrBlockParseDeterministicTest`, `StrictIpUrlAgreementDeterministicTest`).
- Records: `impact.md`'s status names Amendments 1, 2 and 5 and the built marker; `plan.md`'s commit line names `fd93ff9`
  and its status reads "built through S8"; §5 item 3 of this record is in the built state (six declarations).
- **The F1 host-form count.** The parent research's F1 heading says 39 forms were measured; its published table lists 36
  distinct host forms, and `OkHttpLiteralRegexDeterministicTest` holds those 36. The plan §9 row, which said 39, now says
  36 with a one-line note; the catalogue's coverage is unaffected (every `BypassVectors` host also goes through the same
  check, and `PublishedBypassVectorsDeterministicTest` holds 115 rows tagged `F1`).
- Not applied: moving `GuardedDnsComponentTest`'s eight-thread check into a `nonfunctional` class is a placement choice the
  review left conditional ("if concurrency is to live uniformly at 4c"); it stays where plan §9 places it. The two inert
  sub-assertions of `PolicySecrecyNonFunctionalTest` stay as the review says ("nothing to add"). The `//region` structure of
  `GuardedDnsComponentTest` was optional and is not added.

Re-run: `./gradlew :webtools-http:apiCheck :webtools-http:componentTest :webtools-http:integrationTest
:webtools-http:deterministicTest :webtools-http:gatingTest` — `BUILD SUCCESSFUL in 15s`, the only compiler line
`w: JS Environment Not Selected`; `gatingTest` 5/17, `componentTest` 38/298, `deterministicTest` 33/141, `integrationTest`
4/26, 0 failures; the dump differs from `HEAD` only by the three `WebToolsDsl` lines of S8.

## 5. Level 5 (user acceptance) — the manual pass

Procedure P11 of [`plan.md`](plan.md) §9.2. Items 1 and 2 were performed from the command line. Items 3 and 4 need an
IntelliJ session and were **not performed**.

- **Owner of items 3 and 4:** the maintainer.
- **Trigger:** the next IntelliJ import of the repository, and at the latest before unit 6 (#65) starts. The result of each
  item is recorded under it here. The IDE items of units 3 and 4
  ([`../62-http-core/final-implementation.md`](../62-http-core/final-implementation.md) §6, items 1, 2 and 4;
  [`../63-test-support/final-implementation.md`](../63-test-support/final-implementation.md) §5, items 1, 2, 3 and 5) are
  still open and need the same session.

1. **The ABI dump against plan §7.9 and the architecture §9 tier table** — **done**.
   - *Performer:* the implementer, from the command line on the maintainer's host, 2026-10-25.
   - *Read:* `webtools-http/api/webtools-http.api` at commit `fd93ff9` (unchanged by commit `b46f58a`, as plan §11 expects:
     everything in `jvmMain` is `internal`) adds exactly the public types of R1: `CidrBlock` and `CidrBlock$Companion`
     (`parse-IoAF18A`), `MixedAnswers` (`Reject`, `DropDenied`, `getEntries`, `valueOf`, `values`), the abstract
     `OutboundUrlPolicy` with `getSettings` and its `Companion` (`trusted`, `untrusted`, `trusted$default`,
     `untrusted$default`), the final `OutboundUrlPolicy$Trusted` and `$Untrusted` with their getters,
     `equals`/`hashCode`/`toString`, and their `Builder`s with the getters and setters of the `var`s and
     `settings(Function1)`, the final `TransportSettings` with its ten getters (the `Duration` ones mangled `-UwyO8pc`),
     `TransportSettings$Builder`, `TransportSettings$Companion` (`getTrustedDefaults`, `getUntrustedDefaults`) and
     `TransportSettingsKt.TransportSettings`.
   - *Tiers:* every new type is Stable Core (final, or the sealed base, over `internal` or `private` constructors);
     `OutboundUrlPolicy` is `sealed` (`public abstract class` in the dump, no constructor line); nothing is gated by
     `ExperimentalForInheritanceWebToolsApi`, and no `@OptIn` exists in `commonTest` or `jvmTest`.
   - *Greps:* the word-bounded grep for every internal name of the unit prints nothing; `grep -c '$default'` prints `5`. The
     two lines §7.9 did not foresee are the synthetic accessor constructor of `CidrBlock` and the absent constructor line of
     the sealed base (§4, P4).
2. **The Level 3 (integration) measurements read** (§4, P6) — **done**.
   - *Performer:* the implementer, from the command line on the maintainer's host, 2026-10-25.
   - *Read:* the SOCKS control held on JDK 23.0.2, and the guarded socket bypassed the JVM-wide setting; a `Reject` refusal
     surfaces as `PolicyRefusedUnknownHost` at the top, unwrapped; three socket-guard refusals surface as three
     `PolicyRefusedIo` in the top exception and its `suppressed`, with no `ConnectException`; the inherited `newCall` routes
     through the guard's `lookup`.
3. **Quick Documentation of `OutboundUrlPolicy.untrusted`, `TransportSettings`, `Untrusted.extraDeniedRanges`,
   `CidrBlock.parse`, `MixedAnswers` and `WebToolsDsl` as a consumer sees them** — **not performed** (IDE).
   - *Requirement it checks:* R1 and R7: KDoc on every public declaration, naming its tier; the numbers of the two default
     sets; the `extraDeniedRanges` statement about mapped and NAT64 addresses and about a deployment's own NAT64 prefix.
   - *Why IDE-only:* Quick Documentation is the IDE's rendering of the KDoc.
   - *Meanwhile:* P8's coverage check finds a KDoc block on every public declaration. Each of the six carries its tier
     statement and its numbers, `extraDeniedRanges` carries the mapped, NAT64 and own-prefix statements, and `WebToolsDsl`
     names the six builders it marks (`OutboundUrlPolicy.kt`, `TransportSettings.kt`, `CidrBlock.kt`, `MixedAnswers.kt`,
     `WebToolsDsl.kt`). Review passes 1 and 2 read every KDoc block against the code, and their findings are fixed (§4).
4. **IntelliJ's import with the new files, and no spurious red in `commonMain` for the `js` check module** — **not
   performed** (IDE).
   - *Requirement it checks:* R5, a classification core in JVM-free common code, as a developer sees it in the IDE (plan
     acceptance item 1 holds on the command line).
   - *Why IDE-only:* IntelliJ builds its own project model from the Gradle import; only an IDE session shows its resolution
     and its highlighting.
   - *Meanwhile:* `:webtools-http-commoncheck:compileKotlinJs` compiles the nine new `commonMain` files (P4 step 4), and the
     `js` canary of P3 showed that a JVM API there is a compile error (P3 step 2).

## 6. Research that didn't hold

No load-bearing research claim was contradicted. The three source-read claims the plan left to measurement held on JDK
23.0.2 (P5, P6):
- a JVM-wide SOCKS setting reaches the default factory's socket and an `OkHttpClient` with `proxy(Proxy.NO_PROXY)`, and not a
  `Socket(Proxy.NO_PROXY)` subclass ([`research/jvm-enforcement.md`](research/jvm-enforcement.md) F4). The control held, and
  mutation 2 of P9 sent the mutated socket through the fake SOCKS listener;
- a resolver-guard refusal surfaces at the top of the call's exception, unwrapped, and with fast fallback every socket-guard
  refusal is in the top exception or its `suppressed`, with no `ConnectException` (F3, F8);
- OkHttp 5.5.0's inherited `Dns.newCall` routes through the guard's `lookup` (F1).

The other claims the implementation leaned on held as measured:
- the IANA rows and the two rules that are not registry rows: every row and control is a passing table test, and the 249
  published vectors get the outcomes the research gives them
  ([`research/iana-special-purpose-rows.md`](research/iana-special-purpose-rows.md),
  [`research/bypass-vectors.md`](research/bypass-vectors.md); P3);
- the JDK predicates flag 10 of the 26 F7 addresses and miss 16, as the parent research lists
  ([`../research/outbound-url-policy.md`](../research/outbound-url-policy.md) F7; P6);
- the JDK's literal parser is looser than `StrictIp`: it accepted 1,221 IPv6 strings `StrictIp` refuses, and threw nothing
  but `UnknownHostException` (jvm-enforcement F7; P6);
- the dump's mangled names (`parse-IoAF18A`, the `Duration` getters' `-UwyO8pc`) and the two `$default` bridges of the
  trailing `= {}` ([`research/config-api-shape.md`](research/config-api-shape.md); P4).

**One inference of the research did not hold.** `config-api-shape.md` §6 point 7 expected the parameterless sealed base's
dump entry to carry a synthetic `<init>(DefaultConstructorMarker)`, inferred from kotlinx.serialization's `Json`, whose
sealed base takes parameters (its load-bearing claim 4, which held for `Json`). As built, the dump has no constructor line
for `OutboundUrlPolicy` (P4).
- *What it shaped:* plan §7.9's expected dump, and nothing in the design.
- *What it cost:* one line of the P4 reading. No consumer can call the sealed base's constructor either way.

**Three statements of the plan (not research) did not hold**, and became plan amendments:
- plan §9's refusal rows of `CidrBlockParseDeterministicTest` disagreed with the `parse` of plan §7.2 (Amendment 1; S1).
  *Cost:* the two rows; no code change.
- plan §9's `declaredMethods` check for `newCall`: Kotlin 2.2.0 declares a bridge `newCall` in `GuardedDns`, because OkHttp
  compiles `Dns` with `-Xjvm-default=all-compatibility` (Amendment 2; S5). *Cost:* one failed first run, and the behavioural
  check that replaced it; no source change.
- plan §7.11's `@Retention(AnnotationRetention.RUNTIME)` on `WebToolsDsl`: Kotlin 2.2.0's JS checker warns at each use of
  an annotation whose RUNTIME retention is written out, six warnings in the check module's compile (Amendment 5; S8).
  *Cost:* three P12 steps run again; the retention itself, RUNTIME, did not change.

## 7. Known gaps

- **IDE behaviour is untested** (§5 items 3 and 4): Quick Documentation of the public declarations, and IntelliJ's import with
  the new files. The owner and the trigger are in §5 and [`impact.md`](impact.md) §8.
- **Nothing is enforced yet.** The policy reaches the network only through the OkHttp transport (unit 6, #65) and the call
  facades (unit 9, #68). The guards are exercised by this unit's Level 3 (integration) tests over a real `OkHttpClient`.
- **Real networks are not exercised:** real DNS answers, HTTP/2 connection coalescing over real TLS, a host with an ISATAP
  interface, and the reachability of `168.63.129.16`. Only stubs and loopback ran.
- **Not closable by a table:** a network's own NAT64 prefix and a service on the deployer's own public address.
  `extraDeniedRanges` is the seam, and its KDoc names both.
- **Only JDK 23.0.2 on Windows ran the build.** The SOCKS claim and the refusal surfacing are measured there. JDK 18 to 20, 22
  and 24 to 26 are read by inference from 11, 17, 21 and 27, and Linux and macOS did not run.
- **The IANA snapshot** (both registries "Last Updated 2025-10-09") rests on the registry pages' stamps, not on a row-by-row
  diff against an archive. The refresh procedure is documented above the table; no script is built.
- **The `GuardedDns` unusual-length case is unreachable from a test** (`InetAddress.getByAddress` accepts only 4 or 16
  bytes); `judgeAnswer` and `checkAddress` cover it.
- **The four refusing `SocketFactory` overloads have no precedent** in a published guard; OkHttp 5.5.0 never calls them.
- **A `Socket.setSocketImplFactory` or a replaced `ProxySelector`** set by other code in the same JVM is not defended
  against.
- **No permanent compile-fail check** exists for the subclass gate (#62's follow-up), nor, once S8 is built, for the effect
  of `WebToolsDsl`: the P12 canary runs once, and `WebToolsDslComponentTest` keeps the marker on the builders, not its
  effect.
- **Sequential test execution is assumed** by `GuardedSocketSocksIntegrationTest` (`@Isolated`; it sets process-wide SOCKS
  properties) and by the shared-logger capture of `GuardedDnsComponentTest`. A parallel test configuration, such as #58's
  CI might bring, must keep them isolated.

The build closed three of plan §14's gaps: the code blocks compile (P2, P3, P5); the three source-read claims are measured
(P5, P6); and the dump lines plan §7.9 inferred are read from the first `apiDump` (P4).
