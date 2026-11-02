# Issue #65 — `okhttp-transport` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#65`, *"webtools-http: `OkHttpTransport` with manual redirects, the
  one-exchange guard, the resolver and socket guards, and the loopback test factories"*. It is unit 6,
  `okhttp-transport`, of [#55's architecture](../architecture.md) (§6.3.2, §6.7, §7.3, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring
  issues gain or lose with this unit, and what it leaves behind. The file-level detail is in
  [`plan.md`](plan.md); the evidence is in [`research/README.md`](research/README.md).
- **Status:** planned. The as-built record will be `final-implementation.md`; this file is brought up to date
  with it when the unit lands.
- **Baseline:** `master` `53965c8`; branch `feat/issue-65-okhttp-transport`, which holds the research
  commits.
- **Related docs:** [`plan.md`](plan.md); [`research/README.md`](research/README.md) with its four findings
  files; the systems-level [`../consequences.md`](../consequences.md); the as-built records of the units this
  one builds on, [`../62-http-core/`](../62-http-core/final-implementation.md),
  [`../63-test-support/`](../63-test-support/final-implementation.md) and
  [`../64-url-policy/`](../64-url-policy/final-implementation.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the supplied transport: the final class `OkHttpTransport` and its factory `OkHttpTransport(userAgent, policy)`, Stable Core and usable without the opt-in. The ABI dump grows by exactly these. The test-fixtures variant gains `LoopbackTransports`, `MockWebServer.webtoolsUrl(path)`, `Fixture.toMockResponse(method)` and `MockWebServer.enqueue(fixture, method)`, and `FakeHttpTransport`'s body cap now skips a response to `HEAD` and a `1xx`, `204` or `304`. No dependency of either variant changes. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
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

- **New files, all in `webtools-http`:**
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

- **`webtools-http/build.gradle.kts`** gains `okhttp-tls` 5.5.0 for `jvmTest` only, for the one HTTPS
  loopback test; its comment about `mockwebserver3` on the fixtures is brought to the present tense. The
  fixtures' published dependency list does not change, so the fixtures-publication check's expectations stay
  as they are.
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
- **[`../architecture.md`](../architecture.md)** gains dated callouts, when the unit lands, at the points §5
  lists.
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

[`plan.md`](plan.md) §12 has the exact contracts. In short:

- **Unit 9, `call-facades` (#68):**
  - it builds transports with the factory, deriving the policy with the `internal`
    `OutboundUrlPolicy.withSettings` to raise concurrency to the budget's burst;
  - the factory throws `IllegalArgumentException` for a bad `userAgent` or an over-large `maxResponseBytes`;
  - the `internal` list of controlled headers lets it refuse such a default header when a client is built,
    instead of on every call;
  - what a failure implies about what was sent: `InvalidRequest` is only ever returned before anything is
    sent; `RefusedByPolicy` can follow a sent hop (a refused redirect target, or a later hop's resolver or
    socket refusal); `Closed` can end a call whose request already reached the server. The architecture's
    rule that `RefusedByPolicy`, `InvalidRequest` and `Closed` release the permit is therefore exact only for
    `InvalidRequest`, and #68 settles the other two against these facts;
  - a `3xx` the transport does not follow is a response; its Level 3 uses `LoopbackTransports` and
    `webtoolsUrl`.
- **Unit 8, `call-budget` (#67):** a `503`'s `Retry-After` reaches the response with its value under the name
  `Retry-After`; a `429`'s headers are untouched; a returned `3xx` keeps its `Retry-After`, a followed one's is
  dropped.
- **Unit 10, `feeds` (#69):** `redirectChain` carries each followed hop's status (`301` or `308` for a permanent
  move); a `304` is returned with its headers and no body read; its Level 3 uses `LoopbackTransports.untrusted`
  with `webtoolsUrl` and a custom `Dispatcher` (`enqueue(fixture)` refuses a server with a custom dispatcher);
  its `.http` fixtures replay through `toMockResponse`.
- **Unit 7, `response-cache` (#66):** nothing.
- **Unit 11, `http-docs` (#70):** it documents the transport and the loopback fixtures from this unit's KDoc,
  and the README row and paragraph.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes §6.3.2 or §6.7 of
[`../architecture.md`](../architecture.md) concrete against the code as built, and each gets a dated callout
there when the unit lands.

- **`LoopbackTransports`** takes a whole `Untrusted` or `Trusted` policy and a resolver function, where §6.7
  sketches `settings` and `resolve`: the settings live on the policy as built (#64), and a loopback test of the
  mixed-answer and port rules needs the policy itself.
- **`Fixture.toMockResponse` and `MockWebServer.enqueue(fixture)`** take a defaulted request method, because
  only a response to `HEAD` is written differently.
- **The one-exchange guard hides a `407`, `408`, `421` or `503` by its status** (a placeholder and a private
  header the response conversion reads back), where §6.3.2 has it hide a `503`'s `Retry-After` and lets the
  counter cover a `421`. Every status in 100..599 is then a response, as the port requires: a `421` on a
  coalesced connection is delivered rather than `RetryRefused`, and a `407` on a direct route, for which OkHttp
  throws, is delivered too. The counter stays the fail-closed backstop.
- **Twelve request headers are refused** and a `Range` request is sent with `Accept-Encoding: identity`; §6.3.2
  is silent on caller-supplied transport headers, and #62's plan left them to this unit.
- **The dispatcher has its own `ExecutorService` with daemon threads**, so §6.3.2's "without `close()` the JVM
  lingers 60 s" no longer holds; `close()` keeps its steps.
- **An unresolvable or foreign-scheme `Location`** is a `RefusedByPolicy`, and a `300` is returned rather than
  followed; §6.3.2 says only that a `3xx` not followed is returned.
- **The published fake's cap** follows the transport's no-body rule (§6.7 says the fake mirrors the cap).

## 6. Neighbouring issues

- **#58** (CI and repository practices): `CHANGELOG.md` exists after this unit, so #58 no longer needs to
  create it. Its CI would be the first place these tests run on every change; the four `@Isolated` classes
  (the three of this unit and the existing SOCKS class) must not run in parallel with others, and the
  child-JVM test needs a JDK on the agent.
- **The IntelliJ checks of #62, #63 and #64** stay with the maintainer and do not gate this unit; this unit adds
  its own Quick Documentation item (plan §9, Level 5).

## 7. Follow-ups this unit leaves

- **The recorder over the real transport.** A Level 3 test of `FixtureRecorder` wrapping
  `LoopbackTransports.untrusted` would close #63's gap "the recorder against the real transport was not run".
- **An HTTPS loopback fixture**, if a consumer needs one: a `LoopbackTransports` variant that takes TLS trust.
  Additive.
- **A library-written `cause` for `TlsFailed`**, naming the kind of TLS failure (expired, untrusted, hostname)
  without the certificate's text. Additive.
- **A `Retry-After` on a followed `3xx`** is dropped; if the rate-signal reader wants it, the chain would have
  to carry it.
- **A dual-stack ordering refinement:** fast fallback reports the first route's failure on top, so an IPv6
  refusal can hide an IPv4 TLS failure (`ConnectFailed` where `TlsFailed` would be more telling). Mapping an
  `SSLException` among the suppressed failures first is a candidate refinement.
- **Runs on Linux and macOS and on JDK 17 and 26**, once CI exists (#58).

## 8. Known gaps

Planned; brought up to date with the build.

- **Measured only on Windows 11 with JDK 23.0.2:** the research probes, the child-JVM timings, the proxy
  behaviour and the exception messages. The leaves do not depend on platform wording.
- **Real networks are not exercised:** real DNS, real provider idle timeouts, HTTP/2 over real TLS, and a `421`
  on a coalesced HTTP/2 connection (its hiding is tested over HTTP/1.1 and on a synthetic response).
- **The status hide for `408`, `421` and `503` rests on reading `followUpRequest`;** the `407` rewrite was
  measured, and the Level 3 suite measures the others when it runs.
- **A server that ignores `Accept-Encoding: identity`** (a SHOULD in RFC 9110) still delivers a coded body,
  undecoded, with its `Content-Encoding`.
- **`TlsFailed` carries no cause**, so an expired certificate and an untrusted one look alike to a consumer
  (§7).
- **Only OkHttp 5.5.0 was read and run;** 5.5.1's one change in this area was read. A consumer's newer 5.x
  is covered by the layered guards and the Level 3 suite, not by a measurement.
- **IDE behaviour** (Quick Documentation of the new public declarations) is the maintainer's.
