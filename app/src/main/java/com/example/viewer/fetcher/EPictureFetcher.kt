package com.example.viewer.fetcher

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.viewer.Util
import com.example.viewer.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.File

class EPictureFetcher: BasePictureFetcher {
    private val bookDataset = BookRepository(context)
    private val bookUrl: String

    private val fetchingPictureUrl = mutableSetOf<Int>()
    /**
     * map page and its picture url
     */
    private val pictureUrlMap = mutableMapOf<Int, String>()
    private val getPageUrlMutex = Mutex()

    private var pageUrls: Array<String?>
    private var p: Int

    /**
     * for local book
     */
    constructor (context: Context, bookId: String): super(context, bookId) {
        p = bookDataset.getBookP(bookId)
        bookUrl = bookDataset.getBookUrl(bookId)
        pageUrls = bookDataset.getBookPageUrls(bookId)
    }

    /**
     * for online book
     */
    constructor (
        context: Context,
        pageNum: Int,
        bookUrl: String,
        bookId: String? = null
    ): super(context, pageNum, bookId) {
        p = 0
        this.bookUrl = bookUrl
        pageUrls = arrayOfNulls(pageNum)
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
        if (pageUrls[page] != null) {
            return pageUrls[page]!!
        }

        getPageUrlMutex.withLock {
            val logTag = "${this::class.simpleName}.${this::getPageUrl.name}"

            var firstNullIdx = pageUrls.indexOfFirst { it == null }.also {
                if (it == -1) {
                    throw IllegalStateException("[$logTag] all page urls should be fetched, something went wrong")
                }
            }

            while (pageUrls[page] == null) {
                Log.i(logTag, "load next p $p")
                withContext(Dispatchers.IO) {
                    Jsoup.connect(
                        "${bookUrl}?p=$p".also { Log.i(logTag, it) }
                    ).cookies(mapOf("nw" to "1")).get()
                }.select("#gdt a").map { it.attr("href") }.let { pageUrlSegment ->
                    if (pageUrlSegment.isEmpty()) {
                        throw Exception("[${this@EPictureFetcher::class.simpleName}.${this@EPictureFetcher::getPageUrl.name}] no page url fetched")
                    }
                    for (url in pageUrlSegment) {
                        pageUrls[firstNullIdx++] = url
                    }
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
        }
        return pageUrls[page]!!
    }
}