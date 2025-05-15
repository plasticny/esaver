package com.example.viewer.activity.main

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.DragEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.activity.SearchActivity
import com.example.viewer.databinding.MainSearchFragmentBinding
import com.example.viewer.databinding.SearchMarkBinding
import com.example.viewer.database.SearchDatabase
import com.example.viewer.databinding.DialogSearchMarkTagBinding
import com.example.viewer.dialog.ConfirmDialog
import com.example.viewer.dialog.FilterOutDialog
import com.example.viewer.dialog.SearchMarkDialog
import com.example.viewer.struct.SearchMark

data class SearchMarkEntry (
    val id: Int,
    val searchMark: SearchMark,
    val binding: SearchMarkBinding
)

class SearchMarkFragment: Fragment() {
    companion object {
        private val TAGS = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.keys) }.toList()
        private val TAGS_DISPLAY = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.values) }.toList()
    }

    private lateinit var parent: ViewGroup
    private lateinit var binding: MainSearchFragmentBinding
    private lateinit var searchDataset: SearchDatabase

    private var focusedSearchMark: SearchMarkEntry? = null
    private var searchMarkListLastUpdate = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        parent = container!!
        searchDataset = SearchDatabase.getInstance(parent.context)
        binding = MainSearchFragmentBinding.inflate(layoutInflater, parent, false)

        searchMarkListLastUpdate = searchDataset.getSearchMarkListUpdateTime()

        binding.searchEditText.apply {
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    if (event?.action == null || event.action == KeyEvent.ACTION_UP) {
                        SearchActivity.startTmpSearch(
                            context, keyword = text.toString().trim()
                        )
                    }
                }
                true
            }
        }

        binding.advanceSearchButton.apply {
            setOnClickListener {
                val searchMark = SearchMark (
                    name = "",
                    categories = listOf(),
                    keyword = binding.searchEditText.text.toString().trim(),
                    tags = mapOf(),
                    uploader = ""
                )
                SearchMarkDialog(context, layoutInflater).apply {
                    title = "進階搜尋"
                    showNameField = false
                    showSearchButton = true
                    searchCb = { retSearchMark ->
                        SearchActivity.startTmpSearch(
                            context,
                            retSearchMark.categories,
                            retSearchMark.keyword,
                            retSearchMark.tags,
                            retSearchMark.uploader
                        )
                    }
                }.show(searchMark)
            }
        }

        binding.addButton.setOnClickListener {
            SearchMarkDialog(parent.context, layoutInflater).apply {
                title = "新增搜尋標記"
                showConfirmButton = true
                confirmCb = { retSearchMark ->
                    searchDataset.addSearchMark(retSearchMark)
                    refreshSearchMarkWrapper()
                }
            }.show()
        }

        binding.toolBarFilterOutButton.setOnClickListener {
            FilterOutDialog(parent.context, layoutInflater).show()
        }

        binding.toolBarCloseButton.setOnClickListener { deFocusSearchMark() }

        binding.toolBarEditButton.setOnClickListener {
            focusedSearchMark!!.let { entry ->
                SearchMarkDialog(parent.context, layoutInflater).apply {
                    title = "編輯搜尋標記"
                    showConfirmButton = true
                    confirmCb = { retSearchMark ->
                        searchDataset.modifySearchMark(entry.id, retSearchMark)
                        deFocusSearchMark(doModifyBindingStyle = false)
                        refreshSearchMarkWrapper()
                    }
                }.show(entry.searchMark)
            }
        }

        binding.toolBarDeleteButton.setOnClickListener {
            focusedSearchMark!!.let {
                ConfirmDialog(parent.context, inflater).show(
                    "刪除${it.searchMark.name}嗎？",
                    positiveCallback = {
                        searchDataset.removeSearchMark(it.id)
                        deFocusSearchMark(doModifyBindingStyle = false)
                        refreshSearchMarkWrapper()
                    }
                )
            }
        }

        refreshSearchMarkWrapper()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.searchEditText.text.clear()

        searchDataset.getSearchMarkListUpdateTime().let {
            if (it != searchMarkListLastUpdate) {
                refreshSearchMarkWrapper()
                searchMarkListLastUpdate = it
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun refreshSearchMarkWrapper () {
        binding.searchMarkWrapper.removeAllViews()

        for ((id, searchMark) in getSearchMarkEntries()) {
            val searchMarkBinding = SearchMarkBinding.inflate(layoutInflater, binding.searchMarkWrapper, true)

            searchMarkBinding.name.text = searchMark.name

            searchMarkBinding.root.apply {
                setOnClickListener {
                    if (focusedSearchMark == null) {
                        if (!Util.isInternetAvailable(context)) {
                            Toast.makeText(context, "沒有網絡", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        context.startActivity(
                            Intent(context, SearchActivity::class.java).apply {
                                putExtra("searchMarkId", id)
                            }
                        )
                    } else if (focusedSearchMark!!.id != id) {
                        changeFocusSearchMark(id, searchMark, searchMarkBinding)
                    } else {
                        deFocusSearchMark()
                    }
                }

                setOnLongClickListener { v ->
                    if (focusedSearchMark == null) {
                        focusSearchMark(id, searchMark, searchMarkBinding)
                    } else if (focusedSearchMark!!.id != id) {
                        changeFocusSearchMark(id, searchMark, searchMarkBinding)
                    } else {
                        val dragData = ClipData(
                            "drag search mark",
                            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                            ClipData.Item(id.toString())
                        )
                        v.startDragAndDrop(
                            dragData,
                            View.DragShadowBuilder(v),
                            null,
                            0
                        )
                    }
                    true
                }

                setOnDragListener { _, event ->
                    when (event.action) {
                        DragEvent.ACTION_DROP -> {
                            val dragId = event.clipData.getItemAt(0).text.toString().toInt()
                            if (dragId != id) {
                                searchDataset.moveSearchMarkPosition(dragId, id)
                                deFocusSearchMark(doModifyBindingStyle = false)
                                refreshSearchMarkWrapper()
                            }
                        }
                    }
                    true
                }
            }
        }
    }

    private fun focusSearchMark (id: Int, searchMark: SearchMark, searchMarkBinding: SearchMarkBinding) {
        binding.notFocusedToolBarWrapper.visibility = View.GONE
        binding.focusedToolBarWrapper.visibility = View.VISIBLE
        searchMarkBinding.name.setTextColor(parent.context.getColor(R.color.black))
        searchMarkBinding.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.grey))
        focusedSearchMark = SearchMarkEntry(id, searchMark, searchMarkBinding)
    }

    private fun deFocusSearchMark (doModifyBindingStyle: Boolean = true) {
        binding.notFocusedToolBarWrapper.visibility = View.VISIBLE
        binding.focusedToolBarWrapper.visibility = View.GONE
        if (doModifyBindingStyle) {
            focusedSearchMark!!.binding.let {
                it.name.setTextColor(parent.context.getColor(R.color.white))
                it.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.darkgrey))
            }
        }
        focusedSearchMark = null
    }

    private fun changeFocusSearchMark (id: Int, searchMark: SearchMark, searchMarkBinding: SearchMarkBinding) {
        focusedSearchMark!!.binding.let {
            it.name.setTextColor(parent.context.getColor(R.color.white))
            it.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.darkgrey))
        }
        searchMarkBinding.name.setTextColor(parent.context.getColor(R.color.black))
        searchMarkBinding.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.grey))
        focusedSearchMark = SearchMarkEntry(id, searchMark, searchMarkBinding)
    }

    /**
     * @return list of pair, first of pair is id, second is search mark instance
     */
    private fun getSearchMarkEntries (): List<Pair<Int, SearchMark>> =
       searchDataset.getAllSearchMarkIds().map { id ->
           Pair(id, searchDataset.getSearchMark(id))
       }
}