package com.example.viewer.data.struct.interaction

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "RuTagTypes",
    indices = [Index(value = ["type"], unique = true)]
)
data class RuTagType (
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String
)
