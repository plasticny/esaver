package com.example.viewer.data.repository

import android.content.Context
import android.graphics.PointF
import androidx.room.Transaction
import com.example.viewer.activity.search.SearchItemData
import com.example.viewer.data.dao.item.ItemCommonCustomDao
import com.example.viewer.data.dao.item.ItemDao
import com.example.viewer.data.database.ItemDatabase
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.ItemCommonCustom
import com.example.viewer.struct.ItemSource
import kotlinx.coroutines.runBlocking

class ItemRepository (private val context: Context) {
    companion object {
        private var listLastUpdateTime = 0L
        fun getListLastUpdateTime () = listLastUpdateTime
        fun updateListLastUpdateTime () {
            listLastUpdateTime = System.currentTimeMillis()
        }
    }

    private val itemDao: ItemDao
    private val itemCommonCustomDao: ItemCommonCustomDao

    init {
        ItemDatabase.getInstance(context).run {
            itemDao = this.itemDao()
            itemCommonCustomDao = this.itemCommonCustomDao()
        }
    }

    fun addItem (item: Item): Long = runBlocking {
        val id = itemDao.insert(item)
        itemCommonCustomDao.insert(ItemCommonCustom(
            internalId = id,
            coverPage = 0,
            coverCropPositionString = null,
            customTitle = null
        ))
        updateListLastUpdateTime()
        id
    }

    fun getItem (id: Long): Item = runBlocking { itemDao.queryAll(id) }

    fun getSource (id: Long): ItemSource = runBlocking {
        ItemSource.fromOrdinal(itemDao.querySourceOrdinal(id))
    }

    fun getCommonCustom (id: Long): ItemCommonCustom = runBlocking {
        itemCommonCustomDao.queryAll(id)
    }

    fun getCoverPage (id: Long): Int = runBlocking { itemCommonCustomDao.queryCoverPage(id) }

    fun getCoverCropPosition (id: Long): PointF? = runBlocking {
        itemCommonCustomDao.queryCoverCropPositionString(id)?.let {
            ItemCommonCustom.coverCropPositionStringToPoint(it)
        }
    }

    fun getIdSeqH (): List<Item.Companion.SequenceItem> = runBlocking { itemDao.queryIdSeqH() }

    fun getIdSeqNH (): List<Item.Companion.SequenceItem> = runBlocking { itemDao.queryIdSeqNH() }

    // if item stored, return its internal id
    fun isItemStored (searchItemData: SearchItemData, source: ItemSource): Long =
        when (source) {
            ItemSource.Ru -> -1L
            ItemSource.E, ItemSource.Wn -> BookRepository(context).isBookStored(searchItemData.bookId!!, source)
            ItemSource.Hi -> throw NotImplementedError()
        }

    @Transaction
    fun removeItem (itemId: Long): Boolean {
        val groupId = runBlocking { itemDao.queryGroupId(itemId) }
        runBlocking { itemDao.delete(itemId) }
        GroupRepository(context).removeIfEmpty(groupId)

        val itemFolder = Item.getFolder(context, itemId)
        for (file in itemFolder.listFiles()!!) {
            if(!file.delete()) {
                throw Exception("delete image failed")
            }
        }
        if(!itemFolder.delete()) {
            throw Exception("delete folder failed")
        }

        updateListLastUpdateTime()

        return true
    }
}