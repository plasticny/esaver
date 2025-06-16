package com.example.viewer.data.repository

import android.content.Context
import com.example.viewer.Util
import com.example.viewer.data.dao.BookDao
import com.example.viewer.data.dao.BookWithGroupDao
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.struct.BookRecord
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import java.io.File

class BookRepository (context: Context) {
    private val bookDao: BookDao
    private val bookWithGroupDao: BookWithGroupDao

    init {
        BookDatabase.getInstance(context).run {
            bookDao = this.bookDao()
            bookWithGroupDao = this.bookWithGroupDao()
        }
    }

    fun addBookFromPreference (
        id: String,
        url: String,
        category: Category,
        title: String,
        subtitle: String = "",
        pageNum: Int,
        tags: Map<String, List<String>>,
        source: BookSource,
        groupId: Int,
        uploader: String?,
        coverPage: Int,
        skipPages: List<Int>,
        lastViewTime: Long,
        bookMarks: List<Int>,
        pageUrls: List<String>?,
        p: Int?
    ) {
        val gson = Gson()
        runBlocking {
            bookDao.insert(
                Book(
                    id = id,
                    url = url,
                    title = title,
                    subTitle = subtitle,
                    pageNum = pageNum,
                    categoryOrdinal = category.ordinal,
                    uploader = uploader,
                    tagsJson = gson.toJson(tags).toString(),
                    sourceOrdinal = source.ordinal,
                    coverPage = coverPage,
                    skipPagesJson = gson.toJson(skipPages).toString(),
                    lastViewTime = lastViewTime,
                    bookMarksJson = gson.toJson(bookMarks).toString(),
                    pageUrlsJson = pageUrls?.let {
                        gson.toJson(it).toString()
                    },
                    p = p
                )
            )
            bookWithGroupDao.insert(
                BookWithGroup(
                    bookId = id,
                    groupId = groupId
                )
            )
        }
    }

    fun addBook (
        id: String,
        url: String,
        category: Category,
        title: String,
        subtitle: String = "",
        pageNum: Int,
        tags: Map<String, List<String>>,
        source: BookSource,
        uploader: String?
    ) {
        if (pageNum < 1) {
            throw Exception("Invalid pageNum $pageNum")
        }
        val gson = Gson()
        runBlocking {
            bookDao.insert(
                Book(
                    id = id,
                    url = url,
                    title = title,
                    subTitle = subtitle,
                    pageNum = pageNum,
                    categoryOrdinal = category.ordinal,
                    uploader = uploader,
                    tagsJson = gson.toJson(tags).toString(),
                    sourceOrdinal = source.ordinal,
                    coverPage = 0,
                    skipPagesJson = gson.toJson(listOf<Int>()).toString(),
                    lastViewTime = -1L,
                    bookMarksJson = gson.toJson(listOf<Int>()).toString(),
                    pageUrlsJson = if (source == BookSource.E) {
                        gson.toJson(listOf<String>()).toString()
                    } else null,
                    p = if (source == BookSource.E) 0 else null
                )
            )
        }
    }

    fun getBook (context: Context, id: String): BookRecord {
        val book = runBlocking { bookDao.queryById(id) }
        return BookRecord(
            id = book.id,
            url = book.url,
            coverUrl = book.coverPage.let { page ->
                val folder = File(context.getExternalFilesDir(null), id)
                val coverPageFile = File(folder, page.toString())
                if (coverPageFile.exists()) {
                    coverPageFile.path
                } else {
                    File(folder, "0").path
                }
            },
            cat = Util.categoryFromOrdinal(book.categoryOrdinal).name,
            title = book.title,
            subtitle = book.subTitle,
            pageNum = book.pageNum,
            tags = readMap<List<String>>(book.tagsJson),
            groupId = runBlocking { bookWithGroupDao.queryGroupId(id) },
            uploader = book.uploader
        )
    }

    fun removeBook (id: String) = runBlocking { bookDao.deleteById(id) }

    fun isBookStored (id: String) = runBlocking { bookDao.countId(id) != 0 }

    fun getBookMarks (id: String): List<Int> {
        val book = runBlocking { bookDao.queryById(id) }
        return readList(book.bookMarksJson)
    }

