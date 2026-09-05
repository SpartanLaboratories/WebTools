// webtools-scraping - Connector: open/read a URL line by line, plus one-shot
// get / skrape / image download helpers.

dependencies {
    implementation("it.skrape:skrapeit:1.1.5")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")
}

mavenPublishing {
    coordinates(group.toString(), "webtools-scraping", version.toString())
    pom {
        name.set("WebTools Scraping")
        description.set("Connector - open and read a URL line by line, plus one-shot get/skrape/image-download helpers.")
    }
}
