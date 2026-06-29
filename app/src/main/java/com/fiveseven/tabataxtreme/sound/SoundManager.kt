package com.fiveseven.tabataxtreme.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.fiveseven.tabataxtreme.R
import com.fiveseven.tabataxtreme.model.PhaseType

class SoundManager(context: Context) {

    private val soundPool: SoundPool

    private val countdownId: Int
    private val endRestId: Int
    private val phaseStartIds: Map<PhaseType, Int>

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()

        countdownId = soundPool.load(context, R.raw.beep_countdown, 1)
        endRestId = soundPool.load(context, R.raw.end_rest, 1)


        phaseStartIds = mapOf(
            PhaseType.WORK to soundPool.load(context, R.raw.start_work, 1),
            PhaseType.REST to soundPool.load(context, R.raw.start_rest, 1),
            PhaseType.BETWEEN_SETS to soundPool.load(context, R.raw.start_between_sets, 1),
            PhaseType.DONE to soundPool.load(context, R.raw.done, 1),
        )
    }

    fun playCountdownBeep() {
        soundPool.play(countdownId, 1f, 1f, 0, 0, 1f)
    }
    fun playEndRest() {
        soundPool.play(endRestId, 1f, 1f, 0, 0, 1f)
    }

    fun playPhaseStart(type: PhaseType) {
        val id = phaseStartIds[type] ?: return
        soundPool.play(id, 1f, 1f, 0, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
