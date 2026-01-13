package com.example.viewer.activity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.viewer.Gallery
import com.example.viewer.R
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.repository.ItemRepository
import com.example.viewer.databinding.FragmentMainGalleryBinding
import com.example.viewer.dialog.RandomSettingDialog

class GalleryFragment: Fragment() {
    private lateinit var ctx: Context
    private lateinit var binding: FragmentMainGalleryBinding
    private lateinit var gallery: Gallery

    private var groupListLastUpdate = 0L
    private var itemListLastUpdate = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ctx = requireContext()
        binding = FragmentMainGalleryBinding.inflate(layoutInflater, container, false)
        gallery = Gallery(ctx, layoutInflater, binding.recyclerView)

        groupListLastUpdate = GroupRepository.getLastUpdateTime()
        itemListLastUpdate = ItemRepository.getListLastUpdateTime()

        binding.groupListButton.setOnClickListener {
            findNavController().navigate(R.id.main_nav_sort_group)
        }

        binding.randomOpenButton.setOnClickListener {
            RandomSettingDialog(ctx, inflater).show {
                gallery.openRandomItem()
            }
        }

        // handle user select group in group list fragment
        setFragmentResultListener(GroupListFragment.REQUEST_KEY) { _, b ->
            val id = b.getInt(GroupListFragment.BUNDLE_SELECTED_ID_KEY)
            gallery.scrollToGroup(id)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        GroupRepository.getLastUpdateTime().let {
            if (it != groupListLastUpdate) {
                println("[${this::class.simpleName}.${this::onResume.name}] group list updated, refresh")
                gallery.refreshGroup()
                groupListLastUpdate = it
            }
        }
        ItemRepository.getListLastUpdateTime().let {
            if (it != itemListLastUpdate) {
                gallery.refreshItems()
                itemListLastUpdate = it
            }
        }
    }
}
