package com.example.viewer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.viewer.data.struct.Book

@Dao
interface BookDao {
    @Insert
    suspend fun insert (book: Book)

    @Update
    suspend fun update (book: Book)

    @Query("SELECT * FROM Books WHERE id = :id")
    suspend fun queryById (id: String): Book

    @Query("SELECT count(id) FROM Books WHERE id = :id")
    suspend fun countId (id: String): Int

    @Query("SELECT id FROM Books")
    suspend fun getAllBookIds (): List<String>

    @Query("SELECT id, lastViewTime FROM Books WHERE categoryOrdinal != 3 ORDER BY lastViewTime ASC")
    suspend fun getBookIdSeqH (): List<Book.Companion.SequenceItem>

    @Query("SELECT id, lastViewTime FROM Books WHERE categoryOrdinal == 3 ORDER BY lastViewTime ASC")
    suspend fun getBookIdSeqNH () : List<Book.Companion.SequenceItem>

    @Query("SELECT url FROM Books WHERE id = :id")
    suspend fun getUrl (id: String): String

    @Query("SELECT p FROM Books WHERE id = :id")
    suspend fun getP (id: String): Int?

    @Query("SELECT pageNum FROM Books WHERE id = :id")
    suspend fun getPageNum (id: String): Int

    @Query("SELECT sourceOrdinal FROM Books WHERE id = :id")
    suspend fun getSourceOrdinal (id: String): Int

    @Query("SELECT coverPage FROM Books WHERE id = :id")
    suspend fun getCoverPage (id: String): Int

    @Query("SELECT skipPagesJson FROM Books WHERE id = :id")
    suspend fun getSkipPagesJson (id: String): String

    @Query("SELECT lastViewTime FROM Books WHERE id = :id")
    suspend fun getLastViewTime (id: String): Long

    @Query("SELECT coverCropPositionString FROM Books WHERE id = :id")
    suspend fun getCoverCropPositionString (id: String): String?

    @Query("" +
        "UPDATE Books " +
        "SET customTitle = (CASE WHEN :value = '' THEN NULL ELSE :value END) " +
        "WHERE id = :id" +
    "")
    suspend fun updateCustomTitle (id: String, value: String)

    @Query("" +
        "UPDATE Books " +
        "SET coverCropPositionString = :value " +
        "WHERE id = :id" +
    "")
    suspend fun updateCoverCropPositionString (id: String, value: String)

    @Query("DELETE FROM Books WHERE id = :id")
    suspend fun deleteById (id: String)
}
