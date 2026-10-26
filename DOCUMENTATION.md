# LockSphere — Native Android Privacy & App Locker

**LockSphere** is a privacy-first, zero-tracking native Android application locker built with Kotlin, Jetpack Compose, Material Design 3, AndroidX Biometric, Preferences DataStore, and Android's official UsageStatsManager APIs.

---

## 1. Core Architectural Pillars

- **Zero Cloud & 100% Offline**: No network traffic, no server accounts, no cloud database. All selections and configurations reside on-device in DataStore.
- **Zero Tracking & No Advertising**: No analytics, telemetry, or tracking SDKs.
- **No Biometric Template Storage**: Biometric authentication delegates strictly to the Android OS Keystore and `BiometricPrompt`.
- **Policy-Compliant Package Discovery**: Avoids broad `QUERY_ALL_PACKAGES` by declaring a focused launcher query intent filter (`android.intent.action.MAIN` + `android.intent.category.LAUNCHER`).
- **Battery-Conscious Engine**: Screen-state aware `LockSphereService` pauses foreground polling when the device screen turns off, saving battery.

---

## 2. Authentication & Cryptography

1. **Native Biometrics (`AndroidX Biometric`)**:
   - Evaluates device capability via `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)`.
   - Distinct states: `Available`, `NoneEnrolled`, `NoHardware`, `HardwareUnavailable`.
   - Automatically prompts on opening a protected app.
2. **Cryptographic PIN Fallback**:
   - Stored using SHA-256 with a unique 128-bit cryptographically secure salt (`SecureRandom`) saved in Preferences DataStore.
   - Constant-time hash verification (`MessageDigest.isEqual`) prevents timing attacks.
   - **Brute-Force Lockout Protection**: 5 consecutive incorrect attempts trigger an automatic 30-second lockout timer.
3. **Session Management & Relock Policies**:
   - `Immediate`: Relocks as soon as the user exits the app.
   - `Timed`: Configurable grace window (30s, 1m, 3m).
   - `Lock on Screen Off`: Invalidate all active sessions when screen turns off.

---

## 3. Physical Device Testing (Samsung Galaxy A57 / One UI)

Samsung One UI enforces aggressive battery management that can stop third-party background services. To ensure reliable protection:

1. **Usage Access**:
   - Navigate to `Settings` > `Apps` > `Special App Access` > `Usage Data Access` > Enable `LockSphere`.
2. **Display Over Other Apps**:
   - Navigate to `Settings` > `Apps` > `Special App Access` > `Appear on top` > Enable `LockSphere`.
3. **Battery Optimization**:
   - In LockSphere `Settings` tab, tap **Set Unrestricted** under Battery Optimization.
   - Select `Unrestricted` in One UI app battery settings to prevent background service termination.
4. **Emergency Killswitch**:
   - An instant master toggle is accessible in the Dashboard and Settings tab to pause all interception without getting locked out.

---

## 4. Honest Disclosures of Third-Party Android Limitations

LockSphere operates as a consumer third-party Android application:
- It **does not** use root privileges, enterprise Knox MDM profiles, or Accessibility abuse.
- If a user uninstalls LockSphere or force-stops it via Android Settings, third-party locking cannot intercept app launches until restarted.
- This transparency ensures users understand the exact security boundaries provided.
