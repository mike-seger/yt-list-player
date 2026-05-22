package com.ytlistplayer.mobile

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private val logTag = "YTListPlayer"
    private val prefsName = "yt_list_player_mobile"
    private val playbackCheckShownKey = "playback_check_shown_v1"

    private val mediaCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MobileBridge.ACTION_MEDIA_COMMAND) return
            val command = intent.getStringExtra(MobileBridge.EXTRA_COMMAND) ?: return
            dispatchWebCommand(command)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(logTag, "MainActivity created")

        webView = findViewById(R.id.webView)
        configureWebView()

        ContextCompat.startForegroundService(
            this,
            Intent(this, PlaybackService::class.java)
        )

        webView.loadUrl(WEB_APP_URL)
        Log.i(logTag, "Loading WebView asset: $WEB_APP_URL")

        maybeShowBackgroundPlaybackCheck()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(mediaCommandReceiver, IntentFilter(MobileBridge.ACTION_MEDIA_COMMAND))
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(mediaCommandReceiver)
        } catch (_: Throwable) {
            // Receiver may not be registered if Activity start failed.
        }
    }

    private fun configureWebView() {
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(logTag, "WebView page finished: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.e(logTag, "WebView error url=${request?.url} code=${error?.errorCode} desc=${error?.description}")
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                Log.e(logTag, "WebView render process gone crashed=${detail?.didCrash()}")
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val level = consoleMessage.messageLevel().name
                Log.d(
                    logTag,
                    "JS[$level] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                )
                return true
            }
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
    }

    private fun dispatchWebCommand(command: String) {
        webView.post {
            val escaped = command.replace("'", "\\'")
            val js = "window.MobileHost && window.MobileHost.command && window.MobileHost.command('${escaped}')"
            webView.evaluateJavascript(js, null)
        }
    }

    private fun maybeShowBackgroundPlaybackCheck() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (prefs.getBoolean(playbackCheckShownKey, false)) return
        prefs.edit().putBoolean(playbackCheckShownKey, true).apply()
        showBackgroundPlaybackCheckDialog()
    }

    private fun showBackgroundPlaybackCheckDialog() {
        val report = buildBackgroundPlaybackCheckReport()

        AlertDialog.Builder(this)
            .setTitle("Background Playback Check")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .setNeutralButton("Open App Battery Settings") { _, _ ->
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                } catch (error: Throwable) {
                    Log.w(logTag, "Unable to open app settings", error)
                }
            }
            .show()
    }

    private fun buildBackgroundPlaybackCheckReport(): String {
        val powerManager = getSystemService(PowerManager::class.java)

        val hasYoutube = hasYouTubeApp()

        val powerSaverOn = powerManager?.isPowerSaveMode == true
        val ignoreBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val lines = mutableListOf<String>()
        lines += "YT List Player - Background Playback Report"
        lines += "Device: ${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        lines += "Timestamp: ${java.time.Instant.now()}"
        lines += ""
        lines += if (hasYoutube) "- YouTube app installed: Yes" else "- YouTube app installed: No"
        lines += if (powerSaverOn) "- Power saver mode: ON (can stop playback)" else "- Power saver mode: Off"
        lines += if (ignoreBatteryOptimizations) "- Battery optimization for this app: Ignored" else "- Battery optimization for this app: Active"
        lines += if (notificationsGranted) "- Notification permission: Granted" else "- Notification permission: Not granted"
        lines += ""
        lines += "Important: lock-screen/background playback for YouTube videos can still depend on YouTube account/content policy (often Premium)."

        return lines.joinToString("\n")
    }

    private fun hasYouTubeApp(): Boolean {
        val byPackage = try {
            packageManager.getPackageInfo("com.google.android.youtube", 0)
            true
        } catch (_: Throwable) {
            false
        }

        if (byPackage) return true

        val byIntent = try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube://watch?v=dQw4w9WgXcQ"))
            intent.resolveActivity(packageManager) != null
        } catch (_: Throwable) {
            false
        }

        return byIntent
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun log(level: String?, message: String?) {
            when ((level ?: "").lowercase()) {
                "error" -> Log.e(logTag, "JS bridge: ${message.orEmpty()}")
                "warn" -> Log.w(logTag, "JS bridge: ${message.orEmpty()}")
                else -> Log.i(logTag, "JS bridge: ${message.orEmpty()}")
            }
        }

        @JavascriptInterface
        fun onPlaybackState(payload: String) {
            try {
                val obj = JSONObject(payload)
                val intent = Intent(this@MainActivity, PlaybackService::class.java).apply {
                    action = MobileBridge.ACTION_UPDATE_STATE
                    putExtra(MobileBridge.EXTRA_TITLE, obj.optString("title", "YT List Player"))
                    putExtra(MobileBridge.EXTRA_ARTIST, obj.optString("artist", ""))
                    putExtra(MobileBridge.EXTRA_PLAYING, obj.optBoolean("playing", false))
                    putExtra(MobileBridge.EXTRA_POSITION_MS, obj.optLong("positionMs", 0L))
                    putExtra(MobileBridge.EXTRA_DURATION_MS, obj.optLong("durationMs", 0L))
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            } catch (error: Throwable) {
                Log.e(logTag, "Malformed playback payload from JS", error)
            }
        }

        @JavascriptInterface
        fun showBackgroundPlaybackCheck() {
            runOnUiThread {
                showBackgroundPlaybackCheckDialog()
            }
        }

        @JavascriptInterface
        fun getBackgroundPlaybackCheckReport(): String {
            return buildBackgroundPlaybackCheckReport()
        }

        @JavascriptInterface
        fun isNativeYouTubePlayerAvailable(): Boolean {
            return true
        }

        @JavascriptInterface
        fun openNativeYouTubePlayer(
            videoId: String?,
            title: String?,
            artist: String?,
            startSeconds: Float,
            autoplay: Boolean
        ) {
            val id = videoId?.trim().orEmpty()
            if (id.isEmpty()) return

            runOnUiThread {
                try {
                    startActivity(Intent(this@MainActivity, NativeYouTubePlayerActivity::class.java).apply {
                        putExtra(NativeYouTubePlayerActivity.EXTRA_VIDEO_ID, id)
                        putExtra(NativeYouTubePlayerActivity.EXTRA_TITLE, title.orEmpty())
                        putExtra(NativeYouTubePlayerActivity.EXTRA_ARTIST, artist.orEmpty())
                        putExtra(NativeYouTubePlayerActivity.EXTRA_START_SECONDS, startSeconds)
                        putExtra(NativeYouTubePlayerActivity.EXTRA_AUTOPLAY, autoplay)
                    })
                } catch (error: Throwable) {
                    Log.e(logTag, "Unable to open native YouTube player", error)
                }
            }
        }
    }
}

private const val WEB_APP_URL = "https://appassets.androidplatform.net/assets/web/index.html"
