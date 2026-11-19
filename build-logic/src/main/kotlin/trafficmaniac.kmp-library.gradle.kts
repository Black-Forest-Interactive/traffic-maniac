// Plattformfreie Kotlin-Multiplatform-Bibliothek (city, sim-engine, game, ui …).
// Ziele: JVM (Desktop) und – wenn ein Android-SDK gefunden wird – Android.
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("trafficmaniac.quality")
}

val libs = versionCatalogs.named("libs")

fun version(name: String) = libs.findVersion(name).get().requiredVersion

kotlin {
    jvmToolchain(version("jvm-toolchain").toInt())
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

// Android nur mit SDK – sonst bleibt der Desktop-Build, wie er ist
if (hasAndroidSdk()) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
    val target = (extensions.getByName("kotlin") as ExtensionAware).extensions.getByName("android")
    (target as KotlinMultiplatformAndroidLibraryTarget).apply {
        namespace = "de.bfi.trafficmaniac." + project.name.replace("-", "")
        compileSdk = version("android-compileSdk").toInt()
        minSdk = version("android-minSdk").toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // wie auf dem Desktop: serialisierbare Lambdas für Spielstände
            freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
        }
    }
}
