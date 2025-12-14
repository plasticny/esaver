package com.example.viewer.activity.search

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
import com.example.viewer.activity.BookProfileActivity
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.data.repository.SearchRepository
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.databinding.ActivitySearchBookBinding
import com.example.viewer.databinding.DialogSearchInfoBinding
import com.example.viewer.dialog.SearchMarkDialog.SearchMarkDialog
import com.example.viewer.dialog.SimpleEditTextDialog
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
            sourceOrdinal: Int,
            categories: List<Category>,
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
    }

    private lateinit var searchRepo: SearchRepository
    private lateinit var excludeTagRepo: ExcludeTagRepository
    private lateinit var searchMarkData: SearchMarkData
    private lateinit var rootBinding: SearchActivityBinding
    private lateinit var allSearchMarkIds: List<Long>
    private lateinit var searchHelper: SearchHelper

    @Volatile
    private var loadingMore = false
    private var position = -1
    private var lastExcludeTagUpdateTime = 0L
    private var isTemporarySearch = false
    private var resetting = true

    private var totalBookLoaded = -1
    private var totalBookFiltered = -1
    private var lastNextHistory: String? = null

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

        searchMarkData = SearchMarkData.packSearchMark(
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

                    if (searchHelper.hasNextBlock && lm.findLastCompletelyVisibleItemPosition() == recyclerViewAdapter.itemCount - 1) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runBlocking {
                                loadingTriggered = true
                                loadNextBooks()
                                loadingTriggered = false
                            }
                        }
                    }
                    else if (searchHelper.hasPrevBlock && lm.findFirstCompletelyVisibleItemPosition() == 0) {
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
                        searchHelper.hasNextBlock && dy > 0 &&
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
                        searchHelper.hasPrevBlock && dy < 0 &&
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
            val dialog = SearchMarkDialog(this, layoutInflater).apply {
                title = if (isTemporarySearch) "編輯搜尋" else "編輯搜尋標記"
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
                                searchMarkData = SearchMarkData.packSearchMark(searchRepo.getSearchMark(id))
                                lifecycleScope.launch { reset() }
                            }
                        }.show()
                    }
                    // save modification
                    else {
                        searchRepo.modifySearchMark(
                            searchMarkData.id,
                            name = data.name,
                            sourceOrdinal = data.sourceOrdinal,
                            categories = data.categories.toList(),
                            keyword = data.keyword,
                            tags = data.tags,
                            uploader = data.uploader,
                            doExclude = data.doExclude
                        )
                        searchMarkData = SearchMarkData.packSearchMark(searchRepo.getSearchMark(searchMarkData.id))
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
            }

            when (searchMarkData.sourceOrdinal) {
                BookSource.E.ordinal -> dialog.showESearchMark(
                    name = searchMarkData.name,
                    categories = searchMarkData.categories,
                    keyword = searchMarkData.keyword,
                    tags = searchMarkData.tags,
                    uploader = searchMarkData.uploader ?: "",
                    doExclude = searchMarkData.doExclude
                )
                BookSource.Wn.ordinal -> dialog.showWnSearchMark(
                    name = searchMarkData.name,
                    category = searchMarkData.categories.also { assert(it.size == 1) }.first(),
                    keyword = searchMarkData.keyword
                )
                else -> throw IllegalStateException("unexpected ordinal")
            }

            dialog.apply {
                showNameField = !isTemporarySearch
                showSaveButton = true
                showSearchButton = true
            }
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
                searchMarkData = SearchMarkData.packSearchMark(searchRepo.getSearchMark(id))
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
                searchMarkData = SearchMarkData.packSearchMark(searchRepo.getSearchMark(id))
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

    private suspend fun reset () {
        resetting = true

        searchHelper = SearchHelper.getSearchHelper(baseContext, searchMarkData)

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

        if (excludeTagRepo.doExclude(searchMarkData.sourceOrdinal, searchMarkData.categories, searchMarkData.tags)) {
            Toast.makeText(baseContext, "所有書都被濾除了", Toast.LENGTH_SHORT).show()
        } else {
            loadNextBooks()
        }

        resetting = false
    }

    private suspend fun loadNextBooks () {
        if (!searchHelper.hasNextBlock) {
            throw IllegalStateException("no next search block")
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
        if (!searchHelper.hasPrevBlock) {
            throw IllegalStateException("no prev search block")
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
        val mySearchId = searchMarkData.id
        var books = listOf<SearchBookData>()

        do {
            searchRepo.storeLastNext(searchMarkData.id, searchHelper.nextToStore)

            val fetchedBooks = searchHelper.fetchBooks(
                searchHelper.getNextBlockSearchUrl().also {
                    println("[SearchActivity.fetchBooks] fetch book from\n$it")
                }
            ) { mySearchId != searchMarkData.id }?.also { totalBookLoaded += it.size }

            if (fetchedBooks == null) {
                break
            }

            books = if (searchMarkData.doExclude) {
                excludeTagFilter(fetchedBooks).also {
                    totalBookFiltered += (fetchedBooks.size - it.size)
                }
            } else fetchedBooks
        } while (books.isEmpty() && searchHelper.hasNextBlock && mySearchId == searchMarkData.id)

        return books
    }

    private suspend fun fetchPrevBooks (): List<SearchBookData> {
        val mySearchId = searchMarkData.id
        var books = listOf<SearchBookData>()

        do {
            val fetchedBooks = searchHelper.fetchBooks(
                searchHelper.getPrevBlockSearchUrl().also {
                    println("[SearchActivity.fetchBooks] fetch book from\n$it")
                }
            ) { mySearchId != searchMarkData.id }?.also { totalBookLoaded += it.size }

            if (fetchedBooks == null) {
                break
            }

            books = if (searchMarkData.doExclude) {
                excludeTagFilter(fetchedBooks).also {
                    totalBookFiltered += (fetchedBooks.size - it.size)
                }
            } else fetchedBooks
        } while (books.isEmpty() && searchHelper.hasPrevBlock && mySearchId == searchMarkData.id)

        return books
    }

    private fun excludeTagFilter (books: List<SearchBookData>): List<SearchBookData> =
        books.filterNot {
            excludeTagRepo.doExclude(
                searchMarkData.sourceOrdinal,
                listOf(it.cat),
                it.tags
            ).also { excluded ->
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
        val ret = searchHelper.storeDetailAsTmpBook(searchBookData)
        rootBinding.screenProgressBarWrapper.visibility = View.GONE
        return ret
    }

    private fun showInfoDialog () {
        val dialogBinding = DialogSearchInfoBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.apply {
            resultNumber.text = searchHelper.searchResultString
            loadedNumber.text = totalBookLoaded.toString()
            filteredNumber.text = totalBookFiltered.toString()
            filteredDisabledLabel.visibility = if (searchMarkData.doExclude) View.GONE else View.VISIBLE
        }

        dialogBinding.jumpToHistoryButton.apply {
            visibility = if (lastNextHistory == null) View.GONE else View.VISIBLE
            setOnClickListener {
                resetting = true // prevent trigger infinity scroll
                searchHelper.loadSearchHistory(lastNextHistory!!.toInt())
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

            binding.ratingTextView.apply {
                text = if (bookRecord.rating != null) {
                    context.getString(R.string.n_score, bookRecord.rating)
                } else ""
            }

            binding.searchBookCatTextView.apply {
                text = getString(bookRecord.cat.displayText)
                setTextColor(context.getColor(bookRecord.cat.color))
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
