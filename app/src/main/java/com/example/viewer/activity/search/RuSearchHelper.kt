package com.example.viewer.activity.search

import android.content.Context
import com.example.viewer.preference.KeyPreference
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemType
import com.example.viewer.struct.ProfileItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RuSearchHelper (
    context: Context,
    searchMarkData: SearchMarkData
): SearchHelper(context, searchMarkData) {
    private val baseUrl = buildBaseUrl(context, searchMarkData)
    private val client = OkHttpClient()

    private var total: Int? = null
    private var count = 0

    init {
        assert(searchMarkData.sourceOrdinal == ItemSource.Ru.ordinal)
        next = 0 // first pid is 0
        prev = ENDED
        searchResultString = "N/A"
    }

    override fun getNextBlockSearchUrl(): String  {
        assert(next != NOT_SET && hasNextBlock)
        return "$baseUrl&json=1&pid=$next"
    }

    override fun getPrevBlockSearchUrl(): String {
        throw NotImplementedError()
    }

    override suspend fun fetchItems(
        searchUrl: String,
        isSearchMarkChanged: () -> Boolean
    ): List<SearchItemData>? {
        if (total == null) {
            val xml = withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder().url(baseUrl).build()
                client.newCall(request).execute()
            }.body!!.string()
            val regex = """count="(\d+)"""".toRegex()
            val matchResult = regex.find(xml.split("\n").first())
            total = matchResult!!.groups[1]!!.value.toInt()
        }

        val records = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(searchUrl).build()
            client.newCall(request).execute()
        }.body!!.string().let {
            Gson().fromJson<List<Record>>(it, object: TypeToken<List<Record>>(){}.type)
        } ?: listOf()

        count += records.size
        next = if (count < total!!) next + 1 else ENDED

        return records.map {
            SearchItemData(
                url = "",
                coverUrl = it.sample_url,
                cat = Category.Doujinshi,
                title = it.id.toString(),
                pageNum = 0,
                tags = mapOf(
                    "tags" to it.tags.split(" ")
                ),
                rating = it.score.toFloat(),
                type = ItemType.Video,
                videoData = SearchItemData.VideoData(
                    videoUrl = it.file_url,
                    id = it.id.toString(),
                    uploader = it.owner
                )
            )
        }
    }

    override suspend fun storeDetailAsTmpProfileItem(searchItemData: SearchItemData): Boolean {
        val videoData = searchItemData.videoData!!

        ProfileItem.setTmp(ProfileItem(
            id = -1, // internal id
            url = searchItemData.url,
            title = searchItemData.title,
            subTitle = searchItemData.title,
            customTitle = null,
            tags = searchItemData.tags,
            excludedTags = mapOf(),
            source = ItemSource.Ru,
            type = ItemType.Video,
            category = searchItemData.cat,
            coverPage = 0,
            coverUrl = searchItemData.coverUrl,
            coverCropPosition = null,
            uploader = videoData.uploader,
            isTmp = true,
            videoData = ProfileItem.VideoData(
                id = videoData.id,
                videoUrl = videoData.videoUrl
            )
        ))
        return true
    }

    private fun buildBaseUrl (context: Context, searchMarkData: SearchMarkData): String {
        val keyPreference = KeyPreference.getInstance(context)
        assert(keyPreference.isRuReady())
        val tags = searchMarkData.keyword.split(" ").joinToString("+") + "+video"
        return "https://api.rule34.xxx/index.php?page=dapi&s=post&limit=50&q=index&tags=$tags&api_key=${keyPreference.getRuApiKey()}&user_id=${keyPreference.getRuUserId()}"
    }

    private data class Record (
        val preview_url: String,
        val sample_url: String,
        val file_url: String,
        val directory: Int,
        val hash: String,
        val width: Int,
        val height: Int,
        val id: Int,
        val image: String,
        val change: Int,
        val owner: String,
        val parent_id: Int,
        val rating: String,
        val sample: Boolean,
        val sample_height: Int,
        val sample_width: Int,
        val score: Int,
        val tags: String,
        val source: String,
        val status: String,
        val has_notes: Boolean,
        val comment_count: Int
    )
}