package com.spartanlabs.testing.nonfunctional.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.HostileParseJvm
import com.spartanlabs.testing.support.webtools.feeds.Jdk11SmokeMain
import com.spartanlabs.testing.support.webtools.feeds.RomeParserFactory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4c - the one guard against the JDK 11 property-name fault, which the build JDK cannot show: skipped by an assumption unless
 * `WEBTOOLS_JDK11_HOME` names a JDK; then the smoke main runs on that JDK's `java` with the worker's class path and must exit 0 printing
 * `ok:2`, `ok:1`, `LimitExceeded:Depth` and `Refused:Doctype`.
 */
class RomeFeedParserJdk11NonFunctionalTest {

    @Test
    fun `a valid feed parses on JDK 11 and a hostile one is rejected with the right kinds`() {
        val home = System.getenv("WEBTOOLS_JDK11_HOME")
        assumeTrue(home != null && home.isNotBlank(), "WEBTOOLS_JDK11_HOME is not set: no JDK 11 on this host")
        val java = Path.of(home!!, "bin", "java")
        assumeTrue(Files.exists(java) || Files.exists(Path.of("$java.exe")), "WEBTOOLS_JDK11_HOME names no java binary")
        val outcome = HostileParseJvm.run(RomeParserFactory::class.java, "jdk11", parses = 1, javaHome = home, mainClass = Jdk11SmokeMain::class.java)
        assertEquals(0, outcome.exitCode, outcome.toString())
        assertEquals(listOf("ok:2", "ok:1", "LimitExceeded:Depth", "Refused:Doctype"), outcome.lines, outcome.toString())
    }
}
