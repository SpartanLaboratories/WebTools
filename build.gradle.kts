plugins {
    kotlin("jvm") version "2.1.10"
    `java-library`
    `maven-publish`
    idea
}

group = "com.spartanlabs"
version = "1.1.3"

repositories {
    mavenCentral()
    maven("D:/Documents/Programming")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("it.skrape:skrapeit:1.1.5")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")

    implementation("org.seleniumhq.selenium:selenium-java:4.0.0")   // Screenshot utility

    api("com.spartanlabs:GeneralTools:1.2.0c")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(23)
}

publishing{
    publications{
        //create<MavenPublication>("webtools").from(components["java"])
        create<MavenPublication>("webtools-snapshot"){
            version = "LATEST"
        }.from(components["java"])
    }
    repositories{
        maven("D:/Documents/Programming")
    }
}