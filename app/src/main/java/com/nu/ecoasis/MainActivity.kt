package com.nu.ecoasis

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
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
import java.util.Calendar

object StatusUtils {
    fun getStatusText(status: Int): String {
        return when (status) {
            1 -> "LOW"
            2 -> "IN RANGE"
            3 -> "HIGH"
            else -> "- - -"
        }
    }


}
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
    val pumpA: Boolean = false,
    val pumpB: Boolean = false,
    val pumpDown: Boolean = false,
    val pumpUp: Boolean = false,
    val phStatus: Int = 0,
    val ppmStatus: Int = 0
)

class SensorViewModel(private val firestoreManager: FirestoreManager) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus

    init {
        loadSensorData()
        loadPumpStatus()
        loadStatusReading()
        setupRealTimeUpdates()
        setupPumpStatusRealTime()
        setupStatusReadingRealTime()
    }

    private fun loadStatusReading() {
        viewModelScope.launch {
            try {
                val statusReading = firestoreManager.getStatusReading()
                statusReading?.let {
                    _uiState.update { currentState ->
                        currentState.copy(
                            phStatus = it.status.ph,
                            ppmStatus = it.status.tds
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle error silently for status reading
            }
        }
    }

    private fun setupStatusReadingRealTime() {
        firestoreManager.getStatusReadingRealTime { statusReading ->
            statusReading?.let {
                _uiState.update { currentState ->
                    currentState.copy(
                        phStatus = it.status.ph,
                        ppmStatus = it.status.tds
                    )
                }
            }
        }
    }
    private fun loadPumpStatus() {
        viewModelScope.launch {
            try {
                val statusData = firestoreManager.getPumpStatus()
                statusData?.let {
                    _uiState.update { currentState ->
                        currentState.copy(
                            pumpA = it.pump_a,
                            pumpB = it.pump_b,
                            pumpUp = it.pump_ph_up,
                            pumpDown = it.pump_ph_down
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle error silently for pump status
            }
        }
    }

    private fun setupPumpStatusRealTime() {
        firestoreManager.getPumpStatusRealTime { statusData ->
            statusData?.let {
                _uiState.update { currentState ->
                    currentState.copy(
                        pumpA = it.pump_a,
                        pumpB = it.pump_b,
                        pumpUp = it.pump_ph_up,
                        pumpDown = it.pump_ph_down
                    )
                }
            }
        }
    }

    fun loadSensorData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val sensorData = firestoreManager.getSensorOne()
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
        firestoreManager.getSensorOneRealTime { sensorData ->
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

class SensorViewModelFactory(private val firestoreManager: FirestoreManager) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SensorViewModel::class.java)) {
            return SensorViewModel(firestoreManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
class SettingsDialog(context: Context) : Dialog(context) {

    private lateinit var darkModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var logoutOption: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.popup_settings)

        // Set dialog window properties
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        initializeViews()
        setupClickListeners()
        loadCurrentSettings()
    }

    private fun initializeViews() {
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        logoutOption = findViewById(R.id.logoutOption)
    }

    private fun setupClickListeners() {
        // Dark mode switch listener
        darkModeSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            setDarkMode(isChecked)
        }

        // Logout option listener
        logoutOption.setOnClickListener {
            performLogout()
            dismiss()
        }
    }

    private fun loadCurrentSettings() {
        // Load current dark mode setting
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        darkModeSwitch.isChecked = isDarkMode
    }

    private fun setDarkMode(isDarkMode: Boolean) {
        val mode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        // Save preference (optional)
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("dark_mode", isDarkMode)
            apply()
        }
    }

    private fun performLogout() {
        // Perform logout logic
        val firestoreManager = FirestoreManager()
        firestoreManager.signOut()

        // Navigate to login activity
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
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
    private lateinit var upPumpButton: ImageButton
    private lateinit var downPumpButton: ImageButton
    private lateinit var aPumpButton: TextView
    private lateinit var bPumpButton: TextView

    private lateinit var upPumpPercent: ImageView
    private lateinit var downPumpPercent: ImageView
    private lateinit var phStatusText: TextView
    private lateinit var ppmStatusText: TextView
    private fun showSettingsPopup() {
        val settingsDialog = SettingsDialog(this)
        settingsDialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, imeInsets.bottom)
            insets
        }
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
        upPumpButton = findViewById(R.id.up_pump)
        downPumpButton = findViewById(R.id.down_pump)
        upPumpPercent = findViewById(R.id.up_pump_percent)
        downPumpPercent = findViewById(R.id.down_pump_percent)
        aPumpButton = findViewById(R.id.a_pump_text)
        bPumpButton = findViewById(R.id.b_pump_text)
        phStatusText = findViewById(R.id.phStatusText)
        ppmStatusText = findViewById(R.id.ppmStatusText)

        updateButtonStates()
        val settingsCard = findViewById<CardView>(R.id.settingsCard)
        settingsCard.setOnClickListener {
            showSettingsPopup()
        }
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

        val firestoreManager = FirestoreManager()
        val factory = SensorViewModelFactory(firestoreManager)
        sensorViewModel = ViewModelProvider(this, factory)[SensorViewModel::class.java]


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
            dotStatus.setImageResource(R.drawable.ic_dot_green)
            textStatus.text = "Online"
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
        } else {
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

        updatePumpButtonState(upPumpButton, uiState.pumpUp)
        updatePumpButtonState(downPumpButton, uiState.pumpDown)
        updatePumpButtonState(aPumpButton, uiState.pumpA)
        updatePumpButtonState(bPumpButton, uiState.pumpB)
        updateStatusDisplay(uiState.phStatus, uiState.ppmStatus)
        uiState.error?.let { error ->
            showErrorDialog(error)
        }
    }
    private fun updateStatusDisplay(phStatus: Int, ppmStatus: Int) {
        phStatusText.text = StatusUtils.getStatusText(phStatus)
        ppmStatusText.text = StatusUtils.getStatusText(ppmStatus)
    }
    private fun updatePumpButtonState(button: View, isActive: Boolean) {
        val context = button.context
        val activeColor = ContextCompat.getColor(context, R.color.themegreen)
        val inactiveColor = ContextCompat.getColor(context, R.color.textwhite)

        when (button) {
            is ImageButton -> {
                button.isActivated = isActive
            }

            is TextView -> {
                // For TextViews, change text color
                button.setTextColor(if (isActive) activeColor else inactiveColor)
            }
        }
    }

    private fun setupClickListeners() {
        // Setup any click listeners if needed
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
        if (isNightMode) {
            upPumpButton.setImageResource(R.drawable.ic_up_selector_dark)
            downPumpButton.setImageResource(R.drawable.ic_down_selector_dark)

        } else {
            upPumpButton.setImageResource(R.drawable.ic_up_selector)
            downPumpButton.setImageResource(R.drawable.ic_down_selector)

        }
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