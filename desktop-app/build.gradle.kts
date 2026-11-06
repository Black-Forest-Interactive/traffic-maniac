import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("trafficmaniac.jvm")
    id("trafficmaniac.compose")
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.serialization.json)
}

compose.desktop {
    application {
        mainClass = "de.bfi.trafficmaniac.desktop.MainKt"

        nativeDistributions {
            packageName = "traffic-maniac"
            packageVersion = "0.1.0"
            // MSI und DMG verlangen eine Hauptversion >= 1 und kommen mit Version 1.0
            targetFormats(TargetFormat.Deb)
        }
    }
}

// Beim Start aus Gradle relativ zum Projektverzeichnis laufen, damit data/cities gefunden wird
tasks.withType<JavaExec>().configureEach {
    workingDir = rootDir
}

tasks.register<JavaExec>("benchmark") {
    description = "Misst die Simulationsleistung ohne Oberfläche (Pforzheim, eine simulierte Stunde)."
    group = "application"
    mainClass = "de.bfi.trafficmaniac.desktop.BenchmarkKt"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("headless") {
    description = "Spielt Strategien ohne Oberfläche durch (Balancing). Argumente: [Tage] [Strategien]"
    group = "application"
    mainClass = "de.bfi.trafficmaniac.desktop.HeadlessKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx3g")
}
