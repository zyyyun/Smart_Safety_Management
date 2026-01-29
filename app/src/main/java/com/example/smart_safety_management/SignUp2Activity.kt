package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
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

class SignUp2Activity : AppCompatActivity() {

    private var isPhoneVerified = false

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

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.imageButton).setOnClickListener {
            finish()
        }

        // 인증번호 받기 버튼 클릭
        btnGetCode.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim()
            if (phoneNum.isEmpty()) {
                Toast.makeText(this, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            RetrofitClient.instance.sendVerificationCode(VerificationRequest(phoneNum))
                .enqueue(object : Callback<VerificationResponse> {
                    override fun onResponse(call: Call<VerificationResponse>, response: Response<VerificationResponse>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@SignUp2Activity, "인증번호가 전송되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SignUp2Activity, "인증번호 전송 실패", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<VerificationResponse>, t: Throwable) {
                        Toast.makeText(this@SignUp2Activity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        // 인증 확인 버튼 클릭
        btnVerify.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim()
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
                            tvNotice.text = "인증이 완료되었습니다."
                            tvNotice.setTextColor(ContextCompat.getColor(this@SignUp2Activity, R.color.teal500))
                            ivNotice.setImageResource(R.drawable.ic_success)
                            
                            etPhoneNumber.isEnabled = false
                            etVerifyCode.isEnabled = false
                            btnGetCode.isEnabled = false
                            btnVerify.isEnabled = false
                        } else {
                            isPhoneVerified = false
                            tvNotice.text = "인증번호가 일치하지 않습니다."
                            tvNotice.setTextColor(ContextCompat.getColor(this@SignUp2Activity, android.R.color.holo_red_dark))
                            ivNotice.setImageResource(R.drawable.ic_error) // 에러 아이콘이 있다면 교체 필요
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

    private fun saveNameAndMoveToNext() {
        val name = findViewById<TextInputEditText>(R.id.et_name).text.toString().trim()
        val phoneNum = findViewById<TextInputEditText>(R.id.et_phone_number).text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 데이터 저장
        UserSession.userName = name
        UserSession.userPhone = phoneNum

        startActivity(Intent(this, SignUp3Activity::class.java))
    }
}
