package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class CalibrateViewModel : ViewModel() {
    private val firestoreManager = FirestoreCalibrationManager()
    private val _calibrationValues = MutableStateFlow<FirestoreCalibrationManager.CalibrationValues?>(null)
    val calibrationValues: StateFlow<FirestoreCalibrationManager.CalibrationValues?> = _calibrationValues

    init {
        startListening()
    }

    private fun startListening() {
        viewModelScope.launch {
            firestoreManager.listenForCalibrationUpdates(
                onUpdate = { values ->
                    _calibrationValues.value = values
                },
                onError = { error ->
                    error.printStackTrace()
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        firestoreManager.stopListening()
    }
}
class FirestoreCalibrationManager {
    private val db = FirebaseFirestore.getInstance()
    private val calibrationRef = db.collection("ecoasis").document("calibration")
    private var listener: ListenerRegistration? = null

    data class CalibrationValues(
        val ph4: Double? = null,
        val ph7: Double? = null,
        val ph9: Double? = null
    )

    suspend fun getCalibrationValues(): CalibrationValues? {
        return try {
            val document = calibrationRef.get().await()
            if (document.exists() && document.contains("values")) {
                val valuesMap = document.get("values") as? Map<String, Any>
                CalibrationValues(
                    ph4 = extractDouble(valuesMap, "ph4"),
                    ph7 = extractDouble(valuesMap, "ph7"),
                    ph9 = extractDouble(valuesMap, "ph9")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractDouble(map: Map<String, Any>?, key: String): Double? {
        return when (val value = map?.get(key)) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Float -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    fun listenForCalibrationUpdates(
        onUpdate: (CalibrationValues) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        listener = calibrationRef.addSnapshotListener { snapshot, error ->
            error?.let {
                onError(it)
                return@addSnapshotListener
            }

            snapshot?.let { document ->
                if (document.exists() && document.contains("values")) {
                    val valuesMap = document.get("values") as? Map<String, Any>
                    val values = CalibrationValues(
                        ph4 = extractDouble(valuesMap, "ph4"),
                        ph7 = extractDouble(valuesMap, "ph7"),
                        ph9 = extractDouble(valuesMap, "ph9")
                    )
                    onUpdate(values)
                }
            }
        }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    fun startPeriodicCheck(
        intervalMillis: Long = 5000,
        onUpdate: (CalibrationValues?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val values = getCalibrationValues()
                CoroutineScope(Dispatchers.Main).launch {
                    onUpdate(values)
                }
                delay(intervalMillis)
            }
        }
    }
}

class Calibrate : AppCompatActivity() {
    private lateinit var textViewPh4: TextView
    private lateinit var textViewPh7: TextView
    private lateinit var textViewPh9: TextView
    private val viewModel: CalibrateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calibrate)

        // Initialize TextViews
        textViewPh4 = findViewById(R.id.textViewPh4)
        textViewPh7 = findViewById(R.id.textViewPh7)
        textViewPh9 = findViewById(R.id.textViewPh9)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        updateButtonStates()
        setupObservers()

        val backbutton: ImageButton = findViewById(R.id.backbutton)
        backbutton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        val settingsButton: ImageButton = findViewById(R.id.settingbtn)
        settingsButton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.calibrationValues.collect { values ->
                values?.let { updateUI(it) }
            }
        }
    }

    private fun updateUI(values: FirestoreCalibrationManager.CalibrationValues) {
        values.ph4?.let {
            textViewPh4.text = "${String.format("%.2f", it)}"
        } ?: run {
            textViewPh4.text = "-"
        }

        values.ph7?.let {
            textViewPh7.text = "${String.format("%.2f", it)}"
        } ?: run {
            textViewPh7.text = "-"
        }

        values.ph9?.let {
            textViewPh9.text = "${String.format("%.2f", it)}"
        } ?: run {
            textViewPh9.text = "-"
        }
    }

    private fun updateButtonStates() {
        val isNightMode = isNightModeEnabled()
        findViewById<ImageButton>(R.id.settingbtn).isActivated = isNightMode
        findViewById<ImageButton>(R.id.backbutton).isActivated = isNightMode
    }

    private fun isNightModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateButtonStates()
    }
}