package com.selfdiscipline.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfdiscipline.app.ai.AiPrompts
import com.selfdiscipline.app.ai.AiStreamState
import com.selfdiscipline.app.ai.ChatTurn
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.Criterion
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.JieYinLevel
import com.selfdiscipline.app.data.Metrics
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 单项指标打分页：顶部随当前页更新，**左右滑动切换十个项目**，无需退出再进入。
 */
@Composable
fun CategoryDetailScreen(
    vm: MainViewModel,
    date: LocalDate,
    category: Category,
    onBack: () -> Unit,
) {
    val records by vm.records.collectAsState()
    val pagerState = rememberPagerState(initialPage = Category.entries.indexOf(category)) {
        Category.entries.size
    }
    val currentCategory = Category.entries[pagerState.currentPage]
    val record = records.firstOrNull { it.date == date.toString() }
        ?: DailyRecord(date = date.toString())
    val score = Metrics.score(currentCategory, record)
    val isToday = date == LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        // 顶部栏（随滑动更新）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${currentCategory.title} · ${date.monthValue}月${date.dayOfMonth}日",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${currentCategory.group.title} · ${pagerState.currentPage + 1}/${Category.entries.size} 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "$score / 10",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (score == 10) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        Text(
            "← 左右滑动切换项目 →",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            CategoryPage(vm = vm, date = date, category = Category.entries[page], isToday = isToday)
        }
    }
}

