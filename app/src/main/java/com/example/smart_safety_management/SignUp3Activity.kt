package com.example.smart_safety_management

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class SignUp3Activity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_3)

        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 완료 버튼
        findViewById<Button>(R.id.finish_button).setOnClickListener {
            moveToSignUp4()
        }
    }

    private fun moveToSignUp4() {
        val id = findViewById<EditText>(R.id.et_id).text.toString()
        val pw = findViewById<EditText>(R.id.et_password).text.toString()

        if (id.isBlank() || pw.isBlank()) {
            Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val signUpData = SignUpRequest(
            userId = id,
            password = pw,
            userRole = UserSession.userRole.name.lowercase(), // "manager" or "worker"
            name = UserSession.userName,
            email = null,
            phoneNum = null,
            groupId = null
        )

        // Retrofit을 이용한 회원가입 요청
        RetrofitClient.instance.signUp(signUpData).enqueue(object : Callback<SignUpResponse> {
            override fun onResponse(call: Call<SignUpResponse>, response: Response<SignUpResponse>) {
                if (response.isSuccessful) {
                    val message = response.body()?.message ?: "회원가입 성공"
                    Toast.makeText(this@SignUp3Activity, message, Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@SignUp3Activity, SignUp4Activity::class.java)
                    intent.putExtra(EXTRA_USER_ID, id)
                    startActivity(intent)
                    finish() // 가입 완료 후 현재 액티비티 종료
                } else {
                    Toast.makeText(this@SignUp3Activity, "가입 실패: 중복된 아이디거나 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SignUpResponse>, t: Throwable) {
                Toast.makeText(this@SignUp3Activity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

}
