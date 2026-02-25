@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.smart_safety_management.screens.firealarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smart_safety_management.R
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.GetFireDetectorsResponse
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private val CriticalColor = Color(0xFFF97316)
// #06D6A033 (RGBA) -> ARGB 0x33 06 D6 A0
private val OnFg = Color(0xFF06D6A0)

enum class AlarmStatus { NORMAL, NEED_CHECK, OFFLINE }

data class AlarmDevice(
    val id: String,
    val name: String,
    val status: AlarmStatus,
    val statusText: String,
    val isOn: Boolean,
    val isCritical: Boolean = false
)

data class FireAlarmUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val devices: List<AlarmDevice> = emptyList()
)

interface FireAlarmRepository {
    suspend fun fetchDevices(): List<AlarmDevice>
    suspend fun setDevicePower(id: String, isOn: Boolean): Boolean
}

class FakeFireAlarmRepository : FireAlarmRepository {
    private var items = listOf(
        AlarmDevice("1", "1층 로비 화재경보기", AlarmStatus.NORMAL, "정상 작동 중", isOn = true),
        AlarmDevice("2", "2층 사무실 화재경보기", AlarmStatus.NEED_CHECK, "점검 필요 (센서 오류)", isOn = false, isCritical = true),
        AlarmDevice("3", "3층 휴게실 연기감지기", AlarmStatus.NORMAL, "정상 작동 중", isOn = true),
        AlarmDevice("4", "지하 주차장 A구역", AlarmStatus.NORMAL, "정상 작동 중", isOn = true),
    )

    override suspend fun fetchDevices(): List<AlarmDevice> {
        delay(350)
        return items
    }

    override suspend fun setDevicePower(id: String, isOn: Boolean): Boolean {
        delay(200)
        items = items.map { if (it.id == id) it.copy(isOn = isOn) else it }
        return true
    }
}

class RealFireAlarmRepository : FireAlarmRepository {
    override suspend fun fetchDevices(): List<AlarmDevice> {
        return suspendCoroutine { cont ->
            val userId = UserSession.userId
            if (userId == null) {
                cont.resumeWithException(Exception("로그인 정보가 없습니다."))
                return@suspendCoroutine
            }

            RetrofitClient.instance.getFireDetectors(userId).enqueue(object : Callback<GetFireDetectorsResponse> {
                override fun onResponse(
                    call: Call<GetFireDetectorsResponse>,
                    response: Response<GetFireDetectorsResponse>
                ) {
                    if (response.isSuccessful) {
                        val list = response.body()?.fireDetectors ?: emptyList()
                        val devices = list.map { dto ->
                            val isNormal = dto.status == "NORMAL" || dto.status == "정상"
                            AlarmDevice(
                                id = dto.detectorId.toString(),
                                name = dto.detectorName,
                                status = if (isNormal) AlarmStatus.NORMAL else AlarmStatus.NEED_CHECK,
                                statusText = if (isNormal) "정상 작동 중" else dto.status,
                                isOn = dto.isActive,
                                isCritical = !isNormal
                            )
                        }
                        cont.resume(devices)
                    } else {
                        cont.resumeWithException(Exception("서버 오류: ${response.code()}"))
                    }
                }

                override fun onFailure(call: Call<GetFireDetectorsResponse>, t: Throwable) {
                    cont.resumeWithException(t)
                }
            })
        }
    }

    override suspend fun setDevicePower(id: String, isOn: Boolean): Boolean {
        // API 미구현: UI 반영을 위해 true 반환
        return true
    }
}

class FireAlarmViewModel(
    private val repo: FireAlarmRepository = RealFireAlarmRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FireAlarmUiState(isLoading = true))
    val uiState: StateFlow<FireAlarmUiState> = _uiState

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repo.fetchDevices() }
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoading = false, devices = list, errorMessage = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "불러오기 실패") }
                }
        }
    }

    fun togglePower(id: String, newValue: Boolean) {
        val before = _uiState.value.devices
        _uiState.update { st ->
            st.copy(devices = st.devices.map { if (it.id == id) it.copy(isOn = newValue) else it })
        }
        viewModelScope.launch {
            val ok = runCatching { repo.setDevicePower(id, newValue) }.getOrNull() == true
            if (!ok) _uiState.update { it.copy(devices = before, errorMessage = "전원 변경 실패") }
        }
    }
}

@Composable
fun FireAlarmManageRoute(
    vm: FireAlarmViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    FireAlarmManageScreen(
        state = state,
        onRefresh = vm::refresh,
        onToggle = vm::togglePower,
        onClearError = {},
        onBack = onBack
    )
}

