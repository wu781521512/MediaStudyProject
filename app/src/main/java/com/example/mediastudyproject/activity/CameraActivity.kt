package com.example.mediastudyproject.activity

import android.annotation.SuppressLint
import android.graphics.*
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.views.CircleTexureView
import com.example.mediastudyproject.R
import com.example.mediastudyproject.WindowDegree
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.ByteArrayOutputStream

@RuntimePermissions
class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback2, TextureView.SurfaceTextureListener {


    private var mCamera: Camera? = null
//    private var surfaceView: CircleSurfaceView? = null

    private var texureView: CircleTexureView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
//        surfaceView = findViewById(R.id.sv)
//        surfaceView!!.holder.addCallback(this)
        texureView = findViewById(R.id.sv)
        texureView!!.surfaceTextureListener = this
    }


    /**
     * 设置相机的属性，用什么显示，摄像头参数
     */
    @NeedsPermission(android.Manifest.permission.CAMERA)
    fun initCameraControll() {
        if (mCamera != null)
            return
        //后置
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        //自动对焦开启

        //1.使用surfaceView显示
        mCamera?.setDisplayOrientation(WindowDegree.getDegree(this))
        var parameters = mCamera?.parameters

        //自动曝光（需检测硬件是否支持）
        if (parameters!!.isAutoExposureLockSupported) {
            parameters.autoExposureLock = true
        }

        //白平衡
        if (parameters.isAutoWhiteBalanceLockSupported) {
            parameters.autoWhiteBalanceLock = true
        }
        mCamera?.parameters = parameters


        mCamera?.setPreviewCallback { data, camera ->
            var mPreviewSize = camera?.parameters?.previewSize
            Log.i("camera","预览大小  $mPreviewSize")
            var yuvImage = YuvImage(data,ImageFormat.NV21,mPreviewSize!!.width,mPreviewSize!!.height,null)
            val outPutStream = ByteArrayOutputStream()

            //压缩到流
            yuvImage.compressToJpeg(Rect(0,0,mPreviewSize.width,mPreviewSize.height),70,outPutStream)
            val dataArray = outPutStream.toByteArray()

            //生成bitmap
            var options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.RGB_565
            val bitmap = BitmapFactory.decodeByteArray(dataArray,0,dataArray.size,options)
            val rotatedBitmap = rotateBitmap(bitmap,WindowDegree.getDegree(this))
            findViewById<ImageView>(R.id.iv).setImageBitmap(rotatedBitmap)
        }
    }

    /**
     * 旋转原始图片
     */
    fun rotateBitmap(origin: Bitmap?,degree: Int): Bitmap? {
        if (origin == null)
            return null

        var mMatrix = Matrix()
        mMatrix.preRotate(degree.toFloat())
        val bitmap = Bitmap.createBitmap(origin,0,0,origin.width,origin.height,mMatrix,false)
        if (origin?.equals(bitmap)) {
            bitmap.recycle()
            return origin
        }
        origin.recycle()
        return bitmap
    }


    /**
     * surfaceHolder.callback实现函数
     */
    override fun surfaceRedrawNeeded(holder: SurfaceHolder?) {
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
//        mCamera?.stopPreview()
//        mCamera?.setPreviewDisplay(holder)
//        mCamera?.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        releaseCamera()
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        initCameraControllWithPermissionCheck()
        mCamera?.setPreviewDisplay(holder)
        mCamera?.stopPreview()
    }





    /**
     * TextureView监听
     */
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        releaseCamera()
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        initCameraControll()
        mCamera?.setPreviewTexture(surface)
        mCamera?.startPreview()
    }


    /**
     * 释放相机资源
     */
    fun releaseCamera(){
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera?.setPreviewCallback(null)
        mCamera = null
    }
}
