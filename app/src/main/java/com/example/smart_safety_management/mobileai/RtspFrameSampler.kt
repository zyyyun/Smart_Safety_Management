package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.view.TextureView

/**
 * Samples a caller-owned frame bitmap from an RTSP preview source.
 *
 * Implementations that return a non-null [Bitmap] transfer ownership to the caller; the caller is
 * responsible for recycling the bitmap when the frame has been processed.
 */
interface RtspFrameSampler {
    fun sampleFrame(width: Int = 640, height: Int = 640): Bitmap?
}

class TextureViewFrameSampler(
    private val textureView: TextureView
) : RtspFrameSampler {
    override fun sampleFrame(width: Int, height: Int): Bitmap? {
        return if (textureView.isAvailable) {
            textureView.getBitmap(width, height)
        } else {
            null
        }
    }
}

interface MobileFireDetector : AutoCloseable {
    fun detectFrame(frame: Bitmap): MobileFireResult
}
