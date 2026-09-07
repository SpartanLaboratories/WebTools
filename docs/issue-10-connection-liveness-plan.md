# Issue #10 — connection-liveness detection + a disconnect event

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#10` — *"No connection-liveness
  detection or disconnect event"* (label: `enhancement`). The server tracks no
  per-connection last-inbound time and never signals that a client has gone
  silent, so a crashed / powered-off / out-of-range client is addressed by
  `pushToAll` and broadcasts indefinitely and is only ever removed by explicit
  `Connection.terminate()` or a same-name `Iam` supersede.
- **Scope of this plan:** server side only — `MultiConnectionUDPServer` /
  `HandshakeCoordinator` / `Registrations`. Track `lastInboundAt` per registered
  connection (updated on **any** inbound datagram, `KA` included), add an opt-in
  idle threshold, and fire an overridable `onClientDisconnect(connection, reason)`
  hook on the dispatch executor. **Notify-only** — a timeout never auto-terminates
  the connection.
- **Module:** `webtools-udp` only (`io.github.spartanlaboratories:webtools-udp`,
  package `com.spartanlabs.webtools.udp`). No wire / protocol change. No change to
  `MultiConnectionUDPClient`, `Connection`, `UDPConnection`, `UDPSendReceiveServer`.
- **Branch:** `feat/issue-10-connection-liveness` (off `master`).
- **Commit:** `TBD` — this plan document is committed **in the same commit as the
  first implementation stage** (§10) so `git log --follow
  docs/issue-10-connection-liveness-plan.md` binds plan to implementation.
- **PR:** `TBD`.
- **Status:** **ready to execute.** All six design decisions resolved by the
  maintainer (§8), each in favour of the plan's recommendation. The only
  outstanding fields are the post-merge commit / PR SHAs.
- **Target version:** `webtools-udp` `1.2.0` → **`1.3.0`** (non-breaking additive
  change; see §7).
- **Related (boundaries noted, not designed here):**
  - **#12** — opt-in scheduled keepalive. Wants a daemon `ScheduledExecutorService`
    on the client / per-`Connection`. This plan introduces the server-side
    `mcups-liveness` `ScheduledExecutorService` (§2.4); #12's server-side half, if
    it lands, reuses that executor rather than adding a third.
  - **#13** — per-connection RTT / packet-loss estimates. Also wants
    `lastInboundAt`-style per-connection state and a probe timer. This plan adds
    exactly one field (`Registration.lastInboundAt`) and keeps it server-internal;
    #13 extends `Registration` with its counters and may then extract a shared
    `ConnectionStats` holder. See §8 D6.
  - `docs/issue-5-prune-stale-registrations-plan.md` — the supersede / `terminate`
    removal paths this plan hooks a callback onto.
  - `docs/issue-8-binary-datagram-path-plan.md` §2.3 / §9 — the `@JvmOverloads`
    binary-compatibility pattern and the `open`-method interface-evolution pattern
    reused here.
  - `docs/issue-1-tier-2-plan.md` §2.1 — the canonical inbound-datagram flow the
    §2.8 diagram extends.

---

## 1. Context

### 1.1 Root cause (current source, read directly)

There is no per-connection receipt-time state and no timer anywhere on the server:

| Fact | Location |
|---|---|
| `Registration` holds only `onMessage` / `onBytes` (both `@Volatile`) — no timestamp, no liveness flag | `Registrations.kt:20-28` |
| `HandshakeCoordinator.accept` classifies every datagram (`KA` → drop, `Iam` → handshake, else → `deliverData`) and **never records that a datagram arrived** | `HandshakeCoordinator.kt:60-67` |
| a `KA` datagram is consumed with only a `log.trace` — no state change | `HandshakeCoordinator.kt:61-62` |
| the server runs exactly two threads: one daemon listener (`receiveLoop`) and one daemon dispatch thread (`mcups-dispatch`). No scheduled/timer thread exists | `MultiConnectionUDPServer.kt:103-104`, `:121-127` |
| a `Registration` is removed only by `deregister` (via `Connection.terminate()`) or by the same-name supersede branch | `HandshakeCoordinator.kt:90-95`, `:144-148` |
| `onClientConnect` is the only lifecycle hook; it is `abstract` and runs on the **listener** thread | `MultiConnectionUDPServer.kt:150-160` |

Consequence: a client that stops responding without calling `terminate()` (crash,
power loss, egress failure, NAT-mapping expiry) stays registered forever. It is
addressed by every `pushToAll` / `broadcast`, counts toward `coordinator.size`,
and occupies a `Registration` for the life of the server process. The application
has no signal to react to.

### 1.2 Requirements / acceptance criteria

1. Every registered connection carries a `lastInboundAt` timestamp, set when it
   registers and refreshed on **any** inbound datagram from its origin — `KA`,
   application data, and a retransmitted `Iam` all count.
2. `MultiConnectionUDPServer` takes an opt-in idle threshold. When a connection's
   idle time exceeds it, the server invokes an overridable
   `onClientDisconnect(connection, DisconnectReason.TIMEOUT)` hook.
3. The hook runs on the single-threaded **dispatch executor** (`mcups-dispatch`),
   not the sweep thread and not the listener thread — consistent with message
   handlers, so a slow hook cannot stall detection or the listener.
4. **Notify-only.** A timeout does not remove the registration or call
   `terminate()`. The connection stays addressable until the application decides
   otherwise. This is what the GameTools reconnect-grace-window needs.
5. **Off by default.** With no threshold configured, behaviour is byte-for-byte
   what it is today: no extra thread, no callback, `lastInboundAt` not even
   written.
6. The hook also fires for the two existing silent removals — a same-name
   supersede (`DisconnectReason.SUPERSEDED`) and an application `terminate()`
   (`DisconnectReason.TERMINATED`) — so a consumer has one place to observe a
   connection ending. It does **not** fire during `stop()` teardown (§8 D5).
7. Non-breaking: `webtools-udp` `1.3.0`. No existing constructor signature
   removed (`@JvmOverloads`); `onClientDisconnect` is `open` with a no-op body so
   no existing subclass must change.

---

## 2. Design

### 2.1 Shape of the change

Four production units:

1. **`Liveness`** (new, `internal object`) — the pure, socket-free, timer-free
   rules: is a connection overdue, and how often to sweep. Exhaustively unit
   testable (Level 4a).
