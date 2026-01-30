package com.example.smart_safety_management

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_create_workplace)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val etWorkplaceName = findViewById<EditText>(R.id.et_workplace_name)
        val btnCreate = findViewById<Button>(R.id.btn_create)
        val rvWorkplace = findViewById<RecyclerView>(R.id.rv_workplace)
        val line = findViewById<View>(R.id.line)
        val txtCreatedWorkplace = findViewById<TextView>(R.id.txt_created_workplace)

        // 저장된 리스트 불러오기
        fetchWorkplaceList()

        // UI 상태 업데이트 함수 (버튼 및 리스트 관련 뷰 가시성)
        fun updateUIState() {
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

        // 어댑터 초기화
        workplaceAdapter = WorkplaceAdapter(
            workplaceList,
            onEditClick = { _ ->
                // TODO: 편집 기능 구현
            },
            onDeleteClick = { position ->
                val itemToDelete = workplaceList[position]
                val userId = UserSession.userId

                if (userId != null) {
                    val request = DeleteWorkplaceRequest(itemToDelete.name, userId)
                    RetrofitClient.instance.deleteWorkplace(request).enqueue(object : Callback<DeleteWorkplaceResponse> {
                        override fun onResponse(call: Call<DeleteWorkplaceResponse>, response: Response<DeleteWorkplaceResponse>) {
                            if (response.isSuccessful) {
                                workplaceAdapter.removeItem(position)
                                updateUIState() // 삭제 후 UI 상태 업데이트
                                Toast.makeText(this@SettingCreateWorkplaceActivity, "현장이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            } else if (response.code() == 404) {
                                // 서버에 데이터가 없는 경우(404), 로컬 목록에서도 삭제 처리
                                workplaceAdapter.removeItem(position)
                                updateUIState()
                                Toast.makeText(this@SettingCreateWorkplaceActivity, "서버에 없는 현장을 목록에서 삭제했습니다.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@SettingCreateWorkplaceActivity, "삭제 실패: ${response.message()}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<DeleteWorkplaceResponse>, t: Throwable) {
                            Toast.makeText(this@SettingCreateWorkplaceActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
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
                //Toast.makeText(this, "현장은 하나만 등록할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = etWorkplaceName.text.toString().trim()
            val userId = UserSession.userId

            if (name.isNotEmpty()) {
                if (userId == null) {
                    Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@SettingCreateWorkplaceActivity, "현장이 생성되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SettingCreateWorkplaceActivity, "현장 생성 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<CreateWorkplaceResponse>, t: Throwable) {
                        Toast.makeText(this@SettingCreateWorkplaceActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } /*else {
                Toast.makeText(this, "현장명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }*/
        }
    }

    private fun fetchWorkplaceList() {
        val userId = UserSession.userId ?: return
        RetrofitClient.instance.getWorkplace(userId).enqueue(object : Callback<GetWorkplaceResponse> {
            override fun onResponse(call: Call<GetWorkplaceResponse>, response: Response<GetWorkplaceResponse>) {
                if (response.isSuccessful) {
                    val items = response.body()?.workplaces ?: emptyList()
                    workplaceList.clear()
                    workplaceList.addAll(items.map { WorkplaceItem(it.name) })
                    workplaceAdapter.notifyDataSetChanged()
                    // UI 상태 업데이트는 어댑터 갱신 후 호출
                    // updateUIState()는 onCreate 내부 함수라 직접 호출이 어려우므로, 
                    // 필요하다면 etWorkplaceName의 텍스트 변경 리스너를 트리거하거나 별도 메서드로 분리해야 함.
                    // 여기서는 리스트 갱신만으로도 RecyclerView는 업데이트됨.
                }
            }
            override fun onFailure(call: Call<GetWorkplaceResponse>, t: Throwable) {
                Toast.makeText(this@SettingCreateWorkplaceActivity, "목록 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
