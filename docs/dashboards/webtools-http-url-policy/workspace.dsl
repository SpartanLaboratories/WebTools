// webtools-http-url-policy — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-url-policy/index.html.
// As-is at WebTools docs/issue-70-http-architecture-docs 45aa548 (2026-11-07), the outbound-URL policy inside webtools-http 1.0.0 (new, unreleased): the Trusted and Untrusted policies with TransportSettings, CidrBlock and MixedAnswers, the internal JVM-free classifier, and the internal JVM resolver and socket guards that OkHttpTransport applies. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that builds a Trusted policy for a known upstream or an Untrusted one for URLs a third party supplies, and hands it to OkHttpTransport."
        web = softwareSystem "Resolvers and servers" "The system resolver and the hosts a URL names, local, private and public." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            policy = container "webtools-http-url-policy" "Decides, for every hop and every resolved address, whether a fetch may proceed, from data parameters that never admit an address or name the built-in tables deny. Public configuration values; an internal classifier in commonMain; internal guards in jvmMain." "Kotlin Multiplatform · commonMain, jvmMain · webtools-http 1.0.0 unreleased" {
                // ---- public configuration values ----
                policies  = component "OutboundUrlPolicy" "A sealed policy with two final leaves, Trusted and Untrusted, built with trusted { } and untrusted { } or their from(...) forms; every value validated when the block ends. Value equality; toString() prints counts, never a host." "sealed class · final leaves · builders" "Public"
                settings  = component "TransportSettings" "The ten transport settings a policy carries, with TrustedDefaults and UntrustedDefaults; TransportSettings(from) { } derives and validates." "final class · factory · builder" "Public"
                cidr      = component "CidrBlock" "An IPv4 or IPv6 block for extraDeniedRanges; parse(text) returns a Result with one of four constant messages." "final class" "Public"
                mixed     = component "MixedAnswers" "Reject (the default) or DropDenied: what an Untrusted policy does with an answer of allowed and denied addresses." "enum class" "Public"
                // ---- the internal classifier ----
                guard     = component "UrlGuard" "checkHop, checkRequest, admitRedirect, checkAddress and judgeAnswer: one policy applied to a hop, a request, a redirect target, an address and a resolver answer. Pure; never logs." "internal class · commonMain" "Internal"
                hostRules = component "HostRules" "Classifies any host text as an IP literal, a name or refused: lenient numeric forms are HostForm, reserved suffixes and single labels ReservedName." "internal object · commonMain" "Internal"
                ranges    = component "AddressRanges" "The address table: the IANA special-purpose rows, mapped, NAT64 and ISATAP forms, everything outside 2000::/3; each row names its range class." "internal object · commonMain" "Internal"
                strictIp  = component "StrictIp" "The strict IPv4 and RFC 4291 IPv6 parser the guard trusts, printed in RFC 5952 form." "internal object · commonMain" "Internal"
                relax     = component "GuardRelaxation" "None in every production path; LoopbackForTests, reachable only from the test fixtures and the module's tests, exempts loopback." "internal class · commonMain" "Internal"
                // ---- the JVM guards ----
                enforce   = component "PolicyEnforcement" "The three client settings a policy implies: for Untrusted, GuardedDns, GuardedSocketFactory and Proxy.NO_PROXY; for Trusted, the base resolver and OkHttp's defaults." "internal class · jvmMain" "Internal"
                dns       = component "GuardedDns" "An okhttp3.Dns that judges every resolver answer under MixedAnswers; an empty answer is an UnknownHostException with a constant message." "internal class · jvmMain · okhttp-jvm 5.5.0" "Internal"
                socket    = component "GuardedSocket · GuardedSocketFactory" "Unconnected Socket(Proxy.NO_PROXY)s that judge the target of every connect before any I/O." "internal classes · jvmMain" "Internal"
                refusals  = component "Refusal markers" "PolicyRefusedUnknownHost and PolicyRefusedIo, constant messages and no stack trace, found by policyRefusal() through the causes and the suppressed lists." "internal classes · jvmMain" "Internal"
            }
            core      = container "webtools-http-core" "What the policy uses from the common core: Url, RefusalReason, the WebToolsDsl marker and the Log seam." "commonMain"
            okhttp    = container "webtools-http-transport" "OkHttpTransport: applies a policy to every hop, judges the request and every redirect target, and returns a refusal as TransportFailure.RefusedByPolicy." "jvmMain · okhttp-jvm 5.5.0"
            facades   = container "webtools-http-facades" "UpstreamClient and UrlFetcher: pre-check every request with the policy before the cache and the budget; their JVM owning factories hand it to the transport they build." "commonMain · jvmMain owning factories"
            fixtures  = container "webtools-http-test-fixtures" "LoopbackTransports: the real transport with the loopback relaxation, for a consumer's tests." "jvmTestFixtures · test classpath only"
            check     = container "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js, the classifier included, so it stays JVM-free. Unpublished; a part of the Gradle build." "Kotlin/JS klib · check only" "Internal"
        }

        consumer -> webtools.policy.policies "Builds Trusted or Untrusted policies"
        consumer -> webtools.policy.settings "Derives settings in settings { }"
        consumer -> webtools.policy.cidr "Parses extra denied ranges"
        consumer -> webtools.policy.mixed "Chooses how a mixed answer is judged"
        consumer -> webtools.okhttp "Hands the policy to OkHttpTransport(userAgent, policy)"
        webtools.policy.policies -> webtools.policy.settings "Carries"
        webtools.policy.policies -> webtools.policy.cidr "Adds denials with"
        webtools.policy.policies -> webtools.policy.mixed "Judges mixed answers by"
        webtools.policy.guard -> webtools.policy.policies "Applies"
        webtools.policy.guard -> webtools.policy.hostRules "Classifies the host with"
        webtools.policy.guard -> webtools.policy.ranges "Judges an address with"
        webtools.policy.guard -> webtools.policy.relax "Exempts loopback only under LoopbackForTests"
        webtools.policy.hostRules -> webtools.policy.strictIp "Reads a literal with"
        webtools.policy.enforce -> webtools.policy.dns "Installs for Untrusted"
        webtools.policy.enforce -> webtools.policy.socket "Installs for Untrusted, with Proxy.NO_PROXY"
        webtools.policy.dns -> webtools.policy.guard "Judges every answer with judgeAnswer"
        webtools.policy.socket -> webtools.policy.guard "Judges every target with checkAddress"
        webtools.policy.dns -> webtools.policy.refusals "Throws PolicyRefusedUnknownHost"
        webtools.policy.socket -> webtools.policy.refusals "Throws PolicyRefusedIo"
        webtools.policy.dns -> web "Resolves through the base resolver"
        webtools.policy.socket -> web "Connects, never through a proxy"
        webtools.policy.guard -> webtools.core "Reads Url's canonical host, port and scheme; refuses with a RefusalReason"
        webtools.policy.dns -> webtools.core "Logs a dropped answer through the Log seam: the counts and the range classes"
        webtools.okhttp -> webtools.policy.guard "Judges the request and every redirect target before any socket"
        webtools.okhttp -> webtools.policy.enforce "Applies it to its client"
        webtools.okhttp -> webtools.policy.refusals "Finds a refusal through policyRefusal()"
        webtools.facades -> webtools.policy.guard "Pre-checks every request with checkRequest, before the cache and the budget"
        webtools.fixtures -> webtools.policy.relax "Builds the loopback transport with LoopbackForTests"
        webtools.check -> webtools.policy "Compiles its common part for js"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer that builds a policy, and the resolvers and servers it guards against." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The outbound-URL policy and the parts of webtools-http around it." {
            include *
            autoLayout lr
        }
        component webtools.policy "Components" "The outbound-URL policy as built at docs/issue-70-http-architecture-docs 45aa548." {
            include *
            autoLayout tb
        }
        dynamic webtools.policy "Guarded call" "OkHttpTransport fetches a URL under an Untrusted policy, as built." {
            consumer -> webtools.policy.policies "OutboundUrlPolicy.untrusted { allowedPorts = setOf(443) }"
            consumer -> webtools.okhttp "OkHttpTransport(userAgent, policy), then execute(request)"
            webtools.okhttp -> webtools.policy.guard "checkRequest: the host form, the address or reserved names, the port, the sensitive headers"
            webtools.okhttp -> webtools.policy.enforce "the client carries GuardedDns, GuardedSocketFactory and Proxy.NO_PROXY"
            webtools.policy.dns -> web "lookup(host) through the base resolver"
            webtools.policy.dns -> webtools.policy.guard "judgeAnswer(addresses) under MixedAnswers"
            webtools.policy.socket -> webtools.policy.guard "checkAddress(target) before any I/O"
            webtools.policy.socket -> web "connect, with no proxy"
            webtools.okhttp -> webtools.policy.refusals "policyRefusal(): RefusedByPolicy, or none"
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
