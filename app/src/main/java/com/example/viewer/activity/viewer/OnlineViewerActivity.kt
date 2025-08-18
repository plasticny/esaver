package com.example.viewer.activity.viewer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.viewer.R
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.struct.Book
import com.example.viewer.fetcher.EPictureFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException
import kotlin.math.floor

class OnlineViewerActivity: BaseViewerActivity() {
    private lateinit var book: Book
    private lateinit var fetcher: EPictureFetcher
    private lateinit var pictureUrls: MutableList<String?>
    private lateinit var bookRepo: BookRepository

    private var endOfBookNotified = false

    override val enableBookmarkButton = false
    override val enableJumpToButton = true

    override fun onCreate(savedInstanceState: Bundle?) {
        bookRepo = BookRepository(baseContext)

        book = Book.getTmpBook()
        page = 0
        firstPage = 0
        lastPage = book.pageNum - 1

        fetcher = EPictureFetcher(this, pageNum = book.pageNum, bookUrl = book.url, bookId = book.id)
        pictureUrls = MutableList(book.pageNum) {
            val file = File(fetcher.bookFolder, it.toString())
            if (file.exists()) file.path else null
        }

        super.onCreate(savedInstanceState)
    }

    override fun onImageLongClicked(): Boolean = true

    override fun prevPage() {
        endOfBookNotified = false
        if (page > firstPage) {
            page--
            loadPage()
        }
    }

    override fun nextPage() {
        if (page == lastPage && !endOfBookNotified) {
            endOfBookNotified = true
            Toast.makeText(baseContext, "已到尾頁", Toast.LENGTH_SHORT).show()
        }
        else if (page < lastPage) {
            page++
            loadPage()
        }
    }

    override fun reloadPage() {
        fetcher.deletePicture(page)
        loadPage()
    }

    override fun loadPage() {
        super.loadPage()
        try {
            preloadPage(page + 1)
            preloadPage(page + 2)
            preloadPage(page - 1)
            preloadPage(page - 2)
        } catch (e: SocketTimeoutException) {
            Log.e("${this::class.simpleName}.${this::loadPage.name}", e.stackTraceToString())
        }
    }

    override suspend fun getPictureUrl (page: Int): String? {
        println("[${this::class.simpleName}.${this::getPictureUrl.name}] $page")

        if (page < firstPage || page > lastPage) {
            throw IllegalStateException("page out of range")
        }

        if (pictureUrls[page] == null) {
            if (this.page == page) {
                viewerActivityBinding.progress.textView.text = getString(R.string.n_percent, 0)
            }
            withContext(Dispatchers.IO) {
                fetcher.savePicture(page) { total, downloaded ->
                    if (this@OnlineViewerActivity.page == page) {
                        CoroutineScope(Dispatchers.Main).launch {
                            viewerActivityBinding.progress.textView.text = getString(
                                R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                            )
                        }
                    }
                }
            }.path.also { pictureUrls[page] = it }
        }

        return pictureUrls[page]
    }

    private fun preloadPage (page: Int) {
        if (page < firstPage || page > lastPage) {
            return
        }
        lifecycleScope.launch { getPictureUrl(page) }
    }
}