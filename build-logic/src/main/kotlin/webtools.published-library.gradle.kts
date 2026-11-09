// Convention plugin: the Maven Central publishing skeleton shared by every published WebTools
// module. Per-module coordinates(...) and pom { name / description } stay in each module.
// Applies vanniktech, declares mavenCentral(), publishes to Central and signs every publication.
// This plugin applies Dokka and themes it; each library plugin sets the platform with its Dokka javadoc
// jar (mavenPublishing { configure(...) } is a per-platform call, KotlinJvm versus KotlinMultiplatform).
// A release needs, never committed: the Central credentials mavenCentralUsername and
// mavenCentralPassword, and a signing key - signing.keyId, signing.password and
// signing.secretKeyRingFile, or signingInMemoryKey with signingInMemoryKeyId and
// signingInMemoryKeyPassword - as Gradle properties (the in-memory ones also as
// ORG_GRADLE_PROJECT_-prefixed environment variables). Every publication is signed, so a publish to
// any repository, a local file repository included, needs a signing key unless its Sign tasks are
// disabled or its version ends in -SNAPSHOT.

plugins {
    id("org.jetbrains.dokka")
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

//region API documentation
// Each module renders its own pages with its OWN html plugin configuration; the root build's block only
// styles the aggregate's index page, so the theme is applied here too. The paths resolve against the root
// project, where docs/ lives. A missing theme file fails the Dokka task (NoSuchFileException), by design.
// failOnWarning and reportUndocumented make a broken KDoc link and an undocumented public declaration
// fail dokkaGenerateModuleHtml and dokkaGeneratePublicationHtml.
dokka {
    pluginsConfiguration.html {
        val root = isolated.rootProject.projectDirectory
        customStyleSheets.from(root.file("docs/dokka/styles/dokka-styles.css"))
        customAssets.from(root.file("docs/brand/spartan-laboratories-logo.png"))
        footerMessage.set("&copy; 2026 Spartan Laboratories")
    }
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        reportUndocumented.set(true)
        // "View source" links: each source set's directory is src/<set>/kotlin, in this module's folder on GitHub.
        sourceLink {
            localDirectory.set(file("src/$name/kotlin"))
            remoteUrl("https://github.com/SpartanLaboratories/WebTools/blob/master/${project.name}/src/$name/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
//endregion
