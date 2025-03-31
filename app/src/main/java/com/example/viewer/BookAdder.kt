package com.example.viewer

import android.content.Context
import android.widget.Toast
import com.example.viewer.dataset.BookDataset
import com.example.viewer.dataset.BookSource
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.fetcher.HiPictureFetcher
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

abstract class BookAdder (protected val context: Context) {
    companion object {
        fun getBookAdder (context: Context, source: BookSource): BookAdder {
            return when (source) {
                BookSource.E -> EBookAdder(context)
                BookSource.Hi -> HiBookAdder(context)
            }
        }
    }

    protected abstract val bookSource: BookSource

    protected abstract fun getUrl (url: String): String
    protected abstract fun getId (): String
    protected abstract suspend fun fetchPageNum(): Int
    protected abstract fun getFetcher (): BasePictureFetcher
    protected abstract fun storeToDataSet ()

    // init in addBook
    protected lateinit var bookId: String
    protected lateinit var bookUrl: String
    protected var pageNum: Int = -1

    protected val bookDataset = BookDataset.getInstance(context)

    suspend fun addBook (url: String, onEnded: (doAdded: Boolean) -> Unit) {
        if (!Util.isInternetAvailable(context)) {
            throw Exception("[BookAdder.addBook] internet not available")
        }

        bookUrl = getUrl(url)
        bookId = getId()
        pageNum = fetchPageNum()

        // check if the book is already saved
        if (bookDataset.getAllBookIds().contains(bookId)) {
            Toast.makeText(context, "已經存有這本書", Toast.LENGTH_SHORT).show()
            onEnded(false)
            return
        }

        // create folder
        File(context.getExternalFilesDir(null), bookId).let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        storeToDataSet()

        // save cover picture
        val retFlag = getFetcher().savePicture(0)
        onEnded(retFlag)
    }
}

private class EBookAdder (context: Context): BookAdder(context) {
    override val bookSource = BookSource.E

    override fun getUrl(url: String): String = if (url.last() == '/') url.dropLast(1) else url

    override fun getId(): String {
        val urlTokens = bookUrl.split('/')
        return urlTokens[urlTokens.size - 2]
    }

    override fun getFetcher(): BasePictureFetcher = EPictureFetcher(context, bookId)

    override suspend fun fetchPageNum(): Int {
        val html = withContext(Dispatchers.IO) {
            coroutineScope { async { URL(bookUrl).readText() }.await() }
        }
        val pageText = Regex(">(\\d+) pages<").find(html)!!.value
        return pageText.substring(1, pageText.length - 7).toInt()
    }

    override fun storeToDataSet() = bookDataset.addBook(
        bookId, bookUrl, BookSource.E, pageNum
    )
}

private class HiBookAdder (context: Context): BookAdder(context) {
    companion object {
        private data class GalleryInfo (
            val files: List<Any>
        )
    }

    override val bookSource: BookSource = BookSource.Hi

    override fun getUrl(url: String): String {
        val hashTagIdx = url.indexOf('#')
        return if (hashTagIdx == -1) url else url.substring(0, hashTagIdx)
    }

    override fun getId(): String {
        val idSrtIdx = bookUrl.indexOfLast { it == '/' } + 1
        return bookUrl.substring(idSrtIdx, bookUrl.length - 5)
    }

    override fun getFetcher(): BasePictureFetcher = HiPictureFetcher(context, bookId)

    override suspend fun fetchPageNum(): Int {
        var bookIdJs: String
        runBlocking {
            withContext(Dispatchers.IO) {
                bookIdJs = URL("https://ltn.hitomi.la/galleries/${bookId}.js").readText().substring(18)
            }
        }
        val galleryInfo = Gson().fromJson(bookIdJs, GalleryInfo::class.java)!!
        return galleryInfo.files.size
    }

    override fun storeToDataSet() = bookDataset.addBook(
        bookId, bookUrl, BookSource.Hi, pageNum
    )
}
