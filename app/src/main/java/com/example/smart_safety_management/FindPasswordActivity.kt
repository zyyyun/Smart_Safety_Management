package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FindPasswordActivity : AppCompatActivity() {

    private var isPhoneVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.find_password)

        val etName = findViewById<TextInputEditText>(R.id.et_name)
        val etId = findViewById<TextInputEditText>(R.id.et_id)
        val etPhoneNumber = findViewById<TextInputEditText>(R.id.et_phone_number)
        val etVerify = findViewById<TextInputEditText>(R.id.et_verify)

        val btnGetCode = findViewById<Button>(R.id.phone_number_button)
        val btnVerify = findViewById<Button>(R.id.verify_button)
        val nextButton = findViewById<Button>(R.id.next_button)

        // 아이디 찾기 텍스트 밑줄 추가 및 이동
        val findId = findViewById<TextView>(R.id.tv_find_id)
        findId.paintFlags = findId.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        findId.setOnClickListener {
            val intent = Intent(this, FindIdActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 인증번호 받기 버튼
        btnGetCode.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim()
            if (phoneNum.isEmpty()) {
                Toast.makeText(this, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 서버로 인증번호 요청
            RetrofitClient.instance.sendVerificationCode(VerificationRequest(phoneNum, ""))
                .enqueue(object : Callback<VerificationResponse> {
                    override fun onResponse(call: Call<VerificationResponse>, response: Response<VerificationResponse>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@FindPasswordActivity, "인증번호가 전송되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@FindPasswordActivity, "인증번호 전송 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<VerificationResponse>, t: Throwable) {
                        Toast.makeText(this@FindPasswordActivity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        // 인증 확인 버튼
        btnVerify.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim()
            val code = etVerify.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            RetrofitClient.instance.checkVerificationCode(CheckVerificationRequest(phoneNum, code))
                .enqueue(object : Callback<CheckVerificationResponse> {
                    override fun onResponse(call: Call<CheckVerificationResponse>, response: Response<CheckVerificationResponse>) {
                        if (response.isSuccessful && response.body()?.isVerified == true) {
                            isPhoneVerified = true
                            etPhoneNumber.isEnabled = false
                            etVerify.isEnabled = false
                            btnGetCode.isEnabled = false
                            btnVerify.isEnabled = false
                            Toast.makeText(this@FindPasswordActivity, "인증되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@FindPasswordActivity, "인증번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<CheckVerificationResponse>, t: Throwable) {
                        Toast.makeText(this@FindPasswordActivity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        // 다음 버튼
        nextButton.setOnClickListener {
            val name = etName.text.toString().trim()
            val id = etId.text.toString().trim()
            val phoneNum = etPhoneNumber.text.toString().trim()

            if (name.isEmpty() || id.isEmpty() || phoneNum.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isPhoneVerified) {
                Toast.makeText(this, "휴대폰 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 서버에서 이름, 아이디, 전화번호가 일치하는 유저인지 확인
            val verifyRequest = VerifyUserRequest(name, id, phoneNum)
            RetrofitClient.instance.verifyUserForPassword(verifyRequest).enqueue(object : Callback<VerifyUserResponse> {
                override fun onResponse(call: Call<VerifyUserResponse>, response: Response<VerifyUserResponse>) {
                    if (response.isSuccessful && response.body()?.exists == true) {
                        // 정보가 일치함 -> 비밀번호 변경 화면으로 이동
                        val intent = Intent(this@FindPasswordActivity, ChangePasswordActivity::class.java)
                        intent.putExtra("userId", id)
                        startActivity(intent)
                    } else {
                        // 정보가 일치하지 않음
                        Toast.makeText(this@FindPasswordActivity, "입력하신 정보와 일치하는 유저가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<VerifyUserResponse>, t: Throwable) {
                    Toast.makeText(this@FindPasswordActivity, "서버 통신 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
