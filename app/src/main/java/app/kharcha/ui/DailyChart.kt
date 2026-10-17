package app.kharcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kharcha.core.Money
import java.time.LocalDate


/** One thin bar per day of the month; tap a day to select it. */
@Composable
fun DailyChart(days: List<Pair<LocalDate, Long>>, selected: LocalDate?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(
        modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        days.forEach { (day, total) ->
            val isSelected = day == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(day) }
                    .semantics { contentDescription = "${day.dayOfMonth}: ${Money.format(total)}" },
                contentAlignment = Alignment.BottomCenter,
            ) {
                val fraction = if (total <= 0) 0.02f else (total.toFloat() / max).coerceAtLeast(0.04f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            when {
                                isSelected -> ChartStrong
                                total <= 0 -> Line
                                else -> ChartSoft
                            },
                        ),
                )
            }
        }
    }
}
