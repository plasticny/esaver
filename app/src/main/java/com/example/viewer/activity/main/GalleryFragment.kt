package com.example.viewer.activity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.viewer.BookAdder
import com.example.viewer.BookGallery
import com.example.viewer.database.BookSource
import com.example.viewer.R
import com.example.viewer.database.BookDatabase
import com.example.viewer.databinding.MainGalleryFragmentBinding
import kotlinx.coroutines.launch

class GalleryFragment: Fragment() {
    private lateinit var ctx: Context
    private lateinit var binding: MainGalleryFragmentBinding
    private lateinit var bookGallery: BookGallery

    private var authorListLastUpdate = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ctx = container!!.context
        binding = MainGalleryFragmentBinding.inflate(layoutInflater, container, false)
        bookGallery = BookGallery(ctx, layoutInflater, binding.recyclerView)

        authorListLastUpdate = BookDatabase.getInstance(ctx).authorListUpdateTime()

//        binding.addImageView.setOnClickListener {
//            if (Util.isInternetAvailable(ctx)) {
//                showAddDialog()
//            } else {
//                Toast.makeText(ctx, "沒有網絡", Toast.LENGTH_SHORT).show()
//            }
//        }

        binding.randomOpenButton.setOnClickListener {
            bookGallery.openRandomBook()
        }

//        binding.galleryTextAll.setOnClickListener {
//            bookGallery.applyFilter()
//            binding.galleryTextAll.setTextColor(ContextCompat.getColor(ctx, R.color.white))
//            binding.galleryTextDownloaded.setTextColor(ContextCompat.getColor(ctx, R.color.grey))
//            binding.galleryTextNotDownloaded.setTextColor(ContextCompat.getColor(ctx, R.color.grey))
//        }
//        binding.galleryTextDownloaded.setOnClickListener {
//            bookGallery.applyFilter(true)
//            binding.galleryTextAll.setTextColor(ContextCompat.getColor(ctx, R.color.grey))
//            binding.galleryTextDownloaded.setTextColor(ContextCompat.getColor(ctx, R.color.white))
//            binding.galleryTextNotDownloaded.setTextColor(ContextCompat.getColor(ctx, R.color.grey))
//        }
//        binding.galleryTextNotDownloaded.setOnClickListener {
//            bookGallery.applyFilter(false)
//            binding.galleryTextAll.setTextColor(ContextCompat.getColor(ctx, R.color.grey))
//            binding.galleryTextDownloaded.setTextColor(ContextCompat.getColor(ctx, R.color.grey))
//            binding.galleryTextNotDownloaded.setTextColor(ContextCompat.getColor(ctx, R.color.white))
//        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        BookDatabase.getInstance(ctx).authorListUpdateTime().let {
            if (it != authorListLastUpdate) {
                println("[${this::class.simpleName}.${this::onResume.name}] author list updated, refresh")
                bookGallery.refreshAuthor()
                authorListLastUpdate = it
            }
        }
        bookGallery.refreshBooks()
    }

    private fun getBookSource (url: String): BookSource? = when {
        Regex("(http(s?)://)?e-hentai.org/g/(\\d+)/([a-zA-Z0-9]+)(/?)$").matches(url) -> BookSource.E
        Regex("(http(s?)://)?hitomi.la/reader/(\\d+).html(#(\\d+))?$").matches(url) -> BookSource.Hi
        else -> null
    }
}
