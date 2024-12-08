package com.example.viewer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.input.key.Key
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

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

    private var page = 0 // firstPage to lastPage
    private var firstPage = 0 // 0 to pageNum - 1
    private var lastPage = 0 // 0 to pageNum - 1
    private var nextBookFlag = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.viewer)

        photoView = findViewById(R.id.photoView)
        progressBar = findViewById(R.id.viewer_progress_bar)
        tmpImageView = findViewById(R.id.viewer_tmp_image_vew)
        pageTextView = findViewById(R.id.viewer_page_TextView)

        bookId = intent.getStringExtra("bookId")!!
        skipPageSet = History.getBookSkipPages(bookId).toSet()
        updatePageNumRange()
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
                    } else {
                        nextBook()
                    }
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
        pageTextView.text = (page + 1).toString()
        toggleProgressBar(true)

        CoroutineScope(Dispatchers.Main).launch {
            val pictureBuilder = fetcher.getPicture(page, loadListener) ?: return@launch
            pictureBuilder.into(photoView)
        }
        CoroutineScope(Dispatchers.IO).launch {
            val nextPage = page + 1
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
        val bookIds = History.getAllBookIds().filter {
            when {
                it == bookId -> false
                !Util.isInternetAvailable(this) -> {
                    val bookFolder = File(getExternalFilesDir(null), it)
                    History.getBookPageNum(it) <= bookFolder.listFiles()!!.size
                }
                else -> true
            }
        }

        if (bookIds.isEmpty()) {
            Toast.makeText(this, "沒有另一本書", Toast.LENGTH_SHORT).show()
            return
        }

        bookId = bookIds[Random.nextInt(bookIds.size)]
        skipPageSet = History.getBookSkipPages(bookId).toSet()
        updatePageNumRange()
        page = firstPage

        fetcher = APictureFetcher.getFetcher(this, bookId)
        nextBookFlag = false

        loadPage()
    }
}