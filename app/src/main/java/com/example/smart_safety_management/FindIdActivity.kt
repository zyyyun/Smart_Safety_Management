package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FindIdActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.find_id)

        val etName = findViewById<EditText>(R.id.et_name)
        val etPhoneNumber = findViewById<EditText>(R.id.et_phone_number)

        // 전화번호 하이픈 자동 추가
        etPhoneNumber.addTextChangedListener(PhoneNumberFormattingTextWatcher())

        // 비밀번호 찾기 텍스트
        val findPassword = findViewById<TextView>(R.id.tv_find_password)

        // 밑줄 추가
        findPassword.paintFlags =
            findPassword.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // 비밀번호 찾기 이동
        findPassword.setOnClickListener {
            val intent = Intent(this, FindPasswordActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼
        val backBtn = findViewById<ImageButton>(R.id.backButton)
        backBtn.setOnClickListener {
            finish()
        }

        // 다음 버튼
        val nextButton = findViewById<Button>(R.id.next_button)
        nextButton.setOnClickListener {
            val name = etName.text.toString().trim()
            val phoneNum = etPhoneNumber.text.toString().trim()

            if (name.isEmpty() || phoneNum.isEmpty()) {
                Toast.makeText(this, "이름과 전화번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            findIdFromServer(name, phoneNum)
        }
    }

    private fun findIdFromServer(name: String, phoneNum: String) {
        val request = FindIdRequest(name, phoneNum)
        RetrofitClient.instance.findId(request).enqueue(object : Callback<FindIdResponse> {
            override fun onResponse(call: Call<FindIdResponse>, response: Response<FindIdResponse>) {
                if (response.isSuccessful) {
                    val userId = response.body()?.userId
                    if (userId != null) {
                        val intent = Intent(this@FindIdActivity, FoundIdActivity::class.java)
                        intent.putExtra("USER_ID", userId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@FindIdActivity, "일치하는 사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@FindIdActivity, "서버 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<FindIdResponse>, t: Throwable) {
                Toast.makeText(this@FindIdActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
