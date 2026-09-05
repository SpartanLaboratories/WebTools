package com.spartanlabs.testing.uat.webtools.browser

import com.spartanlabs.webtools.browser.WebViewer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Level 5 - manual acceptance for [WebViewer]. `@Disabled` because it needs a real
 * Chrome/Chromium install on the host and outbound network access for Selenium
 * Manager to fetch the matching driver on first run - neither is guaranteed in CI.
 *
 * To run: remove `@Disabled`, ensure Chrome is installed, execute
 * `./gradlew :webtools-browser:uatTest`, and eyeball the saved PNG the test logs.
 */
@Tag("uat")
class WebViewerUatTest {

    @Test
    @Disabled("Manual: needs a local Chrome install + network for Selenium Manager.")
    fun `screenshot of example_com returns a non-trivial image`() {
        val image = (WebViewer() screenshot "https://example.com").getOrThrow()

        // PASS: a real render is at least a few hundred pixels each way.
        assertTrue(image.width > 200 && image.height > 200, "got ${image.width}x${image.height}")
    }

    @Test
    @Disabled("Manual: needs a local Chrome install + network for Selenium Manager.")
    fun `getPage of example_com writes a readable PNG file`() {
        val file = (WebViewer() getPage "https://example.com").getOrThrow()

        println("WebViewer UAT screenshot written to: ${file.absolutePath}")
        assertTrue(file.exists() && file.length() > 0)
    }
}
