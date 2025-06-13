package com.example.viewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.database.GroupDatabase
import com.example.viewer.databinding.DialogLocalReadSettingBinding
import com.example.viewer.struct.BookRecord
import kotlinx.coroutines.runBlocking
import java.io.File

class LocalReadSettingDialog (
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {
    private val dialogBinding = DialogLocalReadSettingBinding.inflate(layoutInflater)
    private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

    private val bookDatabase = BookDatabase.getInstance(context)
    private val groupDatabase = GroupDatabase.getInstance(context)

    fun show (
        bookRecord: BookRecord,
        onApplied: (coverPageUpdated: Boolean) -> Unit
    ) {
        val skipPages = bookDatabase.getBookSkipPages(bookRecord.id)

        dialogBinding.groupNameEditText.setText(
            groupDatabase.getGroupName(bookRecord.groupId)
        )

        dialogBinding.profileDialogCoverPageEditText.setText(
            (bookDatabase.getBookCoverPage(bookRecord.id) + 1).toString()
        )

        dialogBinding.profileDialogSkipPagesEditText.setText(skipPagesListToString(skipPages))

        dialogBinding.searchButton.setOnClickListener {
            SelectGroupDialog(context, layoutInflater).show {
                _, name -> dialogBinding.groupNameEditText.setText(name)
            }
        }

        dialogBinding.profileDialogApplyButton.setOnClickListener {
            var coverPageUpdated = false

            val groupName = dialogBinding.groupNameEditText.text.toString().trim()
            val selectedGroupId = groupName.let {
                if (it.isEmpty()) {
                    return@let 0
                }

                val id = groupDatabase.getGroupIdFromName(it)
                if (id != null) {
                    return@let id
                }

                return@let groupDatabase.createGroup(groupName)
            }
            if (selectedGroupId != bookRecord.groupId) {
                groupDatabase.changeGroup(bookRecord.id, bookRecord.groupId, selectedGroupId)
                runBlocking {
                    BookDatabase.getInstance(context).changeBookGroup(bookRecord.id, selectedGroupId)
                }
            }

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
            if (coverPage != bookDatabase.getBookCoverPage(bookRecord.id) + 1) {
                runBlocking {
                    bookDatabase.setBookCoverPage(bookRecord.id, coverPage - 1)
                }
                coverPageUpdated = true
            }

            updateSkipPages(
                bookRecord.id,
                dialogBinding.profileDialogSkipPagesEditText.text.toString().trim(),
                skipPages
            )

            onApplied(coverPageUpdated)

            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * @param text text of the skip page editText
     */
    private fun updateSkipPages (bookId: String, text: String, originSkipPages: List<Int>) {
        val coverPage = bookDatabase.getBookCoverPage(bookId)
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
            bookDatabase.setBookSkipPages(bookId, updatedSkipPages)
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