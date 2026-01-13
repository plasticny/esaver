package com.example.viewer

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.MediaStoreSignature
import com.example.viewer.activity.ItemProfileActivity
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.repository.ItemRepository
import com.example.viewer.data.struct.item.Item
import com.example.viewer.databinding.FragmentMainGalleryItemBinding
import com.example.viewer.databinding.MainGalleryFragmentAuthorBinding
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.ItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

class Gallery (
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val recyclerView: RecyclerView
) {
    private val itemRepo = ItemRepository(context)
    private val groupRepo = GroupRepository(context)

    // recycler view item metrics
    private val groupNameTextViewHeight = Util.sp2px(context, 18F)
    private val coverImageViewWidth =
        (context.resources.displayMetrics.widthPixels - Util.dp2px(context, 48F)) / 2
    private val coverImageViewHeight = (coverImageViewWidth * 1.4125).toInt()
    private val itemMarginWidth = Util.dp2px(context, 8F)

    private val groupRecyclerViewAdapter: GroupRecyclerViewAdapter
        get() = recyclerView.adapter as GroupRecyclerViewAdapter

    init {
        Log.i("Gallery", "cover image: width $coverImageViewWidth, height $coverImageViewHeight")
        recyclerView.layoutManager = GridLayoutManager(context, 1)
        recyclerView.adapter = GroupRecyclerViewAdapter()
    }

    fun refreshGroup () = groupRecyclerViewAdapter.refreshGroups()

    fun refreshItems () = groupRecyclerViewAdapter.refreshGroupItems()

    fun openRandomItem () = openItem(ItemRNG.next(context))

    fun scrollToGroup (id: Int) = recyclerView.scrollToPosition(groupRecyclerViewAdapter.getGroupPosition(id))

    private fun openItem (itemId: Long) {
        context.startActivity(Intent(context, ItemProfileActivity::class.java).apply {
            putExtra("itemId", itemId)
        })
    }

    //
    // define recycler view adapters
    //

    inner class GroupRecyclerViewAdapter: RecyclerView.Adapter<GroupRecyclerViewAdapter.ViewHolder>() {
        inner class ViewHolder (val binding: MainGalleryFragmentAuthorBinding): RecyclerView.ViewHolder(binding.root)

        private var groupIds: List<Int> = groupRepo.getAllGroupIdsInOrder()
        private val groupHolderMap: MutableMap<Int, ViewHolder> = mutableMapOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(MainGalleryFragmentAuthorBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = groupIds.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val id = groupIds[position]

            holder.binding.galleryAuthorText.apply {
                text = if (id == GroupRepository.DEFAULT_GROUP_ID) {
                    ContextCompat.getString(context, R.string.noGroup)
                } else groupRepo.getGroupName(id)
            }

            holder.binding.galleryAuthorItemRecyclerView.apply {
                layoutManager = GridLayoutManager(context, 2)
                adapter = ItemRecyclerViewAdapter(id)
            }

            groupHolderMap[id] = holder
        }

        override fun onViewAttachedToWindow(holder: ViewHolder) {
            super.onViewAttachedToWindow(holder)
            wrappingContent(holder)
        }

        fun refreshGroups () {
            groupIds = groupRepo.getAllGroupIdsInOrder()
            notifyDataSetChanged()

            val notExistAuthors = groupHolderMap.keys.minus(groupIds.toSet())
            for (author in notExistAuthors) {
                groupHolderMap.remove(author)
            }
        }

        fun refreshGroupItems () {
            for (id in groupIds) {
                refreshGroupItems(id)
            }
        }

        fun refreshGroupItems (id: Int) =
            groupHolderMap[id]?.let {
                (it.binding.galleryAuthorItemRecyclerView.adapter as ItemRecyclerViewAdapter).refresh()
                wrappingContent(it)
            }

        fun getGroupPosition (id: Int): Int = groupIds.indexOf(id)

        private fun wrappingContent (holder: ViewHolder) {
            val adapter = holder.binding.galleryAuthorItemRecyclerView.adapter as ItemRecyclerViewAdapter
            holder.binding.galleryAuthorWrapper.apply {
                layoutParams = (layoutParams as MarginLayoutParams).apply {
                    if (adapter.itemNum == 0) {
                        height = 0
                        bottomMargin = 0
                    } else {
                        height = groupNameTextViewHeight + (coverImageViewHeight + itemMarginWidth * 2) * ceil(adapter.itemNum / 2.0).toInt()
                        bottomMargin = itemMarginWidth
                    }
                }
                visibility = if (adapter.itemNum == 0) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    inner class ItemRecyclerViewAdapter (val groupId: Int): RecyclerView.Adapter<ItemRecyclerViewAdapter.ItemRecyclerViewHolder> () {
        inner class ItemRecyclerViewHolder (itemView: View): RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.gallery_item)
        }

        private var items: List<Item.Companion.GalleryItem> = groupRepo.getGalleryItem(groupId)

        val itemNum: Int
            get() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemRecyclerViewHolder {
            val binding = FragmentMainGalleryItemBinding.inflate(layoutInflater, parent, false)
            binding.galleryItem.layoutParams = binding.galleryItem.layoutParams.apply {
                width = coverImageViewWidth
                height = coverImageViewHeight
            }
            return ItemRecyclerViewHolder(binding.root)
        }

        override fun getItemCount(): Int = itemNum

        override fun onBindViewHolder(holder: ItemRecyclerViewHolder, position: Int) {
            val item = items[position]

            val coverPage = itemRepo.getCoverPage(item.id)
            val cropPosition = itemRepo.getCoverCropPosition(item.id)
            val coverPageFile = File(Item.getFolder(context, item.id), coverPage.toString())

            CoroutineScope(Dispatchers.Main).launch {
                if (!coverPageFile.exists()) {
                    withContext(Dispatchers.IO) {
                        when (ItemType.fromOrdinal(item.typeOrdinal)) {
                            ItemType.Book -> BasePictureFetcher.getFetcher(context, item.id).savePicture(coverPage)
                            else -> NotImplementedError()
                        }
                    }
                }
                Glide.with(context)
                    .load(coverPageFile)
                    .signature(MediaStoreSignature("", coverPageFile.lastModified(), 0))
                    .run { cropPosition?.let { transform(CoverCrop(it)) } ?: this }
                    .into(holder.imageView)
            }

            holder.imageView.setOnClickListener {
                openItem(item.id)
            }

            holder.imageView.setOnLongClickListener {
                true
            }
        }

        fun refresh () {
            items = groupRepo.getGalleryItem(groupId)
            notifyDataSetChanged()
        }
    }
}
