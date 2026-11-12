// Android-App: gleicher Spielkern und dieselbe Karte wie am Desktop, eigene Hülle (Start, Speicherort, Touch).
plugins {
    id("trafficmaniac.android-app")
}

// Version aus dem Release-Tag (-PappVersion=0.2.0 -PappVersionCode=7), sonst die Entwicklerversion
val appVersion = providers.gradleProperty("appVersion").getOrElse("0.1.0-dev")
val appVersionCode = providers.gradleProperty("appVersionCode").getOrElse("1").toInt()

// Release-Schlüssel aus Umgebungsvariablen (CI-Secrets bzw. ~/.android/traffic-maniac-release.properties);
// ohne sie wird auch das Release mit dem Debug-Schlüssel signiert – installierbar, aber nicht updatefähig
val keystore =
    providers
        .environmentVariable("TM_KEYSTORE")
        .orNull
        ?.let(::file)
        ?.takeIf { it.isFile }

android {
    namespace = "de.bfi.trafficmaniac.android"
    defaultConfig {
        applicationId = "de.bfi.trafficmaniac"
        versionCode = appVersionCode
        versionName = appVersion
    }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = providers.environmentVariable("TM_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("TM_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("TM_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.activity.compose)
}

/** Stadtpakete, Übersetzungen und Balancing aus dem Repo als Assets in die APK (`cities/`, `lang/`, `balancing/`). */
abstract class GameAssets : DefaultTask() {
    /** Die Repo-Ordner, nach Asset-Ordnername. */
    @get:Internal
    abstract val folders: MapProperty<String, File>

    @get:InputFiles
    abstract val tracked: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            into(outputDirectory)
            folders.get().forEach { (name, folder) ->
                from(folder) {
                    into(name)
                    include(ASSET_PATTERNS)
                }
            }
        }
        // Ohne Karte wäre die App nutzlos (map.city ist nicht eingecheckt, siehe scripts/import-city.sh)
        check(
            outputDirectory
                .get()
                .asFile
                .walk()
                .any { it.name == "map.city" },
        ) {
            "Keine Stadtkarte in cities/*/map.city – erst scripts/import-city.sh <id> ausführen"
        }
    }

    companion object {
        val ASSET_PATTERNS = listOf("*/city.json", "*/map.city", "*.json")
    }
}

val gameAssets =
    tasks.register<GameAssets>("gameAssets") {
        val folders = listOf("cities", "lang", "balancing").associateWith { rootProject.file(it) }
        this.folders.set(folders)
        folders.values.forEach { tracked.from(fileTree(it) { include(GameAssets.ASSET_PATTERNS) }) }
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(gameAssets, GameAssets::outputDirectory)
    }
}
