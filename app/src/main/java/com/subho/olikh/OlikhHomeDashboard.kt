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

        val mainCard = if (url.isNotBlank()) {
            """
            <div class="hero-box" onclick="openUrl('${esc(url)}')">
                <div class="hero-top">
                    <span class="badge-tag">ACTIVE TAB</span>
                    <span class="dot-indicator"></span>
                </div>
                <div class="hero-heading">${esc(title.ifBlank { "Continue Browsing" })}</div>
                <div class="hero-subtext">${esc(host.ifBlank { url })}</div>
                <div class="hero-action">RESUME SESSION →</div>
            </div>
            """.trimIndent()
        } else {
            """
            <div class="hero-box" onclick="openUrl('https://www.google.com')">
                <div class="hero-top">
                    <span class="badge-tag">OLIKH PRIVATE</span>
                    <span class="dot-indicator"></span>
                </div>
                <div class="hero-heading">What would you like to explore?</div>
                <div class="hero-subtext">Tap to start secure browsing</div>
                <div class="hero-action">GET STARTED →</div>
            </div>
            """.trimIndent()
        }

        return """
        <!doctype html>
        <html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }
        body { background: #050507; color: #F2F2F7; padding: 24px 20px 110px; user-select: none; min-height: 100vh; display: flex; flex-direction: column; justify-content: space-between; }
        
        .header-wrap { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding: 0 4px; }
        .logo-text { font-size: 21px; font-weight: 900; letter-spacing: -0.5px; color: #FFFFFF; }
        .logo-badge { font-size: 9px; font-weight: 800; letter-spacing: 2px; color: #FF5500; background: rgba(255,85,0,0.1); padding: 5px 10px; border-radius: 20px; border: 1px solid rgba(255,85,0,0.2); }

        .hero-box { background: linear-gradient(145deg, #121318, #0a0b0e); border: 1px solid rgba(255,255,255,0.08); border-radius: 26px; padding: 26px 22px; margin-bottom: 16px; box-shadow: 0 20px 40px rgba(0,0,0,0.4); position: relative; overflow: hidden; }
        .hero-box:active { transform: scale(0.98); transition: transform 0.1s ease; }
        .hero-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
        .badge-tag { font-size: 9px; font-weight: 800; letter-spacing: 1.5px; color: #8E8E93; }
        .dot-indicator { width: 6px; height: 6px; background: #FF5500; border-radius: 50%; box-shadow: 0 0 10px #FF5500; }
        .hero-heading { font-size: 24px; font-weight: 800; line-height: 1.15; letter-spacing: -0.6px; color: #FFFFFF; margin-bottom: 8px; }
        .hero-subtext { font-size: 11px; color: #8E8E93; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 16px; }
        .hero-action { font-size: 10px; font-weight: 900; letter-spacing: 1.5px; color: #FF5500; }

        .quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px; }
        .q-item { background: #0c0d10; border: 1px solid rgba(255,255,255,0.05); border-radius: 20px; padding: 16px 6px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; }
        .q-item:active { background: #15161b; }
        .q-icon { width: 38px; height: 38px; background: #16171d; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 16px; color: #FFFFFF; border: 1px solid rgba(255,255,255,0.06); }
        .q-label { font-size: 10px; font-weight: 700; color: #A0A0AB; }

        .links-stack { display: flex; flex-direction: column; gap: 8px; }
        .link-row { background: #0c0d10; border: 1px solid rgba(255,255,255,0.05); border-radius: 18px; padding: 15px 18px; display: flex; justify-content: space-between; align-items: center; }
        .link-row:active { background: #15161b; }
        .link-left { display: flex; align-items: center; gap: 14px; }
        .link-ico { font-size: 16px; color: #FF5500; }
        .link-txt { font-size: 13px; font-weight: 700; color: #E0E0E6; }
        .link-arr { font-size: 12px; color: #55555F; }

        .footer-tag { text-align: center; font-size: 9px; letter-spacing: 2.5px; color: #44444D; text-transform: uppercase; margin-top: 24px; }
        </style></head><body>

        <div>
            <div class="header-wrap">
                <div class="logo-text">OLIKH</div>
                <div class="logo-badge">SECURE</div>
            </div>

            $mainCard

            <div class="quick-grid">
                <div class="q-item" onclick="internal('downloads')">
                    <div class="q-icon">↓</div>
                    <div class="q-label">Downloads</div>
                </div>
                <div class="q-item" onclick="internal('history')">
                    <div class="q-icon">◷</div>
                    <div class="q-label">History</div>
                </div>
                <div class="q-item" onclick="internal('bookmarks')">
                    <div class="q-icon">★</div>
                    <div class="q-label">Bookmarks</div>
                </div>
                <div class="q-item" onclick="internal('tabs')">
                    <div class="q-icon">▣</div>
                    <div class="q-label">Tabs</div>
                </div>
            </div>

            <div class="links-stack">
                <div class="link-row" onclick="internal('privacy')">
                    <div class="link-left">
                        <div class="link-ico">◈</div>
                        <div class="link-txt">Privacy &amp; Data Shield</div>
                    </div>
                    <div class="link-arr">→</div>
                </div>
                <div class="link-row" onclick="internal('reader')">
                    <div class="link-left">
                        <div class="link-ico">Aa</div>
                        <div class="link-txt">Clean Reader Mode</div>
                    </div>
                    <div class="link-arr">→</div>
                </div>
                <div class="link-row" onclick="internal('advanced')">
                    <div class="link-left">
                        <div class="link-ico">⚙</div>
                        <div class="link-txt">Advanced Engine Controls</div>
                    </div>
                    <div class="link-arr">→</div>
                </div>
            </div>
        </div>

        <div class="footer-tag">OLIKH • ZERO TRACE BROWSER</div>

        <script>
            function openUrl(u){if(window.OlikhNative){OlikhNative.openUrl(u)}}
            function internal(t){if(window.OlikhNative){OlikhNative.openInternal(t)}}
        </script>
        </body></html>
        """.trimIndent()
    }
}
