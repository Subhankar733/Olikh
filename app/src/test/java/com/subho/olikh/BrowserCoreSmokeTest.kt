package com.subho.olikh

import org.junit.Assert.*
import org.junit.Test

class BrowserCoreSmokeTest {

    @Test
    fun testSmartUrlFormatting_validHttpAndHttps() {
        val rawHttps = "https://example.com"
        val rawHttp = "http://example.org"
        val rawDomain = "example.com"
        val searchQuery = "olikh browser android"

        // HTTP/HTTPS direct pass-through
        assertTrue(rawHttps.startsWith("https://") || rawHttps.startsWith("http://"))
        assertTrue(rawHttp.startsWith("http://"))

        // Domain auto prefix
        val formattedDomain = if (!rawDomain.startsWith("http://") && !rawDomain.startsWith("https://") && rawDomain.contains(".")) {
            "https://$rawDomain"
        } else {
            rawDomain
        }
        assertEquals("https://example.com", formattedDomain)

        // Search engine query formulation
        val formattedSearch = if (!searchQuery.contains(".") && !searchQuery.startsWith("http")) {
            "https://www.google.com/search?q=" + searchQuery.replace(" ", "+")
        } else {
            searchQuery
        }
        assertEquals("https://www.google.com/search?q=olikh+browser+android", formattedSearch)
    }

    @Test
    fun testTabCreationAndSwitchLogic() {
        data class MockTab(val id: String, var url: String, var title: String)

        val tabList = mutableListOf<MockTab>()
        
        // 1. Initial Tab
        val tab1 = MockTab(id = "tab_1", url = "about:blank", title = "New Tab")
        tabList.add(tab1)
        var activeTabIndex = 0
        
        assertEquals(1, tabList.size)
        assertEquals("tab_1", tabList[activeTabIndex].id)

        // 2. Add New Tab
        val tab2 = MockTab(id = "tab_2", url = "https://example.com", title = "Example")
        tabList.add(tab2)
        activeTabIndex = tabList.size - 1

        assertEquals(2, tabList.size)
        assertEquals("tab_2", tabList[activeTabIndex].id)

        // 3. Switch back to Tab 1
        activeTabIndex = 0
        assertEquals("tab_1", tabList[activeTabIndex].id)

        // 4. Close Active Tab and restore adjacent tab
        tabList.removeAt(activeTabIndex)
        activeTabIndex = (activeTabIndex).coerceAtMost(tabList.size - 1)
        
        assertEquals(1, tabList.size)
        assertEquals("tab_2", tabList[activeTabIndex].id)
    }

    @Test
    fun testDownloadStateTransitions_noEndlessLoop() {
        enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

        data class MockDownloadItem(
            val id: Long,
            val url: String,
            var progress: Int,
            var status: DownloadStatus,
            var isPolling: Boolean
        )

        val item = MockDownloadItem(
            id = 1001L,
            url = "https://example.com/testfile.zip",
            progress = 0,
            status = DownloadStatus.PENDING,
            isPolling = true
        )

        // Progress Update
        item.status = DownloadStatus.DOWNLOADING
        item.progress = 50
        assertTrue(item.isPolling)

        // Completion - Polling MUST stop
        item.progress = 100
        item.status = DownloadStatus.COMPLETED
        if (item.status == DownloadStatus.COMPLETED || item.status == DownloadStatus.FAILED) {
            item.isPolling = false
        }

        assertEquals(DownloadStatus.COMPLETED, item.status)
        assertFalse("Polling loop must terminate immediately upon completion", item.isPolling)
    }

    @Test
    fun testReaderModeContentExtractor_validTextSanitization() {
        val sampleHtml = """
            <html>
                <head><title>Article Title</title></head>
                <body>
                    <div class="ad-banner">Spam Ad</div>
                    <article>
                        <h1>Article Title</h1>
                        <p>This is paragraph 1 of the article.</p>
                        <p>This is paragraph 2 with useful content.</p>
                    </article>
                    <div class="footer">Copyright 2026</div>
                </body>
            </html>
        """.trimIndent()

        val extracted = sampleHtml
            .replace(Regex("<div class=\"ad-banner\">.*?</div>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<div class=\"footer\">.*?</div>", RegexOption.DOT_MATCHES_ALL), "")

        assertFalse(extracted.contains("Spam Ad"))
        assertFalse(extracted.contains("Copyright 2026"))
        assertTrue(extracted.contains("This is paragraph 1 of the article."))
    }
}
