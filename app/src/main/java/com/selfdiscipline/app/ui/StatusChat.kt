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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.selfdiscipline.app.ai.AiStreamState
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
 * 今日状态 · 聊天式界面：消息列表滚动，输入框固定在底部。
 * 发送一条状态 → AI 以医生身份回复 → 形成对话流。
 */
@Composable
fun StatusChat(vm: MainViewModel, modifier: Modifier = Modifier) {
    val logs by vm.dailyLogs.collectAsState()
    val doctorReplyState by vm.doctorReply.collectAsState()
    val replyingLogId by vm.doctorReplyLogId.collectAsState()
    val today = LocalDate.now()
    val todayLogs = logs.filter { it.date == today.toString() }.sortedBy { it.createdAt }
    val listState = rememberLazyListState()

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
    fun send() {
        if (text.isBlank() && newUris.isEmpty()) return
        vm.addDailyLog(date = today, text = text, photos = newUris)
        text = ""
        newUris = emptyList()
    }

    // 消息变化时自动滚动到底部
    LaunchedEffect(todayLogs.size, replyingLogId, doctorReplyState) {
        val count = todayLogs.size
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    Column(modifier.fillMaxSize()) {
        // 消息列表（滚动区）
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(todayLogs.size) { index ->
                val log = todayLogs[index]
                // 用户消息（状态记录）
                UserBubble(log = log, onDelete = { confirmDelete = log })

                // AI 医生回复
                when {
                    replyingLogId == log.id && doctorReplyState is AiStreamState.Loading -> {
                        AiBubble(text = "医生正在看这条状态…", loading = true)
                    }
                    replyingLogId == log.id && doctorReplyState is AiStreamState.Streaming -> {
                        AiBubble(text = (doctorReplyState as AiStreamState.Streaming).text)
                    }
                    replyingLogId == log.id && doctorReplyState is AiStreamState.Error -> {
                        AiBubble(
                            text = "回复失败：${(doctorReplyState as AiStreamState.Error).message}",
                            isError = true,
                        )
                    }
                    log.doctorReply.isNotBlank() -> {
                        AiBubble(text = log.doctorReply)
                    }
                }
            }
        }

        // 底部固定输入区
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (newUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        newUris.forEach { uri ->
                            val bitmap = remember(uri) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                }.getOrNull()
                            }
                            Box {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                }
                                IconButton(
                                    onClick = { newUris = newUris - uri },
                                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "移除",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("写下今天的感受…（可选照片）") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                    )
                    IconButton(onClick = { pickLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "相册")
                    }
                    IconButton(onClick = { launchCamera() }) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "拍照")
                    }
                    IconButton(
                        onClick = { send() },
                        enabled = text.isNotBlank() || newUris.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "发送",
                            tint = if (text.isNotBlank() || newUris.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这条状态？") },
            text = { Text("这条记录的文字、照片和医生回复都会被永久删除。") },
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

/** 用户消息气泡（状态记录）：右对齐，含照片与小删除按钮 */
@Composable
private fun UserBubble(log: DailyLog, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
        ) {
            Box {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (log.text.isNotBlank()) {
                        Text(log.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    val paths = log.photoPaths.split(",").filter { it.isNotBlank() }
                    if (paths.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            paths.forEach { path ->
                                LocalPhoto(
                                    path = path,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** AI 消息气泡（医生回复）：左对齐 */
@Composable
private fun AiBubble(text: String, loading: Boolean = false, isError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        ) {
            if (loading) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
