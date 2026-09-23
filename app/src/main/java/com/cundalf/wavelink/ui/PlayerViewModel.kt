package com.cundalf.wavelink.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.cundalf.wavelink.SubWaveCastApplication
import com.cundalf.wavelink.data.Language
import com.cundalf.wavelink.data.NowPlaying
import com.cundalf.wavelink.data.Radio
import com.cundalf.wavelink.data.RadioState
import com.cundalf.wavelink.data.RequestSubmission
import com.cundalf.wavelink.data.SubWaveApi
import com.cundalf.wavelink.data.SubWaveRequests
import com.cundalf.wavelink.playback.CastConfig
import com.cundalf.wavelink.playback.NowPlayingEvents
import com.cundalf.wavelink.playback.PlaybackError
import com.cundalf.wavelink.playback.PlaybackEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estados de la hoja "Pedir a la cabina". */
sealed interface RequestUiState {
    data object Idle : RequestUiState

    data object Sending : RequestUiState

    data class Sent(val ack: String? = null) : RequestUiState

    data class Resolved(
        val ack: String?,
        val track: String?,
    ) : RequestUiState

    data class Closed(val serverMessage: String?) : RequestUiState

    data class Error(val serverMessage: String?) : RequestUiState
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SubWaveCastApplication
    private val repository = app.radioRepository
    private val settings = app.settingsRepository
    private val playback = PlaybackClient(application)

    val state: StateFlow<RadioState> = repository.state
    val controller = playback.controller
    val playerState: StateFlow<PlaybackUiState> = playback.playerState
    val errorMessage: StateFlow<PlaybackError?> = PlaybackEvents.error
    val castEnabled: Boolean = CastConfig.hasAppId(application)
    val welcomeShown: Flow<Boolean> = settings.welcomeShown
    val language: StateFlow<Language> = app.languageStore.language

    /**
     * Now-playing publicado por el servicio (fuente única de polling contra la
     * estación); solo se expone si corresponde a la radio que está cargada.
     */
    val nowPlaying: StateFlow<NowPlaying?> =
        combine(NowPlayingEvents.update, playerState) { update, player ->
            update?.forRadio(player.mediaId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _requestState = MutableStateFlow<RequestUiState>(RequestUiState.Idle)
    val requestState: StateFlow<RequestUiState> = _requestState.asStateFlow()
    private var requestJob: Job? = null

    init {
        playback.connect()
    }

    fun playRadio(radio: Radio) = playback.playRadio(radio)

    fun togglePlay() {
        val controller = controller.value ?: return
        if (controller.currentMediaItem == null) {
            val default = state.value.default
            if (default != null) playback.playRadio(default)
        } else {
            playback.togglePlay()
        }
    }

    fun playDefaultIfIdle() {
        val controller = controller.value ?: return
        if (controller.isPlaying) return
        val current = controller.currentMediaItem
        if (current == null) {
            val default = state.value.default ?: return
            playback.playRadio(default)
        } else {
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
    }

    /** Reconecta el stream para volver al punto en vivo (pausas/microcortes). */
    val resync: () -> Unit = playback::resync

    fun setDefault(radio: Radio) =
        viewModelScope.launch {
            repository.setDefault(radio.id)
        }

    fun delete(radio: Radio) =
        viewModelScope.launch {
            // Si la radio eliminada es la que está sonando, se corta la
            // reproducción y se limpia la barra (mediaId -> null).
            if (playerState.value.mediaId == radio.id) {
                playback.stop()
            }
            repository.delete(radio.id)
        }

    fun save(
        radio: Radio?,
        nombre: String,
        url: String,
        password: String?,
        showNowPlaying: Boolean,
        allowRequests: Boolean,
    ) = viewModelScope.launch {
        if (radio == null) {
            repository.add(nombre, url, password, showNowPlaying, allowRequests)
        } else {
            repository.update(radio.id, nombre, url, password, showNowPlaying, allowRequests)
        }
    }

    fun currentRadio(): Radio? {
        val mediaId = playerState.value.mediaId ?: return null
        return state.value.radios.firstOrNull { it.id == mediaId }
    }

    // -----------------------------------------------------------------------
    // Pedidos a la cabina (POST /api/request + polling del resultado)
    // -----------------------------------------------------------------------

    fun sendRequest(
        text: String,
        name: String,
    ) {
        val radio = currentRadio() ?: return
        val base = SubWaveApi.apiBase(radio.streamUrl) ?: return
        val requestText = text.trim()
        if (requestText.isBlank()) return
        requestJob?.cancel()
        _requestState.value = RequestUiState.Sending
        requestJob =
            viewModelScope.launch {
                when (val submission = SubWaveRequests.sendRequest(base, requestText, name.trim())) {
                    is RequestSubmission.Accepted -> {
                        _requestState.value = RequestUiState.Sent()
                        pollRequest(base, submission.requestId)
                    }

                    is RequestSubmission.Closed ->
                        _requestState.value = RequestUiState.Closed(submission.message)

                    is RequestSubmission.Rejected ->
                        _requestState.value = RequestUiState.Error(submission.message)

                    RequestSubmission.Failed -> _requestState.value = RequestUiState.Error(null)
                }
            }
    }

    private suspend fun pollRequest(
        base: String,
        requestId: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + REQUEST_POLL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(REQUEST_POLL_INTERVAL_MS)
            val status = SubWaveRequests.pollRequest(base, requestId) ?: continue
            when {
                status.isResolved -> {
                    _requestState.value = RequestUiState.Resolved(status.ack, status.trackTitle)
                    return
                }

                status.isFailed -> {
                    _requestState.value = RequestUiState.Error(status.message)
                    return
                }

                status.isUnknown -> return
            }
        }
    }

    fun resetRequest() {
        requestJob?.cancel()
        _requestState.value = RequestUiState.Idle
    }

    fun markWelcomeShown() =
        viewModelScope.launch {
            settings.markWelcomeShown()
        }

    fun setLanguage(language: Language) = app.languageStore.setLanguage(language)

    override fun onCleared() {
        requestJob?.cancel()
        playback.disconnect()
        super.onCleared()
    }

    private companion object {
        const val REQUEST_POLL_INTERVAL_MS = 2_500L
        const val REQUEST_POLL_TIMEOUT_MS = 60_000L
    }
}
