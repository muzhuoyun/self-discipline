package com.selfdiscipline.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.FileProvider
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

/** 首页「今日状态」卡片：展示文字 + 照片缩略图，点击进入编辑 */
@Composable
fun DailyLogCard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val logs by vm.dailyLogs.collectAsState()
    val todayLog = logs.firstOrNull { it.date == LocalDate.now().toString() }
    var showEditor by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEditor = true }
                .padding(14.dp),
        ) {
            Text(
                "📝 今日状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (todayLog == null) {
                Text(
                    "记录一段文字或一张照片，留住今天的状态…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (todayLog.text.isNotBlank()) {
                    Text(
                        todayLog.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                val paths = todayLog.photoPaths.split(",").filter { it.isNotBlank() }
                if (paths.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        paths.take(3).forEach { path ->
                            LocalPhoto(
                                path = path,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        DailyLogEditorDialog(vm = vm, date = LocalDate.now(), log = todayLog, onDismiss = { showEditor = false })
    }
}

/** 状态记录编辑对话框：文字 + 照片（相册/拍照），可删除记录 */
@Composable
private fun DailyLogEditorDialog(
    vm: MainViewModel,
    date: LocalDate,
    log: DailyLog?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(log?.text ?: "") }
    var removedPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var newUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val existingPaths = log?.photoPaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录今日状态") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("今天的心情、状态、遇到的事…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("相册")
                    }
                    Button(onClick = { launchCamera() }) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照")
                    }
                }
                if (visibleOldPaths.isNotEmpty() || newUris.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        visibleOldPaths.forEach { path ->
                            Box {
                                LocalPhoto(path = path, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                                IconButton(
                                    onClick = { removedPaths = removedPaths + path },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        RoundedCornerShape(10.dp),
                                    ),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
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
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                IconButton(
                                    onClick = { newUris = newUris - uri },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        RoundedCornerShape(10.dp),
                                    ),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
                if (log != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.saveDailyLog(
                        date = date,
                        text = text,
                        keepPaths = visibleOldPaths,
                        removePaths = removedPaths,
                        newPhotos = newUris,
                    )
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条状态记录？") },
            text = { Text("文字和照片都会被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteDailyLog(date)
                        onDismiss()
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
