package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LockSphereApp
import com.example.data.AppInfo
import com.example.data.LockSpherePreferences
import com.example.security.BiometricAuthManager
import com.example.security.BiometricStatus
import com.example.security.PinSecurityManager
import com.example.service.ForegroundAppDetector
import com.example.service.ForegroundDetectionResult
import com.example.service.LockSphereService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProtectionState {
    ACTIVE,
    PERMISSIONS_REQUIRED,
    SETUP_REQUIRED,
    PAUSED
}

data class LockSphereUiState(
    val protectionState: ProtectionState = ProtectionState.SETUP_REQUIRED,
    val isProtectionEnabledInPrefs: Boolean = false,
    val isUsageAccessGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val biometricStatus: BiometricStatus = BiometricStatus.NoneEnrolled,
    val isBiometricEnabledInPrefs: Boolean = true,
    val hasPinConfigured: Boolean = false,
    val relockTimeoutSeconds: Int = 0,
    val lockOnScreenOff: Boolean = true,
    val startOnBoot: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val protectedPackages: Set<String> = emptySet(),
    val installedApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val selectedTab: Int = 0,
    val lastForegroundCheck: ForegroundDetectionResult? = null,
    val showDiagnosticsDialog: Boolean = false
)

class LockSphereViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: LockSpherePreferences = (application as LockSphereApp).preferences
    private val detector = ForegroundAppDetector(application)
    private val biometricAuthManager = BiometricAuthManager(application)

    private val _uiState = MutableStateFlow(LockSphereUiState())
    val uiState: StateFlow<LockSphereUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        refreshPermissions()
        refreshInstalledApps()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferences.isProtectionEnabledFlow,
                preferences.protectedPackagesFlow,
                preferences.relockTimeoutSecondsFlow
            ) { isEnabled, protectedPkgs, timeout ->
                Triple(isEnabled, protectedPkgs, timeout)
            }.collect { (isEnabled, protectedPkgs, timeout) ->
                val usageGranted = detector.isUsageAccessGranted()
                val bioStatus = biometricAuthManager.checkBiometricStatus()
                val bioEnabled = preferences.biometricEnabledFlow.first()
                val pinHash = preferences.pinHashFlow.first()
                val lockScreenOff = preferences.lockOnScreenOffFlow.first()
                val onBoot = preferences.startOnBootFlow.first()
                val vibrate = preferences.vibrationEnabledFlow.first()
                val hasPin = !pinHash.isNullOrBlank()

                val state = when {
                    !isEnabled -> ProtectionState.PAUSED
                    !usageGranted -> ProtectionState.PERMISSIONS_REQUIRED
                    protectedPkgs.isEmpty() -> ProtectionState.SETUP_REQUIRED
                    else -> ProtectionState.ACTIVE
                }

                _uiState.update { current ->
                    val updatedApps = current.installedApps.map { app ->
                        app.copy(isProtected = protectedPkgs.contains(app.packageName))
                    }
                    val filtered = filterAppsList(updatedApps, current.searchQuery)

                    current.copy(
                        protectionState = state,
                        isProtectionEnabledInPrefs = isEnabled,
                        isUsageAccessGranted = usageGranted,
                        protectedPackages = protectedPkgs,
                        installedApps = updatedApps,
                        filteredApps = filtered,
                        relockTimeoutSeconds = timeout,
                        lockOnScreenOff = lockScreenOff,
                        isBiometricEnabledInPrefs = bioEnabled,
                        biometricStatus = bioStatus,
                        hasPinConfigured = hasPin,
                        startOnBoot = onBoot,
                        vibrationEnabled = vibrate
                    )
                }
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredApps = filterAppsList(current.installedApps, query)
            )
        }
    }

    private fun filterAppsList(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        val cleanQuery = query.trim().lowercase()
        return apps.filter {
            it.name.lowercase().contains(cleanQuery) || it.packageName.lowercase().contains(cleanQuery)
        }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        val usageGranted = detector.isUsageAccessGranted()
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true

        val bioStatus = biometricAuthManager.checkBiometricStatus()

        _uiState.update { current ->
            val state = when {
                !current.isProtectionEnabledInPrefs -> ProtectionState.PAUSED
                !usageGranted -> ProtectionState.PERMISSIONS_REQUIRED
                current.protectedPackages.isEmpty() -> ProtectionState.SETUP_REQUIRED
                else -> ProtectionState.ACTIVE
            }

            current.copy(
                isUsageAccessGranted = usageGranted,
                isOverlayGranted = overlayGranted,
                isBatteryOptimizationIgnored = batteryIgnored,
                biometricStatus = bioStatus,
                protectionState = state
            )
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val context = getApplication<Application>()

            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)
                val protectedSet = preferences.protectedPackagesFlow.first()

                resolveInfoList.mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg == context.packageName) return@mapNotNull null // Exclude LockSphere

                    val label = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    AppInfo(
                        packageName = pkg,
                        name = label,
                        isProtected = protectedSet.contains(pkg),
                        icon = icon,
                        isSystemApp = isSystem
                    )
                }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
            }

            _uiState.update { current ->
                current.copy(
                    isLoadingApps = false,
                    installedApps = apps,
                    filteredApps = filterAppsList(apps, current.searchQuery)
                )
            }
        }
    }

    fun toggleProtection() {
        viewModelScope.launch {
            val currentEnabled = _uiState.value.isProtectionEnabledInPrefs
            val newEnabled = !currentEnabled
            preferences.setProtectionEnabled(newEnabled)

            val context = getApplication<Application>()
            if (newEnabled && detector.isUsageAccessGranted()) {
                LockSphereService.startService(context)
            } else {
                LockSphereService.stopService(context)
            }
            refreshPermissions()
        }
    }

    fun toggleAppProtection(packageName: String) {
        viewModelScope.launch {
            preferences.toggleAppProtection(packageName)
        }
    }

    fun setAllAppsProtected(protectAll: Boolean) {
        viewModelScope.launch {
            if (protectAll) {
                val allPkgs = _uiState.value.installedApps.map { it.packageName }.toSet()
                preferences.setProtectedPackages(allPkgs)
            } else {
                preferences.setProtectedPackages(emptySet())
            }
        }
    }

    fun setRelockTimeout(seconds: Int) {
        viewModelScope.launch {
            preferences.setRelockTimeoutSeconds(seconds)
        }
    }

    fun setLockOnScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setLockOnScreenOff(enabled)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBiometricEnabled(enabled)
        }
    }

    fun configurePin(newPin: String) {
        viewModelScope.launch {
            val salt = PinSecurityManager.generateSalt()
            val hash = PinSecurityManager.hashPin(newPin, salt)
            preferences.setPin(hash, salt)
        }
    }

    fun removePin() {
        viewModelScope.launch {
            preferences.removePin()
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setStartOnBoot(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setVibrationEnabled(enabled)
        }
    }

    fun runLiveDiagnostics() {
        val result = detector.getForegroundApp()
        _uiState.update { it.copy(lastForegroundCheck = result, showDiagnosticsDialog = true) }
    }

    fun dismissDiagnostics() {
        _uiState.update { it.copy(showDiagnosticsDialog = false) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            LockSphereService.stopService(context)
            preferences.clearAll()
            PinSecurityManager.resetLockout()
            refreshPermissions()
            refreshInstalledApps()
        }
    }
}
