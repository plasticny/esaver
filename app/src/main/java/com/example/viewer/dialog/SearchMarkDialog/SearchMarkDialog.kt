package com.example.viewer.dialog.SearchMarkDialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.viewer.data.struct.search.SearchMark
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.dialog.BookSourceSelectDialog
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category

open class SearchMarkDialog (
    protected val context: Context,
    private val layoutInflater: LayoutInflater,
) {
    companion object {
        private const val DIALOG_HEIGHT_PERCENT = 0.6
    }

    lateinit var dialogHandler: BaseHandler

    private val dialogBinding = DialogSearchMarkBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

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
    var showCategoryField: Boolean = true
        set (value) {
            field = value
            dialogBinding.categoryFieldWrapper.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showKeywordField: Boolean = true
        set (value) {
            field = value
            dialogBinding.keywordFieldContainer.visibility = if (value) View.VISIBLE else View.GONE
        }
    var showTagsField: Boolean = true
        set(value) {
            field = value
            dialogBinding.tagsFieldContainer.visibility = if (value) View.VISIBLE else View.GONE
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

        dialogBinding.bookSourceEditText.apply {
            setOnClickListener {
                BookSourceSelectDialog(context, layoutInflater).show { source ->
                    val dialogData = dialogHandler.dialogData
                    when (source) {
                        BookSource.E -> showESearchMark(
                            dialogData.name,
                            dialogData.categories.toList(),
                            dialogData.keyword,
                            dialogData.tags,
                            dialogData.uploader,
                            dialogData.doExclude
                        )
                        BookSource.Wn -> showWnSearchMark(
                            dialogData.name,
                            Category.All,
                            dialogData.keyword
                        )
                        BookSource.Ru -> showRuSearchMark(
                            dialogData.keyword
                        )
                        BookSource.Hi -> throw IllegalArgumentException("unexpected source ordinal")
                    }
                }
            }
        }

        dialogBinding.addTagButton.apply {
            setOnClickListener {
                val tagBinding = dialogHandler.createSearchMarkDialogTag()
                dialogBinding.tagWrapper.addView(tagBinding.root, 0)
            }
        }

        dialogBinding.saveButton.apply {
            visibility = View.GONE
            setOnClickListener {
                val data = dialogHandler.dialogData
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
                val data = dialogHandler.dialogData
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
                val data = dialogHandler.dialogData
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
        when (searchMark.sourceOrdinal) {
            BookSource.E.ordinal -> showESearchMark(
                searchMark.name,
                searchMark.getCategories(),
                searchMark.keyword,
                searchMark.getTags(),
                searchMark.uploader ?: "",
                searchMark.doExclude
            )
            BookSource.Wn.ordinal -> showWnSearchMark(
                name = searchMark.name,
                category = searchMark.getCategories().also { assert(it.size == 1) }.first(),
                keyword = searchMark.keyword
            )
            else -> throw IllegalArgumentException("unexpected source ordinal")
        }


    fun showESearchMark (
        name: String = "",
        categories: List<Category> = listOf(),
        keyword: String = "",
        tags: Map<String, List<String>> = mapOf(),
        uploader: String = "",
        doExclude: Boolean = true
    ) {
        dialogHandler = EHandler(
            context, layoutInflater,
            this, dialogBinding,
            name, categories, keyword, tags, uploader, doExclude
        ).also { it.setupUi() }
        dialog.show()
    }

    fun showWnSearchMark (
        name: String = "",
        category: Category = Category.All,
        keyword: String = ""
    ) {
        dialogHandler = WnHandler(
            context, layoutInflater,
            this, dialogBinding,
            name, category, keyword
        ).also { it.setupUi() }
        dialog.show()
    }

    fun showRuSearchMark (
        keyword: String = ""
    ) {
        dialogHandler = RuHandler(
            context, layoutInflater,
            this, dialogBinding,
            keyword
        ).also { it.setupUi() }
        dialog.show()
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
}