2. **`Registration`** — gains `@Volatile var lastInboundAt: Long` (monotonic
   `System.nanoTime()`, set at construction) and `@Volatile var timedOut: Boolean`
   (the fire-once latch).
3. **`HandshakeCoordinator`** — gains an `onDisconnect` callback and an
   `idleTimeoutMillis` it uses to (a) refresh `lastInboundAt` at the top of
   `accept` when tracking is on, (b) expose `sweepIdleConnections()` for the
   server's scheduled task, and (c) route the SUPERSEDED / TERMINATED reasons
   through the same callback.
4. **`MultiConnectionUDPServer`** — gains the `idleTimeoutMillis` constructor
   parameter, an optional `mcups-liveness` `ScheduledExecutorService` created only
   when the parameter is > 0, the `open fun onClientDisconnect(...)` hook, and the
   `DisconnectReason` enum (new top-level public type).

### 2.2 `lastInboundAt` — monotonic, server-internal

- **Monotonic clock.** `System.nanoTime()`, never `currentTimeMillis()`. A
  wall-clock adjustment (NTP step, DST, manual change) must not fabricate or mask
  a timeout. `nanoTime()` differences are the only valid use of the value; it is
  never logged as an absolute.
- **Server-internal.** The value lives on `Registration` (which is `internal`).
  It is **not** exposed on the public `Connection` interface (§8 D4). The
  application's signal is the `onClientDisconnect` callback, not polling. #13 will
  add a coherent set of per-connection accessors (`rttMillis`, loss, counters) and
  `lastInboundAt` joins them then if wanted.
- **Refreshed only when tracking is on.** `HandshakeCoordinator` is constructed
  with `idleTimeoutMillis`; if it is 0 the coordinator sets a
  `private val livenessTracked = false` and `accept` skips the refresh entirely —
  no extra `findByOrigin` scan, no volatile write, for the default configuration.

### 2.3 The refresh point — top of `accept`

`HandshakeCoordinator.accept(origin, bytes, text)` (`HandshakeCoordinator.kt:60`)
is the single inbound entry point for the server. When `livenessTracked`, before
the classification `when`:

```kotlin
if (livenessTracked) registrations.findByOrigin(origin)?.let {
    it.lastInboundAt = System.nanoTime()
    it.timedOut = false // inbound traffic un-latches a previously-notified peer
}
```

This covers all three inbound kinds from a *registered* origin: `KA`,
application data, and a retransmitted `Iam` (a first `Iam` creates the
`Registration`, whose constructor seeds `lastInboundAt`). Resetting `timedOut`
means a peer that falls silent, is reported, then resumes from the same origin
will be reported again if it falls silent a second time. A resumed peer gets no
distinct "reconnected" event — its resumed datagrams flow to the bound handler,
which is the application's signal (documented).

**Cost when tracking is on:** one extra O(n) `findByOrigin` linear scan over the
copy-on-write list per inbound datagram, plus two volatile writes. `deliverData`
and `handleHandshake` already each do their own `findByOrigin`. For a
KA-heavy / many-client workload this roughly doubles the per-datagram scan. §6
notes an optional single-lookup refactor deferred as a micro-optimisation.

### 2.4 The sweep — a dedicated, opt-in `ScheduledExecutorService`

`MultiConnectionUDPServer` gains:

```kotlin
private val livenessExecutor: ScheduledExecutorService? =
    if (idleTimeoutMillis > 0)
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "mcups-liveness").apply { isDaemon = true }
        }
    else null

init {
    livenessExecutor?.let { exec ->
        val interval = Liveness.sweepIntervalMillis(idleTimeoutMillis)
        exec.scheduleWithFixedDelay(
            {
                runCatching { coordinator.sweepIdleConnections() }
                    .onFailure { log.warn("Liveness sweep failed; detection continues", it) }
            },
            interval, interval, TimeUnit.MILLISECONDS,
        )
    }
}
```

- **Dedicated executor, not a piggyback.** A fully silent peer sends nothing, so
  the listener thread's `receive()` blocks indefinitely and cannot be the trigger.
  A `setSoTimeout` wake-loop on the listener would entangle detection with the
  handshake read path. A dedicated single daemon thread is the only mechanism that
  detects a silent peer without touching the hot path. See §8 D3.
- **Created only when enabled.** Default users get no `mcups-liveness` thread at
  all — asserted in the Level 4c test.
- **`scheduleWithFixedDelay`**, not `AtFixedRate` — no value in bunching sweeps if
  one runs long. The `runCatching` wrapper is load-bearing:
  `scheduleWithFixedDelay` silently cancels the task if it throws.
- **Sweep interval derived**, not a second constructor parameter (§8 D3):
  `Liveness.sweepIntervalMillis(idleTimeoutMillis) = (idleTimeoutMillis / 4)`
  clamped to `250..5000` ms. Worst-case detection latency is therefore
  `idleTimeoutMillis + sweepInterval` (≤ ~1.25× the threshold, or threshold +
  5 s for large thresholds).

`HandshakeCoordinator.sweepIdleConnections()`:

```kotlin
fun sweepIdleConnections() {
    val now = System.nanoTime()
    registrations.snapshot().forEach { reg ->
        if (reg.timedOut) return@forEach
        if (!Liveness.isOverdue(reg.lastInboundAt, now, idleTimeoutMillis)) return@forEach
        // A concurrent terminate()/supersede may have removed it since the snapshot.
        if (registrations.findByOrigin(reg.origin) == null) return@forEach
        reg.timedOut = true
        log.info("Connection '{}' ({}) has been idle beyond {} ms - notifying",
            reg.connection.name, reg.origin, idleTimeoutMillis)
        runCatching { onDisconnect(reg.connection, DisconnectReason.TIMEOUT) }
            .onFailure { log.warn("onDisconnect(TIMEOUT) failed for '{}'", reg.connection.name, it) }
    }
}
```

Iterating `registrations.snapshot()` (an immutable copy) is concurrency-safe
against `add` / `removeByOrigin` on the listener thread. The membership re-check
narrows (does not eliminate) the race where a connection is terminated between
snapshot and callback; a stray TIMEOUT for an already-gone connection is harmless
and documented. The per-callback `runCatching` makes `sweepIdleConnections()`
itself throw-safe: one failing notification never aborts the rest of the pass.
(In `MultiConnectionUDPServer` the callback body is only
`dispatchExecutor.execute { ... }`, which does not throw outside shutdown, and
the server additionally wraps the whole sweep call — §2.4 — but the coordinator
does not rely on that.)

