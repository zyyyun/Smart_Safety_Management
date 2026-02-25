@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.smart_safety_management.screens.afci

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.smart_safety_management.GetArcBreakersResponse
import com.example.smart_safety_management.UserSession
import com.example.smart_safety_management.ui.theme.LocalSafeColors
import com.example.smart_safety_management.ui.theme.Pretendard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private val CriticalColor = Color(0xFFF97316)
private val OnFg = Color(0xFF06D6A0) // green fg

enum class AfciStatus { NORMAL, CRITICAL, ALERT, DISCONNECTED }

data class AfciDevice(
    val id: String,
    val name: String,
    val status: AfciStatus,
    val statusText: String,
    val isCritical: Boolean = false
)

data class ArcBreakerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val devices: List<AfciDevice> = emptyList(),
    val totalCount: Int = 0,
    val normalCount: Int = 0,
    val eventCount: Int = 0
)

interface ArcBreakerRepository {
    suspend fun fetchState(): ArcBreakerUiState
}

class FakeArcBreakerRepository : ArcBreakerRepository {
    override suspend fun fetchState(): ArcBreakerUiState {
        delay(350)

        val list = listOf(
            AfciDevice("1", "1층 로비 아크 차단기", AfciStatus.NORMAL, "정상 작동 중"),
            AfciDevice("2", "2층 전산실 아크 차단기", AfciStatus.CRITICAL, "아크 감지됨", isCritical = true),
            AfciDevice("3", "지하 주차장 B구역", AfciStatus.ALERT, "과전류 경보 발생"),
            AfciDevice("4", "창고 1구역 차단기", AfciStatus.DISCONNECTED, "통신 이상 (Disconnected)"),
            AfciDevice("5", "3층 사무실 A구역", AfciStatus.NORMAL, "정상 작동 중"),
        )

        return ArcBreakerUiState(
            isLoading = false,
            devices = list,
            totalCount = 15,
            normalCount = 11,
            eventCount = 4
        )
    }
}

class RealArcBreakerRepository : ArcBreakerRepository {
    override suspend fun fetchState(): ArcBreakerUiState {
        return suspendCoroutine { cont ->
            val userId = UserSession.userId
            if (userId == null) {
                cont.resume(ArcBreakerUiState(isLoading = false, errorMessage = "로그인 정보가 없습니다."))
                return@suspendCoroutine
            }

            RetrofitClient.instance.getArcBreakers(userId).enqueue(object : Callback<GetArcBreakersResponse> {
                override fun onResponse(
                    call: Call<GetArcBreakersResponse>,
                    response: Response<GetArcBreakersResponse>
                ) {
                    if (response.isSuccessful) {
                        val list = response.body()?.arcBreakers ?: emptyList()
                        val devices = list.map { dto ->
                            val statusEnum = when {
                                !dto.isConnected -> AfciStatus.DISCONNECTED
                                dto.status.uppercase() in listOf("DANGER", "CRITICAL", "위험") -> AfciStatus.CRITICAL
                                dto.status.uppercase() in listOf("WARNING", "ALERT", "ALTER", "경고", "주의") -> AfciStatus.ALERT
                                else -> AfciStatus.NORMAL
                            }

                            AfciDevice(
                                id = dto.breakerId.toString(),
                                name = dto.breakerName,
                                status = statusEnum,
                                statusText = dto.statusMsg,
                                isCritical = statusEnum == AfciStatus.CRITICAL
                            )
                        }

                        val total = devices.size
                        val normal = devices.count { it.status == AfciStatus.NORMAL }
                        val event = devices.count { it.status == AfciStatus.CRITICAL || it.status == AfciStatus.ALERT || it.status == AfciStatus.DISCONNECTED }

                        cont.resume(
                            ArcBreakerUiState(
                                isLoading = false,
                                devices = devices,
                                totalCount = total,
                                normalCount = normal,
                                eventCount = event
                            )
                        )
                    } else {
                        cont.resume(ArcBreakerUiState(isLoading = false, errorMessage = "서버 오류: ${response.code()}"))
                    }
                }

                override fun onFailure(call: Call<GetArcBreakersResponse>, t: Throwable) {
                    cont.resume(ArcBreakerUiState(isLoading = false, errorMessage = "네트워크 오류: ${t.message}"))
                }
            })
        }
    }
}

class ArcBreakerViewModel(
    private val repo: ArcBreakerRepository = RealArcBreakerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArcBreakerUiState(isLoading = true))
    val uiState: StateFlow<ArcBreakerUiState> = _uiState

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repo.fetchState() }
                .onSuccess { st -> _uiState.value = st.copy(isLoading = false) }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "불러오기 실패") }
                }
        }
    }
}

@Composable
fun ArcBreakerManageRoute(
    vm: ArcBreakerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    ArcBreakerManageScreen(
        state = state,
        onRefresh = vm::refresh,
        onBack = onBack
    )
}

