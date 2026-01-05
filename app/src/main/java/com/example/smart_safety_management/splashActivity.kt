package com.example.smart_safety_management
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val layouts = listOf(
        R.layout.activity_splash_1,
        R.layout.activity_splash_1,
        R.layout.activity_splash_2
    )

    private var index = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showNext()
    }

    private fun showNext() {
        setContentView(layouts[index])
        index++

        if (index < layouts.size) {
            handler.postDelayed({ showNext() }, 600) // 프레임 시간
        } else {
            handler.postDelayed({
                startActivity(Intent(this, TitleActivity::class.java))
                finish()
            }, 600)
        }
    }
}

