package com.putrasurya.kaizen.timers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seven day buttons, Monday first. Used to pick the day to show (one selected)
 * and to pick days for a new timer (several selected). Today is underlined.
 */
@Composable
fun WeekdayRow(
    selected: Set<Int>,
    today: Int,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            val number = day.value
            val isSelected = number in selected
            val colors = MaterialTheme.colorScheme
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.primary else colors.surfaceVariant)
                    .clickable { onClick(number) }
                    .padding(horizontal = 2.dp),
            ) {
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    fontWeight = if (number == today) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (number == today) TextDecoration.Underline else null,
                    maxLines = 1,
                )
            }
        }
    }
}
