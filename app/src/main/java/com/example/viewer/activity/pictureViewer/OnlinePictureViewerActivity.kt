package com.example.viewer.activity.pictureViewer

import android.os.Bundle
import android.widget.Toast
import com.example.viewer.R
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.fetcher.WnPictureFetcher
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.ProfileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.floor

class OnlinePictureViewerActivity: BaseViewerActivity() {
    private lateinit var profileItem: ProfileItem
    private lateinit var fetcher: BasePictureFetcher
    private lateinit var pictureUrls: MutableList<String?>
    private lateinit var bookRepo: BookRepository

    private var endOfBookNotified = false

    override val enableBookmarkButton = false
    override val enableJumpToButton = true

    override fun onCreate(savedInstanceState: Bundle?) {
        bookRepo = BookRepository(baseContext)

        profileItem = ProfileItem.getTmp()
        val bookData = profileItem.bookData!!

        page = 0
        firstPage = 0
        lastPage = bookData.pageNum - 1

        fetcher = when (profileItem.source) {
            ItemSource.E -> EPictureFetcher(this, pageNum = bookData.pageNum, bookUrl = profileItem.url, bookId = bookData.id)
            ItemSource.Wn -> WnPictureFetcher(this, pageNum = bookData.pageNum, bookUrl = profileItem.url, bookId = bookData.id)
            ItemSource.Hi -> throw NotImplementedError()
            ItemSource.Ru -> throw IllegalStateException()
        }
        pictureUrls = MutableList(bookData.pageNum) {
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

    override fun loadPage(myPage: Int) {
        super.loadPage(page)
        // preload
        for (d in arrayOf(1, -1, 2, -2)) {
            val preloadPage = page + d
            if (preloadPage in 0 until lastPage) {
                super.preloadPage(preloadPage)
            }
        }
    }

    override suspend fun getPictureStoredUrl (page: Int): String {
        println("[${this::class.simpleName}.${this::getPictureStoredUrl.name}] $page")

        fetcher.waitPictureDownload(page)

        if (pictureUrls[page] == null) {
            throw FileNotFoundException()
        }
        return pictureUrls[page]!!
    }

    override suspend fun downloadPicture(page: Int): File {
        if (page < firstPage || page > lastPage) {
            throw IllegalStateException("page out of range")
        }

        if (this.page == page) {
            viewerActivityBinding.progress.textView.text = getString(R.string.n_percent, 0)
        }
        val picture = withContext(Dispatchers.IO) {
            fetcher.savePicture(page) { total, downloaded ->
                if (this@OnlinePictureViewerActivity.page == page) {
                    CoroutineScope(Dispatchers.Main).launch {
                        viewerActivityBinding.progress.textView.text = getString(
                            R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                        )
                    }
                }
            }
        }
        pictureUrls[page] = picture.path
        return picture
    }

    override fun getPictureFetcher(): BasePictureFetcher = fetcher
}