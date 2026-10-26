package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.PinSecurityManager
import com.example.security.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Before
    fun setUp() {
        PinSecurityManager.resetLockout()
        SessionManager.lockAll()
    }

    @Test
    fun testAppNameStringResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LockSphere", appName)
    }

    @Test
    fun testPinSecurityHashingAndVerification() {
        val salt = PinSecurityManager.generateSalt()
        assertTrue(salt.isNotBlank())

        val correctPin = "4829"
        val wrongPin = "0000"

        val hash = PinSecurityManager.hashPin(correctPin, salt)
        assertTrue(hash.isNotBlank())

        // Verify correct PIN matches
        assertTrue(PinSecurityManager.verifyPin(correctPin, hash, salt))

        // Verify incorrect PIN fails
        assertFalse(PinSecurityManager.verifyPin(wrongPin, hash, salt))
    }

    @Test
    fun testPinSecurityBruteForceLockout() {
        val salt = PinSecurityManager.generateSalt()
        val correctPin = "9999"
        val hash = PinSecurityManager.hashPin(correctPin, salt)

        // 5 consecutive failures should trigger lockout
        repeat(5) {
            PinSecurityManager.verifyPin("1111", hash, salt)
        }

        assertTrue(PinSecurityManager.isLockedOut())
        assertTrue(PinSecurityManager.getRemainingLockoutSeconds() > 0)

        // Even with correct PIN, verification should fail while locked out
        assertFalse(PinSecurityManager.verifyPin(correctPin, hash, salt))

        // After reset, verification succeeds
        PinSecurityManager.resetLockout()
        assertFalse(PinSecurityManager.isLockedOut())
        assertTrue(PinSecurityManager.verifyPin(correctPin, hash, salt))
    }

    @Test
    fun testSessionManagerImmediateAndTimeoutRelock() {
        val testPkg = "com.android.chrome"

        // Initially not unlocked
        assertFalse(SessionManager.isUnlocked(testPkg, 0))

        // Record unlock
        SessionManager.recordUnlock(testPkg)
        assertTrue(SessionManager.isUnlocked(testPkg, 0))

        // Screen turned off invalidates sessions when lockOnScreenOff is true
        SessionManager.onScreenTurnedOff(lockOnScreenOff = true)
        assertFalse(SessionManager.isUnlocked(testPkg, 0))
    }
}
