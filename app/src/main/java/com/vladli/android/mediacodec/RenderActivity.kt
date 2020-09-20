package com.vladli.android.mediacodec

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.nio.ByteBuffer

/**
 * Created by vladlichonos on 6/5/15.
 */
class RenderActivity : Activity(), SurfaceHolder.Callback {
    var tag = this.javaClass.name
    var mEncoder: VideoEncoder? = null
    var mDecoder: VideoDecoder? = null
    var mSurfaceView: SurfaceView? = null

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            Log.i(tag, "${info.name} ${info.supportedTypes.joinToString(", ")}")
        }
        mSurfaceView = findViewById(R.id.surface) as SurfaceView
        mSurfaceView!!.holder.addCallback(this)
        mEncoder = MyEncoder()
        mDecoder = VideoDecoder()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // surface is fully initialized on the activity
        mDecoder!!.start()
        mEncoder!!.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mEncoder!!.stop()
        mDecoder!!.stop()
    }

    internal inner class MyEncoder : VideoEncoder(OUTPUT_WIDTH, OUTPUT_HEIGHT) {
        var mRenderer: SurfaceRenderer? = null
        var mBuffer = ByteArray(0)

        // Both of onSurfaceCreated and onSurfaceDestroyed are called from codec's thread,
        // non-UI thread
        override fun onSurfaceCreated(surface: Surface) {
            // surface is created and codec is ready to accept input (Canvas)
            mRenderer = MyRenderer(surface)
            mRenderer!!.start()
        }

        override fun onSurfaceDestroyed(surface: Surface) {
            // need to make sure to block this thread to fully complete drawing cycle
            // otherwise unpredictable exceptions will be thrown (aka IllegalStateException)
            mRenderer!!.stopAndWait()
            mRenderer = null
        }

        override fun onEncodedSample(info: MediaCodec.BufferInfo, data: ByteBuffer) {
            // Here we could have just used ByteBuffer, but in real life case we might need to
            // send sample over network, etc. This requires byte[]
            if (mBuffer.size < info.size) {
                mBuffer = ByteArray(info.size)
            }
            data.position(info.offset)
            data.limit(info.offset + info.size)
            data[mBuffer, 0, info.size]
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                // this is the first and only config sample, which contains information about codec
                // like H.264, that let's configure the decoder
                mDecoder!!.configure(mSurfaceView!!.holder.surface,
                        OUTPUT_WIDTH,
                        OUTPUT_HEIGHT,
                        mBuffer,
                        0,
                        info.size)
            } else {
                // pass byte[] to decoder's queue to render asap
                mDecoder!!.decodeSample(mBuffer,
                        0,
                        info.size,
                        info.presentationTimeUs,
                        info.flags)
            }
        }
    }

    // All drawing is happening here
    // We draw on virtual surface size of 640x480
    // it will be automatically encoded into H.264 stream
    internal inner class MyRenderer(surface: Surface?) : SurfaceRenderer(surface) {
        var mPaint: TextPaint? = null
        var mTimeStart: Long = 0
        override fun start() {
            super.start()
            mTimeStart = System.currentTimeMillis()
        }

        fun formatTime(): String {
            val now = (System.currentTimeMillis() - mTimeStart).toInt()
            val minutes = now / 1000 / 60
            val seconds = now / 1000 % 60
            val millis = now % 1000
            return String.format("%02d:%02d:%03d", minutes, seconds, millis)
        }

        override fun onDraw(canvas: Canvas) {
            // non-UI thread
            canvas.drawColor(Color.BLACK)

            // setting some text paint
            if (mPaint == null) {
                mPaint = TextPaint()
                mPaint!!.isAntiAlias = true
                mPaint!!.color = Color.WHITE
                mPaint!!.textSize = 30f * resources.configuration.fontScale
                mPaint!!.textAlign = Paint.Align.CENTER
            }
            canvas.drawText(formatTime(),
                    OUTPUT_WIDTH / 2.toFloat(),
                    OUTPUT_HEIGHT / 2.toFloat(),
                    mPaint)
        }
    }

    companion object {
        // video output dimension
        const val OUTPUT_WIDTH = 640
        const val OUTPUT_HEIGHT = 480
    }
}