# Research: the outbound-URL policies and the address classifier (`url-policy`)

[SpartanLaboratories/WebTools#64](https://github.com/SpartanLaboratories/WebTools/issues/64), unit 5
(`url-policy`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.3.1, with §6.2, §6.6, §6.9, §7.3, §9 and §11 unit 5)
and the systems-level impact [`../../impact.md`](../../impact.md) (§8). This folder researches the
implementation details that design leaves to the unit and that need evidence from outside this
repository.

## The work

`webtools-http` gains the policy that decides which URLs and which addresses a fetch may reach:

- **Public, in `commonMain`** (package `com.spartanlabs.webtools.http`, Stable Core):
  - `OutboundUrlPolicy`, a sealed pair built through `OutboundUrlPolicy.trusted { }` and
    `OutboundUrlPolicy.untrusted { }`.
    - `Trusted`: optional pinned hosts; `https` only unless cleartext is allowed; no address, name or
      port rule.
    - `Untrusted`: allowed ports (default 80, 443, 8080 and 8443; any port when unset), extra denied
      ranges, extra denied host suffixes, and how a mixed public and private resolver answer is
      treated (rejected whole by default). Nothing lowers its address protection. It refuses a
      request that carries a sensitive header.
  - `TransportSettings`, the caps and timeouts that belong to a policy, with one default set per
    policy:

    | Setting | `Trusted` | `Untrusted` |
    |---|---|---|
    | call timeout (all hops) | 30 s | 15 s |
    | connect, read and write timeouts | 10 s each | 5 s each |
    | response body cap (decoded bytes) | 5 MiB | 2 MiB |
    | redirects followed | 5 | 3 |
    | `https` to `http` redirect | refused | refused |
    | concurrent requests, per host | 16, 5 | 8, 2 |
    | idle-connection lifetime | 4 s | 4 s |

  - `CidrBlock` (`parse(text): Result<CidrBlock>`) and the enum `MixedAnswers` (`Reject`,
    `DropDenied`).
- **`internal`, in `commonMain`:** the classification core, pure functions over bytes and strings:
  - `AddressRanges`: the IANA special-purpose tables (an IPv4 deny list plus multicast; IPv6
    default-deny outside `2000::/3` with the special blocks inside it denied; IPv4-mapped and
    NAT64 well-known addresses judged by their embedded IPv4);
  - `StrictIp`: strict dotted-decimal and strict IPv6 text only;
  - `HostRules`: every lenient numeric form is `HostForm`; reserved names (single labels,
    `localhost`, `local`, `internal`, `home.arpa`, `localdomain` and their subdomains, plus the
    policy's suffixes) are `ReservedName`;
  - `UrlGuard`, which applies a policy to a hop and to an address;
  - `GuardRelaxation`, whose one relaxed value exempts loopback only and is reachable only from the
    test fixtures.
- **`internal`, in `jvmMain`:** `GuardedDns` (an `okhttp3.Dns` that judges every resolved address),
  `GuardedSocketFactory` (judges every connect attempt), and two marker exceptions carrying the
  refusal reason. The OkHttp transport, a later unit, wires them into its client; `Untrusted` uses
  no proxy.

What shapes the questions below:

- **What the policy is handed.** `Url` (built) is `http` or `https` only, never carries user-info,
  prints an IPv6 host in RFC 5952 form without brackets, prints an IPv4-mapped host as dotted IPv4,
  and keeps digits-and-dots and hex-looking hosts (`127.1`, `0x7f.1`, `2130706433`) and one trailing
  dot exactly as written, for the policy to judge. A redirect target is resolved by OkHttp's
  `HttpUrl` before it becomes a `Url`.
- **What a refusal is.** `TransportFailure.RefusedByPolicy(reason)` (built) with the open constants
  class `RefusalReason`: `Scheme`, `UserInfo`, `HostForm`, `ReservedName`, `DeniedAddress`, `Port`,
  `HostNotPinned`, `InsecureRedirect`, `SensitiveHeader`. A refusal never carries an address, a range
  or a query; a log line carries the reason name, the matched range class and the redacted URL.
- **Toolchain.** Kotlin 2.2.0 with `explicitApi()`; a single `jvm()` target compiled with
  `jvmTarget = 11` and `-Xjdk-release=11`, so only the Java 11 API is available, tests included; the
  same `commonMain` compiled for `js` by an unpublished check-only module, so a JVM API in common code
  is a compile error; `okhttp-jvm` 5.5.0 as an `implementation` dependency of `jvmMain`;
  binary-compatibility-validator 0.18.2 recording the JVM ABI. The level tests run on the build JDK,
  17 to 26.
- **Evolution rules.** Configurable types are built by public factory functions taking a builder
  lambda over `internal` constructors. Public constructors and Stable Core functions take no
  defaulted parameter except that trailing lambda, so a new setting is a new builder property and
  never a binary break. No public `data class`.

## Existing findings this research builds on

These are current: the same toolchain and OkHttp version, and nothing newer contradicts them. They
are cited, not researched again.

- **[`../../research/outbound-url-policy.md`](../../research/outbound-url-policy.md).** Why three
  checks (F1, F4); OkHttp's canonicalisation before anyone sees the host (F2); parser disagreement
  (F3); one lookup per new connection and connects only to the answer (F5); proxies (F6); the deny
  table's design and the JDK predicates' gaps (F7); the classifier's portability (F8); redirects (F9);
  the published bypasses up to 2024 (F10); mixed answers (F11); data parameters, not callbacks (F12);
  what a refusal discloses (F13); how refusals surface through OkHttp 5.5.0 (F14).
- **[`../../research/http-transport.md`](../../research/http-transport.md).** The settings' default
  numbers and the reasons for them; the transport's use of the policy.
- **[`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md).** The
  builder-lambda factory pattern and why defaulted parameters are avoided (F7); output reasons as
  constants classes and consumer-chosen inputs as enums (F5).
- **[`../62-http-core/research/url-parsing.md`](../62-http-core/research/url-parsing.md).** What `Url`
  canonicalises and what it leaves for the policy.

The codebase blast radius is small and is surveyed in the plan: the unit adds new files to
`webtools-http`, extends its ABI dump and the README's artifact row, and has no caller in the
repository yet.

## Questions

### 1. How do the JVM guards hook OkHttp 5.5.0 and the JDK so that every resolved address and every connect attempt is judged, and how are they tested without a network?

- **Options.**
  - `GuardedDns`: override `lookup` only, or every member `okhttp3.Dns` has in 5.5.0. Under
    `MixedAnswers.Reject` it throws on any denied address; under `DropDenied` it returns the allowed
    subset and throws when nothing is left.
  - `GuardedSocketFactory`: which `javax.net.SocketFactory` methods OkHttp 5.5.0 calls; whether the
    socket it returns extends the JDK's default `Socket()`, whose implementation may route a direct
    connection through a JVM-wide SOCKS setting, or `Socket(Proxy.NO_PROXY)`; which `connect`
    overloads it overrides; whether the other `createSocket(...)` overloads judge their target or
    refuse; what it does with an unresolved `InetSocketAddress`.
  - The marker exceptions: an `UnknownHostException` subclass from the resolver guard and an
    `IOException` subclass from the socket guard, with constant messages.
- **Requirements.**
  - Only the Java 11 API; `okhttp-jvm` 5.5.0; nothing OkHttp-typed leaves `jvmMain`.
  - Every connect attempt OkHttp 5.5.0 makes for an `Untrusted` fetch is judged, fast-fallback
    attempts included, and a refusal reaches the transport as the marker, at the top of the failure
    chain or in its suppressed list, carrying the reason and the matched range class but never an
    address in its message.
  - `Untrusted` never connects through a proxy of any kind; `Trusted` keeps OkHttp's default proxy
    selection, `Dns.SYSTEM` and the default socket factory.
  - Level 2 tests touch no network. A test oracle for the strict literal parser must parse a literal
    with no DNS lookup on every build JDK from 17 to 26, compiled against the Java 11 API.
- **Evidence that settles it.** OkHttp 5.5.0's sources at the tag `parent-5.5.0` (`Dns`, the route
  selector and planner, the connect plan, the fast-fallback exchange finder, `OkHttpClient`); the JDK
  sources of `java.net.Socket`, `SocksSocketImpl`, `InetAddress` and the default proxy selector for
  Java 11, 17 and 21, and the JDK release notes on IPv4 literal parsing; throwaway probes on the JDKs
  available.
- **Findings:** [`jvm-enforcement.md`](jvm-enforcement.md).

### 2. Which rows do the IANA IPv4 and IPv6 Special-Purpose Address Registries hold today, and how does each map onto the deny table?

- **Options per row.** Deny it; judge the IPv4 address embedded in it (IPv4-mapped `::ffff:0:0/96`,
  NAT64 well-known `64:ff9b::/96`); deny it wholesale although it embeds an IPv4 address (6to4
  `2002::/16`, Teredo `2001::/32`); or leave it to the IPv6 default-deny outside `2000::/3`.
- **Requirements.**
  - IPv4 is a deny list holding every registry row plus multicast `224.0.0.0/4`. IPv6 is
    default-deny outside `2000::/3`, and every registry row inside `2000::/3` is denied explicitly.
  - A Level 4a test feeds every row's first and last address and expects a denial; public control
    addresses on both sides of every IPv4 boundary, and inside `2000::/3` next to every denied block,
    stay allowed.
  - The table in the source carries a dated snapshot note and how to refresh it.
- **Evidence that settles it.** The two registries in their XHTML and CSV forms with their
  last-updated dates, and any change since 2025-10-09; the IPv4 multicast registry or RFC 5771 for
  `224.0.0.0/4`; the IPv6 address-space registry for `2000::/3`.
- **Findings:** [`iana-special-purpose-rows.md`](iana-special-purpose-rows.md).

### 3. Which published bypass vectors must the Level 4a tables carry, and does each fall to one of the classifier's rules?

- **Options.** The rules as designed (strict literals only; the WHATWG "ends in a number" rule for a
  name's last label; the reserved names; the address table with embedded IPv4 judged), or an
  addition where a published vector passes them.
- **Requirements.**
  - Each vector comes with its published source (an advisory identifier or a catalogue at a pinned
    revision), the host text as it reaches the policy, and the expected outcome: refused by
    `Url.parse`, `HostForm`, `ReservedName`, `DeniedAddress`, or a name the resolver guard judges.
  - Coverage: the advisories since 2020 about IP-literal parsing and special-range classification
    (node `ip`, `private-ip`, `netmask`, Python `ipaddress`, Go `net/netip`, and any newer one), the
    published catalogues of SSRF host forms, and URL-parser confusion.
  - Vectors that belong to the transport (redirect handling, header stripping) are listed apart, for
    the transport's own tests.
- **Evidence that settles it.** GitHub Advisory Database and NVD entries; the catalogues' pages at a
  pinned revision; the WHATWG URL standard's IPv4 parser.
- **Findings:** [`bypass-vectors.md`](bypass-vectors.md).

### 4. What public shape should the policy and transport-settings configuration take in idiomatic Kotlin?

- **Options.**
  - The two default settings sets: companion factories that start from a set
    (`TransportSettings.forTrusted { }`, `forUntrusted { }`); named values plus a factory that starts
    from any value (`TransportSettings(from = …) { }`, the shape of `kotlinx.serialization`'s
    `Json(from) { }`); or both.
  - Deriving a modified copy of a settings value, which the call facades need when they raise the
    transport's concurrency: a `from` factory, a member taking a builder lambda, or a `newBuilder()`.
  - A policy's settings inside its builder: an assignable `settings` property, a nested
    `settings { }` block starting from the policy's current settings, or both; and whether nested
    builders carry a `@DslMarker`, given that the later units' configuration builders nest too.
  - Collection-valued properties (`allowedPorts`, `pinnedHosts`, `extraDeniedRanges`,
    `extraDeniedHostSuffixes`): `Set` or `List`, and how they are copied.
  - Equality and `toString()` of configuration values.
- **Requirements.**
  - No defaulted parameter on a public constructor or Stable Core function except a trailing builder
    lambda `= {}`; a new setting can be added in a feature release without a binary break for Kotlin
    callers; no public `data class`; `explicitApi()`; the ABI dump records the shape.
  - `OutboundUrlPolicy` is a sealed pair built through `OutboundUrlPolicy.trusted { }` and
    `untrusted { }`, and each policy carries its default settings set.
  - A developer-written configuration mistake throws `IllegalArgumentException` when the value is
    built; text from outside the process (`CidrBlock.parse`) returns a `Result`.
  - `toString()` of a configuration value prints nothing an operator would not want in a log line.
- **Evidence that settles it.** `kotlinx.serialization`, Ktor, OkHttp, Coil and Apollo at pinned
  versions; Kotlin's API guidelines for library authors and its type-safe builder documentation; a
  throwaway compile of the candidate shapes on Kotlin 2.2.0 with their ABI dumps.
- **Findings:** [`config-api-shape.md`](config-api-shape.md).
