package com.example.viewer.data.struct.interaction

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "RuTags",
    indices = [Index(value = ["tag"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = RuTagType::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("typeId"),
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class RuTag (
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tag: String,
    val typeId: Long
)
