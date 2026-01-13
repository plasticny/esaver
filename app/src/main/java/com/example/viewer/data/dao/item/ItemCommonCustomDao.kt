package com.example.viewer.data.dao.item

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.item.ItemCommonCustom

@Dao
interface ItemCommonCustomDao {
    @Insert
    suspend fun insert (item: ItemCommonCustom)

    @Query("SELECT * FROM ItemCommonCustoms WHERE internalId = :internalId")
    suspend fun queryAll (internalId: Long): ItemCommonCustom

    @Query("SELECT coverPage FROM ItemCommonCustoms WHERE internalId = :internalId")
    suspend fun queryCoverPage (internalId: Long): Int

    @Query("SELECT coverCropPositionString FROM ItemCommonCustoms WHERE internalId = :internalId")
    suspend fun queryCoverCropPositionString (internalId: Long): String?

    @Query("UPDATE ItemCommonCustoms SET coverPage = :v WHERE internalId = :internalId")
    suspend fun updateCoverPage (internalId: Long, v: Int)

    @Query("UPDATE ItemCommonCustoms SET coverCropPositionString = :v WHERE internalId = :internalId")
    suspend fun updateCoverCropPositionString (internalId: Long, v: String)

    @Query("UPDATE ItemCommonCustoms SET customTitle = :v WHERE internalId = :internalId")
    suspend fun updateCustomTitle (internalId: Long, v: String?)
}