package com.example.viewer.fetcher

import android.content.Context
import android.util.Log
import com.example.viewer.Util
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.struct.BookSource
import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document
import java.io.File

class WnPictureFetcher: BasePictureFetcher {
    companion object {
        private const val MAX_REQUEST = 16
        private const val REQUEST_DELAY = 30000L
        private var request_cnt = 0

        suspend fun obtainRequestSlot () {
            withContext(Dispatchers.IO) {
                while (request_cnt >= MAX_REQUEST) {
                    delay(100)
                }
            }
            request_cnt++

            Thread {
                Thread.sleep(REQUEST_DELAY)
                request_cnt--
            }.start()
        }
    }

    private val bookRepo: BookRepository by lazy { BookRepository(context) }
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
    constructor (context: Context, bookId: String): super(context, bookId, BookSource.Wn) {
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
    ): super(context, pageNum, BookSource.Wn, bookId) {
        p = 1
        this.bookUrl = bookUrl
        pageUrls = arrayOfNulls(pageNum)
    }

    override suspend fun savePicture(
        page: Int,
        progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)?
    ): File {
        // dynamic log tag does not work
        Util.log("WnPictureFetcher.SavePicture", "save $page")
        assertPageInRange(page)
        return downloadPicture(page, fetchPictureUrl(page), progressListener =  progressListener)
    }

    override suspend fun fetchPictureUrl(page: Int): String {
        if (fetchingPictureUrl.contains(page)) {
            withContext(Dispatchers.IO) {
                Thread.sleep(100)
            }
        }

        if (pictureUrlMap.containsKey(page)) {
            return pictureUrlMap.getValue(page)
        }
        fetchingPictureUrl.add(page)

        val logTag = "${this::class.simpleName}.${this::fetchPictureUrl.name}"
        Util.log(logTag, "fetch $page start")

        val res = withContext(Dispatchers.IO) {
            obtainRequestSlot()
            fetchWebpage(getPageUrl((page)))
        }.getElementById("picarea")?.attr("src")
        if (res != null) {
            println(res)
            pictureUrlMap[page] = "https:${res}"
        } else {
            throw IllegalStateException("cannot fetch picture url of page $page")
        }
        fetchingPictureUrl.remove(page)

        Util.log(logTag, "fetch $page end")

        assert(pictureUrlMap.containsKey(page)) {
            "exist keys: ${pictureUrlMap.keys}; assert to be exist: $page"
        }
        return pictureUrlMap.getValue(page)
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
                Util.log(logTag, "load next p $p")
                val pageDoc = try {
                    obtainRequestSlot()
                    fetchWebpage(
                        "https://www.wnacg.com/photos-index-page-${p}-aid-${bookId}.html".also {
                            Util.log(logTag, "fetch next p from $it")
                        }
                    )
                } catch (e: HttpStatusException) {
                    if (e.statusCode == 404) {
                        Log.e(logTag, "404 on fetching book p")
                        throw e
                    }
                    continue
                }

                pageDoc.select(".gallary_item a").also {
                    assert(it.isNotEmpty())
                }.forEach { pageUrls[firstNullIdx++] = "https://www.wnacg.com${it.attr("href")}" }

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

    private suspend fun fetchWebpage(webpageUrl: String): Document =
        withContext(Dispatchers.IO) {
            skrape(HttpFetcher) {
                request {
                    url = webpageUrl
                }
                response {
                    htmlDocument { this }
                }
            }.document
        }

}