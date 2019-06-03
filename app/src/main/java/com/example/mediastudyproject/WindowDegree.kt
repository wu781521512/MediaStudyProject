package com.example.mediastudyproject

import android.app.Activity
import android.view.Surface

object WindowDegree {
    fun getDegree(context: Activity): Int {
        //这个方向只针对后置摄像头，Camera类有提供方向设置角度的范例，兼顾前置和后置
        var rotation = context.windowManager.defaultDisplay.rotation
        return when(rotation) {
            Surface.ROTATION_0 ->
                return 90
            Surface.ROTATION_90 ->
                return 0
            Surface.ROTATION_180 ->
                return 270
            Surface.ROTATION_270 ->
                return 180
            else -> 0
        }
    }
}