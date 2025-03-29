package com.example.viewer.activity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.viewer.databinding.MainOtherFragmentBinding
import com.example.viewer.dataset.BookDataset
import com.example.viewer.dataset.SearchDataset

class OtherFragment: Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootBinding = MainOtherFragmentBinding.inflate(layoutInflater, container, false)

        rootBinding.backupButton.setOnClickListener {
            val context = container!!.context
            BookDataset.getInstance(context).backup(context)
            SearchDataset.getInstance(context).backup(context)
            Toast.makeText(context, "備份已存至Documents", Toast.LENGTH_SHORT).show()
        }

        return rootBinding.root
    }
}