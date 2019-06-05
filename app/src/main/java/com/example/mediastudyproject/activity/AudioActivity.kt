package com.example.mediastudyproject.activity

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.mediastudyproject.AudioConfig.AUDIO_FORMAT
import com.example.mediastudyproject.AudioConfig.CHANNEL_CONFIG
import com.example.mediastudyproject.AudioConfig.CHANNEL_OUT_CONFIG
import com.example.mediastudyproject.AudioConfig.SAMPLE_RATE
import com.example.mediastudyproject.PlayerService
import com.example.mediastudyproject.R
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.*
import kotlin.concurrent.thread

@RuntimePermissions
class AudioActivity : AppCompatActivity() {


    private val minAudioBuffSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val audioData = ByteArray(minAudioBuffSize)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var recordFile: File? = null
    @Volatile
    var readCode = 0
    private var mediaRecord: MediaRecorder? = null

    lateinit var serviceBinder: PlayerService.PlayerBinder
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null) {
                serviceBinder = service as PlayerService.PlayerBinder
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)
        bindService(Intent(this, PlayerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)

        //关闭扬声器
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
//        audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL,0,
//            AudioManager.STREAM_VOICE_CALL)

        recordFile = File(cacheDir, "decodeMp4.pcm")

        Log.i("file", recordFile!!.absolutePath)
        Log.i("file", "${recordFile!!.exists()}    ${recordFile!!.length()}")
        audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, minAudioBuffSize
        )


    }


    fun recordAudio(v: View) {

        if (audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord!!.stop()
        }
        startRecordWithPermissionCheck()
    }

    @NeedsPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startRecord() {


        thread {
            val outPut = FileOutputStream(recordFile)

            audioRecord!!.startRecording()
            isRecording = true
            var playData = ByteArray(minAudioBuffSize)

            audioTrack = AudioTrack(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_OUT_CONFIG,
                    AUDIO_FORMAT,
                    minAudioBuffSize,
                    AudioTrack.MODE_STREAM,
                    audioRecord!!.audioSessionId
            )

//            audioTrack!!.play()
            while (isRecording) {
                readCode = audioRecord!!.read(audioData, 0, minAudioBuffSize)

                if (readCode > 0) {
                    outPut.write(audioData)
                }
            }
            Log.i("file", recordFile!!.length().toString())
            outPut.close()
            writeWAVHead()
            audioRecord!!.stop()
            audioTrack!!.stop()
        }

    }

    fun playAudio(v: View) {
        if (recordFile!!.exists ()) {
            serviceBinder.startPlay(recordFile!!.absolutePath)
        }
    }

    fun writeWAVHead() {

        if (recordFile!!.exists() && recordFile!!.isFile) {
            val totalLength = recordFile!!.length() + 36
            var wavBytes = byteArrayOf(
                    'R'.toByte(),
                    'I'.toByte(),
                    'F'.toByte(),
                    'F'.toByte(),
                    (totalLength and 0xff).toByte(),
                    (totalLength shr 8 and 0xff).toByte(),
                    (totalLength shr 16 and 0xff).toByte(),
                    (totalLength shr 24 and 0xff).toByte(),
                    'W'.toByte(),
                    'A'.toByte(),
                    'V'.toByte(),
                    'E'.toByte(),
                    'f'.toByte(),
                    'm'.toByte(),
                    't'.toByte(),
                    ' '.toByte(),
                    16,
                    0,
                    0,
                    0,
                    1,
                    0,
                    1,
                    0,
                    (SAMPLE_RATE and 0xff).toByte(),
                    (SAMPLE_RATE shr 8 and 0xff).toByte(),
                    (SAMPLE_RATE shr 16 and 0xff).toByte(),
                    (SAMPLE_RATE shr 24 and 0xff).toByte(),
                    ((SAMPLE_RATE * 16 / 8) and 0xff).toByte(),
                    ((SAMPLE_RATE * 16 / 8) shr 8 and 0xff).toByte(),
                    ((SAMPLE_RATE * 16 / 8) shr 16 and 0xff).toByte(),
                    ((SAMPLE_RATE * 16 / 8) shr 24 and 0xff).toByte(),
                    4,
                    0,
                    16,
                    0,
                    'd'.toByte(),
                    'a'.toByte(),
                    't'.toByte(),
                    'a'.toByte(),
                    (recordFile!!.length() and 0xff).toByte(),
                    (recordFile!!.length() shr 8 and 0xff).toByte(),
                    (recordFile!!.length() shr 16 and 0xff).toByte(),
                    (recordFile!!.length() shr 24 and 0xff).toByte()
            )
            val randomFile = RandomAccessFile(recordFile, "rw")
            randomFile.seek(0)
            randomFile.write(wavBytes)
            randomFile.close()
        }
    }

    fun stopRecordAudio(v: View) {
        audioRecord?.stop()
        isRecording = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (audioRecord != null) {
            audioRecord!!.release()
            audioRecord = null
            isRecording = false
        }

        if (audioTrack != null) {
            audioTrack!!.release()
            audioTrack = null
        }
        unbindService(serviceConnection)
    }


    fun calc1(lin: ByteArray, off: Int, len: Int) {
        var i: Int
        var j: Int

        i = 0
        while (i < len) {
            j = lin[i + off].toInt()
            lin[i + off] = (j shr 2).toByte()
            i++
        }
    }
}



