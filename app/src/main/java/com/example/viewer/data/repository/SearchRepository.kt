package com.example.viewer.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Transaction
import com.example.viewer.data.dao.ExcludeTagDao
import com.example.viewer.data.dao.SearchMarkDao
import com.example.viewer.data.database.SearchDatabase
import com.example.viewer.data.struct.ExcludeTag
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.struct.Category
import com.example.viewer.R
import com.example.viewer.data.dao.SearchHistoryDao
import com.example.viewer.data.struct.SearchHistory
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class SearchRepository (context: Context) {
    companion object {
        private var searchMarkListLastUpdateTime = 0L
    }

    private val searchMarkDao: SearchMarkDao
    private val searchHistoryDao: SearchHistoryDao

    init {
        SearchDatabase.getInstance(context).run {
            searchMarkDao = this.searchMarkDao()
            searchHistoryDao = this.searchHistoryDao()
        }
    }

    fun getSearchMark (id: Long) = runBlocking { searchMarkDao.queryById(id) }

    fun getAllSearchMarkIdsInOrder () = runBlocking { searchMarkDao.queryAllIdsInOrder() }

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
        searchHistoryDao.insert(SearchHistory(id, null))

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

    fun moveSearchMarkBefore (id: Long, toId: Long) = runBlocking {
        moveSearchMark(
            searchMarkDao.queryItemOrder(id)!!,
            searchMarkDao.queryItemOrder(toId)!!
        )
    }

    fun moveSearchMarkAfter (id: Long, toId: Long) = runBlocking {
        val from = searchMarkDao.queryItemOrder(id)!!
        val to = searchMarkDao.queryItemOrder(toId)!!
        if (from == to) {
            return@runBlocking
        }
        moveSearchMark(from, to)
    }

    @Transaction
    private suspend fun moveSearchMark (fromOrder: Int, toOrder: Int) {
        println("[${this::class.simpleName}.${this::moveSearchMark.name}]")

        if (fromOrder == toOrder) {
            throw IllegalArgumentException("fromOrder == toOrder, something went wrong")
        }

        searchMarkDao.updateItemOrderByOrder(fromOrder, -1)
        try {
            if (fromOrder < toOrder) {
                for (order in fromOrder + 1 .. toOrder) {
                    searchMarkDao.updateItemOrderByOrder(order, order - 1)
                }
            } else {
                for (order in (toOrder until fromOrder).reversed()) {
                    searchMarkDao.updateItemOrderByOrder(order, order + 1)
                }
            }
        } catch (e: SQLiteConstraintException) {
            searchMarkDao.updateItemOrderByOrder(-1, fromOrder)
            throw e
        }
        searchMarkDao.updateItemOrderByOrder(-1, toOrder)

        searchMarkListLastUpdateTime = System.currentTimeMillis()
    }

    fun getSearchMarkListUpdateTime () = searchMarkListLastUpdateTime

    fun getLastNext (id: Long) = runBlocking {
        searchHistoryDao.queryLastNext(id)
    }

    fun storeLastNext (searchMarkId: Long, next: String?) = runBlocking {
        if (next == null) {
            searchHistoryDao.clearLastNext(searchMarkId)
        } else {
            searchHistoryDao.updateLastNext(searchMarkId, next)
        }
    }
}