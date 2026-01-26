package com.example.smart_safety_management

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.smart_safety_management.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.math.roundToInt

enum class InspectionStatus { CHECKED, UNCHECKED }

data class InspectionItem(val location: String, val description: String, val status: InspectionStatus, val specialNote: String? = null)

data class DailyInspectionReport(val date: LocalDate, val items: List<InspectionItem>)

@RequiresApi(Build.VERSION_CODES.O)
val mockReports = listOf(
    DailyInspectionReport(
        date = LocalDate.of(2026, 1, 7),
        items = listOf(
            InspectionItem("E구역 7열", "조명 미점검으로 인한 안전 문제 발생", InspectionStatus.UNCHECKED, "🔔 누르면 근로자에게 알림이 가요"),
            InspectionItem("F구역 12열", "환풍기 작동 불량으로 공기 순환 필요", InspectionStatus.CHECKED),
            InspectionItem("G구역 2열", "소화 장비 부족으로 긴급 보충 요망", InspectionStatus.CHECKED)
        )
    ),
    DailyInspectionReport(
        date = LocalDate.of(2026, 1, 16),
        items = listOf(
            InspectionItem("B구역 2열", "화재 위험 요소 발견, 즉시 보완 필요", InspectionStatus.CHECKED),
            InspectionItem("C구역 1열", "장비 점검 미비로 작동 불량 발생 가능", InspectionStatus.CHECKED),
            InspectionItem("D구역 3열", "보행 경로 장애물로 인한 이동 불편 사항", InspectionStatus.CHECKED)
        )
    )
)

val TooltipShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val triangleWidth = with(density) { 12.dp.toPx() }
        val triangleHeight = with(density) { 8.dp.toPx() }
        val cornerRadius = with(density) { 6.dp.toPx() }
        val path = Path().apply {
            addRoundRect(RoundRect(left = 0f, top = 0f, right = size.width, bottom = size.height - triangleHeight, cornerRadius = CornerRadius(cornerRadius)))
            val tailOffsetX = with(density) { 30.dp.toPx() }
            val tailCenterX = size.width - tailOffsetX 
            moveTo(tailCenterX - triangleWidth / 2f, size.height - triangleHeight)
            lineTo(tailCenterX, size.height)
            lineTo(tailCenterX + triangleWidth / 2f, size.height - triangleHeight)
            close()
        }
        return Outline.Generic(path)
    }
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold)
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MonthlyListScreen() {
    val activity = LocalContext.current as? ComponentActivity
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var startDate by remember { mutableStateOf(currentYearMonth.atDay(1)) }
    var endDate by remember { mutableStateOf(currentYearMonth.atEndOfMonth()) }
    var lastUserInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val listState = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    LaunchedEffect(isScrolling) { if (isScrolling) lastUserInteraction = System.currentTimeMillis() }

    val filteredReports = mockReports.filter { it.date in startDate..endDate }

    Smart_Safety_ManagementTheme {
        Scaffold(
            backgroundColor = MaterialTheme.colors.onPrimary,
            topBar = {
                TopAppBar(
                    title = { Text(text = "월별로 보기", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface, fontSize = 24.sp, fontFamily = Pretendard, modifier = Modifier.offset(x = (-20).dp)) },
                    navigationIcon = { IconButton(onClick = { activity?.onBackPressedDispatcher?.onBackPressed() }) { Icon(painter = painterResource(id = R.drawable.backicon), contentDescription = "Back", tint = MaterialTheme.colors.onSurface) } },
                    backgroundColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).pointerInput(Unit) { detectTapGestures(onTap = { lastUserInteraction = System.currentTimeMillis() }) }) {
                YearMonthSelector(yearMonth = currentYearMonth, onMonthChange = { newMonth -> currentYearMonth = newMonth })
                DateRangeSelector(yearMonth = currentYearMonth, onDateChange = { start, end -> startDate = start; endDate = end })
                Spacer(modifier = Modifier.height(24.dp)); Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)); Spacer(modifier = Modifier.height(24.dp))
                LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(filteredReports) { report ->
                        ReportHeader(report = report)
                        Spacer(modifier = Modifier.height(16.dp))
                        DailyReportItemsCard(report = report, lastUserInteraction = lastUserInteraction)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReportHeader(report: DailyInspectionReport) {
    val checkedCount = report.items.count { it.status == InspectionStatus.CHECKED }; val totalCount = report.items.size; val allChecked = checkedCount == totalCount
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Text(text = "${report.date.dayOfMonth}일", fontWeight = FontWeight.Medium, fontSize = 18.sp, fontFamily = Pretendard, color = MaterialTheme.colors.onBackground)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(percent = 50)).border(width = 1.dp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = if (allChecked) TextMedium else MaterialTheme.colors.primary, fontFamily = Pretendard)) { append(checkedCount.toString()) }
                withStyle(style = SpanStyle(color = TextMedium, fontFamily = Pretendard)) { append("/${totalCount}") }
            }, fontSize = 14.sp,letterSpacing = (2).sp)
        }
    }
}

