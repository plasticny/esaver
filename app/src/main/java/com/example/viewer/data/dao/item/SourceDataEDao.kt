package com.example.viewer.data.dao.item

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.viewer.data.struct.item.SourceDataE
import com.example.viewer.data.struct.item.SourceDataWn

@Dao
interface SourceDataEDao {
    @Insert
    suspend fun insert (sourceDataE: SourceDataE)

    @Query("SELECT * FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryAll (internalId: Long): SourceDataE

    @Query("SELECT internalId FROM SourceDataEs WHERE bookId = :bookId")
    suspend fun queryInternalId (bookId: String): Long?

    @Query("SELECT bookId FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryBookId (internalId: Long): String

    @Query("SELECT bookMarksJson FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryBookMarksJson (internalId: Long): String

    @Query("SELECT url FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryUrl (internalId: Long): String

    @Query("SELECT pageUrlsJson FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryPageUrlJson (internalId: Long): String

    @Query("SELECT pageNum FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryPageNum (internalId: Long): Int

    @Query("SELECT p FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun queryP (internalId: Long): Int

    @Query("SELECT skipPagesJson FROM SourceDataEs WHERE internalId = :internalId")
    suspend fun querySkipPagesJson (internalId: Long): String

    @Query("UPDATE SourceDataEs SET bookMarksJson = :bookMarksJson WHERE internalId = :internalId")
    suspend fun updateBookMarksJson (internalId: Long, bookMarksJson: String)

    @Query("UPDATE SourceDataEs SET pageUrlsJson = :pageUrlJson WHERE internalId = :internalId")
    suspend fun updatePageUrlJson (internalId: Long, pageUrlJson: String)

    @Query("UPDATE SourceDataEs SET p = :value WHERE internalId = :internalId")
    suspend fun updateP (internalId: Long, value: Int)

    @Query("UPDATE SourceDataEs SET skipPagesJson = :v WHERE internalId = :internalId")
    suspend fun updateSkipPagesJson (internalId: Long, v: String)
}