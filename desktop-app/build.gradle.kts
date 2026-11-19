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

/**
 * MSI kennt nur MAJOR.MINOR.BUILD und installiert nur über eine ältere Version drüber – bei gleicher Version bricht
 * es mit „Eine andere Version dieses Produkts ist bereits installiert“ ab. Deshalb bekommt jede Release eine eigene,
 * aufsteigende Nummer: BUILD = PATCH * 1000 + Vorabversion (Endversion = 999).
 * 0.2.0-preview.1 → 0.2.1, 0.2.0-preview.2 → 0.2.2, 0.2.0 → 0.2.999, 0.2.1-preview.1 → 0.2.1001
 */
fun msiVersion(version: String): String {
    val match =
        Regex("""(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z]*\.?(\d+))?""").matchEntire(version)
            ?: error("Version $version passt nicht zu MAJOR.MINOR.PATCH[-preview.N]")
    val (major, minor, patch, pre) = match.destructured
    val previewNumber = pre.toIntOrNull()
    require(previewNumber == null || previewNumber in 1..998) { "Vorabversion $version: Nummer 1 bis 998" }
    return "$major.$minor.${patch.toInt() * 1000 + (previewNumber ?: 999)}"
}

compose.desktop {
    application {
        mainClass = "de.bfi.trafficmaniac.desktop.MainKt"
        // Das installierte Spiel kennt seine Release-Version und lädt fehlende Karten von genau diesem Release (MapDownload)
        providers.gradleProperty("appVersion").orNull?.let { jvmArgs += "-Dtrafficmaniac.version=$it" }
        // Adresse des Statistik-Servers (Paket F); ohne sie bleibt alles im Postausgang auf dem Rechner
        providers.gradleProperty("statsUrl").orNull?.let { jvmArgs += "-Dtrafficmaniac.statsUrl=$it" }

        nativeDistributions {
            // Name in Startmenü, Desktop-Verknüpfung und „Apps & Features“
            packageName = "Traffic Maniac"
            packageVersion = appVersion.substringBefore('-')
            description = "Verkehrsberuhigung als Geschäftsmodell"
            copyright = "© Black Forest Interactive"
            vendor = "Black Forest Interactive"
            // DMG verlangt eine Hauptversion >= 1 und kommt mit Version 1.0
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            // Laut ./gradlew suggestRuntimeModules: Kryo (Spielstände) braucht Unsafe, Statistik und Karten-Download
            // brauchen java.net.http – fehlt ein Modul, stürzt erst das installierte Spiel ab (der Smoke-Test prüft es)
            modules("java.instrument", "java.net.http", "java.sql", "jdk.unsupported")
            // Städte, Balancing, Übersetzungen und Grafiken liegen dem Installer bei (siehe GameFiles)
            appResourcesRootDir.set(layout.buildDirectory.dir("app-resources"))
            linux {
                // Debian-Paketnamen sind klein und ohne Leerzeichen
                packageName = "traffic-maniac"
                // Vorabversionen sortieren mit ~ vor der Endversion (0.2.0~preview.1 < 0.2.0)
                debPackageVersion = appVersion.replace('-', '~')
                iconFile.set(rootProject.file("assets/icon/traffic-maniac.png"))
                shortcut = true
                menuGroup = "Game"
            }
            windows {
                menuGroup = "Traffic Maniac"
                menu = true
                shortcut = true
                // Ohne Administratorrechte nach %LOCALAPPDATA%; ohne Ordnerauswahl, damit ein Update sicher den
                // alten Ordner ersetzt statt eine zweite Kopie anzulegen
                perUserInstall = true
                dirChooser = false
                iconFile.set(rootProject.file("assets/icon/traffic-maniac.ico"))
                // Gleiche ID für alle Versionen + jedes Release eine höhere Nummer: ein neuer Installer ersetzt den alten
                upgradeUuid = "6f2b6f3c-4b7e-4f0e-9a51-3c1d2e8a7b42"
                msiPackageVersion = msiVersion(appVersion)
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
        // Lizenzen: eigene, Drittanbieter (u. a. Java-Laufzeit, siehe THIRD-PARTY.md) und Schriften
        from(rootProject.file("LICENSE"))
        from(rootProject.file("licenses")) { into("licenses") }
    }

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(gameResources)
    // Ohne Karte wäre der Installer nutzlos (map.city ist nicht eingecheckt, siehe ./gradlew importCity)
    val maps = project.rootProject.fileTree("cities") { include("*/map.city") }
    doFirst {
        check(
            !maps.isEmpty,
        ) { "Keine Stadtkarte in cities/*/map.city – erst ./gradlew importCity -Pcity=<id> ausführen" }
    }
}

// Fenstersymbol (Main.appIcon) aus derselben Quelle wie das Installer-Symbol
tasks.processResources {
    from(rootProject.file("assets/icon")) { include("traffic-maniac.png") }
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
