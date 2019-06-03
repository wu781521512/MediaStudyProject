package com.example.mediastudyproject.activity

import android.app.Application
import android.util.Log

class MyApplication : Application(),Thread.UncaughtExceptionHandler {
    override fun onCreate() {
        super.onCreate()
    }

    override fun uncaughtException(t: Thread?, e: Throwable?) {
        Log.w("uncaught","线程名 ${t?.name}  ${e?.message}")
    }

}