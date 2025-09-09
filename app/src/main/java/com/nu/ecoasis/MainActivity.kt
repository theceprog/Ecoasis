package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

data class SensorUiState(
    val air: Double = 0.0,
    val h2o: Double = 0.0,
    val humid: Double = 0.0,
    val lux: Double = 0.0,
    val ph: Double = 0.0,
    val ppm: Int = 0,
    val up: Double = 0.0,
    val down: Double = 0.0,
    val a: Double = 0.0,
    val b: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val pumpa: Boolean = false,
    val pumpb: Boolean = false,
    val pumpdown: Boolean = false,
    val pumpup: Boolean = false
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

class SensorViewModelFactory(private val sensorRepository: SensorRepository) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SensorViewModel::class.java)) {
            return SensorViewModel(sensorRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class SensorData(
    val air: Double = 0.0,
    val h2o: Double = 0.0,
    val humid: Double = 0.0,
    val lux: Double = 0.0,
    val ph: Double = 0.0,
    val ppm: Int = 0,
    val up: Double = 0.0,
    val down: Double = 0.0,
    val a: Double = 0.0,
    val b: Double = 0.0,

    ) {
    // Empty constructor for Firestore
    constructor() : this(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0)
}
class SensorRepository {
    private val sensorCollection by lazy { FirestoreManager.db.collection("ecoasis") }

    suspend fun getSensorOne(): SensorData? {
        return try {
            sensorCollection.document("readings").get().await().toObject(SensorData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getSensorOneRealTime(onUpdate: (SensorData?) -> Unit) {
        sensorCollection.document("readings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(null)
                return@addSnapshotListener
            }

            val sensorData = snapshot?.toObject(SensorData::class.java)
            onUpdate(sensorData)
        }
    }
}
data class StatusData(
    val pumpa: Boolean = false,
    val pumpb: Boolean = false,
    val pumpdown: Boolean = false,
    val pumpup: Boolean = false

) {
    // Empty constructor for Firestore
    constructor() : this(false,false,false,false)
}

class MainActivity : AppCompatActivity() {
    private lateinit var sensorViewModel: SensorViewModel
    private lateinit var tempval: TextView
    private lateinit var phval: TextView
    private lateinit var ppmval: TextView
    private lateinit var luxval: TextView
    private lateinit var humidval: TextView
    private lateinit var airval: TextView
    private lateinit var upnum: TextView
    private lateinit var downnum: TextView
    private lateinit var anum: TextView
    private lateinit var bnum: TextView
    private lateinit var upprogress: ProgressBar
    private lateinit var downprogress: ProgressBar
    private lateinit var aprogress: ProgressBar
    private lateinit var bprogress: ProgressBar
    private lateinit var dotStatus: ImageView
    private lateinit var textStatus: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, imeInsets.bottom) // push content above keyboard
            insets
        }
        updateButtonStates()
        val settingsButton: ImageButton = findViewById(R.id.settingbtn)
        settingsButton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
        }

        val calibrateButton: TextView = findViewById(R.id.calibratebtn)
        calibrateButton.setOnClickListener {
            val intent = Intent(this, Calibrate::class.java)
            startActivity(intent)
        }
        val repository = SensorRepository()
        val factory = SensorViewModelFactory(repository)
        sensorViewModel = ViewModelProvider(this, factory)[SensorViewModel::class.java]
        tempval = findViewById(R.id.temp_val)
        phval = findViewById(R.id.ph_val)
        ppmval = findViewById(R.id.ppm_val)
        luxval = findViewById(R.id.lux_val)
        humidval = findViewById(R.id.humid_val)
        airval = findViewById(R.id.air_val)
        upnum = findViewById(R.id.upnum)
        downnum = findViewById(R.id.downnum)
        anum = findViewById(R.id.anum)
        bnum = findViewById(R.id.bnum)
        aprogress = findViewById(R.id.aprogress)
        bprogress = findViewById(R.id.bprogress)
        upprogress = findViewById(R.id.upprogress)
        downprogress = findViewById(R.id.downprogress)
        dotStatus = findViewById(R.id.dot_status)
        textStatus = findViewById(R.id.text_status)
        setupObservers()
        setupClickListeners()

        val textView3 = findViewById<TextView>(R.id.textView3)
        textView3.text = getGreetingBasedOnTime()
        sensorViewModel.connectionStatus.observe(this) { isConnected ->
            updateStatusUI(isConnected)
        }

    }

    private fun updateStatusUI(isConnected: Boolean) {
        if (isConnected) {
            // Online status
            dotStatus.setImageResource(R.drawable.ic_dot_green) // You need to create green dot
            textStatus.text = "Online"
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
        } else {
            // Offline status
            dotStatus.setImageResource(R.drawable.ic_dot_red)
            textStatus.text = "Offline"
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
        }
    }

    private fun getGreetingBasedOnTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> "Good Morning!"
            in 12..17 -> "Good Afternoon!"
            in 18..21 -> "Good Evening!"
            else -> "Good Night!"
        }
    }

    private fun setupObservers() {

        lifecycleScope.launch {
            sensorViewModel.uiState.collect { uiState ->
                updateUI(uiState)
            }
        }
    }

    private fun updateUI(uiState: SensorUiState) {
        tempval.text = "${uiState.h2o}"
        phval.text = "${uiState.ph}"
        ppmval.text = "${uiState.ppm}"
        luxval.text = "${uiState.lux}"
        humidval.text = "${uiState.humid}"
        airval.text = "${uiState.air}"
        upnum.text = "${uiState.up}%"
        downnum.text = "${uiState.down}%"
        anum.text = "${uiState.a}%"
        bnum.text = "${uiState.b}%"
        upprogress.progress = uiState.up.toInt()
        downprogress.progress = uiState.down.toInt()
        aprogress.progress = uiState.a.toInt()
        bprogress.progress = uiState.b.toInt()
        uiState.error?.let { error ->
            showErrorDialog(error)
        }
    }

    private fun setupClickListeners() {
        //buttonRefresh.setOnClickListener {
        //       sensorViewModel.refreshData()
        //   }

    }

    private fun showErrorDialog(message: String) {
        val toast = Toast.makeText(
            this,
            "❌ Error: $message",
            Toast.LENGTH_LONG
        )
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }


    private fun updateButtonStates() {
        val isNightMode = isNightModeEnabled()
        findViewById<ImageButton>(R.id.settingbtn).isActivated = isNightMode
        findViewById<ImageButton>(R.id.up_pump).isActivated = isNightMode
        findViewById<ImageButton>(R.id.down_pump).isActivated = isNightMode
    }

    private fun isNightModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateButtonStates()
    }
}