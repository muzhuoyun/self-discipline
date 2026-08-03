package com.selfdiscipline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import java.time.LocalDate

val LocalDate.weekdayCn: String
    get() = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[dayOfWeek.value - 1]

/** 日历色：🟢 85~100 · 🟡 70~84 · 🟠 55~69 · 🔴 <55 */
fun calendarColor(total: Int): Color = when {
    total >= 85 -> Color(0xFF43A047)
    total >= 70 -> Color(0xFFF9A825)
    total >= 55 -> Color(0xFFFB8C00)
    else -> Color(0xFFE53935)
}

/** 三档状态：🔴 0~49 调整日 · 🟡 50~74 合格日 · 🟢 75~100 精进日 */
enum class StatusLevel(val label: String, val color: Color) {
    GREEN("精进日", Color(0xFF2E7D32)),
    YELLOW("合格日", Color(0xFFF9A825)),
    RED("调整日", Color(0xFFE53935)),
}

fun statusLevel(total: Int): StatusLevel = when {
    total >= 75 -> StatusLevel.GREEN
    total >= 50 -> StatusLevel.YELLOW
    else -> StatusLevel.RED
}

/** 状态徽章：精进日 / 合格日 / 调整日 */
@Composable
fun StatusChip(total: Int, modifier: Modifier = Modifier) {
    val s = statusLevel(total)
    Surface(
        modifier = modifier,
        color = s.color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(s.color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                s.label,
                color = s.color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 十个星标，实心数 = 得分，一眼看出差距 */
@Composable
fun StarRow(score: Int, modifier: Modifier = Modifier, fontSize: TextUnit = 13.sp) {
    val filled = Color(0xFFFFB300)
    val empty = MaterialTheme.colorScheme.outlineVariant
    val s = score.coerceIn(0, 10)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = filled)) { append("★".repeat(s)) }
            withStyle(SpanStyle(color = empty)) { append("☆".repeat(10 - s)) }
        },
        fontSize = fontSize,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

/** 首页的六类指标行 */
@Composable
fun CategoryRow(
    category: Category,
    record: DailyRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val score = Metrics.score(category, record)
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                StarRow(score)
            }
            Text(
                "$score / 10",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (score == 10) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
