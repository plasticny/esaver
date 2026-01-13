package com.example.viewer.data.dao.item

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.item.ItemGroup

@Dao
interface ItemGroupDao {
    @Insert
    suspend fun insert (itemGroup: ItemGroup)

    @Query(
        "INSERT INTO ItemGroups (id, name, itemOrder) VALUES (" +
                ":id, :name, IFNULL((SELECT MAX(itemOrder) + 1 FROM ItemGroups), 0)" +
                ")"
    )
    suspend fun insert (id: Int, name: String)

    @Query("DELETE FROM ItemGroups WHERE id = :id")
    suspend fun delete (id: Int)

    @Query("SELECT * FROM ItemGroups ORDER BY itemOrder")
    suspend fun queryAllInOrder (): List<ItemGroup>

    @Query("SELECT id FROM ItemGroups WHERE name = :name")
    suspend fun queryId (name: String): Int?

    @Query("SELECT id FROM ItemGroups ORDER BY itemOrder")
    suspend fun queryIdsInOrder (): List<Int>

    @Query("SELECT max(id) + 1 FROM ItemGroups")
    suspend fun queryNextId (): Int

    @Query("SELECT count(id) FROM ItemGroups WHERE id = :id")
    suspend fun countId (id: Int): Int

    @Query("SELECT name FROM ItemGroups WHERE id = :id")
    suspend fun queryName (id: Int): String

    @Query("SELECT itemOrder FROM ItemGroups WHERE id = :id")
    suspend fun queryItemOrder (id: Int): Int

    @Query("SELECT max(itemOrder) FROM ItemGroups")
    suspend fun queryMaxItemOrder (): Int

    @Query("UPDATE ItemGroups SET name = :v WHERE id = :id")
    suspend fun updateName (id: Int, v: String)

    @Query("UPDATE ItemGroups SET itemOrder = :newOrder WHERE itemOrder = :oldOrder")
    suspend fun updateItemOrderByOrder (oldOrder: Int, newOrder: Int)

    @Query("UPDATE ItemGroups SET itemOrder = :itemOrder - 1 WHERE itemOrder = :itemOrder")
    suspend fun decreaseItemOrder (itemOrder: Int)
}