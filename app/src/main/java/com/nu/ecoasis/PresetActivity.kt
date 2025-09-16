package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

data class PlantItem(
    val name: String = "",
    val minPH: Double = 0.0,
    val maxPH: Double = 0.0,
    val minPPM: Int = 0,
    val maxPPM: Int = 0,
    val documentId: String = "",
    var isExpanded: Boolean = false
) {
    val phRange: String get() = "${formatDecimal(minPH)} - ${formatDecimal(maxPH)}"
    val ppmRange: String get() = "$minPPM - $maxPPM PPM"

    private fun formatDecimal(value: Double): String {
        return if (value % 1 == 0.0) {
            value.toInt().toString()
        } else {
            DecimalFormat("#.#").format(value)
        }
    }
}

class FirestorePlantManager {
    private val db = FirebaseFirestore.getInstance()
    private val plantsCollection = db.collection("plants")

    suspend fun getAllPlants(): List<PlantItem> {
        return try {
            val querySnapshot = plantsCollection.get().await()
            querySnapshot.documents.map { document ->
                PlantItem(
                    name = document.getString("name") ?: "",
                    minPH = document.getDouble("minPH") ?: 0.0,
                    maxPH = document.getDouble("maxPH") ?: 0.0,
                    minPPM = document.getLong("minPPM")?.toInt() ?: 0,
                    maxPPM = document.getLong("maxPPM")?.toInt() ?: 0,
                    documentId = document.id
                )
            }.sortedBy { it.name }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addPlant(plant: PlantItem): Boolean {
        return try {
            val plantData = hashMapOf(
                "name" to plant.name,
                "minPH" to plant.minPH,
                "maxPH" to plant.maxPH,
                "minPPM" to plant.minPPM,
                "maxPPM" to plant.maxPPM
            )
            plantsCollection.add(plantData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deletePlant(documentId: String): Boolean {
        return try {
            plantsCollection.document(documentId).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun initializeDefaultPlants(): Boolean {
        val defaultPlants = listOf(
            PlantItem("Spinach", 6.0, 7.0, 600, 750),
            PlantItem("Lettuce", 5.5, 6.5, 500, 700),
            PlantItem("Tomato", 6.0, 6.8, 800, 1200),
            PlantItem("Basil", 5.5, 6.5, 700, 900),
            PlantItem("Kale", 6.0, 7.0, 800, 1000)
        )

        return try {
            val existingPlants = getAllPlants()
            if (existingPlants.isEmpty()) {
                defaultPlants.forEach { plant ->
                    addPlant(plant)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

class PlantAdapter(
    private var items: MutableList<PlantItem>,
    private val onDeleteClick: (PlantItem) -> Unit
) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

    class PlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rightBtn: ImageButton = itemView.findViewById(R.id.rightbtn)
        val deleteBtn: Button = itemView.findViewById(R.id.deleteBtn)
        val nameText: TextView = itemView.findViewById(R.id.nameText)
        val rangeText: TextView = itemView.findViewById(R.id.rangeText)
        val ppmText: TextView = itemView.findViewById(R.id.ppmText)
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.detailsLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plant, parent, false)
        return PlantViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
        val item = items[position]

        holder.nameText.text = item.name
        holder.rangeText.text = "pH: ${item.phRange}"
        holder.ppmText.text = "PPM: ${item.ppmRange}"

        holder.rightBtn.setOnClickListener {
            item.isExpanded = !item.isExpanded
            notifyItemChanged(position)
        }

        holder.detailsLayout.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
        holder.deleteBtn.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<PlantItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(item: PlantItem) {
        val position = items.indexOfFirst { it.documentId == item.documentId }
        if (position != -1) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}

class PresetActivity : AppCompatActivity() {
    private lateinit var adapter: PlantAdapter
    private val plantItems = mutableListOf<PlantItem>()
    private val plantManager = FirestorePlantManager()
    private lateinit var recyclerView: RecyclerView

    private lateinit var plantNameInput: EditText
    private lateinit var minPHInput: EditText
    private lateinit var maxPHInput: EditText
    private lateinit var minPPMInput: EditText
    private lateinit var maxPPMInput: EditText
    private lateinit var addPresetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_preset)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = PlantAdapter(plantItems.toMutableList()) { item ->
            deletePlant(item)
        }

        recyclerView.adapter = adapter

        // Initialize input fields
        plantNameInput = findViewById(R.id.plantNameInput)
        minPHInput = findViewById(R.id.minPHInput)
        maxPHInput = findViewById(R.id.maxPHInput)
        minPPMInput = findViewById(R.id.minPPMInput)
        maxPPMInput = findViewById(R.id.maxPPMInput)
        addPresetButton = findViewById(R.id.addPresetButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, imeInsets.bottom)
            insets
        }

        updateButtonStates()
        loadPlantsFromFirestore()
        setupInputValidation()
        setupAddButton()

        val backbutton: ImageButton = findViewById(R.id.backbutton)
        backbutton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
        }
    }

    private fun setupInputValidation() {
        // pH input validation (allow 1 decimal digit)
        val phTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (text.isNotEmpty()) {
                    if (text.isNotEmpty()) {
                        try {
                            val value = text.toDouble()
                            if (value < 0.0 || value > 14.0) {
                                if (minPHInput.text === s) { // Check if 's' is the Editable of minPHInput
                                    minPHInput.error = "pH must be between 0.0 and 14.0"
                                } else if (maxPHInput.text === s) { // Check if 's' is the Editable of maxPHInput
                                    maxPHInput.error = "pH must be between 0.0 and 14.0"
                                }
                            }
                        } catch (e: NumberFormatException) {
                            if (minPHInput.text === s) {
                                minPHInput.error = "Invalid number format"
                            } else if (maxPHInput.text === s) {
                                maxPHInput.error = "Invalid number format"
                            }
                        }
                    }
                }
            }
        }

        minPHInput.addTextChangedListener(phTextWatcher)
        maxPHInput.addTextChangedListener(phTextWatcher)


        minPHInput.addTextChangedListener(phTextWatcher)
        maxPHInput.addTextChangedListener(phTextWatcher)

        // PPM input validation (integers only)
        val ppmTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (text.isNotEmpty() && !text.matches(Regex("\\d+"))) {
                    s?.replace(0, s.length, text.replace(Regex("[^\\d]"), ""))
                }
            }
        }

        minPPMInput.addTextChangedListener(ppmTextWatcher)
        maxPPMInput.addTextChangedListener(ppmTextWatcher)
    }

    private fun setupAddButton() {
        addPresetButton.setOnClickListener {
            if (validateInputs()) {
                addNewPlant()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val name = plantNameInput.text.toString().trim()
        val minPH = minPHInput.text.toString().trim()
        val maxPH = maxPHInput.text.toString().trim()
        val minPPM = minPPMInput.text.toString().trim()
        val maxPPM = maxPPMInput.text.toString().trim()

        if (name.isEmpty()) {
            plantNameInput.error = "Plant name is required"
            plantNameInput.requestFocus()
            return false
        }

        if (minPH.isEmpty()) {
            minPHInput.error = "Minimum pH is required"
            minPHInput.requestFocus()
            return false
        }

        if (maxPH.isEmpty()) {
            maxPHInput.error = "Maximum pH is required"
            maxPHInput.requestFocus()
            return false
        }

        if (minPPM.isEmpty()) {
            minPPMInput.error = "Minimum PPM is required"
            minPPMInput.requestFocus()
            return false
        }

        if (maxPPM.isEmpty()) {
            maxPPMInput.error = "Maximum PPM is required"
            maxPPMInput.requestFocus()
            return false
        }

        val minPHValue = minPH.toDouble()
        val maxPHValue = maxPH.toDouble()
        val minPPMValue = minPPM.toInt()
        val maxPPMValue = maxPPM.toInt()

        if (minPHValue < 0.0 || minPHValue > 14.0) {
            minPHInput.error = "pH must be between 0.0 and 14.0"
            minPHInput.requestFocus()
            return false
        }

        if (maxPHValue < 0.0 || maxPHValue > 14.0) {
            maxPHInput.error = "pH must be between 0.0 and 14.0"
            maxPHInput.requestFocus()
            return false
        }

        if (minPHValue >= maxPHValue) {
            minPHInput.error = "Minimum pH must be less than maximum pH"
            minPHInput.requestFocus()
            return false
        }

        if (minPPMValue >= maxPPMValue) {
            minPPMInput.error = "Minimum PPM must be less than maximum PPM"
            minPPMInput.requestFocus()
            return false
        }

        return true
    }

    private fun addNewPlant() {
        val name = plantNameInput.text.toString().trim()
        val minPH = minPHInput.text.toString().toDouble()
        val maxPH = maxPHInput.text.toString().toDouble()
        val minPPM = minPPMInput.text.toString().toInt()
        val maxPPM = maxPPMInput.text.toString().toInt()

        val newPlant = PlantItem(name, minPH, maxPH, minPPM, maxPPM)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = plantManager.addPlant(newPlant)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@PresetActivity, "$name added successfully", Toast.LENGTH_SHORT).show()
                        clearInputs()
                        loadPlantsFromFirestore()
                    } else {
                        Toast.makeText(this@PresetActivity, "Failed to add $name", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PresetActivity, "Error adding plant: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearInputs() {
        plantNameInput.text.clear()
        minPHInput.text.clear()
        maxPHInput.text.clear()
        minPPMInput.text.clear()
        maxPPMInput.text.clear()
    }

    private fun loadPlantsFromFirestore() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                plantManager.initializeDefaultPlants()
                val plants = plantManager.getAllPlants()
                withContext(Dispatchers.Main) {
                    adapter.updateItems(plants)
                    if (plants.isEmpty()) {
                        Toast.makeText(this@PresetActivity, "No plants found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PresetActivity, "Error loading plants: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deletePlant(plant: PlantItem) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = plantManager.deletePlant(plant.documentId)
                withContext(Dispatchers.Main) {
                    if (success) {
                        adapter.removeItem(plant)
                        Toast.makeText(this@PresetActivity, "${plant.name} deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PresetActivity, "Failed to delete ${plant.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PresetActivity, "Error deleting plant: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateButtonStates() {
        val isNightMode = isNightModeEnabled()
        findViewById<ImageButton>(R.id.backbutton)?.isActivated = isNightMode
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