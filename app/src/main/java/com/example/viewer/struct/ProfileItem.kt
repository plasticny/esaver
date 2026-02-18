package com.example.viewer.struct

import android.content.Context
import android.graphics.PointF
import com.example.viewer.Util
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.data.repository.ItemRepository
import com.example.viewer.data.repository.VideoRepository
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.ItemCommonCustom
import java.io.File

data class ProfileItem (
    /**
     * internal id
     */
    val id: Long,
    val url: String,
    val title: String,
    val subTitle: String,
    val customTitle: String?,
    val tags: Map<String, List<String>>,
    val excludedTags: Map<String, Set<String>>,
    val source: ItemSource,
    val type: ItemType,
    val category: Category,
    val coverPage: Int,
    val coverUrl: String,
    val coverCropPosition: PointF?,
    val uploader: String?,
    val isTmp: Boolean,

    val bookData: BookData? = null,
    val videoData: VideoData? = null
) {
    companion object {
        private var tmpProfileItem: ProfileItem? = null

        @JvmStatic
        fun build (context: Context, itemId: Long): ProfileItem {
            val item = ItemRepository(context).getItem(itemId)
            return when (ItemSource.fromOrdinal(item.sourceOrdinal)) {
                ItemSource.E -> buildE(context, item)
                ItemSource.Wn -> buildWn(context, item)
                ItemSource.Ru -> buildRu(context, item)
                ItemSource.Hi -> throw NotImplementedError()
            }
        }

        @JvmStatic
        fun buildE (context: Context, item: Item): ProfileItem {
            assert(item.sourceOrdinal == ItemSource.E.ordinal)

            val commonCustom = ItemRepository(context).getCommonCustom(item.id)
            val dataSource = BookRepository(context).getESourceData(item.id)

            val tags: Map<String, List<String>> = Util.readMapFromJson(dataSource.tagsJson)
            val category = Category.fromOrdinal(item.categoryOrdinal)
            return ProfileItem(
                id = item.id,
                url = dataSource.url,
                title = dataSource.title,
                subTitle = dataSource.subTitle,
                customTitle = commonCustom.customTitle,
                tags = tags,
                excludedTags = ExcludeTagRepository(context).findExcludedTags(tags, category),
                source = ItemSource.E,
                type = ItemType.Book,
                category = category,
                coverPage = commonCustom.coverPage,
                coverUrl = File(Item.getFolder(context, item.id), commonCustom.coverPage.toString()).path,
                coverCropPosition = commonCustom.coverCropPositionString?.let { ItemCommonCustom.coverCropPositionStringToPoint(it) },
                uploader = dataSource.uploader,
                isTmp = false,
                bookData = BookData(
                    id = dataSource.bookId,
                    pageNum = dataSource.pageNum
                )
            )
        }

        @JvmStatic
        fun buildWn (context: Context, item: Item): ProfileItem {
            assert(item.sourceOrdinal == ItemSource.Wn.ordinal)

            val commonCustom = ItemRepository(context).getCommonCustom(item.id)
            val dataSource = BookRepository(context).getWnSourceData(item.id)

            val tags: Map<String, List<String>> = Util.readMapFromJson(dataSource.tagsJson)
            val category = Category.fromOrdinal(item.categoryOrdinal)
            return ProfileItem(
                id = item.id,
                url = dataSource.url,
                title = dataSource.title,
                subTitle = "",
                customTitle = commonCustom.customTitle,
                tags = tags,
                excludedTags = ExcludeTagRepository(context).findExcludedTags(tags, category),
                source = ItemSource.Wn,
                type = ItemType.Book,
                category = category,
                coverPage = commonCustom.coverPage,
                coverUrl = File(Item.getFolder(context, item.id), commonCustom.coverPage.toString()).path,
                coverCropPosition = commonCustom.coverCropPositionString?.let { ItemCommonCustom.coverCropPositionStringToPoint(it) },
                uploader = dataSource.uploader,
                isTmp = false,
                bookData = BookData(
                    id = dataSource.bookId,
                    pageNum = dataSource.pageNum
                )
            )
        }

        @JvmStatic
        fun buildRu (context: Context, item: Item): ProfileItem {
            assert(item.sourceOrdinal == ItemSource.Ru.ordinal)

            val commonCustom = ItemRepository(context).getCommonCustom(item.id)
            val dataSource = VideoRepository(context).getRuSourceData(item.id)
            return ProfileItem (
                id = item.id,
                url = "",
                title = dataSource.title,
                subTitle = "",
                customTitle = commonCustom.customTitle,
                tags = Util.readMapFromJson(dataSource.tagsJson),
                excludedTags = mapOf(),
                source = ItemSource.Ru,
                type = ItemType.Video,
                category = Category.fromOrdinal(item.categoryOrdinal),
                coverPage = commonCustom.coverPage,
                coverUrl = File(Item.getFolder(context, item.id), commonCustom.coverPage.toString()).path,
                coverCropPosition = commonCustom.coverCropPositionString?.let { ItemCommonCustom.coverCropPositionStringToPoint(it) },
                uploader = dataSource.uploader,
                isTmp = false,
                videoData = VideoData(
                    id = dataSource.videoId,
                    videoUrl = dataSource.videoUrl
                )
            )
        }

        fun setTmp (item: ProfileItem) {
            tmpProfileItem = item
        }

        fun getTmp (): ProfileItem = tmpProfileItem!!

        fun clearTmp () {
            tmpProfileItem = null
        }
    }

    data class BookData (
        val id: String,
        val pageNum: Int
    )

    data class VideoData (
        val id: String,
        val videoUrl: String
    )
}