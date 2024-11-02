package com.example.battery

import android.app.Service
import android.content.Intent
import android.os.IBinder


class MyForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        // Additional setup if necessary
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Start your intensive background tasks here
        runBackgroundTasks() // Start running background tasks

        return START_STICKY // Restart service if it's killed
    }

    private fun runBackgroundTasks() {
        // Implement your CPU, memory, and GPU intensive tasks here
        Thread {
            // Simulate CPU-intensive task
            while (true) {
                for (i in 0 until Int.MAX_VALUE) {
                    // Busy loop for CPU load
                }
            }
        }.start()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // We don't need to bind this service
    }
}
