package com.example.viewer.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.viewer.BookAdder
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.activity.SearchActivity.Companion.BookRecord
import com.example.viewer.activity.main.MainActivity
import com.example.viewer.activity.viewer.LocalViewerActivity
import com.example.viewer.activity.viewer.OnlineViewerActivity
import com.example.viewer.databinding.BookProfileActivityBinding
import com.example.viewer.databinding.BookProfileTagBinding
import com.example.viewer.database.BookDatabase
import com.example.viewer.database.BookSource
import com.example.viewer.database.SearchDatabase
import com.example.viewer.database.SearchDatabase.Companion.Category
import com.example.viewer.dialog.ConfirmDialog
import kotlinx.coroutines.launch

class BookProfileActivity: AppCompatActivity() {
    private lateinit var bookRecord: BookRecord
    private lateinit var rootBinding: BookProfileActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookRecord = intent.getParcelableExtra("book_record", BookRecord::class.java)!!

        rootBinding = BookProfileActivityBinding.inflate(layoutInflater).apply {
            Glide.with(baseContext).load(bookRecord.coverUrl).into(coverImageView)

            idTextView.text = bookRecord.id

            titleTextView.text = bookRecord.title

            pageNumTextView.text = baseContext.getString(R.string.n_page, bookRecord.pageNum)

            categoryTextView.apply {
                text = bookRecord.cat
                setTextColor(context.getColor(Category.fromString(bookRecord.cat).color))
            }

            readButton.setOnClickListener {
                val bookDataset = BookDatabase.getInstance(baseContext)
                if (bookDataset.getAllBookIds().contains(bookRecord.id)) {
                    startActivity(Intent(baseContext, LocalViewerActivity::class.java).apply {
                        putExtra("bookId", bookRecord.id)
                    })
                } else {
                    startActivity(Intent(baseContext, OnlineViewerActivity::class.java).apply {
                        putExtra("book_record", bookRecord)
                    })
                }
            }

            saveButton.setOnClickListener {
                val bookAdder = BookAdder.getBookAdder(baseContext, BookSource.E)
                lifecycleScope.launch {
                    toggleProgressBar(true)
                    bookAdder.addBook(bookRecord.url) { doAdded ->
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

    private fun createTagRow (tagCat: String, tagValues: List<String>) =
        BookProfileTagBinding.inflate(layoutInflater).apply {
            tagCategoryTextView.text = Util.TAG_TRANSLATION_MAP[tagCat] ?: tagCat
            for (value in tagValues) {
                Button(baseContext).apply {
                    text = value
                    backgroundTintList = ColorStateList.valueOf(baseContext.getColor(R.color.darkgrey))
                    setTextColor(baseContext.getColor(R.color.grey))
                    setOnClickListener {
                        ConfirmDialog(this@BookProfileActivity, layoutInflater).show(
                            "濾除 $tagCat:$value ?",
                            positiveCallback = {
                                addFilterOutTag(tagCat, value)
                                this@BookProfileActivity.finish()
                            }
                        )
                    }
                }.also { tagValueWrapper.addView(it) }
            }
        }

    private fun toggleProgressBar (toggle: Boolean) {
        rootBinding.progressBar.visibility = if (toggle) ProgressBar.VISIBLE else ProgressBar.GONE
    }

    private fun addFilterOutTag (cat: String, value: String) {
        val searchDataset = SearchDatabase.getInstance(baseContext)
        searchDataset.getExcludeTag().toMutableMap().apply {
            val values = get(cat)?.toMutableList() ?: mutableListOf()
            values.add(value)
            set(cat, values)
        }.also {
            searchDataset.storeExcludeTag(it)
        }
    }
}