package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
)
