# Issue #12 — an opt-in scheduled keepalive

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#12` — *"Provide an opt-in scheduled
  keepalive instead of requiring callers to time KA themselves"* (label:
  `enhancement`). `MultiConnectionUDPClient.sendKeepAlive()` and
  `Connection.keepAlive()` are one-shot primitives that "own no timer or thread";
  every consumer therefore hand-rolls the same ~20 s idle timer around them, and a
  consumer that forgets silently loses its NAT mapping after the idle window.
- **Scope of this plan:** `webtools-udp` only. Add an opt-in, idle-aware scheduled
  keepalive on both sides — `MultiConnectionUDPClient.startKeepAlive(...)` /
  `stopKeepAlive()` and `Connection.startKeepAlive(...)` / `stopKeepAlive()` —
  each backed by a single daemon `ScheduledExecutorService` created only on
  opt-in and shut by `stop()` / `terminate()`. The one-shot `sendKeepAlive()` /
  `keepAlive()` primitives are unchanged and remain the underlying mechanism.
- **Module:** `webtools-udp` (`io.github.spartanlaboratories:webtools-udp`,
  package `com.spartanlabs.webtools.udp`). **No wire / protocol change** — the
  `KA` token and its meaning are untouched; this is a local scheduling
  convenience only.
- **Branch:** `feat/issue-12-scheduled-keepalive` (off `master`, deleted after
  merge).
- **Commits:**
  - `63b2524` — `feat:` all `src/main` changes, `KeepAlive.kt`,
    `KeepAliveScheduler.kt`, `README.md`, the test-support fixtures + **this plan
    document** (committed with the first implementation stage per §10, so
    `git log --follow docs/issue-12-scheduled-keepalive-plan.md` binds plan to
    implementation).
  - `1108673` — `test:` the §5 suite, all levels.
  - `f6a0dd4` — `build:` bump `webtools-udp` to `1.5.0` (§3.13).
