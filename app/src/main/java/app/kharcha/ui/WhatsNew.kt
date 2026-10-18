package app.kharcha.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shown once after the app is updated. */
object WhatsNew {
    const val RELEASES_URL = "https://github.com/kiaanwankhede/Kharcha/releases/latest"

    val ITEMS = listOf(
        "📨 Every message from your inbox (never one-time codes) is in Review now, next to the payments: tap Not a payment or It's a payment.",
        "👆 No more swiping on review cards: use the buttons.",
        "🏷️ Tags instead of categories: give a payment as many as you like. Type one: if you have it, it's used; if it's close, it's suggested; if it's new, you're asked first. The first tag counts in your totals; every tag has its own total.",
        "📱 Home-screen widget: swipe through review, safe to spend, today, month, budgets and bills. Accept payments right from it.",
        "＋ Add cash from Quick Settings: pull down, tap, type.",
        "🤝 Lent & borrowed: see who owes you, ask with a UPI link, pay back in one tap.",
        "🏖️ Trips & tags: payments during a trip get tagged by themselves.",
        "🧾 Office reimbursements stay out of your spending.",
        "🎯 Savings goals, a spending calendar, month reports, payee pages and a tax view.",
        "🔒 Hide the app from screenshots and recents; hide widget amounts.",
    )

    fun versionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** True when this launch is the first since an update (not a fresh install). */
    fun shouldShow(context: Context, lastSeen: Long): Boolean {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val updated = info.lastUpdateTime > info.firstInstallTime
        return updated && lastSeen < info.longVersionCode
    }

    fun openReleases(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
    }
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What’s new") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WhatsNew.ITEMS.forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Nice") } },
    )
}
