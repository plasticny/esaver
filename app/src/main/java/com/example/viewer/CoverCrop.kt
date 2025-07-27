package com.example.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.util.Log
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

class CoverCrop (private val normalizedUpperLeft: PointF): BitmapTransformation() {
    companion object {
        private const val ID = "RectangleCrop"
        private val ID_BYTES = ID.toByteArray()
        private const val ASPECT = 1.4125
    }

    init {
        if (normalizedUpperLeft.x < 0 || normalizedUpperLeft.y < 0) {
            throw IllegalArgumentException("invalid points")
        }
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val upperLeftData = ByteBuffer.allocate(8)
            .putFloat(normalizedUpperLeft.x)
            .putFloat(normalizedUpperLeft.y)
            .array()
        messageDigest.update(ID_BYTES)
        messageDigest.update(upperLeftData)
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val upperLeft = Point(
            (normalizedUpperLeft.x * toTransform.width).toInt(),
            (normalizedUpperLeft.y * toTransform.height).toInt()
        )
        val bottomRight = estimateBottomRight(upperLeft, toTransform.width, toTransform.height)

        Log.i("${this::class.simpleName}.transform", "width:${toTransform.width}; height:${toTransform.height}; upperLeft:$upperLeft; bottomRight:$bottomRight")
        if (bottomRight.x > toTransform.width || bottomRight.y > toTransform.height) {
            throw IllegalArgumentException("invalid upper left point")
        }

        Log.i("${this::class.simpleName}.transform", "upperLeft:$upperLeft, bottomRight:$bottomRight")

        val result = pool.get(outWidth, outHeight, toTransform.config)
        Canvas(result).drawBitmap(
            toTransform,
            Matrix().apply {
                val scaleX = outWidth.toFloat() / (bottomRight.x - upperLeft.x)
                val scaleY = outHeight.toFloat() / (bottomRight.y - upperLeft.y)
                setScale(scaleX, scaleY)
                postTranslate(
                    -1 * upperLeft.x * scaleX,
                    -1 * upperLeft.y * scaleY
                )
            },
            null
        )

        return result
    }

    private fun estimateBottomRight (upperLeft: Point, width: Int, height: Int): Point =
        if (width * ASPECT > height) {
            Point(
                upperLeft.x + (height / ASPECT).toInt(),
                upperLeft.y + height
            )
        } else {
            Point (
                upperLeft.x + width,
                upperLeft.y + (width * ASPECT).toInt()
            )
        }
}