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

## Verification

### Outside-world claims

Every load-bearing claim in the four findings files was re-opened at its source on 2026-10-25: code at its
pinned tag or commit, advisories in the GitHub Advisory Database, the IANA registries and RFCs, and the
Kotlin documentation.

**81 claims: 80 hold, none fails, none is unclear, and 1 cites this repository.**

| Findings file | Claims | Result |
|---|---|---|
| [`jvm-enforcement.md`](jvm-enforcement.md) | 28 | all hold |
| [`iana-special-purpose-rows.md`](iana-special-purpose-rows.md) | 10 | all hold |
| [`bypass-vectors.md`](bypass-vectors.md) | 26 | all hold; the second sentence of claim 10 cites this repository |
| [`config-api-shape.md`](config-api-shape.md) | 17 | 16 hold; 17 cites this repository |

**Notes on claims that hold:**

- **Both IANA registries in full.** Every row of the IPv4 and IPv6 Special-Purpose Address Registries (25
  and 25) matches the tables of `iana-special-purpose-rows.md` F1 and F2: block, name, RFC, allocation date,
  the five flags and the footnote markers. Both registries still read "Last Updated 2025-10-09".
- **Two quotations are corrected in `jvm-enforcement.md`; neither changes a finding.**
  - Claim 22: the name-service call that ends `InetAddress.getAllByName` in JDK 17.0.21 is
    `getAllByName0(host, reqAddr, true, true)`.
  - Claim 23: the quoted passages of `IPAddressUtil.java` are in all four JDK lines, but the files are not
    identical; JDK 27 changes `validateNumericFormatV4` to `(String src, boolean throwIAE)`.
- **Bypass-vectors claim 11.** The Black Hat PDF of Orange Tsai's talk holds every quoted string. Its
  OWASP-hosted copy, cited as identical, was not extracted.
- **Nothing newer changes an answer.**
  - OkHttp 5.5.1 (2026-10-22) changes `ConnectPlan.kt` only by a certificate blocklist check, and its
    `HttpUrl.kt` is byte-identical to 5.5.0's.
  - kotlinx.serialization 1.12.0, Ktor 3.6.0, Apollo Kotlin 5.2.0, Coil 3.6.3 and kotlinx.html 0.12.0 are
    their latest releases.
  - Every advisory read is published, and none is withdrawn.

### Codebase facts

The claims about this repository were checked against `ad0ae63`:

- **What `Url.parse` refuses before the policy sees a host,** as `bypass-vectors.md` §3.1 maps it
  (`UrlParsing.kt`, `UrlHost.kt`). For example, `http://127.0.0.1:11211:80/` fails at the port (its port
  text is `11211:80`), `http://foo@evil.com:80@google.com/` at user-info,
  `http://1.1.1.1 &@2.2.2.2# @3.3.3.3/` at the whitespace scan, and `[::ffff:0177.0.0.1]` at the host (a
  dotted quad inside IPv6 takes no leading zero). A digits-and-dots or hex-looking host such as `0x7f.1` is
  kept as written, for the policy to judge.
- **The ABI dump** `webtools-http/api/webtools-http.api` holds the one kind of default the module allows, a
  trailing builder lambda (`HttpHeaders$default`), and the companion parser `Url$Companion.parse-IoAF18A`:
  the shapes `config-api-shape.md` builds on.
- **The test fixtures' fake** already takes a `maxResponseBytes` cap (`FakeHttpTransport.kt`), the name the
  transport settings use for the same limit.
- **OkHttp 5.5.0's own limits** on the values the settings hand it, read at `parent-5.5.0`:
  - `checkDuration` in `okhttp3/internal/-UtilJvm.kt` rejects a timeout whose whole milliseconds exceed
    `Integer.MAX_VALUE` ("too large") and a positive timeout under 1 ms ("too small");
  - `Dispatcher` requires `maxRequests >= 1` and `maxRequestsPerHost >= 1`;
  - `RealConnectionPool` requires a `keepAliveDuration` above 0.

## Summary

### The JVM guards

