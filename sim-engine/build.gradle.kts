plugins {
    id("trafficmaniac.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":city"))
        }
    }
}
