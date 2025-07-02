package com.example.viewer.data.repository

import android.content.Context
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMinByOrNull
import com.example.viewer.data.dao.ExcludeTagDao
import com.example.viewer.data.database.SearchDatabase
import com.example.viewer.data.struct.ExcludeTag
import com.example.viewer.struct.Category
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class ExcludeTagRepository (context: Context) {
    companion object {
        private var listLastUpdateTime = 0L
        private var initialized = false

        // excluded category and tag
        private val excludedBookCategories = mutableSetOf<String>()
        private val excludedTagCategories = mutableSetOf<String>()
        private val excludedTagValues = mutableSetOf<String>()

        private const val CACHE_SIZE = 10
        private val excludeTagCache = mutableListOf<CacheRecord>()
    }

    private val excludeTagDao: ExcludeTagDao

    init {
        SearchDatabase.getInstance(context).run {
            excludeTagDao = this.excludeTagDao()
        }
        // init categoryTagMap
        if (!initialized) {
            val excludeTags = runBlocking { excludeTagDao.queryAll() }
            for (item in excludeTags) {
                if (excludedBookCategories.size < 4) {
                    for (category in item.getCategories()) {
                        excludedBookCategories.add(category.name)
                    }
                }
                val tags = item.getTags()
                for (tagCategory in tags.keys) {
                    excludedTagCategories.add(tagCategory)
                }
                for (tagValue in tags.values.flatten()) {
                    excludedTagValues.add(tagValue)
                }
            }
            println(excludedBookCategories)
            println(excludedTagCategories)
            println(excludedTagValues)
            initialized = true
        }
    }

    fun getAllExcludeTag (): List<ExcludeTag> = runBlocking { excludeTagDao.queryAll() }

    fun getExcludeTag (id: Int) = runBlocking { excludeTagDao.queryById(id) }

    fun addExcludeTag (
        tags: Map<String, List<String>>,
        categories: List<Category>
    ) = runBlocking {
        val gson = Gson()
        excludeTagDao.insert(
            ExcludeTag(
                id = excludeTagDao.getNextId(),
                tagsJson = gson.toJson(tags).toString(),
                categoryOrdinalsJson = gson.toJson(
                    categories.map { it.ordinal }
                ).toString()
            )
        )
        excludedBookCategories.addAll(categories.fastMap { it.name })
        excludedTagCategories.addAll(tags.keys)
        excludedTagValues.addAll(tags.values.flatten())
        listLastUpdateTime = System.currentTimeMillis()
    }

    fun modifyExcludeTag (
        id: Int,
        tags: Map<String, List<String>>,
        categories: List<Category>
    ) = runBlocking {
        val gson = Gson()
        excludeTagDao.update(
            excludeTagDao.queryById(id).apply {
                tagsJson = gson.toJson(tags)
                categoryOrdinalsJson = gson.toJson(
                    categories.map { it.ordinal }
                )
            }
        )
    }

    fun lastExcludeTagUpdateTime () = listLastUpdateTime

    fun doExclude (categories: List<Category>, tags: Map<String, List<String>>): Boolean {
        val categoryNames = categories.fastMap { it.name }.toSet()
        val setTags = tags.keys.associateWith { tags.getValue(it).toSet() }

        if (
            !intersectAny(categoryNames, excludedBookCategories) ||
            !intersectAny(setTags.keys, excludedTagCategories) ||
            !intersectAny(tags.values.flatten().toSet(), excludedTagValues)
        ) { return false }

        // check cache
        val checkedIds = mutableSetOf<Int>()
        for (cacheRecord in excludeTagCache) {
            if (!cacheRecord.categories.containsAll(categoryNames)) {
                continue
            }
            for ((k, v) in setTags.entries) {
                if (
                    cacheRecord.tags.containsKey(k) &&
                    v.containsAll(cacheRecord.tags.getValue(k))
                ) {
                    cacheRecord.count++
                    return true
                } else if (cacheRecord.count != 0) {
                    cacheRecord.count--
                }
            }
            checkedIds.add(cacheRecord.id)
        }

        // check database
        for (excludeTag in runBlocking { excludeTagDao.queryAll() }) {
            if (checkedIds.contains(excludeTag.id)) {
                continue
            }

            val excludeCategories = excludeTag.getCategories().fastMap { it.name }.toSet()
            if (!excludeCategories.containsAll(categoryNames)) {
                continue
            }

            val excludeTags = excludeTag.getTags().mapValues { (_, v) -> v.toSet() }
            for ((k, v) in setTags.entries) {
                if (
                    excludeTags.containsKey(k) &&
                    v.containsAll(excludeTags.getValue(k))
                ) {
                    if (excludeTagCache.size < CACHE_SIZE) {
                        excludeTagCache.add(CacheRecord(
                            id = excludeTag.id,
                            categories = excludeCategories,
                            tags = excludeTags
                        ))
                    } else {
                        excludeTagCache.fastMinByOrNull { it.count }!!.apply {
                            this.id = excludeTag.id
                            this.categories = excludeCategories
                            this.tags = excludeTags
                            this.count = 1
                        }
                    }
                    return true
                }
            }
        }

        return false
    }

    private fun<T> intersectAny (a: Set<T>, b: Set<T>): Boolean {
        val (x, y) = if (a.size <= b.size) Pair(a, b) else Pair(b, a)
        return x.any { y.contains(it) }
    }

    private data class CacheRecord (
        var id: Int,
        var categories: Set<String>,
        var tags: Map<String, Set<String>>,
        var count: Int = 0
    )
}