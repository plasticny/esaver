package com.example.viewer.dialog.SearchMarkDialog

import android.content.Context
import android.view.LayoutInflater
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemSource

class RuHandler (
    context: Context,
    layoutInflater: LayoutInflater,
    owner: SearchMarkDialog,
    ownerRootBinding: DialogSearchMarkBinding,
    private val name: String = "",
    private val keyword: String
): BaseHandler(context, layoutInflater, owner, ownerRootBinding) {
    override val source: ItemSource = ItemSource.Ru

    override fun setupUi() {
        owner.apply {
            showCategoryField = false
            showTagsField = false
            showUploaderField = false
            showDoExcludeField = false
        }

        ownerRootBinding.nameEditText.setText(name)

        ownerRootBinding.run {
            bookSourceEditText.setText(source.keyString)
            keywordEditText.setText(keyword)
        }
    }

    override fun buildDialogData(): DialogData =
        DialogData (
            name = ownerRootBinding.nameEditText.text.toString(),
            sourceOrdinal = source.ordinal,
            categories = setOf(Category.Doujinshi),
            keyword = ownerRootBinding.keywordEditText.text.toString(),
            tags = mapOf(),
            uploader = "",
            doExclude = false
        )
}