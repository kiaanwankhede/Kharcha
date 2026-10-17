package app.kharcha.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.kharcha.KharchaApplication
import app.kharcha.core.SmsParser
import app.kharcha.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Picks up each new SMS as it arrives. Only bank senders are parsed; everything else is ignored. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long SMS arrives in parts; join the parts from each sender back together.
        val bySender = messages.filterNotNull().groupBy { it.originatingAddress.orEmpty() }
        val repository = (context.applicationContext as KharchaApplication).container.repository

        val pending = goAsync()
        scope.launch {
            try {
                for ((sender, parts) in bySender) {
                    if (!SmsParser.isLikelyBankSender(sender)) continue
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val receivedAt = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    val parsed = SmsParser.parse(sender, body, receivedAt) ?: continue
                    repository.ingest(parsed)?.let { Notifications.showNewPayment(context, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
