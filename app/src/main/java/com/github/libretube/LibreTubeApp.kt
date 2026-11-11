package com.github.libretube

import android.app.Application

class LibreTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LibreTubeApp
    }
}
