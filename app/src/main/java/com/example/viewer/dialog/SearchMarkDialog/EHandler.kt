package com.example.viewer.dialog.SearchMarkDialog

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.children
import com.example.viewer.R
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.databinding.DialogSearchMarkTagBinding
import com.example.viewer.struct.BookSource
import com.example.viewer.struct.Category

class EHandler (
    context: Context,
    layoutInflater: LayoutInflater,
    owner: SearchMarkDialog,
    ownerRootBinding: DialogSearchMarkBinding,
    private val name: String = "",
    categories: List<Category> = listOf(),
    private val keyword: String = "",
    private val tags: Map<String, List<String>> = mapOf(),
    private val uploader: String = "",
    private val doExclude: Boolean = true
): BaseHandler(context, layoutInflater, owner, ownerRootBinding) {
    override val source = BookSource.E

    private var selectedCats: MutableSet<Category> = categories.toMutableSet()

    override fun setupUi () {
        owner.apply {
            showKeywordField = true
            showTagsField = true
            showUploaderField = true
            showDoExcludeField = true
        }

        // name field
        ownerRootBinding.nameEditText.setText(name)

        // book source
        ownerRootBinding.bookSourceEditText.setText(source.keyString)

        // keyword
        ownerRootBinding.keywordEditText.setText(keyword)

        // uploader
        ownerRootBinding.uploaderEditText.setText(uploader)

        // tags
        tags.forEach { entry ->
            val cat = entry.key
            for (value in entry.value) {
                val tagBinding = createSearchMarkDialogTag(cat, value)
                ownerRootBinding.tagWrapper.addView(tagBinding.root)
            }
        }

        // category button
        ownerRootBinding.categoryWrapper.removeAllViews()
        listOf(
            Category.Doujinshi, Category.Manga, Category.ArtistCG, Category.NonH
        ).forEachIndexed { index, cat ->
            ownerRootBinding.categoryWrapper.addView(
                createCategoryButton(cat, index % 2).apply {
                    setOnClickListener {
                        setBackgroundColor(context.getColor(
                            if (selectedCats.toggle(cat)) cat.color else R.color.grey
                        ))
                    }
                    setBackgroundColor(context.getColor(
                        if (selectedCats.contains(cat)) cat.color else R.color.grey
                    ))
                }
            )
        }

        // do apply exclude tag
        ownerRootBinding.doExcludeSwitch.isChecked = doExclude
    }

    override fun buildDialogData(): DialogData = DialogData (
        name = if (owner.showNameField) ownerRootBinding.nameEditText.text.toString() else "",
        sourceOrdinal = source.ordinal,
        categories = selectedCats,
        keyword = ownerRootBinding.keywordEditText.text.toString(),
        tags = (ownerRootBinding.tagWrapper.children).mapNotNull {
            val binding = DialogSearchMarkTagBinding.bind(it)
            if (binding.spinner.selectedIndex == 0) {
                return@mapNotNull null
            }
            TAGS[binding.spinner.selectedIndex] to binding.editText.text.toString()
        }.groupBy({it.first}, {it.second}),
        uploader = ownerRootBinding.uploaderEditText.text.toString(),
        doExclude = ownerRootBinding.doExcludeSwitch.isChecked
    )

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
}