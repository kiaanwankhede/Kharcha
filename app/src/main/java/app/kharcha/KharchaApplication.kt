package app.kharcha

import android.app.Application
import android.content.Context
import app.kharcha.backup.BackupManager
import app.kharcha.backup.BackupWorker
import app.kharcha.capture.InboxScanner
import app.kharcha.data.ContactNames
import app.kharcha.data.KharchaDatabase
import app.kharcha.data.PassphraseStore
import app.kharcha.data.Prefs
import app.kharcha.data.TransactionRepository
import app.kharcha.notify.Notifications
import app.kharcha.notify.BillReminderWorker
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
        BillReminderWorker.schedule(this)
        container.repository.onBudgetStatus = { statuses ->
            val month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata"))
            statuses.forEach { status ->
                if (container.prefs.firstTime("budget-${status.budget.id}-$month-${status.level}")) {
                    Notifications.showBudgetAlert(this, status)
                }
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { container.repository.migrateToCategoryTree() }
    }
}

/** The app's shared objects, created once. */
class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val passphrases = PassphraseStore(context)
    private val database = KharchaDatabase.create(context)
    val contacts = ContactNames(context)
    val repository = TransactionRepository(database, prefs, contacts::lookup)
    val scanner = InboxScanner(context, repository)
    val backup = BackupManager(context, repository, prefs, passphrases)
}
