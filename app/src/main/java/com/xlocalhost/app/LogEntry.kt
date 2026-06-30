package com.xlocalhost.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val method: String,
    val path: String,
    val statusCode: Int,
    val statusDescription: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val clientIp: String,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val startedAt: Long,
    val endedAt: Long
) {
    val timeFormatted: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    
    val fullStartedAt: String
        get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss.SSS", Locale.getDefault()).format(Date(startedAt))
    
    val fullEndedAt: String
        get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss.SSS", Locale.getDefault()).format(Date(endedAt))
}
