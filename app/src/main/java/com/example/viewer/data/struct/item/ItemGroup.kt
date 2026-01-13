package com.example.viewer.data.struct.item

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ItemGroups",
    indices = [
        Index(value = ["id"]),
        Index(value = ["itemOrder"], unique = true)
    ]
)
data class ItemGroup(
    @PrimaryKey
    val id: Int,
    var name: String,
    var itemOrder: Int
)