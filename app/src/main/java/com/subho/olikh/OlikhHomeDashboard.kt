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

        val heroSection = if (url.isNotBlank()) {
            """
            <div class="hero-card" onclick="openUrl('${esc(url)}')">
                <div class="hero-badge"><span>RESUME SESSION</span><span class="pulse"></span></div>
                <div class="hero-title">${esc(title.ifBlank { "Active Tab" })}</div>
                <div class="hero-url">${esc(host.ifBlank { url })}</div>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="hero-card" onclick="openUrl('https://www.google.com')">
                <div class="hero-badge"><span>OLIKH BROWSER</span><span class="pulse"></span></div>
                <div class="hero-title">Where do you want to go today?</div>
                <div class="hero-url">Tap to launch search</div>
            </div>
            """.trimIndent()
        }

        return """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }
        body { background: #030305; color: #F1F1F3; padding: 24px 18px 120px; user-select: none; min-height: 100vh; display: flex; flex-direction: column; justify-content: space-between; }
        
        .top-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px; }
        .app-name { font-size: 20px; font-weight: 900; letter-spacing: -0.5px; color: #FFFFFF; }
        .app-edition { font-size: 9px; font-weight: 800; letter-spacing: 2px; color: #FF5500; background: rgba(255,85,0,0.1); padding: 4px 8px; border-radius: 20px; }

        .hero-card { background: linear-gradient(145deg, #121318, #0a0b0e); border: 1px solid rgba(255,255,255,0.08); border-radius: 24px; padding: 28px 22px; margin-bottom: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); position: relative; overflow: hidden; }
        .hero-card:active { transform: scale(0.98); transition: transform 0.1s ease; }
        .hero-badge { display: flex; align-items: center; justify-content: space-between; font-size: 9px; font-weight: 800; letter-spacing: 1.5px; color: #8E8E93; margin-bottom: 12px; }
        .pulse { width: 6px; height: 6px; background: #FF5500; border-radius: 50%; box-shadow: 0 0 8px #FF5500; }
        .hero-title { font-size: 26px; font-weight: 800; line-height: 1.15; letter-spacing: -0.8px; color: #FFFFFF; margin-bottom: 8px; }
        .hero-url { font-size: 11px; color: #FF5500; font-weight: 600; }

        .quick-links { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; }
        .shortcut-item { background: #0c0d10; border: 1px solid rgba(255,255,255,0.05); border-radius: 18px; padding: 14px 8px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; }
        .shortcut-item:active { background: #15161b; }
        .shortcut-icon { width: 36px; height: 36px; background: #16171d; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 16px; color: #FFFFFF; }
        .shortcut-label { font-size: 10px; font-weight: 600; color: #A0A0AB; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; width: 100%; }

        .menu-list { display: flex; flex-direction: column; gap: 8px; }
        .menu-row { background: #0c0d10; border: 1px solid rgba(255,255,255,0.05); border-radius: 16px; padding: 14px 18px; display: flex; justify-content: space-between; align-items: center; }
        .menu-row:active { background: #15161b; }
        .menu-left { display: flex; align-items: center; gap: 14px; }
        .menu-icon { font-size: 16px; color: #FF5500; }
        .menu-text { font-size: 13px; font-weight: 700; color: #E0E0E6; }
        .menu-arrow { font-size: 12px; color: #55555F; }

        .footer-branding { text-align: center; font-size: 9px; letter-spacing: 2px; color: #44444D; text-transform: uppercase; margin-top: 28px; }
        </style></head><body>

        <div>
            <div class="top-header">
                <div class="app-name">OLIKH</div>
                <div class="app-edition">SECURE CORE</div>
            </div>

            $heroSection

            <div class="quick-links">
                <div class="shortcut-item" onclick="internal('downloads')">
                    <div class="shortcut-icon">↓</div>
                    <div class="shortcut-label">Downloads</div>
                </div>
                <div class="shortcut-item" onclick="internal('history')">
                    <div class="shortcut-icon">◷</div>
                    <div class="shortcut-label">History</div>
                </div>
                <div class="shortcut-item" onclick="internal('bookmarks')">
                    <div class="shortcut-icon">★</div>
                    <div class="shortcut-label">Bookmarks</div>
                </div>
                <div class="shortcut-item" onclick="internal('tabs')">
                    <div class="shortcut-icon">▣</div>
                    <div class="shortcut-label">Tabs</div>
                </div>
            </div>

            <div class="menu-list">
                <div class="menu-row" onclick="internal('privacy')">
                    <div class="menu-left">
                        <div class="menu-icon">◈</div>
                        <div class="menu-text">Privacy &amp; Data Shield</div>
                    </div>
                    <div class="menu-arrow">→</div>
                </div>
                <div class="menu-row" onclick="internal('reader')">
                    <div class="menu-left">
                        <div class="menu-icon">Aa</div>
                        <div class="menu-text">Clean Reader Mode</div>
                    </div>
                    <div class="menu-arrow">→</div>
                </div>
                <div class="menu-row" onclick="internal('advanced')">
                    <div class="menu-left">
                        <div class="menu-icon">⚙</div>
                        <div class="menu-text">Advanced Engine Controls</div>
                    </div>
                    <div class="menu-arrow">→</div>
                </div>
            </div>
        </div>

        <div class="footer-branding">OLIKH • ZERO TRACE BROWSER</div>

        <script>
            function openUrl(u){if(window.OlikhNative){OlikhNative.openUrl(u)}}
            function internal(t){if(window.OlikhNative){OlikhNative.openInternal(t)}}
        </script>
        </body></html>
        """.trimIndent()
    }
}
