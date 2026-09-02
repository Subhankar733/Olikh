package com.subho.olikh

import android.net.Uri

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
        body{background:#070709;color:#F5F5F5;padding:16px 16px 130px;user-select:none}
        .brand{padding:4px 2px 16px}.name{font-size:22px;font-weight:850;letter-spacing:-.5px}.sub{margin-top:2px;color:#777A80;font-size:9px;letter-spacing:1.8px;text-transform:uppercase}
        .card{background:#131417;border:1px solid #232429;border-radius:24px;box-shadow:0 12px 28px rgba(0,0,0,.3);overflow:hidden}
        .recent{min-height:220px;padding:20px;position:relative;margin-bottom:12px;background:linear-gradient(145deg,#1c1d22,#101114 65%,#0a0b0d)}
        .recent:after{content:"";position:absolute;width:130px;height:130px;right:-35px;bottom:-45px;border-radius:50%;background:#1d1e23}
        .row{display:flex;justify-content:space-between;align-items:center}.eyebrow{font-size:9px;letter-spacing:1.8px;color:#777A80;font-weight:800}.dot{color:#FF6422;font-size:14px}
        .big{font-size:26px;line-height:1.1;font-weight:850;letter-spacing:-.8px;margin-top:45px;max-width:92%}.muted{color:#777A80;font-size:11px;margin-top:8px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:90%}
        .action{position:absolute;left:20px;bottom:18px;color:#FF6422;font-size:10px;font-weight:850;letter-spacing:1.2px}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.tile{min-height:115px;padding:16px;display:flex;flex-direction:column;justify-content:space-between;position:relative}.tile:active,.recent:active{transform:scale(.985)}
        .icon{width:32px;height:32px;border-radius:10px;background:#1D1E23;display:flex;align-items:center;justify-content:center;color:#F4F4F5;font-size:15px;font-weight:800}.title{font-size:15px;font-weight:800;letter-spacing:-.2px}.hint{font-size:9px;color:#73767C;margin-top:3px}.wide{grid-column:1/3;min-height:95px}.orange{color:#FF6422}
        .footer{text-align:center;color:#4F5258;font-size:9px;letter-spacing:1.5px;margin-top:20px;text-transform:uppercase}
        </style></head><body>
        <div class="brand"><div class="name">OLIKH</div><div class="sub">PRIVATE BROWSER</div></div>
        $recent
        <div class="grid">
        <div class="tile card" onclick="internal('downloads')"><div class="icon orange">↓</div><div><div class="title">Downloads</div><div class="hint">Files &amp; history</div></div></div>
        <div class="tile card" onclick="internal('history')"><div class="icon">◷</div><div><div class="title">History</div><div class="hint">Recent pages</div></div></div>
        <div class="tile card" onclick="internal('bookmarks')"><div class="icon">★</div><div><div class="title">Bookmarks</div><div class="hint">Saved links</div></div></div>
        <div class="tile card" onclick="internal('tabs')"><div class="icon">▣</div><div><div class="title">Tabs</div><div class="hint">Active sessions</div></div></div>
        <div class="tile card" onclick="internal('privacy')"><div class="icon">◈</div><div><div class="title">Privacy</div><div class="hint">Clear data</div></div></div>
        <div class="tile card" onclick="internal('reader')"><div class="icon">Aa</div><div><div class="title">Reader</div><div class="hint">Clean reading</div></div></div>
        <div class="tile card wide" onclick="internal('advanced')"><div class="icon">⚙</div><div><div class="title">Advanced Browser</div><div class="hint">Engine, security &amp; controls</div></div></div>
        </div><div class="footer">OLIKH • PRIVATE BY DEFAULT</div>
        <script>function openUrl(u){if(window.OlikhNative){OlikhNative.openUrl(u)}}function internal(t){if(window.OlikhNative){OlikhNative.openInternal(t)}}</script>
        </body></html>
        """.trimIndent()
    }
}
