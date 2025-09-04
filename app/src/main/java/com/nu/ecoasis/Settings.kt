package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class PlantItem(
    val id: Int,
    val name: String,
    val range: String,
    val ppmRange: String,
    var isExpanded: Boolean = false
)

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
        holder.rangeText.text = item.range
        holder.ppmText.text = item.ppmRange

        holder.rightBtn.setOnClickListener {
            item.isExpanded = !item.isExpanded
            notifyItemChanged(position)
        }

        // Set visibility based on expansion state
        holder.detailsLayout.visibility = if (item.isExpanded) View.VISIBLE else View.GONE

        // Handle delete button click
        holder.deleteBtn.setOnClickListener {
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<PlantItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(item: PlantItem) {
        val position = items.indexOfFirst { it.id == item.id }
        if (position != -1) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}

class PresetActivity : AppCompatActivity() {
    private lateinit var adapter: PlantAdapter
    private val plantItems = mutableListOf(
        PlantItem(1, "Spinach", "6 - 7", "600 - 750 PPM"),
        PlantItem(2, "Lettuce", "5.5 - 6.5", "500 - 700 PPM"),
        PlantItem(3, "Tomato", "6.0 - 6.8", "800 - 1200 PPM")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_preset)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = PlantAdapter(plantItems.toMutableList()) { item ->
            // Handle delete action
            adapter.removeItem(item)
        }

        recyclerView.adapter = adapter
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        updateButtonStates()
        val backbutton: ImageButton = findViewById(R.id.backbutton)
        backbutton.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
        }
    }

    private fun updateButtonStates() {
        val isNightMode = isNightModeEnabled()
        findViewById<ImageButton>(R.id.settingbtn).isActivated = isNightMode
        findViewById<ImageButton>(R.id.backbutton).isActivated = isNightMode
    }

    private fun isNightModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    // Handle theme changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateButtonStates()
    }
}

class Settings : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        updateButtonStates()
        val backbutton: ImageButton = findViewById(R.id.backbutton)
        backbutton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        val managebtn: Button = findViewById(R.id.newpresetbtn)
        managebtn.setOnClickListener {
            val intent = Intent(this, PresetActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateButtonStates() {
        val isNightMode = isNightModeEnabled()
        findViewById<ImageButton>(R.id.settingbtn).isActivated = isNightMode
        findViewById<ImageButton>(R.id.backbutton).isActivated = isNightMode
    }

    private fun isNightModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    // Handle theme changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateButtonStates()
    }
}