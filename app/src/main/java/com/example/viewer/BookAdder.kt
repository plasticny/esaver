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
    protected abstract fun getId (url: String): String
    protected abstract fun getFetcher (id: String, url: String): BasePictureFetcher
    protected abstract suspend fun storeToDataSet (id: String, url: String)

    protected val bookDataset = BookDatabase.getInstance(context)

    suspend fun addBook (
        url: String,
        onSavingProgress: ((contentLength: Long, downloadLength: Long) -> Unit)? = null,
        onEnded: (doAdded: Boolean) -> Unit
    ) {
        if (!Util.isInternetAvailable(context)) {
            throw Exception("[BookAdder.addBook] internet not available")
        }

        val bookUrl = getUrl(url)
        val bookId = getId(bookUrl)

        // check if the book is already saved
        if (bookDataset.isBookStored(bookId)) {
            Toast.makeText(context, "已經儲存這本書", Toast.LENGTH_SHORT).show()
            onEnded(false)
            return
        }

        // download cover picture to tmp
        val pictureFile = getFetcher(bookId, bookUrl).savePicture(0, onSavingProgress)
        if(pictureFile == null) {
            Toast.makeText(context, "儲存失敗，再試一次", Toast.LENGTH_SHORT).show()
            onEnded(false)
            return
        }

        // create folder
        File(context.getExternalFilesDir(null), bookId).also {
            if (!it.exists()) {
                it.mkdirs()
            }
            // move cover picture
            val file = File(it, pictureFile.name)
            pictureFile.copyTo(file)
            pictureFile.delete()
        }

        storeToDataSet(bookId, bookUrl)

        onEnded(true)
    }
}

private class EBookAdder (context: Context): BookAdder(context) {
    override val bookSource = BookSource.E

    override fun getUrl(url: String): String = if (url.last() == '/') url.dropLast(1) else url

    override fun getId(url: String): String {
        val urlTokens = url.split('/')
        return urlTokens[urlTokens.size - 2]
    }

    override fun getFetcher(id: String, url: String): BasePictureFetcher = EPictureFetcher(context, 1, url)

    override suspend fun storeToDataSet(id: String, url: String) {
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(url).get()
        }
        bookDataset.addBook(
            id = id,
            url = url,
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

    override fun getId(url: String): String {
        val idSrtIdx = url.indexOfLast { it == '/' } + 1
        return url.substring(idSrtIdx, url.length - 5)
    }

    override fun getFetcher(id: String, url: String): BasePictureFetcher = HiPictureFetcher(context, id, 1)

    override suspend fun storeToDataSet(id: String, url: String) = bookDataset.addBook(
        id = id,
        url = url,
        category = SearchDatabase.Companion.Category.Doujinshi,
        title = "",
        pageNum = fetchPageNum(id),
        tags = mapOf(),
        source = BookSource.Hi
    )

    fun fetchPageNum(id: String): Int {
        var bookIdJs: String
        runBlocking {
            withContext(Dispatchers.IO) {
                bookIdJs = URL("https://ltn.hitomi.la/galleries/$id.js").readText().substring(18)
            }
        }
        val galleryInfo = Gson().fromJson(bookIdJs, GalleryInfo::class.java)!!
        return galleryInfo.files.size
    }
}
