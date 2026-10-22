// Convention plugin: the Maven Central publishing skeleton shared by every published WebTools
// module. Per-module coordinates(...) and pom { name / description } stay in each module.
// Applies vanniktech, declares mavenCentral(), publishes to Central and signs every publication.
// It never calls configure(...): vanniktech's own afterEvaluate picks KotlinJvm(Javadoc(), Sources())
// for a Kotlin/JVM module, which is what every module published before this plugin existed (an
// explicit configure(KotlinJvm()) would switch to an empty javadoc jar in 0.36.0).
// A release needs, never committed: the Central credentials mavenCentralUsername and
// mavenCentralPassword, and a signing key - signing.keyId, signing.password and
// signing.secretKeyRingFile, or signingInMemoryKey with signingInMemoryKeyId and
// signingInMemoryKeyPassword - as Gradle properties (the in-memory ones also as
// ORG_GRADLE_PROJECT_-prefixed environment variables). Every publication is signed, so a publish to
// any repository, a local file repository included, needs a signing key unless its Sign tasks are
// disabled or its version ends in -SNAPSHOT.

plugins {
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    // coordinates(...) and pom { name / description } are set per module.
    pom {
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
