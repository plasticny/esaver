package com.example.viewer.data.dao.item

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.item.SourceDataWn

@Dao
interface SourceDataWnDao {
    @Insert
    suspend fun insert (sourceDataWn: SourceDataWn)

    @Query("SELECT * FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryAll (internalId: Long): SourceDataWn

    @Query("SELECT internalId FROM SourceDataWns WHERE bookId = :bookId")
    suspend fun queryInternalId (bookId: String): Long?

    @Query("SELECT bookId FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryBookId (internalId: Long): String

    @Query("SELECT bookMarksJson FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryBookMarksJson (internalId: Long): String

    @Query("SELECT url FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryUrl (internalId: Long): String

    @Query("SELECT pageUrlsJson FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryPageUrlJson (internalId: Long): String

    @Query("SELECT pageNum FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryPageNum (internalId: Long): Int

    @Query("SELECT p FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun queryP (internalId: Long): Int

    @Query("SELECT skipPagesJson FROM SourceDataWns WHERE internalId = :internalId")
    suspend fun querySkipPagesJson (internalId: Long): String

    @Query("UPDATE SourceDataWns SET bookMarksJson = :bookMarksJson WHERE internalId = :internalId")
    suspend fun updateBookMarksJson (internalId: Long, bookMarksJson: String)

    @Query("UPDATE SourceDataWns SET pageUrlsJson = :pageUrlJson WHERE internalId = :internalId")
    suspend fun updatePageUrlJson (internalId: Long, pageUrlJson: String)

    @Query("UPDATE SourceDataWns SET p = :value WHERE internalId = :internalId")
    suspend fun updateP (internalId: Long, value: Int)

    @Query("UPDATE SourceDataWns SET skipPagesJson = :v WHERE internalId = :internalId")
    suspend fun updateSkipPagesJson (internalId: Long, v: String)
}