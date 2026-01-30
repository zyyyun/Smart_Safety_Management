package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignUp3Activity : AppCompatActivity() {

    private lateinit var etId: EditText
    private lateinit var etPw: EditText
    private lateinit var etRePw: EditText
    private lateinit var llNotice: LinearLayout
    private lateinit var tvNotice: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_3)

        etId = findViewById(R.id.et_id)
        etPw = findViewById(R.id.et_password)
        etRePw = findViewById(R.id.et_re_password)
        llNotice = findViewById(R.id.ll_notice)
        tvNotice = findViewById(R.id.tv_notice)

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 비밀번호 입력 실시간 체크
        etPw.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePasswordRealTime(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 완료 버튼
        findViewById<Button>(R.id.finish_button).setOnClickListener {
            saveDataAndMoveToNext()
        }
    }

    private fun validatePasswordRealTime(pw: String) {
        if (pw.isEmpty()) {
            llNotice.visibility = View.GONE
            return
        }

        val errorMsg = getPasswordErrorMessage(pw)
        if (errorMsg != null) {
            tvNotice.text = errorMsg
            llNotice.visibility = View.VISIBLE
        } else {
            llNotice.visibility = View.GONE
        }
    }

    private fun getPasswordErrorMessage(pw: String): String? {
        return when {
            pw.length < 8 -> "비밀번호는 8자 이상 입력해주세요."
            !pw.any { it.isLetter() } -> "비밀번호에 영문을 포함해주세요."
            !pw.any { it.isDigit() } -> "비밀번호에 숫자를 포함해주세요."
            else -> null
        }
    }

    private fun saveDataAndMoveToNext() {
        val id = etId.text.toString()
        val pw = etPw.text.toString()
        val rePw = etRePw.text.toString()

        if (id.isBlank() || pw.isBlank() || rePw.isBlank()) {
            Toast.makeText(this, "아이디와 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 아이디 길이 체크 (2글자 이상)
        if (id.length < 2) {
            Toast.makeText(this, "아이디는 2글자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 비밀번호 유효성 체크
        val pwError = getPasswordErrorMessage(pw)
        if (pwError != null) {
            tvNotice.text = pwError
            llNotice.visibility = View.VISIBLE
            return
        }

        if (pw != rePw) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 서버로 회원가입 요청
        val signUpData = SignUpRequest(
            userId = id,
            password = pw,
            userRole = UserSession.userRole.name.lowercase(),
            name = UserSession.userName,
            email = UserSession.userEmail,
            phoneNum = UserSession.userPhone,
            groupId = null
        )

        RetrofitClient.instance.signUp(signUpData).enqueue(object : Callback<SignUpResponse> {
            override fun onResponse(call: Call<SignUpResponse>, response: Response<SignUpResponse>) {
                if (response.isSuccessful) {
                    // 가입 성공 - 세션에 ID 저장 후 이동
                    UserSession.userId = id
                    UserSession.userPassword = null // 보안상 비번은 제거
                    
                    val intent = Intent(this@SignUp3Activity, SignUp4Activity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // 실패 시
                    val errorMsg = response.errorBody()?.string() ?: "회원가입 처리 중 오류가 발생했습니다."
                    Toast.makeText(this@SignUp3Activity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SignUpResponse>, t: Throwable) {
                Toast.makeText(this@SignUp3Activity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