/** 单个项目的打分页内容 */
@Composable
private fun CategoryPage(
    vm: MainViewModel,
    date: LocalDate,
    category: Category,
    isToday: Boolean,
) {
    val records by vm.records.collectAsState()
    val autoCheckState by vm.autoCheck.collectAsState()
    val outcome by vm.autoCheckOutcome.collectAsState()
    val sessions by vm.autoCheckSessions.collectAsState()
    val record = records.firstOrNull { it.date == date.toString() }
        ?: DailyRecord(date = date.toString())
    val score = Metrics.score(category, record)
    // 该「日期 + 条目」的会话（退出再进仍在；不同条目互不串扰）
    val history = sessions[vm.sessionKey(date, category)] ?: emptyList()

    // 进入该页时从数据库恢复会话
    LaunchedEffect(date, category) {
        vm.loadAutoCheckSession(date, category)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LinearProgressIndicator(
            progress = { score / 10f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )

        // 归档提示：只能查看，不能修改
        if (!isToday) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    if (date.isBefore(LocalDate.now())) {
                        "🗂 已归档 · 该日期已过，仅可查看，无法修改或打卡"
                    } else {
                        "⏳ 还没到 · 再等 ${ChronoUnit.DAYS.between(LocalDate.now(), date)} 天再来打卡"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (category == Category.JIE_YIN) {
            Metrics.JIE_YIN_LEVELS.forEach { level ->
                JieYinOption(
                    level = level,
                    selected = record.jieYin == level.score,
                    enabled = isToday,
                ) {
                    vm.setJieYinLevel(date, level.score)
                }
            }
        } else {
            Metrics.criteria.getValue(category).forEachIndexed { index, criterion ->
                CriterionRow(
                    criterion = criterion,
                    checked = Metrics.isChecked(category, record, index),
                    enabled = isToday,
                ) { checked -> vm.toggle(date, category, index, checked) }
            }
        }

        // AI 辅助判断（仅当天可用）
        if (isToday) {
            AiAutoCheckCard(
                vm = vm,
                date = date,
                category = category,
                state = autoCheckState,
                outcome = outcome,
                history = history,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "这个分数只是仪表盘，不是道德审判。低分只说明明天该从哪一项补起。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** AI 辅助判断卡片：多轮对话，每轮 AI 自动勾选 + 给出理由 */
@Composable
private fun AiAutoCheckCard(
    vm: MainViewModel,
    date: LocalDate,
    category: Category,
    state: AiStreamState,
    outcome: com.selfdiscipline.app.ai.AutoCheckOutcome?,
    history: List<ChatTurn>,
) {
    var expanded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🤖", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "拿不准？让 AI 帮你判断",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "可以多轮对话，AI 每轮判断都会应用并记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 永久清除该条目当天的 AI 对话
                if (history.isNotEmpty() || state !is AiStreamState.Idle) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "清除该条目的 AI 对话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(if (expanded) "收起 ▲" else "展开 ▼", style = MaterialTheme.typography.labelSmall)
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))

                // 历史对话轮次
                history.forEachIndexed { index, turn ->
                    if (turn.role == ChatTurn.ROLE_USER) {
                        ChatBubble(isUser = true, text = turn.content)
                    } else {
                        val turnOutcome = AiPrompts.parseAutoCheck(turn.content)
                        if (turnOutcome != null) {
                            TurnResultCard(category = category, outcome = turnOutcome)
                        } else {
                            ChatBubble(isUser = false, text = "AI 返回了无法识别的结果")
                        }
                    }
                }

                // 当前轮状态
                when (state) {
                    is AiStreamState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("AI 正在分析…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is AiStreamState.Streaming -> {
                        // 流式只显示回复文本，隐藏尾部的 JSON 判断部分
                        ChatBubble(isUser = false, text = AiPrompts.streamDisplayText((state as AiStreamState.Streaming).text))
                    }
                    is AiStreamState.Error -> {
                        Text(
                            "判断失败：${state.message}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {}
                }

                // 输入区
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            if (category == Category.JIE_YIN) {
                                "例如：今天刷视频时差点点进去，但忍住了，没接触任何不良内容…"
                            } else {
                                "例如：三餐都按时吃了，晚饭七八分饱，下午喝了一杯奶茶没吃零食…"
                            }
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.runAutoCheck(date, category, input)
                        input = "" // 发送后清空输入框，方便继续对话
                    },
                    enabled = input.isNotBlank() &&
                        (state is AiStreamState.Idle || state is AiStreamState.Done ||
                            state is AiStreamState.Error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (history.isNotEmpty()) "继续对话" else "开始判断")
                }

                if (outcome != null && state is AiStreamState.Done &&
                    (outcome.level != null || outcome.items.isNotEmpty())
                ) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "✅ 本轮判断已自动应用到上面",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除 AI 对话？") },
            text = {
                Text(
                    "将永久删除当天（${date.monthValue}月${date.dayOfMonth}日）" +
                        "「${category.title}」条目的所有 AI 判断对话记录（已应用的勾选不会被撤销）。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        vm.clearAutoCheckSession(date, category)
                    },
                ) {
                    Text("永久清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

/** 用户/助手的气泡行 */
@Composable
private fun ChatBubble(isUser: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** 一轮的结果：AI 的回复 + 判断理由（有判断时） */
@Composable
private fun TurnResultCard(category: Category, outcome: com.selfdiscipline.app.ai.AutoCheckOutcome) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (outcome.reply.isNotBlank()) {
                Text(outcome.reply, style = MaterialTheme.typography.bodyMedium)
            }
            val hasJudgement = outcome.level != null || outcome.items.isNotEmpty()
            if (hasJudgement) {
                if (category == Category.JIE_YIN) {
                    outcome.level?.let { level ->
                        ReasonRow(level = level, reason = outcome.levelReason ?: "")
                    }
                } else {
                    val criteria = Metrics.criteria.getValue(category)
                    outcome.items.entries.sortedBy { it.key }.forEach { (index, checked) ->
                        if (index in criteria.indices) {
                            ReasonRow(
                                label = criteria[index].label,
                                checked = checked,
                                reason = outcome.reasons[index] ?: "",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一条判断理由 */
@Composable
private fun ReasonRow(label: String, checked: Boolean, reason: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (checked) "✅" else "❌",
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (reason.isNotBlank()) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 戒淫的等级判断理由 */
@Composable
private fun ReasonRow(level: Int, reason: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✅", fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "建议等级：$level 分",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (reason.isNotBlank()) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 戒淫：四个等级单选 */
@Composable
private fun JieYinOption(level: JieYinLevel, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(3.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                level.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${level.score} 分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 普通指标：勾选项 */
@Composable
private fun CriterionRow(
    criterion: Criterion,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Card(onClick = { if (enabled) onChecked(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { if (enabled) onChecked(it) },
            )
            Text(
                criterion.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                "+${criterion.points} 分",
                style = MaterialTheme.typography.labelLarge,
                color = if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
