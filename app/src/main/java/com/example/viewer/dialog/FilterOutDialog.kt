package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.databinding.DialogFilterOutBinding
import com.example.viewer.databinding.DialogFilterOutItemBinding

class FilterOutDialog (
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogFilterOutBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show () {
        val repo = ExcludeTagRepository(context)

        repo.getAllExcludeTag().forEach { record ->
            val itemBinding = DialogFilterOutItemBinding.inflate(layoutInflater, dialogBinding.tagWrapper, false)

            itemBinding.name.text = record.getName()

            itemBinding.root.setOnClickListener {
                EditExcludeTagDialog(
                    context, layoutInflater
                ).show(repo.getExcludeTag(record.id)) { data ->
                    repo.modifyExcludeTag(record.id, data.tags, data.categories.toList())
                    itemBinding.name.text = repo.getExcludeTag(record.id).getName()
                }
            }

            dialogBinding.tagWrapper.addView(itemBinding.root)
        }

        dialog.show()
    }
}