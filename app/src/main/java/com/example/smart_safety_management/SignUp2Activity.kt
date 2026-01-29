package com.example.smart_safety_management

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class SignUp2Activity : AppCompatActivity() {

    private var isPhoneVerified = false
    private var countDownTimer: CountDownTimer? = null
    private val verificationTime = 180000L // 3분

    // SMS Retriever를 위한 BroadcastReceiver
    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val smsRetrieverStatus = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status

                when (smsRetrieverStatus?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val message = extras.get(SmsRetriever.EXTRA_SMS_MESSAGE) as? String
                        message?.let {
                            Log.d("SignUp2Activity", "SMS 수신 성공: $it")
                            val pattern = Regex("\\d{6}")
                            val match = pattern.find(it)
                            match?.value?.let { code ->
                                findViewById<TextInputEditText>(R.id.et_verify).setText(code)
                                Toast.makeText(context, "인증번호가 자동으로 입력되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        Log.e("SignUp2Activity", "SMS Retriever 시간 초과")
                    }
                }
            }
        }
    }

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

        btnGetCode.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")

            if (phoneNum.length < 10) {
                Toast.makeText(this, "올바른 전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startSmsRetriever()

            // 서버로 인증번호 전송 요청
            val service = RetrofitClient.instance
            service.sendVerificationCode(VerificationRequest(phoneNum)).enqueue(object : Callback<VerificationResponse> {
                override fun onResponse(call: Call<VerificationResponse>, response: Response<VerificationResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SignUp2Activity, "인증번호가 전송되었습니다.", Toast.LENGTH_SHORT).show()
                        btnGetCode.text = "재전송"
                        startTimer(tvNotice, llNotice, ivNotice)
                    } else {
                        Toast.makeText(this@SignUp2Activity, "인증번호 전송 실패", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<VerificationResponse>, t: Throwable) {
                    Toast.makeText(this@SignUp2Activity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
                }
            })
        }

        btnVerify.setOnClickListener {
            val phoneNum = etPhoneNumber.text.toString().trim().replace("-", "")
            val code = etVerifyCode.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 서버로 인증번호 확인 요청
            val service = RetrofitClient.instance
            service.checkVerificationCode(CheckVerificationRequest(phoneNum, code)).enqueue(object : Callback<CheckVerificationResponse> {
                override fun onResponse(call: Call<CheckVerificationResponse>, response: Response<CheckVerificationResponse>) {
                    if (response.isSuccessful && response.body()?.isVerified == true) {
                        onVerificationSuccess()
                    } else {
                        Toast.makeText(this@SignUp2Activity, "인증번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<CheckVerificationResponse>, t: Throwable) {
                    Toast.makeText(this@SignUp2Activity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
                }
            })
        }

        btnNext.setOnClickListener {
            if (!isPhoneVerified) {
                Toast.makeText(this, "전화번호 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveNameAndMoveToNext()
        }
    }

    private fun startSmsRetriever() {
        val client = SmsRetriever.getClient(this)
        client.startSmsRetriever()
    }

    private fun onVerificationSuccess() {
        isPhoneVerified = true
        countDownTimer?.cancel()

        val llNotice = findViewById<LinearLayout>(R.id.ll_notice)
        val tvNotice = findViewById<TextView>(R.id.tv_notice)
        val ivNotice = findViewById<ImageView>(R.id.iv_notice)

        llNotice.visibility = View.VISIBLE
        tvNotice.text = "인증이 완료되었습니다."
        tvNotice.setTextColor(ContextCompat.getColor(this, R.color.teal500))
        ivNotice.setImageResource(R.drawable.ic_success)

        findViewById<TextInputEditText>(R.id.et_phone_number).isEnabled = false
        findViewById<TextInputEditText>(R.id.et_verify).isEnabled = false
        findViewById<Button>(R.id.phone_number_button).isEnabled = false
        findViewById<Button>(R.id.verify_button).isEnabled = false
        findViewById<Button>(R.id.verify_button).text = "인증완료"
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsVerificationReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsVerificationReceiver, intentFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(smsVerificationReceiver)
        } catch (e: Exception) {}
    }

    private fun startTimer(tvNotice: TextView, llNotice: LinearLayout, ivNotice: ImageView) {
        countDownTimer?.cancel()
        llNotice.visibility = View.VISIBLE
        ivNotice.setImageResource(R.drawable.ic_success)

        countDownTimer = object : CountDownTimer(verificationTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvNotice.text = String.format(Locale.getDefault(), "남은 시간 %02d:%02d", minutes, seconds)
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
