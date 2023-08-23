plugins {
    kotlin("jvm") version "1.8.0"
    `java-library`
    `maven-publish`
    idea
}

group = "com.spartanlabs"
version = "1.1.2-D2"

repositories {
    mavenCentral()
    maven("C:/Users/spartak/Documents/Programming/libraries")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")

    implementation("org.seleniumhq.selenium:selenium-java:4.0.0")   // Screenshot utility

    api("com.spartanlabs:GeneralTools:1.0.9")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

publishing{
    publications{
        create<MavenPublication>("webtools").from(components["java"])
        create<MavenPublication>("generaltools-snapshot"){
            version = "LATEST"
        }.from(components["java"])
    }
    repositories{
        maven("C:/Users/spartak/Documents/Programming/libraries")
    }
}