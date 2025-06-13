package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.BookWithGroup

@Dao
interface BookWithGroupDao {
    @Insert
    suspend fun insert (item: BookWithGroup)

    @Query("SELECT groupId FROM BookWithGroups WHERE bookId = :id")
    suspend fun queryGroupId (id: String): Int

    @Query("UPDATE BookWithGroups SET groupId = :groupId WHERE bookId = :bookId")
    suspend fun updateGroupId (bookId: String, groupId: Int)
}