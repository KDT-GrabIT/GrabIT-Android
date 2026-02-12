package com.example.grabitTest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import kotlin.math.min
import java.io.ByteArrayOutputStream

object BitmapUtils {

    /** 회전 각도(degree)만큼 Bitmap 회전. 0이면 원본 반환. */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap? {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * YUV_420_888 → Bitmap 변환. 원본 해상도 그대로 반환(비율 유지).
     * 640x640 등 정사각형 변환은 createLetterboxBitmap으로 별도 적용.
     */
    fun yuv420ToBitmap(image: Image): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * 비율 유지(Letterboxing)로 targetSize x targetSize 정사각형 생성.
     * ScaleToFit: 더 긴 변이 targetSize에 맞도록 스케일 후, 짧은 쪽은 검은/회색 패딩.
     * 이미지가 찌그러지지 않도록 함.
     * @return (letterbox 비트맵, scale r = scaledDimension / originalDimension)
     */
    fun createLetterboxBitmap(source: Bitmap, targetSize: Int): Pair<Bitmap, Float> {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) {
            val empty = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            return empty to 1f
        }
        val r = min(
            targetSize.toFloat() / w,
            targetSize.toFloat() / h
        )
        val newW = (w * r).toInt().coerceAtLeast(1)
        val newH = (h * r).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, newW, newH, true)
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled != source) scaled.recycle()
        return out to r
    }
}
