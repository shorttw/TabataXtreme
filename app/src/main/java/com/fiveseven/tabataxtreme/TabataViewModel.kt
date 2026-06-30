// SPDX-License-Identifier: MIT
package com.fiveseven.tabataxtreme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fiveseven.tabataxtreme.data.AppState
import com.fiveseven.tabataxtreme.data.PrefsRepository
import com.fiveseven.tabataxtreme.model.PhaseType
import com.fiveseven.tabataxtreme.model.Preset
import com.fiveseven.tabataxtreme.model.WorkoutConfig
import com.fiveseven.tabataxtreme.model.buildPhasePlan
import com.fiveseven.tabataxtreme.sound.SoundManager
import com.fiveseven.tabataxtreme.timer.EngineTick
import com.fiveseven.tabataxtreme.timer.TimeWarpEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class UiState(
    val config: WorkoutConfig = WorkoutConfig(),
    val presets: List<Preset> = emptyList(),
    val tick: EngineTick? = null,
    val editPresetName: String = "",
)

class TabataViewModel(
    private val repo: PrefsRepository,
    private val sound: SoundManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var engine: TimeWarpEngine = TimeWarpEngine(buildPhasePlan(_ui.value.config), _ui.value.config.timeMultiplier)
    private var loopJob: Job? = null

    // Used to avoid repeated beeps for the same second
    private var lastWholeSecondRemaining: Long = Long.MAX_VALUE
    private var lastPhaseIndex: Int = -1

    init {
        viewModelScope.launch {
            repo.stateFlow.collectLatest { state: AppState ->
                val cfg = state.lastConfig
                _ui.value = _ui.value.copy(
                    config = cfg,
                    presets = state.presets
                )
                rebuildEngine(cfg, keepRunning = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }

    fun onPresetNameChange(s: String) {
        _ui.value = _ui.value.copy(editPresetName = s)
    }

    fun updateConfig(update: (WorkoutConfig) -> WorkoutConfig) {
        val next = update(_ui.value.config).sanitized()
        _ui.value = _ui.value.copy(config = next)
        engine.setMultiplier(next.timeMultiplier)
        viewModelScope.launch { repo.setLastConfig(next) }

        // If not running, rebuild plan so preview/totals match new structure
        val running = _ui.value.tick?.isRunning == true
        if (!running) rebuildEngine(next, keepRunning = false)
    }

    fun startPauseToggle() {
        val running = _ui.value.tick?.isRunning == true
        if (running) {
            engine.pause()
        } else {
            engine.start()
            ensureLoop()
        }
        // immediately publish a tick
        publishTick()
    }

    fun reset() {
        engine.reset()
        lastWholeSecondRemaining = Long.MAX_VALUE
        lastPhaseIndex = -1
        publishTick()
    }

    fun skip() {
        engine.skipToNextPhase()
        publishTick()
        ui.value.tick?.phase?.type?.let { sound.playPhaseStart(it) }
        lastWholeSecondRemaining = Long.MAX_VALUE
    }


    fun savePreset() {
        val name = _ui.value.editPresetName.trim()
        if (name.isEmpty()) return
        val preset = Preset(name = name, config = _ui.value.config)
        viewModelScope.launch {
            repo.upsertPreset(preset)
            _ui.value = _ui.value.copy(editPresetName = "")
        }
    }

    fun loadPreset(preset: Preset) {
        _ui.value = _ui.value.copy(config = preset.config)
        viewModelScope.launch { repo.setLastConfig(preset.config) }
        rebuildEngine(preset.config, keepRunning = false)
        publishTick()
    }

    fun deletePreset(name: String) {
        viewModelScope.launch { repo.deletePreset(name) }
    }

    private fun ensureLoop() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            while (true) {
                publishTick()
                // 60fps-ish for smooth progress; nominal time remains stable
                delay(16)
            }
        }
    }

    private fun rebuildEngine(cfg: WorkoutConfig, keepRunning: Boolean) {
        val wasRunning = _ui.value.tick?.isRunning == true
        engine = TimeWarpEngine(buildPhasePlan(cfg), cfg.timeMultiplier)
        if (keepRunning && wasRunning) engine.start()
        lastWholeSecondRemaining = Long.MAX_VALUE
        lastPhaseIndex = -1
    }

    private fun publishTick() {
        val tick = engine.tick()
        _ui.value = _ui.value.copy(tick = tick)

        // Phase transition: play distinct sound for the phase we just entered
        if (tick.phaseIndex != lastPhaseIndex) {
            if (lastPhaseIndex >= 0) {
                sound.playPhaseStart(tick.phase.type)
            }
            lastPhaseIndex = tick.phaseIndex
            lastWholeSecondRemaining = Long.MAX_VALUE
        }

        // 3-second countdown beeps during PREPARE and REST and WORK
        if (
            tick.phase.type == PhaseType.PREPARE ||
            tick.phase.type == PhaseType.REST   ||
            tick.phase.type == PhaseType.WORK
        ) {
            val wholeSecRemaining = ceil(tick.nominalRemainingMs / 1000.0).toLong()
            if (wholeSecRemaining != lastWholeSecondRemaining) {
                if (wholeSecRemaining in 2..4) { // This is a bit odd in that really it should be 1 and 3 range but is like this for a good reason
                    sound.playCountdownBeep()
                }
                lastWholeSecondRemaining = wholeSecRemaining
            }
        }
        // Warning 10 seconds before end of BETWEENSETS
        if (
            tick.phase.type == PhaseType.BETWEEN_SETS
        ) {
            val wholeSecRemaining = ceil(tick.nominalRemainingMs / 1000.0).toLong()
            if (wholeSecRemaining != lastWholeSecondRemaining) {
                if (wholeSecRemaining.toInt() == 6) { // This is a bit odd should be 5 but is like it for a good reason
                    sound.playEndRest()
                }
                lastWholeSecondRemaining = wholeSecRemaining
            }
        }

        if (!tick.isRunning && tick.phase.label == "Done") {
            loopJob?.cancel()
            loopJob = null
        }
    }

// Define limits for configs
    private fun WorkoutConfig.sanitized(): WorkoutConfig = copy(
        prepareSec = prepareSec.coerceIn(3, 120),
        workSec = workSec.coerceIn(3, 600),
        restSec = restSec.coerceIn(0, 600),
        roundsPerSet = roundsPerSet.coerceIn(1, 50),
        sets = sets.coerceIn(1, 20),
        restBetweenSetsSec = restBetweenSetsSec.coerceIn(0, 1800),
        timeMultiplier = timeMultiplier.coerceIn(0.25f, 3.0f),
    )

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = PrefsRepository(appContext)
                    val sound = SoundManager(appContext)
                    return TabataViewModel(repo, sound) as T
                }
            }
    }
}
