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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class SignUp2Activity : AppCompatActivity() {

    private var isPhoneVerified = false
    private var countDownTimer: CountDownTimer? = null
    private val verificationTime = 180000L // 3분 (180,000ms)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

        val etName = findViewById<TextInputEditText>(R.id.et_name)
        val etPhoneNumber = findViewById<TextInputEditText>(R.id.et_phone_number)
        val etVerifyCode = findViewById<TextInputEditText>(R.id.et_verify)
        val btnGetCode = findViewById<Button>(R.id.phone_number_button)
        val btnVerify = findViewById<Button>(R.id.verify_button)
        val btnNext = findViewById<Button>(R.id.next_button)
        val llNotice = findViewById<LinearLayout>(R.id.ll_notice)
        val ivNotice = findViewById<ImageView>(R.id.iv_notice)
        val tvNotice = findViewById<TextView>(R.id.tv_notice)

        // 전화번호 자동 하이픈 및 길이 제한
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

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.imageButton).setOnClickListener {
            finish()
        }

        // 인증번호 받기 버튼 클릭
        btnGetCode.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")
            
            if (phoneNum.isEmpty() || phoneNum.length < 10) {
                Toast.makeText(this, "올바른 전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            RetrofitClient.instance.sendVerificationCode(VerificationRequest(phoneNum))
                .enqueue(object : Callback<VerificationResponse> {
                    override fun onResponse(call: Call<VerificationResponse>, response: Response<VerificationResponse>) {
                        if (response.isSuccessful) {
                            UserSession.userPhone = phoneNum
                            Toast.makeText(this@SignUp2Activity, "인증번호가 전송되었습니다.", Toast.LENGTH_SHORT).show()
                            btnGetCode.text = "재전송"
                            startTimer(tvNotice, llNotice, ivNotice) // 타이머 시작
                        } else {
                            Toast.makeText(this@SignUp2Activity, "전송 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<VerificationResponse>, t: Throwable) {
                        Toast.makeText(this@SignUp2Activity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        // 인증 확인 버튼 클릭
        btnVerify.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")
            val code = etVerifyCode.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            RetrofitClient.instance.verifyCode(CheckVerificationRequest(phoneNum, code))
                .enqueue(object : Callback<CheckVerificationResponse> {
                    override fun onResponse(call: Call<CheckVerificationResponse>, response: Response<CheckVerificationResponse>) {
                        llNotice.visibility = View.VISIBLE
                        if (response.isSuccessful && response.body()?.success == true) {
                            isPhoneVerified = true
                            countDownTimer?.cancel() // 인증 성공 시 타이머 중지
                            
                            tvNotice.text = "인증이 완료되었습니다."
                            tvNotice.setTextColor(ContextCompat.getColor(this@SignUp2Activity, R.color.teal500))
                            ivNotice.setImageResource(R.drawable.ic_success)
                            
                            etPhoneNumber.isEnabled = false
                            etVerifyCode.isEnabled = false
                            btnGetCode.isEnabled = false
                            btnVerify.isEnabled = false
                            btnVerify.text = "인증완료"
                        } else {
                            isPhoneVerified = false
                            tvNotice.text = "인증번호가 일치하지 않거나 만료되었습니다."
                            tvNotice.setTextColor(ContextCompat.getColor(this@SignUp2Activity, android.R.color.holo_red_dark))
                            ivNotice.setImageResource(R.drawable.ic_error)
                        }
                    }
                    override fun onFailure(call: Call<CheckVerificationResponse>, t: Throwable) {
                        Toast.makeText(this@SignUp2Activity, "인증 확인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        // 다음 버튼
        btnNext.setOnClickListener {
            if (!isPhoneVerified) {
                Toast.makeText(this, "전화번호 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveNameAndMoveToNext()
        }
    }

    private fun startTimer(tvNotice: TextView, llNotice: LinearLayout, ivNotice: ImageView) {
        countDownTimer?.cancel()
        
        llNotice.visibility = View.VISIBLE
        // 타이머용 아이콘이 있다면 교체 (없으면 기존 ic_success 유지하거나 GONE 처리)
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
