package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

// ✅ 카메라 데이터 모델 (이미지 리소스 변수 추가)
data class CCTVCameraData(
    val id: String,
    val name: String,
    val location: String,
    val events: List<String>,
    val image: Int // PNG 혹은 벡터 이미지 리소스 ID
)

// ✅ 카메라 더미 데이터 리스트 (각 ID에 맞게 이미지 할당)
val mockCCTVList = listOf(
    CCTVCameraData("1", "CAM01", "A구역 1열", listOf("안전모 미착용", "운반사고", "통로사고"), R.drawable.cctvcam1),
    CCTVCameraData("2", "CAM02", "B구역 3열", listOf("협착사고", "운반사고", "쓰러짐", "통로사고"), R.drawable.cctvcam2),
    CCTVCameraData("3", "CAM03", "C구역 1열", listOf("쓰러짐", "화재사고", "통로사고"), R.drawable.cctvcam3),
    CCTVCameraData("4", "CAM04", "D구역 1열", listOf("안전모 미착용", "협착사고"), R.drawable.cctvcam4)
)

// ✅ 감지 이벤트 카테고리 Enum
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CCTVManagementScreen(
    onBackClick: () -> Unit = {}
) {
    Smart_Safety_ManagementTheme {
        val isLight = MaterialTheme.colors.isLight
        val editColor = TextMedium
        val titleColor = if (isLight) Color.Black else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val frameDividerColor =  if (isLight) TextGray5 else TextGray20

        // ✅ 상태 관리
        var isEditMode by remember { mutableStateOf(false) }
        var selectedArea by remember { mutableStateOf("전체 구역") }
        val selectedEvents = remember { mutableStateListOf<String>("전체") }
        val selectedCameras = remember { mutableStateListOf<String>() }
        val horizontalPadding = 24.dp

        val filteredCCTVList = remember(selectedEvents.toList(), selectedArea) {
            mockCCTVList.filter { camera ->
                val matchesArea = if (selectedArea == "전체 구역") true 
                                 else camera.location.contains(selectedArea.split(" ")[0])
                val matchesEvent = if (selectedEvents.contains("전체")) true
                                  else camera.events.any { it in selectedEvents }
                matchesArea && matchesEvent
            }
        }

        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditMode) "편집" else "CCTV 관리", 
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            fontFamily = Pretendard,
                            modifier = Modifier.offset(x = (-24).dp),
                            fontSize = 24.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isEditMode) {
                                isEditMode = false 
                                selectedCameras.clear()
                            } else {
                                onBackClick() 
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.backicon),
                                contentDescription = "Back",
                                tint = titleColor
                            )
                        }
                    },
                    actions = {
                        if (!isEditMode) {
                            TextButton(onClick = { isEditMode = true }) {
                                Text(
                                    text = "편집",
                                    color = editColor,
                                    fontFamily = Pretendard,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            }
        ) { paddingValues ->
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = 24.dp, bottom = 36.dp) // 🌟 가로 패딩(24.dp)을 제거합니다.
                    .verticalScroll(rememberScrollState())
            ) {
                AnimatedVisibility(
                    visible = !isEditMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    // 🌟 필터 섹션에만 개별적으로 가로 패딩 24.dp를 부여합니다.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer(clip = false),

                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LabelText("설치구역")
                            CCTVCustomDropdown(
                                options = listOf("전체 구역", "A구역", "B구역", "C구역", "D구역"),
                                selectedOption = selectedArea,
                                onOptionSelected = { selectedArea = it },
                                isLight = isLight
                            )
                        }

                        Divider(
                            color = dividerColor,
                            thickness = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth() // 일단 부모가 허용하는 최대 너비를 확보
                                .layout { measurable, constraints ->
                                    val paddingPx = 24.dp.roundToPx()

                                    // 1. 실제 그려질 너비는 부모 패딩 2배를 더한 값
                                    val expandedWidth = constraints.maxWidth + (paddingPx * 2)

                                    // 2. 강제로 확장된 너비로 측정
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = expandedWidth,
                                            maxWidth = expandedWidth
                                        )
                                    )

                                    // 3. [중요] 부모에게는 원래의 maxWidth(패딩 제외 너비)만큼만 차지한다고 보고하고,
                                    // 실제 배치는 왼쪽으로 paddingPx만큼 밀어서 시작합니다.
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.place(-paddingPx, 0)
                                    }
                                }
                        )

                        LabelText("감지 이벤트")

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CCTVEventType.allLabels.forEach { eventLabel ->
                                val isSelected = selectedEvents.contains(eventLabel)
                                CCTVEventSelectButton(
                                    text = eventLabel,
                                    isSelected = isSelected,
                                    onClick = {
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
                                )
                            }
                        }
                        Divider(color = dividerColor, thickness = 1.dp)
                    }
                }

                if (isEditMode) {
                    val isAllSelected = filteredCCTVList.isNotEmpty() && selectedCameras.size == filteredCCTVList.size

                    // 🌟 부모의 가로 패딩이 없으므로 이 Column은 자연스럽게 화면 끝까지 닿습니다.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colors.onPrimary)
                    ) {
                        // 액션 바 콘텐츠는 다시 24.dp 패딩을 주어 정렬을 맞춤
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            EditModeActionBar(
                                isLight = isLight,
                                isAllSelected = isAllSelected,
                                onSelectAll = { checked ->
                                    selectedCameras.clear()
                                    if (checked) {
                                        selectedCameras.addAll(filteredCCTVList.map { it.id })
                                    }
                                },
                                onAddClick = { /* 카메라 추가 로직 */ },
                                onDeleteClick = { /* 선택 삭제 로직 */ }
                            )
                        }

                        // 구분선 (가로 전체)
                        Divider(color = dividerColor, thickness = 1.dp)

                        // ✅ 하단 그림자 (가로 전체 끝까지 닿음)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.05f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }

                // 🌟 하단 목록 제목과 리스트에도 가로 패딩 추가
                LabelText("카메라 목록", modifier = Modifier.padding(horizontal = 24.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    filteredCCTVList.forEachIndexed { index, camera ->
                        CamFrame(
                            camera = camera,
                            isEditMode = isEditMode,
                            isSelected = selectedCameras.contains(camera.id),
                            onSelect = { isSelected ->
                                if (isSelected) selectedCameras.add(camera.id)
                                else selectedCameras.remove(camera.id)
                            }
                        )

                        if (index < filteredCCTVList.size - 1) {
                            Divider(
                                color = frameDividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }
                }
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
    onSelect: (Boolean) -> Unit
) {
    val isLight = MaterialTheme.colors.isLight
    val buttonColor = if (isLight) TextGray else TextGray60
    val titleColor = if (isLight) TextDark else GrayBorder
    val subTextColor = if (isLight) TextGray60 else TextGray
    val frameBgColor = if (isLight) TextGray5 else TextGray20

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false)
    ) {
        if (isEditMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelect,
                colors = CheckboxDefaults.colors(checkedColor = MainOrange),
                modifier = Modifier.offset(y = (-12).dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(133.dp)
                .background(color = MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp))
                .graphicsLayer(clip = false)
                .clickable { if (isEditMode) onSelect(!isSelected) }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().graphicsLayer(clip = false)
            ) {
                // 왼쪽: 이미지 박스 (100x100, 12dp 라운드 적용, 패딩 없이 모서리 밀착)
                Image(
                    painter = painterResource(id = camera.image),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                // 오른쪽: 정보 영역 (start 패딩 16.dp만 적용)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(start = 16.dp, top = 0.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // 🌟 Column에 weight(1f)를 주어 아이콘을 오른쪽 끝으로 확실히 보냅니다.
                        Column(modifier = Modifier.weight(1f)) {
                            // 카메라 이름
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cctvcam),
                                    contentDescription = null,
                                    tint = titleColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = camera.name,
                                    color = titleColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Pretendard
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // 위치 정보
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.cctvlocation),
                                    contentDescription = null,
                                    tint = titleColor,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = camera.location,
                                    color = titleColor,
                                    fontSize = 14.sp,
                                    fontFamily = Pretendard
                                )
                            }

                            // ✅ 감지 이벤트 라벨 및 목록 (위치 텍스트 아래 16dp 간격)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "감지 이벤트",
                                color = titleColor,
                                fontSize = 14.sp,
                                fontFamily = Pretendard
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                camera.events.forEach { e ->
                                    Box(
                                        modifier = Modifier
                                            .height(21.dp)
                                            .background(color = frameBgColor, shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = e, color = subTextColor, fontSize = 12.sp, fontFamily = Pretendard)
                                    }
                                }
                            }
                        }
                        if (!isEditMode) {
                            Icon(
                                painter = painterResource(id = R.drawable.option),
                                contentDescription = "Option",
                                tint = buttonColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditModeActionBar(
    isLight: Boolean,
    isAllSelected: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ✅ 왼쪽: 체크박스 + 전체 선택 텍스트
        Row(
            modifier = Modifier.clickable { onSelectAll(!isAllSelected) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isAllSelected,
                onCheckedChange = onSelectAll,
                colors = CheckboxDefaults.colors(checkedColor = MainOrange)
            )
            Text(
                text = "전체 선택",
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = if (isLight) TextGray20 else TextGray5
            )
        }

        // ✅ 오른쪽: 카메라 추가 | 선택 삭제
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "카메라 추가",
                modifier = Modifier.clickable { onAddClick() },
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = MainOrange,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "  |  ",
                color = if (isLight) GrayBorder else TextDark,
                fontSize = 14.sp
            )
            Text(
                text = "선택 삭제",
                modifier = Modifier.clickable { onDeleteClick() },
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = MainOrange,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CCTVCustomDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    isLight: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor = if (isLight) Color.White else TextGray20
    val borderColor = if (isLight) GrayBorder else TextDark

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = borderColor ,shape = RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedOption,
                fontFamily = Pretendard,
                fontSize = 16.sp,
                color = if (isLight) TextGray20 else TextGray5
            )
            Icon(
                painter = painterResource(id = R.drawable.dropbox),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(14.dp, 9.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(bgColor)
        ) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    onOptionSelected(option)
                    expanded = false
                }) {
                    Text(
                        text = option,
                        color = if (isLight) TextGray20 else TextGray5,
                        fontFamily = Pretendard,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LabelText(text: String, modifier: Modifier = Modifier) {
    val isLight = MaterialTheme.colors.isLight
    val color = if (isLight) TextGray60 else TextGray
    Text(
        text = text,
        modifier = modifier,
        fontFamily = Pretendard,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun CCTVEventSelectButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isLight = MaterialTheme.colors.isLight
    val notBgColor = if (isLight) TextGray5 else TextGray20
    val notTextColor = if (isLight) TextGray else TextGray60
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(
                color = if (isSelected) MainOrange else notBgColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = Pretendard,
            fontSize = 16.sp,
            color = if (isSelected) Color.White else notTextColor,
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun CCTVManagementScreenPreview() {
    CCTVManagementScreen()
}
