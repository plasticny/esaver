package com.example.viewer.activity

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.viewer.R
import com.example.viewer.databinding.SearchActivityBinding
import com.example.viewer.databinding.SearchBookBinding
import com.example.viewer.dataset.SearchDataset
import com.example.viewer.dataset.SearchDataset.Companion.SearchMark
import com.example.viewer.dataset.SearchDataset.Companion.SearchMark.Companion.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class SearchActivity: AppCompatActivity() {
    companion object {
        data class BookRecord (
            val id: String,
            val url: String,
            val coverUrl: String,
            val cat: String,
            val title: String,
            val pageNum: Int,
            val tags: Map<String, List<String>>
        ): Parcelable {
            companion object CREATOR : Parcelable.Creator<BookRecord> {
                override fun createFromParcel(parcel: Parcel): BookRecord {
                    return BookRecord(parcel)
                }

                override fun newArray(size: Int): Array<BookRecord?> {
                    return arrayOfNulls(size)
                }
            }

            constructor(parcel: Parcel) : this(
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readString()!!,
                parcel.readInt(),
                parcel.readBundle(ClassLoader.getSystemClassLoader())!!.let { bundle ->
                    bundle.keySet().associateWith { bundle.getStringArray(it)!!.toList() }
                }
            )

            override fun writeToParcel(parcel: Parcel, flags: Int) {
                parcel.writeString(id)
                parcel.writeString(url)
                parcel.writeString(coverUrl)
                parcel.writeString(cat)
                parcel.writeString(title)
                parcel.writeInt(pageNum)
                parcel.writeBundle(Bundle().apply {
                    for ((key, value) in tags) {
                        putStringArray(key, value.toTypedArray())
                    }
                })
            }

            override fun describeContents(): Int {
                return 0
            }
        }
    }

    private lateinit var searchDataSet: SearchDataset
    private lateinit var searchMark: SearchMark
    private lateinit var binding: SearchActivityBinding
    private lateinit var allSearchMarkIds: List<Int>

    private var searchMarkId = -1
    private var position = -1
    private var next: String? = null

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
                searchMark = searchDataSet.getSearchMark(allSearchMarkIds[--position])
                next = null
                lifecycleScope.launch { resetUI() }
            }
        }
        binding.nextSearchMarkButton.apply {
            setOnClickListener {
                if (position == allSearchMarkIds.lastIndex) {
                    return@setOnClickListener
                }
                searchMark = searchDataSet.getSearchMark(allSearchMarkIds[++position])
                next = null
                lifecycleScope.launch { resetUI() }
            }
        }

        binding.loadMoreButton.apply {
            setOnClickListener {
                lifecycleScope.launch {
                    loadMoreBooks()
                    if (next == null){
                        visibility = Button.INVISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch { resetUI() }
    }

    private suspend fun resetUI () {
        binding.searchMarkName.text = searchMark.name

        binding.prevSearchMarkButton.visibility = if (position == 0) Button.INVISIBLE else Button.VISIBLE
        binding.nextSearchMarkButton.visibility = if (position == allSearchMarkIds.lastIndex) Button.INVISIBLE else Button.VISIBLE

        binding.searchBookWrapper.removeAllViews()

        binding.loadMoreButton.visibility = Button.INVISIBLE
        loadMoreBooks()
        if (next != null) {
            binding.loadMoreButton.visibility = Button.VISIBLE
        }
    }

    private suspend fun loadMoreBooks () {
        binding.searchProgressBar.visibility = ProgressBar.VISIBLE
        fetchBooks().let {
            binding.searchProgressBar.visibility = ProgressBar.GONE
            addBookViews(excludeTagFilter(it))
        }
    }

    private fun addBookViews (books: List<BookRecord>) {
        books.forEach { bookRecord ->
            binding.searchBookWrapper.addView(
                SearchBookBinding.inflate(layoutInflater, binding.searchBookWrapper, false).apply {
                    Glide.with(this.root).load(bookRecord.coverUrl).into(searchBookImageView)
                    searchBookTitleTextView.text = bookRecord.title
                    pageNumTextView.text = baseContext.getString(R.string.n_page, bookRecord.pageNum)
                    searchBookCatTextView.apply {
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
                    root.setOnClickListener {
                        val intent = Intent(baseContext, BookProfileActivity::class.java)
                        intent.putExtra("book_record", bookRecord)
                        startActivity(intent)
                    }
                }.root
            )
        }
    }

    /**
     * This method will access and change the private variable next
     */
    private suspend fun fetchBooks (): List<BookRecord> {
        val doc = withContext(Dispatchers.IO) {
            Jsoup.connect(
                searchMark.url(next).also { println("[SearchActivity.fetchBooks] $it") }
            ).get()
        }

        next = doc.selectFirst("#unext")!!.attribute("href")?.let { attr ->
            val tokens = attr.value.split("next=")
            if (tokens.size == 1) {
                return@let null
            }
            return@let tokens.last().trim()
        }

        val books = doc.select(".itg.glte > tbody > tr")
        return books.mapNotNull { book ->
            if (book.select(".itd").isNotEmpty()) {
                return@mapNotNull null
            }

            val url = book.selectFirst(".gl1e a")!!.attr("href")
            BookRecord(
                id = url.let {
                    (if (url.last() == '/') url.dropLast(1) else url)
                        .split("/").let {
                            it[it.lastIndex - 1]
                        }
                },
                url = url,
                coverUrl = book.selectFirst(".gl1e img")!!.attr("src"),
                cat = book.selectFirst(".cn")!!.text(),
                title = book.selectFirst(".glink")!!.text(),
                pageNum = book.select(".gl3e div").let { divs ->
                    for (div in divs.reversed()) {
                        val text = div.text()
                        if (text.endsWith(" pages")) {
                            return@let text.trim().split(' ').first().toInt()
                        }
                    }
                    throw Exception("page num is not found")
                },
                tags = mutableMapOf<String, List<String>>().apply {
                    book.select(".gl4e.glname table tr").forEach { tr ->
                        val cat = tr.selectFirst(".tc")!!.text().trim().dropLast(1)
                        set(cat, tr.select(".gt,.gtl").map { it.text().trim() })
                    }
                }
            )
        }
    }

    private fun SearchMark.url (next: String?): String {
        val fCatsValue = if (categories.isNotEmpty()) {
            1023 - categories.sumOf { getCategoryValue(it) }
        } else null

        val fSearchValue = if (tags.isNotEmpty()) {
            val tokens = mutableListOf<String>()
            tags.forEach { entry ->
                val cat = entry.key
                for (value in entry.value) {
                    if (value.contains(' ')) {
                        tokens.add("${cat}%3A\"${value}%24\"")
                    } else {
                        tokens.add("${cat}%3A${value}%24")
                    }
                }
            }
            tokens.joinToString(" ")
        } else null

        var ret = "https://e-hentai.org/"
        if (fCatsValue != null || fSearchValue != null) {
            ret += "?"
        }
        fCatsValue?.let { ret += "f_cats=$it&" }
        fSearchValue?.let { ret += "f_search=$fSearchValue&" }
        ret += "inline_set=dm_e&"
        next?.let { ret += "next=$next" }

        return ret
    }

    private fun getCategoryValue (cat: Category): Int = when(cat) {
        Category.Doujinshi -> 2
        Category.Manga -> 4
        Category.ArtistCG -> 8
    }

    private fun excludeTagFilter (books: List<BookRecord>): List<BookRecord> {
        val excludeTags = searchDataSet.getExcludeTag()
        return books.filter { book ->
            for (entry in book.tags) {
                val cat = entry.key
                if (!excludeTags.containsKey(cat)) {
                    continue
                }
                if (excludeTags.getValue(cat).intersect(entry.value.toSet()).isNotEmpty()) {
                    return@filter false
                }
            }
            true
        }
    }
}