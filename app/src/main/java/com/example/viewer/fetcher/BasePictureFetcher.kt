package com.example.viewer.fetcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.example.viewer.database.BookSource
import com.example.viewer.database.BookDatabase
import com.example.viewer.Util
import com.example.viewer.fetcher.HiPictureFetcher.Companion.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.File

abstract class BasePictureFetcher {
    companion object {
        fun getFetcher (context: Context, bookId: String): BasePictureFetcher {
            val source = BookDatabase.getInstance(context).getBookSource(bookId)
            println("[BasePictureFetcher.getFetcher] $source")
            return when (source) {
                BookSource.E -> EPictureFetcher(context, bookId)
                BookSource.Hi -> HiPictureFetcher(context, bookId)
            }
        }
    }

    abstract suspend fun savePicture (page: Int): Boolean
    protected abstract suspend fun fetchPictureUrl (page: Int): String?

    private val downloadingPages = mutableSetOf<Int>()

    protected val context: Context
    protected val bookId: String?
    protected val pageNum: Int
    protected val isLocal: Boolean

    val bookFolder: File?

    /**
     * for local book
     */
    protected constructor (context: Context, bookId: String) {
        this.context = context
        this.bookId = bookId

        pageNum = BookDatabase.getInstance(context).getBookPageNum(bookId)
        bookFolder = File(context.getExternalFilesDir(null), bookId)
        isLocal = true
    }

    /**
     * for online book
     */
    protected constructor (context: Context, pageNum: Int) {
        this.context = context
        this.pageNum = pageNum
        this.bookId = null
        this.bookFolder = null

        isLocal = false
    }

    suspend fun getPictureUrl (page: Int): String? {
        assertPageInRange(page)

        if (!isLocal) {
            // local fetcher, no need to check storage
            return fetchPictureUrl(page)
        }

        //
        // check whether picture is stored
        //
        val pictureFile = File(bookFolder, page.toString())
        println("[${this::class.simpleName}.${this::getPictureUrl.name}]\n${pictureFile.path}")

        // when the picture is on downloading
        if (downloadingPages.contains(page)) {
            withContext(Dispatchers.IO) {
                while (downloadingPages.contains(page)) {
                    Thread.sleep(100)
                }
            }
        }

        if (!pictureFile.exists()) {
            downloadingPages.add(page) // fetching picture url may take time
            savePicture(page).let { retFlag ->
                if (!retFlag) {
                    return null
                }
            }
        }

        return pictureFile.path
    }

    protected suspend fun downloadPicture (
        page: Int,
        url: String,
        headers: Map<String, String> = mapOf()
    ): Boolean {
        println("[BasePictureFetcher.downloadPicture] $url")

        assertCallByLocalBookFetcher()

        if(!Util.isInternetAvailable(context)) {
            return false
        }

        downloadingPages.add(page)

        val file = File(bookFolder, page.toString())
        val requestBuilder = Request.Builder().url(url)
        for (header in headers) {
            requestBuilder.addHeader(header.key, header.value)
        }

        val request = requestBuilder.build()
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    file.outputStream().use { response.body!!.byteStream().copyTo(it) }
                    // this line should be after the write-to-file statement
                    // else the corrupted image might be read
                    downloadingPages.remove(page)
                }
                return@withContext response.isSuccessful
            }
        }
    }

    protected fun assertPageInRange (page: Int) {
        if (page < 0 || page >= pageNum) {
            throw Exception("page out of range")
        }
    }

    private fun assertCallByLocalBookFetcher () {
        if (bookFolder == null) {
            throw Exception("only fetcher construct with local book config call this function")
        }
    }
}