- **PR:** [#25](https://github.com/SpartanLaboratories/WebTools/pull/25), merged as
  `eb14321`.
- **Status:** **done.** Merged to `master` 2026-09-07. Full suite green across all
  levels (gating, component, integration, deterministic, e2e, nonfunctional, uat;
  8 skipped = `@Disabled` Level-5 manual scenarios). All five design decisions are
  resolved (§8) — **Open decisions: none.**
- **Target version:** `webtools-udp` `1.4.0` → **`1.5.0`** (purely additive — new
  members only, nothing removed or changed; see §7).
- **Related (boundaries noted, not designed here):**
  - `docs/issue-10-connection-liveness-plan.md` — introduced the server-side
    `mcups-liveness` `ScheduledExecutorService` and the pure `Liveness` helper.
    §2.4 there notes "#12's server-side scheduled keepalive, if it lands, reuses
    that executor rather than adding a third" — this plan diverges from that
    (§2.6, D-note): the two schedulers have different lifecycles and cadences,
    and `mcups-liveness` exists only when `idleTimeoutMillis > 0`, so a dedicated
    opt-in scheduler is cleaner than a conditional piggyback. The new `KeepAlive`
    helper is a deliberate near-twin of `Liveness` (§2.3).
  - `docs/issue-11-handshake-refusal-and-credential-plan.md` — the
    `@JvmOverloads` + `open`/default-method additive-evolution pattern reused
    here for `Connection` and `MultiConnectionUDPClient`.
  - `docs/issue-3-public-client-handshake-plan.md` — the `MultiConnectionUDPClient`
    lifecycle (`handshake` → `start` → `stop`) this plan extends with
    `startKeepAlive` / `stopKeepAlive`.
  - `docs/issue-1-tier-2-plan.md` §2.1 — the canonical inbound-datagram flow.

---

## 1. Context

### 1.1 Root cause (current source, read directly)

Not a defect — a missing convenience. The keepalive primitives are deliberately
timer-free:

| Fact | Location |
|---|---|
| `MultiConnectionUDPClient.sendKeepAlive()` sends one `KA` datagram and returns; its KDoc: *"one-shot (mirrors [Connection.keepAlive])"* | `MultiConnectionUDPClient.kt:299-304` |
| `Connection.keepAlive()` KDoc: *"A one-shot: the caller schedules it (recommended ~20 s idle cadence). Owns no timer or thread."* | `Connection.kt:114-127` |
| `UDPConnection.keepAlive()` → `channel.send(KEEPALIVE_BYTES, peer)` — no scheduling | `UDPConnection.kt:54-56` |
| the client runs exactly two threads (`mcupc-listener`, `mcupc-dispatch`); no scheduled/timer thread | `MultiConnectionUDPClient.kt:120-125` |
| the server runs a listener + `mcups-dispatch` + optionally `mcups-liveness` (only when `idleTimeoutMillis > 0`); no keepalive timer | `MultiConnectionUDPServer.kt:143-202` |
| the README "Client-side usage" block literally instructs `client.sendKeepAlive()   // call on a ~20s idle cadence` | `README.md:221` |

Consequence: every consumer of `MultiConnectionUDPClient` (and every server that
wants cone-NAT refresh) writes the same `ScheduledExecutorService` +
fixed-delay-timer boilerplate around the primitive, and a consumer that omits it
silently loses its NAT mapping after the idle window — a failure mode that
surfaces only as mysterious mid-session unreachability.

### 1.2 Requirements / acceptance criteria

1. `MultiConnectionUDPClient` gains `startKeepAlive(intervalMillis)` /
   `stopKeepAlive()`. After `startKeepAlive`, the client sends `KA` on the shared
   socket without any caller timer, until `stopKeepAlive()` or `stop()`.
2. `Connection` gains `startKeepAlive(intervalMillis)` / `stopKeepAlive()` with
   the server→client equivalent behaviour, stopped by `stopKeepAlive()`,
   `terminate()`, or server `stop()`.
3. **Idle-aware:** a scheduled keepalive is sent only if nothing else has gone
   out on that path within the interval — application traffic defers the next
   `KA`. (The issue accepts a plain fixed-rate timer as a first cut; §2.3 / D2
   explains why idle-aware is chosen anyway.)
4. Each side's scheduler is a **single daemon `ScheduledExecutorService`**,
   created **only** when `startKeepAlive` is first called (zero cost by default,
   matching the `idleTimeoutMillis = 0` precedent from #10), and shut cleanly on
   `stop()`.
5. The one-shot `sendKeepAlive()` / `keepAlive()` primitives are **unchanged** —
   still available, still own no timer.
6. **Zero behaviour change by default.** A consumer that never calls
   `startKeepAlive` sees byte-for-byte today's behaviour: no extra thread, no
   extra per-datagram work.
7. Additive: `webtools-udp` `1.5.0`. No public signature removed or changed; new
   `Connection` methods have default bodies; new `MultiConnectionUDPClient`
   methods are `@JvmOverloads`-covered. No wire-format change.

---

## 2. Design

### 2.1 Shape of the change

| Unit | Kind | Change |
|---|---|---|
| `KeepAlive` | **new** `internal object` | pure, timer-free rules: poll cadence + "is a keepalive due". Near-twin of `Liveness` (#10). |
| `KeepAliveScheduler` (+ `KeepAliveSchedule` seam) | **new** `internal` | owns one lazily-created daemon `ScheduledExecutorService` and a per-key `ScheduledFuture` map; `schedule` / `cancel` / `shutdown`. Shared by client and server (thread name is a constructor arg). |
| `HandshakeWireFormat` | public | new `const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS = 20_000L`. |
| `ClientChannel` | internal | new `scheduleKeepAlive(peer, intervalMillis)` / `cancelKeepAlive(peer)`. |
| `Connection` | public | new `startKeepAlive()` / `startKeepAlive(Long)` / `stopKeepAlive()` — **default methods** (unsupported / no-op), overridden by `UDPConnection`. |
| `UDPConnection` | public (`internal` ctor) | overrides the two → delegate to `ClientChannel`. |
| `Registration` | internal | new `@Volatile var lastOutboundAt: Long` — the idle-aware clock for the server side. |
| `HandshakeCoordinator` | internal | new `KeepAliveSchedule` collaborator; `keepAliveTracked` flag; `send()` stamps `lastOutboundAt` when tracked; implements the two new `ClientChannel` methods; cancels on `deregister` + supersede; `shutKeepAlive()`. |
| `MultiConnectionUDPServer` | public | constructs the `KeepAliveScheduler`, passes it to the coordinator, shuts it in `stop()`; KDoc. |
| `MultiConnectionUDPClient` | public | new `lastOutboundAtNanos` clock; new `startKeepAlive` (`@JvmOverloads`) / `stopKeepAlive`; `send(ByteArray)` stamps the clock; `stop()` shuts the scheduler. |

Nothing is removed. `KeepAlive` / `KeepAliveScheduler` / `KeepAliveSchedule` /
`Handshake`-style internals are not public surface.

### 2.2 No wire change

`KA` is still exactly `KA`, still dropped on receipt without dispatch, still
"authoritative keepalive is the client's". `startKeepAlive` is a local timer that
calls the existing `sendKeepAlive()` / `keepAlive()` path. An old peer cannot
tell whether a `KA` came from a hand-rolled timer or the new scheduler. Nothing
in `HandshakeProtocol` / `HandshakeCoordinator.classify` changes.

### 2.3 `KeepAlive` — the pure, idle-aware rule

```kotlin
package com.spartanlabs.webtools.udp

/**
 * Pure, socket-free, timer-free rules for the opt-in scheduled keepalive: how
 * often the scheduler wakes, and whether a keepalive is due given the last time
 * something was sent. Deterministic functions of their arguments - fully
 * unit-testable. Sibling of [Liveness].
 */
internal object KeepAlive {
    private const val MIN_POLL_MILLIS = 250L
    private const val MAX_POLL_MILLIS = 5_000L
    private const val POLLS_PER_INTERVAL = 4L

    /**
     * How long the scheduled task should wait between wake-ups for a given
     * keepalive interval: one quarter of the interval, clamped to
     * [MIN_POLL_MILLIS]..[MAX_POLL_MILLIS]. Bounding the poll period this way
     * caps how late a keepalive can go out: at most
     * `intervalMillis + pollIntervalMillis(intervalMillis)` after the last
     * outbound datagram.
     */
    fun pollIntervalMillis(intervalMillis: Long): Long =
        (intervalMillis / POLLS_PER_INTERVAL).coerceIn(MIN_POLL_MILLIS, MAX_POLL_MILLIS)

    /**
     * True if the last outbound datagram is at least [intervalMillis] old, i.e. a
     * keepalive should be sent now. Always true when [intervalMillis] <= 0.
     * Compares in milliseconds so a pathological interval cannot overflow; a
     * [nowNanos] before [lastOutboundAtNanos] (benign clock skew within one poll)
     * yields false.
     */
    fun isDue(lastOutboundAtNanos: Long, nowNanos: Long, intervalMillis: Long): Boolean {
        if (intervalMillis <= 0L) return true
        val idleMillis = (nowNanos - lastOutboundAtNanos) / 1_000_000L
        return idleMillis >= intervalMillis
    }
}
```

**Why idle-aware, not a plain fixed-rate `KA` every interval** (the issue permits
the latter): a fixed-rate timer sends a redundant `KA` alongside a busy
application stream, and — more importantly — a *plain* fixed-delay timer that
does check idleness overshoots to ~`2 x interval` in the worst case (a send lands
just after a tick; the next tick is a full interval later and only then notices
the gap). The quarter-interval poll bounds the overshoot to
`interval + pollInterval` (25 s at the 20 s default) — safely inside typical NAT
mapping lifetimes. The cost is one extra timer wake every ≤ 5 s while a keepalive
is armed, which does nothing but read a `volatile` and compare. This mirrors
`Liveness.sweepIntervalMillis` exactly (#10 §2.4).

**Near-duplication with `Liveness` is deliberate.** `isDue` and `isOverdue` are
the same shape with opposite domains (outbound vs inbound). Merging them into one
`Cadence` object now would couple two independent features; if a third timer
appears, consolidate then.

### 2.4 `KeepAliveScheduler` — one lazily-created daemon executor, shared

```kotlin
package com.spartanlabs.webtools.udp

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** The seam [HandshakeCoordinator] / [MultiConnectionUDPClient] need onto a
 *  keepalive timer, so both can be unit-tested against a socket-free fake. */
internal interface KeepAliveSchedule {
    fun schedule(key: InetSocketAddress, intervalMillis: Long, tick: () -> Unit): Result<Unit>
    fun cancel(key: InetSocketAddress)
    fun shutdown()
}

/**
 * A single daemon [ScheduledExecutorService] plus a per-[key] [ScheduledFuture]
 * map. The executor is created on the first [schedule] call and torn down by
 * [shutdown] - a consumer that never opts into a scheduled keepalive pays for no
 * thread. [key] is the peer endpoint on the server side; the fixed server
 * endpoint on the client side.
 *
 * @param threadName the daemon thread's name (`mcupc-keepalive` on the client,
 * `mcups-keepalive` on the server)
 */
internal class KeepAliveScheduler(private val threadName: String) : KeepAliveSchedule {
    private val futures = ConcurrentHashMap<InetSocketAddress, ScheduledFuture<*>>()
    @Volatile private var executor: ScheduledExecutorService? = null
    @Volatile private var shutDown = false

    @Synchronized
    override fun schedule(key: InetSocketAddress, intervalMillis: Long, tick: () -> Unit): Result<Unit> =
        runCatching {
            require(intervalMillis > 0L) { "intervalMillis must be > 0, was $intervalMillis" }
            check(!shutDown) { "keepalive scheduler already shut down" }
            cancelInternal(key) // last call wins - replace any existing schedule for this key
            val poll = KeepAlive.pollIntervalMillis(intervalMillis)
            // runCatching in the task body is load-bearing: scheduleWithFixedDelay
            // permanently cancels a repeating task the first time it throws.
            futures[key] = executor().scheduleWithFixedDelay(
                { runCatching(tick).onFailure { log.warn("Keepalive tick for {} threw", key, it) } },
                poll, poll, TimeUnit.MILLISECONDS,
            )
        }

    override fun cancel(key: InetSocketAddress) = cancelInternal(key)

    private fun cancelInternal(key: InetSocketAddress) { futures.remove(key)?.cancel(false) }

    @Synchronized
    override fun shutdown() {
        shutDown = true
        futures.values.forEach { it.cancel(false) }
        futures.clear()
        executor?.shutdownNow()
        executor = null
    }

    @Synchronized
    private fun executor(): ScheduledExecutorService =
        executor ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, threadName).apply { isDaemon = true }
        }.also { executor = it }

    private companion object { private val log = LoggerFactory.getLogger(KeepAliveScheduler::class.java) }
}
```

- **`scheduleWithFixedDelay`**, not `AtFixedRate` — no value in bunching wake-ups.
- **Last-call-wins** on a repeat `schedule` for the same key — consistent with
  `MultiConnectionUDPClient.start` / `startBytes` (D3).
- **`shutDown` latch** — a `schedule` after `shutdown()` fails its `Result`
  rather than resurrecting the executor (relevant on the client:
  `startKeepAlive` after `stop()`).

### 2.5 Client — `startKeepAlive` / `stopKeepAlive`, idle-aware

`MultiConnectionUDPClient` gains a **`KeepAliveSchedule` constructor seam**,
mirroring `HandshakeCoordinator`'s injected `keepAliveSchedule` so the client's
idle-aware tick logic is unit-testable without a wall clock. The public
`@JvmOverloads` constructor is unchanged (same three public signatures as today);
an `internal` primary constructor takes the extra collaborator (the same pattern
as `UDPConnection`'s `internal constructor`):

```kotlin
class MultiConnectionUDPClient internal constructor(
    private val serverAddress: InetAddress,
    private val serverPort: Int,
    private val receiveBufferBytes: Int,
    private val keepAlive: KeepAliveSchedule,
) {
    @JvmOverloads
    constructor(
        serverAddress: InetAddress,
        serverPort: Int = MultiConnectionUDPServer.COMMON_LISTEN_PORT,
        receiveBufferBytes: Int = MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
    ) : this(serverAddress, serverPort, receiveBufferBytes, KeepAliveScheduler("mcupc-keepalive"))

    // ...

    /** Monotonic nanoTime of the last datagram this client put on the wire (data
     *  or KA); read by the keepalive tick, written by send(), hence @Volatile.
     *  Only ever used as a nanoTime difference. */
    @Volatile private var lastOutboundAtNanos: Long = System.nanoTime()

    private val serverEndpoint = InetSocketAddress(serverAddress, serverPort)
}
```

The `require(receiveBufferBytes in ...)` `init` guard is unchanged. A test
constructs the `internal` constructor with a `FakeKeepAliveSchedule` (§3.11) and
drives the recorded `tick` directly.

- `send(bytes: ByteArray)` (`MultiConnectionUDPClient.kt:295-297`) — stamp
  `lastOutboundAtNanos = System.nanoTime()` immediately after the successful
  `socket.send(...)`. Because `send(String)` and `sendKeepAlive()` both funnel
  through this overload, every outbound datagram — including a scheduled `KA` —
  advances the clock, so a busy application stream defers the next `KA` and a
  scheduled `KA` spaces the one after it.

```kotlin
/**
 * Starts an opt-in, idle-aware background keepalive: every ~[intervalMillis] of
 * output silence this client sends one `KA` on the shared socket to hold its NAT
 * mapping open, until [stopKeepAlive] or [stop]. Application sends reset the
 * idle timer, so a busy client sends no redundant keepalives.
 *
 * A convenience over [sendKeepAlive] - it removes the hand-rolled timer every
 * consumer otherwise writes. [sendKeepAlive] itself is unchanged and still owns
 * no timer. Calling this again replaces the schedule (last call wins). Backed by
 * one daemon thread (`mcupc-keepalive`) created on the first call.
 *
 * @param intervalMillis output-idle time before a keepalive is sent; must be
 * > 0. Defaults to [HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS]
 * (20 s). A keepalive may go out up to one quarter-interval (max 5 s) late.
 * @return [Result.success] once the schedule is armed; [Result.failure] with an
 * [IllegalArgumentException] for a non-positive interval, or an
 * [IllegalStateException] if [stop] has already run.
 */
@JvmOverloads
fun startKeepAlive(intervalMillis: Long = HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS): Result<Unit> =
    keepAlive.schedule(serverEndpoint, intervalMillis) {
        if (KeepAlive.isDue(lastOutboundAtNanos, System.nanoTime(), intervalMillis)) {
            sendKeepAlive().onFailure { log.warn("Scheduled keepalive send failed", it) }
        }
    }.onFailure { log.error("Could not start the scheduled keepalive", it) }

/**
 * Stops the background keepalive started by [startKeepAlive]. Idempotent and safe
 * to call even if [startKeepAlive] was never called. [stop] also does this.
 * @return [Result.success] once the schedule is cancelled
 */
fun stopKeepAlive(): Result<Unit> = runCatching { keepAlive.cancel(serverEndpoint) }
```

- `stop()` (`MultiConnectionUDPClient.kt:312-329`) — add a `keepAliveStopped`
  step (`runCatching { keepAlive.shutdown() }`) **before** `socket.close()`, and
  thread it into the final `flatMap` chain, so a scheduled `KA` never races a
  socket close. Every step still runs even if an earlier one failed.

### 2.6 Server — `Connection.startKeepAlive` / `stopKeepAlive`

**`Connection` (public interface) — default methods** (D1):

```kotlin
/**
 * Starts an opt-in, idle-aware background keepalive for this connection: every
 * ~[intervalMillis] of output silence toward [peer] the server sends one `KA`
 * datagram, until [stopKeepAlive], [terminate], or server `stop()`.
 *
 * Server -> client keepalives refresh endpoint-independent (full-cone /
 * restricted-cone) NAT mappings and verify the send path; they do **not**
 * reliably refresh port-restricted or symmetric NATs - the authoritative
 * keepalive is still the client's own (see [keepAlive]). A convenience over
 * [keepAlive], which is unchanged and still owns no timer.
 *
 * The default implementation returns [Result.failure] - only the production
 * [UDPConnection] supports a scheduled keepalive.
 *
 * @param intervalMillis output-idle time before a keepalive is sent; must be > 0.
 * Defaults to [HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS] (20 s).
 * @return [Result.success] once armed, or the failure that prevented it
 */
fun startKeepAlive(intervalMillis: Long): Result<Unit> =
    Result.failure(UnsupportedOperationException("This Connection does not support a scheduled keepalive"))

/**
 * Starts the scheduled keepalive at the recommended interval
 * ([HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS], 20 s).
 * @return [Result.success] once armed, or the failure that prevented it
 * @see startKeepAlive
 */
fun startKeepAlive(): Result<Unit> = startKeepAlive(HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS)

/**
 * Stops the background keepalive started by [startKeepAlive]. Idempotent; a no-op
 * if none is running or the implementation does not support one. [terminate] and
 * server `stop()` also do this.
 * @return [Result.success] once cancelled
 */
fun stopKeepAlive(): Result<Unit> = Result.success(Unit)
```

Two explicit overloads (not a defaulted parameter + `@JvmOverloads`, which is
awkward on an interface method) — Java callers get `startKeepAlive()` and
`startKeepAlive(long)` directly. `UDPConnection` overrides only
`startKeepAlive(Long)` and `stopKeepAlive()`; the no-arg default routes through
the override.

**`UDPConnection`:**

```kotlin
override fun startKeepAlive(intervalMillis: Long): Result<Unit> =
    channel.scheduleKeepAlive(peer, intervalMillis)
        .onFailure { log.error("Connection '{}' could not start a scheduled keepalive", name, it) }

override fun stopKeepAlive(): Result<Unit> =
    channel.cancelKeepAlive(peer)
        .onFailure { log.error("Connection '{}' could not stop its scheduled keepalive", name, it) }
```

**`ClientChannel` (internal):**

```kotlin
fun scheduleKeepAlive(peer: InetSocketAddress, intervalMillis: Long): Result<Unit>
fun cancelKeepAlive(peer: InetSocketAddress): Result<Unit>
```

**`HandshakeCoordinator`:**

- New trailing constructor collaborator
  `private val keepAliveSchedule: KeepAliveSchedule` (the server passes the real
  `KeepAliveScheduler`; tests pass a fake).
- `@Volatile private var keepAliveTracked = false` — flipped `true` the first
  time `scheduleKeepAlive` runs; never flipped back. While `false`, `send()` does
  no extra work (default-cost-zero, mirroring #10's `livenessTracked`).
- `send(bytes, to)` (`HandshakeCoordinator.kt:195`):
  ```kotlin
  override fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> {
      if (keepAliveTracked) registrations.findByOrigin(to)?.let { it.lastOutboundAt = System.nanoTime() }
      return sender(bytes, to)
  }
  ```
- `scheduleKeepAlive`:
  ```kotlin
  override fun scheduleKeepAlive(peer: InetSocketAddress, intervalMillis: Long): Result<Unit> {
      keepAliveTracked = true
      return keepAliveSchedule.schedule(peer, intervalMillis) {
          val reg = registrations.findByOrigin(peer) ?: return@schedule
          if (KeepAlive.isDue(reg.lastOutboundAt, System.nanoTime(), intervalMillis)) {
              send(HandshakeProtocol.KEEPALIVE_TOKEN.toByteArray(Charsets.UTF_8), peer)
                  .onFailure { log.warn("Scheduled keepalive to {} failed", peer, it) }
          }
      }
  }
  ```
- `cancelKeepAlive(peer)` → `runCatching { keepAliveSchedule.cancel(peer) }`.
- A private `cancelKeepAlive` call added to **both** removal paths so a scheduled
  keepalive never outlives its registration:
  - `deregister` (`HandshakeCoordinator.kt:211-219`) — after `removeByOrigin`.
  - the same-name supersede branch (`:156-162`) — after
    `registrations.removeByOrigin(stale.origin)`.
- New `fun shutKeepAlive()` → `keepAliveSchedule.shutdown()`.

**`MultiConnectionUDPServer`:**

- New `private val keepAliveScheduler = KeepAliveScheduler("mcups-keepalive")`,
  passed into the `HandshakeCoordinator(...)` construction as `keepAliveSchedule`.
  Created unconditionally as an object, but its executor/thread is still lazy —
  a server whose consumers never call `Connection.startKeepAlive` spawns no
  `mcups-keepalive` thread.
- `stop()` (`MultiConnectionUDPServer.kt:373-392`) — add a `keepAliveStopped`
  step (`runCatching { keepAliveScheduler.shutdown() }`) and thread it into the
  final `flatMap` chain, alongside `livenessStopped`.
- **Divergence from #10 §2.4's note.** #10 anticipated #12 reusing
  `mcups-liveness`. It is not reused: `mcups-liveness` exists only when
  `idleTimeoutMillis > 0`, runs a roster-wide sweep on a derived cadence, and is
  owned for a different reason. A dedicated, independently-lazy
  `mcups-keepalive` keeps each feature's lifecycle self-contained. Documented in
  the `### Concurrency` KDoc.

### 2.7 `Registration.lastOutboundAt`

```kotlin
/** Monotonic nanoTime of the last datagram the server sent to this origin (data,
 *  broadcast, or KA); seeded at construction. Written by the listener / dispatch
 *  threads via HandshakeCoordinator.send, read by the mcups-keepalive thread,
 *  hence @Volatile. Only meaningful once a scheduled keepalive is armed for the
 *  connection; a nanoTime difference, never an absolute. */
@Volatile var lastOutboundAt: Long = System.nanoTime()
```

Mirror of #10's `lastInboundAt`. A lost update between the writer and the
keepalive reader costs at most one extra poll cycle of latency — never a missed
or duplicated `KA`.

### 2.8 Flow — client scheduled keepalive, idle-aware

```mermaid
sequenceDiagram
    participant App as consumer
    participant KA as mcupc-keepalive
    participant Sock as shared socket
    participant Srv as server

    App->>KA: startKeepAlive(20_000)
    Note over KA: executor created lazily; poll = 5_000 ms

    loop every 5_000 ms
        KA->>KA: isDue(lastOutboundAtNanos, now, 20_000)?
        alt >= 20 s since last outbound
            KA->>Sock: sendKeepAlive() -> "KA"
            Sock->>Srv: KA datagram (dropped, not dispatched)
            KA->>KA: lastOutboundAtNanos = now (via send)
        else application sent recently
            Note over KA: skip - traffic already holds the mapping
        end
    end

    App->>Sock: send("hello")
    Sock->>Sock: lastOutboundAtNanos = now
    Note over KA: next tick sees < 20 s idle -> no KA

    App->>KA: stop()  (or stopKeepAlive())
    KA->>KA: future cancelled; executor shutdownNow()
```

The server-side flow is identical with `mcups-keepalive` / `Connection.keepAlive()`
/ `Registration.lastOutboundAt` substituted, and the `KA` travelling server→client.

### 2.9 Alternatives considered

| Option | Rejected because |
|---|---|
| **Plain fixed-rate `KA` every interval, not idle-aware** | The issue allows it as a first cut, but it sends redundant keepalives during application traffic and a fixed-*delay* variant overshoots to ~2x the interval. Idle-aware via a quarter-interval poll costs a few extra no-op wake-ups and bounds the overshoot; it also matches the existing `Liveness` pattern. (D2) |
| **New abstract methods on `Connection`** | `Connection` is published (Maven Central). Adding abstract methods breaks every external implementor → major bump, per the clean-break policy. The policy targets *changing/removing* signatures; *adding a defaulted member* is the additive path the repo already uses (`actuateBytes`, `push(ByteArray)`, `onClientDisconnect`). (D1) |
| **Put the server keepalive on `MultiConnectionUDPServer` keyed by connection** (`server.startKeepAlive(connection, ...)`) | Avoids touching `Connection` entirely, but `connection.startKeepAlive()` is the ergonomic shape the issue asks for and mirrors the client. The default-method route gets both. (D1) |
| **Reuse the `mcups-liveness` executor for the server keepalive** (as #10 §2.4 anticipated) | `mcups-liveness` exists only when `idleTimeoutMillis > 0`; the keepalive must work independently of idle detection. Conditional piggybacking entangles two lifecycles for no saving beyond one idle daemon thread. |
| **A `Timer` / hand-rolled thread instead of `ScheduledExecutorService`** | `ScheduledExecutorService` is already the module's idiom (#10 `mcups-liveness`); `Timer` swallows task exceptions differently and is a single point of failure across tasks. |
| **Always-on keepalive (opt-out)** | A behaviour change for every current consumer, including text-only clients behind a NAT with a long mapping timeout, or ones that already run their own timer (double `KA`). Opt-in keeps `1.5.0` a pure addition. |
| **Self-rescheduling one-shot task** (`schedule` re-arming itself for `interval - idle`) | Exact, minimal overshoot, but re-implements what `scheduleWithFixedDelay` already gives and complicates cancellation/shutdown races. The quarter-interval poll is close enough. |
| **Merge `KeepAlive` into `Liveness` / a shared `Cadence`** | Couples two independent opt-in features. Revisit if a third timer appears. |

---

## 3. File-by-file changes

All paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/`
unless noted.

### 3.1 `KeepAlive.kt` (new, internal)

The `internal object KeepAlive` from §2.3 — `pollIntervalMillis`, `isDue`, three
private constants. Level-1 KDoc on the object; Level-2 KDoc on both functions
(boundary behaviour spelled out).

### 3.2 `KeepAliveScheduler.kt` (new, internal)

`internal interface KeepAliveSchedule` + `internal class KeepAliveScheduler` from
§2.4 (co-located, as `Registrations.kt` / `HandshakeProtocol.kt` co-locate
related types). Level-2 KDoc on the interface and every method; inner-core
comments on the `scheduleWithFixedDelay` throw-cancel hazard and the `shutDown`
latch.

### 3.3 `HandshakeWireFormat.kt` (public)

- New:
  ```kotlin
  /**
   * The recommended output-idle interval, in milliseconds, between keepalive
   * datagrams on a NAT'd path (~20 s). The default for
   * [MultiConnectionUDPClient.startKeepAlive] and [Connection.startKeepAlive].
   */
  const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS = 20_000L
  ```
- `HandshakeProtocol.kt` aliases it: `const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS =
  HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS` (matches how it aliases
  `REFUSED_REPLY` / `KEEPALIVE_TOKEN`) — used by `HandshakeCoordinator`.
- Class KDoc: one sentence that the `KA` cadence is now also available as a
  managed scheduler (`startKeepAlive`), with the one-shot tokens unchanged.

### 3.4 `ClientChannel.kt` (internal)

Add `scheduleKeepAlive(peer, intervalMillis)` / `cancelKeepAlive(peer)` with
Level-2 KDoc (both return `Result<Unit>`; `cancel` is idempotent).

### 3.5 `Connection.kt` (public)

The three default methods from §2.6 (`startKeepAlive()` / `startKeepAlive(Long)` /
`stopKeepAlive()`). Because `Connection` uses two **explicit** overloads (not
`@JvmOverloads`), the no-arg `startKeepAlive()` carries its own full KDoc block —
a one-line summary, its own `@return` tag
(`[Result.success] once armed, or the failure that prevented it`), and a
`@see startKeepAlive` cross-link to the fully-documented `startKeepAlive(Long)`
overload — not just a one-liner. Update `keepAlive()`'s KDoc
(`Connection.kt:114-127`): it remains the one-shot primitive; point at
`startKeepAlive` for the managed equivalent. Class KDoc: note that `UDPConnection`
is still socket-free but a scheduled keepalive borrows the server's
`mcups-keepalive` thread.

### 3.6 `UDPConnection.kt` (public, internal ctor)

Override `startKeepAlive(Long)` and `stopKeepAlive()` per §2.6 (delegate to
`channel`, log on failure). Class KDoc (`UDPConnection.kt:6-15`): "owns no socket
and no thread of its own" gains a caveat — a scheduled keepalive runs on the
server's shared `mcups-keepalive` thread, cancelled by `terminate()`.

### 3.7 `Registrations.kt` (internal)

`Registration`: add `@Volatile var lastOutboundAt: Long = System.nanoTime()`
(§2.7) with a `@property` KDoc entry; note the writer/reader threads.

### 3.8 `HandshakeCoordinator.kt` (internal)

- **Constructor** (`:50-58`): new trailing `private val keepAliveSchedule:
  KeepAliveSchedule`. KDoc `@param`.
- **`send`** (`:195`): the `keepAliveTracked`-guarded `lastOutboundAt` stamp
  (§2.6).
- **New** `@Volatile private var keepAliveTracked = false`.
- **Implement** `ClientChannel.scheduleKeepAlive` / `cancelKeepAlive` (§2.6).
- **`deregister`** (`:211-219`) and the **supersede branch** (`:156-162`): add a
  `keepAliveSchedule.cancel(<origin>)` call after the `removeByOrigin`.
- **New** `fun shutKeepAlive()` → `keepAliveSchedule.shutdown()`.
- Class KDoc (`:6-32`): add a fifth responsibility bullet — it is also the
  **keepalive scheduler seam**: `scheduleKeepAlive` arms an idle-aware `KA` timer
  per connection (refreshing off `Registration.lastOutboundAt`), cancelled on
  `deregister` / supersede.

### 3.9 `MultiConnectionUDPServer.kt` (public)

- New `private val keepAliveScheduler = KeepAliveScheduler("mcups-keepalive")`;
  pass it into the `coordinator` construction (`:160-173`) as `keepAliveSchedule`.
- `stop()` (`:373-392`): new `keepAliveStopped` step + `flatMap` wiring.
- Class KDoc:
  - `### Concurrency` (`:104-121`): a **fourth** daemon thread —
    `mcups-keepalive`, a `ScheduledExecutorService`, exists **only once a
    consumer calls `Connection.startKeepAlive`**; it sends `KA` datagrams and
    runs no application code. Note it is *not* the `mcups-liveness` executor
    (§2.6). Add: *"See the scheduled-keepalive sequence diagram in
    docs/issue-12-scheduled-keepalive-plan.md §2.8."*
  - `### Construction side effects`: unchanged — the keepalive scheduler binds
    nothing and starts no thread at construction.
  - New `### Scheduled keepalive` section: opt-in `Connection.startKeepAlive` /
    `stopKeepAlive`, idle-aware, server→client cone-NAT caveat, stopped by
    `terminate()` / `stop()`.

### 3.10 `MultiConnectionUDPClient.kt` (public)

- **Constructor seam** (§2.5): convert the primary constructor to an `internal`
  4-arg one taking `keepAlive: KeepAliveSchedule`, and add a public
  `@JvmOverloads` secondary constructor with today's three parameters that
  delegates with `KeepAliveScheduler("mcupc-keepalive")`. The three public
  constructor signatures are unchanged; the `require(receiveBufferBytes ...)`
  `init` guard is unchanged. This mirrors the server's injected
  `HandshakeCoordinator.keepAliveSchedule` so the client tick is Level-2
  testable.
- New `lastOutboundAtNanos` field and `serverEndpoint` (§2.5).
- `send(ByteArray)` (`:295-297`): stamp `lastOutboundAtNanos` after
  `socket.send`.
- New `@JvmOverloads fun startKeepAlive(intervalMillis: Long = …)` and
  `fun stopKeepAlive()` (§2.5) — the tick is
  `if (KeepAlive.isDue(lastOutboundAtNanos, System.nanoTime(), intervalMillis)) sendKeepAlive()`.
- `stop()` (`:312-329`): `keepAliveStopped` step before `socket.close()`, wired
  into the `flatMap` chain.
- KDoc: `sendKeepAlive()` (`:299-304`) stays the one-shot primitive, cross-ref
  `startKeepAlive`. Class KDoc `### Concurrency` (`:30-41`): a third daemon thread
  (`mcupc-keepalive`), created only on `startKeepAlive`, shut by `stop()`; add
  *"See the scheduled-keepalive sequence diagram in
  docs/issue-12-scheduled-keepalive-plan.md §2.8."*
  "Concurrent-call contract": `startKeepAlive` / `stopKeepAlive` are safe from any
  thread and may race `stop()` (a `startKeepAlive` after `stop()` fails its
  `Result`; an in-flight tick either sends or observes the closed socket and
  fails its own `Result`).

### 3.11 Test-support fixtures

`webtools-udp/src/test/kotlin/com/spartanlabs/testing/support/webtools/udp/`

- `FakeClientChannel.kt`: implement `scheduleKeepAlive` / `cancelKeepAlive` —
  record `(peer, intervalMillis)` schedule calls and `peer` cancels; return a
  configurable `Result`. Optionally capture the `tick` is not applicable (the
  interface passes no lambda through `ClientChannel`; the tick lives in the
  coordinator), so recording the call args is enough.
- **New** `FakeKeepAliveSchedule.kt`: an `internal class` implementing
  `KeepAliveSchedule` — records `schedule(key, intervalMillis, tick)` (keeping the
  `tick` so a test can invoke it synchronously), `cancel(key)`, `shutdown()`;
  configurable `Result` (incl. a "fail after shutdown" mode). Lets **both** the
  `HandshakeCoordinator` tests and the `MultiConnectionUDPClient` tests (via the
  client's `internal` constructor seam) drive keepalive logic with no real timer.
- `FakeConnection.kt`: add `startKeepAlive` / `stopKeepAlive` overrides that count
  calls (so server-level tests can assert a subclass called them), returning a
  configurable `Result`.

### 3.12 `README.md` (repo root)

- Install snippet (`:19`): `webtools-udp:1.4.0` → `1.5.0`.
- Components table: `MultiConnectionUDPClient` row — note `startKeepAlive` /
  `stopKeepAlive`; `Connection` row — add `startKeepAlive` / `stopKeepAlive` to
  the method list.
- "UDP handshake protocol" section, the `KA` paragraph (`:104-110`): the client
  may either call `sendKeepAlive()` on its own timer **or** call `startKeepAlive()`
  once and let the library time it; same for `Connection` server-side.
- "Client-side usage" block (`:216-228`): replace
  `client.sendKeepAlive()   // call on a ~20s idle cadence` with
  `client.startKeepAlive()   // library times it; no caller timer needed`, and
  keep one line noting `sendKeepAlive()` is still there for callers who want to
  drive it themselves.
- New `### Scheduled keepalive` subsection (after "Handshake screening &
  credentials"): opt-in on both sides, idle-aware, default 20 s, one daemon
  thread per side created on first use and shut by `stop()` / `terminate()`, the
  one-shot primitives unchanged, no wire change, server→client cone-NAT caveat.
  Short code sketch mirroring the other subsections.

### 3.13 `webtools-udp/build.gradle.kts`

- `version` (`:14`): `"1.4.0"` → `"1.5.0"`.
- Version comment block (`:4-13`): add
  ```
  // 1.5.0: opt-in idle-aware scheduled keepalive - MultiConnectionUDPClient.startKeepAlive /
  // stopKeepAlive and Connection.startKeepAlive / stopKeepAlive, each backed by a lazily-created
  // daemon ScheduledExecutorService; the one-shot sendKeepAlive() / keepAlive() primitives
  // unchanged; no wire change (Issue #12).
  ```

### 3.14 `docs/issue-12-scheduled-keepalive-plan.md`

This document. Committed in the same commit as §3.1–§3.12 (stage 1). Backfill the
`Commit:` / `PR:` header fields once they exist (as done for #8/#9/#10/#11).

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves with this change |
|---|---|---|
| **Inner core** (in-editor) | yes | Comments: why quarter-interval poll not fixed-rate; the `scheduleWithFixedDelay` throw-cancel hazard (→ `runCatching` in the tick); `@Volatile lastOutboundAt(Nanos)` single-writer/single-reader reasoning; the `shutDown` latch preventing executor resurrection after `stop()`; why the server keepalive does not reuse `mcups-liveness`. |
| **Component ring** (KDoc) | yes | New KDoc: `KeepAlive.pollIntervalMillis` / `isDue`, `KeepAliveSchedule` / `KeepAliveScheduler`, `HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS`, `ClientChannel.scheduleKeepAlive` / `cancelKeepAlive`, `Connection.startKeepAlive(Long)` / `stopKeepAlive`, `UDPConnection` overrides, `Registration.lastOutboundAt`, `HandshakeCoordinator` `@param keepAliveSchedule` + `shutKeepAlive`, `MultiConnectionUDPClient` `internal` constructor `@param keepAlive` + `startKeepAlive` / `stopKeepAlive`. The no-arg `Connection.startKeepAlive()` overload gets its **own** KDoc block (summary + `@return` + `@see startKeepAlive`), since `Connection` uses two explicit overloads rather than `@JvmOverloads`. Updated KDoc: `Connection.keepAlive`, `MultiConnectionUDPClient.sendKeepAlive` (cross-ref the managed form). |
| **Boundary ring** (protocol) | **no wire change** | The `KA` token, its drop-on-receipt semantics, and "the client's keepalive is authoritative" are unchanged. One clarifying sentence in `HandshakeWireFormat` / README that the cadence can now be library-managed. |
| **Architectural outer layer** | yes | One new opt-in daemon thread per side (`mcupc-keepalive`, `mcups-keepalive`), created only on first `startKeepAlive`, shut on `stop()`. Documented in both `### Concurrency` KDocs, **each of which adds a one-line pointer**: *"See the scheduled-keepalive sequence diagram in docs/issue-12-scheduled-keepalive-plan.md §2.8"* (§2.8 is the canonical flow — the same convention `MultiConnectionUDPClient` / `MultiConnectionUDPServer` already use to point at the issue-1/issue-3/issue-10 diagrams). |
| **README** | yes | Per the README-currency rule: new public methods on two types, new public constant, new (conditional) thread per side, changed usage guidance, dependency-coordinate bump. |

---

## 5. Test plan (5-Level hierarchy)

Package root mirrors production `com.spartanlabs.webtools.udp` →
`com.spartanlabs.testing.<level>.webtools.udp`. One class per file; each class
carries its level `@Tag`. Socket-binding classes run under the module's
`commonUdpPortLock`. Keepalive intervals in automated tests are small
(200–400 ms) with the poll floored at 250 ms; timing assertions use generous
slack and "at least N over a window" rather than exact counts.

### Level 1 — `testing.gating`

- **Extend `HandshakeWireFormatGatingTest.kt`** (socket-free):
  `DEFAULT_KEEPALIVE_INTERVAL_MILLIS == 20_000L`.
- **New `KeepAliveGatingTest.kt`** (socket-free smoke): `isDue` true when the
  gap ≥ interval and false when fresh; `isDue` true for interval ≤ 0;
  `pollIntervalMillis` within `250..5000` for a spread of inputs.
- **New `KeepAliveValidationGatingTest.kt`**: `MultiConnectionUDPClient`
  (ephemeral port, no common-port lock needed) — `startKeepAlive(0)` and
  `startKeepAlive(-1)` return `Result.failure(IllegalArgumentException)` and arm
  nothing (a fake peer socket receives no `KA` in a short window); then `stop()`.
  A `UDPConnection` over a `FakeClientChannel` — `startKeepAlive(0)` surfaces the
  channel's `IllegalArgumentException` failure.

### Level 2 — `testing.component`

- **Extend `RegistrationsTest.kt`**: `Registration.lastOutboundAt` is within a
  few ms of `System.nanoTime()` at construction and is independently mutable.
  (The pure `KeepAlive` rules are tested at Level 4a, not here; Level 2 covers
  collaborator wiring only.)
- **New `UDPConnectionKeepAliveTest.kt`** (or extend `UDPConnectionTest.kt`) over
  `FakeClientChannel`:
  - `startKeepAlive(5_000)` calls `channel.scheduleKeepAlive(peer, 5_000)` once
    and returns its `Result`;
  - `startKeepAlive()` (no arg) passes `DEFAULT_KEEPALIVE_INTERVAL_MILLIS`;
  - `stopKeepAlive()` calls `channel.cancelKeepAlive(peer)`;
  - a `Result.failure` from the channel is propagated and logged (assert via
    `LogCapture`).
- **New `MultiConnectionUDPClientKeepAliveTest.kt`** — constructs the client via
  its `internal` 4-arg constructor with a `FakeKeepAliveSchedule` (no wall
  clock, no real timer). Locks the client's idle-aware tick, the client-side
  mirror of the coordinator test above:
  - `startKeepAlive(300)` records exactly one `schedule(serverEndpoint, 300, tick)`
    on the fake; `startKeepAlive()` records interval
    `DEFAULT_KEEPALIVE_INTERVAL_MILLIS`;
  - invoking the recorded `tick` with `lastOutboundAtNanos` forced into the past
    (`> interval` ago) results in **exactly one** `sendKeepAlive()` (assert via a
    fake peer `DatagramSocket` receiving one `KA`, or a spy on the send path);
  - invoking the `tick` with `lastOutboundAtNanos` fresh (just stamped by a
    preceding `send`) results in **zero** `KA` sends (idle-aware);
  - a repeat `startKeepAlive(500)` records a second `schedule` for the same key
    (last-call-wins — the fake also records the preceding `cancel`);
  - `stopKeepAlive()` records `cancel(serverEndpoint)`; `stop()` records
    `shutdown()`;
  - `startKeepAlive` after `stop()` returns `Result.failure` and records no new
    `schedule` (the real `KeepAliveScheduler.shutDown` latch is exercised at
    Level 3; here the fake is configured to fail post-`shutdown`).
- **Extend `HandshakeCoordinatorTest.kt`** (synchronous `dispatch = { it() }`,
  `FakeKeepAliveSchedule`):
  - update `newCoordinator()` and every inline `HandshakeCoordinator(...)` for the
    new `keepAliveSchedule` param;
  - `scheduleKeepAlive(peer, 300)` flips tracking on: a subsequent `send`/
    `broadcast` to that peer advances `Registration.lastOutboundAt`; before the
    first `scheduleKeepAlive`, `send` leaves it at its construction value;
  - the recorded `tick`, invoked manually when `lastOutboundAt` is forced into
    the past, sends exactly one `KA` datagram to `peer`; invoked when
    `lastOutboundAt` is fresh, sends nothing (idle-aware);
  - the `tick` for a peer whose registration has since been removed is a no-op
    (no throw, no send);
  - `deregister(peer)` calls `keepAliveSchedule.cancel(peer)`;
  - a same-name `Iam` supersede calls `keepAliveSchedule.cancel(staleOrigin)`;
  - `shutKeepAlive()` calls `keepAliveSchedule.shutdown()`;
  - a throwing `tick` does not escape (locked at the `KeepAliveScheduler` level in
    Level 3, but assert the coordinator's `send`-failure `onFailure` logs rather
    than throws).
- **`KeepAliveScheduler` at Level 2 covers only the socket-free, timer-free
  validation paths** of `schedule` — a non-positive interval fails its `Result`
  with `IllegalArgumentException`; a `schedule` after `shutdown()` fails with
  `IllegalStateException`. Its real-timer behaviour (ticks firing, cancel,
  replace, shutdown, thread lifecycle) is tested at Level 3, since it drives a
  real `ScheduledExecutorService`.

### Level 3 — `testing.integration` (real threads / sockets)

- **New `KeepAliveSchedulerTest.kt`** (real `ScheduledExecutorService`, no
  socket): a scheduled `tick` fires repeatedly at roughly the poll cadence;
  `cancel(key)` stops that key's ticks while others continue; a second
  `schedule` for the same key replaces the first (only one tick stream);
  `shutdown()` stops all ticks and leaves no live thread named as configured
  (`Thread.getAllStackTraces`); `schedule` after `shutdown()` fails its `Result`;
  a `tick` that throws does not cancel the repeating task (a later tick still
  fires).
- **New `MultiConnectionUDPClientKeepAliveTest.kt`** (real client socket, fake
  peer `DatagramSocket`, mirrors `MultiConnectionUDPClientTest` setup):
  - after `handshake` (fake peer replies `REGISTERED`) and
    `startKeepAlive(300)`, the fake peer receives `KA` datagrams roughly every
    300 ms while the client is otherwise idle;
  - calling `client.send("x")` more often than the interval suppresses `KA`
    (idle-aware) — the peer sees the data but no `KA` across the window;
  - `stopKeepAlive()` halts the `KA` stream; a later `startKeepAlive(300)`
    resumes it;
  - `stop()` halts the `KA` stream and leaves no `mcupc-keepalive` thread;
  - repeated `startKeepAlive` with a different interval → last wins (cadence
    changes, single stream);
  - `startKeepAlive` after `stop()` → `Result.failure`, no thread.
- **New `MultiConnectionUDPServerKeepAliveTest.kt`** (real server, own instance,
  `@AfterTest { server?.stop() }`, real client socket standing in for a peer):
  - a client handshakes; the test's `onClientConnect` calls
    `connection.startKeepAlive(300)`; the client's socket receives server→client
    `KA` datagrams while idle;
  - server `pushToAll` / `connection.push` traffic suppresses the next `KA`
    (idle-aware off `lastOutboundAt`);
  - `connection.stopKeepAlive()` halts them; `connection.terminate()` also halts
    them (and cancels the schedule);
  - a default server whose consumers never call `startKeepAlive` has no
    `mcups-keepalive` thread (`Thread.getAllStackTraces`);
  - `server.stop()` leaves no `mcups-keepalive` thread.

### Level 4a — `testing.deterministic`

- **New `KeepAliveTest.kt`** (pure, mirrors `LivenessTest.kt`):
  - `isDue` — true for `intervalMillis <= 0` regardless of the gap; boundary at
    exactly the interval (`>=`), just under, well over; a `now` before
    `lastOutboundAt` yields false; no overflow at `Long.MAX_VALUE`;
  - `pollIntervalMillis` — clamps to 250 for small/zero intervals, to 5000 for
    large, returns `interval / 4` in the mid band, never outside `250..5000`
    (including negative and `Long.MAX_VALUE` inputs).

### Level 4b — `testing.e2e`

- **New `MultiConnectionUDPKeepAliveE2ETest.kt`** — real `MultiConnectionUDPClient`
  + real `MultiConnectionUDPServer` subclass over loopback:
  - **the headline guarantee:** server constructed with
    `idleTimeoutMillis = 1_200` (≈4x the KA interval); client `handshake`s,
    `start`s, and calls **only** `startKeepAlive(300)` — no hand-rolled timer.
    Over a window several times the idle timeout, the server's
    `onClientDisconnect(_, TIMEOUT)` never fires and `pushToAll` keeps reaching
    the client. Stopping the keepalive (`client.stopKeepAlive()`) then lets
    `TIMEOUT` fire — proving the scheduler was the only thing keeping it alive.
  - **server→client:** `onClientConnect` calls `connection.startKeepAlive(300)`;
    the client's `start` handler never receives a `KA` (dropped) but the client
    stays reachable; `terminate()` stops the stream.
  - **both sides at once:** client and server each `startKeepAlive`; a bidirectional
    idle session survives well past both intervals; `client.stop()` /
    `server.stop()` are clean and thread-free.
  - **primitive still works:** a client that uses `sendKeepAlive()` on its own
    `ScheduledExecutorService` (the pre-1.5.0 pattern) behaves exactly as before.

### Level 4c — `testing.nonfunctional`

- **New `MultiConnectionUDPClientKeepAliveNonFunctionalTest.kt`**:
  - 200 `startKeepAlive` / `stopKeepAlive` cycles leave at most one
    `mcupc-keepalive` thread and no leaked `ScheduledFuture` (a final
    `startKeepAlive` still delivers `KA`);
  - a wedged `start` message handler (sleeps 2 s) does not delay `KA` — the
    keepalive thread is independent of `mcupc-dispatch`;
  - overshoot bound: with `interval = 300`, measured time from last outbound to
    the next `KA` is `< 300 + pollInterval + slack` across many cycles;
  - a scheduled `KA` racing `stop()` never throws out of either call.
- **New `MultiConnectionUDPServerKeepAliveNonFunctionalTest.kt`**:
  - 50 connections each `startKeepAlive` → one shared `mcups-keepalive` thread,
    every peer receives `KA`;
  - `terminate()` on any connection stops its `KA` within a poll cycle and does
    not disturb the others;
  - a `sender` that intermittently fails (simulated) is logged at WARN and never
    cancels the repeating task;
  - detection cost when unused: a `KA`/data burst against a server whose
    consumers never call `startKeepAlive` does not touch `Registration.lastOutboundAt`
    (assert via an `internal` read) and starts no thread.

### Level 5 — `testing.uat`

- **Extend `MultiConnectionUDPServerUatTest.kt`** (or the client UAT) with one
  `@Disabled` scenario: a real NAT'd client that calls `handshake` → `start` →
  `startKeepAlive()` and **nothing else** (no application traffic, no manual
  timer) stays reachable from a public server across a 5–10 minute idle period;
  `pushToAll` from the server lands throughout; `client.stop()` ends it cleanly.
  PASS = no mid-session unreachability and no consumer-side timer code.

### Cannot be automated

- Real NAT mapping lifetimes and whether the 20 s default (or the
  `interval + pollInterval` worst case) actually holds a given carrier / CPE
  mapping open — only a Level-5 run on a real path shows this.
- Server→client keepalive effectiveness across the different NAT filtering
  behaviours (full-cone vs port-restricted vs symmetric) — loopback cannot model
  it.
- Long-horizon scheduler-thread cost at consumer scale (thousands of connections,
  days).

---

## 6. Risks & edge cases

- **No wire-format change** — the primary safety property. `KA` is byte-identical;
  an old peer, an old server, and a `1.5.0` peer are mutually compatible in every
  direction. A `1.5.0` client against a `1.3.x` server still just sends `KA`.
- **New opt-in thread per side.** `mcupc-keepalive` / `mcups-keepalive` are
  daemons, created only on the first `startKeepAlive`, and shut in `stop()`. A
  `stop()` that throws midway still reaches the keepalive shutdown step (each
  teardown step is independent, as with `livenessExecutor` in #10). Locked by the
  "no live thread after stop" tests at Levels 3 and 4c.
- **`Connection` interface gains members.** Default bodies → source- and
  binary-compatible; no existing subclass or caller changes. The only production
  implementation (`UDPConnection`, `internal` constructor) overrides them; an
  external `Connection` implementation (unlikely — the interface exists mainly for
  test fakes) inherits the "unsupported" default and need not recompile. Call this
  out in the PR body. (D1)
- **`@JvmOverloads` / overload correctness is load-bearing.** Before tagging,
  `javap` the built classes and confirm:
  - `MultiConnectionUDPClient`: `startKeepAlive()` and `startKeepAlive(long)`;
  - `Connection`: `startKeepAlive()`, `startKeepAlive(long)`, `stopKeepAlive()`
    present with default implementations (Kotlin `DefaultImpls` or JVM default
    methods, matching the module's existing `-Xjvm-default` setting).
  (Same pre-tag step as #8/#9/#10/#11.)
- **Idle-aware overshoot.** A `KA` can go out up to `interval + pollInterval`
  (≤ interval + 5 s) after the last outbound datagram. Documented in the
  `startKeepAlive` KDoc and README. A consumer needing a tighter bound passes a
  smaller `intervalMillis`.
- **`scheduleWithFixedDelay` cancels a throwing task permanently.** The `tick`
  body is wrapped in `runCatching` in `KeepAliveScheduler`; the coordinator's
  send failure is an `onFailure` log, not a throw. Locked by the Level-3
  "throwing tick still fires later" test.
- **Extra `findByOrigin` scan per server send when a keepalive is armed.** O(n)
  over the copy-on-write registration list, gated by `keepAliveTracked` so the
  default path is untouched — same trade-off #10 accepted for `lastInboundAt`.
  `keepAliveTracked` never flips back to `false`; a server that armed then
  cancelled all keepalives keeps paying the scan. Acceptable for expected roster
  sizes; noted as a deferred micro-optimisation (§9).
- **`startKeepAlive` racing `stop()`.** The `shutDown` latch in
  `KeepAliveScheduler` makes a post-`stop()` `startKeepAlive` fail its `Result`
  rather than spawn a doomed executor. An in-flight tick during `stop()` either
  completes its send or observes the closed socket and fails its own `Result`
  (logged, harmless).
- **Repeat `startKeepAlive`.** Last call wins (replaces the schedule) — matches
  `MultiConnectionUDPClient.start` / `startBytes`. A consumer that calls it in a
  loop just re-arms; no accumulation of timers. (D3)
- **`lastOutboundAt(Nanos)` concurrency.** `@Volatile`, effectively
  single-writer (send paths) / single-reader (keepalive thread). A lost update
  costs one poll cycle of latency, never a missed or duplicated `KA`. No new
  lock. Monotonic `nanoTime()`, never wall-clock — an NTP/DST step cannot
  fabricate or suppress a keepalive.
- **Client `send` failure path.** `lastOutboundAtNanos` is stamped only after a
  successful `socket.send`; a failed send does not defer the next `KA`, which is
  correct (nothing reached the wire).
- **Cross-repo impact.** None forced. No wire, protocol, or shared-type change.
  GameTools and its client project drop their hand-rolled timers on their own
  schedule; downstream adoption is that repo's concern and is not a gate on
  closing #12.

---

## 7. Version

**`webtools-udp` `1.4.0` → `1.5.0`.**

- Additive only: one new public constant
  (`HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS`), three new
  default-bodied `Connection` methods, two new `MultiConnectionUDPClient` methods
  (`@JvmOverloads`), new `internal` helpers (`KeepAlive`, `KeepAliveScheduler` /
  `KeepAliveSchedule`), one new `internal` `Registration` field, two new
  `internal` `ClientChannel` methods. Nothing removed; no existing public
  signature changed; **no wire-format change**.
- The versioning convention maps an addition to a **third-number bump** — not a
  trailing-letter suffix (bugfix only), not a major (no breaking change). Matches
  the #10 / #11 shape: implementation commit(s), then a
  `build: bump webtools-udp to 1.5.0` commit.
- **Before tagging:** run the `javap` overload check (§6).

---

## 8. Decisions (resolved)

All five were resolved by the maintainer against the "best long-term / least
future rework" criterion. **No decision remains open**; §3 is executable as
written. Each is stated so it can be reverted with a one-line instruction.

- **D1 — How `Connection` gains the scheduled keepalive. RESOLVED: (a) default
  interface methods on `Connection`** (`startKeepAlive()` / `startKeepAlive(Long)`
  / `stopKeepAlive()` with default bodies; `UDPConnection` overrides
  `startKeepAlive(Long)` and `stopKeepAlive()`). Non-breaking now; the module's
  established idiom for additive interface growth (`actuateBytes`,
  `push(ByteArray)`, `onClientDisconnect`); no concrete future driver would force
  these specific methods to become abstract. Idle-awareness needs internal
  `lastOutbound` state, so an external wrapper is not viable — the methods belong
  on the type. Rejected: **(b) abstract methods** — forces a major bump and
  breaks every external `Connection` implementor for a QoL helper; **(c) put it
  on `MultiConnectionUDPServer` keyed by connection** — clumsier call site,
  asymmetric with the client.

- **D2 — Idle-aware vs plain fixed-rate. RESOLVED: idle-aware now, on both
  sides.** The quarter-interval poll + `lastOutboundAt` clock (`KeepAlive` helper,
  `Registration.lastOutboundAt`, the `keepAliveTracked`-guarded `send` stamp)
  mirrors #10's `Liveness` / `lastInboundAt` exactly and bounds keepalive
  overshoot to `interval + pollInterval`. The issue's "plain fixed-rate is an
  acceptable first cut" is not taken.

- **D3 — Repeat `startKeepAlive` semantics. RESOLVED: (a) last call wins** — a
  repeat call cancels any existing schedule for the key and re-arms with the new
  interval. Consistent with `MultiConnectionUDPClient.start` / `startBytes`.

- **D4 — Scope. RESOLVED: both client and server sides now**, in one PR. The
  shared `KeepAlive` helper and `KeepAliveScheduler` back both; splitting would
  duplicate that work across two PRs.

- **D5 — Default interval value and home. RESOLVED:
  `const val DEFAULT_KEEPALIVE_INTERVAL_MILLIS = 20_000L` on `HandshakeWireFormat`**
  — the published constants home the client already imports; `HandshakeProtocol`
  aliases it for internal use. Rejected: a `MultiConnectionUDPServer` companion
  constant or a new public `KeepAlive` constant (more scattered).

**QA amendment (post-implementation review).** Applying D1's "least future
rework" criterion to testability: `MultiConnectionUDPClient` gets the **same
injected `KeepAliveSchedule` seam** the server side already has (an `internal`
4-arg constructor; public constructor signatures unchanged — §2.5, §3.10), so its
idle-aware tick (`KeepAlive.isDue → sendKeepAlive`) is Level-2 unit-testable via
`FakeKeepAliveSchedule` rather than only wall-clock integration-tested (§5
Level 2). No behaviour or public-API change.

**QA amendment 2 (second review pass — accepted, non-blocking).** Two of the
seven tests in
`testing/component/webtools/udp/MultiConnectionUDPClientKeepAliveTest.kt` assert
the keepalive tick fired by observing a real loopback UDP round-trip (a Level-3
technique) rather than a pure spy. Accepted as-is and recorded here as a known,
non-blocking follow-up: reworking them to a spy needs a new production send-seam
on `MultiConnectionUDPClient` (out of scope for #12), and the same round-trip is
already covered by the Level-3 twin
(`testing/integration/webtools/udp/MultiConnectionUDPClientKeepAliveTest.kt`).
The tick's decision logic (`KeepAlive.isDue`, idle-aware suppression,
last-call-wins, post-`stop()` failure) is unit-tested through the
`KeepAliveSchedule` seam in the file's other five tests. Both QA reviewers
labelled this non-blocking polish.

## 9. Sequencing & follow-ups

**Order of operations (all on `feat/issue-12-scheduled-keepalive`):**

1. **Stage 1 — production + docs, one commit.** §3.1–§3.12 (`KeepAlive`,
   `KeepAliveScheduler`, `HandshakeWireFormat`, `ClientChannel`, `Connection`,
   `UDPConnection`, `Registrations`, `HandshakeCoordinator`,
   `MultiConnectionUDPServer`, `MultiConnectionUDPClient`, test-support fixtures),
   §3.12 README, **plus this plan document**.
   `./gradlew :webtools-udp:compileKotlin` green; the existing test suite compiles
   only after Stage 2's coordinator-constructor updates, so run
   `:webtools-udp:build` at the end of Stage 2.
2. **Stage 2 — tests, one commit.** §5 all levels, plus the
   `HandshakeCoordinatorTest` / `HandshakeCoordinatorGatingTest` constructor-call
   updates for the new `keepAliveSchedule` param. Every level task green. Run the
   `javap` overload check and paste the output into the PR.
3. **Stage 3 — version bump, one commit.** §3.13 (`build.gradle.kts` → `1.5.0`).
4. **After merge:** backfill this doc's `Commit:` / `PR:` header fields with the
   real SHA(s) and PR number. Comment on #12 that the library-side change has
   landed and close it (GameTools / its client project drop their hand-rolled
   timers on their own schedule, tracked separately).

**Deliberately deferred (not designed here):**

- The single-lookup refactor of `HandshakeCoordinator.accept` / `send` (resolve
  the `Registration` once and thread it through), so `lastInboundAt` +
  `lastOutboundAt` share one scan — a micro-optimisation, only if profiling a
  high-rate many-client server shows the double scan matters.
- Letting `keepAliveTracked` (and #10's `livenessTracked`) flip back off when the
  last consumer of the feature disarms — currently one-way for simplicity.
- Consolidating `KeepAlive` and `Liveness` into a shared `Cadence` helper — only
  if a third timer appears.
- A "keepalive failed N times consecutively" signal to the application (the
  send `Result` is only logged today) — separate concern, revisit with #13
  (per-connection RTT / loss).
- Exposing `lastOutboundAt` / `idleMillis` on the public `Connection` — same
  reasoning as #10 D4; batch with #13's per-connection accessors if wanted.

---

## 10. Version control

- **Branch:** `feat/issue-12-scheduled-keepalive` off `master`.
- **Working tree is clean** as of planning (verified) — no unrelated pre-existing
  changes to carve into a separate commit.
- **Commit sequence** (each a coherent unit):
  1. `feat: add an opt-in idle-aware scheduled keepalive to the webtools-udp client and Connection (Issue #12)` — §3.1–§3.12 + this plan document.
  2. `test: cover the opt-in scheduled keepalive across all levels (Issue #12)` — §5 all levels; includes the `javap` overload verification.
  3. `build: bump webtools-udp to 1.5.0 for the opt-in scheduled keepalive (Issue #12)` — §3.13.
- The plan document rides in commit 1 (the first implementation stage) so
  `git log --follow docs/issue-12-scheduled-keepalive-plan.md` permanently binds
  plan to implementation.
- **Commit trailer:** none. Attribution is off for this repo — commits carry no
  `Co-Authored-By` / `Claude-Session` / "Generated with" trailer, and the PR
  description carries no attribution footer.
- Open the PR against `master`. Do not commit, push, or open the PR until the
  maintainer asks.
