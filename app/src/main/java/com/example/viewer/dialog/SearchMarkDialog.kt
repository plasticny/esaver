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

class SearchMarkDialog (
    private val context: Context,
    private val layoutInflater: LayoutInflater,
) {
    companion object {
        private val TAGS = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.keys) }.toList()
        private val TAGS_DISPLAY = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.values) }.toList()
    }

    private val dialogBinding = SearchMarkDialogBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private var showNameField: Boolean = false
    private var selectedCats: MutableSet<Category> = mutableSetOf()
    private var tagBindings: MutableList<SearchMarkDialogTagBinding> = mutableListOf()

    fun show (
        title: String? = null,
        searchMark: SearchMark? = null,
        showNameField: Boolean = true,
        showSaveButton: Boolean = false,
        showSearchButton: Boolean = false,
        showConfirmButton: Boolean = false,
        saveCb: ((SearchMark) -> Unit)? = null,
        searchCb: ((SearchMark) -> Unit)? = null,
        confirmCb: ((SearchMark) -> Unit)? = null
    ) {
        this.showNameField = showNameField
        selectedCats = searchMark?.categories?.toMutableSet() ?: Category.entries.toMutableSet()
        tagBindings = mutableListOf()

        dialogBinding.titleTextView.apply {
            if (title == null) {
                visibility = View.GONE
            } else {
                text = title
            }
        }

        // name field
        dialogBinding.nameFieldContainer.visibility = if (showNameField) View.VISIBLE else View.GONE
        dialogBinding.nameEditText.setText(searchMark?.name ?: "")

        // category buttons
        listOf(
            Pair(dialogBinding.catDoujinshi, Category.Doujinshi),
            Pair(dialogBinding.catManga, Category.Manga),
            Pair(dialogBinding.catArtistCg, Category.ArtistCG),
            Pair(dialogBinding.catNonH, Category.NonH)
        ).forEach { (view, category) ->
            view.apply {
                val selectedColor = context.getColor(category.color)
                val deselectedColor = context.getColor(R.color.grey)
                setBackgroundColor(
                    if (selectedCats.contains(category)) selectedColor else deselectedColor
                )
                setOnClickListener {
                    setBackgroundColor(
                        if (selectedCats.toggle(category)) selectedColor else deselectedColor
                    )
                }
            }
        }

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
        dialogBinding.addTagButton.apply {
            setOnClickListener {
                val tagBinding = createSearchMarkDialogTag()
                tagBindings.add(tagBinding)
                dialogBinding.tagWrapper.addView(tagBinding.root, 0)
            }
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.saveButton.apply {
            if (showSaveButton) {
                visibility = View.VISIBLE
                setOnClickListener {
                    saveCb?.let {
                        if (!checkValid()) {
                            return@setOnClickListener
                        }
                        it(constructSearchMark())
                    }
                    dialog.dismiss()
                }
            } else {
                visibility = View.GONE
            }
        }

        dialogBinding.searchButton.apply {
            if (showSearchButton) {
                visibility = View.VISIBLE
                setOnClickListener {
                    searchCb?.let {
                        if (!checkValid()) {
                            return@setOnClickListener
                        }
                        it(constructSearchMark())
                    }
                    dialog.dismiss()
                }
            } else {
                visibility = View.GONE
            }
        }

        dialogBinding.confirmButton.apply {
            if (showConfirmButton) {
                visibility = View.VISIBLE
                setOnClickListener {
                    confirmCb?.let {
                        if (!checkValid()) {
                            return@setOnClickListener
                        }
                        it(constructSearchMark())
                    }
                    dialog.dismiss()
                }
            } else {
                visibility = View.GONE
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
    private fun checkValid (): Boolean {
        if (showNameField && dialogBinding.nameEditText.text.isEmpty()) {
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