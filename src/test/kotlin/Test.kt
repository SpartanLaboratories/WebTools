package com.spartanlabs.webtools.test

import com.spartanlabs.generaltools.capitalizeEveryWord
import com.spartanlabs.generaltools.saveImage
import org.junit.jupiter.api.Test
import com.spartanlabs.webtools.Connector
import com.spartanlabs.webtools.WebViewer
import com.spartanlabs.webtools.saveTo
import com.spartanlabs.webtools.to
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.spartanlabs.generaltools.cropImage

class Test{
    companion object
    {
        private const val bearImage =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/7/71/2010-kodiak-bear-1.jpg/1200px-2010-kodiak-bear-1.jpg"
        private const val resources = "src/test/resources"
    }
    @Test fun saveImageFromWeb():Unit {
        Connector() download bearImage to "$resources/test image file"
    }
    @Test fun saveWebpageAsImage():Unit{
        WebViewer() getPage bearImage saveTo "$resources/testWebpageScreenshot"
    }
    @Test fun getMirrorPage():Unit{
        val mirrorPage = "https://poe.ninja/economy/standard/currency/mirror-of-kalandra"
        val image = ImageIO.read(WebViewer() getPage mirrorPage)
        cropImage(
            image = ImageIO.read(WebViewer() getPage mirrorPage),
            x = 145,        y = 285,
            width = 330,    height = 320
        ) to "$resources/mirrorImage"

    }
    @Test fun listToString(){
        println(listOf("a","b").toString().let { it.substring(1,it.length-1) })
    }
}