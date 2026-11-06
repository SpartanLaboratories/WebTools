// webtools-http-cache — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-cache/index.html.
// As-is at WebTools master 5f221ce (2026-11-05), the response cache inside webtools-http 1.0.0 (new, unreleased): the ResponseCache port, its values and the pass-through ResponseCache.Disabled in common code, and the CaffeineResponseCache default on the JVM. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that builds a cache, loads each answer through getOrLoad with its own loader, invalidates, reads the counts and closes the cache."

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            cache = container "webtools-http-cache" "Serves one decoded value per key to many concurrent callers from one load, for a bounded time, never caching a failure. The port and its values in commonMain; the Caffeine default in jvmMain." "Kotlin Multiplatform · commonMain, jvmMain · webtools-http 1.0.0 unreleased" {
                port     = component "ResponseCache" "getOrLoad, an overload accepting any live entry, invalidate, invalidateAll, stats and an idempotent close(), under an eleven-point contract. Implementing it needs the opt-in." "interface · AutoCloseable · gated · commonMain" "Public"
                disabled = component "ResponseCache.Disabled" "Stores nothing and shares nothing: every call runs its own loader on the caller's coroutine. Final, stateless and shared; needs no opt-in." "object · commonMain" "Public"
                values   = component "Cache values" "CacheKey, CachePolicy with its builder and factory, the sealed Lookup (Found, Missing) and CacheStats: immutable; toString() prints names and weights, never a value." "final classes · a sealed pair · commonMain" "Public"
                runner   = component "runLoader" "The one place that decides what a loader's exception, Error and cancellation mean; Disabled and the Caffeine default share it." "internal function · commonMain" "Internal"
                caffeine = component "CaffeineResponseCache" "The supplied ResponseCache, final, built by the factory CaffeineResponseCache { }: one shared future per key, per-entry lifetimes from the load's completion, a weight bound, a per-call maxAge; usable without the opt-in. It owns no thread, timer or pool." "final class · factory · builder · jvmMain · caffeine 3.3.1" "Public"
                entry    = component "Cache entry and rules" "CacheEntry, CacheOutcome, Residency, Storage and CacheExpiry: what a finished load holds, how long it lives, and whether a call may be served it; the rules are pure." "internal · jvmMain" "Internal"
            }
            core  = container "webtools-http-core" "What the cache uses from the common core: HttpFailure.Closed, catchNonFatal and flatMap, the opt-in marker, the WebToolsDsl marker and the Log seam." "commonMain"
            check = container "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js, the port, its values and the runner included. Unpublished; a part of the Gradle build." "Kotlin/JS klib · check only" "Internal"
        }

        consumer -> webtools.cache.caffeine "Builds it with CaffeineResponseCache { }, then calls getOrLoad, invalidate, stats and close()"
        consumer -> webtools.cache.values "Builds keys and policies; its loader returns a Lookup"
        consumer -> webtools.cache.disabled "Passes it where no cache is wanted"
        webtools.cache.caffeine -> webtools.cache.port "Implements"
        webtools.cache.disabled -> webtools.cache.port "Implements"
        webtools.cache.port -> webtools.cache.values "Takes a CacheKey and a CachePolicy, returns a Result of a Lookup"
        webtools.cache.port -> webtools.core "Fails with Closed(cache) after close(); implementing it is gated by the opt-in marker"
        webtools.cache.disabled -> webtools.cache.runner "Runs each loader through, on the caller's coroutine"
        webtools.cache.caffeine -> webtools.cache.runner "Runs each load through, in its own scope"
        webtools.cache.caffeine -> webtools.cache.entry "Holds one shared future of an entry per key"
        webtools.cache.caffeine -> webtools.core "Logs through the Log seam: never a value, a parameter value or a message text"
        webtools.cache.runner -> webtools.core "Joins the outcome with catchNonFatal and flatMap"
        webtools.cache.entry -> webtools.cache.values "Reads the policy's lifetimes and the Lookup's weight"
        webtools.check -> webtools.cache "Compiles the port, its values and the runner for js"
    }

    views {
        systemContext webtools "Context" "WebTools and the consumer of the response cache." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The response cache and the parts of webtools-http it uses." {
            include *
            autoLayout lr
        }
        component webtools.cache "Components" "The response cache as built at master 5f221ce." {
            include *
            autoLayout tb
        }
        dynamic webtools.cache "One load" "Two callers ask CaffeineResponseCache for one key; one load answers both, as built." {
            consumer -> webtools.cache.values "a CacheKey<Quote> with its parameters · CachePolicy(30.seconds)"
            consumer -> webtools.cache.caffeine "getOrLoad(key, policy, maxAge) { … }"
            webtools.cache.caffeine -> webtools.cache.runner "the loader, in the cache's own scope"
            webtools.cache.caffeine -> webtools.cache.entry "the entry: its outcome, its lifetime, its weight"
            webtools.cache.entry -> webtools.cache.values "ttl for a Found, missTtl for a Missing"
            autoLayout lr
        }
        styles {
            element "Person"          { shape person  background #08427b color #ffffff }
            element "Software System" { background #1168bd color #ffffff }
            element "External"        { background #999999 color #ffffff }
            element "Container"       { background #438dd5 color #ffffff }
            element "Component"       { background #85bbf0 color #000000 }
            element "Internal"        { background #c6dcf5 color #000000 }
            element "Standalone"      { background #eeeeee color #000000 border dotted }
            element "Planned"         { background #ffffff color #666666 border dashed opacity 60 }
        }
    }
}
