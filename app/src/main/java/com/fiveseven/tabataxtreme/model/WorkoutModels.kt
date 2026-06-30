// SPDX-License-Identifier: MIT
package com.fiveseven.tabataxtreme.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlin.math.max

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WorkoutConfig(
    val prepareSec: Int = 10,
    val workSec: Int = 20,
    val restSec: Int = 10,
    val roundsPerSet: Int = 8,
    val sets: Int = 1,
    val restBetweenSetsSec: Int = 60,
    val timeMultiplier: Float = 1.0f, // virtual seconds per real second
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Preset(
    val name: String,
    val config: WorkoutConfig
)

enum class PhaseType { PREPARE,  WORK, REST, BETWEEN_SETS, DONE }


data class Phase(
    val type: PhaseType,
    val nominalDurationMs: Long,
    val label: String,
    val setIndex: Int,   // 1-based
    val roundIndex: Int  // 1-based
)

fun buildPhasePlan(cfg: WorkoutConfig): List<Phase> {
    val plan = mutableListOf<Phase>()

    fun add(type: PhaseType, sec: Int, label: String, set: Int, round: Int) {
        val s = max(0, sec)
        if (s == 0) return
        plan += Phase(type, s * 1000L, label, set, round)
    }

    add(PhaseType.PREPARE, cfg.prepareSec, "Prepare", 1, 1)

    val sets = max(1, cfg.sets)
    val rounds = max(1, cfg.roundsPerSet)

    for (set in 1..sets) {
        for (round in 1..rounds) {

            add(PhaseType.WORK, cfg.workSec, "Work", set, round)

            val isLastRound = (round == rounds)
            // Don't need to do interval rest if on last round.
            if (!(isLastRound)){
                add(PhaseType.REST, cfg.restSec, "Rest", set, round)
            }
        }

        if (set != sets) {
            add(PhaseType.BETWEEN_SETS, cfg.restBetweenSetsSec, "Rest Between Sets", set, 1)
        }
    }

    plan += Phase(PhaseType.DONE, 0L, "Done", sets, rounds)
    return plan
}

