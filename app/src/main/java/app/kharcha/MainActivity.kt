package app.kharcha

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.notify.Notifications
import app.kharcha.ui.AppRoot
import app.kharcha.ui.KharchaTheme
import app.kharcha.ui.LockScreen

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var openReviewRequest by mutableIntStateOf(0)
    private var stoppedAt = 0L
    private lateinit var prompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KharchaApplication).container
        unlocked = savedInstanceState?.getBoolean(KEY_UNLOCKED) ?: false
        prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            },
        )

        handleIntent(intent)

        setContent {
            val theme by container.prefs.themeFlow.collectAsStateWithLifecycle()
            KharchaTheme(theme) {
                if (container.prefs.lockEnabled && !unlocked) {
                    LockScreen(onUnlock = ::authenticate)
                } else {
                    AppRoot(container, startWithOnboarding = !container.prefs.onboarded, openReviewRequest = openReviewRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifications.EXTRA_OPEN_REVIEW, false) == true) {
            openReviewRequest++
            intent.removeExtra(Notifications.EXTRA_OPEN_REVIEW)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after a minute in the background. Shorter trips (the file picker) stay unlocked.
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MILLIS) unlocked = false
    }

    private fun authenticate() {
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kharcha")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val RELOCK_AFTER_MILLIS = 60_000L
    }
}
