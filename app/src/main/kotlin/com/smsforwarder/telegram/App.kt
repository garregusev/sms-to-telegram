package com.smsforwarder.telegram

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {
    companion object {
        private const val TAG = "SmsForwarderApp"
        private const val WORK_NAME = "sms_checker_work"
    }

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicWork()
    }

    private fun schedulePeriodicWork() {
        Log.d(TAG, "Scheduling periodic SMS check work")

        val workRequest = PeriodicWorkRequestBuilder<SmsChecker>(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "Periodic work scheduled successfully")
    }
}
