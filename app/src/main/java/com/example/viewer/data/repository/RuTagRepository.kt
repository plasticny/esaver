package com.example.viewer.data.repository

import android.content.Context
import com.example.viewer.data.dao.interaction.RuTagDao
import com.example.viewer.data.dao.interaction.RuTagTypeDao
import com.example.viewer.data.database.InteractionDatabase
import com.example.viewer.data.struct.interaction.RuTag
import com.example.viewer.data.struct.interaction.RuTagType
import kotlinx.coroutines.runBlocking

class RuTagRepository (context: Context) {
    private val ruTagDao: RuTagDao
    private val ruTagTypeDao: RuTagTypeDao

    init {
        InteractionDatabase.getInstance(context).run {
            ruTagDao = this.ruTagDao()
            ruTagTypeDao = this.ruTagTypeDao()
        }
    }

    fun addType (value: String): Long = runBlocking {
        ruTagTypeDao.insert(RuTagType(type = value))
    }

    /**
     * if a new record violates unique constraint, it will replace the existing one
     */
    fun addTags (ruTags: List<RuTag>) = runBlocking {
        ruTagDao.insertAll(ruTags)
    }

    /**
     * @return first: RuTagTypeRecord of searched tag, second: not searched tag
     */
    fun queryType (tags: List<String>): Pair<List<RuTagTypeRecord>, List<String>> {
        val unsearched = tags.toMutableList()
        val records = runBlocking { ruTagDao.queryRuTagTypeRecord(tags) }
        for (record in records) {
            unsearched.remove(record.tag)
        }
        return Pair(records, unsearched)
    }

    fun queryAllTypes (): List<RuTagType> = runBlocking {
        ruTagTypeDao.queryAll()
    }

    data class RuTagTypeRecord (
        val tag: String,
        val type: String
    )
}