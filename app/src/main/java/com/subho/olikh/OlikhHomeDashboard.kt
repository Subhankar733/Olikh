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
                <div class="row"><span class="eyebrow">ACTIVE SESSION</span><span class="dot">⚡</span></div>
                <div class="big">${esc(title.ifBlank { "Continue browsing" })}</div>
                <div class="muted">${esc(host.ifBlank { url })}</div>
                <div class="action">RESUME →</div>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="recent card" onclick="openUrl('https://www.google.com')">
                <div class="row"><span class="eyebrow">OLIKH OS</span><span class="dot">⚡</span></div>
                <div class="big">Explore the web, seamlessly.</div>
                <div class="muted">Type a URL or search anything</div>
                <div class="action">QUICK LAUNCH →</div>
            </div>
            """.trimIndent()
        }

        return """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
        *{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;-webkit-tap-highlight-color:transparent}
        body{background:linear-gradient(135deg, #050508 0%, #0d0b14 50%, #050508 100%);color:#F5F5F7;padding:20px 16px 120px;user-select:none;min-height:100vh}
        .brand{display:flex;justify-content:space-between;align-items:center;padding:4px 4px 22px}
        .name{font-size:24px;font-weight:900;letter-spacing:-.5px;background:linear-gradient(90deg, #FFFFFF, #A0A0AB);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
        .sub{color:#FF6422;font-size:10px;letter-spacing:2.5px;font-weight:800;text-transform:uppercase}
        
        .card{background:rgba(20, 21, 26, 0.55);border:1px solid rgba(255, 255, 255, 0.08);border-radius:24px;box-shadow:0 20px 40px rgba(0,0,0,0.4);overflow:hidden;position:relative}
        .recent{min-height:220px;padding:24px;margin-bottom:12px;background:linear-gradient(135deg, rgba(35, 28, 54, 0.4), rgba(18, 19, 24, 0.6));border:1px solid rgba(255, 100, 34, 0.2)}
        .recent:after{content:"";position:absolute;width:160px;height:160px;right:-40px;bottom:-50px;border-radius:50%;background:radial-gradient(circle, rgba(255,100,34,0.15) 0%, transparent 70%)}
        
        .row{display:flex;justify-content:space-between;align-items:center}
        .eyebrow{font-size:10px;letter-spacing:2px;color:#A0A0AB;font-weight:800}
        .dot{font-size:13px}
        .big{font-size:26px;line-height:1.1;font-weight:800;letter-spacing:-.8px;margin-top:45px;max-width:90%}
        .muted{color:#8E8E93;font-size:12px;margin-top:8px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:85%}
        .action{position:absolute;left:24px;bottom:20px;color:#FF6422;font-size:11px;font-weight:900;letter-spacing:1.2px}
        
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:10px}
        .tile{min-height:120px;padding:16px;display:flex;flex-direction:column;justify-content:space-between;transition:transform 0.2s ease}
        .tile:active,.recent:active{transform:scale(0.97)}
        
        .icon{width:34px;height:34px;border-radius:10px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.08);display:flex;align-items:center;justify-content:center;color:#F4F4F5;font-size:15px;font-weight:800}
        .title{font-size:15px;font-weight:800;letter-spacing:-.2px;margin-top:8px}
        .hint{font-size:10px;color:#8E8E93;margin-top:2px}
        .wide{grid-column:1/3;min-height:96px;flex-direction:row;align-items:center}
        .wide .icon{margin-right:14px}
        .orange{color:#FF6422;background:rgba(255,100,34,0.1);border-color:rgba(255,100,34,0.2)}
        
        .footer{text-align:center;color:#55555F;font-size:9px;letter-spacing:2px;margin-top:26px;text-transform:uppercase}
        </style></head><body>
        
        <div class="brand">
            <div class="name">OLIKH</div>
            <div class="sub">V 2.0 ULTIMATE</div>
        </div>

        $recent

        <div class="grid">
            <div class="tile card" onclick="internal('downloads')">
                <div class="icon orange">↓</div>
                <div><div class="title">Downloads</div><div class="hint">Files &amp; media</div></div>
            </div>
            <div class="tile card" onclick="internal('history')">
                <div class="icon">◷</div>
                <div><div class="title">History</div><div class="hint">Activity logs</div></div>
            </div>
            <div class="tile card" onclick="internal('bookmarks')">
                <div class="icon">★</div>
                <div><div class="title">Bookmarks</div><div class="hint">Saved links</div></div>
            </div>
            <div class="tile card" onclick="internal('tabs')">
                <div class="icon">▣</div>
                <div><div class="title">Tabs</div><div class="hint">Active sessions</div></div>
            </div>
            <div class="tile card" onclick="internal('privacy')">
                <div class="icon">◈</div>
                <div><div class="title">Privacy</div><div class="hint">Security shield</div></div>
            </div>
            <div class="tile card" onclick="internal('reader')">
                <div class="icon">Aa</div>
                <div><div class="title">Reader</div><div class="hint">Clean view</div></div>
            </div>
            <div class="tile card wide" onclick="internal('advanced')">
                <div class="icon">⚙</div>
                <div><div class="title">Advanced Controls</div><div class="hint">Engine, security &amp; blocker settings</div></div>
            </div>
        </div>

        <div class="footer">OLIKH • SECURE &amp; DECENTRALIZED</div>

        <script>
            function openUrl(u){if(window.OlikhNative){OlikhNative.openUrl(u)}}
            function internal(t){if(window.OlikhNative){OlikhNative.openInternal(t)}}
        </script>
        </body></html>
        """.trimIndent()
    }
}
