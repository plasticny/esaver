package com.example.viewer.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.room.Transaction
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.MediaStoreSignature
import com.example.viewer.CoverCrop
import com.example.viewer.OkHttpHelper
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.activity.main.MainActivity
import com.example.viewer.activity.search.SearchActivity
import com.example.viewer.activity.pictureViewer.LocalPictureViewerActivity
import com.example.viewer.activity.pictureViewer.OnlinePictureViewerActivity
import com.example.viewer.activity.videoViewer.BaseVideoViewerActivity
import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.data.repository.GroupRepository
import com.example.viewer.data.repository.ItemRepository
import com.example.viewer.data.repository.VideoRepository
import com.example.viewer.data.struct.item.Item
import com.example.viewer.databinding.ActivityItemProfileBinding
import com.example.viewer.databinding.ComponentItemProfileTagBinding
import com.example.viewer.databinding.DialogItemInfoBinding
import com.example.viewer.databinding.DialogLocalReadSettingBinding
import com.example.viewer.databinding.DialogTagBinding
import com.example.viewer.dialog.ConfirmDialog
import com.example.viewer.dialog.EditExcludeTagDialog
import com.example.viewer.dialog.SelectGroupDialog
import com.example.viewer.fetcher.BasePictureFetcher
import com.example.viewer.fetcher.EPictureFetcher
import com.example.viewer.fetcher.WnPictureFetcher
import com.example.viewer.struct.ItemSource
import com.example.viewer.struct.Category
import com.example.viewer.struct.ItemType
import com.example.viewer.struct.ProfileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import java.io.File
import kotlin.math.floor
import kotlin.math.min

/**
 * LongExtra: itemId (-1 for temp item)
 */
class ItemProfileActivity: AppCompatActivity() {
    companion object {
        // (width, height)
        private var coverMetrics: Pair<Int, Int>? = null
        private fun getCoverMetrics (context: Context): Pair<Int, Int> {
            return coverMetrics ?: context.resources.displayMetrics.let { displayMetrics ->
                val width = min(Util.dp2px(context, 160F), displayMetrics.widthPixels)
                val height = (width * 1.4125).toInt()
                Pair(width, height).also { coverMetrics = it }
            }
        }

        private var resumeItemId: Long? = null
        fun setResumeItem (itemId: Long) {
            resumeItemId = itemId
        }
    }

    private lateinit var item: ProfileItem
    private lateinit var rootBinding: ActivityItemProfileBinding

