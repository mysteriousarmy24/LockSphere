package com.example.security

import java.util.concurrent.ConcurrentHashMap

object SessionManager {

    private val unlockedTimestamps = ConcurrentHashMap<String, Long>()
    private var lastActivePackage: String? = null

    @Volatile
    var isLockGateVisible: Boolean = false

    @Volatile
    var currentLockGatePackage: String? = null

    /**
     * Checks if a package is currently in an authenticated session.
     */
    fun isUnlocked(packageName: String, relockTimeoutSeconds: Int): Boolean {
        val unlockTime = unlockedTimestamps[packageName] ?: return false
        val now = System.currentTimeMillis()

        return if (relockTimeoutSeconds <= 0) {
            // Immediate relocking policy:
            // Unlocked during initial transition window or as long as it remains active
            val elapsed = now - unlockTime
            if (elapsed < 12000L) {
                true
            } else {
                unlockedTimestamps.containsKey(packageName) && (lastActivePackage == null || lastActivePackage == packageName)
            }
        } else {
            // Configured timeout period (e.g. 30s, 60s, 180s)
            val maxDurationMs = relockTimeoutSeconds * 1000L
            (now - unlockTime) <= maxDurationMs
        }
    }

    /**
     * Record an app as unlocked.
     */
    fun recordUnlock(packageName: String) {
        val now = System.currentTimeMillis()
        unlockedTimestamps[packageName] = now
        lastActivePackage = packageName
    }

    /**
     * Update the currently observed active foreground package.
     * When the user leaves an app under Immediate relock policy, invalidate that app's session.
     */
    fun onForegroundPackageObserved(currentPackage: String, relockTimeoutSeconds: Int, appPackageName: String) {
        // Skip LockSphere itself
        if (currentPackage == appPackageName) {
            return
        }

        if (lastActivePackage != null && lastActivePackage != currentPackage) {
            val previousPkg = lastActivePackage!!
            if (relockTimeoutSeconds <= 0) {
                // Immediate policy: if user moved away after grace window, remove previous app session
                val unlockTime = unlockedTimestamps[previousPkg] ?: 0L
                if (System.currentTimeMillis() - unlockTime > 5000L) {
                    unlockedTimestamps.remove(previousPkg)
                }
            }
        }
        lastActivePackage = currentPackage
    }

    /**
     * Handle screen turned off event.
     */
    fun onScreenTurnedOff(lockOnScreenOff: Boolean) {
        if (lockOnScreenOff) {
            lockAll()
        }
    }

    /**
     * Relock a specific package.
     */
    fun lockPackage(packageName: String) {
        unlockedTimestamps.remove(packageName)
        if (lastActivePackage == packageName) {
            lastActivePackage = null
        }
    }

    /**
     * Invalidate all sessions.
     */
    fun lockAll() {
        unlockedTimestamps.clear()
        lastActivePackage = null
    }

    /**
     * Get snapshot of currently unlocked packages for diagnostics.
     */
    fun getActiveSessions(): Map<String, Long> {
        return HashMap(unlockedTimestamps)
    }
}
