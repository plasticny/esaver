package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "SearchMarks"
)
data class SearchMark (
    @PrimaryKey
    val id: Int,
    var name: String,
    var categoryOrdinalsJson: String,
    var keyword: String,
    var tagsJson: String,
    var uploader: String,
    var doExclude: Boolean
)
