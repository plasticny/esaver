package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ExcludeTags")
data class ExcludeTag (
    @PrimaryKey
    val id: Int,
    var tagsJson: String,
    var categoryOrdinalsJson: String
)