package com.example.viewer.dialog.SearchMarkDialog

import com.example.viewer.struct.Category

data class DialogData (
    val name: String,
    val sourceOrdinal: Int,
    val categories: Set<Category>,
    val keyword: String,
    val tags: Map<String, List<String>>,
    val uploader: String,
    val doExclude: Boolean
)