### 2.5 The hook and the reason enum

New top-level public enum:

```kotlin
/**
 * Why a [Connection] managed by a [MultiConnectionUDPServer] stopped being
 * addressable, as reported to [MultiConnectionUDPServer.onClientDisconnect].
 */
enum class DisconnectReason {
    /** No datagram (data or `KA`) arrived from the peer within the configured
     *  idle threshold. The registration is left in place - notify-only. */
    TIMEOUT,

    /** A fresh `Iam` under this connection's name arrived from a different
     *  origin (e.g. a NAT rebind) and superseded it; the registration has been
     *  removed. A matching [MultiConnectionUDPServer.onClientConnect] fires for
     *  the replacement. */
    SUPERSEDED,

    /** The application called [Connection.terminate]; the registration has been
     *  removed. */
    TERMINATED,
}
```

New hook on `MultiConnectionUDPServer`:

```kotlin
/**
 * Called when a registered client connection stops being addressable - see
 * [DisconnectReason]. Runs on the single-threaded dispatch executor
 * (`mcups-dispatch`), not the caller's thread, so it must return promptly.
 *
 * The default implementation does nothing; override to react (e.g. start a
 * reconnect grace window on [DisconnectReason.TIMEOUT], then keep the entities
 * alive and rebind on a resume token).
 *
 * Notify-only: [DisconnectReason.TIMEOUT] does **not** remove the registration
 * or call [Connection.terminate] - the connection stays addressable by
 * [pushToAll] until the application decides. A subsequent [Connection.terminate]
 * then produces a second call here with [DisconnectReason.TERMINATED].
 *
 * Not called during [stop]; a full server shutdown is not a per-connection event.
 *
 * @param connection the connection that stopped being addressable
 * @param reason why
 */
open fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {}
```

`open` + no-op body: binary- and source-compatible for every existing subclass
(cf. the `Connection` default-method pattern in `docs/issue-8-*` §9 D1). The
asymmetry with the `abstract` `onClientConnect` is deliberate — making
`onClientDisconnect` abstract would force every subclass to change, a major bump.

Wiring in the server (callback marshals onto the dispatch executor):

```kotlin
private val coordinator = HandshakeCoordinator(
    newConnection = { name, peer, channel -> UDPConnection(name, peer, channel) },
    sender = commonChannel::send,
    onRegistered = ::onClientConnect,
    dispatch = { block -> dispatchExecutor.execute(block) },
    onDisconnect = { connection, reason ->
        dispatchExecutor.execute {
            runCatching { onClientDisconnect(connection, reason) }
                .onFailure { log.warn("onClientDisconnect threw for '{}'", connection.name, it) }
        }
    },
    idleTimeoutMillis = idleTimeoutMillis,
)
```

### 2.6 SUPERSEDED / TERMINATED routing, and stop() suppression

`HandshakeCoordinator` gets a suppression latch so `stop()` teardown is silent:

```kotlin
@Volatile private var notifyingDisconnects = true
fun stopNotifying() { notifyingDisconnects = false }
```

- **supersede** (`HandshakeCoordinator.kt:90-95`) — immediately after
  `registrations.removeByOrigin(stale.origin)` and before
  `stale.connection.terminate()`:
  ```kotlin
  if (notifyingDisconnects) onDisconnect(stale.connection, DisconnectReason.SUPERSEDED)
  ```
  Because `removeByOrigin` has already run, the `stale.connection.terminate()`
  call that follows finds nothing in `deregister` and does **not** also fire
  TERMINATED. This ordering already exists in the code (with a comment explaining
  it) and this plan relies on it.

- **terminate** (`deregister`, `HandshakeCoordinator.kt:144-148`):
  ```kotlin
  override fun deregister(peer: InetSocketAddress) {
      val reg = registrations.findByOrigin(peer)
      if (registrations.removeByOrigin(peer)) {
          log.info("Deregistered connection for {}", peer)
          if (notifyingDisconnects && reg != null) {
              onDisconnect(reg.connection, DisconnectReason.TERMINATED)
          }
      }
  }
  ```

- **stop()** (`MultiConnectionUDPServer.kt:226-241`) — `coordinator.stopNotifying()`
  is called first, before `coordinator.terminateAll()`, so the N
  `terminate()` → `deregister` calls during shutdown fire nothing. `stop()` also
  shuts the liveness executor:
  ```kotlin
  fun stop(): Result<Unit> {
      coordinator.stopNotifying()
      val connectionsTerminated = coordinator.terminateAll()
      listening = false
      val listenerJoined = /* unchanged */
      val socketClosed = commonChannel.closeResult()
      val livenessStopped = runCatching { livenessExecutor?.shutdownNow(); Unit }
          .onFailure { log.warn("Could not cleanly shut the liveness executor", it) }
      val executorStopped = runCatching { dispatchExecutor.shutdownNow() }.map { }
          .onFailure { log.warn("Could not cleanly shut the dispatch executor", it) }
      return connectionsTerminated.flatMap { listenerJoined }.flatMap { socketClosed }
          .flatMap { livenessStopped }.flatMap { executorStopped }
  }
  ```

### 2.7 `Liveness` (new pure helper)

```kotlin
package com.spartanlabs.webtools.udp

/**
 * Pure, socket-free, timer-free rules for the [MultiConnectionUDPServer] idle
 * connection sweep: whether a connection is overdue, and how often to sweep.
 * Deterministic functions of their arguments - fully unit-testable.
 */
internal object Liveness {
    /** Smallest / largest gap between idle sweeps, regardless of the threshold. */
    private const val MIN_SWEEP_MILLIS = 250L
    private const val MAX_SWEEP_MILLIS = 5_000L
    /** Sweep this many times per threshold window. */
    private const val SWEEPS_PER_WINDOW = 4L

    /**
     * True if the last inbound datagram is older than [idleTimeoutMillis].
     * Always false when [idleTimeoutMillis] <= 0 (detection disabled). Compares
     * in milliseconds so a pathological threshold cannot overflow.
     */
    fun isOverdue(lastInboundAtNanos: Long, nowNanos: Long, idleTimeoutMillis: Long): Boolean {
        if (idleTimeoutMillis <= 0L) return false
        val idleMillis = (nowNanos - lastInboundAtNanos) / 1_000_000L
        return idleMillis >= idleTimeoutMillis
    }

    /**
     * How long the scheduled sweep should wait between runs for a given
     * threshold: one quarter of the window, clamped to [MIN_SWEEP_MILLIS]..
     * [MAX_SWEEP_MILLIS]. Worst-case detection latency is
     * `idleTimeoutMillis + sweepIntervalMillis(idleTimeoutMillis)`.
     */
    fun sweepIntervalMillis(idleTimeoutMillis: Long): Long =
        (idleTimeoutMillis / SWEEPS_PER_WINDOW).coerceIn(MIN_SWEEP_MILLIS, MAX_SWEEP_MILLIS)
}
```

