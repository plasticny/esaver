package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.viewer.databinding.ConfirmDialogBinding

class ConfirmDialog (
    private val context: Context,
    private val inflater: LayoutInflater
) {
    fun show (
        message: String,
        positiveCallback: (() -> Unit)? = null,
        negativeCallback: (() -> Unit)? = null
    ) {
        val binding = ConfirmDialogBinding.inflate(inflater)

        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        binding.messageTextView.text = message
        binding.positiveButton.setOnClickListener {
            dialog.dismiss()
            positiveCallback?.invoke()
        }
        binding.negativeButton.setOnClickListener {
            dialog.dismiss()
            negativeCallback?.invoke()
        }
        dialog.show()
    }
}