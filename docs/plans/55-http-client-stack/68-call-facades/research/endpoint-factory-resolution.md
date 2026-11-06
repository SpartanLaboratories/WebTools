# Research: building `Endpoint<T>` so a consumer's call resolves to the public factory and infers `T` from the decoder

Question 1 of [`README.md`](README.md) in this folder
([#68](https://github.com/SpartanLaboratories/WebTools/issues/68), unit 9 of
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55)). Probe run on 2026-11-06 (UTC).

## 1. Question

How should the generic, final `Endpoint<T : Any>` of `webtools-http`'s `commonMain` (package
`com.spartanlabs.webtools.http`) be constructed so that, on Kotlin 2.2.0 in common code (compiled for the JVM and
for `js`):

- a consumer's `Endpoint("id", decode)`, `Endpoint("id", decode) { ttl = … }`, `Endpoint("id", ::decodeHero)` and
  `Endpoint<Hero>("id", decode)` resolve to the public factory, with `T` inferred from the decoder;
- the module's own test compilations (which see `internal`) resolve the same calls to the same factory;
- a call naming the `internal` constructor's arguments from another module does not compile;
- the only defaulted public parameter is the trailing `configure = {}`; the builder carries `@WebToolsDsl`; adding a
  builder property later breaks no compiled consumer; the decoder is fixed at construction and is a plain
  `(HttpResponse) -> Result<T>`.

## 2. Bottom line

- **Use the module's existing pair, made generic:** `public class Endpoint<T : Any> internal constructor(…)`, a
  non-generic nested `@WebToolsDsl public class Builder internal constructor()`, and a top-level
  `public fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T>`.
  Every consumer call in the question's list compiled and bound to the factory, in a separate consumer module and in
  the library's own associated test compilation (measured, §4).
- **`T` infers from the decoder** whether it is a typed lambda, an untyped lambda (the parameter type is known from the
  factory), a callable reference, an expected type, or `runCatching { … }`. `@BuilderInference` is not needed. Explicit
  `Endpoint<Hero>("id", decode)` works. `T : Any` rejects a nullable `T` at the call site.
- **The decisive measurement:** an invisible (`internal`) constructor never wins against the visible factory. Even
  when the constructor and the factory both accept the consumer's argument list (shape A2), a consumer binds the
  factory; the compiler ranks an invisible candidate below every applicable one. The one trap is *inside* the
  module, which sees both: there the same-shaped constructor wins, so the constructor's parameter list must not be
  callable the way a consumer calls the factory. The recommended constructor takes the id, the decoder and the
  settings, never just two of them.
- **The companion `operator fun invoke` alternative also compiles and infers**, including `Endpoint<Hero>(…)`
  (explicit type arguments are routed to `invoke`), so it is not ruled out by resolution. It is still worse here: it
  adds a `Companion` field and class to the ABI, breaks the module-wide uniform shape, and gains nothing. A public
  constructor with a trailing `configure = {}` also compiles and gives the clearest diagnostics, but it makes the
  constructor's parameter list public ABI, so adding a constructor parameter later breaks compiled callers; reject it.
- **A cost every shape except the public constructor shares:** a miscall (missing decoder, an extra argument) is
  reported against the `internal` constructor ("Cannot access … it is internal"), not against the factory. This is
  already the case for the module's non-generic pairs; state it in the factory's KDoc rather than try to remove it.
- **The earlier objection to `invoke` did not hold:** the claim that explicit type arguments on a class name resolve
  against the class and so rule out the companion form (`../../research/api-shape-and-failures.md`, Gap 2) is not
  what Kotlin 2.2.0 does (F4, F5).

## 3. Findings

Tiers: **measured** = reproduced by the probe in §4 on Kotlin 2.2.0 (no link needed; commands there);
**documented** = a specification, compiler source or official documentation, linked; **practice** = what mature
libraries visibly do.

