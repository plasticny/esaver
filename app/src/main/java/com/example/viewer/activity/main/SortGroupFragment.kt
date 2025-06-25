package com.example.viewer.activity.main

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Point
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.struct.Group
import com.example.viewer.databinding.FragmentMainSortGroupBinding
import com.example.viewer.databinding.FragmentMainSortGroupItemBinding


class SortGroupFragment: Fragment() {
    private lateinit var binding: FragmentMainSortGroupBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainSortGroupBinding.inflate(layoutInflater, container, false)

        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        refresh()

        return binding.root
    }

    private fun refresh () {
        binding.groupContainer.removeAllViews()
        for (group in GroupRepository(requireContext()).getAllGroupsInOrder()) {
            binding.groupContainer.addView(buildItem(group).root)
        }
    }

    private fun buildItem (group: Group): FragmentMainSortGroupItemBinding {
        val binding = FragmentMainSortGroupItemBinding.inflate(layoutInflater)

        binding.name.text = group.name

        binding.moveButton.apply {
            setOnLongClickListener { _ ->
                val dragData = ClipData(
                    "drag group",
                    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                    ClipData.Item(group.id.toString())
                )
                binding.name.startDragAndDrop(
                    dragData,
                    DragShadowBuilder(binding.name),
                    null,
                    0
                )
            }
        }

        binding.root.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) {
                val dragId = event.clipData.getItemAt(0).text.toString().toInt()
                if (dragId != group.id) {
                    GroupRepository(requireContext()).moveGroup(dragId, group.id)
                    refresh()
                }
            }
            true
        }

        return binding
    }

    private class DragShadowBuilder (v: View) : View.DragShadowBuilder(v) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(view.width, view.height)
            outShadowTouchPoint.set(0, outShadowSize.y / 2)
        }
    }
}