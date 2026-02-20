package com.example.viewer.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Transaction
import com.example.viewer.R
import com.example.viewer.data.dao.item.ItemDao
import com.example.viewer.data.dao.item.ItemGroupDao
import com.example.viewer.data.database.ItemDatabase
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.ItemGroup
//import com.example.viewer.data.dao.BookWithGroupDao
//import com.example.viewer.data.dao.GroupDao
//import com.example.viewer.data.database.BookDatabase
//import com.example.viewer.data.struct.BookWithGroup
//import com.example.viewer.data.struct.Group
import kotlinx.coroutines.runBlocking

class GroupRepository (context: Context) {
    companion object {
        const val DEFAULT_GROUP_ID = 0
        private var latestUpdateTime = 0L
        fun getLastUpdateTime (): Long = latestUpdateTime
    }

    private val itemDao: ItemDao
    private val itemGroupDao: ItemGroupDao

    init {
        ItemDatabase.getInstance(context).run {
            itemDao = this.itemDao()
            itemGroupDao = this.itemGroupDao()
        }
        confirmDefaultGroupExist(context)
    }

    fun getAllGroupIdsInOrder (): List<Int> = runBlocking { itemGroupDao.queryIdsInOrder() }

    fun getAllGroupsInOrder (): List<ItemGroup> = runBlocking { itemGroupDao.queryAllInOrder() }

    fun getGroupName (id: Int): String = runBlocking { itemGroupDao.queryName(id) }

    fun getInternalIds (groupId: Int): List<Long> = runBlocking { itemDao.queryIdsByGroup(groupId) }

    fun getGroupIdFromName (name: String): Int? = runBlocking { itemGroupDao.queryId(name) }

    fun getGalleryItem (groupId: Int): List<Item.Companion.GalleryItem> = runBlocking {
        itemDao.queryGalleryItemByGroupId(groupId)
    }

    @Transaction
    fun changeGroup (internalId: Long, oldGroupId: Int, newGroupId: Int) = runBlocking {
        itemDao.updateGroupId(internalId, newGroupId)
        if (oldGroupId != DEFAULT_GROUP_ID) {
            removeIfEmpty(oldGroupId)
        }
        ItemRepository.updateListLastUpdateTime()
    }

    fun changeGroupName (id: Int, name: String) = runBlocking {
        itemGroupDao.updateName(id, name)
    }

    fun createGroup (name: String): Int = runBlocking {
        itemGroupDao.queryNextId().also {
            itemGroupDao.insert(it, name)
            latestUpdateTime = System.currentTimeMillis()
        }
    }

    fun moveGroupBefore (id: Int, toId: Int) = runBlocking {
        moveGroup(itemGroupDao.queryItemOrder(id), itemGroupDao.queryItemOrder(toId))
    }

    fun moveGroupAfter (id: Int, toId: Int) = runBlocking {
        val from = itemGroupDao.queryItemOrder(id)
        val to = itemGroupDao.queryItemOrder(toId)
        if (from == to) {
            return@runBlocking
        }
        moveGroup(from, to)
    }

    fun removeIfEmpty (id: Int) = runBlocking {
        if (id == DEFAULT_GROUP_ID) {
            return@runBlocking
        }
        if(getInternalIds(id).isEmpty()) {
            removeGroup(id)
        }
    }

    @Transaction
    private suspend fun moveGroup (fromOrder: Int, toOrder: Int) {
        if (fromOrder == toOrder) {
            throw IllegalArgumentException("fromOrder == toOrder, something went wrong")
        }

        itemGroupDao.updateItemOrderByOrder(fromOrder, -1)
        try {
            if (fromOrder < toOrder) {
                for (order in fromOrder + 1 .. toOrder) {
                    itemGroupDao.updateItemOrderByOrder(order, order - 1)
                }
            } else {
                for (order in (toOrder until fromOrder).reversed()) {
                    itemGroupDao.updateItemOrderByOrder(order, order + 1)
                }
            }
        } catch (e: SQLiteConstraintException) {
            itemGroupDao.updateItemOrderByOrder(-1, fromOrder)
            throw e
        }
        itemGroupDao.updateItemOrderByOrder(-1, toOrder)

        latestUpdateTime = System.currentTimeMillis()
    }

    @Transaction
    private fun removeGroup (id: Int) {
        if (id == 0) {
            throw IllegalArgumentException("cannot remove default group")
        }
        runBlocking {
            if (getInternalIds(id).isNotEmpty()) {
                throw IllegalStateException("Cannot delete group if some book in the group")
            }
            val deletedOrder = itemGroupDao.queryItemOrder(id)
            itemGroupDao.delete(id)
            for (order in deletedOrder + 1 .. itemGroupDao.queryMaxItemOrder()) {
                itemGroupDao.decreaseItemOrder(order)
            }
        }
        latestUpdateTime = System.currentTimeMillis()
    }

    private fun confirmDefaultGroupExist (context: Context) = runBlocking {
        if (itemGroupDao.countId(0) == 0) {
            itemGroupDao.insert(0,  context.getString(R.string.noGroup))
        }
    }
}