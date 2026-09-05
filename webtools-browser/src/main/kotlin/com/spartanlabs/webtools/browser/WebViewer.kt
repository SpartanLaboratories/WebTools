package com.spartanlabs.webtools.browser

import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * Headless-Chrome screenshot utility.
 *
 * [getPage] renders a URL in a throwaway headless Chrome instance and returns the
 * capture as a PNG [File]; [screenshot] returns the same capture decoded into a
 * [BufferedImage]. Both return a [Result] rather than throwing - a blank URL, a
 * driver-launch failure, a navigation failure, or an image-decode failure all
 * come back as [Result.failure], matching the rest of WebTools.
 *
 * The Chrome driver binary is resolved at runtime by Selenium Manager (bundled
 * with Selenium since 4.6) - no driver is shipped in this artifact or configured
 * by this class. Running a capture therefore needs a Chrome/Chromium install on
 * the host, and outbound network access the first time a given driver version is
 * downloaded.
 *
 * @param pageSettleMillis how long to wait after navigation before capturing, so
 * late-loading content has a chance to paint. This is a blunt fixed wait; a
 * readiness-based wait (`document.readyState`) is an intended refinement. Defaults
 * to [DEFAULT_PAGE_SETTLE_MILLIS].
 */
class WebViewer(private val pageSettleMillis: Long = DEFAULT_PAGE_SETTLE_MILLIS) {

    /**
     * Renders [url] and writes the screenshot to a PNG file in the system temp directory.
     * @param url the page to render; must not be blank
     * @return the screenshot file, or [Result.failure] holding the cause if the render failed
     */
    infix fun getPage(url: String): Result<File> =
        runCatching {
            require(url.isNotBlank()) { "url must not be blank" }
            capture(url)
        }.onFailure { log.error("Could not capture a screenshot of '{}'", url, it) }

    /**
     * Renders [url] and decodes the screenshot into an in-memory image.
     * @param url the page to render; must not be blank
     * @return the screenshot image, or [Result.failure] if the render or the PNG decode failed
     */
    infix fun screenshot(url: String): Result<BufferedImage> =
        getPage(url).mapCatching { file ->
            ImageIO.read(file) ?: error("no ImageIO reader could decode the screenshot at $file")
        }

    /** Launches headless Chrome, navigates, captures, and always quits the driver. */
    private fun capture(url: String): File {
        val service = ChromeDriverService.createDefaultService()
        val driverLog: OutputStream = File.createTempFile("webviewer-chromedriver", ".log").outputStream()
        service.sendOutputTo(driverLog)
        val driver = ChromeDriver(service, chromeOptions())
        return try {
            driver.get(url)
            Thread.sleep(pageSettleMillis)
            driver.getScreenshotAs(OutputType.FILE)
        } finally {
            driver.quit()
            driverLog.close()
        }
    }

    private fun chromeOptions(): ChromeOptions =
        ChromeOptions()
            .addArguments("--headless=new")
            .addArguments("--disable-gpu")
            .addArguments("--no-sandbox")
            .addArguments("--remote-allow-origins=*")

    companion object {
        private val log = LoggerFactory.getLogger(WebViewer::class.java)

        /** Default post-navigation settle time, in milliseconds. */
        const val DEFAULT_PAGE_SETTLE_MILLIS: Long = 2_000
    }
}
