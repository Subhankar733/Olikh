package com.subho.olikh

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.webkit.WebView

class MediaPipWebRtcController(
    private val activity: Activity
) {
    fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        val autoplay = activity.getSharedPreferences(
            "olikh_advanced",
            Activity.MODE_PRIVATE
        ).getBoolean("autoplay_enabled", false)

        webView.settings.mediaPlaybackRequiresUserGesture = !autoplay
    }

    fun isPipSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun enterPip(): Boolean {
        if (!isPipSupported()) return false

        return runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
                builder.setAutoEnterEnabled(false)
            }

            activity.enterPictureInPictureMode(builder.build())
        }.getOrDefault(false)
    }

    fun mediaStateJavascript(): String =
        "(function(){const m=[...document.querySelectorAll('video,audio')];" +
        "const p=m.filter(x=>!x.paused&&!x.ended&&x.readyState>2);" +
        "return JSON.stringify({media:m.length,videos:m.filter(x=>x.tagName.toLowerCase()==='video').length," +
        "playing:p.length,muted:m.filter(x=>x.muted).length,fullscreen:!!document.fullscreenElement," +
        "pipEnabled:!!document.pictureInPictureEnabled,pipActive:!!document.pictureInPictureElement," +
        "current:p[0]?(p[0].currentSrc||p[0].src||''):''})})()"

    fun webRtcStateJavascript(): String =
        "(function(){const m=navigator.mediaDevices;" +
        "return JSON.stringify({secure:location.protocol==='https:'||location.hostname==='localhost'," +
        "mediaDevices:!!m,getUserMedia:!!(m&&m.getUserMedia)," +
        "enumerateDevices:!!(m&&m.enumerateDevices),permissionsApi:!!navigator.permissions})})()"

    fun capabilitiesJavascript(): String =
        "(function(){return JSON.stringify({" +
        "mediaSession:!!navigator.mediaSession," +
        "pictureInPicture:!!document.pictureInPictureEnabled," +
        "webRtc:!!(navigator.mediaDevices&&navigator.mediaDevices.getUserMedia)," +
        "webAudio:!!(window.AudioContext||window.webkitAudioContext)," +
        "encryptedMedia:!!navigator.requestMediaKeySystemAccess})})()"
}
