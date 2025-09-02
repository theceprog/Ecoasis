package com.nu.ecoasis

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ImageButton
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