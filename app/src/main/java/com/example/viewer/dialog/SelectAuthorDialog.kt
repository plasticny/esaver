package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.example.viewer.database.BookDatabase
import com.example.viewer.databinding.SelectAuthorDialogBinding

class SelectAuthorDialog (
    context: Context,
    layoutInflater: LayoutInflater,
) {
    private val dialogBinding = SelectAuthorDialogBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show (cb: ((String) -> Unit)? = null) {
        dialogBinding.selectAuthorDialogFlexboxLayout.apply {
            val bookDatabase = BookDatabase.getInstance(context)
            for (author in bookDatabase.getUserAuthors()) {
                addView(Button(context).apply {
                    text = author
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    isAllCaps = false
                    setOnClickListener {
                        cb?.invoke(author)
                        dialog.dismiss()
                    }
                })
            }
        }
        dialog.show()
    }
}