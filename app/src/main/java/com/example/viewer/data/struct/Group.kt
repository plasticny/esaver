package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "BookGroups",
    indices = [
        Index(value = ["id"]),
        Index(value = ["itemOrder"], unique = true)
    ]
)
data class Group (
    @PrimaryKey
    val id: Int,
    var name: String,
    var itemOrder: Int
)
