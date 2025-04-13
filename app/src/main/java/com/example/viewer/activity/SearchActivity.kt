package com.example.viewer.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.viewer.BookRecord
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.databinding.SearchBookBinding
import com.example.viewer.database.SearchDatabase
import com.example.viewer.database.SearchDatabase.Companion.SearchMark
import com.example.viewer.database.SearchDatabase.Companion.Category
import com.example.viewer.dialog.PositiveButtonStyle
import com.example.viewer.dialog.SearchMarkDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * intExtra: searchMarkId; -1 for temporary search mark
 */
class SearchActivity: AppCompatActivity() {
    companion object {
        /**
         * @param context context of an activity
         */
        fun startTmpSearch (
            context: Context,
            categories: List<Category> = Category.entries.toList(),
            keyword: String = "",
            tags: Map<String, List<String>> = mapOf()
        ) {
            SearchDatabase.getInstance(context).setTmpSearchMark(
                SearchMark(
                    name = context.getString(R.string.search),
                    categories, keyword, tags
                )
            )
            context.startActivity(
                Intent(context, SearchActivity::class.java).apply {
                    putExtra("searchMarkId", SearchDatabase.TEMP_SEARCH_MARK_ID)
                }
            )
        }
    }

    private lateinit var searchDataSet: SearchDatabase
    private lateinit var searchMark: SearchMark
    private lateinit var binding: SearchActivityBinding
    private lateinit var allSearchMarkIds: List<Int>

    @Volatile
    private var searchMarkId = -1
    private var position = -1
    private var next: String? = null // for load more books
    private var bookRecords = mutableListOf<BookRecord>()
    private var lastExcludeTagUpdateTime = 0L
    private var isTemporarySearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchDataSet = SearchDatabase.getInstance(baseContext)
        allSearchMarkIds = searchDataSet.getAllSearchMarkIds()
        lastExcludeTagUpdateTime = searchDataSet.lastExcludeTagUpdateTime()

        searchMarkId = intent.getIntExtra("searchMarkId", -1)
        searchMark = searchDataSet.getSearchMark(searchMarkId)
        isTemporarySearch = searchMarkId == -1

        // search mark position
        position = allSearchMarkIds.indexOf(searchMarkId)

        binding = SearchActivityBinding.inflate(layoutInflater)

        binding.searchMarkNameContainer.setOnClickListener {
            SearchMarkDialog(this, layoutInflater).show(
                title = "編輯搜尋",
                searchMark = if (isTemporarySearch) SearchMark(
                    name = "",
                    categories = searchMark.categories,
                    keyword = searchMark.keyword,
                    tags = searchMark.tags
                ) else searchMark,
                positiveButtonStyle = PositiveButtonStyle.SAVE
            ) { retSearchMark ->
                if (isTemporarySearch) {
                    searchMarkId = searchDataSet.addSearchMark(retSearchMark)
                    allSearchMarkIds = searchDataSet.getAllSearchMarkIds()
                    position = allSearchMarkIds.indexOf(searchMarkId)
                    isTemporarySearch = false
                    Toast.makeText(baseContext, "已儲存", Toast.LENGTH_SHORT).show()
                } else {
                    searchDataSet.modifySearchMark(searchMarkId, retSearchMark)
                }

                searchMark = retSearchMark
                lifecycleScope.launch { reset() }
            }
        }

        binding.prevSearchMarkButton.apply {
            if (isTemporarySearch) {
                visibility = View.INVISIBLE
            }
            setOnClickListener {
                if (position == 0) {
                    return@setOnClickListener
                }
                val id = allSearchMarkIds[--position]
                searchMark = searchDataSet.getSearchMark(id)
                searchMarkId = id
                lifecycleScope.launch { reset() }
            }
        }
        binding.nextSearchMarkButton.apply {
            if (isTemporarySearch) {
                visibility = View.INVISIBLE
            }
            setOnClickListener {
                if (position == allSearchMarkIds.lastIndex) {
                    return@setOnClickListener
                }
                val id = allSearchMarkIds[++position]
                searchMark = searchDataSet.getSearchMark(id)
                searchMarkId = id
                lifecycleScope.launch { reset() }
            }
        }

        binding.loadMoreButton.apply {
            setOnClickListener {
                lifecycleScope.launch { loadMoreBooks() }
            }
        }

