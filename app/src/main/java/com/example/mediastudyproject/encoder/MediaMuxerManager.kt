package com.example.mediastudyproject.encoder

import android.content.Context
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import java.io.File


class MediaMuxerManager private constructor(){
    companion object{
        val instance = MediaMuxerManager
    }

    private object SingletonHolder{
        val holder = MediaMuxerManager
    }
}