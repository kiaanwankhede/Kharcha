package app.kharcha.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.kharcha.KharchaApplication
import app.kharcha.MainActivity
import app.kharcha.OpenRequest
import app.kharcha.notify.Notifications
import kotlinx.coroutines.launch

/**
 * Invisible stop for taps on the widget. Accepting a payment happens right here, without opening
 * the app; everything else opens the app at the right place.
 */
class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (action == ACCEPT) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            val container = (application as KharchaApplication).container
            if (id > 0) {
                container.scope.launch {
                    container.repository.accept(id)
                    Notifications.cancel(applicationContext, id)
                    DeckWidgetProvider.refresh(applicationContext)
                }
            }
        } else {
            val open = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            when (action) {
                OPEN_REVIEW -> open.putExtra(Notifications.EXTRA_OPEN_REVIEW, true)
                ADD_CASH -> open.putExtra(OpenRequest.ADD_CASH, true)
                OPEN_INSIGHTS -> open.putExtra(OpenRequest.TAB, "insights")
                OPEN_BUDGETS -> open.putExtra(OpenRequest.ROUTE, "budgets")
                OPEN_UPCOMING -> open.putExtra(OpenRequest.ROUTE, "upcoming")
            }
            startActivity(open)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_ACTION = "widget_action"
        private const val EXTRA_ID = "transaction_id"
        private const val ACCEPT = "accept"
        const val OPEN_HOME = "home"
        const val OPEN_REVIEW = "review"
        const val OPEN_INSIGHTS = "insights"
        const val OPEN_BUDGETS = "budgets"
        const val OPEN_UPCOMING = "upcoming"
        const val ADD_CASH = "add_cash"

        /** Fill-in for a tap that opens the app somewhere. */
        fun open(action: String): Intent = Intent().putExtra(EXTRA_ACTION, action)

        /** Fill-in for the ✓ on a payment waiting for review. */
        fun accept(id: Long): Intent = Intent().putExtra(EXTRA_ACTION, ACCEPT).putExtra(EXTRA_ID, id)
    }
}
