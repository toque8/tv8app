package app.tv8

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder

class AudioService : Service() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioResId: Int = 0
    
    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    fun playAudio(resourceId: Int) {
        if (currentAudioResId == resourceId && mediaPlayer?.isPlaying == true) {
            return
        }
        
        stopAudio()
        
        currentAudioResId = resourceId
        mediaPlayer = MediaPlayer.create(this, resourceId)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }
    
    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        currentAudioResId = 0
    }
    
    fun playSoundEffect(resourceId: Int) {
        val soundPlayer = MediaPlayer.create(this, resourceId)
        soundPlayer.setOnCompletionListener { mp ->
            mp.release()
        }
        soundPlayer.start()
    }
    
    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}