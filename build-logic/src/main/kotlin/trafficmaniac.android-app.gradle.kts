// Android-App (nur mit Android-SDK eingebunden, siehe settings.gradle.kts). Kotlin bringt AGP selbst mit.
plugins {
    id("com.android.application")
    id("trafficmaniac.compose")
    id("trafficmaniac.quality")
}

val libs = versionCatalogs.named("libs")

fun version(name: String) = libs.findVersion(name).get().requiredVersion

configure<com.android.build.api.dsl.ApplicationExtension> {
    compileSdk = version("android-compileSdk").toInt()
    defaultConfig {
        minSdk = version("android-minSdk").toInt()
        targetSdk = version("android-compileSdk").toInt()
    }
    // Verlangt der Compose-Preview-Check von AGP
    testOptions.unitTests.isIncludeAndroidResources = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
