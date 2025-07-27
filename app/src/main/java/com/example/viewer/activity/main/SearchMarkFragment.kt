package com.example.viewer.activity.main

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Point
import android.os.Bundle
import android.view.DragEvent
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.activity.SearchActivity
import com.example.viewer.data.repository.SearchRepository
import com.example.viewer.data.struct.SearchMark
import com.example.viewer.databinding.ComponentListItemBinding
import com.example.viewer.databinding.MainSearchFragmentBinding
import com.example.viewer.dialog.ConfirmDialog
import com.example.viewer.dialog.FilterOutDialog
import com.example.viewer.dialog.SearchMarkDialog

class SearchMarkFragment: Fragment() {
    private lateinit var parent: ViewGroup
    private lateinit var binding: MainSearchFragmentBinding
    private lateinit var searchRepo: SearchRepository
    private lateinit var gestureDetector: GestureDetector

    private var focusedSearchMark: SearchMarkEntry? = null
    private var searchMarkListLastUpdate = 0L
    private var lastLongPressX = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        parent = container!!
        searchRepo = SearchRepository(parent.context)
        binding = MainSearchFragmentBinding.inflate(layoutInflater, parent, false)

        gestureDetector = GestureDetector(
            requireContext(),
            object: GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    super.onLongPress(e)
                    lastLongPressX = e.x
                }
            }
        )

        searchMarkListLastUpdate = searchRepo.getSearchMarkListUpdateTime()

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
                SearchMarkDialog(context, layoutInflater).apply {
                    title = "進階搜尋"
                    showNameField = false
                    showSearchButton = true
                    searchCb = { data ->
                        SearchActivity.startTmpSearch(
                            context,
                            data.categories.toList(),
                            data.keyword,
                            data.tags,
                            data.uploader,
                            data.doExclude
                        )
                    }
                }.show(
                    keyword = binding.searchEditText.text.toString().trim()
                )
            }
        }

        binding.addButton.setOnClickListener {
            SearchMarkDialog(parent.context, layoutInflater).apply {
                title = "新增搜尋標記"
                showConfirmButton = true
                confirmCb = { data ->
                    searchRepo.addSearchMark(
                        name = data.name,
                        categories = data.categories.toList(),
                        keyword = data.keyword,
                        tags = data.tags,
                        uploader = data.uploader,
                        doExclude = data.doExclude
                    )
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
                    confirmCb = { data ->
                        searchRepo.modifySearchMark(
                            id = entry.id,
                            name = data.name,
                            categories = data.categories.toList(),
                            keyword = data.keyword,
                            tags = data.tags,
                            uploader = data.uploader,
                            doExclude = data.doExclude
                        )
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
                        searchRepo.removeSearchMark(it.id)
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

        searchRepo.getSearchMarkListUpdateTime().let {
            if (it != searchMarkListLastUpdate) {
                refreshSearchMarkWrapper()
                searchMarkListLastUpdate = it
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun refreshSearchMarkWrapper () {
        binding.searchMarkWrapper.removeAllViews()

        for (searchMark in searchRepo.getAllSearchMarkInListOrder()) {
            val searchMarkBinding = ComponentListItemBinding.inflate(layoutInflater, binding.searchMarkWrapper, true)

            searchMarkBinding.name.text = searchMark.name

            searchMarkBinding.root.apply {
                setOnTouchListener { _, motionEvent ->
                    gestureDetector.onTouchEvent(motionEvent)
                    false
                }

                setOnClickListener {
                    if (focusedSearchMark == null) {
                        if (!Util.isInternetAvailable(context)) {
                            Toast.makeText(context, "沒有網絡", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        context.startActivity(
                            Intent(context, SearchActivity::class.java).apply {
                                putExtra("searchMarkId", searchMark.id)
                            }
                        )
                    } else if (focusedSearchMark!!.id != searchMark.id) {
                        changeFocusSearchMark(searchMark.id, searchMark, searchMarkBinding)
                    } else {
                        deFocusSearchMark()
                    }
                }

                setOnLongClickListener { v ->
                    if (focusedSearchMark == null) {
                        focusSearchMark(searchMark.id, searchMark, searchMarkBinding)
                    } else if (focusedSearchMark!!.id != searchMark.id) {
                        changeFocusSearchMark(searchMark.id, searchMark, searchMarkBinding)
                    } else {
                        val dragData = ClipData(
                            "drag search mark",
                            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                            ClipData.Item(searchMark.id.toString())
                        )
                        v.startDragAndDrop(
                            dragData,
                            DragShadowBuilder(v),
                            null,
                            0
                        )
                    }
                    true
                }

                setOnDragListener { v, event ->
                    when (event.action) {
                        DragEvent.ACTION_DROP -> {
                            val dragId = event.clipData.getItemAt(0).text.toString().toLong()
                            if (dragId != searchMark.id) {
                                if (event.y <= v.height / 2.0) {
                                    searchRepo.moveSearchMarkBefore(dragId, searchMark.id)
                                } else {
                                    searchRepo.moveSearchMarkAfter(dragId, searchMark.id)
                                }
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

    private fun focusSearchMark (id: Long, searchMark: SearchMark, searchMarkBinding: ComponentListItemBinding) {
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
                it.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.dark_grey))
            }
        }
        focusedSearchMark = null
    }

    private fun changeFocusSearchMark (id: Long, searchMark: SearchMark, searchMarkBinding: ComponentListItemBinding) {
        focusedSearchMark!!.binding.let {
            it.name.setTextColor(parent.context.getColor(R.color.white))
            it.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.dark_grey))
        }
        searchMarkBinding.name.setTextColor(parent.context.getColor(R.color.black))
        searchMarkBinding.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.grey))
        focusedSearchMark = SearchMarkEntry(id, searchMark, searchMarkBinding)
    }

    data class SearchMarkEntry (
        val id: Long,
        val searchMark: SearchMark,
        val binding: ComponentListItemBinding
    )

    private inner class DragShadowBuilder (v: View) : View.DragShadowBuilder(v) {
        private val x = v.x
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(view.width, view.height)
            outShadowTouchPoint.set((lastLongPressX - x).toInt(), outShadowSize.y / 2)
        }
    }
}