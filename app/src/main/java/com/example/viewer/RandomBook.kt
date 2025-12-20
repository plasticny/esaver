package com.example.viewer

import android.content.Context
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.struct.Book
import kotlinx.coroutines.runBlocking
import kotlin.math.floor
import kotlin.math.min

class RandomBook private constructor(context: Context) {
    companion object {
        private const val POOL_SIZE = 5

        @Volatile
        private var instance: RandomBook? = null

        private fun getInstance (context: Context): RandomBook = synchronized(this) {
            instance ?: RandomBook(context).also { instance = it }
        }

        fun next (context: Context) = getInstance(context).next()

        fun getPoolStatus (context: Context): Pair<Boolean, Boolean> =
            getInstance(context).let { Pair(it.pullH, it.pullNH) }

        fun changePool (context: Context, pullH: Boolean, pullNH: Boolean) {
            getInstance(context).apply {
                this.pullH = pullH
                this.pullNH = pullNH
            }
        }
    }

    private val bookDataset = BookRepository(context)

    private val bookIdSequenceH: MutableList<Book.Companion.SequenceItem> by lazy {
        runBlocking { bookDataset.getBookIdSeqH().toMutableList() }
    }
    private val bookIdSequenceNH: MutableList<Book.Companion.SequenceItem> by lazy {
        runBlocking { bookDataset.getBookIdSeqNH().toMutableList() }
    }

    private var pullH = true
    private var pullNH = false

    private fun next (): String {
        if (!pullH && !pullNH) {
            throw IllegalStateException()
        }

        val seqSize = (if (pullH) bookIdSequenceH.size else 0) + (if (pullNH) bookIdSequenceNH.size else 0)
        val seqToPick = floor(Math.random() * min(POOL_SIZE, seqSize)).toInt()

        if (!pullH) {
            return pullIdFromSeq(bookIdSequenceNH, seqToPick)
        }
        if (!pullNH) {
            return pullIdFromSeq(bookIdSequenceH, seqToPick)
        }
        return pullIdFromAllSeq(seqToPick)
    }

    private fun pullIdFromSeq (seq: MutableList<Book.Companion.SequenceItem>, toPick: Int): String {
        val ret = seq.removeAt(toPick)
        seq.add(Book.Companion.SequenceItem(
            id = ret.id,
            lastViewTime = seq.last().lastViewTime + 1
        ))
        return ret.id
    }

    private fun pullIdFromAllSeq (toPick: Int): String {
        var idxH = 0
        var idxNH = 0
        var j = 0
        while (j < toPick && idxH <= bookIdSequenceH.lastIndex && idxNH <= bookIdSequenceNH.lastIndex) {
            if (bookIdSequenceH[idxH].lastViewTime < bookIdSequenceNH[idxNH].lastViewTime) {
                idxH++
            } else {
                idxNH++
            }
            j++
        }

        if (idxH > bookIdSequenceH.lastIndex || bookIdSequenceH[idxH].lastViewTime < bookIdSequenceNH[idxNH].lastViewTime) {
            return pullIdFromSeq(bookIdSequenceNH, idxNH)
        }
        return pullIdFromSeq(bookIdSequenceH, idxH)
    }
}