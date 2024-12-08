package com.example.viewer

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.ceil
import kotlin.random.Random

class BookGallery (private val context: Context, private val recyclerView: RecyclerView) {
    private val authorRecyclerViewAdapter: AuthorRecyclerViewAdapter = AuthorRecyclerViewAdapter(
        context, object: AdapterEventHandler {
            override fun onBookClicked(bookId: String) = openBook(bookId)
            override fun onBookLongClicked(author: String, bookId: String) = showProfileDialog(author, bookId)
        }
    )

    init {
        recyclerView.layoutManager = GridLayoutManager(context, 1)
        recyclerView.adapter = authorRecyclerViewAdapter
    }

    fun notifyBookAdded () {
        authorRecyclerViewAdapter.refreshAuthorBooks(History.NO_AUTHOR)
    }

    fun applyFilter (doDownloadComplete: Boolean? = null) {
        BookRecyclerViewAdapter.filter.doDownloadComplete = doDownloadComplete
        authorRecyclerViewAdapter.refreshAuthorBooks()
        recyclerView.scrollToPosition(0)
    }

    fun openRandomBook () {
        val isInternetAvailable = Util.isInternetAvailable(context)
        val bookIds = History.getAllBookIds().filter {
            if (isInternetAvailable) {
                true
            }
            else {
                val bookFolder = File(context.getExternalFilesDir(null), it)
                History.getBookPageNum(it) <= bookFolder.listFiles()!!.size
            }
        }

        if (bookIds.isEmpty()) {
            Toast.makeText(context, "沒找到可打開的書", Toast.LENGTH_SHORT).show()
            return
        }
        openBook(bookIds[Random.nextInt(bookIds.size)])
    }

