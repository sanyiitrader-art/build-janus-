package com.janus.app

import android.app.Application
import com.janus.app.di.AppModule

/**
 * Project Janus application entry point.
 *
 * Owns the single AppModule instance (manual, constructor-based DI — no
 * Hilt/Dagger per the "avoid unnecessary dependencies" requirement). Every
 * ViewModel and subsystem in later phases pulls its dependencies from
 * `appModule`, constructed once here and torn down never (process-lifetime).
 */
class JanusApplication : Application() {

    lateinit var appModule: AppModule
        private set

    override fun onCreate() {
        super.onCreate()
        appModule = AppModule(applicationContext)
    }
}