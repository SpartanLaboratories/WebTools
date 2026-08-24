plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("it.skrape:skrapeit:1.1.5")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")

    implementation("org.seleniumhq.selenium:selenium-java:4.0.0")   // Screenshot utility

    api("io.github.spartanlaboratories:GeneralTools:1.0.2")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.spartanlaboratories", "WebTools", "1.0.1")

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