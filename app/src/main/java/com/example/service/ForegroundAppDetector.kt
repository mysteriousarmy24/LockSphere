package com.example.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

data class ForegroundDetectionResult(
    val packageName: String?,
    val eventType: String,
    val timestamp: Long,
    val isPermissionGranted: Boolean
)

class ForegroundAppDetector(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Detects the top-most foreground package using permitted UsageStatsManager APIs.
     * Looks for recent ACTIVITY_RESUMED or MOVE_TO_FOREGROUND events.
     */
    fun getForegroundApp(): ForegroundDetectionResult {
        if (!isUsageAccessGranted()) {
            return ForegroundDetectionResult(
                packageName = null,
                eventType = "PERMISSION_DENIED",
                timestamp = System.currentTimeMillis(),
                isPermissionGranted = false
            )
        }

        if (usageStatsManager == null) {
            return ForegroundDetectionResult(
                packageName = null,
                eventType = "SERVICE_UNAVAILABLE",
                timestamp = System.currentTimeMillis(),
                isPermissionGranted = true
            )
        }

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000L // Query the last 10 seconds

        var latestPackage: String? = null
        var latestEventTime = 0L
        var latestEventDesc = "NO_EVENTS"

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            val isResumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }

            if (isResumed && event.timeStamp >= latestEventTime) {
                latestPackage = event.packageName
                latestEventTime = event.timeStamp
                latestEventDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "ACTIVITY_RESUMED"
                } else {
                    "MOVE_TO_FOREGROUND"
                }
            }
        }

        // Fallback: If no event in the last 10 seconds, query recent usage stats
        if (latestPackage == null) {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - 60000L,
                endTime
            )
            if (!stats.isNullOrEmpty()) {
                val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
                if (mostRecent != null && mostRecent.lastTimeUsed > 0) {
                    latestPackage = mostRecent.packageName
                    latestEventTime = mostRecent.lastTimeUsed
                    latestEventDesc = "QUERY_STATS_FALLBACK"
                }
            }
        }

        return ForegroundDetectionResult(
            packageName = latestPackage,
            eventType = latestEventDesc,
            timestamp = if (latestEventTime > 0) latestEventTime else endTime,
            isPermissionGranted = true
        )
    }
}
