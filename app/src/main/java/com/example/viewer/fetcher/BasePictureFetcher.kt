package com.example.viewer.fetcher

import android.content.Context
import com.example.viewer.Util
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.struct.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.net.SocketTimeoutException

abstract class BasePictureFetcher {
    companion object {
        private val okHttpClient = OkHttpClient()

        fun getFetcher (context: Context, bookId: String): BasePictureFetcher {
            val source = BookRepository(context).getBookSource(bookId)
            println("[BasePictureFetcher.getFetcher] $source")
            return when (source) {
                BookSource.E -> EPictureFetcher(context, bookId)
                BookSource.Hi -> HiPictureFetcher(context, bookId)
            }
        }
    }

    abstract suspend fun savePicture (
        page: Int, progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)? = null
    ): File?
    protected abstract suspend fun fetchPictureUrl (page: Int): String?

    private val downloadingPages = mutableSetOf<Int>()

    protected val context: Context
    protected val bookId: String?
    protected val pageNum: Int
    protected val isLocal: Boolean

    /**
     * for local book, this will be a folder named by the book id in data folder
     *
     * for online book, this will be a folder named "tmp" in data folder
     */
    val bookFolder: File

    /**
     * for local book
     */
    protected constructor (context: Context, bookId: String) {
        this.context = context
        this.bookId = bookId

        pageNum = BookRepository(context).getBookPageNum(bookId)
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

        this.bookFolder = File(context.getExternalFilesDir(null), "tmp").also {
            if (!it.exists()) {
                it.mkdirs()
            } else {
                for (file in it.listFiles()!!) {
                    file.delete()
                }
            }
        }

        isLocal = false
    }

    suspend fun getPictureUrl (
        page: Int,
        progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)? = null
    ): String? {
        assertPageInRange(page)

        //
        // check whether picture is stored
        //
        val pictureFile = File(bookFolder, page.toString())
        println("[${this::class.simpleName}.${this::getPictureUrl.name}]\n${pictureFile.path}")

        // prevent return the url of an incomplete picture
        waitPictureDownload(page)

        if (!pictureFile.exists()) {
            savePicture(page, progressListener) ?: return null
        }

        return pictureFile.path
    }

    fun deletePicture (page: Int) {
        val file = File(bookFolder, page.toString())
        if (file.exists()) {
            file.delete()
        }
    }

    fun close () {
        if (!isLocal) {
            for (file in bookFolder.listFiles()!!) {
                file.delete()
            }
        }
    }

    /**
     * @return downloaded picture file
     */
    protected suspend fun downloadPicture (
        page: Int,
        url: String,
        headers: Map<String, String> = mapOf(),
        progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)? = null
    ): File? {
        if(!Util.isInternetAvailable(context)) {
            return null
        }

        if (!bookFolder.exists()) {
            bookFolder.mkdirs()
        }

        val file = File(bookFolder, page.toString())

        // prevent multiple download
        waitPictureDownload(page)
        if (file.exists()) {
            return file
        }
        downloadingPages.add(page)

        // build the download request
        val downloadClient = progressListener?.let {
            okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request()).run {
                        newBuilder().body(
                            ProgressResponseBody(body!!, progressListener)
                        ).build()
                    }
                }.build()
        } ?: okHttpClient
        val request = Request.Builder().url(url).apply {
            for (header in headers) {
                addHeader(header.key, header.value)
            }
        }.build()

        return withContext(Dispatchers.IO) {
            try {
                println("[BasePictureFetcher.downloadPicture] start download $page\n$url")
                downloadClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        file.outputStream().use { response.body!!.byteStream().copyTo(it) }
                        // this line should be after the write-to-file statement
                        // else a corrupted image might be read
                        downloadingPages.remove(page)
                        return@withContext file
                    } else {
                        return@withContext null
                    }
                }
            } catch (e: SocketTimeoutException) {
                println("[${this@BasePictureFetcher::class.simpleName}.${this@BasePictureFetcher::downloadingPages.name}] socket time out")
                return@withContext null
            }
        }
    }

    protected fun assertPageInRange (page: Int) {
        if (page < 0 || page >= pageNum) {
            throw Exception("page out of range")
        }
    }

    /**
     * wait if the picture of "page" is downloading
     */
    private suspend fun waitPictureDownload (page: Int) {
        if (downloadingPages.contains(page)) {
            withContext(Dispatchers.IO) {
                while (downloadingPages.contains(page)) {
                    delay(100)
                }
            }
        }
    }
}

private class ProgressResponseBody (
    private val responseBody: ResponseBody,
    private val progressListener: (contentLength: Long, downloadLength: Long) -> Unit
): ResponseBody() {
    private var bufferedSource =
        object: ForwardingSource(responseBody.source()) {
            private var totalBytesRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long =
                super.read(sink, byteCount).also {
                    totalBytesRead += if (it == -1L) 0 else it
                    progressListener(contentLength(), totalBytesRead)
                }
        }.buffer()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun source(): BufferedSource = bufferedSource
}