package com.auwire.iamkhata

import android.app.Application

/** Owns application-scoped dependencies. */
class IamKhataApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
