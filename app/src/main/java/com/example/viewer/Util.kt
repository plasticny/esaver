package com.example.viewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.TypedValue
import com.example.viewer.database.BookDatabase
import com.example.viewer.database.SearchDatabase
import com.example.viewer.database.SearchDatabase.Companion.Category.ArtistCG
import com.example.viewer.database.SearchDatabase.Companion.Category.Doujinshi
import com.example.viewer.database.SearchDatabase.Companion.Category.Manga
import com.example.viewer.database.SearchDatabase.Companion.Category.NonH
import java.io.File
import java.io.FileInputStream

class Util {
    companion object {
        val TAG_TRANSLATION_MAP = mapOf(
            "artist" to "作者",
            "character" to "角色",
            "female" to "女性",
            "group" to "組別",
            "language" to "語言",
            "male" to "男性",
            "mixed" to "混合",
            "other" to "其他",
            "parody" to "原作"
        )
        private val CATEGORY_ENTRIES = SearchDatabase.Companion.Category.entries

        fun dp2px (context: Context, dp: Float): Int {
            val displayMetrics = context.resources.displayMetrics
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics).toInt()
        }

        fun isInternetAvailable (context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val cap = connectivityManager.getNetworkCapabilities(network) ?: return false
            return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        /**
         * @return boolean represent all pages of the book is downloaded
         */
        fun isBookDownloaded (context: Context, bookId: String): Boolean {
            val folder = File(context.getExternalFilesDir(null), bookId)
            return BookDatabase.getInstance(context).getBookPageNum(bookId) <= folder.listFiles()!!.size
        }

        fun isGifFile (file: File): Boolean = FileInputStream(file).use { fis ->
            val header = ByteArray(6)
            if (fis.read(header) != 6) {
                return false
            }
            (header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            (header[3] == '8'.code.toByte() || header[3] == '7'.code.toByte()))
        }

        fun categoryFromOrdinal (ordinal: Int) = CATEGORY_ENTRIES[ordinal]

        fun categoryFromName (name: String) = when (name) {
            Doujinshi.name -> Doujinshi
            Manga.name -> Manga
            ArtistCG.name -> ArtistCG
            "Artist CG" -> ArtistCG
            NonH.name -> NonH
            "Non-H" -> NonH
            else -> throw Exception("unexpected string $name")
        }
    }
}