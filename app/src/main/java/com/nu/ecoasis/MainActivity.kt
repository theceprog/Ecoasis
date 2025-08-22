package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var sensorViewModel: SensorViewModel
    private lateinit var tempval: TextView
    private lateinit var phval: TextView
    private lateinit var ppmval: TextView
    private lateinit var luxval: TextView
    private lateinit var humidval: TextView
    private lateinit var airval: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {

        // Observe sensor ViewModel UI state
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