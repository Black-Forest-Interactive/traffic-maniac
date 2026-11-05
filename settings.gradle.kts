rootProject.name = "traffic-maniac"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
}

include(
    ":sim-engine",
    ":game",
    ":ui",
    ":desktop-app",
    ":osm-import",
)
