package com.example.viewer.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.example.viewer.RandomBook
import com.example.viewer.databinding.DialogRandomBookSettingBinding

class RandomBookSettingDialog (
    private val context: Context,
    layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogRandomBookSettingBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show (onConfirm: () -> Unit) {
        RandomBook.getPoolStatus(context).let { (pullH, pullNH) ->
            dialogBinding.includeHSwitch.isChecked = pullH
            dialogBinding.includeNonHSwitch.isChecked = pullNH
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.confirmButton.setOnClickListener {
            RandomBook.changePool(
                context,
                dialogBinding.includeHSwitch.isChecked,
                dialogBinding.includeNonHSwitch.isChecked
            )
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }
}