package com.example.viewer.activity.viewer

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.example.viewer.databinding.ViewerActivityBinding
import kotlin.math.abs

abstract class BaseViewerActivity: AppCompatActivity() {
    companion object {
        const val FLIP_THRESHOLD = 220
        const val SCROLL_THRESHOLD = 50
    }

    protected abstract fun onImageLongClicked(): Boolean
    protected abstract fun onPageTextClicked()
    protected abstract fun loadPage()
    protected abstract fun prevPage()
    protected abstract fun nextPage()

    protected lateinit var viewerActivityBinding: ViewerActivityBinding

    @Volatile
    protected var page = -1 // current page num, firstPage to lastPage
    protected var firstPage = -1 // 0 to pageNum - 1
    protected var lastPage = -1 // 0 to pageNum - 1

    private val pageSignatures = mutableMapOf<Int, ObjectKey>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewerActivityBinding = ViewerActivityBinding.inflate(layoutInflater)

        viewerActivityBinding.photoView.setOnLongClickListener {
            onImageLongClicked()
        }
        setupChangePageOnImage()

        viewerActivityBinding.pageTextWrapper.setOnClickListener {
            onPageTextClicked()
        }

        viewerActivityBinding.reloadIcon.setOnClickListener {
            loadPage()
        }
        viewerActivityBinding.reloadTextView.setOnClickListener {
            loadPage()
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

    protected fun toggleLoadingUi (toggle: Boolean) {
        viewerActivityBinding.let {
            if (toggle) {
                it.viewerProgressBar.visibility = ProgressBar.VISIBLE
                it.photoView.visibility = View.INVISIBLE
            } else {
                it.viewerProgressBar.visibility = ProgressBar.GONE
                it.photoView.visibility = View.VISIBLE
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

    protected fun showPicture (
        url: String,
        signature: ObjectKey,
        imageView: ImageView = viewerActivityBinding.photoView,
        onPictureReady: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        Glide.with(baseContext)
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
    }

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
            prevPageCallback()
        } else {
            nextPageCallback()
        }
    }
}