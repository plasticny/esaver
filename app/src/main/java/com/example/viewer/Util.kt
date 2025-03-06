package com.example.viewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.TypedValue
import com.example.viewer.dataset.BookDataset
import java.io.File
import java.io.FileInputStream

class Util {
    companion object {
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

        fun isBookDownloaded (context: Context, bookId: String): Boolean {
            val folder = File(context.getExternalFilesDir(null), bookId)
            return BookDataset.getBookPageNum(bookId) <= folder.listFiles()!!.size
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
    }
}