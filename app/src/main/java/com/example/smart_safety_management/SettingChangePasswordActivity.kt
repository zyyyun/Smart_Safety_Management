package com.example.smart_safety_management

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class SettingChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_change_password)

        val etNewPassword = findViewById<TextInputEditText>(R.id.et_new_password)
        val etReNewPassword = findViewById<TextInputEditText>(R.id.et_re_new_password)
        val tvNotice = findViewById<TextView>(R.id.tv_notice)
        val finishButton = findViewById<Button>(R.id.finish_button)
        val backButton = findViewById<ImageButton>(R.id.backButton)

        // 상단 뒤로가기 버튼 클릭 시 다이얼로그 표시
        backButton.setOnClickListener {
            showExitConfirmDialog()
        }

        // 시스템 뒤로가기 버튼 처리
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmDialog()
            }
        })

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newPassword = etNewPassword.text.toString()
                val reNewPassword = etReNewPassword.text.toString()

                if (reNewPassword.isEmpty()) {
                    tvNotice.visibility = View.INVISIBLE
                    etReNewPassword.setBackgroundResource(R.drawable.bg_edittext)
                } else {
                    tvNotice.visibility = View.VISIBLE
                    if (newPassword == reNewPassword) {
                        tvNotice.text = "비밀번호가 일치합니다."
                        tvNotice.setTextColor(ContextCompat.getColor(this@SettingChangePasswordActivity, R.color.teal500))
                        etReNewPassword.setBackgroundResource(R.drawable.bg_edittext)
                    } else {
                        tvNotice.text = "비밀번호가 일치하지 않습니다."
                        tvNotice.setTextColor(ContextCompat.getColor(this@SettingChangePasswordActivity, R.color.red500))
                        etReNewPassword.setBackgroundResource(R.drawable.bg_edittext_error)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etNewPassword.addTextChangedListener(textWatcher)
        etReNewPassword.addTextChangedListener(textWatcher)

        finishButton.setOnClickListener {
            val newPassword = etNewPassword.text.toString()
            val reNewPassword = etReNewPassword.text.toString()

            if (newPassword.isNotEmpty() && newPassword == reNewPassword) {
                // 성공 로직
                finish()
            }
        }
    }

    private fun showExitConfirmDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.view_confirm_action_dialog, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // "계속 작성" 버튼
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        // "나가기" 버튼
        dialogView.findViewById<Button>(R.id.btn_exit).setOnClickListener {
            alertDialog.dismiss()
            finish()
        }

        alertDialog.show()
        alertDialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)

            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            attributes = params
        }
    }
}
