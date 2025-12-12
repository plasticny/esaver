package com.example.viewer.struct

data class SearchMark (
    val name: String,
    val categories: List<Category>,
    val keyword: String,
    val tags: Map<String, List<String>>,
    val uploader: String,
    val doExclude: Boolean
)
