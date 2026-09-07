# Issue #11 — refusable handshake + an opaque credential channel

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#11` — *"Handshake cannot be refused,
  and carries no credential; REGISTERED is always sent"* (labels: `bug`,
  `enhancement`). Two coupled gaps in the `webtools-udp` handshake:
  1. **No reject path.** `HandshakeCoordinator.handleHandshake`
     (`HandshakeCoordinator.kt:112-138`) always replies `REGISTERED` on a
     well-formed `Iam` and registers the connection; `onClientConnect` runs
     *after* that. An application that wants to refuse a client (failed auth,
     over capacity, banned) can only `Connection.terminate()` it post-hoc — the
     refused client has already been told `REGISTERED` and then nothing answers
     it again (the "REGISTERED then silently drop" wart `GameServer` documents).
  2. **No credential channel.** `HandshakeProtocol.parseHandshake`
     (`HandshakeProtocol.kt:50-54`) extracts only `tokens[1]` (the name) and
     `extraTokenCount` (`:62`) treats everything after it as ignored padding.
     There is nowhere to carry an opaque auth token.
- **Scope of this plan:** `webtools-udp` only. Add a pre-accept screening hook
  (`open fun admit(name, peer, credential): Admission`) evaluated **before** the
  `REGISTERED` reply and before any same-name supersede; a distinct
  `REFUSED <reason>` reply on refusal; an optional `Iam <name> <credential>`
  wire slot carrying an opaque, whitespace-free token passed verbatim to
  `admit`; and a typed `HandshakeRefusedException` surfaced by
  `MultiConnectionUDPClient.handshake`.
- **Explicitly out of scope:** GameTools' `AuthProvider` SPI and its
  `TrustingAuthProvider` default — that is the consumer's own concern; this
  library ships only the channel and the decision point. No downstream change
  and no downstream issue is planned here.
- **Module:** `webtools-udp` (`io.github.spartanlaboratories:webtools-udp`,
  package `com.spartanlabs.webtools.udp`). Wire format gains one **optional**
  trailing token and one new reply verb — additive, back-compatible both
  directions (§7).
- **Branch:** `feat/issue-11-handshake-refusal-and-credential` (off `master`,
  deleted after merge).
- **Commits:**
  - `f4fed5a` — `feat:` all `src/main` changes, `Admission.kt`,
    `HandshakeRefusedException.kt`, `README.md` + **this plan document**
    (committed with the first implementation stage per §9, so
    `git log --follow docs/issue-11-handshake-refusal-and-credential-plan.md`
    binds plan to implementation).
  - `8aa2c13` — `test:` the §5 suite, all seven levels.
  - `7956ce1` — `build:` bump `webtools-udp` to `1.4.0` (§9).
- **PR:** [#23](https://github.com/SpartanLaboratories/WebTools/pull/23), merged as
  `0fee3da`.
- **Status:** **done.** Merged to `master` 2026-09-07. Full suite green across all
  7 levels (gating, component, integration, deterministic, e2e, nonfunctional,
  uat). All design decisions resolved from the issue + current source — see §8;
  **Open decisions is empty**.
- **Target version:** `webtools-udp` `1.3.0` → **`1.4.0`** (purely additive; no
  signature removed or changed — see §7). Matches the #10 precedent (`1.3.0`,
  additive).
- **Related (boundaries noted, not designed here):**
  - `docs/issue-3-public-client-handshake-plan.md` — the public
    `MultiConnectionUDPClient.handshake` and the published `HandshakeWireFormat`
    this plan extends.
  - `docs/issue-10-connection-liveness-plan.md` — the `open`-hook +
    `@JvmOverloads` additive-evolution pattern reused here; §2.6 supersede
    ordering that this plan's refusal check sits in front of.
  - `docs/issue-5-prune-stale-registrations-plan.md` — the same-name supersede
    path the refusal check must guard.
  - `docs/issue-1-tier-2-plan.md` §2.1 — the canonical inbound-datagram flow the
    §2.8 diagram extends.

---

## 1. Context

### 1.1 Root cause (current source, read directly)

| Fact | Location |
|---|---|
| `handleHandshake` for a first `Iam` from a new origin: supersede any same-name stale entry, `registrations.add(...)`, then `send(REGISTERED_BYTES, origin).map { onRegistered(connection) }` — unconditional accept | `HandshakeCoordinator.kt:121-137` |
| `onRegistered` is wired to `::onClientConnect`, so the subclass hook runs strictly **after** `REGISTERED` is on the wire | `MultiConnectionUDPServer.kt:144` |
| the only refusal tool is `Connection.terminate()` **after** registration — its own KDoc calls this out: *"an application-level refusal (over capacity, banned, etc.) discovered only after the WebTools-level handshake already completed"* | `Connection.kt:75-80` |
| `parseHandshake` returns `Result<String>` (the name); `tokens[NAME_INDEX]` only | `HandshakeProtocol.kt:50-54` |
| `extraTokenCount` = `tokens.size - MIN_TOKENS` (MIN_TOKENS = 2) — every token after the name is "ignored"; `handleHandshake` logs `"Ignoring {} extra handshake token(s)"` | `HandshakeProtocol.kt:37-62`, `HandshakeCoordinator.kt:114-115` |
| `HandshakeWireFormat.handshakeMessage(name)` builds `"Iam $name"` — no credential parameter | `HandshakeWireFormat.kt:43-48` |
| the client reply check is binary: `isRegistered(reply)` → success, else `Result.failure(IllegalStateException("Expected 'REGISTERED' but got '$reply'"))` | `MultiConnectionUDPClient.kt:146-154` |
| the classifier is `text.split(' ')` → `HandshakeProtocol.isHandshake` (verb match on `tokens[0]`) | `HandshakeCoordinator.kt:96-103` |

Consequence: the server cannot say "no" during the handshake, and the client
cannot present anything for the server to judge. A refusal is always
"`REGISTERED`, then silence", indistinguishable at the client from packet loss.

### 1.2 Requirements / acceptance criteria

1. A subclass-supplied **pre-accept decision point** is evaluated on the
   new-registration path **before** the `REGISTERED` reply and **before** any
   same-name supersede.
2. On refusal the server replies with a distinct `REFUSED <reason>` datagram to
   the `Iam` origin, mints **no** `Connection`, adds **no** `Registration`, and
   fires **no** `onClientConnect`.
3. `MultiConnectionUDPClient.handshake` surfaces a refusal as
   `Result.failure(HandshakeRefusedException(reason))` — a typed, inspectable
   failure distinct from a timeout.
4. The handshake line carries an **optional** opaque credential:
   `Iam <name> <credential>`. It is passed to the decision point **verbatim**,
   never interpreted by the library. Absent credential → empty string.
