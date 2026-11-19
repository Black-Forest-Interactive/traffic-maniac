plugins {
    id("trafficmaniac.jvm")
    id("trafficmaniac.serialization")
    application
}

dependencies {
    implementation(project(":city"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.osmpbf)
    implementation(libs.protobuf.java)
}

application {
    mainClass = "de.bfi.trafficmaniac.osmimport.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx2g")
}

// ./gradlew importCity -Pcity=pforzheim [-Prefresh]: OSM-Auszug laden (falls nötig) und cities/<id>/map.city erzeugen
tasks.register<JavaExec>("importCity") {
    description = "Erzeugt cities/<id>/map.city aus dem OSM-Auszug in city.json. Argumente: -Pcity=<id> [-Prefresh]"
    group = "application"
    mainClass = "de.bfi.trafficmaniac.osmimport.ImportCityKt"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    jvmArgs("-Xmx2g")
    val city = providers.gradleProperty("city")
    val refresh = providers.gradleProperty("refresh")
    argumentProviders +=
        CommandLineArgumentProvider {
            listOfNotNull(city.orNull ?: error("Stadt angeben: -Pcity=<id>"), refresh.orNull?.let { "--refresh" })
        }
}
