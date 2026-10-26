package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.LockSpherePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val preferences = LockSpherePreferences(context)
            CoroutineScope(Dispatchers.Default).launch {
                val startOnBoot = preferences.startOnBootFlow.first()
                val isProtectionEnabled = preferences.isProtectionEnabledFlow.first()
                if (startOnBoot && isProtectionEnabled) {
                    LockSphereService.startService(context)
                }
            }
        }
    }
}
