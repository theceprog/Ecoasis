package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.nu.ecoasis.databinding.ActivitySettingsBinding

class Settings : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: RangeSettingsViewModel by viewModels()
    private var currentPlantIndex = 0
    private var plantsList = emptyList<Pair<String, PlantPreset>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        updateButtonStates()
        setupObservers()
        setupSeekbarsWithAbsoluteRanges()
        setupSeekbarListeners()
        setupPlantPresetListeners()

        viewModel.fetchRangeSettings()
        viewModel.startListeningForSettings()
        loadPlants()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, imeInsets.bottom)
            insets
        }

        binding.backbutton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        binding.newpresetbtn.setOnClickListener {
            val intent = Intent(this, PresetActivity::class.java)
            startActivity(intent)
        }

        binding.saveButton.setOnClickListener {
            saveRangeSettings()
        }
    }


    private fun setupPlantPresetListeners() {
        binding.leftbtn.setOnClickListener {
            if (plantsList.isNotEmpty()) {
                currentPlantIndex = (currentPlantIndex - 1 + plantsList.size) % plantsList.size
                updatePlantDisplay()
            }
        }

        binding.rightbtn.setOnClickListener {
            if (plantsList.isNotEmpty()) {
                currentPlantIndex = (currentPlantIndex + 1) % plantsList.size
                updatePlantDisplay()
            }
        }

        binding.presetsetbtn.setOnClickListener {
            if (plantsList.isNotEmpty()) {
                val (documentId, plant) = plantsList[currentPlantIndex]
                applyPlantPreset(plant, documentId)
            }
        }
    }

    private fun loadPlants() {
        FirestoreManager.getAllPlants(
            onSuccess = { plants ->
                plantsList = plants
                if (plants.isNotEmpty()) {
                    updatePlantDisplay()
                    loadLastAppliedPlant() // Load the last applied plant after plants are loaded
                }
            },
            onFailure = { exception ->
                Toast.makeText(this, "Failed to load plants: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updatePlantDisplay() {
        val (_, plant) = plantsList[currentPlantIndex]
        binding.plantName.text = plant.name
        binding.phRange.text = "${plant.minPH} - ${plant.maxPH}"
        binding.ppmRange.text = "${plant.minPPM} - ${plant.maxPPM} PPM"
    }

    private fun applyPlantPreset(plant: PlantPreset, documentId: String) {
        // Convert Double values to Int for seekbars
        val phMin = plant.minPH.toInt()
        val phMax = plant.maxPH.toInt()

        // Update seekbars
        binding.verta.progress = phMin - FirestoreManager.PH_ABS_MIN
        binding.vertb.progress = phMax - FirestoreManager.PH_ABS_MIN
        binding.vertc.progress = plant.minPPM - FirestoreManager.TDS_ABS_MIN
        binding.vertd.progress = plant.maxPPM - FirestoreManager.TDS_ABS_MIN

        // Update display values
        binding.phMinValue.text = phMin.toString()
        binding.phMaxValue.text = phMax.toString()
        binding.tdsMinValue.text = plant.minPPM.toString()
        binding.tdsMaxValue.text = plant.maxPPM.toString()

        Toast.makeText(this, "${plant.name} preset applied!", Toast.LENGTH_SHORT).show()

        // Save the applied plant document ID to Firestore
        saveAppliedPlantId(documentId)
    }

    private fun saveAppliedPlantId(documentId: String) {
        val data = hashMapOf(
            "appliedPlantDocumentId" to documentId,
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        FirestoreManager.db.collection("ecoasis").document("currentSettings")
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                println("Applied plant document ID saved: $documentId")
            }
            .addOnFailureListener { e ->
                println("Failed to save applied plant document ID: ${e.message}")
            }
    }

    private fun loadLastAppliedPlant() {
        FirestoreManager.db.collection("ecoasis").document("currentSettings")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val appliedDocumentId = document.getString("appliedPlantDocumentId")
                    appliedDocumentId?.let { docId ->
                        // Find this plant in our list and select it
                        val index = plantsList.indexOfFirst { it.first == docId }
                        if (index != -1) {
                            currentPlantIndex = index
                            updatePlantDisplay()
                            val (_, plant) = plantsList[index]
                            applyPlantPreset(plant, docId)
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                println("Failed to load last applied plant settings: ${exception.message}")
            }
    }

    private fun setupSeekbarsWithAbsoluteRanges() {
        binding.verta.max = FirestoreManager.PH_ABS_MAX - FirestoreManager.PH_ABS_MIN
        binding.vertb.max = FirestoreManager.PH_ABS_MAX - FirestoreManager.PH_ABS_MIN
        binding.vertc.max = FirestoreManager.TDS_ABS_MAX - FirestoreManager.TDS_ABS_MIN
        binding.vertd.max = FirestoreManager.TDS_ABS_MAX - FirestoreManager.TDS_ABS_MIN
    }

    private fun setupObservers() {
        viewModel.rangeSettings.observe(this) { settings ->
            updateSeekbarPositions(settings)
            updateDisplayValues(settings)
        }
    }

    private fun updateSeekbarPositions(settings: RangeSettings) {
        binding.verta.progress = settings.phMin - FirestoreManager.PH_ABS_MIN
        binding.vertb.progress = settings.phMax - FirestoreManager.PH_ABS_MIN
        binding.vertc.progress = settings.tdsMin - FirestoreManager.TDS_ABS_MIN
        binding.vertd.progress = settings.tdsMax - FirestoreManager.TDS_ABS_MIN
    }

    private fun setupSeekbarListeners() {
        binding.verta.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progress + FirestoreManager.PH_ABS_MIN
                binding.phMinValue.text = actualValue.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.vertb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progress + FirestoreManager.PH_ABS_MIN
                binding.phMaxValue.text = actualValue.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.vertc.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progress + FirestoreManager.TDS_ABS_MIN
                binding.tdsMinValue.text = actualValue.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.vertd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progress + FirestoreManager.TDS_ABS_MIN
                binding.tdsMaxValue.text = actualValue.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateDisplayValues(settings: RangeSettings) {
        binding.phMinValue.text = settings.phMin.toString()
        binding.phMaxValue.text = settings.phMax.toString()
        binding.tdsMinValue.text = settings.tdsMin.toString()
        binding.tdsMaxValue.text = settings.tdsMax.toString()
    }

    private fun saveRangeSettings() {
        val phMin = binding.verta.progress + FirestoreManager.PH_ABS_MIN
        val phMax = binding.vertb.progress + FirestoreManager.PH_ABS_MIN
        val tdsMin = binding.vertc.progress + FirestoreManager.TDS_ABS_MIN
        val tdsMax = binding.vertd.progress + FirestoreManager.TDS_ABS_MIN

        // Save to Firestore
        FirestoreManager.saveRangeSettings(phMin, phMax, tdsMin, tdsMax,
            onSuccess = {
                Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { exception ->
                Toast.makeText(this, "Failed to save settings: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }
    private fun updateButtonStates() {
        val isNightMode = isNightModeEnabled()
        binding.settingbtn?.isActivated = isNightMode
        binding.backbutton?.isActivated = isNightMode
    }

    private fun isNightModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateButtonStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopListening()
    }
}

class RangeSettingsViewModel : ViewModel() {
    private val firestoreService = FirestoreManager
    private val _rangeSettings = MutableLiveData<RangeSettings>()
    val rangeSettings: LiveData<RangeSettings> = _rangeSettings

    private var settingsListener: ListenerRegistration? = null

    fun fetchRangeSettings() {
        firestoreService.getRangeSettings(
            onSuccess = { phMin, phMax, tdsMin, tdsMax ->
                _rangeSettings.postValue(RangeSettings(phMin, phMax, tdsMin, tdsMax))
            },
            onFailure = { exception ->
                _rangeSettings.postValue(RangeSettings(1, 14, 0, 1500))
            }
        )
    }

    fun startListeningForSettings() {
        settingsListener = firestoreService.listenForRangeSettings(
            onUpdate = { phMin, phMax, tdsMin, tdsMax ->
                _rangeSettings.postValue(RangeSettings(phMin, phMax, tdsMin, tdsMax))
            },
            onError = { error ->
                // Handle error, but keep previous values
            }
        )
    }

    fun stopListening() {
        settingsListener?.remove()
    }
}

data class RangeSettings(
    val phMin: Int = 1,
    val phMax: Int = 14,
    val tdsMin: Int = 0,
    val tdsMax: Int = 1500
)