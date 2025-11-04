package com.sriox.vasatey

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    
    private const val LOG_TAG = "VASATEY_APP"
    private const val MAX_LOG_ENTRIES = 1000
    private val logEntries = mutableListOf<LogEntry>()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun logInfo(category: String, message: String, details: String = "") {
        addLog("INFO", category, message, details)
        Log.i(LOG_TAG, "[$category] $message ${if (details.isNotEmpty()) "- $details" else ""}")
    }
    
    fun logWarning(category: String, message: String, details: String = "") {
        addLog("WARN", category, message, details)
        Log.w(LOG_TAG, "[$category] $message ${if (details.isNotEmpty()) "- $details" else ""}")
    }
    
    fun logError(category: String, message: String, details: String = "", exception: Throwable? = null) {
        val errorDetails = if (exception != null) {
            "$details\nException: ${exception.message}\nStackTrace: ${exception.stackTraceToString()}"
        } else {
            details
        }
        addLog("ERROR", category, message, errorDetails)
        if (exception != null) {
            Log.e(LOG_TAG, "[$category] $message - $details", exception)
        } else {
            Log.e(LOG_TAG, "[$category] $message ${if (details.isNotEmpty()) "- $details" else ""}")
        }
    }
    
    fun logSuccess(category: String, message: String, details: String = "") {
        addLog("SUCCESS", category, message, details)
        Log.i(LOG_TAG, "[$category] ✅ $message ${if (details.isNotEmpty()) "- $details" else ""}")
    }
    
    fun logDebug(category: String, message: String, details: String = "") {
        addLog("DEBUG", category, message, details)
        Log.d(LOG_TAG, "[$category] $message ${if (details.isNotEmpty()) "- $details" else ""}")
    }
    
    private fun addLog(level: String, category: String, message: String, details: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = LogEntry(timestamp, level, category, message, details)
        
        synchronized(logEntries) {
            logEntries.add(0, logEntry) // Add to top
            
            // Keep only the most recent entries
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeAt(logEntries.size - 1)
            }
        }
    }
    
    fun getAllLogs(): List<LogEntry> {
        return synchronized(logEntries) {
            logEntries.toList()
        }
    }
    
    fun clearLogs() {
        synchronized(logEntries) {
            logEntries.clear()
        }
        Log.i(LOG_TAG, "Logs cleared")
    }
    
    fun saveLogsToFile(context: Context): String {
        return try {
            val file = File(context.cacheDir, "vasatey_logs_${System.currentTimeMillis()}.txt")
            val logs = getAllLogs()
            
            file.writeText(buildString {
                appendLine("=== VASATEY APP LOGS ===")
                appendLine("Generated: ${dateFormat.format(Date())}")
                appendLine("Total Entries: ${logs.size}")
                appendLine("=" .repeat(50))
                appendLine()
                
                logs.forEach { log ->
                    appendLine("[${log.timestamp}] ${log.level} [${log.category}] ${log.message}")
                    if (log.details.isNotEmpty()) {
                        appendLine("Details: ${log.details}")
                    }
                    appendLine()
                }
            })
            
            file.absolutePath
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to save logs to file", e)
            "Failed to save logs: ${e.message}"
        }
    }
}

data class LogEntry(
    val timestamp: String,
    val level: String,
    val category: String,
    val message: String,
    val details: String
)