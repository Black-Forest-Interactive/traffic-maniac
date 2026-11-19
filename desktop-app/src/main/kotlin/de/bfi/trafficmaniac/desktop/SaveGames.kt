package de.bfi.trafficmaniac.desktop

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory.FieldSerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.SaveInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Spielstände im Ordner `~/.traffic-maniac/saves/`: je Spielstand eine kleine JSON-Datei mit den Eckdaten fürs Menü
 * und eine gepackte Momentaufnahme des ganzen Spiels. Die Stadtkarte selbst wird nicht mitgespeichert – sie ist
 * beim Laden dieselbe.
 */
class SaveGames(
    /** Karte zu einem Stadtpaket; `null`, wenn es die Stadt nicht (mehr) gibt. */
    private val maps: (String) -> CityMap?,
    private val directory: File = File(System.getProperty("user.home"), ".traffic-maniac/saves"),
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    /** Alle Spielstände, der neueste zuerst; beschädigte oder veraltete werden übersprungen. */
    fun list(): List<SaveInfo> =
        directory
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { json.decodeFromString(SaveInfo.serializer(), file.readText()) }.getOrNull()
            }.filter { it.version == SaveInfo.VERSION && snapshotFile(it.id).isFile }
            .sortedByDescending { it.savedAt }

    /** Speichert [game] unter [info]; ein Spielstand mit derselben Kennung wird überschrieben. */
    fun save(
        game: CityGame,
        info: SaveInfo,
    ) {
        directory.mkdirs()
        // Erst in Zwischendateien, dann umbenennen: ein Absturz beim Schreiben zerstört keinen alten Spielstand
        val snapshot = File(directory, "${info.id}.sav.tmp")
        val kryo = kryo(cityOf(info))
        Output(DeflaterOutputStream(snapshot.outputStream())).use { kryo.writeClassAndObject(it, game) }
        snapshot.replace(snapshotFile(info.id))
        val meta = File(directory, "${info.id}.json.tmp")
        meta.writeText(json.encodeToString(SaveInfo.serializer(), info) + "\n")
        meta.replace(File(directory, "${info.id}.json"))
    }

    /** Lädt das Spiel zu [info]. */
    fun load(info: SaveInfo): CityGame {
        val kryo = kryo(cityOf(info))
        return Input(InflaterInputStream(snapshotFile(info.id).inputStream())).use {
            kryo.readClassAndObject(it) as CityGame
        }
    }

    private fun cityOf(info: SaveInfo) = checkNotNull(maps(info.cityId)) { "Stadt ${info.cityName} fehlt" }

    fun delete(info: SaveInfo) {
        snapshotFile(info.id).delete()
        File(directory, "${info.id}.json").delete()
    }

    private fun snapshotFile(id: String) = File(directory, "$id.sav")

    private fun File.replace(target: File) {
        target.delete()
        check(renameTo(target)) { "$target konnte nicht geschrieben werden" }
    }

    private fun kryo(city: CityMap): Kryo =
        SingletonAwareKryo().apply {
            isRegistrationRequired = false
            references = true
            instantiatorStrategy = ConstructorOrObjenesis()
            // Lambdas merken sich ihr umgebendes Objekt in synthetischen Feldern (this$0) – die gehören dazu
            setDefaultSerializer(FieldSerializerFactory().apply { config.ignoreSyntheticFields = false })
            addDefaultSerializer(CityMap::class.java, Fixed(city))
            // Die Befehlswarteschlange ist beim Speichern leer genug: Ein neues Spiel bekommt eine frische
            addDefaultSerializer(Channel::class.java, Fresh { Channel<Any>(Channel.UNLIMITED) })
        }
}

/** Kotlin-`object`s (auch Begleitobjekte und Lambdas ohne Zustand) bleiben beim Laden dieselbe Instanz. */
private class SingletonAwareKryo : Kryo() {
    override fun getDefaultSerializer(type: Class<*>): Serializer<*> =
        singletonOf(type)?.let { Fixed(it) } ?: super.getDefaultSerializer(type)

    private fun singletonOf(type: Class<*>): Any? {
        val own = type.declaredFields.firstOrNull { it.isStaticOf(type) && it.name == "INSTANCE" }
        // Begleitobjekt: statisches Feld der äußeren Klasse, benannt wie das Objekt (Companion, Random.Default)
        val outer =
            type.declaringClass?.declaredFields?.firstOrNull {
                it.isStaticOf(type) && it.name == type.simpleName
            }
        val field = own ?: outer ?: return null
        return runCatching {
            field.isAccessible = true
            field.get(null)
        }.getOrNull()
    }

    private fun java.lang.reflect.Field.isStaticOf(type: Class<*>) = Modifier.isStatic(modifiers) && this.type == type
}

/**
 * Neue Objekte beim Laden: mit dem parameterlosen Konstruktor, wenn es einen gibt (JDK-Sammlungen brauchen ihre
 * Grundeinrichtung), sonst ohne Konstruktor über Objenesis – Kotlin-Klassen bekommen ihre Felder ohnehin aus dem
 * Spielstand.
 */
private class ConstructorOrObjenesis : InstantiatorStrategy {
    private val fallback = StdInstantiatorStrategy()

    override fun <T : Any> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
        val constructor =
            runCatching { type.getDeclaredConstructor().also { it.isAccessible = true } }.getOrNull()
                ?: return fallback.newInstantiatorOf(type)
        return ObjectInstantiator { constructor.newInstance() }
    }
}

/** Schreibt nichts und liefert beim Lesen immer [value]. */
private class Fixed<T>(
    private val value: T,
) : Serializer<T>() {
    override fun write(
        kryo: Kryo,
        output: Output,
        obj: T,
    ) = Unit

    override fun read(
        kryo: Kryo,
        input: Input,
        type: Class<out T>,
    ): T = value
}

/** Schreibt nichts und erzeugt beim Lesen ein neues Objekt. */
private class Fresh<T>(
    private val create: () -> T,
) : Serializer<T>() {
    override fun write(
        kryo: Kryo,
        output: Output,
        obj: T,
    ) = Unit

    override fun read(
        kryo: Kryo,
        input: Input,
        type: Class<out T>,
    ): T = create()
}
