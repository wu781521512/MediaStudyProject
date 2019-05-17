package com.example.mediastudyproject.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.SurfaceView

class CircleSurfaceView @JvmOverloads constructor(context: Context,
                                                  attributes: AttributeSet? = null,
                                                  defStyle: Int = 0)
    : SurfaceView(context, attributes, defStyle) {
    private val mPaint = Paint()
    private val circlePath = Path()

    init {
        background = ColorDrawable(resources.getColor(android.R.color.transparent))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circlePath.reset()
        circlePath.addCircle(w / 2.toFloat(), h / 2.toFloat(), w / 2.toFloat(), Path.Direction.CW)
    }

    override fun draw(canvas: Canvas?) {
        canvas!!.clipPath(circlePath)
        super.draw(canvas)
    }


}