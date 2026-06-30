package com.example.samplestandard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text(text = "Trampoline Splash", color = Color.White, fontSize = 24.sp)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val next = Intent(this, StandardMainActivity::class.java)
            // Forward all extras from the launcher intent (e.g. INIT_MODE from benchmarks)
            intent.extras?.let { next.putExtras(it) }
            startActivity(next)
            finish()
        }, 100)
    }
}
