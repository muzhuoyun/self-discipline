package com.selfdiscipline.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfdiscipline.app.data.AiChatLog
import com.selfdiscipline.app.data.AiKinds

/** 报告历史独立页面：周报 / 月报两栏，每条可点击展开，可永久删除 */
@Composable
fun ReportHistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val aiChats by vm.aiChats.collectAsState()
    val reports = remember(aiChats) { vm.reportHistory() }
    val weekly = reports.filter { it.kind == AiKinds.WEEKLY }
    val monthly = reports.filter { it.kind == AiKinds.MONTHLY }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<AiChatLog?>(null) }

    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "报告历史",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("📅 周报（${weekly.size}）") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("🗓 月报（${monthly.size}）") },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tab == 0) {
                ReportSection(
                    reports = weekly,
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == it) null else it },
                    onDelete = { confirmDelete = it },
                )
            } else {
                ReportSection(
                    reports = monthly,
                    expandedId = expandedId,
                    onToggle = { expandedId = if (expandedId == it) null else it },
                    onDelete = { confirmDelete = it },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    confirmDelete?.let { report ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这条报告？") },
            text = {
                Text(
                    "将永久删除：${reportListItemLabel(report)}\n\n删除后不可恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteReport(report)
                        confirmDelete = null
                        if (expandedId == report.id) expandedId = null
                    },
                ) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 一栏报告列表 */
@Composable
private fun ReportSection(
    reports: List<AiChatLog>,
    expandedId: Long?,
    onToggle: (Long) -> Unit,
    onDelete: (AiChatLog) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reports.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    "还没有生成过",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            reports.forEach { report ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(report.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                reportListItemLabel(report),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (expandedId == report.id) "▲" else "▼",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            IconButton(onClick = { onDelete(report) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (expandedId == report.id) {
                            Text(
                                report.response,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 列表项格式：「8月1日 08:30 记录的 周报（7月20日~26日）」 */
internal fun reportListItemLabel(report: AiChatLog): String {
    val time = java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(report.createdAt))
    val kind = if (report.kind == AiKinds.WEEKLY) "周报" else "月报"
    val range = Regex("报告范围：([\\d-]+ ~ [\\d-]+)").find(report.prompt)
        ?.groupValues?.get(1)
        ?.let { raw ->
            runCatching {
                val parts = raw.split(" ~ ")
                val start = java.time.LocalDate.parse(parts[0])
                val end = java.time.LocalDate.parse(parts[1])
                "（${start.monthValue}月${start.dayOfMonth}日~${end.monthValue}月${end.dayOfMonth}日）"
            }.getOrNull()
        } ?: ""
    return "$time 记录的 $kind$range"
}
