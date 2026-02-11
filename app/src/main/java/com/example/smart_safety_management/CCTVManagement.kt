package com.example.smart_safety_management

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import coil.compose.AsyncImage
import coil.request.ImageRequest

// ✅ 데이터 모델
data class CCTVCameraData(
    val id: String,
    val name: String,
    val location: String,
    val events: List<String>,
    val image: Int,
    val imageUrl: String? = null
)

// ✅ 카테고리 Enum
enum class CCTVEventType(val label: String) {
    ALL("전체"),
    HELMET_NOT_WEARING("안전모 미착용"),
    AISLE_ACCIDENT("통로사고"),
    COLLISION_ACCIDENT("충돌사고"),
    SAFETY_LINE_VIOLATION("안전선 침범"),
    TRANSPORT_ACCIDENT("운반사고"),
    FIRE_ACCIDENT("화재사고"),
    PINCH_ACCIDENT("협착사고"),
    FALL_DOWN("쓰러짐");

    companion object {
        val allLabels = values().map { it.label }
    }
}

@Composable
fun CCTVManagementScreen(
    onBackClick: () -> Unit = {},
    onCameraClick: (CCTVCameraData) -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val context = LocalContext.current
        val isLight = MaterialTheme.colors.isLight
        val editColor = TextMedium
        val titleColor = if (isLight) Color.Black else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val checkboxColor = if (isLight) GrayBorder else TextDark

        var isEditMode by remember { mutableStateOf(false) }
        var selectedArea by remember { mutableStateOf("전체 구역") }
        val selectedEvents = remember { mutableStateListOf("전체") }
        val selectedCameras = remember { mutableStateListOf<String>() }
        
        // ✅ 서버에서 불러온 데이터를 저장할 상태
        var cctvList by remember { mutableStateOf<List<CCTVCameraData>>(emptyList()) }
        
        // ✅ 목록 새로고침을 위한 트리거
        var refreshTrigger by remember { mutableIntStateOf(0) }

        // ✅ 필터 변경 시 서버에 데이터 요청
        LaunchedEffect(selectedArea, selectedEvents.toList(), refreshTrigger) {
            val areaParam = if (selectedArea == "전체 구역") null else selectedArea
            val eventsParam = if (selectedEvents.contains("전체")) null else selectedEvents.toList()
            val userId = UserSession.userId

            // ✅ [디버깅] 실제 전송하려는 파라미터 값 확인 (Logcat 태그: CCTVManagement)
            Log.d("CCTVManagement", "필터 적용 요청: area=$areaParam, events=$eventsParam")

            if (userId != null) {
                RetrofitClient.instance.getCCTVList(areaParam, eventsParam, userId).enqueue(object : Callback<GetCCTVListResponse> {
                    override fun onResponse(call: Call<GetCCTVListResponse>, response: Response<GetCCTVListResponse>) {
                        if (response.isSuccessful) {
                            val items = response.body()?.cctvList ?: emptyList()
                            cctvList = items.map { item ->
                                val baseUrl = RetrofitClient.BASE_URL.removeSuffix("/")
                                val fullUrl = if (!item.imageUrl.isNullOrEmpty()) baseUrl + item.imageUrl else null
                                
                                CCTVCameraData(
                                    id = item.id.toString(),
                                    name = item.name,
                                    location = item.location,
                                    events = item.events,
                                    image = R.drawable.cctvcam, // 기본 플레이스홀더
                                    imageUrl = fullUrl
                                )
                            }
                        }
                    }
                    override fun onFailure(call: Call<GetCCTVListResponse>, t: Throwable) {
                        // 에러 처리 (로그 등)
                    }
                })
            }
        }

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                CCTVTopAppBar(isEditMode, titleColor, editColor, 
                    onEditToggle = { isEditMode = !isEditMode }, 
                    onBackClick = { if (isEditMode) { isEditMode = false; selectedCameras.clear() } else onBackClick() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    // ✅ 편집 모드일 때 상단바와의 간격을 16.dp로 조절
                    .padding(top = if (isEditMode) 16.dp else 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (isEditMode) 16.dp else 0.dp)
            ) {
                // 필터 섹션
                AnimatedVisibility(visible = !isEditMode, enter = fadeIn(), exit = fadeOut()) {
                    CCTVFilterSection(
                        selectedArea = selectedArea,
                        onAreaChange = { selectedArea = it },
                        selectedEvents = selectedEvents,
                        dividerColor = dividerColor,
                        isLight = isLight
                    )
                }

                // 편집 액션바
                if (isEditMode) {
                    CCTVEditActionBar(
                        filteredCCTVList = cctvList,
                        selectedCameras = selectedCameras,
                        dividerColor = dividerColor,
                        isLight = isLight,
                        checkboxColor = checkboxColor,
                        onDelete = {
                            if (selectedCameras.isNotEmpty()) {
                                val idsToDelete = selectedCameras.mapNotNull { it.toIntOrNull() }
                                if (idsToDelete.isNotEmpty()) {
                                    RetrofitClient.instance.deleteCameras(DeleteCamerasRequest(idsToDelete)).enqueue(object : Callback<DeleteCamerasResponse> {
                                        override fun onResponse(call: Call<DeleteCamerasResponse>, response: Response<DeleteCamerasResponse>) {
                                            if (response.isSuccessful) {
                                                selectedCameras.clear()
                                                isEditMode = false
                                                refreshTrigger++ // 목록 새로고침
                                            }
                                        }
                                        override fun onFailure(call: Call<DeleteCamerasResponse>, t: Throwable) {
                                            Log.e("CCTVManagement", "Delete failed", t)
                                        }
                                    })
                                }
                            }
                        }
                    )
                }
                
                // ✅ 편집 모드일 때는 Spacer와 라벨을 제거하여 카메라 리스트가 전체선택 바에 더 가깝게 붙도록 수정
                if (!isEditMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LabelText("카메라 목록", modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 카메라 리스트
                CCTVCameraList(
                    cameras = cctvList,
                    isEditMode = isEditMode,
                    selectedCameras = selectedCameras,
                    onCameraClick = onCameraClick,
                    checkboxColor = checkboxColor
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CCTVFilterSection(
    selectedArea: String,
    onAreaChange: (String) -> Unit,
    selectedEvents: SnapshotStateList<String>,
    dividerColor: Color,
    isLight: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            LabelText("설치구역")
            CCTVCustomDropdown(
                options = listOf("전체 구역", "A구역", "B구역", "C구역", "D구역"),
                selectedOption = selectedArea,
                onOptionSelected = onAreaChange,
                isLight = isLight
            )
        }

        FullWidthDivider(dividerColor)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LabelText("감지 이벤트", modifier = Modifier.padding(horizontal = 24.dp))

            val eventRows = remember {
                val labels = CCTVEventType.allLabels
                listOf(
                    labels.filterIndexed { index, _ -> index % 2 == 0 },
                    labels.filterIndexed { index, _ -> index % 2 != 0 }
                )
            }

            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                eventRows.forEachIndexed { rowIndex, rowItems ->
                    if (rowIndex > 0) Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { label ->
                            val isSelected = selectedEvents.contains(label)
                            CCTVEventSelectButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = { handleEventSelection(label, selectedEvents, isSelected) }
                            )
                        }
                    }
                }
            }
        }
        
        FullWidthDivider(dividerColor)
    }
}

