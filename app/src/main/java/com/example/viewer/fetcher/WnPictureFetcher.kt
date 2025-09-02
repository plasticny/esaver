package com.example.viewer.fetcher

import android.content.Context
import android.util.Log
import com.example.viewer.Util
import com.example.viewer.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.File

class WnPictureFetcher: BasePictureFetcher {
    companion object {
        private const val MAX_REQUEST = 19
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
    // book id remove "wn"
    private val pureBookId: String
        get() = bookId!!.slice(2 until bookId.length)

    private var pageUrls: Array<String?>
    private var p: Int

    /**
     * for local book
     */
    constructor (context: Context, bookId: String): super(context, bookId) {
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
    ): super(context, pageNum, bookId) {
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
            Jsoup.connect(getPageUrl(page)).get()
        }.getElementById("picarea")?.attr("src")
        if (res != null) {
            pictureUrlMap[page] = "https:${res}"
        } else {
            throw IllegalStateException("cannot fetch picture url of page $page")
        }
        fetchingPictureUrl.remove(page)

        Util.log(logTag, "fetch $page end")

        return pictureUrlMap[page]!!
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
                    Jsoup.connect(
                        "https://www.wnacg.com/photos-index-page-${p}-aid-${pureBookId}.html".also {
                            Util.log(logTag, "fetch next p from $it")
                        }
                    ).get()
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
}