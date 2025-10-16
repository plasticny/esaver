package com.example.viewer.activity.viewer

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.ImageDecoder.DecodeException
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.MediaStoreSignature
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.databinding.ViewerActivityBinding
import com.example.viewer.dialog.SimpleEditTextDialog
import kotlinx.coroutines.launch
import org.jsoup.HttpStatusException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.math.abs

abstract class BaseViewerActivity: AppCompatActivity() {
    companion object {
        const val FLIP_THRESHOLD = 220
        const val SCROLL_THRESHOLD = 50
    }

    protected abstract val enableBookmarkButton: Boolean
    protected abstract val enableJumpToButton: Boolean

    protected abstract fun onImageLongClicked(): Boolean
    protected abstract fun prevPage()
    protected abstract fun nextPage()
    protected abstract fun reloadPage()
    protected abstract suspend fun getPictureStoredUrl (page: Int): String
    protected abstract suspend fun downloadPicture (page: Int): File

    protected lateinit var viewerActivityBinding: ViewerActivityBinding

    @Volatile
    protected var page = -1 // current page num, firstPage to lastPage
    protected var firstPage = -1 // 0 to pageNum - 1
    protected var lastPage = -1 // 0 to pageNum - 1

    private var showingToolBar = false
    /**
     *  set as a drawable when a picture successfully shown,
     *  set as null when loading screen or load failed screen
     */
    private var placeHolderDrawable: Drawable? = null
    private val preloaded = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewerActivityBinding = ViewerActivityBinding.inflate(layoutInflater)

        viewerActivityBinding.photoView.setOnLongClickListener {
            onImageLongClicked()
        }
        setupChangePageOnImage()

        viewerActivityBinding.pageTextWrapper.apply {
            setOnClickListener {
                toggleToolBar()
            }
        }

