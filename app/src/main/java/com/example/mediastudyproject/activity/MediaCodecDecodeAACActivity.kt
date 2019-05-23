package com.example.mediastudyproject.activity

import android.media.*
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.AudioConfig
import com.example.mediastudyproject.R
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class MediaCodecDecodeAACActivity : AppCompatActivity() {

    private var isPlaying: Boolean = true
    private var audioTrack: AudioTrack? = null
    private var audioMediaExtractor: MediaExtractor? = null
    private var audioList: LinkedBlockingDeque<ByteArray> = LinkedBlockingDeque()
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_codec_decode_aac)
        thread {
            getAudioConfigure()
        }

        thread {

            val minSize = AudioRecord.getMinBufferSize(
                    AudioConfig.SAMPLE_RATE, AudioConfig.CHANNEL_CONFIG,
                    AudioConfig.AUDIO_FORMAT
            )
            audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    AudioConfig.SAMPLE_RATE, AudioConfig.CHANNEL_OUT_CONFIG,
                    AudioConfig.AUDIO_FORMAT, minSize, AudioTrack.MODE_STREAM
            )
            audioTrack!!.play()
            while (isPlaying) {
                val decodeData = audioList.poll()
                if (decodeData != null) {
                    audioTrack!!.write(decodeData, 0, decodeData.size)
                }
            }
        }
    }


    fun stopPlay(v: View) {
        isPlaying = false
        audioTrack?.release()
    }

    /**
     * 通过MediaExtractor获取原音频的MediaFormat和读取数据
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getAudioConfigure() {
        //配置MediaExtractor
        audioMediaExtractor = MediaExtractor()
        val path = "${filesDir.path}${File.separator}record.aac"
        audioMediaExtractor!!.setDataSource(path)
        val trackCount = audioMediaExtractor!!.trackCount

        //for循环获取音频轨  这个文件也只有音频轨道
        for (i in 0 until trackCount) {
            val format = audioMediaExtractor!!.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/", true)) {
                //找到音频轨道，选择这个轨道

                //由于我用的是之前编码生成的AAC文件，加了头，所以要配置下
                //表示这是AAC文件  而且有ADTS头部
//                format.setInteger(MediaFormat.KEY_IS_ADTS, 1)
                //配置解码头文件说明信息   2字节表示  信息组成的格式
                // AAC Profile 5bit
                //采样率 4bit
                //声道数 4bit
                //其他 3bit
                //详细表示参见另一个blog    https://blog.csdn.net/lavender1626/article/details/80431902
                //我的配置换算后是下面
//                val keyData = byteArrayOf((0x12).toByte(), (0x08).toByte())
//                val buffer = ByteBuffer.wrap(keyData)
                //设置头部解码信息
//                format.setByteBuffer("csd-0", buffer)
                audioMediaExtractor!!.selectTrack(i)

                if (adjustDecoderSupport(format)) {
                    initDecoder(format, audioMediaExtractor, audioTrack)
                }
            }
        }
    }

    /**
     * 初始化解码器
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initDecoder(format: MediaFormat, audioMediaExtractor: MediaExtractor?, audioTrack: AudioTrack?) {
        val audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioDecoder.setCallback(object : MediaCodec.Callback() {
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                //解码后数据
                val outBuffer = codec.getOutputBuffer(index)
                val byteArray = ByteArray(info.size)
                outBuffer.get(byteArray)
                audioList.offer(byteArray)
                codec.releaseOutputBuffer(index, false)
            }

            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                //获取到输入buffer，用于填充要解码的数据
                val inputBuffer = codec.getInputBuffer(index)

                //从音频轨道读出要解码的数据，填充到buffer中
                val readResult = audioMediaExtractor!!.readSampleData(inputBuffer!!, 0)
                if (readResult < 0) {
                    //读取完毕
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    val data = ByteArray(readResult)
                    inputBuffer.get(data)
                    //将buffer还给Codec
                    codec.queueInputBuffer(index, 0, readResult, audioMediaExtractor.sampleTime, 0)
                    audioMediaExtractor.advance()
                }

            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {

            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }
        })
        audioDecoder.configure(format, null, null, 0)
        audioDecoder.start()
    }

    /**
     * 5.0以上 判断是否支持该解码类型
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun adjustDecoderSupport(format: MediaFormat): Boolean {
        val mediaList = MediaCodecList(MediaCodecList.ALL_CODECS)
        return mediaList.findDecoderForFormat(format) != null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (audioTrack != null) {
            audioTrack!!.release()
        }

        if (audioMediaExtractor != null) {
            audioMediaExtractor!!.release()
        }
    }
}
