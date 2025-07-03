package com.example.viewer.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Transaction
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.MediaStoreSignature
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.activity.main.MainActivity
import com.example.viewer.activity.viewer.LocalViewerActivity
import com.example.viewer.activity.viewer.OnlineViewerActivity
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.struct.Book
import com.example.viewer.databinding.BookProfileActivityBinding
import com.example.viewer.databinding.BookProfileTagBinding
import com.example.viewer.databinding.DialogBookInfoBinding
import com.example.viewer.databinding.DialogTagBinding
import com.example.viewer.dialog.ConfirmDialog
import com.example.viewer.dialog.EditExcludeTagDialog
import com.example.viewer.dialog.LocalReadSettingDialog
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.fetcher.HiPictureFetcher
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import kotlin.math.floor
import kotlin.math.min

/**
 * StringExtra: bookId
 */
class BookProfileActivity: AppCompatActivity() {
    companion object {
        // (width, height)
        private var coverMetrics: Pair<Int, Int>? = null
        private fun getCoverMetrics (context: Context): Pair<Int, Int> {
            return coverMetrics ?: context.resources.displayMetrics.let { displayMetrics ->
                val width = min(Util.dp2px(context, 160F), displayMetrics.widthPixels)
                val height = (width * 1.5).toInt()
                println("[${this::class.simpleName}] cover metrics: ($width, $height)")
                Pair(width, height).also { coverMetrics = it }
            }
        }
    }

    private lateinit var book: Book
    private lateinit var rootBinding: BookProfileActivityBinding
    private lateinit var bookRepo: BookRepository
    private lateinit var excludedTags: Map<String, Set<String>>

    private var isBookStored: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookRepo = BookRepository(baseContext)

        book = bookRepo.getBook(intent.getStringExtra("bookId")!!)

        val bookRepo = BookRepository(baseContext)
        isBookStored = runBlocking { bookRepo.isBookStored(book.id) }

        excludedTags = ExcludeTagRepository(baseContext).findExcludedTags(book)

        //
        // init ui
        //

        rootBinding = BookProfileActivityBinding.inflate(layoutInflater)

        rootBinding.coverWrapper.let {
            // adjust cover image size
            val (width, height) = getCoverMetrics(this)
            it.layoutParams = it.layoutParams.apply {
                this.width = width
                this.height = height
            }
        }

        rootBinding.coverImageView.let {
            if(isBookStored) {
                CoroutineScope(Dispatchers.Main).launch {
                    val coverFile = File(book.getCoverUrl(baseContext))
                    if (!coverFile.exists()) {
                        withContext(Dispatchers.IO) {
                            val id = book.id
                            val source = bookRepo.getBookSource(id)
                            val fetcher = if (source == BookSource.E) EPictureFetcher(
                                baseContext,
                                id
                            ) else HiPictureFetcher(baseContext, id)
                            fetcher.savePicture(bookRepo.getBookCoverPage(book.id))
                        }
                    }
                    Glide.with(baseContext)
                        .load(coverFile)
                        .signature(MediaStoreSignature("", coverFile.lastModified(), 0))
                        .into(it)
                }
            } else {
                Glide.with(baseContext).load(book.getPageUrls()!![0]).into(it)
            }
        }

        rootBinding.titleTextView.text = book.title

        rootBinding.warningContainer.apply {
            // only check warning if book is not stored
            lifecycleScope.launch {
                val isBookWarning = withContext(Dispatchers.IO) {
                    Jsoup.connect(book.url).get()
                }.html().contains("<h1>Content Warning</h1>")

                if (!isBookStored && isBookWarning) {
                    visibility = View.VISIBLE
                }
            }
        }

        rootBinding.pageNumTextView.text = baseContext.getString(R.string.n_page, book.pageNum)

        rootBinding.categoryTextView.apply {
            val name = book.getCategory().name
            text = name
            setTextColor(context.getColor(Util.categoryFromName(name).color))
        }

