package com.example.smart_safety_management

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingCreateWorkplaceActivity : AppCompatActivity() {

    private lateinit var workplaceAdapter: WorkplaceAdapter
    private var workplaceList = mutableListOf<WorkplaceItem>()

    // 뷰 변수들을 클래스 멤버로 선언하여 다른 메서드에서 접근 가능하게 함
    private lateinit var etWorkplaceName: EditText
    private lateinit var btnCreate: Button
    private lateinit var rvWorkplace: RecyclerView
    private lateinit var line: View
    private lateinit var txtCreatedWorkplace: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_create_workplace)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        etWorkplaceName = findViewById(R.id.et_workplace_name)
        btnCreate = findViewById(R.id.btn_create)
        rvWorkplace = findViewById(R.id.rv_workplace)
        line = findViewById(R.id.line)
        txtCreatedWorkplace = findViewById(R.id.txt_created_workplace)
        
        // 저장된 리스트 불러오기
        fetchWorkplaceList()

        // 어댑터 초기화
        workplaceAdapter = WorkplaceAdapter(
            workplaceList,
            onEditClick = { position ->
                if (position in workplaceList.indices) {
                    showEditDialog(workplaceList[position])
                }
            },
            onDeleteClick = { position ->
                if (position in workplaceList.indices) {
                    showDeleteDialog(workplaceList[position])
                }
            }
        )

        // 리사이클러뷰 설정
        rvWorkplace.apply {
            layoutManager = LinearLayoutManager(this@SettingCreateWorkplaceActivity)
            adapter = workplaceAdapter
        }

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 초기 UI 상태 설정
        updateUIState()

        // 입력값에 따른 버튼 색상 변경
        etWorkplaceName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateUIState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 추가 버튼 클릭 시 리스트에 추가 및 저장
        btnCreate.setOnClickListener {
            if (workplaceList.size >= 1) {
                //ToastUtil.showShort(this, "현장은 하나만 등록할 수 있습니다.")
                return@setOnClickListener
            }

            val name = etWorkplaceName.text.toString().trim()
            val userId = UserSession.userId

            if (name.isNotEmpty()) {
                if (userId == null) {
                    ToastUtil.showShort(this, "로그인 정보가 없습니다.")
                    return@setOnClickListener
                }

                val request = CreateWorkplaceRequest(name, userId)
                RetrofitClient.instance.createWorkplace(request).enqueue(object : Callback<CreateWorkplaceResponse> {
                    override fun onResponse(call: Call<CreateWorkplaceResponse>, response: Response<CreateWorkplaceResponse>) {
                        if (response.isSuccessful) {
                            val newItem = WorkplaceItem(name)
                            workplaceAdapter.addItem(newItem)
                            
                            etWorkplaceName.text.clear()
                            rvWorkplace.scrollToPosition(workplaceList.size - 1)
                            updateUIState() // 추가 후 UI 상태 업데이트
                            ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "현장이 생성되었습니다.")
                        } else {
                            ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "현장 생성 실패")
                        }
                    }
                    override fun onFailure(call: Call<CreateWorkplaceResponse>, t: Throwable) {
                        ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "네트워크 오류: ${t.message}")
                    }
                })
            } /*else {
                ToastUtil.showShort(this, "현장명을 입력해주세요.")
            }*/
        }
    }

    // UI 상태 업데이트 함수 (버튼 및 리스트 관련 뷰 가시성)
    private fun updateUIState() {
        // 버튼 상태 업데이트
        if (etWorkplaceName.text.isNullOrEmpty() || workplaceList.size >= 1) {
            btnCreate.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray50_gray900))
            btnCreate.setTextColor(ContextCompat.getColor(this, R.color.gray400_gray700))
        } else {
            btnCreate.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.orange500))
            btnCreate.setTextColor(ContextCompat.getColor(this, R.color.white_black))
        }

        // 리스트 관련 뷰 가시성 업데이트
        if (workplaceList.isEmpty()) {
            line.visibility = View.GONE
            txtCreatedWorkplace.visibility = View.GONE
            rvWorkplace.visibility = View.GONE
        } else {
            line.visibility = View.VISIBLE
            txtCreatedWorkplace.visibility = View.VISIBLE
            rvWorkplace.visibility = View.VISIBLE
        }
    }

    private fun fetchWorkplaceList() {
        val userId = UserSession.userId
        if (userId == null) {
            // ToastUtil.showShort(this, "로그인 정보가 없습니다.") // 필요 시 주석 해제
            return
        }

        RetrofitClient.instance.getWorkplace(userId).enqueue(object : Callback<GetWorkplaceResponse> {
            override fun onResponse(call: Call<GetWorkplaceResponse>, response: Response<GetWorkplaceResponse>) {
                if (response.isSuccessful) {
                    val items = response.body()?.workplaces ?: emptyList()
                    workplaceList.clear()
                    workplaceList.addAll(items.map { WorkplaceItem(it.name) })
                    workplaceAdapter.notifyDataSetChanged()
                    
                    // ✅ 데이터 로드 후 UI 상태(리스트 가시성) 업데이트 호출
                    updateUIState()
                }
            }
            override fun onFailure(call: Call<GetWorkplaceResponse>, t: Throwable) {
                ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "목록 불러오기 실패")
            }
        })
    }

    // ✅ 현장 이름 수정 다이얼로그
    private fun showEditDialog(item: WorkplaceItem) {
        val userId = UserSession.userId ?: return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.view_change_name, null)
        val builder = AlertDialog.Builder(this).setView(dialogView).setCancelable(true)
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val tvCount = dialogView.findViewById<TextView>(R.id.tv_count)
        val btnClear = dialogView.findViewById<ImageView>(R.id.btn_clear)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

        etName.setText(item.name)
        etName.setSelection(etName.length())
        tvCount.text = "${etName.length()}/20"

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvCount.text = "${s?.length ?: 0}/20"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClear.setOnClickListener { etName.text.clear() }
        btnCancel.setOnClickListener { alertDialog.dismiss() }
        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val request = UpdateWorkplaceRequest(item.name, newName, userId)
                    RetrofitClient.instance.updateWorkplace(request).enqueue(object : Callback<UpdateWorkplaceResponse> {
                        override fun onResponse(call: Call<UpdateWorkplaceResponse>, response: Response<UpdateWorkplaceResponse>) {
                            if (response.isSuccessful) {
                                ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "수정되었습니다.")
                                fetchWorkplaceList() // 목록 새로고침
                                alertDialog.dismiss()
                            } else {
                                ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "수정 실패")
                            }
                        }
                        override fun onFailure(call: Call<UpdateWorkplaceResponse>, t: Throwable) {
                            ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "네트워크 오류")
                        }
                    })
                }
            }
        
        alertDialog.show()

        val marginPx = (24 * resources.displayMetrics.density).toInt()
        val width = resources.displayMetrics.widthPixels - (marginPx * 2)
        alertDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ✅ 현장 삭제 확인 다이얼로그
    private fun showDeleteDialog(item: WorkplaceItem) {
        val userId = UserSession.userId ?: return

        // 커스텀 다이얼로그 뷰 인플레이트
        val dialogView = layoutInflater.inflate(R.layout.view_confirm_action_dialog, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_title_message)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_message)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnExit = dialogView.findViewById<Button>(R.id.btn_exit)

        tvTitle.text = "현장 삭제"
        tvMessage.text = "'${item.name}' 현장을 삭제하시겠습니까?"

        // 취소 버튼 설정
        btnCancel.visibility = View.VISIBLE
        btnCancel.text = "취소"
        btnCancel.setOnClickListener { dialog.dismiss() }

        // 삭제(확인) 버튼 설정
        btnExit.text = "삭제"
        btnExit.setOnClickListener {
            dialog.dismiss()
            val request = DeleteWorkplaceRequest(item.name, userId)
            RetrofitClient.instance.deleteWorkplace(request).enqueue(object : Callback<DeleteWorkplaceResponse> {
                override fun onResponse(call: Call<DeleteWorkplaceResponse>, response: Response<DeleteWorkplaceResponse>) {
                    if (response.isSuccessful) {
                        ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "삭제되었습니다.")
                        // 리스트에서 제거 및 UI 업데이트
                        val index = workplaceList.indexOfFirst { it.name == item.name }
                        if (index != -1) {
                            workplaceList.removeAt(index)
                            workplaceAdapter.notifyItemRemoved(index)
                            updateUIState()
                        }
                    } else {
                        ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "삭제 실패")
                    }
                }
                override fun onFailure(call: Call<DeleteWorkplaceResponse>, t: Throwable) {
                    ToastUtil.showShort(this@SettingCreateWorkplaceActivity, "네트워크 오류")
                }
            })
        }

        dialog.show()
        dialog.window?.apply {
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            attributes = params
        }
    }
}
