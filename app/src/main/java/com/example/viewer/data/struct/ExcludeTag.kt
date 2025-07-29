package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.viewer.Util
import com.example.viewer.struct.Category

@Entity(tableName = "ExcludeTags")
data class ExcludeTag (
    @PrimaryKey
    val id: Int,
    var tagsJson: String,
    var categoryOrdinalsJson: String
) {
    fun getName (): String {
        val tags = getTags()
        val tagCategory = tags.keys.first()
        val tagValue = tags.getValue(tagCategory).first()
        return "${Util.TAG_TRANSLATION_MAP[tagCategory]} - $tagValue"
    }

    fun getTags (): Map<String, List<String>> = Util.readMapFromJson(tagsJson)

    fun getCategories (): List<Category> =
        Util.readListFromJson<Int>(categoryOrdinalsJson)
            .map { Category.fromOrdinal(it) }
}