        lifecycleScope.launch { reset() }

        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        // after paused, the exclude tags may updated, do the filter again
        searchDataSet.lastExcludeTagUpdateTime().let { newTime ->
            if (newTime != lastExcludeTagUpdateTime) {
                println("[${this::class.simpleName}.${this::onResume.name}] re-filter books")
                binding.searchBookWrapper.removeAllViews()
                bookRecords = excludeTagFilter(bookRecords).toMutableList()
                addBookViews(bookRecords)
                lastExcludeTagUpdateTime = newTime
            }
        }
    }

    private suspend fun reset () {
        next = null
        bookRecords.clear()

        binding.searchMarkName.text = searchMark.name

        if (!isTemporarySearch) {
            // no need to update these button if temporary search mark
            binding.prevSearchMarkButton.visibility = if (position == 0) Button.INVISIBLE else Button.VISIBLE
            binding.nextSearchMarkButton.visibility = if (position == allSearchMarkIds.lastIndex) Button.INVISIBLE else Button.VISIBLE
        }

        binding.searchBookWrapper.removeAllViews()

        // hide these views while loading
        binding.loadMoreButton.visibility = View.INVISIBLE
        binding.noMoreTextView.visibility = View.INVISIBLE

        loadMoreBooks()
    }

    private suspend fun loadMoreBooks () {
        val mySearchId = searchMarkId

        binding.searchProgressBar.visibility = ProgressBar.VISIBLE
        val books = excludeTagFilter(fetchBooks())
        binding.searchProgressBar.visibility = ProgressBar.GONE

        // if the search mark changed, terminate this function
        if (mySearchId != searchMarkId) {
            return
        }

        addBookViews(books)
        bookRecords.addAll(books)

        // change visibility of load more button and no more text
        if (next != null) {
            binding.loadMoreButton.visibility = View.VISIBLE
            binding.noMoreTextView.visibility = View.GONE
        } else {
            binding.loadMoreButton.visibility = View.GONE
            binding.noMoreTextView.visibility = View.VISIBLE
        }
    }

    private fun addBookViews (books: List<BookRecord>) {
        books.forEach { bookRecord ->
            binding.searchBookWrapper.addView(
                SearchBookBinding.inflate(layoutInflater, binding.searchBookWrapper, false).apply {
                    Glide.with(this.root).load(bookRecord.coverUrl).into(searchBookImageView)
                    searchBookTitleTextView.text = bookRecord.title
                    pageNumTextView.text = baseContext.getString(R.string.n_page, bookRecord.pageNum)
                    searchBookCatTextView.apply {
                        text = bookRecord.cat
                        setTextColor(context.getColor(Util.categoryFromName(bookRecord.cat).color))
                    }
                    root.setOnClickListener {
                        val intent = Intent(baseContext, BookProfileActivity::class.java)
                        intent.putExtra("book_record", bookRecord)
                        startActivity(intent)
                    }
                }.root
            )
        }
    }

    /**
     * This method will access and change the private variable next
     */
    private suspend fun fetchBooks (): List<BookRecord> {
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(
                searchMark.url(next).also { println("[SearchActivity.fetchBooks] $it") }
            ).get()
        }

        next = doc.selectFirst("#unext")?.attribute("href")?.let { attr ->
            val tokens = attr.value.split("next=")
            if (tokens.size == 1) {
                return@let null
            }
            return@let tokens.last().trim()
        }

        val books = doc.select(".itg.glte > tbody > tr")
        return books.mapNotNull { book ->
            if (book.select(".itd").isNotEmpty()) {
                return@mapNotNull null
            }

            val url = book.selectFirst(".gl1e a")!!.attr("href")
            BookRecord(
                id = url.let {
                    (if (url.last() == '/') url.dropLast(1) else url)
                        .split("/").let {
                            it[it.lastIndex - 1]
                        }
                },
                url = url,
                coverUrl = book.selectFirst(".gl1e img")!!.attr("src"),
                cat = book.selectFirst(".cn")!!.text(),
                title = book.selectFirst(".glink")!!.text(),
                pageNum = book.select(".gl3e div").let { divs ->
                    for (div in divs.reversed()) {
                        val text = div.text()
                        if (text.endsWith(" pages") || text.endsWith(" page")) {
                            return@let text.trim().split(' ').first().toInt()
                        }
                    }
                    println(url)
                    throw Exception("page num is not found")
                },
                tags = mutableMapOf<String, List<String>>().apply {
                    book.select(".gl4e.glname table tr").forEach { tr ->
                        val cat = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                        set(cat, tr.select(".gt,.gtl").map { it.text().trim() })
                    }
                }
            )
        }
    }

    private fun SearchMark.url (next: String?): String {
        val fCatsValue = if (categories.isNotEmpty()) {
            1023 - categories.sumOf { it.value }
        } else null

        // f search
        var fSearch = ""
        if (keyword.isNotEmpty()) {
            fSearch += "$keyword+"
        }
        if (tags.isNotEmpty()) {
            val tokens = mutableListOf<String>()
            tags.forEach { entry ->
                val cat = entry.key
                for (value in entry.value) {
                    if (value.contains(' ')) {
                        tokens.add("${cat}%3A\"${value}%24\"")
                    } else {
                        tokens.add("${cat}%3A${value}%24")
                    }
                }
            }
            fSearch += tokens.joinToString(" ")
        }

        var ret = "https://e-hentai.org/"
        if (fCatsValue != null || fSearch.isNotEmpty()) {
            ret += "?"
        }
        fCatsValue?.let { ret += "f_cats=$it&" }
        if (fSearch.isNotEmpty()) {
            ret += "f_search=$fSearch&"
        }
        ret += "inline_set=dm_e&"
        next?.let { ret += "next=$next" }

        return ret
    }

    private fun excludeTagFilter (books: List<BookRecord>): List<BookRecord> {
        val excludeTags = searchDataSet.getExcludeTag()
        return books.filter { book ->
            for (entry in book.tags) {
                val cat = entry.key
                if (!excludeTags.containsKey(cat)) {
                    continue
                }
                if (excludeTags.getValue(cat).intersect(entry.value.toSet()).isNotEmpty()) {
                    return@filter false
                }
            }
            true
        }
    }
}