package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "BookGroups",
    indices = [Index(value = ["id"])]
)
data class Group (
    @PrimaryKey
    val id: Int,
    val name: String
)
