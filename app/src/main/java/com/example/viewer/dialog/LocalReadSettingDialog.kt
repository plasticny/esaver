package com.example.viewer.dialog

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.struct.Book
import com.example.viewer.databinding.DialogLocalReadSettingBinding
import kotlinx.coroutines.runBlocking
import java.io.File

class LocalReadSettingDialog (
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogLocalReadSettingBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private val bookRepo = BookRepository(context)
    private val groupRepo = GroupRepository(context)

    var onApplied: (() -> Unit)? = null
    var onCoverCropClicked: ((coverUri: Uri) -> Unit)? = null

    fun show (book: Book) {
        val skipPages = bookRepo.getBookSkipPages(book.id)
        val groupId = bookRepo.getGroupId(book.id)
        val coverPage = bookRepo.getBookCoverPage(book.id)

        dialogBinding.groupNameEditText.setText(
            groupRepo.getGroupName(groupId)
        )

        dialogBinding.customTitleEditText.setText(
            book.customTitle ?: ""
        )

        dialogBinding.profileDialogCoverPageEditText.setText(
            (coverPage + 1).toString()
        )

        dialogBinding.profileDialogSkipPagesEditText.setText(skipPagesListToString(skipPages))

        dialogBinding.searchButton.setOnClickListener {
            SelectGroupDialog(context, layoutInflater).show {
                _, name -> dialogBinding.groupNameEditText.setText(name)
            }
        }

        dialogBinding.profileDialogApplyButton.setOnClickListener {
            // group
            val groupName = dialogBinding.groupNameEditText.text.toString().trim()
            val selectedGroupId = groupName.let {
                if (it.isEmpty()) {
                    return@let 0
                }

                val id = groupRepo.getGroupIdFromName(it)
                if (id != null) {
                    return@let id
                }

                return@let groupRepo.createGroup(groupName)
            }
            if (selectedGroupId != groupId) {
                groupRepo.changeGroup(book.id, groupId, selectedGroupId)
            }

            // custom title
            dialogBinding.customTitleEditText.text.toString().let {
                bookRepo.updateCustomTitle(book.id, it.trim())
            }

            // cover page
            val coverPage = dialogBinding.profileDialogCoverPageEditText.text.toString().trim().let {
                if (it.isEmpty()) {
                    Toast.makeText(context, "封面頁不能為空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    it.toInt()
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "封面頁輸入格式錯誤", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            bookRepo.setBookCoverPage(book.id, coverPage - 1)

            // skip page
            updateSkipPages(
                book.id,
                dialogBinding.profileDialogSkipPagesEditText.text.toString().trim(),
                skipPages
            )

            onApplied?.invoke()

            dialog.dismiss()
        }

        dialogBinding.cropCoverButton.setOnClickListener {
            onCoverCropClicked?.invoke(
                File(book.getBookFolder(context), coverPage.toString()).toUri()
            )
        }

        dialog.show()
    }

    /**
     * @param text text of the skip page editText
     */
    private fun updateSkipPages (bookId: String, text: String, originSkipPages: List<Int>) {
        val coverPage = bookRepo.getBookCoverPage(bookId)
        val updatedSkipPages = skipPageStringToList(text)

        if (updatedSkipPages == originSkipPages) {
            return
        }

        val newSkipPages = updatedSkipPages.minus(originSkipPages.toSet())
        if (newSkipPages.isNotEmpty()) {
            val bookFolder = File(context.getExternalFilesDir(null), bookId)
            for (p in newSkipPages) {
                if (p == coverPage) {
                    continue
                }
                File(bookFolder, p.toString()).let {
                    if (it.exists()) {
                        it.delete()
                    }
                }
            }
        }

        runBlocking {
            bookRepo.setBookSkipPages(bookId, updatedSkipPages.sorted())
        }
    }

    private fun skipPagesListToString (skipPages: List<Int>): String {
        val tokens = mutableListOf<String>()

        var s = -1
        var p = -1
        for (page in skipPages) {
            // first page of segment
            if (s == -1) {
                s = page
                p = page
                continue
            }

            // extend segment
            if (p == page - 1) {
                p = page
                continue
            }

            // segment end, store and start new
            if (s == p) {
                tokens.add((s + 1).toString())
            } else {
                tokens.add("${s + 1}-${p + 1}")
            }
            s = page
            p = page
        }

        if (s != -1) {
            // store last segment
            if (s == p) {
                tokens.add((s + 1).toString())
            } else {
                tokens.add("${s + 1}-${p + 1}")
            }
        }

        return tokens.joinToString(",")
    }

    private fun skipPageStringToList (text: String): List<Int> {
        val res = mutableSetOf<Int>()
        for (token in text.split(',')) {
            if (token.contains("-")) {
                // x-y
                val dashToken = token.split("-")
                if (dashToken.size != 2) {
                    println("[${this::class.simpleName}.${this::skipPageStringToList.name}] '$token' unexpected dash format")
                    continue
                }

                val x = pageStringToPageIndex(dashToken[0].trim())
                val y = pageStringToPageIndex(dashToken[1].trim())
                if (x == null || y == null || x >= y) {
                    println("[${this::class.simpleName}.${this::skipPageStringToList.name}] invalid range ${dashToken[0]}-${dashToken[1]}")
                    continue
                }

                for (p in x..y) {
                    res.add(p)
                }
            } else {
                // other
                pageStringToPageIndex(token.trim())?.let { res.add(it) }
            }
        }
        return res.sorted()
    }

    private fun pageStringToPageIndex (s: String): Int? =
        try {
            (s.toInt() - 1).let { if (it >= 0) it else null }
        } catch (e: NumberFormatException) {
            println("[${this::class.simpleName}.${this::skipPageStringToList.name}] '$s' cannot convert into int")
            null
        }
}