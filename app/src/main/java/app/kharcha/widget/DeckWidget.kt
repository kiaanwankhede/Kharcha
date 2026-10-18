package app.kharcha.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.kharcha.KharchaApplication
import app.kharcha.MainActivity
import app.kharcha.R
import app.kharcha.core.BudgetLevel
import app.kharcha.core.Confidence
import app.kharcha.core.Deck
import app.kharcha.core.DeckCard
import app.kharcha.core.Direction
import app.kharcha.core.Money
import app.kharcha.core.UpcomingKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The home-screen deck: a stack of cards (review, safe to spend, today, month, budgets, upcoming)
 * shown one at a time. It flips to the next card by itself every few seconds; the › button flips it
 * straight away.
 */
class DeckWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AppWidgetManager.getInstance(context)
                    .partiallyUpdateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_deck).apply { showNext(R.id.deck) })
            }
            return
        }
        super.onReceive(context, intent)
    }

    // Taller or shorter widget: redraw so cards show more or fewer rows.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(id, R.id.deck)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_deck).apply {
                val adapter = Intent(context, DeckWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                setRemoteAdapter(R.id.deck, adapter)
                setEmptyView(R.id.deck, R.id.deck_empty)
                // Each card fills in what a tap on it (or on one of its buttons) should do.
                setPendingIntentTemplate(
                    R.id.deck,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, WidgetActionActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.deck_next,
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, DeckWidgetProvider::class.java)
                            .setAction(ACTION_NEXT)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.deck_empty,
                    PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                )
            }
            manager.updateAppWidget(id, views)
        }
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(ids, R.id.deck)
    }

    companion object {
        private const val ACTION_NEXT = "app.kharcha.widget.NEXT_CARD"

        /** Redraws every deck on the home screen with the latest numbers. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DeckWidgetProvider::class.java))
            @Suppress("DEPRECATION")
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.deck)
        }
    }
}

class DeckWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        DeckFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class DeckFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var cards: List<DeckCard> = emptyList()
    private var today: LocalDate = LocalDate.now(zone)
    private var hideAmounts = false

    /** True when the widget is tall enough (3+ rows) for a second payment, bill or third budget. */
    private var roomy = false

    private fun money(paise: Long): String = if (hideAmounts) "₹ ••••" else Money.format(paise)

    override fun onCreate() = Unit

    override fun onDestroy() = Unit

    // Runs on a binder thread, so blocking for the database is fine here.
    override fun onDataSetChanged() {
        val container = (context.applicationContext as KharchaApplication).container
        if (!container.prefs.onboarded) {
            cards = emptyList()
            return
        }
        today = LocalDate.now(zone)
        hideAmounts = container.prefs.widgetHidesAmounts
        roomy = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) >= 170
        cards = runBlocking(Dispatchers.IO) {
            val repository = container.repository
            val catalog = repository.catalogNow()
            Deck.build(
                all = repository.allTransactions(),
                budgets = repository.budgets.first(),
                bills = repository.cardBillsNow(),
                monthlyLimitPaise = container.prefs.monthlyLimitPaise,
                streak = container.prefs.reviewStreak,
                today = today,
                groupOf = catalog::groupOf,
            )
        }
    }

    override fun getCount(): Int = cards.size

    override fun getViewAt(position: Int): RemoteViews {
        val card = cards.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.deck_card_today)
        return when (card) {
            is DeckCard.Review -> review(card)
            is DeckCard.Safe -> safe(card)
            is DeckCard.Today -> today(card)
            is DeckCard.Month -> month(card)
            is DeckCard.Budgets -> budgets(card)
            is DeckCard.Upcoming -> upcoming(card)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 6

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    private fun views(layout: Int, onTap: Intent) = RemoteViews(context.packageName, layout).apply {
        setOnClickFillInIntent(R.id.card, onTap)
    }

    private fun review(card: DeckCard.Review) = views(R.layout.deck_card_review, WidgetActionActivity.open(WidgetActionActivity.OPEN_REVIEW)).apply {
        setTextViewText(
            R.id.review_title,
            when (card.count) {
                0 -> "All reviewed"
                1 -> "1 to review"
                else -> "${card.count} to review"
            },
        )
        val empty = when {
            card.count > 0 -> null
            card.streak > 1 -> "Nothing waiting. 🔥 ${card.streak}-day streak."
            else -> "Nothing waiting. New payments show up here."
        }
        setTextViewText(R.id.review_empty, empty.orEmpty())
        setViewVisibility(R.id.review_empty, if (empty == null) View.GONE else View.VISIBLE)
        val rows = listOf(
            Triple(R.id.review_row1, R.id.review_amount1, R.id.review_sub1) to R.id.review_accept1,
            Triple(R.id.review_row2, R.id.review_amount2, R.id.review_sub2) to R.id.review_accept2,
        )
        rows.forEachIndexed { i, (ids, accept) ->
            val t = card.top.getOrNull(i)?.takeIf { i == 0 || roomy }
            setViewVisibility(ids.first, if (t == null) View.GONE else View.VISIBLE)
            if (t == null) return@forEachIndexed
            val amount = if (t.direction == Direction.CREDIT) "+${money(t.amountPaise)}" else money(t.amountPaise)
            setTextViewText(ids.second, "$amount · ${t.merchant}")
            setTextViewText(ids.third, "${t.category.emoji} ${t.category.label} · ${t.confidence.label()}")
            setContentDescription(accept, "Accept ${t.merchant} as ${t.category.label}")
            setOnClickFillInIntent(accept, WidgetActionActivity.accept(t.id))
        }
    }

    private fun safe(card: DeckCard.Safe) = views(R.layout.deck_card_safe, WidgetActionActivity.open(WidgetActionActivity.OPEN_HOME)).apply {
        val percent = if (card.limitPaise <= 0) 0 else (card.spentPaise * 100 / card.limitPaise).toInt()
        val over = card.spentPaise >= card.limitPaise
        setTextViewText(R.id.safe_amount, money(card.safeTodayPaise))
        setProgressBar(R.id.safe_bar, 100, percent.coerceIn(0, 100), false)
        setViewVisibility(R.id.safe_bar, if (over) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.safe_bar_over, if (over) View.VISIBLE else View.GONE)
        setTextViewText(R.id.safe_caption, "${money(card.spentPaise)} of ${money(card.limitPaise)} this month")
        setOnClickFillInIntent(R.id.safe_cash, WidgetActionActivity.open(WidgetActionActivity.ADD_CASH))
    }

    private fun today(card: DeckCard.Today) = views(R.layout.deck_card_today, WidgetActionActivity.open(WidgetActionActivity.OPEN_HOME)).apply {
        setTextViewText(R.id.today_amount, money(card.spentPaise))
        setTextViewText(
            R.id.today_count,
            when (card.payments) {
                0 -> "No payments yet"
                1 -> "1 payment"
                else -> "${card.payments} payments"
            },
        )
        setTextViewText(R.id.today_waiting, if (card.waiting > 0) "${card.waiting} waiting for review" else "")
        setViewVisibility(R.id.today_waiting, if (card.waiting > 0) View.VISIBLE else View.GONE)
    }

    private fun month(card: DeckCard.Month) = views(R.layout.deck_card_month, WidgetActionActivity.open(WidgetActionActivity.OPEN_INSIGHTS)).apply {
        setTextViewText(R.id.month_name, card.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        val previous = card.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val change = card.changePercent
        setTextViewText(
            R.id.month_change,
            when {
                change == null || change == 0 -> ""
                change > 0 -> "$change% more than $previous"
                else -> "${-change}% less than $previous"
            },
        )
        setTextColor(R.id.month_change, context.getColor(if ((change ?: 0) > 0) R.color.widget_warn else R.color.widget_muted))
        setTextViewText(R.id.month_amount, money(card.spentPaise))
        setImageViewBitmap(R.id.month_bars, bars(card.lastSevenDays))
        setTextViewText(
            R.id.month_groups,
            card.topGroups.joinToString("  ·  ") { "${it.group.label.substringBefore(" &").substringBefore(",")} ${short(it.spentPaise)}" }
                .ifEmpty { "Nothing reviewed this month yet" },
        )
    }

    private fun budgets(card: DeckCard.Budgets) = views(R.layout.deck_card_budgets, WidgetActionActivity.open(WidgetActionActivity.OPEN_BUDGETS)).apply {
        val rows = listOf(
            arrayOf(R.id.budget_row1, R.id.budget_label1, R.id.budget_amount1, R.id.budget_bar1, R.id.budget_bar_warn1),
            arrayOf(R.id.budget_row2, R.id.budget_label2, R.id.budget_amount2, R.id.budget_bar2, R.id.budget_bar_warn2),
            arrayOf(R.id.budget_row3, R.id.budget_label3, R.id.budget_amount3, R.id.budget_bar3, R.id.budget_bar_warn3),
        )
        rows.forEachIndexed { i, (row, label, amount, bar, warnBar) ->
            val status = card.items.getOrNull(i)?.takeIf { i < 2 || roomy }
            setViewVisibility(row, if (status == null) View.GONE else View.VISIBLE)
            if (status == null) return@forEachIndexed
            val warn = status.level != BudgetLevel.OK
            val percent = (status.fraction * 100).toInt().coerceIn(0, 100)
            setTextViewText(label, "${status.budget.emoji} ${status.budget.label}")
            setTextViewText(amount, "${short(status.spentPaise)} / ${short(status.budget.limitPaise).removePrefix("₹")}")
            setTextColor(amount, context.getColor(if (warn) R.color.widget_warn else R.color.widget_muted))
            setProgressBar(bar, 100, percent, false)
            setProgressBar(warnBar, 100, percent, false)
            setViewVisibility(bar, if (warn) View.GONE else View.VISIBLE)
            setViewVisibility(warnBar, if (warn) View.VISIBLE else View.GONE)
        }
    }

    private fun upcoming(card: DeckCard.Upcoming) = views(R.layout.deck_card_upcoming, WidgetActionActivity.open(WidgetActionActivity.OPEN_UPCOMING)).apply {
        setTextViewText(R.id.up_total, "Upcoming · ${money(card.totalPaise)}")
        val rows = listOf(
            arrayOf(R.id.up_row1, R.id.up_label1, R.id.up_when1, R.id.up_amount1),
            arrayOf(R.id.up_row2, R.id.up_label2, R.id.up_when2, R.id.up_amount2),
        )
        rows.forEachIndexed { i, (row, label, whenId, amount) ->
            val item = card.items.getOrNull(i)?.takeIf { i == 0 || roomy }
            setViewVisibility(row, if (item == null) View.GONE else View.VISIBLE)
            if (item == null) return@forEachIndexed
            val days = ChronoUnit.DAYS.between(today, item.date)
            val date = "${item.date.dayOfMonth} ${item.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
            val soon = item.kind == UpcomingKind.CARD_BILL && days <= 3
            setTextViewText(label, item.label)
            setTextViewText(
                whenId,
                when {
                    item.kind == UpcomingKind.RECURRING -> "Expected $date"
                    days < 0 -> "Was due $date"
                    days == 0L -> "Due today"
                    days == 1L -> "Due tomorrow"
                    else -> "Due $date · in $days days"
                },
            )
            setTextColor(whenId, context.getColor(if (soon) R.color.widget_warn else R.color.widget_muted))
            setInt(row, "setBackgroundResource", if (soon) R.drawable.widget_row_alert_bg else R.drawable.widget_row_bg)
            setTextViewText(amount, money(item.amountPaise))
        }
    }

    /** Seven little columns, today's highlighted. Drawn as a picture because widgets can't size views from code. */
    private fun bars(values: List<Long>): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (280 * density).toInt()
        val height = (36 * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gap = 6 * density
        val barWidth = (width - gap * (values.size - 1)) / values.size
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
        values.forEachIndexed { i, value ->
            val barHeight = maxOf(2 * density, height * value.toFloat() / max)
            val left = i * (barWidth + gap)
            paint.color = when {
                value == 0L -> 0xFF2E3953.toInt()
                i == values.lastIndex -> 0xFF3987E5.toInt()
                else -> 0xFF1C5CAB.toInt()
            }
            canvas.drawRoundRect(RectF(left, height - barHeight, left + barWidth, height.toFloat()), 3 * density, 3 * density, paint)
        }
        return bitmap
    }

    /** ₹450, ₹2.9k, ₹1.2L: short amounts for tight spaces. */
    private fun short(paise: Long): String {
        if (hideAmounts) return "₹ ••"
        val rupees = paise / 100
        fun oneDecimal(value: Double) = if (value >= 10) "%.0f".format(value) else "%.1f".format(value).removeSuffix(".0")
        return when {
            rupees < 1_000 -> "₹$rupees"
            rupees < 1_00_000 -> "₹${oneDecimal(rupees / 1_000.0)}k"
            else -> "₹${oneDecimal(rupees / 1_00_000.0)}L"
        }
    }

    private fun Confidence.label() = when (this) {
        Confidence.SURE -> "Sure"
        Confidence.LIKELY -> "Likely"
        Confidence.GUESS -> "Guess"
    }
}
