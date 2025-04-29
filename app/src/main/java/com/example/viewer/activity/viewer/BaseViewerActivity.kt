package com.example.viewer.activity.viewer

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.example.viewer.R
import com.example.viewer.Util
import com.example.viewer.databinding.ViewerActivityBinding
import com.example.viewer.dialog.SimpleEditTextDialog
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

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
    protected abstract suspend fun getPictureUrl (page: Int): String?

    protected lateinit var viewerActivityBinding: ViewerActivityBinding

    @Volatile
    protected var page = -1 // current page num, firstPage to lastPage
    protected var firstPage = -1 // 0 to pageNum - 1
    protected var lastPage = -1 // 0 to pageNum - 1

    private val pageSignatures = mutableMapOf<Int, ObjectKey>()

    private var showingToolBar = false

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
                    SimpleEditTextDialog(this@BaseViewerActivity, layoutInflater).show(
                        title = "跳至頁面",
                        validator = {
                            try {
                                val valid = (it.toInt() - 1) in firstPage..lastPage
                                if (!valid) {
                                    Toast.makeText(baseContext, "頁數超出範圍", Toast.LENGTH_SHORT).show()
                                }
                                valid
                            } catch (e: Exception) {
                                Toast.makeText(baseContext, "輸入錯誤", Toast.LENGTH_SHORT).show()
                                false
                            }
                        },
                        positiveCb = { toPage(it.toInt() - 1) }
                    )
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
                it.progressWrapper.visibility = ProgressBar.VISIBLE
                it.photoView.visibility = View.INVISIBLE
                it.photoView.imageAlpha = 0
            } else {
                it.progressWrapper.visibility = ProgressBar.GONE
                it.photoView.visibility = View.VISIBLE
                it.photoView.imageAlpha = 255
            }
        }
    }

    protected fun toggleLoadFailedScreen (toggle: Boolean) {
        viewerActivityBinding.let {
            if (toggle) {
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

    protected open fun loadPage () {
        val myPage = page

        viewerActivityBinding.viewerPageTextView.text = (page + 1).toString()
        toggleLoadingUi(true)
        toggleLoadFailedScreen(false)

        lifecycleScope.launch {
            val pictureUrl = getPictureUrl(page)
            if (myPage != page) {
                return@launch
            }

            if (pictureUrl != null) {
                showPicture(
                    pictureUrl, getPageSignature(page),
                    onPictureReady = { toggleLoadFailedScreen(false) },
                    onFailed = { toggleLoadFailedScreen(true) },
                    onFinished = { toggleLoadingUi(false) }
                )
            } else {
                toggleLoadingUi(false)
                toggleLoadFailedScreen(true)
            }
        }
    }

    protected fun showPicture (
        url: String,
        signature: ObjectKey,
        imageView: ImageView = viewerActivityBinding.photoView,
        onPictureReady: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) = Glide.with(baseContext)
            .asDrawable()
            .signature(signature)
            .load(url)
            .listener(object: RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                ): Boolean {
                    onPictureReady?.invoke()
                    onFinished?.invoke()
                    return false
                }
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
                ): Boolean {
                    onFailed?.invoke()
                    onFinished?.invoke()
                    return false
                }
            })
            .into(imageView)

    protected fun getPageSignature (page: Int): ObjectKey {
        if (!pageSignatures.containsKey(page)) {
            resetPageSignature(page)
        }
        return pageSignatures.getValue(page)
    }

    protected fun resetPageSignature (page: Int) {
        pageSignatures[page] = ObjectKey(System.currentTimeMillis())
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
                if (toggle) getColor(R.color.half_transparent_darkgery) else getColor(R.color.transparent_darkgery)
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