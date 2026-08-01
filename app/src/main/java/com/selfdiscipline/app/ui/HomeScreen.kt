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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfdiscipline.app.ai.AiStreamState
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import com.selfdiscipline.app.logic.AchievementEngine
import com.selfdiscipline.app.logic.Summary
import java.time.LocalDate

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onCategoryClick: (Category) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val records by vm.records.collectAsState()
    val reviewState by vm.review.collectAsState()
    val today = LocalDate.now()
    val todayRecord = records.firstOrNull { it.date == today.toString() }
    val record = todayRecord ?: DailyRecord(date = today.toString())
    val yesterday = records.firstOrNull { it.date == today.minusDays(1).toString() }
    val total = Metrics.total(record)
    val ruleSummary = Summary.of(record, yesterday)
    val streak = AchievementEngine.currentStreak(records.sortedBy { it.date }) { true }
    val historyReview = vm.todayReview()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // 标题 + 状态 + 设置
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "三戒三修",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${today.monthValue}月${today.dayOfMonth}日 · ${today.weekdayCn}" +
                        if (streak > 0) " · 🔥连续 $streak 天" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(total)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 今日总分
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "今日总分",
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
                        " / 60",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        val status = statusLevel(total)
                        Text(
                            status.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = status.color,
                        )
                        yesterday?.let {
                            val diff = total - Metrics.total(it)
                            if (diff != 0) {
                                Text(
                                    if (diff > 0) "较昨日 +$diff ↑" else "较昨日 $diff ↓",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (diff > 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { total / 60f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusLevel(total).color,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                )
            }
        }

        // AI 打卡短评区
        when {
            historyReview != null && reviewState is AiStreamState.Idle -> {
                AiReviewCard(
                    title = "今日教练点评",
                    text = historyReview.response,
                    footer = "记录于当天 · 每天只保留最新一份评价",
                    onRegenerate = { vm.checkIn() },
                )
            }
            reviewState is AiStreamState.Idle -> {
                // 未打卡：显示规则总结 + 打卡按钮
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            ruleSummary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.checkIn() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("💪 打卡，听听 AI 教练的点评")
                        }
                        Text(
                            "打卡后 AI 会结合今天的数据给你一段短评。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            reviewState is AiStreamState.Loading -> {
                AiReviewCard(title = "AI 教练点评中…", loading = true)
            }
            reviewState is AiStreamState.Streaming -> {
                AiReviewCard(title = "AI 教练点评", text = (reviewState as AiStreamState.Streaming).text)
            }
            reviewState is AiStreamState.Done -> {
                AiReviewCard(
                    title = "今日教练点评",
                    text = (reviewState as AiStreamState.Done).text,
                    footer = "💾 已自动存档",
                    onRegenerate = { vm.checkIn() },
                )
            }
            reviewState is AiStreamState.Error -> {
                val msg = (reviewState as AiStreamState.Error).message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "点评失败：$msg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.checkIn() }) {
                            Text("重试")
                        }
                    }
                }
            }
        }

        if (todayRecord == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text(
                    "今天还没有打分，点下方任一项目，2 分钟搞定。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        // 三戒
        SectionHeader("三戒", Metrics.groupTotal(record, jie = true), 30)
        Category.entries.filter { it.isJie }.forEach { category ->
            CategoryRow(category = category, record = record, onClick = { onCategoryClick(category) })
        }

        // 三修
        SectionHeader("三修", Metrics.groupTotal(record, jie = false), 30)
        Category.entries.filter { !it.isJie }.forEach { category ->
            CategoryRow(category = category, record = record, onClick = { onCategoryClick(category) })
        }

        Text(
            "评分是仪表盘，不是审判 —— 明天比今天多 1 分，就很好。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** AI 输出卡片：流式文本 / 历史短评，支持重新评价 */
@Composable
private fun AiReviewCard(
    title: String,
    text: String? = null,
    footer: String? = null,
    loading: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (onRegenerate != null) {
                    TextButton(onClick = onRegenerate) {
                        Text("🔄 重新评价", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "正在结合今天的数据写点评…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            } else {
                Text(
                    text.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            footer?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, score: Int, max: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$score / $max",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
