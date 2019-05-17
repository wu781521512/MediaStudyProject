package com.example.mediastudyproject.activity

import android.graphics.*
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.R
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {


    val mPaint = Paint()
    lateinit var surfaceView: SurfaceView
    var isSurfaceCanDraw = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initPaint()
        initView()
    }

    private fun initPaint() {
        with(mPaint) {
            isAntiAlias = true
            color = Color.RED
            style = Paint.Style.STROKE
        }
    }

    private fun initView() {
        surfaceView = findViewById<SurfaceView>(R.id.sv)
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        isSurfaceCanDraw = false
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        isSurfaceCanDraw = true
        thread {
            var rect = Rect(0, 0, 300, 300)
            var canvas = holder?.lockCanvas(rect)
            canvas!!.drawColor(Color.WHITE)
            canvas.drawCircle(surfaceView.width / 2f, surfaceView.height / 2f, surfaceView.width / 2f, mPaint)
            val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.tianshi)
            var mMatrix = Matrix()
            mMatrix.preScale(surfaceView.width / bitmap.width.toFloat(), surfaceView.height / bitmap.height.toFloat())
            canvas.drawBitmap(bitmap, mMatrix, null)
            holder?.unlockCanvasAndPost(canvas)
        }

    }
}
