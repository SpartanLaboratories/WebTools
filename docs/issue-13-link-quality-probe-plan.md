# Issue #13 — per-connection RTT and packet-loss estimates (PING/PONG probe)

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#13` — *"Expose per-connection RTT and
  packet-loss estimates"* (label: `enhancement`). WebTools measures neither RTT
  nor loss: there is no ping/pong, no timestamp exchange, no per-connection
  send/receive counters. GameTools' authoritative netcode sizes input timing /
  reconciliation off RTT; its separate client project sizes interpolation delay
  off RTT + jitter; both want a loss rate for adaptive send-rate control. Today
  each would hand-roll an application-level ping.
- **Scope of this plan:** `webtools-udp` only. Add a lightweight transport-level
  probe — a periodic, timestamped `PING` / `PONG` token pair (analogous to `KA`:
  consumed by the transport, never dispatched to the app callback) — and expose a
  smoothed `rttMillis` (EWMA), an RTT-variance figure, and a recent packet-loss
  ratio per `Connection` and on `MultiConnectionUDPClient`, via a single
  `LinkQuality` snapshot. Opt-in with a configurable probe interval; **off by
  default**. Mirrors Issue #12's opt-in / configuration / wire-token patterns.
- **Module:** `webtools-udp` (`io.github.spartanlaboratories:webtools-udp`,
  package `com.spartanlabs.webtools.udp`). **New wire tokens** `PING <token>` /
  `PONG <token>` — additive; an old peer never sends them, a new peer only sends
  them once a consumer opts in.
- **Branch:** `feat/issue-13-link-quality-probe` (off `master`, deleted after
  merge).
- **Commit / PR (do not exist yet — the executor fills these in):**
  - The plan document is committed **in the same commit as the first
    implementation stage** (§9) so `git log --follow
    docs/issue-13-link-quality-probe-plan.md` binds plan to implementation.
  - `Commit: TBD`
  - `PR: TBD`
- **Status:** planning only. **Open decisions: none** — all four (§8) were
  resolved by the maintainer in favour of the recommended option; §3 / §5 / §10
  are executable as written.
- **Target version:** `webtools-udp` `1.5.0` → **`1.6.0`** (additive: new public
  value type, new public methods with default/`@JvmOverloads` bodies, new public
  constants and wire helpers; nothing removed or changed; see §7).
- **Related (boundaries noted, not redesigned here):**
  - `docs/issue-12-scheduled-keepalive-plan.md` — the opt-in / lazy-scheduler /
    `internal`-constructor-seam / `HandshakeWireFormat`-constant patterns this
    plan reuses wholesale. #12 §9 explicitly flagged two follow-ups for #13: "a
    keepalive-failed-N-times signal" (still deferred here — see §10) and
    "exposing per-connection accessors" (done here as `LinkQuality`). This plan
    also **renames** #12's `KeepAliveScheduler` / `KeepAliveSchedule` (see D4).
  - `docs/issue-10-connection-liveness-plan.md` — `Liveness` (pure) +
    `Registration.lastInboundAt` (state) + `livenessTracked` (default-cost-zero
    guard). `Rtt` (pure) + `Registration.linkQuality` (state) is the same shape.
  - `docs/issue-11-handshake-refusal-and-credential-plan.md` — the
    default-method / `@JvmOverloads` additive-interface-growth pattern.
  - `docs/issue-1-tier-2-plan.md` §2.1 — the canonical inbound-datagram flow the
    new classifier branches slot into.

---

## 1. Context

### 1.1 Requirements / acceptance criteria

Not a defect — a missing capability. From the issue:

1. A **transport-level probe**: a periodic timestamped `PING` / `PONG` token
   pair, analogous to `KA` — consumed by the transport, never dispatched to the
   application handler, on either side.
2. Expose a **smoothed `rttMillis`** (EWMA) and a **recent packet-loss estimate**
   per `Connection` and on `MultiConnectionUDPClient`.
3. **Opt-in** with a **configurable probe interval**; **off by default** (zero
   threads, zero per-datagram work, zero allocation for a consumer that never
   calls the new method).
4. Generic to any real-time UDP protocol — no GameTools-specific assumptions.
5. Additive: `webtools-udp` `1.6.0`. No public signature removed or changed;
   `Connection` grows default-bodied methods; `MultiConnectionUDPClient` grows
   `@JvmOverloads` methods.

### 1.2 Where it plugs into the current design (read directly)

