// Plattformfreie Kotlin-Multiplatform-Bibliothek (sim-engine, game).
// Vorerst nur JVM; weitere Ziele (z. B. Wasm) kommen mit der Web-Variante.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("trafficmaniac.quality")
}

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(libs.findVersion("jvm-toolchain").get().requiredVersion.toInt())
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
