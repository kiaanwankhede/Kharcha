package app.kharcha.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small settings. Kept in plain SharedPreferences, which Android's own backup also covers. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("kharcha", Context.MODE_PRIVATE)

    private val _monthlyLimit = MutableStateFlow(monthlyLimitPaise)
    val monthlyLimit: StateFlow<Long?> = _monthlyLimit.asStateFlow()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit { putBoolean("onboarded", value) }

    var monthlyLimitPaise: Long?
        get() = prefs.getLong("monthly_limit", -1L).takeIf { it > 0 }
        set(value) {
            prefs.edit { if (value == null) remove("monthly_limit") else putLong("monthly_limit", value) }
            _monthlyLimit.value = value
        }

    var backupUri: Uri?
        get() = prefs.getString("backup_uri", null)?.let(Uri::parse)
        set(value) = prefs.edit { putString("backup_uri", value?.toString()) }

    var lastBackupAtMillis: Long?
        get() = prefs.getLong("last_backup_at", -1L).takeIf { it > 0 }
        set(value) = prefs.edit { putLong("last_backup_at", value ?: -1L) }

    var lastBackupError: String?
        get() = prefs.getString("last_backup_error", null)
        set(value) = prefs.edit { putString("last_backup_error", value) }

    var reviewReminder: Boolean
        get() = prefs.getBoolean("review_reminder", true)
        set(value) = prefs.edit { putBoolean("review_reminder", value) }

    private val _theme = MutableStateFlow(theme)

    /** "system", "light" or "dark". */
    val themeFlow: StateFlow<String> = _theme.asStateFlow()

    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) {
            prefs.edit { putString("theme", value) }
            _theme.value = value
        }

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock_enabled", false)
        set(value) = prefs.edit { putBoolean("lock_enabled", value) }
}
