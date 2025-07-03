package com.example.viewer.data.struct

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity (
    tableName = "SearchHistories",
    foreignKeys = [
        ForeignKey(
            entity = SearchMark::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("searchMarkId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SearchHistory (
    @PrimaryKey
    val searchMarkId: Long,
    /**
     * This should be the parameter next in the last fetched url of the search mark
     */
    var lastNext: String?
)
