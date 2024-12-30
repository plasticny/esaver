package com.example.viewer

import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ViewerActivity: AppCompatActivity() {
    private val loadListener = object: RequestListener<Bitmap> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean
        ): Boolean = loadEnded()

        override fun onResourceReady(
            resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean
        ): Boolean = loadEnded()

        private fun loadEnded (): Boolean {
            toggleProgressBar(false)
            return false
        }
    }

    private lateinit var photoView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tmpImageView: ImageView
    private lateinit var pageTextView: TextView

    private lateinit var fetcher: APictureFetcher

    private lateinit var bookId: String
    private lateinit var skipPageSet: Set<Int>

    @Volatile
    private var page = 0 // current page num, firstPage to lastPage

    private var firstPage = 0 // 0 to pageNum - 1
    private var lastPage = 0 // 0 to pageNum - 1
    private var nextBookFlag = false
    private var volumeDownKeyHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.viewer)

        photoView = findViewById(R.id.photoView)
        progressBar = findViewById(R.id.viewer_progress_bar)
        tmpImageView = findViewById(R.id.viewer_tmp_image_vew)
        pageTextView = findViewById(R.id.viewer_page_TextView)

        bookId = intent.getStringExtra("bookId")!!
        skipPageSet = History.getBookSkipPages(bookId).toSet()

        updatePageNumRange() // first page and last page are updated here
        page = firstPage

        fetcher = APictureFetcher.getFetcher(this, bookId)

        photoView.setOnLongClickListener {
            loadPage()
            true
        }

        loadPage()
    }

    private fun updatePageNumRange () {
        firstPage = 0
        lastPage = History.getBookPageNum(bookId) - 1
        while (skipPageSet.contains(firstPage)) {
            firstPage++
        }
        while (skipPageSet.contains(lastPage)) {
            lastPage--
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    nextBookFlag = false
                    prevPage()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (page < lastPage) {
                        nextPage()
                    } else if (!nextBookFlag) {
                        nextBookFlag = true
                        Toast.makeText(this, "尾頁，再按一次到下一本", Toast.LENGTH_SHORT).show()
                    } else if (!volumeDownKeyHeld) {
                        nextBook()
                    }
                    volumeDownKeyHeld = true
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeDownKeyHeld = false
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun nextPage () {
        if (page < lastPage) {
            page++
            while (skipPageSet.contains(page)) {
                page++
            }
            loadPage()
        }
    }

    private fun prevPage () {
        if (page > firstPage) {
            page--
            while (skipPageSet.contains(page)) {
                page--
            }
            loadPage()
        }
    }

    private fun loadPage () {
        val myPage = page

        pageTextView.text = (myPage + 1).toString()
        toggleProgressBar(true)

        CoroutineScope(Dispatchers.Main).launch {
            val pictureBuilder = fetcher.getPicture(myPage, loadListener) ?: return@launch
            if (page == myPage) {
                pictureBuilder.into(photoView)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            val nextPage = myPage + 1
            if (nextPage > lastPage || File(fetcher.bookFolder, nextPage.toString()).exists()) {
                return@launch
            }
            fetcher.savePicture(nextPage)
        }
    }

    private fun toggleProgressBar (toggle: Boolean) {
        if (toggle) {
            progressBar.visibility = ProgressBar.VISIBLE
            photoView.visibility = PhotoView.INVISIBLE
        }
        else {
            progressBar.visibility = ProgressBar.GONE
            photoView.visibility = PhotoView.VISIBLE
        }
    }

    private fun nextBook () {
        bookId = RandomBook.next(this, !Util.isInternetAvailable(this))
        skipPageSet = History.getBookSkipPages(bookId).toSet()
        updatePageNumRange()
        page = firstPage

        fetcher = APictureFetcher.getFetcher(this, bookId)
        nextBookFlag = false

        loadPage()
    }
}