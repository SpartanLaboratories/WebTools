# Issue #65 — `okhttp-transport` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#65`, *"webtools-http: `OkHttpTransport` with manual redirects, the
  one-exchange guard, the resolver and socket guards, and the loopback test factories"*. It is unit 6,
  `okhttp-transport`, of [#55's architecture](../architecture.md) (§6.3.2, §6.7, §7.3, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring
  issues gain or lose with this unit, as built, and what it leaves behind. The file-level detail is in
  [`plan.md`](plan.md); the as-built record, with every measurement and deviation, is
  [`final-implementation.md`](final-implementation.md); the evidence is in [`research/README.md`](research/README.md).
- **Status:** built, with the plan's amendment of 2026-11-02 (two defaulted `internal` constructor parameters for
  the module's own tests) and the deviations of [`final-implementation.md`](final-implementation.md) §2, none of
  which changes a published artifact, a sibling contract or a refinement below.
  [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-02) for this unit at §6.3.2, §6.6,
  §6.7, §7.3 and §13.
- **Baseline:** `master` `53965c8`; branch `feat/issue-65-okhttp-transport`, with the research commits and the
  code commits `962bbe3`, `dd4097e`, `357554a` and `300fb67`, then `db82470` (`test: declare the in-flight close
  case Unit so JUnit runs it (Issue #65)`), the code-landing commit.
- **Related docs:** [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md);
  [`research/README.md`](research/README.md) with its four findings files; the systems-level
  [`../consequences.md`](../consequences.md); the as-built records of the units this one builds on,
  [`../62-http-core/`](../62-http-core/final-implementation.md),
  [`../63-test-support/`](../63-test-support/final-implementation.md) and
  [`../64-url-policy/`](../64-url-policy/final-implementation.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the supplied transport: the final class `OkHttpTransport` and its factory `OkHttpTransport(userAgent, policy)`, Stable Core and usable without the opt-in. The ABI dump grows by exactly these: the class with `close`, `execute` and `toString`, and `OkHttpTransportKt` with the factory. The test-fixtures variant gains `LoopbackTransports`, `MockWebServer.webtoolsUrl(path)`, `Fixture.toMockResponse(method)` and `MockWebServer.enqueue(fixture, method)`, and `FakeHttpTransport`'s body cap now skips a response to `HEAD` and a `1xx`, `204` or `304`. No dependency of either variant changes. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **A transport that enforces the outbound-URL policy.** Until this unit the policies of #64 were values a
  consumer could build and compare; `OkHttpTransport(userAgent, policy)` is the first thing that applies them
  to a network fetch: the hop check before any socket, the resolver and socket guards for `Untrusted`, no
  proxy for `Untrusted`, and the policy's caps and timeouts.
- **One request per hop, never a retry.** OkHttp's own re-send paths are switched off, or hidden from it by
  status: a `407`, `408`, `421` or `503` (a `503` with `Retry-After: 0` included) is delivered once, as the
  response it is, with its headers untouched. A second wire exchange for one hop fails closed as
  `RetryRefused`.
- **Redirects followed by the transport**: `301`, `302`, `303`, `307` and `308` for `GET` and `HEAD`, at most
  `maxRedirects`, every hop judged by the policy before any socket, every sensitive header removed once a hop
  leaves the first origin, each hop recorded in `HttpResponse.redirectChain`.
- **A body cap on decoded bytes**, a body read that a cancelled coroutine stops at once, and no body read for
  `HEAD`, `1xx`, `204`, `304` or a followed redirect.
- **A stricter request contract than `HttpRequest` alone.** A request that carries one of twelve headers the
  transport controls (`User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length`,
  `Transfer-Encoding`, `Keep-Alive`, `Proxy-Connection`, `TE`, `Trailer`, `Upgrade`, `Expect`) fails with
  `InvalidRequest` before anything is sent. `HttpRequest` itself still accepts them, so such a request builds
  and fails only when executed. A `Range` request is sent with `Accept-Encoding: identity`.
- **A factory that refuses what it cannot honour:** a blank or non-printable `userAgent`, or a
  `maxResponseBytes` above the largest body a byte array holds, is an `IllegalArgumentException` at
  construction.
- **A JVM that exits without `close()`.** The transport's dispatcher threads are daemon threads; `close()`
  still cancels the calls in flight and releases the pool, and is the supported way to stop.
- **Loopback test support** for a consumer's own Level 3 tests: the real transport over `mockwebserver3`
  with only loopback exempted from `Untrusted`'s rules, and one `.http` fixture file serving both the fake and
  the mock server.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`final-implementation.md`](final-implementation.md) §1 lists them):
  - `jvmMain`: the transport and its parts (the client it builds, the one-exchange guard, the redirect
    decision, the request and response conversion, the capped body read, the failure mapping, the bridge to
    coroutines);
  - `commonMain`: the `internal` list of the headers the transport controls (compiled for `js` by the check
    module like every common file);
  - `jvmTestFixtures`: the four loopback fixtures;
  - their tests in `jvmTest` (and `commonTest` for the header list), in the level packages, with their
    support classes.
