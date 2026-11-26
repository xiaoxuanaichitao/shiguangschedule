package com.xingheyuzhuan.shiguangschedule.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android 平台开启裁切预览 UI 弹窗
 */
actual val isCropWindowEnabled: Boolean = true

/**
 * Android 平台实现：利用系统底层驱动进行物理裁剪并直接压缩为 ByteArray 字节流
 */
actual suspend fun cropImageBitmapNative(
    source: ImageBitmap,
    srcLeft: Int,
    srcTop: Int,
    cropWidth: Int,
    cropHeight: Int
): ByteArray = withContext(Dispatchers.Default) {
    val androidBitmap = source.asAndroidBitmap()

    // 边界安全处理，防止坐标出界引发崩溃
    val safeLeft = srcLeft.coerceIn(0, androidBitmap.width - 1)
    val safeTop = srcTop.coerceIn(0, androidBitmap.height - 1)
    val safeWidth = cropWidth.coerceAtLeast(1).coerceAtMost(androidBitmap.width - safeLeft)
    val safeHeight = cropHeight.coerceAtLeast(1).coerceAtMost(androidBitmap.height - safeTop)

    // 1. 裁剪 Bitmap
    val croppedBitmap = Bitmap.createBitmap(
        androidBitmap,
        safeLeft,
        safeTop,
        safeWidth,
        safeHeight
    )

    // 2. 将裁剪后的 Bitmap 压缩为 JPEG 字节数组直接输出给 Common/ViewModel
    ByteArrayOutputStream().use { stream ->
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.toByteArray()
    }
}

actual suspend fun encodeImageBitmapForAi(
    source: ImageBitmap,
    maxDimension: Int,
    quality: Int
): ByteArray = withContext(Dispatchers.Default) {
    val androidBitmap = source.asAndroidBitmap()
    val w = androidBitmap.width.coerceAtLeast(1)
    val h = androidBitmap.height.coerceAtLeast(1)
    val maxSide = maxOf(w, h)
    val targetBitmap = if (maxSide > maxDimension && maxDimension > 0) {
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(androidBitmap, targetW, targetH, true)
    } else {
        androidBitmap
    }
    try {
        ByteArrayOutputStream().use { stream ->
            targetBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 95), stream)
            stream.toByteArray()
        }
    } finally {
        if (targetBitmap !== androidBitmap) {
            targetBitmap.recycle()
        }
    }
}