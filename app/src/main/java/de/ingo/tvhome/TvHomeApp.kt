package de.ingo.tvhome

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TvHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SetupServer.start(this)
        startHaService(this)
    }

    companion object {
        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }

        fun startHaService(ctx: Context) {
            val i = Intent(ctx, HaService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Exception) {
            }
        }
    }
}
