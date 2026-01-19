package com.example.smart_safety_management

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_my_profile)

        // 뒤로가기 버튼 클릭 시 현재 액티비티 종료
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 이름 수정 버튼 클릭 시 팝업 띄우기
        findViewById<ImageView>(R.id.btn_edit_name).setOnClickListener {
            showChangeNameDialog()
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
        val currentName = findViewById<TextView>(R.id.tv_user_name).text.toString()
        etName.setText(currentName)
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
            val newName = etName.text.toString()
            if (newName.isNotEmpty()) {
                // 상위 액티비티의 텍스트 뷰 업데이트
                findViewById<TextView>(R.id.tv_user_name).text = newName
                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }
}
