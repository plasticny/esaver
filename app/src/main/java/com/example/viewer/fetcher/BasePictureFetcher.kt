package com.example.viewer.fetcher

import android.content.Context
import android.util.Log
import com.example.viewer.Util
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
import org.jsoup.HttpStatusException
import java.io.File
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.math.log

abstract class BasePictureFetcher {
    companion object {
        private val okHttpClient = OkHttpClient()

        fun getFetcher (context: Context, bookId: String): BasePictureFetcher {
            val source = BookRepository(context).getBookSource(bookId)
            println("[BasePictureFetcher.getFetcher] $source")
            return when (source) {
                BookSource.E -> EPictureFetcher(context, bookId)
                BookSource.Hi -> HiPictureFetcher(context, bookId)
                BookSource.Wn -> WnPictureFetcher(context, bookId)
                else -> throw NotImplementedError(source.name)
            }
        }
    }

    /**
     * @throws HttpStatusException save failed
     * @throws SocketTimeoutException
     * @throws ConnectException
     */
    abstract suspend fun savePicture (
        page: Int, progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)? = null
    ): File
    abstract suspend fun fetchPictureUrl (page: Int): String

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
     * @param bookId system will store this id, and avoid repeat downloading if the current book id is same as previous one
     */
    protected constructor (context: Context, pageNum: Int, bookId: String? = null) {
        this.context = context
        this.pageNum = pageNum
        this.bookId = bookId

        this.bookFolder = File(context.getExternalFilesDir(null), "tmp")
        if (!this.bookFolder.exists()) {
            this.bookFolder.mkdirs()
        }

        // compare previous tmp book id and that of current
        // to determine whether if the tmp folder should be cleared
        val bookIdTxt = File(this.bookFolder, "bookId.txt")
        if (bookId == null || !bookIdTxt.exists() || bookId != bookIdTxt.readText()) {
            println("[${this::class.simpleName}] clear tmp folder")
            for (file in this.bookFolder.listFiles()!!) {
                file.delete()
            }
            bookId?.let {
                bookIdTxt.createNewFile()
                bookIdTxt.writeText(bookId)
            }
        }

        isLocal = false
    }

    /**
     * @throws FileNotFoundException
     */
    fun getPictureStoredUrl (page: Int): String {
        assertPageInRange(page)

        //
        // check whether picture is stored
        //
        val pictureFile = File(bookFolder, page.toString())
        println("[${this::class.simpleName}.${this::getPictureStoredUrl.name}]\n${pictureFile.path}")

        if (!pictureFile.exists()) {
            throw FileNotFoundException()
        }

        return pictureFile.path
    }

    fun deletePicture (page: Int) {
        val file = File(bookFolder, page.toString())
        if (file.exists()) {
            file.delete()
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
    ): File {
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

        val logTag = "${this@BasePictureFetcher::class.simpleName}.${this@BasePictureFetcher::downloadingPages.name}"

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
            Util.log(
                logTag,
                "download started: $page",
                url
            )
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpStatusException("download failed", response.code, url)
                }

                file.outputStream().use {
                    response.body!!.byteStream().copyTo(it)
                }

                // this line should be after the write-to-file statement
                // else a corrupted image might be read
                downloadingPages.remove(page)

                Log.i(logTag, "download finished: $page")

                return@withContext file
            }
        }
    }

    protected fun assertPageInRange (page: Int) {
        if (page < 0 || page >= pageNum) {
            throw IllegalArgumentException("page $page out of range")
        }
    }

    /**
     * wait if the picture of "page" is downloading
     */
    suspend fun waitPictureDownload (page: Int) {
        withContext(Dispatchers.IO) {
            while (downloadingPages.contains(page)) {
                delay(100)
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