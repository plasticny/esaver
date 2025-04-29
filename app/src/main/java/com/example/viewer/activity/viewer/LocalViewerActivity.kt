package com.example.viewer.activity.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.database.BookDatabase
import com.example.viewer.RandomBook
import com.example.viewer.Util
import com.example.viewer.databinding.ViewerImageDialogBinding
import com.example.viewer.dialog.BookmarkDialog
import com.example.viewer.dialog.ConfirmDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * string extra: bookId
 */
class LocalViewerActivity: BaseViewerActivity() {
    companion object {
        private const val ROTATE_LEFT = -90F
        private const val ROTATE_RIGHT = 90F
    }

    private lateinit var bookDataset: BookDatabase

    private lateinit var fetcher: BasePictureFetcher

    private lateinit var bookId: String
    private lateinit var skipPageSet: Set<Int>

    private val bookFolder: File
        get() = fetcher.bookFolder!!

    @Volatile
    private var askingNextBook = false

    override fun onCreate(savedInstanceState: Bundle?) {
        bookDataset = BookDatabase.getInstance(baseContext)
        bookId = intent.getStringExtra("bookId")!!
        prepareBook(bookId)

        super.onCreate(savedInstanceState)

        if (page + 1 <= lastPage) {
            preloadPage(page + 1)
        }
    }

    override fun onImageLongClicked(): Boolean {
        showImageDialog()
        return true
    }

    override fun onPageTextClicked() =
        BookmarkDialog(this, layoutInflater, bookId, page) { bookMarkPage ->
            page = bookMarkPage
            loadPage()
            if (page + 1 <= lastPage) {
                preloadPage(page + 1)
            }
            if (page - 1 >= firstPage) {
                preloadPage(page - 1)
            }
        }.show()

    override fun nextPage () {
        if (page == lastPage && !askingNextBook) {
            askingNextBook = true
            ConfirmDialog(this, layoutInflater).show(
                "已到尾頁，到下一本書嗎？",
                positiveCallback = {
                    nextBook()
                    askingNextBook = false
                },
                negativeCallback = {
                    askingNextBook = false
                }
            )
        }
        else if (page < lastPage) {
            page++
            while (skipPageSet.contains(page)) {
                page++
            }
            loadPage()

            if (page + 1 <= lastPage) {
                preloadPage(page + 1)
            }
        }
    }

    override fun prevPage () {
        if (page > firstPage) {
            page--
            while (skipPageSet.contains(page)) {
                page--
            }
            loadPage()

            if (page - 1 >= firstPage) {
                preloadPage(page - 1)
            }
        }
    }

    override fun reloadPage() {
        val myPage = page

        viewerActivityBinding.viewerPageTextView.text = (page + 1).toString()
        toggleLoadingUi(true)
        toggleLoadFailedScreen(false)

        resetPageSignature(page)
        // download the picture again
        CoroutineScope(Dispatchers.IO).launch {
            fetcher.savePicture(page)
            if (myPage != page) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                loadPage()
            }
        }
    }

    override suspend fun getPictureUrl(page: Int): String? = fetcher.getPictureUrl(page)

    private fun preloadPage (page: Int) {
        if (page < firstPage || page > lastPage) {
            throw Exception("page out of range")
        }

        lifecycleScope.launch {
            if (!File(bookFolder, page.toString()).exists() && !Util.isInternetAvailable(baseContext)) {
                return@launch
            }
            fetcher.getPictureUrl(page)?.let {
                showPicture(
                    it, getPageSignature(page),
                    imageView = viewerActivityBinding.viewerTmpImageVew
                )
            }
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
        fetcher = BasePictureFetcher.getFetcher(this, bookId)
    }

    private fun showImageDialog () {
        val dialogViewBinding = ViewerImageDialogBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()

        // set cover page
        dialogViewBinding.viewImgDialogCoverPageButton.apply {
            setOnClickListener {
                bookDataset.setBookCoverPage(bookId, page)
                dialog.dismiss()
            }
        }

        // skip page button
        dialogViewBinding.viewImgDialogSkipButton.apply {
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
        dialogViewBinding.viewImgDialogNextBookButton.apply {
            setOnClickListener {
                nextBook()
                dialog.dismiss()
            }
        }

        // rotate buttons
        dialogViewBinding.viewImgDialogRotateLeftButton.apply {
            setOnClickListener {
                rotatePage(ROTATE_LEFT)
                dialog.dismiss()
            }
        }
        dialogViewBinding.viewImgDialogRotateRightButton.apply {
            setOnClickListener {
                rotatePage(ROTATE_RIGHT)
                dialog.dismiss()
            }
        }

        dialogViewBinding.reloadButton.setOnClickListener {
            dialog.dismiss()
            reloadPage()
        }

        dialog.show()
    }

    private fun nextBook () {
        bookId = RandomBook.next(this, !Util.isInternetAvailable(this))
        prepareBook(bookId)
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

        toggleLoadingUi(true)
        CoroutineScope(Dispatchers.IO).launch {
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

            // refresh page
            withContext(Dispatchers.Main) {
                resetPageSignature(page)
                loadPage()
                toggleLoadingUi(false)
            }
        }
    }
}