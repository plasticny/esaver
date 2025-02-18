package com.example.viewer

import android.graphics.Bitmap
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import kotlin.math.abs

class ViewerActivity: AppCompatActivity() {
    companion object {
        private const val FLIP_THRESHOLD = 220
        private const val SCROLL_THRESHOLD = 50
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

        bookId = intent.getStringExtra("bookId")!!
        prepareBook(bookId)

        photoView = findViewById<ImageView?>(R.id.photoView).apply {
            setOnLongClickListener {
                showImageDialog()
                true
            }
        }
        progressBar = findViewById(R.id.viewer_progress_bar)
        tmpImageView = findViewById(R.id.viewer_tmp_image_vew)
        setupPageTextView()

        loadPage()
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

    private fun prepareBook (bookId: String) {
        skipPageSet = History.getBookSkipPages(bookId).toSet()

        firstPage = 0
        lastPage = History.getBookPageNum(bookId) - 1
        while (skipPageSet.contains(firstPage)) {
            firstPage++
        }
        while (skipPageSet.contains(lastPage)) {
            lastPage--
        }

        page = firstPage
        fetcher = APictureFetcher.getFetcher(this, bookId)
    }

    private fun setupPageTextView () {
        val simpleOnGestureListener = object: GestureDetector.SimpleOnGestureListener () {
            var scrolledDistance = 0F

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                // change page on fling
                if (e1 != null && isMotionHorizontal(e1, e2)) {
                    if (abs(e2.x - e1.x) > FLIP_THRESHOLD) {
                        return false
                    }
                    changePage(e1, e2)
                    return true
                }
                return false
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                // change page on scrolling
                if (e1 != null && isMotionHorizontal(e1, e2)) {
                    val dx = e2.x - e1.x
                    if (abs(dx) <= FLIP_THRESHOLD) {
                        return super.onScroll(e1, e2, distanceX, distanceY)
                    }

                    scrolledDistance += abs(distanceX)
                    if (scrolledDistance >= SCROLL_THRESHOLD) {
                        scrolledDistance = 0F
                        changePage(e1, e2)
                        return super.onScroll(e1, e2, distanceX, distanceY)
                    }
                }
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            private fun isMotionHorizontal (e1: MotionEvent, e2: MotionEvent) = abs(e2.x - e1.x) > abs(e2.y - e1.y)

            private fun changePage (e1: MotionEvent, e2: MotionEvent) {
                if (e2.x - e1.x > 0) {
                    prevPage()
                } else {
                    nextPage()
                }
            }
        }
        val gestureDetector = GestureDetector(this, simpleOnGestureListener)

        pageTextView = findViewById<TextView?>(R.id.viewer_page_TextView).apply {
            setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) {
                    simpleOnGestureListener.scrolledDistance = 0F
                }
                v.performClick()
                true
            }
        }
    }

    private fun showImageDialog () {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.viewer_image_dialog, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // set cover page
        dialogView.findViewById<Button>(R.id.view_img_dialog_coverPage_button).apply {
            setOnClickListener {
                History.setBookCoverPage(bookId, page)
                dialog.dismiss()
            }
        }

        // skip page button
        dialogView.findViewById<Button>(R.id.view_img_dialog_skip_button).apply {
            setOnClickListener {
                History.setBookSkipPages(bookId, skipPageSet.toMutableList().also { it.add(page) })
                skipPageSet = History.getBookSkipPages(bookId).toSet()

                if (page == firstPage) {
                    firstPage++
                    nextPage()
                } else {
                    if (page == lastPage) {
                        lastPage--
                    }
                    prevPage()
                }

                dialog.dismiss()
            }
        }

        // reload page button
        dialogView.findViewById<Button>(R.id.view_img_dialog_reload_button).apply {
            setOnClickListener {
                loadPage()
                dialog.dismiss()
            }
        }

        // next book button
        dialogView.findViewById<Button>(R.id.view_img_dialog_next_book_button).apply {
            setOnClickListener {
                nextBook()
                dialog.dismiss()
            }
        }

        dialog.show()
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

        // load this page
        CoroutineScope(Dispatchers.Main).launch {
            val pictureBuilder = fetcher.getPicture(
                myPage,
                object: RequestListener<Bitmap> {
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
            )
            if (pictureBuilder != null && page == myPage) {
                pictureBuilder.into(photoView)
            }
        }

        // pre-load next next page
        CoroutineScope(Dispatchers.IO).launch {
            val nextPage = myPage + 1
            if (nextPage > lastPage || File(fetcher.bookFolder, nextPage.toString()).exists()) {
                return@launch
            }
            fetcher.savePicture(nextPage)
        }
    }

    private fun nextBook () {
        bookId = RandomBook.next(this, !Util.isInternetAvailable(this))
        prepareBook(bookId)

        nextBookFlag = false

        History.updateBookLastViewTime(bookId)
        loadPage()
    }
}