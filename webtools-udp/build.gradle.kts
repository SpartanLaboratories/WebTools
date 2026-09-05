// webtools-udp - a multi-client UDP connection layer with a NAT-traversal handshake.
// Runtime dependencies: slf4j-api only (inherited from the root build).

// Serialises the test tasks that bind the fixed common UDP port (9998) - `test`,
// `integrationTest`, `e2eTest`, and `nonfunctionalTest` - so Gradle never runs two of
// them in parallel workers and hits a BindException. Level tasks that touch no socket
// are unaffected. Only this module binds the port, so the lock lives here, not in the
// root build.
abstract class CommonUdpPortLock : BuildService<BuildServiceParameters.None>

val commonUdpPortLock =
    gradle.sharedServices.registerIfAbsent("commonUdpPortLock", CommonUdpPortLock::class) {
        maxParallelUsages = 1
    }

listOf("test", "integrationTest", "e2eTest", "nonfunctionalTest").forEach { name ->
    tasks.named<Test>(name) { usesService(commonUdpPortLock) }
}

mavenPublishing {
    coordinates(group.toString(), "webtools-udp", version.toString())
    pom {
        name.set("WebTools UDP")
        description.set("A multi-client UDP connection layer with a NAT-traversal handshake.")
    }
}
