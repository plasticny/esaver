package com.example.viewer.activity.viewer

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.viewer.struct.BookRecord
import com.example.viewer.fetcher.EPictureFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineViewerActivity: BaseViewerActivity() {
    private lateinit var bookRecord: BookRecord
    private lateinit var fetcher: EPictureFetcher
    private lateinit var pictureUrls: MutableList<String?>

    override val enableBookmarkButton = false
    override val enableJumpToButton = true

    override fun onCreate(savedInstanceState: Bundle?) {
        bookRecord = intent.getParcelableExtra("book_record", BookRecord::class.java)!!
        page = 0
        firstPage = 0
        lastPage = bookRecord.pageNum - 1

        fetcher = EPictureFetcher(this, pageNum = bookRecord.pageNum, bookUrl = bookRecord.url)
        pictureUrls = MutableList(bookRecord.pageNum) { null }

        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        fetcher.close()
    }

    override fun onImageLongClicked(): Boolean = true

    override fun prevPage() {
        if (page > firstPage) {
            page--
            loadPage()
        }
    }

    override fun nextPage() {
        if (page < lastPage) {
            page++
            loadPage()
        }
    }

    override fun reloadPage() = loadPage()

    override fun loadPage() {
        super.loadPage()
        preloadPage(page + 1)
        preloadPage(page + 2)
        preloadPage(page - 1)
        preloadPage(page - 2)
    }

    override suspend fun getPictureUrl (page: Int): String? {
        println("[${this::class.simpleName}.${this::getPictureUrl.name}] $page")

        if (page < firstPage || page > lastPage) {
            throw Exception("page out of range")
        }

        return pictureUrls[page] ?: withContext(Dispatchers.IO) {
            fetcher.savePicture(page)?.path.also { pictureUrls[page] = it }
        }
    }

    private fun preloadPage (page: Int) {
        if (page < firstPage || page > lastPage) {
            return
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
}