package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.LockSpherePreferences
import com.example.security.BiometricAuthManager
import com.example.security.PinSecurityManager
import com.example.security.SessionManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.LockSphereTheme
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.RoseLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LockActivity : FragmentActivity() {

    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var preferences: LockSpherePreferences
    private var targetPackage: String = ""
    private var targetAppName: String = "Protected Application"
    private var targetAppIcon: Drawable? = null

    private val targetAppNameState = mutableStateOf("Protected Application")
    private val targetAppIconState = mutableStateOf<Drawable?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        biometricManager = BiometricAuthManager(this)
        preferences = (application as LockSphereApp).preferences

        loadTargetAppDetails()

        // Safely redirect to home if user tries to go back without unlocking
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHomeScreen()
            }
        })

        setContent {
            LockSphereTheme(darkTheme = true) {
                LockGateScreen(
                    targetAppName = targetAppNameState.value,
                    targetAppIcon = targetAppIconState.value,
                    onExit = { exitToHomeScreen() },
                    onRequestBiometric = { triggerBiometricAuth() },
                    onUnlockSuccess = { onUnlockCompleted() }
                )
            }
        }

        // Automatically prompt biometric if available and configured
        lifecycleScope.launch {
            val biometricEnabled = preferences.biometricEnabledFlow.first()
            if (biometricEnabled && biometricManager.isBiometricAvailable()) {
                triggerBiometricAuth()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SessionManager.isLockGateVisible = true
        SessionManager.currentLockGatePackage = targetPackage
    }

    override fun onPause() {
        super.onPause()
        SessionManager.isLockGateVisible = false
        SessionManager.currentLockGatePackage = null
    }

    override fun onDestroy() {
        super.onDestroy()
        SessionManager.isLockGateVisible = false
        SessionManager.currentLockGatePackage = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newTarget = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (!newTarget.isNullOrBlank() && newTarget != targetPackage) {
            targetPackage = newTarget
            loadTargetAppDetails()
        }
    }

    private fun loadTargetAppDetails() {
        if (targetPackage.isNotBlank()) {
            try {
                val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                targetAppName = label
                targetAppIcon = icon
                targetAppNameState.value = label
                targetAppIconState.value = icon
            } catch (e: PackageManager.NameNotFoundException) {
                targetAppName = "Protected App"
                targetAppNameState.value = "Protected App"
            }
        }
    }

    private fun triggerBiometricAuth() {
        biometricManager.showPrompt(
            activity = this,
            title = "Unlock ${targetAppNameState.value}",
            subtitle = "LockSphere Biometric Protection",
            negativeButtonText = "Use PIN",
            onSuccess = {
                // Biometric authentication successful: record unlock, bring originally intended app back to foreground with (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP), and finish current activity
                SessionManager.recordUnlock(targetPackage)
                vibrate(40)
                redirectToTargetApp(targetPackage)
                finish()
            },
            onError = { _, _ ->
                // Fallback to PIN interface
            },
            onFailed = {
                vibrate(100)
            }
        )
    }

    private fun onUnlockCompleted() {
        SessionManager.recordUnlock(targetPackage)
        vibrate(40)
        redirectToTargetApp(targetPackage)
        finish()
    }

    private fun redirectToTargetApp(targetPkg: String) {
        if (targetPkg.isBlank() || targetPkg == packageName) {
            return
        }

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
                return
            }

            // Fallback for launchable activities
            val queryIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = targetPkg
            }
            val resolveInfo = packageManager.queryIntentActivities(queryIntent, 0).firstOrNull()
            if (resolveInfo != null) {
                val component = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name
                )
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(explicitIntent)
            }
        } catch (e: Exception) {
            // Safe fallback if launching fails
        }
    }

    private fun exitToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore if vibration unavailable
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}

