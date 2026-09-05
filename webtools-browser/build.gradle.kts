// webtools-browser - WebViewer: headless-browser screenshots via Selenium.
// Selenium Manager (bundled since 4.6) provisions the browser driver at runtime,
// so no driver binaries are shipped or configured.

dependencies {
    implementation("org.seleniumhq.selenium:selenium-java:4.48.0")
}

mavenPublishing {
    coordinates(group.toString(), "webtools-browser", version.toString())
    pom {
        name.set("WebTools Browser")
        description.set("WebViewer - headless-browser screenshots via Selenium.")
    }
}