- **`GuardedDns` overrides `lookup` only and inherits `newCall`.** OkHttp 5.5.0's `Dns` gained a
  record-based `newCall` for HTTPS and SVCB records. `RouteSelector` calls `lookup` only when `newCall`
  returns the default `LookupDnsCall`; any other call is read as records, and `lookup` never runs. The
  inherited default routes every resolution through the guard's `lookup`. A guard that forwarded `newCall`,
  or that was written `: Dns by delegate`, would be bypassed by a record-capable delegate
  ([`jvm-enforcement.md`](jvm-enforcement.md) F1).
  - Under `MixedAnswers.Reject` an answer with any denied address is refused whole. Under `DropDenied` the
    allowed addresses are returned in order, and an answer with none left is refused.
  - It never returns an empty list: OkHttp reports one as `"<Dns> returned no addresses for <host>"`, a
    message holding the host. An empty answer from the resolver it wraps becomes an `UnknownHostException`
    with a constant message.
  - A refusal is an `UnknownHostException` subclass. Any other throwable from `lookup` reaches the caller as
    `IOException("canceled due to …")`, with the refusal hidden one level down.
- **`GuardedSocketFactory` creates its own sockets, each a `Socket(Proxy.NO_PROXY)` subclass.**
  - OkHttp 5.5.0 calls only the parameterless `createSocket()`, then `Socket.connect(SocketAddress, Int)`
    through `Platform.connectSocket` (F2). The four abstract overloads are never called, so they refuse.
  - `proxy(Proxy.NO_PROXY)` on the client closes OkHttp's own proxy selection and nothing more. The default
    factory's `Socket()` installs a `SocksSocketImpl`, which consults `ProxySelector.getDefault()` and
    `socksProxyHost` on every connect; `Socket(Proxy.NO_PROXY)` installs the platform implementation
    directly (F4). Wrapping the default factory's sockets would leave that path open.
  - The socket's `connect` refuses an unresolved or non-`InetSocketAddress` target (a `SocksSocketImpl`
    would hand a host name to a SOCKS proxy) and a denied address, before any I/O. `connect(SocketAddress)`
    routes through it on every JDK line read (F5).
  - A refusal is an `IOException` subclass and never a `ConnectException`, which OkHttp re-throws as
    `ConnectException("Failed to connect to <address>")` (F2).
- **How a refusal surfaces.** Fast fallback is on by default and starts another of the answer's addresses
  every 250 ms. Every attempt creates and connects its own socket, so each is judged, and
  `retryOnConnectionFailure(false)` does not stop the next address being tried. The first failure is thrown
  with the later ones in its `suppressed` list; a resolver refusal comes out unwrapped; an expired call
  timeout can carry either as the cause of `InterruptedIOException("timeout")`. A refusal is therefore looked
  for in the exception, its cause chain and its suppressed lists (F3, F8).
