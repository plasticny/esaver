package com.example.viewer

import android.content.Context
import java.util.PriorityQueue
import kotlin.random.Random

class RandomBook private constructor() {
    companion object {
        private const val INIT_POOL_SIZE = 5

        @Volatile
        private var instance: RandomBook? = null

        private fun getInstance (): RandomBook = synchronized(this) {
            instance ?: RandomBook().also { instance = it }
        }

        fun next (context: Context, onlyDownloaded: Boolean = false) = getInstance().next(context, onlyDownloaded)
    }

    private val bookIdSequence: MutableList<String>
    private val arrangedBookId: MutableSet<String>
    private val randomPool = mutableSetOf<String>()
    private var position = INIT_POOL_SIZE - 1

    init {
        // sort the book ids by last view time
        // if the time is the same, random order
        val pq = PriorityQueue(object: Comparator<Pair<String, Long>> {
            override fun compare(a: Pair<String, Long>?, b: Pair<String, Long>?): Int {
                a ?: return 1
                b ?: return -1
                if (a.second != b.second) {
                    return a.second.compareTo(b.second)
                }
                return Random.nextInt(-1, 2)
            }
        })
        for (bookId in History.getAllBookIds()) {
            pq.add(Pair(bookId, History.getBookLastViewTime(bookId)))
        }

        // store result
        bookIdSequence = pq.map { it.first }.toMutableList()
        arrangedBookId = bookIdSequence.toMutableSet()
        randomPool.addAll(bookIdSequence.subList(0, INIT_POOL_SIZE))
    }

    private fun next (context: Context, onlyDownloaded: Boolean): String {
        // check new book added
        getNewAddedBookIds().also {
            if (it.isNotEmpty()) {
                bookIdSequence.addAll(position, it.shuffled())
                arrangedBookId.addAll(it)
            }
        }

        return drawId(context, onlyDownloaded).also {
            randomPool.remove(it)
            randomPool.add(bookIdSequence[position])
            movePosition()
        }
    }

    private fun movePosition () {
        if (++position > bookIdSequence.lastIndex) {
            position = 0
        }
    }

    private fun getNewAddedBookIds (): List<String> {
        val res = mutableListOf<String>()

        // the returned book id is supposed to be sorted by added time, asc
        val allBookIds = History.getAllBookIds()
        for (bookId in allBookIds.reversed()) {
            if (arrangedBookId.contains(bookId)) {
                break
            }
            res.add(bookId)
        }
        return res
    }

    private fun drawId (context: Context, onlyDownloaded: Boolean): String {
        // WARNING: this function supposed some book is downloaded
        if (!onlyDownloaded) {
            return randomPool.random()
        }
        while (true) {
            val id = randomPool.random()
            if (Util.isBookDownloaded(context, id)) {
                return id
            }
            randomPool.remove(id)
            randomPool.add(bookIdSequence[position])
            movePosition()
        }
    }
}