package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.viewer.data.struct.SearchMark

@Dao
interface SearchMarkDao {
    @Insert
    suspend fun insert (item: SearchMark): Long

    @Update
    suspend fun update (item: SearchMark)

    @Query("SELECT * FROM SearchMarks ORDER BY itemOrder ASC")
    suspend fun queryAllInOrder (): List<SearchMark>

    @Query("SELECT id FROM SearchMarks ORDER BY itemOrder ASC")
    suspend fun queryAllIdsInOrder (): List<Long>

    @Query("SELECT * FROM SearchMarks WHERE id = :id")
    suspend fun queryById (id: Long): SearchMark

    @Query("SELECT itemOrder FROM SearchMarks WHERE id = :id")
    suspend fun queryItemOrder (id: Long): Int?

    @Query("SELECT IFNULL(MAX(ItemOrder), 0) + 1 FROM SearchMarks")
    suspend fun getNextItemOrder (): Int

    @Query("SELECT count(*) FROM SearchMarks")
    suspend fun countItems (): Int

    @Query("UPDATE SearchMarks SET itemOrder = itemOrder - 1 WHERE itemOrder >= :fromOrder")
    suspend fun decreaseItemOrder (fromOrder: Int)

    @Query("UPDATE SearchMarks SET itemOrder = itemOrder - 1 WHERE itemOrder >= :fromOrder AND itemOrder <= :toOrder")
    suspend fun decreaseItemOrder (fromOrder: Int, toOrder: Int)

    @Query("UPDATE SearchMarks SET itemOrder = itemOrder + 1 WHERE itemOrder >= :fromOrder AND itemOrder <= :toOrder")
    suspend fun increaseItemOrder (fromOrder: Int, toOrder: Int)

    @Query("UPDATE SearchMarks SET itemOrder = :itemOrder WHERE id = :id")
    suspend fun updateItemOrder (id: Long, itemOrder: Int)

    @Query("UPDATE SearchMarks SET itemOrder = :newOrder WHERE itemOrder = :oldOrder")
    suspend fun updateItemOrderByOrder (oldOrder: Int, newOrder: Int)

    @Query("DELETE FROM SearchMarks WHERE id = :id")
    suspend fun deleteById (id: Long)
}