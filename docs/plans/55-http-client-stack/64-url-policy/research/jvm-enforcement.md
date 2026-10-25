# JVM enforcement: hooking OkHttp 5.5.0 and the JDK so every resolved address and every connect attempt is judged

Researched 2026-10-25. "OkHttp 5.5.0" is tag `parent-5.5.0` of https://github.com/square/okhttp, read from a shallow clone
(`okhttp/src/commonJvmAndroid/kotlin/okhttp3/...`, the sources of the `okhttp-jvm` artifact). JDK sources were read at
`jdk-11.0.32-ga` (jdk11u), `jdk-17.0.21-ga` (jdk17u), `jdk-21.0.13-ga` (jdk21u) and `jdk-27-ga` (jdk, "current"),
`src/java.base/share/classes/...`. Nothing was built or run: every behavioural statement below is read from source and
named as such; the tests that would measure it are listed under Gaps.

## Question

How do the internal `jvmMain` guards (`GuardedDns`, `GuardedSocketFactory`, two marker exceptions) hook OkHttp 5.5.0 and the
JDK (Java 11 API only, build JDK 17 to 26) so that every resolved address and every connect attempt of an `Untrusted` fetch is
judged, with no proxy, and how are they tested without a network? An answer must fix: which members the types override, which
`Socket` the factory creates, what each overload and an unresolved address do, the per-policy bundle of `Dns`, `SocketFactory`
and `Proxy`, the oracle for the strict-literal differential, and the Level 2 and Level 3 tests.

## Bottom line

- **`GuardedDns` overrides `lookup` and must NOT forward `newCall`.** In 5.5.0 `okhttp3.Dns` has two more members, `newCall`
  (a default that returns `LookupDnsCall(…, this, request)`) and the nested `Call`/`Callback`/`Record` types for HTTPS/SVCB
  records. `RouteSelector` calls `address.dns.newCall(...)`; if the result is a `LookupDnsCall` it calls `address.dns.lookup(host)`
  on the client's `Dns` (our guard), otherwise it runs the call and uses the records it returns. A guard that delegates
  `newCall` (a Kotlin `: Dns by delegate`, or `override fun newCall(r) = delegate.newCall(r)`) is safe over `Dns.SYSTEM` (its
  `newCall` is again a `LookupDnsCall`) and unsafe over any record-capable `Dns` (`DnsOverHttps`, `AndroidDns`, new in 5.5.0).
  Inheriting the default `newCall` is correct for every delegate. Never return an empty list: OkHttp turns it into
  `UnknownHostException("<Dns.toString()> returned no addresses for <host>")`, a message with the host in it.
- **`GuardedSocketFactory` needs only `createSocket()`**; OkHttp's own KDoc says so, and the only call site is
  `ConnectPlan.connectSocket` (`route.address.socketFactory.createSocket()!!` for DIRECT and HTTP-proxy routes). The factory must
  override it (the `SocketFactory` base throws `SocketException("Unconnected sockets not implemented")`). The four abstract
  overloads are never called by OkHttp; implement them to refuse (fail closed). The socket it returns is its own
  `GuardedSocket : Socket(Proxy.NO_PROXY)` that overrides `connect(SocketAddress, Int)`. `connect(SocketAddress)` calls it
  (`connect(endpoint, 0)`) on JDK 11, 17, 21 and 27; OkHttp calls it through `Platform.connectSocket` → `socket.connect(address, timeout)`.
  An unresolved or non-`InetSocketAddress` target is refused before any I/O.
- **`proxy(Proxy.NO_PROXY)` alone does not keep a JVM-wide SOCKS setting away from an `Untrusted` connect.** For a DIRECT route
  OkHttp calls `SocketFactory.getDefault().createSocket()`, which is `new Socket()`; `Socket()` installs a `SocksSocketImpl` whose
  `connect` asks `ProxySelector.getDefault()` for a `socket://host:port` proxy and honours `socksProxyHost`. `Socket(Proxy.NO_PROXY)`
  installs the platform impl directly (`PlainSocketImpl` on 11, `NioSocketImpl` on 17+) and never consults the selector. So the
  guarded socket must extend `Socket(Proxy.NO_PROXY)`, and that makes the factory create its own sockets rather than wrap a delegate's.
  This is settled from source and unmeasured (earlier measurements covered `http.proxyHost` only); a Level 3 test settles it.
- **Surfacing, with fast fallback on (the default):** a `Dns.lookup` marker (an `UnknownHostException`) comes out of
  `RealRoutePlanner.plan()`, becomes a `FailedPlan`, and is rethrown as the first failure; a socket marker (an `IOException`
  that is not a `ConnectException`) is the first failure of `ConnectPlan.connectTcp`, with later attempts' failures in its
  `suppressed` list. `retryOnConnectionFailure(false)` does not stop the next address of the same answer being tried (that loop
  lives in the exchange finder and ignores the flag); it only disables whole-call retry and TLS-spec fallback. A marker that
  extended `ConnectException` would be wrapped in `ConnectException("Failed to connect to <address>")`, putting the address in a message.
- **The JDK is a usable no-DNS oracle only for text already known to be a valid literal.** `InetAddress.getByName(s)` parses
  without a lookup when `s` is a valid IPv4 dotted-decimal (short and leading-zero decimal forms included) or, bracketed, a valid
  IPv6 literal. Any other text that starts with a hex digit or `:` and is not bracketed, such as `256.1.1.1` or `1.2.3.4.5`, goes
  to the system resolver. The strict-parser differential therefore calls the JDK only on strings `StrictIp` accepts (IPv4 bare,
  IPv6 as `"[" + s + "]"`), asserts equal bytes (modulo the IPv4-mapped collapse) and that the result has no host name
  (`toString()` starts with `/`); the converse direction is checked for IPv6 only, where the bracket form can never reach the resolver.
  `InetAddress.ofLiteral` is `@since 22` and cannot be used under `-Xjdk-release=11`.

## Findings

### F1. `okhttp3.Dns` at 5.5.0 has three kinds of member (source, OkHttp 5.5.0, released 2026-08-16)

`Dns.kt`: `fun interface Dns` with

- `fun lookup(hostname: String): List<InetAddress>` (the single abstract method; `@Throws(UnknownHostException::class)`);
- `fun newCall(request: Request): Call = LookupDnsCall(TaskRunner.INSTANCE, this, request)` (default);
- nested `Call`, `Callback`, `Request`, `Record` (`Record.IpAddress`, `Record.ServiceMetadata` carrying ALPN ids, port, IP hints
  and `echConfigList`);
