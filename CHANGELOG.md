# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each module is versioned by its own build file.

## [Unreleased]

### Added

- `webtools-http` (unreleased, `1.0.0`): `OkHttpTransport`, the supplied `HttpTransport`, and its factory
  `OkHttpTransport(userAgent, policy)`. It sends one request per hop and never retries; follows `301 302 303 307 308`
  itself for `GET` and `HEAD` under one deadline, judging every hop with the outbound-URL policy and stripping
  sensitive headers on an origin change; reads a capped, decoded body inside the callback so cancelling the caller
  stops the read; refuses twelve request headers the transport controls; maps every OkHttp failure onto
  `TransportFailure`; and closes with `close()`.
- `webtools-http` (unreleased, `1.0.0`): the response cache. The port `ResponseCache` (`getOrLoad` with and without
  a per-call `maxAge`, `invalidate`, `invalidateAll`, `stats`, `close`; implementing it needs the opt-in) with its
  values `CacheKey`, `CachePolicy` (built with `CachePolicy(ttl) { missTtl = … }`), `Lookup` (`Found`, with a weight
  of at least 1, and `Missing`) and `CacheStats`; `ResponseCache.Disabled`, the pass-through that stores and shares
  nothing; and `CaffeineResponseCache`, the supplied JVM cache, built with `CaffeineResponseCache { }`: N concurrent
  callers of one key cost one load, a value and a confirmed miss are stored for their own lifetimes, a weight bound
  limits what is stored, a `Found` heavier than `maxWeight` is delivered but not stored, failures are never stored,
  a loader's `Error` reaches every waiter, `close()` ends every waiting call with `Closed`, and one `DEBUG` line per
  load end says whether the result was stored or why not. Its `timeSource`, `loadDispatcher` and
  `maintenanceExecutor` are builder properties, so one virtual clock drives it under test.
- `webtools-http` (unreleased, `1.0.0`): the call budget. The port `CallBudget` (`acquire` with a fail-fast `maxWait`,
  `utilisation`, `flush`, `close`; implementing it needs the opt-in) with `Permit` (`complete`, `complete(signals)`,
  `release`), `withPermit`, `CallBudget.Unlimited`, and the vocabulary `Lane`, `RateWindow` (built with
  `RateWindow(name, published, period) { margin = …; hard = … }`), `RateSignals`, `WindowReading`, `BudgetUtilisation`
  and `WindowUtilisation`; `RetryAfter.parse` (seconds or any of RFC 9110's three HTTP-date forms) and
  `RateSignalsReader` with `Standard` (`429`, or `503` with a parseable `Retry-After`); the seams `BudgetStore` with
  `InMemoryBudgetStore`, `BudgetSnapshot` and `WindowSnapshot`, and `BudgetObserver` with `BudgetObserver.None` and
  `BudgetEvent` (`Granted`, `Refused`, `Paused`, `HardStop`, `Reconciled`, `StoreFailed`, `Restored`) and its
  vocabularies `PauseSource` and `StoreOperation`; and `WindowedCallBudget`, the supplied budget, built with
  `WindowedCallBudget(windows) { }`: epoch-aligned windows with a boundary guard, per-lane held-back floors, first come
  first served within a lane up to `maxQueued` waiting calls, waits that are refunded and re-planned when a caller is
  cancelled, pauses from `Retry-After`, `resetIn` or the next boundary, server counts that only raise local usage, a
  hard window saved write-ahead through its `BudgetStore` and restored once after a restart, a store failure that is
  reported as `StoreFailed` while grants continue (a store call is bounded by `storeTimeout` and tried again after
  `storeRetryInterval`; an `Error` the store throws propagates to the caller), a `flush()` that writes the exact
  counts, with a zero for a hard window whose grants were all released, so a crash after it never under-counts, and a
  non-suspending `close()` that ends every waiting call with `Closed`. Its `timeSource` and `wallClock` are builder
  properties, so one virtual clock drives it under test.
- `webtools-http` test fixtures: `LoopbackTransports.untrusted` and `trusted`, `MockWebServer.webtoolsUrl(path)`,
  `Fixture.toMockResponse(method)` and `MockWebServer.enqueue(fixture, method)`.
- `webtools-http` test fixtures: `RecordingBudgetObserver` and `FakeBudgetStore`, a scripted `BudgetStore` with
  `loadDelay` and `saveDelay` in virtual time and failures injected by `failLoads` and `failSaves` (each takes a
  `times` of at least 1).
- `webtools-http` (unreleased, `1.0.0`): the call facades. `Endpoint`, declared once with
  `Endpoint(id, decode) { ttl = …; missTtl = …; cost = …; lane = …; misses(404) }` (the decoder is the second argument;
  `missTtl` follows `ttl` unless set), and the per-call `CallSpec` and `FetchSpec`. `UpstreamClient` calls the endpoints
  of one upstream from a base URL: it checks each request against the outbound-URL policy first (a refusal costs
  nothing), serves a `GET` from the response cache, takes a budget permit inside the cache's loader (so N concurrent
  callers cost one permit and one request) and sends through the transport. A throttle is `RateLimited` (its
  `retryAfter` is the longer of the server's `Retry-After` and the budget's pause), a declared miss status is
  `Lookup.Missing`, a `2xx` the decoder rejects is `DecodeFailure`, any other status is `UpstreamError`, and nothing is
  ever retried or cached as a failure. Only `InvalidRequest` releases a permit; every other transport failure leaves it
  spent. Built with `UpstreamClient(transport, baseUrl) { … }`, or on the JVM with
  `UpstreamClient(baseUrl, userAgent) { … }`, which owns an `OkHttpTransport`, a `CaffeineResponseCache` and the
  `WindowedCallBudget` of `budget(windows)` and raises OkHttp's concurrency limits to the budget's `maxBurst`;
  `defaultMaxWait` is 5 seconds; `close()` closes what the client built, in the order cache, budget, transport, and
  `flush()` writes a persistent budget's exact counts. `UrlFetcher` fetches administrator-supplied URLs with the
  policy's pre-check first, a budget (`Unlimited` by default) and no cache: every status is the response except a
  throttle; built with `UrlFetcher(transport, policy) { … }`, or on the JVM with `UrlFetcher(userAgent)`, which owns a
  transport under the `Untrusted` defaults, and `UrlFetcher(userAgent, policy)`, one under the given policy.
  `UpstreamFailure.RateLimited.retryAfter` now means "how long to wait before calling again".
- `webtools-http` test fixtures: `LoopbackFetchers`, whose `untrusted` and `trusted` each build a `UrlFetcher` for a
  loopback `mockwebserver3` server; the fetcher owns the matching `LoopbackTransports` transport. `untrusted` relaxes
  the pre-check for loopback exactly as `LoopbackTransports` relaxes the transport, so every other `Untrusted` rule
  stays live; `trusted` forces cleartext on in both and relaxes nothing.

### Changed

- `webtools-http` test fixtures: `FakeHttpTransport`'s `maxResponseBytes` cap is no longer applied to a response to a
  `HEAD` request or to a `1xx`, `204` or `304` status, as the real transport reads no body for them.
