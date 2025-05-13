package com.example.viewer.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.viewer.struct.BookRecord
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.database.SearchDatabase
import com.example.viewer.database.SearchDatabase.Companion.SearchMark
import com.example.viewer.database.SearchDatabase.Companion.Category
import com.example.viewer.databinding.ActivitySearchBookBinding
import com.example.viewer.dialog.SearchMarkDialog
import com.example.viewer.dialog.SimpleEditTextDialog
import kotlinx.coroutines.CoroutineScope
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
    @Volatile
    private var loadingMore = false
    private var position = -1
    private var next: String? = null // for load more books
    private var lastExcludeTagUpdateTime = 0L
    private var isTemporarySearch = false
    private var reseting = true
    private var doNoMoreAlerted = false

    // recycler view item metrics
    private var coverImageWidth: Int = -1
    private var coverImageHeight: Int = -1

    private val recyclerViewAdapter: BookRecyclerViewAdapter
        get() = binding.recyclerView.adapter as BookRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        coverImageWidth = (resources.displayMetrics.widthPixels - Util.dp2px(this, 80F)) / 2
        coverImageHeight = (coverImageWidth * 1.5).toInt()

        searchDataSet = SearchDatabase.getInstance(baseContext)
        allSearchMarkIds = searchDataSet.getAllSearchMarkIds()
        lastExcludeTagUpdateTime = searchDataSet.lastExcludeTagUpdateTime()

        searchMarkId = intent.getIntExtra("searchMarkId", -1)
        searchMark = searchDataSet.getSearchMark(searchMarkId)
        isTemporarySearch = searchMarkId == -1

        // search mark position
        position = allSearchMarkIds.indexOf(searchMarkId)

        binding = SearchActivityBinding.inflate(layoutInflater)

        binding.recyclerView.apply {
            visibility
            layoutManager = GridLayoutManager(context, 2)
            adapter = BookRecyclerViewAdapter()
            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (reseting) {
                        return
                    }

                    // trigger load more book
                    if (next == null) {
                        return
                    }

                    val lm = layoutManager as GridLayoutManager
                    if (lm.findLastCompletelyVisibleItemPosition() == recyclerViewAdapter.itemCount - 1) {
                        lifecycleScope.launch { loadMoreBooks() }
                    }
                }
            })
        }

        binding.searchMarkNameContainer.setOnClickListener {
            SearchMarkDialog(this, layoutInflater).apply {
                title = if (isTemporarySearch) "編輯搜尋" else "編輯搜尋標記"
                showNameField = !isTemporarySearch
                showSaveButton = true
                showSearchButton = true
                saveCb = { retSearchMark ->
                    if (isTemporarySearch) {
                        SimpleEditTextDialog(this@SearchActivity, layoutInflater).apply {
                            title = "標記這個搜尋"
                            hint = "起個名字"
                            validator = { it.trim().isNotEmpty() }
                            positiveCb = { name ->
                                val saveSearchMark = SearchMark(
                                    name = name,
                                    categories = retSearchMark.categories,
                                    keyword = retSearchMark.keyword,
                                    tags = retSearchMark.tags
                                )
                                searchMarkId = searchDataSet.addSearchMark(saveSearchMark)
                                allSearchMarkIds = searchDataSet.getAllSearchMarkIds()
                                position = allSearchMarkIds.indexOf(searchMarkId)
                                isTemporarySearch = false
                                Toast.makeText(baseContext, "已儲存", Toast.LENGTH_SHORT).show()
                                searchMark = saveSearchMark
                                lifecycleScope.launch { reset() }
                            }
                        }.show()
                    } else {
                        searchDataSet.modifySearchMark(searchMarkId, retSearchMark)
                        searchMark = retSearchMark
                        lifecycleScope.launch { reset() }
                    }
                }
                searchCb = { retSearchMark ->
                    searchMark =
                        if (isTemporarySearch)
                            SearchMark (
                                name = getString(R.string.search),
                                categories = retSearchMark.categories,
                                keyword = retSearchMark.keyword,
                                tags = retSearchMark.tags
                            )
                        else retSearchMark
                    lifecycleScope.launch { reset() }
                }
            }.show(searchMark)
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

        lifecycleScope.launch { reset() }

        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        // after paused, the exclude tags may updated, do the filter again
        searchDataSet.lastExcludeTagUpdateTime().let { newTime ->
            if (newTime != lastExcludeTagUpdateTime) {
                println("[${this::class.simpleName}.${this::onResume.name}] re-filter books")
                recyclerViewAdapter.getBooks()?.let {
                    recyclerViewAdapter.refreshBooks(excludeTagFilter(it))
                }
                lastExcludeTagUpdateTime = newTime
            }
        }
    }

    private suspend fun reset () {
        reseting = true

        next = null
        doNoMoreAlerted = false

        binding.searchMarkName.text = searchMark.name

        if (!isTemporarySearch) {
            // no need to update these button if temporary search mark
            binding.prevSearchMarkButton.visibility = if (position == 0) Button.INVISIBLE else Button.VISIBLE
            binding.nextSearchMarkButton.visibility = if (position == allSearchMarkIds.lastIndex) Button.INVISIBLE else Button.VISIBLE
        }

        recyclerViewAdapter.clear()
        if (isSearchMarkExcluded(searchMark)) {
            doNoMoreAlerted = true
            Toast.makeText(baseContext, "所有書都被濾除了", Toast.LENGTH_SHORT).show()
        } else {
            loadMoreBooks()
        }

        reseting = false
    }

    private suspend fun loadMoreBooks () {
        val mySearchId = searchMarkId

        withContext(Dispatchers.IO) {
            while (loadingMore) {
                Thread.sleep(100)
            }
        }
        if (mySearchId != searchMarkId) {
            return
        }

        loadingMore = true

        binding.searchProgressBar.wrapper.visibility = ProgressBar.VISIBLE
        val books = excludeTagFilter(fetchBooks())
        binding.searchProgressBar.wrapper.visibility = ProgressBar.GONE

        if (mySearchId == searchMarkId) {
            recyclerViewAdapter.addBooks(books)
        }
        loadingMore = false
    }

    /**
     * This method will access and change the private variable next
     */
    private suspend fun fetchBooks (): List<BookRecord> {
        val mySearchId = searchMarkId

        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(
                searchMark.url(next).also { println("[SearchActivity.fetchBooks] fetch book from\n$it") }
            ).get()
        }

        val newNext = doc.selectFirst("#unext")?.attribute("href")?.let { attr ->
            val tokens = attr.value.split("next=")
            if (tokens.size == 1) {
                if (!doNoMoreAlerted) {
                    Toast.makeText(baseContext, "沒有更多了", Toast.LENGTH_LONG).show()
                    doNoMoreAlerted = true
                }
                return@let null
            }
            return@let tokens.last().trim()
        }

        if (mySearchId != searchMarkId) {
            println("[${this::class.simpleName}.${this::fetchBooks.name}] terminated")
            return listOf()
        }
        next = newNext

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
                    throw Exception("page num is not found")
                },
                tags = mutableMapOf<String, List<String>>().apply {
                    book.select(".gl4e.glname table tr").forEach { tr ->
                        val cat = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                        set(cat, tr.select(".gt,.gtl,.gtw").map { it.text().trim() })
                    }
                }
            )
        }
    }

    private fun excludeTagFilter (books: List<BookRecord>): List<BookRecord> {
        val excludeTagEntries = searchDataSet.getAllExcludeTag()
        return books.filterNot {
            book -> excludeTagEntries.any { it.second.excluded(book) }.also {
                if (it) {
                    println("[${this::class.simpleName}.${this::excludeTagFilter.name}] ${book.id} is excluded")
                }
            }
        }
    }

    /**
     * @return if the search mark is excluded by any exclude tag record
     */
    private fun isSearchMarkExcluded (searchMark: SearchMark): Boolean {
        val excludeTagEntries = searchDataSet.getAllExcludeTag()
        return excludeTagEntries.any { it.second.excluded(searchMark) }
    }

    private suspend fun fetchDetailBookRecord (briefBookRecord: BookRecord): BookRecord {
        withContext(Dispatchers.Main) { binding.screenProgressBarWrapper.visibility = View.VISIBLE }
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(briefBookRecord.url).get()
        }
        withContext(Dispatchers.Main) { binding.screenProgressBarWrapper.visibility = View.GONE }

        return BookRecord(
            id = briefBookRecord.id,
            url = briefBookRecord.url,
            coverUrl = briefBookRecord.coverUrl,
            cat = briefBookRecord.cat,
            title = doc.selectFirst("#gj")!!.text().trim().let {
                if (it.isNotEmpty()) it else briefBookRecord.title
            },
            subtitle = briefBookRecord.title,
            pageNum = briefBookRecord.pageNum,
            tags = doc.select("#taglist tr").run {
                val tags = mutableMapOf<String, List<String>>()
                forEach { tr ->
                    val category = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                    tags[category] = tr.select(".gt,.gtl,.gtw").map { it.text().trim() }
                }
                tags
            }
        )
    }

    //
    // define recycler view adapter
    //

    inner class BookRecyclerViewAdapter: RecyclerView.Adapter<BookRecyclerViewAdapter.ViewHolder>() {
        inner class ViewHolder (val binding: ActivitySearchBookBinding): RecyclerView.ViewHolder(binding.root)

        private val bookRecords = mutableListOf<BookRecord>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ActivitySearchBookBinding.inflate(layoutInflater, parent, false)
            binding.searchBookImageView.layoutParams = binding.searchBookImageView.layoutParams.apply {
                height = coverImageHeight
                width = coverImageWidth
            }
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int = bookRecords.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val binding = holder.binding
            val bookRecord = bookRecords[position]

            binding.searchBookImageView.let {
                Glide.with(it.context).load(bookRecord.coverUrl).into(it)
            }

            binding.searchBookTitleTextView.text = bookRecord.title

            binding.pageNumTextView.apply {
                text = context.getString(R.string.n_page, bookRecord.pageNum)
            }

            binding.searchBookCatTextView.apply {
                text = bookRecord.cat
                setTextColor(context.getColor(Util.categoryFromName(bookRecord.cat).color))
            }

            binding.root.apply {
                setOnClickListener {
                    lifecycleScope.launch {
                        val intent = Intent(context, BookProfileActivity::class.java)
                        intent.putExtra(
                            "book_record",
                            withContext(Dispatchers.IO) { fetchDetailBookRecord(bookRecord) }
                        )
                        startActivity(intent)
                    }
                }
            }
        }

        fun getBooks (): List<BookRecord> = bookRecords

        fun addBooks (books: List<BookRecord>) {
            val positionStart = bookRecords.size
            bookRecords.addAll(books)
            notifyItemRangeInserted(positionStart, books.size)
        }

        fun refreshBooks (books: List<BookRecord>) {
            bookRecords.clear()
            bookRecords.addAll(books)
            notifyDataSetChanged()
        }

        fun clear () {
            val size = bookRecords.size
            bookRecords.clear()
            notifyItemRangeRemoved(0, size)
        }
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