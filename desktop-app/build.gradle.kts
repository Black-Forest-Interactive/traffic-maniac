import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("trafficmaniac.jvm")
    id("trafficmaniac.compose")
    id("trafficmaniac.serialization")
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kryo)
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

// Beim Start aus Gradle relativ zum Projektverzeichnis laufen, damit cities/ und balancing/ gefunden werden
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

// Eigenständige Kopie für lange Kopflos-Läufe: Weiterentwickeln stört laufende Partien dann nicht
tasks.register<Sync>("headlessDist") {
    description =
        "Kopiert Klassen und Bibliotheken nach build/headless (Start: java -cp 'desktop-app/build/headless/*' ...)."
    group = "application"
    from(configurations.runtimeClasspath)
    from(tasks.jar)
    into(layout.buildDirectory.dir("headless"))
}
