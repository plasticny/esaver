package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.Group

@Dao
interface GroupDao {
    @Insert
    suspend fun insert (group: Group): Long

    @Query("SELECT id FROM BookGroups")
    suspend fun queryAllIds (): List<Int>

    @Query("SELECT name FROM BookGroups WHERE id = :id")
    suspend fun queryName (id: Int): String

    @Query("SELECT id FROM BookGroups WHERE name = :name")
    suspend fun queryId (name: String): Int?

    @Query("SELECT count(id) FROM BookGroups WHERE id = :id")
    suspend fun countId (id: Int): Int

    @Query("SELECT max(id) + 1 FROM BookGroups")
    suspend fun getNextId (): Int

    @Query("DELETE FROM BookGroups WHERE id = :id")
    suspend fun delete (id: Int)
}