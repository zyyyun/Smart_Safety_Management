package com.example.smart_safety_management

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.text
import com.google.android.material.textfield.TextInputEditText

class SettingProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_my_profile)

        // 초기 데이터 로드 (전역 변수 UserSession 사용)
        loadUserData()

        // 뒤로가기 버튼 클릭 시 현재 액티비티 종료
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 1. 이름 수정 버튼 (다이얼로그 방식)
        findViewById<ImageView>(R.id.btn_edit_name).setOnClickListener {
            showChangeNameDialog()
        }

        // 2. 휴대폰 번호 수정 로직 (인라인 방식)
        setupPhoneEditLogic()

        // 3. 이메일 수정 로직 (인라인 방식)
        setupEmailEditLogic()
    }

    private fun loadUserData() {
        findViewById<TextView>(R.id.tv_user_name).text = UserSession.userName
        findViewById<TextView>(R.id.tv_user_phone).text
        findViewById<TextView>(R.id.tv_user_email).text
    }

    private fun setupPhoneEditLogic() {
        val tvPhoneValue = findViewById<TextView>(R.id.tv_user_phone)
        val btnEditPhone = tvPhoneValue.parent as LinearLayout // tv_user_phone의 부모
        val layoutPhoneEdit = findViewById<LinearLayout>(R.id.layout_phone_edit)
        val etPhoneEdit = findViewById<TextInputEditText>(R.id.et_phone_edit)
        val btnConfirm = findViewById<ImageView>(R.id.btn_phone_confirm)
        val btnCancel = findViewById<ImageView>(R.id.btn_phone_cancel)

        // '>' 버튼 클릭 시 (정확히는 그 텍스트뷰 영역 포함 레이아웃 클릭 시)
        btnEditPhone.setOnClickListener {
            btnEditPhone.visibility = View.GONE
            layoutPhoneEdit.visibility = View.VISIBLE
            etPhoneEdit.setText(tvPhoneValue.text)
            etPhoneEdit.requestFocus()
        }

        // 'X' 취소 버튼 클릭 시
        btnCancel.setOnClickListener {
            layoutPhoneEdit.visibility = View.GONE
            btnEditPhone.visibility = View.VISIBLE
        }

        // 'V' 확인 버튼 클릭 시
        btnConfirm.setOnClickListener {
            val newPhone = etPhoneEdit.text.toString()
            if (newPhone.isNotEmpty()) {
                // TODO: 여기에 백엔드 API 연결 (전화번호 업데이트)
                tvPhoneValue.text = newPhone

                layoutPhoneEdit.visibility = View.GONE
                btnEditPhone.visibility = View.VISIBLE
            }
        }
    }

    // setupEmailEditLogic 함수 추가 및 호출
    private fun setupEmailEditLogic() {
        val layoutEmailView = findViewById<LinearLayout>(R.id.layout_email_view)
        val layoutEmailEdit = findViewById<LinearLayout>(R.id.layout_email_edit)
        val tvEmailValue = findViewById<TextView>(R.id.tv_user_email)
        val etEmailEdit = findViewById<TextInputEditText>(R.id.et_email_edit)
        val btnEditEmail = findViewById<ImageView>(R.id.btn_edit_email)
        val btnConfirm = findViewById<ImageView>(R.id.btn_email_confirm)
        val btnCancel = findViewById<ImageView>(R.id.btn_email_cancel)

        // '>' 버튼 클릭 시
        layoutEmailView.setOnClickListener {
            layoutEmailView.visibility = View.GONE
            layoutEmailEdit.visibility = View.VISIBLE
            etEmailEdit.setText(tvEmailValue.text)
            etEmailEdit.requestFocus()
        }

        // 'X' 버튼 클릭 시
        btnCancel.setOnClickListener {
            layoutEmailEdit.visibility = View.GONE
            layoutEmailView.visibility = View.VISIBLE
        }

        // 'V' 확인 버튼 클릭 시
        btnConfirm.setOnClickListener {
            val newEmail = etEmailEdit.text.toString()
            if (newEmail.isNotEmpty()) {
                // TODO: 백엔드 API 호출 (이메일 업데이트)
                tvEmailValue.text = newEmail
                layoutEmailEdit.visibility = View.GONE
                layoutEmailView.visibility = View.VISIBLE
            }
        }
    }

    private fun showChangeNameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.view_change_name, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val tvCount = dialogView.findViewById<TextView>(R.id.tv_count)
        val btnClear = dialogView.findViewById<ImageView>(R.id.btn_clear)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

        // 현재 이름을 EditText에 세팅
        etName.setText(UserSession.userName)
        etName.setSelection(etName.length()) // 커서를 끝으로 이동
        tvCount.text = "${etName.length()}/20"

        // 글자 수 실시간 업데이트
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                tvCount.text = "$length/20"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 엔터 키 입력 방지 (아래 버튼으로 변경 및 줄바꿈 차단)
        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSave.performClick()
                true
            } else {
                false
            }
        }

        // X 버튼 클릭 시 입력 내용 삭제
        btnClear.setOnClickListener {
            etName.text.clear()
        }

        // 취소 버튼
        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        // 저장 버튼
        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isNotEmpty()) {
                // 전역 변수 UserSession에 저장 (통일됨)
                UserSession.userName = newName

                // 현재 UI 업데이트
                findViewById<TextView>(R.id.tv_user_name).text = newName
                alertDialog.dismiss()
            }
        }

        alertDialog.show()

        // 양옆 마진 24dp 적용
        val marginPx = (24 * resources.displayMetrics.density).toInt()
        val width = resources.displayMetrics.widthPixels - (marginPx * 2)
        alertDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}