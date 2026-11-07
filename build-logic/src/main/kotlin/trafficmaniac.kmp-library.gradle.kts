// Plattformfreie Kotlin-Multiplatform-Bibliothek (sim-engine, game).
// Vorerst nur JVM; weitere Ziele (z. B. Wasm) kommen mit der Web-Variante.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("trafficmaniac.quality")
}

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(libs.findVersion("jvm-toolchain").get().requiredVersion.toInt())
    jvm {
        compilerOptions {
            // Lambdas und Funktionsreferenzen als Klassen erzeugen: so sind sie serialisierbar (Spielstände)
            freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
