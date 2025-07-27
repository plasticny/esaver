package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.Group

@Dao
interface GroupDao {
    @Insert
    suspend fun insert (group: Group)

    @Query(
        "INSERT INTO BookGroups (id, name, itemOrder) VALUES (" +
            ":id, :name, IFNULL((SELECT MAX(itemOrder) + 1 FROM BookGroups), 0)" +
        ")"
    )
    suspend fun insert (id: Int, name: String)

    @Query("SELECT * FROM BookGroups ORDER BY itemOrder")
    suspend fun queryAllInOrder (): List<Group>

    @Query("SELECT id FROM BookGroups ORDER BY itemOrder")
    suspend fun queryAllIdsInOrder (): List<Int>

    @Query("SELECT name FROM BookGroups WHERE id = :id")
    suspend fun queryName (id: Int): String

    @Query("SELECT id FROM BookGroups WHERE name = :name")
    suspend fun queryId (name: String): Int?

    @Query("SELECT itemOrder FROM BookGroups WHERE id = :id")
    suspend fun queryItemOrder (id: Int): Int

    @Query("SELECT count(id) FROM BookGroups WHERE id = :id")
    suspend fun countId (id: Int): Int

    @Query("UPDATE BookGroups SET itemOrder = :itemOrder - 1 WHERE itemOrder = :itemOrder")
    suspend fun decreaseItemOrder (itemOrder: Int)

    @Query("UPDATE BookGroups SET itemOrder = :itemOrder WHERE id = :id")
    suspend fun updateItemOrder (id: Int, itemOrder: Int)

    @Query("UPDATE BookGroups SET itemOrder = :newOrder WHERE itemOrder = :oldOrder")
    suspend fun updateItemOrderByOrder (oldOrder: Int, newOrder: Int)

    @Query("UPDATE BookGroups SET name = :name WHERE id = :id")
    suspend fun updateName (id: Int, name: String)

    @Query("SELECT max(id) + 1 FROM BookGroups")
    suspend fun getNextId (): Int

    @Query("SELECT max(itemOrder) FROM BookGroups")
    suspend fun getMaxItemOrder (): Int

    @Query("DELETE FROM BookGroups WHERE id = :id")
    suspend fun delete (id: Int)
}