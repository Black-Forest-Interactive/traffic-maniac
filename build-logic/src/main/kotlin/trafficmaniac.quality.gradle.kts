// Gemeinsame Code-Qualität für alle Module: ktlint (Formatierung) und detekt (statische Analyse)
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

val libs = versionCatalogs.named("libs")

ktlint {
    version = libs.findVersion("ktlint").get().requiredVersion
    filter {
        exclude { it.file.path.contains("${File.separator}build${File.separator}generated${File.separator}") }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Erzeugter Code (z. B. die Resource-Klassen von Compose) wird nicht geprüft
tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { it.file.path.contains("${File.separator}build${File.separator}generated${File.separator}") }
}

// Der Standard-Task "detekt" findet in Multiplatform-Modulen keine Quellen.
// Deshalb hängt "check" an den Tasks pro Source-Set (ohne Typauflösung, damit es schnell bleibt).
tasks.named("check") {
    dependsOn(
        tasks.matching { it.name.startsWith("detekt") && it.name.endsWith("SourceSet") && !it.name.startsWith("detektBaseline") },
    )
}