5. **Back-compatible both directions** (§7): an old client (`Iam <name>`) against
   a new server behaves exactly as today (empty credential, default `admit`
   accepts); a new client (`Iam <name> <cred>`) against an old server still
   registers (old server ignores the trailing token).
6. **Zero behaviour change by default.** A `MultiConnectionUDPServer` subclass
   that does not override the new hook accepts every well-formed `Iam` exactly as
   it does today.
7. Additive: `webtools-udp` `1.4.0`. No public signature removed or changed;
   new optional parameters are trailing and `@JvmOverloads`-covered.
8. A refused same-name reconnect from a **new** origin must **not** evict the
   legitimate incumbent (§2.4 — a name-spoofing eviction DoS otherwise).

---

## 2. Design

### 2.1 Shape of the change

| Unit | Kind | Change |
|---|---|---|
| `Admission` | **new** public `sealed interface` | `Admitted` / `Refused(reason)` — the decision-point return type |
| `HandshakeRefusedException` | **new** public `Exception` | carries `reason`; the `Result.failure` cause at the client |
| `HandshakeWireFormat` | public | `credential` param on `handshakeMessage` (`@JvmOverloads`); new `REFUSED_REPLY` token + `refusedMessage` / `isRefused` / `refusalReason` |
| `HandshakeProtocol` | internal | `parseHandshake` returns `Result<Handshake>` (name + credential); `extraTokenCount` counts from after the credential slot |
| `Handshake` | **new** internal `data class` | `(name: String, credential: String)` |
| `HandshakeCoordinator` | internal | new `admit` constructor collaborator; refusal branch in `handleHandshake` ahead of supersede/registration |
| `MultiConnectionUDPServer` | public | new `open fun admit(name, peer, credential): Admission = Admission.Admitted`; wires it into the coordinator |
| `MultiConnectionUDPClient` | public | `credential` param on `handshake` (`@JvmOverloads`); `REFUSED` reply → `HandshakeRefusedException` |

Nothing is removed. `HandshakeCoordinator` / `HandshakeProtocol` / `Handshake`
are `internal`, so their signature changes are not public surface.

### 2.2 Wire format — one optional token, one new reply verb

```
client → server   Iam <name>                     (unchanged — credential absent)
client → server   Iam <name> <credential>        (new — credential present)
server → client   REGISTERED                     (unchanged — accepted)
server → client   REFUSED                        (new — refused, no reason)
server → client   REFUSED <reason text...>       (new — refused, reason is everything after "REFUSED ")
```

- **`<credential>` is one whitespace-free token.** The entire wire format is
  space-split (`text.split(' ')`, and `handshakeMessage`'s KDoc already requires
  a whitespace-free `<name>`). A caller with a structured or binary credential
  encodes it (base64url is the documented recommendation); the library treats it
  as an opaque string and never parses it. This is §8 D1.
- **`<reason>` may contain spaces.** `REFUSED ` is a fixed 8-char prefix;
  everything after it is the reason, verbatim. `refusalReason` is
  `reply.removePrefix("REFUSED").trim()`. The server trims the reason and
  collapses any embedded newline/control run to a single space before it goes on
  the wire (cosmetic — it is a single datagram, so there is no framing-injection
  risk; §6).
- **Classifier interaction.** `REFUSED …` is only ever *interpreted* by
  `MultiConnectionUDPClient.handshake`, which reads exactly one reply datagram.
  After `start()`, the client classifier checks only `isKeepAlive` — a
  server→client application datagram that happens to read `REFUSED x` is
  delivered normally. Same class of caveat as the existing `REGISTERED` / `KA`
  tokens; documented in `HandshakeWireFormat` KDoc.

### 2.3 The decision point — `admit`, on the new-registration path only

`HandshakeCoordinator.handleHandshake` today (`HandshakeCoordinator.kt:112-138`):

```
parseHandshake(tokens).flatMap { name ->
    registrations.findByOrigin(origin)?.let { /* retransmit → repeat REGISTERED */ }
        ?: run {
            registrations.findByName(name)?.let { stale -> /* supersede */ }
            val connection = newConnection(name, origin, this)
            registrations.add(Registration(connection))
            send(REGISTERED_BYTES, origin).map { onRegistered(connection) }
        }
}
```

After the change:

```
parseHandshake(tokens).flatMap { (name, credential) ->
    val extra = HandshakeProtocol.extraTokenCount(tokens)
    if (extra > 0) log.debug("Ignoring {} extra handshake token(s)", extra)

    registrations.findByOrigin(origin)?.let {
        // retransmit from an already-admitted origin — no re-screening, repeat the token it earned
        log.info("Repeating handshake reply for already-registered origin {}", origin)
        send(REGISTERED_BYTES, origin)
    } ?: run {
        // NEW — screen the newcomer BEFORE supersede / registration / reply
        val admission = runCatching { admit(name, origin, credential) }.getOrElse { cause ->
            log.warn("admit(...) threw for '{}' from {} - dropping the handshake", name, origin, cause)
            return@flatMap Result.failure(cause)
        }
        if (admission is Admission.Refused) {
            log.info("Refused handshake for '{}' from {}: {}", name, origin, admission.reason)
            return@flatMap send(HandshakeWireFormat.refusedMessage(admission.reason).toByteArray(Charsets.UTF_8), origin)
        }

        registrations.findByName(name)?.let { stale -> /* supersede — unchanged */ }
        val connection = newConnection(name, origin, this)
        registrations.add(Registration(connection))
        log.info("Registered connection '{}' for {}", name, origin)
        send(REGISTERED_BYTES, origin).map { onRegistered(connection) }
    }
}
```

Key properties:

- **`admit` runs only for a first `Iam` from an unknown origin.** A retransmit
  from an already-registered origin (`findByOrigin(origin) != null`) just repeats
  `REGISTERED` — it was screened when it first registered; re-screening risks a
  flip-flop if `admit` is non-deterministic (capacity).
- **`admit` runs before `registrations.findByName(name)`.** A refused newcomer
  `return@flatMap`s before the supersede code — the incumbent is never touched
  (§2.4).
- **Refusal return value.** `accept` returns the `Result` of *sending* the
  `REFUSED` datagram — `Result.success` if it went out. Refusing a client is a
  successful server action, not a failure.
- **A throwing `admit`** is caught, logged WARN, the handshake datagram is
  dropped (no reply, no registration), and `accept` returns `Result.failure`.
  The client sees a timeout and may retry — the existing failure mode for a
  server-side handshake fault. We do **not** synthesise a `REFUSED` from an
  exception (it would leak an internal fault as an auth decision).
