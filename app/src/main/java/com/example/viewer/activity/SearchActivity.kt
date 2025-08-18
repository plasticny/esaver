package com.example.viewer.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.data.repository.SearchRepository
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.databinding.ActivitySearchBookBinding
import com.example.viewer.databinding.DialogSearchInfoBinding
import com.example.viewer.dialog.SearchMarkDialog
import com.example.viewer.dialog.SimpleEditTextDialog
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.reflect.jvm.internal.impl.serialization.deserialization.EnumEntriesDeserializationSupport

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
            sourceOrdinal: Int,
            categories: List<Category> = Category.entries.toList(),
            keyword: String = "",
            tags: Map<String, List<String>> = mapOf(),
            uploader: String = "",
            doExclude: Boolean = true
        ) {
            SearchMark.setTmpSearchMark(
                context,
                sourceOrdinal, categories, keyword, tags, uploader, doExclude
            )
            context.startActivity(
                Intent(context, SearchActivity::class.java).apply {
                    putExtra("searchMarkId", -1L)
                }
            )
        }

        private const val NO_SET = -1
        private const val ENDED = -2
    }

    private lateinit var searchRepo: SearchRepository
    private lateinit var excludeTagRepo: ExcludeTagRepository
    private lateinit var searchMarkData: SearchMarkData
    private lateinit var rootBinding: SearchActivityBinding
    private lateinit var allSearchMarkIds: List<Long>

    @Volatile
    private var loadingMore = false
    private var position = -1
    private var lastExcludeTagUpdateTime = 0L
    private var isTemporarySearch = false
    private var resetting = true

    private var foundResultString: String = ""
    private var totalBookLoaded = -1
    private var totalBookFiltered = -1
    private var lastNextHistory: String? = null

    // for load more books
    private var next: Int = NO_SET
    private var prev: Int = NO_SET

    // recycler view item metrics
    private var coverImageWidth: Int = -1
    private var coverImageHeight: Int = -1

    private val recyclerViewAdapter: BookRecyclerViewAdapter
        get() = rootBinding.recyclerView.adapter as BookRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        coverImageWidth = (resources.displayMetrics.widthPixels - Util.dp2px(this, 80F)) / 2
        coverImageHeight = (coverImageWidth * 1.5).toInt()

        searchRepo = SearchRepository(baseContext)
        excludeTagRepo = ExcludeTagRepository(baseContext)
        allSearchMarkIds = searchRepo.getAllSearchMarkIdsInOrder()
        lastExcludeTagUpdateTime = excludeTagRepo.lastExcludeTagUpdateTime()

        searchMarkData = packSearchMark(
            intent.getLongExtra("searchMarkId", -1L).let {
                if (it == -1L) SearchMark.getTmpSearchMark() else searchRepo.getSearchMark(it)
            }
        )
        isTemporarySearch = searchMarkData.id == -1L

        // search mark position
        position = allSearchMarkIds.indexOf(searchMarkData.id)

        rootBinding = SearchActivityBinding.inflate(layoutInflater)

        rootBinding.recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = BookRecyclerViewAdapter()
            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                private val lm = layoutManager as GridLayoutManager
                private var loadingTriggered = false

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    if (loadingTriggered || loadingMore || newState != 1) {
                        return
                    }

                    if (next != ENDED && lm.findLastCompletelyVisibleItemPosition() == recyclerViewAdapter.itemCount - 1) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runBlocking {
                                loadingTriggered = true
                                loadNextBooks()
                                loadingTriggered = false
                            }
                        }
                    }
                    else if (prev != ENDED && lm.findFirstCompletelyVisibleItemPosition() == 0) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runBlocking {
                                loadingTriggered = true
                                loadPrevBooks()
                                loadingTriggered = false
                            }
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (loadingTriggered || resetting || loadingMore) {
                        return
                    }

                    if (
                        next != ENDED && dy > 0 &&
                        lm.findLastCompletelyVisibleItemPosition() == recyclerViewAdapter.itemCount - 1
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runBlocking {
                                loadingTriggered = true
                                loadNextBooks()
                                loadingTriggered = false
                            }
                        }
                    }
                    else if (
                        prev != ENDED && dy < 0 &&
                        lm.findFirstCompletelyVisibleItemPosition() == 0
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runBlocking {
                                loadingTriggered = true
                                loadPrevBooks()
                                loadingTriggered = false
                            }
                        }
                    }
                }
            })
        }

        rootBinding.searchMarkNameContainer.setOnClickListener {
            SearchMarkDialog(this, layoutInflater).apply {
                title = if (isTemporarySearch) "編輯搜尋" else "編輯搜尋標記"
                showNameField = !isTemporarySearch
                showSaveButton = true
                showSearchButton = true
                saveCb = { data ->
                    // save tmp search
                    if (isTemporarySearch) {
                        SimpleEditTextDialog(this@SearchActivity, layoutInflater).apply {
                            title = "標記這個搜尋"
                            hint = "起個名字"
                            validator = { it.trim().isNotEmpty() }
                            positiveCb = { name ->
                                val id = searchRepo.addSearchMark(
                                    name = name,
                                    sourceOrdinal = data.sourceOrdinal,
                                    categories = data.categories.toList(),
                                    keyword = data.keyword,
                                    tags = data.tags,
                                    uploader = data.uploader,
                                    doExclude = data.doExclude
                                )
                                Toast.makeText(baseContext, "已儲存", Toast.LENGTH_SHORT).show()

                                allSearchMarkIds = searchRepo.getAllSearchMarkIdsInOrder()
                                position = allSearchMarkIds.indexOf(id)
                                isTemporarySearch = false
                                searchMarkData = packSearchMark(searchRepo.getSearchMark(id))
                                lifecycleScope.launch { reset() }
                            }
                        }.show()
                    }
                    // save modification
                    else {
                        searchRepo.modifySearchMark(
                            searchMarkData.id,
                            name = data.name,
                            categories = data.categories.toList(),
                            keyword = data.keyword,
                            tags = data.tags,
                            uploader = data.uploader,
                            doExclude = data.doExclude
                        )
                        searchMarkData = packSearchMark(searchRepo.getSearchMark(searchMarkData.id))
                        lifecycleScope.launch { reset() }
                    }
                }
                searchCb = { data ->
                    searchMarkData = SearchMarkData(
                        id = searchMarkData.id,
                        name = if (isTemporarySearch) getString(R.string.search) else data.name,
                        sourceOrdinal = data.sourceOrdinal,
                        keyword = data.keyword,
                        categories = data.categories.toList(),
                        tags = data.tags,
                        uploader = data.uploader,
                        doExclude = data.doExclude
                    )
                    lifecycleScope.launch { reset() }
                }
            }.show(
                name = searchMarkData.name,
                sourceOrdinal = searchMarkData.sourceOrdinal,
                categories = searchMarkData.categories,
                keyword = searchMarkData.keyword,
                tags = searchMarkData.tags,
                uploader = searchMarkData.uploader ?: "",
                doExclude = searchMarkData.doExclude
            )
        }

        rootBinding.prevSearchMarkButton.apply {
            if (isTemporarySearch) {
                visibility = View.INVISIBLE
            }
            setOnClickListener {
                if (position == 0) {
                    return@setOnClickListener
                }
                val id = allSearchMarkIds[--position]
                searchMarkData = packSearchMark(searchRepo.getSearchMark(id))
                lifecycleScope.launch { reset() }
            }
        }
        rootBinding.nextSearchMarkButton.apply {
            if (isTemporarySearch) {
                visibility = View.INVISIBLE
            }
            setOnClickListener {
                if (position == allSearchMarkIds.lastIndex) {
                    return@setOnClickListener
                }
                val id = allSearchMarkIds[++position]
                searchMarkData = packSearchMark(searchRepo.getSearchMark(id))
                lifecycleScope.launch { reset() }
            }
        }
        rootBinding.infoButton.setOnClickListener {
            if (!resetting) {
                showInfoDialog()
            }
        }

        lifecycleScope.launch { reset() }

        setContentView(rootBinding.root)
    }

    override fun onResume() {
        super.onResume()
        // after paused, the exclude tags may updated, do the filter again
        excludeTagRepo.lastExcludeTagUpdateTime().let { newTime ->
            if (newTime != lastExcludeTagUpdateTime) {
                println("[${this::class.simpleName}.${this::onResume.name}] re-filter books")
                recyclerViewAdapter.getBooks().let {
                    recyclerViewAdapter.refreshBooks(
                        if (searchMarkData.doExclude) {
                            excludeTagFilter(it)
                        } else it
                    )
                }
                lastExcludeTagUpdateTime = newTime
            }
        }
    }

    private fun packSearchMark (searchMark: SearchMark) =
        SearchMarkData (
            id = searchMark.id,
            name = searchMark.name,
            sourceOrdinal = searchMark.sourceOrdinal,
            keyword = searchMark.keyword,
            categories = Util.readListFromJson<Int>(searchMark.categoryOrdinalsJson)
                .map { Category.fromOrdinal(it) },
            tags = Util.readMapFromJson(searchMark.tagsJson),
            uploader = searchMark.uploader,
            doExclude = searchMark.doExclude
        )

    private suspend fun reset () {
        resetting = true

        next = NO_SET
        totalBookLoaded = 0
        totalBookFiltered = 0
        lastNextHistory = searchRepo.getLastNext(searchMarkData.id)

        rootBinding.searchMarkName.text = searchMarkData.name

        if (!isTemporarySearch) {
            // no need to update these button if temporary search mark
            rootBinding.prevSearchMarkButton.visibility = if (position == 0) Button.INVISIBLE else Button.VISIBLE
            rootBinding.nextSearchMarkButton.visibility = if (position == allSearchMarkIds.lastIndex) Button.INVISIBLE else Button.VISIBLE
        }

        recyclerViewAdapter.clear()

        if (excludeTagRepo.doExclude(searchMarkData.categories, searchMarkData.tags)) {
            Toast.makeText(baseContext, "所有書都被濾除了", Toast.LENGTH_SHORT).show()
        } else {
            loadNextBooks()
        }

        resetting = false
    }

    private suspend fun loadNextBooks () {
        if (this.next == ENDED) {
            throw IllegalStateException("next equal to ENDED")
        }

        val mySearchId = searchMarkData.id

        withContext(Dispatchers.IO) {
            while (loadingMore) {
                Thread.sleep(100)
            }
        }
        if (mySearchId != searchMarkData.id) {
            return
        }

        loadingMore = true
        withContext(Dispatchers.Main) {
            rootBinding.searchProgressBar.wrapper.visibility = ProgressBar.VISIBLE
        }
        val books = fetchNextBooks()
        withContext(Dispatchers.Main) {
            rootBinding.searchProgressBar.wrapper.visibility = ProgressBar.GONE
        }
        loadingMore = false

        if (mySearchId == searchMarkData.id) {
            recyclerViewAdapter.addNextBooks(books)
        }
    }

    private suspend fun loadPrevBooks () {
        if (this.prev == ENDED) {
            throw IllegalStateException("prev equal to ENDED")
        }

        val mySearchId = searchMarkData.id

        withContext(Dispatchers.IO) {
            while (loadingMore) {
                Thread.sleep(100)
            }
        }
        if (mySearchId != searchMarkData.id) {
            return
        }

        loadingMore = true
        withContext(Dispatchers.Main) {
            rootBinding.searchProgressBar.wrapper.visibility = ProgressBar.VISIBLE
        }
        val books = fetchPrevBooks()
        withContext(Dispatchers.Main) {
            rootBinding.searchProgressBar.wrapper.visibility = ProgressBar.GONE
        }
        loadingMore = false

        if (mySearchId == searchMarkData.id) {
            recyclerViewAdapter.addPrevBooks(books)
        }
    }

    private suspend fun fetchNextBooks (): List<SearchBookData> {
        if (this.next == ENDED) {
            return listOf()
        }

        val mySearchId = searchMarkData.id
        var books = listOf<SearchBookData>()

        do {
            val next = if (this.next == NO_SET) null else this.next

            searchRepo.storeLastNext(searchMarkData.id, next?.toString())
            val doc = withContext(Dispatchers.IO) {
                Jsoup.connect(
                    searchMarkData.getSearchUrl(next = next?.toString()).also { println("[SearchActivity.fetchBooks] fetch book from\n$it") }
                ).get()
            }

            if (mySearchId != searchMarkData.id) {
                break
            }

            val fetchedBooks = processFetchedBooks(doc).also { totalBookLoaded += it.size }
            books = if (searchMarkData.doExclude) {
                excludeTagFilter(fetchedBooks).also {
                    totalBookFiltered += (fetchedBooks.size - it.size)
                }
            } else fetchedBooks
        } while (books.isEmpty() && this.next != ENDED && mySearchId == searchMarkData.id)

        return books
    }

    private suspend fun fetchPrevBooks (): List<SearchBookData> {
        if (this.prev == ENDED) {
            return listOf()
        }

        val mySearchId = searchMarkData.id
        var books = listOf<SearchBookData>()

        do {
            val prev = if (this.prev == NO_SET) null else this.prev
            val doc = withContext(Dispatchers.IO) {
                Jsoup.connect(
                    searchMarkData.getSearchUrl(prev = prev.toString()).also { println("[SearchActivity.fetchBooks] fetch book from\n$it") }
                ).get()
            }

            if (mySearchId != searchMarkData.id) {
                break
            }

            val fetchedBooks = processFetchedBooks(doc).also { totalBookLoaded += it.size }
            books = if (searchMarkData.doExclude) {
                excludeTagFilter(fetchedBooks).also {
                    totalBookFiltered += (fetchedBooks.size - it.size)
                }
            } else fetchedBooks
        } while (books.isEmpty() && this.prev != ENDED && mySearchId == searchMarkData.id)

        return books
    }

    /**
     * This method will access and change the private variable next and prev
     */
    private fun processFetchedBooks (doc: Document): List<SearchBookData> {
        if (this.next != ENDED) {
            this.next = doc.selectFirst("#unext")?.attribute("href")?.let {
                val n = it.value.split("next=").last().trim().toInt()
                if (this.next == NO_SET || n < this.next) n else this.next
            } ?: ENDED
        }
        if (this.prev != ENDED) {
            this.prev = doc.selectFirst("#uprev")?.attribute("href")?.let {
                val p = it.value.split("prev=").last().trim().toInt()
                if (this.prev == NO_SET || p > this.prev) p else this.prev
            } ?: ENDED
        }
        foundResultString = doc.selectFirst(".searchtext")?.run { text().trim() } ?: "沒有搜尋結果"

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
                        set(cat, tr.select(".gt,.gtl").map { it.text().trim() })
                    }
                }
            )
        }
    }

    private fun excludeTagFilter (books: List<SearchBookData>): List<SearchBookData> =
        books.filterNot {
            excludeTagRepo.doExclude(listOf(Category.fromName(it.cat)), it.tags).also { excluded ->
                if (excluded) {
                    println("[${this::class.simpleName}.${this::excludeTagFilter.name}] ${it.id} is excluded")
                }
            }
        }

    /**
     * fetch detail information of the book, and store as tmp book
     * @return do the store success
     */
    private suspend fun storeTmpBook (searchBookData: SearchBookData): Boolean {
        rootBinding.screenProgressBarWrapper.visibility = View.VISIBLE
        val doc = try {
            EPictureFetcher.fetchWebpage(searchBookData.url, true)
        } catch (_: HttpStatusException) {
            return false
        }
        rootBinding.screenProgressBarWrapper.visibility = View.GONE

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
            categoryOrdinal = Category.fromName(searchBookData.cat).ordinal,
            uploader = doc.selectFirst("#gdn a")?.text(),
            tagsJson = gson.toJson(tags),
            sourceOrdinal = BookSource.E.ordinal,
            pageUrlsJson = gson.toJson(listOf(searchBookData.coverUrl))
        )
        return true
    }

    private fun showInfoDialog () {
        val dialogBinding = DialogSearchInfoBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.apply {
            resultNumber.text = foundResultString
            loadedNumber.text = totalBookLoaded.toString()
            filteredNumber.text = totalBookFiltered.toString()
            filteredDisabledLabel.visibility = if (searchMarkData.doExclude) View.GONE else View.VISIBLE
        }

        dialogBinding.jumpToHistoryButton.apply {
            visibility = if (lastNextHistory == null) View.GONE else View.VISIBLE
            setOnClickListener {
                resetting = true // prevent trigger infinity scroll
                prev = NO_SET
                next = lastNextHistory!!.toInt()
                recyclerViewAdapter.clear()
                CoroutineScope(Dispatchers.IO).launch {
                    runBlocking {
                        loadNextBooks()
                        resetting = false
                    }
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    data class SearchBookData (
        val id: String,
        val url: String,
        val coverUrl: String,
        val cat: String,
        val title: String,
        val pageNum: Int,
        val tags: Map<String, List<String>>
    )

    /**
     * re-packed search mark data for this activity
      */
    private data class SearchMarkData (
        val id: Long,
        val name: String,
        val sourceOrdinal: Int,
        val keyword: String,
        val categories: List<Category>,
        val tags: Map<String, List<String>>,
        val uploader: String?,
        val doExclude: Boolean
    ) {
        fun getSearchUrl (
            next: String? = null,
            prev: String? = null
        ): String {
            if (next != null && prev != null) {
                throw Exception("Do not pass in next and prev together")
            }

            val fCatsValue = 1023 - if (categories.isNotEmpty()) {
                categories.sumOf { it.value }
            } else {
                Category.entries.sumOf { it.value }
            }

            // f search
            var fSearch = ""
            if (keyword.isNotEmpty()) {
                fSearch += "$keyword+"
            }
            if (tags.isNotEmpty() || uploader?.isNotEmpty() == true) {
                val tokens = mutableListOf<String>()
                tags.forEach { entry ->
                    val cat = entry.key
                    for (value in entry.value) {
                        tokens.add(buildTagValueString(cat, value))
                    }
                }
                if (uploader?.isNotEmpty() == true) {
                    tokens.add(buildTagValueString("uploader", uploader))
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

    //
    // define recycler view adapter
    //

    inner class BookRecyclerViewAdapter: RecyclerView.Adapter<BookRecyclerViewAdapter.ViewHolder>() {
        inner class ViewHolder (val binding: ActivitySearchBookBinding): RecyclerView.ViewHolder(binding.root)

        private val bookRecords = mutableListOf<SearchBookData>()

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
                setTextColor(context.getColor(Category.fromName(bookRecord.cat).color))
            }

            binding.root.apply {
                setOnClickListener {
                    lifecycleScope.launch {
                        val bookDb = BookRepository(baseContext)
                        val intent = Intent(context, BookProfileActivity::class.java)
                        intent.putExtra(
                            "bookId",
                            if (bookDb.isBookStored(bookRecord.id)) {
                                bookRecord.id
                            } else {
                                storeTmpBook(bookRecord).let {
                                    if (!it) {
                                        // store failed
                                        Toast.makeText(baseContext, "這本書出現錯誤，無法打開", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                }
                                "-1"
                            }
                        )
                        startActivity(intent)
                    }
                }
            }
        }

        fun getBooks (): List<SearchBookData> = bookRecords

        fun addNextBooks (books: List<SearchBookData>) {
            val positionStart = bookRecords.size
            bookRecords.addAll(books)
            notifyItemRangeInserted(positionStart, books.size)
        }

        fun addPrevBooks (books: List<SearchBookData>) {
            bookRecords.addAll(0, books)
            notifyItemRangeInserted(0, books.size)
        }

        fun refreshBooks (books: List<SearchBookData>) {
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
