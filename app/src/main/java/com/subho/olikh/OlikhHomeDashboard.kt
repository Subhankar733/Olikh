package com.subho.olikh

import android.net.Uri

/**
 * Blockit-inspired OLIKH home dashboard.
 *
 * Replace MainActivity.buildStartPageHtml() with a call to:
 * OlikhHomeDashboard.build(lastUsedUrl, lastUsedTitle)
 */
object OlikhHomeDashboard {
    private fun esc(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    fun build(lastUsedUrl: String = "", lastUsedTitle: String = ""): String {
        val url = lastUsedUrl.trim()
        val title = lastUsedTitle.trim()
        val host = runCatching { Uri.parse(url).host?.removePrefix("www.").orEmpty() }
            .getOrDefault("")

        val recent = if (url.isNotBlank()) {
            """
            <div class="recent card" onclick="openUrl('${esc(url)}')">
                <div class="row"><span class="eyebrow">LAST USED</span><span class="dot">●</span></div>
                <div class="big">${esc(title.ifBlank { "Continue browsing" })}</div>
                <div class="muted">${esc(host.ifBlank { url })}</div>
                <div class="action">CONTINUE BROWSING</div>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="recent card" onclick="openUrl('https://www.google.com')">
                <div class="row"><span class="eyebrow">OLIKH</span><span class="dot">●</span></div>
                <div class="big">Ready when you are.</div>
                <div class="muted">No recent site yet</div>
                <div class="action">START BROWSING</div>
            </div>
            """.trimIndent()
        }

        return """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
        *{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;-webkit-tap-highlight-color:transparent}
        body{background:#08090A;color:#F5F5F5;padding:18px 16px 110px;user-select:none}
        .brand{padding:2px 2px 18px}.name{font-size:23px;font-weight:850;letter-spacing:-.8px}.sub{margin-top:3px;color:#66686D;font-size:10px;letter-spacing:1.8px;text-transform:uppercase}
        .card{background:#18191B;border:1px solid #2A2B2E;border-radius:28px;box-shadow:0 14px 32px rgba(0,0,0,.28);overflow:hidden}
        .recent{min-height:245px;padding:23px;position:relative;margin-bottom:14px;background:linear-gradient(145deg,#202124,#121315 65%,#0D0E10)}
        .recent:after{content:"";position:absolute;width:145px;height:145px;right:-48px;bottom:-62px;border-radius:50%;background:#202125}
        .row{display:flex;justify-content:space-between;align-items:center}.eyebrow{font-size:10px;letter-spacing:2px;color:#777A80;font-weight:800}.dot{color:#FF6422;font-size:15px}
        .big{font-size:29px;line-height:1.05;font-weight:850;letter-spacing:-1px;margin-top:57px;max-width:92%}.muted{color:#777A80;font-size:12px;margin-top:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:90%}
        .action{position:absolute;left:23px;bottom:21px;color:#FF6422;font-size:11px;font-weight:850;letter-spacing:1.4px}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.tile{min-height:128px;padding:18px;display:flex;flex-direction:column;justify-content:space-between;position:relative}.tile:active,.recent:active{transform:scale(.985)}
        .icon{width:35px;height:35px;border-radius:11px;background:#252629;display:flex;align-items:center;justify-content:center;color:#F4F4F5;font-size:16px;font-weight:800}.title{font-size:16px;font-weight:800;letter-spacing:-.25px}.hint{font-size:10px;color:#73767C;margin-top:4px}.wide{grid-column:1/3;min-height:104px}.orange{color:#FF6422}
        .footer{text-align:center;color:#4F5258;font-size:9px;letter-spacing:1.5px;margin-top:22px;text-transform:uppercase}
        </style></head><body>
        <div class="brand"><div class="name">OLIKH</div><div class="sub">PRIVATE BROWSER</div></div>
        $recent
        <div class="grid">
        <div class="tile card" onclick="internal('downloads')"><div class="icon orange">↓</div><div><div class="title">Downloads</div><div class="hint">Download history &amp; files</div></div></div>
        <div class="tile card" onclick="internal('history')"><div class="icon">◷</div><div><div class="title">History</div><div class="hint">Recently visited pages</div></div></div>
        <div class="tile card" onclick="internal('bookmarks')"><div class="icon">★</div><div><div class="title">Bookmarks</div><div class="hint">Saved pages</div></div></div>
        <div class="tile card" onclick="internal('tabs')"><div class="icon">▣</div><div><div class="title">Tabs &amp; sessions</div><div class="hint">Open browser tabs</div></div></div>
        <div class="tile card" onclick="internal('privacy')"><div class="icon">◈</div><div><div class="title">Privacy</div><div class="hint">Clear data &amp; permissions</div></div></div>
        <div class="tile card" onclick="internal('reader')"><div class="icon">Aa</div><div><div class="title">Reader</div><div class="hint">Clean reading mode</div></div></div>
        <div class="tile card wide" onclick="internal('advanced')"><div class="icon">⚙</div><div><div class="title">Advanced Browser</div><div class="hint">Engine, security, blocker &amp; browser controls</div></div></div>
        </div><div class="footer">OLIKH • PRIVATE BY DEFAULT</div>
        <script>function openUrl(u){if(window.OlikhNative){OlikhNative.openUrl(u)}}function internal(t){if(window.OlikhNative){OlikhNative.openInternal(t)}}</script>
        </body></html>
        """.trimIndent()
    }
}
