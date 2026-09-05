package com.spartanlabs.testing.gating.webtools.browser

import com.spartanlabs.webtools.browser.WebViewer
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 1 - fast, browser-free smoke. Everything a capture needs a real Chrome for
// is out of scope here (see the UAT level); this only pins the contract that can be
// checked without launching a driver: a blank URL is rejected as a Result failure
// before any browser work, and the settle-time constant is sane.
@Tag("gating")
class WebViewerGatingTest {

    @Test
    fun `getPage rejects a blank url as a failure without launching a browser`() {
        val outcome = WebViewer() getPage "   "

        assertTrue(outcome.isFailure, "expected a blank URL to be rejected")
        assertIs<IllegalArgumentException>(outcome.exceptionOrNull())
    }

    @Test
    fun `screenshot rejects a blank url as a failure without launching a browser`() {
        val outcome = WebViewer() screenshot ""

        assertTrue(outcome.isFailure, "expected a blank URL to be rejected")
        assertIs<IllegalArgumentException>(outcome.exceptionOrNull())
    }

    @Test
    fun `the default page-settle time is a positive duration`() {
        assertTrue(WebViewer.DEFAULT_PAGE_SETTLE_MILLIS > 0)
    }
}
