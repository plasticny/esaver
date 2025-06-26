package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SearchHistoryDao {
    @Query("SELECT lastNext FROM SearchHistories WHERE searchMarkId = :searchMarkId")
    suspend fun queryLastNext (searchMarkId: Long): String?

    @Query("UPDATE SearchHistories SET lastNext = :next WHERE searchMarkId = :searchMarkId")
    suspend fun updateLastNext (searchMarkId: Long, next: String?)
}