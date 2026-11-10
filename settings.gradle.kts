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
    ":i18n",
    ":city",
    ":sim-engine",
    ":game",
    ":ui",
    ":desktop-app",
    ":osm-import",
)

// Die Android-App nur, wenn ein Android-SDK da ist (local.properties, ANDROID_HOME oder ~/Android/Sdk) –
// ohne SDK baut alles andere wie gewohnt.
val androidSdk =
    listOfNotNull(
        file("local.properties")
            .takeIf { it.isFile }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("="),
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        System.getProperty("user.home")?.let { "$it/Android/Sdk" },
    ).map(::File).firstOrNull { File(it, "platforms").isDirectory }
if (androidSdk != null) include(":android-app")
