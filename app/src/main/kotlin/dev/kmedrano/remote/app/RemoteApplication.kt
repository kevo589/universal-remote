package dev.kmedrano.remote.app

import android.app.Application
import dev.kmedrano.remote.app.di.AppContainer

class RemoteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
