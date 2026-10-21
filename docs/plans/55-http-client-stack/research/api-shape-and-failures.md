# Public API shape and failure model of `webtools-http`

## Question

What public surface and failure model make the `webtools-http` stack coherent in idiomatic Kotlin 2.2.0: the opt-in markers and how they propagate, one `Throwable` failure hierarchy carried in `kotlin.Result`, the call facade and its endpoint descriptor, one evolvability convention for Stable Core classes, the vocabulary types, packages and naming, lifecycle, logging from common code, and ABI tooling?

An answer has to meet these requirements:

- What consumers call or configure is Stable Core. Every seam a consumer implements or substitutes is Experimental behind **one** `@RequiresOptIn` marker. The supplied defaults are usable without opting in.
- Seams graduate to `@SupportedExtension` in a feature release once a consumer has built against them.
- Failures travel through `kotlin.Result` with no nullable results and no exceptions across the API. Cancellation propagates.
- Open hierarchies sit at genuine extension points and `sealed` only where the closed set is the contract. Policy is parameterised. Hooks are exposed. Additions are opt-in.
- Ports live in `commonMain`. `explicitApi()` and Level-2 KDoc apply. No library type appears in a public signature.
- Kotlin 2.2.0, kotlinx-coroutines 1.10.2, Java 11 bytecode.

Evidence labels: **established consensus** (compiler source and test data at a pinned tag, the language specification, several independent credible sources), **common practice** (what mature libraries visibly do, read at a pinned tag), **one source** (one author or one document). No compiler was available to this research: every Kotlin behaviour below is read from compiler test data, stdlib source or documentation, and says so.

## Bottom line

