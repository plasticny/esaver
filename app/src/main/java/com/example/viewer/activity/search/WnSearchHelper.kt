package com.example.viewer.activity.search

import android.net.Uri
import com.example.viewer.data.struct.Book
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.google.gson.Gson
import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

class WnSearchHelper (
    searchMarkData: SearchMarkData
): SearchHelper(searchMarkData) {
    private val isKeywordSearching: Boolean
    private val category: Category

    init {
        assert(searchMarkData.sourceOrdinal == BookSource.Wn.ordinal)
        next = 1
        prev = ENDED
        category = searchMarkData.categories.first()
        isKeywordSearching = category == Category.All && searchMarkData.keyword.isNotEmpty()
        searchResultString = "N/A"
    }

    override fun getNextBlockSearchUrl (): String {
        assert(next != NOT_SET && hasNextBlock)
        return createSearchUrl(next)
    }

    override fun getPrevBlockSearchUrl (): String {
        assert(prev != NOT_SET && hasPrevBlock)
        return createSearchUrl(prev)
    }

    override suspend fun fetchWebpage(webpageUrl: String): Document =
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

    override fun processSearchDoc (doc: Document): List<SearchBookData> {
        // update next
        if (isKeywordSearching) {
            val elThisPage = doc.getElementsByClass("thispage").first()
            if (elThisPage == null || elThisPage.text() == next.toString()) {
                next = ENDED
            } else {
                next++
            }
        } else {
            if (doc.getElementsByClass("next").isNotEmpty()) {
                next++
            } else {
                next = ENDED
            }
        }


        return doc.getElementsByClass("gallary_item").mapNotNull { galleryItem ->
            val bookUrl = galleryItem.getElementsByTag("a").first()!!.attr("href")
            SearchBookData(
                id = bookUrl.slice(18..bookUrl.length - 6),
                url = "https://www.wnacg.com$bookUrl",
                coverUrl = "https:${galleryItem.getElementsByTag("img").first()!!.attr("src")}",
                cat = if (searchMarkData.sourceOrdinal != Category.All.ordinal) {
                    try {
                        cateClassToCategory(
                            galleryItem.getElementsByClass("pic_box").first()!!.classNames().last()
                        )
                    } catch (_: ExcludedCategoryError) {
                        return@mapNotNull null
                    }
                } else {
                    Category.fromOrdinal(searchMarkData.sourceOrdinal)
                },
                title = galleryItem.getElementsByClass("title").first()!!.text(),
                pageNum = galleryItem.getElementsByClass("info_col").first()!!.text()
                    .trim().split("張").first().toInt(),
                tags = mapOf(),
                rating = null
            )
        }
    }

    override suspend fun storeDetailAsTmpBook (searchBookData: SearchBookData): Boolean {
        val doc = withContext(Dispatchers.IO) {
            fetchWebpage(searchBookData.url)
        }

        val gson = Gson()

        val tags: Map<String, List<String>> = mapOf(
            "標籤" to doc.select(".addtags > .tagshow").map { it.text() }
        )

        Book.setTmpBook(
            id = searchBookData.id,
            url = searchBookData.url,
            title = searchBookData.title,
            subTitle = "",
            pageNum = searchBookData.pageNum,
            categoryOrdinal = searchBookData.cat.ordinal,
            uploader = doc.selectFirst(".uwuinfo p")?.text(),
            tagsJson = gson.toJson(tags),
            sourceOrdinal = BookSource.Wn.ordinal,
            coverUrl = searchBookData.coverUrl
        )

        return true
    }

    private fun createSearchUrl (searchPageNumber: Int): String {
        return if (isKeywordSearching) {
            "https://www.wnacg.com/search/?q=${Uri.encode(searchMarkData.keyword)}&syn=yes&f=_all&s=create_time_DESC&p=${searchPageNumber}"
        } else if (category == Category.All) {
            "https://www.wnacg.com/albums-index-page-${searchPageNumber}.html"
        } else {
            val cate = when (category) {
                Category.Doujinshi -> 5
                Category.Manga -> 6
                Category.Magazine -> 7
                else -> throw IllegalStateException()
            }
            "https://www.wnacg.com/albums-index-page-${searchPageNumber}-cate-${cate}.html"
        }
    }

    private fun cateClassToCategory (cateClass: String): Category {
        val cateIndex = cateClass.slice(5 until cateClass.length)
        return when (cateIndex.toInt()) {
            1, 2, 12, 16, 37 -> Category.Doujinshi
            3 -> Category.Cosplay
            9, 13 -> Category.Manga
            7, 10, 14, 17 -> Category.Magazine
            20, 23 -> throw ExcludedCategoryError(cateIndex)
            else -> throw NotImplementedError(cateIndex)
        }
    }

    class ExcludedCategoryError (cateIndex: String): Exception(cateIndex)
}