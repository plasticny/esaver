package com.example.viewer.data.repository

import android.content.Context
import androidx.room.Transaction
import com.example.viewer.data.dao.ExcludeTagDao
import com.example.viewer.data.dao.SearchMarkDao
import com.example.viewer.data.database.SearchDatabase
import com.example.viewer.data.struct.ExcludeTag
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.struct.Category
import com.example.viewer.R
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class SearchRepository (context: Context) {
    companion object {
        private var searchMarkListLastUpdateTime = 0L
    }

    private val searchMarkDao: SearchMarkDao

    init {
        SearchDatabase.getInstance(context).run {
            searchMarkDao = this.searchMarkDao()
        }
    }

    fun getAllSearchMarkIds () = runBlocking { searchMarkDao.getAllIds() }

    fun getSearchMark (id: Long) = runBlocking { searchMarkDao.queryById(id) }

    fun getAllSearchMarkInListOrder (): List<SearchMark> = runBlocking {
        searchMarkDao.queryAllInOrder()
    }

    @Transaction
    fun removeSearchMark (id: Long) = runBlocking {
        val removedOrder = searchMarkDao.queryItemOrder(id)!!
        searchMarkDao.deleteById(id)
        searchMarkDao.decreaseItemOrder(removedOrder + 1)
        searchMarkListLastUpdateTime = System.currentTimeMillis()
    }

    @Transaction
    fun addSearchMark (
        name: String,
        categories: List<Category> = listOf(),
        keyword: String,
        tags: Map<String, List<String>> = mapOf(),
        uploader: String?,
        doExclude: Boolean
    ): Long = runBlocking {
        val gson = Gson()
        val id = searchMarkDao.insert(
            SearchMark(
                name = name,
                categoryOrdinalsJson = gson.toJson(
                    categories.map { it.ordinal }
                ).toString(),
                keyword = keyword,
                tagsJson = gson.toJson(tags).toString(),
                uploader = uploader,
                doExclude = doExclude,
                itemOrder = searchMarkDao.getNextItemOrder()
            )
        )

        searchMarkListLastUpdateTime = System.currentTimeMillis()

        return@runBlocking id
    }

    fun modifySearchMark (
        id: Long,
        name: String,
        categories: List<Category>,
        keyword: String,
        tags: Map<String, List<String>>,
        uploader: String?,
        doExclude: Boolean
    ) = runBlocking {
        val gson = Gson()
        searchMarkDao.update(
            searchMarkDao.queryById(id).apply {
                this.name = name
                this.categoryOrdinalsJson = gson.toJson(categories.map { it.ordinal })
                this.keyword = keyword
                this.tagsJson = gson.toJson(tags)
                this.uploader = uploader
                this.doExclude = doExclude
            }
        )
    }

    /**
     * move the search mark with id to the front of toId
     */
    @Transaction
    fun moveSearchMarkPosition (id: Long, toId: Long) = runBlocking {
        if (id == toId) {
            return@runBlocking
        }

        val fromOrder = searchMarkDao.queryItemOrder(id)!!
        val toOrder = searchMarkDao.queryItemOrder(toId)!!

        if (id < toId) {
            searchMarkDao.decreaseItemOrder(fromOrder + 1, toOrder - 1)
            searchMarkDao.updateItemOrder(id, toOrder - 1)
        }
        // id > toId
        else {
            searchMarkDao.increaseItemOrder(toOrder, fromOrder - 1)
            searchMarkDao.updateItemOrder(id, toOrder)
        }
    }

    fun getSearchMarkListUpdateTime () = searchMarkListLastUpdateTime
}