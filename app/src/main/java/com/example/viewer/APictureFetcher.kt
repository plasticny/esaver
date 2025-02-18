package com.example.viewer

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.example.viewer.HiPictureFetcher.Companion.okHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

abstract class APictureFetcher (
    protected val context: Context, protected val bookId: String
): CoroutineScope by MainScope() {
    companion object {
        fun getFetcher (context: Context, bookId: String): APictureFetcher {
            val source = History.getBookSource(bookId)
            println("[APictureFetcher.getFetcher] $source")
            return when (source) {
                BookSource.E -> EPictureFetcher(context, bookId)
                BookSource.Hi -> HiPictureFetcher(context, bookId)
            }
        }
    }

    protected val pageNum: Int = History.getBookPageNum(bookId)
    private val fileGlide = Glide.with(context)
        .setDefaultRequestOptions(RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
        )
        .asBitmap()
    private val downloadedPage = mutableSetOf<Int>()

    val bookFolder = File(context.getExternalFilesDir(null), bookId)

    suspend fun getPicture (page: Int, loadListener: RequestListener<Bitmap>? = null): RequestBuilder<Bitmap>? {
        assertPageInRange(page)

        val pictureFile = File(bookFolder, page.toString())
        println("[APictureFetcher.getPicture]\n${pictureFile.path}")

        if (!pictureFile.exists()) {
            if (downloadedPage.contains(page)) {
                return null
            }
            downloadedPage.add(page)

            val retFlag = savePicture(page)
            if (!retFlag) {
                return null
            }
        }
        return fileGlide.listener(loadListener).load(pictureFile.path)
    }

    protected fun assertPageInRange (page: Int) {
        if (page < 0 || page >= pageNum) {
            throw Exception("page out of range")
        }
    }

    protected suspend fun downloadPicture (page: Int, url: String, headers: Map<String, String> = mapOf()): Boolean {
        println("[APictureFetcher.downloadPicture] $url")

        if(!Util.isInternetAvailable(context)) {
            return false
        }

        val file = File(bookFolder, page.toString())
        val requestBuilder = Request.Builder().url(url)
        for (header in headers) {
            requestBuilder.addHeader(header.key, header.value)
        }

        val request = requestBuilder.build()
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    throw Exception("[HiPictureFetcher.savePicture] code 403 when downloading picture")
                }
                if (response.code == 404) {
                    throw Exception("[HiPictureFetcher.savePicture] code 404 when downloading picture")
                }
                if (!response.isSuccessful) {
                    throw Exception("[HiPictureFetcher.savePicture] unexpected response code ${response.code} when downloading picture")
                }
                file.outputStream().use { response.body!!.byteStream().copyTo(it) }
            }
        }
        return true
    }

    abstract suspend fun savePicture (page: Int): Boolean
}