        viewerActivityBinding.jumpToButton.apply {
            if (enableJumpToButton) {
                setOnClickListener {
                    SimpleEditTextDialog(this@BaseViewerActivity, layoutInflater).apply {
                        title = "跳至頁面"
                        hint = "${firstPage + 1} - ${lastPage + 1}"
                        inputType = InputType.TYPE_CLASS_NUMBER
                        validator = {
                            try {
                                val valid = (it.toInt() - 1) in firstPage..lastPage
                                if (!valid) {
                                    Toast.makeText(baseContext, "頁數超出範圍", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                valid
                            } catch (e: Exception) {
                                Toast.makeText(baseContext, "輸入錯誤", Toast.LENGTH_SHORT).show()
                                false
                            }
                        }
                        positiveCb = { toPage(it.toInt() - 1) }
                    }.show()
                }
            }
        }

        viewerActivityBinding.reloadIcon.setOnClickListener {
            reloadPage()
        }
        viewerActivityBinding.reloadTextView.setOnClickListener {
            reloadPage()
        }
        setupChangePageOnFailScreen()

        setContentView(viewerActivityBinding.root)

        loadPage()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    prevPage()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    nextPage()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (showingToolBar && ev.action == MotionEvent.ACTION_DOWN) {
                val viewRect = Rect().also {
                    viewerActivityBinding.toolbarContainer.getGlobalVisibleRect(it)
                }
                if (!viewRect.contains(ev.x.toInt(), ev.y.toInt())) {
                    toggleToolBar(false)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    protected fun toggleLoadingUi (toggle: Boolean) {
        viewerActivityBinding.let {
            if (toggle) {
                placeHolderDrawable = null
                it.progress.wrapper.visibility = ProgressBar.VISIBLE
                it.photoView.visibility = View.INVISIBLE
                it.photoView.imageAlpha = 0
            } else {
                it.progress.wrapper.visibility = ProgressBar.GONE
                it.photoView.visibility = View.VISIBLE
                it.photoView.imageAlpha = 255
            }
        }
    }

    protected fun toggleLoadFailedScreen (toggle: Boolean, msg: String = getString(R.string.fail_to_load_picture)) {
        viewerActivityBinding.let {
            if (toggle) {
                placeHolderDrawable = null
                it.photoView.setImageDrawable(null)
                it.reloadTextView.text = msg
                it.loadFailedContainer.visibility = ProgressBar.VISIBLE
                it.photoView.visibility = View.INVISIBLE
            } else {
                it.loadFailedContainer.visibility = ProgressBar.GONE
                it.photoView.visibility = View.VISIBLE
            }
        }
    }

    protected fun toPage (page: Int) {
        if (page < firstPage || page > lastPage) {
            throw Exception("page out of range")
        }
        this.page = page
        loadPage()
    }

    protected open fun loadPage (myPage: Int = this.page) {
        if (myPage == page) {
            viewerActivityBinding.viewerPageTextView.text = (myPage + 1).toString()
        }

        lifecycleScope.launch {
            try {
                val pictureUrl = try {
                    getPictureStoredUrl(myPage)
                } catch (e: FileNotFoundException) {
                    if (myPage == page) {
                        toggleLoadFailedScreen(false)
                        toggleLoadingUi(true)
                    }
                    downloadPicture(myPage).path
                }
                if (myPage == page) {
//                    viewerActivityBinding.photoView.setImageDrawable(
//                        ImageDecoder.decodeDrawable(
//                            ImageDecoder.createSource(File(pictureUrl))
//                        )
//                    )
                    val pictureFile = File(pictureUrl)
                    Glide.with(baseContext)
                        .load(pictureFile)
//                        .placeholder(placeHolderDrawable)
                        .signature(MediaStoreSignature("", pictureFile.lastModified(), 0))
                        .listener(object: RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                toggleLoadFailedScreen(true, getString(R.string.fail_to_load_picture))
                                return false
                            }
                            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
//                                placeHolderDrawable = resource
                                return false
                            }
                        })
                        .into(viewerActivityBinding.photoView)
                    toggleLoadFailedScreen(false)
                }
            } catch (e: Exception) {
                Util.log(
                    "${this@BaseViewerActivity::class.simpleName}.${this@BaseViewerActivity::loadPage}",
                    e.stackTraceToString()
                )
                if (myPage == page) {
                    toggleLoadFailedScreen(
                        true,
                        when (e) {
                            is SocketTimeoutException -> "圖片下載超時"
                            is HttpStatusException -> {
                                if (e.statusCode == 429) {
                                    Toast.makeText(baseContext, "too many request", Toast.LENGTH_SHORT).show()
                                }
                                "圖片下載失敗"
                            }
                            is ConnectException, is SocketException -> "連接失敗"
//                            is DecodeException -> "${getString(R.string.fail_to_load_picture)} (decode)"
//                            is IOException -> "${getString(R.string.fail_to_load_picture)} (io)"
                            is GlideException -> getString(R.string.fail_to_load_picture)
                            else -> throw e
                        }
                    )
                }
            } finally {
                if (myPage == page) {
                    toggleLoadingUi(false)
                }
            }
        }
    }

    protected fun preloadPage (page: Int) {
        if (preloaded.contains(page)) {
            return
        }

        lifecycleScope.launch {
            try {
                val pictureUrl = try {
                    getPictureStoredUrl(page)
                } catch (e: FileNotFoundException) {
                    downloadPicture(page).path
                }
                val pictureFile = File(pictureUrl)
                Glide.with(baseContext)
                    .load(pictureFile)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .signature(MediaStoreSignature("", pictureFile.lastModified(), 0))
                    .listener(object: RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean = false
                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            preloaded.add(page)
                            return false
                        }
                    })
                    .preload()
            } catch (e: Exception) {
                Util.log(
                    "BaseViewerActivity.preloadPage",
                    e.stackTraceToString()
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupChangePageOnImage () {
        val photoView = viewerActivityBinding.photoView
        val listener = ChangePageGestureListener(
            FLIP_THRESHOLD, SCROLL_THRESHOLD,
            prevPageCallback = {
                if (photoView.scale == 1F) {
                    prevPage()
                }
            },
            nextPageCallback = {
                if (photoView.scale == 1F) {
                    nextPage()
                }
            }
        )
        val detector = GestureDetector(this, listener)

        photoView.setOnTouchListener { v, event ->
            v.performClick()
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                listener.reset()
            }
            photoView.attacher.onTouch(v, event)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupChangePageOnFailScreen () {
        val listener = ChangePageGestureListener(
            FLIP_THRESHOLD, SCROLL_THRESHOLD,
            prevPageCallback = { prevPage() },
            nextPageCallback = { nextPage() }
        )
        val detector = GestureDetector(this, listener)

        viewerActivityBinding.loadFailedContainer.setOnTouchListener { v, event ->
            v.performClick()
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                listener.reset()
            }
            true
        }
    }

    private fun toggleToolBar (toggle: Boolean = !showingToolBar) {
        if (toggle == showingToolBar) {
            return
        }

        viewerActivityBinding.toolbarContainer.apply {
            ValueAnimator.ofArgb(
                (background as ColorDrawable).color,
                if (toggle) getColor(R.color.half_transparent_dark_gery) else getColor(R.color.transparent_dark_gery)
            ).apply {
                duration = 250
                addUpdateListener { setBackgroundColor(animatedValue as Int) }
            }.start()
        }

        viewerActivityBinding.jumpToButton.apply {
            if (!enableJumpToButton) {
                return@apply
            }

            if (toggle) {
                visibility = View.VISIBLE
            }
            ValueAnimator.ofFloat(alpha, 1 - alpha).apply {
                duration = 250
                addUpdateListener { alpha = animatedValue as Float }
                if (!toggle) {
                    addListener(object: AnimatorListener {
                        override fun onAnimationStart(animation: Animator) = Unit
                        override fun onAnimationEnd(animation: Animator) {
                            visibility = View.GONE
                        }
                        override fun onAnimationCancel(animation: Animator) = Unit
                        override fun onAnimationRepeat(animation: Animator) = Unit
                    })
                }
            }.start()
        }

        viewerActivityBinding.bookmarkButton.apply {
            if (!enableBookmarkButton) {
                return@apply
            }

            if (toggle) {
                visibility = View.VISIBLE
            }
            ValueAnimator.ofFloat(alpha, 1 - alpha).apply {
                duration = 250
                addUpdateListener { alpha = animatedValue as Float }
                if (!toggle) {
                    addListener(object: AnimatorListener {
                        override fun onAnimationStart(animation: Animator) = Unit
                        override fun onAnimationEnd(animation: Animator) {
                            visibility = View.GONE
                        }
                        override fun onAnimationCancel(animation: Animator) = Unit
                        override fun onAnimationRepeat(animation: Animator) = Unit
                    })
                }
            }.start()
        }

        showingToolBar = toggle
    }
}

private class ChangePageGestureListener (
    private val flipThreshold: Int,
    private val scrollThreshold: Int,
    private val prevPageCallback: () -> Unit,
    private val nextPageCallback: () -> Unit
): GestureDetector.SimpleOnGestureListener() {
    private var scrolledDistance = 0F
    private var lastScrollE2: MotionEvent? = null

    override fun onFling(
        e1: MotionEvent?, e2: MotionEvent,
        velocityX: Float, velocityY: Float
    ): Boolean {
        // change page on fling
        if (e1 != null && isMotionHorizontal(e1, e2)) {
            if (abs(e2.x - e1.x) <= flipThreshold) {
                changePage(e1, e2)
            }
        }
        return true
    }

    override fun onScroll(
        e1: MotionEvent?, e2: MotionEvent,
        distanceX: Float, distanceY: Float
    ): Boolean {
        // change page on scrolling
        if (e1 != null && isMotionHorizontal(e1, e2)) {
            val dx = e2.x - e1.x
            if (abs(dx) <= flipThreshold) {
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            scrolledDistance += abs(distanceX)
            if (scrolledDistance >= scrollThreshold) {
                scrolledDistance = 0F
                changePage(lastScrollE2 ?: e1, e2)
                lastScrollE2 = MotionEvent.obtain(e2)
            }
        }
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    fun reset () {
        scrolledDistance = 0F
        lastScrollE2 = null
    }

    private fun isMotionHorizontal (e1: MotionEvent, e2: MotionEvent) = abs(e2.x - e1.x) > abs(e2.y - e1.y)

    private fun changePage (e1: MotionEvent, e2: MotionEvent) {
        if (e2.x - e1.x > 0) {
            nextPageCallback()
        } else {
            prevPageCallback()
        }
    }
}