package com.example.viewer

import android.content.Context
import com.example.viewer.database.BookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * for development usage and backup code
 */
class Dev {
    companion object {
        fun run (context: Context) {
        }
    }

//    private fun rotateGif () {
        // This method is too bad performance, and the rotated gif is not playable

        // handle gif file rotation
//        val originGif = GifDrawable(imageFile)
//        val metadata = GifAnimationMetaData(imageFile)
//        val frameDelay = (metadata.duration / metadata.numberOfFrames).toLong()

        // rotate and write to file
//        FileOutputStream(imageFile).use { fos ->
//            val encoder = GifEncoder(fos, metadata.height, metadata.width, 0)
//            val option = ImageOptions().apply { setDelay(frameDelay, TimeUnit.MILLISECONDS) }
//
//            for (i in 0 until metadata.numberOfFrames) {
//                println("$i / ${metadata.numberOfFrames}")
//                val rotatedFrame = Bitmap.createBitmap(
//                    originGif.seekToFrameAndGet(i),
//                    0, 0,
//                    metadata.width, metadata.height,
//                    matrix, true
//                )
//                val intArray = IntArray(metadata.width * metadata.height).also {
//                    rotatedFrame.getPixels(it, 0, metadata.height, 0, 0, metadata.height, metadata.width)
//                }
//                encoder.addImage(Image.fromRgb(intArray, metadata.height), option)
//                rotatedFrame.recycle()
//            }
//            encoder.finishEncoding()
//        }
//        originGif.recycle()
//    }
}