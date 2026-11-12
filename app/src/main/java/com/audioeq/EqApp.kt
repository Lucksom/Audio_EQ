package com.audioeq

import android.app.Application
import com.audioeq.audio.MusicAppTracker
import com.audioeq.shizuku.ShizukuManager

class EqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MusicAppTracker.init(this)
        ShizukuManager.init(this)
    }
}
