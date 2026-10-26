# Issue #64 — `url-policy` (impact)

- **Covers:** `SpartanLaboratories/WebTools#64`, *"webtools-http: outbound-URL policies `Trusted`/`Untrusted`,
  `TransportSettings` and the common address classifier"*. It is unit 5, `url-policy`, of
  [#55's architecture](../architecture.md) (§6.3.1, §9, §11).
- **What this covers:** what the repository, its published artifacts and the sibling units gain or lose with
  this unit, as built. The file-level detail is in [`plan.md`](plan.md); the as-built record, with every
  measurement, is [`final-implementation.md`](final-implementation.md).
- **Status:** built as planned, with plan Amendments 1, 2 and 5 (the test-check corrections, which changed no
  source, and the marker's implicit retention); the `WebToolsDsl` marker of plan Amendment 3 is built (stage S8), and
  this file states its effect. [`../architecture.md`](../architecture.md) carries three dated corrections for this
  unit (plan Amendment 4).
- **Baseline:** `master` `ad0ae63`; branch `feat/issue-64-url-policy`, with the implementation commits `fd93ff9`
  and `b46f58a`; PR TBD (it closes #64).
- **Related docs:**
  - [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md);
  - [`research/README.md`](research/README.md), with [`research/jvm-enforcement.md`](research/jvm-enforcement.md),
    [`research/iana-special-purpose-rows.md`](research/iana-special-purpose-rows.md),
    [`research/bypass-vectors.md`](research/bypass-vectors.md) and
    [`research/config-api-shape.md`](research/config-api-shape.md);
  - the systems-level [`../impact.md`](../impact.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the outbound-URL policies and their settings: `OutboundUrlPolicy` with `Trusted`, `Untrusted`, their builders and the four factories (`trusted { }`, `trusted(from) { }`, `untrusted { }`, `untrusted(from) { }`); `TransportSettings` with its builder, `TrustedDefaults`, `UntrustedDefaults` and the factory `TransportSettings(from) { }`; `CidrBlock`; `MixedAnswers`; and `WebToolsDsl`, the DSL marker on every builder of the module. The ABI dump `api/webtools-http.api` grows by exactly these: 127 lines for the policy types, as measured, and three for the marker. No dependency changes, and the test-fixtures variant does not change. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **Two policies as configuration values, Stable Core.**
  - `OutboundUrlPolicy.untrusted { }` describes a fetch of a URL a third party supplied: `http` and `https`;
    ports 80, 443, 8080 and 8443 by default (any port when `allowedPorts` is `null`); no address in the IANA
    special-purpose space, multicast, the Azure platform endpoint or an ISATAP interface identifier; no reserved,
    single-label or lenient numeric host; no request carrying a sensitive header; a mixed resolver answer
    refused whole by default; and tighter caps and timeouts. Every parameter can only add a denial.
  - `OutboundUrlPolicy.trusted { }` describes a known upstream: `https` only unless cleartext is allowed,
    optional host pinning, the downgrade rule, and no address, name or port rule.
- **One DSL for the module's builders, Stable Core.** `@WebToolsDsl` marks every builder a consumer's lambda
  receives: `TransportSettings.Builder`, both policy builders, and the existing `HttpHeaders.Builder`,
  `HttpRequest.Builder` and `HttpResponse.Builder`. Inside a nested block only the innermost builder is an implicit
  receiver, and an outer one is reached through its label (`this@untrusted.allowedPorts`). On the three existing
  builders this tightens a consumer's source: a lambda that reached an outer builder implicitly stops compiling.
  `webtools-http` is unreleased, so no published consumer has such a lambda.
- **Nothing is enforced by this unit alone.** The policy reaches the network through the OkHttp transport (unit 6)
  and the call facades (unit 9). Until they land, a consumer can build, derive and compare policies and nothing
  else.
- **No new runtime dependency.** The JVM guards are `internal` and use the `okhttp-jvm` 5.5.0 the module already
  depends on.

**Release.** No published module's version moves, so this landing makes no release candidate, and
`webtools-http` 1.0.0 stays unreleased until #55's stack is complete.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`:**
  - `commonMain`: the public `TransportSettings`, `MixedAnswers`, `CidrBlock`, `OutboundUrlPolicy` and
    `WebToolsDsl`, and the `internal` classifier `StrictIp`, `HostRules`, `AddressRanges`, `GuardRelaxation` and
    `UrlGuard`;
  - `jvmMain`: the refusal markers with `policyRefusal()`, `GuardedDns`, `GuardedSocketFactory`, `GuardedSocket`
    and the per-policy bundle `PolicyEnforcement`;
  - their tests in `commonTest` and `jvmTest`, in the level packages of plan §9, with five test-support files.
- **Three existing files gain one line each:** `@WebToolsDsl` above the builders of `HttpHeaders.kt`,
  `HttpRequest.kt` and `HttpResponse.kt`. No other existing source file changes: `UrlHost.kt` keeps its own IPv6
  and dotted-quad parsers; the policy calls its `canonicalHost` for pinned hosts and suffixes and adds `StrictIp`
  beside it, and a Level 4a (deterministic) test pins the two to the same IPv6 literal forms.
- **No caller exists yet.** No class in the repository classifies an address or applies an outbound-URL policy
  today; `General.kt`'s `resolveLocalAddress()` is a UDP probe, not HTTP (architecture §8).

### Build

- **No build file changes.**
- **`./gradlew build` does more work:**
  - the new tests in `webtools-http`'s `jvmTest`: 34 classes and 228 tests for the policy, and the marker's two
    classes; among them a loopback-only Level 3 (integration) class that sets and restores the JVM-wide SOCKS
    properties. With the earlier units' tests, `jvmTest` runs 500 tests in 88 classes before the marker's;
  - the `js` compile of the new common files in `webtools-http-commoncheck`, which keeps them JVM-free;
  - `apiCheck` against the regenerated dump;
  - the fixtures-publication check once more, because its inputs include `commonMain` and `jvmMain`.
    `:webtools-http:check :webtools-http-commoncheck:check` took 1 min 12 s with it.

### Documentation

- **`README.md`:** the purpose cell of the `webtools-http` row of the artifacts table also names the
  `Trusted`/`Untrusted` outbound-URL policies with their transport settings. One line changes, and the file keeps
  its CRLF line endings.
- **KDoc** on every public declaration states its tier, its default numbers and its contract; the address table
  carries a dated snapshot comment and its refresh procedure. The `internal` JVM seams carry the contracts unit 6
  builds on: how the bundle is wired, which thread `lookup` runs on, the one `DEBUG` line, and what a call does after
  a guard throws.
- **[`../architecture.md`](../architecture.md)** carries three dated corrections (2026-10-26), at §6.3.1 and §7.4
  (§5).
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is unit 11's.

## 3. Adoption

- **No existing class uses the mechanism.** Address classification and an outbound-URL policy are new to the
  repository; no `InetAddress` predicate is used for address classification anywhere in the main sources.
- **The existing builders adopt the marker.** `HttpHeaders.Builder`, `HttpRequest.Builder` and
  `HttpResponse.Builder` carry `@WebToolsDsl`, so `headers = HttpHeaders { }` inside a request or response block is
  scope-checked. No source or test of the repository reached an outer builder implicitly.
- **`Connector`** (`webtools-scraping`) keeps the verdicts of architecture §8: its follow-ups move `get`,
  `skrape` and the fetch half of `download` onto the stack later, where the policy then applies.
- **`WebViewer`** (`webtools-browser`) is not an HTTP client. The follow-up `webviewer-url-precheck` would need the
  classifier's host rules, which stay `internal` in 1.0.0 (§7).

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts. In short:

- **Unit 6, `okhttp-transport` (#65).**
  - It takes the policy and its settings; each setting's meaning and the OkHttp 5.5.0 limit it respects are in
    plan §12 (every timeout from 1 ms to `Int.MAX_VALUE` ms, the body cap at least 1, concurrency at least 1, the
    idle-connection TTL above 0).
  - It calls `UrlGuard.checkRequest(request)` before hop 1 and `UrlGuard.admitRedirect(target, previous)` for each
    redirect target, both before any `Call`; it resolves `Location` itself with `HttpUrl.resolve` and catches what
    that throws.
  - It applies `UrlGuard.enforcement(baseDns)` to its `OkHttpClient.Builder` (`dns`; `socketFactory` and `proxy`
    when set) and keeps fast fallback at its default and `retryOnConnectionFailure(false)`. The KDoc of
    `PolicyEnforcement` states this wiring and the order of the three guards.
  - Its failure mapping checks `policyRefusal()` first, turns a refusal into `RefusedByPolicy(reason)`, and logs
    the one `WARN` with `Url.redacted()`, the reason name and the range class. As measured through a real
    `OkHttpClient`: a resolver-guard refusal under `Reject` is the call's exception, unwrapped, with nothing in
    `suppressed`; socket-guard refusals arrive as `PolicyRefusedIo` in the top exception and its `suppressed`, never
    as a `ConnectException`; and every new connection resolves through the guard again.
  - Its `LoopbackTransports.untrusted` builds `UrlGuard(policy, GuardRelaxation.LoopbackForTests)`;
    `LoopbackTransports.trusted` uses `OutboundUrlPolicy.trusted { allowCleartext = true }`.
  - The transport vectors of [`research/bypass-vectors.md`](research/bypass-vectors.md) §5 are its tests.
- **Unit 9, `call-facades` (#68).** `UrlFetcher`'s pre-check is `UrlGuard.checkRequest`: a refusal costs nothing,
  and the facade, which creates the `RefusedByPolicy` failure, logs it once. `withSettings` raises the transport's
  concurrency to the budget's burst. The client's default policy is
  `OutboundUrlPolicy.trusted { pinnedHosts = setOf(baseUrl.host) }`.
- **Unit 10, `feeds` (#69).** Its owning factory builds an `Untrusted` fetcher with `OutboundUrlPolicy.untrusted { }`.
- **Unit 11, `http-docs` (#70).** It documents the policies and the builder DSL from this unit's KDoc, and the README
  row.
- **Units 7 and 8 (#66, #67):** nothing from the policy.
- **Every later unit's builders.** A builder that a later unit adds to `webtools-http` and passes to a consumer's
  lambda carries `@WebToolsDsl`, and joins the list of `WebToolsDslComponentTest` and the list in `WebToolsDsl`'s
  KDoc (plan §12).
- **Unit 4, `test-support` (#63):** its fixtures do not change. They reach `GuardRelaxation` through the
  compilation association, which unit 6's `LoopbackTransports` uses.

## 5. Where the design refines the parent documents

[`../architecture.md`](../architecture.md) carries three dated corrections (2026-10-26) for this unit, at §6.3.1 for
the first two points below and at §7.4 for the third. The other points make its §6.3.1 concrete without contradicting
its text.

- **The address table holds two rules that are not registry rows** (corrected at §6.3.1): `168.63.129.16/32`, the
  Azure platform endpoint (a public address in no registry row, listed as an SSRF target by the published
  catalogues), and an ISATAP interface identifier inside `2000::/3`, denied wholesale as 6to4 and Teredo are. Both
  close published bypasses of the registry-only table ([`research/bypass-vectors.md`](research/bypass-vectors.md)
  §4). Nested registry rows are listed most specific first, so each logs its own class.
- **The socket guard creates its own sockets, and the resolver guard inherits `newCall`** (corrected at §6.3.1).
  Each socket extends `Socket(Proxy.NO_PROXY)`: `proxy(Proxy.NO_PROXY)` on the client leaves a JVM-wide SOCKS setting
  in force at the socket layer, which the default factory's `Socket()` honours
  ([`research/jvm-enforcement.md`](research/jvm-enforcement.md) F4). OkHttp 5.5.0's `Dns` gained a record-based
  `newCall`; the inherited default routes it through the guard's `lookup`, and a forwarded one would let a
  record-capable delegate bypass the guard (jvm-enforcement F1).
- **A refusal carries the range class beside the reason** (corrected at §7.4). The transport receives an `internal`
  refusal holding the reason and, for `DeniedAddress`, the matched class, so that the one log line of §6.6 can carry
  the class.
- **`UrlGuard` has more entry points than `checkHop` and `checkAddress`:** `checkRequest` (hop 1 plus the header
  rule), `admitRedirect` (a resolved redirect target, parsed and judged) and `judgeAnswer` (a whole resolver answer
  under `MixedAnswers`); the JVM side adds `enforcement(baseDns)` and `policyRefusal()`.
- **The sensitive-header rule reads `HttpHeaders.Entry.isSensitive`,** which covers `Set-Cookie` as well as the three
  names §6.3.1 lists, and every flagged entry.
- **The configuration API:** `TransportSettings.TrustedDefaults` and `UntrustedDefaults` with the factory
  `TransportSettings(from) { }`, the policies' `from` factories beside `trusted { }` and `untrusted { }`, an
  `internal` `withSettings` for the facades, and `WebToolsDsl` on every builder of the module (plan Amendment 3), a
  public type that changes no system boundary.
- **`HostRules` is total on any text,** with ASCII-only folding, an explicit character set, and `0x` with zero hex digits
  counted as a number.
- **The parent research's IPv6 deny list** names `::/96`, `::ffff:0:0:0/96`, `fec0::/10` and `ff00::/8` as
  special-purpose rows. They are not rows of that registry; all four lie outside `2000::/3`, so the outcome is the
  same ([`research/iana-special-purpose-rows.md`](research/iana-special-purpose-rows.md) F3).

**What the measurements show** (JDK 23.0.2; [`final-implementation.md`](final-implementation.md) §4, P5 and P6):
- with `socksProxyHost` set, the default factory's socket and an `OkHttpClient` with `proxy(Proxy.NO_PROXY)` both reached
  a fake SOCKS listener, and `GuardedSocket` connected directly;
- the JDK's `InetAddress` predicates flag 10 of the 26 special-purpose addresses of the parent research's F7 and miss
  16, so the guard uses none of them;
- in 100,000 generated IPv6 strings, the JDK's literal parser accepted 1,221 that `StrictIp` refuses, so the guard
  reads literals with `StrictIp` alone;
- the inherited `newCall` resolves through the guard's `lookup`, and a refusal surfaces as described in §4.

## 6. Neighbouring issues

- **#58** (CI and repository practices). Its CI would be the first place this unit's tests run on every change.
  Two of them assume sequential test execution: the SOCKS test changes process-wide properties and restores them
  (`@Isolated`), and `GuardedDnsComponentTest` captures a shared logger. CI must not run them in parallel.
- **The IDE checks of units 3, 4 and 5** are the maintainer's, in one IntelliJ session: at the next import of the
  repository, and at the latest before unit 6 (#65) starts. This unit's two items are in
  [`final-implementation.md`](final-implementation.md) §5; units 3 and 4 list theirs in
  [`../62-http-core/impact.md`](../62-http-core/impact.md) §7 and
  [`../63-test-support/impact.md`](../63-test-support/impact.md) §7. This unit adds no source set and no build
  configuration for the IDE to import.

## 7. Follow-ups this unit leaves

- **One IP-literal grammar for the module.** `UrlHost.kt`'s private IPv6 and dotted-quad parsers and `StrictIp`
  implement the same grammar, and a test pins their agreement; moving `UrlHost.kt` onto `StrictIp` would leave
  one implementation.
- **A public pre-check of a URL** against a policy (for a consumer's subscription-time check, or the follow-up
  `webviewer-url-precheck`). The classifier stays `internal` in 1.0.0; a check is additive.
- **A network-specific NAT64 prefix** judged by its embedded IPv4 address. Today a deployment behind its own NAT64
  prefix can only deny the whole prefix through `extraDeniedRanges`; a parameter for it is additive.
- **Refreshing the IANA snapshot.** The procedure is in the comment above the table; a script is not built.
- **A consumer-implemented, deny-only guard seam** (architecture §10), and public accessors or `contains` on
  `CidrBlock`: additive later.
- **A compile-fail harness.** #62's follow-up for the subclass gate; the same harness would hold the effect of
  `WebToolsDsl`, which a throwaway canary shows once.
- **A JDK-version matrix** for the SOCKS measurement (JDK 24 to 26), once CI exists.

## 8. Known gaps

- **Measured on one JDK and one OS:** the SOCKS behaviour, the refusal surfacing and the inherited `newCall` routing
  hold on JDK 23.0.2 on Windows. JDK 18 to 20, 22 and 24 to 26 are read by inference from 11, 17, 21 and 27; Linux and
  macOS did not run the build.
- **Real networks are not exercised:** real DNS answers, HTTP/2 connection coalescing over TLS, a host with an ISATAP
  interface, and the reachability of `168.63.129.16` from a given host.
- **Not closable by a table:** a network's own NAT64 prefix and a service on the deployer's own public address;
  `extraDeniedRanges` is the seam, and its KDoc names both.
- **IDE behaviour is untested:** Quick Documentation of the public declarations and IntelliJ's import with the new
  files. Owner: the maintainer; trigger: the next IntelliJ import, at the latest before unit 6 (#65) starts (§6).
- **The marker's effect has no permanent check.** That an implicit reach into an outer builder does not compile is
  shown once by a throwaway canary; `WebToolsDslComponentTest` keeps the marker on the builders, not its effect.
