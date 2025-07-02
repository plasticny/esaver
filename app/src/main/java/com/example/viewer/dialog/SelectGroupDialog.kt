package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.databinding.DialogSelectGroupBinding
import com.example.viewer.R

class SelectGroupDialog (
    private val context: Context,
    layoutInflater: LayoutInflater,
) {
    private val dialogBinding = DialogSelectGroupBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show (
        title: String = context.getString(R.string.select_group),
        cb: ((Int, String) -> Unit)? = null
    ) {
        dialogBinding.title.text = title

        dialogBinding.flexboxLayout.apply {
            val groupRepo = GroupRepository(context)
            for (id in groupRepo.getAllGroupIdsInOrder()) {
                val name = groupRepo.getGroupName(id)
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