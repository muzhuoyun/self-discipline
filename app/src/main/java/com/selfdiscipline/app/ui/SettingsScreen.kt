package com.selfdiscipline.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selfdiscipline.app.AppGraph
import com.selfdiscipline.app.ai.AiSettingsStore
import com.selfdiscipline.app.reminder.ReminderScheduler

/** 设置页：大模型配置 + 打卡提醒 + 数据管理 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel = viewModel(), onBack: () -> Unit) {
    val aiChats by vm.aiChats.collectAsState()
    var baseUrl by remember { mutableStateOf(AppGraph.aiSettings.baseUrl()) }
    var apiKey by remember { mutableStateOf(AppGraph.aiSettings.apiKey()) }
    var model by remember { mutableStateOf(AppGraph.aiSettings.model()) }
    var saved by remember { mutableStateOf(false) }

    // 打卡提醒
    val context = LocalContext.current
    var reminderEnabled by remember { mutableStateOf(AppGraph.aiSettings.reminderEnabled()) }
    var reminderHour by remember { mutableStateOf(AppGraph.aiSettings.reminderHour()) }
    var reminderMinute by remember { mutableStateOf(AppGraph.aiSettings.reminderMinute()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    fun applyReminder(enabled: Boolean, hour: Int, minute: Int) {
        AppGraph.aiSettings.saveReminder(enabled, hour, minute)
        reminderEnabled = enabled
        reminderHour = hour
        reminderMinute = minute
        if (enabled) ReminderScheduler.schedule(context, hour, minute)
        else ReminderScheduler.cancel(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) applyReminder(true, reminderHour, reminderMinute)
        else permissionDenied = true
    }

    fun toggleReminder(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            applyReminder(enabled, reminderHour, reminderMinute)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "AI 设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; saved = false },
                        label = { Text("接口基地址") },
                        placeholder = { Text("https://api.deepseek.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; saved = false },
                        label = { Text("API Key（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; saved = false },
                        label = { Text("模型名") },
                        placeholder = { Text(AiSettingsStore.DEFAULT_MODEL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            vm.saveAiSettings(baseUrl, apiKey, model)
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (saved) "已保存 ✓" else "保存")
                    }
                    Text(
                        "兼容 OpenAI 格式的服务（/chat/completions，SSE 流式）。" +
                            "本地服务（模拟器）请用 http://10.0.2.2:端口/v1 访问宿主机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- 打卡提醒 ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "⏰ 打卡提醒",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            Text(
                                "每天定时提醒你该打卡了",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { toggleReminder(it) },
                        )
                    }
                    if (reminderEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("提醒时间", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "%02d:%02d".format(reminderHour, reminderMinute),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "修改时间后立即生效；设备重启后提醒会自动恢复。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------- 数据管理 ----------
            DataManagementCard(
                aiChatsCount = aiChats.size,
                onClearAutoCheck = { vm.clearAllAutoCheck() },
                onClearReports = { vm.clearAllReports() },
                onClearAll = { vm.clearAllData() },
            )

            Text(
                "提示：AI 仅用于提供情绪价值和辅助判断，最终决定权永远在你手里。" +
                    "AI 生成的成就都基于真实记录数据计算，不会凭空出现。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            onConfirm = { h, m ->
                showTimePicker = false
                applyReminder(true, h, m)
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("需要通知权限") },
            text = {
                Text("开启通知权限后才能收到打卡提醒。可在系统设置中授予「三戒三修」通知权限后重试。")
            },
            confirmButton = {
                TextButton(onClick = { permissionDenied = false }) { Text("知道了") }
            },
        )
    }
}

/** 时间选择弹窗（24 小时制） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置提醒时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 数据管理：三类删除，每次删除前都要确认 */
@Composable
private fun DataManagementCard(
    aiChatsCount: Int,
    onClearAutoCheck: () -> Unit,
    onClearReports: () -> Unit,
    onClearAll: () -> Unit,
) {
    var confirm by remember { mutableStateOf<Int?>(null) } // 1=判断 2=报告 3=全部

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "数据管理（删除前都会再次确认）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text(
                "AI 交互记录共 ${aiChatsCount} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DataDeleteRow("全部 AI 判断记录", "详情页 AI 辅助判断的多轮对话记录", onClearAutoCheck) {
                confirm = 1
            }
            DataDeleteRow("全部周报 / 月报", "历史页生成的 AI 报告存档", onClearReports) {
                confirm = 2
            }
            DataDeleteRow(
                "清空所有数据",
                "打卡记录 + AI 交互 + AI 成就，恢复出厂状态",
                onClearAll,
            ) {
                confirm = 3
            }
        }
    }

    val (title, text) = when (confirm) {
        1 -> "删除全部 AI 判断记录？" to "将永久删除所有条目的 AI 判断对话记录，不可恢复。已应用的勾选不会被撤销。"
        2 -> "删除全部周报 / 月报？" to "将永久删除所有已生成的周报和月报，不可恢复。"
        3 -> "清空所有数据？" to "将永久删除全部打卡记录、AI 交互记录和 AI 成就，应用将回到初始状态，不可恢复！"
        else -> null to null
    }
    if (title != null && confirm != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(text ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (confirm) {
                            1 -> onClearAutoCheck()
                            2 -> onClearReports()
                            3 -> onClearAll()
                        }
                        confirm = null
                    },
                ) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DataDeleteRow(title: String, desc: String, action: () -> Unit, requestConfirm: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = requestConfirm) {
            Text("删除", color = MaterialTheme.colorScheme.error)
        }
    }
}