| Fact | Location |
|---|---|
| The client listener classifies every inbound datagram as `KA` (dropped) or data (dispatched); nothing else | `MultiConnectionUDPClient.kt:298-303` (`receiveLoop`) |
| The client comment states *"the listener thread itself never sends"* — this plan makes it send `PONG` replies | `MultiConnectionUDPClient.kt:54-55` |
| Every client outbound datagram funnels through `send(ByteArray)`, which stamps `lastOutboundAtNanos` | `MultiConnectionUDPClient.kt:334-340` |
| `MultiConnectionUDPClient` already has an `internal` constructor seam taking a `KeepAliveSchedule` (added by #12) | `MultiConnectionUDPClient.kt:112-129` |
| The server classifier: `isKeepAlive` → drop, `isHandshake` → state machine, else → `deliverData` (drops unregistered origins) | `HandshakeCoordinator.kt:120-127`, `:185-187` |
| `HandshakeCoordinator` replies inline on the listener thread (`send(REGISTERED_BYTES, origin)`) — an inline `PONG` reply is consistent | `HandshakeCoordinator.kt:147`, `:181` |
| `HandshakeCoordinator.send` already stamps `Registration.lastOutboundAt` when `keepAliveTracked` | `HandshakeCoordinator.kt:210-215` |
| `deregister` and the same-name supersede branch already `keepAliveSchedule.cancel(origin)` — the probe schedule hooks the same two points | `HandshakeCoordinator.kt:170-177`, `:250-259` |
| `Registration` already carries `@Volatile lastInboundAt` / `lastOutboundAt` / `timedOut` — a nullable `linkQuality` slot is the same pattern | `Registrations.kt:35-52` |
| `KeepAliveScheduler` is already a generic per-`InetSocketAddress` `scheduleWithFixedDelay` wrapper with a lazily-created daemon executor and a `shutDown` latch — only its *name* is keepalive-specific | `KeepAliveScheduler.kt:39-99` |
| `HandshakeWireFormat` is the published constants/verbs home the client imports; `HandshakeProtocol` aliases them internally | `HandshakeWireFormat.kt:52-140`, `HandshakeProtocol.kt:24-38` |
| The `Iam` / `KA` / `REFUSED` classifier-collision caveat is already documented as the application's responsibility | `HandshakeWireFormat.kt:35-50`, `README.md:130-136` |
| Test levels are JUnit `@Tag`-bound, one per `testing.<level>` package; socket-binding tasks serialised by `commonUdpPortLock` | `build.gradle.kts` (root `:44-62`), `webtools-udp/build.gradle.kts:25-34` |

---

## 2. Design

### 2.1 The probe exchange

A **prober** (either side, once its consumer calls `startProbe`) sends, on an
idle-aware cadence, one datagram:

```
PING <token>
```

where `<token>` is a monotonically increasing **decimal sequence number** the
prober assigns. The **responder** (the other side, unconditionally on the client,
only for a registered origin on the server) echoes the token verbatim:

```
PONG <token>
```

On receiving the `PONG` the prober looks the token up in its own record of
outstanding probes, computes `sample = now - sentAt` against its **own**
monotonic clock (no wire timestamp is trusted), feeds the sample into an EWMA,
and marks that sequence delivered. Sequence numbers never acknowledged within a
loss horizon are counted as lost.

Both `PING` and `PONG` are consumed entirely by the transport — like `KA`, they
are never handed to `start` / `actuate` / `actuateBytes`.

- **Symmetric.** Client→server and server→client probing are independent. A side
  that never calls `startProbe` still answers the other side's `PING` with a
  `PONG` (exactly as inbound `KA` is always dropped) so the peer can measure.
- **Token is a sequence number, not a timestamp.** Keeps the datagram tiny
  (`PING 4711`, ~9 bytes), keeps the RTT computation on the prober's own clock,
  and gives loss tracking a natural key. A peer that echoes the wrong token just
  forfeits that sample (counted as loss) — it degrades its *own* reported link
  quality, not an attack surface (§6).

### 2.2 Wire additions (`HandshakeWireFormat`, public)

```kotlin
/** The verb that opens a transport-level round-trip probe: `PING <token>`. */
const val PROBE_REQUEST_VERB = "PING"

/** The verb of the probe echo: `PONG <token>`, the token copied verbatim. */
const val PROBE_REPLY_VERB = "PONG"

/**
 * The default probe interval, in milliseconds (1 s), for
 * [MultiConnectionUDPClient.startProbe] and [Connection.startProbe]. One tiny
 * datagram per second per probed connection; a consumer that wants lighter
 * passes a larger interval. The probe is off unless a consumer opts in.
 */
const val DEFAULT_PROBE_INTERVAL_MILLIS = 1_000L

fun probeRequestMessage(token: String): String = "$PROBE_REQUEST_VERB $token"
fun probeReplyMessage(token: String): String = "$PROBE_REPLY_VERB $token"
fun isProbeRequest(text: String): Boolean =
    text == PROBE_REQUEST_VERB || text.startsWith("$PROBE_REQUEST_VERB ")
fun isProbeReply(text: String): Boolean =
    text == PROBE_REPLY_VERB || text.startsWith("$PROBE_REPLY_VERB ")
/** The token carried by a `PING`/`PONG` line — everything after the verb, trimmed; `""` if bare. */
fun probeToken(text: String): String =
    text.removePrefix(PROBE_REQUEST_VERB).removePrefix(PROBE_REPLY_VERB).trim()
```

`HandshakeProtocol` aliases `PROBE_REQUEST_VERB` / `PROBE_REPLY_VERB` /
`DEFAULT_PROBE_INTERVAL_MILLIS` for internal use (as it already aliases
`KEEPALIVE_TOKEN` / `DEFAULT_KEEPALIVE_INTERVAL_MILLIS`).

**Classifier-collision caveat** (documented, not defended — same class as
`Iam ` / `KA` / `REFUSED `): a text or binary application datagram whose trimmed
UTF-8 view is exactly `PING`/`PONG` or begins `PING `/`PONG ` is intercepted and
never delivered. The existing mitigation stands — lead binary datagrams with a
byte `>= 0x80` or `0x00`. Added to the `HandshakeWireFormat` KDoc, the README
binary-payload caveat, and the protocol table.

### 2.3 `Rtt` — the pure rules (new `internal object`, sibling of `Liveness` / `KeepAlive`)

```kotlin
package com.spartanlabs.webtools.udp

/**
 * Pure, socket-free, timer-free rules for the opt-in link-quality probe: EWMA
 * smoothing of an RTT sample, RTT-variation smoothing, the windowed loss ratio,
 * and whether an unacknowledged probe is old enough to count as lost.
 * Deterministic functions of their arguments. Sibling of [Liveness] / [KeepAlive].
 */
internal object Rtt {
    // RFC 6298 gains: SRTT uses 1/8, RTTVAR uses 1/4.
    private const val SRTT_ALPHA = 0.125
    private const val RTTVAR_BETA = 0.25

    /** How many probe intervals an unacked probe may age before it is declared lost. */
    const val LOSS_HORIZON_INTERVALS = 3L

    /** EWMA of the round-trip time: `prev + alpha * (sample - prev)`. */
    fun smoothedRttMillis(previousMillis: Double, sampleMillis: Double): Double =
        previousMillis + SRTT_ALPHA * (sampleMillis - previousMillis)

    /** EWMA of |SRTT - sample|, the RFC 6298 RTTVAR (a jitter proxy); never negative. */
    fun smoothedRttVarianceMillis(previousVarMillis: Double, previousRttMillis: Double, sampleMillis: Double): Double =
        (1 - RTTVAR_BETA) * previousVarMillis + RTTVAR_BETA * kotlin.math.abs(previousRttMillis - sampleMillis)

    /** `lost / (lost + delivered)`, clamped to `0.0..1.0`; `0.0` when nothing has resolved. */
    fun lossRatio(lost: Int, delivered: Int): Double {
        val resolved = lost + delivered
        return if (resolved <= 0) 0.0 else (lost.toDouble() / resolved).coerceIn(0.0, 1.0)
    }

    /**
     * True if a probe sent at [sentAtNanos] is still unacknowledged and now older
     * than [LOSS_HORIZON_INTERVALS] * [intervalMillis]. Compares in milliseconds
     * so a pathological interval cannot overflow; a [nowNanos] before
     * [sentAtNanos] yields false. Always false for [intervalMillis] <= 0.
     */
    fun isProbeLost(sentAtNanos: Long, nowNanos: Long, intervalMillis: Long): Boolean {
        if (intervalMillis <= 0L) return false
        val ageMillis = (nowNanos - sentAtNanos) / 1_000_000L
        return ageMillis >= LOSS_HORIZON_INTERVALS * intervalMillis
    }
}
```

### 2.4 `LinkQualityTracker` — the per-endpoint state (new `internal class`)

Holds the mutable estimator state for one probed endpoint: the SRTT/RTTVAR
accumulators, the outstanding-probe ring, and the sent/delivered/lost counters.
Not pure — one instance per probed connection (server) or per client.

```kotlin
package com.spartanlabs.webtools.udp

/**
 * Per-endpoint link-quality accumulator for the opt-in probe. Thread-safe: the
 * probe-scheduler thread calls [beginProbe] / [sweep], the listener thread calls
 * [completeProbe], and any consumer thread calls [snapshot]; every method is
 * `@Synchronized` (contention is ~2 short calls per probe interval).
 *
 * @param nanoClock monotonic time source; injectable for deterministic tests
 * @param windowSize how many recent probes inform the loss ratio (ring capacity)
 */
internal class LinkQualityTracker(
    private val nanoClock: () -> Long = System::nanoTime,
    private val windowSize: Int = DEFAULT_WINDOW,
) {
    private class Probe(val seq: Long, val sentAtNanos: Long) {
        var state = State.IN_FLIGHT   // IN_FLIGHT -> DELIVERED | LOST (terminal)
    }
    private enum class State { IN_FLIGHT, DELIVERED, LOST }

    private val ring = ArrayDeque<Probe>(windowSize)   // insertion order; oldest evicted first
    private var nextSeq = 0L
    private var probesSent = 0L
    private var probesDelivered = 0L
    private var srttMillis = Double.NaN               // NaN until the first sample seeds it
    private var rttVarMillis = 0.0

    /** @Volatile so [probeIntervalMillis] set on arm is visible to the sweep thread. */
    @Volatile var probeIntervalMillis: Long = HandshakeProtocol.DEFAULT_KEEPALIVE_INTERVAL_MILLIS

    /** Records a new outstanding probe and returns its sequence number for the `PING` token. */
    @Synchronized fun beginProbe(): Long { /* nextSeq++, push Probe(seq, now), evict if over capacity, probesSent++ */ }

    /** Matches [seq] to an in-flight probe, feeds `now - sentAt` into the EWMA, marks it DELIVERED. No-op for an unknown or already-terminal seq. */
    @Synchronized fun completeProbe(seq: Long) { /* first sample: srtt = sample, rttvar = sample/2 (RFC 6298); else Rtt.smoothed* */ }

    /** Marks every IN_FLIGHT probe that [Rtt.isProbeLost] declares as LOST. */
    @Synchronized fun sweep() { /* iterate ring, terminal transition IN_FLIGHT -> LOST */ }

    /** Immutable snapshot, or null until the first probe has been delivered. */
    @Synchronized fun snapshot(): LinkQuality? {
        if (probesDelivered == 0L) return null
        val lost = ring.count { it.state == State.LOST }
        val delivered = ring.count { it.state == State.DELIVERED }
        return LinkQuality(srttMillis, rttVarMillis, Rtt.lossRatio(lost, delivered), probesSent, probesDelivered)
    }

    private companion object { const val DEFAULT_WINDOW = 64 }
}
```

- **Lazily created** — a `Registration` / a client holds `null` until the first
  `startProbe`, so a consumer that never probes allocates nothing (mirrors #12's
  `keepAliveTracked` and #10's `livenessTracked` default-cost-zero rule).
- **First sample seeds** `srtt = sample`, `rttvar = sample / 2` (RFC 6298), then
  EWMA. `snapshot()` returns `null` until `probesDelivered > 0`, so a consumer
  never reads an un-seeded RTT.
- **Loss ratio** is over the ring's terminal (DELIVERED + LOST) probes only —
  in-flight probes do not drag it up until the horizon passes.

### 2.5 Public value type `LinkQuality` (new, public `data class`)

```kotlin
package com.spartanlabs.webtools.udp

/**
 * A point-in-time snapshot of one connection's measured link quality, produced by
 * the opt-in probe ([MultiConnectionUDPClient.startProbe] / [Connection.startProbe]).
 * All figures come from the same instant, so RTT and loss are mutually consistent.
 *
 * @property rttMillis smoothed round-trip time (EWMA, RFC 6298 SRTT), milliseconds
 * @property rttVarianceMillis smoothed RTT variation (RFC 6298 RTTVAR) — a jitter
 * proxy; size an interpolation delay from `rttMillis + k * rttVarianceMillis`
 * @property packetLossRatio fraction of recently-resolved probes that went
 * unanswered within the loss horizon, `0.0..1.0`; lagging and windowed — not a
 * fast congestion trigger
 * @property probesSent total probes this side has sent since [startProbe]
 * @property probesDelivered total probes answered with a matching `PONG`
 */
data class LinkQuality(
    val rttMillis: Double,
    val rttVarianceMillis: Double,
    val packetLossRatio: Double,
    val probesSent: Long,
    val probesDelivered: Long,
)
```

### 2.6 Scheduler — rename + a second instance (D4)

`KeepAliveScheduler` / `KeepAliveSchedule` are already a fully generic
per-`InetSocketAddress` `scheduleWithFixedDelay` wrapper (lazy daemon executor,
per-key `ScheduledFuture` map, `shutDown` latch, defensive `runCatching` in the
tick). Only the identifiers are keepalive-specific.

**Rename** (internal only — no public surface, no wire, no version implication
beyond the feature bump):

| Before (#12) | After |
|---|---|
| `KeepAliveSchedule` (interface) | `PeriodicSchedule` |
| `KeepAliveScheduler` (class) | `PeriodicScheduler` |
| `FakeKeepAliveSchedule` (test support) | `FakePeriodicSchedule` |
| `KeepAliveSchedulerValidationTest` (L2) | `PeriodicSchedulerValidationTest` |
| `KeepAliveSchedulerTest` (L3) | `PeriodicSchedulerTest` |

Logic, key type (`InetSocketAddress`), and semantics are **unchanged**. The
`KeepAlive` pure object and `HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS`
keep their names (they *are* keepalive-specific).

**Each side then runs a second instance** for probes:

| Side | Keepalive (existing) | Probe (new) |
|---|---|---|
| client | `PeriodicScheduler("mcupc-keepalive")` | `PeriodicScheduler("mcupc-probe")` |
| server | `PeriodicScheduler("mcups-keepalive")` | `PeriodicScheduler("mcups-probe")` |

Both are lazy: no `mcup?-probe` thread exists until the first `startProbe`. Two
instances (not one shared, keyed by `(kind, endpoint)`) keeps each feature's
cancellation and shutdown independent, at a cost of one extra idle daemon thread
only when both features are opted in. See D4 for the alternatives.

### 2.7 Client — `startProbe` / `stopProbe` / `linkQuality`

`MultiConnectionUDPClient`'s `internal` constructor gains one collaborator
(`probe: PeriodicSchedule`); the public `@JvmOverloads` constructor is unchanged
and supplies `PeriodicScheduler("mcupc-probe")`:

```kotlin
class MultiConnectionUDPClient internal constructor(
    private val serverAddress: InetAddress,
    private val serverPort: Int,
    private val receiveBufferBytes: Int,
    private val keepAlive: PeriodicSchedule,
    private val probe: PeriodicSchedule,   // NEW
) {
    @JvmOverloads
    constructor(serverAddress: InetAddress, serverPort: Int = …, receiveBufferBytes: Int = …) :
        this(serverAddress, serverPort, receiveBufferBytes,
             PeriodicScheduler("mcupc-keepalive"), PeriodicScheduler("mcupc-probe"))

    /** Lazily created on the first startProbe; null => no probe ever armed => zero cost. */
    @Volatile private var linkQualityTracker: LinkQualityTracker? = null
```

```kotlin
/**
 * Starts an opt-in link-quality probe: every [intervalMillis] this client sends
 * one `PING <seq>` to the server, and each matching `PONG` updates a smoothed
 * RTT / jitter / packet-loss estimate readable via [linkQuality]. Off until
 * called; runs until [stopProbe] or [stop]. Backed by one daemon thread
 * (`mcupc-probe`) created on the first call. Calling this again re-arms it (last
 * call wins). Call after [handshake] — probes sent before registration go
 * unanswered and register as loss.
 *
 * @param intervalMillis probe period; must be > 0. Default
 * [HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS] (1 s).
 * @return [Result.success] once armed; [Result.failure] with
 * [IllegalArgumentException] for a non-positive interval or [IllegalStateException]
 * if [stop] has already run.
 */
@JvmOverloads
fun startProbe(intervalMillis: Long = HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS): Result<Unit> {
    val tracker = linkQualityTracker ?: LinkQualityTracker().also { linkQualityTracker = it }
    tracker.probeIntervalMillis = intervalMillis
    return probe.schedule(serverEndpoint, intervalMillis) {
        tracker.sweep()
        send(HandshakeWireFormat.probeRequestMessage(tracker.beginProbe().toString()))
            .onFailure { log.warn("Scheduled probe send failed", it) }
    }.onFailure { log.error("Could not start the link-quality probe", it) }
}

/** Stops the probe started by [startProbe]. Idempotent; [stop] also does this. The last [linkQuality] snapshot remains readable. */
fun stopProbe(): Result<Unit> = runCatching { probe.cancel(serverEndpoint) }

/** The latest link-quality snapshot, or null until the first `PONG` has come back. */
fun linkQuality(): LinkQuality? = linkQualityTracker?.snapshot()
```

`receiveLoop` classification (after the existing `isKeepAlive` drop, before
`deliver`):

```kotlin
when {
    HandshakeWireFormat.isKeepAlive(text) -> log.trace("Dropped keepalive from server")
    HandshakeWireFormat.isProbeRequest(text) ->
        send(HandshakeWireFormat.probeReplyMessage(HandshakeWireFormat.probeToken(text)))
            .onFailure { log.debug("Could not answer a PING", it) }
    HandshakeWireFormat.isProbeReply(text) ->
        HandshakeWireFormat.probeToken(text).toLongOrNull()
            ?.let { seq -> linkQualityTracker?.completeProbe(seq) }
    else -> deliver(bytes, text)
}
```

- `stop()` gains a `probeStopped` step (`runCatching { probe.shutdown() }`)
  wired into the teardown `flatMap` chain **before** `socket.close()`, alongside
  the existing `keepAliveStopped` step.
- The `PONG`-reply send from the listener thread is new — the class KDoc's
  "the listener thread itself never sends" boundary note is updated to
  "…never sends application data; it answers an inbound `PING` with a `PONG` inline".

### 2.8 Server — `Connection.startProbe` / `stopProbe` / `linkQuality`

**`Connection` (public interface) — default methods**, mirroring #12's
`startKeepAlive`:

```kotlin
/**
 * Starts an opt-in link-quality probe toward [peer]: every [intervalMillis] the
 * server sends one `PING`, and each `PONG` updates a smoothed RTT / jitter /
 * loss estimate readable via [linkQuality]. Runs until [stopProbe], [terminate],
 * or server `stop()`. Server -> client `PING` reaches the client whenever the
 * session is live (the client is actively holding its own mapping open); the
 * same cone-NAT caveat as [keepAlive] applies if the client has gone silent.
 *
 * The default returns [Result.failure] — only the production [UDPConnection]
 * supports a probe.
 * @param intervalMillis probe period; must be > 0. Default
 * [HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS] (1 s).
 */
fun startProbe(intervalMillis: Long): Result<Unit> =
    Result.failure(UnsupportedOperationException("This Connection does not support a link-quality probe"))

/** Starts the probe at [HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS]. @see startProbe */
fun startProbe(): Result<Unit> = startProbe(HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS)

/** Stops the probe started by [startProbe]. Idempotent; [terminate] and server `stop()` also do this. */
fun stopProbe(): Result<Unit> = Result.success(Unit)

/** The latest link-quality snapshot for this connection, or null if no probe is running or none has resolved yet. */
fun linkQuality(): LinkQuality? = null
```

Two explicit `startProbe` overloads (not `@JvmOverloads` on an interface method —
awkward, per #12 D1); the no-arg overload carries its own full KDoc block with
`@return` and `@see`.

**`UDPConnection`** overrides `startProbe(Long)` / `stopProbe()` / `linkQuality()`,
delegating to `ClientChannel`:

```kotlin
override fun startProbe(intervalMillis: Long): Result<Unit> =
    channel.scheduleProbe(peer, intervalMillis)
        .onFailure { log.error("Connection '{}' could not start a link-quality probe", name, it) }
override fun stopProbe(): Result<Unit> =
    channel.cancelProbe(peer).onFailure { log.error("Connection '{}' could not stop its probe", name, it) }
override fun linkQuality(): LinkQuality? = channel.linkQualityOf(peer)
```

**`ClientChannel` (internal)** gains:

```kotlin
fun scheduleProbe(peer: InetSocketAddress, intervalMillis: Long): Result<Unit>
fun cancelProbe(peer: InetSocketAddress): Result<Unit>
fun linkQualityOf(peer: InetSocketAddress): LinkQuality?
```

**`HandshakeCoordinator`:**

- New trailing constructor collaborator `private val probeSchedule: PeriodicSchedule`.
- `classify` gains two branches (after `isKeepAlive`, before `isHandshake`):
  ```kotlin
  HandshakeProtocol.isProbeRequest(text) -> {
      val reg = registrations.findByOrigin(origin)
      if (reg == null) Result.success(Unit).also { log.debug("PING from unregistered {}, dropped", origin) }
      else send(HandshakeWireFormat.probeReplyMessage(HandshakeWireFormat.probeToken(text)), origin)
  }
  HandshakeProtocol.isProbeReply(text) -> {
      HandshakeWireFormat.probeToken(text).toLongOrNull()
          ?.let { seq -> registrations.findByOrigin(origin)?.linkQuality?.completeProbe(seq) }
      Result.success(Unit)
  }
  ```
- Implement `scheduleProbe`:
  ```kotlin
  override fun scheduleProbe(peer: InetSocketAddress, intervalMillis: Long): Result<Unit> {
      val reg = registrations.findByOrigin(peer)
          ?: return Result.failure(IllegalStateException("No registration for $peer"))
      val tracker = reg.linkQuality ?: LinkQualityTracker().also { reg.linkQuality = it }
      tracker.probeIntervalMillis = intervalMillis
      return probeSchedule.schedule(peer, intervalMillis) {
          val live = registrations.findByOrigin(peer)?.linkQuality ?: return@schedule
          live.sweep()
          send(HandshakeWireFormat.probeRequestMessage(live.beginProbe().toString()), peer)
              .onFailure { log.warn("Scheduled probe to {} failed", peer, it) }
      }
  }
  ```
- `cancelProbe(peer)` → `runCatching { probeSchedule.cancel(peer) }`.
- `linkQualityOf(peer)` → `registrations.findByOrigin(peer)?.linkQuality?.snapshot()`.
- `deregister` and the same-name supersede branch also `probeSchedule.cancel(<origin>)`.
- New `fun shutProbe()` → `probeSchedule.shutdown()`.
- Class KDoc gains a sixth responsibility bullet (the probe seam).

**`Registration`** gains `@Volatile var linkQuality: LinkQualityTracker? = null`
(with a `@property` KDoc entry: created on the first `scheduleProbe` for this
connection; written there and by `completeProbe` on the listener thread, read by
the `mcups-probe` thread and by consumer calls to `Connection.linkQuality`).

**`MultiConnectionUDPServer`:**

- New `private val probeScheduler = PeriodicScheduler("mcups-probe")`, passed to
  the `HandshakeCoordinator(...)` construction as `probeSchedule`.
- `stop()` gains a `probeStopped` step (`runCatching { coordinator.shutProbe() }`)
  wired into the teardown `flatMap` chain, alongside `keepAliveStopped`.
- Class KDoc: a fifth daemon thread `mcups-probe` (lazy, opt-in via
  `Connection.startProbe`); a new `### Link quality (RTT & packet loss)` section;
  a pointer to §2.9's diagram.

### 2.9 Flow — client probes the server

```mermaid
sequenceDiagram
    participant App as consumer
    participant Pr as mcupc-probe
    participant T as LinkQualityTracker
    participant L as mcupc-listener
    participant Srv as server

    App->>Pr: startProbe(1_000)
    Note over Pr: executor created lazily; poll = 250 ms

    loop every 1_000 ms
        Pr->>T: sweep()  (age out unacked probes -> LOST)
        Pr->>T: beginProbe() -> seq
        Pr->>Srv: PING <seq>   (via send(); stamps lastOutboundAtNanos)
        Srv->>L: PONG <seq>
        L->>T: completeProbe(seq)  (sample = now - sentAt -> EWMA)
    end

    App->>Pr: linkQuality()
    Pr-->>App: LinkQuality(rttMillis, rttVarianceMillis, packetLossRatio, sent, delivered)

    Note over Srv: server never called startProbe — it just echoes every PING

    App->>Pr: stopProbe()  (or stop())
    Pr->>Pr: future cancelled; executor shutdownNow() on stop()
```

The server→client flow is identical with `mcups-probe` /
`Connection.startProbe()` / `Registration.linkQuality` substituted and the
client's `receiveLoop` doing the `PONG` echo.

### 2.10 Alternatives considered

| Option | Rejected because |
|---|---|
| **Echo the sender's wall/`nanoTime` timestamp in the token** instead of a sequence number | Larger datagram; invites trusting a wire timestamp; the prober must keep a send-time record for loss tracking anyway, so the sequence number is free. |
| **One shared `PeriodicScheduler` per side, keyed by `(kind, endpoint)`** | Fewer threads (one timer per side ever), but couples keepalive and probe shutdown/cancel into one executor and changes #12's key type. Noted as a deferred consolidation (D4, §10) if a fourth periodic task appears. |
| **A standalone `ProbeScheduler` duplicating `KeepAliveScheduler`** | Straight copy-paste of a proven 60-line class. The rename-and-reuse costs less and keeps one implementation. |
| **Individual nullable accessors (`rttMillis: Double?`, `packetLossRatio: Double?`)** on `Connection` / the client | Freely extensible without a clean break, but RTT and loss could then be read from different instants, and it is more surface. `LinkQuality` gives one consistent snapshot. (D1) |
| **Passive RTT (no `PING`)** — infer RTT from application request/response timing | Needs an application-level request/response notion WebTools does not have; the issue explicitly asks for a transport probe. |
| **Server measures RTT off the client's inbound `PING` arrival cadence** | Arrival cadence is not a round trip; the server still needs its own `PING` to measure its RTT. Hence the symmetric design. |
| **Always-on probe (opt-out)** | A per-datagram/thread cost for every current consumer, including text-only clients that do not care about RTT. Opt-in keeps `1.6.0` a pure addition. |
| **New abstract methods on `Connection`** | `Connection` is published; abstract methods break every external implementor. Default methods are the module idiom (#12 D1, `actuateBytes`, `onClientDisconnect`). |
| **Reuse `KeepAlive`'s `pollIntervalMillis` for the probe poll** | Fine as-is — the probe scheduler already calls `KeepAlive.pollIntervalMillis` via the shared `PeriodicScheduler`. No separate poll rule needed; `Rtt` owns only the loss horizon. |
| **Fold loss detection into a fixed count (`seq <= highestAcked - K`)** | Time-based (`LOSS_HORIZON_INTERVALS * interval`) tolerates reordering and a stalled peer without a magic packet count; the ring bounds memory. |

---

## 3. File-by-file changes

All paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/`
unless noted.

### 3.1 `Rtt.kt` (new, internal)
The `internal object Rtt` from §2.3 — `smoothedRttMillis`,
`smoothedRttVarianceMillis`, `lossRatio`, `isProbeLost`, `LOSS_HORIZON_INTERVALS`,
private gain constants. Level-2 KDoc on the object and every function; inner-core
comment citing RFC 6298 for the gains and the ms-comparison overflow guard.

### 3.2 `LinkQualityTracker.kt` (new, internal)
The `internal class LinkQualityTracker` from §2.4. Level-2 KDoc on the class and
every method; inner-core comments on the `@Synchronized` writer/reader threads,
the first-sample RFC 6298 seeding, the ring eviction, and why `snapshot()`
returns `null` before the first delivery.

### 3.3 `LinkQuality.kt` (new, public)
The `data class LinkQuality` from §2.5. Level-2 KDoc — `@property` for every
field, including the "size interpolation delay from `rtt + k*variance`" hint and
the "lagging / windowed, not a fast trigger" caveat on `packetLossRatio`.

### 3.4 `HandshakeWireFormat.kt` (public)
Add `PROBE_REQUEST_VERB`, `PROBE_REPLY_VERB`, `DEFAULT_PROBE_INTERVAL_MILLIS`,
`probeRequestMessage`, `probeReplyMessage`, `isProbeRequest`, `isProbeReply`,
`probeToken` (§2.2). Class KDoc: a new `### Link-quality probe` paragraph
(round-trip `PING`/`PONG`, transport-consumed, off by default, no effect on any
existing token) and add `PING `/`PONG ` to the classifier-collision caveat list.

### 3.5 `HandshakeProtocol.kt` (internal)
Alias `PROBE_REQUEST_VERB` / `PROBE_REPLY_VERB` / `DEFAULT_PROBE_INTERVAL_MILLIS`
from `HandshakeWireFormat`; add `isProbeRequest` / `isProbeReply` delegating to
`HandshakeWireFormat` (mirrors the existing `isKeepAlive` delegate).

### 3.6 `KeepAliveScheduler.kt` → `PeriodicScheduler.kt` (internal, rename)
Rename the file, the `KeepAliveSchedule` interface → `PeriodicSchedule`, and the
`KeepAliveScheduler` class → `PeriodicScheduler`. **No logic change.** Update the
class/interface KDoc to drop the keepalive-specific wording ("a keepalive timer"
→ "a periodic per-endpoint task"). Keep the `threadName` constructor arg. All
call sites: `HandshakeCoordinator`, `MultiConnectionUDPServer`,
`MultiConnectionUDPClient`, `FakeKeepAliveSchedule`.

### 3.7 `ClientChannel.kt` (internal)
Add `scheduleProbe(peer, intervalMillis)` / `cancelProbe(peer)` /
`linkQualityOf(peer)` with Level-2 KDoc (mirrors the `scheduleKeepAlive` /
`cancelKeepAlive` block added by #12).

### 3.8 `Connection.kt` (public)
The four default methods from §2.8 (`startProbe(Long)` / `startProbe()` /
`stopProbe()` / `linkQuality()`). The no-arg `startProbe()` gets its own full
KDoc block (summary + `@return` + `@see startProbe`). Class KDoc: one sentence
that `UDPConnection` is still socket-free but a probe borrows the server's shared
`mcups-probe` thread and updates a per-connection estimator.

### 3.9 `UDPConnection.kt` (public, internal ctor)
Override `startProbe(Long)` / `stopProbe()` / `linkQuality()` per §2.8. Class
KDoc: the "owns no socket and no thread" note gains the probe caveat (shared
`mcups-probe` thread, cancelled by `terminate()`).

### 3.10 `Registrations.kt` (internal)
`Registration`: add `@Volatile var linkQuality: LinkQualityTracker? = null` with
a `@property` KDoc entry (§2.8).

### 3.11 `HandshakeCoordinator.kt` (internal)
- Constructor: new trailing `private val probeSchedule: PeriodicSchedule`
  (`@param` KDoc); rename the `keepAliveSchedule` param **type** to
  `PeriodicSchedule` (name unchanged).
- `classify`: the two new branches (§2.8).
- Implement `ClientChannel.scheduleProbe` / `cancelProbe` / `linkQualityOf`.
- `deregister` (`:250-259`) and supersede branch (`:170-177`): add
  `probeSchedule.cancel(<origin>)`.
- New `fun shutProbe()`.
- Class KDoc: sixth responsibility bullet.

### 3.12 `MultiConnectionUDPServer.kt` (public)
- New `private val probeScheduler = PeriodicScheduler("mcups-probe")`; pass to
  `coordinator` as `probeSchedule`; rename the `keepAliveScheduler` field's type
  reference.
- `stop()`: new `probeStopped` step + `flatMap` wiring.
- Class KDoc: `### Concurrency` fifth daemon thread `mcups-probe`; new
  `### Link quality (RTT & packet loss)` section; diagram pointer to this doc §2.9.

### 3.13 `MultiConnectionUDPClient.kt` (public)
- `internal` constructor: new `probe: PeriodicSchedule` param; public
  `@JvmOverloads` constructor supplies `PeriodicScheduler("mcupc-probe")`. Rename
  the `keepAlive` param type to `PeriodicSchedule`. `init` guard unchanged.
- New `@Volatile private var linkQualityTracker: LinkQualityTracker? = null`.
- `receiveLoop` classification: the `when` from §2.7.
- New `@JvmOverloads fun startProbe(intervalMillis: Long = …)`, `fun stopProbe()`,
  `fun linkQuality(): LinkQuality?` (§2.7).
- `stop()`: `probeStopped` step before `socket.close()`, into the `flatMap` chain.
- KDoc: `### Concurrency` — third/fourth daemon thread `mcupc-probe`, lazy, shut
  by `stop()`; the "listener never sends" boundary note updated (§2.7);
  "Concurrent-call contract" — `startProbe` / `stopProbe` safe from any thread,
  may race `stop()` (post-`stop()` `startProbe` fails its `Result`).

### 3.14 Test-support fixtures
`webtools-udp/src/test/kotlin/com/spartanlabs/testing/support/webtools/udp/`
- `FakeKeepAliveSchedule.kt` → `FakePeriodicSchedule.kt` (rename; implements
  `PeriodicSchedule`; content otherwise unchanged).
- `FakeClientChannel.kt`: implement `scheduleProbe` / `cancelProbe` /
  `linkQualityOf` — record `(peer, intervalMillis)` and `peer`; return a
  configurable `Result` / a configurable `LinkQuality?`.
- `FakeConnection.kt`: add `startProbe` / `stopProbe` / `linkQuality` overrides
  that count calls and return configurable values.

### 3.15 `README.md` (repo root)
- Install snippet: `webtools-udp:1.5.0` → `1.6.0`.
- Components table: `MultiConnectionUDPClient` and `Connection` rows — add
  `startProbe` / `stopProbe` / `linkQuality`; new `LinkQuality` row;
  `HandshakeWireFormat` row — add `PING` / `PONG`.
- Protocol table: new row `client ↔ server | PING <token> / PONG <token>
  (link-quality probe; transport-consumed) | port 9998, same socket`.
- Binary-payload caveat (`:130-136`): add `PING ` / `PONG ` to the reserved
  prefixes.
- New `### Link quality (RTT & packet loss)` subsection after "Scheduled
  keepalive": opt-in on both sides, off by default, `startProbe()` /
  `stopProbe()` / `linkQuality()`, the `LinkQuality` fields, 1 s default, one
  lazy daemon thread per side shut by `stop()` / `terminate()`, the responder
  answers `PING` unconditionally, `packetLossRatio` is lagging/windowed. Short
  code sketch mirroring the other subsections.

### 3.16 `webtools-udp/build.gradle.kts`
- `version` `"1.5.0"` → `"1.6.0"` — lands in the **Stage 3** `build:` commit
  (§9), not with the code.
- Version comment block (rides Stage 1 with the code it describes): add
  ```
  // 1.6.0: opt-in per-connection link-quality probe - MultiConnectionUDPClient.startProbe /
  // stopProbe / linkQuality and Connection.startProbe / stopProbe / linkQuality, a periodic
  // transport-level PING/PONG round trip yielding a smoothed RTT, RTT-variance, and windowed
  // packet-loss ratio (LinkQuality); off by default; one lazily-created daemon
  // ScheduledExecutorService per side. New wire tokens PING/PONG, additive (Issue #13).
  ```
- No change to the `commonUdpPortLock` task list (the new socket-binding tests
  are `integrationTest` / `e2eTest` / `nonfunctionalTest`, already listed).

### 3.17 `docs/issue-13-link-quality-probe-plan.md`
This document. Committed in the same commit as §3.1–§3.16 (stage 1). Backfill the
`Commit:` / `PR:` header fields once they exist (as done for #8–#12).

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves with this change |
|---|---|---|
| **Inner core** (in-editor) | yes | Comments: RFC 6298 gains and first-sample seeding; the ms-comparison overflow guard in `Rtt`; `LinkQualityTracker`'s `@Synchronized` writer/reader threads and ring eviction; why `snapshot()` is `null` before the first delivery; the `PONG`-from-listener-thread send; why the probe scheduler is a second `PeriodicScheduler` instance not a shared one. |
| **Component ring** (KDoc) | yes | New KDoc: `Rtt` (4 functions), `LinkQualityTracker` (every method), `LinkQuality` (every `@property`), `HandshakeWireFormat` probe verbs/constant/helpers, `HandshakeProtocol` aliases, `ClientChannel.scheduleProbe` / `cancelProbe` / `linkQualityOf`, `Connection.startProbe(Long)` / `startProbe()` / `stopProbe()` / `linkQuality()`, `UDPConnection` overrides, `Registration.linkQuality`, `HandshakeCoordinator` `@param probeSchedule` + `shutProbe`, `MultiConnectionUDPClient` `@param probe` + `startProbe` / `stopProbe` / `linkQuality`. Renamed KDoc: `PeriodicSchedule` / `PeriodicScheduler` (de-keepalive'd wording). |
| **Boundary ring** (protocol) | **yes — additive wire change** | Two new tokens `PING <token>` / `PONG <token>`, transport-consumed both directions, round-trip, off unless a consumer opts in. Documented in `HandshakeWireFormat` KDoc, the README protocol table, and this plan §2.1–§2.2. The `PING `/`PONG ` classifier-collision caveat is added to the existing `Iam`/`KA`/`REFUSED` caveat everywhere it appears. |
| **Architectural outer layer** | yes | One new opt-in daemon thread per side (`mcupc-probe`, `mcups-probe`), lazily created on the first `startProbe`, shut on `stop()`. Documented in both `### Concurrency` KDocs with a pointer to §2.9's sequence diagram. |
| **README** | yes | Per the README-currency rule: new public methods on two types, new public type, new public constants, a protocol change, a new (conditional) thread per side, a dependency-coordinate bump. |

---

## 5. Test plan (5-Level hierarchy)

Package root mirrors production `com.spartanlabs.webtools.udp` →
`com.spartanlabs.testing.<level>.webtools.udp`. One class per file; each class
carries its level `@Tag`. Socket-binding classes run under `commonUdpPortLock`.
Probe intervals in automated tests are small (200–400 ms) with the poll floored
at 250 ms; timing assertions use generous slack and "within a window" phrasing.
`LinkQualityTracker` tests inject a controllable `nanoClock`; the pure `Rtt`
math is Level 4a.

### Level 1 — `testing.gating`

- **Extend `HandshakeWireFormatGatingTest.kt`** (socket-free):
  `DEFAULT_PROBE_INTERVAL_MILLIS == 1_000L`; `PROBE_REQUEST_VERB == "PING"`,
  `PROBE_REPLY_VERB == "PONG"`; `isProbeRequest` / `isProbeReply` /
  `probeToken` round-trip against `probeRequestMessage` / `probeReplyMessage`
  (incl. bare `PING`, `PING 42`, and a non-probe string).
- **New `RttGatingTest.kt`** (socket-free smoke): `smoothedRttMillis` moves
  toward the sample; `lossRatio(0, 0) == 0.0`; `isProbeLost` true past the
  horizon, false within it, false for `interval <= 0`.
- **New `ProbeValidationGatingTest.kt`**: `MultiConnectionUDPClient` (ephemeral
  port) — `startProbe(0)` / `startProbe(-1)` → `Result.failure(IllegalArgumentException)`
  and arm nothing (a fake peer socket sees no `PING` in a short window);
  `linkQuality()` is `null` before any probe; then `stop()`. A `UDPConnection`
  over `FakeClientChannel` — `startProbe(0)` surfaces the channel's failure;
  `linkQuality()` returns the channel's configured value.

### Level 2 — `testing.component`

- **New `LinkQualityTrackerTest.kt`** (injected `nanoClock`):
  - `snapshot()` is `null` before the first `completeProbe`;
  - `beginProbe()` then `completeProbe(seq)` with the clock advanced 40 ms →
    `rttMillis ≈ 40.0` (first sample seeds SRTT), `rttVarianceMillis ≈ 20.0`;
  - a second sample of 80 ms → `rttMillis` between 40 and 80, nearer 40 (α = 1/8);
  - `completeProbe` for an unknown seq, and a duplicate `completeProbe` for an
    already-delivered seq, are no-ops (no double count of `probesDelivered`);
  - `sweep()` after the clock passes `LOSS_HORIZON_INTERVALS * interval` moves
    the still-in-flight probes to LOST → `packetLossRatio` reflects
    `lost / (lost + delivered)`;
  - after `windowSize` probes only the last `windowSize` inform the ratio (ring
    eviction);
  - `probesSent` / `probesDelivered` counts are exact.
- **New `UDPConnectionProbeTest.kt`** over `FakeClientChannel`:
  - `startProbe(5_000)` calls `channel.scheduleProbe(peer, 5_000)` once and
    returns its `Result`; `startProbe()` passes `DEFAULT_PROBE_INTERVAL_MILLIS`;
  - `stopProbe()` calls `channel.cancelProbe(peer)`;
  - `linkQuality()` returns `channel.linkQualityOf(peer)`;
  - a `Result.failure` from the channel is propagated and logged (`LogCapture`).
- **New `MultiConnectionUDPClientProbeTest.kt`** — client built via its `internal`
  constructor with a `FakePeriodicSchedule`:
  - `startProbe(300)` records exactly one `schedule(serverEndpoint, 300, tick)`;
    `startProbe()` records interval `DEFAULT_PROBE_INTERVAL_MILLIS`;
  - invoking the recorded `tick` sends exactly one `PING <n>` (fake peer
    `DatagramSocket` receives it) with a strictly increasing token across ticks;
  - feeding a matching `PONG <n>` back through a real loopback listen populates
    `linkQuality()` (a plausible small `rttMillis`, `packetLossRatio == 0.0`);
  - a `PONG` with an unknown/garbage token neither throws nor populates;
  - a repeat `startProbe(500)` records a second `schedule` for the key
    (last-call-wins; the fake also records the preceding `cancel`);
  - `stopProbe()` records `cancel(serverEndpoint)`; `stop()` records `shutdown()`;
  - `startProbe` after `stop()` → `Result.failure`, no new `schedule`
    (`FakePeriodicSchedule(failAfterShutdown = true)`).
  - *(Known compromise, same class as #12's QA-amendment-2: asserting the `PING`
    went out uses a loopback socket rather than a pure send-spy, because
    `MultiConnectionUDPClient` has no injectable send seam. The tick's decision
    logic is otherwise driven through the `PeriodicSchedule` seam.)*
- **Extend `HandshakeCoordinatorTest.kt`** (synchronous `dispatch = { it() }`,
  `FakePeriodicSchedule` for both scheduler params):
  - update `newCoordinator()` and every inline `HandshakeCoordinator(...)` for
    the new `probeSchedule` param and the `PeriodicSchedule` type rename;
  - inbound `PING <tok>` from a **registered** origin → exactly one `PONG <tok>`
    to that origin, nothing dispatched to the handler;
  - inbound `PING` from an **unregistered** origin → dropped, no send;
  - inbound `PONG <tok>` for a live probe → `linkQualityOf(peer)` eventually
    non-null;
  - `scheduleProbe(peer, 300)` then invoking the recorded `tick` → one `PING` to
    `peer` and `Registration.linkQuality` becomes non-null;
  - `scheduleProbe` for an unknown peer → `Result.failure`;
  - `deregister(peer)` calls `probeSchedule.cancel(peer)`;
  - a same-name `Iam` supersede calls `probeSchedule.cancel(staleOrigin)`;
  - `shutProbe()` calls `probeSchedule.shutdown()`.
- **Rename `KeepAliveSchedulerValidationTest.kt` → `PeriodicSchedulerValidationTest.kt`**;
  update the type names. Behaviour unchanged (non-positive interval →
  `IllegalArgumentException` failure; schedule after shutdown →
  `IllegalStateException` failure).

### Level 3 — `testing.integration` (real threads / sockets)

- **Rename `KeepAliveSchedulerTest.kt` → `PeriodicSchedulerTest.kt`**; update type
  names. Behaviour coverage unchanged (ticks fire at the poll cadence; `cancel`
  stops one key; a re-`schedule` replaces; `shutdown` stops all and leaves no
  named thread; `schedule` after `shutdown` fails; a throwing tick does not
  cancel the repeating task).
- **New `MultiConnectionUDPClientProbeTest.kt`** (real client socket, fake peer
  `DatagramSocket` that echoes `PONG` for each `PING`):
  - after `handshake` + `startProbe(300)`, the peer receives `PING` roughly every
    300 ms while idle; each echoed `PONG` drives `client.linkQuality()` to a
    non-null small-`rttMillis` snapshot with `packetLossRatio == 0.0`;
  - when the peer stops echoing, `packetLossRatio` climbs toward 1.0 within a few
    loss horizons and `rttMillis` stops advancing;
  - `stopProbe()` halts the `PING` stream and freezes the last snapshot;
    a later `startProbe(300)` resumes it;
  - `stop()` halts the stream and leaves no `mcupc-probe` thread
    (`Thread.getAllStackTraces`);
  - a default client that never calls `startProbe` has no `mcupc-probe` thread
    and `linkQuality()` is `null`;
  - `startProbe` after `stop()` → `Result.failure`, no thread.
- **New `MultiConnectionUDPServerProbeTest.kt`** (real server, own instance,
  `@AfterTest { server?.stop() }`, a real `MultiConnectionUDPClient` as the peer
  so its listener auto-answers `PING`):
  - the test's `onClientConnect` calls `connection.startProbe(300)`; the client
    receives server→client `PING`, auto-replies `PONG`, and
    `connection.linkQuality()` becomes non-null with a small `rttMillis`;
  - `connection.stopProbe()` and `connection.terminate()` each halt the stream
    and cancel the schedule;
  - reverse direction: the client calls `startProbe`, the server echoes every
    `PING` even though the server-side `Connection` never called `startProbe`,
    and `client.linkQuality()` populates;
  - a default server has no `mcups-probe` thread; `server.stop()` leaves none.

### Level 4a — `testing.deterministic`

- **New `RttTest.kt`** (pure, mirrors `LivenessTest` / `KeepAliveTest`):
  - `smoothedRttMillis` — exact α-weighting for known inputs; equal inputs return
    the same value (no drift, no NaN); monotone convergence over a sample run;
  - `smoothedRttVarianceMillis` — exact β-weighting; always `>= 0`; shrinks when
    samples equal SRTT;
  - `lossRatio` — `(3, 1) == 0.75`; `(0, 0) == 0.0`; clamped to `0.0..1.0` for
    absurd inputs;
  - `isProbeLost` — true at/after `LOSS_HORIZON_INTERVALS * interval`, false
    just under; false for `interval <= 0`; false for `now < sentAt`; no overflow
    at `Long.MAX_VALUE`.

### Level 4b — `testing.e2e`

- **New `MultiConnectionUDPProbeE2ETest.kt`** — real `MultiConnectionUDPClient` +
  real `MultiConnectionUDPServer` subclass over loopback:
  - **headline:** client `handshake` → `start` → `startProbe(200)`; within ~1 s
    `client.linkQuality()` reports a sub-50 ms `rttMillis`, `packetLossRatio ==
    0.0`, `probesDelivered` climbing; `start`'s handler never sees a `PING`/`PONG`;
  - server side: `onClientConnect` calls `connection.startProbe(200)`;
    `connection.linkQuality()` populates symmetrically;
  - **induced loss:** a server subclass (or client wrapper) that drops every
    Nth outbound datagram → `packetLossRatio` approaches `1/N` within slack on
    the affected direction, and recovers to `0.0` after the drop stops;
  - **application traffic unaffected:** interleaved `pushToAll` / `client.send`
    payloads (text and binary leading `0x00`) arrive intact throughout;
  - `client.stop()` / `server.stop()` are clean and leave no probe threads.

### Level 4c — `testing.nonfunctional`

- **New `MultiConnectionUDPClientProbeNonFunctionalTest.kt`**:
  - 200 `startProbe` / `stopProbe` cycles leave at most one `mcupc-probe` thread
    and no leaked `ScheduledFuture` (a final `startProbe` still delivers `PING`);
  - a wedged `start` handler (sleeps 2 s) does not delay `PING` — the probe
    thread is independent of `mcupc-dispatch`;
  - `LinkQualityTracker` under 100k interleaved `beginProbe` (one thread) +
    `completeProbe` (another) + `snapshot` (a third): no exception, `rttMillis`
    stays finite and `>= 0`, `packetLossRatio` stays in `0.0..1.0`;
  - a probe datagram is small (`PING <seq>` well under 32 bytes);
  - a probe tick racing `stop()` never throws out of either call.
- **New `MultiConnectionUDPServerProbeNonFunctionalTest.kt`**:
  - 50 connections each `startProbe` → one shared `mcups-probe` thread; every
    peer receives `PING`;
  - `terminate()` on any connection stops its `PING` within a poll cycle, frees
    its `Registration.linkQuality` reference for GC, and does not disturb others;
  - **unused cost:** a `PING`/data burst against a server whose consumers never
    call `startProbe` allocates no `LinkQualityTracker` (assert via an `internal`
    read) and starts no thread;
  - the responder path adds only bounded work — N inbound `PING`/s produce N
    `PONG`/s inline on the listener thread, never touching `mcups-dispatch`.

### Level 5 — `testing.uat`

- **Extend `MultiConnectionUDPServerUatTest.kt`** (or a client UAT) with one
  `@Disabled` scenario: a real client over a real path (ideally WAN / NAT, with
  `tc netem` available) calls `handshake` → `start` → `startProbe()` and logs
  `linkQuality()` once a second for several minutes. A human confirms `rttMillis`
  tracks an independent `ping`, `rttVarianceMillis` rises under jitter injection,
  and `packetLossRatio` rises to match an injected drop rate and recovers. PASS =
  the numbers are usable for sizing an interpolation delay and an adaptive send
  rate.

### Cannot be automated

- Whether the RFC 6298 gains, the 1 s default, and the 3-interval loss horizon
  produce numbers actually useful to a real netcode's interpolation / send-rate
  control — only integration in GameTools / its client shows this.
- Real-path RTT / jitter / loss accuracy versus `ping` / `tc netem` ground truth.
- Probe overhead at consumer scale (thousands of connections at 1 Hz, over days).
- Server→client `PING` delivery across the different NAT filtering behaviours —
  loopback cannot model it.

---

## 6. Risks & edge cases

- **Additive wire change — two new tokens.** `PING <token>` / `PONG <token>` are
  new; an old peer never sends them and a new peer sends them only after a
  consumer calls `startProbe`. A `1.6.0` client against a `1.5.x` server that
  calls `startProbe` will send `PING`s the old server does not recognise — the
  old server's classifier falls through to `deliverData`, which **drops them as
  unregistered-origin data only if unregistered; for a registered origin it
  would dispatch `PING <n>` to the application handler.** Mitigation / statement:
  `startProbe` is documented as requiring both ends on `1.6.0`+ (same "not
  enforced until both ends upgrade" note as #11's credential); a consumer that
  calls `startProbe` against a pre-`1.6.0` peer gets `packetLossRatio → 1.0`
  (no `PONG`s) and should not have opted in. Called out in the `startProbe` KDoc
  and README.
- **New reserved classifier prefixes `PING ` / `PONG `.** Same caveat class as
  `Iam ` / `KA` / `REFUSED `: an application datagram whose trimmed UTF-8 view is
  `PING`/`PONG` or starts `PING `/`PONG ` is now intercepted. Documented in
  `HandshakeWireFormat`, the README binary-payload caveat, and the protocol
  table. No evidence any consumer sends such text; the mitigation (lead binary
  with `>= 0x80` / `0x00`) already covers it.
- **No breaking API change.** New `Connection` members have default bodies
  (source- and binary-compatible; the module idiom — #12 D1). New client methods
  are `@JvmOverloads`. `LinkQuality`, the constants, and the wire helpers are
  additive. `webtools-udp 1.5.0 → 1.6.0` (third-number bump — an addition, not a
  bugfix suffix, not a major).
- **`LinkQuality` field-set is a one-shot.** Adding a field later is a clean
  break (major bump, per repo policy). Mitigated by fixing the set now:
  `rttMillis` + `rttVarianceMillis` cover the client's "RTT + jitter" need,
  `packetLossRatio` covers adaptive send rate, `probesSent`/`probesDelivered`
  cover basic diagnostics. D1 records the individual-accessor alternative that
  would avoid this.
- **Echoed-token trust.** The token is the prober's own sequence number; RTT is
  computed against the prober's monotonic clock, so a peer cannot fabricate a
  low RTT — only add real delay by sitting on the `PONG` (which *is* RTT). A peer
  echoing a wrong/garbage token forfeits that sample (counted as loss) and
  degrades its **own** reported quality — not an attack on the prober.
- **Probe / keepalive interaction.** A probe datagram goes out via the same
  `send` path, so it stamps `lastOutboundAt(Nanos)` and defers a scheduled
  keepalive. An active sub-20 s probe makes a 20 s `startKeepAlive` redundant —
  documented, not enforced; the features stay independent (D-note, §2.8).
- **`packetLossRatio` is lagging and windowed.** Over 64 probes with a 3-interval
  horizon it needs ~3 s (at 1 Hz) to first resolve and reacts over tens of
  seconds. Not a fast congestion signal — documented on the `LinkQuality`
  property and in the README.
- **Probe before handshake / after the peer vanishes.** `PING`s go unanswered,
  `packetLossRatio → 1.0`, `rttMillis` goes stale — the correct signal.
  `startProbe` KDoc says "call after `handshake`".
- **`scheduleWithFixedDelay` throw-cancel hazard** — inherited from
  `PeriodicScheduler`, whose tick body is already `runCatching`-wrapped; the
  probe tick's `send` failure is an `onFailure` log. Locked by the renamed L3
  scheduler test.
- **Extra `findByOrigin` scan per inbound `PING`/`PONG`.** O(n) over the
  copy-on-write registration list, but only on control datagrams (rare relative
  to data). Same trade-off #10 / #12 accepted. Not gated by a `probeTracked`
  flag because the classifier must recognise `PING` regardless — but the scan on
  a `PING`/`PONG` is bounded by control-traffic rate, not data rate.
- **Two more possible daemon threads** (`mcupc-probe` / `mcups-probe`) — lazy,
  daemon, only when `startProbe` is called, shut in `stop()`. "No live thread
  after stop" locked at L3 and L4c. D4's shared-scheduler alternative would cap
  it at one timer thread per side.
- **`LinkQualityTracker` concurrency.** `@Synchronized` on every method;
  contention is one `beginProbe` + one `completeProbe` + the occasional reader
  per interval — negligible. `snapshot()` returns an immutable `LinkQuality`.
- **Renaming #12's just-merged `KeepAliveScheduler` files.** Mechanical,
  compiler-checked; the alternative (a mis-named second instance) is a lasting
  wart. D4.
- **Cross-repo.** None forced beyond the additive wire tokens. GameTools and its
  client adopt `linkQuality()` on their own schedule; not a gate on closing #13.

---

## 7. Version

**`webtools-udp` `1.5.0` → `1.6.0`.**

- Additive only: one new public `data class` (`LinkQuality`), four new
  default-bodied `Connection` methods, three new `MultiConnectionUDPClient`
  methods (`@JvmOverloads` where defaulted), three new public
  `HandshakeWireFormat` constants + five new helper functions, new `internal`
  helpers (`Rtt`, `LinkQualityTracker`), one new `internal` `Registration` field,
  three new `internal` `ClientChannel` methods. An internal rename
  (`KeepAliveScheduler` → `PeriodicScheduler`). Two new wire tokens, additive.
  Nothing removed; no existing public signature changed.
- The versioning convention maps an addition to a **third-number bump** — not a
  trailing-letter suffix (bugfix only), not a major (no breaking change).
  Matches the #10 / #11 / #12 shape: implementation commit(s), then a
  `build: bump webtools-udp to 1.6.0` commit.
- **Before tagging:** `javap` the built classes and confirm the overload set —
  `MultiConnectionUDPClient.startProbe()` / `startProbe(long)`;
  `Connection.startProbe()` / `startProbe(long)` / `stopProbe()` / `linkQuality()`
  present with default implementations (same pre-tag step as #8–#12).

---

## 8. Decisions (resolved)

All four were resolved by the maintainer in favour of the recommended option, on
2026-09-07, before implementation. **No decision remains open**; §3 / §5 / §10
are executable as written. Each is stated so it can be reverted with a one-line
instruction.

- **D1 — Exposed-stats shape. RESOLVED: (a) a single `LinkQuality` value
  object.** `Connection.linkQuality(): LinkQuality?` and
  `MultiConnectionUDPClient.linkQuality(): LinkQuality?` return one atomic,
  internally-consistent snapshot (RTT and loss from the same instant), `null`
  until the first probe resolves. Rejected: **(b) individual nullable accessors**
  (`rttMillis: Double?`, `packetLossRatio: Double?`, …) — freely extensible but
  lets RTT and loss be read from different instants and is more surface. The
  clean-break cost of adding a `LinkQuality` field later (major bump, per repo
  policy) is accepted and mitigated by fixing the field set now (D2).

- **D2 — RTT variance / jitter. RESOLVED: include `rttVarianceMillis` (RFC 6298
  RTTVAR) in `LinkQuality` now.** Already computed as part of the SRTT machinery;
  the client explicitly needs it to size an interpolation delay
  (`rttMillis + k * rttVarianceMillis`); deferring would force a second clean
  break on `LinkQuality`.

- **D3 — Default probe interval and its home. RESOLVED:
  `const val DEFAULT_PROBE_INTERVAL_MILLIS = 1_000L` on `HandshakeWireFormat`**
  (1 Hz) — the published constants home the client already imports;
  `HandshakeProtocol` aliases it for internal use. One ~10-byte datagram per
  second per probed connection; usable loss window in a few seconds; RTT tracking
  fast enough for interpolation and send-rate control. A consumer that wants
  lighter passes a larger interval; the probe is off by default regardless.
  Rejected: `2_000L` / `500L`.

- **D4 — Scheduler consolidation / rename. RESOLVED: (a) rename
  `KeepAliveScheduler` / `KeepAliveSchedule` → `PeriodicScheduler` /
  `PeriodicSchedule`** (internal-only, mechanical — identifiers only, no logic or
  key-type change) **and run a second instance per side for probes**
  (`mcupc-probe` / `mcups-probe`). Fully independent feature lifecycles; marginal
  cost one idle daemon thread only when both features are opted in. Rejected:
  **(b) a single shared instance per side keyed by `(kind, endpoint)`** — noted
  as a deferred consolidation (§10) if a fourth periodic task appears;
  **(c) no rename** (a second `KeepAliveScheduler("mcupc-probe")` with a
  misleading type name).

---

## 9. Sequencing & follow-ups

**Order of operations (all on `feat/issue-13-link-quality-probe`):**

1. **Stage 1 — production + docs, one commit.** §3.1–§3.16 (`Rtt`,
   `LinkQualityTracker`, `LinkQuality`, `HandshakeWireFormat`,
   `HandshakeProtocol`, the `PeriodicScheduler` rename, `ClientChannel`,
   `Connection`, `UDPConnection`, `Registrations`, `HandshakeCoordinator`,
   `MultiConnectionUDPServer`, `MultiConnectionUDPClient`, README,
   `build.gradle.kts` version-comment block), the renamed test-support fixtures,
   **plus this plan document**. The `version` line itself is Stage 3.
   `./gradlew :webtools-udp:compileKotlin` green; the full test
   suite compiles only after Stage 2's coordinator-constructor and rename updates.
2. **Stage 2 — tests, one commit.** §5 all levels, plus the
   `HandshakeCoordinatorTest` / `HandshakeCoordinatorGatingTest` constructor
   updates and the `KeepAliveScheduler*Test` renames. Every level task green.
   Run the `javap` overload check; paste it into the PR.
3. **Stage 3 — version bump, one commit.** §3.16's `version = "1.6.0"` line, a
   **separate commit** to match #10 / #11 / #12
   (`build: bump webtools-udp to 1.6.0 …`). The version-comment block rides
   Stage 1 with the code it describes.
4. **After merge:** backfill this doc's `Commit:` / `PR:` header fields with the
   real SHA(s) and PR number. Comment on #13 that the library-side change has
   landed and close it (GameTools / its client adopt `linkQuality()` on their own
   schedule, tracked separately).

**Deliberately deferred (not designed here):**

- A "probe failed / loss exceeded a threshold" **callback** to the application
  (the #12 follow-up "keepalive-failed-N-times signal"). `linkQuality()` is a
  poll-only snapshot; a push signal is a separate concern — revisit if a consumer
  asks.
- Consolidating the two per-side `PeriodicScheduler` instances into one shared
  timer keyed by `(kind, endpoint)` (D4 option b) — only if a fourth periodic
  task appears.
- Consolidating `Rtt` / `KeepAlive` / `Liveness` into one `Cadence` helper — same
  reasoning as #12 §2.3: three near-twins is still not enough coupling to justify
  it.
- Exposing `lastOutboundAt` / `idleMillis` on the public `Connection` (the #12
  follow-up) — out of scope; `LinkQuality` does not need it.
- Per-connection raw send/receive **byte/datagram counters** (the issue mentions
  "no send/receive counters exposed") — `probesSent` / `probesDelivered` cover
  the probe; full data-plane counters are a larger, separate feature.
- An adaptive probe cadence (back off when the link is quiet, speed up under
  loss) — start with a fixed interval; revisit with real usage data.

---

## 10. Version control

- **Branch:** `feat/issue-13-link-quality-probe` off `master`.
- **Working tree is clean** as of planning (verified) — no unrelated pre-existing
  changes to carve into a separate commit.
- **Commit sequence** (each a coherent unit):
  1. `feat: add an opt-in per-connection RTT / packet-loss probe to webtools-udp (Issue #13)`
     — §3.1–§3.15 (the `PeriodicScheduler` rename included) + the
     `build.gradle.kts` version-comment block **only** (not the `version` line)
     + this plan document.
  2. `test: cover the link-quality probe across all levels (Issue #13)` — §5 all
     levels, the coordinator-constructor updates, the `KeepAliveScheduler*Test`
     renames, and the `javap` overload verification.
  3. `build: bump webtools-udp to 1.6.0 for the opt-in link-quality probe (Issue #13)`
     — the `version = "1.6.0"` line (§3.16), split from commit 1 per #10–#12.
- The plan document rides in commit 1 (the first implementation stage) so
  `git log --follow docs/issue-13-link-quality-probe-plan.md` permanently binds
  plan to implementation.
- **Commit trailer:** none. Attribution is off for this repo — no
  `Co-Authored-By` / `Claude-Session` / "Generated with" trailer on commits, and
  no attribution footer in the PR description.
- Open the PR against `master`. Do not commit, push, or open the PR until the
  maintainer asks.
- After the PR merges, delete the branch (local + remote).
