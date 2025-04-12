package com.example.viewer.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.GridLayout.Spec
import android.widget.GridLayout.UNDEFINED
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.database.BookDatabase
import com.example.viewer.databinding.BookmarkDialogBinding
import com.example.viewer.databinding.BookmarkItemBinding
import kotlinx.coroutines.withContext

class BookmarkDialog (
    context: Context,
    private val layoutInflater: LayoutInflater,
    private val bookId: String,
    private val curPage: Int,
    private val onJumpToClicked: (page: Int) -> Unit
) {
    private val bookDatabase = BookDatabase.getInstance(context)

    private val dialogBinding = BookmarkDialogBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private var selectedBookMarkBinding: BookmarkItemBinding? = null
    private var doCurPageMarked: Boolean = false

    fun show () {
        refreshBookMarks()

        dialogBinding.addButton.apply {
            setOnClickListener {
                if (doCurPageMarked) {
                    return@setOnClickListener
                }
                bookDatabase.addBookMark(bookId, curPage)
                refreshBookMarks()
            }
        }

        dialogBinding.removeButton.setOnClickListener {
            selectedBookMarkBinding?.let {
                bookDatabase.removeBookMark(bookId, it.bookmarkTextView.text.toString().toInt() - 1)
                refreshBookMarks()
            }
        }

        dialogBinding.jumpToButton.setOnClickListener {
            selectedBookMarkBinding?.let {
                onJumpToClicked(it.bookmarkTextView.text.toString().toInt() - 1)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun refreshBookMarks () {
        val bookmarkBindings = bookDatabase.getBookMarks(bookId).also {
            doCurPageMarked = it.contains(curPage)
            toggleButtonStyle(dialogBinding.addButton, !doCurPageMarked)
        }.map { page ->
            val bookmarkBinding = BookmarkItemBinding.inflate(layoutInflater, dialogBinding.bookmarkContainer, false)
            bookmarkBinding.cardView.apply {
                setOnClickListener {
                    // check previous selected bookmark style
                    selectedBookMarkBinding?.let {
                        it.cardView.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.grey2))
                        it.bookmarkIcon.setTextColor(context.getColor(R.color.grey))
                        it.bookmarkTextView.setTextColor(context.getColor(R.color.grey))
                    }

                    // reassign selectedBookMarkBinding, and change self style
                    if (selectedBookMarkBinding == bookmarkBinding) {
                        selectedBookMarkBinding = null
                    } else {
                        bookmarkBinding.cardView.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.grey))
                        bookmarkBinding.bookmarkIcon.setTextColor(context.getColor(R.color.black))
                        bookmarkBinding.bookmarkTextView.setTextColor(context.getColor(R.color.black))
                        selectedBookMarkBinding = bookmarkBinding
                    }

                    toggleButtonStyle(dialogBinding.removeButton, selectedBookMarkBinding != null)
                    toggleButtonStyle(dialogBinding.jumpToButton, selectedBookMarkBinding != null)
                }
            }
            // page + 1 cause the stored page in db starts from 0
            bookmarkBinding.bookmarkTextView.text = (page + 1).toString().trim()
            return@map bookmarkBinding
        }

        with(dialogBinding.bookmarkContainer) {
            removeAllViews()
            bookmarkBindings.forEach { addView(it.root) }
            if (bookmarkBindings.size == 1) {
                // add a dummy view to make the only bookmark item has 50% width
                addView(
                    View(context).apply {
                        val margin = Util.dp2px(context, 4f)
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            height = GridLayout.LayoutParams.MATCH_PARENT
                            columnSpec = GridLayout.spec(UNDEFINED, 1f)
                            leftMargin = margin
                            rightMargin = margin
                            topMargin = margin
                            bottomMargin = margin
                        }
                    }
                )
            }
        }

        dialogBinding.bookmarkScrollView.visibility =
            if (bookmarkBindings.isEmpty()) View.GONE else View.VISIBLE
        dialogBinding.noBookmarkTextView.visibility =
            if (bookmarkBindings.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleButtonStyle (button: Button, toggle: Boolean) = button.apply {
        backgroundTintList = ColorStateList.valueOf(context.getColor(
            if (toggle) R.color.grey else R.color.grey2
        ))
        setTextColor(context.getColor(
            if (toggle) R.color.black else R.color.darkgrey
        ))
    }
}