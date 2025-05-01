package com.example.viewer.fetcher

import android.content.Context
import android.widget.Toast
import com.example.viewer.Util
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.URL

/**
 * Note: this fetcher always fetch for a stored book
 */
class HiPictureFetcher (context: Context, bookId: String): BasePictureFetcher(context, bookId) {
    private data class PictureInfo (
        val hash: String,
        val haswebp: Int,
        val hasavif: Int
    )

    private data class GalleryInfo (
        val files: List<PictureInfo>
    )

    private inner class GG {
        val b: String
        val o: List<Int>
        val cases: Set<Int>

        init {
            var content: String
            runBlocking {
                withContext(Dispatchers.IO) {
                    content = URL("https://ltn.hitomi.la/gg.js").readText()
                }
            }

            val bv = Regex("b: '(.*)'").find(content)!!.value
            b = bv.substring(4, bv.lastIndex)

            o = Regex("o = (\\d);").findAll(content).map {
                val s = it.value
                s.substring(4, s.lastIndex).toInt()
            }.toList()

            cases = Regex("case (\\d+):").findAll(content).map {
                val s = it.value
                s.substring(5, s.lastIndex).toInt()
            }.toSet()
        }

        fun h (fileHash: String): String {
            val s = fileHash.last() + fileHash.substring(fileHash.length - 3, fileHash.length - 1)
            return s.toInt(16).toString()
        }

        fun m (g: Int): Int {
            return if (cases.contains(g)) o[1] else o[0]
        }
    }

    // if all pages are downloaded, these will always be null
    private var pictureInfos: List<PictureInfo>? = null
    private var gg: GG? = null
    private var base: String? = null

    init {
        if (pageNum > bookFolder.listFiles()!!.size) {
            println("[HiPictureFetcher] get data for constructing url")

            if (!Util.isInternetAvailable(context)) {
                throw Exception("[HiPictureFetcher.updateToken] internet not available")
            }
            pictureInfos = getPictureInfos()
            gg = GG()
            base = getBase()
        }
    }

    override suspend fun savePicture(
        page: Int,
        progressListener: ((contentLength: Long, downloadLength: Long) -> Unit)?
    ): File? {
        assertPageInRange(page)

        if (!Util.isInternetAvailable(context)) {
            Toast.makeText(context, "沒有網絡，無法下載", Toast.LENGTH_SHORT).show()
            return null
        }

        return fetchPictureUrl(page)?.let { url ->
            val headers = mapOf(
                "accept" to "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                "accept-language" to "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7",
                "sec-ch-ua" to "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Opera GX\";v=\"114\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "sec-fetch-dest" to "image",
                "sec-fetch-mode" to "no-cors",
                "sec-fetch-site" to "same-site",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 OPR/114.0.0.0 (Edition GX-CN)",
                "referer" to "https://hitomi.la/reader/${bookId!!}.html"
            )
            downloadPicture(page, url, headers, progressListener = progressListener)
        }
    }

    override suspend fun fetchPictureUrl (page: Int): String? {
        val pictureInfo = pictureInfos!![page]
        if (pictureInfo.haswebp == 0) {
            throw Exception("no webp")
        }
        val thirdDomain = findThirdDomain(pictureInfo.hash)
        return "https://${thirdDomain}.hitomi.la/webp/${gg!!.b}${gg!!.h(pictureInfo.hash)}/${pictureInfo.hash}.webp"
    }

    private fun getPictureInfos (): List<PictureInfo> {
        if (!Util.isInternetAvailable(context)) {
            throw Exception("[HiPictureFetcher.updateToken] internet not available")
        }

        var bookIdJs: String
        runBlocking {
            withContext(Dispatchers.IO) {
                bookIdJs = URL("https://ltn.hitomi.la/galleries/${bookId!!}.js").readText().substring(18)
            }
        }
        val galleryInfo = Gson().fromJson(bookIdJs, GalleryInfo::class.java)!!
        return galleryInfo.files
    }

    private fun getBase (): String {
        var content: String
        runBlocking {
            withContext(Dispatchers.IO) {
                content = URL("https://ltn.hitomi.la/reader.js").readText()
            }
        }

        val params = Regex("url_from_url_from_hash\\((.*)\\);").findAll(content).last().value
        val baseV = Regex("'(.*)'").find(params.split(",").last())!!.value

        return baseV.substring(1, baseV.lastIndex)
    }

    private fun findThirdDomain (fileHash: String): String {
        val s = fileHash.last() + fileHash.substring(fileHash.length - 3, fileHash.length - 1)
        val g = s.toInt(16)
        return (97 + gg!!.m(g)).toChar() + base!!
    }
}
