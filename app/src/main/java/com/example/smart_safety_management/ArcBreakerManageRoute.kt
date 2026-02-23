@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.smart_safety_management.screens.afci

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.smart_safety_management.ui.theme.Pretendard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.example.smart_safety_management.RetrofitClient
import com.example.smart_safety_management.GetArcBreakersResponse
import com.example.smart_safety_management.UserSession
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private val CriticalColor = Color(0xFFF97316)
private val OnBg = Color(0x3306D6A0) // green bg
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
    // ✅ 사진처럼 “요약 카운트”는 서버/DB에서 내려오는 값으로 쓰기 좋게 분리
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

        // ✅ 사진: 전체 15 / 정상 11 / 이벤트/경보 4
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
                            // 서버 상태값 매핑
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
                            ArcBreakerUiState(isLoading = false, devices = devices, totalCount = total, normalCount = normal, eventCount = event)
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
    Scaffold(
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.backicon),
                            contentDescription = "뒤로가기",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                title = {
                    Text(
                        "아크 차단기 관리",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
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
                    fontSize = 16.sp
                )

                IconButton(onClick = { /* 필터 */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.filter), // 없으면 너 아이콘으로 교체
                        contentDescription = "필터",
                        tint = Color.Unspecified,
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
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
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
    val border = if (emphasized) BorderStroke(1.dp, Color(0xFFFFD7C0)) else BorderStroke(1.dp, Color(0xFFE8E8E8))
    val valueColor = when {
        emphasized -> CriticalColor
        title == "정상" -> OnFg
        else -> Color.Black
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = border,
        color = if (emphasized) Color(0xFFFFF1E8) else Color.White,
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
                color = if (emphasized) CriticalColor else Color(0xFF6B7280),
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
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcBreakerDeviceCard(device: AfciDevice) {
    val (iconBg, iconText) = when (device.status) {
        AfciStatus.NORMAL -> (Color(0xFFEAFBF4) to "🛡")   // 느낌만
        AfciStatus.CRITICAL -> (Color(0xFFFFF1E8) to "⚡")
        AfciStatus.ALERT -> (Color(0xFFFFF1E8) to "⚠")
        AfciStatus.DISCONNECTED -> (Color(0xFFF3F4F6) to "📶")
    }

    val statusTextColor = when (device.status) {
        AfciStatus.NORMAL -> OnFg
        AfciStatus.CRITICAL -> CriticalColor
        AfciStatus.ALERT -> Color(0xFFF59E0B)
        AfciStatus.DISCONNECTED -> Color(0xFF6B7280)
    }

    val border = when (device.status) {
        AfciStatus.NORMAL -> BorderStroke(1.dp, Color(0xFFE8E8E8))
        else -> BorderStroke(1.dp, CriticalColor)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = border,
        color = Color.White,
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
                    .background(iconBg),
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
                    color = Color.Black
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
    val (text, bg, fg) = when (status) {
        AfciStatus.NORMAL -> Triple("정상", OnBg, OnFg)
        AfciStatus.CRITICAL -> Triple("위험", Color(0xFFF97316), Color.White)
        AfciStatus.ALERT -> Triple("경고", Color(0xFFFFEEDB), Color(0xFFF59E0B))
        AfciStatus.DISCONNECTED -> Triple("오류", Color(0xFFF3F4F6), Color(0xFF6B7280))
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