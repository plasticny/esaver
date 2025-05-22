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
import com.bumptech.glide.Glide
import com.example.viewer.struct.BookRecord
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.activity.main.MainActivity
import com.example.viewer.activity.viewer.LocalViewerActivity
import com.example.viewer.activity.viewer.OnlineViewerActivity
import com.example.viewer.databinding.BookProfileActivityBinding
import com.example.viewer.databinding.BookProfileTagBinding
import com.example.viewer.database.BookDatabase
import com.example.viewer.database.BookSource
import com.example.viewer.database.GroupDatabase
import com.example.viewer.database.SearchDatabase
import com.example.viewer.databinding.DialogBookInfoBinding
import com.example.viewer.databinding.DialogTagBinding
import com.example.viewer.dialog.ConfirmDialog
import com.example.viewer.dialog.EditExcludeTagDialog
import com.example.viewer.dialog.LocalReadSettingDialog
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.struct.ExcludeTagRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.min

/**
 * ParcelableExtra: book_record
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

    private lateinit var bookRecord: BookRecord
    private lateinit var rootBinding: BookProfileActivityBinding

    private var isBookStored: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookRecord = intent.getParcelableExtra("book_record", BookRecord::class.java)!!

        val bookDatabase = BookDatabase.getInstance(baseContext)
        isBookStored = bookDatabase.isBookStored(bookRecord.id)

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
            Glide.with(baseContext).load(bookRecord.coverUrl).into(it)
        }

        rootBinding.titleTextView.text = bookRecord.title

        rootBinding.warningContainer.apply {
            // only check warning if book is not stored
            lifecycleScope.launch {
                if (!isBookStored && EPictureFetcher.isBookWarning(bookRecord.url)) {
                    visibility = View.VISIBLE
                }
            }
        }

        rootBinding.pageNumTextView.text = baseContext.getString(R.string.n_page, bookRecord.pageNum)

        rootBinding.categoryTextView.apply {
            text = bookRecord.cat
            setTextColor(context.getColor(Util.categoryFromName(bookRecord.cat).color))
        }

        rootBinding.readButton.setOnClickListener {
            if (isBookStored) {
                BookDatabase.getInstance(baseContext).updateBookLastViewTime(bookRecord.id)
                startActivity(Intent(baseContext, LocalViewerActivity::class.java).apply {
                    putExtra("bookId", bookRecord.id)
                })
            } else {
                startActivity(Intent(baseContext, OnlineViewerActivity::class.java).apply {
                    putExtra("book_record", bookRecord)
                })
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
                        bookRecord,
                        onApplied = { coverPageUpdated ->
                            bookRecord = BookDatabase.getInstance(baseContext).getBook(baseContext, bookRecord.id)
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
                            deleteBook(bookRecord).let { retFlag ->
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
                for (entry in bookRecord.tags.entries) {
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

    private fun createTagRow (tagCat: String, tagValues: List<String>) =
        BookProfileTagBinding.inflate(layoutInflater).apply {
            tagCategoryTextView.text = Util.TAG_TRANSLATION_MAP[tagCat] ?: tagCat
            for (value in tagValues) {
                Button(baseContext).apply {
                    text = value
                    backgroundTintList = ColorStateList.valueOf(baseContext.getColor(R.color.dark_grey))
                    isAllCaps = false
                    setTextColor(baseContext.getColor(R.color.grey))
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

        dialogViewBinding.uploaderTextView.text = bookRecord.uploader ?: getString(R.string.noName)

        dialogViewBinding.urlTextView.text = bookRecord.url

        if (bookRecord.subtitle.isEmpty()) {
            dialogViewBinding.subtitle.visibility = View.GONE
        } else {
            dialogViewBinding.subtitle.text = bookRecord.subtitle
        }

        dialog.show()
    }

    private fun addFilterOutTag (tagCategory: String, tagValue: String) =
        EditExcludeTagDialog(this, layoutInflater).show(
            ExcludeTagRecord(
                mapOf(tagCategory to listOf(tagValue)),
                SearchDatabase.Companion.Category.entries.toSet()
            )
        ) { recordToSave ->
            toggleProgressBar(true)
            CoroutineScope(Dispatchers.IO).launch {
                SearchDatabase.getInstance(baseContext).addExcludeTag(recordToSave)
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
            Glide.with(baseContext)
                .load(
                    File(
                        "${getExternalFilesDir(null)}/${bookRecord.id}",
                        BookDatabase.getInstance(baseContext).getBookCoverPage(bookRecord.id).toString()
                    )
                )
                .into(rootBinding.coverImageView)
        }
    }

    private fun deleteBook (bookRecord: BookRecord): Boolean {
        BookDatabase.getInstance(baseContext).removeBook(bookRecord.id)
        GroupDatabase.getInstance(baseContext).removeBookIdFromGroup(bookRecord.groupId, bookRecord.id)

        val bookFolder = File(getExternalFilesDir(null), bookRecord.id)
        for (file in bookFolder.listFiles()!!) {
            file.delete()
        }
        bookFolder.delete()
        return true
    }

    private suspend fun saveBook () {
        if (isBookStored) {
            return
        }

        rootBinding.progress.textView.text = getString(R.string.n_percent, 0)
        toggleProgressBar(true)

        // download cover image file to tmp
        val coverFile = withContext(Dispatchers.IO) {
            EPictureFetcher(baseContext, 1, bookRecord.url).savePicture(0) { total, downloaded ->
                CoroutineScope(Dispatchers.Main).launch {
                    rootBinding.progress.textView.text = getString(
                        R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                    )
                }
            }
        }
        if (coverFile == null) {
            Toast.makeText(baseContext, "儲存失敗，再試一次", Toast.LENGTH_SHORT).show()
            toggleProgressBar(false)
            return
        }

        // create book folder
        File(getExternalFilesDir(null), bookRecord.id).also {
            if (!it.exists()) {
                it.mkdirs()
            }
            // move cover picture
            val file = File(it, coverFile.name)
            coverFile.copyTo(file)
            coverFile.delete()
        }

        BookDatabase.getInstance(baseContext).addBook(
            id = bookRecord.id,
            url = bookRecord.url,
            category = Util.categoryFromName(bookRecord.cat),
            title = bookRecord.title,
            subtitle = bookRecord.subtitle,
            pageNum = bookRecord.pageNum,
            tags = bookRecord.tags,
            source = BookSource.E,
            groupId = GroupDatabase.DEFAULT_GROUP_ID,
            uploader = bookRecord.uploader
        )
        GroupDatabase.getInstance(baseContext).addBookIdToGroup(GroupDatabase.DEFAULT_GROUP_ID, bookRecord.id)

        // update ui
        toggleProgressBar(false)
        isBookStored = true
        bookRecord = BookDatabase.getInstance(baseContext).getBook(baseContext, bookRecord.id)
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