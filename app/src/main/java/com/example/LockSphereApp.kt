package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.LockSpherePreferences

class LockSphereApp : Application() {

    lateinit var preferences: LockSpherePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = LockSpherePreferences(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PROTECTION,
                "LockSphere Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing protection status while LockSphere is active."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_PROTECTION = "locksphere_protection_channel"
        lateinit var instance: LockSphereApp
            private set
    }
}
