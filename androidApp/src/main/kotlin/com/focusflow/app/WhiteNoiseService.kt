package com.focusflow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.focusflow.designsystem.WhiteNoiseController
import com.focusflow.designsystem.WhiteNoiseKind
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * 白噪音：运行时合成棕噪声 WAV（无版权素材依赖）循环播放；Media3 MediaSessionService 承载
 * mediaPlayback 前台与媒体通知（Media3 默认通知，无需手写）；尊重系统音频焦点。
 * AndroidWhiteNoise 是 UI 侧入口，未运行时通过 startForegroundService 拉起。
 */
class WhiteNoiseService : MediaSessionService() {
    private var session: MediaSession? = null
    internal var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // 立即满足 startForegroundService 的 5 秒契约：同步进前台，不等 WAV 生成与播放准备
                runCatching {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel(CHANNEL, "白噪音", NotificationManager.IMPORTANCE_LOW))
                    val notice = android.app.Notification.Builder(this, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("白噪音播放中")
                        .setOngoing(true).build()
                    if (android.os.Build.VERSION.SDK_INT >= 29)
                        startForeground(NOTICE_ID, notice, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    else startForeground(NOTICE_ID, notice)
                }
                intent.getStringExtra(EXTRA_KIND)?.let { kindName ->
                    start(WhiteNoiseKind.valueOf(kindName))
                }
            }
            ACTION_STOP -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** WAV 首次生成约 900KB，移出主线程；任何失败静默（白噪音绝不允许拖垮专注）。 */
    fun start(kind: WhiteNoiseKind) {
        if (kind == WhiteNoiseKind.SILENCE) { stop(); return }
        val context = this
        Thread {
            val file = runCatching { noiseFile(context, kind) }.getOrNull()
            mainHandler.post {
                runCatching {
                    val mediaSession = session ?: MediaSession.Builder(this, buildPlayer()).build().also { session = it }
                    player = mediaSession.player as ExoPlayer
                    player?.apply {
                        volume = getSharedPreferences("white_noise", MODE_PRIVATE).getFloat("volume", 0.6f)
                        setMediaItem(MediaItem.fromUri(file!!.toURI().toString()))
                        prepare()
                        play()
                    }
                }
            }
        }.start()
    }

    fun stop() {
        runCatching {
            player?.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(this)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ONE }

    override fun onDestroy() {
        instance = null
        player?.release()
        session?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: WhiteNoiseService? = null
        private const val CHANNEL = "white_noise"
        private const val NOTICE_ID = 4102
        private const val ACTION_START = "com.focusflow.app.NOISE_START"
        private const val ACTION_STOP = "com.focusflow.app.NOISE_STOP"
        private const val EXTRA_KIND = "kind"

        /** 棕噪声 WAV：10 秒循环、首尾淡入淡出避免接缝；WIND 档更轻柔。 */
        fun noiseFile(context: Context, kind: WhiteNoiseKind): File {
            val file = File(context.filesDir, "brown_noise_${kind.name.lowercase()}.wav")
            if (file.exists()) return file
            val rate = 44100
            val samples = rate * 10
            val pcm = ShortArray(samples)
            var brown = 0.0
            val scale = if (kind == WhiteNoiseKind.WIND) 0.12 else 0.2
            for (i in 0 until samples) {
                val white = Math.random() * 2 - 1
                brown = (brown + 0.02 * white) / 1.02
                val fade = when {
                    i < rate / 10 -> i.toDouble() / (rate / 10)
                    i > samples - rate / 10 -> (samples - i).toDouble() / (rate / 10)
                    else -> 1.0
                }
                pcm[i] = (brown * 3.5 * scale * fade * Short.MAX_VALUE.toLong()).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            val data = ByteArray(44 + pcm.size * 2)
            fun text(offset: Int, value: String) { for (i in value.indices) data[offset + i] = value[i].code.toByte() }
            fun int(offset: Int, value: Int) { for (i in 0 until 4) data[offset + i] = (value shr (8 * i)).toByte() }
            fun short(offset: Int, value: Int) { for (i in 0 until 2) data[offset + i] = (value shr (8 * i)).toByte() }
            text(0, "RIFF"); int(4, 36 + pcm.size * 2); text(8, "WAVE"); text(12, "fmt ")
            int(16, 16); short(20, 1); short(22, 1); int(24, rate); int(28, rate * 2)
            short(32, 2); short(34, 16); text(36, "data"); int(40, pcm.size * 2)
            for (i in pcm.indices) short(44 + i * 2, pcm[i].toInt())
            file.writeBytes(data)
            return file
        }
    }
}

/** UI 侧白噪音入口；服务存活时直接控制，否则拉起服务。 */
class AndroidWhiteNoise(private val context: Context) : WhiteNoiseController {
    private val preferences = context.getSharedPreferences("white_noise", Context.MODE_PRIVATE)
    override val playing: Boolean get() = WhiteNoiseService.instance?.player?.isPlaying == true
    val storedVolume: Float
        get() = preferences.getFloat("volume", 0.6f)

    override fun start(kind: WhiteNoiseKind) {
        // 白噪音只是体验增强：任何失败（如屏幕固定下系统拒绝前台服务）都静默，绝不中断专注
        runCatching {
            val running = WhiteNoiseService.instance
            if (running != null) running.start(kind)
            else context.startForegroundService(
                Intent(context, WhiteNoiseService::class.java).setAction("com.focusflow.app.NOISE_START").putExtra("kind", kind.name))
        }
    }

    override fun stop() {
        runCatching {
            val running = WhiteNoiseService.instance
            if (running != null) running.stop()
            else context.startService(Intent(context, WhiteNoiseService::class.java).setAction("com.focusflow.app.NOISE_STOP"))
        }
    }

    override fun setVolume(volume: Float) {
        runCatching {
            preferences.edit().putFloat("volume", volume).apply()
            WhiteNoiseService.instance?.player?.setVolume(volume.coerceIn(0f, 1f))
        }
    }
}
