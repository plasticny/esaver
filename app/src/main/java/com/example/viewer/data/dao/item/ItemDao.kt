package com.example.viewer.data.dao.item

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.item.Item

@Dao
interface ItemDao {
    @Insert
    suspend fun insert (item: Item): Long

    @Query("DELETE FROM Items WHERE id = :id")
    suspend fun delete (id: Long)

    @Query("SELECT * FROM Items WHERE id = :id")
    suspend fun queryAll (id: Long): Item

    @Query("SELECT id, typeOrdinal, sourceOrdinal, orderInGroup FROM Items WHERE groupId = :groupId")
    suspend fun queryGalleryItemByGroupId (groupId: Int): List<Item.Companion.GalleryItem>

    @Query("SELECT sourceOrdinal FROM Items WHERE id = :id")
    suspend fun querySourceOrdinal (id: Long): Int

    @Query("SELECT id FROM Items WHERE typeOrdinal = :typeOrdinal")
    suspend fun queryIdsByType (typeOrdinal: Int): List<Long>

    @Query("SELECT id FROM Items WHERE groupId = :groupId")
    suspend fun queryIdsByGroup (groupId: Int): List<Long>

    @Query("SELECT id, lastViewTime FROM Items WHERE categoryOrdinal != 3 ORDER BY lastViewTime ASC")
    suspend fun queryIdSeqH (): List<Item.Companion.SequenceItem>

    @Query("SELECT id, lastViewTime FROM Items WHERE categoryOrdinal == 3 ORDER BY lastViewTime ASC")
    suspend fun queryIdSeqNH (): List<Item.Companion.SequenceItem>

    @Query("SELECT typeOrdinal FROM Items WHERE id = :id")
    suspend fun queryTypeOrdinal (id: Long): Int

    @Query("SELECT groupId FROM Items WHERE id = :id")
    suspend fun queryGroupId (id: Long): Int

    @Query("SELECT count(id) FROM Items WHERE id = :id AND typeOrdinal = :itemTypeOrdinal")
    suspend fun countId (id: Long, itemTypeOrdinal: Int): Int

    @Query("UPDATE Items SET groupId = :groupId WHERE id = :id")
    suspend fun updateGroupId (id: Long, groupId: Int)

    @Query("UPDATE Items SET lastViewTime = :v WHERE id = :id")
    suspend fun updateLastViewTime (id: Long, v: Long)
}