package com.example.viewer.activity.search

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.viewer.OkHttpHelper
import com.example.viewer.Util
import com.example.viewer.data.repository.RuTagRepository
import com.example.viewer.data.struct.interaction.RuTag
import com.example.viewer.preference.KeyPreference
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemType
import com.example.viewer.struct.ProfileItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RuSearchHelper (
    context: Context,
    searchMarkData: SearchMarkData
): SearchHelper(context, searchMarkData) {
    private val baseUrl = buildBaseUrl(context, searchMarkData)
    private val okHttpHelper = OkHttpHelper()

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
            val xml = okHttpHelper.get(baseUrl).body!!.string()
            val regex = """count="(\d+)"""".toRegex()
            val matchResult = regex.find(xml.split("\n").first())
            total = matchResult!!.groups[1]!!.value.toInt()
        }

        val records = okHttpHelper.get(searchUrl).body!!.string().let {
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
            id = -1L, // internal id
            url = searchItemData.url,
            title = searchItemData.title,
            subTitle = searchItemData.title,
            customTitle = null,
            tags = processRawTags(searchItemData.tags.getValue("tags")),
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

    private suspend fun processRawTags (tags: List<String>): Map<String, List<String>> {
        val repo = RuTagRepository(context)

        val (ruTagTypeRecords, unsearched) = repo.queryType(tags).let {
            it.first to it.second.toMutableSet()
        }
        var ret = ruTagTypeRecords.groupBy (
            { it.type }, { it.tag }
        )
        if (unsearched.isEmpty()) {
            return ret
        }

        // search tag types
        ret = ret.mapValues { it.value.toMutableList() }
            .toMutableMap()

        val type2id = repo.queryAllTypes().associateBy(
            { it.type }, { it.id }
        ).toMutableMap()
        val searchedRuTags = mutableListOf<RuTag>()
        val headers = mapOf(
            "origin" to "https://rule34.xxx",
            "referer" to "https://rule34.xxx/"
        )
        while (unsearched.isNotEmpty()) {
            val tag = unsearched.first()

            val response = withContext(Dispatchers.IO) {
                okHttpHelper.get(
                    "https://ac.rule34.xxx/autocomplete.php?q=$tag",
                    headers
                )
            }
            if (response.code != 200) {
                Util.log(
                    "${this::class.simpleName}.${this::processRawTags.name}",
                    "tag $tag status code ${response.code}"
                )
                continue
            }

            val autoCompleteResults = Gson().fromJson<List<AutoCompleteResult>>(
                response.body!!.string(),
                object: TypeToken<List<AutoCompleteResult>>(){}.type
            )
            for (result in autoCompleteResults) {
                if (!type2id.containsKey(result.type)) {
                    type2id[result.type] = repo.addType(result.type)
                }

                if (unsearched.contains(result.value)) {
                    if(!ret.containsKey(result.type)) {
                        ret[result.type] = mutableListOf()
                    }
                    ret[result.type]!!.add(result.value)
                    unsearched.remove(result.value)
                }

                searchedRuTags.add(RuTag(
                    tag = result.value,
                    typeId = type2id[result.type]!!
                ))

                println("${result.value} ${result.type}")
            }

            // avoid infinite loop
            if (unsearched.contains(tag)) {
                unsearched.remove(tag)
                Util.log(
                    "${this::class.simpleName}.${this::processRawTags.name}",
                    "tag $tag is not added into ret after search its result."
                )
            }
        }

        // store new tag records into db
        repo.addTags(searchedRuTags)

        for (v in ret.values) {
            v.sort()
        }
        return ret
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

    private data class AutoCompleteResult (
        val label: String,
        val value: String,
        val type: String
    )
}