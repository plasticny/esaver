package com.example.viewer.data.struct

import android.content.Context
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.viewer.Util
import java.io.File

@Entity(
    tableName = "Books",
    indices = [Index(value = ["id"])]
)
data class Book (
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val subTitle: String,
    val pageNum: Int,
    val categoryOrdinal: Int,
    val uploader: String?,
    val tagsJson: String,
    val sourceOrdinal: Int,
    // user data
    var coverPage: Int,
    var skipPagesJson: String,
    var lastViewTime: Long,
    var bookMarksJson: String,
    // for e book only
    var pageUrlsJson: String?,
    var p: Int?
) {
    companion object {
        private var tmpBook: Book? = null

        fun setTmpBook (
            id: String,
            url: String,
            title: String,
            subTitle: String,
            pageNum: Int,
            categoryOrdinal: Int,
            uploader: String?,
            tagsJson: String,
            sourceOrdinal: Int,
            pageUrlsJson: String
        ) {
            tmpBook = Book(
                id = id,
                url = url,
                title = title,
                subTitle = subTitle,
                pageNum = pageNum,
                categoryOrdinal = categoryOrdinal,
                uploader = uploader,
                tagsJson = tagsJson,
                sourceOrdinal = sourceOrdinal,
                coverPage = 0,
                skipPagesJson = "[]",
                lastViewTime = 0L,
                bookMarksJson = "[]",
                pageUrlsJson = pageUrlsJson,
                p = null
            )
        }

        fun getTmpBook () = tmpBook!!

        fun clearTmpBook () {
            tmpBook = null
        }
    }

    fun getTags (): Map<String, List<String>> = Util.readMapFromJson(tagsJson)

    fun getCategory () = Util.categoryFromOrdinal(categoryOrdinal)

    fun getPageUrls () = pageUrlsJson?.let { Util.readListFromJson<String>(it) }

    fun getCoverUrl (context: Context): String {
        val folder = getBookFolder(context)
        val coverPageFile = File(folder, coverPage.toString())
        return coverPageFile.path
//        return if (coverPageFile.exists()) {
//            coverPageFile.path
//        } else {
//            File(folder, "0").path
//        }
    }

    fun getBookFolder (context: Context): File =
        File(
            context.getExternalFilesDir(null),
            if (isTmpBook()) "tmp" else id
        )

    private fun isTmpBook () = tmpBook?.id == id
}
