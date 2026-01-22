package com.example.smart_safety_management

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.*

// ✅ 데이터 모델
data class CCTVCameraData(
    val id: String,
    val name: String,
    val location: String,
    val events: List<String>,
    val image: Int
)

// ✅ 더미 데이터
val mockCCTVList = listOf(
    CCTVCameraData("1", "CAM01", "A구역 1열", listOf("안전모 미착용", "운반사고", "통로사고"), R.drawable.cctvcam1),
    CCTVCameraData("2", "CAM02", "B구역 3열", listOf("협착사고", "운반사고", "쓰러짐", "통로사고"), R.drawable.cctvcam2),
    CCTVCameraData("3", "CAM03", "C구역 1열", listOf("쓰러짐", "화재사고", "통로사고"), R.drawable.cctvcam3),
    CCTVCameraData("4", "CAM04", "D구역 1열", listOf("안전모 미착용", "협착사고"), R.drawable.cctvcam4)
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
        val isLight = MaterialTheme.colors.isLight
        val editColor = TextMedium
        val titleColor = if (isLight) Color.Black else TextGray5
        val dividerColor = if (isLight) Lightgray else GrayBackground
        val checkboxBorderColor = if (isLight) GrayBorder else TextDark

        var isEditMode by remember { mutableStateOf(false) }
        var selectedArea by remember { mutableStateOf("전체 구역") }
        val selectedEvents = remember { mutableStateListOf("전체") }
        val selectedCameras = remember { mutableStateListOf<String>() }

        val filteredCCTVList by remember(selectedArea, selectedEvents.size) {
            derivedStateOf {
                mockCCTVList.filter { camera ->
                    val matchesArea = if (selectedArea == "전체 구역") true 
                                     else camera.location.contains(selectedArea.split(" ")[0])
                    val matchesEvent = if (selectedEvents.contains("전체")) true
                                      else camera.events.any { it in selectedEvents }
                    matchesArea && matchesEvent
                }
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
                    .padding(top = 24.dp, bottom = 36.dp)
                    .verticalScroll(rememberScrollState())
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
                        filteredCCTVList = filteredCCTVList,
                        selectedCameras = selectedCameras,
                        dividerColor = dividerColor,
                        isLight = isLight,
                        checkboxBorderColor = checkboxBorderColor
                    )
                }

                if (!isEditMode) {
                    Spacer(modifier = Modifier.height(24.dp))
                    LabelText("카메라 목록", modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 카메라 리스트
                CCTVCameraList(
                    cameras = filteredCCTVList,
                    isEditMode = isEditMode,
                    selectedCameras = selectedCameras,
                    onCameraClick = onCameraClick,
                    checkboxBorderColor = checkboxBorderColor
                )
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
    checkboxBorderColor: Color
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
                checkboxBorderColor = checkboxBorderColor
            )
            if (index < cameras.size - 1) {
                Divider(color = frameDividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 32.dp))
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
    checkboxBorderColor: Color
) {
    val isLight = MaterialTheme.colors.isLight
    val buttonColor = if (isLight) TextGray else TextGray60
    val titleColor = if (isLight) TextDark else GrayBorder
    val subTextColor = if (isLight) TextGray60 else TextGray
    val frameBgColor = if (isLight) TextGray5 else TextGray20

    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false)) {
        if (isEditMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelect,
                colors = CheckboxDefaults.colors(
                    checkedColor = MainOrange, // ✅ 체크 시 배경색 MainOrange
                    uncheckedColor = checkboxBorderColor,
                    checkmarkColor = MaterialTheme.colors.onPrimary // ✅ 체크 표시 테마 대응
                ),
                modifier = Modifier.offset(x = (-12).dp, y = (-12).dp)
            )
        }

        Box(
            modifier = Modifier.weight(1f).wrapContentHeight().background(color = MaterialTheme.colors.onPrimary, shape = RoundedCornerShape(12.dp)).graphicsLayer(clip = false)
                .clickable { if (isEditMode) onSelect(!isSelected) else onCameraClick() }
        ) {
            Row(modifier = Modifier.fillMaxWidth().graphicsLayer(clip = false) ) {
                Image(painter = painterResource(id = camera.image), contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.Top) {
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
    checkboxBorderColor: Color
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
                onDeleteClick = { },
                checkboxBorderColor = checkboxBorderColor
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
    checkboxBorderColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.clickable { onSelectAll(!isAllSelected) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isAllSelected, 
                onCheckedChange = onSelectAll, 
                colors = CheckboxDefaults.colors(
                    checkedColor = MainOrange, // ✅ 체크 시 배경색 MainOrange
                    uncheckedColor = checkboxBorderColor,
                    checkmarkColor = MaterialTheme.colors.onPrimary // ✅ 체크 표시 테마 대응
                ),
                modifier = Modifier.offset(x = (-12).dp)
            )
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
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).background(color = bgColor, shape = RoundedCornerShape(8.dp)).border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = selectedOption, fontFamily = Pretendard, fontSize = 16.sp, color = if (isLight) TextGray20 else TextGray5)
            Icon(painter = painterResource(id = R.drawable.dropbox), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(14.dp, 9.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f).background(bgColor)) {
            options.forEach { option ->
                DropdownMenuItem(onClick = { onOptionSelected(option); expanded = false }) {
                    Text(text = option, color = if (isLight) TextGray20 else TextGray5, fontFamily = Pretendard, fontSize = 16.sp)
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
