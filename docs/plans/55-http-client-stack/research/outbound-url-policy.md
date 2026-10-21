# The outbound-URL policy: server-side request forgery guard and where each check runs

## Question

How should the `Trusted` and `Untrusted` outbound-URL policies of `webtools-http` guard against server-side request forgery (SSRF) on the OkHttp 5.5.0 transport, and exactly where does each check run?

An answer has to settle:

- The URL-level, resolver-level and connect-time checks, and the job of each.
- The deny list, derived from the IANA special-purpose registries.
- Proxies.
- DNS rebinding and multiple resolved addresses.
- The shape of the two policies, and how a test permits loopback.
- The typed refusal reasons, and what a refusal discloses.
- The bypasses published against incomplete guards.

Requirements:

- `Untrusted` allows `http` and `https` only, rejects loopback, link-local, private and other non-public ranges, holds across redirects, caps redirects and body size, and keeps timeouts short.
- The two supplied policies are Stable Core configuration.
- Any seam a consumer implements is Experimental behind the single opt-in marker, and the defaults work without it.
- No OkHttp or `java.*` type appears in a `commonMain` signature.
- Failures go through `Result`, and cancellation propagates.
- Policy numbers are constructor parameters with defaults.
- Credentials and queries are never logged.

Evidence labels: **documented consensus** (a standard, official documentation, or several independent credible sources), **observed practice** (what a mature library visibly does, read at a pinned commit), **measured** (run by me in throwaway probes outside the repository: OkHttp 5.5.0, `mockwebserver3` 5.5.0, Kotlin 2.2.0, Gradle 9.7.1, JDK 23 with the behaviour checks repeated on JDK 11, 17 and 21), **single source** (one author's opinion or a very young project).

## Bottom line

- **No binding requirement fails.** The `Untrusted` behaviour holds across redirects with three checks that each have a distinct job, and the design needs no public loosening knob.
- **Three checks, because every one alone leaks (measured, 39 host forms):**
  - **(A) The per-hop URL check** runs in the transport's redirect loop before any `Call`, so before any socket. It owns scheme, user-info, host form, IP literals, reserved names and ports. It is the only check that stops IP-literal hosts: with an `okhttp3.Dns` guard alone, `http://2130706433/`, `http://127.1/`, `http://[::1]/` and `http://169.254.169.254/` all connected, because OkHttp never calls `Dns` for a digits-and-dots or colon host.
  - **(B) A guarding `Dns`** judges every address a name resolves to. It owns names that point at private space (`localhost`, `foo.localhost`, a public name with a private A or AAAA record, rebinding). OkHttp does one lookup per new connection and connects only to addresses in that answer.
  - **(C) A guarding `SocketFactory`** judges the `InetSocketAddress` of every connect attempt. It is a version-independent backstop for the day a newer OkHttp 5.x (the declared version is an ordinary requirement a consumer may raise) reaches a socket without `Dns`. With native redirects it was the only layer that stopped a redirect to `169.254.169.254`.
  - An `EventListener` is not a check, because it cannot veto a connect.
- **Proxies: `Untrusted` forces `Proxy.NO_PROXY`** and has no proxy parameter. With an HTTP proxy the target name never reaches `Dns` and the socket factory sees only the proxy's address. With a SOCKS proxy the socket factory is bypassed entirely. `Trusted` keeps OkHttp's default selection (`ProxySelector.getDefault()`, so JVM proxy properties apply).
- **Deny table: default-deny for IPv6, deny list for IPv4, as a pure function over address bytes in common code.**
  - IPv6 is allowed only inside global unicast `2000::/3`, minus the IANA special-purpose blocks inside it. IPv4-mapped and NAT64 well-known addresses are judged by the IPv4 address they carry.
  - `java.net.InetAddress`'s predicates miss 16 of the 26 non-public addresses tried: CGNAT, `192.0.0.0/24`, benchmarking, documentation, reserved, `fc00::/7`, IPv4-compatible, NAT64, 6to4 and others.
  - The classifier compiles and its tests pass identically on JVM and Node (JS), so it is Level 4a testable and portable. The JVM side only converts `InetAddress.address`.
- **Host-form rule: refuse any digits-and-dots host that is not strict dotted-decimal.** The JDK reads `127.1`, `2130706433` and `0177.0.0.1` leniently (and reads `0177` as decimal 177, where `inet_aton` reads octal), so the same text names different machines to different parsers. Refusing non-canonical forms removes the disagreement (the Python `ipaddress`, `netmask` and node `ip` advisories are this bug).
- **Shape: a closed pair, `OutboundUrlPolicy.Trusted` and `OutboundUrlPolicy.Untrusted`, each carrying its default `TransportSettings`.** Both take tightening-only data parameters, so there is no consumer-implemented seam and no Experimental marker is needed for 1.0.0.
  - `Trusted` checks scheme, user-info and optional host pinning, and does no address filtering.
  - The loopback test seam is an internal flag reachable only from the test-fixtures factory. It exempts loopback hosts and addresses only.
- **Refusals carry a coarse reason and never an address or a query.** Mixed public/private answers are rejected as a whole by default (the mature guards split: three drop denied addresses, one vets each connect attempt, one rejects the whole answer; see Consideration 2).

## Findings

### F1. A resolver-only guard misses every IP-literal host (measured + documented)

OkHttp 5.5.0 `RouteSelector.nextRoutes` sends a host that matches the regex `([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\d.]+)` straight to `InetAddress.getByName`, and calls `Dns.lookup` only for everything else. For a SOCKS proxy it never resolves the target at all.

Measured with a recording `Dns` and a socket factory that records the requested `InetSocketAddress` and then connects to a local mock server instead (no packet leaves the machine). Host parsed by `HttpUrl` -> `Dns` called? -> address OkHttp tried:

| Input host | `HttpUrl.host` | `Dns` called | Connect target |
|---|---|---|---|
| `2130706433` | `2130706433` | no | `127.0.0.1` |
| `127.1`, `127.0.1` | unchanged | no | `127.0.0.1` |
| `0`, `0.0.0.0`, `[::]` | `0`, `0.0.0.0`, `::` | no | `0.0.0.0`, `::` |
| `0177.0.0.1`, `0177.0.0.01` | unchanged | no | `177.0.0.1` (decimal, not octal) |
| `08.0.0.1`, `00000127.0.0.1` | unchanged | no | `8.0.0.1`, `127.0.0.1` |
| `017700000001` | unchanged | no | none: `IllegalArgumentException: Invalid IP address literal` |
| `127.0.0.1.` (trailing dot) | unchanged | no | none: the JDK falls through to the OS resolver |
| `0x7f000001`, `0x7f.0.0.1`, `0x7f.1` | unchanged | **yes** | whatever the resolver returns |
| `[::ffff:127.0.0.1]`, `[::ffff:7f00:1]`, `[0:0:0:0:0:ffff:7f00:1]` | `127.0.0.1` | no | `127.0.0.1` |
| `[::127.0.0.1]`, `[::7f00:1]` | `::7f00:1` | no | IPv6 `::7f00:1` |
| `[64:ff9b::7f00:1]` | `64:ff9b::7f00:1` | no | that address |
| `[2002:7f00:1::]`, `[fd00::1]`, `[fe80::1]`, `[fec0::1]`, `[ff02::1]` | unchanged | no | that address |
| `169.254.169.254`, `100.64.0.1`, `10.0.0.5` | unchanged | no | that address |
| `localhost`, `LOCALHOST`, `foo.localhost`, `localhost.` | lower-cased, dot kept | yes | resolver's answer |
| `[fe80::1%25eth0]`, `[fe80::1%eth0]`, `127.0.0.1%00` | none | n/a | `HttpUrl` rejects the URL |

Source quotes are in claim 1 and 2. This extends the finding in `docs/plans/55-http-client-stack/research/http-transport.md` (F4, "IP-literal forms skip `Dns`") from two forms to the whole catalogue.

### F2. OkHttp canonicalises before anyone sees the host, which helps (measured)

- IPv4-mapped IPv6 (`[::ffff:127.0.0.1]`, `[::ffff:7f00:1]`, the long form) arrives as plain dotted IPv4 `127.0.0.1`. OkHttp's source comments it: `// An IPv4-mapped IPv6 address.`
- Fullwidth `ｌｏｃａｌｈｏｓｔ` and circled `ⓛ...` letters arrive as `localhost`.
- Fullwidth digits `１２７.０.０.１`, an ideographic full stop (`127。0。0。1`) and a fullwidth full stop arrive as `127.0.0.1`.
- Percent-encoded host bytes (`%31%32%37.0.0.1`, `127%2E0%2E0%2E1`) arrive as `127.0.0.1`.
- Upper case is lower-cased.
- A trailing dot is kept (`localhost.`).
- `http:127.1/`, `http:/127.0.0.1/`, `http:///127.0.0.1/` and `HTTP://` are accepted.
- Ports `:0`, `:65536`, a second `:80` and a tab in the host are rejected.

Consequence: the per-hop check must run on the `HttpUrl` that OkHttp will connect to (post-IDNA, post-percent-decoding), never on the caller's string and never on a second parser's reading of it.

### F3. Parser disagreement is real and OkHttp is the parser that counts (measured + documented)

Host read by each parser for URL-confusion strings (measured):

| Input | OkHttp 5.5.0 host | `java.net.URI` | `java.net.URL` |
|---|---|---|---|
| `http://evil.example\@127.0.0.1:80/` | `evil.example` | rejected | rejected |
| `http://127.0.0.1:80\@evil.example/` | `127.0.0.1` | rejected | rejected |
| `http://127.0.0.1。evil.example/` | `127.0.0.1.evil.example` | no host (the authority keeps the non-ASCII full stop) | a host containing the non-ASCII full stop |
| `http://１２７.0.0.1/` | `127.0.0.1` | no host (the authority keeps the fullwidth digits) | a host of fullwidth digits |
| `http://127.0.0.1%09.evil.example/` | rejected | no host | host `127.0.0.1%09.evil.example` |
| `http://localhost%2e/` | `localhost.` | no host | `localhost%2e` |
| `http://127.1/`, `http://0x7f.1/` | unchanged | no host (registry authority) | host `127.1`, `0x7f.1` |

Of the 23 strings tried (the table shows the instructive ones), `java.net.URI` and `java.net.URL` return no host or a different host than OkHttp in 7, and refuse 3 more that OkHttp accepts. So a validator that reads `java.net.URI` or `java.net.URL` and a fetcher that connects through OkHttp do not see the same host. This is the root cause Orange Tsai's talk names ("The root cause of the problem lies in the inconsistency of URL parsers and URL requesters.") and the OWASP cheat sheet's rule is "Treat parser disagreement as a rejection." The check therefore reads the `HttpUrl`, and the common-code `Url.parse` of the transport port is differentially tested against `HttpUrl` (Level 4a, JVM test).

`Location` values go through `HttpUrl.resolve`, the same parser the connect uses. Of 23 hostile `Location` strings (protocol-relative `//2130706433/x`, `//127.1/x`, `//[::1]/x`, backslash forms, `gopher://127.0.0.1:6379/_PING`, `file:///etc/passwd`, user-info forms), 6 resolved to `null` (not followed), 14 were refused by the per-hop check, and 3 were allowed because their host is public (`../../c`, `https://public.example/ok`, and `//evil.example\@127.0.0.1/`, which resolves to host `evil.example` and then goes through B and C like any public name).

### F4. What each layer catches (measured, JDK 11, 21 and 23 identical)

Each cell is the outcome of one `GET` with only that layer on (A = per-hop URL check before the call; B = guarding `Dns`, reject-any; C = guarding `SocketFactory`). "reached" means OkHttp connected to the listed address (to the local tunnel). The stub `Dns` answered `93.184.216.34` (never connected) for unknown names.

| Bypass | A only | B only | C only |
|---|---|---|---|
| `2130706433`, `127.1`, `0`, `0.0.0.0` | refused | **reached** `127.0.0.1` / `0.0.0.0` | refused |
| `0177.0.0.1` (JDK: 177.0.0.1) | refused (non-canonical) | reached `177.0.0.1` | reached `177.0.0.1` (a public address, correctly) |
| `0x7f000001`, `0x7f.0.0.1` | refused (numeric last label) | judges the resolver's answer | judges the resolver's answer |
| `[::]`, `[::1]`, mapped, `[::7f00:1]`, NAT64, 6to4, ULA, link-local | refused | **reached** | refused |
| `169.254.169.254`, `100.64.0.1`, `10.0.0.5`, `127.0.0.1` | refused | **reached** | refused |
| `localhost`, `LOCALHOST`, `foo.localhost`, fullwidth `localhost` | refused (name rule) | refused (loopback answer) | refused |
| fullwidth `127.0.0.1`, `%31%32%37.0.0.1` | refused | **reached** | refused |
| public name -> `10.0.0.5`, `169.254.169.254`, `fd00::1`, `::ffff:127.0.0.1`, `64:ff9b::7f00:1`, `127.0.0.1` | **reached** | refused | refused |
| name -> `[93.184.216.34, 10.0.0.5]` or `[10.0.0.5, 93.184.216.34]` | **reached** | refused (reject-any) | skips the denied attempt, connects to the public one |
| rebinding: second lookup returns `10.0.0.5` | n/a | refused on the new connection | refused |
| redirect `302` to `http://169.254.169.254/` (OkHttp native follow) | n/a | **reached** `169.254.169.254` | refused (`PolicyRefusalIo` at the top of the chain) |
| redirect `302` to `http://2130706433:P/` (native follow) | n/a | **reached** `127.0.0.1` | refused |
| redirect `302` to a name resolving to `10.0.0.5` | n/a | refused | refused |
| the same redirects, manual per-hop loop with A, B, C | refused at hop 1, **0 sockets** | | |

Reading the table: B alone fails every literal; A alone fails every name that points inward; C alone gets every resolved address right but only after a socket call, and it cannot tell a refusal from an unreachable host unless the exception carries a marker. The three are not alternatives.

### F5. OkHttp connects only to the addresses the `Dns` returned, with one lookup per new connection (measured + documented)

- A `Dns` that returned `[public]` on its first call and `[10.0.0.5]` on every later call: call 1 connected to the public address (1 lookup); call 2 to the same host reused the pooled connection with **no lookup** and no new connect; after `ConnectionPool.evictAll()` call 3 did a second lookup, and the guard refused it.
- With two public addresses where the first failed to connect, there was still exactly 1 lookup and the second address was tried. Lookup and connect list are the same object, so there is no check-then-resolve window of the kind OWASP warns about ("A second, unchecked DNS lookup between validation and connection can bypass these checks").
- With a filtering `Dns` over `[10.0.0.5, public, 127.0.0.1]`, OkHttp connected only to the public address.
- Connection coalescing (HTTP/2) to a different host requires the new host's own `Dns` answer to contain the already-connected address (`RealConnection.isEligible`: "The routes must share an IP address."). The new host's answer passes through the guard first, and the connection's address was admitted when it was made, so a hop to a new name cannot ride an old connection to a denied address. Not exercised over real TLS (see Known gaps).
- A guarding `Dns` overrides `lookup` only. OkHttp 5.5.0's default `Dns.newCall` delegates to `lookup`; the record-based `Dns.newCall` route exists in the same `RouteSelector` and a future change there is the reason for layer C.

### F6. Proxies bypass A's address half, B, and (for SOCKS) C; `NO_PROXY` closes all of it (measured + documented)

| Setup | `Dns` called for the target | Socket factory saw | The proxy saw |
|---|---|---|---|
| explicit HTTP proxy, `http://internal.example/secret` | no | the proxy's address | `GET http://internal.example/secret HTTP/1.1` |
| explicit HTTP proxy, `https://internal.example/secret` | no | the proxy's address | `CONNECT internal.example:443 HTTP/1.1` |
| explicit SOCKS proxy (closed loopback port) | no | **nothing** (OkHttp builds `Socket(proxy)` itself) | `EventListener.connectStart` got an unresolved `internal.example` |
| default client + JVM property `http.proxyHost` | no | the proxy's address | the absolute-form request |
| `proxy(Proxy.NO_PROXY)` + the same property | **yes** | the target's address | n/a |
| `proxySelector` returning `NO_PROXY` + the same property | yes | the target's address | n/a |

- OkHttp: `proxy(...)` "takes precedence over [proxySelector], which is only honored when this proxy is null (which it is by default). To disable proxy use completely, call `proxy(Proxy.NO_PROXY)`."
- The JDK default selector goes direct only for `localhost|127.*|[::1]` (`http.nonProxyHosts` default). `10.0.0.5` and every internal name go to the proxy. A configured `http.proxyHost` on the deployment host would therefore have turned an `Untrusted` fetch of `http://10.0.0.5/` into a request from the proxy's network position, with no `Dns` or socket-factory involvement. Mock servers on loopback bypass such a proxy, so tests are unaffected.
- The proxy resolves names itself, so "resolve and check" cannot be done by the client. Advocate's README says so about its own approach: "it's definitely preferable to proxy through a host can't access anything internal in the first place!" A deployment that must route through a mandatory egress proxy needs that proxy to be the guard (Smokescreen is such a proxy), and `Untrusted` cannot be offered there at 1.0.0.

### F7. The deny table follows the IANA registries, and `InetAddress` predicates miss most of it (measured + documented)

Both registries read 2026-10-21, both "Last Updated 2025-10-09". Every IPv4 row and every IPv6 row (25 each) was fed to the classifier by its first and last address, and all are denied. For the two IPv6 rows that embed IPv4 (`::ffff:0:0/96`, `64:ff9b::/96`) that holds only because their first and last embedded addresses are `0.0.0.0` and `255.255.255.255`; their interior is judged by the IPv4 table (`64:ff9b::808:808` and `::ffff:8.8.8.8` stay allowed). 25 public control addresses stay allowed, which checks the range boundaries: `1.1.1.1`, `172.15.255.255`, `172.32.0.0`, `100.63.255.255`, `100.128.0.0`, `198.17.255.255`, `198.20.0.0`, `169.253.255.255`, `169.255.0.0`, `192.167.255.255`, `192.169.0.0`, `2606:4700:4700::1111`, `2001:4860:4860::8888`, `2a00:1450:4001::1`, `2001:200::1` and others.

`java.net.InetAddress` predicates (`isLoopbackAddress || isAnyLocalAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress`), identical on JDK 11, 17, 21, 23, against 26 non-public addresses:

- **Caught (10):** `127.0.0.1`, `0.0.0.0`, `10.0.0.1`, `169.254.169.254`, `224.0.0.1`, `::1`, `::`, `fe80::1`, `fec0::1`, `ff02::1`.
- **Missed (16):** `100.64.0.1` (no CGNAT predicate), `192.0.0.1`, `198.18.0.1`, `192.0.2.1`, `240.0.0.1`, `255.255.255.255`, `fd00::1` and `fc00::1` (IPv6 `isSiteLocalAddress` is the deprecated `fec0::/10` only), `::7f00:1` (IPv4-compatible), `64:ff9b::7f00:1` (NAT64), `2002:7f00:1::` (6to4), `2001:db8::1`, `100::1`, `::ffff:0:127.0.0.1` (IPv4-translated), `2001::1`, `5f00::1`.
- `InetAddress.getByAddress` and `getByName` collapse a 16-byte IPv4-mapped address to an `Inet4Address`; the predicates then work on the IPv4 side. Every other embedding stays IPv6.

The table is in the Recommendation. The tier choices behind it:

- **IPv4 deny list, whole registry plus multicast.** The registry's "globally reachable" rows (PCP/TURN anycast, AS112, AMT) are denied too: they are anycast service prefixes, not hosts that serve a feed, so denying them costs nothing and keeps the rule "no special-purpose block".
- **IPv6 default-deny.** Only `2000::/3` (the block IANA allocates global unicast from) is eligible, minus `2001::/23`, `2001:db8::/32`, `2002::/16` and `3fff::/20` inside it. Everything else (`::/8`, `64:ff9b:1::/48`, `100::/64`, `5f00::/16`, `fc00::/7`, `fe80::/10`, `fec0::/10`, `ff00::/8`) is denied by not being in `2000::/3`, with no entry that can be forgotten. This is the OWASP preference stated at the end of its deny-list section ("Deny-lists are bypass-prone. Prefer allow-lists."), applied where it is cheap.
- **Embedding.** IPv4-mapped and the NAT64 well-known prefix `64:ff9b::/96` are judged by the IPv4 address in the low 32 bits, so a DNS64/NAT64 network that synthesises `64:ff9b::<public IPv4>` keeps working. 6to4 (`2002::/16`, deprecated by RFC 7526) and Teredo (`2001::/32`) are denied wholesale: no legitimate public feed host lives there, and extracting their embedded addresses buys nothing. The NAT64 local-use prefix `64:ff9b:1::/48` is denied wholesale (outside `2000::/3`).
- **Alternatives seen in the wild:** Smokescreen denies all NAT64, 6to4 and Teredo space wholesale (which would also refuse DNS64-synthesised addresses of public hosts); `ssrf_filter` and the Kotlin `ssrf-guard` unwrap and judge the embedded IPv4 address. Both are safe; the NAT64 well-known prefix is the one place the choice changes availability.

### F8. The classifier fits in common code and is portable (measured)

`AddressClassifier` (bytes -> verdict), `StrictIp` (strict text -> bytes) and `HostClassifier` (host string -> literal address / refusal) use only `ByteArray`, `Int`, `String.split/lowercase`, `List`, `Char` ranges. A probe project compiled them with `kotlin("multiplatform")` 2.2.0 for `jvm()` and `js(IR) { nodejs() }` and ran the same `commonTest` on both: 4 tests, 0 failures on each target. Contents: non-canonical IPv4 refusals, address verdicts by bytes, zone/garbage refusal, and 25 bypass vectors (the advisory-listed ones plus common variants, F10).

A differential fuzz of `StrictIp` against the JDK's own literal parsers (`IPAddressUtil.textToNumericFormatV4/V6`, 2.7 million generated strings including mutations):

- Strings `StrictIp` accepts: **0** that the JDK rejects (1 327 146 accepted by both), and none whose bytes differ except the IPv4-mapped collapse (the JDK returns 4 bytes, `StrictIp` returns 16 with the same IPv4 tail).
- Strings only the JDK accepts: 377 216. The twelve examples printed are all non-canonical IPv4 forms (leading zeros such as `0176.2.06.0`, single numbers such as `206541348`, short forms); I did not break out IPv6 cases. These are the forms the host rule refuses.

So the strict parser is a subset of what the JDK accepts: whatever the strict parser classifies, the JDK would connect to that same address.

### F9. Redirects: a manual per-hop loop plus layers B and C (measured)

- With OkHttp's own follow-up, a `302` to `http://169.254.169.254/` and a `302` to `http://2130706433:P/` both reached the target through a guarding `Dns` (F4); only a guarding socket factory stopped them, after the redirect request was built.
- With the manual loop, the per-hop check refused hop 1 before a `Call` existed: the tunnel recorded only the first connect.
- Redirect to a name that resolves privately: refused by B under both strategies.
- Downgrade (`https` -> `http`), the cap, and cross-origin header stripping are the transport's (`http-transport.md` F4) and are unchanged.

### F10. Published bypasses all come from the same three mistakes (documented consensus)

| Advisory | What went wrong | Test vector for this design |
|---|---|---|
| node `ip` `isPublic` CVE-2023-42282, then CVE-2024-29415 ("incomplete fix") | uncommon forms reported public: `0x7F.1`, `127.1`, `01200034567`, `012.1.2.3`, `000:0:0000::01`, `::fFFf:127.0.0.1` | all in `PublishedBypassVectorsTest`, all refused or denied |
| `private-ip` CVE-2020-28360 | "Insufficient RegEx ... insufficiently filters reserved IP ranges" | the IANA row-by-row boundary test |
| `netmask` CVE-2021-28918 (fix incomplete: CVE-2021-29418) | "Improper input validation of octal strings" | `0177.0.0.1`, `017700000001`, `012.1.2.3` |
| Python `ipaddress` CVE-2021-29921 | "IP address octects are left stripped instead of evaluated as valid IP addresses" (`0127.0.0.1`) | `0127.0.0.1`, `127.000.000.001`, `127.0.0.01` |
| `request` CVE-2023-28155 | "bypass of SSRF mitigations via an attacker-controller server that does a cross-protocol redirect (HTTP to HTTPS, or HTTPS to HTTP)" | per-hop check plus downgrade refusal, Level 3 |
| Orange Tsai, URL-parser confusion (Black Hat USA 2017) | validator and requester parse the same string to different hosts | F3 table, differential test |

The three mistakes: judging a text form instead of the bytes a connect would use (octal/hex/short/decimal, mapped and embedded IPv6); a hand-written range list that omits entries (the `private-ip` regex); and a validator whose parser differs from the requester's.

### F11. Mixed answers: the mature guards split (observed practice)

- **Drop denied addresses and connect to an allowed one**, failing only when none is left: Smokescreen, Advocate, `ssrf_filter` (read in code).
- **Vet each connect attempt** with a dialer `Control` hook: `safeurl`. (The Go dialer then moves to the next resolved address; I did not read the Go standard library to confirm.)
- **Reject the whole answer when any entry is denied:** `request-filtering-agent` (its `lookup` wrapper, for the `all` option) and the Kotlin `ssrf-guard` (0.1.0, single source): "A name that resolves to both a public and a private address is rejected, not partially allowed".
- In all of them the connection goes only to a validated address, so both strategies are safe.

Measured here: with a reject-any `Dns`, layer C never fires in normal operation, so a C refusal is an unambiguous "something bypassed B" signal. With dropping, a filtered answer is what OkHttp connects to (F5). One ambiguity measured when C ran without B: with `[10.0.0.5, public]` where the public connect then failed, the exception at the top of the chain was the policy refusal with the `ConnectException` suppressed, so a mapping that treats any marker in the chain as a refusal reports a refusal for a host that was merely unreachable. Reject-any in B removes that case.

### F12. Policy parameters in comparable guards are data lists, not callbacks (observed practice)

- Smokescreen: `DenyRanges` and `AllowRanges` (`RuleRange`), an ACL file.
- Advocate: `ip_whitelist`/`ip_blacklist`, hostname patterns, a port whitelist.
- `safeurl`: `AllowedIPs`/`BlockedIPs` and `AllowedPorts`/`AllowedSchemes`.
- `request-filtering-agent`: `allowIPAddressList`/`denyIPAddressList`.
- `ssrf_filter`: `scheme_whitelist`, a `resolver` proc, redirect and header options.

None exposes the address verdict as a consumer-implemented predicate. All of them expose loosening (allow lists, `allowPrivateIPAddress`, `UnsafeAllowPrivateRanges`). For `webtools-http` the loosening use cases (a self-hosted API at a private address) are what `Trusted` is for, so `Untrusted` needs no loosening parameter.

### F13. Reporting a refusal: coarse reason, no address, no query (documented + reasoning)

- The submitter of an `Untrusted` URL is the party the guard distrusts. A failure that says "resolved to 10.0.0.5" or "range: link-local" turns the bot into a probe of its own network (which internal names exist, what they point at). Even the choice between a "denied address" and a "no such host" reason reveals that a name resolves to internal space.
- The library therefore returns a typed reason with no address, no range and no resolved name in the failure or its message, and the consumer chooses how much of the reason reaches the user (BotTools can collapse every reason into "that URL is not allowed").
- Logs: a WARN with the method, `Url.redacted()` (no query), the reason and the matched range **class** (for example `link-local`), never the resolved address, because logs are shipped and read by more people than the bot's administrators. The address may be logged by a DEBUG line that is off by default, only if the planner wants it; this report recommends omitting it.
- `Url.parse` already rejects user-info and never echoes its input. A redirect target carrying user-info is refused with the reason `UserInfo` and logged as `redacted()`.

### F14. How refusals surface through OkHttp (measured)

- A `Dns` that throws a library `UnknownHostException` subclass surfaces as the exception at the top of the failure chain (no wrapping), on JDK 11, 21 and 23.
- A `SocketFactory` socket whose `connect` throws a library `IOException` subclass surfaces at the top, with any earlier or later per-address failures in `suppressed`.
- With three denied addresses and only layer C, OkHttp tried all three (IPv6 first, `fd00::1`, then `10.0.0.5`, `10.0.0.6`) and failed with the first refusal plus two suppressed refusals.

## How comparable projects do it

| Project (ref) | Where the check runs | What it does | Notes |
|---|---|---|---|
| **Smokescreen** (Stripe, Go egress proxy; commit `bc3bf31`, 2026-10-16) | In the proxy's own dial: `safeResolve` resolves, `selectTargetAddr` picks the first allowed address and dials that IP | `defaultIPClassification`: embedding (NAT64, 6to4, Teredo) denied wholesale, `IsPrivate`, CGNAT `100.64.0.0/10`; base test is Go's `IsGlobalUnicast` and not loopback; `DenyRanges`/`AllowRanges` as data; `UnsafeAllowPrivateRanges` | Filter semantics ("If no IP passes validation, return denyError"). A proxy, so the client cannot bypass it with its own resolution. |
| **Advocate** (Python; commit `f65cbf9`, last commit 2023-08-31) | `validating_create_connection`: every `getaddrinfo` result is validated and the socket connects to the validated, exploded address | IPv6 off by default, `DEFAULT_PORT_WHITELIST = {80, 8080, 443, 8443, 8000}`, v4 nested in mapped/6to4/Teredo/DNS64 checked, `192.88.99.0/24` denied | Filter semantics (`continue` on a denied result). README recommends an isolated Squid proxy as the stronger setup. Unmaintained since 2023. |
| **ssrf_filter** (Ruby 1.6.0; commit `9860723`, 2026-10-13) | Manual redirect loop; per hop resolve, drop unsafe addresses, raise `PrivateIPAddress` if none left, then `Net::HTTP.start(..., ipaddr: ip)` pins the validated address (it passes `nil` for the proxy argument; I did not verify in Ruby's documentation that this disables proxies) | IPv4 deny list ~ the IANA registry; IPv6 list plus every IPv4 deny range re-expressed as compatible/mapped/translated/NAT64 forms; RFC 6052 decode for `64:ff9b:1::/48`; strips `authorization`/`cookie` cross-origin, `max_redirects` 10 | The closest design to this one (per-hop, resolve once, pin). Deny-list approach for IPv6. |
| **safeurl** (Doyensec, Go; commit `bfe6b43`, 2026-06-11) | `net.Dialer.Control` (connect time, every attempt) plus scheme/credential validators | IPv6 disabled by default (`isIPv6Enabled` false), `AllowedPorts`, allow/block lists; deny list almost line-for-line the IANA rows incl. `64:ff9b::/96` wholesale | Connect-time check on the actual socket address, the Go equivalent of layer C. |
| **request-filtering-agent** (Node, 3.2.1; commit `29dd910`, 2026-09-30) | `createConnection` override: literal host checked directly ("Direct ip address request without dns-lookup"), plus a filtering `lookup` | allow/deny lists; "It will prevent DNS rebinding" | Same literal-plus-lookup split as A plus B. |
| **ssrf-guard** (Kotlin/JVM 0.1.0, `Andreydemo`, commit `45675ef`, 2026-09-10; 0 stars) | Four layers: strict URL parse, own resolve, `PublicOnlyDns` for OkHttp, check at save and at send time | Reject-any on mixed answers; unwraps mapped/NAT64/6to4/compatible and judges the IPv4; reads leading zeros as decimal "matching the JDK"; HTTPS and default port only | Single source and very young. Independently states: "OkHttp never calls its `Dns` hook for a literal IP address". |
| **alibaba/seckit** (Java; commit `29cef38`, 2025-10-15) | Adds a network interceptor to the OkHttp builder that checks the URL and calls `InetAddress.getByName(host)`, plus a thread-level "net hook" | not evaluated beyond the OkHttp decorator | The interceptor sits after the connect (F4 of `http-transport.md`) and re-resolves the host; the net-hook half was not read. No maintained Java or Kotlin guard of the Smokescreen/`ssrf_filter` quality was found. |
| **OWASP SSRF cheat sheet** (no revision date on the page) | "connects only to validated addresses"; "Apply the destination policy to retries and fallback connections as well"; redirects off; parser disagreement is rejection | `Deny-list (Last Resort)` minimum ranges; IANA registries as sources | The behaviour this design implements as A+B+C. |

## Pitfalls

1. **Resolver-only guard.** Every IP-literal form reaches the socket (F1, F4). The Kotlin `ssrf-guard` README states the same.
2. **Network interceptor or `EventListener` as the guard.** Network interceptors run after the TCP connect and `EventListener` cannot veto; a second `InetAddress.getByName` inside either re-resolves.
3. **Checking the caller's string.** Fullwidth digits, percent-encoded bytes, ideographic full stops, backslashes and `127.0.0.1` inside user-info all read differently in other parsers (F2, F3). Check the `HttpUrl` OkHttp will connect to.
4. **Hand-written ranges.** `isSiteLocalAddress` on `fd00::1` is false; CGNAT has no predicate (F7). Derive the table from the registries and test every row's first and last address.
5. **Judging text instead of bytes.** Octal/decimal ambiguity (`0177.0.0.1`: JDK 177.0.0.1, libc 127.0.0.1) is the Python/`netmask`/`ip` bug class. Refuse non-canonical forms.
6. **Embedded IPv4.** `::ffff:`, `64:ff9b::`, `2002::`, `::a.b.c.d`, `::ffff:0:a.b.c.d` and Teredo; missing any is a bypass (`ssrf_filter` re-expresses its IPv4 list in four embedded forms; this design default-denies IPv6 instead).
7. **Following redirects natively with a resolver-only guard** (F4, F9).
8. **A proxy** (F6): no `Dns`, and SOCKS also skips the socket factory. `proxy(NO_PROXY)` must be explicit; `proxySelector` alone also works but the system property route is easy to miss.
9. **The loopback test seam leaking.** A public `allowLoopback` or `addressRules` parameter on the main artifact lets a consumer switch the guard off; Smokescreen, Advocate, `safeurl` and `request-filtering-agent` all ship such knobs, and a library that sells `Untrusted` as a guarantee should not.
10. **Ports.** Without a port rule an administrator-supplied URL turns the bot into a port scanner of third-party hosts (public addresses only, since internal ones are denied), and the failure kind tells open from closed. Advocate defaults to a five-port whitelist.
11. **Reflecting the denied address or range to the submitter** (F13).
12. **IANA drift.** The registry changed in 2024 and 2025 (`3fff::/20`, `5f00::/16`, `100:0:0:1::/64`). The table is a dated snapshot; the default-deny IPv6 rule is what keeps new special blocks outside `2000::/3` denied.
13. **Marker in the exception chain for an unreachable allowed host** (F11).
14. **Tailscale and cloud overlay addresses** in `100.64.0.0/10` are denied by the CGNAT rule; a bot on a tailnet cannot fetch tailnet-only feeds through `Untrusted`, by design.

## Recommendation for this repository

Implement the policy as a closed pair with two enforcement layers on the JVM and a pure rule core in `commonMain`, and keep the test seam internal.

### Where each check runs

| Layer | Runs | Job | Does not do |
|---|---|---|---|
| **A. per-hop URL check** (`UrlGuard.checkHop`, called by the transport's redirect loop before the `Call` for every hop, including the first) | on the `HttpUrl` OkHttp will use; no network | scheme in `{http, https}`; no user-info; host form; IP literals (strict dotted-decimal only, IPv6 parsed strictly, zone refused) classified by bytes; reserved names; port rule; `Trusted` host pinning; downgrade | resolve names |
| **B. `GuardedDns`** (`okhttp3.Dns`, wraps `Dns.SYSTEM` or the fixtures' stub) | at the one lookup per new connection | judge every returned address by bytes; reject the whole answer if any is denied (default) | see IP literals |
| **C. `GuardedSocketFactory`** (`javax.net.SocketFactory`) | on every `Socket.connect(InetSocketAddress, timeout)`, including fallback attempts | refuse an unresolved target and any denied address; backstop for A and B and for newer OkHttp internals | replace A: it fires only when a connect is attempted, after the request is built, and not at all through a SOCKS proxy (OkHttp builds `Socket(proxy)` itself) |
| **D. proxy** | at client build | `Untrusted`: `proxy(Proxy.NO_PROXY)` | |

Cancellation is untouched: no check suspends, and the refusals are values returned through `Result`.

### Common types (`commonMain`, package `com.spartanlabs.webtools.http`; names provisional)

```kotlin
public sealed class OutboundUrlPolicy {
    public abstract val settings: TransportSettings          // caps and timeouts that belong to this policy

    public class Trusted(
        public val pinnedHosts: Set<String> = emptySet(),    // empty = any host; non-empty = every hop must match
        public val allowCleartext: Boolean = false,          // false = https only
        override val settings: TransportSettings = TransportSettings.forTrusted(),
    ) : OutboundUrlPolicy()

    public class Untrusted(
        public val allowedPorts: Set<Int>? = DefaultUntrustedPorts,   // null = any port
        public val extraDeniedRanges: List<CidrBlock> = emptyList(),  // tighten only
        public val extraDeniedHostSuffixes: List<String> = emptyList(),
        public val mixedAnswers: MixedAnswers = MixedAnswers.Reject,
        override val settings: TransportSettings = TransportSettings.forUntrusted(),
    ) : OutboundUrlPolicy()
}

public enum class MixedAnswers { Reject, DropDenied }

public class CidrBlock private constructor(/* bytes, prefix */) {
    public companion object { public fun parse(text: String): Result<CidrBlock> }
}

// carried by TransportFailure.RefusedByPolicy(reason)
public enum class RefusalReason {
    Scheme, UserInfo, HostForm, ReservedName, DeniedAddress, Port,
    HostNotPinned, InsecureRedirect, SensitiveHeader,
}
```

`TransportSettings.forUntrusted()` holds the numbers already proposed in `http-transport.md` (call timeout 15 s, connect/read/write 5 s, 2 MiB, 3 redirects, 8/2 concurrent); `forTrusted()` the Trusted column. `DefaultUntrustedPorts` is `{80, 443, 8080, 8443}` (judgement; Consideration 3). `RefusalReason` is documented as "may gain entries in a feature release; handle unknown values", the failure-model research owns whether it is an enum or a constants class.

Rules that belong to the types:

- There is no parameter that lowers `Untrusted`'s protection. Parameters only add denials. "Fetch from a private address" is `Trusted`.
- `Untrusted` refuses (reason `SensitiveHeader`) a request carrying a header flagged sensitive or named `Authorization`, `Proxy-Authorization` or `Cookie`: a credential on a request to an administrator-chosen host is a leak. (An addition beyond the stated `Untrusted` behaviour; Consideration 4.)
- `Trusted` runs A's scheme, user-info and (if set) pinning rules and the downgrade rule, and no address filtering, no name rules and no port rule. A redirect that leaves `pinnedHosts` is refused with `HostNotPinned`; without pins, cross-origin redirects are followed and the transport's sensitive-header stripping applies. A facade that knows its endpoint's base URL passes that host as the pin.

### Internal core (`commonMain`, `internal`; Level 4a testable)

```kotlin
internal object AddressRanges { fun deny(address: ByteArray): DeniedRange? }   // 4 or 16 bytes; null = allowed
internal object StrictIp { fun parseV4(text: String): ByteArray?; fun parseV6(text: String): ByteArray? }
internal object HostRules { fun classify(canonicalAsciiHost: String): HostVerdict }
internal class UrlGuard(policy: OutboundUrlPolicy, relaxation: GuardRelaxation = GuardRelaxation.None) {
    fun checkHop(scheme: String, host: String, port: Int, hasUserInfo: Boolean, previousScheme: String?): RefusalReason?
    fun checkAddress(address: ByteArray): RefusalReason?
}
internal class GuardRelaxation private constructor(val allowLoopback: Boolean) {
    companion object { val None: GuardRelaxation; val LoopbackForTests: GuardRelaxation }
}
```

`DeniedRange` carries the matched class (`"link-local"`) for logging only. The probe version of these classes (about 250 lines of Kotlin) compiled for JVM and JS and passed its tests.

### JVM enforcement (`jvmMain`, `internal`)

```kotlin
internal fun okhttp3.HttpUrl.hopFacts(): HopFacts                     // scheme, host, port, hasUserInfo
internal class GuardedDns(delegate: okhttp3.Dns, guard: UrlGuard) : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> // overrides lookup only
}
internal class GuardedSocketFactory(delegate: javax.net.SocketFactory, guard: UrlGuard) : javax.net.SocketFactory()
internal class PolicyRefusedUnknownHost(val reason: RefusalReason) : java.net.UnknownHostException()   // message: constant text
internal class PolicyRefusedIo(val reason: RefusalReason) : java.io.IOException()                      // message: constant text
```

Wiring in `OkHttpTransport` for `Untrusted`: `proxy(Proxy.NO_PROXY)`, `dns(GuardedDns(Dns.SYSTEM, guard))`, `socketFactory(GuardedSocketFactory(SocketFactory.getDefault(), guard))`; for `Trusted`: default proxy selection, system `Dns`, no guards beyond A. The failure mapper checks first for the marker exceptions at the top of the cause chain and in `suppressed`, so `RefusedByPolicy(reason)` wins over `DnsFailed` and `Io`.

The `GuardedSocketFactory` returns a `Socket` subclass that overrides `connect(SocketAddress, int)` and calls the check before `super.connect`. (The probe's subclass of `Socket` worked on JDK 11, 17, 21 and 23. `OkHttpClient.Builder.socketFactory` requires that it not be an `SSLSocketFactory`.)

The `internal constructor` already planned for `OkHttpTransport` (`http-transport.md:264`) takes a `UrlGuard`/`GuardRelaxation` and a `Dns` instead of a bare `HopCheck`. Everything else in that constructor stays.

### The test seam

- The main artifact exposes no loosening: there is no public flag, no public relaxation type, and `GuardRelaxation.LoopbackForTests` is `internal`.
- The test-fixtures variant (which sees `internal` declarations) offers a factory such as `untrustedForLoopbackTests(userAgent, settings, dns = null)` that builds the same `Untrusted` guard with `GuardRelaxation.LoopbackForTests`. The relaxation exempts exactly: the hosts `localhost`, `127.0.0.1` and `[::1]`; the loopback addresses `127.0.0.0/8` and `::1`; and the port rule for those hosts. Every other rule stays live.
- What this lets Level 3 do against `mockwebserver3`:
  - Fetch through `Untrusted` including caps, conditional GET and short timeouts.
  - Follow a redirect from the loopback server and see hop 2 refused before any socket when it points at `169.254.169.254`, `10.0.0.5` or `http://2130706433/` (the second server's `requestCount` is 0).
  - Run rebinding with the factory's `Dns` stub (lookup 1 -> loopback, lookup 2 -> `10.0.0.5`) after `ConnectionPool` eviction.
- The unrelaxed guard is tested in the other direction: `server.url("/")` through the production `Untrusted` must be refused with `server.requestCount == 0` (the acceptance check BotTools lists).
- Level 2 needs no network: `GuardedDns` over a stub `Dns`, and `GuardedSocketFactory` over a delegate whose sockets only record the endpoint.

### Tests this implies

- **4a (`commonTest`, runs on JVM and JS):** every IANA row's first and last address, the allowed controls, `StrictIp` accept/reject tables, the host-form catalogue (F1), the published vectors (F10), reserved names.
- **4a (`jvmTest`):** differential `StrictIp` vs `InetAddress` on a generated corpus; `Url.parse` vs `HttpUrl` on the F3 corpus; JDK-predicate-gap table.
- **2:** layers B and C against stubs; reject-any versus `DropDenied`; markers in `suppressed`.
- **3:** the catalogue of F4 through the real transport with the fixtures factory; redirect hop into each private form; rebinding; `NO_PROXY` with `http.proxyHost` set; a `shared` donor client that carries a proxy, a `Dns` or a `SocketFactory` is overridden by the policy's own (the transport re-applies them after `newBuilder()`).
- **4c:** worst-case memory and concurrency stay in the transport research.

### Deny table (IPv4; all rows deny; source: IANA IPv4 Special-Purpose Address Registry, last updated 2025-10-09, plus the multicast block)

| Block | Name | Verdict and reason |
|---|---|---|
| `0.0.0.0/8` (incl. `0.0.0.0/32`) | "This network" | deny: reaches the local host on Linux and Windows connects |
| `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` | Private-Use (RFC 1918) | deny |
| `100.64.0.0/10` | Shared address space (CGNAT) | deny: carrier and overlay networks (no `InetAddress` predicate) |
| `127.0.0.0/8` | Loopback | deny |
| `169.254.0.0/16` | Link-local (cloud metadata `169.254.169.254`) | deny |
| `192.0.0.0/24` | IETF protocol assignments (incl. DS-Lite `192.0.0.0/29`, `192.0.0.8`, PCP `.9`, TURN `.10`, NAT64 discovery `.170/.171`) | deny: protocol anycast and service addresses, not content hosts |
| `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` | Documentation | deny |
| `192.31.196.0/24`, `192.175.48.0/24` | AS112 | deny: globally reachable anycast DNS sinks, not content hosts |
| `192.52.193.0/24` | AMT | deny: same |
| `192.88.99.0/24` (incl. `.2`) | 6to4 relay anycast (deprecated) | deny |
| `198.18.0.0/15` | Benchmarking | deny |
| `224.0.0.0/4` | Multicast (not in the special-purpose registry) | deny |
| `240.0.0.0/4` (incl. `255.255.255.255`) | Reserved, limited broadcast | deny |

### Deny rules (IPv6; source: IANA IPv6 Special-Purpose Address Registry, last updated 2025-10-09)

| Rule | Covers | Verdict |
|---|---|---|
| `::ffff:0:0/96` | IPv4-mapped | judge the low 32 bits by the IPv4 table |
| `64:ff9b::/96` | NAT64 well-known | judge the low 32 bits by the IPv4 table |
| not in `2000::/3` | `::/128`, `::1`, `::/96` compatible, `::ffff:0:0:0/96` translated, `64:ff9b:1::/48`, `100::/64`, `100:0:0:1::/64`, `5f00::/16`, `fc00::/7`, `fe80::/10`, `fec0::/10`, `ff00::/8`, and all unallocated space | deny (default) |
| `2001::/23` | IETF protocol assignments incl. Teredo `2001::/32`, anycast, benchmarking `2001:2::/48`, AMT, AS112-v6, ORCHID | deny |
| `2001:db8::/32`, `3fff::/20` | Documentation | deny |
| `2002::/16` | 6to4 | deny wholesale |
| `2620:4f:8000::/48` | Direct delegation AS112 | deny |
| everything else in `2000::/3` | | allow |

### Host rules (strings, after OkHttp canonicalisation)

1. Host must be ASCII with no controls; an empty host is refused.
2. Contains `:` -> strict IPv6 parse; failure (including a zone) -> `HostForm`; success -> address table.
3. Only digits and dots -> strict dotted-decimal (four parts, no leading zero, 0-255) -> address table; anything else (`127.1`, `2130706433`, `0177.0.0.1`, `127.0.0.1.`, `1.1`) -> `HostForm`.
4. Last label (after one trailing dot) all digits or `0x`+hex -> `HostForm` (the WHATWG "ends in a number" rule; also catches `0x7f.0.0.1`, which OkHttp sends to `Dns`).
5. Single-label names, and `localhost`, `local`, `internal`, `home.arpa`, `localdomain` (and their subdomains) plus `extraDeniedHostSuffixes` -> `ReservedName`. (`localhost` names are loopback by RFC 6761; `.local` is mDNS; `.internal` is reserved for private use; these rules are fail-fast and diagnostic, since B denies whatever they resolve to.)

### Rejected options

- **A guard in a network interceptor or `EventListener`:** post-connect, cannot veto (F4).
- **An open `UrlGuard` interface in 1.0.0:** see Consideration 1.
- **Filtering instead of rejecting by default:** see Consideration 2.
- **Reading leading zeros as decimal (the JDK) or octal (libc):** refused instead, so the choice does not matter.
- **Allow `Untrusted` through a proxy:** unverifiable (F6).
- **A public `allowLoopback`/`unsafe` parameter:** pitfall 9.

## Considerations

### 1. Is there a consumer-implemented guard seam at 1.0.0? (needs a call)

- **Options.** (a) None: tightening data parameters only. (b) An Experimental `UrlGuard` interface (consumer predicate over a hop's scheme/host/port and resolved addresses), behind the single opt-in marker. (c) (a) now, (b) added later without breaking.
- **Recommendation: (a), with (c) as the path.** No guard in the survey exposes a verdict callback (F12); BotTools' own `FeedUrlPolicy` runs in the bot at subscription time and at every poll and needs no hook in the transport; a callback that can return "allow" is the one extension that can silently break the `Untrusted` guarantee; and the org rule's Experimental tier exists for exactly the seam "whose shape has not been proven by a real consumer". Add (b) when a consumer asks, as an additive Experimental interface that can only add denials.

### 2. Mixed public/private answers: reject or drop (needs a call)

- **Options.** (a) `Reject` (default): any denied address refuses the lookup. (b) `DropDenied`: connect to the allowed addresses only.
- **Recommendation: (a) as default, (b) behind `MixedAnswers.DropDenied`.** Deterministic whatever the answer order, a loud signal for what is either a misconfigured or a hostile name, and it keeps C a pure backstop (F11). The cost is availability for hosts that publish a stray private record, and it is not the majority choice among the guards read (three filter, one vets each attempt, one rejects; F11). A feed administrator can pick another URL; flip the default only if real feeds trip it.

### 3. Default ports for `Untrusted` (needs a call)

- **Options.** (a) `{80, 443, 8080, 8443}`. (b) any port (`null`). (c) `{80, 443}`.
- **Recommendation: (a).** Advocate ships `{80, 8080, 443, 8443, 8000}`; `safeurl` and `ssrf_filter` default to any port. The risk with any port is third-party port scanning from the bot's address. The cost of (a) is that a self-hosted feed on `:8000` or `:3000` needs the administrator's operator to pass `allowedPorts`. Judgement, not evidence.

### 4. Refusing credentials on `Untrusted` requests (needs a call)

- **Options.** (a) Refuse a request carrying a sensitive-flagged or default-sensitive header with `SensitiveHeader`. (b) Send it and rely on cross-origin stripping.
- **Recommendation: (a).** Header stripping only protects redirects; the first hop of an `Untrusted` call already goes to a host the key's owner never chose. It is a one-line check in A with no cost to feeds (conditional GET uses `If-None-Match`/`If-Modified-Since`). `ssrf_filter` has the same idea as an opt-in `CredentialLeakage` error.

### 5. `Trusted` default for cleartext (needs a call)

- **Options.** (a) `https` only; `allowCleartext = true` to permit `http`. (b) `http` and `https`.
- **Recommendation: (a).** All current `Trusted` providers (OpenDota, Steam, Open-Meteo, Zippopotam) serve https, and credentials travel in headers, so cleartext should be a deliberate choice. The fixtures factory for `Trusted` against a plain `mockwebserver3` sets it.

## Known gaps

- **OS-resolver behaviour for hex/short names was not measured.** `0x7f000001` and `0x7f.0.0.1` go to `Dns.lookup`; glibc's `getaddrinfo` may read them as `127.0.0.1` while Windows may not. Running them through the system resolver would send a query for a nonsense name, so the probe used a stub resolver. The design does not depend on the answer: A refuses the forms and B/C judge whatever comes back.
- **HTTP/2 connection coalescing over real TLS was not exercised.** The claim in F5 is from reading `RealConnection.isEligible` at `parent-5.5.0` and measured pool reuse over HTTP/1.1.
- **Only OkHttp 5.5.0 was measured.** Layer C is the hedge for newer 5.x internals; it was not tested against a newer OkHttp.
- **No packet left the machine.** Connects were recorded by a socket factory that tunnelled to a local mock server. Real connects to public hosts, real DNS answers for the catalogue, and IPv6-only/NAT64 networks were not exercised. The first `forms` run sent a handful of numeric strings (for example `256.1.1.1`, `4294967296`) to the OS resolver through OkHttp's `InetAddress.getByName` fallback, because they were not pre-screened.
- **Windows host only**, JDK 11, 17 (literal parsing only), 21, 23 from the Gradle toolchain cache.
- **Port defaults, the reserved-suffix list (`local`, `internal`, `home.arpa`, `localdomain`) and the single-label rule are judgement.** The `.internal` reservation is from press coverage and an IANA notice about a proposal; I did not read the ICANN Board resolution.
- **Information-leak reasoning (F13) is argument, not a cited standard.** OWASP says nothing specific about error-message disclosure of resolved addresses on this page.
- **`alibaba/seckit` was only skimmed**; its thread-level "net hook" may do connect-time checking that I did not read.
- **No maintained Java/Kotlin equivalent** of the compared guards was found (one 0.1.0 Kotlin project with no stars, one Alibaba SDK). The search was `gh` repository search and web search, not an exhaustive code search.
- **Teredo and 6to4 wholesale denial** is a choice I did not test against any real deployment; a network that only offers 6to4 would be refused.
- **The probe's classifier is a prototype**, not the shipped implementation; the shipped table needs the Level 4a tests above and a dated comment for the registry snapshot, plus a maintenance note for refreshing it.
- **`RefusalReason` as an enum versus an open constants class** is left to the failure-model research.
- **The tests do not cover `Dns.newCall` record-based resolution** (the HTTPS/SVCB path); a guarding `Dns` that overrides only `lookup` relies on the default `newCall`.

## Load-bearing claims

All sources were opened on 2026-10-21. "OkHttp 5.5.0" means tag `parent-5.5.0` of https://github.com/square/okhttp.

1. **OkHttp does not call `Dns` for digits-and-dots or colon hosts; it hands them to `InetAddress.getByName`.** "`if (socketHost.canParseAsIpAddress()) {` / `return listOf(` / `Route(` / `address,` / `proxy,` / `InetSocketAddress(InetAddress.getByName(socketHost), socketPort),`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RouteSelector.kt>, OkHttp 5.5.0.
2. **The regex that decides it.** "`private val VERIFY_AS_IP_ADDRESS = "([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)".toRegex()`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HostnamesCommon.kt>, OkHttp 5.5.0.
3. **A SOCKS route is never resolved by the client.** "`if (proxy.type() == Proxy.Type.SOCKS) {` / `return listOf(` / `Route(` / `address,` / `proxy,` / `InetSocketAddress.createUnresolved(socketHost, socketPort),`". Same `RouteSelector.kt`, OkHttp 5.5.0.
4. **OkHttp canonicalises IPv4-mapped IPv6 hosts to IPv4 and maps IDNA.** "`if (address.size == 4) return inet4AddressToAscii(address) // An IPv4-mapped IPv6 address.`" and "`if (!IDNA_MAPPING_TABLE.map(codePoint, bufferB)) return null`". Same `-HostnamesCommon.kt`, OkHttp 5.5.0.
5. **An explicit `proxy(NO_PROXY)` overrides the selector and disables proxies.** "This takes precedence over [proxySelector], which is only honored when this proxy is null (which it is by default). To disable proxy use completely, call `proxy(Proxy.NO_PROXY)`." <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt>, `OkHttpClient.Builder.proxy`, OkHttp 5.5.0.
6. **HTTP/2 coalescing requires a shared route address, and never happens through a proxy.** "// 2. The routes must share an IP address." and "We can't coalesce connections that use a proxy, since proxies don't tell us the origin server's IP address." <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealConnection.kt>, `isEligible`/`routeMatchesAny`, OkHttp 5.5.0.
7. **`Dns` is the lookup used for establishing connections.** "Loads IP addresses and service metadata for a hostname. This interface is exclusively concerned with collecting the information necessary to establish new HTTP and HTTPS connections." <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dns.kt>, OkHttp 5.5.0.
8. **The JDK's default proxy exclusion list.** "http.nonProxyHosts (default: localhost|127.*|[::1])". <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/doc-files/net-properties.html>, Java SE 21.
9. **The JDK parses short and single-number IPv4 text.** "d.d.d.d / d.d.d / d.d / d" and "When only one part is given, the value is stored directly in the network address without any byte rearrangement." <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Inet4Address.html>, Java SE 21.
10. **The IANA registries and their date.** "Last Updated 2025-10-09" on both <https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry.xhtml> and <https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry.xhtml>; the IPv6 `2001::/23` row's footnote reads "Unless allowed by a more specific allocation."; CSV rows used for the table: <https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry-1.csv>, <https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry-1.csv>.
11. **OWASP: connect only to validated addresses, retries and fallback included, no second lookup.** "Domain allowlisting alone does not prevent DNS rebinding. Validate the resolved destination IP addresses against the application's permitted networks, then ensure the HTTP client connects only to validated addresses. A second, unchecked DNS lookup between validation and connection can bypass these checks" and "Apply the destination policy to retries and fallback connections as well." <https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html> (no revision date on the page).
12. **OWASP: parser disagreement, redirects, deny-lists.** "Treat parser disagreement as a rejection." / "Disable the support for the following of the redirection in your web client" / "Deny-lists are bypass-prone. Prefer allow-lists." / "Do not accept complete URLs from the user because URL are difficult to validate and the parser can be abused depending on the technology used as showcased by the following talk of Orange Tsai." Same page.
13. **Orange Tsai's root cause.** "The root cause of the problem lies in the inconsistency of URL parsers and URL requesters." <https://infocondb.org/con/black-hat/black-hat-usa-2017/a-new-era-of-ssrf-exploiting-url-parser-in-trending-programming-languages>, Black Hat USA 2017.
14. **WHATWG: numeric-last-label host rule and canonical IPv4.** "The ends in a number checker takes an ASCII string input and then runs these steps." / "If last is not the empty string and contains only ASCII digits, then return true." / "A valid IPv4-address string must be four shortest possible strings of ASCII digits, representing a decimal number in the range 0 to 255, inclusive, separated from each other by U+002E (.)." <https://url.spec.whatwg.org/> (living standard, read 2026-10-21).
15. **RFC 6761: `localhost` names.** "Name resolution APIs and libraries SHOULD recognize localhost names as special and SHOULD always return the IP loopback address for address queries" and "Application software MAY recognize localhost names as special, or MAY pass them to name resolution APIs as they would for other domain names." <https://www.rfc-editor.org/rfc/rfc6761.txt>, section 6.3.
16. **Advisories.** CVE-2023-42282: "The `isPublic()` function in the NPM package `ip` doesn't correctly identify certain private IP addresses in uncommon formats such as `0x7F.1` as private." (GHSA-78xj-cgh5-2h22). CVE-2024-29415: "some IP addresses (such as 127.1, 01200034567, 012.1.2.3, 000:0:0000::01, and ::fFFf:127.0.0.1) are improperly categorized as globally routable via isPublic. NOTE: this issue exists because of an incomplete fix for CVE-2023-42282." (GHSA-2p57-rm9w-gvfp). CVE-2020-28360: "Insufficient RegEx in private-ip npm package v1.0.5 and below insufficiently filters reserved IP ranges" (GHSA-43ch-2h55-2vj7). CVE-2021-28918: "Improper input validation of octal strings in netmask npm package v1.0.6 and below" (GHSA-4c7m-wxvm-r7gc). CVE-2021-29921: "IP address octects are left stripped instead of evaluated as valid IP addresses." (GHSA-cxqv-r2cc-r9c9). CVE-2023-28155: "allow a bypass of SSRF mitigations via an attacker-controller server that does a cross-protocol redirect (HTTP to HTTPS, or HTTPS to HTTP)." (GHSA-p8p7-x288-28g6). All via `gh api /advisories?cve_id=...` (GitHub Advisory Database), read 2026-10-21.
17. **Smokescreen.** "Block IPv6 addresses that embed IPv4 addresses (NAT64, 6to4, Teredo, IPv4-mapped)" in `pkg/smokescreen/ip_policy.go`; "If no IP passes validation, return denyError with details about denials" in `pkg/smokescreen/smokescreen.go` (`selectTargetAddr`). <https://github.com/stripe/smokescreen/blob/bc3bf31dc7ebe28ae2582a1a06cb96bcb57db78a/pkg/smokescreen/ip_policy.go>, commit `bc3bf31` (2026-10-16).
18. **Advocate.** "`DEFAULT_PORT_WHITELIST = {80, 8080, 443, 8443, 8000}`" in `advocate/addrvalidator.py`; in `advocate/connection.py` `validating_create_connection` skips results where `validator.is_addrinfo_allowed(...)` is false (`continue`); README: "it's definitely preferable to proxy through a host can't access anything internal in the first place!" <https://github.com/JordanMilne/Advocate/blob/f65cbf925cacafded20bbb878776a543b6f2d21b/advocate/addrvalidator.py>, commit `f65cbf9` (2023-08-31).
19. **ssrf_filter.** "`public_addresses = ip_addresses.reject(&method(:unsafe_ip_address?))`" / "`raise PrivateIPAddress, "Hostname '#{hostname}' has no public ip addresses" if public_addresses.empty?`" / "`ipaddr: ip`"; README: "Attempts to guard against this are often implemented incorrectly, by blocking all ip addresses, not handling IPv6 or http redirects correctly, or having TOCTTOU bugs and other issues." <https://github.com/arkadiyt/ssrf_filter/blob/98607230cdc337d14132bfdeeba73d7b937790bc/lib/ssrf_filter/ssrf_filter.rb>, version 1.6.0, commit `9860723` (2026-10-13).
20. **safeurl.** "`Control:  buildRunFunc(wc),`" in `client.go`; README: "It also implements mitigation for DNS rebinding attacks."; `isIPv6Enabled: false` default in `config.go`. <https://github.com/doyensec/safeurl/blob/bfe6b43562f4f4787b124a821eeb4b2bd138f9d8/client.go>, commit `bfe6b43` (2026-06-11).
21. **request-filtering-agent.** "Direct ip address request without dns-lookup" (comment in `createConnection`) and README "It will prevent DNS rebinding". <https://github.com/azu/request-filtering-agent/blob/29dd9105a2d471ca2d933f10ce5d9ef0732904ca/src/request-filtering-agent.ts>, version 3.2.1, commit `29dd910` (2026-09-30).
22. **Kotlin `ssrf-guard` (single source).** "OkHttp never calls its `Dns` hook for a literal IP address, so this is not a substitute for the parse-time literal check" and "A name that resolves to both a public and a private address is rejected, not partially allowed". <https://github.com/Andreydemo/ssrf-guard/blob/45675ef45df2e38616d7c43aeaf15c554f6fb636/README.md>, v0.1.0, commit `45675ef` (2026-09-10).
23. **OkHttp builds the raw socket from the client's socket factory only for direct and HTTP-proxy routes; a SOCKS route gets `Socket(proxy)`.** "`Proxy.Type.DIRECT, Proxy.Type.HTTP -> route.address.socketFactory.createSocket()!!` / `else -> JavaNetSocket(route.proxy)`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/ConnectPlan.kt>, `connectSocket`, OkHttp 5.5.0.
24. **`OkHttpClient.Builder.socketFactory` refuses an `SSLSocketFactory`.** "`require(socketFactory !is SSLSocketFactory) { "socketFactory instanceof SSLSocketFactory" }`". `OkHttpClient.kt`, OkHttp 5.5.0 (URL as in claim 5).
25. **`kotlin("multiplatform")` 2.2.0 compiles the classifier for `jvm()` and `js(IR)`, and `kotlin-test` runs it on both.** Measured in a throwaway probe; no external source. (Listed so the verifier knows it is a measurement, not a citation.)
