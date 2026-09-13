package com.yuku.browser.core

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.webkit.WebView
import androidx.annotation.RequiresApi

/** Render the WebView itself, including pixels obscured by browser chrome. */
@RequiresApi(Build.VERSION_CODES.Q)
internal fun captureHardwarePage(web: WebView, top: Int, factor: Int): Bitmap? {
    val width = web.width / factor
    val height = (web.height - top) / factor
    if (width < 1 || height < 1 || !web.isAttachedToWindow) return null
    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
    val renderer = HardwareRenderer()
    val node = RenderNode("Full page preview")
    val scrollbar = web.isVerticalScrollBarEnabled
    return try {
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording()
        try {
            canvas.scale(1f / factor, 1f / factor)
            // ViewGroup normally supplies this scroll translation before
            // drawing a child. A direct WebView draw must supply it itself.
            canvas.translate(-web.scrollX.toFloat(), -top.toFloat() - web.scrollY)
            web.isVerticalScrollBarEnabled = false
            web.draw(canvas)
        } finally {
            web.isVerticalScrollBarEnabled = scrollbar
            node.endRecording()
        }
        renderer.setSurface(reader.surface)
        renderer.setContentRoot(node)
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        reader.acquireLatestImage()?.use { image ->
            image.hardwareBuffer?.use { buffer ->
                val hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
                try { hardware?.copy(Bitmap.Config.ARGB_8888, false) }
                finally { hardware?.recycle() }
            }
        }
    } catch (failure: RuntimeException) {
        android.util.Log.w("HardwarePageCapture", "Page capture unavailable", failure)
        null
    } finally {
        renderer.destroy()
        node.discardDisplayList()
        reader.close()
    }
}
