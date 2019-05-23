package com.example.mediastudyproject

import android.media.AudioFormat

object AudioConfig {
    const val SAMPLE_RATE = 44100  //采样率
    /**
     * CHANNEL_IN_MONO 单声道   能够保证所有设备都支持
     * CHANNEL_IN_STEREO 立体声
     */
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    /**
     * 返回的音频数据格式
     */
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /**
     * 输出的音频声道
     */
    const val CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO
}