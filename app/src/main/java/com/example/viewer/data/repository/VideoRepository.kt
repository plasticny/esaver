package com.example.viewer.data.repository

import android.content.Context
import androidx.room.Transaction
import com.example.viewer.data.dao.item.ItemDao
import com.example.viewer.data.dao.item.SourceDataRuDao
import com.example.viewer.data.database.ItemDatabase
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.SourceDataRu
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.ItemType
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class VideoRepository (private val context: Context) {
    private val itemDao: ItemDao
    private val sourceDataRuDao: SourceDataRuDao

    init {
        ItemDatabase.getInstance(context).run {
            itemDao = this.itemDao()
            sourceDataRuDao = this.sourceDataRuDao()
        }
    }

    @Transaction
    fun addVideo (
        id: String,
        videoUrl: String,
        category: Category,
        source: ItemSource,
        tags: Map<String, List<String>>,
        uploader: String
    ): Long = runBlocking {
        val internalId = ItemRepository(context).addItem(
            Item(
                typeOrdinal = ItemType.Video.ordinal,
                sourceOrdinal = source.ordinal,
                lastViewTime = -1L,
                groupId = GroupRepository.DEFAULT_GROUP_ID,
                categoryOrdinal = category.ordinal,
                orderInGroup = id.toInt()
            )
        )

        when (source) {
            ItemSource.Ru -> sourceDataRuDao.insert(SourceDataRu(
                internalId = internalId,
                videoId = id,
                videoUrl = videoUrl,
                uploader = uploader,
                title = id,
                tagsJson = Gson().toJson(tags).toString()
            ))
            ItemSource.E, ItemSource.Hi, ItemSource.Wn -> throw IllegalArgumentException()
        }

        return@runBlocking internalId
    }

    fun getRuSourceData (internalId: Long): SourceDataRu = runBlocking {
        sourceDataRuDao.queryAll(internalId)
    }
}