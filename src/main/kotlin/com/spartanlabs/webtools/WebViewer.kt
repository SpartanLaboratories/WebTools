package com.spartanlabs.webtools

import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import javax.imageio.ImageIO

class WebViewer {
    init{
        System.setProperty("webdriver.chrome.driver", "C:/Users/spartak/Documents/Programming/Kotlin Workspace/IdeaProjects/WebTools/src/main/resources/chromedriver_win32/chromedriver.exe")
        //System.setProperty("webdriver.http.factory", "jdk-http-client")
    }
    infix fun getPage(url:String) =
        (ChromeDriver(
            ChromeOptions()
                .addArguments("--remote-allow-origins=*")
                .addArguments("headless")
        ).apply {
            get(url)
            Thread.sleep(5000)
        } as TakesScreenshot).getScreenshotAs(OutputType.FILE)
    infix fun screenshot(url:String) = ImageIO.read(getPage(url))
}