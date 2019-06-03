package com.example.mediastudyproject.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.ImageUtil
import com.example.mediastudyproject.R
import kotlinx.android.synthetic.main.activity_h264_encode.view.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList
import kotlin.concurrent.thread


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class H264EncodeActivity : AppCompatActivity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private val subThread = HandlerThread("camera")
    private lateinit var cameraHandler: Handler
    private var cameraDevice: CameraDevice? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSession: CameraCaptureSession? = null
    var isEndTip = false
    private val originVideoDataList = LinkedBlockingQueue<ByteArray>()
    private lateinit var outputStream: FileOutputStream
    private lateinit var imageView: ImageView
    private var configBytes: ByteArray? = null
    val bundle = Bundle()
    private lateinit var outputFile: File
    private lateinit var mPreviewSize: Size
    private val TAG = "encode"
    private var startRecord = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_h264_encode)
        imageView = findViewById(R.id.iv)
        outputFile = File(filesDir, "videoRecord.264")

        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

        outputStream = FileOutputStream(outputFile, true)
        subThread.start()
        cameraHandler = Handler(subThread.looper)
        initView()

    }


    fun start2Decode(v: View) {
//        startActivity(Intent(this, H264DecodeActivity::class.java))
        if (outputFile.exists()) {
            Toast.makeText(this,"文件大小 ${outputFile.length()}",Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecord(v: View) {
        startRecord = true
        initView()
    }


    /**
     * 获取目标图像格式的支持性
     * @param characteristics 相机信息类，通过CameraManager和CameraID获取
     * @param format 图片编码格式 ImageFormat或PixelFromat
     */
    private fun isSupportTargetImageFormat(characteristics: CameraCharacteristics, format: Int): Boolean {
        val cameraConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        return cameraConfigMap.isOutputSupportedFor(format)
    }


    /**
     * 获取设备对于Camera2 API支持的程度，为了最大化使用Camera2,
     * 符合FULL及以上级别才选择使用Camera2
     * 支持级别从低到高 Legacy -> Limited —> FULL -> 3
     */
    private fun isSupportCamera2Friendly(characteristics: CameraCharacteristics): Boolean {
        return when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ->
                true
            else ->
                false
        }
    }

    /**
     *
     * 获取合适的预览尺寸，非常关键，如果手机不支持目标尺寸，会出现花屏的现象
     * @param characteristics 相机的信息类
     * @param format ImageFormat或PixelFormat中的格式
     * @param width 目标预览宽度，一般是预览控件SurfaceView或TextureView
     * @param height 目标预览高度
     */
    private fun getPreferredPreviewSize(
            characteristics: CameraCharacteristics,
            format: Int,
            width: Int,
            height: Int
    ): Size {
        val cameraConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        //根据格式，获取系统支持的该格式预览大小
        val mapSizes = cameraConfigMap.getOutputSizes(format)
        val suitableSizes = ArrayList<Size>()
        //获取支持宽高不小于目标预览大小的Size
        for (option in mapSizes) {
            if (width > height) {
                if (option.width > width && option.height > height) {
                    suitableSizes.add(option)
                }
            } else {
                if (option.width > height && option.height > width) {
                    suitableSizes.add(option)
                }
            }
        }
        //如果合适的不止一个，按照占用大小最少的，返回最小占用的Size
        if (suitableSizes.size > 0) {
            return Collections.min(suitableSizes) { lhs, rhs -> lhs.width * lhs.height - rhs.width * rhs.height }
        }
        return mapSizes[0]
    }

    /**
     * 获取可使用Camera2的API的摄像头ID
     */
    private fun initCamera(width: Int, height: Int) {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        //获取可用的camera列表
        val cameraIdList = cameraManager.cameraIdList
        for (i in 0 until cameraIdList.size) {
            //获取不同摄像头信息
            val cameraCharacteristic = cameraManager.getCameraCharacteristics(cameraIdList[i])


            //获取摄像头对Camera2的API的支持级别

            //如果支持级别为Full或以上就用camera2的API，否则就退回用Camera1的API，这里就用2的示例
            //我的设备是三星 S7，后置摄像头支持Camera2的功能
            val supportCamera2Friendly = isSupportCamera2Friendly(cameraCharacteristic)
            if (supportCamera2Friendly) {
                cameraId = cameraIdList[i]
                //获取目标图像编码是否支持
                val isSupportImageFormat = isSupportTargetImageFormat(cameraCharacteristic, ImageFormat.YUV_420_888)
                if (isSupportImageFormat) {
                    //获取对于目标图像编码支持的预览大小
                    mPreviewSize = getPreferredPreviewSize(
                            cameraCharacteristic,
                            ImageFormat.YUV_420_888,
                            width,
                            height
                    )
                } else {
                    throw Exception("设备不支持该图像编码")
                }
                break
            }
        }
        if (cameraId == null) {
            throw Exception("设备摄像头均不支持Camera2 一般就前置和后置摄像头")
        }


    }

    /**
     * 初始化TextureView，设置回调信息
     */
    private fun initView() {
        val textureView = TextureView(this)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                return true
            }

            @SuppressLint("MissingPermission")
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                bundle.putBoolean("isAvailable", true)
                bundle.putInt("width", width)
                bundle.putInt("height", height)
                surfaceTexture = surface
//                configureOrientation(width,height.toFloat(),textureView)
                //初始化相机参数，进行支持度判断，初始化mPreviewSize
                initCamera(width, height)
                openCamera()
                //这里会用到mPreviewSize
                encord2H264()


            }
        }

        findViewById<FrameLayout>(R.id.frame_preview).addView(textureView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400f, resources.displayMetrics).toInt())
    }


    @SuppressLint("MissingPermission")
    fun openCamera() {
        //创建ImageReader，用于获取每帧图像

        var imageReader: ImageReader? = null

        imageReader = ImageReader.newInstance(mPreviewSize.width, mPreviewSize.height, ImageFormat.YUV_420_888, 1)
        imageReader.setOnImageAvailableListener({
            val acquireNextImage = it.acquireNextImage()

            val bytesFromImageAsType = ImageUtil.getBytesFromImageAsType(acquireNextImage, ImageUtil.YUV420SP)
////                val yuvImage = YuvImage(
////                        bytesFromImageAsType,
////                        ImageFormat.NV21,
////                        acquireNextImage.width,
////                        acquireNextImage.height,
////                        null
////                )
////                val byteoutput = ByteArrayOutputStream()
////                yuvImage.compressToJpeg(Rect(0, 0, acquireNextImage.width, acquireNextImage.height), 70, byteoutput)
////                val byteArray = byteoutput.toByteArray()
////                runOnUiThread {
////                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size))
////                }
            originVideoDataList.offer(bytesFromImageAsType)
            //别忘记回收，不然会出错
//            acquireNextImage.close()
        }, cameraHandler)

        //surface可用了，打开相机
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                val textureSurface = Surface(surfaceTexture)
                val surfaceList = ArrayList<Surface>()
                if (null != imageReader) {
                    surfaceList.add(imageReader.surface)
                }

                surfaceList.add(textureSurface)
                //打开了摄像头  我们就要建立会话
                camera.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraSession = session
                        //会话建立成功，就要发送请求了，这里发送预览请求
                        val createCaptureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//                                createCaptureRequest.addTarget(newInstance.surface)
                        createCaptureRequest.addTarget(textureSurface)
                        if (null != imageReader) {
                            createCaptureRequest.addTarget(imageReader!!.surface)
                        }
                        //设置拍照前持续自动对焦
                        createCaptureRequest.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )
                        createCaptureRequest.set(CaptureRequest.JPEG_ORIENTATION,90)
                        //重复发送请求
                        session.setRepeatingRequest(createCaptureRequest.build(), null, cameraHandler)

                    }
                }, cameraHandler)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }
        }, cameraHandler)
    }


    /**
     * 准备数据编码成H264文件
     */
    fun encord2H264() {
        //配置MediaFormat信息

        val videoMediaFormat =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mPreviewSize.width, mPreviewSize.height)
        //设置颜色类型  5.0新加的颜色格式
        videoMediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        //设置帧率
        videoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        //设置比特率
        videoMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mPreviewSize.width * mPreviewSize.height * 5)
        //设置每秒关键帧间隔
        videoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        //创建编码器
        val videoMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoMediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                //获取outputBuffer
                val outputBuffer = codec.getOutputBuffer(index)
                val byteArray = ByteArray(info.size)
                outputBuffer?.get(byteArray)

                when {
                    info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                        configBytes = ByteArray(info.size)
                        System.arraycopy(byteArray, 0, configBytes, 0, info.size)
                    }
                    info.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME -> {
                        val keyframe = ByteArray(info.size + configBytes!!.size)
                        System.arraycopy(configBytes, 0, keyframe, 0, configBytes!!.size)
                        System.arraycopy(byteArray, 0, keyframe, configBytes!!.size, byteArray.size)

                        outputStream.write(keyframe, 0, keyframe.size)
                        outputStream.flush()
                    }
                    else -> {
                        outputStream.write(byteArray)
                        outputStream.flush()
                    }
                }

                //别忘记释放
                codec.releaseOutputBuffer(index, false)
            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                val inputBuffer = codec.getInputBuffer(index)
                val yuvData = originVideoDataList.poll()
                if (yuvData != null && yuvData.size == 1 && yuvData[0] == (-333).toByte()) {
                    //结束标志
                    isEndTip = true
                    codec.queueInputBuffer(
                            index,
                            0,
                            0,
                            surfaceTexture!!.timestamp / 1000,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
                if (yuvData != null && !isEndTip) {
                    inputBuffer.put(yuvData)
                    codec.queueInputBuffer(index, 0, yuvData.size, surfaceTexture!!.timestamp / 1000, 0)

                }

                if (null == yuvData && !isEndTip) {

                    codec.queueInputBuffer(index, 0, 0, surfaceTexture!!.timestamp / 1000, 0)
                }

            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

        })
        videoMediaCodec.configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        videoMediaCodec.start()
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private fun computePresentationTime(frameIndex: Long): Long {
        return 132 + frameIndex * 1000000 / 30
    }

    fun stopRecord(v: View) {
        if (cameraSession != null) {
            cameraSession!!.stopRepeating()
            originVideoDataList.offer(byteArrayOf((-333).toByte()))
        }
        startRecord = false
    }

    fun startDelete(v: View) {
        if (outputFile.exists()) {
            Toast.makeText(this, "删除文件  ${outputFile.length()}", Toast.LENGTH_SHORT).show()
            outputFile.delete()
        }
    }

    override fun onResume() {
        super.onResume()

        if (!bundle.isEmpty && startRecord) {
            openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }

        if (cameraSession != null) {
            cameraSession!!.close()
            cameraSession = null
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        subThread.quitSafely()
        if (outputStream != null)
            outputStream.close()
    }

    fun configureOrientation(width: Int,height: Float,textureView: TextureView){
        val mMatrix = Matrix()
        mMatrix.setPolyToPoly(floatArrayOf(0f, 0f, // top left
                width.toFloat(), 0f, // top right
                0f, height, // bottom left
                width.toFloat(), height),
                0, floatArrayOf(width.toFloat(),0f, // top left
                width.toFloat(), height, // top right
                0f,0f, // bottom left
                0f,height),0,4)
        textureView.setTransform(mMatrix)
    }
}




