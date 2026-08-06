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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.selfdiscipline.app.ai.AiStreamState
import com.selfdiscipline.app.data.DailyLog
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import com.selfdiscipline.app.logic.Summary
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
 * 今日打卡大卡片 = 教练点评 + 今日状态（内联文本框与拍照框，常驻显示）。
 */
@Composable
fun TodayCheckInCard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val records by vm.records.collectAsState()
    val reviewState by vm.review.collectAsState()
    val logs by vm.dailyLogs.collectAsState()
    val today = LocalDate.now()
    val todayRecord = records.firstOrNull { it.date == today.toString() }
    val record = todayRecord ?: DailyRecord(date = today.toString())
    val ruleSummary = Summary.of(record, null)
    val historyReview = vm.todayReview()
    val todayLog = logs.firstOrNull { it.date == today.toString() }

    // ---- 状态编辑状态 ----
    val context = LocalContext.current
    var text by remember(todayLog?.text) { mutableStateOf(todayLog?.text ?: "") }
    var removedPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var newUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }

    val existingPaths = todayLog?.photoPaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val visibleOldPaths = existingPaths.filterNot { it in removedPaths }

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
    fun saveLog() {
        vm.saveDailyLog(
            date = today,
            text = text,
            keepPaths = visibleOldPaths,
            removePaths = removedPaths,
            newPhotos = newUris,
        )
        removedPaths = emptyList()
        newUris = emptyList()
        savedFlash = true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            // ============ 教练点评 ============
            when {
                historyReview != null && reviewState is AiStreamState.Idle -> {
                    ReviewHeader("今日教练点评", onRegenerate = { vm.checkIn() })
                    ReviewText(historyReview.response)
                }
                reviewState is AiStreamState.Idle -> {
                    Text(
                        ruleSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.checkIn() }, modifier = Modifier.fillMaxWidth()) {
                        Text("💪 打卡，听听 AI 教练的点评")
                    }
                }
                reviewState is AiStreamState.Loading -> {
                    ReviewHeader("AI 教练点评中…")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "正在结合今天的数据写点评…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                reviewState is AiStreamState.Streaming -> {
                    ReviewHeader("AI 教练点评")
                    ReviewText((reviewState as AiStreamState.Streaming).text)
                }
                reviewState is AiStreamState.Done -> {
                    ReviewHeader("今日教练点评", onRegenerate = { vm.checkIn() })
                    ReviewText((reviewState as AiStreamState.Done).text)
                }
                reviewState is AiStreamState.Error -> {
                    Text(
                        "点评失败：${(reviewState as AiStreamState.Error).message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { vm.checkIn() }) { Text("重试") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            // ============ 今日状态（内联编辑） ============
            Text(
                "📝 今日状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("今天的心情、状态、遇到的事…（可选）") },
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
                Spacer(Modifier.weight(1f))
                if (todayLog != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (visibleOldPaths.isNotEmpty() || newUris.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleOldPaths.forEach { path ->
                        PhotoThumb(path = path, onRemove = { removedPaths = removedPaths + path })
                    }
                    newUris.forEach { uri ->
                        NewPhotoThumb(context = context, uri = uri, onRemove = { newUris = newUris - uri })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { saveLog() },
                enabled = text.isNotBlank() || newUris.isNotEmpty() || removedPaths.isNotEmpty() || todayLog != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (savedFlash) "已保存 ✓" else "保存今日状态")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条状态记录？") },
            text = { Text("文字和照片都会被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteDailyLog(today)
                        text = ""
                        removedPaths = emptyList()
                        newUris = emptyList()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ReviewHeader(title: String, onRegenerate: (() -> Unit)? = null) {
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
}

@Composable
private fun ReviewText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/** 已有照片缩略图（可移除） */
@Composable
private fun PhotoThumb(path: String, onRemove: () -> Unit) {
    Box {
        LocalPhoto(path = path, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
        RemoveBadge(modifier = Modifier.align(Alignment.TopEnd), onRemove = onRemove)
    }
}

/** 新选照片缩略图（可移除） */
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
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        RemoveBadge(modifier = Modifier.align(Alignment.TopEnd), onRemove = onRemove)
    }
}

@Composable
private fun RemoveBadge(modifier: Modifier = Modifier, onRemove: () -> Unit) {
    IconButton(
        onClick = onRemove,
        modifier = modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "移除",
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(12.dp),
        )
    }
}
