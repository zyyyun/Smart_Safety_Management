package com.example.smart_safety_management.mobileai

import android.graphics.Bitmap
import android.view.TextureView

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
