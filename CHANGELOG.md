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
- `webtools-http` test fixtures: `LoopbackTransports.untrusted` and `trusted`, `MockWebServer.webtoolsUrl(path)`,
  `Fixture.toMockResponse(method)` and `MockWebServer.enqueue(fixture, method)`.

### Changed

- `webtools-http` test fixtures: `FakeHttpTransport`'s `maxResponseBytes` cap is no longer applied to a response to a
  `HEAD` request or to a `1xx`, `204` or `304` status, as the real transport reads no body for them.
