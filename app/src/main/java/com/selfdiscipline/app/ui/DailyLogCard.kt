package com.selfdiscipline.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.selfdiscipline.app.ai.AiStreamState
import com.selfdiscipline.app.ai.ChatTurn
import com.selfdiscipline.app.data.DailyLog
import java.io.File
import java.time.LocalDate

/** 本地照片加载（无图片库依赖，直接解码文件） */
@Composable
fun LocalPhoto(path: String, modifier: Modifier = Modifier, maxWidth: Int = 400) {
    val bitmap = remember(path) {
        runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > maxWidth) sample *= 2
            BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/**
 * 今日状态卡：同一天可多条记录；文本框与拍照框内联常驻，直接添加。
 */
@Composable
fun DailyLogCard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val logs by vm.dailyLogs.collectAsState()
    val today = LocalDate.now()
    val todayLogs = logs.filter { it.date == today.toString() }.sortedBy { it.createdAt }

    // 输入状态
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var newUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf<DailyLog?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> newUris = newUris + uris }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success -> if (success) cameraUri?.let { newUris = newUris + it } }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "📝 今日状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            // 今日已有记录（多条）
            todayLogs.forEach { log ->
                LogItem(log = log, onDelete = { confirmDelete = log })
                Spacer(Modifier.height(8.dp))
            }

            // 输入区（内联常驻）
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("记录一条：今天的心情、状态、遇到的事…（可选）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("相册")
                }
                OutlinedButton(onClick = { launchCamera() }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("拍照")
                }
            }
            if (newUris.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    newUris.forEach { uri ->
                        NewPhotoThumb(context = context, uri = uri, onRemove = { newUris = newUris - uri })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    vm.addDailyLog(date = today, text = text, photos = newUris)
                    text = ""
                    newUris = emptyList()
                },
                enabled = text.isNotBlank() || newUris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加记录")
            }
        }
    }

    confirmDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这条状态记录？") },
            text = { Text("这条记录的文字和照片都会被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteDailyLog(log)
                        confirmDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 一条状态记录：文字 + 照片行 + 右上角小删除按钮 */
@Composable
private fun LogItem(log: DailyLog, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Box {
            Column(Modifier.padding(10.dp)) {
                if (log.text.isNotBlank()) {
                    Text(log.text, style = MaterialTheme.typography.bodyMedium)
                }
                val paths = log.photoPaths.split(",").filter { it.isNotBlank() }
                if (paths.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        paths.forEach { path ->
                            LocalPhoto(
                                path = path,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        }
                    }
                }
            }
            // 小删除按钮（右上角）
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除记录",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 新选照片缩略图（可移除，小 ✕） */
@Composable
private fun NewPhotoThumb(context: android.content.Context, uri: Uri, onRemove: () -> Unit) {
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    Box {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** AI 健康顾问卡：以医生视角审视今日状态，多轮询问身体状况 */
@Composable
fun DoctorCard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val aiChats by vm.aiChats.collectAsState()
    val doctorState by vm.doctor.collectAsState()
    val history = remember(aiChats) { vm.doctorHistory() }
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "🏥 AI 健康顾问",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "以医生视角审视你的今日状态，询问身体细节，给出日常建议（不替代就医）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))

            // 历史会话
            history.forEachIndexed { index, turn ->
                if (turn.role == ChatTurn.ROLE_USER) {
                    DoctorBubble(isUser = true, text = turn.content)
                } else {
                    DoctorBubble(isUser = false, text = turn.content)
                }
            }

            // 当前轮状态
            when (doctorState) {
                is AiStreamState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("医生正在查看你的状态…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is AiStreamState.Streaming -> {
                    DoctorBubble(isUser = false, text = (doctorState as AiStreamState.Streaming).text)
                }
                is AiStreamState.Error -> {
                    Text(
                        "对话失败：${(doctorState as AiStreamState.Error).message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {}
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("聊聊今天的身体感受…（可选）") },
                minLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.runDoctor(input)
                    input = ""
                },
                enabled = input.isNotBlank() && doctorState is AiStreamState.Idle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("让 AI 看看我的状态")
            }
        }
    }
}

@Composable
private fun DoctorBubble(isUser: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.secondaryContainer
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
