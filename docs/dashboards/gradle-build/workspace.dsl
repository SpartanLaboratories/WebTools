// gradle-build — the Gradle build: C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/gradle-build/index.html.
// As-is at WebTools master 1e27071 (2026-10-22): Gradle 9.7.1, Kotlin Gradle plugin 2.2.0, the included build-logic build from #61. The build publishes nothing of its own. Code is authoritative on any conflict.
// Elements tagged Planned come from Issue #55's architecture (docs/plans/55-http-client-stack/architecture.md, sections 6.1, 6.8 and 11) and carry their sub-issue. None of them is built; they are drawn dashed.
workspace "WebTools" "Kotlin/JVM internet I/O helpers, published as three independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        maintainer = person "Maintainer" "Builds, tests and publishes the modules with the Gradle wrapper; the repository has no CI."
        central = softwareSystem "Maven Central" "Hosts the io.github.spartanlaboratories artifacts and serves the build's dependencies." "External"
        portal  = softwareSystem "Gradle Plugin Portal" "Serves the foojay settings plugin and, with Maven Central, the plugin markers build-logic depends on." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            udp      = container "webtools-udp" "Multi-client UDP connection layer. Its build file applies webtools.kotlin-jvm-library and adds its version 2.0.0a and its UDP-port lock." "Kotlin/JVM library · 2.0.0a"
            scraping = container "webtools-scraping" "Connector: open a URL and read it line by line, plus one-shot helpers. Its build file applies webtools.kotlin-jvm-library." "Kotlin/JVM library · 1.0.0"
            browser  = container "webtools-browser" "WebViewer: headless-Chrome screenshots. Its build file applies webtools.kotlin-jvm-library." "Kotlin/JVM library · 1.0.0"

            gradleBuild = container "Gradle build" "Builds, tests and publishes every module: the wrapper, the settings, the root script and the included build-logic build with its convention plugins. Publishes nothing of its own." "Gradle 9.7.1 · Kotlin 2.2.0" "Internal" {
                // ---- what a module build script may use: the plugin ids and WebToolsBuild ----
                published = component "webtools.published-library" "Applies vanniktech maven-publish 0.36.0, declares mavenCentral(), calls publishToMavenCentral(), signAllPublications() and the shared POM. Never calls configure(...)." "precompiled script plugin" "Public"
                jvmLib    = component "webtools.kotlin-jvm-library" "Applies Kotlin/JVM 2.2.0 and webtools.published-library; test dependencies, JUnit Platform, the seven level tasks over the test source set. No jvmTarget or toolchain." "precompiled script plugin" "Public"
                kmpLib    = component "webtools.kotlin-multiplatform-library" "Kotlin Multiplatform with one jvm() target and its TargetJvmVersion attribute, binary-compatibility-validator 0.18.2 with ASM 9.9.1, level tasks over the jvm test compilation, a lifecycle test task. No module applies it yet." "precompiled script plugin" "Public"
                levels    = component "WebToolsBuild" "LEVEL_PACKAGE_ROOT, the ordered LEVELS and registerLevelTasks: one Test task per level, selecting com.spartanlabs.testing.<level>.* and passing when nothing matches." "Kotlin object · default package" "Public"

                // ---- internals ----
                wrapper    = component "Gradle wrapper" "gradlew, gradlew.bat and gradle/wrapper pin Gradle 9.7.1; gradle.properties sets only kotlin.code.style." "Gradle 9.7.1" "Internal"
                settings   = component "settings.gradle.kts" "Includes build-logic through pluginManagement as its first statement, applies foojay-resolver-convention 0.8.0, includes the three modules." "Gradle Kotlin DSL" "Internal"
                root       = component "build.gradle.kts" "allprojects: group io.github.spartanlaboratories and the default version 1.0.0. No plugins, no subprojects block, no tasks of its own." "Gradle Kotlin DSL" "Internal"
                logicBuild = component "build-logic build" "A kotlin-dsl build: four plugin markers at literal versions, its own level tasks that fail on an empty run, and the testKitHomeLock build service." "Gradle included build · unpublished" "Internal"
                harness    = component "TestKit harness" "SampleProject, ProjectWorkspace, NestedBuild, TestReports, ModuleMetadata, PomFile, BuildLogicSource: nested Gradle builds of generated projects, for build-logic's Level 3, 4b and 4c tests." "test sources · Gradle TestKit" "Internal"

                // ---- planned by Issue #55, not built ----
                floor       = component "Java floor" "#47 (unit 2): WebToolsBuild.JAVA_FLOOR = 11, jvmTarget and -Xjdk-release from it in both library plugins, and verifyJavaFloor in check." "planned" "Planned"
                commonCheck = component "webtools.kotlin-common-check" "#62 (unit 3): a plugin that compiles a multiplatform module's commonMain for js, publishing nothing." "planned" "Planned"
                jsCheck     = component "check-only js modules" "#62 and #69: webtools-http-commoncheck and webtools-feeds-commoncheck, unpublished." "planned" "Planned"
            }
        }

        // ---- as built ----
        maintainer -> webtools.gradleBuild.wrapper "Runs ./gradlew build, a level task, publishToMavenCentral"
        maintainer -> webtools.gradleBuild.harness "Runs :build-logic:test, and the nested-build tasks on demand"
        webtools.gradleBuild.wrapper -> webtools.gradleBuild.settings "Starts Gradle 9.7.1 on the settings"
        webtools.gradleBuild.settings -> webtools.gradleBuild.logicBuild "Includes through pluginManagement, first"
        webtools.gradleBuild.settings -> webtools.gradleBuild.root "Includes the three modules the root script sets group and version on"
        webtools.gradleBuild.settings -> portal "Resolves foojay-resolver-convention 0.8.0"
        webtools.gradleBuild.logicBuild -> portal "Resolves its plugin markers from Maven Central and the Plugin Portal: Kotlin 2.2.0, vanniktech 0.36.0, validator 0.18.2"
        webtools.gradleBuild.logicBuild -> webtools.gradleBuild.published "Compiles, with its plugin markers on the classpath"
        webtools.gradleBuild.logicBuild -> webtools.gradleBuild.jvmLib "Compiles"
        webtools.gradleBuild.logicBuild -> webtools.gradleBuild.kmpLib "Compiles"
        webtools.gradleBuild.jvmLib -> webtools.gradleBuild.published "Applies"
        webtools.gradleBuild.kmpLib -> webtools.gradleBuild.published "Applies"
        webtools.gradleBuild.jvmLib -> webtools.gradleBuild.levels "registerLevelTasks over the test source set"
        webtools.gradleBuild.kmpLib -> webtools.gradleBuild.levels "registerLevelTasks over the jvm test compilation"
        webtools.gradleBuild.root -> webtools.udp "Sets group; version 1.0.0, which the module overrides with 2.0.0a"
        webtools.gradleBuild.root -> webtools.scraping "Sets group and version 1.0.0"
        webtools.gradleBuild.root -> webtools.browser "Sets group and version 1.0.0"
        webtools.gradleBuild.jvmLib -> webtools.udp "Configures: applied by id in its plugins block"
        webtools.gradleBuild.jvmLib -> webtools.scraping "Configures: applied by id in its plugins block"
        webtools.gradleBuild.jvmLib -> webtools.browser "Configures: applied by id in its plugins block"
        webtools.gradleBuild.published -> central "Publishes signed artifacts: publishToMavenCentral"
        webtools.gradleBuild.harness -> webtools.gradleBuild.jvmLib "Applies it to generated projects through TestKit"
        webtools.gradleBuild.harness -> webtools.gradleBuild.kmpLib "Applies it to generated projects through TestKit"
        webtools.gradleBuild.harness -> webtools.gradleBuild.logicBuild "Copies its sources into an end-to-end composite"

        // ---- planned by Issue #55 (dashed: one end is Planned) ----
        webtools.gradleBuild.floor -> webtools.gradleBuild.levels "Would add JAVA_FLOOR = 11"
        webtools.gradleBuild.floor -> webtools.gradleBuild.jvmLib "Would fill the TODO(#47) java floor region"
        webtools.gradleBuild.floor -> webtools.gradleBuild.kmpLib "Would set TargetJvmVersion to the floor"
        webtools.gradleBuild.jsCheck -> webtools.gradleBuild.commonCheck "Would apply"
        webtools.gradleBuild.logicBuild -> webtools.gradleBuild.commonCheck "Would compile (#62)"
    }

    views {
        systemContext webtools "Context" "WebTools, its maintainer and the services the build reaches." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The three modules and the Gradle build that configures them." {
            include *
            autoLayout lr
        }
        component webtools.gradleBuild "Components" "The Gradle build as built at master 1e27071, with its planned parts dashed: the Java floor (#47) and the js common check (#62, #69)." {
            include *
            autoLayout tb
        }
        dynamic webtools.gradleBuild "ModuleBuild" "How a module is configured when ./gradlew runs, as built." {
            maintainer -> webtools.gradleBuild.wrapper "./gradlew :webtools-udp:componentTest"
            webtools.gradleBuild.wrapper -> webtools.gradleBuild.settings "Gradle 9.7.1 evaluates the settings"
            webtools.gradleBuild.settings -> webtools.gradleBuild.logicBuild "pluginManagement includes build-logic, compiled on first use"
            webtools.gradleBuild.root -> webtools.udp "allprojects: group and version 1.0.0"
            webtools.gradleBuild.jvmLib -> webtools.udp "plugins { id(webtools.kotlin-jvm-library) }"
            webtools.gradleBuild.jvmLib -> webtools.gradleBuild.published "applies the publishing skeleton"
            webtools.gradleBuild.jvmLib -> webtools.gradleBuild.levels "registers gatingTest … uatTest, selected by package"
            autoLayout lr
        }
        styles {
            element "Person"          { shape person  background #08427b color #ffffff }
            element "Software System" { background #1168bd color #ffffff }
            element "External"        { background #999999 color #ffffff }
            element "Container"       { background #438dd5 color #ffffff }
            element "Component"       { background #85bbf0 color #000000 }
            element "Internal"        { background #c6dcf5 color #000000 }
            element "Standalone"      { background #eeeeee color #000000 border dotted }
            element "Planned"         { background #ffffff color #666666 border dashed opacity 60 }
        }
    }
}
