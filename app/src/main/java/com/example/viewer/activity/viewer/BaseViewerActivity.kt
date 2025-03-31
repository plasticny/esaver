package com.example.viewer.activity.viewer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.example.viewer.databinding.ViewerActivityBinding
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.abs

abstract class BaseViewerActivity: AppCompatActivity() {
    companion object {
        const val FLIP_THRESHOLD = 220
        const val SCROLL_THRESHOLD = 50
    }

    protected abstract fun onImageLongClicked(): Boolean
    protected abstract fun loadPage()
    protected abstract fun prevPage()
    protected abstract fun nextPage()

    protected lateinit var viewerActivityBinding: ViewerActivityBinding

    @Volatile
    protected var page = -1 // current page num, firstPage to lastPage
    protected var firstPage = -1 // 0 to pageNum - 1
    protected var lastPage = -1 // 0 to pageNum - 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewerActivityBinding = ViewerActivityBinding.inflate(layoutInflater).apply {
            photoView.setOnLongClickListener { onImageLongClicked() }
        }
        setupPageTextView()

        setContentView(viewerActivityBinding.root)

        loadPage()
    }

    protected fun toggleProgressBar (toggle: Boolean) {
        viewerActivityBinding.let {
            if (toggle) {
                it.viewerProgressBar.visibility = ProgressBar.VISIBLE
                it.photoView.visibility = PhotoView.INVISIBLE
            } else {
                it.viewerProgressBar.visibility = ProgressBar.GONE
                it.photoView.visibility = PhotoView.VISIBLE
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPageTextView () {
        val listener = CustomGestureListener(
            FLIP_THRESHOLD, SCROLL_THRESHOLD,
            { prevPage() }, { nextPage() }
        )
        val detector = GestureDetector(this, listener)

        viewerActivityBinding.viewerPageTextView.setOnTouchListener { v, event ->
            v.performClick()
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                listener.reset()
            }
            true
        }
    }
}

private class CustomGestureListener (
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
            if (abs(e2.x - e1.x) > flipThreshold) {
                return false
            }
            changePage(e1, e2)
            return true
        }
        return false
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
