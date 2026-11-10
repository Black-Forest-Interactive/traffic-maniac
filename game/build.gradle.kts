plugins {
    id("trafficmaniac.kmp-library")
    id("trafficmaniac.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":sim-engine"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
    }
}
