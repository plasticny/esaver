package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.viewer.databinding.DialogListItemBinding
import com.example.viewer.databinding.DialogSelectBookSourceBinding
import com.example.viewer.struct.ItemSource

class BookSourceSelectDialog (context: Context, layoutInflater: LayoutInflater) {
    private val dialogBinding = DialogSelectBookSourceBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private lateinit var cb: (selectedSource: ItemSource) -> Unit

    init {
        for (source in listOf(ItemSource.E, ItemSource.Wn, ItemSource.Ru)) {
            val itemBinding = DialogListItemBinding.inflate(layoutInflater, dialogBinding.tagWrapper, false)

            itemBinding.name.text = source.keyString

            itemBinding.root.setOnClickListener {
                cb.invoke(source)
                dialog.dismiss()
            }

            dialogBinding.tagWrapper.addView(itemBinding.root)
        }
    }

    fun show(cb: (selectedSource: ItemSource) -> Unit) {
        this.cb = cb
        dialog.show()
    }
}
