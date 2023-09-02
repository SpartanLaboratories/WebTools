package com.spartanlabs.webtools

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.*
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import javax.imageio.ImageIO

/**
 * This class handles the lowest level online actions.
 * These are not bot actions and this class does not interact
 * as a bot with discord api. It only contains internet
 * browsing logic. For bot actions that use online data see
 * this class' wrapper class OnlineAction.
 * @see OnlineAction
 *
 * @author Spartak
 */
class Connector {
    private var reader: BufferedReader? = null
    private var connection: URLConnection? = null
    private lateinit var savedURL: String

    @Volatile
    private var isOpen = false

    /**
     * The current line of the html of the most recently
     * established connection.
     */
    protected var data: String? = null

    /** Creates a new Connector  */
    init {
        Unirest.setTimeouts(0, 0)
        log.info("Connector was created successfully")

    }

    /**
     * Opens a new connection with the given URL.
     * If another [.open] call is made before the current
     * connection is closed with [.close] then the new connection
     * will wait indefinitely until the current connection closes.
     * @param urlName the url that you are trying to access
     * @return Whether a connection was successfully established
     */
    @Synchronized
    infix fun open(urlName: String): Boolean {
        log.info("Starting attempt to connect to url: {}", urlName)
        waitForTurn()
        val url = getURL(urlName)
        // This is to try to appear as a browser user since some websites reject the connection otherwise
        System.setProperty("http.agent", "Mozilla/4.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0")
        connection = openConnection(url)
        isOpen = true
        createReader()
        return readerState()
    }

    /**
     * Blocks the thread if there is a connection that is still open.
     * If a second connection is opened, the first one may close the reader
     * prior to the second one being done using it.
     */
    private fun waitForTurn() {
        while (isOpen) {
            log.trace("Waiting on current connection to: {} to close", connection!!.url)
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                log.error("An error occured while waiting for a connection to close")
                e.printStackTrace()
            }
        }
        log.debug("A connection is ready to be opened")
    }

    private infix fun getURL(urlName: String): URL {
        return try {
            val url = URL(urlName)
            log.trace("URL formed successfully")
            url
        } catch (e: MalformedURLException) {
            log.error("Invalid URL provided: {}", urlName)
            throw IllegalArgumentException("Given URL is not valid")
        }
    }

    private infix fun openConnection(url: URL): URLConnection? {
        log.trace("Attempting to open connection")
        return try {
            url.openConnection().also{
                log.trace("Successfully opened a url connection")
            }
        } catch (e: IOException) {
            log.error("A connection to the URL: {} could not be opened", url)
            e.printStackTrace()
            null
        }
    }

    private fun createReader() {
        log.trace("Attempting to create a Buffered Reader from an opened URL connection")
        try {
            val `is` = connection!!.getInputStream()
            reader = BufferedReader(InputStreamReader(`is`))
            log.trace("A reader was successfully created")
        } catch (e: IOException) {
            log.error("Could not create a reader for the url connection: {}", connection!!.url)
            e.printStackTrace()
        }
    }

    private fun readerState(): Boolean {
        log.trace("Checking reader state")
        return try {
            if (reader == null) {
                log.error("A newly opened connection's reader is null")
                return false
            }
            if (!reader!!.ready()) {
                log.error("A reader was created but is not ready")
                log.error(data)
                return false
            }
            log.trace("Reader was validated")
            true
        } catch (e: IOException) {
            log.warn("An error occured while checking the reader state")
            false
        }
    }

    /**
     * Closes the currently established connection. Must be called before another connection can be established.
     */
    fun close() {
        try {
            reader!!.close()
            isOpen = false
        } catch (e: IOException) {
            log.error("An error occurred while trying to close the reader.")
            e.printStackTrace()
        }
    }

    infix fun download(imageUrl:String):BufferedImage =
        Jsoup.connect(imageUrl).ignoreContentType(true).execute().bodyStream().let {
            val image = ImageIO.read(it)
            it.close()
            image
        }

    /**
     * Skip the specified number of lines in the html data of the
     * most recently established connection
     * @param lines - the number of lines that you want skipped
     * @throws IOException if there aren't that many lines left in the html
     */
    @Throws(IOException::class)
    fun next(lines: Int) {
        var lines = lines
        while (lines-- > 0) next()
    }

    /**
     * Goes to the next line in the html data of the most
     * recently established connection and returns the value
     * of that line as a new String
     * @return the next line
     * @throws IOException if there are no more lines left
     */
    @Throws(IOException::class)
    operator fun next() : String{
        log.info("Attemped next(). Data was: $data ")
        data = reader!!.readLine()
        return data!!
    }
    operator fun hasNext() = reader!!.ready()
    operator fun iterator() = this

    /**
     * Performs a GET request on a given URL
     * @param URL that you want to send a GET request to
     * @return the result of the GET request as a String
     * or null if unable to GET
     */
    infix fun get(URL: String): String? {
        requireValidURL(URL)
        try {
            return Unirest.get(URL).asString().body
        } catch (e: UnirestException){
            e.printStackTrace()
        }
        return null
    }



    /**
     * Reads the given URL using the Skrape library
     */
    infix fun skrape(url:String) = testAndSkrape(url,::getScrapeResults).responseBody
    /**
     *Takes a URL, validates it, and perform a skrape request
     * @throws IllegalArgumentException if the url is invalid
     * @return a skrape Result
     */
    private fun <T> testAndSkrape(url:String, func:(String)->T):T {
        requireValidURL(url)
        return func(url)
    }

    /**
     * Wrapper around the skrape library skrape call
     */
    @Throws(java.lang.IllegalArgumentException::class)
    private fun getScrapeResults(URL:String) = skrape(HttpFetcher){
        request{url=URL}
        response{this}
    }
    private fun requireValidURL(urlName: String) = require(urlName.isValidURL()){ "Given value: \"$urlName\" is not a valid url" }
    /**
     * Checks if the receiver is a valid URL.
     * @return true if the String forms a valid URL
     * <br> false if it does not
     */
    private fun String.isValidURL():Boolean = try {
        URL(this)
        true
    }   catch (_:MalformedURLException) { false }
        catch(_:ConnectException)       { false }

    companion object {
        private val log = LoggerFactory.getLogger(Connector::class.java)
    }
}