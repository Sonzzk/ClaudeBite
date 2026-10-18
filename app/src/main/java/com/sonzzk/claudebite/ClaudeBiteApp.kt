package com.sonzzk.claudebite

import android.app.Application

class ClaudeBiteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