@Composable
fun DailyReportItemsCard(report: DailyInspectionReport, lastUserInteraction: Long) {
    val borderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp)).border(1.dp, borderColor, RoundedCornerShape(8.dp)))
        Column(modifier = Modifier.fillMaxWidth().padding(1.dp)) {
            report.items.forEachIndexed { index, item ->
                val itemShape = when { report.items.size == 1 -> RoundedCornerShape(8.dp); index == 0 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp); index == report.items.size - 1 -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp); else -> RoundedCornerShape(0.dp) }
                val itemZIndex = if (item.specialNote != null && item.status == InspectionStatus.UNCHECKED) 1f else 0f
                Box(modifier = Modifier.zIndex(itemZIndex)) { InspectionItemView(item = item, lastUserInteraction = lastUserInteraction, shape = itemShape) }
                if (index < report.items.size - 1) { Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f), thickness = 1.dp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun InspectionItemActions(item: InspectionItem, tooltipVisible: Boolean, onShowDialog: () -> Unit, onTooltipTap: () -> Unit, buttonBackgroundColor: Color, buttonContentColor: Color, buttonText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "tooltip_animation")
    val floatingOffset by infiniteTransition.animateFloat(initialValue = -5f, targetValue = 5f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000), repeatMode = RepeatMode.Reverse), label = "floating_offset")
    Box(modifier = Modifier.wrapContentSize()) {
        Button(onClick = { if (item.status == InspectionStatus.UNCHECKED) onShowDialog() }, elevation = ButtonDefaults.elevation(0.dp, 0.dp), colors = ButtonDefaults.buttonColors(backgroundColor = buttonBackgroundColor, contentColor = buttonContentColor), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), shape = RoundedCornerShape(50.dp), modifier = Modifier.height(22.dp)) {
            val iconTint = if (item.status == InspectionStatus.UNCHECKED && !MaterialTheme.colors.isLight) Color(0xFF000000) else LocalContentColor.current
            if (item.status == InspectionStatus.UNCHECKED) { Icon(painter = painterResource(id = R.drawable.alarm), contentDescription = null, modifier = Modifier.size(12.dp), tint = iconTint) } else { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = iconTint) }
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = buttonText, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = Pretendard, letterSpacing = (-0.3).sp)
        }
        if (item.specialNote != null && item.status == InspectionStatus.UNCHECKED) {
            val tooltipBgColor = if (MaterialTheme.colors.isLight) Lightgray else GrayBackground
            AnimatedVisibility(visible = tooltipVisible, exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).offset(y = (-40).dp).offset { IntOffset(0, floatingOffset.roundToInt()) }.zIndex(10f).layout { measurable, constraints -> val placeable = measurable.measure(constraints); layout(0, 0) { placeable.place(-placeable.width, 0) } }) {
                Surface(onClick = onTooltipTap, shape = TooltipShape, color = tooltipBgColor, elevation = 2.dp) {
                    Text(text = item.specialNote, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = Pretendard, color = if (MaterialTheme.colors.isLight) TextDark else Color.White)
                }
            }
        }
    }
}

