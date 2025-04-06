package com.example.viewer.activity.viewer

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.viewer.activity.SearchActivity.Companion.BookRecord
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
    }

    override fun onImageLongClicked(): Boolean = true

    override fun onPageTextClicked() = Unit

    override fun loadPage() {
        val myPage = page

        toggleProgressBar(true)

        viewerActivityBinding.viewerPageTextView.text = (page + 1).toString()
        viewerActivityBinding.photoView.imageAlpha = 0

        lifecycleScope.launch {
            getPictureUrl(page).let {
                if (myPage != page) {
                    return@let
                }

                toggleProgressBar(false)
                Glide.with(baseContext)
                    .load(it)
                    .listener(loadRequestListener)
                    .run {
                        into(viewerActivityBinding.photoView)
                        viewerActivityBinding.photoView.imageAlpha = 255
                    }
            }
        }

        // pre-load next page
        if (page + 1 <= lastPage) {
            lifecycleScope.launch {
                Glide.with(baseContext)
                    .load(getPictureUrl(page + 1))
                    .into(viewerActivityBinding.viewerTmpImageVew)
            }
        }
    }

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

    private suspend fun getPictureUrl (page: Int): String {
        println("[${this::class.simpleName}.${this::getPictureUrl.name}] $page")

        if (page < firstPage || page > lastPage) {
            throw Exception("page out of range")
        }

        if (pictureUrls[page] == null) {
            pictureUrls[page] = withContext(Dispatchers.IO) {
                 fetcher.fetchPictureUrl(page)
            }
        }

        return pictureUrls[page]!!
    }
}