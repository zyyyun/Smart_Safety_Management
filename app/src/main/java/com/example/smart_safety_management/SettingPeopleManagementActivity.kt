package com.example.smart_safety_management

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingPeopleManagementActivity : AppCompatActivity() {
    private lateinit var adapter: PeopleAdapter
    private var allPeople = mutableListOf<PeopleItem>()
    private var currentFilter = "전체"
    private var currentSearch = ""
    private var isManager = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_people_management)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val filterSpinner = findViewById<Spinner>(R.id.filterSpinner)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)

        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }

        // 데이터 초기화 (서버에서 불러옴)
        allPeople = mutableListOf()

        // 어댑터 설정 (삭제 콜백 전달)
        adapter = PeopleAdapter(allPeople, isManager) { deletedItem -> deleteUser(deletedItem) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 커스텀 드롭다운 어댑터 설정
        val filterItems = resources.getStringArray(R.array.people_filter_array)
        val spinnerAdapter = object : ArrayAdapter<String>(
            this,
            R.layout.spinner_item,
            filterItems
        ) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                // 부모 리스트뷰의 기본 리플(selector)을 투명하게 설정하여 제거
                (parent as? ListView)?.selector = ColorDrawable(Color.TRANSPARENT)

                val view = layoutInflater.inflate(R.layout.item_spinner_dropdown, parent, false)
                val tv = if (view is TextView) view else view.findViewById(android.R.id.text1)
                tv.text = getItem(position)

                // 위치에 따라 다른 배경(리플 효과) 적용
                when (position) {
                    0 -> tv.setBackgroundResource(R.drawable.ripple_orange_top_round)
                    count - 1 -> tv.setBackgroundResource(R.drawable.ripple_orange_bottom_round)
                    else -> tv.setBackgroundResource(R.drawable.ripple_orange_middle)
                }

                return view
            }
        }
        filterSpinner.adapter = spinnerAdapter

        // 필터 스피너 리스너 설정
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = parent?.getItemAtPosition(position).toString()
                applyFilterAndSearch()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 검색창 설정
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString()
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 사용자 목록 불러오기
        loadUsers()
    }

    private fun applyFilterAndSearch() {
        val filteredList = allPeople.filter { item ->
            val matchesFilter = currentFilter == "전체" || item.role == currentFilter
            val matchesSearch = item.name.contains(currentSearch, ignoreCase = true) ||
                    item.phone.contains(currentSearch)
            
            matchesFilter && matchesSearch
        }
        adapter.updateList(filteredList)
    }

    private fun loadUsers() {
        val userId = UserSession.userId
        if (userId == null) {
            ToastUtil.showShort(this, "로그인 정보가 없습니다.")
            return
        }

        RetrofitClient.instance.getUsers(userId).enqueue(object : Callback<GetUsersResponse> {
            override fun onResponse(call: Call<GetUsersResponse>, response: Response<GetUsersResponse>) {
                if (response.isSuccessful) {
                    val users = response.body()?.users ?: emptyList()

                    // 현재 접속한 유저의 권한 확인
                    val currentUser = users.find { it.userId == userId }
                    isManager = currentUser?.userRole == "manager" || currentUser?.userRole == "general_manager"

                    allPeople.clear()
                    users.forEach { user ->
                        val uId = user.userId ?: return@forEach
                        // 본인은 리스트에서 제외
                        if (uId == userId) return@forEach

                        // DB의 role(manager/worker)을 한글로 변환
                        val roleName = if (user.userRole == "manager" || user.userRole == "general_manager") "관리자" else "근로자"
                        // 전화번호 포맷팅
                        val formattedPhone = formatPhoneNumber(user.phoneNum ?: "")
                        
                        // PeopleItem 생성 (id는 userId의 해시코드 사용)
                        allPeople.add(PeopleItem(uId, user.name, formattedPhone, roleName))
                    }

                    // 권한 정보가 업데이트 되었으므로 어댑터 재설정
                    adapter = PeopleAdapter(allPeople, isManager) { deletedItem -> deleteUser(deletedItem) }
                    findViewById<RecyclerView>(R.id.recyclerView).adapter = adapter
                    applyFilterAndSearch()
                } else {
                    ToastUtil.showShort(this@SettingPeopleManagementActivity, "사용자 목록을 불러오지 못했습니다.")
                }
            }

            override fun onFailure(call: Call<GetUsersResponse>, t: Throwable) {
                ToastUtil.showShort(this@SettingPeopleManagementActivity, "네트워크 오류: ${t.message}")
            }
        })
    }

    private fun deleteUser(deletedItem: PeopleItem) {
        if (!isManager) {
            ToastUtil.showShort(this, "관리자만 삭제할 수 있습니다.")
            return
        }

        val request = RemoveFromGroupRequest(userId = deletedItem.userId)
        RetrofitClient.instance.removeFromGroup(request).enqueue(object: Callback<RemoveFromGroupResponse> {
            override fun onResponse(
                call: Call<RemoveFromGroupResponse>,
                response: Response<RemoveFromGroupResponse>
            ) {
                if (response.isSuccessful) {
                    ToastUtil.showShort(this@SettingPeopleManagementActivity, "${deletedItem.name} 님을 그룹에서 제외했습니다.")
                    allPeople.remove(deletedItem)
                    applyFilterAndSearch()
                } else {
                    // 서버에서 보낸 구체적인 에러 메시지 표시
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        val json = org.json.JSONObject(errorBody)
                        json.getString("message")
                    } catch (e: Exception) {
                        "제거에 실패했습니다." // 파싱 실패 시 기본 메시지
                    }
                    ToastUtil.showShort(this@SettingPeopleManagementActivity, errorMessage)
                }
            }
            override fun onFailure(call: Call<RemoveFromGroupResponse>, t: Throwable) {
                ToastUtil.showShort(this@SettingPeopleManagementActivity, "네트워크 오류: ${t.message}")
            }
        })
    }

    private fun formatPhoneNumber(phone: String): String {
        val number = phone.replace(Regex("[^0-9]"), "")
        return if (number.length == 11) {
            "${number.substring(0, 3)}-${number.substring(3, 7)}-${number.substring(7)}"
        } else if (number.length == 10) {
            if (number.startsWith("02")) {
                "${number.substring(0, 2)}-${number.substring(2, 6)}-${number.substring(6)}"
            } else {
                "${number.substring(0, 3)}-${number.substring(3, 6)}-${number.substring(6)}"
            }
        } else {
            number
        }
    }
}