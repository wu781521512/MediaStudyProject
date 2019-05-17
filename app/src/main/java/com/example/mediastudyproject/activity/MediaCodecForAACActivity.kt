package com.example.mediastudyproject.activity

import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.AudioConfig
import com.example.mediastudyproject.R
import kotlin.concurrent.thread

class MediaCodecForAACActivity : AppCompatActivity() {

    private lateinit var audioRecorder: AudioRecord
    private var minBufferSize: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_codec_for_aac)
        initAudioRecorder()
    }

    /**
     * 初始化音频采集
     */

    private fun initAudioRecorder() {
        //设置最小缓冲区大小
        minBufferSize = AudioRecord.getMinBufferSize(AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,AudioConfig.AUDIO_FORMAT)

        //创建音频记录器对象
        audioRecorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            AudioConfig.SAMPLE_RATE,AudioConfig.CHANNEL_CONFIG,AudioConfig.AUDIO_FORMAT,
            minBufferSize)

        thread {

        }
    }
}
