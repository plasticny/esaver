package com.example.viewer.data.struct.item

import android.graphics.PointF
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ItemCommonCustoms",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("internalId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ItemCommonCustom(
    @PrimaryKey
    var internalId: Long,
    var coverPage: Int,
    var coverCropPositionString: String?,
    var customTitle: String?
) {
    companion object {
        @JvmStatic
        fun coverCropPositionStringToPoint (v: String): PointF {
            val tokens = v.split(',')
            if (tokens.size != 2) {
                throw IllegalStateException("tokens size error")
            }
            return PointF(tokens[0].toFloat(), tokens[1].toFloat())
        }
    }
}
