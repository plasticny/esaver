package com.example.viewer.fetcher

import android.content.Context
import android.widget.Toast
import com.example.viewer.Util
import com.example.viewer.database.BookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup

class EPictureFetcher: BasePictureFetcher {
    private val bookDataset = BookDatabase.getInstance(context)
    private val bookUrl: String

    @Volatile
    private var gettingPageUrl = false
    private var pageUrls: MutableList<String>
    private var p: Int

    constructor (context: Context, bookId: String): super(context, bookId) {
        p = bookDataset.getBookP(bookId)
        bookUrl = bookDataset.getBookUrl(bookId)
        pageUrls = bookDataset.getBookPageUrls(bookId).toMutableList()
    }

    constructor (context: Context, pageNum: Int, bookUrl: String): super(context, pageNum) {
        p = 0
        this.bookUrl = bookUrl
        pageUrls = mutableListOf()
    }

    override suspend fun savePicture(page: Int): Boolean {
        println("[EPictureFetcher.savePicture] $page")
        assertPageInRange(page)

        if (!Util.isInternetAvailable(context)) {
            Toast.makeText(context, "沒有網絡，無法下載", Toast.LENGTH_SHORT).show()
            return false
        }

        return fetchPictureUrl(page)?.let {
            downloadPicture(page, it)
        } ?: false
    }

    override suspend fun fetchPictureUrl (page: Int): String? {
        println("[${this::class.simpleName}.${this::fetchPictureUrl.name}] $page")

        if (page >= pageNum) {
            throw Exception("page out of range")
        }

        return withContext(Dispatchers.IO) {
            try {
                Jsoup.connect(getPageUrl(page)).get()
            } catch (e: HttpStatusException) {
                null
            }
        }?.selectFirst("#i3 #img")!!.attr("src") ?: null
    }

    private suspend fun getPageUrl (page: Int): String {
        while (gettingPageUrl) {
            withContext(Dispatchers.IO) {
                Thread.sleep(100)
            }
        }

        if (page > pageUrls.lastIndex) {
            gettingPageUrl = true

            println("[${this::class.simpleName}.${this::getPageUrl.name}] load next p $p")

            withContext(Dispatchers.IO) {
                val url = "${bookUrl}/?p=$p"
                println("[${this@EPictureFetcher::class.simpleName}.${this@EPictureFetcher::getPageUrl.name}]\nfetching page url from $url")
                Jsoup.connect(url).get()
            }.select("#gdt a").map { it.attr("href") }.let {
                pageUrlSegment -> pageUrls.addAll(pageUrlSegment)
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

            gettingPageUrl = false
        }
        return pageUrls[page]
    }
}