// Android-App: gleicher Spielkern und dieselbe Karte wie am Desktop, eigene Hülle (Start, Speicherort, Touch).
plugins {
    id("trafficmaniac.android-app")
}

android {
    namespace = "de.bfi.trafficmaniac.android"
    defaultConfig {
        applicationId = "de.bfi.trafficmaniac"
        versionCode = 1
        versionName = "0.1.0"
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
