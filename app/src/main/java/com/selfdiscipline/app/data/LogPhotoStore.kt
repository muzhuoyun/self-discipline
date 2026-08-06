package com.selfdiscipline.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 状态照片的本地存储：复制 + 压缩到 App 私有目录，绝不上传。
 */
object LogPhotoStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "logs").apply { mkdirs() }

    /** 将所选照片压缩复制到本地（文件名带时间戳避免覆盖），返回文件路径列表 */
    fun savePhotos(context: Context, date: String, uris: List<Uri>): List<String> {
        val saved = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            runCatching {
                val target = File(dir(context), "${date}_${System.currentTimeMillis()}_$index.jpg")
                compressToFile(context, uri, target)
                saved.add(target.absolutePath)
            }
        }
        return saved
    }

    /** 压缩（宽 ≤1024、JPEG 80）并写入文件 */
    private fun compressToFile(context: Context, uri: Uri, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input, null, opts) ?: return
            FileOutputStream(target).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
    }

    /** 删除某天的全部照片文件 */
    fun deletePhotosFor(context: Context, date: String) {
        dir(context).listFiles()?.filter { it.name.startsWith("${date}_") }?.forEach { it.delete() }
    }

    /** 删除全部照片文件 */
    fun deleteAllPhotos(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
