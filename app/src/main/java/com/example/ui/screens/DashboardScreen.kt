package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LockActivity
import com.example.R
import com.example.security.BiometricStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.RoseLock
import com.example.ui.viewmodel.LockSphereUiState
import com.example.ui.viewmodel.LockSphereViewModel
import com.example.ui.viewmodel.ProtectionState

@Composable
fun DashboardScreen(
    uiState: LockSphereUiState,
    viewModel: LockSphereViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.locksphere_logo),
                        contentDescription = "LockSphere Logo",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = "LockSphere",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Native Privacy & App Locker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = when (uiState.protectionState) {
                        ProtectionState.ACTIVE -> EmeraldSecurity.copy(alpha = 0.15f)
                        ProtectionState.PERMISSIONS_REQUIRED -> AmberWarning.copy(alpha = 0.15f)
                        ProtectionState.SETUP_REQUIRED -> CyberCyan.copy(alpha = 0.15f)
                        ProtectionState.PAUSED -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (uiState.protectionState) {
                            ProtectionState.ACTIVE -> EmeraldSecurity
                            ProtectionState.PERMISSIONS_REQUIRED -> AmberWarning
                            ProtectionState.SETUP_REQUIRED -> CyberCyan
                            ProtectionState.PAUSED -> MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Text(
                        text = when (uiState.protectionState) {
                            ProtectionState.ACTIVE -> "ACTIVE"
                            ProtectionState.PERMISSIONS_REQUIRED -> "PERM NEEDED"
                            ProtectionState.SETUP_REQUIRED -> "SETUP"
                            ProtectionState.PAUSED -> "PAUSED"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = when (uiState.protectionState) {
                            ProtectionState.ACTIVE -> EmeraldSecurity
                            ProtectionState.PERMISSIONS_REQUIRED -> AmberWarning
                            ProtectionState.SETUP_REQUIRED -> CyberCyan
                            ProtectionState.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Master Status Hero Card
        item {
            MasterStatusCard(
                uiState = uiState,
                onToggle = { viewModel.toggleProtection() },
                onResolvePermissions = onNavigateToSettings
            )
        }

        // Quick Stats Overview Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Protected",
                    value = "${uiState.protectedPackages.size}",
                    subtext = "Selected apps",
                    icon = Icons.Default.Lock,
                    tint = CyberCyan,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToApps
                )
                StatCard(
                    title = "Biometrics",
                    value = when (uiState.biometricStatus) {
                        is BiometricStatus.Available -> "Enrolled"
                        is BiometricStatus.NoneEnrolled -> "Not Setup"
                        is BiometricStatus.NoHardware -> "None"
                        else -> "Unavailable"
                    },
                    subtext = if (uiState.isBiometricEnabledInPrefs) "Active" else "Disabled",
                    icon = Icons.Default.Fingerprint,
                    tint = if (uiState.biometricStatus is BiometricStatus.Available) EmeraldSecurity else AmberWarning,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSecurity
                )
            }
        }

        // Primary Action Controls
        item {
            Text(
                text = "SYSTEM CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            ActionTile(
                title = "Select Apps to Protect",
                description = "Choose which apps require authentication",
                icon = Icons.Default.Apps,
                tint = CyberCyan,
                testTag = "action_select_apps",
                onClick = onNavigateToApps
            )
        }

        item {
            ActionTile(
                title = "Test Authentication Gate",
                description = "Preview the biometric & PIN unlock screen now",
                icon = Icons.Default.Security,
                tint = EmeraldSecurity,
                testTag = "action_test_gate",
                onClick = {
                    val intent = Intent(context, LockActivity::class.java).apply {
                        putExtra(LockActivity.EXTRA_TARGET_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
        }

        item {
            ActionTile(
                title = "Run Foreground Diagnostics",
                description = "Inspect real-time UsageStats foreground detection",
                icon = Icons.Default.Speed,
                tint = AmberWarning,
                testTag = "action_diagnostics",
                onClick = { viewModel.runLiveDiagnostics() }
            )
        }

        // Physical Device Honesty & Samsung Note
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Device Notice",
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Physical Device Architecture",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "LockSphere operates strictly offline without root, Knox or accessibility abuse. Foreground detection uses Android's official UsageStats API. For Samsung Galaxy A57 (One UI), ensure Battery Optimization is set to 'Unrestricted' in Settings to prevent OS background killing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MasterStatusCard(
    uiState: LockSphereUiState,
    onToggle: () -> Unit,
    onResolvePermissions: () -> Unit
) {
    val isReady = uiState.protectionState == ProtectionState.ACTIVE
    val isNeedsPerms = uiState.protectionState == ProtectionState.PERMISSIONS_REQUIRED

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isReady -> EmeraldSecurity
            isNeedsPerms -> AmberWarning
            uiState.protectionState == ProtectionState.SETUP_REQUIRED -> CyberCyan
            else -> MaterialTheme.colorScheme.outline
        },
        label = "heroBorderColor"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_status_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isReady -> EmeraldSecurity.copy(alpha = 0.15f)
                                    isNeedsPerms -> AmberWarning.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isReady -> Icons.Default.CheckCircle
                                isNeedsPerms -> Icons.Default.Warning
                                else -> Icons.Default.PowerSettingsNew
                            },
                            contentDescription = "Status Icon",
                            tint = when {
                                isReady -> EmeraldSecurity
                                isNeedsPerms -> AmberWarning
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            text = when (uiState.protectionState) {
                                ProtectionState.ACTIVE -> "Protection Active"
                                ProtectionState.PERMISSIONS_REQUIRED -> "Usage Access Needed"
                                ProtectionState.SETUP_REQUIRED -> "Select Protected Apps"
                                ProtectionState.PAUSED -> "Protection Paused"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (uiState.protectionState) {
                                ProtectionState.ACTIVE -> "Monitoring ${uiState.protectedPackages.size} protected apps"
                                ProtectionState.PERMISSIONS_REQUIRED -> "Grant Usage Access in Settings"
                                ProtectionState.SETUP_REQUIRED -> "Toggle apps in the Apps tab"
                                ProtectionState.PAUSED -> "Turn on to resume locking"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = uiState.isProtectionEnabledInPrefs,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EmeraldSecurity
                    ),
                    modifier = Modifier.testTag("master_protection_toggle")
                )
            }

            if (isNeedsPerms) {
                Button(
                    onClick = onResolvePermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberWarning,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_usage_access_button")
                ) {
                    Text("Grant Required Usage Access", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
