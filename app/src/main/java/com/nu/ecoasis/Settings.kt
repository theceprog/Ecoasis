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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat



// Data class for range settings (if not already defined elsewhere correctly)
data class RangeSettings(
    val phMin: Double = 1.0,
    val phMax: Double = 14.0,
    val tdsMin: Int = 0,
    val tdsMax: Int = 1500
)
private fun Double.roundToDecimalPlace(decimalPlaces: Int): Double {
    return BigDecimal(this).setScale(decimalPlaces, RoundingMode.HALF_UP).toDouble()
}

class Settings : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: RangeSettingsViewModel by viewModels()
    private var currentPlantIndex = 0
    private var plantsList = emptyList<Pair<String, PlantPreset>>() // PlantPreset uses Double for pH

    // Helper to format Doubles to one decimal place string
    private fun formatPhValue(value: Double): String {
        return DecimalFormat("0.0").format(value)
    }

    // Helper to round Doubles to one decimal place
    private fun Double.roundToDecimalPlace(decimalPlaces: Int): Double {
        return BigDecimal(this).setScale(decimalPlaces, RoundingMode.HALF_UP).toDouble()
    }

    // Scaling factor for SeekBars to handle one decimal place for pH
    private val PH_SEEKBAR_SCALE = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        updateButtonStates()
        setupObservers()
        setupSeekbarsWithAbsoluteRanges() // Will be adjusted for Double
        setupSeekbarListeners()          // Will be adjusted for Double
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
        FirestoreManager().getAllPlants( // Assuming PlantPreset uses Double for pH
            onSuccess = { plants ->
                plantsList = plants
                if (plants.isNotEmpty()) {
                    updatePlantDisplay()
                    loadLastAppliedPlant()
                } else {
                    binding.plantName.text = "No Presets"
                    binding.phRange.text = "-"
                    binding.ppmRange.text = "-"
                }
            },
            onFailure = { exception ->
                Toast.makeText(this, "Failed to load plants: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updatePlantDisplay() {
        if (plantsList.isNotEmpty() && currentPlantIndex < plantsList.size) {
            val (_, plant) = plantsList[currentPlantIndex]
            binding.plantName.text = plant.name
            binding.phRange.text = "${formatPhValue(plant.minPH)} - ${formatPhValue(plant.maxPH)}"
            binding.ppmRange.text = "${plant.minPPM} - ${plant.maxPPM} PPM"
        }
    }

    private fun applyPlantPreset(plant: PlantPreset, documentId: String) {
        // pH values are Doubles
        val phMin = plant.minPH
        val phMax = plant.maxPH

        // Update seekbars (scale Double to Int for SeekBar)
        val phAbsMinScaled = (FirestoreManager().PH_ABS_MIN * PH_SEEKBAR_SCALE).toInt()
        binding.verta.progress = (phMin * PH_SEEKBAR_SCALE).toInt() - phAbsMinScaled
        binding.vertb.progress = (phMax * PH_SEEKBAR_SCALE).toInt() - phAbsMinScaled

        binding.vertc.progress = plant.minPPM - FirestoreManager().TDS_ABS_MIN // TDS remains Int
        binding.vertd.progress = plant.maxPPM - FirestoreManager().TDS_ABS_MIN // TDS remains Int

        // Update display values
        binding.phMinValue.text = formatPhValue(phMin)
        binding.phMaxValue.text = formatPhValue(phMax)
        binding.tdsMinValue.text = plant.minPPM.toString()
        binding.tdsMaxValue.text = plant.maxPPM.toString()

        Toast.makeText(this, "${plant.name} preset applied!", Toast.LENGTH_SHORT).show()
        saveAppliedPlantId(documentId)
    }

    private fun saveAppliedPlantId(documentId: String) {
        val data = hashMapOf(
            "appliedPlantDocumentId" to documentId,
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )
        FirestoreManager().db.collection("ecoasis").document("currentSettings")
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                println("Applied plant document ID saved: $documentId")
            }
            .addOnFailureListener { e ->
                println("Failed to save applied plant document ID: ${e.message}")
            }
    }

    private fun loadLastAppliedPlant() {
        FirestoreManager().db.collection("ecoasis").document("currentSettings")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val appliedDocumentId = document.getString("appliedPlantDocumentId")
                    appliedDocumentId?.let { docId ->
                        val index = plantsList.indexOfFirst { it.first == docId }
                        if (index != -1) {
                            currentPlantIndex = index
                            // updatePlantDisplay() // Called by applyPlantPreset indirectly via observers
                            val (_, plant) = plantsList[index]
                            applyPlantPreset(plant, docId) // This will update UI and seekbars
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                println("Failed to load last applied plant settings: ${exception.message}")
            }
    }

    private fun setupSeekbarsWithAbsoluteRanges() {
        val phAbsMinScaled = (FirestoreManager().PH_ABS_MIN * PH_SEEKBAR_SCALE).toInt()
        val phAbsMaxScaled = (FirestoreManager().PH_ABS_MAX * PH_SEEKBAR_SCALE).toInt()

        binding.verta.max = phAbsMaxScaled - phAbsMinScaled
        binding.vertb.max = phAbsMaxScaled - phAbsMinScaled

        // TDS remains Int
        binding.vertc.max = FirestoreManager().TDS_ABS_MAX - FirestoreManager().TDS_ABS_MIN
        binding.vertd.max = FirestoreManager().TDS_ABS_MAX - FirestoreManager().TDS_ABS_MIN
    }

    private fun setupObservers() {
        viewModel.rangeSettings.observe(this) { settings ->
            // settings now contains Double for phMin, phMax
            updateSeekbarPositions(settings)
            updateDisplayValues(settings)
        }
    }

    private fun updateSeekbarPositions(settings: RangeSettings) {
        val phAbsMinScaled = (FirestoreManager().PH_ABS_MIN * PH_SEEKBAR_SCALE).toInt()

        binding.verta.progress = (settings.phMin * PH_SEEKBAR_SCALE).toInt() - phAbsMinScaled
        binding.vertb.progress = (settings.phMax * PH_SEEKBAR_SCALE).toInt() - phAbsMinScaled

        // TDS remains Int
        binding.vertc.progress = settings.tdsMin - FirestoreManager().TDS_ABS_MIN
        binding.vertd.progress = settings.tdsMax - FirestoreManager().TDS_ABS_MIN
    }

    private fun setupSeekbarListeners() {
        val phAbsMin = FirestoreManager().PH_ABS_MIN

        binding.verta.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val actualValue = (progress.toDouble() / PH_SEEKBAR_SCALE + phAbsMin)
                    binding.phMinValue.text = formatPhValue(actualValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.vertb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val actualValue = (progress.toDouble() / PH_SEEKBAR_SCALE + phAbsMin)
                    binding.phMaxValue.text = formatPhValue(actualValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // TDS Listeners remain the same as they handle Ints
        binding.vertc.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val actualValue = progress + FirestoreManager().TDS_ABS_MIN
                    binding.tdsMinValue.text = actualValue.toString()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.vertd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val actualValue = progress + FirestoreManager().TDS_ABS_MIN
                    binding.tdsMaxValue.text = actualValue.toString()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateDisplayValues(settings: RangeSettings) {
        binding.phMinValue.text = formatPhValue(settings.phMin)
        binding.phMaxValue.text = formatPhValue(settings.phMax)
        binding.tdsMinValue.text = settings.tdsMin.toString()
        binding.tdsMaxValue.text = settings.tdsMax.toString()
    }

    private fun saveRangeSettings() {
        val phAbsMin = FirestoreManager().PH_ABS_MIN
        // Convert SeekBar progress back to Double for pH
        val phMin = (binding.verta.progress.toDouble() / PH_SEEKBAR_SCALE + phAbsMin)
        val phMax = (binding.vertb.progress.toDouble() / PH_SEEKBAR_SCALE + phAbsMin)

        val tdsMin = binding.vertc.progress + FirestoreManager().TDS_ABS_MIN
        val tdsMax = binding.vertd.progress + FirestoreManager().TDS_ABS_MIN

        // Validate before saving
        if (phMin >= phMax) {
            Toast.makeText(this, "pH Min must be less than pH Max", Toast.LENGTH_SHORT).show()
            return
        }
        if (tdsMin >= tdsMax) {
            Toast.makeText(this, "TDS Min must be less than TDS Max", Toast.LENGTH_SHORT).show()
            return
        }


        FirestoreManager().saveRangeSettings(phMin, phMax, tdsMin, tdsMax,
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
        // Assuming settingbtn is nullable from your original code
        binding.settingbtn?.isActivated = isNightMode
        binding.backbutton.isActivated = isNightMode // backbutton is not nullable in ViewBinding usually
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
    private val firestoreService = FirestoreManager()
    private val _rangeSettings = MutableLiveData<RangeSettings>()
    val rangeSettings: LiveData<RangeSettings> = _rangeSettings

    private var settingsListener: ListenerRegistration? = null

    // Helper to round Doubles to one decimal place
  
    fun fetchRangeSettings() {
        firestoreService.getRangeSettings(
            onSuccess = { phMin, phMax, tdsMin, tdsMax -> // phMin, phMax are now Double
                _rangeSettings.postValue(
                    RangeSettings(
                        phMin,
                        phMax,
                        tdsMin,
                        tdsMax
                    )
                )
            },
            onFailure = { _ -> // Handle exception if needed
                _rangeSettings.postValue(
                    RangeSettings( // Default Doubles
                        FirestoreManager().PH_ABS_MIN,
                        FirestoreManager().PH_ABS_MAX,
                        FirestoreManager().TDS_ABS_MIN,
                        FirestoreManager().TDS_ABS_MAX
                    )
                )
            }
        )
    }

    fun startListeningForSettings() {
        settingsListener = firestoreService.listenForRangeSettings(
            onUpdate = { phMin, phMax, tdsMin, tdsMax -> // phMin, phMax are now Double
                _rangeSettings.postValue(
                    RangeSettings(
                        phMin,
                        phMax,
                        tdsMin,
                        tdsMax
                    )
                )
            },
            onError = { _ -> // Handle error if needed
                _rangeSettings.postValue(
                    RangeSettings( // Default Doubles on error
                        FirestoreManager().PH_ABS_MIN,
                        FirestoreManager().PH_ABS_MAX,
                        FirestoreManager().TDS_ABS_MIN,
                        FirestoreManager().TDS_ABS_MAX
                    )
                )
            }
        )
    }

    fun stopListening() {
        settingsListener?.remove()
    }
}
