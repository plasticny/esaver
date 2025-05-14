package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.example.viewer.struct.ExcludeTagRecord
import com.example.viewer.struct.SearchMark

class EditExcludeTagDialog (
    context: Context,
    layoutInflater: LayoutInflater,
): SearchMarkDialog(context, layoutInflater) {
    init {
        title = "編輯濾除規則"
        showKeywordField = false
        showUploaderField = false
        showNameField = false
        showSaveButton = true
    }

    override fun checkValid(item: SearchMark): Boolean {
        if (item.tags.isEmpty()) {
            Toast.makeText(context, "需要至少一個標籤", Toast.LENGTH_SHORT).show()
            return false
        }
        return super.checkValid(item)
    }

    fun show(record: ExcludeTagRecord, onSave: (ExcludeTagRecord) -> Unit) {
        saveCb = { itemToSave ->
            val recordToSave = ExcludeTagRecord(
                itemToSave.tags,
                itemToSave.categories.toSet()
            )
            onSave(recordToSave)
        }
        super.show(
            SearchMark(
                name = "",
                categories = record.categories.toList(),
                keyword = "",
                tags = record.tags,
                uploader = ""
            )
        )
    }
}