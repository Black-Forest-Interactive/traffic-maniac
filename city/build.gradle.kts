plugins {
    id("trafficmaniac.kmp-library")
    id("trafficmaniac.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.cbor)
            api(project(":i18n"))
        }
    }
}