- `companion object { @JvmField val SYSTEM: Dns }` whose `lookup` is `Platform.get().systemDns.lookup(hostname)` and whose `newCall`
  is `Platform.get().systemDns.newCall(request)`. `Platform.systemDns` is `InetAddressDns` on the JVM, whose `lookup` is
  `InetAddress.getAllByName(hostname).toList()` and which inherits the default `newCall`.

Evidence tier: source of the pinned tag (established). The class doc adds: "Implementations of this interface must be safe for
concurrent use." The 5.5.0 changelog calls the record-based API new: "This release includes a major update to OkHttp's DNS API".

`RouteSelector.dnsLookup` (the only caller that resolves a target host):

```kotlin
val dnsRequest = Dns.Request(socketHost, dnsPort)
val result = when (val dnsCall = address.dns.newCall(dnsRequest)) {
    is LookupDnsCall -> { val inetAddresses = address.dns.lookup(socketHost); inetAddresses.map { Route(address, proxy, InetSocketAddress(it, socketPort)) } }
    else -> { val records = dnsCall.execute(); ... records.filterIsInstance<Dns.Record.IpAddress>().map { ... } }
}
if (result.isEmpty()) throw UnknownHostException("${address.dns} returned no addresses for $socketHost")
```

Consequences for a guard:

- **Which target resolutions skip `lookup`** (and so skip a guard that overrides only `lookup`): (a) a host matching digits-and-dots or
  colon/hex goes to `InetAddress.getByName(socketHost)` (`canParseAsIpAddress`; already established, F1 of the existing research);
  (b) a SOCKS route is never resolved by the client (`InetSocketAddress.createUnresolved`); (c) a `Dns` whose `newCall` returns
  something other than `LookupDnsCall` is consumed as records and `lookup` is never called. (c) is new at 5.5.0 and is the reason
  not to forward `newCall`. `Untrusted` has no proxy, so (b) does not arise; (a) is the per-hop URL check's and the socket guard's job.
- **Empty answer:** `UnknownHostException("${address.dns} returned no addresses for $socketHost")`, a message holding `Dns.toString()` and the host.
  `GuardedDns` never returns an empty list (`DropDenied` that filters everything away throws the marker), and an empty delegate
  answer should be replaced by a plain `UnknownHostException` with a constant message.
- **A throwing `lookup`:** an `UnknownHostException` (any `IOException`) propagates untouched (F8 below). A non-`IOException`
  (including `IllegalArgumentException` from `Dns.Request`'s hostname canonicalisation) is rethrown from the exchange finder and
  reaches the caller as `IOException("canceled due to <t>")` with the throwable as cause (`RealCall.AsyncCall.run`). The guard must
  therefore throw only `UnknownHostException` for refusals.
- Records the other paths use `address.dns` too: `JavaNetAuthenticator` calls `dns.lookup(url.host)` for proxy authentication only; the
  transport installs no authenticator.

### F2. The socket path: one factory method, one connect call (source, OkHttp 5.5.0)

- Only `ConnectPlan.connectSocket` uses the client's `SocketFactory`:
  `Proxy.Type.DIRECT, Proxy.Type.HTTP -> route.address.socketFactory.createSocket()!!`, otherwise `JavaNetSocket(route.proxy)`. A grep of
  the whole 5.5.0 tree finds no other `createSocket` call on a `SocketFactory` (the TLS one is on the `SSLSocketFactory`, F6).
  `OkHttpClient.Builder.socketFactory` documents it: "OkHttp only uses the parameterless [SocketFactory.createSocket] method to create
  unconnected sockets." and rejects an `SSLSocketFactory`.
- It then connects with `Platform.get().connectSocket(rawSocket, route.socketAddress, socketConnectTimeoutMillis)`; `Platform.connectSocket`
  is `socket.connect(address, connectTimeout)`. The only override is Android's.
- A `ConnectException` thrown from there is re-thrown as `ConnectException("Failed to connect to ${route.socketAddress}")` with the original
  as cause. Other `IOException`s are not wrapped. `ConnectPlan.connectTcp` catches `IOException` into `ConnectResult(plan, throwable = e)` and
  closes the raw socket (`rawSocket?.closeQuietly()`) if it failed.
- `Interceptor.Chain.withSocketFactory` lets an interceptor replace the factory for one call; the transport's own interceptors do not,
  and the transport clears inherited interceptors (architecture §6.3.2), so the guard stays in place.
- The socket factory and `Dns` are part of `Address` equality (`this.dns == that.dns`), so a pooled connection is never shared with a
  client configured with other guards.

### F3. Fast fallback (source, OkHttp 5.5.0)

- On by default: `OkHttpClient.Builder` has `internal var fastFallback = true`, and the builder KDoc: "Defaults to enabled, call with
  [fastFallback] = false to revert to 4.x behaviour." `RealCall` picks `FastFallbackExchangeFinder` when it is on, else `SequentialExchangeFinder`.
- Planning: `RouteSelector.nextRoutes` builds one `Route` per address of the answer, in answer order; with fast fallback it runs
  `reorderForHappyEyeballs`, which interleaves IPv6 and IPv4 when both families are present. `FastFallbackExchangeFinder.find` launches the first
  plan, then another every 250 ms (`connectDelayNanos`) or at once when nothing is in flight, each `connectTcp` on a `TaskRunner` thread, until one
  connects; the winners' rivals are cancelled (`plan.cancel()` closes the raw socket) and kept as deferred retries. Every attempt calls
  `createSocket()` and `connect` afresh, so each is judged. The guard runs concurrently on several threads: it must be stateless.
- Failure combination: `firstException = throwable` for the first failure, `firstException.addSuppressed(throwable)` for each later one, and
  `throw firstException!!` at the end (the sequential finder does the same). Loop exit also requires `attemptAnotherConnection(throwable)`, which
  is `e !is EchUntrustedException`.
- `retryOnConnectionFailure(false)` does not change trying the next address. The finders loop on `routePlanner.hasNext()`; the flag is used
  in `RetryAndFollowUpInterceptor.recover` (`// The application layer has forbidden retries.`) and in `ConnectPlan.nextConnectionSpec`
  (`// If recovery is configured off, don't retry.`, a TLS-spec downgrade on the same address).

### F4. `Socket()` versus `Socket(Proxy.NO_PROXY)` (source, jdk-11.0.32, 17.0.21, 21.0.13, 27)

| | `new Socket()` | `new Socket(Proxy.NO_PROXY)` |
|---|---|---|
| Java 11 | `setImpl()` → `impl = new SocksSocketImpl()` (extends `PlainSocketImpl`) | `factory == null` → `impl = new PlainSocketImpl()` |
| Java 17, 21, 27 | `SocketImpl delegate = SocketImpl.createPlatformSocketImpl(false); impl = new SocksSocketImpl(delegate)` (21 and 27 in a private static `createImpl()`) | `// create a platform or custom SocketImpl for the DIRECT case` → `SocketImpl.createPlatformSocketImpl(false)` (the NIO impl) |

- `SocketFactory.getDefault()` is a `DefaultSocketFactory` whose `createSocket()` is `new Socket()` (all four JDK lines). So OkHttp's default
  DIRECT socket is a `SocksSocketImpl` socket.
- `SocksSocketImpl.connect(SocketAddress, int)` (all lines): "server is not null only when the socket was created with a specified proxy in
  which case it does bypass the ProxySelector"; for the general case it asks `ProxySelector.getDefault()` (`return ProxySelector.getDefault();`),
  builds `socket://<host>:<port>`, and connects through the first proxy of type `SOCKS`; any other answer connects directly.
- `DefaultProxySelector` (17.0.21): the property table has `{"socket", "socksProxy"}`, so `socksProxyHost` / `socksProxyPort` apply to
  `socket://` URIs, read on every call through `NetProperties.get` (a system property overrides `net.properties`). `socksNonProxyHosts`
  defaults to `"localhost|127.*|[::1]|0.0.0.0|[::0]"`, so a loopback destination bypasses a SOCKS proxy unless the property is set to the empty
  string ("If it is empty, leave empty."). `java.net.useSystemProxies` is read once, in a static initialiser, and then makes
  `getSystemProxies("socks", host)` the fallback when no property names a host. A replaced `ProxySelector.setDefault(...)` is honoured too.
- Answer to the question: no, `proxy(Proxy.NO_PROXY)` on the `OkHttpClient` closes OkHttp's own selection (`RouteSelector.selectProxies` returns
  `listOf(proxy)`) and nothing more. The JVM-wide SOCKS configuration is reached one layer lower, through `Socket()`, and a SOCKS proxy
  reached that way is handed the already-resolved `InetSocketAddress`, so the destination the guard judged is the one the proxy is asked
  for; what is lost is "no proxy of any kind" and the egress position. A guarded socket extending `Socket(Proxy.NO_PROXY)` closes it
  (the DIRECT branch above never reaches `SocksSocketImpl`). Residual: a JVM-wide `Socket.setSocketImplFactory(...)` is honoured by the
  `NO_PROXY` branch too (`factory.createSocketImpl()`); it is deprecated and process-wide, and is not guardable.

