package com.example.mediastudyproject

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer

object ImageUtil {
    public val YUV420P = 0
    public val YUV420SP = 1
    public val NV21 = 2
    private const val TAG = "ImageUtil"

    /***
     * 此方法内注释以640*480为例
     * 未考虑CropRect的
     */
    fun getBytesFromImageAsType(image: Image, type: Int): ByteArray? {
        try {
            //获取源数据，如果是YUV格式的数据planes.length = 3
            //plane[i]里面的实际数据可能存在byte[].length <= capacity (缓冲区总大小)
            val planes = image.getPlanes();

            //数据有效宽度，一般的，图片width <= rowStride，这也是导致byte[].length <= capacity的原因
            // 所以我们只取width部分
            val width = image.getWidth();
            val height = image.getHeight();

            //此处用来装填最终的YUV数据，需要1.5倍的图片大小，因为Y U V 比例为 4:1:1
            val yuvBytes = ByteArray(width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8)
            //目标数组的装填到的位置
            var dstIndex = 0;

            //临时存储uv数据的
            val uBytes = ByteArray(width * height / 4)
            val vBytes = ByteArray(width * height / 4)
            var uIndex = 0;
            var vIndex = 0;

            var pixelsStride = 0
            var rowStride = 0;
            for (i in 0 until planes.size) {
                pixelsStride = planes[i].getPixelStride();
                rowStride = planes[i].getRowStride();

                val buffer = planes[i].getBuffer();

                //如果pixelsStride==2，一般的Y的buffer长度=640*480，UV的长度=640*480/2-1
                //源数据的索引，y的数据是byte中连续的，u的数据是v向左移以为生成的，两者都是偶数位为有效数据
                val bytes = ByteArray(buffer.capacity())
                buffer.get(bytes);

                var srcIndex = 0;
                if (i == 0) {
                    //直接取出来所有Y的有效区域，也可以存储成一个临时的bytes，到下一步再copy
                    for (j in 0 until height) {
                        System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                        srcIndex += rowStride;
                        dstIndex += width;
                    }
                } else if (i == 1) {
                    //根据pixelsStride取相应的数据
                    for (j in 0 until height / 2) {
                        for (k in 0 until width / 2) {
                            uBytes[uIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                } else if (i == 2) {
                    //根据pixelsStride取相应的数据
                    for (j in 0 until height / 2) {
                        for (k in 0 until width / 2) {
                            vBytes[vIndex++] = bytes[srcIndex];
                            srcIndex += pixelsStride;
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width;
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2;
                        }
                    }
                }
            }


            //根据要求的结果类型进行填充
            when (type) {
                YUV420P -> {
                    System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.size);
                    System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.size, vBytes.size);

                }

                YUV420SP ->
                    for (i in 0 until vBytes.size) {
                        yuvBytes[dstIndex++] = uBytes[i];
                        yuvBytes[dstIndex++] = vBytes[i];
                    }

                NV21 ->
                    for (i in 0 until vBytes.size) {
                        yuvBytes[dstIndex++] = vBytes[i];
                        yuvBytes[dstIndex++] = uBytes[i];
                    }
            }
            return yuvBytes;
        } catch (e: Exception) {
            if (image != null) {
                image.close();
            }
            Log.i(TAG, e.toString());
        }
        return null;
    }
}
