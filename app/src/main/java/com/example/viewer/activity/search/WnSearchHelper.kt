package com.example.viewer.activity.search

import com.example.viewer.data.struct.Book
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class WnSearchHelper (
    searchMarkData: SearchMarkData
): SearchHelper(searchMarkData) {
    init {
        assert(searchMarkData.sourceOrdinal == BookSource.Wn.ordinal)
        next = 1
        prev = ENDED
    }

    override fun getNextBlockSearchUrl (): String {
        assert(next != NOT_SET && hasNextBlock)
        return createSearchUrl(next)
    }

    override fun getPrevBlockSearchUrl (): String {
        assert(prev != NOT_SET && hasPrevBlock)
        return createSearchUrl(prev)
    }

    override fun processSearchDoc (doc: Document): List<SearchBookData> {
        // update next
        if (doc.getElementsByClass("next").isNotEmpty()) {
            next++
        } else {
            next = ENDED
        }

        return doc.getElementsByClass("gallary_item").map { galleryItem ->
            val bookUrl = galleryItem.getElementsByTag("a").first()!!.attr("href")
            SearchBookData(
                id = "wn${bookUrl.slice(18..bookUrl.length - 6)}",
                url = "https://www.wnacg.com$bookUrl",
                coverUrl = "https:${galleryItem.getElementsByTag("img").first()!!.attr("src")}",
                cat = if (searchMarkData.sourceOrdinal != Category.All.ordinal) {
                    cateClassToCategory(
                        galleryItem.getElementsByClass("pic_box").first()!!.classNames().last()
                    )
                } else {
                    Category.fromOrdinal(searchMarkData.sourceOrdinal)
                },
                title = galleryItem.getElementsByClass("title").first()!!.text(),
                pageNum = galleryItem.getElementsByClass("info_col").first()!!.text()
                    .trim().split("張照片").first().toInt(),
                tags = mapOf()
            )
        }
    }

    override suspend fun storeDetailAsTmpBook (searchBookData: SearchBookData): Boolean {
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(searchBookData.url).get()
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
        val category = searchMarkData.categories.first()
        return if (category == Category.All) {
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
            1, 2, 12, 16 -> Category.Doujinshi
            3 -> Category.Cosplay
            9, 13 -> Category.Manga
            10, 14 -> Category.Magazine
            else -> throw NotImplementedError(cateIndex)
        }
    }
}