@Composable
fun ArcBreakerManageScreen(
    state: ArcBreakerUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val c = LocalSafeColors.current

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
                        "아크 차단기 관리",
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
                total = state.totalCount,
                normal = state.normalCount,
                event = state.eventCount,
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
                    "장치 상세 목록",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = c.text
                )

                IconButton(onClick = { /* 필터 */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.filter),
                        contentDescription = "필터",
                        tint = if (c.isDark) Color.White else Color.Unspecified,
                        modifier = Modifier.size(20.dp)
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
                    items(state.devices, key = { it.id }) { device ->
                        ArcBreakerDeviceCard(device = device)
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
    event: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryChip(title = "전체 장치", value = total.toString(), sub = "UNIT", emphasized = false, modifier = Modifier.weight(1f))
        SummaryChip(title = "정상", value = normal.toString(), sub = null, emphasized = false, modifier = Modifier.weight(1f))
        SummaryChip(title = "이벤트/경보", value = event.toString(), sub = null, emphasized = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(
    title: String,
    value: String,
    sub: String?,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalSafeColors.current

    val border = if (emphasized) {
        BorderStroke(1.dp, if (c.isDark) Color(0xFF3A2A21) else Color(0xFFFFD7C0))
    } else {
        BorderStroke(1.dp, c.border)
    }

    val chipBg = if (emphasized) {
        if (c.isDark) Color(0xFF1A1410) else Color(0xFFFFF1E8)
    } else {
        c.surface
    }

    val valueColor = when {
        emphasized -> CriticalColor
        title == "정상" -> OnFg
        else -> c.text
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = border,
        color = chipBg,
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
                color = if (emphasized) CriticalColor else c.sub,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = valueColor
                )
                if (!sub.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sub,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = c.sub
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcBreakerDeviceCard(device: AfciDevice) {
    val c = LocalSafeColors.current

    val (iconBg, iconText) = when (device.status) {
        AfciStatus.NORMAL -> (Color(0xFFEAFBF4) to "🛡")
        AfciStatus.CRITICAL -> (Color(0xFFFFF1E8) to "⚡")
        AfciStatus.ALERT -> (Color(0xFFFFF1E8) to "⚠")
        AfciStatus.DISCONNECTED -> (Color(0xFFF3F4F6) to "📶")
    }

    // ✅ 다크에서 아이콘 박스 배경은 살짝 톤다운
    val iconBgFixed = if (!c.isDark) iconBg else when (device.status) {
        AfciStatus.NORMAL -> Color(0xFF0E241C)
        AfciStatus.CRITICAL -> Color(0xFF1A1410)
        AfciStatus.ALERT -> Color(0xFF1A1410)
        AfciStatus.DISCONNECTED -> Color(0xFF1F2937)
    }

    val statusTextColor = when (device.status) {
        AfciStatus.NORMAL -> OnFg
        AfciStatus.CRITICAL -> CriticalColor
        AfciStatus.ALERT -> Color(0xFFF59E0B)
        AfciStatus.DISCONNECTED -> c.sub
    }

    val border = when (device.status) {
        AfciStatus.NORMAL -> BorderStroke(1.dp, c.border)
        else -> BorderStroke(1.dp, CriticalColor)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = border,
        color = c.surface,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgFixed),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconText, fontSize = 18.sp)
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

            StatusPill(status = device.status)
        }
    }
}

@Composable
private fun StatusPill(status: AfciStatus) {
    val c = LocalSafeColors.current

    val (text, bg, fg) = when (status) {
        AfciStatus.NORMAL -> {
            val bg2 = if (c.isDark) Color(0x1A06D6A0) else Color(0x3306D6A0)
            Triple("정상", bg2, OnFg)
        }
        AfciStatus.CRITICAL -> Triple("위험", Color(0xFFF97316), Color.White)
        AfciStatus.ALERT -> {
            val bg2 = if (c.isDark) Color(0xFF2A1F10) else Color(0xFFFFEEDB)
            Triple("경고", bg2, Color(0xFFF59E0B))
        }
        AfciStatus.DISCONNECTED -> {
            val bg2 = if (c.isDark) Color(0xFF1F2937) else Color(0xFFF3F4F6)
            Triple("오류", bg2, c.sub)
        }
    }

    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun Preview_ArcBreakerManageScreen() {
    MaterialTheme {
        ArcBreakerManageScreen(
            state = ArcBreakerUiState(
                isLoading = false,
                devices = listOf(
                    AfciDevice("1", "1층 로비 아크 차단기", AfciStatus.NORMAL, "정상 작동 중"),
                    AfciDevice("2", "2층 전산실 아크 차단기", AfciStatus.CRITICAL, "아크 감지됨", isCritical = true),
                    AfciDevice("3", "지하 주차장 B구역", AfciStatus.ALERT, "과전류 경보 발생"),
                    AfciDevice("4", "창고 1구역 차단기", AfciStatus.DISCONNECTED, "통신 이상 (Disconnected)"),
                    AfciDevice("5", "3층 사무실 A구역", AfciStatus.NORMAL, "정상 작동 중"),
                ),
                totalCount = 15,
                normalCount = 11,
                eventCount = 4
            ),
            onRefresh = {},
            onBack = {}
        )
    }
}