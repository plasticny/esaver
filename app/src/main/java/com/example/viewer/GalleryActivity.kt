package com.example.viewer

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class GalleryActivity: AppCompatActivity() {
    private lateinit var addImgView: ImageView
    private lateinit var randomOpenImgView: ImageView
    private lateinit var tmpImgView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewAll: TextView
    private lateinit var textViewDownloaded: TextView
    private lateinit var textViewNotDownloaded: TextView

    private lateinit var bookGallery: BookGallery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery)

        History.init(this)
        println("authors: ${History.getAllAuthors()}")

        bookGallery = BookGallery(this, this.findViewById(R.id.recycler_view))

        addImgView = findViewById(R.id.add_image_view)
        addImgView.setOnClickListener {
            if (Util.isInternetAvailable(this)) {
                showAddDialog()
            }
            else {
                Toast.makeText(this, "沒有連接網絡", Toast.LENGTH_SHORT).show()
            }
        }

        randomOpenImgView = findViewById(R.id.random_open_imageView)
        randomOpenImgView.setOnClickListener {
            bookGallery.openRandomBook()
        }

        tmpImgView = findViewById(R.id.tmp_image_vew)
        progressBar = findViewById(R.id.gallery_progress_bar)

        textViewAll = findViewById(R.id.gallery_text_all)
        textViewAll.setOnClickListener {
            bookGallery.applyFilter()
            textViewAll.setTextColor(ContextCompat.getColor(this, R.color.white))
            textViewDownloaded.setTextColor(ContextCompat.getColor(this, R.color.grey))
            textViewNotDownloaded.setTextColor(ContextCompat.getColor(this, R.color.grey))
        }

        textViewDownloaded = findViewById(R.id.gallery_text_downloaded)
        textViewDownloaded.setOnClickListener {
            bookGallery.applyFilter(true)
            textViewAll.setTextColor(ContextCompat.getColor(this, R.color.grey))
            textViewDownloaded.setTextColor(ContextCompat.getColor(this, R.color.white))
            textViewNotDownloaded.setTextColor(ContextCompat.getColor(this, R.color.grey))
        }

        textViewNotDownloaded = findViewById(R.id.gallery_text_not_downloaded)
        textViewNotDownloaded.setOnClickListener {
            bookGallery.applyFilter(false)
            textViewAll.setTextColor(ContextCompat.getColor(this, R.color.grey))
            textViewDownloaded.setTextColor(ContextCompat.getColor(this, R.color.grey))
            textViewNotDownloaded.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    private fun showAddDialog () {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.add_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val editText = dialogView.findViewById<EditText>(R.id.add_dialog_editText)
        val addButton = dialogView.findViewById<Button>(R.id.add_dialog_add_button)

        addButton.setOnClickListener {
            val url = editText.text.toString()
            if (url.isEmpty()) {
                Toast.makeText(this, "url不能空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bookSource = getBookSource(url)
            if (bookSource == null) {
                Toast.makeText(this, "錯誤的url", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            lifecycleScope.launch {
                progressBar.visibility = ProgressBar.VISIBLE
                val bookAdder = BookAdder.getBookAdder(this@GalleryActivity, bookSource)
                bookAdder.addBook(url) { doAdded ->
                    progressBar.visibility = ProgressBar.GONE
                    if (doAdded) {
                        bookGallery.notifyBookAdded()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun getBookSource (url: String): BookSource? = when {
        Regex("(http(s?)://)?e-hentai.org/g/(\\d+)/([a-zA-Z0-9]+)(/?)$").matches(url) -> BookSource.E
        Regex("(http(s?)://)?hitomi.la/reader/(\\d+).html(#(\\d+))?$").matches(url) -> BookSource.Hi
        else -> null
    }
}
