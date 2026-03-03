package app.tv8

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {
    
    private lateinit var videoView: VideoView
    private lateinit var screenContainer: FrameLayout
    private var audioService: AudioService? = null
    private var isBound = false
    private var isPoweredOn = false
    private var currentChannelIndex = 0

    private val nextChannelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "NEXT_CHANNEL") {
                runOnUiThread {
                    nextChannelFromNotification()
                }
            }
        }
    }

    private val stopChannelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "STOP_CHANNEL") {
                runOnUiThread {
                    if (isPoweredOn) {
                        isPoweredOn = false
                        videoView.visibility = View.INVISIBLE
                        videoView.stopPlayback()
                    }
                }
            }
        }
    }

    private val pauseResumeVideoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "PAUSE_RESUME_VIDEO") {
                val isPaused = intent.getBooleanExtra("isPaused", false)
                runOnUiThread {
                    if (isPaused) {
                        if (isPoweredOn) {
                            isPoweredOn = false
                            videoView.visibility = View.INVISIBLE
                            videoView.stopPlayback()
                        }
                    } else {
                        if (!isPoweredOn) {
                            isPoweredOn = true
                            videoView.visibility = View.VISIBLE
                            setChannel(0)
                        }
                    }
                }
            }
        }
    }
    
    private val channels = listOf(
        Channel(R.raw.static_video, R.raw.audio_static),
        Channel(R.raw.fire_video, R.raw.fire),
        Channel(R.raw.rain_video, R.raw.rain),
        Channel(R.raw.ocean_video, R.raw.ocean),
        Channel(R.raw.forest_video, R.raw.forest)
    )

    private val channelNames = listOf("Static", "Fire", "Rain", "Ocean", "Forest")
    
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
        screenContainer = findViewById(R.id.screen_container)
        
        val serviceIntent = Intent(this, AudioService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
            nextChannelReceiver, IntentFilter("NEXT_CHANNEL")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stopChannelReceiver, IntentFilter("STOP_CHANNEL")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            pauseResumeVideoReceiver, IntentFilter("PAUSE_RESUME_VIDEO")
        )
        
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
                audioService?.stopPlayback()
            }
        }
    }
    
    private fun setChannel(index: Int) {
        if (index < 0 || index >= channels.size) return
    
        if (isBound) {
            audioService?.playSoundEffect(R.raw.bottom2)
        }
        currentChannelIndex = index
        
        val channel = channels[index]
        val videoUri = Uri.parse("android.resource://" + packageName + "/" + channel.videoResId)
        videoView.setVideoURI(videoUri)
        
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)

            val containerWidth = screenContainer.width
            val containerHeight = screenContainer.height
            if (containerWidth > 0 && containerHeight > 0) {
                val videoWidth = mp.videoWidth
                val videoHeight = mp.videoHeight
                if (videoWidth > 0 && videoHeight > 0) {
                    // Единый масштаб, чтобы видео заполнило контейнер целиком (cover)
                    val scale = Math.max(
                        containerWidth.toFloat() / videoWidth,
                        containerHeight.toFloat() / videoHeight
                    )
                    videoView.scaleX = scale
                    videoView.scaleY = scale
                    videoView.pivotX = 0f
                    videoView.pivotY = 0f
                    videoView.translationX = (containerWidth - videoWidth * scale) / 2f
                    videoView.translationY = (containerHeight - videoHeight * scale) / 2f
                }
            }

            videoView.start()
        }
        
        if (isBound) {
            audioService?.playAudio(channel.audioResId, channelNames[index])
        }
    }

    private fun nextChannelFromNotification() {
        if (!isPoweredOn) return
        var nextIndex = currentChannelIndex + 1
        if (nextIndex >= channels.size) nextIndex = 0
        setChannel(nextIndex)
    }
    
    override fun onPause() {
        super.onPause()
        if (isPoweredOn) {
            try {
                videoView.pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (isPoweredOn) {
            try {
                if (!videoView.isPlaying) {
                    videoView.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(nextChannelReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stopChannelReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pauseResumeVideoReceiver)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val serviceIntent = Intent(this, AudioService::class.java)
        stopService(serviceIntent)
        super.onDestroy()
    }
}