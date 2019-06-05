package com.example.mediastudyproject.activity

import android.graphics.ImageFormat
import android.hardware.Camera
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.AudioConfig
import com.example.mediastudyproject.WindowDegree
import com.example.mediastudyproject.activity.Camera1PreviewActivity.Companion.isRecording
import com.example.mediastudyproject.threads.MuxThread
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList
import kotlin.concurrent.thread


class Camera1PreviewActivity : AppCompatActivity() {

    private var prevOutputPTSUs = 0L
    private lateinit var surfaceView: SurfaceView
    private var mCamera: Camera? = null
    private var isSurfaceAvailiable = false
    private var holder: SurfaceHolder? = null
    private var mediaRecorder: MediaRecorder? = null
    //    private var focusHandler = FocusHandler(this@Camera1PreviewActivity)
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private lateinit var audioMediaFormat: MediaFormat
    private lateinit var videoMediaFormat: MediaFormat
    private var appropriatePreviewSizes: Camera.Size? = null
    private var audioRecorder: AudioRecord? = null
    private lateinit var mediaMuxer: MediaMuxer
    //AAC数据流  1024为一帧大小  读取时候按照一帧读取，添加时间戳
    val SAMPLES_PER_FRAME = 1024
    val FRAMES_PER_BUFFER = 30

    private val minSize = AudioRecord.getMinBufferSize(
        AudioConfig.SAMPLE_RATE, AudioConfig.CHANNEL_CONFIG,
        AudioConfig.AUDIO_FORMAT
    )


