package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PinSecurityManager {

    private const val SALT_LENGTH = 16
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds

    private var consecutiveFailures = 0
    private var lockoutUntilTimestamp = 0L

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    fun verifyPin(inputPin: String, storedHash: String, storedSalt: String): Boolean {
        if (isLockedOut()) {
            return false
        }

        val computedHash = hashPin(inputPin, storedSalt)
        val computedBytes = Base64.decode(computedHash, Base64.NO_WRAP)
        val storedBytes = Base64.decode(storedHash, Base64.NO_WRAP)

        val matches = MessageDigest.isEqual(computedBytes, storedBytes)
        if (matches) {
            consecutiveFailures = 0
            lockoutUntilTimestamp = 0L
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilTimestamp = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            }
        }
        return matches
    }

    fun isLockedOut(): Boolean {
        return System.currentTimeMillis() < lockoutUntilTimestamp
    }

    fun getRemainingLockoutSeconds(): Int {
        val remainingMs = lockoutUntilTimestamp - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs / 1000).toInt() + 1 else 0
    }

    fun getRemainingAttempts(): Int {
        val remaining = MAX_FAILED_ATTEMPTS - consecutiveFailures
        return if (remaining > 0) remaining else 0
    }

    fun resetLockout() {
        consecutiveFailures = 0
        lockoutUntilTimestamp = 0L
    }
}
