package com.example.viewer.activity.main

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Point
import android.os.Bundle
import android.view.DragEvent
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ContentInfoCompat.Flags
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.example.viewer.R
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.struct.item.ItemGroup
import com.example.viewer.databinding.ComponentListItemWithButtonBinding
import com.example.viewer.databinding.FragmentMainSortGroupBinding
import com.example.viewer.dialog.SimpleEditTextDialog

class GroupListFragment: Fragment() {
    companion object {
        const val REQUEST_KEY = "select_group"
        const val BUNDLE_SELECTED_ID_KEY = "selected_id"
    }

    private lateinit var rootBinding: FragmentMainSortGroupBinding
    private lateinit var groupRepo: GroupRepository
    private lateinit var gestureDetector: GestureDetector

    private var droppingItem: ComponentListItemWithButtonBinding? = null
    private var lastLongClickedX = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        groupRepo = GroupRepository(requireContext())

        gestureDetector = GestureDetector(
            requireContext(),
            object: GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    super.onLongPress(e)
                    lastLongClickedX = e.x
                }
            }
        )

        rootBinding = FragmentMainSortGroupBinding.inflate(layoutInflater, container, false)

        rootBinding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        refresh()

        return rootBinding.root
    }

    private fun refresh () {
        rootBinding.groupContainer.removeAllViews()
        for (group in groupRepo.getAllGroupsInOrder()) {
            rootBinding.groupContainer.addView(buildItem(group).root)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildItem (group: ItemGroup): ComponentListItemWithButtonBinding {
        val binding = ComponentListItemWithButtonBinding.inflate(layoutInflater, rootBinding.groupContainer, false)

        binding.name.text = group.name

        binding.editButton.setOnClickListener {
            val name = groupRepo.getGroupName(group.id)
            SimpleEditTextDialog(requireContext(), layoutInflater).apply {
                title = "修改組別名稱"
                hint = name
                validator = { input ->
                    if (input.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "名字不能為空",
                            Toast.LENGTH_SHORT
                        ).show()
                        false
                    }
                    else if (input == name) {
                        Toast.makeText(
                            requireContext(),
                            "名字跟之前一樣",
                            Toast.LENGTH_SHORT
                        ).show()
                        false
                    } else {
                        true
                    }
                }
                positiveCb = { input ->
                    groupRepo.changeGroupName(group.id, input)
                    refresh()
                }
            }.show()
        }

        binding.goButton.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(BUNDLE_SELECTED_ID_KEY to group.id)
            )
            findNavController().navigateUp()
        }

        binding.root.apply {
            setOnTouchListener { _, motionEvent ->
                gestureDetector.onTouchEvent(motionEvent)
                false
            }

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

            setOnDragListener { v, event ->
                if (event.action == DragEvent.ACTION_DROP) {
                    val dragId = event.clipData.getItemAt(0).text.toString().toInt()
                    if (dragId != group.id) {
                        if (event.y <= v.height / 2.0) {
                            groupRepo.moveGroupBefore(dragId, group.id)
                        } else {
                            groupRepo.moveGroupAfter(dragId, group.id)
                        }
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

    private inner class DragShadowBuilder (v: View) : View.DragShadowBuilder(v) {
        private val x = v.x

        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(view.width, view.height)
            outShadowTouchPoint.set((lastLongClickedX - x).toInt(), outShadowSize.y / 2)
        }
    }
}