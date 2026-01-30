package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

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

        // 아이디 영문/숫자 체크
        val idRegex = Regex("^[a-zA-Z0-9]*$")
        if (!id.matches(idRegex)) {
            Toast.makeText(this, "아이디는 영문과 숫자만 입력 가능합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 비밀번호 유효성 체크
        val pwError = getPasswordErrorMessage(pw)
        if (pwError != null) {
            tvNotice.text = pwError
            llNotice.visibility = View.VISIBLE
            Toast.makeText(this, pwError, Toast.LENGTH_SHORT).show()
            return
        }

        if (pw != rePw) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // UserSession에 임시 저장
        UserSession.userId = id
        UserSession.userPassword = pw

        // 다음 화면으로 이동 (실제 DB 저장은 다음 화면에서 수행)
        val intent = Intent(this, SignUp4Activity::class.java)
        startActivity(intent)
        finish()
    }
}
