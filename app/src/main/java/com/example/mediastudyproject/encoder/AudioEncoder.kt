package com.example.mediastudyproject.encoder

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import com.example.mediastudyproject.AudioConfig
import com.example.mediastudyproject.activity.Camera1PreviewActivity
import com.example.mediastudyproject.activity.MediaCodecForAACActivity
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.min

class AudioEncoder {
    private lateinit var audioMediaCodec: MediaCodec
    private lateinit var inputBuffers: Array<ByteBuffer>
    private lateinit var outputBuffers: Array<ByteBuffer>
    private var audioRecorder: AudioRecord? = null
    fun initAudioEncoder() {
        //构建MediaFormat
        val audioMediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AudioConfig.SAMPLE_RATE,
            1
        )
        audioMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)


        //构建MediaCodec
        if (MediaCodecForAACActivity.isSupprotAAC()) {
            audioMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioMediaCodec.configure(
                audioMediaFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            audioMediaCodec.start()
            inputBuffers = audioMediaCodec.inputBuffers
            outputBuffers = audioMediaCodec.outputBuffers
        } else {
            throw Exception("不支持AAC编码")
        }
    }

    fun stopRecord() {
        audioRecorder?.run {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {

                stop()
                release()
                audioRecorder = null
            }
        }

    }


    fun startRecord() {
        audioRecorder?.let {
            it.startRecording()
        }
    }

    /**
     * 准备初始化AudioRecord
     */
    fun prepareAudioRecord() {
        val minSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE, AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER, AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG, AudioConfig.AUDIO_FORMAT, minSize
        )
    }


    /**
     * AudioRecord录入数据，准备编码，写入Muxer
     */
    fun onAudioInput(data: ByteArray) {
        val inputBufferIndex = audioMediaCodec.dequeueInputBuffer(-1)
        if (inputBufferIndex >= 0) {
            val byteBuffer = inputBuffers[inputBufferIndex]
            byteBuffer.clear()
            byteBuffer.put(data)
            if (Camera1PreviewActivity.isRecording) {
                audioMediaCodec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    data.size,
                    Date().time * 1000,
                    0
                )
            } else {
                audioMediaCodec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    data.size,
                    Date().time * 1000,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = audioMediaCodec.dequeueOutputBuffer(bufferInfo, -1)
            while (outputBufferIndex >= 0) {
                val outputByteBuffer = outputBuffers[outputBufferIndex]
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                //使用Muxer写入数据
                audioMediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = audioMediaCodec.dequeueOutputBuffer(bufferInfo, -1)
            }
        }
    }
}
