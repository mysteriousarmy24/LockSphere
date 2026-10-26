package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.LockActivity
import com.example.LockSphereApp
import com.example.MainActivity
import com.example.R
import com.example.data.LockSpherePreferences
import com.example.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LockSphereService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var detector: ForegroundAppDetector
    private lateinit var preferences: LockSpherePreferences

    private var detectionJob: Job? = null
    private var isScreenOn = true
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    serviceScope.launch {
                        val lockOnScreenOff = preferences.lockOnScreenOffFlow.first()
                        SessionManager.onScreenTurnedOff(lockOnScreenOff)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(this)
        preferences = (application as LockSphereApp).preferences

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        receiverRegistered = true

        startForegroundNotification()
        startDetectionLoop()
    }

    private fun startForegroundNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, LockSphereApp.CHANNEL_ID_PROTECTION)
            .setContentTitle("LockSphere Protection Active")
            .setContentText("Monitoring selected apps for biometric authentication")
            .setSmallIcon(R.drawable.locksphere_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startDetectionLoop() {
        detectionJob?.cancel()
        detectionJob = serviceScope.launch {
            while (isActive) {
                if (isScreenOn) {
                    try {
                        checkForegroundApp()
                    } catch (e: Exception) {
                        // Silent safety handling - avoid service crash
                    }
                }
                delay(800L) // Efficient 800ms poll to detect app switch without battery drain
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val isEnabled = preferences.isProtectionEnabledFlow.first()
        if (!isEnabled) {
            stopSelf()
            return
        }

        // If the lock gate is already visible, do not re-launch or interrupt the user
        if (SessionManager.isLockGateVisible) {
            return
        }

        val protectedApps = preferences.protectedPackagesFlow.first()
        if (protectedApps.isEmpty()) {
            return
        }

        val relockTimeout = preferences.relockTimeoutSecondsFlow.first()
        val detection = detector.getForegroundApp()
        val currentPackage = detection.packageName ?: return

        // Skip LockSphere itself
        if (currentPackage == packageName) {
            return
        }

        // Notify session manager of foreground app transitions
        SessionManager.onForegroundPackageObserved(currentPackage, relockTimeout, packageName)

        // Check if this package is protected
        if (protectedApps.contains(currentPackage)) {
            val isUnlocked = SessionManager.isUnlocked(currentPackage, relockTimeout)
            if (!isUnlocked) {
                launchLockGate(currentPackage)
            }
        }
    }

    private fun launchLockGate(targetPackage: String) {
        val lockIntent = Intent(this, LockActivity::class.java).apply {
            putExtra(LockActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(lockIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            receiverRegistered = false
        }
        detectionJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, LockSphereService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LockSphereService::class.java)
            context.stopService(intent)
        }
    }
}