    private val groupRepo: GroupRepository by lazy { GroupRepository(baseContext) }
    private val bookRepo: BookRepository by lazy { BookRepository(baseContext) }
    private val videRepo: VideoRepository by lazy { VideoRepository(baseContext) }
    private val itemRepo: ItemRepository by lazy { ItemRepository(baseContext) }
    private val cropLauncher = registerForActivityResult(CropContract()) {
        it?.let {
            when (item.type) {
                ItemType.Book -> bookRepo.updateCoverCropPosition(item.id, it)
                ItemType.Video -> throw NotImplementedError()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootBinding = ActivityItemProfileBinding.inflate(layoutInflater)

        rootBinding.coverWrapper.let {
            // adjust cover image size
            val (width, height) = getCoverMetrics(baseContext)
            it.layoutParams = it.layoutParams.apply {
                this.width = width
                this.height = height
            }
        }

        rootBinding.readButton.setOnClickListener {
            when (item.type) {
                ItemType.Book -> {
                    if (item.isTmp) {
                        startActivity(Intent(baseContext, OnlinePictureViewerActivity::class.java))
                    } else {
                        ItemRepository(baseContext).updateLastViewTime(item.id)
                        startActivity(Intent(baseContext, LocalPictureViewerActivity::class.java).apply {
                            putExtra("itemId", item.id)
                        })
                    }
                }
                ItemType.Video -> {
                    if (item.isTmp) {
                        startActivity(Intent(baseContext, BaseVideoViewerActivity::class.java))
                    } else {
                        ItemRepository(baseContext).updateLastViewTime(item.id)
                        startActivity(Intent(baseContext, BaseVideoViewerActivity::class.java).apply {
                            putExtra("itemId", item.id)
                        })
                    }
                }
            }
        }

        rootBinding.saveButton.setOnClickListener {
            if (!item.isTmp) {
                return@setOnClickListener
            }
            when (item.type) {
                ItemType.Book -> lifecycleScope.launch { saveBook() }
                ItemType.Video -> lifecycleScope.launch { saveVideo() }
                else -> throw NotImplementedError()
            }
        }

        rootBinding.localSettingButton.setOnClickListener {
            if (!item.isTmp) {
                LocalReadSettingDialog().show(item)
            }
        }

        rootBinding.infoButton.setOnClickListener {
            showInfoDialog()
        }

        rootBinding.saveAsButton.setOnClickListener {
            val dialog = ConfirmDialog(this@ItemProfileActivity, layoutInflater)
            when (item.type) {
                ItemType.Book -> dialog.show(
                    message = "另存？",
                    positiveCallback = { saveAs() }
                )
                else -> throw NotImplementedError()
            }
        }

        rootBinding.deleteButton.apply {
            setOnClickListener {
                if (item.isTmp) {
                    return@setOnClickListener
                }
                ConfirmDialog(this@ItemProfileActivity, layoutInflater).show(
                    getString(R.string.doDelete),
                    positiveCallback = {
                        toggleProgressBar(true)
                        CoroutineScope(Dispatchers.IO).launch {
                            itemRepo.removeItem(item.id).let { retFlag ->
                                withContext(Dispatchers.Main) {
                                    toggleProgressBar(false)
                                    if (retFlag) {
                                        finish()
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        setContentView(rootBinding.root)

        prepareItem(intent.getLongExtra("itemId", -1))
    }

    override fun onResume() {
        super.onResume()
        if (resumeItemId != null) {
            prepareItem(resumeItemId!!)
            resumeItemId = null
        }
        else if (!item.isTmp) {
            // the item may be updated
            prepareItem(item.id)
            refreshCoverPage()
        }
    }

    private fun prepareItem (itemId: Long) {
        item = if (itemId == -1L) ProfileItem.getTmp() else ProfileItem.build(baseContext, itemId)

        //
        // setup ui
        //

        rootBinding.coverImageView.let {
            CoroutineScope(Dispatchers.Main).launch {
                if (item.isTmp) {
                    Glide.with(baseContext).load(item.coverUrl).into(it)
                } else {
                    val coverFile = File(item.coverUrl)
                    if (!coverFile.exists()) {
                        withContext(Dispatchers.IO) {
                            BasePictureFetcher.getFetcher(baseContext, item.id).savePicture(item.coverPage)
                        }
                    }
                    Glide.with(baseContext)
                        .load(coverFile)
                        .signature(MediaStoreSignature("", coverFile.lastModified(), 0))
                        .run {
                            item.coverCropPosition?.let { p -> transform(CoverCrop(p)) }
                            into(it)
                        }
                }
            }
        }

        rootBinding.titleTextView.text = item.customTitle ?: item.title

        rootBinding.warningContainer.apply {
            visibility = View.GONE

            // for e only
//            if (!item.isTmp) {
//                return@apply
//            }
//            if (item.source != ItemSource.E) {
//                return@apply
//            }
//            if (!Util.isInternetAvailable(baseContext)) {
//                return@apply
//            }
//
//            // only check warning if book is not stored
//            lifecycleScope.launch {
//                val doc = try {
//                    EPictureFetcher.fetchWebpage(item.url)
//                } catch (_: HttpStatusException) {
//                    visibility = View.VISIBLE
//                    rootBinding.warningText.text = getString(R.string.book_seems_deleted)
//                    return@launch
//                }
//
//                // check is book has content warning, only for non stored book
//                if (doc.html().contains("<h1>Content Warning</h1>")) {
//                    visibility = View.VISIBLE
//                    rootBinding.warningText.text = getString(R.string.contain_offensive_context)
//                }
//            }
        }

        rootBinding.pageNumTextView.apply {
            when (item.type) {
                ItemType.Book -> {
                    visibility = View.VISIBLE
                    text = baseContext.getString(R.string.n_page, item.bookData!!.pageNum)
                }
                ItemType.Video -> {
                    visibility = View.GONE
                }
            }
        }

        rootBinding.categoryTextView.apply {
            text = getString(item.category.displayText)
            setTextColor(context.getColor(item.category.color))
        }

        rootBinding.tagWrapper.apply {
            removeAllViews()
            lifecycleScope.launch {
                for (entry in item.tags.entries) {
                    addView(createTagRow(entry.key, entry.value).root)
                }
            }
        }

        refreshButtons()
    }

    private fun createTagRow (tagCat: String, tagValues: List<String>) =
        ComponentItemProfileTagBinding.inflate(layoutInflater).apply {
            tagCategoryTextView.text = Util.TAG_TRANSLATION_MAP[tagCat] ?: tagCat
            for (value in tagValues) {
                Button(baseContext).apply {
                    text = value
                    backgroundTintList = ColorStateList.valueOf(baseContext.getColor(R.color.dark_grey))
                    isAllCaps = false
                    setTextColor(getColor(
                        if (item.excludedTags[tagCat]?.contains(value) == true) {
                            R.color.grey2
                        } else R.color.grey
                    ))

                    setOnClickListener { showTagDialog(tagCat, value) }
                }.also { tagValueWrapper.addView(it) }
            }
        }

    private fun toggleProgressBar (toggle: Boolean) {
        rootBinding.progress.wrapper.visibility = if (toggle) ProgressBar.VISIBLE else ProgressBar.GONE
    }

    private fun showTagDialog (category: String, value: String) {
        val dialogViewBinding = DialogTagBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()
        val translatedCategory = Util.TAG_TRANSLATION_MAP[category]

        dialogViewBinding.categoryTextView.text = translatedCategory

        dialogViewBinding.valueTextView.text = value

        dialogViewBinding.excludeButton.setOnClickListener {
            addFilterOutTag(category, value)
        }

        dialogViewBinding.searchButton.setOnClickListener {
            SearchActivity.startTmpSearch(
                this@ItemProfileActivity,
                sourceOrdinal = item.source.ordinal,
                tags = mapOf(Pair(category, listOf(value))),
                categories = Category.ECategories.toList()
            )
        }

        dialog.show()
    }

    private fun showInfoDialog () {
        val dialogViewBinding = DialogItemInfoBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogViewBinding.root).create()

        dialogViewBinding.uploaderTextView.text = item.uploader ?: getString(R.string.noName)

        dialogViewBinding.urlTextView.text = item.url

        dialogViewBinding.title.text = item.title

        if (item.subTitle.isEmpty()) {
            dialogViewBinding.subtitle.visibility = View.GONE
        } else {
            dialogViewBinding.subtitle.text = item.subTitle
        }

        dialog.show()
    }

    private fun addFilterOutTag (tagCategory: String, tagValue: String) =
        EditExcludeTagDialog(this, layoutInflater).show(
            categories = Category.entries,
            tags = mapOf(tagCategory to listOf(tagValue))
        ) { recordToSave ->
            toggleProgressBar(true)
            CoroutineScope(Dispatchers.IO).launch {
                ExcludeTagRepository(baseContext).addExcludeTag(
                    tags = recordToSave.tags,
                    categories = recordToSave.categories.toList()
                )
                withContext(Dispatchers.Main) {
                    toggleProgressBar(false)
                    ConfirmDialog(this@ItemProfileActivity, layoutInflater).show(
                        "已濾除標籤，返回搜尋？",
                        positiveCallback = {
                            this@ItemProfileActivity.finish()
                        }
                    )
                }
            }
        }

    /**
     * modify buttons based on the current stored state
     */
    private fun refreshButtons () {
        if (item.isTmp) {
            rootBinding.saveButton.visibility = View.VISIBLE
            rootBinding.localSettingButton.visibility = View.GONE
            rootBinding.deleteButton.visibility = View.GONE
            rootBinding.saveAsButton.visibility = View.GONE
        } else {
            rootBinding.saveButton.visibility = View.GONE
            rootBinding.localSettingButton.visibility = View.VISIBLE
            rootBinding.deleteButton.visibility = View.VISIBLE
            rootBinding.saveAsButton.visibility = View.VISIBLE
        }
    }

    /**
     * only for profile of stored item
     */
    private fun refreshCoverPage () {
        if (!item.isTmp) {
            val file = File(
                Item.getFolder(baseContext, item.id),
                item.coverPage.toString()
            )
            Glide.with(baseContext)
                .load(file)
                .signature(MediaStoreSignature("", file.lastModified(), 0))
                .run { item.coverCropPosition?.let { transform(CoverCrop(it)) } ?: this }
                .into(rootBinding.coverImageView)
        }
    }

    @Transaction
    private suspend fun saveBook () {
        rootBinding.progress.textView.text = getString(R.string.n_percent, 0)
        toggleProgressBar(true)

        val fetcher = getOnlinePictureFetcher()

        // download cover page if not exist
        if (!File(fetcher.bookFolder, "0").exists()) {
            val success = withContext(Dispatchers.IO) {
                try {
                    fetcher.savePicture(0) { total, downloaded ->
                        CoroutineScope(Dispatchers.Main).launch {
                            rootBinding.progress.textView.text = getString(
                                R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                            )
                        }
                    }
                    true
                } catch (e: Exception) {
                    println(e.stackTraceToString())
                    false
                }
            }
            if (!success) {
                Toast.makeText(baseContext, "儲存失敗，再試一次", Toast.LENGTH_SHORT).show()
                toggleProgressBar(false)
                return
            }
        }

        val internalId = bookRepo.addBook(
            id = item.bookData!!.id,
            url = item.url,
            category = item.category,
            title = item.title,
            subtitle = item.subTitle,
            pageNum = item.bookData!!.pageNum,
            tags = item.tags,
            source = item.source,
            uploader = item.uploader
        )

        // create book folder
        Item.getFolder(baseContext, internalId).also {
            if (!it.exists()) {
                it.mkdirs()
            }
            // move picture from tmp folder to book folder
            for (tmpFile in fetcher.bookFolder.listFiles()!!) {
                if (tmpFile.extension != "txt") {
                    tmpFile.copyTo(File(it, tmpFile.name))
                }
            }
        }

        ProfileItem.clearTmp()

        // update ui
        toggleProgressBar(false)
        item = ProfileItem.build(baseContext, internalId)
        refreshButtons()
        ConfirmDialog(this, layoutInflater).show(
            "已加入到收藏，返回收藏？",
            positiveCallback = {
                startActivity(
                    Intent(baseContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
        )
    }

    @Transaction
    private suspend fun saveVideo () {
        rootBinding.progress.textView.text = getString(R.string.n_percent, 0)
        toggleProgressBar(true)

        val videoData = item.videoData!!

        val internalId = videRepo.addVideo(
            id = videoData.id,
            videoUrl = videoData.videoUrl,
            category = item.category,
            source = item.source,
            tags = item.tags,
            uploader = item.uploader!!
        )

        Item.getFolder(baseContext, internalId).also { folder ->
            if (!folder.exists()) {
                folder.mkdirs()
            }

            val okHttpHelper = OkHttpHelper { total, downloaded ->
                CoroutineScope(Dispatchers.Main).launch {
                    rootBinding.progress.textView.text = getString(
                        R.string.n_percent, floor(downloaded.toDouble() / total * 100).toInt()
                    )
                }
            }

            val success = withContext(Dispatchers.IO) {
                okHttpHelper.downloadImage(
                    item.coverUrl,
                    File(folder, "0")
                ).let {
                    if (!it) {
                        return@withContext false
                    }
                }

                CoroutineScope(Dispatchers.Main).launch {
                    rootBinding.progress.textView.text = getString(R.string.n_percent, 0)
                }
                okHttpHelper.curl(
                    videoData.videoUrl,
                    File(folder, "video")
                )
            }

            if (!success) {
                Toast.makeText(baseContext, "儲存失敗，再試一次", Toast.LENGTH_SHORT).show()
                toggleProgressBar(false)
                return
            }
        }

        ProfileItem.clearTmp()

        // update ui
        toggleProgressBar(false)
        item = ProfileItem.build(baseContext, internalId)
        refreshButtons()
        ConfirmDialog(this, layoutInflater).show(
            "已加入到收藏，返回收藏？",
            positiveCallback = {
                startActivity(
                    Intent(baseContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
        )
    }

    @Transaction
    private fun saveAs () {
        val newId = when (item.type) {
            ItemType.Book -> { bookRepo.saveAsBook(item.id) }
            ItemType.Video -> throw NotImplementedError()
        }

        Item.getFolder(baseContext, newId).also { newFolder ->
            if (!newFolder.exists()) {
                newFolder.mkdirs()
            }
            val originFolder = Item.getFolder(baseContext, item.id)
            for (originFile in originFolder.listFiles()!!) {
                val newFile = File(newFolder, originFile.name)
                originFile.copyTo(newFile)
            }
        }

        item = ProfileItem.build(baseContext, newId)
        ConfirmDialog(this, layoutInflater).show(
            "已另存，返回收藏？",
            positiveCallback = {
                startActivity(
                    Intent(baseContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
        )
    }

    private fun getOnlinePictureFetcher (): BasePictureFetcher {
        assert(item.type == ItemType.Book)
        return when (item.source) {
            ItemSource.E -> EPictureFetcher(baseContext,
                item.bookData!!.pageNum, item.url, item.bookData!!.id
            )
            ItemSource.Wn -> WnPictureFetcher(baseContext,
                item.bookData!!.pageNum, item.url, item.bookData!!.id
            )
            ItemSource.Ru, ItemSource.Hi -> throw IllegalStateException()
        }
    }

    private class CropContract: ActivityResultContract<Uri, PointF?>() {
        override fun createIntent(context: Context, input: Uri): Intent {
            return Intent(context, CropActivity::class.java).
            putExtra(CropActivity.EXTRA_IMAGE_URI, input)
        }
        override fun parseResult(resultCode: Int, intent: Intent?): PointF? {
            if (resultCode != RESULT_OK) {
                return null
            }
            return intent?.let {
                val x = it.getFloatExtra(CropActivity.RESULT_OFFSET_X, -1f)
                val y = it.getFloatExtra(CropActivity.RESULT_OFFSET_Y, -1f)
                assert(x != -1f && y != -1f)
                PointF(x, y)
            }
        }
    }

    /**
     * this dialog should place in this class because it is using the cropLauncher
     */
    private inner class LocalReadSettingDialog {
        private val context = this@ItemProfileActivity
        private val dialogBinding = DialogLocalReadSettingBinding.inflate(layoutInflater)
        private val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()

        fun show (item: ProfileItem) {
            when (item.type) {
                ItemType.Book -> showBook(item)
                ItemType.Video -> throw NotImplementedError()
            }
        }

        fun showBook (item: ProfileItem) {
            val skipPages = bookRepo.getBookSkipPages(item.id)
            val groupId = itemRepo.getGroupId(item.id)

            dialogBinding.groupNameEditText.setText(
                groupRepo.getGroupName(groupId)
            )

            dialogBinding.customTitleEditText.setText(
                item.customTitle ?: ""
            )

            dialogBinding.profileDialogCoverPageEditText.setText(
                (item.coverPage + 1).toString()
            )

            dialogBinding.profileDialogSkipPagesEditText.setText(skipPagesListToString(skipPages))

            dialogBinding.searchButton.setOnClickListener {
                SelectGroupDialog(context, layoutInflater).show {
                        _, name -> dialogBinding.groupNameEditText.setText(name)
                }
            }

            dialogBinding.profileDialogApplyButton.setOnClickListener {
                // group
                val groupName = dialogBinding.groupNameEditText.text.toString().trim()
                val selectedGroupId = groupName.let {
                    if (it.isEmpty()) {
                        return@let 0
                    }

                    val id = groupRepo.getGroupIdFromName(it)
                    if (id != null) {
                        return@let id
                    }

                    return@let groupRepo.createGroup(groupName)
                }
                if (selectedGroupId != groupId) {
                    groupRepo.changeGroup(item.id, groupId, selectedGroupId)
                }

                // custom title
                itemRepo.updateCustomTitle(
                    item.id,
                    dialogBinding.customTitleEditText.text.toString().trim()
                )

                // cover page
                bookRepo.setBookCoverPage(
                    item.id,
                    dialogBinding.profileDialogCoverPageEditText.text.toString().trim().let {
                        if (it.isEmpty()) {
                            Toast.makeText(context, "封面頁不能為空", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        try {
                            it.toInt()
                        } catch (e: NumberFormatException) {
                            Toast.makeText(context, "封面頁輸入格式錯誤", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    } - 1
                )

                // skip page
                updateSkipPages(
                    item,
                    dialogBinding.profileDialogSkipPagesEditText.text.toString().trim(),
                    skipPages
                )

                this@ItemProfileActivity.item = ProfileItem.build(baseContext, item.id)
                refreshCoverPage()
                rootBinding.titleTextView.text = this@ItemProfileActivity.item.run { customTitle ?: title }

                dialog.dismiss()
            }

            dialogBinding.cropCoverButton.setOnClickListener {
                cropLauncher.launch(
                    File(Item.getFolder(baseContext, item.id), item.coverPage.toString()).toUri()
                )
            }

            dialog.show()
        }

        /**
         * @param text text of the skip page editText
         */
        private fun updateSkipPages (item: ProfileItem, text: String, originSkipPages: List<Int>) {
            val coverPage = item.coverPage
            val updatedSkipPages = skipPageStringToList(text)

            if (updatedSkipPages == originSkipPages) {
                return
            }

            val newSkipPages = updatedSkipPages.minus(originSkipPages.toSet())
            if (newSkipPages.isNotEmpty()) {
                val bookFolder = Item.getFolder(baseContext, item.id)
                for (p in newSkipPages) {
                    if (p == coverPage) {
                        continue
                    }
                    File(bookFolder, p.toString()).let {
                        if (it.exists()) {
                            it.delete()
                        }
                    }
                }
            }

            runBlocking {
                bookRepo.setBookSkipPages(item.id, updatedSkipPages.sorted())
            }
        }

        private fun skipPagesListToString (skipPages: List<Int>): String {
            val tokens = mutableListOf<String>()

            var s = -1
            var p = -1
            for (page in skipPages) {
                // first page of segment
                if (s == -1) {
                    s = page
                    p = page
                    continue
                }

                // extend segment
                if (p == page - 1) {
                    p = page
                    continue
                }

                // segment end, store and start new
                if (s == p) {
                    tokens.add((s + 1).toString())
                } else {
                    tokens.add("${s + 1}-${p + 1}")
                }
                s = page
                p = page
            }

            if (s != -1) {
                // store last segment
                if (s == p) {
                    tokens.add((s + 1).toString())
                } else {
                    tokens.add("${s + 1}-${p + 1}")
                }
            }

            return tokens.joinToString(",")
        }

        private fun skipPageStringToList (text: String): List<Int> {
            if (text.trim().isEmpty()) {
                return listOf()
            }

            val res = mutableSetOf<Int>()
            for (token in text.split(',')) {
                if (token.contains("-")) {
                    // x-y
                    val dashToken = token.split("-")
                    if (dashToken.size != 2) {
                        println("[${this::class.simpleName}.${this::skipPageStringToList.name}] '$token' unexpected dash format")
                        continue
                    }

                    val x = pageStringToPageIndex(dashToken[0].trim())
                    val y = pageStringToPageIndex(dashToken[1].trim())
                    if (x == null || y == null || x >= y) {
                        println("[${this::class.simpleName}.${this::skipPageStringToList.name}] invalid range ${dashToken[0]}-${dashToken[1]}")
                        continue
                    }

                    for (p in x..y) {
                        res.add(p)
                    }
                } else {
                    // other
                    pageStringToPageIndex(token.trim())?.let { res.add(it) }
                }
            }
            return res.sorted()
        }

        private fun pageStringToPageIndex (s: String): Int? =
            try {
                (s.toInt() - 1).let { if (it >= 0) it else null }
            } catch (e: NumberFormatException) {
                println("[${this::class.simpleName}.${this::pageStringToPageIndex.name}] '$s' cannot convert into int")
                null
            }
    }
}