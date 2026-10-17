package app.kharcha.backup

import android.content.Context
import android.net.Uri
import app.kharcha.core.BackupCodec
import app.kharcha.core.CsvExporter
import app.kharcha.core.ReviewStatus
import app.kharcha.data.PassphraseStore
import app.kharcha.data.Prefs
import app.kharcha.data.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the encrypted backup to the file the user picked (for example in Google Drive, through
 * Android's file picker) and reads it back. The bytes are encrypted before they leave the app.
 */
class BackupManager(
    private val context: Context,
    private val repository: TransactionRepository,
    private val prefs: Prefs,
    private val passphrases: PassphraseStore,
) {
    suspend fun backUpNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = prefs.backupUri ?: error("Choose where to save the backup first")
            val passphrase = passphrases.get() ?: error("Set a backup passphrase first")
            val bytes = BackupCodec.encode(repository.snapshot(), passphrase)
            passphrase.fill(' ')
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("Couldn't open the backup file. Choose the location again.")
            prefs.lastBackupAtMillis = System.currentTimeMillis()
            prefs.lastBackupError = null
        }.onFailure { prefs.lastBackupError = it.message }
    }

    suspend fun restore(uri: Uri, passphrase: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Couldn't open that file")
            val payload = BackupCodec.decode(bytes, passphrase)
            repository.restore(payload)
            payload.transactions.size
        }
    }

    suspend fun exportCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = repository.allTransactions().filter { it.status == ReviewStatus.REVIEWED }
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(CsvExporter.export(transactions, groupOf = repository.catalogNow()::groupOf).toByteArray(Charsets.UTF_8))
            } ?: error("Couldn't open that file")
            transactions.size
        }
    }
}
