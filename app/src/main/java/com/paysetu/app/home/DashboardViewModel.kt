package com.paysetu.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysetu.app.sync.PerformGlobalSyncUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val trustScore: Int = 100,
    val hoursUntilSyncRequired: Int = 48,
    val isSyncing: Boolean = false,
    val syncError: String? = null
)

// Note: Add @HiltViewModel and @Inject constructor here if you are using Dagger Hilt for Dependency Injection
class DashboardViewModel(
    private val deviceStateRepository: `DeviceState.kt`,
    private val performGlobalSyncUseCase: PerformGlobalSyncUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshDashboardData()
    }

    fun refreshDashboardData() {
        viewModelScope.launch {
            fetchAndUpdateState()
        }
    }

    // Extracted logic to a suspend function to guarantee sequential execution
    private suspend fun fetchAndUpdateState() {
        val score = deviceStateRepository.getTrustScore()
        val lastSync = deviceStateRepository.getLastSyncTimestamp()

        // Calculate remaining hours in the 48-hour window
        val currentTime = System.currentTimeMillis()
        val hoursSinceSync = ((currentTime - lastSync) / (1000 * 60 * 60)).toInt()
        val hoursLeft = (48 - hoursSinceSync).coerceAtLeast(0)

        // Using .update{} is safer for concurrent state updates
        _uiState.update { currentState ->
            currentState.copy(
                trustScore = score,
                hoursUntilSyncRequired = if (lastSync == 0L) 48 else hoursLeft
            )
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncError = null) }

            // Artificial delay for UI feedback if it's too fast
            delay(500)

            val success = performGlobalSyncUseCase.execute()

            if (success) {
                // Fetch the new score and reset timer BEFORE turning off the loading indicator
                fetchAndUpdateState()
                _uiState.update { it.copy(isSyncing = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncError = "Network unavailable. Try again later."
                    )
                }
            }
        }
    }
}