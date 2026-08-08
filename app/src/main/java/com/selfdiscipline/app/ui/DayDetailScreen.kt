package com.selfdiscipline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfdiscipline.app.data.AiKinds
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 某一天的详情页：总分、AI 评价、十项一览；今天可进入编辑，其他日期只读 */
@Composable
fun DayDetailScreen(
    vm: MainViewModel,
    date: LocalDate,
    onBack: () -> Unit,
    onEditCategory: (LocalDate, Category) -> Unit,
) {
    val records by vm.records.collectAsState()
    val aiChats by vm.aiChats.collectAsState()
    val record = records.firstOrNull { it.date == date.toString() }
        ?: DailyRecord(date = date.toString())
    val total = Metrics.total(record)
    val today = LocalDate.now()
    val isToday = date == today
    val review = aiChats.lastOrNull {
        it.kind == AiKinds.REVIEW && it.date == date.toString()
    }
    // 打卡 = 该日期有 AI 评价；无评价显示「未打卡」
    val status = if (review != null) statusLevel(total) else null

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${date.monthValue}月${date.dayOfMonth}日 · ${date.weekdayCn}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        isToday -> "今天"
                        date.isBefore(today) -> "🗂 已归档 · 只能查看"
                        else -> "⏳ 还没到 · 再等 ${ChronoUnit.DAYS.between(today, date)} 天"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 打卡 = 该日期存在 AI 评价（与是否打分无关）
            StatusChip(total = total, hasRecord = review != null)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 总分卡
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "当日总分",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$total",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            " / 100",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            status?.label ?: "未打卡",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = status?.color ?: MaterialTheme.colorScheme.outline,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { total / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = status?.color ?: MaterialTheme.colorScheme.outline,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                }
            }

            // AI 教练评价
            if (review != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "🤖 教练点评",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            review.response,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else if (isToday) {
                Text(
                    "今天还没打卡，点下方项目打分，然后首页打卡获取 AI 点评。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 当日状态（文字 + 照片，可多条）
            val dayLogs = vm.dailyLogs.value
                .filter { it.date == date.toString() }
                .sortedBy { it.createdAt }
            if (dayLogs.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "📝 当日状态",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        dayLogs.forEach { log ->
                            if (log.text.isNotBlank() || log.photoPaths.isNotBlank() || log.doctorReply.isNotBlank()) {
                                if (log.text.isNotBlank()) {
                                    Text(
                                        log.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                val paths = log.photoPaths.split(",").filter { it.isNotBlank() }
                                if (paths.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        paths.forEach { path ->
                                            LocalPhoto(
                                                path = path,
                                                modifier = Modifier
                                                    .size(88.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                            )
                                        }
                                    }
                                }
                                // AI 医生回复
                                if (log.doctorReply.isNotBlank()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            "🤖 医生：${log.doctorReply}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 十项一览
            Category.entries.forEach { category ->
                CategoryRow(
                    category = category,
                    record = record,
                    onClick = { onEditCategory(date, category) },
                )
            }

            if (!isToday) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (date.isBefore(today)) "🗂 已归档：该日期只能查看，无法修改或补录。"
                        else "⏳ 该日期还未到来，无法提前打卡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
