package com.ytlistplayer.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class PlaybackService : Service() {

    private lateinit var mediaSession: MediaSessionCompat

    private var title: String = "YT List Player"
    private var artist: String = ""
    private var playing: Boolean = false
    private var positionMs: Long = 0L
    private var durationMs: Long = 0L
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createMediaChannel()
        setupMediaSession()
        refreshForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MobileBridge.ACTION_UPDATE_STATE -> {
                title = intent.getStringExtra(MobileBridge.EXTRA_TITLE) ?: title
                artist = intent.getStringExtra(MobileBridge.EXTRA_ARTIST) ?: artist
                playing = intent.getBooleanExtra(MobileBridge.EXTRA_PLAYING, playing)
                positionMs = intent.getLongExtra(MobileBridge.EXTRA_POSITION_MS, positionMs)
                durationMs = intent.getLongExtra(MobileBridge.EXTRA_DURATION_MS, durationMs)
                updateMediaSessionState()
            }

            MobileBridge.ACTION_CMD_PLAY -> {
                broadcastCommand(MobileBridge.CMD_PLAY)
                playing = true
                updateMediaSessionState()
            }

            MobileBridge.ACTION_CMD_PAUSE -> {
                broadcastCommand(MobileBridge.CMD_PAUSE)
                playing = false
                updateMediaSessionState()
            }

            MobileBridge.ACTION_CMD_NEXT -> {
                broadcastCommand(MobileBridge.CMD_NEXT)
                updateMediaSessionState()
            }

            MobileBridge.ACTION_CMD_PREV -> {
                broadcastCommand(MobileBridge.CMD_PREV)
                updateMediaSessionState()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "yt-list-player-session")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = broadcastCommand(MobileBridge.CMD_PLAY)
            override fun onPause() = broadcastCommand(MobileBridge.CMD_PAUSE)
            override fun onSkipToNext() = broadcastCommand(MobileBridge.CMD_NEXT)
            override fun onSkipToPrevious() = broadcastCommand(MobileBridge.CMD_PREV)
            override fun onStop() = broadcastCommand(MobileBridge.CMD_PAUSE)
        })
        mediaSession.isActive = true
        updateMediaSessionState()
    }

    private fun updateMediaSessionState() {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                positionMs,
                if (playing) 1.0f else 0.0f
            )
            .build()
        mediaSession.setPlaybackState(state)

        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()
        mediaSession.setMetadata(meta)

        refreshForegroundNotification()
    }

    private fun refreshForegroundNotification() {
        val notification = NotificationCompat.Builder(this, MobileBridge.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setContentIntent(openAppPendingIntent())
            .addAction(android.R.drawable.ic_media_previous, "Prev", commandPendingIntent(MobileBridge.ACTION_CMD_PREV))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                commandPendingIntent(if (playing) MobileBridge.ACTION_CMD_PAUSE else MobileBridge.ACTION_CMD_PLAY)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", commandPendingIntent(MobileBridge.ACTION_CMD_NEXT))
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (!isForegroundStarted) {
            startForeground(MobileBridge.NOTIFICATION_ID, notification)
            isForegroundStarted = true
        } else {
            NotificationManagerCompat.from(this).notify(MobileBridge.NOTIFICATION_ID, notification)
        }
    }

    private fun commandPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            90,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastCommand(command: String) {
        val intent = Intent(MobileBridge.ACTION_MEDIA_COMMAND).apply {
            setPackage(packageName)
            putExtra(MobileBridge.EXTRA_COMMAND, command)
        }
        sendBroadcast(intent)
    }

    private fun createMediaChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            MobileBridge.CHANNEL_ID,
            getString(R.string.media_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.media_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
