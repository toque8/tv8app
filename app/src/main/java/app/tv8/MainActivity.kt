package app.tv8

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var videoView: VideoView
    private var audioService: AudioService? = null
    private var isBound = false
    private var isPoweredOn = false
    private var currentChannelIndex = 0
    
    private val channels = listOf(
        Channel("static", "static.mp4", R.raw.audio_static),
        Channel("fire", "fire.mp4", R.raw.fire),
        Channel("rain", "rain.mp4", R.raw.rain),
        Channel("ocean", "ocean.mp4", R.raw.ocean),
        Channel("forest", "forest.mp4", R.raw.forest)
    )
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            audioService = (service as AudioService.LocalBinder).getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            audioService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        videoView = findViewById(R.id.video_view)
        setupVideoView()
        
        val serviceIntent = Intent(this, AudioService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        isBound = true
        
        findViewById<Button>(R.id.power_btn).setOnClickListener {
            togglePower()
        }
        
        findViewById<Button>(R.id.channel1_btn).setOnClickListener {
            if (isPoweredOn) setChannel(1)
        }
        
        findViewById<Button>(R.id.channel2_btn).setOnClickListener {
            if (isPoweredOn) setChannel(2)
        }
        
        findViewById<Button>(R.id.channel3_btn).setOnClickListener {
            if (isPoweredOn) setChannel(3)
        }
        
        findViewById<Button>(R.id.channel4_btn).setOnClickListener {
            if (isPoweredOn) setChannel(4)
        }
    }
    
    private fun setupVideoView() {
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
        }
    }
    
    private fun togglePower() {
        if (isBound) {
            audioService?.playSoundEffect(R.raw.bottom1)
        }
        
        isPoweredOn = !isPoweredOn
        
        if (isPoweredOn) {
            videoView.visibility = View.VISIBLE
            setChannel(0)
        } else {
            videoView.visibility = View.INVISIBLE
            videoView.stopPlayback()
            if (isBound) {
                audioService?.stopAudio()
            }
        }
    }
    
    private fun setChannel(index: Int) {
        if (index < 0 || index >= channels.size) return
        if (index == currentChannelIndex && isPoweredOn) return
        
        if (isBound) {
            audioService?.playSoundEffect(R.raw.bottom2)
        }
        currentChannelIndex = index
        
        val channel = channels[index]
        
        try {
            val assetPath = "video/${channel.videoFileName}"
            val assetManager = assets
            val inputStream = assetManager.open(assetPath)
            val tempFile = File(cacheDir, channel.videoFileName)
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            
            videoView.setVideoPath(tempFile.absolutePath)
            videoView.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (isBound) {
            audioService?.playAudio(channel.audioResourceId)
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (isPoweredOn) {
            videoView.pause()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (isPoweredOn && !videoView.isPlaying) {
            videoView.start()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val serviceIntent = Intent(this, AudioService::class.java)
        stopService(serviceIntent)
    }
}