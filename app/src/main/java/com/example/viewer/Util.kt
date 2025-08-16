package com.example.viewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.TypedValue
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
            "parody" to "原作",
            "temp" to "臨時"
        )
        private val CATEGORY_ENTRIES = Category.entries

        fun dp2px (context: Context, dp: Float): Int {
            val displayMetrics = context.resources.displayMetrics
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics).toInt()
        }

        fun sp2px (context: Context, sp: Float): Int {
            val displayMetrics = context.resources.displayMetrics
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics).toInt()
        }

        fun isInternetAvailable (context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val cap = connectivityManager.getNetworkCapabilities(network) ?: return false
            return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

        /**
         * @return boolean represent all pages of the book is downloaded
         */
        fun isBookDownloaded (context: Context, bookId: String): Boolean {
            val folder = File(context.getExternalFilesDir(null), bookId)
            return BookRepository(context).getBookPageNum(bookId) <= folder.listFiles()!!.size
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
            Category.Doujinshi.name -> Category.Doujinshi
            Category.Manga.name -> Category.Manga
            Category.ArtistCG.name -> Category.ArtistCG
            "Artist CG" -> Category.ArtistCG
            Category.NonH.name -> Category.NonH
            "Non-H" -> Category.NonH
            else -> throw Exception("unexpected string $name")
        }

        fun getUrlSource (url: String): BookSource? = when {
            Regex("(http(s?)://)?e-hentai.org/g/(\\d+)/([a-zA-Z0-9]+)(/?)$").matches(url) -> BookSource.E
            Regex("(http(s?)://)?hitomi.la/reader/(\\d+).html(#(\\d+))?$").matches(url) -> BookSource.Hi
            else -> null
        }

        inline fun<reified T> readListFromJson (json: String): List<T> =
            ObjectMapper().registerKotlinModule()
                .readerForListOf(T::class.java)
                .readValue(json)

        inline fun<reified T> readArrayFromJson (json: String): Array<T> =
            readListFromJson<T>(json).toTypedArray()

        fun<T> readMapFromJson (json: String): Map<String, T> =
            ObjectMapper().registerKotlinModule()
                .readerFor(Map::class.java)
                .readValues<Map<String, T>>(json)
                .let { if (it.hasNextValue()) it.next() else mapOf() }
    }
}