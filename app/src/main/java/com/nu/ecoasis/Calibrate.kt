package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
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
    private val _calibrationValues =
        MutableStateFlow<FirestoreCalibrationManager.CalibrationValues?>(null)
    private val _requestStatus = MutableStateFlow<Map<String, Boolean>?>(null) // Added missing

    val calibrationValues: StateFlow<FirestoreCalibrationManager.CalibrationValues?> =
        _calibrationValues
    val requestStatus: StateFlow<Map<String, Boolean>?> = _requestStatus // Added missing

    init {
        startListening()
        startListeningToRequests() // Added missing call
    }

    fun setCalibrationRequest(phType: String, value: Boolean) {
        viewModelScope.launch {
            firestoreManager.setRequestValue(phType, value) // This method needs to be implemented
        }
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

    private fun startListeningToRequests() {
        viewModelScope.launch {
            firestoreManager.listenForRequestUpdates(
                onUpdate = { requests ->
                    _requestStatus.value = requests
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
    private var requestListener: ListenerRegistration? = null // Added missing

    data class CalibrationValues(
        val ph4: Double? = null,
        val ph7: Double? = null,
        val ph9: Double? = null
    )

    // Added missing method
    suspend fun setRequestValue(phType: String, value: Boolean): Boolean {
        return try {
            val updateData = hashMapOf<String, Any>(
                "request.$phType" to value
            )
            calibrationRef.update(updateData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun listenForRequestUpdates(
        onUpdate: (Map<String, Boolean>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        requestListener = calibrationRef.addSnapshotListener { snapshot, error ->
            error?.let {
                onError(it)
                return@addSnapshotListener
            }

            snapshot?.let { document ->
                if (document.exists() && document.contains("request")) {
                    val requestMap = document.get("request") as? Map<String, Boolean>
                    onUpdate(requestMap ?: emptyMap())
                }
            }
        }
    }

    suspend fun getRequestStatus(): Map<String, Boolean> {
        return try {
            val document = calibrationRef.get().await()
            if (document.exists() && document.contains("request")) {
                document.get("request") as? Map<String, Boolean> ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

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
        requestListener?.remove()
        listener = null
        requestListener = null
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
    private lateinit var btnPh4: Button
    private lateinit var btnPh7: Button
    private lateinit var btnPh9: Button
    private val viewModel: CalibrateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calibrate)

        textViewPh4 = findViewById(R.id.textViewPh4)
        textViewPh7 = findViewById(R.id.textViewPh7)
        textViewPh9 = findViewById(R.id.textViewPh9)
        btnPh4 = findViewById(R.id.btnph4)
        btnPh7 = findViewById(R.id.btnph7)
        btnPh9 = findViewById(R.id.btnph9)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupButtonListeners()
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

    private fun setupButtonListeners() {
        btnPh4.setOnClickListener {
            viewModel.setCalibrationRequest("ph4", true)
            btnPh4.isEnabled = false

        }

        btnPh7.setOnClickListener {
            viewModel.setCalibrationRequest("ph7", true)
            btnPh7.isEnabled = false

        }

        btnPh9.setOnClickListener {
            viewModel.setCalibrationRequest("ph9", true)
            btnPh9.isEnabled = false

        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.calibrationValues.collect { values ->
                values?.let { updateUI(it) }
            }
        }

        lifecycleScope.launch {
            viewModel.requestStatus.collect { requests ->
                requests?.let { updateButtonStates(it) }
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

    private fun updateButtonStates(requests: Map<String, Boolean>) {
        requests["ph4"]?.let { isRequested ->
            btnPh4.isEnabled = !isRequested
        }

        requests["ph7"]?.let { isRequested ->
            btnPh7.isEnabled = !isRequested
        }

        requests["ph9"]?.let { isRequested ->
            btnPh9.isEnabled = !isRequested
        }
    }

    private fun isNightModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle theme changes if needed
    }
}
