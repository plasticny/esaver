package com.example.viewer.activity.search

import com.example.viewer.Util
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.struct.Category

/**
 * re-packed search mark data for search activity
 */
data class SearchMarkData (
    val id: Long,
    val name: String,
    val sourceOrdinal: Int,
    val keyword: String,
    val categories: List<Category>,
    val tags: Map<String, List<String>>,
    val uploader: String?,
    val doExclude: Boolean
) {
    companion object {
        fun packSearchMark (searchMark: SearchMark) =
            SearchMarkData (
                id = searchMark.id,
                name = searchMark.name,
                sourceOrdinal = searchMark.sourceOrdinal,
                keyword = searchMark.keyword,
                categories = Util.readListFromJson<Int>(searchMark.categoryOrdinalsJson)
                    .map { Category.fromOrdinal(it) },
                tags = Util.readMapFromJson(searchMark.tagsJson),
                uploader = searchMark.uploader,
                doExclude = searchMark.doExclude
            )
    }
}
