package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.ui.Ambience
import de.bfi.trafficmaniac.ui.SoundEffect
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    private fun Synth.seconds(seconds: Double): FloatArray {
        val out = FloatArray((seconds * Synth.SAMPLE_RATE).toInt())
        render(out)
        return out
    }

    private fun rms(samples: FloatArray) = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)

    @Test
    fun everythingAtOnceStaysInRange() {
        val synth = Synth()
        synth.ambience = Ambience(traffic = 1f, honking = 1f, siren = true)
        SoundEffect.entries.forEach(synth::play)
        val samples = synth.seconds(3.0)
        assertTrue(samples.none { it.isNaN() }, "Keine ungültigen Samples")
        assertTrue(samples.all { abs(it) <= 1f }, "Begrenzt auf ±1")
        assertTrue(rms(samples) > 0.02, "Es ist etwas zu hören: ${rms(samples)}")
    }

    @Test
    fun musicPlaysOnItsOwnAndCanBeSwitchedOff() {
        val withMusic = Synth().seconds(2.0)
        assertTrue(rms(withMusic) > 0.01, "Marschmusik")
        val silent = Synth().apply { music = false }.seconds(2.0)
        assertEquals(0.0, rms(silent), "Ohne Musik und Verkehr ist es still")
    }

    @Test
    fun mutedMeansSilence() {
        val synth = Synth()
        synth.muted = true
        synth.ambience = Ambience(traffic = 1f, honking = 1f, siren = true)
        synth.play(SoundEffect.CASH)
        assertTrue(synth.seconds(1.0).all { it == 0f })
    }

    @Test
    fun moreTrafficIsLouder() {
        fun loudness(traffic: Float): Double {
            val synth = Synth().apply { music = false }
            synth.ambience = Ambience(traffic = traffic)
            synth.seconds(2.0)
            return rms(synth.seconds(1.0))
        }
        assertTrue(loudness(1f) > loudness(0.2f) * 2)
    }
}
