package com.example.smart_safety_management
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val imageView = findViewById<ImageView>(R.id.splashImage)

        // ImageView의 drawable이 animation-list라서 AnimationDrawable로 캐스팅 가능
        val anim = imageView.drawable as AnimationDrawable
        anim.start()

        // 프레임 총 시간 = 각 duration 합
        val totalDuration = (0 until anim.numberOfFrames).sumOf { anim.getDuration(it) }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, TitleActivity::class.java))
            finish()
        }, totalDuration.toLong())
    }
}
