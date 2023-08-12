import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    kotlin("jvm") version "1.8.0"
    `java-library`
    `maven-publish`
}

group = "com.spartanlabs"
version = "1.1.1"

repositories {
    mavenCentral()
    maven("C:/Users/spartak/Documents/Programming/libraries")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("ch.qos.logback:logback-classic:1.3.0-alpha13")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")// https://mvnrepository.com/artifact/com.mashape.unirest/unirest-java

    implementation("org.seleniumhq.selenium:selenium-java:4.0.0")   // Screenshot utility
//    implementation("org.seleniumhq.selenium:selenium-http-jdk-client:4.8.1")
    implementation("org.apache.directory.studio:org.apache.commons.io:2.4")          // Files Utility

    api("com.spartanlabs:GeneralTools:1.0.4")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

publishing{
    publications{
        create<MavenPublication>("webtools") {
            from(components["java"])
            pom{

            }
        }
    }
    repositories{
        maven("C:/Users/spartak/Documents/Programming/libraries")
    }
}