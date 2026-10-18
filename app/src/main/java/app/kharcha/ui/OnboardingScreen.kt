package app.kharcha.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.kharcha.AppContainer
import app.kharcha.capture.PaymentNotificationListener
import app.kharcha.capture.ScanProgress
import app.kharcha.notify.Notifications
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    when (step) {
        0 -> Welcome(onNext = { step = 1 })
        1 -> Permissions(onNext = { smsGranted -> if (smsGranted) step = 2 else onDone() })
        else -> Backfill(container, onDone)
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Rust),
            contentAlignment = Alignment.Center,
        ) { Text("₹", color = CardColor, fontSize = 52.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
        Text("Kharcha", color = Paper, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Your spending, tracked automatically", color = Sand)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Navy)) {
            Text("Get started")
        }
    }
}

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

fun hasSmsPermission(context: Context): Boolean = SMS_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun openNotificationAccess(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun Permissions(onNext: (smsGranted: Boolean) -> Unit) {
    val context = LocalContext.current
    var sms by remember { mutableStateOf(hasSmsPermission(context)) }
    var notifications by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        sms = hasSmsPermission(context)
        asked = true
    }
    var postNotifications by remember { mutableStateOf(Notifications.canPost(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postNotifications = Notifications.canPost(context)
    }
    LifecycleResumeEffect(Unit) {
        sms = hasSmsPermission(context)
        notifications = PaymentNotificationListener.isEnabled(context)
        postNotifications = Notifications.canPost(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Allow access", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Kharcha reads only messages from banks and UPI apps. Everything stays on this phone.", color = Muted)

        Panel(color = Sand) {
            Text("SMS" + if (sms) "  ✓ allowed" else "", fontWeight = FontWeight.Bold)
            Text("Bank and UPI messages, to record each payment.", color = Muted)
            if (!sms) {
                Button(onClick = { launcher.launch(SMS_PERMISSIONS) }) { Text("Allow SMS") }
            }
        }

        if (!sms && asked) {
            Panel(color = WarmTint) {
                Text("Pixel blocked it? That’s expected.", fontWeight = FontWeight.Bold)
                Text(
                    "Android blocks SMS access for apps installed outside the Play Store until you allow it once:\n" +
                        "1. Tap Open app settings below.\n" +
                        "2. Tap ⋮ (top right) → Allow restricted settings, and confirm.\n" +
                        "3. Come back here and tap Allow SMS again.",
                    color = Muted,
                )
                OutlinedButton(onClick = { openAppSettings(context) }) { Text("Open app settings") }
            }
        }

        Panel(color = Sand) {
            Text("Payment app notifications" + if (notifications) "  ✓ allowed" else "  (optional)", fontWeight = FontWeight.Bold)
            Text("Google Pay, PhonePe, Paytm. Catches payments with no bank SMS and gives better names.", color = Muted)
            if (!notifications) {
                OutlinedButton(onClick = { openNotificationAccess(context) }) { Text("Open notification access") }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Panel(color = Sand) {
                Text("Kharcha notifications" + if (postNotifications) "  ✓ allowed" else "", fontWeight = FontWeight.Bold)
                Text("A note after each payment so you can review it, and a 9 PM reminder.", color = Muted)
                if (!postNotifications) {
                    OutlinedButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { onNext(sms) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (sms) "Continue" else "Skip for now")
        }
    }
}

@Composable
private fun Backfill(container: AppContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(ScanProgress(0, 0, 0, 0)) }
    var finished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!finished) {
            container.scanner.scan { progress = it }
            container.prefs.allMessagesLoaded = true
            finished = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (finished) "All set" else "Reading the last 6 months",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        val fraction = if (progress.total == 0) (if (finished) 1f else 0f) else progress.checked.toFloat() / progress.total
        Bar(fraction, color = Rust)
        Text("${progress.checked} of ${progress.total} messages checked", color = Muted)
        Panel(color = Sand) {
            Text("${progress.found}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("transactions found", color = Muted)
        }
        Panel(color = Sand) {
            Text("${progress.accounts}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("accounts and cards", color = Muted)
        }
        Spacer(Modifier.weight(1f))
        if (finished) {
            Text("Where should your review start?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Payments before this are accepted as they are. Everything after waits for you to review.", color = Muted)
            fun startFrom(daysBack: Long?) {
                scope.launch {
                    val cutoff = daysBack?.let { LocalDate.now(Zone).minusDays(it).atStartOfDay(Zone).toInstant().toEpochMilli() }
                    if (cutoff != null) container.repository.reviewAllBefore(cutoff)
                    onDone()
                }
            }
            Button(onClick = { startFrom(0) }, modifier = Modifier.fillMaxWidth()) { Text("Today only (recommended)") }
            OutlinedButton(onClick = { startFrom(6) }, modifier = Modifier.fillMaxWidth()) { Text("The last 7 days") }
            OutlinedButton(onClick = { startFrom(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Everything (${progress.found} payments)")
            }
        } else {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Skip (you can re-scan from Settings)") }
        }
    }
}
