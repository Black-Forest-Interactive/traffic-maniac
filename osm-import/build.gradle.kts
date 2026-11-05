plugins {
    id("trafficmaniac.jvm")
    application
}

dependencies {
    implementation(project(":city"))
    implementation(libs.osmpbf)
    implementation(libs.protobuf.java)
}

application {
    mainClass = "de.bfi.trafficmaniac.osmimport.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx2g")
}
