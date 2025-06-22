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

class SearchRepository (private val context: Context) {
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

    fun getSearchMark (id: Int) = runBlocking { searchMarkDao.queryById(id) }

    fun getAllSearchMarkInListOrder (): List<SearchMark> = runBlocking {
        searchMarkDao.queryLastInListId()?.let { lastId ->
            val ret = mutableListOf<SearchMark>()
            var curSearchMark: SearchMark = searchMarkDao.queryById(lastId)
            while (true) {
                ret.add(0, curSearchMark)
                curSearchMark = searchMarkDao.queryPreviousId(curSearchMark.id)?.let {
                    searchMarkDao.queryById(it)
                } ?: break
            }
            ret
        } ?: listOf()
    }

    @Transaction
    fun removeSearchMark (id: Int) = runBlocking {
        val nextInList = searchMarkDao.queryNextInListById(id)
        val previousInList = searchMarkDao.queryPreviousId(id)

        searchMarkDao.deleteById(id)
        previousInList?.let {
            searchMarkDao.updateNextInList(previousInList, nextInList)
        }

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
    ): Int = runBlocking {
        val gson = Gson()

        val id = searchMarkDao.countItems() + 1
        val lastInListId = searchMarkDao.queryLastInListId()

        searchMarkDao.insert(
            SearchMark(
                id = id,
                name = name,
                categoryOrdinalsJson = gson.toJson(
                    categories.map { it.ordinal }
                ).toString(),
                keyword = keyword,
                tagsJson = gson.toJson(tags).toString(),
                uploader = uploader,
                doExclude = doExclude,
                nextInList = null
            )
        )
        lastInListId?.let {
            searchMarkDao.updateNextInList(lastInListId, id)
        }

        searchMarkListLastUpdateTime = System.currentTimeMillis()

        return@runBlocking id
    }

    fun modifySearchMark (
        id: Int,
        name: String,
        categories: List<Category>,
        keyword: String,
        tags: Map<String, List<String>>,
        uploader: String?,
        doExclude: Boolean
    ) = runBlocking {
        val gson = Gson()
        searchMarkDao.update(
            SearchMark(
                id = id,
                name = name,
                categoryOrdinalsJson = gson.toJson(categories.map { it.ordinal }),
                keyword = keyword,
                tagsJson = gson.toJson(tags),
                uploader = uploader,
                doExclude = doExclude,
                nextInList = null
            )
        )
    }

    /**
     * move the search mark with id to the front of toId
     */
    @Transaction
    fun moveSearchMarkPosition (id: Int, toId: Int) = runBlocking {
        // link prev and next of the target search mark
        searchMarkDao.queryPreviousId(id)?.let { prevId ->
            val nextId = searchMarkDao.queryPreviousId(id)
            searchMarkDao.updateNextInList(prevId, nextId)
        }
        // move the search mark to the target position
        searchMarkDao.queryPreviousId(toId)?.let {
            searchMarkDao.updateNextInList(it, id)
        }
        searchMarkDao.updateNextInList(id, toId)
    }

    fun getSearchMarkListUpdateTime () = searchMarkListLastUpdateTime
}