- **The per-policy bundle.** `Untrusted`: `proxy(Proxy.NO_PROXY)`, the guarded `Dns` over the base resolver
  (`Dns.SYSTEM`, or the test fixtures' stub) and the guarded socket factory. `Trusted`: none of the three.
- **The JDK as a test oracle.** `InetAddress.getByName` parses a valid literal with no lookup, and sends any
  other unbracketed text that starts with a hex digit or `:` (`256.1.1.1`, `1.2.3.4.5`) to the system
  resolver. A differential therefore hands the JDK only what `StrictIp` accepts, IPv6 in brackets, and checks
  the bytes and that the result carries no host name. `InetAddress.ofLiteral` is Java 22+, and
  `sun.net.util.IPAddressUtil` is not exported, so neither is usable (F7).

### The deny table

- **The registries are unchanged since the snapshot of 2025-10-09** (read 2026-10-25): 25 IPv4 rows holding
  26 prefixes, and 25 IPv6 rows ([`iana-special-purpose-rows.md`](iana-special-purpose-rows.md) F1 to F3).
  IPv4 multicast `224.0.0.0/4` comes from the IPv4 Multicast Address Space registry (RFC 5771), and
  `2000::/3` is still the only block IANA allocates global unicast from (F4, F5).
- **IPv4:** every registry prefix plus multicast. Nested rows are kept, most specific first, so that each logs
  its own class.
- **IPv6:** five explicit blocks inside `2000::/3` (`2001::/23` with its nested rows, `2001:db8::/32`,
  `2002::/16`, `2620:4f:8000::/48`, `3fff::/20`); `::ffff:0:0/96` and `64:ff9b::/96` judged by their low 32
  bits; everything else outside `2000::/3` denied by default.
- **Two rules beyond the registries close published bypasses** ([`bypass-vectors.md`](bypass-vectors.md) §4):
  - `168.63.129.16/32`, the Azure platform endpoint: a public address outside every registry row, listed as
    an SSRF target by the published catalogues, and not a host that serves web content;
  - an ISATAP interface identifier inside `2000::/3` (bits 64 to 95 equal to `0000:5efe` or `0200:5efe`,
    ignoring the universal/local and group bits), which embeds an IPv4 address that a host with an ISATAP
    interface tunnels to. It is denied wholesale, as 6to4 and Teredo are.
- **Every row the registries mark globally reachable is denied too** (5 IPv4, 9 IPv6). Each is an anycast
  service, a relay, an AS112 sink or an identifier space, not a web host.
- **The table in tests.** An IPv6 vector is fed in the form the parser prints (`64:ff9b::7f00:1`, not
  `64:ff9b::127.0.0.1`), and each registry row by its first and last address.

### The host rules and the published vectors

- **Every published host-form vector falls to a rule** (bypass-vectors §2, §3): one of `Url.parse`'s
  refusals, `HostForm` for every lenient numeric form, `ReservedName`, or the address table. The two that
  passed every rule are the additions above.
- **`HostRules` is total on any text,** not only on what `Url.parse` lets through. A character outside
  `a-z 0-9 - _ .` (and `:` with hex digits for an IPv6 literal) is `HostForm`; letters are folded ASCII-only;
  an empty label is `HostForm`; and `0x` with zero hex digits counts as a number under the WHATWG "ends in a
  number" rule.
- **Not closable by a table:** a network's own NAT64 prefix, and a service on the deployer's own public
  address. Both are what `extraDeniedRanges` is for.
- **The transport's vectors** are listed in bypass-vectors §5 for the transport's own tests: redirects into
  private space, cross-protocol redirects, a `Location` that `HttpUrl.resolve` throws on, rebinding, mixed
  answers, process proxies, credentials across an origin change, and pinned-host confusion.

### The configuration API

- **Default sets as named values, derivation through a required `from`.** `TransportSettings.TrustedDefaults`
  and `UntrustedDefaults`, and `TransportSettings(from) { }`. The policies add `trusted(from) { }` and
  `untrusted(from) { }` beside `trusted { }` and `untrusted { }` ([`config-api-shape.md`](config-api-shape.md)
  F2, F3). A required `from` keeps a `$default` bridge out of the ABI.
- **A policy's builder** holds an assignable `settings` and a `settings { }` block that starts from the current
  value.
- **The sealed base has no `internal` constructor,** which Kotlin forbids on a sealed class. It is
  parameterless, and `Trusted` and `Untrusted` are nested final classes with `internal` constructors (F5).
- **Collections** are read-only `Set`s copied when the value is built; `allowedPorts` is `null` for any port.
- **Equality** is hand-written value equality. A policy's `toString()` prints counts for its pinned hosts,
  denied ranges and suffixes.

### Where the findings differ, and which view holds

- **Setting names.** `maxResponseBytes` and `idleConnectionTtl` hold, not `maxResponseBodyBytes` and
  `idleConnectionLifetime`. The test fixtures' `FakeHttpTransport` already takes `maxResponseBytes`, and the
  architecture names the idle-connection TTL.
- **`CidrBlock.toString()`** prints the canonical block (`10.0.0.0/8`), not only its family and prefix length.
  A block is a value, as a `Url` is; the stack never logs one, and a policy prints only how many it holds.
- **A DSL marker** for the module's builders is not part of this unit. The builders already in the module carry
  none, and inside a nested block the outer builder's members remain visible and still set what they name. A
  marker can be added to every builder of the module before `webtools-http` 1.0.0 is released at no cost to a
  consumer; after the release it would tighten consumers' sources.
- **The parent research's IPv6 list** names `::/96`, `::ffff:0:0:0/96`, `fec0::/10` and `ff00::/8` as
  special-purpose rows. They are not rows of that registry. All four lie outside `2000::/3`, so the outcome
  is the same.
- **Reason precedence.** The per-hop order is scheme, user-info, host form, IP literal, reserved name, port,
  pin, downgrade. A request's sensitive headers are judged after its first hop's URL.

### Known gaps

- **Read from source, not yet measured:** a JVM-wide SOCKS setting reaching the default factory's socket, the
  surfacing of a refusal with fast fallback on, and the inherited `newCall` routing through `lookup`. The
  unit's Level 2 (component) and Level 3 (integration) tests measure each.
- **Real networks** were not exercised: real DNS answers, HTTP/2 coalescing over TLS, a host with an ISATAP
  interface, and the reachability of `168.63.129.16` from a given host.
- **JDK 18 to 20 and 22 to 26** were not read file by file. 11.0.32, 17.0.21, 21.0.13 and 27 bracket them,
  and the `Socket` and `SocksSocketImpl` structure is the same in 17, 21 and 27.
- **The advisory search** filtered titles over the GitHub Advisory Database's 1,052 reviewed CWE-918
  advisories since 2020, plus four NVD keyword queries; it is not exhaustive.
