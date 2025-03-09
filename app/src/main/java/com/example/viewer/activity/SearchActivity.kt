package com.example.viewer.activity

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.databinding.SearchBookBinding
import com.example.viewer.dataset.SearchDataset
import com.example.viewer.dataset.SearchDataset.Companion.SearchMark
import com.example.viewer.dataset.SearchDataset.Companion.SearchMark.Companion.Category
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
    private lateinit var searchDataSet: SearchDataset
    private lateinit var searchMark: SearchMark
    private lateinit var binding: SearchActivityBinding
    private lateinit var allSearchMarkIds: List<Int>

    private var searchMarkId = -1
    private var position = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchDataSet = SearchDataset.getInstance(baseContext)
        allSearchMarkIds = searchDataSet.getAllSearchMarkIds()

        searchMarkId = intent.getIntExtra("searchMarkId", -1)
        searchMark = searchDataSet.getSearchMark(searchMarkId)

        position = allSearchMarkIds.indexOf(searchMarkId)

        binding = SearchActivityBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.prevSearchMarkButton.apply {
            setOnClickListener {
                if (position == 0) {
                    return@setOnClickListener
                }
                searchMark = searchDataSet.getSearchMark(allSearchMarkIds[--position])!!
                lifecycleScope.launch { refreshUi() }
            }
        }
        binding.nextSearchMarkButton.apply {
            setOnClickListener {
                if (position == allSearchMarkIds.lastIndex) {
                    return@setOnClickListener
                }
                searchMark = searchDataSet.getSearchMark(allSearchMarkIds[++position])!!
                lifecycleScope.launch { refreshUi() }
            }
        }

        lifecycleScope.launch { refreshUi() }
    }

    private suspend fun refreshUi () {
        binding.searchMarkName.text = searchMark.name

        binding.prevSearchMarkButton.visibility = if (position == 0) Button.INVISIBLE else Button.VISIBLE
        binding.nextSearchMarkButton.visibility = if (position == allSearchMarkIds.lastIndex) Button.INVISIBLE else Button.VISIBLE

        binding.searchBookWrapper.apply {
            removeAllViews()

            binding.searchProgressBar.visibility = ProgressBar.VISIBLE
            val books = fetchBooks()
            binding.searchProgressBar.visibility = ProgressBar.GONE

            books.forEach { book ->
                addView(
                    SearchBookBinding.inflate(layoutInflater, binding.searchBookWrapper, false).apply {
                        Glide.with(this.root).load(book.coverUrl).into(searchBookImageView)
                        searchBookTitleTextView.text = book.title
                        searchBookCatTextView.text = book.cat
                    }.root
                )
            }
        }
    }

    private suspend fun fetchBooks (): List<BookRecord> {
        val doc = withContext(Dispatchers.IO) { Jsoup.connect(searchMark.url()).get() }
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

    private fun SearchMark.url (): String {
        val fCatsValue = if (categories.isNotEmpty()) {
            1023 - categories.sumOf { getCategoryValue(it) }
        } else null

        val fSearchValue = if (tags.isNotEmpty()) {
            tags.groupBy({it.first}, {it.second}).map {
                val value = it.value.joinToString(" ") { tagValue -> "\"$tagValue\"" }
                "${it.key}%3A$value"
            }.joinToString(" ")
        } else null

        var ret = "https://e-hentai.org/"
        if (fCatsValue != null || fSearchValue != null) {
            ret += "?"
        }
        if (fCatsValue != null) {
            ret += "f_cats=$fCatsValue&"
        }
        if (fSearchValue != null) {
            ret += "f_search=$fSearchValue%24"
        }
        return ret
    }

    private fun getCategoryValue (cat: Category): Int = when(cat) {
        Category.Doujinshi -> 2
        Category.Manga -> 4
        Category.ArtistCG -> 8
    }
}