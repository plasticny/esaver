package com.example.viewer.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.viewer.R
import com.example.viewer.activity.SearchActivity.Companion.BookRecord
import com.example.viewer.activity.viewer.OnlineViewerActivity
import com.example.viewer.databinding.BookProfileActivityBinding
import com.example.viewer.databinding.BookProfileTagBinding

class BookProfileActivity: AppCompatActivity() {
    companion object {
        private val TAG_CAT_TRANSLATION_MAP = mapOf(
            "artist" to "作者",
            "character" to "角色",
            "female" to "女性",
            "group" to "組別",
            "language" to "語言",
            "male" to "男性",
            "mixed" to "混合",
            "other" to "其他",
            "parody" to "原作"
        )
    }

    private lateinit var bookRecord: BookRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookRecord = intent.getParcelableExtra("book_record", BookRecord::class.java)!!

        val binding = BookProfileActivityBinding.inflate(layoutInflater).apply {
            Glide.with(baseContext).load(bookRecord.coverUrl).into(coverImageView)

            idTextView.text = bookRecord.id

            titleTextView.text = bookRecord.title

            pageNumTextView.text = baseContext.getString(R.string.n_page, bookRecord.pageNum)

            categoryTextView.apply {
                text = bookRecord.cat
                setTextColor(context.getColor(
                    when (bookRecord.cat) {
                        "Doujinshi" -> R.color.doujinshi_red
                        "Manga" -> R.color.manga_orange
                        "Artist CG" -> R.color.artistCG_yellow
                        else -> throw Exception("Unexpected category ${bookRecord.cat}")
                    }
                ))
            }

            readButton.setOnClickListener {
                val intent = Intent(baseContext, OnlineViewerActivity::class.java)
                intent.putExtra("book_record", bookRecord)
                startActivity(intent)
            }

            tagWrapper.apply {
                val groupedTags = bookRecord.tags.groupBy({it.cat}, {it.value})
                for (entry in groupedTags.entries) {
                    addView(createTagRow(entry.key, entry.value).root)
                }
            }
        }

        setContentView(binding.root)
    }

    private fun createTagRow (tagCat: String, tagValues: List<String>) =
        BookProfileTagBinding.inflate(layoutInflater).apply {
            tagCategoryTextView.text = TAG_CAT_TRANSLATION_MAP[tagCat] ?: tagCat
            for (value in tagValues) {
                Button(baseContext).apply {
                    text = value
                    backgroundTintList = ColorStateList.valueOf(baseContext.getColor(R.color.darkgrey))
                    setTextColor(baseContext.getColor(R.color.grey))
                }.also { tagValueWrapper.addView(it) }
            }
        }
}