@Composable
fun InspectionItemView(item: InspectionItem, lastUserInteraction: Long, shape: Shape = RoundedCornerShape(0.dp)) {
    var showDialog by remember { mutableStateOf(false) }; var dialogWasOpened by remember { mutableStateOf(false) }; val creationTime = remember { System.currentTimeMillis() }; var tooltipVisible by remember { mutableStateOf(true) }
    LaunchedEffect(lastUserInteraction) { if (lastUserInteraction > creationTime) tooltipVisible = false }
    val itemBackgroundColor = if (item.status == InspectionStatus.UNCHECKED && !dialogWasOpened) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
    val buttonBackgroundColor = if (item.status == InspectionStatus.UNCHECKED) if (MaterialTheme.colors.isLight) MaterialTheme.colors.primary.copy(alpha = 0.2f) else MaterialTheme.colors.primary else StatusGreen.copy(alpha = 0.2f)
    val buttonContentColor = if (item.status == InspectionStatus.UNCHECKED) if (MaterialTheme.colors.isLight) MaterialTheme.colors.primary else Color.Black else StatusGreenDark
    val buttonText = if (item.status == InspectionStatus.UNCHECKED) "미점검" else "점검완료"
    if (showDialog) UncheckedItemDialog(onDismissRequest = { showDialog = false })
    Row(modifier = Modifier.fillMaxWidth().background(itemBackgroundColor, shape = shape).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.location, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Pretendard, color = MaterialTheme.colors.onSurface, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.description, fontSize = 14.sp, fontFamily = Pretendard, color = if (MaterialTheme.colors.isLight) TextGray60 else TextGray, maxLines = 2, modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.width(12.dp))
        InspectionItemActions(item = item, tooltipVisible = tooltipVisible, onShowDialog = { showDialog = true; dialogWasOpened = true; tooltipVisible = false }, onTooltipTap = { tooltipVisible = false }, buttonBackgroundColor = buttonBackgroundColor, buttonContentColor = buttonContentColor, buttonText = buttonText)
    }
}

@Composable
fun UncheckedItemDialog(onDismissRequest: () -> Unit) { Dialog(onDismissRequest = onDismissRequest) { UncheckedItemDialogContent(onDismissRequest = onDismissRequest) } }

