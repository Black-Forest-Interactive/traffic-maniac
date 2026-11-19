package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.ui.Ambience
import de.bfi.trafficmaniac.ui.NoSound
import de.bfi.trafficmaniac.ui.SoundBoard
import de.bfi.trafficmaniac.ui.SoundEffect
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/**
 * Tonausgabe über `javax.sound`: Ein Hintergrund-Thread lässt den [Synth] rechnen und schreibt die Samples
 * (44,1 kHz, 16 bit, mono) in die Soundkarte. Ohne Audiogerät bleibt das Spiel stumm ([open] liefert [NoSound]).
 */
class SynthSoundBoard private constructor(
    private val synth: Synth,
    private val line: SourceDataLine,
) : SoundBoard {
    override var muted: Boolean
        get() = synth.muted
        set(value) {
            synth.muted = value
        }

    override var music: Boolean
        get() = synth.music
        set(value) {
            synth.music = value
        }

    override fun play(effect: SoundEffect) = synth.play(effect)

    override fun ambience(ambience: Ambience) {
        synth.ambience = ambience
    }

    private fun start() {
        Thread({
            val samples = FloatArray(BLOCK_SAMPLES)
            val bytes = ByteArray(BLOCK_SAMPLES * 2)
            while (true) {
                synth.render(samples)
                for (i in samples.indices) {
                    val value = (samples[i] * Short.MAX_VALUE).toInt()
                    bytes[2 * i] = value.toByte()
                    bytes[2 * i + 1] = (value shr BITS_PER_BYTE).toByte()
                }
                line.write(bytes, 0, bytes.size)
            }
        }, "sound").apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val BLOCK_SAMPLES = 1_024
        private const val BUFFER_BYTES = 8_192
        private const val BITS_PER_BYTE = 8
        private const val SAMPLE_BITS = 16

        /** Öffnet die Soundkarte; ohne Audiogerät (z. B. auf einem Server) gibt es Stille. */
        fun open(): SoundBoard =
            try {
                val format = AudioFormat(Synth.SAMPLE_RATE.toFloat(), SAMPLE_BITS, 1, true, false)
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format, BUFFER_BYTES)
                line.start()
                SynthSoundBoard(Synth(), line).also { it.start() }
            } catch (e: LineUnavailableException) {
                println("Kein Audiogerät verfügbar – das Spiel bleibt stumm (${e.message})")
                NoSound
            } catch (e: IllegalArgumentException) {
                println("Audioformat nicht unterstützt – das Spiel bleibt stumm (${e.message})")
                NoSound
            }
    }
}
