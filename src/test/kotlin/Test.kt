package com.spartanlabs.webtools.test

import com.spartanlabs.generaltools.cropImage
import com.spartanlabs.generaltools.to
import com.spartanlabs.webtools.Connector
import com.spartanlabs.webtools.WebViewer
import org.junit.jupiter.api.Test

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
        WebViewer() screenshot bearImage to "$resources/testWebpageScreenshot"
    }
    @Test fun getMirrorPage():Unit{
        val mirrorPage = "https://poe.ninja/economy/standard/currency/mirror-of-kalandra"
        cropImage(
            image = WebViewer() screenshot mirrorPage,
            x = 145,        y = 285,
            width = 330,    height = 320
        ) to "$resources/mirrorImage"

    }
}