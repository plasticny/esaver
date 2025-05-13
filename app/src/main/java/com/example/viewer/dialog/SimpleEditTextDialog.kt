package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.example.viewer.databinding.DialogSimpleEditTextBinding

class SimpleEditTextDialog (
    context: Context,
    layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogSimpleEditTextBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show (
        title: String? = null,
        hint: String? = null,
        validator: ((String) -> Boolean)? = null,
        positiveCb: ((String) -> Unit)? = null
    ) {
        dialogBinding.titleTextView.apply {
            if (title == null) {
                visibility = View.GONE
            } else {
                text = title
            }
        }

        dialogBinding.editText.hint = hint ?: ""

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.confirmButton.setOnClickListener {
            positiveCb?.let {
                val text = dialogBinding.editText.text.toString()
                if (validator?.invoke(text) != false) {
                    positiveCb(text)
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}