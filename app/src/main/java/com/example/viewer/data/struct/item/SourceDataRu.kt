package com.example.viewer.data.struct.item

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "SourceDataRus",
    primaryKeys = ["internalId"],
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("internalId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SourceDataRu (
    val internalId: Long,
    val videoId: String,
    val videoUrl: String,
    val uploader: String,
    val title: String,
    val tagsJson: String
)