### F5. A `Socket` subclass overriding `connect(SocketAddress, Int)` (source)

- `Socket.connect(SocketAddress)` is `connect(endpoint, 0);` in jdk 11.0.32, 17.0.21, 21.0.13 and 27, so overriding the two-argument method
  covers both (do not also override the one-argument form and call `super.connect(endpoint)`, which re-enters the override).
- Other connecting paths of a `Socket`: the `Socket(String/InetAddress, int...)` constructors (not used; they call `connect` virtually);
  `SocketChannel.socket()` adaptors (not used). `bind` does not connect. The impl-level `connect` is reached only from `Socket.connect`.
- `Socket.connect` accepts an unresolved address at its own level (`checkAddress(addr, "connect")` returns for `null`) and leaves rejection
  to the impl: `NioSocketImpl.connect` throws `new UnknownHostException(isa.getHostName())` (17, 21, 27), `AbstractPlainSocketImpl` the
  same (11), but `SocksSocketImpl` would pass the host name to a SOCKS5 proxy for the proxy to resolve (`domainNameBytes`, 17+).
  Refusing an unresolved target in the override, before `super.connect`, is therefore necessary, not cosmetic. OkHttp 5.5.0 only creates
  unresolved addresses for SOCKS routes, which bypass the factory.
- What a subclass of `Socket(Proxy.NO_PROXY)` can do: override every public method, including `connect`, `close`, `bind`; the constructor
  `Socket(Proxy)` is public. It cannot touch the package-private `getImpl()`/`createImpl()`/`setImpl()`. The impl is created lazily at first
  `connect`/`bind`, so constructing the socket allocates no file descriptor and does no I/O (a Level 2 test can create one freely).
- TLS: `route.address.sslSocketFactory.createSocket(rawSocket, host, port, true) as SSLSocket` under the comment "Create the wrapper over the
  connected socket." The JDK's `SSLSocketImpl` constructor for this form says "We always layer over a connected socket" and throws
  `SocketException("Underlying socket is not connected")` otherwise; its own `connect` throws "Already connected" when layered. No new connection
  is opened, so the single judged `connect` per attempt is the only one.
- Thread-safety: the JDK documents nothing about a `connect` racing a `close`; fast fallback does exactly that to losing attempts
  (`ConnectPlan.cancel` closes the raw socket while another thread may be in `connect`). The override holds no state beyond the final
  `super.connect`, so it adds no hazard.

### F6. The four abstract `SocketFactory` overloads (source; JDK 11 to 27 identical)