    private fun openBook (bookId: String) {
        val bookFolder = File(context.getExternalFilesDir(null), bookId)
        if (!Util.isInternetAvailable(context) && History.getBookPageNum(bookId) > bookFolder.listFiles()!!.size) {
            Toast.makeText(context, "未完成下載+沒有網絡", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, ViewerActivity::class.java)
        intent.putExtra("bookId", bookId)
        context.startActivity(intent)
    }

    private fun showProfileDialog (author: String, bookId: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.profile_dialog, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        val bookIdTextView = dialogView.findViewById<TextView>(R.id.profile_dialog_bookId_textView)
        val deleteImageView = dialogView.findViewById<ImageView>(R.id.profile_dialog_delete_imageView)
        val authorEditText = dialogView.findViewById<EditText>(R.id.profile_dialog_author_editText)
        val coverPageEditText = dialogView.findViewById<EditText>(R.id.profile_dialog_coverPage_editText)
        val skipPageEditText = dialogView.findViewById<EditText>(R.id.profile_dialog_skipPages_editText)
        val applyButton = dialogView.findViewById<Button>(R.id.profile_dialog_apply_button)

        bookIdTextView.text = bookId
        authorEditText.setText(author)
        coverPageEditText.setText((History.getBookCoverPage(bookId) + 1).toString())
        skipPageEditText.setText(History.getBookSkipPages(bookId).joinToString(",") { (it + 1).toString() })

        var deleteImageFirstClicked = false
        deleteImageView.setOnClickListener {
            if (!deleteImageFirstClicked) {
                deleteImageFirstClicked = true
                Toast.makeText(context, "再點一次就刪除", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.Main).launch {
                deleteBook(author, bookId)
                authorRecyclerViewAdapter.refreshAuthorBooks(author)
                dialog.dismiss()
            }
        }

        applyButton.setOnClickListener {
            val authorText = authorEditText.text.toString().trim()
            val coverPageText = coverPageEditText.text.toString().trim()
            val skipPageText = skipPageEditText.text.toString().trim()

            if (authorText.isEmpty()) {
                Toast.makeText(context, "作者不能為空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (authorText.isEmpty()) {
                Toast.makeText(context, "封面頁不能為空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // update author and cover page
            val coverPage = coverPageText.toInt()
            var doRefreshAuthor = false
            val refreshAuthorSet = mutableSetOf<String>()
            if (authorText != author) {
                changeAuthor(bookId, author, authorText)
                doRefreshAuthor = true
                refreshAuthorSet.add(author)
                refreshAuthorSet.add(authorText)
            }
            if (coverPage != History.getBookCoverPage(bookId) + 1) {
                History.setBookCoverPage(bookId, coverPage - 1)
                refreshAuthorSet.add(authorText)
            }

            if (doRefreshAuthor) {
                authorRecyclerViewAdapter.refreshAuthor()
            }
            for (refreshAuthor in refreshAuthorSet) {
                authorRecyclerViewAdapter.refreshAuthorBooks(refreshAuthor)
            }

            // update skip pages
            val newSkipPages = mutableListOf<Int>()
            for (token in skipPageText.split(',')) {
                try {
                    newSkipPages.add(token.trim().toInt() - 1)
                }
                catch (e: Exception) {
                    println("[reading skip page] token '$token' cannot convert into int")
                }
            }
            History.setBookSkipPages(bookId, newSkipPages)

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteBook (author: String, bookId: String) {
        History.removeAuthorBookId(author, bookId)
        History.removeBookPageNum(bookId)
        History.removeBookUrl(bookId)
        History.removeBookCoverPage(bookId)
        History.removeBookSkipPages(bookId)

        if (History.getBookSource(bookId) == BookSource.E) {
            History.removeBookPageUrls(bookId)
            History.removeBookP(bookId)
        }
        History.removeBookSource(bookId)

        val bookFolder = File(context.getExternalFilesDir(null), bookId)
        for (file in bookFolder.listFiles()!!) {
            file.delete()
        }
        bookFolder.delete()

        History.removeBookId(bookId)
    }

    private fun changeAuthor (bookId: String, oldAuthor: String, newAuthor: String) {
        if (!History.getAllAuthors().contains(newAuthor)) {
            History.addAuthor(newAuthor)
        }

        History.addAuthorBookId(newAuthor, bookId)
        History.removeAuthorBookId(oldAuthor, bookId)

        if (oldAuthor != History.NO_AUTHOR && History.getAuthorBookIds(oldAuthor).isEmpty()) {
            History.removeAuthor(oldAuthor)
        }
    }
}

//
// adapter event handler
//
private interface AdapterEventHandler {
    fun onBookClicked (bookId: String)
    fun onBookLongClicked (author: String, bookId: String)
}

//
//  author recycler view
//
private class AuthorRecyclerViewHolder (itemView: View): RecyclerView.ViewHolder(itemView) {
    val wrapper: ConstraintLayout = itemView.findViewById(R.id.gallery_author_wrapper)
    val authorTextView: TextView = itemView.findViewById(R.id.gallery_author_text)
    val bookRecyclerView: RecyclerView = itemView.findViewById(R.id.gallery_author_bookRecyclerView)
}

private class AuthorRecyclerViewAdapter (val context: Context, val handler: AdapterEventHandler): RecyclerView.Adapter<AuthorRecyclerViewHolder>() {
    companion object {
        private const val NAME_HEIGHT = 24F
        private const val COVER_HEIGHT = 276F
        private const val MARGIN_HEIGHT = 8F + 14F
    }

    private var authors: List<String> = History.getAllAuthors()
    private val authorHolderMap: MutableMap<String, AuthorRecyclerViewHolder> = mutableMapOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuthorRecyclerViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.gallery_author, parent, false)
        return AuthorRecyclerViewHolder(view)
    }

    override fun getItemCount(): Int = authors.size

    override fun onBindViewHolder(holder: AuthorRecyclerViewHolder, position: Int) {
        val author = authors[position]
        println("[AuthorRecyclerViewAdapter.onBindViewHolder] $author")

        // author name text view
        val authorText = if (author == History.NO_AUTHOR) ContextCompat.getString(context, R.string.noName) else author
        holder.authorTextView.text = authorText

        // set recycler view for books
        holder.bookRecyclerView.layoutManager = GridLayoutManager(context, 2)
        holder.bookRecyclerView.adapter = BookRecyclerViewAdapter(context, author, handler)
        authorHolderMap[author] = holder
    }

    override fun onViewAttachedToWindow(holder: AuthorRecyclerViewHolder) {
        super.onViewAttachedToWindow(holder)
        wrappingContent(holder)
    }

    fun refreshAuthor () {
        authors = History.getAllAuthors()
        notifyDataSetChanged()

        val notExistAuthors = authorHolderMap.keys.minus(authors.toSet())
        for (author in notExistAuthors) {
            println("remove $author")
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

    private fun wrappingContent (holder: AuthorRecyclerViewHolder) {
        val bookAdapter = holder.bookRecyclerView.adapter as BookRecyclerViewAdapter

        val heightDp: Float = if (bookAdapter.bookNum == 0) 0F else NAME_HEIGHT + COVER_HEIGHT * ceil(bookAdapter.bookNum / 2.0F) + MARGIN_HEIGHT
        val layoutParams = holder.wrapper.layoutParams
        layoutParams.height = Util.dp2px(context, heightDp)

        holder.wrapper.layoutParams = layoutParams
        holder.wrapper.visibility = if (bookAdapter.bookNum == 0) View.INVISIBLE else View.VISIBLE
    }

    private fun wrappingContent (author: String) {
        wrappingContent(authorHolderMap[author]!!)
    }
}

//
//  book recycler view
//
private class BookRecyclerViewHolder (itemView: View): RecyclerView.ViewHolder(itemView) {
    val imageView: ImageView = itemView.findViewById(R.id.gallery_item)
}

private class BookRecyclerViewAdapter (
    val context: Context,
    val author: String,
    val handler: AdapterEventHandler
): RecyclerView.Adapter<BookRecyclerViewHolder> () {
    companion object {
        class Filter {
            var doDownloadComplete: Boolean? = null
            fun isFiltered (context: Context, bookId: String): Boolean {
                if (doDownloadComplete == null) {
                    return true
                }
                val bookFolder = File(context.getExternalFilesDir(null), bookId)
                val downloadedPageNum = bookFolder.listFiles()!!.size
                return (downloadedPageNum == History.getBookPageNum(bookId)) == doDownloadComplete
            }
        }
        val filter = Filter()
    }

    private var bookIds: List<String> = getBookIds()
    val bookNum: Int
        get() = bookIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookRecyclerViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.gallery_book, parent, false)
        return BookRecyclerViewHolder(view)
    }

    override fun getItemCount(): Int = bookNum

    override fun onBindViewHolder(holder: BookRecyclerViewHolder, position: Int) {
        val id = bookIds[position]
        val bookFolder = File(context.getExternalFilesDir(null), id)
        val coverPage = History.getBookCoverPage(id)
        val coverPageFile = File(bookFolder, coverPage.toString())

        Glide.with(context).load(
            if (coverPageFile.exists()) coverPageFile else File(bookFolder, "0")
        ).into(holder.imageView)

        holder.imageView.setOnClickListener {
            handler.onBookClicked(id)
        }

        holder.imageView.setOnLongClickListener {
            handler.onBookLongClicked(author, id)
            true
        }
    }

    fun refresh () {
        bookIds = getBookIds()
        notifyDataSetChanged()
    }

    private fun getBookIds (): List<String> = History.getAuthorBookIds(author).filter { filter.isFiltered(context, it) }
}
