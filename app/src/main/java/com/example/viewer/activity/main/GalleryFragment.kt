package com.example.viewer.activity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.viewer.BookGallery
import com.example.viewer.R
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.databinding.FragmentMainGalleryBinding

class GalleryFragment: Fragment() {
    private lateinit var ctx: Context
    private lateinit var binding: FragmentMainGalleryBinding
    private lateinit var bookGallery: BookGallery

    private var groupListLastUpdate = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ctx = requireContext()
        binding = FragmentMainGalleryBinding.inflate(layoutInflater, container, false)
        bookGallery = BookGallery(ctx, layoutInflater, binding.recyclerView)

        groupListLastUpdate = GroupRepository(ctx).getLastUpdateTime()

        binding.groupListButton.setOnClickListener {
            findNavController().navigate(R.id.main_nav_sort_group)
        }

        binding.randomOpenButton.setOnClickListener {
            bookGallery.openRandomBook()
        }

        // handle user select group in group list fragment
        setFragmentResultListener(GroupListFragment.REQUEST_KEY) { _, b ->
            val id = b.getInt(GroupListFragment.BUNDLE_SELECTED_ID_KEY)
            bookGallery.scrollToGroup(id)
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
        GroupRepository(ctx).getLastUpdateTime().let {
            if (it != groupListLastUpdate) {
                println("[${this::class.simpleName}.${this::onResume.name}] group list updated, refresh")
                bookGallery.refreshGroup()
                groupListLastUpdate = it
            }
        }
        bookGallery.refreshBooks()
    }
}
