package com.example.smart_safety_management

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
            ivProfile.setImageURI(Uri.parse(it))
            ivProfile.scaleType = ImageView.ScaleType.CENTER_CROP
            ivProfile.setPadding(0, 0, 0, 0)
        }
    }

    private fun applyProfileImage(uri: Uri) {
        // 앨범에서 선택한 경우(content://) 권한 유지를 위해 내부 저장소로 복사
        val savedUri = if (uri.scheme == "content") {
            copyUriToInternalStorage(uri)
        } else {
            uri
        }

        if (savedUri == null) {
            Toast.makeText(this, "이미지를 설정할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val ivProfile = findViewById<ImageView>(R.id.iv_profile)
        ivProfile.setImageURI(savedUri)
        ivProfile.scaleType = ImageView.ScaleType.CENTER_CROP
        ivProfile.setPadding(0, 0, 0, 0)
        UserSession.profileImageUri = savedUri.toString()
    }

    private fun copyUriToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val fileName = "profile_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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