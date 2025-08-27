package com.nu.ecoasis

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SensorUiState(
    val air: Double = 0.0,
    val h2o: Double = 0.0,
    val humid: Double = 0.0,
    val lux: Double = 0.0,
    val ph: Double = 0.0,
    val ppm: Double = 0.0,
    val up: Double = 0.0,
    val down: Double = 0.0,
    val a: Double = 0.0,
    val b: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class SensorViewModel(private val sensorRepository: SensorRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus
    init {
        loadSensorData()
        setupRealTimeUpdates()
    }


    fun loadSensorData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val sensorData = sensorRepository.getSensorOne()
                sensorData?.let {
                    _uiState.update { currentState ->
                        currentState.copy(
                            air = it.air,
                            h2o = it.h2o,
                            humid = it.humid,
                            lux = it.lux,
                            ph = it.ph,
                            ppm = it.ppm,
                            up = it.up,
                            down = it.down,
                            a = it.a,
                            b = it.b,
                            isLoading = false,
                            error = null
                        )
                    }
                    _connectionStatus.value = true
                } ?: run {
                    // Handle case where sensorData is null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No sensor data available"
                        )
                    }
                    _connectionStatus.value = false
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load sensor data: ${e.message}"
                    )
                }
                _connectionStatus.value = false
            }
        }
    }

    private fun setupRealTimeUpdates() {
        sensorRepository.getSensorOneRealTime { sensorData ->
            sensorData?.let {
                _uiState.update { currentState ->
                    currentState.copy(
                        air = it.air,
                        h2o = it.h2o,
                        humid = it.humid,
                        lux = it.lux,
                        ph = it.ph,
                        ppm = it.ppm,
                        up = it.up,
                        down = it.down,
                        a = it.a,
                        b = it.b,
                        isLoading = false,
                        error = null
                    )
                }
                _connectionStatus.value = true
            } ?: run {
                // Handle real-time connection failure
                _connectionStatus.value = false
                // Don't update UI state to preserve last known good values
                // You could optionally set an error state here
            }
        }
    }

    fun refreshData() {
        loadSensorData()
    }
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}