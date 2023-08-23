package com.spartanlabs.webtools

import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import java.io.File
import java.io.OutputStream
import javax.imageio.ImageIO

class WebViewer {
    init{
        System.setProperty("webdriver.chrome.driver", "C:/Users/spartak/Documents/Programming/Kotlin Workspace/IdeaProjects/WebTools/src/main/resources/chromedriver-win64/chromedriver.exe")
        System.setProperty("webdriver.gecko.driver", "C:/Users/spartak/Documents/Programming/Kotlin Workspace/IdeaProjects/WebTools/src/main/resources/firefoxdriver/firefoxdriver.exe")
        //System.setProperty("webdriver.http.factory", "jdk-http-client")
    }
    private infix fun getChromePage(url:String): File {
        val chromeDriverService = ChromeDriverService.createDefaultService()
        var output :OutputStream= File.createTempFile("out", "txt").outputStream()
        //output = OutputStream.nullOutputStream()
        chromeDriverService.sendOutputTo(output)
        return (ChromeDriver(
            chromeDriverService, ChromeOptions()
                //.setBinary("C:/Users/spartak/Documents/Programming/Kotlin Workspace/IdeaProjects/WebTools/src/main/resources/Chrome/Chrome.exe")
                .addArguments("--remote-allow-origins=*")
                .addArguments("--headless")
                .addArguments("--disable-gpu")
                .addArguments("--no-sandbox")
                .addArguments("--webdriver-loglevel=NONE")
        ).apply {
            get(url)
            Thread.sleep(4900)
        } as TakesScreenshot).getScreenshotAs(OutputType.FILE).also {output.close()}
    }
    @org.apache.http.annotation.Experimental
    private infix fun getFirefoxPage(url:String):File{
        return (FirefoxDriver(FirefoxOptions()
            .setBinary("C:/Users/spartak/Documents/Programming/Kotlin Workspace/IdeaProjects/WebTools/src/main/resources/Firefox/FirefoxPortable/Firefox.exe")
            .addArguments("--headless")
        ).apply{
            get(url)
            Thread.sleep(4800)
        } as TakesScreenshot).getScreenshotAs(OutputType.FILE)
    }
    infix fun getPage(url:String) = getChromePage(url)
    infix fun screenshot(url:String) = ImageIO.read(getPage(url))
}