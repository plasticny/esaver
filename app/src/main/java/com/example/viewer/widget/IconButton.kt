package com.example.viewer.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

class IconButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): MaterialButton(context, attrs, defStyleAttr), IconViewBase {
    init {
        text = buildText(context, attrs, text.toString())
    }
}