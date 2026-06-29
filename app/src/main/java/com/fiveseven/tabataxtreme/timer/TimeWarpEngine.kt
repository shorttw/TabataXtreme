package com.fiveseven.tabataxtreme.timer

import android.os.SystemClock
import com.fiveseven.tabataxtreme.model.Phase
import com.fiveseven.tabataxtreme.model.PhaseType
import kotlin.math.max
import kotlin.math.min

data class EngineTick(
    val phase: Phase,
    val phaseIndex: Int,
    val phaseCount: Int,
    val nominalRemainingMs: Long,
    val nominalTotalMs: Long,
    val isRunning: Boolean,
)

class TimeWarpEngine(
    private val plan: List<Phase>,
    private var multiplier: Float,
) {
    private var phaseIndex: Int = 0
    private var virtualElapsedMsInPhase: Double = 0.0

    private var running: Boolean = false
    private var lastRealNanos: Long = 0L

    fun setMultiplier(m: Float) {
        multiplier = m.coerceIn(0.25f, 3.0f)
    }

    fun start() {
        if (running) return
        running = true
        lastRealNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun pause() {
        running = false
    }

    fun reset() {
        running = false
        phaseIndex = 0
        virtualElapsedMsInPhase = 0.0
    }

    fun skipToNextPhase() {
        if (phaseIndex >= plan.lastIndex) return
        phaseIndex++
        virtualElapsedMsInPhase = 0.0
    }

    fun currentPhase(): Phase = plan[min(phaseIndex, plan.lastIndex)]

    /**
     * Advance time by "real" time delta, scaled into "virtual" time by multiplier.
     * Returns tick state for UI.
     */
    fun tick(): EngineTick {
        var phase = currentPhase()
        if (running) {
            val now = SystemClock.elapsedRealtimeNanos()
            val realDeltaMs = (now - lastRealNanos) / 1_000_000.0
            lastRealNanos = now

            // virtual time passes faster or slower than real time
            if (phase.type == PhaseType.WORK) { // Only work phase has virtual time multiplier
                val scaled = realDeltaMs * multiplier.toDouble()
                virtualElapsedMsInPhase += scaled
            } else {
                virtualElapsedMsInPhase += realDeltaMs
            }
            stepPhasesIfNeeded()
            phase = currentPhase()  // Need this incase we're into next phase
        }


        val total = phase.nominalDurationMs
        val remaining = max(0L, (total - virtualElapsedMsInPhase).toLong())

        return EngineTick(
            phase = phase,
            phaseIndex = phaseIndex,
            phaseCount = plan.size,
            nominalRemainingMs = remaining,
            nominalTotalMs = total,
            isRunning = running
        )
    }

    private fun stepPhasesIfNeeded() {
        while (true) {
            val phase = currentPhase()
            val total = phase.nominalDurationMs
            if (phase.type == PhaseType.DONE) {
                running = false
                return
            }
            if (virtualElapsedMsInPhase < total) return

            // carry overflow into next phase
            val overflow = virtualElapsedMsInPhase - total
            if (phaseIndex >= plan.lastIndex) {
                running = false
                return
            }
            phaseIndex++
            virtualElapsedMsInPhase = overflow
        }
    }
}
