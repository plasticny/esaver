package com.example.viewer.data.struct

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.struct.Category
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.gson.Gson

@Entity(
    tableName = "SearchMarks",
    foreignKeys = [
        ForeignKey(
            entity = SearchMark::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("nextInList"),
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class SearchMark (
    @PrimaryKey val id: Int,
    var name: String,
    var categoryOrdinalsJson: String,
    var keyword: String,
    var tagsJson: String,
    var uploader: String?,
    var doExclude: Boolean,
    // for custom the search mark sorting
    @ColumnInfo(index = true)
    var nextInList: Int?
) {
    companion object {
        private var tmpSearchMark: SearchMark? = null

        fun setTmpSearchMark (
            context: Context,
            categories: List<Category> = listOf(),
            keyword: String,
            tags: Map<String, List<String>> = mapOf(),
            uploader: String?,
            doExclude: Boolean
        ) {
            val gson = Gson()
            tmpSearchMark = SearchMark(
                id = -1,
                name = context.getString(R.string.search),
                categoryOrdinalsJson = gson.toJson(categories.map { it.ordinal }),
                keyword = keyword,
                tagsJson = gson.toJson(tags),
                uploader = uploader,
                doExclude = doExclude,
                nextInList = null
            )
        }
        fun getTmpSearchMark () = tmpSearchMark
    }

    fun getCategories (): List<Category> =
        Util.readListFromJson<Int>(categoryOrdinalsJson)
            .map { Util.categoryFromOrdinal(it) }

    fun getTags (): Map<String, List<String>> = Util.readMapFromJson(tagsJson)
}
