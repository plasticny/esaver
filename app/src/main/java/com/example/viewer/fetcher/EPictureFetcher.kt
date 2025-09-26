package com.example.viewer.fetcher

import android.content.Context
import android.util.Log
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.struct.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.UnknownHostException

class EPictureFetcher: BasePictureFetcher {
    companion object {
        /**
         * with context io wrapped
         */
        @JvmStatic
        suspend fun fetchWebpage (url: String, nw: Boolean = false): Document {
            return try {
                withContext(Dispatchers.IO) {
                    Jsoup.connect(url).run {
                        if (nw) {
                            cookies(mapOf("nw" to "1"))
                        }
                        this
                    }.get()
                }
            } catch (e: HttpStatusException) {
                assert(e.statusCode == 404 || e.statusCode == 408)
                throw e
            } catch (e: UnknownHostException) {
                // throw when no network
                throw e
            }
        }
    }

    private val bookRepo = BookRepository(context)
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
    constructor (context: Context, bookId: String): super(context, bookId, BookSource.E) {
        p = bookRepo.getBookP(bookId)
        bookUrl = bookRepo.getBookUrl(bookId)
        pageUrls = bookRepo.getBookPageUrls(bookId)
    }

    /**
     * for online book
     */
    constructor (
        context: Context,
        pageNum: Int,
        bookUrl: String,
        bookId: String? = null
    ): super(context, pageNum, BookSource.E, bookId) {
        p = 0
        this.bookUrl = bookUrl
        pageUrls = arrayOfNulls(pageNum)
    }

    override suspend fun savePicture(
        page: Int,
        progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)?
    ): File {
        println("[EPictureFetcher.savePicture] $page")
        assertPageInRange(page)
        return downloadPicture(page, fetchPictureUrl(page), progressListener =  progressListener)
    }

    override suspend fun fetchPictureUrl (page: Int): String {
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
        val res = fetchWebpage(getPageUrl(page)).selectFirst("#i3 #img")?.attr("src")
        if (res != null) {
            pictureUrlMap[page] = res
        } else {
            throw IllegalStateException("cannot fetch picture url of page $page")
        }
        fetchingPictureUrl.remove(page)

        return res
    }

    private suspend fun getPageUrl (page: Int): String {
        getPageUrlMutex.withLock {
            if (pageUrls[page] != null) {
                return pageUrls[page]!!
            }

            val logTag = "${this::class.simpleName}.${this::getPageUrl.name}"

            var firstNullIdx = pageUrls.indexOfFirst { it == null }.also {
                if (it == -1) {
                    throw IllegalStateException("[$logTag] all page urls should be fetched, something went wrong")
                }
            }

            while (pageUrls[page] == null) {
                Log.i(logTag, "load next p $p")
                val pageDoc = try {
                    fetchWebpage("${bookUrl}?p=$p".also { Log.i(logTag, it) }, true)
                } catch (e: HttpStatusException) {
                    if (e.statusCode == 404) {
                        Log.e(logTag, "404 on fetching book p")
                        throw e
                    }
                    null
                }

                if (pageDoc == null) {
                    continue
                }

                pageDoc.select("#gdt a").map { it.attr("href") }.let { pageUrlSegment ->
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
                        p = bookRepo.increaseBookP(bookId)
                        bookRepo.setBookPageUrls(bookId, pageUrls)
                    }
                } else {
                    p++
                }
            }
        }
        return pageUrls[page]!!
    }
}