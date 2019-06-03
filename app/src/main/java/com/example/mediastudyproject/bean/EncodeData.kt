package com.example.mediastudyproject.bean

import android.media.MediaCodec
import java.nio.ByteBuffer

data class EncodeData(val buffer: ByteBuffer,val bufferInfo: MediaCodec.BufferInfo) {
}