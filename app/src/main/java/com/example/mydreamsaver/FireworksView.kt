package com.example.mydreamsaver

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.WindowManager

class FireworksView(context: Context) : GLSurfaceView(context) {

    private val renderer: FireworksRenderer

    init {
        // Create an OpenGL ES 3.0 context
        setEGLContextClientVersion(3)

        // Standard 8-bit color depth (8888) with a 16-bit depth buffer
        // For basic HDR on Android TV, 8-bit buffer + Window HDR Mode usually works
        // as the OS handles the tone mapping.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        renderer = FireworksRenderer()
        setRenderer(renderer)

        // Render only when there is a change in the drawing data
        // For a particle system, we usually switch this to CONTINUOUSLY later,
        // but keep it dirty for now until we add the loop.
        renderMode = RENDERMODE_CONTINUOUSLY

        // Use WindowMetrics for API 30+ (Native 4K support) ---
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE)
                as WindowManager
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds

        // Force the surface to the full physical resolution
        // This bypasses Android TV's 1080p UI scaling
        holder.setFixedSize(bounds.width(), bounds.height())
    }
}
