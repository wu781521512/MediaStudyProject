package com.example.mediastudyproject.activity

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
    private lateinit var outputPath: String
    private var isRecording = false
    private lateinit var outputStream: FileOutputStream
    private lateinit var logOutputStream: FileWriter
    private lateinit var encodeFormat: MediaFormat
    private lateinit var mediaEncode: MediaCodec
    private lateinit var file: File
    private lateinit var logFile: File
    private var audioList: LinkedBlockingDeque<ByteArray>? = LinkedBlockingDeque()
    private lateinit var bufferedOutputStream: BufferedOutputStream
    private lateinit var logBufferedOutputStream: BufferedWriter
    private val maxByteArray = ByteArray(1024 * 1024 * 5)
    private var offset: Int = 0
    var isEndTip = false


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.mediastudyproject.R.layout.activity_media_codec_for_aac)
        file = File(filesDir, "record.aac")


        if (!logFile.exists()) {
            val createSuccess = logFile.createNewFile()
        }
        logOutputStream = FileWriter(logFile, true)
        logBufferedOutputStream = BufferedWriter(logOutputStream, 1024)

        if (!file.exists())
            file.createNewFile()
        if (file.isDirectory) {

        } else {
            outputStream = FileOutputStream(file, true)
            bufferedOutputStream = BufferedOutputStream(outputStream, 4096)
        }
        initAudioRecorder()
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
     * 开始录制声音
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
                        logBufferedOutputStream.write("读取音频数据\n")
                        var readCode = audioRecorder.read(outputArray, 0, minBufferSize)
                        logBufferedOutputStream.write("读取音频数据个数 $readCode \n")
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
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AudioConfig.SAMPLE_RATE, 1)
            //配置比特率
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            //这是什么？
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
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

                    addADTStoPacket(AudioConfig.SAMPLE_RATE, outData, outPacketSize)

                    outputBuffer.get(outData, 7, outBitsSize)

                    outputBuffer.position(info.offset)


                    logBufferedOutputStream.write("output 数据大小 ${info.size} \n")
                    System.arraycopy(outData, 0, maxByteArray, offset, outData.size)
                    offset += outData.size
                    logBufferedOutputStream.write("offset $offset \n")

                    bufferedOutputStream.write(outData)
                    bufferedOutputStream.flush()
////                    outputStream.flush()
//                    bufferedOutputStream.flush()
                    outputBuffer.clear()
                    codec.releaseOutputBuffer(index, false)
                }

                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

                    val inputBuffer = codec.getInputBuffer(index)

                    val pop = audioList?.poll()
                    logBufferedOutputStream.write("Input 获取pop $pop \n")
                    if (pop != null && pop.size >= 2 && (pop[0] == (-777).toByte() && pop[1] == (-888).toByte())) {
                        //结束标志
                        isEndTip = true
                    }
                    if (pop != null && !isEndTip) {
                        logBufferedOutputStream.write("Input 正常pop写入个数 ${pop.size} \n")
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
                        logBufferedOutputStream.write("Input 遇到结尾数据 \n")
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


    fun startWrite(v: View) {
//        try {
//            bufferedOutputStream.write(maxByteArray, 0, offset)
//            bufferedOutputStream.flush()
//        } catch (e: IOException) {
//            val message = e.message
//            val small = message
//        } finally {
            bufferedOutputStream.close()
            outputStream.close()
//        }
        if (file.exists()) {
            Toast.makeText(this, "写完了 文件大小 ${file.length()}", Toast.LENGTH_SHORT).show()
        }
    }


    private var mediaPlayer: MediaPlayer? = null


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

    fun deleteLog(v: View) {
        if (logFile.exists()) {
            val success = logFile.delete()
            Toast.makeText(this, "删除Log文件 $success", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检测设备是否支持目标压缩格式
     */
    private fun isSupprotAAC(): Boolean {
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
        if (logOutputStream != null) {
            logOutputStream.close()
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

}
