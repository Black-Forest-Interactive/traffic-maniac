package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.ui.Ambience
import de.bfi.trafficmaniac.ui.SoundEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Kleiner Software-Synthesizer für die Geräuschkulisse – alle Klänge werden berechnet, es gibt keine Audiodateien:
 *
 * - **Verkehrsrauschen**: braunes Rauschen, lauter mit mehr Verkehr und näherem Zoom.
 * - **Hupkonzert**: zufällige, leicht verstimmte Huptöne, umso häufiger, je mehr Autos im Stau stehen.
 * - **Martinshorn**: die zwei Töne im Wechsel, solange Blaulicht unterwegs ist.
 * - **Effekte**: Blitzer-Klick mit "Pling", Kassenklingeln, Knopfdruck.
 * - **Musik**: ein fröhlich-bürokratischer Blaskapellen-Marsch (Tuba auf 1 und 3, Akkorde auf 2 und 4, Melodie).
 *
 * [render] füllt einen Puffer mit Samples von −1 bis 1; Lautstärkewechsel werden geglättet, damit nichts knackt.
 */
class Synth(
    private val sampleRate: Int = SAMPLE_RATE,
    seed: Int = 7,
) {
    private val random = Random(seed)
    private val voices = ArrayList<Voice>()
    private var sample = 0L

    @Volatile var ambience = Ambience()

    @Volatile var muted = false

    @Volatile var music = true

    private var traffic = 0.0
    private var brown = 0.0
    private var sirenPhase = 0.0
    private var sirenGain = 0.0
    private var nextEighth = 0L
    private var eighth = 0

    /** Ein Ton mit Hüllkurve; [sweepTo] lässt die Frequenz über die Dauer gleiten. */
    private class Voice(
        val wave: Wave,
        val frequency: Double,
        val start: Long,
        val length: Long,
        val volume: Double,
        /** Abklingen pro Sekunde (0 = gleichbleibend). */
        val decay: Double = 0.0,
        val sweepTo: Double = frequency,
    ) {
        var phase = 0.0
    }

    private enum class Wave { SINE, TRIANGLE, SQUARE, PULSE, NOISE }

    fun play(effect: SoundEffect) {
        synchronized(voices) {
            when (effect) {
                SoundEffect.CAMERA_FLASH -> {
                    voice(Wave.NOISE, 0.0, 0.0, CLICK_SECONDS, CAMERA_CLICK_VOLUME)
                    voice(
                        Wave.SINE,
                        PLING_FROM_HZ,
                        CLICK_SECONDS,
                        PLING_SECONDS,
                        PLING_VOLUME,
                        PLING_DECAY,
                        PLING_TO_HZ,
                    )
                }

                SoundEffect.CASH -> {
                    voice(Wave.NOISE, 0.0, 0.0, KA_SECONDS, KA_VOLUME)
                    voice(Wave.SINE, BELL_LOW_HZ, KA_SECONDS, BELL_SECONDS, BELL_VOLUME, BELL_DECAY)
                    voice(Wave.SINE, BELL_HIGH_HZ, KA_SECONDS * 2, BELL_SECONDS, BELL_VOLUME, BELL_DECAY)
                }

                SoundEffect.CLICK -> {
                    voice(Wave.SQUARE, TICK_HZ, 0.0, TICK_SECONDS, TICK_VOLUME, TICK_DECAY)
                }
            }
        }
    }

    private fun voice(
        wave: Wave,
        frequency: Double,
        delaySeconds: Double,
        seconds: Double,
        volume: Double,
        decay: Double = 0.0,
        sweepTo: Double = frequency,
    ) {
        if (voices.size >= MAX_VOICES) return
        val start = sample + (delaySeconds * sampleRate).toLong()
        voices += Voice(wave, frequency, start, (seconds * sampleRate).toLong(), volume, decay, sweepTo)
    }

    /** Füllt [out] mit den nächsten Samples. */
    fun render(out: FloatArray) {
        val now = ambience
        synchronized(voices) {
            for (i in out.indices) {
                if (music) sequence()
                maybeHonk(now.honking)
                var value = rumble(now.traffic) + siren(now.siren)
                value += sumVoices()
                sample++
                out[i] = if (muted) 0f else tanh(value * MASTER).toFloat()
            }
            voices.removeAll { sample > it.start + it.length }
        }
    }

    private fun rumble(target: Float): Double {
        traffic += (target * RUMBLE_VOLUME - traffic) * SMOOTHING
        brown = (brown + random.nextDouble(-1.0, 1.0) * BROWN_STEP) / BROWN_LEAK
        return brown * traffic
    }

    private fun siren(on: Boolean): Double {
        sirenGain += ((if (on) SIREN_VOLUME else 0.0) - sirenGain) * SMOOTHING
        if (sirenGain < SILENCE) return 0.0
        // Martinshorn: zwei Töne im Wechsel
        val high = (sample / (SIREN_TONE_SECONDS * sampleRate)).toLong() % 2 == 1L
        sirenPhase += (if (high) SIREN_HIGH_HZ else SIREN_LOW_HZ) / sampleRate
        return triangle(sirenPhase) * sirenGain
    }

    private fun maybeHonk(honking: Float) {
        if (honking <= 0f || random.nextDouble() >= honking * HONKS_PER_SECOND / sampleRate) return
        val pitch = random.nextDouble(HONK_MIN_HZ, HONK_MAX_HZ)
        val seconds = random.nextDouble(HONK_MIN_SECONDS, HONK_MAX_SECONDS)
        voice(Wave.SQUARE, pitch, 0.0, seconds, HONK_VOLUME)
        voice(Wave.SQUARE, pitch * HONK_DETUNE, 0.0, seconds, HONK_VOLUME)
    }

    private fun sumVoices(): Double {
        var sum = 0.0
        for (v in voices) {
            val age = sample - v.start
            if (age < 0 || age > v.length) continue
            val t = age.toDouble() / sampleRate
            val progress = age.toDouble() / v.length
            val frequency = v.frequency + (v.sweepTo - v.frequency) * progress
            v.phase += frequency / sampleRate
            val envelope = envelope(age, v.length) * (if (v.decay > 0) exp(-v.decay * t) else 1.0)
            sum += wave(v.wave, v.phase) * envelope * v.volume
        }
        return sum
    }

    private fun wave(
        wave: Wave,
        phase: Double,
    ): Double =
        when (wave) {
            Wave.SINE -> sin(2 * PI * phase)
            Wave.TRIANGLE -> triangle(phase)
            Wave.SQUARE -> if (phase % 1.0 < HALF) 1.0 else -1.0
            Wave.PULSE -> if (phase % 1.0 < PULSE_WIDTH) 1.0 else -1.0
            Wave.NOISE -> random.nextDouble(-1.0, 1.0)
        }

    private fun triangle(phase: Double): Double {
        val p = phase % 1.0
        return 1 - TRIANGLE_SLOPE * abs(p - HALF)
    }

    /** Kurzes Ein- und Ausblenden gegen Knackser. */
    private fun envelope(
        age: Long,
        length: Long,
    ): Double {
        val fade = (FADE_SECONDS * sampleRate).toLong().coerceAtMost(length / 2).coerceAtLeast(1)
        return when {
            age < fade -> age.toDouble() / fade
            length - age < fade -> (length - age).toDouble() / fade
            else -> 1.0
        }
    }

    /** Der Marsch: auf jedem Achtel Melodie, auf 1 und 3 die Tuba, auf 2 und 4 die Akkorde. */
    private fun sequence() {
        if (sample < nextEighth) return
        val eighthSamples = (sampleRate * SECONDS_PER_MINUTE / TEMPO_BPM / 2).toLong()
        nextEighth = sample + eighthSamples
        val bar = eighth / EIGHTHS_PER_BAR % CHORDS.size
        val position = eighth % EIGHTHS_PER_BAR
        val root = CHORDS[bar]
        val eighthSeconds = eighthSamples.toDouble() / sampleRate
        // Auf den Vierteln: 1 und 3 die Tuba (Grundton, dann Quinte), 2 und 4 die Akkorde
        if (position % QUARTER == 0) {
            val beat = position / QUARTER
            if (beat % 2 == 0) {
                val bass = root - OCTAVE * 2 + if (beat == THIRD_BEAT) FIFTH else 0
                voice(Wave.TRIANGLE, frequency(bass), 0.0, eighthSeconds * BASS_LENGTH, BASS_VOLUME, BASS_DECAY)
            } else {
                for (interval in listOf(0, MAJOR_THIRD, FIFTH)) {
                    voice(
                        Wave.TRIANGLE,
                        frequency(root + interval),
                        0.0,
                        eighthSeconds * CHORD_LENGTH,
                        CHORD_VOLUME,
                        CHORD_DECAY,
                    )
                }
            }
        }
        MELODY[eighth % MELODY.size]?.let { (note, eighths) ->
            voice(Wave.PULSE, frequency(note), 0.0, eighthSeconds * eighths * LEGATO, MELODY_VOLUME, MELODY_DECAY)
        }
        eighth++
    }

    companion object {
        const val SAMPLE_RATE = 44_100

        private const val MASTER = 0.9
        private const val MAX_VOICES = 48
        private const val SMOOTHING = 0.0005
        private const val SILENCE = 1e-4
        private const val HALF = 0.5
        private const val PULSE_WIDTH = 0.3
        private const val FADE_SECONDS = 0.004
        private const val RUMBLE_VOLUME = 0.6
        private const val BROWN_STEP = 0.04
        private const val BROWN_LEAK = 1.002
        private const val SIREN_VOLUME = 0.07
        private const val SIREN_LOW_HZ = 440.0
        private const val SIREN_HIGH_HZ = 585.0
        private const val SIREN_TONE_SECONDS = 0.65
        private const val HONKS_PER_SECOND = 2.5
        private const val HONK_MIN_HZ = 330.0
        private const val HONK_MAX_HZ = 520.0
        private const val HONK_MIN_SECONDS = 0.12
        private const val HONK_MAX_SECONDS = 0.45
        private const val HONK_DETUNE = 1.06
        private const val HONK_VOLUME = 0.035
        private const val CLICK_SECONDS = 0.015
        private const val CAMERA_CLICK_VOLUME = 0.3
        private const val PLING_FROM_HZ = 2_000.0
        private const val PLING_TO_HZ = 4_000.0
        private const val PLING_SECONDS = 0.25
        private const val PLING_VOLUME = 0.12
        private const val PLING_DECAY = 8.0
        private const val KA_SECONDS = 0.03
        private const val KA_VOLUME = 0.25
        private const val BELL_LOW_HZ = 1_318.5
        private const val BELL_HIGH_HZ = 1_975.5
        private const val BELL_SECONDS = 0.7
        private const val BELL_VOLUME = 0.14
        private const val BELL_DECAY = 5.0
        private const val TICK_HZ = 1_500.0
        private const val TICK_SECONDS = 0.012
        private const val TICK_VOLUME = 0.06
        private const val TICK_DECAY = 200.0
        private const val TEMPO_BPM = 112.0
        private const val SECONDS_PER_MINUTE = 60.0
        private const val EIGHTHS_PER_BAR = 8
        private const val QUARTER = 2
        private const val THIRD_BEAT = 2
        private const val BASS_LENGTH = 1.6
        private const val CHORD_LENGTH = 0.7
        private const val TRIANGLE_SLOPE = 4.0
        private const val BASS_VOLUME = 0.16
        private const val BASS_DECAY = 3.0
        private const val CHORD_VOLUME = 0.035
        private const val CHORD_DECAY = 6.0
        private const val MELODY_VOLUME = 0.045
        private const val MELODY_DECAY = 1.5
        private const val LEGATO = 0.9
        private const val OCTAVE = 12
        private const val MAJOR_THIRD = 4
        private const val FIFTH = 7
        private const val A4_MIDI = 69
        private const val A4_HZ = 440.0

        private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /** MIDI-Notennummer aus "C4", "F#5" … */
        private fun midi(name: String): Int {
            val pitch = name.dropLast(1)
            val octave = name.last().digitToInt()
            return NOTE_NAMES.indexOf(pitch) + (octave + 1) * OCTAVE
        }

        private fun frequency(midi: Int): Double = A4_HZ * 2.0.pow((midi - A4_MIDI) / OCTAVE.toDouble())

        /** Akkorde je Takt (Grundton in der vierten Oktave). */
        private val CHORDS =
            "C F G C C F G C C F G C C F G C"
                .split(' ')
                .map { midi("${it}4") }

        /** Melodie in Achteln: "Note:Achtel", "R" = Pause; je Takt acht Achtel. */
        private val MELODY: List<Pair<Int, Int>?> =
            (
                "E5:2 G5:2 C6:2 G5:2 | A5:2 F5:2 C5:4 | B4:2 D5:2 G5:2 F5:2 | E5:4 C5:4 | " +
                    "E5:1 E5:1 G5:2 E5:2 C5:2 | F5:2 A5:2 C6:4 | B5:2 A5:2 G5:2 D5:2 | C5:6 R:2 | " +
                    "G5:2 E5:2 G5:2 C6:2 | A5:2 F5:2 A5:2 C6:2 | G5:2 F5:2 E5:2 D5:2 | E5:4 G5:4 | " +
                    "C6:2 B5:2 A5:2 G5:2 | F5:2 A5:2 G5:2 F5:2 | E5:2 D5:2 G4:2 B4:2 | C5:6 R:2"
            ).split(' ')
                .filter { it != "|" }
                .flatMap { token ->
                    val (note, length) = token.split(':')
                    val eighths = length.toInt()
                    val first = if (note == "R") null else midi(note) to eighths
                    listOf(first) + List(eighths - 1) { null }
                }
    }
}
