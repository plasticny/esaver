package com.example.viewer.activity.search

import android.content.Context
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemType
import com.example.viewer.struct.ProfileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ESearchHelper (
    context: Context,
    searchMarkData: SearchMarkData
): SearchHelper(context, searchMarkData) {
    init {
        assert(searchMarkData.sourceOrdinal == ItemSource.E.ordinal)
    }

    override fun getNextBlockSearchUrl(): String = getSearchUrl(
        next = if (this.next == NOT_SET) null else this.next.toString()
    )

    override fun getPrevBlockSearchUrl(): String = getSearchUrl(
        prev = if (this.prev == NOT_SET) null else this.prev.toString()
    )

    override suspend fun fetchItems(searchUrl: String, isSearchMarkChanged: () -> Boolean): List<SearchItemData>? {
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(searchUrl).get()
        }
        return if (isSearchMarkChanged()) null else processSearchDoc(doc)
    }

    override suspend fun storeDetailAsTmpProfileItem(searchItemData: SearchItemData): Boolean {
        val doc = try {
            EPictureFetcher.fetchWebpage(searchItemData.url, true)
        } catch (_: HttpStatusException) {
            return false
        }

        val tags = doc.select("#taglist tr").run {
            val tags = mutableMapOf<String, List<String>>()
            forEach { tr ->
                val category = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                tags[category] = tr.select(".gt,.gtl").map { it.text().trim() }
            }
            tags
        }

        ProfileItem.setTmp(ProfileItem(
            id = -1,
            url = searchItemData.url,
            title = doc.selectFirst("#gj")!!.text().trim().ifEmpty { searchItemData.title },
            subTitle = searchItemData.title,
            customTitle = null,
            tags = tags,
            excludedTags = ExcludeTagRepository(context).findExcludedTags(
                tags, searchItemData.cat
            ),
            source = ItemSource.E,
            type = ItemType.Book,
            category = searchItemData.cat,
            coverPage = 0,
            coverUrl = searchItemData.coverUrl,
            coverCropPosition = null,
            uploader = doc.selectFirst("#gdn a")?.text(),
            isTmp = true,
            bookData = ProfileItem.BookData(
                id = searchItemData.bookId!!,
                pageNum = searchItemData.pageNum
            )
        ))
        return true
    }

    /**
     * This method will access and change the private variable next and prev
     */
    private fun processSearchDoc (doc: Document): List<SearchItemData> {
        // update next and prev
        if (next != ENDED) {
            next = doc.selectFirst("#unext")?.attribute("href")?.let {
                val n = it.value.split("next=").last().trim().toInt()
                if (next == NOT_SET || n < next) n else next
            } ?: ENDED
        }
        if (prev != ENDED) {
            prev = doc.selectFirst("#uprev")?.attribute("href")?.let {
                val p = it.value.split("prev=").last().trim().toInt()
                if (prev == NOT_SET || p > prev) p else prev
            } ?: ENDED
        }

        if (searchResultString == null) {
            searchResultString = doc.selectFirst(".searchtext")?.run { text().trim() } ?: "沒有搜尋結果"
        }

        val books = doc.select(".itg.glte > tbody > tr")
        return books.mapNotNull { book ->
            if (book.select(".itd").isNotEmpty()) {
                return@mapNotNull null
            }

            val url = book.selectFirst(".gl1e a")!!.attr("href")
            SearchItemData(
                url = url,
                coverUrl = book.selectFirst(".gl1e img")!!.attr("src"),
                cat = Category.fromName(book.selectFirst(".cn")!!.text()),
                title = book.selectFirst(".glink")!!.text(),
                pageNum = book.select(".gl3e div").let { divs ->
                    for (div in divs.reversed()) {
                        val text = div.text()
                        if (text.endsWith(" pages") || text.endsWith(" page")) {
                            return@let text.trim().split(' ').first().toInt()
                        }
                    }
                    throw Exception("page num is not found")
                },
                tags = mutableMapOf<String, List<String>>().apply {
                    book.select(".gl4e.glname table tr").forEach { tr ->
                        val cat = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                        set(cat, tr.select(".gt,.gtl").map { it.text().trim() })
                    }
                },
                rating = book.selectFirst(".ir")!!.attr("style").split(';').let {
                    for (item in it) {
                        val itemTokens = item.split(':')
                        if (itemTokens[0].trim() == "background-position") {
                            // value example: "-48px -1px"
                            val valueTokens = itemTokens[1].split("px")
                            val x = valueTokens[0].trim().toInt()
                            val y = valueTokens[1].trim().toInt()
                            var rating = 5 + x / 16f
                            if (y != -1) {
                                rating -= 0.5f
                            }
                            return@let rating
                        }
                    }
                    throw IllegalStateException()
                },
                bookId = url.let {
                    (if (url.last() == '/') url.dropLast(1) else url)
                        .split("/").let {
                            it[it.lastIndex - 1]
                        }
                }
            )
        }
    }

    private fun getSearchUrl (next: String? = null, prev: String? = null): String {
        assert(next == null || prev == null)

        val fCatsValue = 1023 - if (searchMarkData.categories.isNotEmpty()) {
            searchMarkData.categories.sumOf {
                assert(it.value != -1) { it.name }
                it.value
            }
        } else {
            Category.ECategories.sumOf { it.value }
        }

        // f search
        var fSearch = ""
        if (searchMarkData.keyword.isNotEmpty()) {
            fSearch += "${searchMarkData.keyword}+"
        }
        if (searchMarkData.tags.isNotEmpty() || searchMarkData.uploader?.isNotEmpty() == true) {
            val tokens = mutableListOf<String>()
            searchMarkData.tags.forEach { entry ->
                val cat = entry.key
                for (value in entry.value) {
                    tokens.add(buildTagValueString(cat, value))
                }
            }
            if (searchMarkData.uploader?.isNotEmpty() == true) {
                tokens.add(buildTagValueString("uploader", searchMarkData.uploader))
            }
            fSearch += tokens.joinToString(" ")
        }

        var ret = "https://e-hentai.org/?f_cats=$fCatsValue&"
        if (fSearch.isNotEmpty()) {
            ret += "f_search=$fSearch&"
        }
        ret += "inline_set=dm_e&"
        next?.let { ret += "next=$next" }
        prev?.let { ret += "prev=$prev" }

        return ret
    }

    private fun buildTagValueString (category: String, value: String): String {
        return if (value.contains(' ')) {
            "${category}%3A\"${value}%24\""
        } else {
            "${category}%3A${value}%24"
        }
    }
}