@Composable
fun UncheckedItemDialogContent(onDismissRequest: () -> Unit) {
    val cardBgColor = if (MaterialTheme.colors.isLight) Color.White else GrayBackground
    Card(modifier = Modifier.width(330.dp).height(259.dp), shape = RoundedCornerShape(16.dp), elevation = 0.dp, backgroundColor = cardBgColor) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp),horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            Icon(painter = painterResource(id = R.drawable.bell_icon), null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(24.dp)); Text(text = "점검요청 재알림", fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, fontFamily = Pretendard, color = MaterialTheme.colors.onSurface)
            Spacer(modifier = Modifier.height(4.dp)); Text(text = "근로자에게 점검요청 재알림을 \n발송하였습니다.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center, fontFamily = Pretendard)
            Spacer(modifier = Modifier.height(24.dp)); Button(onClick = onDismissRequest, modifier = Modifier.width(290.dp).height(55.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary), shape = RoundedCornerShape(12.dp)) { Text(text = "확인", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun YearMonthSelector(yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(yearMonth.minusMonths(1)) }) { Icon(painter = painterResource(id = R.drawable.left), contentDescription = null, tint = Color.Unspecified) }
        Spacer(modifier = Modifier.width(20.dp)); Text(text = "${yearMonth.year}년 ${yearMonth.monthValue}월", fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard, color = MaterialTheme.colors.onBackground)
        Spacer(modifier = Modifier.width(20.dp)); IconButton(onClick = { onMonthChange(yearMonth.plusMonths(1)) }) { Icon(painter = painterResource(id = R.drawable.right), contentDescription = null, tint = Color.Unspecified) }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateRangeSelector(yearMonth: YearMonth, onDateChange: (LocalDate, LocalDate) -> Unit) {
    var startDateStr by remember { mutableStateOf("") }; var endDateStr by remember { mutableStateOf("") }
    var showCustomPicker by remember { mutableStateOf(false) }; var pickingStartDate by remember { mutableStateOf(true) }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    LaunchedEffect(yearMonth) { val firstDay = yearMonth.atDay(1); val lastDay = yearMonth.atEndOfMonth(); startDateStr = firstDay.format(formatter); endDateStr = lastDay.format(formatter); onDateChange(firstDay, lastDay) }
    val iconTint = if (MaterialTheme.colors.isLight) Color.Unspecified else GrayBorder

    if (showCustomPicker) {
        val initialDate = if (pickingStartDate) LocalDate.parse(startDateStr, formatter) else LocalDate.parse(endDateStr, formatter)
        CustomDatePickerDialog(
            initialDate = initialDate,
            onDismiss = { showCustomPicker = false },
            onDateSelected = { selectedDate ->
                if (pickingStartDate) startDateStr = selectedDate.format(formatter) else endDateStr = selectedDate.format(formatter)
                onDateChange(LocalDate.parse(startDateStr, formatter), LocalDate.parse(endDateStr, formatter))
                showCustomPicker = false
            }
        )
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = startDateStr, onValueChange = {}, modifier = Modifier.weight(1f).height(50.dp), readOnly = true, textStyle = TextStyle(fontFamily = Pretendard, fontSize = 14.sp), trailingIcon = { IconButton(onClick = { pickingStartDate = true; showCustomPicker = true }) { Icon(painter = painterResource(id = R.drawable.calendar2), null, tint = iconTint) } }, colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colors.primary, unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), textColor = MaterialTheme.colors.onSurface))
        Icon(painter = painterResource(id = R.drawable.underbar), null, tint = MaterialTheme.colors.onSurface)
        OutlinedTextField(value = endDateStr, onValueChange = {}, modifier = Modifier.weight(1f).height(50.dp), readOnly = true, textStyle = TextStyle(fontFamily = Pretendard, fontSize = 14.sp), trailingIcon = { IconButton(onClick = { pickingStartDate = false; showCustomPicker = true }) { Icon(painter = painterResource(id = R.drawable.calendar2), null, tint = iconTint) } }, colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colors.primary, unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), textColor = MaterialTheme.colors.onSurface))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CustomDatePickerDialog(initialDate: LocalDate, onDismiss: () -> Unit, onDateSelected: (LocalDate) -> Unit) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    
    val datesWithReports = remember { mockReports.map { it.date }.toSet() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = if (MaterialTheme.colors.isLight) Color.White else GrayBackground, modifier = Modifier.width(330.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewMonth = viewMonth.minusMonths(1) }) { Icon(painterResource(id = R.drawable.left), null, tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        YearDropdown(viewMonth.year) { viewMonth = viewMonth.withYear(it) }
                        Spacer(modifier = Modifier.width(8.dp)); MonthDropdown(viewMonth.monthValue) { viewMonth = viewMonth.withMonth(it) }
                    }
                    IconButton(onClick = { viewMonth = viewMonth.plusMonths(1) }) { Icon(painterResource(id = R.drawable.right), null, tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val days = listOf("일", "월", "화", "수", "목", "금", "토")
                    days.forEach { day -> Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontFamily = Pretendard, color = Color.Gray) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                val firstDayOfMonth = viewMonth.atDay(1)
                val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
                
                val calendarRows = mutableListOf<List<LocalDate>>()
                var currentDay = firstDayOfMonth.minusDays(firstDayOfWeek.toLong())
                
                repeat(6) {
                    val row = mutableListOf<LocalDate>()
                    repeat(7) {
                        row.add(currentDay)
                        currentDay = currentDay.plusDays(1)
                    }
                    calendarRows.add(row)
                }

                Column {
                    calendarRows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { date ->
                                val isCurrentMonth = date.month == viewMonth.month
                                val isSelected = date == selectedDate
                                val hasReport = datesWithReports.contains(date)
                                
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colors.primary else Color.Transparent)
                                            .clickable { selectedDate = date }
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            color = when {
                                                isSelected -> Color.White
                                                isCurrentMonth -> if (MaterialTheme.colors.isLight) Color.Black else Color.White
                                                else -> Color.Gray.copy(alpha = 0.5f)
                                            },
                                            fontSize = 14.sp,
                                            fontFamily = Pretendard
                                        )
                                        
                                        if (hasReport && !isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colors.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(backgroundColor = if (MaterialTheme.colors.isLight) Color(0xFFF4F5F6) else Color(0xFF333333)), elevation = ButtonDefaults.elevation(0.dp), shape = RoundedCornerShape(8.dp)) { Text("취소", color = Color.Gray, fontFamily = Pretendard) }
                    Button(onClick = { onDateSelected(selectedDate) }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary), elevation = ButtonDefaults.elevation(0.dp), shape = RoundedCornerShape(8.dp)) { Text("선택", color = Color.White, fontFamily = Pretendard) }
                }
            }
        }
    }
}

@Composable
fun YearDropdown(year: Int, onYearSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SelectorBox(text = "${year}년") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { (2020..2030).forEach { y -> DropdownMenuItem(onClick = { onYearSelected(y); expanded = false }) { Text("${y}년", fontFamily = Pretendard) } } }
    }
}

@Composable
fun MonthDropdown(month: Int, onMonthSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SelectorBox(text = "${month}월") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { (1..12).forEach { m -> DropdownMenuItem(onClick = { onMonthSelected(m); expanded = false }) { Text("${m}월", fontFamily = Pretendard) } } }
    }
}

@Composable
fun SelectorBox(text: String, onClick: () -> Unit) {
    Box(modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Pretendard, color = MaterialTheme.colors.onSurface)
            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colors.onSurface)
        }
    }
}
