package com.example.viewer.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.viewer.R
import com.example.viewer.databinding.ActivityCropBinding
import kotlin.math.abs
import kotlin.math.round

/**
 * Pass in EXTRA_IMAGE_URI (Uri);
 * Result RESULT_OFFSET_X (float) and RESULT_OFFSET_Y (float)
 */
class CropActivity: AppCompatActivity() {
    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val RESULT_OFFSET_X = "offset_x"
        const val RESULT_OFFSET_Y = "offset_y"
    }

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var rootBinding: ActivityCropBinding

    // scaling
    private var isScaling = false
    private var minScale = -1f

    // moving
    private var lastP: PointF? = null
    private var maxAbsTranslationY = 0f
    private var maxAbsTranslationX = 0f

    private var originBounds = RectF()
    private var fileWidth = -1
    private var fileHeight = -1
    private var offsetX = -1f
    private var offsetY = -1f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scaleGestureDetector = ScaleGestureDetector(baseContext, ScaleListener())

        val imageUri = if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_IMAGE_URI)
        }
        val imageBitmap = BitmapFactory.decodeFile(imageUri!!.path).also {
            fileWidth = it.width
            fileHeight = it.height
        }
        offsetX = -1f
        offsetY = -1f

        rootBinding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(rootBinding.root)

        rootBinding.imageView.setImageBitmap(imageBitmap)

        rootBinding.touchPad.apply {
            setOnTouchListener { _, motionEvent ->
//                scaleGestureDetector.onTouchEvent(motionEvent)

                val action = motionEvent.action and MotionEvent.ACTION_MASK

                if (isScaling) {
                    return@setOnTouchListener true
                }

                if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
                    return@setOnTouchListener true
                }

                val motionP = PointF(motionEvent.x, motionEvent.y)
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastP = PointF(motionP)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lastP?.let {
                            rootBinding.imageView.apply {
                                translationX = maxOf(-maxAbsTranslationX, minOf(translationX + motionP.x - it.x, maxAbsTranslationX))
                                translationY = maxOf(-maxAbsTranslationY, minOf(translationY + motionP.y - it.y, maxAbsTranslationY))
                            }
                            it.set(motionP)
                        }
                    }
                }
                true
            }
        }

        rootBinding.highlightAreaDummy.post {
            // fit the image to highlight area
            originBounds = getImageBounds()
            val width = originBounds.width()
            val height = originBounds.height()

            minScale = maxOf(
                rootBinding.highlightAreaDummy.height / height,
                rootBinding.highlightAreaDummy.width / width
            )
            scaleImage(minScale)

            // move to crop position if set
            rootBinding.imageView.translationX = if (offsetX == -1f) { 0f } else {
                (width * minScale - rootBinding.highlightAreaDummy.width) / 2 - (offsetX / fileWidth * width * minScale)
            }
            rootBinding.imageView.translationY = if (offsetY == -1f) { 0f } else {
                (height * minScale - rootBinding.highlightAreaDummy.height) / 2 - (offsetY / fileHeight * height * minScale)
            }
        }

        rootBinding.cropButton.setOnClickListener { onCropClicked() }

        rootBinding.cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        rootBinding.imageWrapper.addView(HighLightView())
    }

    private fun getImageBounds () =
        RectF().also {
            rootBinding.imageView.imageMatrix.mapRect(
                it, RectF(rootBinding.imageView.drawable.bounds)
            )
        }

    private fun scaleImage (scale: Float) {
        rootBinding.imageView.scaleX = scale
        rootBinding.imageView.scaleY = scale

        maxAbsTranslationX = abs(rootBinding.highlightAreaDummy.width - originBounds.width() * scale) / 2f
        maxAbsTranslationY = abs(rootBinding.highlightAreaDummy.height - originBounds.height() * scale) / 2f

        rootBinding.imageView.apply {
            translationX = maxOf(-maxAbsTranslationX, minOf(translationX, maxAbsTranslationX))
            translationY = maxOf(-maxAbsTranslationY, minOf(translationY, maxAbsTranslationY))
        }
    }

    /**
     * estimate the offset on the scale of original image
     * @return offset of upper left point, normalized to the original image
     */
    private fun estimateNormalizedOffset (): Pair<Float, Float> {
        val lx = (originBounds.width() * minScale - rootBinding.highlightAreaDummy.width) / 2 - rootBinding.imageView.translationX
        val ly = (originBounds.height() * minScale - rootBinding.highlightAreaDummy.height) / 2 - rootBinding.imageView.translationY
//        x and y without normalize
//        val x = (lx * fileWidth) / (originBounds.width() * minScale)
//        val y = (ly * fileHeight) / (originBounds.height() * minScale)
        val x = lx / (originBounds.width() * minScale)
        val y = ly / (originBounds.height() * minScale)
        return Pair(round(x * 100) / 100, round(y * 100) / 100)
    }

    private fun onCropClicked () {
        val (x,y) = estimateNormalizedOffset()
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(RESULT_OFFSET_X, x)
                putExtra(RESULT_OFFSET_Y, y)
            }
        )
        finish()
    }

    private inner class ScaleListener: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var curScale = -1f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            curScale = rootBinding.imageView.scaleX
            isScaling = true
            lastP = null
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            super.onScaleEnd(detector)
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // minScale <= scale <= 5.0
            scaleImage(maxOf(minScale, minOf(curScale * detector.scaleFactor, 5.0f)))
            return super.onScale(detector)
        }
    }

    private inner class HighLightView: View(this) {
        private val dimPaint = Paint().apply {
            color = getColor(R.color.half_transparent_dark_gery)
            style = Paint.Style.FILL
        }
        private val highLightPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        init {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
            with (rootBinding.highlightAreaDummy) {
                canvas.drawRect(
                    x, y, x + width, y + height,
                    highLightPaint
                )
            }
        }
    }
}