@Composable
fun CCTVCameraList(
    cameras: List<CCTVCameraData>,
    isEditMode: Boolean,
    selectedCameras: SnapshotStateList<String>,
    onCameraClick: (CCTVCameraData) -> Unit,
    checkboxColor: Color
) {
    val frameDividerColor = if (MaterialTheme.colors.isLight) TextGray5 else TextGray20
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        cameras.forEachIndexed { index, camera ->
            CamFrame(
                camera = camera,
                isEditMode = isEditMode,
                isSelected = selectedCameras.contains(camera.id),
                onSelect = { isSelected ->
                    if (isSelected) selectedCameras.add(camera.id)
                    else selectedCameras.remove(camera.id)
                },
                onCameraClick = { onCameraClick(camera) },
                checkboxColor = checkboxColor
            )
            if (index < cameras.size - 1) {
                // ✅ 편집 모드일 때 프레임 간 상하 간격을 더 좁게 조절
                val verticalPadding = if (isEditMode) 12.dp else 32.dp
                Divider(color = frameDividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = verticalPadding))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamFrame(
    camera: CCTVCameraData,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: (Boolean) -> Unit,
    onCameraClick: () -> Unit = {},
    checkboxColor: Color
) {
    val isLight = MaterialTheme.colors.isLight
    val buttonColor = if (isLight) TextGray else TextGray60
    val titleColor = if (isLight) TextDark else GrayBorder
    val subTextColor = if (isLight) TextGray60 else TextGray
    val frameBgColor = if (isLight) TextGray5 else TextGray20

    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false)) {
        if (isEditMode) {
            // ✅ 시각적 박스 시작점을 24dp 선에 맞춤 (-14dp)
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelect,
                colors = CheckboxDefaults.colors(
                    checkedColor = MainOrange,
                    uncheckedColor = checkboxColor,
                    checkmarkColor = MaterialTheme.colors.onPrimary
                ),
                modifier = Modifier.offset(x = (-14).dp, y = (-12).dp)
            )
            // ✅ 시각적 박스 끝(20dp)에서 프레임 시작까지 16dp 간격 확보
            Spacer(modifier = Modifier.width(2.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                // ✅ 편집모드일 때 사진 프레임이 포함된 요소를 체크박스 쪽으로 8.dp만큼 더 가깝게 이동
                .then(if (isEditMode) Modifier.offset(x = (-8).dp) else Modifier)
                .wrapContentHeight()
                .background(color = MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp))
                .graphicsLayer(clip = false)
                .clickable { if (isEditMode) onSelect(!isSelected) else onCameraClick() }
        ) {
            Row(modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false) ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(camera.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    placeholder = painterResource(id = camera.image),
                    error = painterResource(id = camera.image),
                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // ✅ 편집모드일 때 텍스트 요소를 왼쪽으로 8.dp만큼 더 가깝게 이동 (16 -> 8)
                        .padding(start = if (isEditMode) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(id = R.drawable.cctvcam), contentDescription = null, tint = titleColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = camera.name, 
                                    color = titleColor, 
                                    fontSize = 18.sp, 
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = Pretendard
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(id = R.drawable.cctvlocation), contentDescription = null, tint = titleColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = camera.location, color = titleColor, fontSize = 14.sp, fontFamily = Pretendard)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "감지 이벤트", color = titleColor, fontSize = 14.sp, fontFamily = Pretendard)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                camera.events.forEach { e ->
                                    Box(modifier = Modifier.height(21.dp).background(color = frameBgColor, shape = RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp)) {
                                        Text(text = e, color = subTextColor, fontSize = 12.sp, fontFamily = Pretendard)
                                    }
                                }
                            }
                        }
                        if (!isEditMode) {
                            Icon(painter = painterResource(id = R.drawable.option), contentDescription = "Option", tint = buttonColor, modifier = Modifier.size(24.dp).padding(top = 4.dp, end = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CCTVTopAppBar(isEditMode: Boolean, titleColor: Color, editColor: Color, onEditToggle: () -> Unit, onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(text = if (isEditMode) "편집" else "CCTV 관리", fontWeight = FontWeight.Bold, color = titleColor, fontFamily = Pretendard, modifier = Modifier.offset(x = (-24).dp), fontSize = 24.sp)
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) { Icon(painter = painterResource(id = R.drawable.backicon), contentDescription = "Back", tint = titleColor) }
        },
        actions = {
            if (!isEditMode) {
                TextButton(onClick = onEditToggle) { Text("편집", color = editColor, fontFamily = Pretendard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        },
        backgroundColor = MaterialTheme.colors.onPrimary, elevation = 0.dp, modifier = Modifier.height(56.dp)
    )
}

@Composable
fun CCTVEditActionBar(
    filteredCCTVList: List<CCTVCameraData>, 
    selectedCameras: SnapshotStateList<String>, 
    dividerColor: Color, 
    isLight: Boolean,
    checkboxColor: Color,
    onDelete: () -> Unit
) {
    val isAllSelected = filteredCCTVList.isNotEmpty() && selectedCameras.size == filteredCCTVList.size
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.onPrimary)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            EditModeActionBar(
                isLight = isLight, 
                isAllSelected = isAllSelected, 
                onSelectAll = { checked ->
                    selectedCameras.clear()
                    if (checked) selectedCameras.addAll(filteredCCTVList.map { it.id })
                }, 
                onAddClick = { }, 
                onDeleteClick = onDelete,
                checkboxColor = checkboxColor
            )
        }
        Divider(color = dividerColor, thickness = 1.dp)
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Transparent))))
    }
}

