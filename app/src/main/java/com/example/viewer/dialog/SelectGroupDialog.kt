package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.example.viewer.database.GroupDatabase
import com.example.viewer.databinding.DialogSelectGroupBinding

class SelectGroupDialog (
    context: Context,
    layoutInflater: LayoutInflater,
) {
    private val dialogBinding = DialogSelectGroupBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show (cb: ((Int, String) -> Unit)? = null) {
        dialogBinding.flexboxLayout.apply {
            val groupDb = GroupDatabase.getInstance(context)
            for (id in groupDb.getAllGroupIds()) {
                val name = groupDb.getGroupName(id)
                addView(Button(context).apply {
                    text = name
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    isAllCaps = false
                    setOnClickListener {
                        cb?.invoke(id, name)
                        dialog.dismiss()
                    }
                })
            }
        }
        dialog.show()
    }
}