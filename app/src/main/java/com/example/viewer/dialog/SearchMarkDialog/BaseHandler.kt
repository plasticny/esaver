package com.example.viewer.dialog.SearchMarkDialog

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.core.view.setMargins
import com.example.viewer.Util
import com.example.viewer.databinding.DialogSearchMarkBinding
import com.example.viewer.databinding.DialogSearchMarkTagBinding
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.Category

abstract class BaseHandler (
    private val context: Context,
    val layoutInflater: LayoutInflater,
    protected val owner: SearchMarkDialog,
    protected val ownerRootBinding: DialogSearchMarkBinding,
) {
    companion object {
        val TAGS = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.keys) }.toList()
        val TAGS_DISPLAY = mutableListOf("-").also { it.addAll(Util.TAG_TRANSLATION_MAP.values) }.toList()
    }

    abstract val source: ItemSource

    abstract fun setupUi ()
    abstract fun buildDialogData (): DialogData

    val dialogData: DialogData
        get() = buildDialogData()

    private val catButtonTextSize = Util.sp2px(context, 7f).toFloat()
    private val catButtonMargin = Util.dp2px(context, 4f)

    fun createSearchMarkDialogTag (cat: String? = null, value: String? = null) =
        DialogSearchMarkTagBinding.inflate(layoutInflater).apply {
            spinner.apply {
                setItems(TAGS_DISPLAY)
                cat?.let { selectedIndex = TAGS.indexOf(it) }
            }
            value?.let { editText.setText(it) }
        }

    protected fun createCategoryButton (category: Category, columnIndex: Int): Button =
        Button(context).apply {
            text = context.getString(category.displayText)
            textSize = catButtonTextSize
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0 // Use 0 for weight-based width
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) // Fill the row
                columnSpec = GridLayout.spec(columnIndex, 1f) // Alternate columns
                setMargins(catButtonMargin)
            }
        }
}