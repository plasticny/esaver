package com.example.viewer.data.struct

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.viewer.Util
import com.example.viewer.struct.Category
import java.io.File

@Entity(
    tableName = "Books",
    primaryKeys = ["id", "sourceOrdinal"],
    indices = [Index(value = ["id"])]
)
data class Book (
    val id: String,
    val sourceOrdinal: Int,
    val url: String,
    val title: String, // origin title
    val subTitle: String, // origin sub-title
    val pageNum: Int,
    val categoryOrdinal: Int,
    val uploader: String?,
    val tagsJson: String,
    // user data
    var coverPage: Int,
    var skipPagesJson: String,
    var lastViewTime: Long,
    var bookMarksJson: String,
    var customTitle: String?, // title set by user, it has higher display priority if it is not null
    // a string in the format: x,y.
    // Represent of the top left point of the cover position
    // Stored in normalized coordinates, i.e. x and y are >= 0 and <= 1
    var coverCropPositionString: String?,
    // for e and wn book, url of page contain image
    var pageUrlsJson: String?,
    // for e and wn book, page of the book profile in the source website
    var p: Int?
) {
    companion object {
        private var tmpBook: Book? = null
        private var tmpCoverUrl: String? = null

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
            // for book profile quick load cover page
            coverUrl: String
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
                customTitle = null,
                coverCropPositionString = null,
                pageUrlsJson = "",
                p = null
            )
            tmpCoverUrl = coverUrl
        }

        fun getTmpBook () = tmpBook!!

        fun getTmpCoverUrl () = tmpCoverUrl!!

        fun clearTmpBook () {
            tmpBook = null
        }

        fun coverCropPositionStringToPoint (string: String): PointF {
            val tokens = string.split(',')
            if (tokens.size != 2) {
                throw IllegalStateException("tokens size error")
            }
            return PointF(tokens[0].toFloat(), tokens[1].toFloat())
        }
    }

    init {
        if (pageNum < 1) {
            throw IllegalArgumentException("Invalid pageNum $pageNum")
        }
    }

    fun getTags (): Map<String, List<String>> = Util.readMapFromJson(tagsJson)

    fun getCategory () = Category.fromOrdinal(categoryOrdinal)

    fun getPageUrls () = pageUrlsJson?.let { Util.readListFromJson<String>(it) }

    fun getCoverUrl (context: Context): String {
        val folder = getBookFolder(context)
        val coverPageFile = File(folder, coverPage.toString())
        return coverPageFile.path
    }

    fun getBookFolder (context: Context): File =
        File(
            context.getExternalFilesDir(null),
            if (isTmpBook()) "tmp" else id
        )

    fun getCoverCropPosition (): PointF? {
        if (coverCropPositionString == null) {
            return null
        }
        return coverCropPositionStringToPoint(coverCropPositionString!!)
    }

    private fun isTmpBook () = tmpBook?.id == id
}
