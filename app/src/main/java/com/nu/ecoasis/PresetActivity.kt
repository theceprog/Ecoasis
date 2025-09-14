package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class PlantItem(
    val name: String = "",
    val minPH: Double = 0.0,
    val maxPH: Double = 0.0,
    val minPPM: Int = 0,
    val maxPPM: Int = 0,
    val documentId: String = "",
    var isExpanded: Boolean = false
) {
    // Helper properties for display
    val phRange: String get() = "${minPH.toInt()} - ${maxPH.toInt()}"
    val ppmRange: String get() = "$minPPM - $maxPPM PPM"
}

// Firestore Plant Manager
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
            PlantItem( "Tomato", 6.0, 6.8, 800, 1200),
            PlantItem( "Basil", 5.5, 6.5, 700, 900),
            PlantItem("Kale", 6.0, 7.0, 800, 1000)
        )

        return try {
            val existingPlants = getAllPlants()
            if (existingPlants.isEmpty()) {
                defaultPlants.forEach { plant ->
                    val plantData = hashMapOf(
                        "name" to plant.name,
                        "minPH" to plant.minPH,
                        "maxPH" to plant.maxPH,
                        "minPPM" to plant.minPPM,
                        "maxPPM" to plant.maxPPM
                    )
                    plantsCollection.add(plantData).await()
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

// Plant Adapter
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
        holder.rangeText.text = item.phRange
        holder.ppmText.text = item.ppmRange

        // Set detailed information
        holder.rangeText.text = "pH: ${item.minPH} - ${item.maxPH}"
        holder.ppmText.text = "PPM: ${item.minPPM} - ${item.maxPPM}"

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

// Preset Activity
class PresetActivity : AppCompatActivity() {
    private lateinit var adapter: PlantAdapter
    private val plantItems = mutableListOf<PlantItem>()
    private val plantManager = FirestorePlantManager()
    private lateinit var recyclerView: RecyclerView

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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, imeInsets.bottom) // push content above keyboard
            insets
        }
        updateButtonStates()
        loadPlantsFromFirestore()

        val backbutton: ImageButton = findViewById(R.id.backbutton)
        backbutton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
        }


    }

    private fun loadPlantsFromFirestore() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize default plants if empty
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
        findViewById<ImageButton>(R.id.settingbtn)?.isActivated = isNightMode
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
