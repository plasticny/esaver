package com.example.viewer.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.viewer.BookAdder
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

/**
 * ParcelableExtra: book_record
 */
class BookProfileActivity: AppCompatActivity() {
    private lateinit var bookRecord: BookRecord
    private lateinit var rootBinding: BookProfileActivityBinding

    private var isBookStored: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookDatabase = BookDatabase.getInstance(baseContext)

        bookRecord = intent.getParcelableExtra("book_record", BookRecord::class.java)!!

        isBookStored = bookDatabase.isBookStored(bookRecord.id)

        rootBinding = BookProfileActivityBinding.inflate(layoutInflater)

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

        rootBinding.readButtonWrapper.setOnClickListener {
            if (isBookStored) {
                startActivity(Intent(baseContext, LocalViewerActivity::class.java).apply {
                    putExtra("bookId", bookRecord.id)
                })
            } else {
                startActivity(Intent(baseContext, OnlineViewerActivity::class.java).apply {
                    putExtra("book_record", bookRecord)
                })
            }
        }

        rootBinding.saveButtonWrapper.apply {
            setOnClickListener {
                if (isBookStored) {
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    toggleProgressBar(true)
                    BookAdder.getBookAdder(baseContext, BookSource.E).addBook(bookRecord.url) { doAdded ->
                        toggleProgressBar(false)
                        if (!doAdded) {
                            return@addBook
                        }
                        isBookStored = true
                        refreshActionBar()
                        ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
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
            }
        }

        rootBinding.localSettingButtonWrapper.apply {
            setOnClickListener {
                if (isBookStored) {
                    LocalReadSettingDialog(this@BookProfileActivity, layoutInflater).show(
                        bookRecord.id, bookRecord.author!!,
                        onApplied = { coverPageUpdated ->
                            if (coverPageUpdated) {
                                refreshCoverPage()
                            }
                        }
                    )
                }
            }
        }

        rootBinding.infoButtonWrapper.setOnClickListener {
            showInfoDialog()
        }

        rootBinding.deleteButtonWrapper.apply {
            setOnClickListener {
                if (!isBookStored) {
                    return@setOnClickListener
                }
                ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
                    getString(R.string.doDelete),
                    positiveCallback = {
                        toggleProgressBar(true)
                        CoroutineScope(Dispatchers.IO).launch {
                            deleteBook(bookRecord.id, bookRecord.author).let { retFlag ->
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
                    backgroundTintList = ColorStateList.valueOf(baseContext.getColor(R.color.darkgrey))
                    setTextColor(baseContext.getColor(R.color.grey))
                    setOnClickListener { showTagDialog(tagCat, value) }
                }.also { tagValueWrapper.addView(it) }
            }
        }

    private fun toggleProgressBar (toggle: Boolean) {
        rootBinding.progressBar.visibility = if (toggle) ProgressBar.VISIBLE else ProgressBar.GONE
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

        dialogViewBinding.urlTextView.text = bookRecord.url

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
            rootBinding.saveButtonWrapper.visibility = View.GONE
            rootBinding.localSettingButtonWrapper.visibility = View.VISIBLE
            rootBinding.deleteButtonWrapper.visibility = View.VISIBLE
        } else {
            rootBinding.saveButtonWrapper.visibility = View.VISIBLE
            rootBinding.localSettingButtonWrapper.visibility = View.GONE
            rootBinding.deleteButtonWrapper.visibility = View.GONE
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

    private fun deleteBook (bookId: String, author: String?): Boolean {
        val ret = BookDatabase.getInstance(baseContext).removeBook(bookId, author)
        if (ret) {
            val bookFolder = File(getExternalFilesDir(null), bookId)
            for (file in bookFolder.listFiles()!!) {
                file.delete()
            }
            bookFolder.delete()
        }
        return ret
    }
}