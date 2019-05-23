package com.example.mediastudyproject.activity

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.mediastudyproject.R
import com.example.mediastudyproject.adapter.ListAdapter
import com.example.mediastudyproject.bean.Song
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread

@RuntimePermissions
class AudioAndVideoProcessActivity : AppCompatActivity() {

    private val songList = ArrayList<Song>()
    private var adapter: ListAdapter? = null
    private lateinit var listView: ListView
    private lateinit var outputVideoPath: String
    private var maxFrameSize = 0 //最大视频帧大小
    private var frameRate = 0 //视频帧率
    private var maxAudioSize = 0

    private lateinit var videoView: VideoView

    private var audioMuxerTrackIndex = -1  //音轨添加到Muxer后生成到轨道下标

    private var videoMuxerTrackIndex = -1 //视频轨添加到Muxer后生成的轨道下标
    /**
     * 视频提取器
     */
    private lateinit var mVideoMediaExtractor: MediaExtractor

    /**
     * 音频提取器
     */
    private lateinit var mAudioMediaExtractor: MediaExtractor

    /**
     * 音视频合成者
     */
    private lateinit var mMediaMuxer: MediaMuxer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_and_video_process)
        outputVideoPath = filesDir.absolutePath + "/output.mp4"
        videoView = findViewById(R.id.video_view)
        listView = findViewById(R.id.song_list)
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, id ->
            thread {
                configureVideoAndAudioExtractorWithPermissionCheck(position)
            }
        }

//        configureVideoAndAudioExtractorWithPermissionCheck(0)
        getLocalSongWithPermissionCheck()
    }

    /**
     * 配置音视频提取器
     * @param position  点击的文件下标
     */
    @RequiresApi(Build.VERSION_CODES.N)
    @NeedsPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun configureVideoAndAudioExtractor(position: Int) {
        try {
            //1.设置要提取视频的文件
//
            /**
             *
             * MediaExtractor反复提示初始化失败
             * 1.检查文件访问权限
             * 2.检查视频文件大小是否大于0！！！！！！！！！！！！！！！
             *
             */

            mMediaMuxer = MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mVideoMediaExtractor = MediaExtractor()
            mAudioMediaExtractor = MediaExtractor()

            mVideoMediaExtractor.setDataSource(songList[position].path)


//            val audioFile = File(songList[position + 1].path)
//            val audioFileInputStream = FileInputStream(audioFile)
            //设置要提取音频的文件
            mAudioMediaExtractor.setDataSource(songList[position+1].path)
//            mAudioMediaExtractor.setDataSource("/storage/emulated/0/RecordVideo/疾风传OP+12-ブルーバード.mp3")

            //获取轨道，找到视频和音频轨道
            for (i in 0 until mVideoMediaExtractor.trackCount) {
                val mediaFormat = mVideoMediaExtractor.getTrackFormat(i)
                if (mediaFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    //获取到视频轨道
                    mVideoMediaExtractor.selectTrack(i)
                    //将视频轨道赋予Muxer
                    videoMuxerTrackIndex = mMediaMuxer.addTrack(mediaFormat)
                    //获取视频帧最大值
                    maxFrameSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    //获取视频帧率
                    frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                }
            }

            for (j in 0 until mAudioMediaExtractor.trackCount) {
                val mediaFormat = mAudioMediaExtractor.getTrackFormat(j)
                if (mediaFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
//                    //获取音轨
                    mAudioMediaExtractor.selectTrack(j)
//                    //添加音轨到Muxer
                    audioMuxerTrackIndex = mMediaMuxer.addTrack(mediaFormat)
//                    //获取音频最大输入
                    maxAudioSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                }
            }

            compoundVideoAndAudioWithPermissionCheck()
        } catch (e: IOException) {
            Log.i("exception", e.message)

        } finally {
            //释放资源
            if (mMediaMuxer != null) {
                mMediaMuxer.release()
            }
            if (mVideoMediaExtractor != null) {
                mVideoMediaExtractor.release()
            }

            if (mAudioMediaExtractor != null) {
                mAudioMediaExtractor.release()
            }
        }

    }

    /**
     * 合成视频和音频
     */
    @NeedsPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun compoundVideoAndAudio() {
        mMediaMuxer.start()

        //合成视频

        if (-1 != videoMuxerTrackIndex) {

            //描述缓冲区数据信息类
            val videoBufferInfo = MediaCodec.BufferInfo()

            //缓冲区（NIO）
            val videoByteBuffer = ByteBuffer.allocate(maxFrameSize)
            while (true) {
                //获取样本大小
                val videoSampleSize = mVideoMediaExtractor.readSampleData(videoByteBuffer, 0)
                if (videoSampleSize < 0) {
                    break
                }

                //设置样本信息
                videoBufferInfo.offset = 0
                videoBufferInfo.size = videoSampleSize
                videoBufferInfo.flags = mVideoMediaExtractor.sampleFlags
                videoBufferInfo.presentationTimeUs += 1000 * 1000 / frameRate   //每次加每帧的微秒数
                mMediaMuxer.writeSampleData(videoMuxerTrackIndex, videoByteBuffer, videoBufferInfo)

                //推进到下个样本  类似快进
                mVideoMediaExtractor.advance()
            }
        }

        /**
         * 合成音频
         */

        if (-1 != audioMuxerTrackIndex) {
            val audioBufferInfo = MediaCodec.BufferInfo()
            val audioByteBuffer = ByteBuffer.allocate(maxAudioSize)

            while (true) {
                val audioSampleSize = mAudioMediaExtractor.readSampleData(audioByteBuffer, 0)
                if (audioSampleSize < 0) {
                    break
                }

                audioBufferInfo.offset = 0
                audioBufferInfo.size = audioSampleSize
                audioBufferInfo.flags = mAudioMediaExtractor.sampleFlags
                audioBufferInfo.presentationTimeUs += 1000 * 1000 / frameRate

                mMediaMuxer.writeSampleData(audioMuxerTrackIndex, audioByteBuffer, audioBufferInfo)

                mAudioMediaExtractor.advance()
            }
        }


        runOnUiThread {
            videoView.setVideoPath(outputVideoPath)
            videoView.setOnPreparedListener {
                videoView.start()
            }
        }
    }

    @NeedsPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun getLocalSong() {
        val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                null, null, null, null
        )

        if (cursor != null) {
            while (cursor.moveToNext()) {
                var song = Song(
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                )

                val file = File(song.path)
                if (file.exists() && file.length() > 0)
                    songList.add(song)

                Log.i("path", "路径  ${song.path}")
            }
            cursor.close()
        }

        adapter = ListAdapter(this, songList)
        listView.adapter = adapter

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }
}
