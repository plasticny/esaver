package com.example.viewer.dialog.SearchMarkDialog

import android.content.Context
import android.view.LayoutInflater
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category

class WnHandler (
    context: Context,
    layoutInflater: LayoutInflater,
    owner: SearchMarkDialog,
    ownerRootBinding: DialogSearchMarkBinding,
    private val name: String = "",
    categories: List<Category> = listOf()
): BaseHandler(context, layoutInflater, owner, ownerRootBinding) {
    override val source = BookSource.Wn

    override fun setupUi() {
        TODO("Not yet implemented")
    }

    override fun buildDialogData(): DialogData {
        TODO("Not yet implemented")
    }
}