- **`admit` is called inline on the listener thread**, exactly like the existing
  `onClientConnect`. Its KDoc carries the same "return promptly" contract, made
  sharper: a blocking auth lookup (DB, network) stalls *all* inbound
  demultiplexing for *every* client. A truly asynchronous accept path is a
  larger design, deferred (§9). The issue asks for a synchronous predicate.

### 2.4 Refused newcomer must not evict the incumbent

Without care, this is a trivial denial of service: an attacker sends
`Iam <victim-name> <garbage-credential>` from a fresh source port. Today that
would supersede the victim (`HandshakeCoordinator.kt:126-132`) — the victim is
deregistered and stops receiving broadcasts — *before* anyone checks the
credential.

Because `admit` is evaluated inside the `run { }` block **before**
`registrations.findByName(name)`, a `Refused` admission `return@flatMap`s and the
supersede code is never reached. The incumbent keeps its registration, its
handler binding, and its place in `pushToAll`. Locked by a Level-2 test
("a refused same-name newcomer from a new origin leaves the incumbent
registered and addressable") and a Level-4b test.

An **admitted** same-name newcomer from a new origin still supersedes, exactly as
today — the NAT-rebind reconnect path is unchanged.

### 2.5 `Admission` and `HandshakeRefusedException` (new public types)

```kotlin
package com.spartanlabs.webtools.udp

/**
 * The outcome of [MultiConnectionUDPServer.admit] - the pre-accept screening
 * decision for an incoming `Iam` handshake, evaluated on the listener thread
 * before the `REGISTERED` reply and before any same-name supersede.
 */
sealed interface Admission {
    /** Accept the handshake: register the connection and reply `REGISTERED`. */
    data object Admitted : Admission

    /**
     * Refuse the handshake: reply `REFUSED <reason>` to the client, register
     * nothing, and do not fire [MultiConnectionUDPServer.onClientConnect].
     * @property reason a short, human-readable cause placed on the wire after
     * `REFUSED ` verbatim (trimmed; embedded control characters collapsed to a
     * space). Keep it terse - it is echoed to an unauthenticated peer.
     */
    data class Refused(val reason: String) : Admission
}
```

```kotlin
package com.spartanlabs.webtools.udp

/**
 * The server refused the handshake during [MultiConnectionUDPClient.handshake]:
 * it replied `REFUSED <reason>` rather than `REGISTERED`. Distinct from a
 * timeout (the server never answered) and from a malformed reply.
 * @property reason the server-supplied cause (everything after `REFUSED `), or
 * the empty string if the server sent a bare `REFUSED`.
 */
class HandshakeRefusedException(val reason: String) :
    Exception("Server refused the handshake" + if (reason.isBlank()) "" else ": $reason")
```

`Kotlin 2.2` (repo's version) supports `data object`. Java consumers: reference
`Admission.Admitted.INSTANCE`, construct `new Admission.Refused("full")`, read
`ex.getReason()`.

### 2.6 `MultiConnectionUDPServer.admit` (new open hook)

```kotlin
/**
 * Screens an incoming `Iam` handshake before it is accepted. The default
 * admits every well-formed handshake - today's behaviour. Override to validate
 * [credential] (an opaque token the client supplied, empty if it sent none) or
 * to refuse for application reasons (over capacity, banned): return
 * [Admission.Refused] and the client receives `REFUSED <reason>` as a typed
 * [HandshakeRefusedException], with nothing registered and no
 * [onClientConnect] call.
 *
 * Runs **inline on the common listener thread** (like [onClientConnect]), so it
 * must return promptly - a blocking credential lookup stalls inbound handling
 * for every client. Do fast, local checks here; hand a slow verification to
 * another thread and gate on its cached result.
 *
 * Called only for a first `Iam` from an unknown origin - not for a retransmit
 * from an already-registered origin. Called for a same-name reconnect from a
 * **new** origin *before* the stale registration is superseded, so refusing it
 * leaves the existing connection intact.
 *
 * @param name the client's chosen name
 * @param peer the client's observed post-NAT origin
 * @param credential the opaque token from `Iam <name> <credential>`, verbatim;
 * the empty string if the client sent `Iam <name>`
 * @return [Admission.Admitted] to accept, [Admission.Refused] to reject
 */
open fun admit(name: String, peer: java.net.InetSocketAddress, credential: String): Admission =
    Admission.Admitted
```

Wired into the coordinator (`MultiConnectionUDPServer.kt:141-153`):

```kotlin
private val coordinator = HandshakeCoordinator(
    newConnection = { name, peer, channel -> UDPConnection(name, peer, channel) },
    sender = commonChannel::send,
    onRegistered = ::onClientConnect,
    admit = ::admit,                       // NEW
    dispatch = { block -> dispatchExecutor.execute(block) },
    onDisconnect = { connection, reason -> /* unchanged */ },
    idleTimeoutMillis = idleTimeoutMillis,
)
```

`open` + behaviour-preserving default → binary- and source-compatible for every
existing subclass (same pattern as `onClientDisconnect` in the #10 plan §2.5).

### 2.7 `MultiConnectionUDPClient.handshake` (credential param + typed refusal)

```kotlin
@JvmOverloads
fun handshake(
    name: String,
    timeoutMillis: Int = HANDSHAKE_TIMEOUT_MILLIS,
    credential: String = "",                 // NEW — trailing, defaulted
): Result<Unit> = runCatching {
    val payload = HandshakeWireFormat.handshakeMessage(name, credential).toByteArray(Charsets.UTF_8)
    socket.send(DatagramPacket(payload, payload.size, serverAddress, serverPort))
    socket.soTimeout = timeoutMillis
    val buffer = ByteArray(receiveBufferBytes)
    val reply = DatagramPacket(buffer, buffer.size)
    socket.receive(reply)
    String(reply.data, 0, reply.length, Charsets.UTF_8).trim()
}.flatMap { reply ->
    when {
        HandshakeWireFormat.isRegistered(reply) -> Result.success(Unit)
        HandshakeWireFormat.isRefused(reply) ->
            Result.failure(HandshakeRefusedException(HandshakeWireFormat.refusalReason(reply)))
        else -> Result.failure(
            IllegalStateException("Expected '${HandshakeWireFormat.REGISTERED_REPLY}' but got '$reply'"),
        )
    }
}.onFailure { log.error("Handshake with {}:{} failed", serverAddress, serverPort, it) }
```

`credential` is **last**, so `handshake(name)` and `handshake(name, timeout)` —
every existing call, Kotlin positional or Java — bind unchanged. `@JvmOverloads`
generates `handshake(String)`, `handshake(String, int)`,
`handshake(String, int, String)`. Kotlin callers wanting a credential without a
custom timeout use the named argument: `handshake("alice", credential = tok)`.

### 2.8 Flow — accepted, refused, and refused-newcomer

```mermaid
sequenceDiagram
    participant C as client
    participant L as listener thread
    participant HC as HandshakeCoordinator
    participant App as admit() (subclass)
    participant R as Registrations

    Note over C,R: accepted handshake with a credential
    C->>L: Iam alice <cred>
    L->>HC: accept(origin, bytes, "Iam alice <cred>")
    HC->>HC: parseHandshake -> Handshake("alice","<cred>")
    HC->>App: admit("alice", origin, "<cred>")
    App-->>HC: Admitted
    HC->>R: (supersede same-name stale, if any) ; add(Registration)
    HC->>C: REGISTERED
    HC->>App: onClientConnect(connection)

    Note over C,R: refused handshake
    C->>L: Iam mallory bad-token
    L->>HC: accept(...)
    HC->>App: admit("mallory", origin, "bad-token")
    App-->>HC: Refused("invalid credential")
    HC->>C: REFUSED invalid credential
    Note over HC,R: no Registration added, no onClientConnect
    Note over C: handshake() -> Result.failure(HandshakeRefusedException("invalid credential"))

    Note over C,R: refused newcomer under an existing name (spoof / DoS attempt)
    C->>L: Iam alice garbage   (from a NEW origin)
    L->>HC: accept(...)
    HC->>App: admit("alice", newOrigin, "garbage")
    App-->>HC: Refused("invalid credential")
    HC->>C: REFUSED invalid credential
    Note over R: incumbent 'alice' registration untouched - supersede never reached
```

### 2.9 Alternatives considered

| Option | Rejected because |
|---|---|
| **Make `onClientConnect` return a rejection** (`onClientConnect(conn): Admission`) | A signature break on the one `abstract` method every subclass implements → forces every consumer to change → major bump. And the `Connection` is already minted/registered by the time it runs, so "reject" would mean "tear down what we just built". A pre-accept hook with a default is additive and does no wasted work. |
| **A `Predicate`/`(…) -> Boolean` instead of a sealed `Admission`** | A bare `false` gives the client no reason — back to "can't tell refused from loss". `Admission.Refused(reason)` carries the cause to the wire. |
| **Length-delimited trailing credential blob** (`Iam <name> <len> <bytes…>`) | Needs a bespoke non-split parse path just for the handshake, breaking the "everything is space-split" invariant the whole format relies on. A whitespace-free token (base64url for binary) costs the caller one `encode` call and keeps the format uniform. (§8 D1) |
| **Re-run `admit` on every retransmit** | A non-deterministic `admit` (capacity) could then refuse an already-registered client on a stray retransmit and desync the two sides. Screen once, at first registration. |
| **Skip `admit` on the same-name supersede path** (only screen brand-new names) | Leaves the name-spoof eviction DoS open (§2.4). The newcomer must clear `admit` before the incumbent is disturbed. (§8 D2) |
| **Treat a thrown `admit` as `Refused("internal error")`** | Leaks an internal fault to an unauthenticated peer as an auth verdict and could mask a bug as a routine refusal. Drop + `Result.failure` (→ client timeout → retry) is the existing server-fault mode. |
| **New reply verb `REJECTED` / `DENIED`** | The issue names `REFUSED`; no reason to diverge. |

---

## 3. File-by-file changes

All paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/`
unless noted.

### 3.1 `Admission.kt` (new, public)

The `sealed interface Admission` from §2.5 — `data object Admitted`,
`data class Refused(val reason: String)`. Level-2 KDoc on the interface and both
members (`@property reason`).

### 3.2 `HandshakeRefusedException.kt` (new, public)

The `class HandshakeRefusedException(val reason: String) : Exception(...)` from
§2.5. Level-2 KDoc, `@property reason`.

### 3.3 `HandshakeWireFormat.kt` (public)

- New `const val REFUSED_REPLY = "REFUSED"`.
- `handshakeMessage`:
  ```kotlin
  @JvmOverloads
  fun handshakeMessage(name: String, credential: String = ""): String =
      if (credential.isEmpty()) "$HANDSHAKE_VERB $name" else "$HANDSHAKE_VERB $name $credential"
  ```
- New:
  ```kotlin
  /** Builds the server's `REFUSED <reason>` reply (bare `REFUSED` if [reason] is
   *  blank). [reason] is trimmed and any control/newline run collapsed to a
   *  single space so the reply stays one clean line. */
  fun refusedMessage(reason: String): String {
      val clean = reason.trim().replace(Regex("[\\p{Cntrl}\\s]+"), " ")
      return if (clean.isEmpty()) REFUSED_REPLY else "$REFUSED_REPLY $clean"
  }

  /** True if [reply] is the server's handshake-refused token (bare or with a reason). */
  fun isRefused(reply: String): Boolean =
      reply == REFUSED_REPLY || reply.startsWith("$REFUSED_REPLY ")

  /** The reason carried by a `REFUSED <reason>` reply - everything after
   *  `REFUSED `, trimmed; the empty string for a bare `REFUSED`. Only meaningful
   *  when [isRefused] is true. */
  fun refusalReason(reply: String): String = reply.removePrefix(REFUSED_REPLY).trim()
  ```
- KDoc (class + `handshakeMessage`): the optional `<credential>` slot —
  `Iam <name> <credential>`, one whitespace-free token, opaque (library never
  parses it), base64url for binary, absent → empty string; the `REFUSED <reason>`
  reply and that it is only interpreted during the client handshake, not
  mid-session (Boundary-Ring caveat, alongside the existing `REGISTERED`/`KA`
  one). Note the credential rides **in cleartext** — the library provides a
  channel, not confidentiality (§6).

### 3.4 `HandshakeProtocol.kt` (internal)

- New `internal data class Handshake(val name: String, val credential: String)`
  (top-level in this file).
- New `const val REFUSED_REPLY = HandshakeWireFormat.REFUSED_REPLY`.
- New `private const val CREDENTIAL_INDEX = 2`.
- `parseHandshake`:
  ```kotlin
  fun parseHandshake(tokens: List<String>): Result<Handshake> = runCatching {
      require(tokens.firstOrNull() == VERB) { "Not an $VERB message: $tokens" }
      require(tokens.size >= MIN_TOKENS) { "Expected '$VERB <name>' but got ${tokens.size} token(s)" }
      Handshake(tokens[NAME_INDEX], tokens.getOrElse(CREDENTIAL_INDEX) { "" })
  }
  ```
- `extraTokenCount`: count from after the credential slot —
  `(tokens.size - (CREDENTIAL_INDEX + 1)).coerceAtLeast(0)`. Update KDoc: "how
  many tokens past the optional credential…".
- KDoc: `parseHandshake` now yields name **and** the opaque credential
  (`tokens[2]`, or `""`); the object owns the credential-slot rule.

### 3.5 `HandshakeCoordinator.kt` (internal)

- **Constructor** (`HandshakeCoordinator.kt:41-48`): new collaborator after
  `onRegistered`:
  ```kotlin
  private val admit: (name: String, peer: InetSocketAddress, credential: String) -> Admission,
  ```
  KDoc `@param admit` — the pre-accept screen; invoked inline; expected to return
  promptly.
- **`handleHandshake`** (`:112-138`): destructure `parseHandshake` to
  `(name, credential)`; insert the §2.3 refusal branch inside the `run { }`
  block, before `registrations.findByName(name)`. `extraTokenCount` /
  "ignoring extra tokens" log unchanged in meaning (now counts post-credential
  tokens).
- Class KDoc (`:6-27`): the "handshake state machine" bullet — a first `Iam`
  from a new origin is **screened by `admit`** before registration; a refusal
  replies `REFUSED <reason>`, registers nothing, and does not fire
  `onRegistered`.
- No change to `accept`, `classify`, `deliverData`, the liveness paths, or the
  `ClientChannel` methods.

### 3.6 `MultiConnectionUDPServer.kt` (public)

- New `import java.net.InetSocketAddress`.
- New `open fun admit(name, peer, credential): Admission = Admission.Admitted`
  (§2.6), placed directly after `abstract fun onClientConnect`
  (`MultiConnectionUDPServer.kt:227`).
- `coordinator` property (`:141-153`): add `admit = ::admit,`.
- Class KDoc:
  - The paragraph on the handshake rules (`:16-22`) — a client may append an
    opaque `<credential>` token to `Iam`; the server screens every new
    handshake through `admit` and may reply `REFUSED <reason>` instead of
    `REGISTERED`.
  - New `### Handshake screening & credentials` section: the opt-in `admit`
    override, `Admission.Admitted` / `Refused`, the listener-thread execution
    contract, that a refusal mints nothing and fires no `onClientConnect`, and
    that it removes the old "REGISTERED then silently drop" pattern for capacity
    refusals.
  - `### Concurrency` (`:87-105`) — `admit` runs on the listener thread inline
    (like `onClientConnect`); a slow `admit` stalls demultiplexing for all
    clients.

### 3.7 `MultiConnectionUDPClient.kt` (public)

- `handshake` (`MultiConnectionUDPClient.kt:137-154`): `@JvmOverloads`; new
  trailing `credential: String = ""`; use `handshakeMessage(name, credential)`;
  the §2.7 three-way `when` on the reply.
- KDoc: `@param credential` (opaque, forwarded verbatim in the `Iam` line,
  default empty); the return contract gains "…or `Result.failure` holding a
  `HandshakeRefusedException` if the server replied `REFUSED`". Note `handshake`
  is one-shot: a `HandshakeRefusedException` for a bad credential will recur on
  retry with the same credential; a `Refused` for capacity may not.
- Class KDoc "Ordering contract" unchanged; add one line that a refused
  handshake leaves the client safe to `stop()` and discard.

### 3.8 `README.md` (repo root)

- Install snippet (`README.md:19`): `webtools-udp:1.3.0` → `1.4.0`.
- Components table (`:52`): `HandshakeWireFormat` row — add `REFUSED`; server row
  (`:47`) — mention `admit` / handshake screening.
- "UDP handshake protocol" table (`:72-77`): add the optional
  `Iam <name> <credential>` row and the `server → client  REFUSED <reason>` row.
- The paragraph after the table (`:82-90`): a server may refuse a handshake via
  `admit`; a refused client gets `REFUSED <reason>` (a typed
  `HandshakeRefusedException` on the client), not `REGISTERED` — this replaces
  the "call `terminate()` after the fact" workaround for the refuse-at-connect
  case (that sentence at `:88-90` is reworded to point at `admit`).
- New `### Handshake screening & credentials` subsection (after "Connection
  liveness"): override `admit(name, peer, credential)` → `Admission`; the opaque
  whitespace-free credential (base64url for binary); `handshake(name, credential = …)`
  on the client; the back-compat matrix in one sentence (absent credential →
  empty; old/new client vs old/new server); the cleartext-channel caveat; a
  short code sketch mirroring "Client-side usage".

### 3.9 `webtools-udp/build.gradle.kts`

- `version` (`:11`): `"1.3.0"` → `"1.4.0"`.
- Version comment block (`:4-10`): add
  `// 1.4.0: refusable handshake + opaque credential channel - Iam <name> <credential>,`
  `// the open admit(name, peer, credential): Admission hook, and the REFUSED <reason>`
  `// reply surfaced as HandshakeRefusedException (Issue #11).`

### 3.10 `docs/issue-11-handshake-refusal-and-credential-plan.md`

This document. Committed in the same commit as §3.1–§3.8 (stage 1). Backfill the
`Commit:` / `PR:` header fields once they exist (as done for #8/#9/#10).

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves with this change |
|---|---|---|
| **Inner core** (in-editor) | yes | Comments: why `admit` sits before `findByName` (spoof-eviction guard); why a thrown `admit` drops rather than synthesises `REFUSED`; why retransmits skip re-screening; the `refusedMessage` control-char collapse. |
| **Component ring** (KDoc) | yes | New KDoc: `Admission` (+ members), `HandshakeRefusedException`, `MultiConnectionUDPServer.admit`, `HandshakeWireFormat.{REFUSED_REPLY, refusedMessage, isRefused, refusalReason}` + `credential` on `handshakeMessage`, `MultiConnectionUDPClient.handshake` `@param credential` + refusal return, `HandshakeProtocol.parseHandshake` / `Handshake` / `extraTokenCount`, `HandshakeCoordinator` `@param admit`. |
| **Boundary ring** (protocol) | yes | Wire format gains an optional `Iam` token and a `REFUSED <reason>` reply verb — additive, back-compatible. The credential is opaque and **cleartext**. Stated in `HandshakeWireFormat` KDoc and the README handshake-protocol section. |
| **Architectural outer layer** | yes | The server gains a pre-accept authorization decision point — a new place application/security policy plugs into the connection lifecycle. The §2.8 sequence diagram is the canonical accepted/refused flow. No new thread. |
| **README** | yes | Per the README-currency rule: new (optional) wire token + reply verb, new overridable hook, new public types, dependency-coordinate bump. |

---

## 5. Test plan (5-Level hierarchy)

Package root mirrors production `com.spartanlabs.webtools.udp` →
`com.spartanlabs.testing.<level>.webtools.udp`. One class per file; each class
carries its level `@Tag`. Socket-binding classes (Levels 3, 4b, 4c) run under the
module's `commonUdpPortLock`.

### Level 1 — `testing.gating`

- **Extend `HandshakeWireFormatGatingTest.kt`** (socket-free, sub-ms smoke):
  - `handshakeMessage("alice", "tok")` == `"Iam alice tok"`; `handshakeMessage("alice")` == `"Iam alice"` (unchanged); `handshakeMessage("alice", "")` == `"Iam alice"`.
  - `REFUSED_REPLY` == `"REFUSED"`.
  - `isRefused("REFUSED")` / `isRefused("REFUSED over capacity")` true; `isRefused("REGISTERED")` / `isRefused("REFUSEDX")` false.
  - `refusalReason("REFUSED over capacity")` == `"over capacity"`; `refusalReason("REFUSED")` == `""`.
- **Extend `HandshakeCoordinatorGatingTest.kt`** (socket-free): with a
  `admit` lambda that refuses name `"banned"`:
  - `accept(origin, "Iam banned")` → one `REFUSED …` reply, `coordinator.size == 0`, no `newConnection`.
  - `accept(origin, "Iam alice tok")` → registers, one `REGISTERED`, and the recorded `admit` call carries credential `"tok"`.
  - default `admit` lambda (`{ _,_,_ -> Admission.Admitted }`) → unchanged accept.

### Level 2 — `testing.component`

- **Extend `HandshakeCoordinatorTest.kt`** (synchronous `dispatch = { it() }`,
  recording collaborators):
  - update `newCoordinator()` and every inline `HandshakeCoordinator(...)`
    (≈5 sites) for the new `admit` param — default a recording lambda
    `admitCalls += Triple(name, peer, credential); admissionToReturn` with
    `admissionToReturn` a per-test `var` defaulting to `Admission.Admitted`.
  - `admit` receives the parsed credential: `"Iam alice tok123"` → `admitCalls` last == `("alice", originA, "tok123")`.
  - `admit` receives `""` for `"Iam alice"`.
  - `Admission.Refused("nope")`: `created` empty, `coordinator.size == 0`, `sent` == `[("REFUSED nope", originA)]`, `registeredNames` empty, `accept` returns success.
  - a refusal reason with spaces round-trips: `Refused("over capacity now")` → sent datagram `"REFUSED over capacity now"`.
  - **refused newcomer does not evict the incumbent**: register `alice` at `originA` (admitted); set `admissionToReturn = Refused("x")`; `accept(originB, "Iam alice")` → `coordinator.size == 1`, surviving origin `originA`, `disconnects` empty (no `SUPERSEDED`), `created` still 1; `broadcast("ping")` hits `originA`.
  - **admitted newcomer still supersedes**: incumbent at `originA`; `admissionToReturn = Admitted`; `accept(originB, "Iam alice")` → supersede as today, `disconnects` == `[("alice", SUPERSEDED)]`, and `admit` was consulted for the newcomer.
  - **retransmit skips `admit`**: `accept(originA, "Iam alice")` twice → `admitCalls.size == 1`.
  - **throwing `admit`**: lambda throws → `accept` returns failure, `coordinator.size == 0`, `sent` empty (no reply).
  - rename/repurpose the existing `` `tokens after the name are ignored` `` test:
    `"Iam carol 10.0.0.9 junk"` → `admit` credential == `"10.0.0.9"`, `"junk"`
    is the one ignored extra token (`extraTokenCount` == 1), registration name
    `"carol"`.
- **`RegistrationsTest.kt`**: no change (no new `Registration` field).

### Level 3 — `testing.integration` (real sockets)

- **New `MultiConnectionUDPServerAdmissionTest.kt`** — its own server instance
  (not the shared one in `MultiConnectionUDPServerTest`), `@AfterTest { server?.stop() }`:
  ```kotlin
  object : MultiConnectionUDPServer() {
      override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission =
          when {
              name == "banned"                 -> Admission.Refused("banned")
              credential != "s3cret" && name == "guarded" -> Admission.Refused("invalid credential")
              else -> Admission.Admitted
          }
      // record onClientConnect names
  }
  ```
  - `Iam banned` from a real `DatagramSocket` → reply text `"REFUSED banned"`; name never appears in the connected set; a follow-up datagram from that socket is dropped (unregistered).
  - `Iam guarded s3cret` → `"REGISTERED"`, registered.
  - `Iam guarded wrong` → `"REFUSED invalid credential"`, not registered.
  - `Iam alice` (default branch, no credential) → `"REGISTERED"`; server's `admit` saw `""`.
  - a socket refused once can retry successfully with a good name/credential on the same socket.
  - **default server** (a second `object : MultiConnectionUDPServer() {}` with no `admit` override, run in its own test with the port lock) — `Iam whoever any-credential-here` still registers → back-compat.

### Level 4a — `testing.deterministic`

- **Extend `HandshakeWireFormatTest.kt`**:
  - `handshakeMessage` truth table: no credential, empty credential, ordinary token, a 200-char base64url token — all `Iam name[ credential]`.
  - `isRefused` truth table: `"REFUSED"`, `"REFUSED x"`, `"REFUSED a b c"` → true; `"REFUSEDX"`, `"refused"`, `""`, `"REGISTERED"`, `" REFUSED"` → false.
  - `refusalReason`: bare → `""`; `"REFUSED full"` → `"full"`; `"REFUSED  over   capacity "` → `"over   capacity"` (internal spacing preserved, ends trimmed).
  - `refusedMessage`: blank / whitespace-only reason → `"REFUSED"`; `"over capacity"` → `"REFUSED over capacity"`; a reason with `\n` / `\t` → collapsed to single spaces, single line.
  - round-trip property: for a reason with no leading/trailing/control whitespace, `refusalReason(refusedMessage(r)) == r`.
- **Extend `HandshakeProtocolTest.kt`**:
  - `parseHandshake(listOf("Iam","bob"))` → `Handshake("bob", "")`.
  - `parseHandshake(listOf("Iam","bob","tok"))` → `Handshake("bob", "tok")`.
  - `parseHandshake(listOf("Iam","bob","tok","x","y"))` → `Handshake("bob","tok")`, `extraTokenCount` == 2.
  - verb-only / empty / non-`Iam` verb still `isFailure` with `IllegalArgumentException`.
  - `extraTokenCount` truth table shifted by the credential slot: `["Iam","bob"]` → 0; `["Iam","bob","tok"]` → 0; `["Iam","bob","tok","z"]` → 1.

### Level 4b — `testing.e2e`

- **New `MultiConnectionUDPHandshakeRefusalE2ETest.kt`** — real
  `MultiConnectionUDPClient` + real `MultiConnectionUDPServer` subclass over
  loopback:
  - `admit` validates `credential == "good"`: `client.handshake("alice", credential = "good")` → success, `onClientConnect` fires, session works (`send` / `push` round-trip).
  - `client.handshake("mallory", credential = "bad")` → `Result.failure`; `exceptionOrNull()` is `HandshakeRefusedException` with `reason == "invalid credential"`; server never registered `mallory`; `client.stop()` is clean.
  - **capacity refusal**: `admit` returns `Refused("server full")` once `connections.size >= 1`; first client connects, second client's `handshake` fails with `HandshakeRefusedException("server full")`; the first client's session is unaffected.
  - **back-compat**: against a *default* (no-override) server, `client.handshake("bob")` (no credential) succeeds exactly as today.
  - **refused newcomer does not evict incumbent**: `alice` connects with a good credential and `start`s; a second `MultiConnectionUDPClient` sends `handshake("alice", credential = "bad")` → refused; the first client keeps receiving `pushToAll` / `push` afterward.

### Level 4c — `testing.nonfunctional`

- **New `MultiConnectionUDPServerAdmissionNonFunctionalTest.kt`**:
  - **verbatim credential**: a 1 KiB base64url credential arrives byte-identical at `admit` (assert equality on the captured string).
  - **throwing `admit` is survivable**: 100 handshakes whose `admit` throws never kill the listener thread — a 101st handshake with a well-behaved `admit` still registers.
  - **refusal does not leak**: a burst of 500 refused handshakes leaves `coordinator`/roster size at 0 and the server still accepts a good client afterward.
  - **slow `admit` documents the cost**: an `admit` that sleeps 200 ms serialises handshakes (measure: N sequential handshakes take ≈ N×200 ms) — asserted as a documented property, and that state stays correct (final roster == the admitted subset) under interleaved admit/refuse.
  - **reason sanitisation**: `Refused("line1\nline2  end")` produces a single-line `REFUSED line1 line2 end` on the wire.
- Alternatively fold the first three into `HandshakeNonFunctionalTest.kt` if that
  file already owns the handshake robustness surface; keep the slow-`admit`
  timing case in its own class.

### Level 5 — `testing.uat`

- **Extend `MultiConnectionUDPServerUatTest.kt`** with one `@Disabled` scenario:
  a real NAT'd client against a public server wired to a GameTools-style
  `admit` (token check + capacity gate). PASS =
  (a) a bad token → the client sees `HandshakeRefusedException("<reason>")`
  within the handshake timeout (not a hang);
  (b) a good token → connects and plays;
  (c) an over-capacity client → `HandshakeRefusedException("server full")`,
  clear and immediate;
  (d) a third party sending `Iam <existing-name> <junk>` from another host does
  **not** knock the legitimate player offline.

### Cannot be automated

- A real network-backed `admit` (DB / auth service) and its latency impact on the
  single listener thread under production connection rates (Level 5 / load test
  in the consumer).
- The client-perceived distinction between "refused" and "packet loss" on a
  genuinely lossy path — loopback never drops (Level 5).
- Credential confidentiality on a real path — it is cleartext by design; only a
  deployment review can judge whether the credential scheme tolerates that.

---

## 6. Risks & edge cases

- **Wire back-compat — the core analysis.**

  | Client \ Server | old server (`≤ 1.3.0`) | new server (`1.4.0`) |
  |---|---|---|
  | **old client** `Iam name` | unchanged | `credential = ""`, default `admit` → `Admitted` → byte-identical to today |
  | **new client** `Iam name cred` | old server ignores tokens after the name (`extraTokenCount`) → replies `REGISTERED`; credential silently dropped, **auth not enforced** | `credential` forwarded to `admit` verbatim |

  - **old client vs new server, refused:** the old client's `handshake` sees a
    non-`REGISTERED` reply and already returns
    `Result.failure(IllegalStateException("Expected 'REGISTERED' but got 'REFUSED …'"))`
    — strictly better than today's silent hang, just not the typed exception.
  - **new client vs old server:** never receives `REFUSED` (old server has no
    reject path) — identical to today. A consumer that needs auth *enforced*
    must run a `1.4.0`+ server; documented.
  - No datagram layout change for the existing tokens; `REGISTERED` / `KA` /
    `Iam <name>` are byte-for-byte unchanged.

- **`extraTokenCount` semantics shift.** `tokens[2]` moves from "ignored
  padding" to "credential". A legacy client that appends a claimed address
  (`Iam name 10.0.0.9`) now delivers `"10.0.0.9"` to `admit` as a credential.
  The default `admit` ignores it → no behaviour change; a custom `admit` must
  tolerate arbitrary/garbage credential strings. Documented in `admit` KDoc and
  the README.

- **`admit` on the listener thread.** A blocking implementation stalls inbound
  demultiplexing for *every* client (handshakes and data). Same constraint as
  `onClientConnect`, stated more forcefully in the KDoc. An async accept path is
  deferred (§9) — the issue asks for a synchronous predicate.

- **`REFUSED` token collision.** A server→client application datagram reading
  `REFUSED …` is delivered normally post-`start()` (the session classifier only
  checks `KA`); `REFUSED` is interpreted *only* by `handshake()` reading its one
  reply. Same caveat class as `REGISTERED`; documented.

- **Credential is cleartext.** UDP here has no transport security; an opaque
  bearer token is sniffable and replayable on the path. The library provides a
  *channel*, not confidentiality — a scheme needing secrecy must be
  self-protecting (short TTL, channel binding) or run over an encrypted underlay.
  Boundary-Ring note in `HandshakeWireFormat` KDoc and the README.

- **Handshake amplification / no rate limiting.** Each junk `Iam` now costs an
  `admit` call plus a `REFUSED <reason>` reply that can be larger than the
  request. Mitigation guidance: keep refusal reasons short (enforced-ish by the
  control-char collapse, not by length). Per-origin handshake rate limiting is a
  possible follow-up (§9), not in scope.

- **`@JvmOverloads` correctness (load-bearing).** Before tagging, `javap` the
  built classes and confirm:
  - `MultiConnectionUDPClient`: `handshake(String)`, `handshake(String, int)`,
    `handshake(String, int, String)`.
  - `HandshakeWireFormat`: `handshakeMessage(String)`,
    `handshakeMessage(String, String)`.
  (Same pre-tag step as #8/#9/#10.)

- **`data object Admitted`.** Requires Kotlin ≥ 1.9; repo is on 2.2 — fine.
  Java sees `Admission.Admitted.INSTANCE`.

- **Concurrency.** No new shared state and no new thread. `admit`'s result is
  consumed inline on the listener thread before `registrations.add`. The
  registration store is already copy-on-write.

- **`onClientConnect` timing unchanged for the accept path** — still fires after
  `REGISTERED`, still on the listener thread. Only the refuse path is new.

- **Cross-repo impact.** None forced. GameTools' `AuthProvider` / server wiring
  adopts `admit` on its own schedule; downstream adoption is that repo's concern
  and is not a gate on closing #11.

---

## 7. Version

**`webtools-udp` `1.3.0` → `1.4.0`.**

- Additive only: two new public types (`Admission`, `HandshakeRefusedException`),
  one new `open` method with a behaviour-preserving default
  (`MultiConnectionUDPServer.admit`), new members on `HandshakeWireFormat`
  (`REFUSED_REPLY`, `refusedMessage`, `isRefused`, `refusalReason`), one new
  trailing `@JvmOverloads` parameter on each of `handshakeMessage` and
  `handshake`. `HandshakeProtocol` / `Handshake` are `internal`.
- Nothing removed; no existing public signature changed. Wire format gains an
  **optional** token and a new reply verb, back-compatible both directions (§6).
- The versioning convention maps an addition to a **third-number bump** — not a
  trailing-letter suffix (bugfix only), not a major (no breaking change).
  Matches the #10 shape: implementation commit(s), then a
  `build: bump webtools-udp to 1.4.0` commit.

---

## 8. Decisions (resolved)

All resolved from the issue text + current source. **No decision is left for the
maintainer**; §3 is executable as written. Each is stated so it can be
overridden with a one-line instruction if the maintainer disagrees.

- **D1 — Credential encoding. RESOLVED: a single whitespace-free token,**
  `Iam <name> <credential>`, opaque, passed verbatim to `admit`; absent → `""`.
  The whole wire format is space-split and already forbids whitespace in
  `<name>`; a length-delimited blob would need a bespoke parse path. Binary
  credentials: the caller base64url-encodes (documented). Rejected: length-prefix
  blob (§2.9).

- **D2 — Re-screen on a same-name supersede. RESOLVED: yes — `admit` runs
  before `registrations.findByName(name)`.** A refused newcomer never reaches the
  supersede code, so it cannot evict the incumbent (name-spoof eviction DoS
  otherwise). An admitted same-name newcomer still supersedes as today (§2.4).

- **D3 — Version. RESOLVED: `1.4.0`, additive.** Trailing defaulted params +
  `@JvmOverloads`, `open` method with a compatible default, `internal` protocol
  changes, new types. No signature break → no major bump; not a bugfix → not a
  letter suffix (§7).

- **D4 — Java-caller ergonomics. RESOLVED: `@JvmOverloads` on `handshake` and
  `handshakeMessage`; `credential` is the last parameter** so every existing
  positional call is unchanged. `admit` needs no overloads (all params required;
  it is an override point). `javap` check before tagging (§6).

- **D5 — `Admission` shape and `Refused.reason` round-trip. RESOLVED:** public
  `sealed interface Admission` in its own file, `data object Admitted` +
  `data class Refused(val reason: String)`. On the wire: `REFUSED ` fixed
  prefix, reason = everything after it; `refusalReason(reply) =
  reply.removePrefix("REFUSED").trim()`. The server trims and collapses
  control/newline runs in the reason before sending (cosmetic; single datagram,
  no injection risk). Multi-space reasons survive (only the 8-char prefix is
  stripped).

- **D6 — Thrown `admit`. RESOLVED: catch, log WARN, drop the handshake (no
  reply, no registration), `accept` → `Result.failure`.** Not synthesised into a
  `REFUSED` — an internal fault must not be presented to an unauthenticated peer
  as an auth verdict. Client sees a timeout and may retry (the existing
  server-fault mode).

- **D7 — Reply verb spelling. RESOLVED: `REFUSED`,** per the issue.

---

## 9. Sequencing & follow-ups

**Order of operations (all on `feat/issue-11-handshake-refusal-and-credential`):**

1. **Stage 1 — production + docs, one commit.** §3.1–§3.8 (`Admission`,
   `HandshakeRefusedException`, `HandshakeWireFormat`, `HandshakeProtocol` +
   `Handshake`, `HandshakeCoordinator`, `MultiConnectionUDPServer`,
   `MultiConnectionUDPClient`), §3.8 README, **plus this plan document**.
   `./gradlew :webtools-udp:compileKotlin` green; the existing test suite compiles
   only after Stage 2's coordinator-constructor updates, so run
   `:webtools-udp:build` at the end of Stage 2.
2. **Stage 2 — tests, one commit.** §5 all levels, plus the
   `HandshakeCoordinatorTest` / `HandshakeCoordinatorGatingTest` constructor-call
   updates for the new `admit` param. Every level task green. Run the `javap`
   signature check and paste the output into the PR.
3. **Stage 3 — version bump, one commit.** §3.9 (`build.gradle.kts` → `1.4.0`).
4. **After merge:** backfill this doc's `Commit:` / `PR:` header fields with the
   real SHA(s) and PR number. Comment on #11 that the library-side change has
   landed and close it (GameTools' `AuthProvider` adoption tracked separately in
   that repo).

**Deliberately deferred (not designed here):**

- An **asynchronous accept path** (`admit` returns a future / the handshake is
  parked until an out-of-band verification completes) — a substantially larger
  change to the listener-thread model; only if a consumer's auth cannot be made
  fast-and-local.
- **Per-origin handshake rate limiting / refusal backoff** — mitigates the junk-
  `Iam` amplification noted in §6; independent of this change.
- A **structured credential type** on the public API (vs. the opaque string) —
  only if multiple consumers converge on the same shape.
- Surfacing the **refusal reason as an enum / code** rather than free text — the
  issue asks for `REFUSED <reason>` free text; revisit if consumers want to
  branch on it programmatically.

---

## 10. Version control

- **Branch:** `feat/issue-11-handshake-refusal-and-credential` off `master`.
- **Working tree is clean** as of planning (verified) — no unrelated pre-existing
  changes to carve into a separate commit.
- **Commit sequence** (each a coherent unit):
  1. `feat: allow the webtools-udp server to refuse a handshake and carry an opaque credential (Issue #11)` — §3.1–§3.8 + this plan document.
  2. `test: cover handshake refusal, the credential channel, and the incumbent-eviction guard (Issue #11)` — §5 all levels; includes the `javap` signature verification.
  3. `build: bump webtools-udp to 1.4.0 for the refusable handshake and credential channel (Issue #11)` — §3.9.
- The plan document rides in commit 1 (the first implementation stage) so
  `git log --follow docs/issue-11-handshake-refusal-and-credential-plan.md`
  permanently binds plan to implementation.
- **Commit trailer:** none. Attribution is off for this repo — commits carry no
  `Co-Authored-By` / `Claude-Session` trailer.
- **PR description:** no attribution footer.
- Open the PR against `master`.
