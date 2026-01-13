package com.example.viewer.data.struct.item

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "SourceDataEs",
    primaryKeys = ["internalId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("internalId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SourceDataE (
    var internalId: Long,
    val bookId: String,
    val url: String,
    val title: String,
    val subTitle: String,
    val pageNum: Int,
    val uploader: String?,
    val tagsJson: String,

    var skipPagesJson: String,
    var bookMarksJson: String,

    var pageUrlsJson: String,
    var p: Int
)