@Composable
fun FullWidthDivider(color: Color, modifier: Modifier = Modifier) {
    Divider(
        color = color, thickness = 1.dp,
        modifier = modifier.fillMaxWidth().layout { measurable, constraints ->
            val paddingPx = 24.dp.roundToPx()
            val expandedWidth = constraints.maxWidth + (paddingPx * 2)
            val placeable = measurable.measure(constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-paddingPx, 0) }
        }
    )
}

@Composable
fun LabelText(text: String, modifier: Modifier = Modifier) {
    val isLight = MaterialTheme.colors.isLight
    val color = if (isLight) TextGray60 else TextGray
    Text(text = text, modifier = modifier, fontFamily = Pretendard, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun CCTVEventSelectButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val isLight = MaterialTheme.colors.isLight
    val notBgColor = if (isLight) TextGray5 else TextGray20
    val notTextColor = if (isLight) TextGray else TextGray60
    Box(
        modifier = Modifier.height(34.dp).background(color = if (isSelected) MainOrange else notBgColor, shape = RoundedCornerShape(20.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontFamily = Pretendard, fontSize = 16.sp, color = if (isSelected) Color.White else notTextColor)
    }
}

@Composable
fun EditModeActionBar(
    isLight: Boolean, 
    isAllSelected: Boolean, 
    onSelectAll: (Boolean) -> Unit, 
    onAddClick: () -> Unit, 
    onDeleteClick: () -> Unit,
    checkboxColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.clickable { onSelectAll(!isAllSelected) }, verticalAlignment = Alignment.CenterVertically) {
            // ✅ 시각적 상자 시작점을 맞추기 위해 -14dp 오프셋
            Checkbox(
                checked = isAllSelected, 
                onCheckedChange = onSelectAll, 
                colors = CheckboxDefaults.colors(
                    checkedColor = MainOrange,
                    uncheckedColor = checkboxColor,
                    checkmarkColor = MaterialTheme.colors.onPrimary 
                ),
                modifier = Modifier.offset(x = (-14).dp)
            )
            // ✅ 텍스트와 16dp 간격 확보
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "전체 선택", fontFamily = Pretendard, fontSize = 16.sp, color = if (isLight) TextGray20 else TextGray5)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "카메라 추가", modifier = Modifier.clickable { onAddClick() }, fontFamily = Pretendard, fontSize = 16.sp, color = MainOrange, fontWeight = FontWeight.SemiBold)
            Text(text = "  |  ", color = if (isLight) GrayBorder else TextDark, fontSize = 14.sp)
            Text(text = "선택 삭제", modifier = Modifier.clickable { onDeleteClick() }, fontFamily = Pretendard, fontSize = 16.sp, color = MainOrange, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CCTVCustomDropdown(options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit, isLight: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor = if (isLight) Color.White else TextGray20
    val borderColor = if (isLight) GrayBorder else TextDark
    val dropboxBorder = if (isLight) TextGray5 else TextGray20
    val selectAlpha = if (isLight) 0.12f else 0.36f

    val density = LocalDensity.current
    var buttonWidth by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onSizeChanged { buttonWidth = it.width }
                .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = selectedOption, fontFamily = Pretendard, fontSize = 16.sp, color = if (isLight) TextGray20 else TextGray5)
            Icon(painter = painterResource(id = R.drawable.dropbox), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(14.dp, 9.dp))
        }
        
        MaterialTheme(
            colors = MaterialTheme.colors.copy(surface = bgColor),
            shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))
        ) {
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false }, 
                modifier = Modifier
                    .width(with(density) { buttonWidth.toDp() })
                    .background(bgColor)
            ) {
                options.forEachIndexed { index, option ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    
                    DropdownMenuItem(
                        onClick = { onOptionSelected(option); expanded = false },
                        interactionSource = interactionSource,
                        modifier = Modifier.background(
                            if (isPressed) MainOrange.copy(alpha = selectAlpha) else bgColor
                        )
                    ) {
                        Text(
                            text = option, 
                            color = if (isLight) TextGray20 else TextGray5, 
                            fontFamily = Pretendard, 
                            fontSize = 16.sp
                        )
                    }
                    
                    if (index < options.size - 1) {
                        Divider(color = dropboxBorder, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

private fun handleEventSelection(eventLabel: String, selectedEvents: MutableList<String>, isSelected: Boolean) {
    if (eventLabel == "전체") {
        selectedEvents.clear()
        selectedEvents.add("전체")
    } else {
        selectedEvents.remove("전체")
        if (isSelected) {
            selectedEvents.remove(eventLabel)
            if (selectedEvents.isEmpty()) selectedEvents.add("전체")
        } else {
            selectedEvents.add(eventLabel)
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CCTVManagementScreenPreview() {
    CCTVManagementScreen()
}
