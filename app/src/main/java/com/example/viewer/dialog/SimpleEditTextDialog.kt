package com.example.viewer.dialog

import android.content.Context
import android.renderscript.ScriptGroup.Input
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.viewer.databinding.DialogSimpleEditTextBinding

class SimpleEditTextDialog (
    context: Context,
    layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogSimpleEditTextBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    var title: String? = null
    var hint: String = ""
    var inputType: Int = InputType.TYPE_CLASS_TEXT
    var validator: ((String) -> Boolean)? = null
    var positiveCb: ((String) -> Unit)? = null

    fun show () {
        dialogBinding.titleTextView.apply {
            if (title == null) {
                visibility = View.GONE
            } else {
                text = title
            }
        }

        dialogBinding.editText.apply {
            hint = this@SimpleEditTextDialog.hint
            inputType = this@SimpleEditTextDialog.inputType
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.confirmButton.setOnClickListener {
            val text = dialogBinding.editText.text.toString()
            if (validator?.invoke(text) != false) {
                positiveCb?.invoke(text)
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}