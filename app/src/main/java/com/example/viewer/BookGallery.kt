package com.example.viewer

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.viewer.activity.BookProfileActivity
import com.example.viewer.activity.viewer.LocalViewerActivity
import com.example.viewer.database.BookDatabase
import com.example.viewer.databinding.FragmentMainGalleryBookBinding
import com.example.viewer.dialog.SelectAuthorDialog
import java.io.File
import kotlin.math.ceil

class BookGallery (
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val recyclerView: RecyclerView
) {
    private val bookDataset = BookDatabase.getInstance(context)
    private val adapterEventHandler = object: AdapterEventHandler {
        override fun onBookClicked(bookId: String) = openBook(bookId)
        override fun onBookLongClicked(author: String, bookId: String) {
            context.startActivity(Intent(context, BookProfileActivity::class.java).apply {
                putExtra(
                    "book_record",
                    bookDataset.getBook(context, bookId, author)
                )
            })
        }
        override fun onAuthorClicked() = SelectAuthorDialog(context, layoutInflater).show {
                author -> scrollToAuthor(author)
        }
    }
    private val filter = Filter()

    // recycler view item metrics
    private val authorTextViewHeight = Util.sp2px(context, 18F)
    private val coverImageViewWidth =
        (context.resources.displayMetrics.widthPixels - Util.dp2px(context, 48F)) / 2
    private val coverImageViewHeight = (coverImageViewWidth * 1.5).toInt()
    private val bookMarginWidth = Util.dp2px(context, 8F)

    private val authorRecyclerViewAdapter: AuthorRecyclerViewAdapter
        get() = recyclerView.adapter as AuthorRecyclerViewAdapter

    init {
        recyclerView.layoutManager = GridLayoutManager(context, 1)
        recyclerView.adapter = AuthorRecyclerViewAdapter()
        println(authorTextViewHeight)
    }

    fun notifyBookAdded () {
        authorRecyclerViewAdapter.refreshAuthorBooks(BookDatabase.NO_AUTHOR)
        scrollToAuthor(BookDatabase.NO_AUTHOR)
    }

    fun applyFilter (doDownloadComplete: Boolean? = null) {
        filter.doDownloadComplete = doDownloadComplete
        authorRecyclerViewAdapter.refreshAuthorBooks()
        recyclerView.scrollToPosition(0)
    }

    fun refreshAuthor () = authorRecyclerViewAdapter.refreshAuthor()

    fun refreshBooks () = authorRecyclerViewAdapter.refreshAuthorBooks()

    fun openRandomBook () = openBook(RandomBook.next(context, !Util.isInternetAvailable(context)))

    private fun openBook (bookId: String) {
        if (!Util.isInternetAvailable(context) && File(context.getExternalFilesDir(null), bookId).listFiles()!!.isEmpty()) {
            Toast.makeText(context, "沒有網絡+未下載任何一頁", Toast.LENGTH_SHORT).show()
            return
        }

        bookDataset.updateBookLastViewTime(bookId)

        val intent = Intent(context, LocalViewerActivity::class.java)
        intent.putExtra("bookId", bookId)
        context.startActivity(intent)
    }

    private fun scrollToAuthor (author: String) = recyclerView.scrollToPosition(authorRecyclerViewAdapter.getAuthorPosition(author)!!)

    inner class Filter {
        var doDownloadComplete: Boolean? = null
        fun isFiltered (context: Context, bookId: String, bookDataset: BookDatabase): Boolean {
            if (doDownloadComplete == null) {
                return true
            }
            val bookFolder = File(context.getExternalFilesDir(null), bookId)
            val downloadedPageNum = bookFolder.listFiles()!!.size
            return (downloadedPageNum == bookDataset.getBookPageNum(bookId)) == doDownloadComplete
        }
    }

    //
    // define recycler view adapters
    //
    interface AdapterEventHandler {
        fun onBookClicked (bookId: String)
        fun onBookLongClicked (author: String, bookId: String)
        fun onAuthorClicked ()
    }

    inner class AuthorRecyclerViewAdapter: RecyclerView.Adapter<AuthorRecyclerViewAdapter.AuthorRecyclerViewHolder>() {
        inner class AuthorRecyclerViewHolder (itemView: View): RecyclerView.ViewHolder(itemView) {
            val wrapper: ConstraintLayout = itemView.findViewById(R.id.gallery_author_wrapper)
            val authorTextView: TextView = itemView.findViewById(R.id.gallery_author_text)
            val bookRecyclerView: RecyclerView = itemView.findViewById(R.id.gallery_author_bookRecyclerView)
        }

        private var authors: List<String> = bookDataset.getAllAuthors()
        private val authorHolderMap: MutableMap<String, AuthorRecyclerViewHolder> = mutableMapOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuthorRecyclerViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.gallery_author, parent, false)
            return AuthorRecyclerViewHolder(view)
        }

        override fun getItemCount(): Int = authors.size

        override fun onBindViewHolder(holder: AuthorRecyclerViewHolder, position: Int) {
            val author = authors[position]
            println("[AuthorRecyclerViewAdapter.onBindViewHolder] $author")

            holder.authorTextView.apply {
                text = if (author == BookDatabase.NO_AUTHOR) ContextCompat.getString(context, R.string.noName) else author
                setOnClickListener { adapterEventHandler.onAuthorClicked() }
            }

            holder.bookRecyclerView.apply {
                layoutManager = GridLayoutManager(context, 2)
                adapter = BookRecyclerViewAdapter(author)
            }

            authorHolderMap[author] = holder
        }

        override fun onViewAttachedToWindow(holder: AuthorRecyclerViewHolder) {
            super.onViewAttachedToWindow(holder)
            wrappingContent(holder)
        }

        fun refreshAuthor () {
            authors = bookDataset.getAllAuthors()
            notifyDataSetChanged()

            val notExistAuthors = authorHolderMap.keys.minus(authors.toSet())
            for (author in notExistAuthors) {
                authorHolderMap.remove(author)
            }
        }

        fun refreshAuthorBooks () {
            for (author in authors) {
                refreshAuthorBooks(author)
            }
        }

        fun refreshAuthorBooks (author: String) {
            val bookAdapter = getBookAdapter(author) ?: return
            bookAdapter.refresh()
            wrappingContent(author)
        }

        fun getBookAdapter (author: String): BookRecyclerViewAdapter? {
            val holder = authorHolderMap[author] ?: return null
            return holder.bookRecyclerView.adapter as BookRecyclerViewAdapter
        }

        fun getAuthorPosition (author: String): Int = authors.indexOf(author)

        private fun wrappingContent (holder: AuthorRecyclerViewHolder) {
            val bookAdapter = holder.bookRecyclerView.adapter as BookRecyclerViewAdapter

            holder.wrapper.layoutParams = (holder.wrapper.layoutParams as MarginLayoutParams).apply {
                if (bookAdapter.bookNum == 0) {
                    height = 0
                    bottomMargin = 0
                } else {
                    height = authorTextViewHeight + (coverImageViewHeight + bookMarginWidth * 2) * ceil(bookAdapter.bookNum / 2.0).toInt()
                    bottomMargin = bookMarginWidth
                }
            }
            holder.wrapper.visibility = if (bookAdapter.bookNum == 0) View.INVISIBLE else View.VISIBLE
        }

        private fun wrappingContent (author: String) = wrappingContent(authorHolderMap[author]!!)
    }

    inner class BookRecyclerViewAdapter (val author: String): RecyclerView.Adapter<BookRecyclerViewAdapter.BookRecyclerViewHolder> () {
        inner class BookRecyclerViewHolder (itemView: View): RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.gallery_item)
        }

        private var bookIds: List<String> = getBookIds()
        val bookNum: Int
            get() = bookIds.size

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
            val id = bookIds[position]
            val bookFolder = File(context.getExternalFilesDir(null), id)
            val coverPage = bookDataset.getBookCoverPage(id)
            val coverPageFile = File(bookFolder, coverPage.toString())

            Glide.with(context).load(
                if (coverPageFile.exists()) coverPageFile else File(bookFolder, "0")
            ).into(holder.imageView)

            holder.imageView.setOnClickListener {
                adapterEventHandler.onBookClicked(id)
            }

            holder.imageView.setOnLongClickListener {
                adapterEventHandler.onBookLongClicked(author, id)
                true
            }
        }

        fun refresh () {
            bookIds = getBookIds()
            notifyDataSetChanged()
        }

        private fun getBookIds (): List<String> = bookDataset.getAuthorBookIds(author).filter {
            filter.isFiltered(context, it, bookDataset)
        }
    }
}
