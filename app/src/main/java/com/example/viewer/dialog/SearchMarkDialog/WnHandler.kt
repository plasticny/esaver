package com.example.viewer.dialog.SearchMarkDialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import com.example.viewer.R
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category

class WnHandler (
    context: Context,
    layoutInflater: LayoutInflater,
    owner: SearchMarkDialog,
    ownerRootBinding: DialogSearchMarkBinding,
    private val name: String = "",
    private var category: Category = Category.All
): BaseHandler(context, layoutInflater, owner, ownerRootBinding) {
    override val source = BookSource.Wn

    private val categoryButtons = mutableListOf<Button>()

    override fun setupUi() {
        owner.apply {
            showKeywordField = false
            showTagsField = false
            showUploaderField = false
            showDoExcludeField = false
        }

        // name field
        ownerRootBinding.nameEditText.setText(name)

        // book source
        ownerRootBinding.bookSourceEditText.setText(source.keyString)

        // category button
        ownerRootBinding.categoryWrapper.removeAllViews()
        categoryButtons.clear()
        listOf(
            Category.All, Category.Doujinshi, Category.Manga, Category.Magazine
        ).forEachIndexed { index, cat ->
            ownerRootBinding.categoryWrapper.addView(
                createCategoryButton(cat, index % 2).apply {
                    setOnClickListener {
                        if (category == cat) {
                            return@setOnClickListener
                        }
                        for (i in 0 until categoryButtons.size) {
                            categoryButtons[i].setBackgroundColor(context.getColor(
                                if (i == index) cat.color else R.color.grey
                            ))
                        }
                        category = cat
                    }
                    setBackgroundColor(context.getColor(
                        if (cat == category) cat.color else R.color.grey
                    ))
                }.also { categoryButtons.add(it) }
            )
        }
    }

    override fun buildDialogData(): DialogData =
        DialogData(
            name = ownerRootBinding.nameEditText.text.toString(),
            sourceOrdinal = source.ordinal,
            categories = setOf(category),
            keyword = "",
            tags = mapOf(),
            uploader = "",
            doExclude = true
        )
}