package app.tv8

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AudioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioResId: Int = 0
    private var currentChannelName: String = ""
    private var isPaused = false
    private lateinit var notificationManager: NotificationManager

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tv8_channel",
                "TV8 Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PAUSE_RESUME" -> pauseResumePlayback()
            "ACTION_NEXT" -> nextChannel()
            "ACTION_STOP" -> {
                stopPlayback()
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("STOP_CHANNEL"))
            }
        }
        return START_STICKY
    }

    fun playAudio(resourceId: Int, channelName: String) {
        if (currentAudioResId == resourceId && mediaPlayer != null && !isPaused) {
            return
        }
        releasePlayer()
        currentAudioResId = resourceId
        currentChannelName = channelName
        mediaPlayer = MediaPlayer.create(this, resourceId).apply {
            isLooping = true
            start()
        }
        isPaused = false
        showNotification(isPaused = false)
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        currentAudioResId = 0
        currentChannelName = ""
        isPaused = false
    }

    fun stopPlayback() {
        releasePlayer()
        notificationManager.cancel(1)
        stopSelf()
    }

    fun playSoundEffect(resourceId: Int) {
        val soundPlayer = MediaPlayer.create(this, resourceId)
        soundPlayer.setOnCompletionListener { mp -> mp.release() }
        soundPlayer.start()
    }

    fun pauseResumePlayback() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                isPaused = true
            } else {
                mp.start()
                isPaused = false
            }
            showNotification(isPaused)
            Intent("PAUSE_RESUME_VIDEO").apply {
                putExtra("isPaused", isPaused)
                LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(this)
            }
        }
    }

    private fun nextChannel() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("NEXT_CHANNEL"))
    }

    private fun showNotification(isPaused: Boolean) {
        val notification = buildNotification(isPaused)
        notificationManager.notify(1, notification)
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val pauseResumeIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_PAUSE_RESUME"
        }
        val nextIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_NEXT"
        }
        val stopIntent = Intent(this, AudioService::class.java).apply {
            action = "ACTION_STOP"
        }
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pauseResumeIcon = if (isPaused) R.drawable.ic_media_play else R.drawable.ic_media_pause
        val pauseResumeText = if (isPaused) "Play" else "Pause"

        val builder = NotificationCompat.Builder(this, "tv8_channel")
            .setContentTitle("TV8")
            .setContentText(currentChannelName)
            .setSmallIcon(R.drawable.ic_media_play)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                pauseResumeIcon,
                pauseResumeText,
                PendingIntent.getService(
                    this,
                    1,
                    pauseResumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.ic_media_next,
                "Next",
                PendingIntent.getService(
                    this,
                    2,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setDeleteIntent(
                PendingIntent.getService(
                    this,
                    0,
                    stopIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(Color.WHITE)
            .setColorized(true)

        val style = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)

        builder.setStyle(style)

        return builder.build()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}