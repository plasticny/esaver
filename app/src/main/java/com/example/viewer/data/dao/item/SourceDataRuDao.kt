package com.example.viewer.data.dao.item

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.item.SourceDataRu

@Dao
interface SourceDataRuDao {
    @Insert
    suspend fun insert (sourceDataRu: SourceDataRu)

    @Query("SELECT * FROM SourceDataRus WHERE internalId = :internalId")
    suspend fun queryAll (internalId: Long): SourceDataRu
}