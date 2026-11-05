import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("trafficmaniac.jvm")
    id("trafficmaniac.compose")
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
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
