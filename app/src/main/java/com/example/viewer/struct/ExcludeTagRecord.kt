package com.example.viewer.struct

import com.example.viewer.Util
import com.example.viewer.database.SearchDatabase.Companion.Category
import com.example.viewer.database.SearchDatabase.Companion.SearchMark

data class ExcludeTagRecord (
    val tags: Map<String, List<String>>,
    val categories: Set<Category>
) {
    val name: String
        get() {
            val tagCategory = tags.keys.first()
            val tagValue = tags.getValue(tagCategory).first()
            return "${Util.TAG_TRANSLATION_MAP[tagCategory]} - $tagValue"
        }

    fun excluded (bookRecord: BookRecord): Boolean {
        if (!categories.contains(Util.categoryFromName(bookRecord.cat))) {
            return false
        }
        // exclude the book's tag contain all exclude tag
        return tags.entries.all { (tagCategory, tagValues) ->
            val bookCategoryTags = bookRecord.tags[tagCategory] ?: return@all false
            return@all bookCategoryTags.containsAll(tagValues)
        }
    }

    fun excluded (searchMark: SearchMark): Boolean {
        if (!categories.containsAll(searchMark.categories)) {
            return false
        }
        return tags.entries.all { (tagCategory, tagValues) ->
            val searchMarkCategoryTags = searchMark.tags[tagCategory] ?: return@all false
            return@all searchMarkCategoryTags.containsAll(tagValues)
        }
    }
}