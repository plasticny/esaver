package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.viewer.data.struct.BookWithGroup

@Dao
interface BookWithGroupDao {
    @Insert
    suspend fun insert (item: BookWithGroup)

    @Update
    suspend fun update (item: BookWithGroup)

    @Delete
    suspend fun delete (item: BookWithGroup)

    @Query("SELECT * FROM BookWithGroups WHERE bookId = :bookId AND groupId = :groupId")
    suspend fun queryExactly (bookId: String, groupId: Int): BookWithGroup

    @Query("SELECT * FROM BookWithGroups WHERE bookId = :id")
    suspend fun queryByBookId (id: String): BookWithGroup

    @Query("SELECT groupId FROM BookWithGroups WHERE bookId = :id")
    suspend fun queryGroupId (id: String): Int

    @Query("SELECT bookId FROM BookWithGroups WHERE groupId = :groupId ORDER BY bookId")
    suspend fun queryBookIds (groupId: Int): List<String>

    @Query("SELECT count(groupId) FROM BookWithGroups WHERE groupId = :id")
    suspend fun countByGroupId (id: Int): Int

    @Query("UPDATE BookWithGroups SET groupId = :groupId WHERE bookId = :bookId")
    suspend fun updateGroupId (bookId: String, groupId: Int)
}