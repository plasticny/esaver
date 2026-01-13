package com.example.viewer.data.struct.item

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.io.File

@Entity(
    tableName = "Items",
    foreignKeys = [
        ForeignKey(
            entity = ItemGroup::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("groupId"),
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val typeOrdinal: Int,
    val sourceOrdinal: Int,
    val categoryOrdinal: Int,
    val lastViewTime: Long,
    @ColumnInfo(index = true)
    val groupId: Int,
    var orderInGroup: Int
) {
    companion object {
        data class GalleryItem (
            @ColumnInfo("id")
            val id: Long,
            @ColumnInfo("typeOrdinal")
            val typeOrdinal: Int,
            @ColumnInfo("sourceOrdinal")
            val sourceOrdinal: Int,
            @ColumnInfo("orderInGroup")
            val orderInGroup: Int
        )

        data class SequenceItem (
            @ColumnInfo("id")
            val id: Long,
            @ColumnInfo("lastViewTime")
            val lastViewTime: Long
        )

        @JvmStatic
        fun getFolder (context: Context, id: Long): File {
            assert(id >= 0L)
            return File(
                context.getExternalFilesDir(null),
                id.toString()
            )
        }
    }
}
