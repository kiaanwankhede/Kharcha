package app.kharcha.ui

import app.kharcha.core.Direction
import app.kharcha.core.Money
import app.kharcha.core.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val Zone: ZoneId = ZoneId.of("Asia/Kolkata")

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun Transaction.localDate(): LocalDate = Instant.ofEpochMilli(timestampMillis).atZone(Zone).toLocalDate()

fun Transaction.signedAmount(): String =
    if (direction == Direction.DEBIT) "−" + Money.format(amountPaise) else "+" + Money.format(amountPaise)

fun Transaction.dateTimeText(): String = Instant.ofEpochMilli(timestampMillis).atZone(Zone).format(dateTimeFormat)

fun LocalDate.dayHeader(today: LocalDate = LocalDate.now(Zone)): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(dayFormat)
}

fun YearMonth.title(): String = format(monthFormat)

fun millisText(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Zone).format(dateTimeFormat)
