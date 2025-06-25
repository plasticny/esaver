package com.example.viewer.activity.main

import android.content.ClipData
import android.content.ClipDescription
import android.content.res.ColorStateList
import android.graphics.Point
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.example.viewer.R
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.struct.Group
import com.example.viewer.databinding.ComponentListItemWithButtonBinding
import com.example.viewer.databinding.FragmentMainSortGroupBinding

class GroupListFragment: Fragment() {
    companion object {
        const val REQUEST_KEY = "select_group"
        const val BUNDLE_SELECTED_ID_KEY = "selected_id"
    }

    private lateinit var rootBinding: FragmentMainSortGroupBinding

    private var droppingItem: ComponentListItemWithButtonBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootBinding = FragmentMainSortGroupBinding.inflate(layoutInflater, container, false)

        rootBinding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        refresh()

        return rootBinding.root
    }

    private fun refresh () {
        rootBinding.groupContainer.removeAllViews()
        for (group in GroupRepository(requireContext()).getAllGroupsInOrder()) {
            rootBinding.groupContainer.addView(buildItem(group).root)
        }
    }

    private fun buildItem (group: Group): ComponentListItemWithButtonBinding {
        val binding = ComponentListItemWithButtonBinding.inflate(layoutInflater, rootBinding.groupContainer, false)

        binding.name.text = group.name

        binding.goButton.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(BUNDLE_SELECTED_ID_KEY to group.id)
            )
            findNavController().navigateUp()
        }

        binding.root.apply {
            setOnLongClickListener { _ ->
                val dragData = ClipData(
                    "drag group",
                    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                    ClipData.Item(group.id.toString())
                )

                val context = requireContext()
                binding.name.setTextColor(context.getColor(R.color.black))
                backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.grey))

                startDragAndDrop(
                    dragData,
                    DragShadowBuilder(this),
                    null,
                    0
                )

                droppingItem = binding
                true
            }
            setOnDragListener { _, event ->
                if (event.action == DragEvent.ACTION_DROP) {
                    val dragId = event.clipData.getItemAt(0).text.toString().toInt()
                    if (dragId != group.id) {
                        GroupRepository(requireContext()).moveGroup(dragId, group.id)
                        refresh()
                    }

                    droppingItem!!.name.setTextColor(context.getColor(R.color.white))
                    backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.dark_grey))
                }
                true
            }
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