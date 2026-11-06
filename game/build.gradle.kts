plugins {
    id("trafficmaniac.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":sim-engine"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}
