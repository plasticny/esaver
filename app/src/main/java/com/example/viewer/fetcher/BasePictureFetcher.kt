package com.example.viewer.fetcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
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

    private val fileGlide: RequestBuilder<Drawable>
    private val downloadedPage = mutableSetOf<Int>()
    private var downloadFailureCallback: ((Response) -> Unit)? = null

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

        fileGlide = Glide.with(context).asDrawable()
//            .setDefaultRequestOptions(RequestOptions()
//                .diskCacheStrategy(DiskCacheStrategy.NONE)
//                .skipMemoryCache(true)
//            ).asDrawable()
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

        fileGlide = Glide.with(context).asDrawable()
        isLocal = false
    }

    suspend fun getPicture (page: Int, loadListener: RequestListener<Drawable>? = null): RequestBuilder<Drawable>? {
        assertPageInRange(page)
        assertCallByLocalBookFetcher()

        val pictureFile = File(bookFolder, page.toString())
        println("[BasePictureFetcher.getPicture]\n${pictureFile.path}")

        if (!pictureFile.exists()) {
            // prevent multiple download
            if (downloadedPage.contains(page)) {
                return null
            }
            downloadedPage.add(page)

            if (!Util.isInternetAvailable(context)) {
                Toast.makeText(context, "沒有網絡，無法下載", Toast.LENGTH_SHORT).show()
                return null
            }

            savePicture(page).let { retFlag ->
                if (!retFlag) {
                    return null
                }
            }
        }

        return fileGlide.listener(loadListener).load(pictureFile.path)
    }

    fun setDownloadFailureCallback (cb: (Response) -> Unit) {
        downloadFailureCallback = cb
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

        val file = File(bookFolder, page.toString())
        val requestBuilder = Request.Builder().url(url)
        for (header in headers) {
            requestBuilder.addHeader(header.key, header.value)
        }

        val request = requestBuilder.build()
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    file.outputStream().use { response.body!!.byteStream().copyTo(it) }
                } else {
                    downloadFailureCallback?.invoke(response)
                }
            }
        }
        return true
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