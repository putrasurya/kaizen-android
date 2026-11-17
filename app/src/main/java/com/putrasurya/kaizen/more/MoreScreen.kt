package com.putrasurya.kaizen.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.putrasurya.kaizen.core.navigation.MoreRoutes

/** The More tab: features that don't need their own tab, plus tools. */
@Composable
fun MoreScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        MoreItem("Notes", "Quick notes that come back on a weekday, or every day") { onOpen(MoreRoutes.NOTES) }
        MoreItem("Milestones", "Your record of the year: what you reached, and goals") { onOpen(MoreRoutes.MILESTONES) }
        HorizontalDivider()
        MoreItem("Reminder test", "Alarm, notification and voice check") {
            onOpen(MoreRoutes.REMINDER_TEST)
        }
    }
}

@Composable
private fun MoreItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
