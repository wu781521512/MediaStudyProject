package com.example.mediastudyproject.activity

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)

class H264DecodeActivity : AppCompatActivity() {
    private lateinit var audioMediaExtractor: MediaExtractor
    private lateinit var fileInputStream: RandomAccessFile
    private val MIN_READ_SIZE = 1024 * 1000
    private val videoFrameDataList = LinkedBlockingQueue<ByteArray>()
    private lateinit var sourceFile: File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.mediastudyproject.R.layout.activity_h264_decode)
        sourceFile = File(filesDir, "videoRecord.264")
//        if (sourceFile.exists())
//            fileInputStream = RandomAccessFile(sourceFile,"r")

        initView()

    }

    fun startDecodeAndPlay(v: View) {
        initView()

    }
//    private fun initExtractor(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
//        //配置MediaExtractor
//        audioMediaExtractor = MediaExtractor()
//        val path = File(filesDir,"videoRecord.h264").absolutePath
//        audioMediaExtractor!!.setDataSource(path)
//        val trackCount = audioMediaExtractor!!.trackCount
//
//        //for循环获取音频轨  这个文件也只有音频轨道
//        for (i in 0 until trackCount) {
//            val format = audioMediaExtractor!!.getTrackFormat(i)
//            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/", true)) {
//
//                audioMediaExtractor!!.selectTrack(i)
//                initDecode(format,surfaceTexture,width,height)
//
//            }
//        }
//    }

    private fun initView() {
        val textureView = findViewById<TextureView>(com.example.mediastudyproject.R.id.ture_view)
        val mMatrix = Matrix()
        val width = resources.displayMetrics.widthPixels
        val height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,400f,resources.displayMetrics)
//        mMatrix.setPolyToPoly(floatArrayOf(0f, 0f, // top left
//                width.toFloat(), 0f, // top right
//                0f, height, // bottom left
//                width.toFloat(), height),
//                0, floatArrayOf(width.toFloat(),0f, // top left
//                width.toFloat(), height, // top right
//                0f,0f, // bottom left
//                0f,height),0,4)
//        textureView.setTransform(mMatrix)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                return true

            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {

                readFrameFile()

                thread {
                    initDecode(surface, width, height)
                }
            }
        }
    }

    /**
     * 读取文件中的数据，一次读取一帧视频数据
     */
    private fun readFrameFile() {
        MediaCodecThread(videoFrameDataList, sourceFile.path).start()

    }

    private fun initDecode(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        val decodeMediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val decodeMediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)




        decodeMediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                codec.releaseOutputBuffer(index, true)
            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                //获取到输入buffer，用于填充要解码的数据
                val inputBuffer = codec.getInputBuffer(index)
                val metaData = videoFrameDataList.poll()
                var endFlag = false
                if (metaData != null && metaData.size == 1 && metaData[0] == (-333).toByte()) {
                    //结束符号
                    endFlag = true
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }

                if (metaData != null && !endFlag) {
                    inputBuffer.put(metaData)
                    codec.queueInputBuffer(index, 0, metaData.size, Date().time * 1000, 0)
                }

                if (metaData == null) {
                    codec.queueInputBuffer(index, 0, 0, 0, 0)
                }
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }
        })

        decodeMediaCodec.configure(decodeMediaFormat, Surface(surfaceTexture), null, 0)
        decodeMediaCodec.start()
    }
}
