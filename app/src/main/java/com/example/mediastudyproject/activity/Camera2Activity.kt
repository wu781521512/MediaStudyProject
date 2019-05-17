package com.example.mediastudyproject.activity

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.ImageUtil
import com.example.mediastudyproject.R
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.ByteArrayOutputStream
import java.util.*

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@RuntimePermissions
class Camera2Activity : AppCompatActivity(), SurfaceHolder.Callback {


    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        if (captureSession != null) {
            captureSession?.stopRepeating()
            captureSession = null
        }

        if (mCameraDevice != null) {
            mCameraDevice?.close()
            mCameraDevice = null
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
//        initCamera()
        cameraCharacteristics = cameraManager.getCameraCharacteristics("1")
        val previewSize =
            getOptimalSize(cameraCharacteristics!!, SurfaceHolder::class.java, surfaceView.width, surfaceView.height)
//        holder?.setFixedSize()
        openCameraWithPermissionCheck(holder)
    }


    private val cameraThread = HandlerThread("cameraThread")
    lateinit var cameraHandler: Handler
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    lateinit var surfaceView: SurfaceView

    private var captureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private var imageView: ImageView? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var imageReader: ImageReader? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        cameraThread.start()
        surfaceView = findViewById(R.id.sv)
        imageView = findViewById(R.id.iv)
        cameraHandler = Handler(cameraThread.looper)
        surfaceView.holder.addCallback(this)
//        initCamera()
    }


    /**
     * 获取相机管理器
     */
    private fun initCamera() {
        //1.获取CameraManager

        //遍历摄像头，找到要的编号
        for (cameraId in cameraManager.cameraIdList) {
            Log.i("camera", "相机ID  $cameraId")
            val characteristic = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristic.get(CameraCharacteristics.LENS_FACING)

            Log.i("camera", "摄像头类型  $facing")
            Log.i("camera", "摄像头支持级别  ${characteristic[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]}")

            val map = characteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            Log.i("camera", "是否支持RGB_565  ${map?.outputFormats}")


            val supportSizes = map?.getOutputSizes(SurfaceHolder::class.java)
            for (size in supportSizes!!) {
                Log.i("camera", "输出宽  ${size.width}")
                Log.i("camera", "输出高  ${size.height}")
            }

        }
    }

    /**
     * 打开摄像头
     */
    @SuppressLint("MissingPermission")
    @NeedsPermission(android.Manifest.permission.CAMERA)
    fun openCamera(holder: SurfaceHolder?) {

        //创建图像数据接受对象
        if (cameraCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            !!.isOutputSupportedFor(ImageFormat.YUV_420_888)
        ) {
            imageReader = ImageReader.newInstance(
                imageView!!.width, imageView!!.height, ImageFormat.YUV_420_888, 10
            )
            imageReader!!.setOnImageAvailableListener({
                val image = it.acquireNextImage()

                if (image != null) {
//                    val yPixelStride = image.planes[0].pixelStride
//                    val yRowStride = image.planes[0].rowStride
//                    val buffer = image.planes[0].buffer
//                    Log.i("camera","Y像素间距 $yPixelStride")
//                    Log.i("camera","Y行间距 $yRowStride")
//                    Log.i("camera","宽度 ${image.width}")
//                    Log.i("camera","Y容量 ${buffer.capacity()}")
//                    Log.i("camera","Y remaining ${buffer.remaining()}")
//                    val data = ByteArray(image.width*image.height/2)
//
//
//                    val uPixelStride = image.planes[1].pixelStride
//                    val uRowStride = image.planes[1].rowStride
//                    val uBuffer = image.planes[1].buffer
//                    uBuffer.get(data)
//                    Log.i("camera","U像素间距 $uPixelStride")
//                    Log.i("camera","U行间距 $uRowStride")
//                    Log.i("camera","宽度 ${image.width}")
//                    Log.i("camera","U容量 ${uBuffer.capacity()}")
//                    Log.i("camera","U remaining ${uBuffer.remaining()}")
                    val data = ImageUtil.getBytesFromImageAsType(image, ImageUtil.NV21)
                    val yuvImage = YuvImage(data, ImageFormat.NV21, image.width, image.height, null)
                    val outputStream = ByteArrayOutputStream()
                    val isyuv = yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 70, outputStream)
                    val bitmap = BitmapFactory.decodeByteArray(outputStream.toByteArray(), 0, outputStream.toByteArray().size)
//                    val yuvImage = YuvImage(data,ImageFormat.YUV_420_888,image.width,image.height,null)
//                    val outputStream = ByteArrayOutputStream()
//                    yuvImage.compressToJpeg(Rect(0,0,image.width,image.height),70,outputStream)
//                    val outArray = outputStream.toByteArray()
//                    val bitmap = BitmapFactory.decodeByteArray(outArray, 0, outArray!!.size)
                    runOnUiThread {
                        imageView?.setImageBitmap(bitmap)
                    }
                    image.close()
                }
            }, cameraHandler)
        }

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                /**
                 *
                 * 帧率要求高的(120fps以上)
                 * createConstrainedHighSpeedCaptureSession更好
                 */
                //创建会话
                camera.createCaptureSession(
                    Arrays.asList(holder?.surface, imageReader?.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigureFailed(session: CameraCaptureSession) {

                        }

                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            postCaptureRequest(camera, session, holder)
                        }
                    }, cameraHandler
                )
                mCameraDevice = camera

            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }

        }

        //打开相机过程复杂，不能在主线程开启，创建子线程Handler
        cameraManager.openCamera(
            "0",
            stateCallback, cameraHandler
        )
    }

    fun postCaptureRequest(device: CameraDevice, session: CameraCaptureSession, holder: SurfaceHolder?) {
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        request.addTarget(holder!!.surface)
        if (imageReader != null) {
            request.addTarget(imageReader!!.surface)
        }
        request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        session.setRepeatingRequest(request.build(), null, cameraHandler)

    }

    /**
     * 获取和显示区域合适的输出尺寸
     */
    fun getOptimalSize(
        cameraCharacteristics: CameraCharacteristics, classz: Class<*>,
        maxWidth: Int, maxHeight: Int
    ): Size? {
        val ration = maxWidth / maxHeight.toFloat()
        val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(classz)
        if (sizes != null) {
            for (size in sizes) {
                if (size.width / size.height.toFloat() == ration && size.width <= maxWidth && size.height <= maxHeight) {
                    return size
                }
            }
        }
        return null
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
    }
}
