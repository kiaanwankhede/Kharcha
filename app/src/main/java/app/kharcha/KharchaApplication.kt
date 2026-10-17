package app.kharcha

import android.app.Application
import android.content.Context
import app.kharcha.backup.BackupManager
import app.kharcha.backup.BackupWorker
import app.kharcha.capture.InboxScanner
import app.kharcha.data.KharchaDatabase
import app.kharcha.data.PassphraseStore
import app.kharcha.data.Prefs
import app.kharcha.data.TransactionRepository
import app.kharcha.notify.Notifications
import app.kharcha.notify.ReviewReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KharchaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BackupWorker.schedule(this)
        Notifications.createChannels(this)
        ReviewReminderWorker.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { container.repository.migrateToCategoryTree() }
    }
}

/** The app's shared objects, created once. */
class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val passphrases = PassphraseStore(context)
    private val database = KharchaDatabase.create(context)
    val repository = TransactionRepository(database, prefs)
    val scanner = InboxScanner(context, repository)
    val backup = BackupManager(context, repository, prefs, passphrases)
}