**F1 (measured). The recommended shape A compiles for the JVM and for `js`, and every listed call binds to the
factory.** From a separate consumer module (JVM): `Endpoint("id", decode)`, `Endpoint("id", decode) { ttl = 5.seconds }`,
`Endpoint("id", ::decodeHero)`, `Endpoint("id", ::decodeHero) { name = "x" }`, `Endpoint<Hero>("id", decode)`,
`Endpoint("id", { r: HttpResponse -> … })`, `Endpoint<Hero>("id", { r -> … }) { name = "n" }`,
`Endpoint("id", decode = ::decodeHero, configure = { name = "x" })`, `Endpoint("id", { r: HttpResponse -> runCatching { Hero(r.body) } })`
and `val l: Endpoint<Hero> = Endpoint("id", { r -> runCatching { Hero(r.body) } })` all compile with no
warning. `javap -c` of the consumer shows each as `invokestatic probe/http/EndpointKt.Endpoint` or
`EndpointKt.Endpoint$default` (the call without a lambda), never `Endpoint.<init>`. From the library's own associated
test compilation the same calls compile and bind the same way; the one call with the internal constructor's four
arguments, `Endpoint("id", decode, "n", 5.seconds)`, binds `Endpoint.<init>`. `:lib:compileKotlinJs` (a `js()` target
with no environment) compiles the shape.

**F2 (measured). Inference of `T`.** `T` is fixed by the `decode` parameter alone. An untyped lambda
`Endpoint("id", { r -> Result.success(Hero(r.body)) })` compiles, because the parameter type
`(HttpResponse) -> Result<T>` supplies `r`. A callable reference `::decodeHero` for
`fun decodeHero(r: HttpResponse): Result<Hero>` fixes `T = Hero`. `Endpoint<Hero>("id", decode)` with an explicit
argument compiles. A decoder returning `Result<Hero?>` does not:
`Return type mismatch: expected 'Result<uninferred T (of fun <T : Any> Endpoint)>', actual 'Result<Hero?>'`.
No `@BuilderInference` is involved.

