plugins {
    kotlin("jvm").version("2.2.0")
    id("com.vanniktech.maven.publish") version "0.36.0"
    kotlin("kapt") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Logging: a library should only depend on the slf4j API, never on a concrete
    // backend. Consumers of this library choose their own logging implementation.
    api("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    // logback is a logging *implementation*, so it is only wired up for tests -
    // it must never leak onto the library's runtime classpath.
    testImplementation("ch.qos.logback:logback-classic:1.5.6")

    implementation("it.skrape:skrapeit:1.1.5")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")

    implementation("org.seleniumhq.selenium:selenium-java:4.0.0")   // Screenshot utility

    api("io.github.spartanlaboratories:GeneralTools:2.0.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.spartanlaboratories", "WebTools", "2.0.0a")

    pom {
        name.set("Web Tools")
        description.set("A compilation of internet IO tools.")
        inceptionYear.set("2026")
        url.set("https://github.com/SpartanLaboratories/WebTools")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("SpaSinghOut")
                name.set("Spartak Singh")
                url.set("https://github.com/SpaSinghOut")
            }
        }
        scm {
            url.set("https://github.com/SpartanLaboratories/WebTools/")
            connection.set("scm:git:git://github.com/SpartanLaboratories/WebTools.git")
            developerConnection.set("scm:git:ssh://git@github.com/SpartanLaboratories/WebTools.git")
        }
    }
}