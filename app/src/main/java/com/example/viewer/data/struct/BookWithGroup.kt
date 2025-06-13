package com.example.viewer.data.struct

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity (
    tableName = "BookWithGroups",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("bookId"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Group::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("groupId"),
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class BookWithGroup (
    @PrimaryKey val bookId: String,
    @ColumnInfo(index = true)
    val groupId: Int
)
