package com.example.viewer.activity

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
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
        data class Tag (
            val cat: String,
            val value: String
        ): Parcelable {
            companion object CREATOR : Parcelable.Creator<Tag> {
                override fun createFromParcel(parcel: Parcel): Tag {
                    return Tag(parcel)
                }

                override fun newArray(size: Int): Array<Tag?> {
                    return arrayOfNulls(size)
                }
            }

            constructor(parcel: Parcel) : this(
                parcel.readString()!!,
                parcel.readString()!!
            )

            override fun describeContents(): Int {
                return 0
            }

            override fun writeToParcel(dest: Parcel, flags: Int) {
                dest.writeString(cat)
                dest.writeString(value)
            }
        }

        data class BookRecord (
            val id: String,
            val url: String,
            val coverUrl: String,
            val cat: String,
            val title: String,
            val pageNum: Int,
            val tags: List<Tag>
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
                parcel.createTypedArrayList(Tag.CREATOR)!!
            )

            override fun writeToParcel(parcel: Parcel, flags: Int) {
                parcel.writeString(id)
                parcel.writeString(url)
                parcel.writeString(coverUrl)
                parcel.writeString(cat)
                parcel.writeString(title)
                parcel.writeInt(pageNum)
                parcel.writeTypedList(tags)
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
                lifecycleScope.launch { refreshUi() }
            }
        }
        binding.nextSearchMarkButton.apply {
            setOnClickListener {
                if (position == allSearchMarkIds.lastIndex) {
                    return@setOnClickListener
                }
                searchMark = searchDataSet.getSearchMark(allSearchMarkIds[++position])
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

            books.forEach { bookRecord ->
                addView(
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
    }

    private suspend fun fetchBooks (): List<BookRecord> {
        val doc = withContext(Dispatchers.IO) { Jsoup.connect(searchMark.url()).get() }
        val books = doc.select(".itg tr")
        return books.mapNotNull { book ->
            val cover = book.selectFirst(".glthumb img") ?: return@mapNotNull null
            val url = book.selectFirst(".glname > a")!!.attr("href")

            BookRecord(
                id = url.let {
                    val tmp = if (url.last() == '/') url.dropLast(1) else url
                    tmp.split("/").let {
                        it[it.lastIndex - 1]
                    }
                },
                url = url,
                coverUrl = if (cover.hasAttr("data-src")) cover.attr("data-src") else cover.attr("src"),
                cat = book.selectFirst(".glcat")!!.text(),
                title = book.selectFirst(".glink")!!.text(),
                pageNum = book.select("div").let { divs ->
                    for (div in divs.reversed()) {
                        val text = div.text()
                        if (text.endsWith("pages")) {
                            return@let text.trim().split(' ').first().toInt()
                        }
                    }
                    throw Exception("page num is not found")
                },
                tags = book.select(".gt").map {
                    val tokens = it.attr("title").split(':')
                    Tag(tokens[0], tokens[1])
                }
            )
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