**F3 (measured, with a documented cause). An invisible constructor loses to the visible factory.** Shape A2 gives the
internal constructor the *same* two-argument shape a consumer's call has: `internal constructor(id, decode)` beside
the factory `Endpoint(id, decode, configure = {})`. From the consumer module, `Endpoint("id", decode)` compiled and
bound `EndpointA2Kt.Endpoint$default` (the factory); no "Cannot access" error. Kotlin's compiler source ranks
`K2_VISIBILITY_ERROR` ("Candidate isn't visible. Provokes INVISIBLE_REFERENCE.") below every successful
applicability, which is the documented reason (Load-bearing claim 2). Inside the library, though, the same call
`Endpoint("id", decode)` (in its own test, and in the factory's body) bound `Endpoint.<init>`, not the factory:
when both candidates are visible and applicable, the candidate that uses no default argument is the more specific
(Load-bearing claim 3), and the constructor uses none while the factory uses its default. A module test that meant to
exercise the factory with the consumer's argument list would silently test the constructor.

**F4 (measured, documented). Constructors and top-level functions of the same name compete in one tier; explicit type
arguments filter by arity.** The specification lists "A constructor of a type named XXX" and "A function named XXX"
as the same kind of callable and puts top-level functions in one tier by package/import origin; explicit type
arguments filter the candidate sets to callables with the same number of formal type parameters before selection
(Load-bearing claims 4 and 5). In the probe `Endpoint<Hero>("id", decode)` reached the factory (one type parameter on
the constructor and one on the factory both survive the filter; the constructor then loses on arity and visibility).

**F5 (measured, documented). A companion `invoke` also works with explicit type arguments.** Shape B puts
`public operator fun <T : Any> invoke(id, decode, configure = {})` in `public companion object`. Every call of F1
compiled from the consumer module and bound `probe/b/Endpoint$Companion.invoke` / `invoke$default`, including
`Endpoint<Hero>("id", decode)` and `Endpoint<Hero>("id", { r -> … }) { name = "n" }`. The specification states that for
a property-like callable "type parameters must be present at the `invoke` operator function declaration instead"
(claim 5) and places a companion's `invoke` in the lowest-priority set of its parts (claim 6), which agrees. Cost,
measured in the ABI dump: a `public static final field Companion`, a `probe/b/Endpoint$Companion` class and its
`invoke$default` synthetic.

**F6 (measured). A public constructor with the trailing lambda (shape C) compiles and resolves every call**, with
the clearest diagnostics (`No value passed for parameter 'decode'.`, `Argument type mismatch: actual type is 'String',
but 'Function1<Endpoint.Builder, Unit>' was expected.`). Its ABI exposes the constructor
`<init>(String, Function1, Function1)` and the synthetic `<init>(…, int, DefaultConstructorMarker)`, so any later
constructor parameter changes a public JVM signature. The Kotlin guidelines state the same hazard for any added
parameter (claim 7).

**F7 (measured). A miscall is reported against the internal constructor.** Consumer-side, in shapes A, A3 and B:
`Endpoint("id")` gives `Cannot infer type for this parameter. Specify it explicitly.` and
`Cannot access 'constructor<T : Any>(id: String, decode: (HttpResponse) -> Result<T>, name: String?, ttl: Duration?): Endpoint<T>': it is internal in 'probe/http/Endpoint'.`;
`Endpoint("id", decode, "name")` gives the same "Cannot access" error. A wrong trailing lambda has the same effect:
`Endpoint<Hero>("id") { r -> … }` (a decoder written as the trailing lambda, which `configure` takes) gives
`Cannot access 'constructor<T : Any>(…)…` plus `Cannot infer type for this parameter` and `Unresolved reference 'body'`.
The decoder is therefore never the trailing lambda: it is the second argument, as a function reference or a lambda
in parentheses. Shape C is the only one whose miscall diagnostics name the call's real problem.

**F8 (measured). Builder and annotation behave as for the non-generic pairs.** A non-generic `Builder` nested in the
generic class is named `Endpoint.Builder` (no type argument), can be the receiver of a reusable lambda
`val shared: Endpoint.Builder.() -> Unit = { ttl = 1.seconds; name = "shared" }` and passed as `configure`, and
carries its `@DslMarker` annotation at run time (`RuntimeVisibleAnnotations: probe.http.ProbeDsl` in `javap -v`).
With a marked outer receiver, `Endpoint("id", decode) { outerProp = 2 }` inside `outer { … }` is rejected:
`'var outerProp: Int' cannot be called in this context with an implicit receiver. Use an explicit receiver if necessary.`
`Endpoint.Builder()` from the consumer: `Cannot access 'constructor(): Endpoint.Builder': it is internal in 'probe/http/Endpoint.Builder'.`

**F9 (measured). ABI dump of shape A** (binary-compatibility-validator 0.18.2, `apiDump`): the class has getters and
no `<init>` (the internal constructor is not recorded); the facade class holds exactly
`public static final fun Endpoint (Ljava/lang/String;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)Lprobe/http/Endpoint;`
and `public static synthetic fun Endpoint$default (…;ILjava/lang/Object;)Lprobe/http/Endpoint;`, the same pair as the
committed `CachePolicyKt` (`CachePolicy-VtjQ1oo` and `CachePolicy-VtjQ1oo$default`). The dump records erased
descriptors only: neither `T`, nor its `Any` bound, nor any `out` is visible in it, so `apiCheck` does not pin the
generic contract; the KDoc and the Level 2 tests must. The generic signature is present in the class file
(`public static final <T> probe.http.Endpoint<T> Endpoint(String, Function1<? super HttpResponse, ? extends Result<? extends T>>, Function1<? super Endpoint$Builder, Unit>)`).
The absence of any `<init>` for `Endpoint` in the dump does pin "no public constructor".

**F10 (measured). Declaration-site variance compiles.** `class Endpoint<out T : Any>` with the same
internal-constructor-plus-factory pair compiles and `val any: Endpoint<Any> = a` (from an `Endpoint<Hero>`) is
accepted. `T` appears only in the decoder's result, so `out` is legal. Whether to declare it is a separate API call
(adding `out` later is source-compatible; removing it is not).

**F11 (documented, practice vs. convention). The Kotlin coding conventions discourage a factory named like its
class; mature libraries do it anyway for a configured type.** "If you declare a factory function for a class, avoid
giving it the same name as the class itself … Only if there is really no special semantics, you can use the same name
as the class." (claim 8). Ktor 3.3.0 publishes `public class HttpClient` beside the top-level generic
`public fun <T : HttpClientEngineConfig> HttpClient(engineFactory: HttpClientEngineFactory<T>, block: HttpClientConfig<T>.() -> Unit = {}): HttpClient`
and calls its own `internal constructor(engine, userConfig, manageEngine)` from that factory (claim 9). kotlinx.coroutines
1.10.2 publishes generic same-name factories for its interfaces: `public fun <E> Channel(capacity: Int = RENDEZVOUS): Channel<E>`
(claim 10), used with explicit type arguments `Channel<Int>()`. The module's convention is the Ktor shape.

## 4. The probe

**Environment.** Windows 11; `java -version`: Java 23.0.2 (Oracle, 2025-01-21) as the Gradle launcher; Gradle 9.7.1
(`./gradlew --version`, wrapper copied from this repository); Kotlin Multiplatform Gradle plugin and compiler 2.2.0
(`kotlin("multiplatform") version "2.2.0"`); binary-compatibility-validator plugin 0.18.2 on `:lib` for the dump;
JVM target 17 (`jvmTarget = JVM_17`, not the module's floor; irrelevant to resolution). Repositories:
`gradlePluginPortal()` and `mavenCentral()`. No other dependency than the Kotlin plugin, `kotlin("test")` for the
library's tests and the stdlib. Location: a scratch directory outside the repository; the layout and sources below
reproduce it.

**Layout.** `settings.gradle.kts` includes `:lib` and `:consumer`. `gradle.properties`:
`org.gradle.configuration-cache=false`.

```kotlin
// lib/build.gradle.kts            (the module's shape: explicitApi, jvm + js with no environment, associated tests)
plugins { kotlin("multiplatform"); id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2" }
kotlin {
    explicitApi()
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    js()                                                    // no browser()/nodejs(), as webtools-http-commoncheck
    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
// consumer/build.gradle.kts       (a separate module: no access to lib's internal declarations)
plugins { kotlin("multiplatform") }
kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    sourceSets { commonMain.dependencies { implementation(project(":lib")) } }
}
```

```kotlin
// lib/src/commonMain/kotlin/probe/http/Base.kt
package probe.http
public class HttpResponse(public val status: Int, public val body: String)
@DslMarker @Target(AnnotationTarget.CLASS) public annotation class ProbeDsl

// lib/src/commonMain/kotlin/probe/http/Endpoint.kt        -- shape A (recommended)
package probe.http
import kotlin.time.Duration
public class Endpoint<T : Any> internal constructor(
    public val id: String,
    public val decode: (HttpResponse) -> Result<T>,
    public val name: String?,
    public val ttl: Duration?,
) {
    @ProbeDsl
    public class Builder internal constructor() {
        public var name: String? = null
        public var ttl: Duration? = null
    }
}
public fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T> {
    val b = Endpoint.Builder().apply(configure)
    return Endpoint(id, decode, b.name, b.ttl)
}
```

Variants, each in its own package of `:lib` with the same `Builder` and the same fields:

- **A2** (`probe.a2`): `internal constructor(id, decode)`, `name` and `ttl` as `internal set` properties, the same factory.
- **A3** (`probe.a3`): `internal constructor(id, decode, settings: Settings)` with an internal nested `Settings(name, ttl)`.
- **B** (`probe.b`): the constructor of A, no top-level factory, and
  `public companion object { public operator fun <T : Any> invoke(id: String, decode: (HttpResponse) -> Result<T>, configure: Builder.() -> Unit = {}): Endpoint<T> }`.
- **C** (`probe.c`): `public class Endpoint<T : Any>(public val id: String, public val decode: (HttpResponse) -> Result<T>, configure: Builder.() -> Unit = {})`,
  `name` and `ttl` computed in `init` from `Builder().apply(configure)`.
- **V** (`probe.v`): shape A with `Endpoint<out T : Any>`.

The test source (`lib/src/commonTest/kotlin/probe/http/TestCalls.kt`, and `TestA2.kt` for A2) holds the calls of F1 in
a `@Test`. The consumer (`consumer/src/jvmMain/kotlin/probe/consumer/`) holds the positive calls in `Positive.kt`; each
negative or variant snippet is copied in as the single file `Neg.kt` by a three-line script
(`cp <snippet> …/Neg.kt; ./gradlew --no-daemon :consumer:compileKotlinJvm --console=plain | grep -E "^(e|w): |BUILD"; rm …/Neg.kt`),
because a failing compile reports every error in the file and one run per snippet keeps the diagnostics apart.

**Commands** (each from the probe directory):

```
./gradlew --no-daemon :lib:compileKotlinJvm :lib:compileKotlinJs :lib:compileTestKotlinJvm :lib:jvmTest :consumer:compileKotlinJvm --console=plain
./gradlew --no-daemon :lib:apiDump --console=plain          # lib/api/lib.api
javap -c -p <consumer or lib class file>                    # which call each site bound
javap -p -v lib/build/classes/kotlin/jvm/main/probe/http/Endpoint\$Builder.class   # the DslMarker annotation
```

**Results.**

| Call (consumer module unless noted) | A | A2 | A3 | B (`invoke`) | C (public ctor) |
|---|---|---|---|---|---|
| `Endpoint("id", decode)` | factory | factory | factory | `Companion.invoke$default` | `<init>` (default marker) |
| `Endpoint("id", decode) { ttl = … }` | factory | factory | factory | `Companion.invoke` | `<init>` |
| `Endpoint("id", ::decodeHero)` and with a trailing lambda | factory | factory | factory | `invoke` | `<init>` |
| `Endpoint<Hero>("id", decode)` | factory | factory | factory | `invoke` | `<init>` |
| `Endpoint<Hero>("id", { r -> … }) { name = "n" }` | factory | factory | factory | `invoke` | `<init>` |
| `T` from `runCatching { … }`, from an expected type | compiles | compiles | compiles | compiles | compiles |
| `Endpoint("id")` | "Cannot access constructor … internal" | same | same | same | `No value passed for parameter 'decode'.` |
| `Endpoint("id", decode, "name")` | "Cannot access constructor … internal" | - | same | same | `Argument type mismatch: actual type is 'String', but 'Function1<Endpoint.Builder, Unit>' was expected.` |
| `Endpoint("id", decode, "n", 5.seconds)` (A's internal arity) | `Cannot access 'constructor<T : Any>(id: String, decode: (HttpResponse) -> Result<T>, name: String?, ttl: Duration?): Endpoint<T>': it is internal in 'probe/http/Endpoint'.` | - | - | - | - |
| the same 2-argument call, inside the library (test and factory body) | factory | **constructor** | factory | invoke | `<init>` |
| `Endpoint.Builder()` from the consumer | `Cannot access 'constructor(): Endpoint.Builder': it is internal in 'probe/http/Endpoint.Builder'.` | | | | |

`:lib:compileKotlinJs` succeeded with all five variants present; `:lib:jvmTest` ran green.

**JVM signatures (shape A, `javap -p`; `apiDump`).**

```
public final class probe.http.Endpoint<T> {                       // getters only; internal ctor private + synthetic marker ctor
  private probe.http.Endpoint(String, Function1<? super HttpResponse, ? extends Result<? extends T>>, String, Duration);
  public probe.http.Endpoint(String, Function1, String, Duration, DefaultConstructorMarker);   // synthetic bridge for the internal ctor
}
public final class probe.http.EndpointKt {
  public static final <T> Endpoint<T> Endpoint(String, Function1<? super HttpResponse, ? extends Result<? extends T>>, Function1<? super Endpoint$Builder, Unit>);
  public static Endpoint Endpoint$default(String, Function1, Function1, int, Object);          // synthetic
}
```

The dump line for shape A is `public final class probe/http/Endpoint { public final fun getDecode …; getId …; getName …; getTtl-FghU774 … }`,
`public final class probe/http/Endpoint$Builder { getName; getTtl-FghU774; setName; setTtl-BwNAW2A }` and the
`probe/http/EndpointKt` pair above. Shape B adds `public static final field Companion` and
`probe/b/Endpoint$Companion { invoke; invoke$default }`; shape C adds the two constructors. (The synthetic
`DefaultConstructorMarker` constructor in A appears because `Duration` is an inline class; it is not in the dump.)

## 5. How comparable projects do it

- **Ktor `HttpClient`, 3.3.0** ([`HttpClient.kt`](https://github.com/ktorio/ktor/blob/3.3.0/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt)):
  a class with an `internal constructor(engine, userConfig, manageEngine)`, and top-level
  `public fun <T : HttpClientEngineConfig> HttpClient(engineFactory: HttpClientEngineFactory<T>, block: HttpClientConfig<T>.() -> Unit = {}): HttpClient`
  whose body builds the config with `HttpClientConfig<T>().apply(block)` and calls the internal constructor. The
  closest precedent: generic factory, trailing config lambda defaulting to `{}`, internal constructor. Ktor's
  builder is itself generic (`HttpClientConfig<T>`); the endpoint's is not, which keeps `Endpoint.Builder` nameable
  without arguments (F8).
- **kotlinx.coroutines 1.10.2** ([`Channel.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/channels/Channel.kt),
  `SharedFlow.kt`, `CompletableDeferred.kt`): generic same-name factories (`Channel<E>(…)`, `MutableSharedFlow<T>(…)`,
  `CompletableDeferred<T>(…)`) for interfaces, with explicit type arguments at the call and inference otherwise.
  That is the stdlib-adjacent idiom for "the type argument is sometimes inferred, sometimes written".
- **The module's own pairs**, as built: `CachePolicy`, `RateSignals`, `HttpRequest`, `WindowedCallBudget` (a final
  class with an `internal` constructor, a nested `@WebToolsDsl` builder and a top-level same-name factory whose last
  parameter is `configure: …Builder.() -> Unit = {}`; `CachePolicyKt` holds the factory and its `$default`).
- No project surveyed uses a companion `invoke` as the *public configured-type factory* of a generic class in the
  sources read here; that is absence of evidence, not a finding about the form.

## 6. Pitfalls and regrets

- **Same-shape internal constructor (A2).** Consumers are fine, but the module's own code and tests bind the
  constructor, so a test cannot reach the factory with the consumer's argument list (F3). Keep the constructor's
  parameter list distinct: the id, the decoder and every setting, as `RateSignals` does with three parameters. A
  settings object (A3) is fine; two parameters or the factory's own list is not.
- **Misleading diagnostics (F7).** A consumer who passes the decoder as the trailing lambda
  (`Endpoint<Hero>("id") { r -> … }`) or forgets it is told a constructor is internal, not what is wrong. Mitigate in
  KDoc and in the README example only; do not weaken the constructor's visibility.
- **The ABI dump does not carry `T`, its bound or variance (F9).** A change from `T : Any` to `T`, or a change of
  variance, passes `apiCheck`. The KDoc and Level 2 tests that type a value as `Endpoint<Hero>` and call the factory with
  a decoder of each kind are the guard.
- **A public constructor (C) pins the ABI to the constructor's list (F6)**; a later setting is a binary break for
  Kotlin callers, the exact thing the module's pattern avoids.
- **Companion `invoke` (B)** compiles and infers, but the `Companion` class and field enter the ABI forever, and
  `Endpoint.invoke(…)` / `Endpoint.Companion(…)` become additional spellings.
- **Convention friction (F11).** The Kotlin coding conventions advise against a factory with the class's name; the
  module has already chosen it for its pairs. Keep `Endpoint` consistent with them rather than introduce a lowercase
  `endpoint(…)` for the one generic case.
- **Variance is a one-way door (F10).** If `Endpoint<out T : Any>` is wanted, declare it now; removing `out` later
  breaks source.

## 7. Recommendation for this repository

Shape A. In `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Endpoint.kt`:

```kotlin
public class Endpoint<T : Any> internal constructor(
    public val id: String,
    /** The decoder; fixed at construction, never null. */
    public val decode: (HttpResponse) -> Result<T>,
    /* name, ttl, missTtl, cost, lane, misses: the builder's resolved values, in the order of the builder's properties */
) {
    @WebToolsDsl
    public class Builder internal constructor() {
        /* var name, ttl, missTtl, cost, lane; fun misses(vararg statuses: Int) / (statuses: Set<Int>) */
    }
    /* equals/hashCode/toString as the module's other value types decide */
}

public fun <T : Any> Endpoint(
    id: String,
    decode: (HttpResponse) -> Result<T>,
    configure: Endpoint.Builder.() -> Unit = {},
): Endpoint<T>
```

- **Class header:** final, `Endpoint<T : Any>`; whether to write `out T` is a separate call (F10); if it is wanted,
  write it in 1.0.0. Constructor `internal`, with the id, the decoder and every resolved setting (at least four
  parameters, or the id, the decoder and one internal settings object), never a list a consumer's call could also
  satisfy (F3, A2).
- **Builder:** non-generic, nested, `internal constructor()`, annotated `@WebToolsDsl`; add `Endpoint.Builder` to the
  marker's KDoc list in `WebToolsDsl.kt`. Properties and `misses` added later are binary-safe (F9).
- **Factory:** last parameter `configure: Endpoint.Builder.() -> Unit = {}`, the only default; it validates (blank id,
  negative durations, a `cost` below 1) and builds the instance from the builder's values, the way
  `CachePolicy(ttl, configure)` does. File facade `EndpointKt` holds `Endpoint` and `Endpoint$default`.
- **KDoc to state:** the decoder is the second positional argument, never the trailing lambda; `T` is inferred from
  the decoder and `Endpoint<Hero>("id", decode)` is accepted; `Endpoint("id", ::decodeHero) { ttl = 10.minutes }` is
  the typical call; the decoder is a plain function and `T` is non-null; the Java spelling is
  `EndpointKt.Endpoint(…)`; the factory's `@throws IllegalArgumentException` names the property, not a value; the
  class has no public constructor (a compile error naming "it is internal" is expected when one is tried).
- **Tests:** a Level 2 component test calls the factory with each consumer-style argument list (F1) and a second,
  distinct test constructs through the internal constructor with its full list; the ABI dump is committed and must
  show `EndpointKt.Endpoint` plus `Endpoint$default` and no `Endpoint.<init>`. A compile of the consumer-side
  negative (an `internal`-constructor call from another module) cannot run in the module's own test compilations,
  because they see `internal`; the dump carries that guarantee instead.
- **Rejected:** a companion `invoke` (works, but adds `Companion` to the ABI and breaks uniformity) and a public
  constructor (best diagnostics, but pins the constructor's list). Neither is needed to make resolution or inference
  work.

## 8. Load-bearing claims

Probe results (F1 to F10) are measured on Kotlin 2.2.0 with Gradle 9.7.1 and need no link. The outside-world claims:

1. **Constructors are function-like callables and a companion's `invoke` is property-like.** Source: Kotlin language
   specification, "Callables and `invoke` convention" ([kotlinlang.org/spec/overload-resolution.html](https://kotlinlang.org/spec/overload-resolution.html)),
   page read 2026-11-06. Quote: "Function-like callables: A function named XXX at its declaration site; A constructor
   of a type named XXX at its declaration site;"
2. **An invisible candidate ranks below every applicable candidate.** Source: Kotlin compiler,
   `CandidateApplicability` at tag `v2.2.0` ([`CandidateApplicability.kt`](https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/resolution.common/src/org/jetbrains/kotlin/resolve/calls/tower/CandidateApplicability.kt)).
   Quote: "Candidate isn't visible. Provokes INVISIBLE_REFERENCE." (`K2_VISIBILITY_ERROR`), declared in the enum before
   the "Everything below has isSuccess = true" line; the enum is ordered from least to most applicable.
3. **Among applicable candidates the one with fewer unspecified defaults is more specific.** Source: specification,
   "Choosing the most specific candidate" (same page). Quote: "For each candidate we count the number of default
   parameters not specified in the call (i.e., the number of parameters for which we use the default value). The
   candidate with the least number of non-specified default parameters is a more specific candidate;"
4. **Same-name constructor and function are in one candidate tier.** Source: specification, "Call without an explicit
   receiver" (same page). Quote: "When analyzing these sets, the first set which contains any callable with the
   corresponding name and conforming types is picked for c-level partition, which gives us the resulting overload
   candidate set." (top-level non-extension functions form one tier, by import/package origin; claim 1 puts the
   constructor among the function-like callables.)
5. **Explicit type arguments filter by arity, and for a property-like callable apply to `invoke`.** Source:
   specification, "Explicit type arguments" (same page). Quote: "the overload resolution sets are filtered to only
   contain callables which contain exactly the same number of formal type parameters at declaration site. In case of
   a property-like callable with `invoke`, type parameters must be present at the `invoke` operator function
   declaration instead."
6. **A companion's `invoke` is the lowest-priority candidate.** Source: specification, "Callables and `invoke`
   convention" (same page). Quote: "a property-like callable with an `invoke function` belongs to the lowest priority
   set of its parts."
7. **Adding a parameter, even a defaulted one, breaks binary compatibility; `@JvmOverloads` does not rescue Kotlin
   callers.** Source: Kotlin docs, "Backward compatibility guidelines for library authors"
   ([kotlinlang.org/docs/api-guidelines-backward-compatibility.html](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html)),
   read 2026-11-06. Quote: "However, even adding default arguments can break compatibility." and "The
   `@JvmOverloads` annotation doesn't preserve binary compatibility for Kotlin callers."
8. **The coding conventions discourage a same-named factory unless there is no special semantics.** Source: Kotlin
   docs, "Coding conventions" ([kotlinlang.org/docs/coding-conventions.html](https://kotlinlang.org/docs/coding-conventions.html)),
   read 2026-11-06. Quote: "If you declare a factory function for a class, avoid giving it the same name as the class
   itself. Prefer using a distinct name, making it clear why the behavior of the factory function is special. Only if
   there is really no special semantics, you can use the same name as the class."
9. **Ktor 3.3.0 pairs a class having an internal constructor with a generic same-name factory with a trailing
   `= {}` lambda.** Source: [`HttpClient.kt` at 3.3.0](https://github.com/ktorio/ktor/blob/3.3.0/ktor-client/ktor-client-core/common/src/io/ktor/client/HttpClient.kt).
   Quote: "public fun <T : HttpClientEngineConfig> HttpClient( engineFactory: HttpClientEngineFactory<T>, block:
   HttpClientConfig<T>.() -> Unit = {} ): HttpClient {" and, in `public class HttpClient(…)`, "internal constructor(
   engine: HttpClientEngine, userConfig: HttpClientConfig<out HttpClientEngineConfig>, manageEngine: Boolean )".
   (Line breaks of the source collapsed.)
10. **kotlinx.coroutines 1.10.2 uses generic same-name factories.** Source: [`Channel.kt` at 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/channels/Channel.kt).
    Quote: "public fun <E> Channel(capacity: Int = RENDEZVOUS): Channel<E> = Channel(capacity)".

## 9. Gaps

- **The probe's `Endpoint` is a stand-in.** `HttpResponse` there is a plain public class, not the module's; the real
  type has `Lane`, `Duration` and status-set properties. Resolution depends on the parameter lists and visibility,
  which the stand-in reproduces, not on those types. The real `Builder.misses(statuses)` (a function, not a
  property) was not probed; a member function on a builder does not affect factory resolution.
- **Only the JVM consumer was compiled.** The `js` target was compiled for the library (all variants); a `js`
  consumer and a `js` test compilation were not.
- **The module's real compilation was not run.** Nothing was compiled inside the repository (not permitted); the
  probe's `explicitApi()`, `jvm()` and no-environment `js()` mirror `build-logic`'s settings, but the real module's
  opt-in markers, `-Xjdk-release` flag and test-fixtures wiring were not part of it. None bears on resolution.
- **Diagnostics are the compiler's wording at 2.2.0**; a later Kotlin may word them differently. The ranking of an
  invisible candidate is documented in compiler source (claim 2), the wording is measured only.
- **No survey of every comparable library.** Ktor and kotlinx.coroutines were read at pinned tags; kotlinx.serialization,
  Apollo Kotlin, Coil and Arrow were not re-read for this question (the first two as Gap 2 of the earlier API research
  already names them as non-generic precedents). The statement "no surveyed project uses a companion `invoke` for the
  public configured-type factory" covers only the sources read here.
- **`out T` was compiled, not designed.** Whether `Endpoint` should be covariant depends on how `UpstreamClient` and
  `Lookup<T>` use it and is not decided here.
- **`@BuilderInference` and callable-reference overload ambiguity.** If the module later adds a second overload of
  the factory (for example a `suspend` decoder), `::decodeHero` could become ambiguous; not probed, and the shape
  adds no second overload.
