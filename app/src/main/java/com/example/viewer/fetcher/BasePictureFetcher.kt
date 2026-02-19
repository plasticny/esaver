package com.example.viewer.fetcher

import android.content.Context
import android.util.Log
import com.example.viewer.OkHttpHelper
import com.example.viewer.Util
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.ItemRepository
import com.example.viewer.data.struct.item.Item
import com.example.viewer.struct.ItemSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import java.io.File
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException

abstract class BasePictureFetcher {
    companion object {
        fun getFetcher (context: Context, itemId: Long): BasePictureFetcher {
            val itemSource = runBlocking { ItemRepository(context).getSource(itemId) }
            println("[BasePictureFetcher.getFetcher] ${itemSource.name}")
            return when (itemSource) {
                ItemSource.E -> EPictureFetcher(context, itemId)
                ItemSource.Wn -> WnPictureFetcher(context, itemId)
                else -> throw NotImplementedError(itemSource.name)
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
    protected val itemId: Long
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
    protected constructor (context: Context, itemId: Long, itemSource: ItemSource) {
        val bookRepo = BookRepository(context)

        this.context = context
        this.itemId = itemId
        this.bookId = bookRepo.getBookId(itemId)

        pageNum = bookRepo.getBookPageNum(itemId)
        bookFolder = Item.getFolder(context, itemId)
        isLocal = true
    }

    /**
     * for online book
     * @param bookId system will store this id, and avoid repeat downloading if the current book id is same as previous one
     */
    protected constructor (context: Context, pageNum: Int, itemSource: ItemSource, bookId: String? = null) {
        this.context = context
        this.pageNum = pageNum
        this.itemId = -1L
        this.bookId = bookId

        this.bookFolder = File(context.getExternalFilesDir(null), "tmp")
        if (!this.bookFolder.exists()) {
            this.bookFolder.mkdirs()
        }

        // compare previous tmp book id and that of current
        // to determine whether if the tmp folder should be cleared
        val bookIdTxt = File(this.bookFolder, "bookId.txt")
        val fullBookId = bookId?.let {
            when (itemSource) {
                ItemSource.Wn -> "wn$bookId"
                ItemSource.E -> bookId
                else -> throw NotImplementedError(itemSource.name)
//                BookSource.Hi -> bookId
            }
        }
        if (bookId == null || !bookIdTxt.exists() || fullBookId!! != bookIdTxt.readText()) {
            println("[${this::class.simpleName}] clear tmp folder")
            for (file in this.bookFolder.listFiles()!!) {
                file.delete()
            }
            bookId?.let {
                bookIdTxt.createNewFile()
                bookIdTxt.writeText(fullBookId!!)
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

        val logTag = "${this@BasePictureFetcher::class.simpleName}.${this@BasePictureFetcher::downloadPicture.name}"
        Util.log(
            logTag,
            "download started: $page",
            url
        )

        val okHttpHelper = progressListener?.let { OkHttpHelper(it) } ?: OkHttpHelper()
        val success = okHttpHelper.downloadImage(url, file, headers)
        if (!success) {
            if (file.exists()) {
                file.delete()
            }
            throw PictureDownloadFailException()
        }

        downloadingPages.remove(page)

        Log.i(logTag, "download finished: $page")

        return file
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

    fun isPictureDownloading (page: Int) = downloadingPages.contains(page)

    class PictureDownloadFailException: Exception()
}