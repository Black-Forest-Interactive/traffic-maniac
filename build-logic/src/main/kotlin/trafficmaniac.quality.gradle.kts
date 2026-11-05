// Gemeinsame Code-Qualität für alle Module: ktlint (Formatierung) und detekt (statische Analyse)
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

val libs = versionCatalogs.named("libs")

ktlint {
    version = libs.findVersion("ktlint").get().requiredVersion
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Der Standard-Task "detekt" findet in Multiplatform-Modulen keine Quellen.
// Deshalb hängt "check" an den Tasks pro Source-Set (ohne Typauflösung, damit es schnell bleibt).
tasks.named("check") {
    dependsOn(
        tasks.matching { it.name.startsWith("detekt") && it.name.endsWith("SourceSet") && !it.name.startsWith("detektBaseline") },
    )
}
