package com.example.viewer.data.dao.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.viewer.data.struct.search.ExcludeTag

@Dao
interface ExcludeTagDao {
    @Insert
    suspend fun insert (item: ExcludeTag)

    @Update
    suspend fun update (item: ExcludeTag)

    @Query("SELECT * FROM ExcludeTags")
    suspend fun queryAll (): List<ExcludeTag>

    @Query("SELECT * FROM ExcludeTags WHERE id = :id")
    suspend fun queryById (id: Int): ExcludeTag

    @Query("SELECT count(*) + 1 FROM ExcludeTags")
    suspend fun getNextId (): Int

    @RawQuery
    suspend fun rawQueryExcludeTag (query: SupportSQLiteQuery): List<ExcludeTag>
}