package com.example.viewer.activity.main

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.viewer.R
import com.example.viewer.activity.SearchActivity
import com.example.viewer.databinding.MainSearchFragmentBinding
import com.example.viewer.databinding.SearchMarkBinding
import com.example.viewer.databinding.SearchMarkDialogBinding
import com.example.viewer.databinding.SearchMarkDialogTagBinding
import com.example.viewer.dataset.SearchDataset
import com.example.viewer.dataset.SearchDataset.Companion.SearchMark
import com.example.viewer.dataset.SearchDataset.Companion.SearchMark.Companion.Category
import com.example.viewer.dialog.ConfirmDialog

data class SearchMarkEntry (
    val id: Int,
    val searchMark: SearchMark,
    val binding: SearchMarkBinding
)

class SearchFragment: Fragment() {
    companion object {
        private val TAGS = listOf(
            "-", "female", "parody", "artist", "group", "other"
        )
        private val TAGS_DISPLAY = listOf(
            "-", "女性", "原作", "作者", "組別", "其他"
        )
    }

    private lateinit var parent: ViewGroup
    private lateinit var binding: MainSearchFragmentBinding
    private lateinit var searchDataset: SearchDataset

    private var focusedSearchMark: SearchMarkEntry? = null

    /**
     * Given a value, add it if it not exist, else remove it
     *
     * @return boolean represent that the value is in the set after the operation
     */
    private fun <T> MutableSet<T>.toggle(value: T): Boolean {
        return if (this.contains(value)) {
            this.remove(value)
            false
        } else {
            this.add(value)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        parent = container!!
        searchDataset = SearchDataset.getInstance(parent.context)
        binding = MainSearchFragmentBinding.inflate(layoutInflater, parent, false)

        binding.addButton.setOnClickListener {
            openSearchMarkDialog { retSearchMark ->
                searchDataset.addSearchMark(retSearchMark)
                refreshSearchMarkWrapper()
            }
        }

        binding.toolBarCloseButton.setOnClickListener { deFocusSearchMark() }

        binding.toolBarEditButton.setOnClickListener {
            focusedSearchMark!!.let { entry ->
                openSearchMarkDialog(entry.searchMark) { retSearchMark ->
                    searchDataset.modifySearchMark(entry.id, retSearchMark)
                    deFocusSearchMark(doModifyBindingStyle = false)
                    refreshSearchMarkWrapper()
                }
            }
        }

        binding.toolBarDeleteButton.setOnClickListener {
            focusedSearchMark!!.let {
                ConfirmDialog(parent.context, inflater).show(
                    "要刪除${it.searchMark.name}嗎？",
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

    @SuppressLint("ClickableViewAccessibility")
    private fun refreshSearchMarkWrapper () {
        binding.searchMarkWrapper.removeAllViews()

        for ((id, searchMark) in readSearchMarkEntries()) {
            val searchMarkBinding = SearchMarkBinding.inflate(layoutInflater, binding.searchMarkWrapper, true)

            searchMarkBinding.name.text = searchMark.name

            searchMarkBinding.root.apply {
                setOnClickListener {
                    if (focusedSearchMark == null) {
                        val intent = Intent(context, SearchActivity::class.java)
                        intent.putExtra("searchMarkId", id)
                        context.startActivity(intent)
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
        binding.toolBarWrapper.visibility = View.VISIBLE
        searchMarkBinding.name.setTextColor(parent.context.getColor(R.color.black))
        searchMarkBinding.root.backgroundTintList = ColorStateList.valueOf(parent.context.getColor(R.color.grey))
        focusedSearchMark = SearchMarkEntry(id, searchMark, searchMarkBinding)
    }

    private fun deFocusSearchMark (doModifyBindingStyle: Boolean = true) {
        binding.toolBarWrapper.visibility = View.GONE
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

    private fun openSearchMarkDialog (
        searchMark: SearchMark? = null,
        saveCb: ((SearchMark) -> Unit)? = null
    ) {
        val dialogBinding = SearchMarkDialogBinding.inflate(layoutInflater, parent, false)
        val dialog = AlertDialog.Builder(parent.context)
            .setView(dialogBinding.root)
            .create()

        val selectedCats = searchMark?.categories?.toMutableSet() ?: Category.entries.toMutableSet()
        val tagBindings = mutableListOf<SearchMarkDialogTagBinding>()

        dialogBinding.nameEditText.setText(searchMark?.name ?: "")

        // category buttons
        dialogBinding.catDoujinshi.apply {
            val selectedColor = context.getColor(R.color.doujinshi_red)
            val deselectedColor = context.getColor(R.color.grey)
            setBackgroundColor(
                if (selectedCats.contains(Category.Doujinshi)) selectedColor else deselectedColor
            )
            setOnClickListener {
                setBackgroundColor(
                    if (selectedCats.toggle(Category.Doujinshi)) selectedColor else deselectedColor
                )
            }
        }
        dialogBinding.catManga.apply {
            val selectedColor = context.getColor(R.color.manga_orange)
            val deselectedColor = context.getColor(R.color.grey)
            setBackgroundColor(
                if (selectedCats.contains(Category.Manga)) selectedColor else deselectedColor
            )
            setOnClickListener {
                setBackgroundColor(
                    if (selectedCats.toggle(Category.Manga)) selectedColor else deselectedColor
                )
            }
        }
        dialogBinding.catArtistCg.apply {
            val selectedColor = context.getColor(R.color.artistCG_yellow)
            val deselectedColor = context.getColor(R.color.grey)

            setBackgroundColor(
                if (selectedCats.contains(Category.ArtistCG)) selectedColor else deselectedColor
            )
            setOnClickListener {
                setBackgroundColor(
                    if (selectedCats.toggle(Category.ArtistCG)) selectedColor else deselectedColor
                )
            }
        }

        // tags
        searchMark?.tags?.forEach { tag ->
            val tagBinding = createSearchMarkDialogTag(dialogBinding.tagWrapper, tag)
            tagBindings.add(tagBinding)
            dialogBinding.tagWrapper.addView(tagBinding.root)
        }
        dialogBinding.addTagButton.apply {
            setOnClickListener {
                val tagBinding = createSearchMarkDialogTag(dialogBinding.tagWrapper)
                tagBindings.add(tagBinding)
                dialogBinding.tagWrapper.addView(tagBinding.root, 0)
            }
        }

        // save button
        dialogBinding.saveButton.apply {
            setOnClickListener {
                if (dialogBinding.nameEditText.text.isEmpty()) {
                    Toast.makeText(context, "名字不能為空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (saveCb != null) {
                    val retSearchMark = SearchMark(
                        name = dialogBinding.nameEditText.text.toString(),
                        categories = selectedCats.toList(),
                        tags = tagBindings.mapNotNull {
                            if (it.spinner.selectedItemPosition == 0) {
                                return@mapNotNull null
                            }
                            TAGS[it.spinner.selectedItemPosition] to it.editText.text.toString()
                        }
                    )
                    saveCb(retSearchMark)
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun createSearchMarkDialogTag (parent: ViewGroup, tag: Pair<String, String>? = null) =
        SearchMarkDialogTagBinding.inflate(layoutInflater, parent, false).apply {
            spinner.apply {
                adapter = ArrayAdapter(context, R.layout.white_spinner_item, TAGS_DISPLAY).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                // make the small triangle white
                background.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_ATOP)

                tag?.first?.let { setSelection(TAGS.indexOf(it)) }
            }
            tag?.second?.let { editText.setText(it) }
        }

    /**
     * @return list of pair, first of pair is id, second is search mark instance
     */
    private fun readSearchMarkEntries (): List<Pair<Int, SearchMark>> =
       searchDataset.getAllSearchMarkIds().map { id ->
           Pair(id, searchDataset.getSearchMark(id))
       }
}