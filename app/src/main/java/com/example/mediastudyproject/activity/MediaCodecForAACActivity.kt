package com.example.mediastudyproject.activity

import android.content.Intent
import android.graphics.ImageFormat
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.AudioConfig
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.*
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread


@RuntimePermissions
class MediaCodecForAACActivity : AppCompatActivity() {

    private lateinit var audioRecorder: AudioRecord
    private var minBufferSize: Int = 0
    private var isRecording = false
    private lateinit var outputStream: FileOutputStream
    private lateinit var mediaEncode: MediaCodec
    private lateinit var file: File
    private var audioList: LinkedBlockingDeque<ByteArray>? = LinkedBlockingDeque()
    private lateinit var bufferedOutputStream: BufferedOutputStream
    private var isEndTip = false
    private var mediaPlayer: MediaPlayer? = null

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.mediastudyproject.R.layout.activity_media_codec_for_aac)
        file = File(filesDir, "record.aac")


        if (!file.exists())
            file.createNewFile()
        if (file.isDirectory) {

        } else {
            outputStream = FileOutputStream(file, true)
            bufferedOutputStream = BufferedOutputStream(outputStream, 4096)
        }
        initAudioRecorder()
    }


    fun jump2Decode(v: View) {
        startActivity(Intent(this, MediaCodecDecodeAACActivity::class.java))
    }

    /**
     * 初始化音频采集
     */

    private fun initAudioRecorder() {
        //设置最小缓冲区大小
        minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG, AudioConfig.AUDIO_FORMAT
        )

        //创建音频记录器对象
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioConfig.SAMPLE_RATE, AudioConfig.CHANNEL_CONFIG, AudioConfig.AUDIO_FORMAT,
            minBufferSize
        )

    }

    /**
     * 开始录制声音  startRecord为xml中button的onclick方法
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @NeedsPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startRecord(v: View) {

        thread(priority = android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
            isRecording = true
            try {
                if (AudioRecord.STATE_INITIALIZED == audioRecorder.state) {
                    audioRecorder.startRecording()
                    val outputArray = ByteArray(minBufferSize)
                    while (isRecording) {
                        var readCode = audioRecorder.read(outputArray, 0, minBufferSize)
                        if (readCode > 0) {
                            val realArray = ByteArray(readCode)
                            System.arraycopy(outputArray, 0, realArray, 0, readCode)
                            audioList?.offer(realArray)
                        }
                    }
                    val stopArray = byteArrayOf((-777).toByte(), (-888).toByte())
                    audioList?.offer(stopArray)
                }
            } catch (e: IOException) {

            } finally {
                if (audioRecorder != null)
                    audioRecorder.release()

            }

        }

        thread {
            //代码中引入了三方动态权限库，原方法被包装成这样  原方法是mediaCodecEncodeToAAC
            mediaCodecEncodeToAACWithPermissionCheck()
        }
    }


    /**
     *
     * 停止录制
     */
    fun stopRecord(v: View) {
        isRecording = false

        if (audioRecorder.state == AudioRecord.STATE_INITIALIZED)
            audioRecorder.stop()
    }


    fun startDelete(v: View) {

        if (file.exists()) {
            val success = file.delete()
            Toast.makeText(this, "删除文件 $success", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 压缩PCM，转换成AAC格式音频
     */

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @NeedsPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun mediaCodecEncodeToAAC() {

        val currentTime = Date().time * 1000
        try {

            val isSupprot = isSupprotAAC()
            //创建音频MediaFormat
            val encodeFormat =
                MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    AudioConfig.SAMPLE_RATE,
                    1
                )
            //配置比特率
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            encodeFormat.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            //配置最大输入大小
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 2)

//            encodeFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.ENCODING_PCM_16BIT)

            //初始化编码器
            mediaEncode = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaEncode.setCallback(object : MediaCodec.Callback() {
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Log.i("error", e.message)
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
//                    val outputBuffer = codec.getOutputBuffer(index)
//                    val headArray = ByteArray(7)
//                    val newBuffer = ByteBuffer.allocate(7 + info.size)
//                    addADTStoPacket(AudioConfig.SAMPLE_RATE, headArray, 7 + info.size)
//                    newBuffer.put(headArray)
//                    newBuffer.put(outputBuffer)
//                    newBuffer.flip()
//                    while (newBuffer!!.hasRemaining()) {
//                        fileWrite.write(newBuffer)
//                    }
//
//                    codec.releaseOutputBuffer(index, false)

                    val outBitsSize = info.size

                    val outPacketSize = outBitsSize + 7 // 7 is ADTS size

                    val outputBuffer = codec.getOutputBuffer(index)

                    outputBuffer.position(info.offset)

                    outputBuffer.limit(info.offset + outBitsSize)

                    val outData = ByteArray(outPacketSize)

                    addADTStoPacket(0x04, outData, outPacketSize)

                    outputBuffer.get(outData, 7, outBitsSize)

                    outputBuffer.position(info.offset)

                    bufferedOutputStream.write(outData)
                    bufferedOutputStream.flush()
                    outputBuffer.clear()
                    codec.releaseOutputBuffer(index, false)
                }

                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

                    val inputBuffer = codec.getInputBuffer(index)

                    val pop = audioList?.poll()
                    if (pop != null && pop.size >= 2 && (pop[0] == (-777).toByte() && pop[1] == (-888).toByte())) {
                        //结束标志
                        isEndTip = true
                    }
                    if (pop != null && !isEndTip) {
                        inputBuffer?.clear()
                        inputBuffer?.limit(pop.size)
                        inputBuffer?.put(pop, 0, pop.size)
                        codec.queueInputBuffer(
                            index,
                            0,
                            pop.size,
                            Date().time * 1000 - currentTime,
                            0
                        )
                    }

                    //如果为null就不调用queueInputBuffer  回调几次后就会导致无可用InputBuffer，从而导致MediaCodec任务结束 只能写个配置文件
                    if (pop == null && !isEndTip) {

                        codec.queueInputBuffer(
                            index,
                            0,
                            0,
                            Date().time * 1000 - currentTime,
                            0
                        )
                    }

                    if (isEndTip) {
                        codec.queueInputBuffer(
                            index,
                            0,
                            0,
                            Date().time * 1000 - currentTime,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    }
                }

            })
            mediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaEncode.start()
        } catch (e: IOException) {
            Log.e("mistake", e.message)
        } finally {
//                fileWrite?.close()
//                mediaEncode.release()
        }

    }

    fun startPlay(v: View) {
        mediaPlayer = MediaPlayer()
        mediaPlayer!!.apply {
            setDataSource(file.path)
            setOnErrorListener { mp, what, extra ->
                Log.i("media", "错误类型  $what  错误码 $extra")
                false
            }
            prepare()
            start()
        }
    }


    /**
     * 检测设备是否支持目标编码格式
     */
    companion object {
        fun isSupprotAAC(): Boolean {
            val mediaCount = MediaCodecList.getCodecCount()
            for (i in 0 until mediaCount) {
                val codecInfoAt = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfoAt.isEncoder)
                    continue
                val supportedTypes = codecInfoAt.supportedTypes
                for (j in 0 until supportedTypes.size) {
                    if (supportedTypes[j].equals(MediaFormat.MIMETYPE_AUDIO_AAC, true)) {
                        return true
                    }
                }
            }
            return false
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (audioRecorder != null) {
            audioRecorder.release()
        }

        if (mediaEncode != null) {
            mediaEncode.release()
        }
        if (outputStream != null) {
            outputStream.close()
        }

        if (bufferedOutputStream != null) {
            bufferedOutputStream.close()
        }

        if (mediaPlayer != null) {
            mediaPlayer!!.release()
            mediaPlayer = null
        }
        isRecording = false

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

}
