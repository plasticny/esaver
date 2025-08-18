package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.databinding.DialogSearchMarkTagBinding
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category

open class SearchMarkDialog (
    protected val context: Context,
    private val layoutInflater: LayoutInflater,
) {
    companion object {
        private const val DIALOG_HEIGHT_PERCENT = 0.6

        private val TAGS = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.keys) }.toList()
        private val TAGS_DISPLAY = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.values) }.toList()
    }

    private val dialogBinding = DialogSearchMarkBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private var selectedCats: MutableSet<Category> = mutableSetOf()
    private var tagBindings: MutableList<DialogSearchMarkTagBinding> = mutableListOf()
    private lateinit var bookSource: BookSource

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
    var showUploaderField: Boolean = true
        set (value) {
            field = value
            dialogBinding.uploaderFieldContainer.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showDoExcludeField: Boolean = true
        set (value) {
            field = value
            dialogBinding.doExcludeWrapper.visibility = if (value) View.VISIBLE else View.GONE
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
    var saveCb: ((DialogData) -> Unit)? = null
    var searchCb: ((DialogData) -> Unit)? = null
    var confirmCb: ((DialogData) -> Unit)? = null

    init {
        // set dialog height
        dialogBinding.mainWrapper.apply {
            layoutParams = layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * DIALOG_HEIGHT_PERCENT).toInt()
            }
        }

        dialogBinding.titleTextView.visibility = View.GONE
        dialogBinding.nameFieldContainer.visibility = View.VISIBLE

        // book source edittext

        dialogBinding.bookSourceEditText.apply {
            setOnClickListener {
                BookSourceSelectDialog(context, layoutInflater).show { source ->
                    bookSource = source
                    setText(source.keyString)
                }
            }
        }

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
                val data = buildDialogData()
                saveCb?.let {
                    if (!checkValid(data)) {
                        return@setOnClickListener
                    }
                    it(data)
                }
                dialog.dismiss()
            }
        }

        dialogBinding.searchButton.apply {
            visibility = View.GONE
            setOnClickListener {
                val data = buildDialogData()
                searchCb?.let {
                    if (!checkValid(data)) {
                        return@setOnClickListener
                    }
                    it(data)
                }
                dialog.dismiss()
            }
        }

        dialogBinding.confirmButton.apply {
            visibility = View.GONE
            setOnClickListener {
                val data = buildDialogData()
                confirmCb?.let {
                    if (!checkValid(data)) {
                        return@setOnClickListener
                    }
                    it(data)
                }
                dialog.dismiss()
            }
        }
    }

    fun show (searchMark: SearchMark) =
        show(
            name = searchMark.name,
            sourceOrdinal = searchMark.sourceOrdinal,
            categories = searchMark.getCategories(),
            keyword = searchMark.keyword,
            tags = searchMark.getTags(),
            uploader = searchMark.uploader ?: "",
            doExclude = searchMark.doExclude
        )

    fun show (
        name: String = "",
        sourceOrdinal: Int,
        categories: List<Category> = listOf(),
        keyword: String = "",
        tags: Map<String, List<String>> = mapOf(),
        uploader: String = "",
        doExclude: Boolean = true
    ) {
        selectedCats = categories.toMutableSet()
        tagBindings = mutableListOf()

        // name field
        dialogBinding.nameEditText.setText(name)

        // book source
        bookSource = BookSource.entries[sourceOrdinal]
        dialogBinding.bookSourceEditText.setText(bookSource.keyString)

        // keyword
        dialogBinding.keywordEditText.setText(keyword)

        // uploader
        dialogBinding.uploaderEditText.setText(uploader)

        // tags
        tags.forEach { entry ->
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

        // do apply exclude tag
        dialogBinding.doExcludeSwitch.isChecked = doExclude

        dialog.show()
    }

    private fun createSearchMarkDialogTag (cat: String? = null, value: String? = null) =
        DialogSearchMarkTagBinding.inflate(layoutInflater).apply {
            spinner.apply {
                setItems(TAGS_DISPLAY)
                cat?.let { selectedIndex = TAGS.indexOf(it) }
            }
            value?.let { editText.setText(it) }
        }

    /**
     * check the filled in information is valid
     */
    protected open fun checkValid (item: DialogData): Boolean {
        if (showNameField && item.name.isEmpty()) {
            Toast.makeText(context, "名字不能為空", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun buildDialogData () = DialogData (
        name = if (showNameField) dialogBinding.nameEditText.text.toString() else "",
        sourceOrdinal = bookSource.ordinal,
        categories = selectedCats,
        keyword = dialogBinding.keywordEditText.text.toString(),
        tags = tagBindings.mapNotNull {
            if (it.spinner.selectedIndex == 0) {
                return@mapNotNull null
            }
            TAGS[it.spinner.selectedIndex] to it.editText.text.toString()
        }.groupBy({it.first}, {it.second}),
        uploader = dialogBinding.uploaderEditText.text.toString(),
        doExclude = dialogBinding.doExcludeSwitch.isChecked
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

    data class DialogData (
        val name: String,
        val sourceOrdinal: Int,
        val categories: Set<Category>,
        val keyword: String,
        val tags: Map<String, List<String>>,
        val uploader: String,
        val doExclude: Boolean
    )
}