package com.example.viewer.activity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.viewer.BookGallery
import com.example.viewer.database.GroupDatabase
import com.example.viewer.databinding.MainGalleryFragmentBinding

class GalleryFragment: Fragment() {
    private lateinit var ctx: Context
    private lateinit var binding: MainGalleryFragmentBinding
    private lateinit var bookGallery: BookGallery

    private var groupListLastUpdate = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ctx = container!!.context
        binding = MainGalleryFragmentBinding.inflate(layoutInflater, container, false)
        bookGallery = BookGallery(ctx, layoutInflater, binding.recyclerView)

        groupListLastUpdate = GroupDatabase.getInstance(ctx).getLastUpdateTime()

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
        GroupDatabase.getInstance(ctx).getLastUpdateTime().let {
            if (it != groupListLastUpdate) {
                println("[${this::class.simpleName}.${this::onResume.name}] author list updated, refresh")
                bookGallery.refreshGroup()
                groupListLastUpdate = it
            }
        }
        bookGallery.refreshBooks()
    }
}
