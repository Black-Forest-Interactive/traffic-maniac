// Reines JVM-Modul (Anwendungen und Werkzeuge wie osm-import, desktop-app)
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("trafficmaniac.quality")
}

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(libs.findVersion("jvm-toolchain").get().requiredVersion.toInt())
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
