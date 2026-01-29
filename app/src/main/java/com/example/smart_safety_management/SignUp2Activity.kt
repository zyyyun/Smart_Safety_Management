package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.Locale
import java.util.concurrent.TimeUnit

class SignUp2Activity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var isPhoneVerified = false
    private var countDownTimer: CountDownTimer? = null
    private val verificationTime = 180000L // 3분

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

        auth = FirebaseAuth.getInstance()

        val etName = findViewById<TextInputEditText>(R.id.et_name)
        val etPhoneNumber = findViewById<TextInputEditText>(R.id.et_phone_number)
        val etVerifyCode = findViewById<TextInputEditText>(R.id.et_verify)
        val btnGetCode = findViewById<Button>(R.id.phone_number_button)
        val btnVerify = findViewById<Button>(R.id.verify_button)
        val btnNext = findViewById<Button>(R.id.next_button)
        val llNotice = findViewById<LinearLayout>(R.id.ll_notice)
        val ivNotice = findViewById<ImageView>(R.id.iv_notice)
        val tvNotice = findViewById<TextView>(R.id.tv_notice)

        // 전화번호 자동 하이픈 로직
        etPhoneNumber.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(13))
        etPhoneNumber.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val input = s.toString().replace("-", "")
                val formatted = when {
                    input.length <= 3 -> input
                    input.length <= 7 -> {
                        if (input.length > 3) "${input.substring(0, 3)}-${input.substring(3)}"
                        else input
                    }
                    else -> "${input.substring(0, 3)}-${input.substring(3, 7)}-${input.substring(7)}"
                }
                s?.replace(0, s.length, formatted)
                isFormatting = false
            }
        })

        findViewById<ImageButton>(R.id.imageButton).setOnClickListener { finish() }

        // 인증번호 받기 (Firebase Phone Auth)
        btnGetCode.setOnClickListener {
            val phoneNumWithHyphen = etPhoneNumber.text.toString().trim()
            val phoneNum = phoneNumWithHyphen.replace("-", "")
            
            if (phoneNum.length < 10) {
                Toast.makeText(this, "올바른 전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 한국 국가번호 +82 추가 (010 -> +8210)
            val formattedPhone = "+82${phoneNum.substring(1)}"

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // 자동 인증 성공 (보통 기기에서 자동으로 코드를 읽을 때 발생)
                        signInWithPhoneAuthCredential(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        Log.e("SignUp2Activity", "인증 실패: ${e.message}")
                        Toast.makeText(this@SignUp2Activity, "인증 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                        verificationId = id
                        Toast.makeText(this@SignUp2Activity, "인증번호가 전송되었습니다.", Toast.LENGTH_SHORT).show()
                        btnGetCode.text = "재전송"
                        startTimer(tvNotice, llNotice, ivNotice)
                    }
                })
                .build()
            
            PhoneAuthProvider.verifyPhoneNumber(options)
        }

        // 인증 확인 버튼
        btnVerify.setOnClickListener {
            val code = etVerifyCode.text.toString().trim()
            if (code.isEmpty() || verificationId == null) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
            signInWithPhoneAuthCredential(credential)
        }

        btnNext.setOnClickListener {
            if (!isPhoneVerified) {
                Toast.makeText(this, "전화번호 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveNameAndMoveToNext()
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                val tvNotice = findViewById<TextView>(R.id.tv_notice)
                val llNotice = findViewById<LinearLayout>(R.id.ll_notice)
                val ivNotice = findViewById<ImageView>(R.id.iv_notice)

                if (task.isSuccessful) {
                    isPhoneVerified = true
                    countDownTimer?.cancel()
                    llNotice.visibility = View.VISIBLE
                    tvNotice.text = "인증이 완료되었습니다."
                    tvNotice.setTextColor(ContextCompat.getColor(this, R.color.teal500))
                    ivNotice.setImageResource(R.drawable.ic_success)
                    
                    findViewById<TextInputEditText>(R.id.et_phone_number).isEnabled = false
                    findViewById<TextInputEditText>(R.id.et_verify).isEnabled = false
                    findViewById<Button>(R.id.phone_number_button).isEnabled = false
                    findViewById<Button>(R.id.verify_button).isEnabled = false
                    findViewById<Button>(R.id.verify_button).text = "인증완료"
                } else {
                    Toast.makeText(this, "인증번호가 일치하지 않거나 만료되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startTimer(tvNotice: TextView, llNotice: LinearLayout, ivNotice: ImageView) {
        countDownTimer?.cancel()
        llNotice.visibility = View.VISIBLE
        ivNotice.setImageResource(R.drawable.ic_success)

        countDownTimer = object : CountDownTimer(verificationTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                tvNotice.text = "남은 시간 $timeString"
                tvNotice.setTextColor(ContextCompat.getColor(this@SignUp2Activity, R.color.gray700_gray400))
            }

            override fun onFinish() {
                tvNotice.text = "인증 시간이 만료되었습니다. 다시 시도해주세요."
                tvNotice.setTextColor(ContextCompat.getColor(this@SignUp2Activity, android.R.color.holo_red_dark))
                ivNotice.setImageResource(R.drawable.ic_error)
                isPhoneVerified = false
            }
        }.start()
    }

    private fun saveNameAndMoveToNext() {
        val name = findViewById<TextInputEditText>(R.id.et_name).text.toString().trim()
        val phoneNum = findViewById<TextInputEditText>(R.id.et_phone_number).text.toString().trim().replace("-", "")
        
        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        
        UserSession.userName = name
        UserSession.userPhone = phoneNum
        startActivity(Intent(this, SignUp3Activity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
