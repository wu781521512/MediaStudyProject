package com.example.mediastudyproject

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class PlayerService : MediaBrowserService(), MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    var mediaPlayer: MediaPlayer? = null
    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnPreparedListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return PlayerBinder()
    }

    inner class PlayerBinder : Binder() {
        fun startPlay(path: String) {
            if (mediaPlayer != null) {

                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer?.apply {
                        reset()
                        setDataSource(path)
                        prepareAsync()
                    }

                } else {
                    mediaPlayer?.apply {
                        reset()
                        setDataSource(path)
                        prepareAsync()
                    }
                }
            } else {
                mediaPlayer = MediaPlayer.create(baseContext, Uri.parse(path), null)
                mediaPlayer?.start()
            }

        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        return false
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer?.apply {
                    stop()
                    release()
                }
                mediaPlayer = null
            }
        }
    }
}