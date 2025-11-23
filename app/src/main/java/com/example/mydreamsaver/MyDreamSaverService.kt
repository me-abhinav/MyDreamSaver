package com.example.mydreamsaver

import android.content.pm.ActivityInfo
import android.service.dreams.DreamService

class MyDreamSaverService: DreamService() {

    private lateinit var glView: FireworksView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        // NATIVE HDR SUPPORT (SDK 32+)
        window.colorMode = ActivityInfo.COLOR_MODE_HDR

        glView = FireworksView(this)
        setContentView(glView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        glView.onResume()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        glView.onPause()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // View cleanup if necessary
    }
}