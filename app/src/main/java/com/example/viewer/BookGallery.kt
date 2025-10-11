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
import com.example.viewer.activity.BookProfileActivity
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.databinding.FragmentMainGalleryBookBinding
import com.example.viewer.databinding.MainGalleryFragmentAuthorBinding
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.struct.BookSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

class BookGallery (
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val recyclerView: RecyclerView
) {
    private val bookRepo = BookRepository(context)
    private val groupRepo = GroupRepository(context)
    private val filter = Filter()

    // recycler view item metrics
    private val groupNameTextViewHeight = Util.sp2px(context, 18F)
    private val coverImageViewWidth =
        (context.resources.displayMetrics.widthPixels - Util.dp2px(context, 48F)) / 2
    private val coverImageViewHeight = (coverImageViewWidth * 1.4125).toInt()
    private val bookMarginWidth = Util.dp2px(context, 8F)

    private val groupRecyclerViewAdapter: GroupRecyclerViewAdapter
        get() = recyclerView.adapter as GroupRecyclerViewAdapter

    init {
        Log.i("BookGallery", "cover image: width $coverImageViewWidth, height $coverImageViewHeight")
        recyclerView.layoutManager = GridLayoutManager(context, 1)
        recyclerView.adapter = GroupRecyclerViewAdapter()
    }

    fun notifyBookAdded () {
        groupRecyclerViewAdapter.refreshGroupBooks(GroupRepository.DEFAULT_GROUP_ID)
        scrollToGroup(GroupRepository.DEFAULT_GROUP_ID)
    }

    fun applyFilter (doDownloadComplete: Boolean? = null) {
        filter.doDownloadComplete = doDownloadComplete
        groupRecyclerViewAdapter.refreshGroupBooks()
        recyclerView.scrollToPosition(0)
    }

    fun refreshGroup () = groupRecyclerViewAdapter.refreshGroups()

    fun refreshBooks () = groupRecyclerViewAdapter.refreshGroupBooks()

    fun openRandomBook () = openBook(RandomBook.next(context))

    fun scrollToGroup (id: Int) = recyclerView.scrollToPosition(groupRecyclerViewAdapter.getGroupPosition(id))

    private fun openBook (bookId: String) {
        context.startActivity(Intent(context, BookProfileActivity::class.java).apply {
            putExtra("bookId", bookId)
        })
    }

    inner class Filter {
        var doDownloadComplete: Boolean? = null
        fun isFiltered (context: Context, bookId: String, bookDataset: BookDatabase): Boolean {
            throw NotImplementedError()
//            if (doDownloadComplete == null) {
//                return true
//            }
//            val bookFolder = File(context.getExternalFilesDir(null), bookId)
//            val downloadedPageNum = bookFolder.listFiles()!!.size
//            return (downloadedPageNum == bookDataset.getBookPageNum(bookId)) == doDownloadComplete
        }
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

            holder.binding.galleryAuthorBookRecyclerView.apply {
                layoutManager = GridLayoutManager(context, 2)
                adapter = BookRecyclerViewAdapter(id)
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

        fun refreshGroupBooks () {
            for (id in groupIds) {
                refreshGroupBooks(id)
            }
        }

        fun refreshGroupBooks (id: Int) =
            groupHolderMap[id]?.let {
                (it.binding.galleryAuthorBookRecyclerView.adapter as BookRecyclerViewAdapter).refresh()
                wrappingContent(it)
            }

        fun getGroupPosition (id: Int): Int = groupIds.indexOf(id)

        private fun wrappingContent (holder: ViewHolder) {
            val bookAdapter = holder.binding.galleryAuthorBookRecyclerView.adapter as BookRecyclerViewAdapter
            holder.binding.galleryAuthorWrapper.apply {
                layoutParams = (layoutParams as MarginLayoutParams).apply {
                    if (bookAdapter.bookNum == 0) {
                        height = 0
                        bottomMargin = 0
                    } else {
                        height = groupNameTextViewHeight + (coverImageViewHeight + bookMarginWidth * 2) * ceil(bookAdapter.bookNum / 2.0).toInt()
                        bottomMargin = bookMarginWidth
                    }
                }
                visibility = if (bookAdapter.bookNum == 0) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    inner class BookRecyclerViewAdapter (val groupId: Int): RecyclerView.Adapter<BookRecyclerViewAdapter.BookRecyclerViewHolder> () {
        inner class BookRecyclerViewHolder (itemView: View): RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.gallery_item)
        }

        private var bookIdentifies: List<BookWithGroup.Companion.BookIdentify> = getBookIdentifies()
        val bookNum: Int
            get() = bookIdentifies.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookRecyclerViewHolder {
            val binding = FragmentMainGalleryBookBinding.inflate(layoutInflater, parent, false)
            binding.galleryItem.layoutParams = binding.galleryItem.layoutParams.apply {
                width = coverImageViewWidth
                height = coverImageViewHeight
            }
            return BookRecyclerViewHolder(binding.root)
        }

        override fun getItemCount(): Int = bookNum

        override fun onBindViewHolder(holder: BookRecyclerViewHolder, position: Int) {
            val (id, sourceOrdinal) = bookIdentifies[position]

            println("[${this@BookGallery::class.simpleName}.${this::class.simpleName}] binding $id")
            val bookFolder = Book.getBookFolder(context, id, sourceOrdinal)

            val coverPage = bookRepo.getBookCoverPage(id)
            val cropPosition = bookRepo.getCoverCropPosition(id)
            val coverPageFile = File(bookFolder, coverPage.toString())

            CoroutineScope(Dispatchers.Main).launch {
                if (!coverPageFile.exists()) {
                    withContext(Dispatchers.IO) {
                        BasePictureFetcher.getFetcher(context, id).savePicture(coverPage)
                    }
                }
                Glide.with(context)
                    .load(coverPageFile)
                    .signature(MediaStoreSignature("", coverPageFile.lastModified(), 0))
                    .run { cropPosition?.let { transform(CoverCrop(it)) } ?: this }
                    .into(holder.imageView)
            }

            holder.imageView.setOnClickListener {
                openBook(id)
            }

            holder.imageView.setOnLongClickListener {
                true
            }
        }

        fun refresh () {
            bookIdentifies = getBookIdentifies()
            notifyDataSetChanged()
        }

        private fun getBookIdentifies (): List<BookWithGroup.Companion.BookIdentify> {
            return groupRepo.getGroupBookIdentifies(groupId)
//            return groupDatabase.getGroupBookIds(groupId).filter {
//                filter.isFiltered(context, it, bookDatabase)
//            }
        }
    }
}
