package com.ytlistplayer.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class NativeYouTubePlayerActivity : ComponentActivity() {

    private var youTubePlayer: YouTubePlayer? = null
    private var youTubePlayerView: YouTubePlayerView? = null
    private var videoId: String = ""
    private var isPlaying = false
    private var durationSec = 0f
    private var currentSec = 0f
    private var isUserSeeking = false
    private var handledEmbedRestriction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_native_youtube_player)

        videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.trim().orEmpty()
        if (videoId.isEmpty()) {
            Toast.makeText(this, "Missing YouTube video id", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val startSeconds = intent.getFloatExtra(EXTRA_START_SECONDS, 0f)
        val autoplay = intent.getBooleanExtra(EXTRA_AUTOPLAY, true)

        val titleView = findViewById<TextView>(R.id.nativeTitle)
        val subtitleView = findViewById<TextView>(R.id.nativeSubtitle)
        val closeButton = findViewById<MaterialButton>(R.id.nativeClose)
        val playPauseButton = findViewById<MaterialButton>(R.id.nativePlayPause)
        val openInYoutubeButton = findViewById<MaterialButton>(R.id.nativeOpenYoutube)
        val seekBar = findViewById<SeekBar>(R.id.nativeSeekBar)
        val elapsedView = findViewById<TextView>(R.id.nativeElapsed)
        val durationView = findViewById<TextView>(R.id.nativeDuration)
        val playerView = findViewById<YouTubePlayerView>(R.id.nativePlayerView)
        youTubePlayerView = playerView

        titleView.text = title.ifBlank { "YT List Player" }
        subtitleView.text = artist
        subtitleView.visibility = if (artist.isBlank()) View.GONE else View.VISIBLE

        // Allows playback when app is backgrounded if host app chooses to keep playback active.
        playerView.enableBackgroundPlayback(true)
        val options = IFramePlayerOptions.Builder()
            .controls(0)
            .fullscreen(0)
            .rel(0)
            .ivLoadPolicy(3)
            .build()

        seekBar.max = 1000
        elapsedView.text = formatTime(0f)
        durationView.text = formatTime(0f)
        playPauseButton.text = "Play"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || durationSec <= 0f) return
                val target = (progress / 1000f) * durationSec
                elapsedView.text = formatTime(target)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val progress = bar?.progress ?: 0
                val target = if (durationSec > 0f) (progress / 1000f) * durationSec else 0f
                currentSec = target
                youTubePlayer?.seekTo(target)
                isUserSeeking = false
            }
        })

        playerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                this@NativeYouTubePlayerActivity.youTubePlayer = youTubePlayer
                // Keep audio on for native mode unless device/app audio routing forces mute.
                youTubePlayer.unMute()
                youTubePlayer.setVolume(100)
                if (autoplay) {
                    youTubePlayer.loadVideo(videoId, startSeconds)
                } else {
                    youTubePlayer.cueVideo(videoId, startSeconds)
                }
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState) {
                isPlaying = state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.PLAYING
                playPauseButton.text = if (isPlaying) "Pause" else "Play"
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                currentSec = second
                if (!isUserSeeking) {
                    elapsedView.text = formatTime(second)
                    if (durationSec > 0f) {
                        val progress = ((second / durationSec).coerceIn(0f, 1f) * 1000f).toInt()
                        seekBar.progress = progress
                    }
                }
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                durationSec = duration
                durationView.text = formatTime(duration)
            }

            override fun onError(
                youTubePlayer: YouTubePlayer,
                error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError
            ) {
                val blocked = error == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
                if (blocked && !handledEmbedRestriction) {
                    handledEmbedRestriction = true
                    Toast.makeText(
                        this@NativeYouTubePlayerActivity,
                        "Embedded playback blocked. Opening in YouTube.",
                        Toast.LENGTH_LONG
                    ).show()
                    openInYoutube(videoId)
                }
            }
        }, true, options)

        playPauseButton.setOnClickListener {
            val player = youTubePlayer ?: return@setOnClickListener
            if (isPlaying) player.pause() else player.play()
            player.unMute()
            player.setVolume(100)
        }

        closeButton.setOnClickListener {
            finish()
        }

        openInYoutubeButton.setOnClickListener {
            openInYoutube(videoId)
        }
    }

    override fun onDestroy() {
        youTubePlayer = null
        youTubePlayerView?.release()
        youTubePlayerView = null
        super.onDestroy()
    }

    private fun formatTime(seconds: Float): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val mins = total / 60
        val secs = total % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }

    private fun openInYoutube(videoId: String) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://watch?v=$videoId"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
        try {
            startActivity(appIntent)
        } catch (_: Throwable) {
            try {
                startActivity(webIntent)
            } catch (_: Throwable) {
                Toast.makeText(this, "No app can open YouTube links.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "native.videoId"
        const val EXTRA_TITLE = "native.title"
        const val EXTRA_ARTIST = "native.artist"
        const val EXTRA_START_SECONDS = "native.startSeconds"
        const val EXTRA_AUTOPLAY = "native.autoplay"
    }
}
