// Convention plugin: an unpublished, check-only Kotlin Multiplatform module with a single js target.
// It compiles another module's commonMain directory for a second target, so that a JVM API in common
// code is a compile error (a module with only jvm() accepts java.* in commonMain) and so is an expect
// declaration without a js actual. It applies the multiplatform plugin and declares mavenCentral()
// itself; it does not apply webtools.published-library, so the module has no publishing plugin, no
// signing and no ABI dump, and nothing in it is published. It is not a library plugin either: no Java
// floor and no verifyJavaFloor, because a js target has no JVM class files.
// A check module adds, in its own build file:
// - the sibling's commonMain directory as a source directory of its commonMain, as a path relative to
//   the check module (no project(...) access, so the configuration cache needs nothing from another
//   project);
// - the dependencies of the sibling's commonMain;
// - the opt-in of the sibling's subclass gate, as languageSettings.optIn on its commonMain and on its
//   jsMain: the jsMain line is the one the js compile reads, and the commonMain line keeps the pair
//   valid if the module ever gains a target;
// and, in its own src/jsMain/kotlin, a js actual for every expect declaration in that directory.
// commonTest is not compiled: the tests of the sibling run on its jvm target.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

repositories {
    mavenCentral()
}

//region js target
// js() with no environment, on purpose. nodejs() and browser() both create the js test task, and that
// task depends on the Node.js and Yarn set-up, npm install and the kotlin-js-store lock file. Disabling
// a task does not skip the tasks it depends on, so `testTask { enabled = false }` still downloads
// Node.js and Yarn during `build` and writes kotlin-js-store/. Either call also applies the Kotlin
// Node.js root plugin to the root project, which gains build, check, assemble and clean tasks and the
// Node.js and Yarn tasks. Without an environment the target compiles to a klib, has no test task and
// leaves the root project alone.
// The cost is one "JS Environment Not Selected" warning per configuration-cache miss: the Kotlin Gradle
// plugin says the omission will be an error in a future release (it is still a warning in 2.4.21). The
// TestKit check of this plugin fails on that day. The fallback then is
//   js { nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } } }
// which downloads nothing but gives the root project the base plugin and about 18 tasks, among them its
// own build, check, assemble and clean.
kotlin {
    js()
}
//endregion

//region check
// compileKotlinJs belongs to assemble (through jsJar), and a module with no test source leaves check with
// nothing to run, so without this line check would pass without compiling anything.
tasks.named("check") {
    dependsOn("compileKotlinJs")
}
//endregion
