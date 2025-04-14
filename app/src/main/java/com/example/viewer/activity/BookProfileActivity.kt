package com.example.viewer.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.viewer.BookAdder
import com.example.viewer.BookRecord
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
import com.example.viewer.database.SearchDatabase.Companion.SearchMark
import com.example.viewer.databinding.DialogTagBinding
import com.example.viewer.databinding.LocalReadSettingDialogBinding
import com.example.viewer.databinding.SelectAuthorDialogBinding
import com.example.viewer.dialog.ConfirmDialog
import com.example.viewer.dialog.SelectAuthorDialog
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

        rootBinding = BookProfileActivityBinding.inflate(layoutInflater).apply {
            Glide.with(baseContext).load(bookRecord.coverUrl).into(coverImageView)

            idTextView.text = bookRecord.id

            titleTextView.text = bookRecord.title

            pageNumTextView.text = baseContext.getString(R.string.n_page, bookRecord.pageNum)

            categoryTextView.apply {
                text = bookRecord.cat
                setTextColor(context.getColor(Util.categoryFromName(bookRecord.cat).color))
            }

            readButtonWrapper.setOnClickListener {
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

            saveButtonWrapper.apply {
                visibility = if (isBookStored) View.GONE else View.VISIBLE

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

            localSettingButtonWrapper.apply {
                visibility = if (isBookStored) View.VISIBLE else View.GONE
                setOnClickListener {
                    if (isBookStored) {
                        showReadSettingDialog(bookRecord.author!!, bookRecord.id)
                    }
                }
            }

            deleteButtonWrapper.apply {
                visibility = if (isBookStored) View.VISIBLE else View.GONE
                setOnClickListener {
                    if (!isBookStored) {
                        return@setOnClickListener
                    }
                    ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
                        getString(R.string.doDelete),
                        positiveCallback = {
                            toggleProgressBar(true)
                            CoroutineScope(Dispatchers.IO).launch {
                                deleteBook(bookRecord.author!!, bookRecord.id).let { retFlag ->
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

            tagWrapper.apply {
                lifecycleScope.launch {
                    for (entry in bookRecord.tags.entries) {
                        addView(createTagRow(entry.key, entry.value).root)
                    }
                }
            }
        }

        setContentView(rootBinding.root)
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

    private fun showReadSettingDialog (author: String, bookId: String) {
        val bookDatabase = BookDatabase.getInstance(baseContext)

        val dialogViewBinding = LocalReadSettingDialogBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()

        val authorEditText = dialogViewBinding.profileDialogAuthorEditText.apply {
            setText(author)
        }
        val coverPageEditText = dialogViewBinding.profileDialogCoverPageEditText.apply {
            setText((bookDatabase.getBookCoverPage(bookId) + 1).toString())
        }
        val skipPageEditText = dialogViewBinding.profileDialogSkipPagesEditText.apply {
            setText(bookDatabase.getBookSkipPages(bookId).joinToString(",") { (it + 1).toString() })
        }

        dialogViewBinding.searchAuthorButton.setOnClickListener {
            if (bookDatabase.getUserAuthors().isEmpty()) {
                Toast.makeText(baseContext, "沒有作者可以選擇", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SelectAuthorDialog(this@BookProfileActivity, layoutInflater).show {
                author -> authorEditText.setText(author)
            }
        }

        dialogViewBinding.profileDialogApplyButton.setOnClickListener {
            val authorText = authorEditText.text.toString().trim()
            val coverPageText = coverPageEditText.text.toString().trim()
            val skipPageText = skipPageEditText.text.toString().trim()

            if (authorText.isEmpty()) {
                Toast.makeText(baseContext, "作者不能為空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (authorText.isEmpty()) {
                Toast.makeText(baseContext, "封面頁不能為空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // update author and cover page
            if (authorText != author) {
                bookDatabase.changeAuthor(bookId, author, authorText)
            }
            coverPageText.toInt().let {
                if (it != bookDatabase.getBookCoverPage(bookId) + 1) {
                    bookDatabase.setBookCoverPage(bookId, it - 1)
                    refreshCoverPage()
                }
            }

            // update skip pages
            val newSkipPages = mutableListOf<Int>()
            for (token in skipPageText.split(',')) {
                try {
                    newSkipPages.add(token.trim().toInt() - 1)
                }
                catch (e: Exception) {
                    println("[reading skip page] token '$token' cannot convert into int")
                }
            }
            bookDatabase.setBookSkipPages(bookId, newSkipPages)

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTagDialog (category: String, value: String) {
        val dialogViewBinding = DialogTagBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()
        val translatedCategory = Util.TAG_TRANSLATION_MAP[category]

        dialogViewBinding.categoryTextView.text = translatedCategory

        dialogViewBinding.valueTextView.text = value

        dialogViewBinding.excludeButton.setOnClickListener {
            ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
                "濾除 $translatedCategory: $value ?",
                positiveCallback = { addFilterOutTag(category, value) }
            )
        }

        dialogViewBinding.searchButton.setOnClickListener {
            SearchActivity.startTmpSearch(
                this@BookProfileActivity,
                tags = mapOf(Pair(category, listOf(value)))
            )
        }

        dialog.show()
    }

    private fun addFilterOutTag (cat: String, value: String) {
        toggleProgressBar(true)
        CoroutineScope(Dispatchers.IO).launch {
            val searchDataset = SearchDatabase.getInstance(baseContext)
            val excludeTags = searchDataset.getExcludeTag().toMutableMap()
            val values = excludeTags[cat]?.toMutableList() ?: mutableListOf()

            if (!values.contains(value)) {
                excludeTags[cat] = values.apply { add(value) }
                searchDataset.storeExcludeTag(excludeTags)
            }

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

    private fun deleteBook (author: String, bookId: String): Boolean {
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