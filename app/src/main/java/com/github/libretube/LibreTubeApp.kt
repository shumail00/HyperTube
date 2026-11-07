package com.github.libretube

import android.app.Application
import com.github.libretube.api.NewPipeExtractorInstance

class LibreTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        NewPipeExtractorInstance.init()
    }

    companion object {
        lateinit var instance: LibreTubeApp
    }
}
