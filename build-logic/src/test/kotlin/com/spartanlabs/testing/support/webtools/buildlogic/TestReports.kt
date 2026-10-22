package com.spartanlabs.testing.support.webtools.buildlogic

import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.streams.asSequence

/**
 * Reads which test classes a nested build's test task ran, from the JUnit XML reports it wrote.
 *
 * The XML files, not task outcomes, are the evidence of test selection: a level task whose package
 * matches nothing may still end `SUCCESS`. Each class name is read from the `classname` attribute of
 * the report's `<testcase>` elements, which holds the package-qualified name for a plain `Test` task
 * and for a multiplatform `jvmTest` alike. Neither the file name nor `<testsuite name>` serves:
 * Gradle shortens a report file name longer than 106 bytes and appends a hash to it, and a
 * multiplatform `jvmTest` report names its suite `<SimpleName>[jvm]`.
 */
object TestReports {

    /**
     * The package-qualified names of the test classes that [taskName] of the project in
     * [projectDir] wrote a report for: the `classname` attribute of every `<testcase>` element of
     * each `<projectDir>/build/test-results/<taskName>/TEST-*.xml` in [workspace].
     *
     * @param workspace the generated project.
     * @param taskName the test task's name, for example `jvmTest` or `componentTest`.
     * @param projectDir the project's directory relative to [workspace], with forward slashes; empty
     *   for the root project.
     * @return the class names, as a read-only set; an empty set when the report directory is absent
     *   (a level that matched nothing may leave no directory).
     * @throws java.io.IOException if a report cannot be read.
     * @throws org.xml.sax.SAXException if a report is not well-formed XML; a broken report means a
     *   broken nested build, so the calling test fails.
     */
    fun resultClasses(workspace: ProjectWorkspace, taskName: String, projectDir: String = ""): Set<String> =
        resultClasses(
            workspace.resolve(
                if (projectDir.isEmpty()) "build/test-results/$taskName" else "$projectDir/build/test-results/$taskName",
            ),
        )

    private fun resultClasses(reportDir: Path): Set<String> =
        if (!Files.isDirectory(reportDir)) emptySet()
        else Files.list(reportDir).use { entries ->
            entries.asSequence()
                .filter { report -> report.fileName.toString().let { it.startsWith("TEST-") && it.endsWith(".xml") } }
                .flatMap { report -> testCaseClassNames(report).asSequence() }
                .toSet()
        }

    // Every <testcase classname="<package-qualified class name>" ...> of a JUnit XML report: a plain
    // Test task and KGP's KotlinJvmTest both write the full name there, while KotlinJvmTest names the
    // suite "<SimpleName>[jvm]". Gradle writes these reports itself; a DOCTYPE declaration is refused
    // all the same.
    private fun testCaseClassNames(report: Path): List<String> {
        val testCases = DocumentBuilderFactory.newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(report.toFile())
            .getElementsByTagName("testcase")
        return (0 until testCases.length).map { (testCases.item(it) as Element).getAttribute("classname") }
    }
}