- **Changed files:**
  - `FakeHttpTransport.kt` (fixtures): its cap follows the transport's no-body rule; one component test is
    added to `FakeHttpTransportComponentTest`;
  - `PolicyEnforcement.kt` (`jvmMain`): its KDoc names the transport;
  - `PolicyRefusals.kt` (`jvmMain`): `policyRefusal()` runs on a bounded cause-and-suppressed walk moved into
    a generic helper, which the transport's own marker uses too; its behaviour is unchanged and its existing
    test is the guard;
  - `webtools-http/api/webtools-http.api`: the transport class and its factory.
- **No caller exists yet.** No production code in the repository calls an `HttpTransport`; the call facades
  (#68) are the first.

### Build

- **`webtools-http/build.gradle.kts`** gains `okhttp-tls` 5.5.0 for `jvmTest` only, for the HTTPS loopback
  tests; its comment about `mockwebserver3` on the fixtures is brought to the present tense. The fixtures'
  published dependency list does not change, so the fixtures-publication check's expectations stay as they
  are.
- **`./gradlew build` does more work:** the new tests of `webtools-http`, Level 3 among them with real
  loopback servers and one child JVM; the fixtures-publication check runs again because its inputs include
  the fixtures and `jvmMain`; `apiCheck` against the regenerated dump; the check module's `js` compile of
  the new common file.
- **Tests that need sequential execution:** three new `@Isolated` classes set process-wide state and restore it
  in `finally` (the HTTP proxy properties, the SOCKS properties, and `System.err` for the hostile status
  line), beside the existing SOCKS class.

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the OkHttp transport, and the
  test-fixtures paragraph names the loopback fixtures.
- **`CHANGELOG.md`** is created with this unit, in the Keep a Changelog layout, its entry under
  `## [Unreleased]`.
- **KDoc** on every new public declaration states its tier and contract; `HttpHeaders.addSensitive`'s KDoc,
  which already says the transport strips a sensitive header on a cross-origin hop, becomes true.
- **[`../architecture.md`](../architecture.md)** carries dated corrections (2026-11-02) at §6.3.2, §6.6, §6.7 and
  §7.3 for the points of §5, and at §13 for the `CHANGELOG.md` this unit creates.
- **[`../consequences.md`](../consequences.md)**, the systems-level document, gains one line for this unit,
  linking to §4. It was `impact.md` until this landing; the sub-issue folders from before it keep their own
  `impact.md`.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class does HTTP through the stack**, so nothing is moved onto the transport here. The
  `Connector` follow-ups of [`../architecture.md`](../architecture.md) §8
  (`connector-get-skrape-on-webtools-http`, `connector-download-fetch-half`, `connector-line-reader-api`) can
  build on it once `webtools-http` 1.0.0 is released.
- **`FakeHttpTransport` adopts the transport's no-body rule** for its cap, so a test against the fake and a
  run against the transport agree on a `HEAD` response or a `304` that declares a large length. The fake
  still mirrors only `Closed` and the cap: it does not refuse the controlled headers, judge URLs or follow
  redirects.
- **`FixtureRecorder`** can now decorate the real transport; its tests still record through the fake (§7).

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts, and the build holds them as written. In short:

- **Unit 9, `call-facades` (#68):**
  - it builds transports with the factory, deriving the policy with the `internal`
    `OutboundUrlPolicy.withSettings` to raise concurrency to the budget's burst;
  - `jvmMain` code may call the `internal` constructor `(userAgent, guard, baseDns, donor)`; its two further,
    defaulted parameters (the deadline's `TimeSource` and each hop's `Call.Factory`) are for this module's
    tests, and the facades never pass them;
  - the factory throws `IllegalArgumentException` for a bad `userAgent` or an over-large `maxResponseBytes`;
  - the `internal` list of controlled headers lets it refuse such a default header when a client is built,
    instead of on every call;
  - what a failure implies about what was sent: `InvalidRequest` is only ever returned before anything is
    sent; `RefusedByPolicy` can follow a sent hop (a refused redirect target, or a later hop's resolver or
    socket refusal); `Closed` can end a call whose request already reached the server. The architecture's
    rule that `RefusedByPolicy`, `InvalidRequest` and `Closed` release the permit is therefore exact only for
    `InvalidRequest`: #68 settles its permit handling for the other two against the table of plan §12, which
    says for each failure whether a request was written;
  - a `3xx` the transport does not follow is a response; its Level 3 uses `LoopbackTransports` and
    `webtoolsUrl`.
- **Unit 8, `call-budget` (#67):** a `503`'s `Retry-After` reaches the response with its value under the name
  `Retry-After`; a `429`'s headers are untouched; a returned `3xx` keeps its `Retry-After`, a followed one's is
  dropped.
- **Unit 10, `feeds` (#69):** `redirectChain` carries each followed hop's status (`301` or `308` for a permanent
  move); a `304` is returned with its headers and no body read; its Level 3 uses `LoopbackTransports.untrusted`
  with `webtoolsUrl` and a custom `Dispatcher`, because `enqueue(fixture)` refuses a server whose dispatcher was
  replaced; its `.http` fixtures replay through `toMockResponse`.
- **Unit 7, `response-cache` (#66):** nothing.
- **Unit 11, `http-docs` (#70):** it documents the transport and the loopback fixtures from this unit's KDoc,
  and the README row and paragraph.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md)
concrete against the code as built, and each is a dated correction (2026-11-02) there, at the section named.

- **`LoopbackTransports`** (§6.7) takes a whole `Untrusted` or `Trusted` policy and a resolver function, where
  §6.7 sketches `settings` and `resolve`: the settings live on the policy as built (#64), and a loopback test of
  the mixed-answer and port rules needs the policy itself.
- **`Fixture.toMockResponse` and `MockWebServer.enqueue(fixture)`** (§6.7) take a defaulted request method,
  because only a response to `HEAD` is written differently.
- **The one-exchange guard hides a `407`, `408`, `421` or `503` by its status** (§6.3.2 and §7.3: a placeholder
  and a private header the response conversion reads back), where §6.3.2 has it hide a `503`'s `Retry-After`
  and lets the counter cover a `421`. Every status in 100..599 is then a response, as the port requires: a
  `421` on a coalesced connection is delivered rather than `RetryRefused`, and a `407` on a direct route, for
  which OkHttp throws, is delivered too. The counter stays the fail-closed backstop.
- **Twelve request headers are refused** and a `Range` request is sent with `Accept-Encoding: identity`
  (§6.3.2); §6.3.2 is silent on caller-supplied transport headers, and #62's plan left them to this unit.
- **The dispatcher has its own `ExecutorService` with daemon threads** (§6.3.2), so §6.3.2's "without
  `close()` the JVM lingers 60 s" no longer holds; `close()` keeps its steps.
- **An unresolvable or foreign-scheme `Location`** is a `RefusedByPolicy`, and a `300` is returned rather than
  followed (§6.3.2 and §7.3); §6.3.2 says only that a `3xx` not followed is returned.
- **The `internal` constructor** (§6.3.2) takes, after the guard, the resolver and the optional donor, the
  shared deadline's `TimeSource` and each hop's `Call.Factory`, both defaulted and passed only by the module's
  own Level 2 test. Neither is a seam in the tier sense: the public shape, the dump and the fixtures are
  unchanged.
- **The permit rule of §6.6** ("`RefusedByPolicy`, `InvalidRequest`, `Closed` release the permit") is exact only
  for `InvalidRequest` (§4).
- **The published fake's cap** follows the transport's no-body rule (§6.7 says the fake mirrors the cap).

## 6. Neighbouring issues

- **#58** (CI and repository practices): `CHANGELOG.md` exists after this unit, so #58 no longer needs to
  create it. Its CI would be the first place these tests run on every change; the four `@Isolated` classes
  (the three of this unit and the existing SOCKS class) must not run in parallel with others, and the
  child-JVM test needs a JDK on the CI machine.
- **The IDE checks of #62, #63 and #64** are still open. They did not gate this unit; their trigger, before
  this unit started, has passed, and they now share this unit's: the next IntelliJ session (§8).

## 7. Follow-ups this unit leaves

- **Two dead lines in tests**, deleted in the next change that touches these tests: the trailing
  `launch { }.join()` of `CancellationIntegrationTest`'s "a coroutine cancelled before execute runs throws
  CancellationException", a yield with no observable effect (the `call.await()` before it already observes the
  cancelled job); and the `(result as Result<HttpResponse>)` cast at `TransportSecrecyNonFunctionalTest.kt:88`,
  a no-op, because the value already has that type.
- **[WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87):** making
  `UrlParseLinearTimeNonFunctionalTest`, landed with #62, hold its bound when the level tasks run concurrently
  ([`final-implementation.md`](final-implementation.md) §4 has the run it failed).
- **The recorder over the real transport.** A Level 3 test of `FixtureRecorder` wrapping
  `LoopbackTransports.untrusted` would close #63's gap "the recorder against the real transport was not run".
- **An HTTP/2 Level 3 test** with an `okhttp-tls` server that negotiates `h2`: the `407` and `421` hiding over
  HTTP/2, a server-sent `PROTOCOL_ERROR` reset (mapped to `Io`) and the header-name rules on the wire
  ([`plan.md`](plan.md) §13). Additive.
- **An HTTPS loopback fixture**, if a consumer needs one: a `LoopbackTransports` variant that takes TLS trust.
  Additive.
- **A library-written `cause` for `TlsFailed`**, naming the kind of TLS failure (expired, untrusted, hostname)
  without the certificate's text. Additive.
- **A `HostnameVerifier` or certificate-pinning setting** on the factory, additive on
  `OutboundUrlPolicy.Trusted` ([`plan.md`](plan.md) §13).
- **A `Retry-After` on a followed `3xx`** is dropped; if the rate-signal reader wants it from a chain, the
  chain would have to carry it ([`plan.md`](plan.md) §13).
- **A public streaming or larger-than-array body:** a new API, not a substitution ([`plan.md`](plan.md) §13).
- **A dual-stack ordering refinement:** fast fallback reports the first route's failure on top, so an IPv6
  refusal can hide an IPv4 TLS failure (`ConnectFailed` where `TlsFailed` would be more telling). Mapping an
  `SSLException` among the suppressed failures first is a candidate refinement.
- **An assumption-guarded IPv6 loopback test** (`webtoolsUrl` on `::1`); the formatting is already a pure test
  ([`plan.md`](plan.md) §13).
- **Runs on Linux and macOS and on JDK 17 and 26**, once CI exists (#58; [`plan.md`](plan.md) §13).

## 8. Known gaps

- **Measured only on Windows 11 with JDK 23.0.2:** the research probes, the build's runs, the child-JVM
  timings, the proxy behaviour and the exception messages, among them the plain `SocketException` a TLS hello
  to a listener that closes on accept gets ([`final-implementation.md`](final-implementation.md) D4). The
  leaves do not depend on platform wording.
- **Real networks are not exercised:** real DNS, real provider idle timeouts, HTTP/2 over real TLS, and a `421`
  on a coalesced HTTP/2 connection. The optional real-endpoint fetch of the Level 5 pass was not run
  ([`final-implementation.md`](final-implementation.md) §5).
- **The status hide is measured over HTTP/1.1 on loopback only.** `ExchangeGuardStatusIntegrationTest`
  delivers a `407`, `408`, `421` and `503`, each once and with its headers, and mutations 1 and 13 show that the
  hide is what delivers the `503` once and the `407` at all. What OkHttp would do with a `408` or a `421`
  without the hide, and anything over HTTP/2, rests on reading `followUpRequest`.
- **A server that ignores `Accept-Encoding: identity`** (a SHOULD in RFC 9110) still delivers a coded body,
  undecoded, with its `Content-Encoding`.
- **`TlsFailed` carries no cause**, so an expired certificate and an untrusted one look alike to a consumer
  (§7).
- **Only OkHttp 5.5.0 was read and run;** 5.5.1's one change in this area was read. A consumer's newer 5.x
  is covered by the layered guards and the Level 3 suite, not by a measurement.
- **The close-step failure case is not tested** ([`final-implementation.md`](final-implementation.md) D6).
  Covered instead: each step of `close()` runs inside `catchNonFatal`, whose behaviour
  `CatchNonFatalComponentTest` (#62) pins, and the close tests show that `close()` twice neither throws nor
  blocks.
- **A received header whose name is not a token, at Level 2** ([`final-implementation.md`](final-implementation.md)
  D2). The `DEBUG` line that counts the entries the response conversion drops is asserted by no test, and such
  an entry on a hidden status is tested at no level. Covered instead:
  `ReceiveHeadersDeterministicTest` drops and counts such names, and `ReceivedHeadersIntegrationTest` delivers
  a `200` from the wire with an obs-fold continuation, which the exchange guard copies and the conversion drops
  while its neighbours stay.
- **P9's mutation 4 was not run** ([`final-implementation.md`](final-implementation.md) D5). Covered instead:
  the endless-body case of `CancellationIntegrationTest` and mutation 3.
- **`webtoolsUrl`'s refusal of a server bound to a non-loopback address is not tested:** binding a server to a
  non-loopback address has no set-up that holds on every host. The refusal is one `check` on
  `InetAddress.isLoopbackAddress`; `WebtoolsUrlComponentTest` covers the other two refusals and
  `LoopbackUrlTextDeterministicTest` the URL text of every loopback form.
- **IDE behaviour is untested:** Quick Documentation of `OkHttpTransport`, the factory,
  `LoopbackTransports.untrusted` and `webtoolsUrl` ([`final-implementation.md`](final-implementation.md) §5).
  Owner: the maintainer; trigger: the next IntelliJ session, together with the open IDE items of #62, #63 and
  #64 ([`../62-http-core/impact.md`](../62-http-core/impact.md) §7,
  [`../63-test-support/impact.md`](../63-test-support/impact.md) §7,
  [`../64-url-policy/impact.md`](../64-url-policy/impact.md) §8).

## 9. The plan's assumptions, as built

One assumption of [`plan.md`](plan.md) §3 failed: a TLS hello to a listener that closes on accept gets a plain
`SocketException` on this host, not an `SSLException`, so that Level 3 case accepts `TlsFailed` or `Io`, as the
assumption's fallback foresaw, and the untrusted-certificate case keeps `TlsFailed` covered
([`final-implementation.md`](final-implementation.md) D4).

The others held as far as this build shows: `okhttp-tls` stays a `jvmTest` dependency and the fixtures' list is
unchanged; the rebinding, mixed-answer and port tests run through `LoopbackTransports` with whole policies; the
`@Isolated` classes pass in the full runs, and the two run alone also pass alone; no later hop's request was found
that fails to build; and no version moves. Linux and macOS, a newer OkHttp 5.x and a server that ignores
`Accept-Encoding: identity` stay as §8 says.
