package com.movielocal.server.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.movielocal.server.R
import java.io.IOException

class MovieServerService : Service() {

    private var movieServer: MovieServer? = null
    private val binder = LocalBinder()
    
    var isServerRunning = false
        private set

    inner class LocalBinder : Binder() {
        fun getService(): MovieServerService = this@MovieServerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startServer()
            ACTION_STOP_SERVER -> stopServer()
        }
        return START_STICKY
    }

    fun startServer() {
        if (isServerRunning) return

        try {
            movieServer = MovieServer(applicationContext, PORT)
            movieServer?.start()
            isServerRunning = true
            
            val notification = createNotification("Servidor rodando na porta $PORT")
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: IOException) {
            e.printStackTrace()
            isServerRunning = false
        }
    }

    fun stopServer() {
        movieServer?.stop()
        movieServer = null
        isServerRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Movie Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Movie Server Service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Movie Server")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    companion object {
        private const val CHANNEL_ID = "MovieServerChannel"
        private const val NOTIFICATION_ID = 1
        private const val PORT = 8080
        
        const val ACTION_START_SERVER = "com.movielocal.server.START_SERVER"
        const val ACTION_STOP_SERVER = "com.movielocal.server.STOP_SERVER"
    }
}
