package com.example.viewer.activity

import android.os.Bundle
import android.view.RoundedCorner
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.databinding.SearchBookBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class BookRecord (
    val url: String,
    val coverUrl: String,
    val cat: String,
    val title: String,
    val tags: List<Tag>
)

data class Tag (
    val cat: String,
    val value: String
)

class SearchActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = SearchActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            binding.searchProgressBar.visibility = ProgressBar.VISIBLE
            val books = fetchBooks(intent.getStringExtra("searchUrl")!!)
            binding.searchProgressBar.visibility = ProgressBar.GONE

            books.forEach { book ->
                binding.searchBookWrapper.addView(
                    SearchBookBinding.inflate(layoutInflater, binding.searchBookWrapper, false).apply {
                        Glide.with(this.root).load(book.coverUrl).into(searchBookImageView)
                        searchBookTitleTextView.text = book.title
                        searchBookCatTextView.text = book.cat
                    }.root
                )
            }
        }
    }

    private suspend fun fetchBooks (searchUrl: String): List<BookRecord> {
        val doc = withContext(Dispatchers.IO) { Jsoup.connect(searchUrl).get() }
        val books = doc.select(".itg tr")
        return books.mapNotNull { book ->
            try {
                val cover = book.selectFirst(".glthumb img")!!
                BookRecord(
                    url = book.selectFirst(".glname > a")!!.attr("href"),
                    coverUrl = if (cover.hasAttr("data-src")) cover.attr("data-src") else cover.attr("src"),
                    cat = book.selectFirst(".glcat")!!.text(),
                    title = book.selectFirst(".glink")!!.text(),
                    tags = book.select(".gt").map {
                        val tokens = it.attr("title").split(':')
                        Tag(tokens[0], tokens[1])
                    }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}