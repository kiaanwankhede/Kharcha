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
    const val EXTRA_OPEN_REVIEW = "open_review"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENTS, "New payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A note after each payment so you can review it"
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

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, pending: Int) {
        if (!canPost(context) || pending <= 0) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle("$pending payment${if (pending == 1) "" else "s"} to review")
            .setContentText("Take a minute to go through today’s spending.")
            .setContentIntent(openReview(context, REMINDER_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_ID, notification)
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
            Notifications.showReminder(applicationContext, container.repository.pendingCount.first())
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
