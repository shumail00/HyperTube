package com.github.libretube.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class RouterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedLink = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!sharedLink.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_SHARED_LINK, sharedLink)
            }
        }
        startActivity(mainIntent)
        finish()
    }
}
