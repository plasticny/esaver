package com.example.viewer

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File

class OkHttpHelper {
    companion object {
        private val _client = OkHttpClient()
    }

    private val myClient: OkHttpClient

    constructor () {
        myClient = _client
    }

    constructor (
        progressListener: (contentLength: Long, downloadLength: Long) -> Unit
    ) {
        myClient = _client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request()).run {
                    newBuilder().body(
                        ProgressResponseBody(body!!, progressListener)
                    ).build()
                }
            }.build()
    }

    suspend fun curl (
        url: String,
        dst: File,
        headers: Map<String, String> = mapOf()
    ): Boolean {
        val request = Request.Builder().url(url).apply {
            for (header in headers) {
                addHeader(header.key, header.value)
            }
        }.build()

        return withContext(Dispatchers.IO) {
            myClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext false
                } else {
                    dst.outputStream().use {
                        response.body!!.byteStream().copyTo(it)
                    }
                    return@withContext true
                }
            }
        }
    }

    suspend fun downloadImage (
        url: String,
        dst: File,
        headers: Map<String, String> = mapOf(),
    ): Boolean = curl(url, dst, headers) && BitmapFactory.decodeFile(dst.absolutePath) != null

    private class ProgressResponseBody (
        private val responseBody: ResponseBody,
        private val progressListener: (contentLength: Long, downloadLength: Long) -> Unit
    ): ResponseBody() {
        private var bufferedSource =
            object: ForwardingSource(responseBody.source()) {
                private var totalBytesRead = 0L
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also {
                        totalBytesRead += if (it == -1L) 0 else it
                        progressListener(contentLength(), totalBytesRead)
                    }
            }.buffer()

        override fun contentLength(): Long = responseBody.contentLength()

        override fun contentType(): MediaType? = responseBody.contentType()

        override fun source(): BufferedSource = bufferedSource
    }
}