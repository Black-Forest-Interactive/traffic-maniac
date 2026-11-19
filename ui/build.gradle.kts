plugins {
    id("trafficmaniac.kmp-library")
    id("trafficmaniac.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":game"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            // Schriften (Inter, JetBrains Mono, Bricolage Grotesque) als Compose-Resources
            implementation(libs.compose.resources)
        }
        // Rendering-Tests brauchen Skias native Bibliotheken
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "de.bfi.trafficmaniac.ui.resources"
    publicResClass = false
}

// Unter Android liegen die Compose-Resources (Schriften) in den Android-Resources der Bibliothek
if (hasAndroidSdk()) {
    val target = (kotlin as ExtensionAware).extensions.getByName("android")
    (target as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget).androidResources.enable = true
}
