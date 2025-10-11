package com.example.viewer.data.repository

import android.content.Context
import android.graphics.PointF
import androidx.room.Transaction
import com.example.viewer.Util
import com.example.viewer.data.dao.BookDao
import com.example.viewer.data.dao.BookWithGroupDao
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

class BookRepository (private val context: Context) {
    companion object {
        private var listLastUpdateTime = 0L
        fun getListLastUpdateTime () = listLastUpdateTime
    }

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
                    customTitle = null,
                    coverCropPositionString = null,
                    pageUrlsJson = pageUrls?.let {
                        gson.toJson(it).toString()
                    },
                    p = p
                )
            )
            bookWithGroupDao.insert(
                BookWithGroup(
                    bookId = id,
                    bookSourceOrdinal = source.ordinal,
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
    ) = runBlocking {
        val gson = Gson()
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
                customTitle = null,
                coverCropPositionString = null,
                pageUrlsJson = when (source) {
                    BookSource.E, BookSource.Wn -> gson.toJson(listOf<String>()).toString()
                    BookSource.Hi -> null
                },
                p = when (source) {
                    BookSource.E -> 0
                    BookSource.Wn -> 1
                    BookSource.Hi -> null
                }
            )
        )
        listLastUpdateTime = System.currentTimeMillis()
    }

    fun addBook (book: Book) = runBlocking { bookDao.insert(book) }

    fun getBook (id: String): Book =
        if (id == "-1") { Book.getTmpBook() } else runBlocking { bookDao.queryById(id) }

    @Transaction
    fun removeBook (book: Book): Boolean {
        runBlocking { bookDao.deleteById(book.id) }

        val bookFolder = book.getBookFolder(context)
        for (file in bookFolder.listFiles()!!) {
            if(!file.delete()) {
                throw Exception("delete image failed")
            }
        }
        if(!bookFolder.delete()) {
            throw Exception("delete book folder failed")
        }

        listLastUpdateTime = System.currentTimeMillis()

        return true
    }

    fun isBookStored (id: String) = runBlocking { bookDao.countId(id) != 0 }

    fun getBookMarks (id: String): List<Int> {
        val book = runBlocking { bookDao.queryById(id) }
        return Util.readListFromJson(book.bookMarksJson)
    }

    fun addBookMark (id: String, page: Int) {
        val gson = Gson()
        val dao = bookDao

        val book = runBlocking { dao.queryById(id) }
        val bookmarks = Util.readListFromJson<Int>(book.bookMarksJson).toMutableList()
        bookmarks.add(page)
        book.bookMarksJson = gson.toJson(bookmarks.toList()).toString()

        runBlocking { dao.update(book) }
    }

    fun removeBookMark (id: String, page: Int) {
        val gson = Gson()
        val dao = bookDao

        val book = runBlocking { dao.queryById(id) }
        val bookmarks = Util.readListFromJson<Int>(book.bookMarksJson).toMutableList()
        if (!bookmarks.remove(page)) {
            throw Exception("the bookmark page $page is not exist")
        }
        book.bookMarksJson = gson.toJson(bookmarks.toList()).toString()

        runBlocking { dao.update(book) }
    }

    fun getAllBookIds () = runBlocking { bookDao.getAllBookIds() }

    fun getBookIdSeqH () = runBlocking { bookDao.getBookIdSeqH() }

    fun getBookIdSeqNH () = runBlocking { bookDao.getBookIdSeqNH() }

    fun getBookUrl (id: String) = runBlocking { bookDao.getUrl(id) }

    fun getBookPageUrls (id: String): Array<String?> {
        val book = queryBook(id)
        if (book.sourceOrdinal == BookSource.Hi.ordinal) {
            throw Exception("Page urls are not stored for this book source")
        }

        val stored = Util.readArrayFromJson<String?>(book.pageUrlsJson!!)
        if (stored.size == book.pageNum) {
            return stored
        }

        return arrayOfNulls<String>(book.pageNum).apply {
            for ((i, v) in stored.withIndex()) {
                this[i] = v
            }
        }
    }
    fun setBookPageUrls (id: String, urls: Array<String?>) {
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
        BookSource.fromOrdinal(runBlocking { bookDao.getSourceOrdinal(id) })

    fun getBookCoverPage (id: String): Int = runBlocking { bookDao.getCoverPage(id) }
    fun setBookCoverPage (id: String, v: Int) {
        val dao = bookDao
        val book = queryBook(id)
        book.coverPage = v
        runBlocking { dao.update(book) }
        listLastUpdateTime = System.currentTimeMillis()
    }

    fun getBookSkipPages (id: String): List<Int> = Util.readListFromJson(
        runBlocking { bookDao.getSkipPagesJson(id) }.also { println(it) }
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

    /**
     * get group id of a book
     * @param id book id
     */
    fun getGroupId (id: String): Int = runBlocking { bookWithGroupDao.queryGroupId(id) }

    fun updateCustomTitle (id: String, value: String) = runBlocking {
        bookDao.updateCustomTitle(id, value)
    }

    fun getCoverCropPosition (id: String): PointF? = runBlocking {
        bookDao.getCoverCropPositionString(id)?.let {
            Book.coverCropPositionStringToPoint(it)
        }
    }

    /**
     * @param position this should be in normalized coordinates
     */
    fun updateCoverCropPosition (id: String, position: PointF) = runBlocking {
        if (position.x < 0 || position.x > 1 || position.y < 0 || position.y > 1) {
            throw IllegalArgumentException("the position seems not a valid normalized coordinates")
        }
        bookDao.updateCoverCropPositionString(id, "${position.x},${position.y}")
    }

    private fun queryBook (id: String) = runBlocking { bookDao.queryById(id) }
}