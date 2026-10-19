package app.kharcha.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.capture.PaymentNotificationListener
import app.kharcha.core.Money
import app.kharcha.core.NotABackupException
import app.kharcha.core.WrongPassphraseException
import app.kharcha.notify.Notifications
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, openCategories: () -> Unit, modifier: Modifier = Modifier, openRoute: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = container.prefs

    var message by remember { mutableStateOf<String?>(null) }
    var limitText by rememberSaveable { mutableStateOf(prefs.monthlyLimitPaise?.let { (it / 100).toString() }.orEmpty()) }
    var hasPassphrase by remember { mutableStateOf(container.passphrases.hasPassphrase()) }
    var backupUri by remember { mutableStateOf(prefs.backupUri) }
    var lastBackup by remember { mutableStateOf(prefs.lastBackupAtMillis) }
    var lastError by remember { mutableStateOf(prefs.lastBackupError) }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var scanStatus by remember { mutableStateOf<String?>(null) }
    var askingScanDate by remember { mutableStateOf(false) }
    var sms by remember { mutableStateOf(hasSmsPermission(context)) }
    var notifications by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    var lockEnabled by remember { mutableStateOf(prefs.lockEnabled) }
    var secureScreen by remember { mutableStateOf(prefs.secureScreen) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var widgetHides by remember { mutableStateOf(prefs.widgetHidesAmounts) }
    var postNotifications by remember { mutableStateOf(Notifications.canPost(context)) }
    var reviewReminder by remember { mutableStateOf(prefs.reviewReminder) }
    val theme by prefs.themeFlow.collectAsStateWithLifecycle()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postNotifications = Notifications.canPost(context)
    }
    var contactsAllowed by remember { mutableStateOf(container.contacts.hasPermission()) }
    val applyContacts: () -> Unit = {
        scope.launch {
            val changed = container.repository.applyContactNames()
            message = if (changed > 0) "Named $changed payments from your contacts." else "No phone-number payees matched your contacts."
        }
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
        if (granted) applyContacts()
    }

    LifecycleResumeEffect(Unit) {
        sms = hasSmsPermission(context)
        notifications = PaymentNotificationListener.isEnabled(context)
        postNotifications = Notifications.canPost(context)
        onPauseOrDispose { }
    }

    val chooseBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefs.backupUri = uri
            backupUri = uri
            scope.launch {
                val result = container.backup.backUpNow()
                lastBackup = prefs.lastBackupAtMillis
                lastError = prefs.lastBackupError
                message = if (result.isSuccess) "Backed up." else result.exceptionOrNull()?.message
            }
        }
    }
    val pickRestoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val result = container.backup.exportCsv(uri)
                message = result.fold({ "Exported $it transactions." }, { it.message })
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        message?.let { Panel(color = Accent) { Text(it, color = Paper) } }

        SectionLabel("Appearance")
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    if (theme == value) {
                        Button(onClick = {}) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { prefs.theme = value }) { Text(label) }
                    }
                }
            }
        }

        SectionLabel("Review")
        Panel {
            Text("New payments wait in Review until you confirm them. Only reviewed payments count in totals.", color = Muted)
            if (!postNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text("Notifications are off, so you won’t hear about new payments.", color = Rust)
                Button(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("End-of-day summary", fontWeight = FontWeight.SemiBold)
                    Text("At 9 PM: today’s payments and spend, and anything still waiting for review.", color = Muted)
                }
                Switch(checked = reviewReminder, onCheckedChange = {
                    reviewReminder = it
                    prefs.reviewReminder = it
                })
            }
        }

        SectionLabel("Plan")
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openRoute("budgets") }) { Text("Budgets") }
                OutlinedButton(onClick = { openRoute("upcoming") }) { Text("Bills") }
                OutlinedButton(onClick = { openRoute("accounts") }) { Text("Accounts") }
            }
        }

        SectionLabel("Payee names")
        Panel {
            Text(
                "UPI transfers to phone numbers show as “UPI ··3210”. Allow contacts to show “Mom” instead. Names are looked up on this phone only.",
                color = Muted,
            )
            if (contactsAllowed) {
                OutlinedButton(onClick = applyContacts) { Text("Update past payments with contact names") }
            } else {
                Button(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Use contact names") }
            }
            Text("You can also rename any payee from its payment screen.", style = MaterialTheme.typography.bodySmall, color = Faint)
        }

        SectionLabel("Tags")
        Panel {
            Text("Rename, merge or delete your tags.", color = Muted)
            Button(onClick = openCategories) { Text("Manage tags") }
        }

        SectionLabel("Monthly limit")
        Panel {
            Text("Used for “Safe to spend today” on the home screen. Leave empty to hide it.", color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit).take(9) },
                    modifier = Modifier.weight(1f),
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(onClick = {
                    prefs.monthlyLimitPaise = limitText.toLongOrNull()?.takeIf { it > 0 }?.times(100)
                    message = prefs.monthlyLimitPaise?.let { "Monthly limit set to ${Money.format(it)}." } ?: "Monthly limit removed."
                }) { Text("Save") }
            }
        }

        SectionLabel("Backup")
        Panel {
            Text(
                "Your data is encrypted with your passphrase, then saved to a file you choose. Pick Google Drive in the " +
                    "file picker to keep it off the phone. It updates nightly while charging on Wi-Fi.",
                color = Muted,
            )
            Text(
                when {
                    lastBackup != null -> "Last backup: ${millisText(lastBackup!!)}"
                    else -> "No backup yet"
                },
                fontWeight = FontWeight.SemiBold,
            )
            lastError?.let { Text("Last attempt failed: $it", color = Rust) }
            if (!hasPassphrase) {
                Button(onClick = { showPassphraseDialog = true }) { Text("1. Set backup passphrase") }
            } else if (backupUri == null) {
                Button(onClick = { chooseBackupFile.launch("kharcha-backup.khb") }) { Text("2. Choose where to save") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val result = container.backup.backUpNow()
                            lastBackup = prefs.lastBackupAtMillis
                            lastError = prefs.lastBackupError
                            message = if (result.isSuccess) "Backed up." else result.exceptionOrNull()?.message
                        }
                    }) { Text("Back up now") }
                    OutlinedButton(onClick = { chooseBackupFile.launch("kharcha-backup.khb") }) { Text("Change file") }
                }
                TextButton(onClick = { showPassphraseDialog = true }) { Text("Change passphrase") }
            }
            OutlinedButton(onClick = { pickRestoreFile.launch(arrayOf("*/*")) }) { Text("Restore from a backup") }
            OutlinedButton(onClick = { exportCsv.launch("kharcha-transactions.csv") }) { Text("Export to CSV (Excel, Sheets)") }
        }

        SectionLabel("Capture")
        Panel {
            Text("SMS: " + if (sms) "allowed" else "not allowed", fontWeight = FontWeight.SemiBold)
            if (!sms) {
                Text("Open app settings → ⋮ → Allow restricted settings, then allow SMS under Permissions.", color = Muted)
                OutlinedButton(onClick = { openAppSettings(context) }) { Text("Open app settings") }
            }
            Text("Payment app notifications: " + if (notifications) "allowed" else "off", fontWeight = FontWeight.SemiBold)
            if (!notifications) OutlinedButton(onClick = { openNotificationAccess(context) }) { Text("Open notification access") }
            Button(
                enabled = sms && scanStatus?.startsWith("Scanning") != true,
                onClick = { askingScanDate = true },
            ) { Text("Read SMS inbox from a date…") }
            if (askingScanDate) {
                ScanDateDialog(onDismiss = { askingScanDate = false }, onPick = { date ->
                    askingScanDate = false
                    scope.launch {
                        scanStatus = "Scanning…"
                        val result = container.scanner.scan(date.atStartOfDay(Zone).toInstant().toEpochMilli()) {
                            scanStatus = "Scanning… ${it.checked} of ${it.total}"
                        }
                        container.prefs.allMessagesLoaded = true
                        scanStatus = "Done. ${result.found} new payments found; they're waiting in Review."
                    }
                })
            }
            scanStatus?.let { Text(it, color = Muted) }
        }

        SectionLabel("Privacy")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App lock", fontWeight = FontWeight.SemiBold)
                    Text("Fingerprint, face or screen lock to open Kharcha.", color = Muted)
                }
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { enable ->
                        val can = BiometricManager.from(context).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                        if (enable && !can) {
                            message = "Set up a screen lock on your phone first."
                        } else {
                            prefs.lockEnabled = enable
                            lockEnabled = enable
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide from recents & screenshots", fontWeight = FontWeight.SemiBold)
                    Text("The app shows blank in the app switcher and can’t be screenshotted.", color = Muted)
                }
                Switch(checked = secureScreen, onCheckedChange = {
                    secureScreen = it
                    prefs.secureScreen = it
                    (context as? android.app.Activity)?.let { activity -> app.kharcha.applySecureScreen(activity, it) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide amounts on the widget", fontWeight = FontWeight.SemiBold)
                    Text("Shows ₹ •••• on the home screen.", color = Muted)
                }
                Switch(checked = widgetHides, onCheckedChange = {
                    widgetHides = it
                    prefs.widgetHidesAmounts = it
                    app.kharcha.widget.DeckWidgetProvider.refresh(context)
                })
            }
            Text("No account, no server. The only data leaving the phone is your encrypted backup file.", color = Muted)
        }

        SectionLabel("About")
        Panel {
            Text("Kharcha ${WhatsNew.versionName(context)}", fontWeight = FontWeight.SemiBold)
            Text("New builds are published on GitHub. Download the latest kharcha.apk and open it to update; your data stays.", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { WhatsNew.openReleases(context) }) { Text("Check for updates") }
                TextButton(onClick = { showWhatsNew = true }) { Text("What’s new") }
            }
        }
    }

    if (showWhatsNew) WhatsNewDialog(onDismiss = { showWhatsNew = false })

    if (showPassphraseDialog) {
        PassphraseDialog(
            confirm = true,
            onDismiss = { showPassphraseDialog = false },
            onSubmit = { passphrase ->
                container.passphrases.save(passphrase)
                hasPassphrase = true
                showPassphraseDialog = false
                message = "Passphrase saved. Keep it somewhere safe: without it the backup can’t be opened."
            },
        )
    }

    restoreUri?.let { uri ->
        PassphraseDialog(
            confirm = false,
            title = "Restore backup",
            note = "This replaces all transactions on this phone with the ones in the backup.",
            onDismiss = { restoreUri = null },
            onSubmit = { passphrase ->
                restoreUri = null
                scope.launch {
                    val result = container.backup.restore(uri, passphrase)
                    message = result.fold(
                        { "Restored $it transactions." },
                        {
                            when (it) {
                                is WrongPassphraseException -> "That passphrase doesn’t match this backup."
                                is NotABackupException -> "That file isn’t a Kharcha backup."
                                else -> it.message
                            }
                        },
                    )
                    limitText = prefs.monthlyLimitPaise?.let { p -> (p / 100).toString() }.orEmpty()
                }
            },
        )
    }
}

@Composable
private fun PassphraseDialog(
    confirm: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
    title: String = "Backup passphrase",
    note: String = "At least 8 characters. Nobody can recover it if you forget it, so write it down.",
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = first.length >= 8 && (!confirm || first == second)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(note)
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text("Type it again") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = second.isNotEmpty() && second != first,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(first.toCharArray()) }, enabled = valid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
