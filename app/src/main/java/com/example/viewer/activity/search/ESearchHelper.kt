package com.example.viewer.activity.search

import com.example.viewer.data.struct.Book
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ESearchHelper (
    searchMarkData: SearchMarkData
): SearchHelper(searchMarkData) {
    init {
        assert(searchMarkData.sourceOrdinal == BookSource.E.ordinal)
    }

    override fun getNextBlockSearchUrl(): String = getSearchUrl(
        next = if (this.next == NOT_SET) null else this.next.toString()
    )

    override fun getPrevBlockSearchUrl(): String = getSearchUrl(
        prev = if (this.prev == NOT_SET) null else this.prev.toString()
    )

    override suspend fun fetchWebpage(webpageUrl: String): Document =
        withContext(Dispatchers.IO) {
            Jsoup.connect(webpageUrl).get()
        }

    /**
     * This method will access and change the private variable next and prev
     */
    override fun processSearchDoc (doc: Document): List<SearchBookData> {
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
            SearchBookData(
                id = url.let {
                    (if (url.last() == '/') url.dropLast(1) else url)
                        .split("/").let {
                            it[it.lastIndex - 1]
                        }
                },
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
                }
            )
        }
    }

    override suspend fun storeDetailAsTmpBook(searchBookData: SearchBookData): Boolean {
        val doc = try {
            EPictureFetcher.fetchWebpage(searchBookData.url, true)
        } catch (_: HttpStatusException) {
            return false
        }

        val gson = Gson()
        val tags = doc.select("#taglist tr").run {
            val tags = mutableMapOf<String, List<String>>()
            forEach { tr ->
                val category = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                tags[category] = tr.select(".gt,.gtl").map { it.text().trim() }
            }
            tags
        }

        Book.setTmpBook(
            id = searchBookData.id,
            url = searchBookData.url,
            title = doc.selectFirst("#gj")!!.text().trim().ifEmpty { searchBookData.title },
            subTitle = searchBookData.title,
            pageNum = searchBookData.pageNum,
            categoryOrdinal = searchBookData.cat.ordinal,
            uploader = doc.selectFirst("#gdn a")?.text(),
            tagsJson = gson.toJson(tags),
            sourceOrdinal = BookSource.E.ordinal,
            coverUrl = searchBookData.coverUrl
        )
        return true
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