@Composable
fun FireAlarmManageScreen(
    state: FireAlarmUiState,
    onRefresh: () -> Unit,
    onToggle: (id: String, newValue: Boolean) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit
) {
    val c = LocalSafeColors.current

    val devices = state.devices
    val total = devices.size
    val normal = devices.count { it.status == AlarmStatus.NORMAL }
    val needCheck = devices.count { it.status == AlarmStatus.NEED_CHECK }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.backicon),
                            contentDescription = "뒤로가기",
                            tint = if (c.isDark) Color.White else Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                title = {
                    Text(
                        "화재경보기 관리",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = c.text
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = c.topBar
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .padding(padding)
        ) {
            Spacer(Modifier.height(12.dp))

            SummaryRow(
                total = total,
                normal = normal,
                needCheck = needCheck,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "장치 목록",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = c.text
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRefresh() }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.reset),
                        contentDescription = "새로고침",
                        tint = CriticalColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "새로고침",
                        color = CriticalColor,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        FireAlarmDeviceCard(device = device)
                    }
                }

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = CriticalColor
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage ?: "",
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    total: Int,
    normal: Int,
    needCheck: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryChip("전체 장치", total.toString(), emphasized = false, modifier = Modifier.weight(1f))
        SummaryChip("정상 작동", normal.toString(), emphasized = false, modifier = Modifier.weight(1f))
        SummaryChip("점검 필요", needCheck.toString(), emphasized = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(
    title: String,
    value: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    val border = if (emphasized) BorderStroke(1.dp, CriticalColor) else BorderStroke(1.dp, c.border)
    val bg = if (emphasized) {
        if (c.isDark) Color(0xFF1A1410) else c.surface
    } else c.surface

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = border,
        color = bg,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = c.sub,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (emphasized) CriticalColor else c.text
            )
        }
    }
}

@Composable
private fun FireAlarmDeviceCard(device: AlarmDevice) {
    val c = LocalSafeColors.current
    val isNeedCheck = device.status == AlarmStatus.NEED_CHECK

    val dotColor = when (device.status) {
        AlarmStatus.NORMAL -> OnFg
        AlarmStatus.NEED_CHECK -> CriticalColor
        AlarmStatus.OFFLINE -> c.sub
    }

    val statusTextColor = when (device.status) {
        AlarmStatus.NORMAL -> OnFg
        AlarmStatus.NEED_CHECK -> CriticalColor
        AlarmStatus.OFFLINE -> c.sub
    }

    val cardBorder = if (device.isCritical) BorderStroke(1.dp, CriticalColor) else BorderStroke(1.dp, c.border)

    // ✅ 아이콘 박스 배경: 다크에서 톤다운
    val iconBg = if (!c.isDark) {
        if (isNeedCheck) Color(0xFFFFF1E8) else Color(0xFFEAFBF4)
    } else {
        if (isNeedCheck) Color(0xFF1A1410) else Color(0xFF0E241C)
    }

    // ✅ 켜짐 배경: 다크에서 더 은은하게
    val onBg = if (c.isDark) Color(0x1A06D6A0) else Color(0x3306D6A0)

    Box(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = cardBorder,
            color = c.surface,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isNeedCheck) "⚠" else "🚨",
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = c.text
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isNeedCheck) {
                            Icon(
                                painter = painterResource(id = R.drawable.warn),
                                contentDescription = "경고",
                                tint = CriticalColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Text(
                            text = device.statusText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = statusTextColor
                        )
                    }
                }

                val (pillBg, pillFg) =
                    if (device.isOn) (onBg to OnFg)
                    else ((if (c.isDark) Color(0xFF1F2937) else Color(0xFFF3F4F6)) to c.sub)

                PowerPill(
                    text = if (device.isOn) "켜짐" else "꺼짐",
                    bg = pillBg,
                    fg = pillFg
                )
            }
        }

        if (device.isCritical) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomStart = 10.dp))
                    .background(CriticalColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "CRITICAL",
                    color = Color.White,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PowerPill(
    text: String,
    bg: Color,
    fg: Color,
    fontSize: Int = 13,
    verticalPadding: Int = 2
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = verticalPadding.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun Preview_FireAlarmManageScreen() {
    val devices = listOf(
        AlarmDevice("1", "1층 로비 화재경보기", AlarmStatus.NORMAL, "정상 작동 중", isOn = true),
        AlarmDevice("2", "2층 사무실 화재경보기", AlarmStatus.NEED_CHECK, "점검 필요 (센서 오류)", isOn = false, isCritical = true),
        AlarmDevice("3", "3층 휴게실 연기감지기", AlarmStatus.NORMAL, "정상 작동 중", isOn = true),
        AlarmDevice("4", "지하 주차장 A구역", AlarmStatus.NORMAL, "정상 작동 중", isOn = true),
    )

    MaterialTheme {
        FireAlarmManageScreen(
            state = FireAlarmUiState(isLoading = false, devices = devices),
            onRefresh = {},
            onToggle = { _, _ -> },
            onClearError = {},
            onBack = {}
        )
    }
}