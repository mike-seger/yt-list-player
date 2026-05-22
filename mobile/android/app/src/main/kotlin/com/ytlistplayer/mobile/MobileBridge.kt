package com.ytlistplayer.mobile

object MobileBridge {
    const val CHANNEL_ID = "yt_list_player_media"
    const val NOTIFICATION_ID = 7001

    const val ACTION_UPDATE_STATE = "com.ytlistplayer.mobile.action.UPDATE_STATE"
    const val ACTION_MEDIA_COMMAND = "com.ytlistplayer.mobile.action.MEDIA_COMMAND"

    const val ACTION_CMD_PLAY = "com.ytlistplayer.mobile.action.PLAY"
    const val ACTION_CMD_PAUSE = "com.ytlistplayer.mobile.action.PAUSE"
    const val ACTION_CMD_NEXT = "com.ytlistplayer.mobile.action.NEXT"
    const val ACTION_CMD_PREV = "com.ytlistplayer.mobile.action.PREV"

    const val CMD_PLAY = "play"
    const val CMD_PAUSE = "pause"
    const val CMD_NEXT = "next"
    const val CMD_PREV = "prev"

    const val EXTRA_COMMAND = "extra_command"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_ARTIST = "extra_artist"
    const val EXTRA_PLAYING = "extra_playing"
    const val EXTRA_POSITION_MS = "extra_position_ms"
    const val EXTRA_DURATION_MS = "extra_duration_ms"
}