    fun addBookMark (id: String, page: Int) {
        val gson = Gson()
        val dao = bookDao

        val book = runBlocking { dao.queryById(id) }
        val bookmarks = readList<Int>(book.bookMarksJson).toMutableList()
        bookmarks.add(page)
        book.bookMarksJson = gson.toJson(bookmarks.toList()).toString()

        runBlocking { dao.update(book) }
    }

    fun removeBookMark (id: String, page: Int) {
        val gson = Gson()
        val dao = bookDao

        val book = runBlocking { dao.queryById(id) }
        val bookmarks = readList<Int>(book.bookMarksJson).toMutableList()
        if (!bookmarks.remove(page)) {
            throw Exception("the bookmark page $page is not exist")
        }
        book.bookMarksJson = gson.toJson(bookmarks.toList()).toString()

        runBlocking { dao.update(book) }
    }

    fun getAllBookIds () = runBlocking { bookDao.getAllBookIds() }

    fun getBookUrl (id: String) = runBlocking { bookDao.getUrl(id) }

    fun getBookPageUrls (id: String): List<String> {
        val book = queryBook(id)
        if (book.sourceOrdinal == BookSource.Hi.ordinal) {
            throw Exception("Page urls are not stored for this book source")
        }
        return book.pageUrlsJson!!.let {
            if (it == "[]") {
                listOf()
            } else {
                it.substring(1 until it.length - 1)
                    .split(",")
                    .map { s ->
                        s.trim().substring(1 until s.length - 1)
                    }
            }
        }
    }
    fun setBookPageUrls (id: String, urls: List<String>) {
        val dao = bookDao
        val book = queryBook(id)
        book.pageUrlsJson = Gson().toJson(urls).toString()
        runBlocking { dao.update(book) }
    }

    fun getBookP (id: String): Int = runBlocking { bookDao.getP(id)!! }
    fun increaseBookP (id: String): Int {
        val dao = bookDao
        val book = queryBook(id)
        book.p = book.p!! + 1
        runBlocking { dao.update(book) }
        return book.p!!
    }

    fun getBookPageNum (id: String): Int = runBlocking { bookDao.getPageNum(id) }

    fun getBookSource (id: String): BookSource =
        when (val sourceOrdinal = runBlocking { bookDao.getSourceOrdinal(id) }) {
            BookSource.Hi.ordinal -> BookSource.Hi
            BookSource.E.ordinal -> BookSource.E
            else -> {
                throw Exception("unexpected ordinal $sourceOrdinal")
            }
        }

    fun getBookCoverPage (id: String): Int = runBlocking { bookDao.getCoverPage(id) }
    fun setBookCoverPage (id: String, v: Int) {
        val dao = bookDao
        val book = queryBook(id)
        book.coverPage = v
        runBlocking { dao.update(book) }
    }

    fun getBookSkipPages (id: String): List<Int> = readList(
        runBlocking { bookDao.getSkipPagesJson(id) }
    )
    fun setBookSkipPages (id: String, v: List<Int>) {
        val dao = bookDao
        val book = queryBook(id)
        book.skipPagesJson = Gson().toJson(v).toString()
        runBlocking { dao.update(book) }
    }

    fun getBookLastViewTime (id: String) = runBlocking { bookDao.getLastViewTime(id) }
    fun updateBookLastViewTime (id: String) {
        val dao = bookDao
        val book = queryBook(id)
        book.lastViewTime = System.currentTimeMillis()
        runBlocking { dao.update(book) }
    }

    private fun<T> readMap (json: String): Map<String, T> =
        ObjectMapper().registerKotlinModule()
            .readerFor(Map::class.java)
            .readValues<Map<String, T>>(json)
            .let { if (it.hasNextValue()) it.next() else mapOf() }

    private fun<T> readList (json: String): List<T> =
        ObjectMapper().registerKotlinModule()
            .readerFor(List::class.java)
            .readValues<List<T>>(json)
            .let { if (it.hasNextValue()) it.next() else listOf() }

    private fun queryBook (id: String) = runBlocking { bookDao.queryById(id) }
}