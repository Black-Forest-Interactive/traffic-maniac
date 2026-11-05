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
        }
    }
}
