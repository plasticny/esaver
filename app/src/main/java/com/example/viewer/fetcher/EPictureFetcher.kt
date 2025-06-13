package com.example.viewer.fetcher

import android.content.Context
import android.widget.Toast
import com.example.viewer.Util
import com.example.viewer.data.database.BookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.File

class EPictureFetcher: BasePictureFetcher {
    companion object {
        suspend fun isBookWarning (url: String) =
            withContext(Dispatchers.IO) {
                Jsoup.connect(url).get()
            }.html().contains("<h1>Content Warning</h1>")
    }

    private val bookDataset = BookDatabase.getInstance(context)
    private val bookUrl: String

    private val fetchingPictureUrl = mutableSetOf<Int>()
    /**
     * map page and its picture url
     */
    private val pictureUrlMap = mutableMapOf<Int, String>()

    @Volatile
    private var gettingPageUrl = false
    private var pageUrls: MutableList<String>
    private var p: Int

    /**
     * for local book
     */
    constructor (context: Context, bookId: String): super(context, bookId) {
        p = bookDataset.getBookP(bookId)
        bookUrl = bookDataset.getBookUrl(bookId)
        pageUrls = bookDataset.getBookPageUrls(bookId).toMutableList()
    }

    /**
     * for online book
     */
    constructor (context: Context, pageNum: Int, bookUrl: String): super(context, pageNum) {
        p = 0
        this.bookUrl = bookUrl
        pageUrls = mutableListOf()
    }

    override suspend fun savePicture(
        page: Int,
        progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)?
    ): File? {
        println("[EPictureFetcher.savePicture] $page")
        assertPageInRange(page)

        if (!Util.isInternetAvailable(context)) {
            Toast.makeText(context, "沒有網絡，無法下載", Toast.LENGTH_SHORT).show()
            return null
        }

        return fetchPictureUrl(page)?.let {
            downloadPicture(page, it, progressListener =  progressListener)
        }
    }

    override suspend fun fetchPictureUrl (page: Int): String? {
        if (page >= pageNum) {
            throw Exception("page out of range")
        }

        if (fetchingPictureUrl.contains(page)) {
            withContext(Dispatchers.IO) {
                Thread.sleep(100)
            }
        }

        if (pictureUrlMap.containsKey(page)) {
            return pictureUrlMap.getValue(page)
        }

        println("[${this::class.simpleName}.${this::fetchPictureUrl.name}] $page")

        fetchingPictureUrl.add(page)
        val res = withContext(Dispatchers.IO) {
            try {
                Jsoup.connect(getPageUrl(page)).get()
            } catch (e: HttpStatusException) {
                null
            }
        }?.selectFirst("#i3 #img")?.attr("src")
        if (res != null) {
            pictureUrlMap[page] = res
        }
        fetchingPictureUrl.remove(page)

        return res
    }

    private suspend fun getPageUrl (page: Int): String {
        while (gettingPageUrl) {
            withContext(Dispatchers.IO) {
                Thread.sleep(100)
            }
        }

        while (page > pageUrls.lastIndex) {
            gettingPageUrl = true

            println("[${this::class.simpleName}.${this::getPageUrl.name}] load next p $p")

            val doc = withContext(Dispatchers.IO) {
                val url = if (isBookWarning(bookUrl)) "${bookUrl}/?p=$p&nw=always" else "${bookUrl}/?p=$p"
                println("[${this@EPictureFetcher::class.simpleName}.${this@EPictureFetcher::getPageUrl.name}]\nfetching page url from $url")
                Jsoup.connect(url).get()
            }
            doc.select("#gdt a").map { it.attr("href") }.let { pageUrlSegment ->
                if (pageUrlSegment.isEmpty()) {
                    println(doc.html())
                    throw Exception("[${this@EPictureFetcher::class.simpleName}.${this@EPictureFetcher::getPageUrl.name}] no page url fetched")
                }
                pageUrls.addAll(pageUrlSegment)
            }

            if (isLocal) {
                // save progress if local fetcher
                bookId!!.let {
                    p = bookDataset.increaseBookP(bookId)
                    bookDataset.setBookPageUrls(bookId, pageUrls)
                }
            } else {
                p++
            }
        }
        gettingPageUrl = false

        return pageUrls[page]
    }
}