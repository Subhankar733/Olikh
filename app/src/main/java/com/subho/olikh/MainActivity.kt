package com.subho.olikh

import android.os.Bundle
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var urlEditText: EditText
    private lateinit var menuButton: ImageButton
    private val downloadHelper by lazy { DownloadHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlEditText = findViewById(R.id.urlEditText)
        menuButton = findViewById(R.id.menuButton)

        urlEditText.setOnEditorActionListener { _, _, _ ->
            val url = urlEditText.text.toString()
            if (url.isNotEmpty()) {
                // Navigation logic
            }
            true
        }
    }
}
