package com.example.viewer.data.struct

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity (
    tableName = "BookWithGroups",
    primaryKeys = ["bookId", "bookSourceOrdinal"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = arrayOf("id", "sourceOrdinal"),
            childColumns = arrayOf("bookId", "bookSourceOrdinal"),
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
    val bookId: String,
    val bookSourceOrdinal: Int,
    @ColumnInfo(index = true)
    var groupId: Int
) {
    companion object {
        data class BookIdentify (
            @ColumnInfo(name = "bookId")
            val id: String,
            @ColumnInfo(name = "bookSourceOrdinal")
            val sourceOrdinal: Int
        )
    }
}