        rootBinding.readButton.setOnClickListener {
            if (isBookStored) {
                BookRepository(baseContext).updateBookLastViewTime(book.id)
                startActivity(Intent(baseContext, LocalViewerActivity::class.java).apply {
                    putExtra("bookId", book.id)
                })
            } else {
                startActivity(Intent(baseContext, OnlineViewerActivity::class.java))
            }
        }

        rootBinding.saveButton.apply {
            setOnClickListener {
                lifecycleScope.launch { saveBook() }
            }
        }

        rootBinding.localSettingButton.apply {
            setOnClickListener {
                if (isBookStored) {
                    LocalReadSettingDialog(this@BookProfileActivity, layoutInflater).show(
                        book,
                        onApplied = { coverPageUpdated ->
                            book = BookRepository(baseContext).getBook(book.id)
                            if (coverPageUpdated) {
                                refreshCoverPage()
                            }
                        }
                    )
                }
            }
        }

        rootBinding.infoButton.setOnClickListener {
            showInfoDialog()
        }

        rootBinding.deleteButton.apply {
            setOnClickListener {
                if (!isBookStored) {
                    return@setOnClickListener
                }
                ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
                    getString(R.string.doDelete),
                    positiveCallback = {
                        toggleProgressBar(true)
                        CoroutineScope(Dispatchers.IO).launch {
                            deleteBook(book).let { retFlag ->
                                withContext(Dispatchers.Main) {
                                    toggleProgressBar(false)
                                    if (retFlag) {
                                        finish()
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        rootBinding.tagWrapper.apply {
            lifecycleScope.launch {
                for (entry in book.getTags().entries) {
                    addView(createTagRow(entry.key, entry.value).root)
                }
            }
        }

        setContentView(rootBinding.root)

        refreshActionBar()
    }

    override fun onResume() {
        super.onResume()
        if (isBookStored) {
            // the cover page may updated
            refreshCoverPage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Book.clearTmpBook()
    }

    private fun createTagRow (tagCat: String, tagValues: List<String>) =
        BookProfileTagBinding.inflate(layoutInflater).apply {
            tagCategoryTextView.text = Util.TAG_TRANSLATION_MAP[tagCat] ?: tagCat
            for (value in tagValues) {
                Button(baseContext).apply {
                    text = value
                    backgroundTintList = ColorStateList.valueOf(baseContext.getColor(R.color.dark_grey))
                    isAllCaps = false
                    setTextColor(getColor(
                        if (excludedTags[tagCat]?.contains(value) == true) {
                            R.color.grey2
                        } else R.color.grey
                    ))

                    setOnClickListener { showTagDialog(tagCat, value) }
                }.also { tagValueWrapper.addView(it) }
            }
        }

    private fun toggleProgressBar (toggle: Boolean) {
        rootBinding.progress.wrapper.visibility = if (toggle) ProgressBar.VISIBLE else ProgressBar.GONE
    }

    private fun showTagDialog (category: String, value: String) {
        val dialogViewBinding = DialogTagBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()
        val translatedCategory = Util.TAG_TRANSLATION_MAP[category]

        dialogViewBinding.categoryTextView.text = translatedCategory

        dialogViewBinding.valueTextView.text = value

        dialogViewBinding.excludeButton.setOnClickListener {
            addFilterOutTag(category, value)
        }

        dialogViewBinding.searchButton.setOnClickListener {
            SearchActivity.startTmpSearch(
                this@BookProfileActivity,
                tags = mapOf(Pair(category, listOf(value)))
            )
        }

        dialog.show()
    }

    private fun showInfoDialog () {
        val dialogViewBinding = DialogBookInfoBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()

        dialogViewBinding.uploaderTextView.text = book.uploader ?: getString(R.string.noName)

        dialogViewBinding.urlTextView.text = book.url

        if (book.subTitle.isEmpty()) {
            dialogViewBinding.subtitle.visibility = View.GONE
        } else {
            dialogViewBinding.subtitle.text = book.subTitle
        }

        dialog.show()
    }

    private fun addFilterOutTag (tagCategory: String, tagValue: String) =
        EditExcludeTagDialog(this, layoutInflater).show(
            categories = Category.entries,
            tags = mapOf(tagCategory to listOf(tagValue))
        ) { recordToSave ->
            toggleProgressBar(true)
            CoroutineScope(Dispatchers.IO).launch {
                ExcludeTagRepository(baseContext).addExcludeTag(
                    tags = recordToSave.tags,
                    categories = recordToSave.categories.toList()
                )
                withContext(Dispatchers.Main) {
                    toggleProgressBar(false)
                    ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
                        "已濾除標籤，返回搜尋？",
                        positiveCallback = {
                            this@BookProfileActivity.finish()
                        }
                    )
                }
            }
        }

    /**
     * modify buttons in action bar based on the current book stored state
     */
    private fun refreshActionBar () {
        if (isBookStored) {
            rootBinding.saveButton.visibility = View.GONE
            rootBinding.localSettingButton.visibility = View.VISIBLE
            rootBinding.deleteButton.visibility = View.VISIBLE
        } else {
            rootBinding.saveButton.visibility = View.VISIBLE
            rootBinding.localSettingButton.visibility = View.GONE
            rootBinding.deleteButton.visibility = View.GONE
        }
    }

    /**
     * only for profile of stored book
     */
    private fun refreshCoverPage () {
        if (isBookStored) {
            val file = File(
                "${getExternalFilesDir(null)}/${book.id}",
                BookRepository(baseContext).getBookCoverPage(book.id).toString()
            )
            Glide.with(baseContext)
                .load(file)
                .into(rootBinding.coverImageView)
        }
    }

    private fun deleteBook (book: Book): Boolean = BookRepository(baseContext).removeBook(book)

    @Transaction
    private suspend fun saveBook () {
        if (isBookStored) {
            return
        }

        rootBinding.progress.textView.text = getString(R.string.n_percent, 0)
        toggleProgressBar(true)

        val fetcher = EPictureFetcher(baseContext, 1, book.url, book.id)

        // download cover page if not exist
        if (!File(fetcher.bookFolder, "0").exists()) {
            val success = withContext(Dispatchers.IO) {
                val file = fetcher.savePicture(0) { total, downloaded ->
                    CoroutineScope(Dispatchers.Main).launch {
                        rootBinding.progress.textView.text = getString(
                            R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                        )
                    }
                }
                file != null
            }
            if (!success) {
                Toast.makeText(baseContext, "儲存失敗，再試一次", Toast.LENGTH_SHORT).show()
                toggleProgressBar(false)
                return
            }
        }

        // create book folder
        File(getExternalFilesDir(null), book.id).also {
            if (!it.exists()) {
                it.mkdirs()
            }
            // move picture from tmp folder to book folder
            for (tmpFile in fetcher.bookFolder.listFiles()!!) {
                if (tmpFile.extension != "txt") {
                    tmpFile.copyTo(File(it, tmpFile.name))
                }
            }
        }

        BookRepository(baseContext).addBook(
            id = book.id,
            url = book.url,
            category = book.getCategory(),
            title = book.title,
            subtitle = book.subTitle,
            pageNum = book.pageNum,
            tags = book.getTags(),
            source = BookSource.E,
            uploader = book.uploader
        )
        GroupRepository(baseContext).addBookIdToGroup(GroupRepository.DEFAULT_GROUP_ID, book.id)

        // update ui
        toggleProgressBar(false)
        isBookStored = true
        book = BookRepository(baseContext).getBook(book.id)
        refreshActionBar()
        ConfirmDialog(this, layoutInflater).show(
            "已加入到書庫，返回書庫？",
            positiveCallback = {
                startActivity(
                    Intent(baseContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
        )
    }
}