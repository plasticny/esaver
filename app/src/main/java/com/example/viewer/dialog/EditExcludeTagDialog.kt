package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.example.viewer.data.struct.ExcludeTag
import com.example.viewer.struct.Category
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
        showDoExcludeField = false
        showNameField = false
        showSaveButton = true
    }

    override fun checkValid(item: DialogData): Boolean {
        if (item.tags.isEmpty()) {
            Toast.makeText(context, "需要至少一個標籤", Toast.LENGTH_SHORT).show()
            return false
        }
        return super.checkValid(item)
    }

    fun show (
        categories: List<Category>,
        tags: Map<String, List<String>>,
        onSave: (OnSaveData) -> Unit
    ) {
        saveCb = { itemToSave ->
            val recordToSave = OnSaveData(
                itemToSave.tags,
                itemToSave.categories.toSet()
            )
            onSave(recordToSave)
        }
        super.show(
            name = "",
            categories = categories,
            keyword = "",
            tags = tags,
            uploader = "",
            doExclude = false
        )
    }

    fun show(record: ExcludeTag, onSave: (OnSaveData) -> Unit) =
        show(record.getCategories(), record.getTags(), onSave)

    data class OnSaveData (
        val tags: Map<String, List<String>>,
        val categories: Set<Category>
    )
}