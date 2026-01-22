package com.example.smart_safety_management

import DailyCheckItem
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import android.content.Intent
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

private val dailyCheckMap = mapOf(
    7 to listOf(
        DailyCheckItem("B구역 1열", "정리미흡으로 안전사고 발생 우려", "미점검"),
        DailyCheckItem("C구역 3열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
    ),
    12 to listOf(
        DailyCheckItem("A구역 1열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
    ),
    22 to listOf(
        DailyCheckItem("A구역 4열", "정리미흡으로 안전사고 발생 우려", "미점검"),
        DailyCheckItem("A구역 4열", "정리미흡으로 안전사고 발생 우려", "미점검"),
        DailyCheckItem("D구역 2열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
        DailyCheckItem("D구역 1열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
        DailyCheckItem("D구역 2열", "정리미흡으로 안전사고 발생 우려", "점검완료"),
    ),
    25 to listOf(
        DailyCheckItem("C구역 2열", "정리미흡으로 안전사고 발생 우려", "미점검")
    ),
    26 to listOf(
        DailyCheckItem("A구역 4열", "정리미흡으로 인적사고 발생 우려", "미점검")
    )
)

class HomeWorkerActivity : AppCompatActivity() {

    private lateinit var dailyAdapter: DailyCheckAdapter
    private lateinit var eventAdapter: WorkerEventAdapter
    private var selectedDay: Int? = null
    private var isInviteDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_home_worker)

        initUI()
    }

    private fun initUI() {
        updateProfileName()
        updateWorkerDay()
        checkInviteCodeDialog()

        val topBar = findViewById<View>(R.id.top_bar)
        val btnAlarm = topBar.findViewById<ImageButton>(R.id.btn_alarm)
        val alarmDot = findViewById<View>(R.id.view_alarm_dot)
        val btnSetting = topBar.findViewById<ImageButton>(R.id.btn_setting)

        alarmDot.visibility = if (true) View.VISIBLE else View.GONE

        // 알림 버튼 -> 알림 화면
        btnAlarm.setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }

        // 설정 버튼 -> 근로자용 설정 화면
        btnSetting.setOnClickListener {
            startActivity(Intent(this, SettingWorkerActivity::class.java))
        }

        // 일일안전점검 리스트 초기화
        val rvDaily = findViewById<RecyclerView>(R.id.rv_daily_check)
        rvDaily.layoutManager = LinearLayoutManager(this)
        dailyAdapter = DailyCheckAdapter(emptyList())
        rvDaily.adapter = dailyAdapter

        // 조치요청 리스트 초기화
        val rvEvent = findViewById<RecyclerView>(R.id.rv_worker_event)
        rvEvent.layoutManager = LinearLayoutManager(this)
        eventAdapter = WorkerEventAdapter(getSampleEvents())
        rvEvent.adapter = eventAdapter

        selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        dailyAdapter.initTooltip()

        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)
        scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, scrollX, scrollY, oldX, oldY ->
                if (scrollX != oldX || scrollY != oldY) {
                    dailyAdapter.updateTooltipPosition()
                }
            }
        )

        updateDailyCheckList(selectedDay)
    }

    private fun getSampleEvents(): List<Pair<EventData, EventStatus>> {
        return listOf(
            EventData(accidentType = "위험", location = "C구역 2열", content = "화재사고가 감지되었습니다.", "지금") to EventStatus.PENDING,
            EventData(accidentType = "경고", location = "C구역 2열", content = "쓰러짐이 감지되었습니다.", "1분 전") to EventStatus.PENDING,
            EventData(accidentType = "주의", location = "C구역 2열", content = "이동경로 미정돈이 감지되었습니다.", "3분 전") to EventStatus.PENDING,
            // 조치완료 및 오탐처리 추가
            EventData(accidentType = "주의", location = "C구역 2열", content = "안전고리 미착용이 감지되었습니다.", "1분 전") to EventStatus.COMPLETED,
            EventData(accidentType = "경고", location = "C구역 2열", content = "안전고리 미착용이 감지되었습니다.", "1분 전") to EventStatus.COMPLETED
            //EventData(accidentType = "경고", location = "C구역 2열", content = "안전고리 미착용이 감지되었습니다.", "1분 전") to EventStatus.FALSE_DETECTION
        )
    }

    private fun updateWorkerDay() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val tvWorkerDay = findViewById<TextView>(R.id.tv_worker_day)
        tvWorkerDay?.text = "${year}년 ${month}월 ${day}일"
    }

    override fun onResume() {
        super.onResume()
        updateProfileName()
    }

    private fun updateProfileName() {
        val profileBar = findViewById<View>(R.id.profile_bar)
        profileBar?.findViewById<TextView>(R.id.tv_user_name)?.text = UserSession.userName
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            dailyAdapter.dismissTooltip()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun updateDailyCheckList(day: Int?) {
        val list = dailyCheckMap[day] ?: emptyList()
        dailyAdapter.updateList(list)

        val totalCount = list.size
        val uncheckedCount = list.count { it.status == "미점검" }
        
        val progressText = "$uncheckedCount/$totalCount"
        val spannable = SpannableString(progressText)

        val orangeColor = ContextCompat.getColor(this, R.color.orange500)
        val grayColor = ContextCompat.getColor(this, R.color.gray600)
        
        val separatorIndex = progressText.indexOf("/")
        
        if (separatorIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(orangeColor), 0, separatorIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(grayColor), separatorIndex, progressText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        findViewById<TextView>(R.id.tv_progress)?.text = spannable
        adjustRecyclerMinHeight()
    }

    private fun adjustRecyclerMinHeight() {
        val rv = findViewById<RecyclerView>(R.id.rv_daily_check)
        val spacer = findViewById<View>(R.id.rv_spacer)
        val scroll = findViewById<NestedScrollView>(R.id.home_scroll)

        rv?.post {
            val rvBottom = rv.bottom
            val scrollBottom = scroll.bottom
            val gap = scrollBottom - rvBottom
            spacer?.layoutParams?.height = if (gap > 0) gap else 0
            spacer?.requestLayout()
        }
    }

    private fun checkInviteCodeDialog() {
        if (!UserSession.isInviteDoneWorker) showInviteCodeDialog()
    }

    private fun showInviteCodeDialog() {
        isInviteDialogShowing = true
        val dialogView = layoutInflater.inflate(R.layout.invite_code, null)
        val etInviteCode = dialogView.findViewById<EditText>(R.id.et_invite_code)
        val btnSubmit = dialogView.findViewById<View>(R.id.btn_submit)
        val tvError = dialogView.findViewById<View>(R.id.tv_error)
        val tvSkip = dialogView.findViewById<TextView>(R.id.tv_skip_invite_code)

        tvSkip.paintFlags = tvSkip.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnSubmit.setOnClickListener {
            val inputCode = etInviteCode.text.toString().trim()
            if (inputCode == "1234") {
                UserSession.isInviteDoneWorker = true
                UserSession.isInviteSuccessWorker = true
                dialog.dismiss()
            } else {
                tvError.visibility = View.VISIBLE
                etInviteCode.setBackgroundResource(R.drawable.bg_edittext_error)
            }
        }

        etInviteCode.doAfterTextChanged {
            tvError.visibility = View.GONE
            etInviteCode.setBackgroundResource(R.drawable.bg_edittext)
        }

        tvSkip.setOnClickListener {
            UserSession.isInviteDoneWorker = true
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val params = dialog.window?.attributes
        params?.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.attributes = params
    }
}
