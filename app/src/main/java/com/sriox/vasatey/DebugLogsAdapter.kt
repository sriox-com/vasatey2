package com.sriox.vasatey

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DebugLogsAdapter(private val logs: List<DebugLog>) : RecyclerView.Adapter<DebugLogsAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestampText: TextView = itemView.findViewById(R.id.textTimestamp)
        val levelText: TextView = itemView.findViewById(R.id.textLevel)
        val categoryText: TextView = itemView.findViewById(R.id.textCategory)
        val messageText: TextView = itemView.findViewById(R.id.textMessage)
        val detailsText: TextView = itemView.findViewById(R.id.textDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_debug_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        
        holder.timestampText.text = log.timestamp
        holder.levelText.text = log.level
        holder.categoryText.text = log.category
        holder.messageText.text = log.message
        holder.detailsText.text = log.details
        
        // Color code by level
        val color = when (log.level) {
            "ERROR" -> Color.parseColor("#F44336")
            "WARN" -> Color.parseColor("#FF9800") 
            "SUCCESS" -> Color.parseColor("#4CAF50")
            "INFO" -> Color.parseColor("#2196F3")
            else -> Color.parseColor("#757575")
        }
        
        holder.levelText.setTextColor(color)
        
        // Show/hide details
        if (log.details.isBlank()) {
            holder.detailsText.visibility = View.GONE
        } else {
            holder.detailsText.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = logs.size
}