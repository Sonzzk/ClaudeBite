package com.sonzzk.claudebite.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tiny 8-bit sound effects, synthesised on the fly (square waves), so the app ships no audio files.
 * Each effect is a list of (frequency Hz, milliseconds). 0 Hz = a short rest.
 */
object Chiptune {
    private const val RATE = 22_050
    private val worker = Executors.newSingleThreadExecutor()

    val TAP = listOf(880 to 35)
    val POP = listOf(660 to 40, 990 to 50)
    val SAVE = listOf(523 to 60, 659 to 60, 784 to 90)
    val COIN = listOf(988 to 60, 1319 to 140)
    val LEVEL_UP = listOf(523 to 80, 659 to 80, 784 to 80, 1047 to 220)
    val POUT = listOf(330 to 70, 262 to 120)
    val QUEST = listOf(784 to 70, 0 to 20, 784 to 70, 1047 to 180)

    fun play(notes: List<Pair<Int, Int>>, volume: Float = 0.18f) {
        worker.execute {
            runCatching {
                val total = notes.sumOf { it.second } * RATE / 1000
                val pcm = ShortArray(total)
                var i = 0
                for ((freq, ms) in notes) {
                    val n = ms * RATE / 1000
                    for (k in 0 until n) {
                        if (i >= total) break
                        val env = 1f - k.toFloat() / n // simple decay
                        val v = if (freq == 0) 0f else {
                            val phase = (k.toDouble() * freq / RATE) % 1.0
                            val square = if (phase < 0.5) 1f else -1f
                            // soften with a little sine so it's cute, not harsh
                            0.7f * square + 0.3f * sin(2 * PI * phase).toFloat()
                        }
                        pcm[i++] = (v * env * volume * Short.MAX_VALUE).toInt().toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(notes.sumOf { it.second }.toLong() + 60)
                track.release()
            }
        }
    }
}

/** Sound + haptics in one call, respecting the Settings toggles. */
class Feedback(private val sounds: Boolean, private val haptics: Boolean, private val buzz: (HapticFeedbackType) -> Unit) {
    fun tap() { if (haptics) buzz(HapticFeedbackType.TextHandleMove); if (sounds) Chiptune.play(Chiptune.TAP) }
    fun pop() { if (haptics) buzz(HapticFeedbackType.TextHandleMove); if (sounds) Chiptune.play(Chiptune.POP) }
    fun success() { if (haptics) buzz(HapticFeedbackType.LongPress); if (sounds) Chiptune.play(Chiptune.SAVE) }
    fun coin() { if (sounds) Chiptune.play(Chiptune.COIN) }
    fun levelUp() { if (haptics) buzz(HapticFeedbackType.LongPress); if (sounds) Chiptune.play(Chiptune.LEVEL_UP) }
    fun quest() { if (haptics) buzz(HapticFeedbackType.LongPress); if (sounds) Chiptune.play(Chiptune.QUEST) }
    fun pout() { if (haptics) buzz(HapticFeedbackType.LongPress); if (sounds) Chiptune.play(Chiptune.POUT) }
}

@Composable
fun rememberFeedback(): Feedback {
    val settings = LocalAppSettings.current
    val haptic = LocalHapticFeedback.current
    return remember(settings.sounds, settings.haptics, haptic) {
        Feedback(settings.sounds, settings.haptics) { haptic.performHapticFeedback(it) }
    }
}
