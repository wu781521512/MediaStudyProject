package com.example.mediastudyproject.threads

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.widget.Toast
import com.example.mediastudyproject.activity.Camera1PreviewActivity.Companion.audioAddTrack
import com.example.mediastudyproject.activity.Camera1PreviewActivity.Companion.isRecording
import com.example.mediastudyproject.activity.Camera1PreviewActivity.Companion.videoAddTrack
import com.example.mediastudyproject.bean.EncodeData
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class MuxThread(val context: Context) : Thread() {
    private val audioData = LinkedBlockingQueue<EncodeData>()
    private val videoData = LinkedBlockingQueue<EncodeData>()

    companion object {
        var muxIsReady = false
        var audioMediaFormat: MediaFormat? = null
        var videoMediaFormat: MediaFormat? = null
        var muxExit = false
    }

    private lateinit var mediaMuxer: MediaMuxer
    fun addAudioData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        audioData.offer(EncodeData(byteBuffer, bufferInfo))
    }

    fun addVideoData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        videoData.offer(EncodeData(byteBuffer, bufferInfo))
    }


    private fun initMuxer() {

        val file = File(context.filesDir, "muxer.mp4")
        if (!file.exists()) {
            file.createNewFile()
        }
        mediaMuxer = MediaMuxer(
            file.path,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        audioAddTrack = mediaMuxer.addTrack(audioMediaFormat)
        videoAddTrack = mediaMuxer.addTrack(videoMediaFormat)
        mediaMuxer.start()
        muxIsReady = true

    }

    private fun muxerParamtersIsReady() = audioMediaFormat != null && videoMediaFormat != null


    override fun run() {
        super.run()
        while (!muxerParamtersIsReady()) {
        }
        initMuxer()
        Log.i("camera1", "当前记录状态 $isRecording ")
        while (!muxExit) {
            if (audioAddTrack != -1) {
                if (audioData.isNotEmpty()) {
                    val poll = audioData.poll()
                    Log.i("camera1", "混合写入音频 ${poll.bufferInfo.size} ")
                    mediaMuxer.writeSampleData(audioAddTrack, poll.buffer, poll.bufferInfo)

                }
            }
            if (videoAddTrack != -1) {
                if (videoData.isNotEmpty()) {
                    val poll = videoData.poll()
                    Log.i("camera1", "混合写入视频 ${poll.bufferInfo.size} ")
                    mediaMuxer.writeSampleData(videoAddTrack, poll.buffer, poll.bufferInfo)

                }
            }
        }
        mediaMuxer.stop()
        mediaMuxer.release()
        Log.i("camera1", "合成器释放")
        Log.i("camera1", "未写入音频 ${audioData.size}")
        Log.i("camera1", "未写入视频 ${videoData.size}")
    }
}