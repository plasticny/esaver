package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.database.SearchDatabase.Companion.Category
import com.example.viewer.database.SearchDatabase.Companion.SearchMark
import com.example.viewer.databinding.SearchMarkDialogBinding
import com.example.viewer.databinding.SearchMarkDialogTagBinding

open class SearchMarkDialog (
    protected val context: Context,
    private val layoutInflater: LayoutInflater,
) {
    companion object {
        private val TAGS = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.keys) }.toList()
        private val TAGS_DISPLAY = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.values) }.toList()
    }

    private val dialogBinding = SearchMarkDialogBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private var selectedCats: MutableSet<Category> = mutableSetOf()
    private var tagBindings: MutableList<SearchMarkDialogTagBinding> = mutableListOf()

    var title: String = ""
        set (value) {
            field = value
            dialogBinding.titleTextView.apply {
                if (value.isEmpty()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = value
                }
            }
        }
    var showNameField: Boolean = true
        set (value) {
            field = value
            dialogBinding.nameFieldContainer.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showKeywordField: Boolean = true
        set (value) {
            field = value
            dialogBinding.keywordFieldContainer.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showSaveButton: Boolean = false
        set (value) {
            field = value
            dialogBinding.saveButton.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showSearchButton: Boolean = false
        set (value) {
            field = value
            dialogBinding.searchButton.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showConfirmButton: Boolean = false
        set (value) {
            field = value
            dialogBinding.confirmButton.visibility = if (value) View.VISIBLE else View.GONE
        }
    var saveCb: ((SearchMark) -> Unit)? = null
    var searchCb: ((SearchMark) -> Unit)? = null
    var confirmCb: ((SearchMark) -> Unit)? = null

    init {
        dialogBinding.titleTextView.visibility = View.GONE
        dialogBinding.nameFieldContainer.visibility = View.VISIBLE

        // category buttons
        listOf(
            Pair(dialogBinding.catDoujinshi, Category.Doujinshi),
            Pair(dialogBinding.catManga, Category.Manga),
            Pair(dialogBinding.catArtistCg, Category.ArtistCG),
            Pair(dialogBinding.catNonH, Category.NonH)
        ).forEach { (view, category) ->
            view.apply {
                setOnClickListener {
                    setBackgroundColor(context.getColor(
                        if (selectedCats.toggle(category)) category.color else R.color.grey
                    ))
                }
            }
        }

        dialogBinding.addTagButton.apply {
            setOnClickListener {
                val tagBinding = createSearchMarkDialogTag()
                tagBindings.add(tagBinding)
                dialogBinding.tagWrapper.addView(tagBinding.root, 0)
            }
        }

        dialogBinding.saveButton.apply {
            visibility = View.GONE
            setOnClickListener {
                val searchMark = constructSearchMark()
                saveCb?.let {
                    if (!checkValid(searchMark)) {
                        return@setOnClickListener
                    }
                    it(searchMark)
                }
                dialog.dismiss()
            }
        }

        dialogBinding.searchButton.apply {
            visibility = View.GONE
            setOnClickListener {
                val searchMark = constructSearchMark()
                searchCb?.let {
                    if (!checkValid(searchMark)) {
                        return@setOnClickListener
                    }
                    it(searchMark)
                }
                dialog.dismiss()
            }
        }

        dialogBinding.confirmButton.apply {
            visibility = View.GONE
            setOnClickListener {
                val searchMark = constructSearchMark()
                confirmCb?.let {
                    if (!checkValid(searchMark)) {
                        return@setOnClickListener
                    }
                    it(searchMark)
                }
                dialog.dismiss()
            }
        }
    }

    fun show (searchMark: SearchMark? = null) {
        selectedCats = searchMark?.categories?.toMutableSet() ?: Category.entries.toMutableSet()
        tagBindings = mutableListOf()

        // name field
        dialogBinding.nameEditText.setText(searchMark?.name ?: "")

        // keyword
        dialogBinding.keywordEditText.setText(searchMark?.keyword ?: "")

        // tags
        searchMark?.tags?.forEach { entry ->
            val cat = entry.key
            for (value in entry.value) {
                val tagBinding = createSearchMarkDialogTag(cat, value)
                tagBindings.add(tagBinding)
                dialogBinding.tagWrapper.addView(tagBinding.root)
            }
        }

        // category button
        listOf(
            Pair(dialogBinding.catDoujinshi, Category.Doujinshi),
            Pair(dialogBinding.catManga, Category.Manga),
            Pair(dialogBinding.catArtistCg, Category.ArtistCG),
            Pair(dialogBinding.catNonH, Category.NonH)
        ).forEach { (view, category) ->
            view.apply {
                setBackgroundColor(context.getColor(
                    if (selectedCats.contains(category)) category.color else R.color.grey
                ))
            }
        }

        dialog.show()
    }

    private fun createSearchMarkDialogTag (cat: String? = null, value: String? = null) =
        SearchMarkDialogTagBinding.inflate(layoutInflater).apply {
            spinner.apply {
                setItems(TAGS_DISPLAY)
                cat?.let { selectedIndex = TAGS.indexOf(it) }
            }
            value?.let { editText.setText(it) }
        }

    /**
     * check the filled in information is valid
     */
    protected open fun checkValid (item: SearchMark): Boolean {
        if (showNameField && item.name.isEmpty()) {
            Toast.makeText(context, "名字不能為空", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * construct a search mark base on what user filled
     */
    private fun constructSearchMark (): SearchMark =
        SearchMark(
            name = if (showNameField) dialogBinding.nameEditText.text.toString() else "",
            categories = selectedCats.toList(),
            keyword = dialogBinding.keywordEditText.text.toString(),
            tags = tagBindings.mapNotNull {
                if (it.spinner.selectedIndex == 0) {
                    return@mapNotNull null
                }
                TAGS[it.spinner.selectedIndex] to it.editText.text.toString()
            }.groupBy({it.first}, {it.second})
        )

    /**
     * Given a value, add it if it not exist, else remove it
     *
     * @return boolean represent that the value is in the set after the operation
     */
    private fun <T> MutableSet<T>.toggle(value: T): Boolean {
        return if (this.contains(value)) {
            this.remove(value)
            false
        } else {
            this.add(value)
        }
    }
}