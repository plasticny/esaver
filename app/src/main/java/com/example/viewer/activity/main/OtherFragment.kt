package com.example.viewer.activity.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.viewer.database.BaseDatabase
import com.example.viewer.databinding.FragmentMainOtherBinding
import java.util.Date

class OtherFragment: Fragment() {
    private lateinit var context: Context

    private val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let {
//            BaseDatabase.importDb(context, it)
            // restart the app
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val mi = Intent.makeRestartActivityTask(intent!!.component).apply {
                `package` = context.packageName
            }
            context.startActivity(mi)
            Runtime.getRuntime().exit(0)
        }
    }
    private val saveBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        it?.let {
//            BaseDatabase.backupDb(context, it)
            Toast.makeText(context, "已儲存備份", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "unavailable", Toast.LENGTH_SHORT).show()
//            saveBackupLauncher.launch("eSaver_backup_${Date()}.txt")
        }

        rootBinding.importButton.setOnClickListener {
            Toast.makeText(context, "unavailable", Toast.LENGTH_SHORT).show()
//            importBackupLauncher.launch(arrayOf("text/plain"))
        }

        return rootBinding.root
    }
}