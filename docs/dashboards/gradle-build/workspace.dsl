// gradle-build — the Gradle build: C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/gradle-build/index.html.
// As-is at WebTools feat/issue-63-test-support 5d98102 (2026-10-25, not yet landed): Gradle 9.7.1, Kotlin Gradle plugin 2.2.0, the included build-logic build from #61 with the Java 11 floor and verifyJavaFloor from #47, and the webtools.kotlin-common-check plugin from #62. The build publishes nothing of its own. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        maintainer = person "Maintainer" "Builds, tests and publishes the modules with the Gradle wrapper; the repository has no CI."
        central = softwareSystem "Maven Central" "Hosts the io.github.spartanlaboratories artifacts and serves the build's dependencies." "External"
        portal  = softwareSystem "Gradle Plugin Portal" "Serves the foojay settings plugin and, with Maven Central, the plugin markers build-logic depends on." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            udp      = container "webtools-udp" "Multi-client UDP connection layer. Its build file applies webtools.kotlin-jvm-library and adds its version 2.0.0a and its UDP-port lock." "Kotlin/JVM library · 2.0.0a · Java 11 bytecode"
            scraping = container "webtools-scraping" "Connector: open a URL and read it line by line, plus one-shot helpers. Its build file applies webtools.kotlin-jvm-library and sets version 1.0.0a." "Kotlin/JVM library · 1.0.0a · Java 11 bytecode"
            browser  = container "webtools-browser" "WebViewer: headless-Chrome screenshots. Its build file applies webtools.kotlin-jvm-library and sets version 1.0.0a." "Kotlin/JVM library · 1.0.0a · Java 11 bytecode"
            http     = container "webtools-http" "The HTTP stack's common core. Its build file applies webtools.kotlin-multiplatform-library with explicitApi() and the opt-in on its main source sets; 1.0.0 from the root default, unreleased." "Kotlin Multiplatform library · jvm() · 1.0.0 unreleased"

            gradleBuild = container "Gradle build" "Builds, tests and publishes every module: the wrapper, the settings, the root script and the included build-logic build with its convention plugins. Publishes nothing of its own." "Gradle 9.7.1 · Kotlin 2.2.0 · Java floor 11" "Internal" {
                // ---- what a module build script may use: the plugin ids and WebToolsBuild ----
                published = component "webtools.published-library" "Applies vanniktech maven-publish 0.36.0, declares mavenCentral(), calls publishToMavenCentral(), signAllPublications() and the shared POM. Never calls configure(...)." "precompiled script plugin" "Public"
                jvmLib    = component "webtools.kotlin-jvm-library" "Applies Kotlin/JVM 2.2.0 and webtools.published-library; compiles to the Java floor (jvmTarget, -Xjdk-release, Java compatibility) and puts verifyJavaFloor in check; test dependencies, JUnit Platform, the seven level tasks over the test source set." "precompiled script plugin" "Public"
                kmpLib    = component "webtools.kotlin-multiplatform-library" "Kotlin Multiplatform with one jvm() target at the Java floor and TargetJvmVersion 11, verifyJavaFloor in check, binary-compatibility-validator 0.18.2, level tasks over the jvm test compilation, a lifecycle test task. webtools-http applies it." "precompiled script plugin" "Public"
                commonCheck = component "webtools.kotlin-common-check" "The multiplatform plugin with js() and no environment, mavenCentral() of its own, check wired to compileKotlinJs; no publishing, no Java floor. No test task, no Node.js, no root task." "precompiled script plugin" "Public"
                levels    = component "WebToolsBuild" "LEVEL_PACKAGE_ROOT, the ordered LEVELS and registerLevelTasks; JAVA_FLOOR = 11, classFileMajor and registerVerifyJavaFloor." "Kotlin object · default package" "Public"
                floor     = component "Java floor" "WebToolsBuild.JAVA_FLOOR = 11, read by both library plugins; the VerifyJavaFloor task (verifyJavaFloor, in check) and JavaFloorScan read a module's jars, its Gradle Module Metadata and its runtime classpath, and fail on anything above class-file major 55 or a JVM version other than 11." "Gradle task type · Kotlin · default package" "Public"

                // ---- internals ----
                wrapper    = component "Gradle wrapper" "gradlew, gradlew.bat and gradle/wrapper pin Gradle 9.7.1; gradle.properties sets only kotlin.code.style." "Gradle 9.7.1" "Internal"
                settings   = component "settings.gradle.kts" "Includes build-logic through pluginManagement as its first statement, applies foojay-resolver-convention 0.8.0, includes the three modules." "Gradle Kotlin DSL" "Internal"
                root       = component "build.gradle.kts" "allprojects: group io.github.spartanlaboratories and the default version 1.0.0. No plugins, no subprojects block, no tasks of its own." "Gradle Kotlin DSL" "Internal"
                logicBuild = component "build-logic build" "A kotlin-dsl build: four plugin markers at literal versions, its own level tasks that fail on an empty run, and the testKitHomeLock build service." "Gradle included build · unpublished" "Internal"
                harness    = component "TestKit harness" "SampleProject, ProjectWorkspace, NestedBuild, TestReports, ModuleMetadata, PomFile, BuildLogicSource, JarClasses: nested Gradle builds of generated projects, for build-logic's Level 3, 4b and 4c tests." "test sources · Gradle TestKit" "Internal"
                jsCheck    = component "check-only js modules" "webtools-http-commoncheck compiles webtools-http's commonMain for js with two js actuals, publishing nothing, so a JVM API in common code is a compile error. webtools-feeds-commoncheck is planned." "unpublished module · Kotlin/JS klib" "Internal"
            }
        }

        // ---- as built ----
        maintainer -> webtools.gradleBuild.wrapper "Runs ./gradlew build, a level task, publishToMavenCentral"
        maintainer -> webtools.gradleBuild.harness "Runs :build-logic:test, and the nested-build tasks on demand"
        webtools.gradleBuild.wrapper -> webtools.gradleBuild.settings "Starts Gradle 9.7.1 on the settings"
        webtools.gradleBuild.settings -> webtools.gradleBuild.logicBuild "Includes through pluginManagement, first"
        webtools.gradleBuild.settings -> webtools.gradleBuild.root "Includes the five modules the root script sets group and version on"
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
        webtools.gradleBuild.root -> webtools.scraping "Sets group; version 1.0.0, which the module overrides with 1.0.0a"
        webtools.gradleBuild.root -> webtools.browser "Sets group; version 1.0.0, which the module overrides with 1.0.0a"
        webtools.gradleBuild.jvmLib -> webtools.udp "Configures: applied by id in its plugins block"
        webtools.gradleBuild.jvmLib -> webtools.scraping "Configures: applied by id in its plugins block"
        webtools.gradleBuild.jvmLib -> webtools.browser "Configures: applied by id in its plugins block"
        webtools.gradleBuild.published -> central "Publishes signed artifacts: publishToMavenCentral"
        webtools.gradleBuild.harness -> webtools.gradleBuild.jvmLib "Applies it to generated projects through TestKit"
        webtools.gradleBuild.harness -> webtools.gradleBuild.kmpLib "Applies it to generated projects through TestKit"
        webtools.gradleBuild.harness -> webtools.gradleBuild.logicBuild "Copies its sources into an end-to-end composite"
        webtools.gradleBuild.logicBuild -> webtools.gradleBuild.floor "Compiles"
        webtools.gradleBuild.jvmLib -> webtools.gradleBuild.floor "Compiles at JAVA_FLOOR; registerVerifyJavaFloor, wired into check"
        webtools.gradleBuild.kmpLib -> webtools.gradleBuild.floor "Compiles jvm() at JAVA_FLOOR, TargetJvmVersion 11; registerVerifyJavaFloor, wired into check"
        webtools.gradleBuild.floor -> webtools.gradleBuild.levels "Reads JAVA_FLOOR and classFileMajor"
        webtools.gradleBuild.harness -> webtools.gradleBuild.floor "Reads the built jars and module.json files through TestKit"
        webtools.gradleBuild.logicBuild -> webtools.gradleBuild.commonCheck "Compiles"
        webtools.gradleBuild.kmpLib -> webtools.http "Configures: applied by id in its plugins block"
        webtools.gradleBuild.root -> webtools.http "Sets group and the version 1.0.0 it keeps"
        webtools.gradleBuild.commonCheck -> webtools.gradleBuild.jsCheck "Configures: applied by id in its plugins block"
        webtools.gradleBuild.jsCheck -> webtools.http "Compiles its commonMain for js, through a relative srcDir"
        webtools.gradleBuild.harness -> webtools.gradleBuild.commonCheck "Applies it to generated projects through TestKit"
    }

    views {
        systemContext webtools "Context" "WebTools, its maintainer and the services the build reaches." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The four published modules and the Gradle build that configures them." {
            include *
            autoLayout lr
        }
        component webtools.gradleBuild "Components" "The Gradle build as built at feat/issue-63-test-support 5d98102." {
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
            webtools.gradleBuild.jvmLib -> webtools.gradleBuild.floor "compiles at JAVA_FLOOR 11; check runs verifyJavaFloor"
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