### 2.8 Flow — one silent client, detection enabled

```mermaid
sequenceDiagram
    participant Peer as client (goes silent)
    participant List as listener thread
    participant HC as HandshakeCoordinator
    participant Sweep as mcups-liveness
    participant Disp as mcups-dispatch
    participant App as onClientDisconnect

    Note over Peer,HC: steady state
    Peer->>List: KA / data datagram
    List->>HC: accept(origin, bytes, text)
    HC->>HC: registration.lastInboundAt = nanoTime(); timedOut = false
    HC-->>List: classify + deliver (unchanged)

    Note over Peer: client crashes / loses network
    loop every sweepIntervalMillis
        Sweep->>HC: sweepIdleConnections()
        HC->>HC: for each registration: isOverdue(lastInboundAt, now, threshold)?
        alt overdue and not yet notified and still registered
            HC->>HC: registration.timedOut = true
            HC->>Disp: execute { onClientDisconnect(conn, TIMEOUT) }
            Disp->>App: onClientDisconnect(conn, TIMEOUT)
            Note over App: start grace window; keep entities; DO NOT terminate
        end
    end

    alt client resumes from same origin
        Peer->>List: datagram
        List->>HC: accept(...)
        HC->>HC: lastInboundAt refreshed; timedOut = false
        Note over App: resumed datagrams reach the bound handler (the resume signal)
    else app gives up after the grace window
        App->>HC: connection.terminate() -> deregister
        HC->>Disp: execute { onClientDisconnect(conn, TERMINATED) }
        Disp->>App: onClientDisconnect(conn, TERMINATED)
    end
```

### 2.9 Alternatives considered

| Option | Rejected because |
|---|---|
| **Auto-`terminate` on timeout** | Destroys the registration, so the GameTools grace window cannot hold entities and rebind. Notify-only is strictly more flexible; the app can call `terminate()` itself in one line. (§8 D1) |
| **On by default** (e.g. 60 s) | A behaviour change for every current consumer, including text-only ones that never send `KA`. Off by default keeps `1.3.0` a pure addition. (§8 D2) |
| **Piggyback the sweep on the listener thread** (`setSoTimeout` wake loop) | A silent peer sends nothing, so the listener never wakes; adding a timeout entangles detection with the handshake read path and risks spurious handshake timeouts. (§8 D3) |
| **Schedule one delayed task per connection** on a shared scheduler | More bookkeeping (cancel/reschedule on every inbound datagram) for no better latency than a single periodic sweep over a copy-on-write list that is expected to be small. |
| **`lastInboundAt` / `idleMillis` on the public `Connection`** | An interface property with no clean default; the use case is callback-driven, not poll-driven; #13 batches per-connection accessors coherently. (§8 D4) |
| **A single-purpose `onClientTimeout(connection)` hook** | Leaves supersede and `terminate()` still silent, so a consumer needs two mechanisms to know a connection ended. One `onClientDisconnect(connection, reason)` covers all three. (§8 D5) |
| **Wall-clock (`currentTimeMillis`) timestamps** | An NTP step or manual clock change would fabricate or mask timeouts. `nanoTime()` is monotonic. |

---

## 3. File-by-file changes

All paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/`
unless noted.

### 3.1 `Liveness.kt` (new, internal)

The `internal object Liveness` from §2.7 — `isOverdue`, `sweepIntervalMillis`, and
the three private constants. Level-1 KDoc on the object; Level-2 KDoc on both
functions (`@param` / `@return`, boundary behaviour spelled out).

### 3.2 `DisconnectReason.kt` (new, public)

The `enum class DisconnectReason { TIMEOUT, SUPERSEDED, TERMINATED }` from §2.5
with per-constant KDoc. Top-level public type, mirroring how `Connection` /
`HandshakeWireFormat` are top-level.

### 3.3 `Registrations.kt`

- `Registration` (`Registrations.kt:20-28`): add
  ```kotlin
  /** Monotonic nanoTime of the last inbound datagram from this origin (data or
   *  KA); seeded at construction. Read by the liveness sweep, written by the
   *  listener thread via HandshakeCoordinator.accept, hence @Volatile. Only ever
   *  used as a nanoTime difference - never as an absolute time. */
  @Volatile var lastInboundAt: Long = System.nanoTime()

  /** Fire-once latch: set true when onClientDisconnect(TIMEOUT) has been
   *  dispatched for this registration, cleared by any later inbound datagram. */
  @Volatile var timedOut: Boolean = false
  ```
- Class KDoc: add both `@property` entries; note the sweep/listener threading.
- `Registrations` itself is unchanged.

### 3.4 `HandshakeCoordinator.kt`

- **Constructor** (`HandshakeCoordinator.kt:30-35`): two new trailing parameters:
  ```kotlin
  private val onDisconnect: (connection: Connection, reason: DisconnectReason) -> Unit,
  private val idleTimeoutMillis: Long,
  ```
  and `private val livenessTracked = idleTimeoutMillis > 0`. KDoc `@param` for
  both; note `onDisconnect` is expected to marshal onto the dispatch executor
  (the coordinator calls it inline).
- **`accept(origin, bytes, text)`** (`:60-67`): prepend the §2.3 refresh block
  (guarded by `livenessTracked`). The classification `when` is **unchanged**.
- **`accept(origin, text)`** (`:73-74`): unchanged (still delegates to the 3-arg
  form).
- **`handleHandshake`** supersede branch (`:90-95`): after
  `registrations.removeByOrigin(stale.origin)`, add
  `if (notifyingDisconnects) onDisconnect(stale.connection, DisconnectReason.SUPERSEDED)`.
- **`deregister`** (`:144-148`): capture the `Registration` before removal; on
  successful removal, `if (notifyingDisconnects && reg != null) onDisconnect(reg.connection, DisconnectReason.TERMINATED)`.
- **New** `fun sweepIdleConnections()` — the §2.4 body.
- **New** `@Volatile private var notifyingDisconnects = true` and
  `fun stopNotifying()` — §2.6.
- Class KDoc (`:6-29`): add a fourth bullet — it is also the **liveness tracker**:
  refreshes `Registration.lastInboundAt` on every inbound datagram when a
  threshold is configured, and `sweepIdleConnections()` reports connections idle
  beyond it via `onDisconnect(_, TIMEOUT)`.

### 3.5 `MultiConnectionUDPServer.kt`

- **Constructor** (`:80-87`): add the trailing parameter, keep `@JvmOverloads`:
  ```kotlin
  abstract class MultiConnectionUDPServer @JvmOverloads protected constructor(
      private val receiveBufferBytes: Int = DEFAULT_RECEIVE_BUFFER_BYTES,
      private val idleTimeoutMillis: Long = DISABLED_IDLE_TIMEOUT,
  ) {
      init {
          require(receiveBufferBytes in MIN_RECEIVE_BUFFER_BYTES..MAX_UDP_PAYLOAD_BYTES) { ... }
          require(idleTimeoutMillis >= 0L) {
              "idleTimeoutMillis must be >= 0 (0 disables idle detection), was $idleTimeoutMillis"
          }
      }
  ```
  Both `require`s stay in the first `init` block, before the socket-binding
  property initialisers (same fail-fast ordering the receive-buffer guard relies
  on).
- **`coordinator`** (`:110-115`): add `onDisconnect = { ... dispatchExecutor.execute { ... } }`
  and `idleTimeoutMillis = idleTimeoutMillis` per §2.5.
- **New property** `livenessExecutor: ScheduledExecutorService?` and the scheduling
  `init` block — §2.4. Place the `init` block after the `coordinator` property
  (it references it) and before or after the listener-thread `init` (order between
  the two is immaterial; put liveness after for readability).
- **New** `open fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {}`
  — §2.5 KDoc. Place it directly after `abstract fun onClientConnect` (`:160`).
- **`stop()`** (`:226-241`): `coordinator.stopNotifying()` first; add the
  `livenessStopped` step and thread it into the final `flatMap` chain — §2.6.
- **Companion** (`:243-275`): add
  ```kotlin
  /** Default [idleTimeoutMillis]: 0, i.e. idle-connection detection is off.
   *  When enabling it, a value around 3x the ~20 s KA cadence (~60_000) is a
   *  sane starting point. */
  const val DISABLED_IDLE_TIMEOUT = 0L
  ```
- **Imports**: add `java.util.concurrent.ScheduledExecutorService`,
  `java.util.concurrent.TimeUnit`.
- **Class KDoc**:
  - The `KA` paragraph (`:22-23`) — `KA` is still consumed without dispatch, but
    now also refreshes the sender's liveness timestamp when idle detection is on.
  - **New `### Connection liveness` section**: the opt-in `idleTimeoutMillis`
    parameter, the notify-only `onClientDisconnect(_, TIMEOUT)` contract, that
    SUPERSEDED / TERMINATED also flow through the hook, and that `stop()` does
    not. Add `@param idleTimeoutMillis`.
  - The `### Concurrency` section (`:65-78`): a **third** daemon thread —
    `mcups-liveness`, a `ScheduledExecutorService`, exists **only when
    `idleTimeoutMillis > 0`**; it runs `sweepIdleConnections()` and never runs
    application code (the hook is handed to `mcups-dispatch`).
  - `### Construction side effects` (`:52-60`): `require`s throw
    `IllegalArgumentException` if `idleTimeoutMillis < 0`; a positive value also
    starts the `mcups-liveness` thread.

### 3.6 `HandshakeProtocol.kt` / `HandshakeWireFormat.kt`

- No behavioural change. `HandshakeWireFormat` KDoc: one sentence that an inbound
  `KA`, besides being dropped, refreshes the server's per-connection liveness
  timestamp when idle detection is enabled (Boundary-Ring: a consumer reasoning
  about `KA` semantics needs this).

### 3.7 Test-support fixtures

`webtools-udp/src/test/kotlin/com/spartanlabs/testing/support/webtools/udp/`

- `FakeConnection.kt`: no change needed (already a full `Connection`).
- No fixture needs a structural change; the coordinator tests construct
  `HandshakeCoordinator` directly and will pass a recording `onDisconnect` lambda
  and an explicit `idleTimeoutMillis`.

### 3.8 `README.md` (repo root)

- Dependency snippet (`README.md:19`): `webtools-udp:1.2.0` → `1.3.0`.
- `webtools-udp` components table, `MultiConnectionUDPServer` row (`:47`): note
  `onClientDisconnect` alongside `onClientConnect`.
- "UDP handshake protocol" section, the `KA` paragraph (`:92-96`): `KA` (and any
  inbound datagram) now refreshes the server's per-connection liveness timestamp.
- **New `### Connection liveness` subsection** after "Binary application payloads":
  the opt-in `idleTimeoutMillis` constructor parameter (0 = off, default),
  `onClientDisconnect(connection, reason)` with the three `DisconnectReason`s,
  the **notify-only** contract (a timeout does not terminate — the app runs its
  own grace window and calls `terminate()` when ready), the dispatch-thread
  execution, and that `stop()` does not fire it. One short code sketch mirroring
  the "Client-side usage" block.

### 3.9 `webtools-udp/build.gradle.kts`

- `version` (`:8`): `"1.2.0"` → `"1.3.0"`.
- Version comment block (`:4-7`): add
  `// 1.3.0: opt-in idle-connection detection - per-connection lastInboundAt,`
  `// idleTimeoutMillis ctor param, and the open onClientDisconnect(conn, reason)`
  `// hook on the dispatch executor (Issue #10).`

### 3.10 `docs/issue-10-connection-liveness-plan.md`

This document. Committed in the same commit as §3.1–§3.6 + §3.8 (the first
implementation stage). Backfill the `Commit:` / `PR:` header fields with real
references once they exist (as done for #8 in `e27d8de` and #9 in `df0549f`).

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves with this change |
|---|---|---|
| **Inner core** (in-editor) | yes | Comments: why `nanoTime()` not `currentTimeMillis()`; the `timedOut` fire-once latch and its reset; the `runCatching` around the sweep (`scheduleWithFixedDelay` cancels a throwing task); the supersede-ordering reliance that avoids a double TERMINATED. |
| **Component ring** (KDoc) | yes | New KDoc on `Liveness.isOverdue` / `sweepIntervalMillis`, `DisconnectReason` (per constant), `Registration.lastInboundAt` / `timedOut`, `MultiConnectionUDPServer.onClientDisconnect` + `@param idleTimeoutMillis` + `DISABLED_IDLE_TIMEOUT`, `HandshakeCoordinator` new params / `sweepIdleConnections` / `stopNotifying`. |
| **Boundary ring** (protocol) | yes | `KA` semantics change on the server: previously "consumed silently", now "consumed + refreshes liveness when detection is on". Stated in `HandshakeWireFormat` KDoc and the README "UDP handshake protocol" section. No wire-format change. |
| **Architectural outer layer** | yes | A third server-side daemon thread (`mcups-liveness`), conditional on `idleTimeoutMillis > 0`. Documented in the `MultiConnectionUDPServer` `### Concurrency` KDoc; the §2.8 sequence diagram is the canonical liveness flow. |
| **README** | yes | Per the README-currency rule: new constructor parameter, new overridable hook, changed `KA` behaviour, new (conditional) thread, dependency-coordinate bump. |

---

## 5. Test plan (5-Level hierarchy)

Package root mirrors production `com.spartanlabs.webtools.udp` →
`com.spartanlabs.testing.<level>.webtools.udp`. One class per file. Each class
carries the `@Tag` for its level. Port-9998 classes run under the module's
`commonUdpPortLock`; JUnit runs test classes sequentially so only one binds 9998
at a time. Idle thresholds in automated tests are small (200–400 ms) with a
derived sweep interval floored at 250 ms.

### Level 1 — `testing.gating`

- **New** `IdleTimeoutValidationGatingTest.kt` — the `require(idleTimeoutMillis >= 0)`
  guard rejects `-1` and `Long.MIN_VALUE` with `IllegalArgumentException` **before
  any socket is bound** (guard precedes the socket property initialisers), and
  accepts `0` and a positive value (construct, then immediately `stop()`). The
  accepting cases bind port 9998 → covered by `commonUdpPortLock`; if binding in a
  gating test is unwanted, keep only the rejection cases here and move the
  accepting cases to Level 3 (mirrors the `ReceiveBufferValidationGatingTest`
  note).
- **Extend** `HandshakeCoordinatorGatingTest.kt` (socket-free): with
  `idleTimeoutMillis = 1` and a recording `onDisconnect`:
  - a registered connection whose `lastInboundAt` is forced into the past is
    reported exactly once by `sweepIdleConnections()`; a second sweep reports
    nothing;
  - an `accept(origin, "KA")` between sweeps clears `timedOut` and a later sweep
    re-reports;
  - with `idleTimeoutMillis = 0`, `sweepIdleConnections()` never reports and
    `accept` does not write `lastInboundAt` (value unchanged from construction).

### Level 2 — `testing.component`

- **Extend** `RegistrationsTest.kt`:
  - `Registration.lastInboundAt` is within a few ms of `System.nanoTime()` at
    construction and is independently mutable;
  - `timedOut` defaults `false` and is settable.
- **Extend** `HandshakeCoordinatorTest.kt` (synchronous `dispatch = { it() }`,
  recording `onDisconnect`, `idleTimeoutMillis` per test):
  - `accept` with application data / `KA` / a retransmitted `Iam` each refresh the
    registration's `lastInboundAt` (assert it advanced) — only when
    `idleTimeoutMillis > 0`;
  - `sweepIdleConnections()` fires `onDisconnect(conn, TIMEOUT)` once for an
    overdue registration and leaves it **registered** (`coordinator.size`
    unchanged, still in `snapshot()`, still a `broadcast` target) — the
    notify-only guarantee;
  - a not-yet-overdue registration is not reported;
  - a same-name `Iam` from a new origin fires `onDisconnect(stale, SUPERSEDED)`
    and does **not** also fire `TERMINATED`;
  - a real `UDPConnection.terminate()` fires `onDisconnect(conn, TERMINATED)`;
  - after `stopNotifying()`, neither `terminateAll()` nor a subsequent
    `deregister` fires anything;
  - a throwing `onDisconnect` does not propagate out of `sweepIdleConnections()`
    and does not stop it reporting the other overdue registrations in the pass
    (register three, force all overdue, first callback throws, assert the other
    two still fire) — locks the per-callback `runCatching` in §2.4;
  - update the existing `newCoordinator()` helper and the ~4 inline
    `HandshakeCoordinator(...)` constructions in this file for the two new params.

### Level 3 — `testing.integration` (real sockets)

- **New** `MultiConnectionUDPServerLivenessTest.kt` — its own server instance
  (`object : MultiConnectionUDPServer(idleTimeoutMillis = 300) { ... }`), separate
  from `MultiConnectionUDPServerTest`'s shared server (mirrors
  `MultiConnectionUDPServerReceiveBufferTest`'s rationale). `@AfterTest { server?.stop() }`.
  - a client that handshakes then goes silent triggers
    `onClientDisconnect(conn, TIMEOUT)` within ~`threshold + sweepInterval + slack`;
  - a client that sends `KA` every ~100 ms is **not** reported across a window
    several times the threshold;
  - notify-only: after the TIMEOUT callback, `server.pushToAll(...)` still reaches
    that client's socket, and the connection is still in the roster;
  - the callback runs on `mcups-dispatch` (`Thread.currentThread().name`);
  - a `MultiConnectionUDPServer()` with the **default** (disabled) threshold never
    fires the hook for a silent client, and `Thread.getAllStackTraces().keys` has
    no `mcups-liveness` thread — the default-cost-zero guarantee;
  - `stop()` on a liveness-enabled server leaves no live `mcups-liveness` thread
    and fires no TERMINATED storm.

### Level 4a — `testing.deterministic`

- **New** `LivenessTest.kt`:
  - `isOverdue` — `false` for `idleTimeoutMillis <= 0` regardless of the gap;
    boundary at exactly the threshold (`>=`), just under, well over; a `now`
    before `lastInboundAt` (clock skew within a sweep) yields `false`;
  - `sweepIntervalMillis` — clamps to 250 for small / zero thresholds, to 5000 for
    large thresholds, and returns `threshold / 4` in the mid band; never returns
    outside `250..5000`;
  - no overflow for `idleTimeoutMillis = Long.MAX_VALUE` (millisecond comparison).

### Level 4b — `testing.e2e`

- **New** `MultiConnectionUDPLivenessE2ETest.kt` — real `MultiConnectionUDPClient`
  + real `MultiConnectionUDPServer` subclass over loopback, `idleTimeoutMillis`
  ~300 ms:
  - client handshakes, `start`s, then `stop()`s; the server's
    `onClientDisconnect(_, TIMEOUT)` fires; the test's hook then calls
    `connection.terminate()` and a second callback `(_, TERMINATED)` is observed —
    the full grace-window-then-give-up path;
  - a client running a `sendKeepAlive()` loop on a ~100 ms cadence stays alive
    across many sweep intervals; stopping the loop then triggers TIMEOUT;
  - reconnect: after TIMEOUT (entity held, not terminated), a **new**
    `MultiConnectionUDPClient` handshakes under the same name → the server sees
    `onClientDisconnect(stale, SUPERSEDED)` then `onClientConnect(fresh)` — the
    GameTools resume shape.

### Level 4c — `testing.nonfunctional`

- **New** `MultiConnectionUDPServerLivenessNonFunctionalTest.kt`:
  - 50 idle clients are each reported exactly once, all within a bounded time, and
    the `mcups-liveness` thread does not wedge (a later handshake still registers);
  - a deliberately slow `onClientDisconnect` (sleeps 500 ms) does not delay
    detection of other idle clients — the sweep thread is decoupled from the
    dispatch executor;
  - a throwing `onClientDisconnect` never kills the sweep thread or the dispatch
    thread across many events;
  - with detection disabled, no `mcups-liveness` thread is created and per-datagram
    handling does not touch `Registration.lastInboundAt` (assert via a large `KA`
    burst leaving the roster and timing state untouched — or assert the field is
    unchanged through a reflective/`internal` read).

### Level 5 — `testing.uat`

- **Extend** `MultiConnectionUDPServerUatTest.kt` with one `@Disabled` scenario:
  a real NAT'd client handshakes against a public server configured with
  `idleTimeoutMillis = 60_000`; the operator pulls the client's network
  connection; PASS = `onClientDisconnect(_, TIMEOUT)` fires within ~65 s and the
  entities are still held (not terminated); reconnecting the client under the same
  name within the grace window produces `SUPERSEDED` + `onClientConnect` and the
  session rebinds.

### Cannot be automated

- Real crash / power-loss / network-egress-failure / NAT-mapping-expiry timing on
  a live path — loopback never goes silent involuntarily (Level 5).
- Monotonic-clock immunity to a real NTP step or manual wall-clock change in a
  deployed server (the Level 4a `isOverdue` test proves the logic; the field
  behaviour under a real clock jump is not reproducible in CI).
- Long-horizon sweep-thread cost at consumer scale (thousands of connections over
  days).

---

## 6. Risks & edge cases

- **`KA` behaviour change.** Inbound `KA` now mutates
  `Registration.lastInboundAt` / `timedOut` **when detection is on**. Harmless
  (previously a no-op) and off entirely by default. Locked by the Level-2
  "`accept` with `KA` refreshes" and Level-3 "`KA` loop is not reported" tests.
- **Two callbacks for one ending.** A timed-out connection the app then
  `terminate()`s produces `onClientDisconnect(_, TIMEOUT)` **and** later
  `(_, TERMINATED)`. Intended — distinct facts ("went silent" vs "now removed").
  Documented; a consumer that only cares about removal ignores `TIMEOUT`.
- **Stray TIMEOUT for an already-gone connection.** The sweep snapshots, then a
  concurrent `terminate()` / supersede removes the entry before the callback is
  dispatched. Narrowed by the membership re-check in `sweepIdleConnections()`;
  not eliminated. Harmless (the callback just names a connection the app already
  discarded) and documented.
- **Extra `findByOrigin` scan per datagram when detection is on.** O(n) over the
  copy-on-write list, on top of the lookups `deliverData` / `handleHandshake`
  already do. Acceptable for the expected roster sizes; an optional single-lookup
  refactor of `accept` (resolve the `Registration` once, thread it into the
  branches) is noted as a deferred micro-optimisation, not done here.
- **`@JvmOverloads` correctness is load-bearing.** Adding `idleTimeoutMillis` as a
  trailing defaulted parameter must keep `<init>()` and `<init>(int)` for
  precompiled subclasses. Verify with `javap` on the built
  `MultiConnectionUDPServer.class` before tagging (same step as #8 / #9):
  expect `<init>()`, `<init>(int)`, `<init>(int, long)`.
- **Abstract-class constructor change.** Kotlin `: MultiConnectionUDPServer()` and
  `: MultiConnectionUDPServer(4096)` subclasses stay source-compatible (new param
  defaulted); `@JvmOverloads` keeps the compiled `super()` / `super(int)` targets.
- **`open fun onClientDisconnect` default.** A no-op body → binary- and
  source-compatible; no existing subclass changes. An external subclass that
  happens to already declare a method of that name/signature would now override
  it — vanishingly unlikely for a brand-new verb; call it out in the PR body.
- **Sweep thread lifecycle.** `mcups-liveness` must be a daemon and must be shut
  in `stop()`. A `stop()` that throws midway still reaches `livenessExecutor?.shutdownNow()`
  (each teardown step is independent). Locked by the Level-3 "no live
  `mcups-liveness` after stop" test.
- **Re-entrancy.** `onClientDisconnect` runs on `mcups-dispatch`. Calling
  `connection.terminate()` from it schedules the TERMINATED callback as a *new*
  dispatch task (via the server's marshalling lambda) — not re-entrant, no
  deadlock on the single-threaded executor.
- **Concurrency.** `lastInboundAt` / `timedOut` are `@Volatile`, single-writer
  (listener thread) / single-reader (sweep thread) except the sweep's own
  `timedOut = true` and the listener's `timedOut = false`; a lost update there
  only costs one extra sweep cycle of latency, never a missed or duplicated
  callback (the callback is gated on the `timedOut` transition plus the
  membership check). No new lock. The registration list is already copy-on-write.
- **Performance.** Default configuration: zero new threads, zero new per-datagram
  work. Enabled: one periodic O(n) sweep every 250 ms–5 s plus one O(n) scan per
  inbound datagram. No send-path change.
- **Cross-repo impact.** None forced. No wire-format, protocol, or shared-type
  change. GameTools' `SessionRegistry` adopts the hook on its own schedule
  (downstream adoption is that repo's concern per standing project guidance and
  is not a gate on closing #10).

---

## 7. Version

**`webtools-udp` `1.2.0` → `1.3.0`.**

- Additive: one new constructor parameter (kept binary-compatible via
  `@JvmOverloads`), one new `open` (default no-op) method, two new public types
  (`DisconnectReason`, `DISABLED_IDLE_TIMEOUT` constant), one new `internal`
  helper. Nothing removed, no existing signature changed.
- The versioning convention maps an addition to a **third-number bump** — not a
  trailing-letter suffix (bugfix-only), not `2.0.0` (no breaking change).
- Mirrors the #8 / #9 shape: implementation commit(s), then a
  `build: bump webtools-udp to 1.3.0` commit.
- **Before tagging:** `javap` the built `MultiConnectionUDPServer.class` and
  confirm `<init>()`, `<init>(int)`, `<init>(int, long)` are all present.

---

## 8. Decisions (resolved)

All six were resolved by the maintainer, each in favour of the plan's
recommendation. No decision remains open; §3 is executable as written.

- **D1 — Notify-only vs. auto-terminate on timeout. RESOLVED: notify-only.**
  `onClientDisconnect(_, TIMEOUT)` leaves the registration in place; the
  application calls `terminate()` when its own grace window expires. Auto-terminate
  would foreclose the GameTools "hold entities, rebind on resume token" use case
  and saves the consumer only one line.

- **D2 — Default on/off and default threshold. RESOLVED: off by default**
  (`idleTimeoutMillis = 0`). Enabling detection by default would change behaviour
  for every current consumer, including text-only ones that never send `KA`. The
  KDoc / README document ~`60_000` (≈3× the ~20 s `KA` cadence) as the suggested
  value when a consumer opts in; no non-zero default is baked in.

- **D3 — Sweep mechanism. RESOLVED: a dedicated single-thread
  `ScheduledExecutorService`** (`mcups-liveness`), created only when
  `idleTimeoutMillis > 0`, with the sweep interval derived (`threshold/4`, clamped
  `250..5000` ms) — not a second constructor parameter. A silent peer generates no
  I/O, so piggybacking on the listener thread cannot detect it; a derived interval
  keeps the API surface minimal. #12's server-side scheduled keepalive, if it
  lands, reuses this executor.

- **D4 — Public `Connection` liveness accessor. RESOLVED: no — `lastInboundAt`
  stays server-internal for #10.** The use case is callback-driven. #13 introduces
  a coherent set of per-connection accessors (`rttMillis`, packet-loss, counters);
  `lastInboundAt` / `idleMillis` can join that set then as one deliberate
  public-surface addition.

- **D5 — Hook signature and reason scope. RESOLVED:
  `open fun onClientDisconnect(connection: Connection, reason: DisconnectReason)`**,
  no-op default body, invoked on `mcups-dispatch`; enum
  `DisconnectReason { TIMEOUT, SUPERSEDED, TERMINATED }`, all three wired now.
  One hook covering every way a connection stops being addressable beats a
  single-purpose `onClientTimeout` that leaves supersede and `terminate()` silent.
  `stop()` teardown does **not** fire it (a whole-server shutdown is not a
  per-connection event). Shipping the full enum now avoids a later
  exhaustive-`when`-breaking enum expansion.

- **D6 — Shared per-connection stats holder. RESOLVED: no — stay minimal.** Add
  exactly `lastInboundAt` + `timedOut` to `Registration`. When #13 adds RTT / loss
  / counters, extract a `ConnectionStats` holder at that point with the full field
  set known, rather than guessing the shape now.

---

## 9. Sequencing & follow-ups

**Order of operations (all on `feat/issue-10-connection-liveness`):**

1. **Stage 1 — production + docs, one commit.** §3.1–§3.6 (`Liveness`,
   `DisconnectReason`, `Registration`, `HandshakeCoordinator`,
   `MultiConnectionUDPServer`, `HandshakeWireFormat` KDoc), §3.8 README, **plus
   this plan document**. `./gradlew :webtools-udp:compileKotlin` green; existing
   tests still compile only after Stage 2's helper updates, so run
   `:webtools-udp:build` at the end of Stage 2.
2. **Stage 2 — tests, one commit.** §5 all levels, plus the
   `HandshakeCoordinatorTest` / `HandshakeCoordinatorGatingTest` constructor-call
   updates. Run every level task green. Run the `javap` signature check here and
   paste the output into the PR.
3. **Stage 3 — version bump, one commit.** §3.9 (`build.gradle.kts` → `1.3.0`).
4. **After merge:** backfill this doc's `Commit:` / `PR:` header fields with the
   real SHA(s) and PR number. Comment on #10 that the library-side change has
   landed; close it (downstream `SessionRegistry` adoption tracked separately).

**Deliberately deferred (not designed here):**

- **#12** — opt-in scheduled keepalive. Would add a client-side (and possibly a
  server-side `Connection`-level) scheduled sender; the server half reuses
  `mcups-liveness`.
- **#13** — per-connection RTT / packet-loss. Extends `Registration` with
  counters and a probe; at that point extract a `ConnectionStats` holder and
  decide whether to surface `idleMillis` / `lastInboundAt` publicly (D4 / D6).
- The single-lookup refactor of `HandshakeCoordinator.accept` (resolve the
  `Registration` once and thread it through the branches) — a micro-optimisation,
  only if profiling a high-rate many-client server shows the double scan matters.
- A distinct "client resumed" event after a `TIMEOUT` — the application already
  learns from resumed inbound datagrams; add only if a consumer asks.

---

## 10. Version control

- **Branch:** `feat/issue-10-connection-liveness` off `master`.
- **Working tree is clean** as of planning (verified) — no unrelated pre-existing
  changes to carve into a separate commit.
- **Commit sequence** (each a coherent unit):
  1. `feat: detect idle client connections and fire onClientDisconnect on the webtools-udp server (Issue #10)` — §3.1–§3.6, §3.8, **plus this plan document**.
  2. `test: cover connection-liveness detection and the disconnect hook (Issue #10)` — §5 all levels; includes the `javap` signature verification.
  3. `build: bump webtools-udp to 1.3.0 for connection-liveness detection (Issue #10)` — §3.9.
- The plan document rides in commit 1 (the first implementation stage) so
  `git log --follow docs/issue-10-connection-liveness-plan.md` permanently binds
  plan to implementation.
- **Commit trailer** on every commit:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01WXBJw9mbV1AeRuqcoSwcvt
  ```
- **PR description** ends with:
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)`
- Do not commit, push, or open the PR until the maintainer asks; open it against
  `master`.
