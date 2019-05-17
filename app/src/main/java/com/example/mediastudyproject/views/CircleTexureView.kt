package com.example.mediastudyproject.views

import android.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.TextureView

class CircleTexureView @JvmOverloads constructor(context: Context,
                                                 attributes: AttributeSet? = null,
                                                 defStyle: Int = 0)
    : TextureView(context, attributes, defStyle) {


    val mPath = Path()
    init {
        background = ColorDrawable(resources.getColor(R.color.transparent))

    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mPath.reset()
        mPath.addCircle(w/2.toFloat(),w/2.toFloat(),w/2.toFloat(),Path.Direction.CW)
    }


    override fun onDrawForeground(canvas: Canvas?) {
        canvas!!.clipPath(mPath)
        super.onDrawForeground(canvas)
    }
}