    private val threadPool = Executors.newSingleThreadExecutor()
    private var muxerThread: MuxThread? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.mediastudyproject.R.layout.activity_camera1_preview)
        initView()
    }

    companion object {
        var audioAddTrack = -1
        var videoAddTrack = -1
        @Volatile
        var isRecording = false
        //等待时间会影响音视频播放效果     设置过大 出现音频没声音和跳帧严重问题
        const val TIME_OUT_US = 10000L
        @Volatile
        private var audioExit = false
        @Volatile
        private var videoExit = false
    }

    /**
     * 初始化视频和音频需要的MediaFormat
     * 音频必须至少4个参数
     * 视频必须至少7个
     *
     * 由于用到PreviewSize参数，需要相机初始化后调用
     */
    private fun initAudioFormat() {

        audioMediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AudioConfig.SAMPLE_RATE,
            1
        )
        audioMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)


        audioMediaFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        //配置最大输入大小
        audioMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minSize * 2)


    }

    private fun initVideoFormat() {
        videoMediaFormat =
            MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                appropriatePreviewSizes!!.width,
                appropriatePreviewSizes!!.height
            )
        //设置颜色类型  5.0新加的颜色格式
        videoMediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        //设置帧率
        videoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        //设置比特率
        videoMediaFormat.setInteger(
            MediaFormat.KEY_BIT_RATE,
            appropriatePreviewSizes!!.width * appropriatePreviewSizes!!.height * 5
        )
        //设置每秒关键帧间隔
        videoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
    }


    /**
     * 准备初始化AudioRecord
     */
    private fun prepareAudioRecord() {
        initAudioFormat()

        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)

        audioCodec!!.configure(audioMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioCodec!!.start()

//        var buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER
//        if (buffer_size < minSize)
//            buffer_size = (minSize / SAMPLES_PER_FRAME + 1) * SAMPLES_PER_FRAME * 2
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG, AudioConfig.AUDIO_FORMAT, minSize
        )


        if (audioRecorder!!.state == AudioRecord.STATE_INITIALIZED) {

            audioRecorder?.run {
                startRecording()

                val byteArray = ByteArray(SAMPLES_PER_FRAME)
                var read = read(byteArray, 0, SAMPLES_PER_FRAME)
                while (read > 0 && isRecording) {
                    Log.i("camera1", "读取到的音频 $read")

                    encodeAudio(byteArray, read, getPTSUs())
                    read = read(byteArray, 0, SAMPLES_PER_FRAME)

                }

                audioRecorder!!.release()
                //发送EOS编码结束信息
                encodeAudio(ByteArray(0), 0, getPTSUs())
                Log.i("camera1", "音频释放")
                audioCodec!!.release()
            }
        }
    }

    inner class AudioThread : Thread() {
        private val audioData = LinkedBlockingQueue<ByteArray>()


        fun addVideoData(byteArray: ByteArray) {
            audioData.offer(byteArray)
        }

        override fun run() {
            super.run()
            prepareAudioRecord()
        }
    }


    /**
     * 添加ADTS头，如果要与视频流合并就不用添加，单独AAC文件就需要添加，否则无法正常播放
     */
    fun addADTStoPacket(sampleRateType: Int, packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC
        val chanCfg = 1 // CPE

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = ((profile - 1 shl 6) + (sampleRateType shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = ((chanCfg and 3 shl 6) + (packetLen shr 11)).toByte()
        packet[4] = (packetLen and 0x7FF shr 3).toByte()
        packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    /***
     * @param 音频数据个数
     */
    private fun encodeAudio(audioArray: ByteArray?, read: Int, timeStamp: Long) {
        val index = audioCodec!!.dequeueInputBuffer(TIME_OUT_US)
        val audioInputBuffers = audioCodec!!.inputBuffers

        if (index >= 0) {
            val byteBuffer = audioInputBuffers[index]
            byteBuffer.clear()
            byteBuffer.put(audioArray, 0, read)
            if (read != 0) {
                audioCodec!!.queueInputBuffer(index, 0, read, timeStamp, 0)
            } else {
                audioCodec!!.queueInputBuffer(
                    index,
                    0,
                    read,
                    timeStamp,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )

            }


            val bufferInfo = MediaCodec.BufferInfo()
            Log.i("camera1", "编码audio  $index 写入buffer ${audioArray?.size}")
            var dequeueIndex = audioCodec!!.dequeueOutputBuffer(bufferInfo, TIME_OUT_US)
            if (dequeueIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (MuxThread.audioMediaFormat == null) {
                    MuxThread.audioMediaFormat = audioCodec!!.outputFormat
                }
            }
            var audioOutputBuffers = audioCodec!!.outputBuffers
            if (dequeueIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                audioOutputBuffers = audioCodec!!.outputBuffers
            }
            while (dequeueIndex >= 0) {
                val outputBuffer = audioOutputBuffers[dequeueIndex]
                Log.i(
                    "camera1",
                    "编码后audio $dequeueIndex buffer.size ${bufferInfo.size} buff.position ${outputBuffer.position()}"
                )
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    Log.i("camera1","音频时间戳  ${bufferInfo.presentationTimeUs /1000}")
//                    bufferInfo.presentationTimeUs = getPTSUs()

                    val byteArray = ByteArray(bufferInfo.size+7)
                    outputBuffer.get(byteArray,7,bufferInfo.size)
                    addADTStoPacket(0x04,byteArray,bufferInfo.size+7)
                    outputBuffer.clear()
                    val headBuffer = ByteBuffer.allocate(byteArray.size)
                    headBuffer.put(byteArray)
                    muxerThread?.addAudioData(outputBuffer, bufferInfo)
//                    prevOutputPTSUs = bufferInfo.presentationTimeUs

                }

                audioCodec!!.releaseOutputBuffer(dequeueIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    dequeueIndex = audioCodec!!.dequeueOutputBuffer(bufferInfo, TIME_OUT_US)
                } else {
                    break
                }
            }
        }

    }

    private fun encodeVideo(data: ByteArray, isFinish: Boolean) {
        val videoArray = ByteArray(data.size)
        if (!isFinish) {
            NV21toI420SemiPlanar(
                data,
                videoArray,
                appropriatePreviewSizes!!.width,
                appropriatePreviewSizes!!.height
            )
        }
        val videoInputBuffers = videoCodec!!.inputBuffers
        var videoOutputBuffers = videoCodec!!.outputBuffers
        val index = videoCodec!!.dequeueInputBuffer(TIME_OUT_US)

        if (index >= 0) {
            val byteBuffer = videoInputBuffers[index]
            byteBuffer.clear()
            byteBuffer.put(videoArray)
            if (!isFinish) {
                videoCodec!!.queueInputBuffer(index, 0, videoArray.size, System.nanoTime()/1000, 0)
            } else {
                videoCodec!!.queueInputBuffer(
                    index,
                    0,
                    0,
                    System.nanoTime()/1000,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )

            }
            val bufferInfo = MediaCodec.BufferInfo()
            Log.i("camera1", "编码video  $index 写入buffer ${videoArray?.size}")

            var dequeueIndex = videoCodec!!.dequeueOutputBuffer(bufferInfo, TIME_OUT_US)
            if (dequeueIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (MuxThread.videoMediaFormat == null)
                    MuxThread.videoMediaFormat = videoCodec!!.outputFormat
            }

            if (dequeueIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                videoOutputBuffers = videoCodec!!.outputBuffers
            }

            while (dequeueIndex >= 0) {
                val outputBuffer = videoOutputBuffers[dequeueIndex]
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    muxerThread?.addVideoData(outputBuffer, bufferInfo)
                }
                Log.i(
                    "camera1",
                    "编码后video $dequeueIndex buffer.size ${bufferInfo.size} buff.position ${outputBuffer.position()}"
                )
                videoCodec!!.releaseOutputBuffer(dequeueIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    dequeueIndex = videoCodec!!.dequeueOutputBuffer(bufferInfo, TIME_OUT_US)
                } else {
                    break
                }
            }
        }
    }

    private fun NV21toI420SemiPlanar(
        nv21bytes: ByteArray,
        i420bytes: ByteArray,
        width: Int,
        height: Int
    ) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height)
        var i = width * height
        while (i < nv21bytes.size) {
            i420bytes[i] = nv21bytes[i + 1]
            i420bytes[i + 1] = nv21bytes[i]
            i += 2
        }
    }

    private fun getPTSUs(): Long {

        var result = System.nanoTime() / 1000L
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = prevOutputPTSUs - result + result
        return result
    }

    private fun initView() {
        surfaceView = findViewById(com.example.mediastudyproject.R.id.surface_view)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback2 {
            override fun surfaceRedrawNeeded(holder: SurfaceHolder?) {
            }

            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                isSurfaceAvailiable = true
                this@Camera1PreviewActivity.holder = holder
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                isSurfaceAvailiable = false
                mCamera?.stopPreview()
                //这里要把之前设置的预览回调取消，不然关闭app，camera释放了，但是还在回调，会报异常
                mCamera?.setPreviewCallback(null)
                mCamera?.release()
                mCamera = null
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                isSurfaceAvailiable = true
                this@Camera1PreviewActivity.holder = holder
                thread {
                    openCamera(Camera.CameraInfo.CAMERA_FACING_BACK)
                }
            }
        })
    }

    fun switch2Back(v: View) {
        if (isSurfaceAvailiable) {
            mCamera?.run {
                stopPreview()
                release()
                openCamera(Camera.CameraInfo.CAMERA_FACING_BACK)
            }
        }
    }

    fun switch2Front(v: View) {
        if (isSurfaceAvailiable) {

            mCamera?.run {
                stopPreview()
                release()
                openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT)
            }
        }
    }

    fun takePicture(v: View) {
        mCamera?.takePicture(null, null, Camera.PictureCallback { data, camera ->
        })
    }

    fun deleteOrigin(v: View) {
        val file = File(filesDir, "muxer.mp4")
        if (file.exists()) {
            val size = file.length()
            val delete = file.delete()
            Toast.makeText(this, "删除文件muxer 大小 $size $delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareVideoRecorder() {
//        mediaRecorder = MediaRecorder()
//        mCamera?.run {
//            //step 1: 取消camera锁定
//            unlock()
//        }
//
//        mediaRecorder?.run {
//            //step 2.设置camera
//            setCamera(this@Camera1PreviewActivity.mCamera)
//            //step 3. 设置音视频资源
//            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
//            setVideoSource(MediaRecorder.VideoSource.CAMERA)
//
////            setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))
//
//            //step 4. 设置输出文件
//            val file = File(filesDir, "newRecord1.mp4")
//            if (!file.exists()) {
//                file.createNewFile()
//            }
//            setOutputFile(file.path)
//
//            //step 5. 设置预览输出
//            setOrientationHint(WindowDegree.getDegree(this@Camera1PreviewActivity))
////            setPreviewDisplay(holder?.surface)
//            //step 6. 设置输出文件格式和音视频编码
////            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
////            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
////            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
////            setVideoEncodingBitRate(1920*1080*5)
//
//
//            //准备录制
//            try {
//                prepare()
//                start()
//            } catch (e: IllegalStateException) {
//                releaseMediaRecorder()
//            } catch (e: IOException) {
//                releaseMediaRecorder()
//            }
//        }
    }

    var videoThread = VideoEncodeThread()
    val audioThread = AudioThread()
    fun startRecorder(v: View) {

        isRecording = true
        audioThread.start()
        videoThread.start()
        muxerThread = MuxThread(this)
        muxerThread?.start()

    }

    fun stopRecorder(v: View) {
//        releaseMediaRecorder()

        isRecording = false
        thread {
            audioExit = true
            audioThread.join()
            videoExit = true
            videoThread.join()
            MuxThread.muxExit = true
            muxerThread?.join()
        }
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.run {
            //            stop()
            reset()
            release()
            mediaRecorder = null
            mCamera?.lock()
            mCamera = null
        }
    }

    /**
     * 初始化并打开相机
     */
    private fun openCamera(cameraId: Int) {
        mCamera = Camera.open(cameraId)
        mCamera?.run {
            setPreviewDisplay(holder)
            setDisplayOrientation(WindowDegree.getDegree(this@Camera1PreviewActivity))

            var cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, cameraInfo)
            Log.i("camera1", "相机方向 ${cameraInfo.orientation}")


            val parameters = parameters

            parameters?.run {

                //自动曝光结果给我爆一团黑，不能忍 自己设置
                exposureCompensation = maxExposureCompensation

                //自动白平衡
                autoWhiteBalanceLock = isAutoWhiteBalanceLockSupported


                //设置预览大小
                appropriatePreviewSizes = getAppropriatePreviewSizes(parameters)
                setPreviewSize(appropriatePreviewSizes?.width!!, appropriatePreviewSizes?.height!!)

                //设置对焦模式
                val supportedFocusModes = supportedFocusModes
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }
                previewFormat = ImageFormat.NV21
            }

            setPreviewCallback { data, camera ->
                if (isRecording) {
                    if (data != null) {
                        Log.i("camera1", "获取视频数据 ${data.size}")
                        Log.i("camera1", "视频线程是否为   $videoThread")
                        videoThread.addVideoData(data)
                    }
                }

            }

            startPreview()
//            val message = Message.obtain()
//            message.what = 1
//            focusHandler.sendMessage(message)
        }
    }

    class FocusHandler(context: Camera1PreviewActivity) : Handler() {
        val weakReference = WeakReference(context)
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            when (msg?.what) {
                1 -> {

                    weakReference.get()?.mCamera?.autoFocus(null)
                    val message = Message.obtain()
                    message.what = 1
                    sendMessageDelayed(message, 1000)
                }

            }
        }
    }

    private fun getAppropriatePreviewSizes(parameters: Camera.Parameters): Camera.Size? {
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        val surfaceWidth = surfaceView.width
        val surfaceHeight = surfaceView.height
        val sizeList = ArrayList<Camera.Size>()
        if (surfaceWidth < surfaceHeight) {
            //相当于竖屏模式
            for (size in supportedPreviewSizes) {
                if (size.height >= surfaceWidth && size.width >= surfaceHeight) {
                    //相机的宽高和自然方向相反
                    sizeList.add(size)
                }
            }
            if (sizeList.size > 0) {
                return Collections.min(sizeList) { o1, o2 -> o1.height * o1.width - o2.height * o2.width }
            } else
                return supportedPreviewSizes[0]
        } else {
            //相当于横屏
            for (size in supportedPreviewSizes) {
                if (size.height >= surfaceHeight && size.width >= surfaceWidth) {
                    //相机的宽高和自然方向相反
                    sizeList.add(size)
                }
            }
            if (sizeList.size > 0) {
                return Collections.min(sizeList) { o1, o2 -> o1.height * o1.width - o2.height * o2.width }
            } else
                return supportedPreviewSizes[0]
        }
    }


    override fun onDestroy() {
        super.onDestroy()

    }


    inner class VideoEncodeThread : Thread() {
        private val videoData = LinkedBlockingQueue<ByteArray>()


        fun addVideoData(byteArray: ByteArray) {
            videoData.offer(byteArray)
        }


        override fun run() {
            super.run()
            initVideoFormat()
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec!!.configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoCodec!!.start()
            while (!videoExit) {

                val poll = videoData.poll()
                if (poll != null) {
                    encodeVideo(poll, false)
                }
            }

            //发送编码结束标志
            encodeVideo(ByteArray(0), true)
            videoCodec!!.release()
            Log.i("camera1", "视频释放")
        }
    }
}
