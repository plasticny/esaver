package com.example.viewer.activity.main

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.viewer.R
import com.example.viewer.databinding.MainSearchFragmentBinding
import com.example.viewer.databinding.SearchMarkDialogBinding
import com.example.viewer.databinding.SearchMarkDialogTagBinding
import java.io.Serializable

data class SearchMark (
    val name: String,
    val categories: List<Category>,
    val tags: List<Pair<String, String>>
): Serializable {
    companion object {
        enum class Category {
            Doujinshi { override val value = 2 },
            Manga { override val value = 4 },
            ArtistCG { override val value = 8 };
            abstract val value: Int;
        }
        fun categoryFromString (s: String): Category {
            return when (s) {
                "Doujinshi" -> Category.Doujinshi
                "Manga" -> Category.Manga
                else -> throw Exception("unexpected category name")
            }
        }
    }
}

class SearchFragment: Fragment() {
    companion object {
        private val TAGS = mapOf(
            "-" to "-",
            "女性" to "female",
            "原作" to "parody",
            "其他" to "other"
        )
    }

    private lateinit var parent: ViewGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        parent = container!!
        val binding = MainSearchFragmentBinding.inflate(layoutInflater, parent, false)

        val searchMark = SearchMark(
            name = "emotionless",
            categories = listOf(SearchMark.Companion.Category.Doujinshi, SearchMark.Companion.Category.Manga),
            tags = listOf("female" to "emotionless sex")
        )

        binding.testButton.setOnClickListener {
            openSearchMarkDialog { retSearchMark ->
                println(searchMarkToUrl(retSearchMark))
            }
//            parent.context.startActivity(Intent(parent.context, SearchActivity::class.java).apply {
//                putExtra("searchUrl", searchMarkToUrl(searchMark))
//            })
        }

        return binding.root
    }

    private fun openSearchMarkDialog (cb: ((SearchMark) -> Unit)? = null) {
        val dialogBinding = SearchMarkDialogBinding.inflate(layoutInflater, parent, false)
        val dialog = AlertDialog.Builder(parent.context)
            .setView(dialogBinding.root)
            .create()

        val selectedCats = mutableSetOf<SearchMark.Companion.Category>()
        dialogBinding.catDoujinshi.apply {
            setOnClickListener {
                val selected = selectedCats.toggle(SearchMark.Companion.Category.Doujinshi)
                setBackgroundColor(if (selected) context.getColor(R.color.doujinshi_red) else context.getColor(R.color.grey))
            }
        }
        dialogBinding.catManga.apply {
            setOnClickListener {
                val selected = selectedCats.toggle(SearchMark.Companion.Category.Manga)
                setBackgroundColor(if (selected) context.getColor(R.color.manga_orange) else context.getColor(R.color.grey))
            }
        }
        dialogBinding.catArtistCg.apply {
            setOnClickListener {
                val selected = selectedCats.toggle(SearchMark.Companion.Category.ArtistCG)
                setBackgroundColor(if (selected) context.getColor(R.color.artistCG_yellow) else context.getColor(R.color.grey))
            }
        }

        val tagBindings = mutableListOf<SearchMarkDialogTagBinding>()
        dialogBinding.addTagButton.apply {
            setOnClickListener {
                val view = SearchMarkDialogTagBinding.inflate(layoutInflater, dialogBinding.tagWrapper, false).apply {
                    spinner.apply {
                        adapter = ArrayAdapter(context, R.layout.white_spinner_item, TAGS.keys.toList()).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        // make the small triangle white
                        background.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_ATOP)
                    }
                }.also {
                    tagBindings.add(it)
                }.root
                dialogBinding.tagWrapper.addView(view, 0)
            }
        }

        dialogBinding.saveButton.apply {
            setOnClickListener {
                if (dialogBinding.nameEditText.text.isEmpty()) {
                    Toast.makeText(context, "名字不能為空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val retSearchMark = SearchMark(
                    name = dialogBinding.nameEditText.text.toString(),
                    categories = selectedCats.toList(),
                    tags = tagBindings.mapNotNull {
                        val spinnerValue = it.spinner.selectedItem.toString()
                        if (spinnerValue == "-") {
                            return@mapNotNull null
                        }
                        TAGS.getValue(spinnerValue) to it.editText.text.toString()
                    }
                )
                cb?.invoke(retSearchMark)

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun searchMarkToUrl (searchMark: SearchMark): String {
        val fCatsValue = if (searchMark.categories.isNotEmpty()) {
            1023 - searchMark.categories.sumOf { it.value }
        } else null

        val fSearchValue = if (searchMark.tags.isNotEmpty()) {
            searchMark.tags.groupBy({it.first}, {it.second}).map {
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

    private fun <T> MutableSet<T>.toggle(value: T): Boolean {
        return if (this.contains(value)) {
            this.remove(value)
            false
        } else {
            this.add(value)
        }
    }
}