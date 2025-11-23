package com.example.mydreamsaver

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var glView: FireworksView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NATIVE HDR SUPPORT (SDK 32+)
        // Force the window into HDR mode.
        window.colorMode = ActivityInfo.COLOR_MODE_HDR

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        glView = FireworksView(this)
        setContentView(glView)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }
}