package app.kharcha.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kharcha.KharchaApplication
import app.kharcha.MainActivity
import app.kharcha.R
import app.kharcha.core.Direction
import app.kharcha.core.Money
import app.kharcha.core.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Notifications {
    private const val CHANNEL_PAYMENTS = "payments"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val REMINDER_ID = 1
    private const val CHANNEL_ALERTS = "alerts"
    const val EXTRA_OPEN_REVIEW = "open_review"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENTS, "New payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A note after each payment so you can review it"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Budgets, bills & balance", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Budget limits, credit card due dates and low balance"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Review reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "An evening reminder when payments are waiting for review"
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openReview(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_REVIEW, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun showNewPayment(context: Context, t: Transaction) {
        if (!canPost(context)) return
        val id = t.id.toInt()
        val accept = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReviewActionReceiver::class.java)
                .setAction(ReviewActionReceiver.ACTION_ACCEPT)
                .putExtra(ReviewActionReceiver.EXTRA_ID, t.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (t.direction == Direction.DEBIT) {
            "${Money.format(t.amountPaise)} · ${t.merchant}"
        } else {
            "+${Money.format(t.amountPaise)} from ${t.merchant}"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle(title)
            .setContentText("${t.category.emoji} ${t.category.label}? Tap to review")
            .setContentIntent(openReview(context, id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, "✓ Looks right", accept)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** End-of-day summary: today's payments and spend, and what is still waiting for review. */
    @SuppressLint("MissingPermission")
    fun showDailySummary(context: Context, paymentsToday: Int, spentTodayPaise: Long, pending: Int, streak: Int) {
        if (!canPost(context) || (paymentsToday == 0 && pending == 0)) return
        val title = if (paymentsToday > 0) {
            "Today: $paymentsToday payment${if (paymentsToday == 1) "" else "s"} · ${Money.format(spentTodayPaise)}"
        } else {
            "$pending payment${if (pending == 1) "" else "s"} to review"
        }
        val text = when {
            pending > 0 -> "$pending waiting for review. Take a minute to go through them."
            streak > 1 -> "All reviewed. 🔥 $streak-day streak."
            else -> "All reviewed. Nice."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openReview(context, REMINDER_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_ID, notification)
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun alert(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun showBudgetAlert(context: Context, status: app.kharcha.core.BudgetStatus) {
        val b = status.budget
        val over = status.level == app.kharcha.core.BudgetLevel.OVER
        alert(
            context,
            ("budget" + b.id).hashCode(),
            if (over) "${b.emoji} ${b.label} budget crossed" else "${b.emoji} ${b.label} at ${(status.fraction * 100).toInt()}%",
            if (over) {
                "${Money.format(status.spentPaise)} spent of ${Money.format(b.limitPaise)} this month."
            } else {
                "${Money.format(status.leftPaise)} left of ${Money.format(b.limitPaise)} this month."
            },
        )
    }

    fun showBillReminder(context: Context, bill: app.kharcha.core.CardBill, daysLeft: Long) {
        val whenText = when {
            daysLeft < 0 -> "was due on ${bill.dueDate.dayOfMonth} ${bill.dueDate.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
            daysLeft == 0L -> "is due today"
            daysLeft == 1L -> "is due tomorrow"
            else -> "is due in $daysLeft days"
        }
        alert(
            context,
            ("bill" + bill.id).hashCode(),
            "💳 ${bill.label} bill $whenText",
            "Total ${Money.format(bill.totalDuePaise)}" + (bill.minDuePaise?.let { ", minimum ${Money.format(it)}" } ?: "") + ".",
        )
    }

    fun showLowBalance(context: Context, account: String, balancePaise: Long) {
        alert(context, ("low" + account).hashCode(), "Low balance in $account", "Balance is ${Money.format(balancePaise)}.")
    }

    fun cancel(context: Context, transactionId: Long) {
        NotificationManagerCompat.from(context).cancel(transactionId.toInt())
    }
}

/** Handles "✓ Looks right" on a payment notification without opening the app. */
class ReviewActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACCEPT) return
        val id = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it > 0 } ?: return
        val repository = (context.applicationContext as KharchaApplication).container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.accept(id)
                Notifications.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "app.kharcha.ACCEPT_PAYMENT"
        const val EXTRA_ID = "transaction_id"
    }
}

/** Every evening around 9 PM: a nudge if anything is waiting for review. */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KharchaApplication).container
        if (container.prefs.reviewReminder) {
            val (count, spent) = container.repository.todaySummary()
            Notifications.showDailySummary(
                applicationContext,
                paymentsToday = count,
                spentTodayPaise = spent,
                pending = container.repository.pendingCount.first(),
                streak = container.prefs.reviewStreak,
            )
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "review-reminder"

        fun schedule(context: Context) {
            val zone = ZoneId.of("Asia/Kolkata")
            val now = ZonedDateTime.now(zone)
            var next = now.with(LocalTime.of(21, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Every morning around 9:30: reminders for credit card bills due in 3 days, tomorrow, today, or just missed. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KharchaApplication).container
        val zone = ZoneId.of("Asia/Kolkata")
        val today = java.time.LocalDate.now(zone)
        val transactions = container.repository.allTransactions().filter { it.status != app.kharcha.core.ReviewStatus.IGNORED }
        container.repository.cardBillsNow()
            .groupBy { it.last4 ?: it.bank ?: "" }
            .mapNotNull { (_, bills) -> bills.maxByOrNull { it.receivedAtMillis } }
            .forEach { bill ->
                val state = app.kharcha.core.CardBills.state(bill, transactions, today)
                if (state == app.kharcha.core.BillState.PAID) return@forEach
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, bill.dueDate)
                if (daysLeft in listOf(3L, 1L, 0L, -1L) && container.prefs.firstTime("bill-${bill.id}-$daysLeft")) {
                    Notifications.showBillReminder(applicationContext, bill, daysLeft)
                }
            }
        return Result.success()
    }

    companion object {
        private const val NAME = "bill-reminder"

        fun schedule(context: Context) {
            val zone = ZoneId.of("Asia/Kolkata")
            val now = ZonedDateTime.now(zone)
            var next = now.with(LocalTime.of(9, 30))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