- Abstract: `createSocket(String, int)`, `createSocket(String, int, InetAddress, int)`, `createSocket(InetAddress, int)`,
  `createSocket(InetAddress, int, InetAddress, int)`. Not abstract: `createSocket()`, which throws `SocketException("Unconnected sockets not
  implemented")` (the JDK's own "refuse" precedent for the zero-argument form).
- None of them is called by OkHttp 5.5.0 (F2).
- Fail-closed options: (1) refuse all four with an exception; (2) judge: the `InetAddress` forms could build a `GuardedSocket`, connect it
  (judging the address) and return it connected; the `String` forms would have to resolve the name, which would be a second, unguarded
  resolver call unless it went through the guarded `Dns`. (1) costs nothing today, cannot be abused, and a future OkHttp that calls
  one overload fails loudly instead of connecting unjudged. Which exception: a `SocketException`/`UnsupportedOperationException` is
  not a refusal reason; an `IOException` marker with a "not a guarded entry point" class would also do. Precedent for the shape of the
  full override: OkHttp's own test helper `okhttp3.sockets.DelegatingSocketFactory` overrides all five and delegates all five; no
  guard library for the JVM was found that implements these overloads as refusals (Gaps).

### F7. The JDK as a literal oracle (source; jdk-11.0.32, 17.0.21, 21.0.13, 27)

`InetAddress.getAllByName(String)` (the body is the same in 11, 17 and 21; 27 moves the parsing into `Inet4Address.parseAddressString` /
`Inet6Address.parseAddressString` with the same control flow):

1. A null or empty host returns the loopback address.
2. A host starting with `[` must end with `]`; the brackets are stripped and `ipv6Expected = true`.
3. `if (IPAddressUtil.digit(host.charAt(0), 16) != -1 || (host.charAt(0) == ':'))` — only a first character that is a hex digit or `:` is
   treated as a possible literal. Unbracketed, `IPAddressUtil.validateNumericFormatV4` is tried; on success `new Inet4Address(null, addr)`. Else the IPv6 parser
   runs: a failure with `host.contains(":") || ipv6Expected` throws `UnknownHostException("...: invalid IPv6 address literal")` — no lookup.
4. A bracketed host whose first character is not a hex digit or `:` throws "invalid IPv6 address literal" — no lookup.
5. **Everything else falls through to `getAllByName0(...)`, the name-service lookup**: `256.1.1.1`, `1.2.3.4.5`, `abc.example`, `0x`-less
   garbage. So: no lookup for a valid literal and for any bracketed text; a lookup for an unbracketed non-literal.
6. The literal result is built with a `null` host name (`new Inet4Address(null, addr)`), so `toString()` starts with `/`; a looked-up result
   carries the name. That is an observable "no lookup happened" check.

Ambiguity (`IPAddressUtil`, all four lines): `textToNumericFormatV4` accepts decimal parts only, 1 to 4 of them (`d.d.d.d`, `d.d.d`, `d.d`,
`d`: "When only one part is given, the value is stored directly in the network address without any byte rearrangement."), with leading zeros
read as decimal (`0177.0.0.1` is `177.0.0.1`). `validateNumericFormatV4` throws `IllegalArgumentException` (which `getAllByName` rethrows as
`UnknownHostException` with the IAE as cause, no lookup) when the decimal parse fails but `isBsdParsableV4` succeeds (hex or octal parts, mixed).
The property `jdk.net.allowAmbiguousIPAddressLiterals` (default `false`, read once in a static field) turns the throw off — the string then
goes to the name service (the OS resolver may read it as `inet_addr` does) — and also switches `IPAddressUtil.digit` from ASCII-only
`parseAsciiDigit` to `Character.digit`, which accepts non-ASCII digits. Introduced as JDK-8277608, "Address IP Addressing": commit `de08dc4d79`
(jdk11u, 2022-07-01), `b0059e1eef` (jdk17u, 2022-07-01), in `jdk` before `jdk-19-ga`; by `gh api compare`, `jdk-11.0.15-ga` and
`jdk-17.0.3-ga` do not contain it and `jdk-11.0.16-ga` and `jdk-17.0.4-ga` do. The JDK 18 release note (JDK-8277608) is quoted on the net-dev list
as: "The release note states that the `InetAddress` accepts IPv4 address literals in decimal quad notation."

`InetAddress.getByAddress(String, byte[])` performs no lookup: 4 bytes give `Inet4Address`; 16 bytes go through
`IPAddressUtil.convertFromIPv4MappedAddress`, and an IPv4-mapped (`::ffff:a.b.c.d`) array becomes an `Inet4Address`; every other embedding
(IPv4-compatible `::a.b.c.d`, NAT64, 6to4) stays `Inet6Address`. The IPv6 text parser applies the same collapse (`textToNumericFormatV6` ends with
`convertFromIPv4MappedAddress`). `getByAddress(byte[])` is the same.

`InetAddress.ofLiteral(String)` exists only from 22: its source javadoc in `jdk-27-ga` ends `@since 22`, and says "This method doesn't block,
i.e. no reverse lookup is performed." It does not compile under `-Xjdk-release=11`. Equally, `IPAddressUtil` is `sun.net.util`, not exported.

Weakness to keep in mind: the JDK parser is lenient in other ways (IPv6 zone ids, a trailing-garbage defect reported by NCC Group on 2022-10-06,
which the vendor rated as input-validation hardening only), so the JDK accepts a superset of the strict grammar. That is the right direction for
an oracle of "whatever `StrictIp` accepts, the JDK connects to the same address" and the wrong one for "the JDK agrees these strings are invalid".

### F8. How a refusal surfaces through an OkHttp 5.5.0 call (source; consistent with the earlier measurement)

- **Resolver marker.** `RouteSelector` throws it inside `RealRoutePlanner.planConnect`, called by `plan()`. Fast fallback:
  `launchTcpConnect` wraps `routePlanner.plan()` in `try { ... } catch (e: Throwable) { FailedPlan(e) }` and returns `plan.result`; `find` sets
  `firstException = throwable` (an `IOException`), `attemptAnotherConnection` is true, the loop ends because `routePlanner.hasNext()` is false (the
  proxy list `[NO_PROXY]` has been consumed before the lookup), and `throw firstException!!`. Sequential: `catch (e: IOException)` the same. No wrapping.
- **Socket marker.** Thrown from `Platform.connectSocket`; `connectSocket` rewraps only `ConnectException`, so an `IOException` marker survives as
  the exception; `connectTcp` returns `ConnectResult(plan = this, throwable = e)`; `find` records it as the first failure or adds it with `addSuppressed`.
  With several denied addresses and no allowed one, the top is the first refusal and the others are in `suppressed`.
- **Top of the call.** `RetryAndFollowUpInterceptor.intercept`: `if (!isRecoverable) throw e.withSuppressed(recoveredFailures)`. With
  `retryOnConnectionFailure(false)`, `recover` returns false at once. Even with it on, a failure inside `exchangeFinder.find()` leaves
  `exchange == null`, so `retryAfterFailure()` is false and the same exception is thrown. `AsyncCall.run` passes an `IOException` to
  `onFailure` unchanged; `execute()` throws it. The one disguise is the call timeout: if `timeout.exit()` reports expiry, `timeoutExit` returns
  `InterruptedIOException("timeout")` with the real failure as `initCause`. The transport's mapper should walk `cause` as well as `suppressed`.
- **Ambiguity with an unreachable allowed host:** a denied attempt that comes before an allowed host's failed connect puts the marker first
  with the `ConnectException` suppressed (existing F11). Under `Reject` the resolver never lets a denied address reach the socket layer, so a
  socket marker is a true "something bypassed the resolver guard" signal; under `DropDenied` likewise. The mapper should still treat the marker
  as a refusal wherever it appears in cause-or-suppressed, since the marker only exists when something was actually denied.
- **What OkHttp puts in messages:** `ConnectException("Failed to connect to ${route.socketAddress}")` (only for `ConnectException`); the empty-answer
  message of F1; `EventListener.connectFailed(call, route.socketAddress, ...)` and `dnsEnd(..., inetAddressList)` carry addresses to any listener
  (the transport installs none that logs them). The markers' own messages stay constant.

### F9. Mature guards vs. these hooks (common practice; sources: the earlier research, plus Spring Boot)

The earlier research already records the split: Smokescreen, Advocate and `ssrf_filter` filter the resolver answer and dial only a validated
address; `safeurl` vets each attempt in the dialer's `Control` hook (the socket-level equivalent); `request-filtering-agent` does both. Spring Boot's
`InetAddressFilter` (commit `11778bdc24f72f208f532eba8185311473908019`, read 2026-10-25) filters at the resolver of each supported client:
`HttpComponentsFilteredDnsResolver implements DnsResolver` overrides both `resolve(String)` and `resolve(String, int)` (it overrides every
resolution entry point of the interface, which is the same discipline as "do not forward `newCall`"), filters the answer, and throws a
`FilteredHostException` when nothing is left, i.e. drop-and-throw-if-empty, the `DropDenied` shape. It has no OkHttp support and no socket-level
layer. So resolver-only is the common practice and resolver plus socket is the stricter pair this design adopts.

## How comparable projects do it

| Project (ref) | Hook | Notes |
|---|---|---|
| OkHttp `okhttp3.Dns` (5.5.0) | `lookup` plus default `newCall` → `LookupDnsCall(this)`; `RouteSelector` branches on `is LookupDnsCall` | A wrapper that inherits `newCall` is routed through its own `lookup` |
| OkHttp test helper `okhttp3.sockets.DelegatingSocketFactory` (5.5.0, `okhttp-testing-support`) | overrides all five `createSocket` overloads and delegates each, with a `configureSocket` hook | The only complete override in OkHttp; shows the five-method shape |
| Spring Boot `HttpComponentsFilteredDnsResolver` (`11778bd`) | wraps Apache `DnsResolver`; both `resolve` overloads; throws when the filtered list is empty | Resolver-only; `resolveCanonicalHostname` delegates unfiltered |
| `safeurl` (Go), Advocate (Python), `request-filtering-agent` (Node) | connect-time hook on the socket/dial | Equivalent of the `GuardedSocket.connect` check; see the existing research |

## Pitfalls and regrets

1. **Forwarding `newCall`** (explicitly, or with Kotlin `by delegate`): safe today over `Dns.SYSTEM`, a bypass over a record-capable delegate.
2. **Extending or wrapping `Socket()`/the default factory's sockets** and believing `proxy(NO_PROXY)` covers SOCKS: it does not (F4). A delegate-wrapping
   factory (`delegate.createSocket()` then a wrapper) cannot swap the impl and would also have to proxy every `Socket` method for the TLS wrapper.
3. **A marker that extends `ConnectException`** is wrapped with the address in the message (F2).
4. **Returning an empty list** from `GuardedDns` yields a message with the host and the `Dns.toString()` (F1). Give `GuardedDns` a constant `toString()`.
5. **Throwing a non-`IOException`** from `lookup` or `connect` turns into `IOException("canceled due to ...")`, cancels the call and, for the cause chain,
   hides the marker one level down (F1).
6. **Calling `InetAddress.getByName` on arbitrary text in a test or a guard**: an unbracketed non-literal goes to the OS resolver (F7). Judge the bytes
   from `InetSocketAddress.address.address` / the `Dns` answer, never re-resolve a string.
7. **Treating every `InetAddress` as 4 or 16 bytes**: a custom `InetAddress` subclass, or an `Inet6Address` built through its static factory with a
   mapped array, is not collapsed. The classifier must fail closed on any other length and judge a mapped array by its embedded IPv4.
8. **System-property tests in a parallel JVM.** `socksProxyHost` and `ProxySelector.setDefault` are process-wide; restore them in `finally` and keep
   such a test out of any parallel execution (JUnit 5 `@Isolated`, or `@ResourceLock(Resources.SYSTEM_PROPERTIES)`).
9. **A refusal that depends on timing.** A real connect to an unrouted address (`10.0.0.5`) times out slowly; a test that wants "refused before
   I/O" asserts the marker class, which a real attempt can never produce.

## Recommendation for this repo

Build the `jvmMain` pair as follows (internal; Java 11 API only; no OkHttp type leaves `jvmMain`).

**`GuardedDns`** — `internal class GuardedDns(private val delegate: Dns, private val guard: UrlGuard, private val mixedAnswers: MixedAnswers) : Dns`.
Override `lookup` only and a constant `toString()`; do not use `by`, do not override `newCall`.
`lookup`: `val answer = delegate.lookup(hostname)` (exceptions propagate); empty answer → `UnknownHostException` with a constant message; map each
address to `guard.checkAddress(address.address)` (a `ByteArray`; 4 or 16 bytes, anything else is denied). `Reject`: any denial → throw
`PolicyRefusedUnknownHost(reason, rangeClass)`. `DropDenied`: return the allowed addresses in order; none left → throw the marker. The marker is
`internal class PolicyRefusedUnknownHost(val reason: RefusalReason, val rangeClass: String) : UnknownHostException(MESSAGE)` with a `const` message; it
never receives the host, the address or the range. The judgement is a value (`Verdict`); the throw is the SPI boundary, where the JDK and OkHttp require
exceptions (the repo's `Result` rule applies to the library API, not to these callbacks).

**`GuardedSocketFactory`** — `internal class GuardedSocketFactory(private val guard: UrlGuard) : SocketFactory()` (not an `SSLSocketFactory`; OkHttp
rejects one). `createSocket()` returns `GuardedSocket(guard)`. The four abstract overloads throw (fail closed; one shared private helper). No delegate
factory: the factory must create the socket itself to choose the `SocketImpl`.

**`GuardedSocket`** — `internal class GuardedSocket(guard, …) : Socket(Proxy.NO_PROXY)` overriding only `connect(endpoint: SocketAddress?, timeout: Int)`:
require an `InetSocketAddress` that is resolved, take `endpoint.address.address`, call `guard.checkAddress`, throw `PolicyRefusedIo(reason, rangeClass)`
(extends `IOException`, never `ConnectException`; constant message) on denial, on an unresolved address, and on any other `SocketAddress` type; only then
`super.connect`. Keep the final `super.connect` behind one small `internal` hook so Level 2 can assert "an allowed address reaches the connect step"
without a network. Consider overriding `fillInStackTrace()` in the markers: refusals are an expected, attacker-triggerable event, and the stack adds nothing.
For an unresolved or non-Inet target use `RefusalReason.DeniedAddress` with a range class such as `unresolved` (these are not a reason of their own at the
public level).

**Per-policy bundle** — one internal function the transport calls on its `OkHttpClient.Builder`:
`Untrusted`: `proxy(Proxy.NO_PROXY)`, `dns(GuardedDns(baseDns, guard, mixedAnswers))`, `socketFactory(GuardedSocketFactory(guard))`; leave `fastFallback` at its
default and keep `retryOnConnectionFailure(false)` as already planned. `Trusted`: none of the three (default selector, `baseDns`, default factory); `baseDns`
is `Dns.SYSTEM` or the fixtures' stub. The transport's failure mapper looks for the markers in the top exception, its `cause` chain and its `suppressed` list
(depth-first, bounded), then maps to `RefusedByPolicy(reason)`.

**Why not the obvious alternative.** Wrapping `SocketFactory.getDefault()` (`GuardedSocketFactory(delegate, guard)` as sketched earlier) is rejected
because it leaves the socket a `SocksSocketImpl` socket: OkHttp's `proxy(NO_PROXY)` does not stop a JVM-wide SOCKS selector there, and "Untrusted connects only directly"
would depend on the deployment host's properties.

**Oracle for the `StrictIp` differential (`jvmTest`, Level 4a, Java 11 API only).** For every generated string `s` that `StrictIp` accepts:
IPv4 → `InetAddress.getByName(s)`; IPv6 → `InetAddress.getByName("[" + s + "]")`. Assert the returned `address` equals `StrictIp`'s bytes (a 16-byte mapped
result compared with the JDK's 4-byte `Inet4Address` through its last four bytes), the type matches the length, and `toString().startsWith("/")` (no host
name, so no lookup). For IPv6 only, also run strings `StrictIp` rejects through the bracketed form: the JDK either parses them (counted as "JDK looser", no
failure) or throws `UnknownHostException` without touching the resolver. Never pass an unbracketed rejected string to the JDK. Because the property
`jdk.net.allowAmbiguousIPAddressLiterals` or a JDK older than 11.0.16/17.0.4 changes only what the JDK accepts beyond decimal, the accept-direction assertion is
independent of both.

**Tests.**
- Level 2 (`com.spartanlabs.testing.component.webtools.http`): `GuardedDnsComponentTest` over a hand-written stub `Dns` returning `InetAddress.getByAddress(ByteArray)`
  values (no lookup): `Reject` with a mixed answer throws the marker with the reason and a range class and a message equal to the constant; `DropDenied` keeps only allowed
  addresses in order; all-denied throws and never returns empty; a delegate `UnknownHostException` and an empty answer; an unusual-length address is denied; a structural
  test that `GuardedDns` declares no `newCall` (reflection over `declaredMethods`) plus a behavioural one calling the inherited `newCall(Dns.Request(host)).enqueue(...)` and
  asserting the callback sees only the filtered records or the marker. `GuardedSocketFactoryComponentTest`: `createSocket()` returns a `GuardedSocket`, unconnected,
  not closed; each of the four overloads throws; `connect` with a denied `InetAddress`, an unresolved `InetSocketAddress.createUnresolved(...)`, a non-`InetSocketAddress`
  `SocketAddress` subclass, and via `connect(SocketAddress)` all throw the marker before any I/O; an allowed address reaches the test hook. A bundle test builds the
  `OkHttpClient` for each policy and asserts `client.proxy`, `client.dns` and `client.socketFactory` (no network: building a client opens nothing).
- Level 3 (`...integration.webtools.http`), loopback only, guard relaxed through `GuardRelaxation` (visible to `jvmTest`):
  (a) a local `ServerSocket(0, 50, loopback)`: through the relaxed guard `createSocket().connect(...)` succeeds and the server accepts; through the unrelaxed guard the
  marker is thrown and a short `soTimeout` `accept()` sees no connection.
  (b) the SOCKS case: set `socksProxyHost`/`socksProxyPort` to a recording fake SOCKS server on loopback and `socksNonProxyHosts` to the empty string (the default
  list contains `127.*` and would hide the effect), then (i) control: `SocketFactory.getDefault().createSocket()` connecting to the target reaches the fake SOCKS server
  (shows the path is reachable, and a real `OkHttpClient` with `proxy(NO_PROXY)` and the default factory does the same), (ii) `GuardedSocket` connects straight to the target and the
  fake SOCKS server sees no connection. Restore the properties in `finally`; mark the class `@Isolated`.
  (c) real OkHttp, no network: a client with `proxy(NO_PROXY)`, the guarded `Dns` and factory, a stub `Dns` answering `[10.0.0.5, 10.0.0.6, fd00::1]` under `DropDenied`/`Reject`
  and with only the socket guard installed: the call fails with the marker at the top and the others in `suppressed`, in answer order after Happy-Eyeballs interleaving, with
  fast fallback on and with `retryOnConnectionFailure(false)`; the same stub with `[10.0.0.5, 127.0.0.1]` and a loopback server under the socket guard only connects to the allowed
  address (each attempt is judged); the stub `Dns` is called once per new connection.

## Load-bearing claims

1. **`Dns.newCall` is a default that wraps `lookup`; `Dns.SYSTEM` forwards both.** "`fun newCall(request: Request): Call = LookupDnsCall(TaskRunner.INSTANCE, this, request)`" and "`override fun newCall(request: Request) = Platform.get().systemDns.newCall(request)`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dns.kt>, OkHttp 5.5.0.
2. **`Dns` must be concurrency-safe and covers HTTPS records.** "Implementations of this interface must be safe for concurrent use." and "Typical implementations will read `A` (IPv4), `AAAA` (IPv6) and `HTTPS` (service metadata) records only." Same `Dns.kt`, OkHttp 5.5.0.
3. **The system `Dns` on the JVM is `InetAddressDns`.** "The default [Dns] for the system, by default [InetAddressDns]." <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/platform/Platform.kt>, OkHttp 5.5.0.
4. **`RouteSelector` branches on `LookupDnsCall` and otherwise consumes records.** "`when (val dnsCall = address.dns.newCall(dnsRequest)) {`", "`is LookupDnsCall -> {`", "`val inetAddresses = address.dns.lookup(socketHost)`", "`val records = dnsCall.execute()`". <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RouteSelector.kt>, OkHttp 5.5.0.
5. **An empty answer becomes a message with the Dns and the host.** "`throw UnknownHostException("${address.dns} returned no addresses for $socketHost")`". Same `RouteSelector.kt`, OkHttp 5.5.0.
6. **The record-based DNS API is new in 5.5.0.** "This release includes a major update to OkHttp’s DNS API: it now supports multiple resource record types (not just IP addresses!), asynchronous streaming results, and in-memory caching." <https://github.com/square/okhttp/blob/parent-5.5.0/CHANGELOG.md>, 5.5.0 (2026-08-16).
7. **OkHttp creates the raw socket with the parameterless `createSocket()` and connects with `Platform.connectSocket`.** "`Proxy.Type.DIRECT, Proxy.Type.HTTP -> route.address.socketFactory.createSocket()!!`" and "`Platform.get().connectSocket(rawSocket, route.socketAddress, socketConnectTimeoutMillis)`" in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/ConnectPlan.kt>; "`socket.connect(address, connectTimeout)`" in `.../internal/platform/Platform.kt`; OkHttp 5.5.0.
8. **The builder documents that only the parameterless `createSocket` is used.** "OkHttp only uses the parameterless [SocketFactory.createSocket] method to create unconnected sockets." <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt>, `Builder.socketFactory`, OkHttp 5.5.0.
9. **Only `ConnectException` is re-wrapped, with the address in the message.** "`throw ConnectException("Failed to connect to ${route.socketAddress}").apply {`". Same `ConnectPlan.kt`, OkHttp 5.5.0.
10. **TLS layers over the connected raw socket.** "`// Create the wrapper over the connected socket.`" (`ConnectPlan.kt`, OkHttp 5.5.0); "`// We always layer over a connected socket`" in <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/sun/security/ssl/SSLSocketImpl.java>, JDK 17.0.21.
11. **Fast fallback is on by default and races an address every 250 ms.** "Defaults to enabled, call with [fastFallback] = false to revert to 4.x behaviour." (`OkHttpClient.kt`, OkHttp 5.5.0); "This kicks off new attempts every 250 ms until a connect succeeds." in <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/FastFallbackExchangeFinder.kt>, OkHttp 5.5.0.
12. **Failures combine as first plus suppressed; a planning failure becomes a `FailedPlan`.** "`firstException.addSuppressed(throwable)`", "`FailedPlan(e)`", "`throw firstException!!`" in `FastFallbackExchangeFinder.kt`, OkHttp 5.5.0.
13. **The next address is tried regardless of `retryOnConnectionFailure`; the flag affects only whole-call recovery and TLS-spec fallback.** "`internal fun attemptAnotherConnection(e: IOException): Boolean = e !is EchUntrustedException`" (<https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RetryTlsHandshake.kt>); "`// The application layer has forbidden retries.`" (`.../internal/http/RetryAndFollowUpInterceptor.kt`); "`// If recovery is configured off, don't retry.`" (`ConnectPlan.kt`); OkHttp 5.5.0.
14. **The thrown exception reaches the caller itself.** "`if (!isRecoverable) throw e.withSuppressed(recoveredFailures)`" in `RetryAndFollowUpInterceptor.kt`; "`val canceledException = IOException("canceled due to $t")`" for non-`IOException` throwables in `.../internal/connection/RealCall.kt`; OkHttp 5.5.0.
15. **`Socket()` uses `SocksSocketImpl`; `Socket(Proxy.NO_PROXY)` uses the platform impl.** JDK 17.0.21 `Socket.java`: "`// create a SOCKS SocketImpl that delegates to a platform SocketImpl`" followed by "`impl = new SocksSocketImpl(delegate);`" (in `setImpl`), and "`// create a platform or custom SocketImpl for the DIRECT case`" in `Socket(Proxy)`. <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/java/net/Socket.java>. JDK 11.0.32 `Socket.java`: "`impl = new SocksSocketImpl();`" and "`impl = new PlainSocketImpl();`". <https://github.com/openjdk/jdk11u/blob/jdk-11.0.32-ga/src/java.base/share/classes/java/net/Socket.java>. JDK 21.0.13 and 27 `Socket.java` carry the same comments (`createImpl()`).
16. **The default socket factory returns `new Socket()`.** In `DefaultSocketFactory.createSocket()`: "`return new Socket();`"; the base `createSocket()` message is "`Unconnected sockets not implemented`". <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/javax/net/SocketFactory.java>, JDK 17.0.21 (11.0.32, 21.0.13 and 27 equivalent).
17. **`SocksSocketImpl.connect` consults the default `ProxySelector` unless built for a given proxy.** "server is not null only when the socket was created with a" … "specified proxy in which case it does bypass the ProxySelector" (two comment lines in `connect`) and "`return ProxySelector.getDefault();`". <https://github.com/openjdk/jdk11u/blob/jdk-11.0.32-ga/src/java.base/share/classes/java/net/SocksSocketImpl.java>, JDK 11.0.32 (17.0.21 and 21.0.13 call `ProxySelector.getDefault()` in the same method; 27 as `ProxySelector sel = ProxySelector.getDefault();`).
18. **`Socket(Proxy.NO_PROXY)` is documented as a plain socket.** "`Socket s = new Socket(Proxy.NO_PROXY);} will create a plain socket ignoring any other proxy configuration.`" <https://github.com/openjdk/jdk11u/blob/jdk-11.0.32-ga/src/java.base/share/classes/java/net/Socket.java>, JDK 11.0.32.
19. **The default selector applies SOCKS properties to `socket://` URIs and excludes loopback by default.** `{"socket", "socksProxy"}` and "`static final String defStringVal = "localhost|127.*|[::1]|0.0.0.0|[::0]";`" and "`// is empty, leave empty.`" and "`final String key = "java.net.useSystemProxies";`" in <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/sun/net/spi/DefaultProxySelector.java>, JDK 17.0.21.
20. **`Socket.connect(SocketAddress)` calls the two-argument method.** "`connect(endpoint, 0);`" in `Socket.java` of jdk 11.0.32, 17.0.21, 21.0.13 and 27 (links as claim 15 and <https://github.com/openjdk/jdk/blob/jdk-27-ga/src/java.base/share/classes/java/net/Socket.java>).
21. **Unresolved addresses are rejected by the platform impl, not by `Socket`.** "`if (isa.isUnresolved()) {`" then "`throw new UnknownHostException(isa.getHostName());`" in <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/sun/nio/ch/NioSocketImpl.java>, JDK 17.0.21; "`throw new UnknownHostException(addr.getHostName());`" in `AbstractPlainSocketImpl.java`, JDK 11.0.32; `Socket.checkAddress` begins "`if (addr == null) {`" and returns, JDK 17.0.21.
22. **`InetAddress.getAllByName` parses literals without a lookup and otherwise looks up.** "`// Check and try to parse host string as an IP address literal`", "`ret[0] = new Inet4Address(null, addr);`", "`// and ends with square brackets, but we got something else.`" and the final "`return getAllByName0(host, true, true);`". <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/java/net/InetAddress.java>, JDK 17.0.21 (same flow in 11.0.32 and 21.0.13; 27 uses `Inet4Address.parseAddressString`).
23. **Ambiguous IPv4 forms are rejected unless the property is set; the property also widens digit parsing.** "`style rules, the IPv4 address string content is treated as ambiguous and`" (javadoc of `validateNumericFormatV4`), "`return Character.digit(ch, radix);`" (in `digit` when the property is true), and the property name `"jdk.net.allowAmbiguousIPAddressLiterals"`. <https://github.com/openjdk/jdk17u/blob/jdk-17.0.21-ga/src/java.base/share/classes/sun/net/util/IPAddressUtil.java>, JDK 17.0.21 (identical in 11.0.32, 21.0.13, 27).
24. **The ambiguity fix's update versions.** Commit "8277608: Address IP Addressing": jdk11u `de08dc4d79` and jdk17u `b0059e1eef` (both 2022-07-01); `gh api repos/openjdk/jdk11u/compare/de08dc4d79...jdk-11.0.16-ga` and `.../jdk17u/compare/b0059e1eef...jdk-17.0.4-ga` report `status: ahead` (tag contains the commit), and `...jdk-11.0.15-ga` / `...jdk-17.0.3-ga` report `status: behind`. Read 2026-10-25. JDK 18 note: "The release note states that the `InetAddress` accepts IPv4 address literals in decimal quad notation." (net-dev quote of <https://www.oracle.com/java/technologies/javase/18all-relnotes.html#JDK-8277608>) via <https://mail.openjdk.org/pipermail/net-dev/2023-October/022335.html>.
25. **IPv4-mapped collapse, no lookup in `getByAddress`.** "`= IPAddressUtil.convertFromIPv4MappedAddress(addr);`" followed by "`return new Inet4Address(host, newAddr);`" in `InetAddress.getByAddress(String, byte[])`, JDK 17.0.21 (link as claim 22); and "`Convert IPv4-Mapped address to IPv4 address.`" in `IPAddressUtil.java`.
26. **`InetAddress.ofLiteral` is Java 22+.** "`public static InetAddress ofLiteral(String ipAddressLiteral) {`" preceded by "`* @since 22`" and "This method doesn't block, i.e. no reverse lookup is performed." <https://github.com/openjdk/jdk/blob/jdk-27-ga/src/java.base/share/classes/java/net/InetAddress.java>, JDK 27 GA; absent in the 21.0.13 file.
27. **OkHttp's own complete `SocketFactory` override delegates all five methods.** "`A [SocketFactory] that delegates calls.`" <https://github.com/square/okhttp/blob/parent-5.5.0/okhttp-testing-support/src/main/kotlin/okhttp3/sockets/DelegatingSocketFactory.kt>, OkHttp 5.5.0.
28. **Spring Boot's resolver filter drops denied addresses and throws when none is left.** "`return FilteredAddresses.of(Arrays.stream(resolved), this.filter::matches)`" then, after `.toArray(InetAddress[]::new)`, "`.orElseThrow(host, this.filter);`" in <https://github.com/spring-projects/spring-boot/blob/11778bdc24f72f208f532eba8185311473908019/module/spring-boot-http-client/src/main/java/org/springframework/boot/http/client/HttpComponentsFilteredDnsResolver.java>, commit `11778bd` (read 2026-10-25).

## Gaps

- **Nothing was run.** Every behavioural statement is read from source. The ones a test in this module should confirm before the design is frozen:
  (1) the Level 3 SOCKS test (F4): that a default-factory DIRECT socket goes through `socksProxyHost` and a `Socket(Proxy.NO_PROXY)` subclass does not;
  (2) the Level 3 real-OkHttp test (F8): marker at the top and the rest in `suppressed` with fast fallback on, including after `reorderForHappyEyeballs`;
  (3) the `newCall` pair of Level 2 tests (F1): that the inherited `newCall` routes through the guard's `lookup`.
- **JDKs 18 to 20 and 22 to 26 were not read individually.** Sources read: 11.0.32, 17.0.21, 21.0.13 and 27 GA, which bracket the build range; the `Socket`/`SocksSocketImpl`
  structure is identical in 17, 21 and 27, so 18 to 26 are assumed the same. `InetAddress.ofLiteral` being 22+ is from the 27 source javadoc, not from the 22 file.
- **Windows-specific `SocketImpl` for JDK 11** (`DualStackPlainSocketImpl`) was not read; the share-classes `Socket(Proxy)` branch is what decides the SOCKS question.
- **Build JDK floor.** "17" nominal includes 17.0.0 to 17.0.3, which parse BSD hex/octal IPv4 forms natively (the ambiguity check arrived in 17.0.4). The oracle recommended
  above does not depend on it, but any test that feeds the JDK a non-canonical decimal-or-BSD form would.
- **The NCC Group advisory** (2022-10-06, trailing garbage after an IPv6 zone) was read through the fetch tool's summary, not verbatim, and its fix status in 17.0.21 / 21 / 27 was not
  checked; it is only used to justify "the JDK is looser than `StrictIp`".
- **No JVM guard library implementing the other four `createSocket` overloads as refusals was found** (searches of GitHub code and the web returned only JDK mirrors and OkHttp's delegating
  helpers); refusal is a reasoned fail-closed choice, not a precedent.
- **The reason and range class for an unresolved or non-Inet target** (`DeniedAddress` with a range class such as `unresolved`) is a plan-level naming choice; no source decides it.
- **`Socket.setSocketImplFactory` and `ProxySelector.setDefault`** by other code in the same JVM are not defended against; the first affects even `Socket(Proxy.NO_PROXY)`.
- **HTTP/2 coalescing, TLS and a real resolver** were not examined for this question (unchanged from the existing research).
