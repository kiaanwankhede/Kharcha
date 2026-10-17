package app.kharcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kharcha.core.Money
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val BarSelected = Color(0xFF2A78D6)
private val BarOther = Color(0xFF86B6EF)

/** Spend per month as bars; tap a bar to look at that month. Only the selected bar carries its value. */
@Composable
fun TrendChart(totals: List<Pair<YearMonth, Long>>, selected: YearMonth, onSelect: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val max = (totals.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(
        modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        totals.forEach { (month, total) ->
            val isSelected = month == selected
            val monthName = month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(month) }
                    .semantics { contentDescription = "$monthName ${Money.format(total)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (isSelected) compactMoney(total) else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fraction = if (total <= 0) 0f else (total.toFloat() / max).coerceAtLeast(0.03f)
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (isSelected) BarSelected else BarOther),
                    )
                }
                Text(
                    monthName,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Navy else Faint,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** ₹950, ₹42k, ₹1.2L — short enough to sit above a bar. */
fun compactMoney(paise: Long): String {
    val rupees = paise / 100
    return when {
        rupees >= 1_00_000 -> "₹%.1fL".format(rupees / 1_00_000.0).replace(".0L", "L")
        rupees >= 1_000 -> "₹${(rupees + 500) / 1_000}k"
        else -> "₹$rupees"
    }
}
