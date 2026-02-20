package com.example.viewer.data.dao.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.search.SearchHistory

@Dao
interface SearchHistoryDao {
    @Insert
    suspend fun insert (item: SearchHistory)

    @Query("SELECT lastNext FROM SearchHistories WHERE searchMarkId = :searchMarkId")
    suspend fun queryLastNext (searchMarkId: Long): String?

    @Query("UPDATE SearchHistories SET lastNext = :next WHERE searchMarkId = :searchMarkId")
    suspend fun updateLastNext (searchMarkId: Long, next: String)

    @Query("UPDATE SearchHistories SET lastNext = NULL WHERE searchMarkId = :searchMarkId")
    suspend fun clearLastNext (searchMarkId: Long)
}