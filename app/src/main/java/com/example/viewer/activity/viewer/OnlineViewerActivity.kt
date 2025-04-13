package com.example.viewer.activity.viewer

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.viewer.BookRecord
import com.example.viewer.fetcher.EPictureFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineViewerActivity: BaseViewerActivity() {
    private lateinit var bookRecord: BookRecord
    private lateinit var fetcher: EPictureFetcher
    private lateinit var pictureUrls: MutableList<String?>

    override fun onCreate(savedInstanceState: Bundle?) {
        bookRecord = intent.getParcelableExtra("book_record", BookRecord::class.java)!!
        page = 0
        firstPage = 0
        lastPage = bookRecord.pageNum - 1

        fetcher = EPictureFetcher(this, pageNum = bookRecord.pageNum, bookUrl = bookRecord.url)
        pictureUrls = MutableList(bookRecord.pageNum) { null }

        super.onCreate(savedInstanceState)

        if (page + 1 <= lastPage) {
            preloadPage(page + 1)
        }
    }

    override fun onImageLongClicked(): Boolean = true

    override fun onPageTextClicked() = Unit

    override fun loadPage() {
        val myPage = page

        viewerActivityBinding.viewerPageTextView.text = (page + 1).toString()
        toggleLoadingUi(true)
        toggleLoadFailedScreen(false)

        lifecycleScope.launch {
            val pictureUrl = getPictureUrl(page)
            if (myPage != page) {
                return@launch
            }

            if (pictureUrl != null) {
                showPicture(
                    pictureUrl, getPageSignature(page),
                    onPictureReady = { toggleLoadFailedScreen(false) },
                    onFailed = { toggleLoadFailedScreen(true) },
                    onFinished = { toggleLoadingUi(false) }
                )
            } else {
                toggleLoadingUi(false)
                toggleLoadFailedScreen(true)
            }
        }
    }

    override fun prevPage() {
        if (page > firstPage) {
            page--
            loadPage()

            (page - 1).let {
                if (it >= firstPage) {
                    preloadPage(it)
                }
            }
        }
    }

    override fun nextPage() {
        if (page < lastPage) {
            page++
            loadPage()

            (page + 1).let {
                if (it <= lastPage) {
                    preloadPage(it)
                }
            }
        }
    }

    private fun preloadPage (page: Int) {
        if (page < firstPage || page > lastPage) {
            throw Exception("page out of range")
        }
        lifecycleScope.launch {
            getPictureUrl(page)?.let {
                showPicture(
                    it, getPageSignature(page),
                    imageView = viewerActivityBinding.viewerTmpImageVew
                )
            }
        }
    }

    private suspend fun getPictureUrl (page: Int): String? {
        println("[${this::class.simpleName}.${this::getPictureUrl.name}] $page")

        if (page < firstPage || page > lastPage) {
            throw Exception("page out of range")
        }

        if (pictureUrls[page] == null) {
            pictureUrls[page] = withContext(Dispatchers.IO) {
                 fetcher.getPictureUrl(page)
            }
        }

        return pictureUrls[page]
    }
}