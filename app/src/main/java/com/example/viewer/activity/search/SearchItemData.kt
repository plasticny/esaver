package com.example.viewer.activity.search

import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemType

data class SearchItemData (
    val url: String,
    val coverUrl: String,
    val cat: Category,
    val title: String,
    val pageNum: Int,
    val tags: Map<String, List<String>>,
    val rating: Float?,
    val type: ItemType,
    val bookId: String? = null,
    val videoData: VideoData? = null
) {
    data class VideoData (
        val videoUrl: String,
        val id: String,
        val uploader: String
    )
}