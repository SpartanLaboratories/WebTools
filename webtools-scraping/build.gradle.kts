// webtools-scraping - Connector: open/read a URL line by line, plus one-shot
// get / skrape / image download helpers.

plugins {
    id("webtools.kotlin-jvm-library")
}

dependencies {
    // Logging: a library depends only on the slf4j API, never on a concrete backend.
    api("org.slf4j:slf4j-api:2.0.13")
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
