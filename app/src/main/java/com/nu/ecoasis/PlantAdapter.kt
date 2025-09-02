package com.nu.ecoasis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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