- **One marker, applied only through `@SubclassOptInRequired`.** `ExperimentalForInheritanceWebToolsApi` (`RequiresOptIn.Level.ERROR`, `@Target()`) lives in `webtools-http`; `webtools-feeds` reuses it through its `api` dependency. `@SubclassOptInRequired` is stable on 2.2.0 (`@SinceKotlin("2.1")`, no opt-in needed), gates implementing and delegating but not mentioning a type, and works on `abstract` classes and plain interfaces. A direct `@RequiresOptIn` on any type that appears in a default-path signature would force opt-in on callers who never name it (Kotlin's own KDoc example is `bar(arg: Unstable = Unstable())`), so `@Target()` makes that impossible by construction. **A `fun interface` cannot carry the gate** (compile error in the 2.2.0 test data), so `WallClock` and `BudgetObserver` become plain interfaces and lose lambda construction. `@SupportedExtension` is defined when the first seam graduates, because no 1.0.0 type carries it.
- **Failures: an open `abstract class HttpFailure : Exception` hierarchy, not a sealed one.** Two abstract groups (`TransportFailure`, `UpstreamFailure`), final leaves nested in their group, one shared `Closed`. This is what `ReliableSendFailure` already does in this repository, for the stated reason. A sealed root would turn every added leaf into a compile error for consumers' `when` and, for already-compiled consumers, a runtime exception. Output reason types (`RefusalReason`, `ExhaustedReason`) are open constants classes, not enums, so the compiler forces the consumer's `else`. Cancellation is thrown, never a failure; no `runCatching` around suspend calls.
- **Two final facade classes built by factory functions, composing cache then budget then transport with the budget acquired inside the cache loader.** `UpstreamClient` serves `Endpoint<T>` calls for one trusted upstream; `UrlFetcher` serves raw calls for administrator-supplied URLs (policy pre-check costs nothing, no decode, no cache). The decode function travels with the `Endpoint<T>`, because the cache key excludes the decoder. A `429` becomes `RateLimited(status, retryAfter)` returned to the caller; nothing retries. The client closes only what it built.
- **One evolvability rule: no defaulted parameters in public constructors or Stable Core functions.** A class either has a frozen shape (every constructor parameter is required and part of its meaning) or it is built by a same-named factory function with a trailing builder lambda and an `internal` constructor. Adding a defaulted parameter breaks binary compatibility for Kotlin callers too, `@JvmOverloads` does not repair it, and `@IntroducedAt` is `@SinceKotlin("2.3")` and experimental, so it is unavailable on 2.2.0. **This conflicts with the requirement that policy numbers be constructor parameters with defaults**: the numbers stay parameterised with defaults, but as builder properties. Consideration 1 lays out the alternative (flat constructors plus hidden-overload discipline).
- **ABI tooling: `org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.2, one JVM dump per module.** KGP 2.2.0's built-in `abiValidation` is documented as experimental ("we don't recommend using it in production yet") behind an ERROR-level opt-in whose DSL changed by 2.4. Dumps record the marker class but not the `@SubclassOptInRequired` gate, so gating needs its own compile check.
- **Lifecycle, logging, packages.** Every long-lived component is a `kotlin.AutoCloseable` with a non-suspend, idempotent `close()`; the budget's suspend `close()` becomes `close()` plus an optional `suspend flush()`. Logging uses an `internal` logging facade obtained through `internal expect fun`, with an slf4j actual in `jvmMain`; `BudgetObserver` stays as a consumer hook, not the logging path. One flat package `com.spartanlabs.webtools.http`. Java callers are a non-goal (suspend, `Result` and `Duration` all mangle JVM names).

## Findings

### F1. `@SubclassOptInRequired`: stable on 2.2.0, `abstract` and plain interfaces yes, `fun interface` and `sealed` no (established consensus; Kotlin 2.2.0)

- The annotation is declared `@SinceKotlin("2.1")` and `@WasExperimental(ExperimentalSubclassOptIn::class)`, with no `@RequiresOptIn` of its own, in the common stdlib source at `v2.2.0`. Its KDoc: "Uses of this annotation are limited to open and abstract classes, and non-`fun` interfaces. Any other uses allowed by `CLASS` annotation target yield a compilation error." (Claim 1.)
- The compiler's test data at `v2.2.0` confirms each inapplicable target with `SUBCLASS_OPT_IN_INAPPLICABLE`: final classes, data and value classes, **`fun interface`**, enums, objects, **`sealed class` and `sealed interface`**, and local classes. (Claim 4.) So the budget findings' statement that a `fun interface` "cannot take the opt-in" (`call-budget.md:404`) is correct, and a sealed type cannot be gated either.
- Behaviour on the allowed targets, from the same test directory (claims 5 to 7):
  - Mentioning a gated interface in a signature needs nothing: `fun useSiteTestInterfaces(o: InterfaceOptInApi, ...)` carries the comment "usage opt-in isn't required".
  - Implementing it needs the opt-in, and so does **inheritance by delegation**: `ToBeInheritedByDelegation by arg` reports `OPT_IN_TO_INHERITANCE_ERROR`. A consumer's decorator over `HttpTransport` is therefore an opt-in use.
  - `@OptIn(Marker::class)` on a subclass is non-propagating ("opt-in present: no diagnostic, opt-in isn't propagated"), and "inheritance opt-in isn't propagated" to a further subtype of an opted-in subtype.
  - The default message reads "This class or interface requires opt-in to be implemented. Its usage must be marked with '@ApiMarker', '@OptIn(ApiMarker::class)' or '@SubclassOptInRequired(ApiMarker::class)'".
- The requirement "is not propagated to any inner or nested classes" (Kotlin documentation), so a companion `val System` or a nested leaf class stays free to use.
- It is used in common code by comparable libraries: `kotlinx.serialization` v1.9.0 puts `@SubclassOptInRequired(SealedSerializationApi::class)` on `SerialDescriptor` in `core/commonMain`. (Claim 9.)
- Measured earlier on Kotlin 2.2.0 JVM (`test-support-and-time.md:54`): the gate binds implementers of the port, neither users of a fake nor subclasses of an `open` fake. Supplied defaults are therefore `final`.

### F2. Mentioning a directly marked type propagates the opt-in to every caller; mentioning a subclass-gated type does not (established consensus)

- Kotlin's documentation: "If the signature of an API element includes a type that requires opt-in, the signature itself must also require opt-in. Otherwise, if an API element doesn't require opt-in, but its signature includes a type that does, using it triggers an error." and, for `@OptIn` on a declaration whose signature mentions a marked type, "the opt-in requirement still propagates". (Claim 3.)
- The stdlib KDoc spells out the default-argument case: `@OptIn(UnstableApi::class) fun bar(arg: Unstable = Unstable()) {}` and a call `bar()` that "will trigger" the opt-in error: "Even though call sites do not mention `Unstable` type directly, the corresponding opt-in warning or error will be triggered in each call site due to propagation contagiousness." (Claim 2.)
- Consequences for the stack, with `RateSignals` and `BudgetStore` as the examples the budget findings marked directly:
  - `Permit.complete(signals: RateSignals)` with `RateSignals` marked would force an opt-in on every caller of `permit.complete()`, including the facade's consumers who use `withPermit`.
  - `WindowedCallBudget(windows, store: BudgetStore = InMemoryBudgetStore())` with `BudgetStore` marked would force an opt-in on every `WindowedCallBudget(windows)`.
  - With `@SubclassOptInRequired` on `BudgetStore` and no mark on `RateSignals`, neither happens. Only `class MyStore : BudgetStore` opts in.
- Design rule that follows: the marker never appears directly on a declaration. It has `@Target()`, so the compiler rejects direct use and the default path cannot be contaminated by a later edit.

### F3. The marker's shape follows two published precedents and the org's own (common practice)

- `kotlinx.serialization` v1.9.0: `SealedSerializationApi` is `@RequiresOptIn(level = ERROR)` with `@Target() // no direct targets, only argument to @SubclassOptInRequired`. (Claim 9.)
- `kotlinx.coroutines` 1.10.2: `ExperimentalForInheritanceCoroutinesApi`, `@Target(AnnotationTarget.CLASS)`, level `WARNING`, KDoc "Marks declarations that cannot be safely inherited from." It had to be `WARNING` because the gate was retrofitted onto widely implemented types. The JVM API dump lists the marker class and does not list the gate on `SharedFlow`. (Claim 22.)
- GeneralTools (`SpartanLaboratories/GeneralTools`, branch `feat/issue-7-kotlin-multiplatform`, `7e171ab`): `ExperimentalForInheritanceGeneralToolsApi`, `ERROR`, `@Target()`, `@Retention(BINARY)`, with the KDoc "The level is ERROR: lowering a level later never breaks a consumer, raising one would." MyGameTools (`master`, `dc5214f`) uses `ExperimentalGameToolsApi` at `ERROR` with explicit targets and KDoc that says the tagged API "may change incompatibly in a Feature release". The name pattern `ExperimentalForInheritance<Library>Api` is the one for subclass-only gates.
- Reuse by the feeds module is mechanical: `webtools-feeds` declares `api(project(":webtools-http"))`, writes `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)` on its parser port, and a consumer opts in with the same one import. One marker for the WebTools HTTP stack and its feeds module is consistent with "one marker"; a separate artifact only to hold an annotation is not worth a third coordinate. The name says "WebTools" rather than "Http" so `webtools-udp` could adopt it later without a rename.
- Once a seam graduates, the marker class stays (MyGameTools' KDoc: "Never deleted ... a consumer's own `@OptIn(...)` would fail to compile with an unresolved reference"). Kotlin's documentation says the same: "you should leave the annotation classes in modules so that the existing client code remains compatible." (Claim 3.)
- `@SupportedExtension` is documentary (GeneralTools, MyGameTools: `BINARY` retention, no gate). Nothing in `webtools-http` 1.0.0 is a Supported Extension, because every seam starts Experimental, so the annotation would be defined and unused. It is added in the feature release that graduates the first seam, as an additive new type, together with the CHANGELOG record of the promotion.

### F4. Open failure hierarchy: the repository precedent, the specification and the compiler agree on why (established consensus)

- `webtools-udp` already carries the decision. `ReliableSendFailure.kt:3-15`: "Deliberately **open, not sealed**: a caller can `catch`/`is`-check one type today, and a future reliable-send failure can join this hierarchy in a minor version without breaking anyone's exhaustive `when`. `abstract` because nothing should ever construct or throw the bare supertype ... only as a [Result.failure] payload, never thrown." The README states the matching rule for enums (`README.md:70-76`: "may each gain a new entry in a **minor** release; keep an `else` branch in any `when` over one of them").
- What the compiler does when a library adds a subtype to a sealed type:
  - Source: the specification calls a `when` with a subject exhaustive when "all of its *direct non-sealed subtypes* are covered", and a non-exhaustive `when` "has type `kotlin.Unit` and may be used only as a statement". Since Kotlin 1.7, "the `when` statement with an enum, sealed, or Boolean subject being non-exhaustive" is an error. So a consumer recompiling against a library that added a leaf gets a compile error in every `when` over the sealed type that lacks an `else`. (Claims 11, 12.)
  - Binary: JetBrains' own codegen test compiles `fun test(s: S): String = when (s) { is K -> "" }` against `sealed interface S` with the single subtype `K`, then runs it with a module where `S` also has `K2`, and expects an exception. Already-compiled consumers therefore fail at runtime, not at link time. (Claim 13.)
- Sealed hierarchies still grow in practice. Apollo Kotlin 4.4.1 has `sealed class ApolloException` with some fifteen leaves, including one `@Deprecated` leaf (`ApolloParseException`, "now mapped to ApolloNetworkException") and one `@ApolloExperimental` leaf; each such change is a source break for exhaustive consumers. (Claim 23.)
- An open (`abstract`) root also keeps the gate available: `@SubclassOptInRequired` cannot go on a sealed type (F1).
- Cost of open: the compiler enforces `else` only for `when` expressions; a `when` used as a statement over an open type silently ignores unknown leaves. The KDoc therefore states: "match the groups you care about, and keep an `else`".
- Kotlin's own `Result` KEEP records a dissent: "The `Result` class is not designed to represent domain-specific error conditions" and prefers nullable or domain-specific result types for failures a caller handles locally. The repository rule (`.aiassistant/rules/CLAUDE.md:15-16`) chooses `Result` for expected failures regardless, so the dissent is recorded here and not acted on. (Claim 18.)

### F5. Output reasons are open constants classes; input choices may be enums (established consensus, derived from F4)

The language specification makes an `enum`-subject `when` exhaustive when all entries are checked, so the same growth problem applies to a library-produced enum. A class with a private constructor and companion constants has no exhaustiveness rule, so the compiler requires an `else` in every `when` expression over it. `HttpMethod` already has this shape in the transport findings (`http-transport.md:198`).

- Library-produced values the consumer branches on (`RefusalReason`, `ExhaustedReason`): constants class, equality by name, no `of()` (the library is the only producer).
- Values the consumer chooses and passes in (`MixedAnswers`, `RecordMode`): enum. A new entry never breaks a producer.
- Closed sets that are the contract: `sealed` (`OutboundUrlPolicy.Trusted`/`Untrusted`, `Lookup.Found`/`Missing`, `RecordingGate`, `Unmatched`).

### F6. `Result`, cancellation and exceptions-as-values (established consensus plus common practice)

- `Result` is `@JvmInline public value class Result<out T>` at v2.2.0 and "encapsulates a successful outcome with a value of type [T] or a failure with an arbitrary [Throwable] exception". Every function that takes or returns it is mangled. (Claims 15, 16.)
- `CancellationException` "indicates _normal_ cancellation of a coroutine" and is declared `public expect open class CancellationException : IllegalStateException` in the common stdlib, so a `catch (e: IllegalStateException)` or `catch (e: Exception)` catches it. Detekt's `SuspendFunSwallowedCancellation` rule states "`suspend` functions should not be called inside `runCatching`'s lambda block" and that `CancellationException` must be rethrown immediately. Arrow's `NonFatal` excludes `CancellationException` and VM errors: "This will also not match [CancellationException] since that's a fatal exception in Kotlin for cancellation purposes." (Claims 19 to 21.) The design takes the same line: an internal helper catches `Exception` except `CancellationException`, converts it to `Result.failure`, and lets `Error` and cancellation propagate. Public code never wraps a suspend call in `runCatching`.
- Failures are created on a library dispatcher thread inside the cache loader and are shared by every coalesced waiter (`response-cache.md:32`, `:48`). So a failure is an immutable value; nobody calls `addSuppressed` on it; its stack trace names where it was created, not where it was consumed. At the rate-limited volumes of this stack (tens of calls a minute) the cost of `fillInStackTrace` is irrelevant. It also cannot be avoided in common code: the common `Exception` has only the four `(message, cause)` constructors, and the JVM-only `writableStackTrace` constructor is not reachable. (Claim 17.)
- kotlinx-coroutines' stack-trace recovery copies an exception reflectively only if it has no fields beyond `Throwable`'s ("Skip reflective copy if an exception has additional fields"). Failure leaves that carry state (`BodyTooLarge`, `RateLimited`) are skipped; stateless leaves are copied with the same class, message and original cause, which is harmless. (Claim 14.)
- Message rule, common practice and contrast: Ktor 3.6.0's `ResponseException` builds its message from `response` and the body text, and its `ServerResponseException` message includes `request.url` ("Server error(GET https://...") and the response text. Retrofit 3.0.0's `HttpException` message is `"HTTP " + code + " " + message` and nothing else. A message here is a constant per type plus non-sensitive fields (status, limits, a reason name); never a URL with its query, a header, a credential, a body or a resolved address. (Claims 24, 25.)
- `Result.getOrThrow()` rethrows the failure itself, so a consumer working in exception style gets an `HttpFailure` directly. `Lookup.Missing` is a success value; a caller that wants "missing is an error" maps it explicitly.

### F7. Evolvability: defaulted parameters are a binary break for Kotlin callers, and the 2.2.0 toolbox has two safe patterns (established consensus)

- Kotlin's library guidelines: "Adding non-default arguments to a public API breaks both binary and source compatibility ... However, even adding default arguments can break compatibility." and "The `@JvmOverloads` annotation doesn't preserve binary compatibility for Kotlin callers. Instead, use the `@IntroducedAt` annotation or add manual overloads when changing published APIs." and "Avoid using data classes in your API" (the generated constructor and `copy` change signature). (Claim 8.)
- `@IntroducedAt` is declared `@SinceKotlin("2.3")` with an ERROR-level `@ExperimentalVersionOverloading`. It is absent from the stdlib sources at `v2.2.0` and `v2.3.0` and present at `v2.3.20`. It cannot be used on Kotlin 2.2.0. (Claim 10.)
- A Kotlin caller compiled against `Foo(a = 1)` binds to the synthetic `Foo.<init>(..., int mask, DefaultConstructorMarker)`. Adding a parameter changes that signature, so the old call site throws `NoSuchMethodError` unless the old constructor is kept. This is why the guidelines say binary compatibility is lost "because the signature of the method changed in the bytecode generated by the Kotlin/JVM compiler".
- Pattern A, builder-lambda factory (common practice): `kotlinx.serialization`'s `public fun Json(from: Json = Json.Default, builderAction: JsonBuilder.() -> Unit): Json` over `public class JsonBuilder internal constructor(json: Json)`; Ktor's `public fun HttpClient(engine: HttpClientEngine, block: HttpClientConfig<*>.() -> Unit): HttpClient`. The factory signature never changes; the builder gains properties. Holds for Kotlin callers: the compiled call site references the factory (`(…, Function1)`) and the builder's existing accessors, none of which change. Value-class properties such as `Duration` produce mangled accessor names (`setCallTimeout-<hash>`), which are stable as long as the property type is.
- Pattern B, retain the old constructor as `@Deprecated(level = HIDDEN)` plus a new constructor (Coil 3 does this: `NetworkRequest` carries two hidden constructors "Kept for binary compatibility", `http-transport.md:161`). It keeps the literal "constructor parameters with defaults" and costs one permanent overload and a hand-checked `$default` shape per addition.
- Explicit overloads, not defaults, for the few Stable Core functions with an optional input (`Permit.complete()` and `complete(signals)`; `RetryAfter.parse(value, now)` and `parse(value, now, serverDate)`). Interface ports grow by adding a method **with a body**: Kotlin 2.2.0 compiles interface bodies to JVM default methods under the default `-jvm-default=enable`, which "generates default implementations in interfaces and includes bridge functions in subclasses and `DefaultImpls` classes". (Claim 16.)
- Public `inline` functions copy their body into callers and may call only public or `@PublishedApi` API (guidelines, "`@PublishedApi` and inline functions"). `withPermit` is therefore not `inline`.
- File-facade names are part of the ABI: a top-level factory function lives in the file named after its class, or carries `@file:JvmName` so a later move does not rename `FooKt`.

### F8. ABI tooling at 2.2.0 (established consensus)

- KGP 2.2.0's built-in validation is experimental: "we're experimenting with moving the functionality of the binary compatibility validator into the Kotlin Gradle plugin (KGP). You can try it out in toy projects, but we don't recommend using it in production yet." DSL `abiValidation { enabled.set(true) }` under `@OptIn(ExperimentalAbiValidation::class)`, tasks `checkLegacyAbi` and `updateLegacyAbi`. The marker at `v2.2.0` is `@RequiresOptIn("The ABI Validation DSL is experimental and may be changed or removed in any future release.", ERROR)`. The same capability is spelled `abiValidation()` with `enabled` removed at 2.4.21 (SpartanGraphics' research, re-read at `v2.4.21`; GeneralTools' research records the same churn), so a compiler bump would break the build file. (Claims 16, 26.)
- `org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.2 (2026-09-02): "This plugin is in maintenance mode. Fixes for critical bugs and support for current versions of Kotlin continue to be delivered." Requires Kotlin 1.6.20 or newer. It dumps "binary API of a JVM part of a Kotlin library", with klib dumps behind `@OptIn(ExperimentalBCVApi::class)` ("The KLib validation support is experimental"). Its `apply` runs `allprojects { configureProject(...) }` from the project it is applied to, so applying it in a module's convention plugin configures that module only, with its multiplatform branch registering `apiDump` and `apiCheck` and wiring `check`. (Claim 26.)
- Dumps contain mangled names (`execute-gIAlu-s`, `http-transport.md:152`, `kmp-build-topology.md:213`) because `Result` and `Duration` are inline value classes (Kotlin documentation: "functions using inline classes are mangled by adding some stable hashcode to the function name"). A signature change renames the method and shows as a removal plus an addition. `internal` constructors do not appear in a dump (BCV README: "*internal*, only in case if the class is annotated with `PublishedApi`"), which is what makes Pattern A visible as additions only.
- Neither tool records `@SubclassOptInRequired`: the `kotlinx-coroutines-core.api` dump at 1.10.2 contains the marker class and no gate on `SharedFlow`. Gating needs its own check (Gap 4).
- With only `jvm()` published, a JVM dump is the whole published ABI. A published `js` target (build-topology Consideration 1) would add a klib dump, which both tools support (BCV experimentally, KGP 2.2.0 in its `klib` block).

### F9. The facade is a composition, so substitution is one level down; ownership follows Ktor's `manageEngine` (common practice)

- Ktor's `HttpClient(engine: HttpClientEngine, block)` delegates with `manageEngine = false`, while the engine-factory overload manages (closes) the engine it created; `HttpClient` is `CoroutineScope, Closeable` and its `close()` also closes `AutoCloseable` plugins. That is the ownership rule needed here: close what you built, leave what you were given.
- Apollo's `ApolloClient.Builder` (v4.4.1), Coil 3's `ImageLoader.Builder` (`ccd6696`) and OkHttp's `OkHttpClient.Builder` are the chained-call form of Pattern A. With Java callers a non-goal, the Kotlin DSL form is used.
- The facade has no abstract members a consumer would add and no behaviour to substitute that is not already a component. Making it a final class lets it gain methods freely. Tests substitute `HttpTransport` with the fixtures' fake, which is the level at which fakes are useful (a fake of the facade would hide the cache, budget and status logic under test).

### F10. Cache identity makes the decoder part of the endpoint (derived from the cache findings)

The cache stores decoded values keyed by `(endpoint id, canonical params)` and never by caller, lane, headers or decoder (`response-cache.md:119`). If the decode function were a per-call argument, two calls with the same key and different decoders would share one stored value of whatever the first decoder produced. Binding the decoder to the `Endpoint<T>` removes that case, makes `CacheKey<T>` honest, and keeps serialization types in the caller's code (the decoder is a lambda from `HttpResponse` to `Result<T>`; `T` is the caller's domain type). A decoder that captures per-call data would be wrong for a shared entry anyway, and binding it at endpoint construction discourages that.

### F11. Lifecycle: `kotlin.AutoCloseable` is common since 2.0 and `use` is inline (established consensus)

`kotlin.AutoCloseable` is a common declaration "Since Kotlin 2.0" and `inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R` is common, so `client.use { it.call(...) }` compiles in common code with suspend calls inside the inline lambda (claim 25). The cache and transport ports already extend it. The budget's `suspend fun close()` is the only outlier. The write-ahead high-water mark means the persisted count is always at least the true usage (`call-budget.md:269-273`), so a final exact write in `close()` only reduces over-counting by at most `persistAhead - 1`; it is not a correctness need and can be an explicit `suspend fun flush()`.

### F12. Logging from common code: an `internal expect fun` seam is the one mechanism that logs by default (common practice plus repository research)

- The repository rule: "Include structured **logging** statements for key lifecycle events, data flows, and failure states." and "For libraries use slf4j" (`.aiassistant/rules/CLAUDE.md:20-21`). Existing code logs parameterised slf4j messages and logs full URLs (`codebase-blast-radius.md:134-138`), which this stack must not copy.
- `slf4j-api` is JVM-only, and `internal expect fun` raises no Beta warning on KGP 2.2.0 while `expect class` does (measured in the build-topology research, `codebase-blast-radius.md:146`).
- An observer port alone cannot be the logging path: with the default `BudgetObserver.None` the budget would be silent unless every constructing code path remembered to install a logging observer. Consumer-visible events and default logging are different jobs; both are kept (see "Logging" in the Recommendation).
- The transport findings (`http-transport.md:299`) already settle that observation of the transport is decoration, not a listener.

### F13. Packages and names: one flat package (repository precedent), technology-named defaults (common practice)

`webtools-udp` has 36 source files in one package, `com.spartanlabs.webtools.udp` (checked 2026-10-21). A flat package never needs a type to move: moving a type to a subpackage renames it for every consumer, which is a binary and source break. `internal` does the hiding that `.internal` subpackages would. The budget findings' `…http.budget` package is not used. Fixtures stay in `com.spartanlabs.webtools.http.testing` (a separate variant, no collisions). Defaults are named for their technology or algorithm (`OkHttpTransport`, `CaffeineResponseCache`, `WindowedCallBudget`, `InMemoryBudgetStore`); Ktor's `OkHttp` engine, Coil's `OkHttpNetworkFetcherFactory` and Apollo's `DefaultHttpEngine` show both habits, and `Default…`/`…Impl` says nothing a consumer can use. Trivial defaults are named constants on the port's companion (`WallClock.System`, `RateSignalsReader.Standard`, `ResponseCache.Disabled`, `CallBudget.Unlimited`, `BudgetObserver.None`).

### F14. Value classes are the wrong tool for `Lane` and the other vocabulary types (established consensus)

An inline value class is mangled in every function that takes it and "boxed whenever [it is] used as another type" such as a generic argument (Kotlin documentation, claim 25). `Lane` appears in `Map<Lane, Double>` (lane shares), in `Permit.lane` and in `acquire(cost, lane, maxWait)`. It is a plain final class with name equality, validation in the constructor, and companion constants for `Interactive` and `Tracker`. A sealed `Lane` with a `Custom` leaf would be closed for no benefit. The same rule gives `HttpMethod` (private constructor, constants, `of(name): Result<HttpMethod>`), `RefusalReason` and `ExhaustedReason`.

## How comparable projects do it

| Project (version) | Failure model | Construction and evolution | Marker or gate | What to take or avoid |
|---|---|---|---|---|
| **Ktor client** 3.6.0 (`d962801`) | Exceptions: `open class ResponseException` with `RedirectResponseException`, `ServerResponseException`, `ClientRequestException`; `HttpRequestTimeoutException` | `HttpClient(engine, block)` DSL, `manageEngine` ownership, `HttpClient : CoroutineScope, Closeable` | `@InternalAPI` opt-in | Open, non-sealed hierarchy and builder DSL: copy. Messages that embed `request.url` and the response text: avoid (a URL with a query key lands in a log). |
| **Apollo Kotlin** 4.4.1 | `sealed class ApolloException` (~15 leaves, thrown not returned), one `@Deprecated` leaf, one `@ApolloExperimental` leaf | `ApolloClient.Builder`; `HttpEngine : Closeable` | `@ApolloExperimental` per declaration | A sealed hierarchy that still gains and loses leaves: the cost F4 predicts. |
| **Coil 3** (`ccd6696`) | `sealed interface ImageResult` (`SuccessResult`, `ErrorResult(throwable)`), returned not thrown | `@Poko` plain classes with hand-written `copy`; hidden deprecated constructors for compatibility | none | A two-case sealed result is a genuine closed set (like `Lookup`); avoiding `data class` is the guideline; hidden constructors are Pattern B's cost. |
| **Kord** 0.18.1 | `abstract class RestRequestException(request, status, error)` with a Ktor-backed `KtorRequestException(httpResponse: HttpResponse, ...)` | n/a | not examined | Abstract root with an implementation subclass is the open shape; a Ktor type in a public property is what this repository forbids. |
| **Retrofit** 3.0.0 | `Response<T>` carries status as a value; `HttpException(response)` message `"HTTP " + code + " " + message` | `Retrofit.Builder` | none | URL-free message; status as data. |
| **kotlinx.serialization** v1.9.0 | exceptions | `Json { }` function over `JsonBuilder internal constructor` | `SealedSerializationApi`, `@Target()`, `@SubclassOptInRequired` on `SerialDescriptor` | Exact precedent for the marker and for Pattern A. |
| **kotlinx.coroutines** 1.10.2 | `CancellationException` for cancellation | n/a | `ExperimentalForInheritanceCoroutinesApi` (`WARNING`, `CLASS`) on `Job`, `SharedFlow` | Gate on abstractions that are stable to use; level was forced to `WARNING` by retrofit. |
| **OkHttp** 5.5.0 | `IOException` family | `OkHttpClient.Builder`, `newBuilder()` | none | The Java form of Pattern A. |
| **Arrow** 2.2.3 (contrast only) | `Either<E, A>` with a typed `E`; its `NonFatal` excludes cancellation and VM errors | n/a | not examined | A typed error channel gives exhaustive `when` for free and costs the open-hierarchy evolution story; this repository's `Throwable` channel interoperates with `getOrThrow`, `runCatching`-free code and the existing `ReliableSendFailure`. Take its cancellation rule, not its type. |

Org precedent: GeneralTools and MyGameTools each define `SupportedExtension` and an Experimental marker at library creation; SpartanGraphics guards backend contracts with `@SubclassOptInRequired(ExperimentalGraphicsApi)` (`docs/roadmap/research/backend-contract-design.md` in that repository).

## Pitfalls and regrets

1. **A directly marked type in a default-path signature** contaminates callers who never name it (F2). Prevented by `@Target()` on the marker.
2. **A `fun interface` seam cannot be gated** (F1). The price of gating `WallClock` and `BudgetObserver` is losing `WallClock { ... }`. A seam that is a pure single-method function and never grows is the case for an ungated Stable Core `fun interface` instead (Consideration 2).
3. **Delegation counts as subclassing.** A consumer's `class Logging(inner: HttpTransport) : HttpTransport by inner` opts in. That makes "decoration is the hook" an Experimental use, which is consistent, and the fixtures' own `FixtureRecorder` is the worked example.
4. **An `open` supplied default is a back door** around the gate (`test-support-and-time.md:54`). Defaults are `final`.
5. **A sealed failure root** turns each new leaf into a compile error for consumers and a runtime exception for compiled ones (F4); Apollo is the live example. An enum for a library-produced reason has the same effect (F5).
6. **`runCatching` around a suspend call** swallows cancellation. Use the internal non-fatal helper (F6).
7. **Sharing one failure instance among coalesced waiters** shares its stack trace and any `addSuppressed` call; treat failures as immutable.
8. **Messages built from URLs or bodies** leak keys into logs (Ktor's `ResponseException`).
9. **Defaulted constructor parameters** break binary compatibility for Kotlin callers, `@JvmOverloads` does not help them, and `@IntroducedAt` is not available before Kotlin 2.3 (F7).
10. **A top-level factory function moved to another file** renames its facade class and breaks binary compatibility; keep it beside its class or pin `@file:JvmName`.
11. **`inline` on a public function** (`withPermit` in the budget sketch) freezes its body into callers.
12. **Dumps do not record gating.** A change that drops `@SubclassOptInRequired` from a port passes the ABI check silently (F8).
13. **Raising a marker level later is a break; lowering is not.** Start at `ERROR` (GeneralTools' KDoc).
14. **A suspend `close()` on one of three siblings** defeats `use` and forces every owner to special-case it (F11).
15. **Logging through a hook only**, so the default is silent (F12).
16. **A shared cache load carries the first caller's lane and wait cap** (`response-cache.md:32`, `call-budget.md:636-639`); an interactive caller that joins a queued tracker load can wait as long as that load takes. The tracker lane therefore uses a short `maxWait`, and an interactive caller that must not wait wraps its own call in `withTimeoutOrNull` (safe: a waiter's cancellation does not cancel the load, `response-cache.md:55`).

## Recommendation for this repository

Adopt the surface below. Signatures only; package `com.spartanlabs.webtools.http` (common unless marked `jvmMain`), `explicitApi()` on, Level-2 KDoc on every declaration. Names are final unless a sibling system's research changes them.

### The marker and the rules it enforces

```kotlin
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target()                       // only an argument to @OptIn / @SubclassOptInRequired
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Implementing or substituting this WebTools seam is Experimental and may change in a minor " +
        "release. Using the supplied implementations needs no opt-in. " +
        "Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).",
)
public annotation class ExperimentalForInheritanceWebToolsApi
```

KDoc states that implementing the type, or delegating to it, is Experimental; calling it and passing it around is Stable Core; and the first consumer to build against a seam triggers graduation to `@SupportedExtension` (the marker class stays, and the CHANGELOG records the promotion). Rules:

1. The marker is used only as the argument of `@SubclassOptInRequired` on non-`fun` interfaces and abstract classes, and of `@OptIn` in the module's own build (a module-wide `optIn` compiler option) and in fixtures source. The supplied defaults additionally show the `@OptIn` a consumer would write, in their KDoc.
2. Seam interfaces are plain `interface`s. Supplied defaults are `final`. Fakes are `final`.
3. A seam interface grows only by adding methods with a body.
4. A consumer's own failure subclass, port implementation or decorator is an opt-in use.

### Failure model

```kotlin
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class HttpFailure : Exception {
    public constructor(message: String)
    public constructor(message: String, cause: Throwable?)

    // direct leaves (final)
    public class Closed internal constructor() : HttpFailure          // transport, cache, budget or client closed; one type for all four
    public class BudgetExhausted(public val wait: Duration, public val reason: ExhaustedReason, public val window: String?) : HttpFailure
    public class DecodeFailure(message: String, cause: Throwable?) : HttpFailure   // public: a decoder returns it
}

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class TransportFailure : HttpFailure {            // no usable response was produced, or the request was refused before one
    public class Timeout(message: String, cause: Throwable?) : TransportFailure
    public class ConnectFailed(message: String, cause: Throwable?) : TransportFailure
    public class DnsFailed(public val host: String, cause: Throwable?) : TransportFailure
    public class TlsFailed(message: String, cause: Throwable?) : TransportFailure
    public class Protocol(message: String, cause: Throwable?) : TransportFailure
    public class Io(message: String, cause: Throwable?) : TransportFailure
    public class InvalidRequest(message: String) : TransportFailure
    public class RefusedByPolicy(public val reason: RefusalReason) : TransportFailure
    public class BodyTooLarge(public val limit: Long, public val declared: Long?) : TransportFailure
    public class TooManyRedirects(public val limit: Int) : TransportFailure
    public class RetryRefused(message: String) : TransportFailure     // the one-exchange guard fired; indicates a bug or a new OkHttp path
}

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class UpstreamFailure(public val status: Int) : HttpFailure {   // the upstream answered, with a status the call does not accept
    public class RateLimited(status: Int, public val retryAfter: Duration) : UpstreamFailure
    public class UpstreamError(status: Int, public val headers: HttpHeaders, public val body: ByteArray) : UpstreamFailure
}
```

- **Grouping.** Transport (nothing usable came back), upstream (the server answered), and three direct leaves. There is no budget group: the one budget failure sits directly under the root, and `BudgetClosed` is `Closed`. Groups are added only when two members share a property a consumer needs.
- **Evolution.** New leaves may be added in a minor release; the KDoc and the README state "keep an `else` branch". Leaf primary constructors are frozen (all parameters required); an addition is a new property or a secondary constructor, never a changed primary one. Leaf constructors are public only where an implementer of a seam or a decoder must create them (`TransportFailure` leaves for transport implementers, `DecodeFailure`, `UpstreamError`); the rest are `internal`.
- **Shared `Closed`.** Cache, budget, transport and clients all return the same leaf. The message names the component.
- **Messages and causes.** A message is a constant per type plus non-sensitive fields; `cause` keeps the underlying exception for debugging; `toString` of `UpstreamError` omits headers and body. A decoder may return its own `HttpFailure` subtype (it opts in) or a plain `Throwable`, which the facade wraps in `DecodeFailure`.
- **Reasons** are open constants classes:

```kotlin
public class RefusalReason private constructor(public val name: String) {
    public companion object { public val Scheme: RefusalReason; /* UserInfo, HostForm, ReservedName, DeniedAddress, Port,
        HostNotPinned, InsecureRedirect, SensitiveHeader */ }
}
public class ExhaustedReason private constructor(public val name: String) {
    public companion object { public val Window: ExhaustedReason; /* Paused, CostExceedsLimit, QueueFull */ }
}
```

- **Cancellation.** `CancellationException` is thrown, never wrapped. An internal `suspend inline` helper catches `Exception` except `CancellationException`, converts it to `Result.failure`, and rethrows everything else. Fatal `Error`s propagate (the cache rethrows them to each waiter, `response-cache.md:204`).

### Conventions

| Rule | Statement |
|---|---|
| No defaults on public API | Public constructors and Stable Core functions take no defaulted parameters. Optional input is a builder property or an explicit overload. The sole default allowed is a trailing builder lambda `= {}`. |
| Frozen-shape types | A type whose every constructor parameter is required and is its meaning keeps a public constructor: `Lane(name)`, `CacheKey`, `Lookup.Found(value, weight)` with `Found(value)` as a second constructor, failure leaves, `RateWindow` required parts. |
| Configurable types | `class X internal constructor(...)` plus `public fun X(required..., configure: X.Builder.() -> Unit = {}): X` and `public class Builder` with `var` properties (defaults in the builder) and validation at construction. Applies to `TransportSettings`, `CachePolicy`, `OutboundUrlPolicy.Trusted/Untrusted` (through `OutboundUrlPolicy.trusted { }` and `untrusted { }`), `Endpoint`, `HttpRequest`, `HttpResponse`, `RateSignals`, `RateWindow`, and the configuration of `OkHttpTransport`, `CaffeineResponseCache`, `WindowedCallBudget`, `UpstreamClient`, `UrlFetcher`. There is no separate `BudgetPolicy` type; its numbers are properties of the budget's builder. |
| Not `data` | No public `data class`. `equals`, `hashCode` and `toString` are hand-written where equality is meaningful (`Lane`, `CacheKey`, `Url`, `HttpMethod`, reasons); `toString` never prints values that may hold secrets (`CacheKey` prints parameter names only). |
| Inputs and parsing | Text from outside the process returns `Result` (`Url.parse`, `CidrBlock.parse`, `HttpMethod.of`). Developer-written constants are checked by `require` and throw `IllegalArgumentException` at construction (`Lane`, header names and values in `HttpHeaders.Builder`, builder validation). That is a startup bug, not an expected failure. |
| Ports | Interface ports with `@SubclassOptInRequired`; method parameters frozen; growth through overloads with bodies. Trivial defaults are companion constants. |
| Outputs | Library-produced reasons: constants classes. Consumer-chosen options: enums. Contract-closed sets: `sealed`. |
| Inline | No public `inline` functions (so `withPermit` is a plain `suspend fun`), except where a lambda must be non-local and the body is trivial and uses only public API. |
| Fixtures | Held to source compatibility only, because test code is compiled against the fixtures in the same build and no ABI dump covers the variant. Defaulted parameters stay there. Everything in the fixtures that is public is documented. |

### The facade

```kotlin
public class Endpoint<T : Any> internal constructor(...) {
    public val id: String; public val name: String
    public val ttl: Duration; public val missTtl: Duration
    public val cost: Int; public val lane: Lane
    public val missStatuses: Set<Int>              // statuses that are a confirmed miss; default none
    public class Builder { public var name: String; public var ttl: Duration; public var missTtl: Duration
                           public var cost: Int; public var lane: Lane; public fun misses(vararg statuses: Int) }
}
public fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T>

public class CallSpec internal constructor() {     // per call
    public fun path(vararg segments: String)       // each segment percent-encoded; "." and ".." become InvalidRequest at call time
    public fun query(name: String, value: String)  // decoded; the canonical key sorts by name, stable
    public fun header(name: String, value: String)
    public var method: HttpMethod                  // Get; non-GET bypasses the cache and coalescing
    public fun body(bytes: ByteArray, contentType: String)
    public var lane: Lane?                         // unset: the endpoint's
    public var maxWait: Duration?                  // budget wait cap; unset: the client's
    public var maxAge: Duration?                   // unset: any live entry
}

public class UpstreamClient internal constructor(...) : AutoCloseable {
    public val baseUrl: Url
    public suspend fun <T : Any> call(endpoint: Endpoint<T>, configure: CallSpec.() -> Unit = {}): Result<Lookup<T>>
    public suspend fun utilisation(): BudgetUtilisation
    public fun cacheStats(): CacheStats
    override fun close()                           // idempotent; closes cache, then budget, then transport, each only if the client built it
}
public fun UpstreamClient(transport: HttpTransport, baseUrl: Url, configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClient   // commonMain
public fun UpstreamClient(baseUrl: Url, userAgent: String, configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClient         // jvmMain: builds and owns OkHttp, Caffeine, Windowed

public class UpstreamClientConfig internal constructor() {
    public var policy: OutboundUrlPolicy           // default: trusted, pinned to baseUrl's host
    public var timeSource: TimeSource              // Monotonic; reaches owned cache and budget
    public var wallClock: WallClock                // WallClock.System; reaches owned budget and the signal reader
    public var dispatcher: CoroutineDispatcher     // Dispatchers.Default; reaches the owned cache's load scope
    public var rateSignals: RateSignalsReader      // RateSignalsReader.Standard
    public var defaultMaxWait: Duration
    public fun headers(configure: HttpHeaders.Builder.() -> Unit)   // default request headers; credentials via addSensitive
    public fun cache(cache: ResponseCache)         // caller-owned, never closed by the client; unset: owned Caffeine (jvmMain) or Disabled (commonMain)
    public fun budget(budget: CallBudget)          // caller-owned
    public fun budget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {})   // built and owned
}

public class UrlFetcher internal constructor(...) : AutoCloseable {   // administrator-supplied URLs: no endpoint, decode or cache
    public suspend fun fetch(request: HttpRequest, configure: FetchSpec.() -> Unit = {}): Result<HttpResponse>
    override fun close()
}
public class FetchSpec internal constructor() { public var cost: Int; public var lane: Lane?; public var maxWait: Duration? }
public fun UrlFetcher(transport: HttpTransport, policy: OutboundUrlPolicy, configure: UrlFetcherConfig.() -> Unit = {}): UrlFetcher   // commonMain
public fun UrlFetcher(userAgent: String, configure: UrlFetcherConfig.() -> Unit = {}): UrlFetcher                                     // jvmMain: Untrusted, Unlimited budget, no cache
```

**Pipeline of `UpstreamClient.call`** (the layering is fixed; the budget runs inside the cache loader, so a hit costs nothing and N coalesced callers cost one permit):

1. Build the request: `baseUrl` + path + query, default headers plus the call's headers. A violation recorded by `CallSpec` returns `TransportFailure.InvalidRequest` now, before anything is spent.
2. For a `GET`, `cache.getOrLoad(CacheKey<T>(endpoint.id, canonicalParams), CachePolicy(ttl, missTtl), maxAge) { loader }`, where the canonical parameters are the encoded path as a reserved first entry, then the query pairs sorted by name (stable). Headers, body, lane, `maxAge` and credentials are never in the key. A non-`GET` skips the cache and goes straight to the loader.
3. Loader: `budget.withPermit(cost, lane, maxWait) { permit -> transport.execute(request) ... }`. `BudgetExhausted` and `Closed` return unchanged and send nothing. If the transport fails before a request was sent (`RefusedByPolicy`, `InvalidRequest`, `Closed`) the permit is released (refunded); any other transport failure completes the permit as spent with no signals.
4. On a response: `signals = rateSignals.read(response, wallClock.nowEpochMillis())`, then `permit.complete(signals)` (runs in `NonCancellable` inside the budget).
5. Classify, in this order: `signals.throttled` or status `429` gives `UpstreamFailure.RateLimited(status, retryAfter = max(signals.retryAfter, budget.utilisation().pausedFor))` and returns it with no retry; status `2xx` gives the decode function inside a catch that wraps a thrown or plain-`Throwable` failure in `DecodeFailure` and passes an `HttpFailure` through; status in `missStatuses` gives `Lookup.Missing`; anything else gives `UpstreamError(status, headers, body)`. Failures are never stored (the cache's rule); `Missing` is stored for `missTtl`.
6. A coalesced caller with a tighter deadline wraps its own call in `withTimeoutOrNull`; the load survives its cancellation. The load's lane and `maxWait` are the first caller's. No deadline parameter ships in 1.0.0; adding one to `CallSpec` is additive.

**`UrlFetcher.fetch`** runs the policy's per-hop URL check first, so `RefusedByPolicy` costs nothing and reaches neither budget nor transport, then budget (default `CallBudget.Unlimited`), transport, signals and `complete`. Any status is a success value except a throttle, which is `RateLimited` because the budget has already paused. A `304` is a response, so the caller's conditional GET reads it directly.

### Lifecycle

- Every long-lived type (`HttpTransport`, `ResponseCache`, `CallBudget`, `UpstreamClient`, `UrlFetcher`, `FixtureRecorder`-wrapped transports) is a `kotlin.AutoCloseable`. `close()` is non-suspend, idempotent, never throws, and does no unbounded blocking. After `close()` every call returns `HttpFailure.Closed`, and in-flight work completes with it.
- `CallBudget` gains `suspend fun flush(): Result<Unit>` (default body: success) for an exact final persist. `close()` fails waiters with `Closed` and marks the budget closed.
- Ownership: a component the client built is closed by `close()`; one handed to it is the caller's to close. The closing order is cache, budget, transport.

### Logging

- Each module that logs from common code has an `internal` interface `Log` with `debug`, `info` and `warn` taking a `{}`-placeholder message and arguments, obtained through `internal expect fun logger(name: String): Log`. `jvmMain` provides the slf4j actual; a check-only or published `js` target needs a no-op actual. JVM code (transport, cache, guards) uses the same facade so there is one style and one test-capture path (logback in `jvmTest`). `webtools-feeds` keeps its own copy of the six-line facade (an `internal` declaration is not visible across modules) or, if the feeds module is JVM-only, calls slf4j directly.
- `BudgetObserver` remains the Experimental consumer hook; the budget's single event-emission point both logs and forwards to the observer.
- Levels. The site that creates a failure or event logs it once; layers that pass it through do not.

| Level | What |
|---|---|
| `INFO` | Component created or closed (settings numbers only), budget restored from a store |
| `WARN` | Budget pause started (until, source), hard stop reached (once per window), store failure, policy refusal (reason name and range class, `Url.redacted()`), `RetryRefused`, upstream throttle |
| `DEBUG` | One line per call: endpoint id, cache hit, miss or joined, lane, cost, waited ms, status, bytes, elapsed ms; per redirect hop; a budget refusal |
| never | Headers, bodies, query strings, credentials, resolved addresses, cache-key values, OkHttp objects |

### Per-type tier table

"Experimental" means implementing, delegating to or subclassing needs the marker; calling and passing the type are Stable Core. Everything not listed is `internal`.

| Type | Tier |
|---|---|
| `ExperimentalForInheritanceWebToolsApi` | Stable Core (never removed) |
| `HttpTransport`, `ResponseCache`, `CallBudget`, `Permit`, `WallClock`, `RateSignalsReader`, `BudgetStore`, `BudgetObserver` | Experimental (implement) |
| `HttpFailure`, `TransportFailure`, `UpstreamFailure` | Experimental (subclass) |
| `HttpFailure.Closed/BudgetExhausted/DecodeFailure`, every `TransportFailure.*` and `UpstreamFailure.*` leaf | Stable Core |
| `HttpMethod`, `Url`, `HttpHeaders` (+ `Entry`, `Builder`), `RequestBody`, `HttpRequest`, `HttpResponse` (+ builders) | Stable Core |
| `TransportSettings`, `OutboundUrlPolicy` (+ `Trusted`, `Untrusted`), `MixedAnswers`, `CidrBlock`, `RefusalReason` | Stable Core |
| `CacheKey`, `CachePolicy`, `Lookup` (+ `Found`, `Missing`), `CacheStats`, `ResponseCache.Disabled` | Stable Core |
| `Lane`, `RateWindow`, `RateSignals`, `WindowReading`, `RetryAfter`, `BudgetSnapshot`, `WindowSnapshot`, `BudgetEvent` (+ leaves), `BudgetUtilisation`, `WindowUtilisation`, `ExhaustedReason`, `withPermit` | Stable Core |
| `WallClock.System`, `RateSignalsReader.Standard`, `CallBudget.Unlimited`, `BudgetObserver.None` | Stable Core |
| `Endpoint`, `CallSpec`, `FetchSpec`, `UpstreamClient`, `UrlFetcher`, their configs | Stable Core |
| `OkHttpTransport`, `CaffeineResponseCache` (`jvmMain`), `WindowedCallBudget`, `InMemoryBudgetStore` | Stable Core (supplied defaults, usable without opt-in) |
| Fixtures: `VirtualClock`, `FakeHttpTransport`, `RequestMatch` (a `fun interface`; a frozen single-method predicate), `Step`, `Fixture`, `FixtureLoader`, `LoopbackTransports`, `RecordingGate`, `Redaction`, `FixtureRecorder`, `RecordingBudgetObserver`, `FakeBudgetStore`, error types | Stable Core, source-compatibility only |
| Feeds: the parser port | Experimental (implement); feed model and fetch function Stable Core |

### Signatures in the sibling findings that this surface replaces

| Shape in this surface | Shape in the sibling findings | Why |
|---|---|---|
| `WallClock`, `BudgetObserver` are plain interfaces | `fun interface` | A `fun interface` cannot be gated (F1). |
| `RateSignals`, `WindowReading`, `RetryAfter`, `BudgetSnapshot`, `WindowSnapshot`, `InMemoryBudgetStore` carry no marker; the interfaces `RateSignalsReader` and `BudgetStore` are subclass-gated | Marked directly | Direct marking contaminates `Permit.complete` and the budget constructor (F2). |
| `ExhaustedReason`, `RefusalReason` are constants classes | Enums | Library-produced values (F5). |
| `Closed` is one leaf | `BudgetClosed` and `Closed` | One handling site for the consumer. |
| `CallBudget.close()` is non-suspend, plus `flush()` | `suspend fun close()` | One lifecycle convention (F11). |
| `withPermit` is a plain `suspend fun` | `suspend inline` | F7. |
| `Permit.complete()` and `complete(signals)` overloads | `complete(signals = RateSignals.None)` | No defaults on public API. |
| `getOrLoad(key, policy, maxAge: Duration?, loader)` with no default | `maxAge: Duration? = null` | Same. |
| `Lookup.Found(value)` and `Found(value, weight)` | `weight: Int = 1` | Same. |
| `TransportSettings`, `CachePolicy`, policies, `CaffeineResponseCache`, `WindowedCallBudget`, `OkHttpTransport` built by factory plus builder | Constructors with defaulted parameters; a `BudgetPolicy` class | Pattern A (F7); Consideration 1. |
| `WindowedCallBudget` and the cache live in the flat package | `…http.budget` | F13. |
| Failure leaves nested in `TransportFailure` or `UpstreamFailure` or `HttpFailure` | `HttpFailure.RateLimited`, flat `TransportFailure.BodyTooLarge` | Grouping by what a consumer can act on. |

## Considerations

### 1. Configurable classes: builder-lambda factories or flat constructors with hidden overloads?

- **A (recommended).** Factory function plus builder; internal constructor; no defaults on public API (F7). Additive by construction and binary-safe for Kotlin callers; idiomatic (`Json { }`, `HttpClient { }`); policy numbers are builder properties with defaults, in one place per class. Cost: the letter of "constructor parameters with defaults" becomes "builder properties with defaults"; one builder class per configurable type; call sites use a lambda rather than named arguments.
- **B.** Keep flat public constructors with defaulted parameters (the literal requirement) and, on every release that adds a parameter, keep the previous constructor as `@Deprecated(level = HIDDEN)`, appending new parameters at the end. Cost: a permanent overload per addition, kept correct by hand and detected only by the ABI check; `@IntroducedAt` would replace this from Kotlin 2.3, which this repository does not use.
- Recommendation: A. The two differ only in syntax at the call site; A removes a class of silent break. The decision changes every signature in the table above, so it needs settling before the sub-issues are planned.

### 2. Gate or SAM for `WallClock` and `BudgetObserver`?

- **A (recommended).** Plain interfaces with the gate: implementing is Experimental as required; consumers lose `WallClock { ... }`. Supplied: `WallClock.System`, the fixtures' `VirtualClock.wallClock`.
- **B.** Keep them `fun interface`, Stable Core and ungated, on the argument that a one-method clock or event sink is frozen by design. Consumers keep lambdas. This deviates from "every seam a consumer implements is Experimental".
- Recommendation: A, to keep one rule.

### 3. One facade class or two?

- **A (recommended).** `UpstreamClient` (endpoints, cache, base URL) and `UrlFetcher` (raw, policy pre-check, no cache). The types encode the invariant: an untrusted URL cannot reach `call`, and `fetch` cannot be handed an endpoint's cache.
- **B.** One class with an optional base URL and both methods. Fewer public types; `call` on a client without a base URL fails at runtime.
- Recommendation: A.

### 4. Decode with the endpoint or with the call?

- **A (recommended).** With the endpoint (F10). BotTools' adapter builds one WebTools `Endpoint<T>` per its own endpoint and keeps it.
- **B.** With each call, so one endpoint object serves callers with different decoders. The cache key cannot see the decoder, so inconsistent decoders corrupt shared entries; the contract becomes "pass the same decoder every time".
- Recommendation: A.

### 5. Raw `fetch` and `429`

- **A (recommended).** `fetch` returns `RateLimited` for a throttle (the budget has already paused) and a response for every other status.
- **B.** Return every status, `429` included, as a response, and leave interpretation to the caller.
- Recommendation: A. The caller and the budget agree on the pause, and a conditional-GET poller does not need to parse `Retry-After` itself.

### 6. Config-time validation throws

Builders and constants (`Lane`, header names) throw `IllegalArgumentException` for a developer-written value that is invalid. The alternative is `Result` everywhere, including builder `build()` and `Lane.of`, at the cost of unusable call syntax for constants. Recommendation: throw at construction for programmer errors, `Result` for text from outside the process (the convention table). This reads "no exceptions across the API" as "no exceptions for expected failures".

## Load-bearing claims

All sources opened 2026-10-21. Versions are as stated.

1. **`@SubclassOptInRequired` is stable on 2.2.0 and limited to open and abstract classes and non-`fun` interfaces.** "Uses of this annotation are limited to open and abstract classes, and non-`fun` interfaces. Any other uses allowed by `CLASS` annotation target yield a compilation error." with `@SinceKotlin("2.1")` and `@WasExperimental(ExperimentalSubclassOptIn::class)`. <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/annotations/OptIn.kt>, tag `v2.2.0`.
2. **Mentioning a marked type in a signature is contagious, including through default arguments.** "Even though call sites do not mention `Unstable` type directly, the corresponding opt-in warning or error will be triggered in each call site due to propagation contagiousness." (KDoc of `RequiresOptIn`, same file and tag as claim 1.)
3. **Documentation of signature propagation, subclass opt-in and keeping marker classes.** "If the signature of an API element includes a type that requires opt-in, the signature itself must also require opt-in. Otherwise, if an API element doesn't require opt-in, but its signature includes a type that does, using it triggers an error." and "Note that when you use the `@SubclassOptInRequired` annotation to require opt-in, the requirement is not propagated to any inner or nested classes." and "you should leave the annotation classes in modules so that the existing client code remains compatible." <https://kotlinlang.org/docs/opt-in-requirements.html>, Kotlin 2.x documentation.
4. **`fun interface` and `sealed` are inapplicable targets (compiler test data).** "// functional interface is not a valid target for @SubclassOptInRequired" followed by `<!SUBCLASS_OPT_IN_INAPPLICABLE!>@SubclassOptInRequired(ApiMarker::class)<!>` on `fun interface FunctionalInterface { fun func() }`; and "// sealed classifiers are not valid targets for @SubclassOptInRequired" on `sealed class SealedKlass` and `sealed interface SealedInterface`. <https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/testData/diagnostics/testsWithStdLib/annotations/subclassOptInRequired/IncorrectApplicability.kt>, tag `v2.2.0`.
5. **Mentioning a gated interface is free; `@OptIn` on a subtype does not propagate.** "o: InterfaceOptInApi,           // usage opt-in isn't required"; "// opt-in present: no diagnostic, opt-in isn't propagated" / "@OptIn(ApiMarker::class)" / "interface InterfaceOptInApiInheritorB: InterfaceOptInApi"; "interface InterfaceOptInApiInheritorE: InterfaceOptInApiInheritorB // inheritance opt-in isn't propagated". <https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/testData/diagnostics/testsWithStdLib/annotations/subclassOptInRequired/WithInterfaces.kt>, tag `v2.2.0`.
6. **Delegation counts as inheritance.** "open class InheritingByDelegationA(arg: ToBeInheritedByDelegation): <!OPT_IN_TO_INHERITANCE_ERROR!>ToBeInheritedByDelegation<!> by arg". <https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/testData/diagnostics/testsWithStdLib/annotations/subclassOptInRequired/WithInheritanceByDelegation.kt>, tag `v2.2.0`.
7. **Diagnostic wording.** "This class or interface requires opt-in to be implemented. Its usage must be marked with '@ApiMarker', '@OptIn(ApiMarker::class)' or '@SubclassOptInRequired(ApiMarker::class)'". <https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/testData/diagnostics/testsWithStdLib/annotations/subclassOptInRequired/DiagnosticDefaultMessage_error.kt>, tag `v2.2.0`.
8. **Kotlin library guidelines on data classes, added defaults and `@JvmOverloads`.** "Avoid using data classes in your API"; "Adding non-default arguments to a public API breaks both binary and source compatibility ... However, even adding default arguments can break compatibility."; "The `@JvmOverloads` annotation doesn't preserve binary compatibility for Kotlin callers. Instead, use the `@IntroducedAt` annotation or add manual overloads when changing published APIs."; "You can also create overloads manually instead of using a single function with default arguments."; "Kotlin allows inline functions to be a part of your library's API ... these functions are not allowed to call non-public-API declarations." <https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html>.
9. **`kotlinx.serialization` v1.9.0 gates through `@Target()` and `@SubclassOptInRequired` in common code.** "@Target() // no direct targets, only argument to @SubclassOptInRequired" with `level = RequiresOptIn.Level.ERROR` on `SealedSerializationApi`: <https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/core/commonMain/src/kotlinx/serialization/ApiLevels.kt>; `@SubclassOptInRequired(SealedSerializationApi::class)` on `SerialDescriptor`: <https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/core/commonMain/src/kotlinx/serialization/descriptors/SerialDescriptor.kt>. Also `Json`: "public fun Json(from: Json = Json.Default, builderAction: JsonBuilder.() -> Unit): Json" and "public class JsonBuilder internal constructor(json: Json)": <https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/formats/json/commonMain/src/kotlinx/serialization/json/Json.kt>.
10. **`@IntroducedAt` is Kotlin 2.3+ and experimental.** "@SinceKotlin("2.3")" / "@ExperimentalVersionOverloading" / "public annotation class IntroducedAt(val version: String)" at <https://github.com/JetBrains/kotlin/blob/v2.3.20/libraries/stdlib/src/kotlin/annotations/VersionOverloads.kt>, tag `v2.3.20`; the file does not exist at tags `v2.2.0` and `v2.3.0` (HTTP 404 from the contents API). `ExperimentalVersionOverloading` is `@RequiresOptIn(level = RequiresOptIn.Level.ERROR)`.
11. **Exhaustiveness in the specification.** "A when expression is called exhaustive if at least one of the following is true: ... The bound expression is of a `sealed` class or interface `S` and all of its *direct non-sealed subtypes* are covered in this expression" and "If when expression is not exhaustive, it has type `kotlin.Unit` and may be used only as a statement." <https://kotlinlang.org/spec/expressions.html> (living specification).
12. **Non-exhaustive `when` statements over sealed types are an error since Kotlin 1.7.** "Kotlin 1.7 will report an error about the `when` statement with an enum, sealed, or Boolean subject being non-exhaustive". <https://kotlinlang.org/docs/compatibility-guide-17.html>.
13. **A sealed type that gained a subtype makes an already-compiled `when` throw.** Module `m1` has `sealed interface S` / `class K : S`; module `m12` has the same plus `class K2 : S`; module `m2` (compiled against `m1`) has `fun test(s: S): String { return when (s) { is K -> "" } }`; `box` runs `test(K2())` against `m12` and expects an exception (`catch (e: Exception)`, "exception was expected"). <https://github.com/JetBrains/kotlin/blob/b8568ead75312ef4b2bbe0178aab3b36c6abfef6/compiler/testData/codegen/box/when/NoWhenBranchMatchedException.kt>, commit `b8568ea` (default branch, 2026-07-29); the runtime class is `public actual open class NoWhenBranchMatchedException : RuntimeException` in <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/jvm/runtime/kotlin/NoWhenBranchMatchedException.kt>.
14. **Stack-trace recovery skips exceptions with extra fields.** "// Skip reflective copy if an exception has additional fields (that are typically populated in user-defined constructors)" / "if (throwableFields != clz.fieldsCountOrDefault(0)) return nullResult". <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/internal/ExceptionsConstructor.kt>, tag `1.10.2`.
15. **`Result` is a value class over any `Throwable`.** "A discriminated union that encapsulates a successful outcome with a value of type [T] or a failure with an arbitrary [Throwable] exception." and "@JvmInline / public value class Result<out T>". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/util/Result.kt>, tag `v2.2.0`.
16. **Kotlin 2.2.0: experimental ABI validation in KGP; interface bodies compile to JVM defaults.** "we're experimenting with moving the functionality of the binary compatibility validator into the Kotlin Gradle plugin (KGP). You can try it out in toy projects, but we don't recommend using it in production yet." and `abiValidation { enabled.set(true) }`, tasks `checkLegacyAbi`, `updateLegacyAbi`; "Starting from Kotlin 2.2.0, functions declared in interfaces are compiled to JVM default methods unless configured otherwise." and the `enable` mode "generates default implementations in interfaces and includes bridge functions in subclasses and `DefaultImpls` classes." <https://kotlinlang.org/docs/whatsnew22.html>. Inline-class mangling: "functions using inline classes are mangled by adding some stable hashcode to the function name" <https://kotlinlang.org/docs/inline-classes.html>.
17. **Common `Exception` has only `(message, cause)`-style constructors.** `public expect open class Exception : Throwable { public constructor() ... public constructor(message: String?) ... public constructor(message: String?, cause: Throwable?) ... public constructor(cause: Throwable?) }`. <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/common/src/kotlin/ExceptionsH.kt>, tag `v2.2.0`.
18. **`Result` is not meant for domain errors (the dissent).** "The `Result` class is not designed to represent domain-specific error conditions." <https://github.com/Kotlin/KEEP/blob/main/proposals/stdlib/KEEP-0127-result.md>, read 2026-10-21.
19. **`CancellationException` is normal cancellation.** "Thrown by cancellable suspending functions if the [Job] of the coroutine is cancelled while it is suspending. It indicates _normal_ cancellation of a coroutine." <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-cancellation-exception/>. It is declared "public expect open class CancellationException : IllegalStateException {" in <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/coroutines/cancellation/CancellationExceptionH.kt>, tag `v2.2.0`.
20. **Detekt on `runCatching` and suspend functions.** "`suspend` functions should not be called inside `runCatching`'s lambda block" (`SuspendFunSwallowedCancellation`). <https://detekt.dev/docs/rules/coroutines/>.
21. **Arrow's non-fatal rule.** "This will also not match [CancellationException] since that's a fatal exception in Kotlin for cancellation purposes." <https://github.com/arrow-kt/arrow/blob/2.2.3/arrow-libs/core/arrow-exception-utils/src/commonMain/kotlin/arrow/core/NonFatal.kt>, tag `2.2.3`.
22. **`kotlinx.coroutines` 1.10.2 gate and dump.** `@Target(AnnotationTarget.CLASS)` / `@RequiresOptIn(level = RequiresOptIn.Level.WARNING, message = "Inheriting from this kotlinx.coroutines API is unstable. ...` / `public annotation class ExperimentalForInheritanceCoroutinesApi` in <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Annotations.kt>; the dump has `public abstract interface annotation class kotlinx/coroutines/ExperimentalForInheritanceCoroutinesApi : java/lang/annotation/Annotation {` and no `SubclassOptInRequired` text at all: <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/api/kotlinx-coroutines-core.api>.
23. **Apollo Kotlin 4.4.1's sealed exception root.** "sealed class ApolloException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)" with leaves including `@Deprecated("ApolloParseException was only used for I/O exceptions and is now mapped to ApolloNetworkException.")` and `@ApolloExperimental data object ApolloWebSocketForceCloseException`. <https://github.com/apollographql/apollo-kotlin/blob/v4.4.1/libraries/apollo-api/src/commonMain/kotlin/com/apollographql/apollo/exception/Exceptions.kt>, tag `v4.4.1`.
24. **Ktor 3.6.0.** `) : IllegalStateException("Bad response: $response. Text: \"$cachedResponseText\"")` (on `public open class ResponseException(`) and `override val message: String = "Server error(${response.call.request.method.value} ${response.call.request.url}: " +` followed by `"${response.status}. Text: \"$cachedResponseText\""` in <https://github.com/ktorio/ktor/blob/d96280165fc9890217196ed0dffd45228e35987d/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/DefaultResponseValidation.kt>; `): HttpClient = HttpClient(engine, HttpClientConfig<HttpClientEngineConfig>().apply(block), manageEngine = false)` (on `public fun HttpClient(engine: HttpClientEngine, block: HttpClientConfig<*>.() -> Unit`) and `) : CoroutineScope, Closeable {` (on `public class HttpClient(`) in <https://github.com/ktorio/ktor/blob/d96280165fc9890217196ed0dffd45228e35987d/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt>. Commit `d962801` (3.6.0).
25. **Other comparable shapes.** Retrofit 3.0.0: `return "HTTP " + response.code() + " " + response.message();` <https://github.com/square/retrofit/blob/3.0.0/retrofit/src/main/java/retrofit2/HttpException.java>. Kord 0.18.1: `public abstract class RestRequestException(` with `public val status: HttpStatus,` and `public class KtorRequestException(` with `public val httpResponse: HttpResponse,` <https://github.com/kordlib/kord/blob/0.18.1/rest/src/commonMain/kotlin/request/RestRequestException.kt>. Coil (`ccd6696`): `sealed interface ImageResult {`, `@Poko` / `class ErrorResult(` with `val throwable: Throwable,` and a hand-written `fun copy(`; `class Builder {` in `coil-core/src/commonMain/kotlin/coil3/ImageLoader.kt`; Apollo `v4.4.1`: `class Builder : MutableExecutionOptions<Builder> {` in `libraries/apollo-runtime/src/commonMain/kotlin/com/apollographql/apollo/ApolloClient.kt` <https://github.com/coil-kt/coil/blob/ccd6696ee0bd2583d39022b2ee397dd9622d31c9/coil-core/src/commonMain/kotlin/coil3/request/ImageResult.kt>. Inline-class boxing: "As a rule of thumb, inline classes are boxed whenever they are used as another type." <https://kotlinlang.org/docs/inline-classes.html>. `AutoCloseable.use`: "expect inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R", "Since Kotlin 2.0", platform tags Common, JS, JVM, Native, Wasm-JS, Wasm-WASI: <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/use.html>.
26. **ABI tooling.** KGP `v2.2.0`: `@RequiresOptIn("The ABI Validation DSL is experimental and may be changed or removed in any future release.", RequiresOptIn.Level.ERROR) ... annotation class ExperimentalAbiValidation`, and an `AbiValidationKlibKindExtension` with `val enabled: Property<Boolean>`: <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/abi/ExperimentalAbiValidation.kt> and `.../AbiValidationMultiplatformExtension.kt`. BCV 0.18.2 (2026-09-02): "This plugin is in maintenance mode."; "Kotlin version `1.6.20` or newer."; "The tool allows dumping binary API of a JVM part of a Kotlin library"; "The KLib validation support is experimental and is a subject to change" <https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/README.md>; `override fun apply(target: Project): Unit = with(target) { ... allprojects { configureProject(it, extension) }` and a multiplatform branch registering `apiDump`/`apiCheck` in <https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/src/main/kotlin/BinaryCompatibilityValidatorPlugin.kt>. The 2.4.21 DSL change (`abiValidation()`, `enabled` removed) is in SpartanGraphics' research `docs/plans/1-phase-1-spartangraphics/3-g1-build-skeleton/research/abi-validation-across-modules.md`, which read it at tag `v2.4.21`; I re-read only the `v2.2.0` side.

## Gaps

1. **Nothing here was compiled.** No compiler was available to this research. The opt-in behaviours (F1, F2) come from the compiler's own test data and stdlib KDoc at `v2.2.0`, which is strong evidence but not a build of this module. The earlier measurement (`test-support-and-time.md:54`) covers Kotlin 2.2.0 JVM with three Gradle subprojects, not common code. Before the first sub-issue, compile a probe with the marker, a gated interface, a default that mentions it in a constructor default and a consumer without opt-in, in `commonMain` and `jvmMain`.
2. **Overload resolution of a same-named class and factory function for a generic `Endpoint<T>`** (an `internal` constructor plus `fun <T : Any> Endpoint(id, decode, configure)`) is argued from Ktor's and `kotlinx.serialization`'s non-generic precedents, not tested. The companion-`invoke` alternative is not recommended for the generic case because explicit type arguments on a class name resolve against the class.
3. **Non-suspend `close()` on the Mutex-based budget** (completing waiters without suspending, and the exact-persist move to `flush()`) is not designed or probed; it changes the budget sub-issue's `close` contract.
4. **A compile-fail check for the gate does not exist.** Dumps do not record `@SubclassOptInRequired` (F8). Options are a TestKit-style build check that compiles a consumer snippet with and without the opt-in, or review only. Not evaluated.
5. **BCV 0.18.2 against KGP 2.2.0 and Gradle 9.7.1, and its multiplatform `jvm()`-only dump layout, were not run.** The plugin source and README were read; the build-topology research measured KGP 2.2.0 on Gradle 9.7.1 without ABI tooling. The published target set (build-topology Consideration 1) decides whether a klib dump is also needed.
6. **The `internal expect fun` logging seam on a check-only or published `js` target** needs a no-op actual, which was measured only as "no Beta warning on KGP 2.2.0" in the build-topology research. How `webtools-feeds` obtains its logger depends on whether it is multiplatform (feed-parsing question, not settled here).
7. **Fixtures held to source compatibility only** is my judgement, not a source; if a consumer ever publishes test jars compiled against the fixtures, binary compatibility would matter there.
8. **`RateLimited.retryAfter` takes the larger of the reader's delay and `budget.utilisation().pausedFor`.** Whether `pausedFor` already includes the reader's value after `complete` was not checked against the budget design; if it does, the `max` is redundant.
9. **A per-call deadline for a coalesced joiner** is left to `withTimeoutOrNull` in the caller. No parameter was designed; a `CallSpec.deadline` returning a new failure leaf would be additive.
10. **The 200-with-null "confirmed miss"** (a decoder reporting emptiness) is not expressible: `Endpoint` declares misses by status only. A `missWhen` predicate on the builder is the additive path if BotTools needs it.
11. **Kord's and Coil's gating markers** (`@KordUnsafe`, `@Poko`) were not re-read at their tags; the table rows for those two cover failure and construction shapes only.
12. **Apollo's history.** I read the `v4.4.1` sealed hierarchy and its deprecated and experimental leaves; I did not trace which leaves were added in which minor release.
