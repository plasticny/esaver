package com.example.viewer.activity.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.viewer.database.BookDatabase
import com.example.viewer.database.GroupDatabase
import com.example.viewer.database.SearchDatabase
import com.example.viewer.databinding.DialogImportDbBinding
import com.example.viewer.databinding.FragmentMainOtherBinding
import java.io.File

class OtherFragment: Fragment() {
    private lateinit var context: Context

    private lateinit var pickBookDbLauncher: ActivityResultLauncher<String>
    private lateinit var pickGroupDbLauncher: ActivityResultLauncher<String>
    private lateinit var pickSearchDbLauncher: ActivityResultLauncher<String>

    override fun onCreateView (
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootBinding = FragmentMainOtherBinding.inflate(layoutInflater, container, false)

        context = requireContext()

        pickBookDbLauncher = createFilePickLauncher { uri ->
            BookDatabase.getInstance(context).importDb(context, uri)
        }
        pickGroupDbLauncher = createFilePickLauncher { uri ->
            GroupDatabase.getInstance(context).importDb(context, uri)
        }
        pickSearchDbLauncher = createFilePickLauncher { uri ->
            SearchDatabase.getInstance(context).importDb(context, uri)
        }

        rootBinding.backupButton.setOnClickListener {
            BookDatabase.getInstance(context).backup(context)
            SearchDatabase.getInstance(context).backup(context)
            GroupDatabase.getInstance(context).backup(context)
            Toast.makeText(context, "備份已存至Documents", Toast.LENGTH_SHORT).show()
        }

        rootBinding.importButton.setOnClickListener {
            val dialogBinding = DialogImportDbBinding.inflate(inflater)
            val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

            dialogBinding.book.setOnClickListener {
                pickBookDbLauncher.launch("*/*")
            }
            dialogBinding.search.setOnClickListener {
                pickSearchDbLauncher.launch("*/*")
            }
            dialogBinding.group.setOnClickListener {
                pickGroupDbLauncher.launch("*/*")
            }

            dialog.show()
        }

        return rootBinding.root
    }

    private fun createFilePickLauncher (cb: (Uri) -> Unit) =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let { cb(it) }
        }
}