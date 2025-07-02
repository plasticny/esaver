package com.example.viewer.activity.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.viewer.databinding.FragmentMainOtherBinding
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.Date

class OtherFragment: Fragment() {
    companion object {
        private val BACKUP_HEADER_TAG_BYTES = "29448d15-9254-45e8-87b9-835a1cc7cf0c".toByteArray()
    }

    private lateinit var context: Context

    private val saveBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        it?.let {
            backup(it)
            Toast.makeText(context, "已儲存備份", Toast.LENGTH_SHORT).show()
        }
    }
    private val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let {
            import(it)
            // restart the app
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val mi = Intent.makeRestartActivityTask(intent!!.component).apply {
                `package` = context.packageName
            }
            context.startActivity(mi)
            Runtime.getRuntime().exit(0)
        }
    }

    override fun onCreateView (
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootBinding = FragmentMainOtherBinding.inflate(layoutInflater, container, false)

        context = requireContext()

        rootBinding.backupButton.setOnClickListener {
            saveBackupLauncher.launch("eSaver_backup_${Date()}.txt")
        }

        rootBinding.importButton.setOnClickListener {
            importBackupLauncher.launch(arrayOf("text/plain"))
        }

        return rootBinding.root
    }

    private fun backup (backupFileUri: Uri) {
        val bookBytes = readDbBytes("book_db")
        val searchBytes = readDbBytes("search_db")
        val backupBytes = mutableListOf<Byte>().apply {
            addAll("${bookBytes.size} ${searchBytes.size}".toByteArray().toList())
            addAll(BACKUP_HEADER_TAG_BYTES.toList())
            addAll(bookBytes)
            addAll(searchBytes)
        }

        context.contentResolver.openFileDescriptor(backupFileUri, "w")?.use { fd ->
            FileOutputStream(fd.fileDescriptor).use { fos ->
                fos.write(backupBytes.toByteArray())
            }
        }
    }

    private fun import (backupFileUri: Uri) {
        val bytes = context.contentResolver.openFileDescriptor(backupFileUri, "r")!!.use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis -> fis.readBytes() }
        }

        val tagStart = findHeaderTagIndex(bytes)
        val tagEnd = tagStart + BACKUP_HEADER_TAG_BYTES.size - 1
        val sizes = String(bytes.slice(0 until tagStart).toByteArray()).split(' ').map { it.toInt() }

        val bookStart = tagEnd + 1
        val searchStart = bookStart + sizes[0]

        val bookBytes = bytes.slice(bookStart until searchStart).toByteArray()
        val searchBytes = bytes.slice(searchStart .. bytes.lastIndex).toByteArray()

        FileOutputStream(context.getDatabasePath("book_db")).use { it.write(bookBytes) }
        FileOutputStream(context.getDatabasePath("search_db")).use { it.write(searchBytes) }
    }

    private fun readDbBytes (name: String) = try {
        FileInputStream(context.getDatabasePath(name)).use {
            it.readBytes().toList()
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
        listOf()
    }

    private fun findHeaderTagIndex (bytes: ByteArray): Int {
        for (i in 0 .. bytes.size - BACKUP_HEADER_TAG_BYTES.size) {
            if ((i until i + BACKUP_HEADER_TAG_BYTES.size).all { bytes[it] == BACKUP_HEADER_TAG_BYTES[it - i] }) {
                return i
            }
        }
        throw Exception("[${this::class.simpleName}.${this::findHeaderTagIndex.name}] header tag not found")
    }
}