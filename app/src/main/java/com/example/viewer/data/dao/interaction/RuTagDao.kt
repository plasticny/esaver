package com.example.viewer.data.dao.interaction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.viewer.data.repository.RuTagRepository
import com.example.viewer.data.struct.interaction.RuTag

@Dao
interface RuTagDao {
    @Insert
    suspend fun insert (v: RuTag): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll (tags: List<RuTag>)

    @Query("" +
        "SELECT RT.tag, RTT.type " +
        "FROM RuTags AS RT " +
        "JOIN RuTagTypes AS RTT ON RT.typeId = RTT.id " +
        "WHERE RT.tag IN (:tags)")
    suspend fun queryRuTagTypeRecord (tags: List<String>): List<RuTagRepository.RuTagTypeRecord>
}