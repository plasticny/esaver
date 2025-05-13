package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.viewer.Util
import com.example.viewer.database.SearchDatabase
import com.example.viewer.databinding.DialogFilterOutBinding
import com.example.viewer.databinding.DialogFilterOutItemBinding
import com.example.viewer.struct.ExcludeTagRecord

class FilterOutDialog (
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogFilterOutBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    fun show () {
        val searchDatabase = SearchDatabase.getInstance(context)

        searchDatabase.getAllExcludeTag().forEach { (id, record) ->
            val itemBinding = DialogFilterOutItemBinding.inflate(layoutInflater, dialogBinding.tagWrapper, false)

            itemBinding.name.text = record.name

            itemBinding.root.setOnClickListener {
                EditExcludeTagDialog(
                    context, layoutInflater
                ).show(searchDatabase.getExcludeTag(id)) { recordToSave ->
                    itemBinding.name.text = recordToSave.name
                    SearchDatabase.getInstance(context).modifyExcludeTag(id, recordToSave)
                }
            }

            dialogBinding.tagWrapper.addView(itemBinding.root)
        }

        dialog.show()
    }
}