import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Wo das Android-SDK liegt: `sdk.dir` in `local.properties`, sonst `ANDROID_HOME`/`ANDROID_SDK_ROOT`, sonst
 * `~/Android/Sdk` (Standard von Android Studio). `null`, wenn keines da ist – dann wird nur für den Desktop gebaut.
 */
fun androidSdkDir(rootDir: File): File? {
    val local = File(rootDir, "local.properties")
    val fromProperties =
        if (local.isFile) Properties().apply { local.inputStream().use { load(it) } }.getProperty("sdk.dir") else null
    return listOfNotNull(
        fromProperties,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        System.getProperty("user.home")?.let { "$it/Android/Sdk" },
    ).map(::File).firstOrNull { File(it, "platforms").isDirectory }
}

/** Gibt es ein Android-SDK? Dann bekommen die Module zusätzlich ein Android-Ziel. */
fun Project.hasAndroidSdk(): Boolean = androidSdkDir(rootDir) != null
