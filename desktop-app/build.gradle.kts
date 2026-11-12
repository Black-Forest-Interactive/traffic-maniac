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

// Version aus dem Release-Tag (-PappVersion=0.2.0-preview.1), sonst die Entwicklerversion
val appVersion: String = providers.gradleProperty("appVersion").getOrElse("0.1.0")

compose.desktop {
    application {
        mainClass = "de.bfi.trafficmaniac.desktop.MainKt"

        nativeDistributions {
            packageName = "traffic-maniac"
            // Version aus dem Release-Tag (-PappVersion=0.2.0-preview.1); Installer kennen nur MAJOR.MINOR.BUILD
            packageVersion = appVersion.substringBefore('-')
            description = "Verkehrsberuhigung als Geschäftsmodell"
            vendor = "Black Forest Interactive"
            // DMG verlangt eine Hauptversion >= 1 und kommt mit Version 1.0
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            // Kryo (Spielstände) braucht Unsafe; vorgeschlagen von ./gradlew suggestRuntimeModules
            modules("java.instrument", "java.sql", "jdk.unsupported")
            // Städte, Balancing, Übersetzungen und Grafiken liegen dem Installer bei (siehe GameFiles)
            appResourcesRootDir.set(layout.buildDirectory.dir("app-resources"))
            windows {
                menuGroup = "Traffic Maniac"
                menu = true
                shortcut = true
                // Ohne Administratorrechte, ins Benutzerprofil
                perUserInstall = true
                dirChooser = true
                // Gleiche ID für alle Versionen: ein neuer Installer ersetzt den alten
                upgradeUuid = "6f2b6f3c-4b7e-4f0e-9a51-3c1d2e8a7b42"
            }
        }
    }
}

/** Spieldaten für den Installer: `common/` landet im Installationsordner (`compose.application.resources.dir`). */
val gameResources =
    tasks.register<Sync>("gameResources") {
        into(layout.buildDirectory.dir("app-resources/common"))
        from(rootProject.file("cities")) {
            into("cities")
            include("*/city.json", "*/map.city", "*/LICENSE*")
        }
        from(rootProject.file("lang")) { into("lang") }
        from(rootProject.file("balancing")) { into("balancing") }
        from(rootProject.file("assets/sprites")) { into("assets/sprites") }
    }

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(gameResources)
    // Ohne Karte wäre der Installer nutzlos (map.city ist nicht eingecheckt, siehe scripts/import-city.sh)
    val maps = project.rootProject.fileTree("cities") { include("*/map.city") }
    doFirst {
        check(!maps.isEmpty) { "Keine Stadtkarte in cities/*/map.city – erst scripts/import-city.sh <id> ausführen" }
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
