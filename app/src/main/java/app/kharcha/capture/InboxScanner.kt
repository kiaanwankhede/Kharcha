package app.kharcha.capture

import android.content.Context
import android.provider.Telephony
import app.kharcha.data.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanProgress(val checked: Int, val total: Int, val found: Int, val accounts: Int)

/** Reads the existing SMS inbox from a date the user picks. Safe to re-run: messages already seen are skipped. */
class InboxScanner(private val context: Context, private val repository: TransactionRepository) {

    suspend fun scan(since: Long, onProgress: (ScanProgress) -> Unit = {}): ScanProgress = withContext(Dispatchers.IO) {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return@withContext ScanProgress(0, 0, 0, 0)

        var checked = 0
        var found = 0
        val accounts = mutableSetOf<String>()
        cursor.use {
            val total = it.count
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                checked++
                val sender = it.getString(addressIndex).orEmpty()
                // Every message goes in (so you can mark what isn't a payment); only bank ones become payments.
                val body = it.getString(bodyIndex).orEmpty()
                val receivedAt = it.getLong(dateIndex)
                val captured = repository.capture(sender, body, receivedAt)
                if (captured.newTransaction != null) found++
                captured.parsed?.let { parsed -> parsed.accountLast4?.let { last4 -> accounts += (parsed.bank.orEmpty() + last4) } }
                if (checked % 50 == 0 || checked == total) {
                    onProgress(ScanProgress(checked, total, found, accounts.size))
                }
            }
            ScanProgress(checked, total, found, accounts.size).also(onProgress)
        }
    }
}
