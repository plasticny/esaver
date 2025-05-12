package com.example.viewer.widget

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.withStyledAttributes
import com.example.viewer.R

interface IconViewBase {
    fun buildText (context: Context, attrs: AttributeSet?, defaultText: String = ""): SpannableStringBuilder =
        attrs?.let {
            context.withStyledAttributes(attrs, R.styleable.IconViewBase) {
                val icon = getString(R.styleable.IconViewBase_viewIcon)
                val text = getString(R.styleable.IconViewBase_viewText)
                if (icon != null) {
                    return@let buildSSB(context, icon, text ?: defaultText)
                } else {
                    return@let SpannableStringBuilder(text ?: defaultText)
                }
            }
            throw Exception("something went wrong")
        } ?: SpannableStringBuilder(defaultText)

    private fun buildSSB(context: Context, icon: String, text: String) =
        SpannableStringBuilder("$icon $text").apply {
            setSpan(
                CustomTypefaceSpan(ResourcesCompat.getFont(context, R.font.font_awesome)!!),
                0, icon.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE
            )
            setSpan(
                CustomTypefaceSpan(ResourcesCompat.getFont(context, R.font.roboto_regular)!!),
                icon.length, text.length + 1, Spanned.SPAN_EXCLUSIVE_INCLUSIVE
            )
        }

    private class CustomTypefaceSpan(
        private val tf: Typeface
    ): TypefaceSpan("") {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = tf
        }
        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = tf
        }
    }
}