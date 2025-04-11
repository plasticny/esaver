package com.example.viewer

import android.content.Context
import android.widget.Toast
import com.example.viewer.database.BookDatabase
import com.example.viewer.database.BookSource
import com.example.viewer.database.SearchDatabase
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.fetcher.HiPictureFetcher
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
    protected abstract fun getFetcher (): BasePictureFetcher
    protected abstract suspend fun storeToDataSet ()

    // init in addBook
    protected lateinit var bookId: String
    protected lateinit var bookUrl: String

    protected val bookDataset = BookDatabase.getInstance(context)

    suspend fun addBook (url: String, onEnded: (doAdded: Boolean) -> Unit) {
        if (!Util.isInternetAvailable(context)) {
            throw Exception("[BookAdder.addBook] internet not available")
        }

        bookUrl = getUrl(url)
        bookId = getId()

        // check if the book is already saved
        if (bookDataset.isBookStored(bookId)) {
            Toast.makeText(context, "已經儲存這本書", Toast.LENGTH_SHORT).show()
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

    override suspend fun storeToDataSet() {
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(bookUrl).get()
        }
        bookDataset.addBook(
            id = bookId,
            url = bookUrl,
            category = Util.categoryFromName(
                doc.selectFirst("#gdc")!!.text().trim()
            ),
            title = findTitle(doc),
            pageNum = findPageNum(doc),
            tags = findTags(doc),
            source = BookSource.E
        )
    }

    fun findTitle (doc: Document): String {
        doc.selectFirst("#gj")!!.text().trim().let {
            if (it.isNotEmpty()) {
                return it
            }
        }
        return doc.selectFirst("#gn")!!.text().trim()
    }

    fun findPageNum (doc: Document): Int {
        val gdt2s = doc.select("#gdd .gdt2")
        for (gdt2 in gdt2s) {
            val text = gdt2.text()
            if (text.contains("page")) {
                return text.split(' ').first().toInt()
            }
        }
        throw Exception("[${this::class.simpleName}.${this::findPageNum.name}] cannot find page number")
    }

    fun findTags (doc: Document): Map<String, List<String>> {
        val tags = mutableMapOf<String, List<String>>()

        doc.select("#taglist tr").forEach { tr ->
            val category = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
            tags[category] = tr.select(".gt,.gtl").map { it.text().trim() }
        }

        return tags
    }
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

    override suspend fun storeToDataSet() = bookDataset.addBook(
        id = bookId,
        url = bookUrl,
        category = SearchDatabase.Companion.Category.Doujinshi,
        title = "",
        pageNum = fetchPageNum(),
        tags = mapOf(),
        source = BookSource.Hi
    )

    fun fetchPageNum(): Int {
        var bookIdJs: String
        runBlocking {
            withContext(Dispatchers.IO) {
                bookIdJs = URL("https://ltn.hitomi.la/galleries/${bookId}.js").readText().substring(18)
            }
        }
        val galleryInfo = Gson().fromJson(bookIdJs, GalleryInfo::class.java)!!
        return galleryInfo.files.size
    }
}
