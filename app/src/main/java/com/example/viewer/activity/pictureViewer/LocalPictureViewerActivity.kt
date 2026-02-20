package com.example.viewer.activity.pictureViewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.viewer.R
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.ItemRNG
import com.example.viewer.Util
import com.example.viewer.activity.ItemProfileActivity
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.databinding.ViewerImageDialogBinding
import com.example.viewer.dialog.BookmarkDialog
import com.example.viewer.dialog.ConfirmDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor

/**
 * long extra: itemId
 */
class LocalPictureViewerActivity: BaseViewerActivity() {
    companion object {
        private const val ROTATE_LEFT = -90F
        private const val ROTATE_RIGHT = 90F
    }

    private lateinit var bookDataset: BookRepository
    private lateinit var fetcher: BasePictureFetcher
    private lateinit var skipPageSet: Set<Int>

    private val bookFolder: File
        get() = fetcher.bookFolder

    private var itemId: Long = -1

    @Volatile
    private var askingNextBook = false

    override val enableBookmarkButton = true
    override val enableJumpToButton = true

    override fun onCreate(savedInstanceState: Bundle?) {
        bookDataset = BookRepository(baseContext)
        itemId = intent.getLongExtra("itemId", -1)
        prepareBook(itemId)

        super.onCreate(savedInstanceState)

        viewerActivityBinding.bookmarkButton.setOnClickListener {
            BookmarkDialog(this, layoutInflater, itemId, page) { bookMarkPage ->
                toPage(bookMarkPage)
            }.show()
        }
    }

    override fun onImageLongClicked(): Boolean {
        showImageDialog()
        return true
    }

    override fun nextPage () {
        if (page == lastPage && !askingNextBook) {
            askingNextBook = true
            ConfirmDialog(this, layoutInflater).show(
                "已到尾頁，到下一本書嗎？",
                positiveCallback = {
                    nextBook()
                },
                finishCb = {
                    askingNextBook = false
                }
            )
        }
        else if (page < lastPage) {
            page = nextPageOf(page)!!
            loadPage()
        }
    }

    override fun prevPage () {
        if (page > firstPage) {
            page = prevPageOf(page)!!
            loadPage()
        }
    }

    override fun reloadPage() {
        fetcher.deletePicture(page)
        loadPage()
    }

    override suspend fun getPictureStoredUrl(page: Int): String {
        fetcher.waitPictureDownload(page)
        return fetcher.getPictureStoredUrl(page)
    }

    override suspend fun downloadPicture(page: Int): File {
        if (page < firstPage || page > lastPage) {
            throw IllegalStateException("page out of range")
        }

        if (this.page == page) {
            viewerActivityBinding.progress.textView.text = getString(R.string.n_percent, 0)
        }
        return fetcher.savePicture(page) { total, downloaded ->
            if (this.page == page) {
                CoroutineScope(Dispatchers.Main).launch {
                    viewerActivityBinding.progress.textView.text = getString(
                        R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                    )
                }
            }
        }
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

    override fun getPictureFetcher(): BasePictureFetcher = fetcher

    private fun prepareBook (itemId: Long) {
        skipPageSet = bookDataset.getBookSkipPages(itemId).toSet()

        firstPage = 0
        lastPage = bookDataset.getBookPageNum(itemId) - 1
        while (skipPageSet.contains(firstPage)) {
            firstPage++
        }
        while (skipPageSet.contains(lastPage)) {
            lastPage--
        }

        page = firstPage
        fetcher = BasePictureFetcher.getFetcher(this, itemId)
    }

    private fun showImageDialog () {
        val dialogViewBinding = ViewerImageDialogBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()

        // set cover page
        dialogViewBinding.viewImgDialogCoverPageButton.apply {
            setOnClickListener {
                bookDataset.setBookCoverPage(itemId, page)
                dialog.dismiss()
            }
        }

        // skip page button
        dialogViewBinding.viewImgDialogSkipButton.apply {
            setOnClickListener {
                bookDataset.setBookSkipPages(
                    itemId,
                    skipPageSet.toMutableList().also { it.add(page) }.sorted()
                )
                skipPageSet = bookDataset.getBookSkipPages(itemId).toSet()

                if (bookDataset.getBookCoverPage(itemId) != page) {
                    // image file of skipped page is no longer needed
                    File(bookFolder, page.toString()).delete()
                }

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
        itemId = ItemRNG.next(this)

        prepareBook(itemId)
        runBlocking {
            bookDataset.updateBookLastViewTime(itemId)
        }

        ItemProfileActivity.setResumeItem(itemId)

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
                rotatedImage.compress(
                    if (Build.VERSION.SDK_INT == 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP,
                    100,
                    it
                )
            }

            originImage.recycle()
            rotatedImage.recycle()

            // refresh page
            withContext(Dispatchers.Main) {
                loadPage()
                toggleLoadingUi(false)
            }
        }
    }

    private fun nextPageOf (page: Int): Int? {
        if (page == lastPage) {
            return null
        }

        var ret = page + 1
        while (skipPageSet.contains(ret)) {
            ret++
        }
        return ret
    }

    private fun prevPageOf (page: Int): Int? {
        if (page == firstPage) {
            return null
        }

        var ret = page - 1
        while (skipPageSet.contains(ret)) {
            ret--
        }
        return ret
    }
}