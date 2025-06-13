package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Group (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)