@Composable
fun LockGateScreen(
    targetAppName: String,
    targetAppIcon: Drawable?,
    onExit: () -> Unit,
    onRequestBiometric: () -> Unit,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { (context.applicationContext as LockSphereApp).preferences }

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableIntStateOf(PinSecurityManager.getRemainingLockoutSeconds()) }

    // Lockout countdown timer
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = PinSecurityManager.getRemainingLockoutSeconds()
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lock_gate_screen"),
        color = NavyBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding.calculateTopPadding() + 16.dp)
                .padding(bottom = navBarPadding.calculateBottomPadding() + 16.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Exit button & LockSphere Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield Locked",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "LOCKSPHERE GATE",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                IconButton(
                    onClick = onExit,
                    modifier = Modifier.testTag("lock_gate_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit to home",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Target App Identity Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(NavySurfaceCard)
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (targetAppIcon != null) {
                        val bitmap = remember(targetAppIcon) {
                            targetAppIcon.toBitmap(128, 128).asImageBitmap()
                        }
                        Image(
                            bitmap = bitmap,
                            contentDescription = targetAppName,
                            modifier = Modifier.size(60.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.locksphere_logo),
                            contentDescription = "LockSphere Logo",
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = targetAppName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Authentication required to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Error / Lockout indicator
                if (lockoutSeconds > 0) {
                    Text(
                        text = "Too many attempts. Locked for ${lockoutSeconds}s",
                        color = RoseLock,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = RoseLock,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // PIN Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .testTag("pin_dots_indicator")
            ) {
                val maxDigits = 4
                for (i in 0 until maxDigits) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) CyberCyan else NavySurfaceCard
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFilled) CyberCyan else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Numeric Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIOMETRIC", "0", "BACKSPACE")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            KeypadButton(
                                text = key,
                                enabled = lockoutSeconds <= 0 && !isChecking,
                                onClick = {
                                    when (key) {
                                        "BACKSPACE" -> {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                                errorMessage = null
                                            }
                                        }
                                        "BIOMETRIC" -> {
                                            onRequestBiometric()
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                val nextPin = enteredPin + key
                                                enteredPin = nextPin
                                                errorMessage = null

                                                if (nextPin.length == 4) {
                                                    // Verify PIN against stored hash
                                                    isChecking = true
                                                    scope.launch {
                                                        val storedHash = preferences.pinHashFlow.first()
                                                        val storedSalt = preferences.pinSaltFlow.first()

                                                        if (storedHash.isNullOrBlank() || storedSalt.isNullOrBlank()) {
                                                            // If no custom PIN was ever set, default master PIN "1234" is permitted on first-run with prompt
                                                            if (nextPin == "1234") {
                                                                onUnlockSuccess()
                                                            } else {
                                                                errorMessage = "Incorrect PIN (Default: 1234)"
                                                                enteredPin = ""
                                                            }
                                                        } else {
                                                            val verified = PinSecurityManager.verifyPin(
                                                                inputPin = nextPin,
                                                                storedHash = storedHash,
                                                                storedSalt = storedSalt
                                                            )
                                                            if (verified) {
                                                                onUnlockSuccess()
                                                            } else {
                                                                lockoutSeconds = PinSecurityManager.getRemainingLockoutSeconds()
                                                                val attempts = PinSecurityManager.getRemainingAttempts()
                                                                errorMessage = if (lockoutSeconds > 0) {
                                                                    "Locked out for ${lockoutSeconds}s"
                                                                } else {
                                                                    "Incorrect PIN ($attempts attempts left)"
                                                                }
                                                                enteredPin = ""
                                                            }
                                                        }
                                                        isChecking = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Safe Exit Action
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavySurfaceCard,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("lock_gate_return_home_button")
            ) {
                Text("Cancel & Return to Home", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (text == "BIOMETRIC" || text == "BACKSPACE") NavyBackground else NavySurfaceCard
            )
            .border(
                width = 1.dp,
                color = if (text == "BIOMETRIC" || text == "BACKSPACE") MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true)
            ) { onClick() }
            .testTag("keypad_btn_$text"),
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            "BACKSPACE" -> {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            "BIOMETRIC" -> {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Biometric prompt",
                    tint = CyberCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            else -> {
                Text(
                    text = text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
