package com.example.viewer.data.repository

import android.content.Context
import android.graphics.PointF
import androidx.room.Transaction
import com.example.viewer.Util
import com.example.viewer.data.dao.item.ItemCommonCustomDao
import com.example.viewer.data.dao.item.ItemDao
import com.example.viewer.data.dao.item.SourceDataEDao
import com.example.viewer.data.dao.item.SourceDataWnDao
import com.example.viewer.data.database.ItemDatabase
import com.example.viewer.data.repository.ItemRepository
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.ItemCommonCustom
import com.example.viewer.data.struct.item.SourceDataE
import com.example.viewer.data.struct.item.SourceDataWn
//import com.example.viewer.data.dao.BookDao
//import com.example.viewer.data.dao.BookWithGroupDao
//import com.example.viewer.data.database.BookDatabase
//import com.example.viewer.data.struct.Book
//import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemType
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class BookRepository (private val context: Context) {
    private val itemDao: ItemDao
    private val itemCommonCustomDao: ItemCommonCustomDao
    private val sourceDataEDao: SourceDataEDao
    private val sourceDataWnDao: SourceDataWnDao

    init {
        ItemDatabase.getInstance(context).run {
            itemDao = this.itemDao()
            itemCommonCustomDao = this.itemCommonCustomDao()
            sourceDataEDao = this.sourceDataEDao()
            sourceDataWnDao = this.sourceDataWnDao()
        }
    }

    @Transaction
    fun addBook (
        id: String,
        url: String,
        category: Category,
        title: String,
        subtitle: String = "",
        pageNum: Int,
        tags: Map<String, List<String>>,
        source: ItemSource,
        uploader: String?
    ): Long = runBlocking {
        val gson = Gson()

        val internalId = ItemRepository(context).addItem(
            Item(
                typeOrdinal = ItemType.Book.ordinal,
                sourceOrdinal = source.ordinal,
                lastViewTime = -1L,
                groupId = GroupRepository.DEFAULT_GROUP_ID,
                categoryOrdinal = category.ordinal,
                orderInGroup = id.toInt()
            )
        )
        val emptyIntListJson = gson.toJson(listOf<Int>()).toString()

        when (source) {
            ItemSource.E -> sourceDataEDao.insert(SourceDataE(
                internalId = internalId,
                bookId = id,
                url= url,
                title = title,
                subTitle = subtitle,
                pageNum = pageNum,
                uploader = uploader,
                tagsJson = gson.toJson(tags).toString(),
                skipPagesJson = emptyIntListJson,
                bookMarksJson = emptyIntListJson,
                pageUrlsJson = gson.toJson(listOf<String>()).toString(),
                p = 0
            ))
            ItemSource.Wn -> sourceDataWnDao.insert(SourceDataWn(
                internalId = internalId,
                bookId = id,
                url = url,
                title = title,
                pageNum = pageNum,
                uploader = uploader!!,
                tagsJson = gson.toJson(tags).toString(),
                skipPagesJson = emptyIntListJson,
                bookMarksJson = emptyIntListJson,
                pageUrlsJson = gson.toJson(listOf<String>()).toString(),
                p = 1
            ))
            ItemSource.Ru -> throw IllegalArgumentException()
            ItemSource.Hi -> throw NotImplementedError()
        }

        internalId
    }

    @Transaction
    fun saveAsBook (internalId: Long): Long = runBlocking {
        val originItem = itemDao.queryAll(internalId)
        if (originItem.typeOrdinal != ItemType.Book.ordinal) {
            throw IllegalStateException()
        }
        val source = ItemSource.fromOrdinal(originItem.sourceOrdinal)
        val bookId = getBookId(internalId, source)

        val newId = itemDao.insert(Item(
            typeOrdinal = ItemType.Book.ordinal,
            sourceOrdinal = originItem.sourceOrdinal,
            categoryOrdinal = originItem.categoryOrdinal,
            lastViewTime = -1L,
            groupId = originItem.groupId,
            orderInGroup = bookId.toInt()
        ))
        itemCommonCustomDao.insert(
            itemCommonCustomDao.queryAll(internalId).apply {
                this.internalId = newId
                this.customTitle = "${this.customTitle}_copy"
            }
        )

        when (source) {
            ItemSource.E -> {
                sourceDataEDao.insert(
                    sourceDataEDao.queryAll(internalId).apply {
                        this.internalId = newId
                    }
                )
            }
            ItemSource.Wn -> {
                sourceDataWnDao.insert(
                    sourceDataWnDao.queryAll(internalId).apply {
                        this.internalId = newId
                    }
                )
            }
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }

        ItemRepository.updateListLastUpdateTime()

        newId
    }

    fun getESourceData (internalId: Long): SourceDataE = runBlocking {
        sourceDataEDao.queryAll(internalId)
    }

    fun getWnSourceData (internalId: Long): SourceDataWn = runBlocking {
        sourceDataWnDao.queryAll(internalId)
    }

    fun getBookId (internalId: Long, source: ItemSource? = null): String = runBlocking {
        when (source ?: ItemSource.fromOrdinal(itemDao.querySourceOrdinal(internalId))) {
            ItemSource.E -> sourceDataEDao.queryBookId(internalId)
            ItemSource.Wn -> sourceDataWnDao.queryBookId(internalId)
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
    }

    fun getBookMarks (internalId: Long): List<Int> {
        val sourceOrdinal = runBlocking { itemDao.querySourceOrdinal(internalId) }
        val bookMarksJson = when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> runBlocking { sourceDataEDao.queryBookMarksJson(internalId) }
            ItemSource.Wn -> runBlocking { sourceDataWnDao.queryBookMarksJson(internalId) }
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
        return Util.readListFromJson(bookMarksJson)
    }

    fun addBookMark (internalId: Long, page: Int) {
        val bookmarks = getBookMarks(internalId).toMutableList().also { it.add(page) }
        val bookMarksJson = Gson().toJson(bookmarks.toList()).toString()

        val sourceOrdinal = runBlocking { itemDao.querySourceOrdinal(internalId) }
        when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> runBlocking { sourceDataEDao.updateBookMarksJson(internalId, bookMarksJson) }
            ItemSource.Wn -> runBlocking { sourceDataWnDao.updateBookMarksJson(internalId, bookMarksJson) }
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
    }

    fun removeBookMark (internalId: Long, page: Int) {
        val bookmarks = getBookMarks(internalId).toMutableList()
        if (!bookmarks.remove(page)) {
            throw Exception("the bookmark page $page is not exist")
        }
        val bookMarksJson = Gson().toJson(bookmarks.toList()).toString()

        val sourceOrdinal = runBlocking { itemDao.querySourceOrdinal(internalId) }
        when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> runBlocking { sourceDataEDao.updateBookMarksJson(internalId, bookMarksJson) }
            ItemSource.Wn -> runBlocking { sourceDataWnDao.updateBookMarksJson(internalId, bookMarksJson) }
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
    }

    fun getBookUrl (internalId: Long): String = runBlocking {
        val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
        return@runBlocking when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> sourceDataEDao.queryUrl(internalId)
            ItemSource.Wn -> sourceDataWnDao.queryUrl(internalId)
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
    }

    fun getBookPageUrls (internalId: Long): Array<String?> {
        val (pageUrlJson, pageNum) = runBlocking {
            val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
            return@runBlocking when (ItemSource.fromOrdinal(sourceOrdinal)) {
                ItemSource.E -> sourceDataEDao.queryPageUrlJson(internalId) to sourceDataEDao.queryPageNum(internalId)
                ItemSource.Wn -> sourceDataWnDao.queryPageUrlJson(internalId) to sourceDataWnDao.queryPageNum(internalId)
                ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
            }
        }

        val stored = Util.readArrayFromJson<String?>(pageUrlJson)
        if (stored.size == pageNum) {
            return stored
        }
        return arrayOfNulls<String>(pageNum).apply {
            for ((i, v) in stored.withIndex()) {
                this[i] = v
            }
        }
    }
    fun setBookPageUrls (internalId: Long, urls: Array<String?>) {
        val pageUrlsJson = Gson().toJson(urls).toString()
        runBlocking {
            val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
            when (ItemSource.fromOrdinal(sourceOrdinal)) {
                ItemSource.E -> sourceDataEDao.updatePageUrlJson(internalId, pageUrlsJson)
                ItemSource.Wn -> sourceDataWnDao.updatePageUrlJson(internalId, pageUrlsJson)
                ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
            }
        }
    }

    fun getBookP (internalId: Long): Int = runBlocking {
        val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
        return@runBlocking when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> sourceDataEDao.queryP(internalId)
            ItemSource.Wn -> sourceDataWnDao.queryP(internalId)
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
    }
    fun increaseBookP (internalId: Long): Int {
        val p = getBookP(internalId) + 1
        runBlocking {
            val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
            when (ItemSource.fromOrdinal(sourceOrdinal)) {
                ItemSource.E -> sourceDataEDao.updateP(internalId, p)
                ItemSource.Wn -> sourceDataWnDao.updateP(internalId, p)
                ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
            }
        }
        return p
    }

    fun getBookPageNum (internalId: Long, source: ItemSource? = null): Int = runBlocking {
        val sourceOrdinal = source?.ordinal ?: itemDao.querySourceOrdinal(internalId)
        return@runBlocking when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> sourceDataEDao.queryPageNum(internalId)
            ItemSource.Wn -> sourceDataWnDao.queryPageNum(internalId)
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
    }

    fun getBookCoverPage (internalId: Long): Int = runBlocking {
        itemCommonCustomDao.queryCoverPage(internalId)
    }
    fun setBookCoverPage (internalId: Long, v: Int) = runBlocking {
        itemCommonCustomDao.updateCoverPage(internalId, v)
        ItemRepository.updateListLastUpdateTime()
    }

    fun getBookSkipPages (internalId: Long): List<Int> = runBlocking {
        val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
        val json = when (ItemSource.fromOrdinal(sourceOrdinal)) {
            ItemSource.E -> sourceDataEDao.querySkipPagesJson(internalId)
            ItemSource.Wn -> sourceDataWnDao.querySkipPagesJson(internalId)
            ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
        }
        Util.readListFromJson(json)
    }
    fun setBookSkipPages (internalId: Long, v: List<Int>) {
        val skipPagesJson = Gson().toJson(v).toString()
        runBlocking {
            val sourceOrdinal = itemDao.querySourceOrdinal(internalId)
            when (ItemSource.fromOrdinal(sourceOrdinal)) {
                ItemSource.E -> sourceDataEDao.updateSkipPagesJson(internalId, skipPagesJson)
                ItemSource.Wn -> sourceDataWnDao.updateSkipPagesJson(internalId, skipPagesJson)
                ItemSource.Hi, ItemSource.Ru -> throw IllegalStateException()
            }
        }
    }

    /**
     * @param position this should be in normalized coordinates
     */
    fun updateCoverCropPosition (internalId: Long, position: PointF) = runBlocking {
        if (position.x < 0 || position.x > 1 || position.y < 0 || position.y > 1) {
            throw IllegalArgumentException("the position seems not a valid normalized coordinates")
        }
        itemCommonCustomDao.updateCoverCropPositionString(
            internalId,
            "${position.x},${position.y}"
        )
    }

    // return book's internal id if stored, else -1
    fun isBookStored (bookId: String, source: ItemSource): Long = runBlocking {
        when (source) {
            ItemSource.E -> sourceDataEDao.queryInternalId(bookId) ?: -1L
            ItemSource.Wn -> sourceDataWnDao.queryInternalId(bookId) ?: -1L
            ItemSource.Hi -> throw NotImplementedError()
            ItemSource.Ru -> throw IllegalStateException()
        }
    }
}