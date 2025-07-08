package com.example.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Point
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class RectangleCrop (
    private val upperLeft: Point,
    private val bottomRight: Point
): BitmapTransformation() {
    companion object {
        private const val ID = "RectangleCrop"
        private val ID_BYTES = ID.toByteArray()
    }

    init {
        if (
            upperLeft.x < 0 || upperLeft.x >= bottomRight.x ||
            upperLeft.y < 0 || upperLeft.y >= bottomRight.y
        ) {
            throw IllegalArgumentException("invalid points")
        }
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (bottomRight.x > toTransform.width || bottomRight.y > toTransform.height) {
            throw IllegalArgumentException("invalid points")
        }

        if (upperLeft.equals(0, 0) && bottomRight.equals(toTransform.width, toTransform.height)) {
            return toTransform
        }

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
}