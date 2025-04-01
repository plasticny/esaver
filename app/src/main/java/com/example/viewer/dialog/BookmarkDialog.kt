package com.example.viewer.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.example.viewer.R
import com.example.viewer.database.BookDatabase
import com.example.viewer.databinding.BookmarkDialogBinding
import com.example.viewer.databinding.BookmarkItemBinding

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

    fun show () {
        refreshBookMarks()

        dialogBinding.addButton.setOnClickListener {
            bookDatabase.addBookMark(bookId, curPage)
            refreshBookMarks()
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
        val bookmarkPages = bookDatabase.getBookMarks(bookId)

        dialogBinding.bookmarkContainer.removeAllViews()

        for (page in bookmarkPages) {
            val bookmarkBinding = BookmarkItemBinding.inflate(layoutInflater)

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
                        bookmarkBinding.cardView.backgroundTintList = ColorStateList.valueOf(context.getColor(
                            R.color.grey))
                        bookmarkBinding.bookmarkIcon.setTextColor(context.getColor(R.color.black))
                        bookmarkBinding.bookmarkTextView.setTextColor(context.getColor(R.color.black))
                        selectedBookMarkBinding = bookmarkBinding
                    }

                    toggleButtonStyle(dialogBinding.removeButton)
                    toggleButtonStyle(dialogBinding.jumpToButton)
                }
            }

            // page + 1 cause the stored page in db starts from 0
            bookmarkBinding.bookmarkTextView.text = (page + 1).toString().trim()

            dialogBinding.bookmarkContainer.addView(bookmarkBinding.root)
        }

        dialogBinding.bookmarkScrollView.visibility =
            if (bookmarkPages.isEmpty()) View.GONE else View.VISIBLE
        dialogBinding.noBookmarkTextView.visibility =
            if (bookmarkPages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleButtonStyle (button: Button) = button.apply {
        backgroundTintList = ColorStateList.valueOf(context.getColor(
            if (selectedBookMarkBinding == null) R.color.grey2 else R.color.grey
        ))
        setTextColor(context.getColor(
            if (selectedBookMarkBinding == null) R.color.grey else R.color.black
        ))
    }
}