package com.example.viewer.fetcher

import android.content.Context
import com.example.viewer.dataset.BookDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URL

class EPictureFetcher (context: Context, bookId: String): APictureFetcher(context, bookId) {
    companion object {
        private const val I3_TAG = "<div id=\"i3\">"
        private const val URL_START_TAG = "src=\""
    }

    private val bookUrl: String = BookDataset.getBookUrl(bookId)
    private var pageUrls: MutableList<String> = BookDataset.getBookPageUrls(bookId).toMutableList()

    override suspend fun savePicture(page: Int): Boolean {
        println("[EPictureFetcher.savePicture] $page")
        assertPageInRange(page)

        val url = fetchPictureUrl(page)
        return downloadPicture(page, url)
    }

    private suspend fun fetchPictureUrl (page: Int): String {
        println("[EPictureFetcher.fetchPictureUrl] $page")
        val pageUrl = getPageUrl(page)
        val pageText = coroutineScope {
            withContext(Dispatchers.IO) {
                async { URL(pageUrl).readText() }.await()
            }
        }

        val i3Idx = pageText.indexOf(I3_TAG)
        val startUrlIdx = pageText.indexOf(URL_START_TAG, i3Idx + I3_TAG.length)
        val endUrlIdx = pageText.indexOf("\"", startUrlIdx + URL_START_TAG.length)

        return pageText.substring(startUrlIdx + URL_START_TAG.length, endUrlIdx)
    }

    private suspend fun getPageUrl (page: Int): String {
        if (page > pageUrls.lastIndex) {
            println("[EPictureFetcher.getPageUrl]\nload next p")

            val p = BookDataset.getBookP(bookId)
            val html = coroutineScope {
                withContext(Dispatchers.IO) {
                    async { URL("${bookUrl}/?p=${p}").readText() }.await()
                }
            }

            val pageUrlSegment = Regex("https://e-hentai.org/s/(.*?)/${bookId}-(\\d+)").findAll(html).map { it.value }.toList()
            pageUrls.addAll(pageUrlSegment)

            BookDataset.increaseBookP(bookId)
            BookDataset.setBookPageUrls(bookId, pageUrls)
        }
        return pageUrls[page]
    }
}