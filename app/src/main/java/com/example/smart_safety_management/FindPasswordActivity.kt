package com.example.smart_safety_management

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
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

class FindPasswordActivity : AppCompatActivity() {

    private var isPhoneVerified = false
    private var countDownTimer: CountDownTimer? = null
    private val verificationTime = 180000L // 3분

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.find_password)

        val etName = findViewById<TextInputEditText>(R.id.et_name)
        val etId = findViewById<TextInputEditText>(R.id.et_id)
        val etPhoneNumber = findViewById<TextInputEditText>(R.id.et_phone_number)
        val etVerifyCode = findViewById<TextInputEditText>(R.id.et_verify)

        val btnGetCode = findViewById<Button>(R.id.phone_number_button)
        val btnVerify = findViewById<Button>(R.id.verify_button)
        val nextButton = findViewById<Button>(R.id.next_button)

        val llNotice = findViewById<LinearLayout>(R.id.ll_notice)
        val ivNotice = findViewById<ImageView>(R.id.iv_notice)
        val tvNotice = findViewById<TextView>(R.id.tv_notice)

        // 전화번호 하이픈 자동 추가
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
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")
            if (phoneNum.length < 10) {
                ToastUtil.showShort(this, "올바른 전화번호를 입력해주세요.")
                return@setOnClickListener
            }

            // 서버로 인증번호 요청
            RetrofitClient.instance.sendVerificationCode(VerificationRequest(phoneNum, ""))
                .enqueue(object : Callback<VerificationResponse> {
                    override fun onResponse(call: Call<VerificationResponse>, response: Response<VerificationResponse>) {
                        if (response.isSuccessful) {
                            ToastUtil.showShort(this@FindPasswordActivity, "인증번호가 전송되었습니다.")
                            btnGetCode.text = "재전송"
                            startTimer(tvNotice, llNotice, ivNotice)
                        } else {
                            ToastUtil.showShort(this@FindPasswordActivity, "인증번호 전송 실패")
                        }
                    }
                    override fun onFailure(call: Call<VerificationResponse>, t: Throwable) {
                        ToastUtil.showShort(this@FindPasswordActivity, "서버 연결 실패")
                    }
                })
        }

        // 인증 확인 버튼
        btnVerify.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")
            val code = etVerifyCode.text.toString().trim()

            if (code.isEmpty()) {
                ToastUtil.showShort(this, "인증번호를 입력해주세요.")
                return@setOnClickListener
            }

            RetrofitClient.instance.checkVerificationCode(CheckVerificationRequest(phoneNum, code))
                .enqueue(object : Callback<CheckVerificationResponse> {
                    override fun onResponse(call: Call<CheckVerificationResponse>, response: Response<CheckVerificationResponse>) {
                        if (response.isSuccessful && response.body()?.isVerified == true) {
                            onVerificationSuccess(etPhoneNumber, etVerifyCode, btnGetCode, btnVerify, llNotice, tvNotice, ivNotice)
                        } else {
                            ToastUtil.showShort(this@FindPasswordActivity, "인증번호가 일치하지 않습니다.")
                        }
                    }
                    override fun onFailure(call: Call<CheckVerificationResponse>, t: Throwable) {
                        ToastUtil.showShort(this@FindPasswordActivity, "서버 연결 실패")
                    }
                })
        }

        // 다음 버튼
        nextButton.setOnClickListener {
            val name = etName.text.toString().trim()
            val id = etId.text.toString().trim()
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")

            if (name.isEmpty() || id.isEmpty() || phoneNum.isEmpty()) {
                ToastUtil.showShort(this, "모든 정보를 입력해주세요.")
                return@setOnClickListener
            }

            if (!isPhoneVerified) {
                ToastUtil.showShort(this, "휴대폰 인증을 완료해주세요.")
                return@setOnClickListener
            }

            // 서버에서 이름, 아이디, 전화번호가 일치하는 유저인지 확인
            val verifyRequest = VerifyUserRequest(name, id, phoneNum)
            RetrofitClient.instance.verifyUserForPassword(verifyRequest).enqueue(object : Callback<VerifyUserResponse> {
                override fun onResponse(call: Call<VerifyUserResponse>, response: Response<VerifyUserResponse>) {
                    if (response.isSuccessful && response.body()?.exists == true) {
                        val intent = Intent(this@FindPasswordActivity, ChangePasswordActivity::class.java)
                        intent.putExtra("userId", id)
                        startActivity(intent)
                    } else {
                        ToastUtil.showShort(this@FindPasswordActivity, "입력하신 정보와 일치하는 유저가 없습니다.")
                    }
                }
                override fun onFailure(call: Call<VerifyUserResponse>, t: Throwable) {
                    ToastUtil.showShort(this@FindPasswordActivity, "서버 통신 오류: ${t.message}")
                }
            })
        }
    }

    private fun startTimer(tvNotice: TextView, llNotice: LinearLayout, ivNotice: ImageView) {
        countDownTimer?.cancel()
        llNotice.visibility = View.VISIBLE
        ivNotice.visibility = View.GONE

        countDownTimer = object : CountDownTimer(verificationTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvNotice.text = String.format(Locale.getDefault(), "남은 시간 %02d:%02d", minutes, seconds)
                tvNotice.setTextColor(ContextCompat.getColor(this@FindPasswordActivity, R.color.gray700_gray400))
            }

            override fun onFinish() {
                tvNotice.text = "인증 시간이 만료되었습니다. 다시 시도해주세요."
                tvNotice.setTextColor(ContextCompat.getColor(this@FindPasswordActivity, android.R.color.holo_red_dark))
                ivNotice.visibility = View.VISIBLE
                ivNotice.setImageResource(R.drawable.ic_error)
                isPhoneVerified = false
            }
        }.start()
    }

    private fun onVerificationSuccess(
        etPhone: TextInputEditText,
        etVerify: TextInputEditText,
        btnGet: Button,
        btnVer: Button,
        llNotice: LinearLayout,
        tvNotice: TextView,
        ivNotice: ImageView
    ) {
        isPhoneVerified = true
        countDownTimer?.cancel()

        llNotice.visibility = View.VISIBLE
        tvNotice.text = "인증이 완료되었습니다."
        tvNotice.setTextColor(ContextCompat.getColor(this, R.color.teal500))
        ivNotice.visibility = View.VISIBLE
        ivNotice.setImageResource(R.drawable.ic_success)

        etPhone.isEnabled = false
        etVerify.isEnabled = false

        btnGet.isEnabled = false
        btnGet.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray50_gray900)
        btnGet.setTextColor(ContextCompat.getColor(this, R.color.gray400_gray700))

        btnVer.isEnabled = false
        btnVer.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray50_gray900)
        btnVer.setTextColor(ContextCompat.getColor(this, R.color.gray400_gray700))
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
