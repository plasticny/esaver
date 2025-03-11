package com.example.viewer.activity.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.dataset.BookDataset
import com.example.viewer.R
import com.example.viewer.RandomBook
import com.example.viewer.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream

class LocalViewerActivity: BaseViewerActivity() {
    companion object {
        private const val ROTATE_LEFT = -90F
        private const val ROTATE_RIGHT = 90F
    }

    private lateinit var bookDataset: BookDataset

    private lateinit var fetcher: BasePictureFetcher

    private lateinit var bookId: String
    private lateinit var skipPageSet: Set<Int>

    private var nextBookFlag = false
    private var volumeDownKeyHeld = false

    private val bookFolder: File
        get() = fetcher.bookFolder

    override fun onCreate(savedInstanceState: Bundle?) {
        bookDataset = BookDataset.getInstance(baseContext)
        bookId = intent.getStringExtra("bookId")!!
        prepareBook(bookId)

        super.onCreate(savedInstanceState)
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

    override fun onImageLongClicked(): Boolean {
        showImageDialog()
        return true
    }

    override fun nextPage () {
        if (page < lastPage) {
            page++
            while (skipPageSet.contains(page)) {
                page++
            }
            loadPage()
        }
    }

    override fun prevPage () {
        if (page > firstPage) {
            page--
            while (skipPageSet.contains(page)) {
                page--
            }
            loadPage()
        }
    }

    override fun loadPage () {
        val myPage = page

        viewerActivityBinding.viewerPageTextView.text = (myPage + 1).toString()
        toggleProgressBar(true)

        // load this page
        CoroutineScope(Dispatchers.Main).launch {
            val pictureBuilder = fetcher.getPicture(
                myPage,
                object: RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
                    ): Boolean = loadEnded()

                    override fun onResourceReady(
                        resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                    ): Boolean = loadEnded()

                    private fun loadEnded (): Boolean {
                        toggleProgressBar(false)
                        return false
                    }
                }
            )
            if (pictureBuilder != null && page == myPage) {
                pictureBuilder.into(viewerActivityBinding.photoView)
            }
        }

        // pre-load next next page
        CoroutineScope(Dispatchers.IO).launch {
            val nextPage = myPage + 1
            if (nextPage > lastPage || File(bookFolder, nextPage.toString()).exists()) {
                return@launch
            }
            fetcher.savePicture(nextPage)
        }
    }

    private fun prepareBook (bookId: String) {
        skipPageSet = bookDataset.getBookSkipPages(bookId).toSet()

        firstPage = 0
        lastPage = bookDataset.getBookPageNum(bookId) - 1
        while (skipPageSet.contains(firstPage)) {
            firstPage++
        }
        while (skipPageSet.contains(lastPage)) {
            lastPage--
        }

        page = firstPage
        fetcher = BasePictureFetcher.getFetcher(this, bookId).apply {
            setDownloadFailureCallback { res -> onPictureDownloadFailure(res) }
        }
    }

    private fun showImageDialog () {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.viewer_image_dialog, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // set cover page
        dialogView.findViewById<Button>(R.id.view_img_dialog_coverPage_button).apply {
            setOnClickListener {
                bookDataset.setBookCoverPage(bookId, page)
                dialog.dismiss()
            }
        }

        // skip page button
        dialogView.findViewById<Button>(R.id.view_img_dialog_skip_button).apply {
            setOnClickListener {
                bookDataset.setBookSkipPages(bookId, skipPageSet.toMutableList().also { it.add(page) })
                skipPageSet = bookDataset.getBookSkipPages(bookId).toSet()

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

        // next book button
        dialogView.findViewById<Button>(R.id.view_img_dialog_next_book_button).apply {
            setOnClickListener {
                nextBook()
                dialog.dismiss()
            }
        }

        // rotate buttons
        dialogView.findViewById<Button>(R.id.view_img_dialog_rotate_left_button).apply {
            setOnClickListener {
                rotatePage(ROTATE_LEFT)
                dialog.dismiss()
            }
        }
        dialogView.findViewById<Button>(R.id.view_img_dialog_rotate_right_button).apply {
            setOnClickListener {
                rotatePage(ROTATE_RIGHT)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun nextBook () {
        bookId = RandomBook.next(this, !Util.isInternetAvailable(this))
        prepareBook(bookId)

        nextBookFlag = false

        bookDataset.updateBookLastViewTime(bookId)
        loadPage()
    }

    private fun rotatePage (rotation: Float) {
        val imageFile = File(bookFolder, page.toString())

        val matrix = if (rotation == ROTATE_LEFT || rotation == ROTATE_RIGHT) {
            Matrix().apply { postRotate(rotation) }
        } else {
            throw Exception("[ViewerActivity.rotatePage] unexpected rotation $rotation")
        }

        if (Util.isGifFile(imageFile)) {
            Toast.makeText(baseContext, "不支持旋轉GIF", Toast.LENGTH_SHORT).show()
            return
        }
        else {
            // handle static file rotation
            val originImage = BitmapFactory.decodeFile(imageFile.path)
            val rotatedImage = Bitmap.createBitmap(
                originImage,
                0, 0,
                originImage.width, originImage.height,
                matrix, true
            )
            FileOutputStream(imageFile).use {
                rotatedImage.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
            }
            originImage.recycle()
            rotatedImage.recycle()
        }

        // refresh page
        loadPage()
    }

    private fun onPictureDownloadFailure (response: Response) {
        toggleProgressBar(false)
        runOnUiThread {
            Toast.makeText(
                baseContext,
                "圖片下載失敗，響應代碼﹕${response.code}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}