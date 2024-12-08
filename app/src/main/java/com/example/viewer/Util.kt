package com.example.viewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.TypedValue

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
    }
}