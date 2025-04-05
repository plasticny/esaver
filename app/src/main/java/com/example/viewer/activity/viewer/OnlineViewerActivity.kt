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
        toggleProgressBar(true)

        val myPage = page
        viewerActivityBinding.viewerPageTextView.text = (page + 1).toString()

        lifecycleScope.launch {
            Glide.with(baseContext)
                .load(withContext(Dispatchers.IO) { getPictureUrl(page) })
                .listener(object: RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
                    ): Boolean {
                        Toast.makeText(this@OnlineViewerActivity, "讀取圖片失敗", Toast.LENGTH_SHORT).show()
                        return loadEnded()
                    }

                    override fun onResourceReady(
                        resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                    ): Boolean = loadEnded()

                    private fun loadEnded (): Boolean {
                        toggleProgressBar(false)
                        return false
                    }
                }).run {
                    if (myPage == page) {
                        into(viewerActivityBinding.photoView)
                    }
                }
        }

        // pre-load next page
        if (page + 1 <= lastPage) {
            lifecycleScope.launch {
                Glide.with(baseContext)
                    .load(withContext(Dispatchers.IO) { getPictureUrl(page + 1) })
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
            pictureUrls[page] = fetcher.fetchPictureUrl(page)
        }

        return pictureUrls[page]!!
    }
}