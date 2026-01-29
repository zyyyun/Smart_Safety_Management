package com.example.smart_safety_management

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.bumptech.glide.Glide
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SettingProfileActivity : AppCompatActivity() {

    private var photoUri: Uri? = null

    // 카메라 권한 요청 런처
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openCamera()
        else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    // 앨범에서 사진 선택 런처
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { applyProfileImage(it) }
    }

    // 카메라 촬영 런처
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { applyProfileImage(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setting_my_profile)

        loadUserData()

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btn_edit_name).setOnClickListener {
            showChangeNameDialog()
        }

        findViewById<MaterialCardView>(R.id.btn_camera).setOnClickListener {
            showImagePickerOptions()
        }

        // 로그아웃 버튼 클릭 시 즉시 로그아웃 수행
        findViewById<TextView>(R.id.tv_logout).setOnClickListener {
            performLogout()
        }

        // 회원 탈퇴 버튼 클릭 시
        findViewById<View>(R.id.btn_withdraw).setOnClickListener {
            showWithdrawConfirmDialog()
        }

        setupPhoneEditLogic()
        setupEmailEditLogic()
    }

    private fun loadUserData() {
        findViewById<TextView>(R.id.tv_user_name).text = UserSession.userName
        val authorityTv = findViewById<TextView>(R.id.tv_user_name_authority)
        authorityTv.text = if (UserSession.userRole == UserRole.MANAGER) "관리자" else "근로자"

        findViewById<TextView>(R.id.tv_user_phone).text = formatPhoneNumber(UserSession.userPhone)
        findViewById<TextView>(R.id.tv_user_email).text = UserSession.userEmail ?: ""

        UserSession.profileImageUri?.let {
            val ivProfile = findViewById<ImageView>(R.id.iv_profile)
            Glide.with(this).load(it).centerCrop().into(ivProfile)
            ivProfile.setPadding(0, 0, 0, 0)
        }
    }

    private fun performLogout() {
        // 1. 세션 정보 초기화 (SharedPreferences 포함)
        UserSession.clearSession(this)
        
        // 현장 목록 로컬 데이터 초기화
        getSharedPreferences("workplace_prefs", MODE_PRIVATE).edit().clear().apply()
        
        // 2. 스플래시(첫 화면)로 이동 및 기존 스택 제거
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun showWithdrawConfirmDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.view_confirm_action_dialog, null)
        val builder = AlertDialog.Builder(this).setView(dialogView).setCancelable(true)
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tv_title_message).text = "회원 탈퇴"
        dialogView.findViewById<TextView>(R.id.tv_message).text = "정말로 탈퇴하시겠습니까?\n계정 정보가 모두 삭제됩니다."
        
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnWithdraw = dialogView.findViewById<Button>(R.id.btn_exit)

        btnCancel.text = "취소"
        btnWithdraw.text = "탈퇴"

        btnCancel.setOnClickListener { alertDialog.dismiss() }
        btnWithdraw.setOnClickListener {
            alertDialog.dismiss()
            performWithdraw()
        }

        alertDialog.show()

        // 다이얼로그 너비 설정
        val marginPx = (24 * resources.displayMetrics.density).toInt()
        val width = resources.displayMetrics.widthPixels - (marginPx * 2)
        alertDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun performWithdraw() {
        val userId = UserSession.userId
        if (userId == null) {
            Toast.makeText(this, "로그인 정보가 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("Withdraw", "Attempting to delete account for user: $userId")

        RetrofitClient.instance.deleteAccount(userId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("Withdraw", "Account deletion successful")
                    Toast.makeText(this@SettingProfileActivity, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    
                    // 세션 및 로컬 데이터 초기화
                    UserSession.clearSession(this@SettingProfileActivity)
                    getSharedPreferences("workplace_prefs", MODE_PRIVATE).edit().clear().apply()

                    val intent = Intent(this@SettingProfileActivity, SplashActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "탈퇴 처리 중 오류가 발생했습니다."
                    Log.e("Withdraw", "Account deletion failed: $errorMsg")
                    Toast.makeText(this@SettingProfileActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("Withdraw", "Network error during account deletion", t)
                Toast.makeText(this@SettingProfileActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun applyProfileImage(uri: Uri) {
        val ivProfile = findViewById<ImageView>(R.id.iv_profile)
        // 로컬 이미지를 먼저 보여줌 (사용자 경험 향상)
        Glide.with(this).load(uri).centerCrop().into(ivProfile)
        ivProfile.setPadding(0, 0, 0, 0)

        // 1. Uri를 File로 변환
        val file = uriToFile(uri)
        if (file == null) {
            Toast.makeText(this, "이미지 파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 서버로 이미지 업로드 요청
        val requestFile = RequestBody.create(MediaType.parse("image/*"), file)
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

        RetrofitClient.instance.uploadImage(body).enqueue(object : Callback<UploadImageResponse> {
            override fun onResponse(call: Call<UploadImageResponse>, response: Response<UploadImageResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val imageUrl = response.body()!!.imageUrl
                    
                    // 3. 업로드 성공 시 반환된 URL로 프로필 업데이트
                    updateProfileWithUrl(imageUrl)
                } else {
                    Toast.makeText(this@SettingProfileActivity, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<UploadImageResponse>, t: Throwable) {
                Toast.makeText(this@SettingProfileActivity, "업로드 네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Uri를 임시 파일로 변환하는 헬퍼 함수
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("upload", ".jpg", cacheDir)
            val outputStream = java.io.FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun updateProfileWithUrl(imageUrl: String) {
        val userId = UserSession.userId ?: return
        
        val request = UpdateProfileRequest(userId = userId, profileImageUri = imageUrl)
        RetrofitClient.instance.updateProfile(request).enqueue(object : Callback<UpdateProfileResponse> {
            override fun onResponse(call: Call<UpdateProfileResponse>, response: Response<UpdateProfileResponse>) {
                if (response.isSuccessful) {
                    UserSession.profileImageUri = imageUrl // 세션에도 URL 저장
                    UserSession.saveSession(this@SettingProfileActivity) // 변경된 이미지 URL 저장
                    Toast.makeText(this@SettingProfileActivity, "프로필 사진이 서버에 저장되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingProfileActivity, "프로필 정보 업데이트 실패", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<UpdateProfileResponse>, t: Throwable) {
                Toast.makeText(this@SettingProfileActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("카메라로 촬영", "앨범에서 선택")
        AlertDialog.Builder(this)
            .setTitle("프로필 사진 설정")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }.show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile(
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_",
            ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        photoUri = uri
        takePhotoLauncher.launch(uri)
    }

    private fun setupPhoneEditLogic() {
        val layoutPhoneView = findViewById<LinearLayout>(R.id.layout_phone_view)
        val layoutPhoneEdit = findViewById<LinearLayout>(R.id.layout_phone_edit)
        val tvPhoneValue = findViewById<TextView>(R.id.tv_user_phone)
        val etPhoneEdit = findViewById<TextInputEditText>(R.id.et_phone_edit)
        val btnConfirm = findViewById<ImageView>(R.id.btn_phone_confirm)
        val btnCancel = findViewById<ImageView>(R.id.btn_phone_cancel)

        layoutPhoneView?.setOnClickListener {
            layoutPhoneView.visibility = View.GONE
            layoutPhoneEdit.visibility = View.VISIBLE
            etPhoneEdit.setText(tvPhoneValue.text)
            etPhoneEdit.requestFocus()
        }

        btnCancel?.setOnClickListener {
            layoutPhoneEdit.visibility = View.GONE
            layoutPhoneView?.visibility = View.VISIBLE
        }

        btnConfirm?.setOnClickListener {
            val rawInput = etPhoneEdit.text.toString()
            // DB 저장용: 숫자만 추출 (하이픈 제거)
            val newPhone = rawInput.replace(Regex("[^0-9]"), "")
            val userId = UserSession.userId

            if (userId == null) {
                Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = UpdateProfileRequest(userId = userId, phoneNum = newPhone)
            RetrofitClient.instance.updateProfile(request).enqueue(object : Callback<UpdateProfileResponse> {
                override fun onResponse(call: Call<UpdateProfileResponse>, response: Response<UpdateProfileResponse>) {
                    if (response.isSuccessful) {
                        UserSession.userPhone = newPhone
                        UserSession.saveSession(this@SettingProfileActivity) // 변경된 정보 저장
                        tvPhoneValue.text = formatPhoneNumber(newPhone)
                        layoutPhoneEdit.visibility = View.GONE
                        layoutPhoneView?.visibility = View.VISIBLE
                        Toast.makeText(this@SettingProfileActivity, "전화번호가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SettingProfileActivity, "수정 실패", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<UpdateProfileResponse>, t: Throwable) {
                    Toast.makeText(this@SettingProfileActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupEmailEditLogic() {
        val layoutEmailView = findViewById<LinearLayout>(R.id.layout_email_view)
        val layoutEmailEdit = findViewById<LinearLayout>(R.id.layout_email_edit)
        val tvEmailValue = findViewById<TextView>(R.id.tv_user_email)
        val etEmailEdit = findViewById<TextInputEditText>(R.id.et_email_edit)
        val btnConfirm = findViewById<ImageView>(R.id.btn_email_confirm)
        val btnCancel = findViewById<ImageView>(R.id.btn_email_cancel)

        layoutEmailView?.setOnClickListener {
            layoutEmailView.visibility = View.GONE
            layoutEmailEdit.visibility = View.VISIBLE
            etEmailEdit.setText(tvEmailValue.text)
            etEmailEdit.requestFocus()
        }

        btnCancel?.setOnClickListener {
            layoutEmailEdit.visibility = View.GONE
            layoutEmailView?.visibility = View.VISIBLE
        }

        btnConfirm?.setOnClickListener {
            val newEmail = etEmailEdit.text.toString()
            val userId = UserSession.userId

            if (userId == null) {
                Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = UpdateProfileRequest(userId = userId, email = newEmail)
            RetrofitClient.instance.updateProfile(request).enqueue(object : Callback<UpdateProfileResponse> {
                override fun onResponse(call: Call<UpdateProfileResponse>, response: Response<UpdateProfileResponse>) {
                    if (response.isSuccessful) {
                        UserSession.userEmail = newEmail
                        UserSession.saveSession(this@SettingProfileActivity) // 변경된 정보 저장
                        tvEmailValue.text = newEmail
                        layoutEmailEdit.visibility = View.GONE
                        layoutEmailView?.visibility = View.VISIBLE
                        Toast.makeText(this@SettingProfileActivity, "이메일이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SettingProfileActivity, "수정 실패", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<UpdateProfileResponse>, t: Throwable) {
                    Toast.makeText(this@SettingProfileActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showChangeNameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.view_change_name, null)
        val builder = AlertDialog.Builder(this).setView(dialogView).setCancelable(true)
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val tvCount = dialogView.findViewById<TextView>(R.id.tv_count)
        val btnClear = dialogView.findViewById<ImageView>(R.id.btn_clear)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

        etName.setText(UserSession.userName)
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
            if (newName.isNotEmpty()) {
                val userId = UserSession.userId
                if (userId == null) {
                    Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val request = UpdateProfileRequest(userId = userId, name = newName)
                RetrofitClient.instance.updateProfile(request).enqueue(object : Callback<UpdateProfileResponse> {
                    override fun onResponse(call: Call<UpdateProfileResponse>, response: Response<UpdateProfileResponse>) {
                        if (response.isSuccessful) {
                            UserSession.userName = newName
                            UserSession.saveSession(this@SettingProfileActivity) // 변경된 정보 저장
                            findViewById<TextView>(R.id.tv_user_name).text = newName
                            Toast.makeText(this@SettingProfileActivity, "이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                        } else {
                            Toast.makeText(this@SettingProfileActivity, "이름 변경 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<UpdateProfileResponse>, t: Throwable) {
                        Toast.makeText(this@SettingProfileActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
        alertDialog.show()

        val marginPx = (24 * resources.displayMetrics.density).toInt()
        val width = resources.displayMetrics.widthPixels - (marginPx * 2)
        alertDialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // 전화번호 포맷팅 함수 (01012345678 -> 010-1234-5678)
    private fun formatPhoneNumber(phone: String?): String {
        if (phone.isNullOrEmpty()) return ""
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
