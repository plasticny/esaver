package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.viewer.data.struct.SearchMark

@Dao
interface SearchMarkDao {
    @Insert
    suspend fun insert (item: SearchMark)

    @Update
    suspend fun update (item: SearchMark)

    @Query("SELECT * FROM SearchMarks WHERE id = :id")
    suspend fun queryById (id: Int): SearchMark

    @Query("SELECT nextInList FROM SearchMarks WHERE id = :id")
    suspend fun queryNextInListById (id: Int): Int

    @Query("SELECT id FROM SearchMarks WHERE nextInList = :id")
    suspend fun queryPreviousId (id: Int): Int?

    @Query("SELECT id FROM SearchMarks WHERE nextInList IS NULL")
    suspend fun queryLastInListId (): Int?

    @Query("SELECT id FROM SearchMarks")
    suspend fun getAllIds (): List<Int>

    @Query("SELECT count(*) FROM SearchMarks")
    suspend fun countItems (): Int

    @Query("DELETE FROM SearchMarks WHERE id = :id")
    suspend fun deleteById (id: Int)

    @Query("UPDATE SearchMarks SET nextInList = :nextInList WHERE id = :id")
    suspend fun updateNextInList (id: Int, nextInList: Int?)
}