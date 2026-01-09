package com.example.smart_safety_management

import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class InspectionStatus { CHECKED, UNCHECKED }

data class InspectionItem(val location: String, val description: String, val status: InspectionStatus, val specialNote: String? = null)

data class DailyInspectionReport(val date: LocalDate, val items: List<InspectionItem>)

@RequiresApi(Build.VERSION_CODES.O)
val mockReports = listOf(
    DailyInspectionReport(
        date = LocalDate.of(2026, 1, 7),
        items = listOf(
            InspectionItem("E구역 7열", "조명 미점검으로 인한 안전 문제 발생", InspectionStatus.UNCHECKED, "\uD83D\uDD14 누르면 근로자에게 알림이 가요"),
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
            val tailCenterX = size.width * 0.9f 
            moveTo(tailCenterX - triangleWidth / 2f, size.height - triangleHeight)
            lineTo(tailCenterX, size.height)
            lineTo(tailCenterX + triangleWidth / 2f, size.height - triangleHeight)
            close()
        }
        return Outline.Generic(path)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
fun MonthlyListScreen() {
    val activity = LocalContext.current as? ComponentActivity
    var currentYearMonth by remember { mutableStateOf(YearMonth.of(2026, 1)) }
    var startDate by remember { mutableStateOf(currentYearMonth.atDay(1)) }
    var endDate by remember { mutableStateOf(currentYearMonth.atEndOfMonth()) }
    var lastUserInteraction by remember { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    LaunchedEffect(isScrolling) { if (isScrolling) lastUserInteraction = System.currentTimeMillis() }

    val filteredReports = mockReports.filter { it.date in startDate..endDate }

    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "월별로 보기", fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.offset(x = (-20).dp)) },
                    navigationIcon = { IconButton(onClick = { activity?.onBackPressedDispatcher?.onBackPressed() }) { Icon(painter = painterResource(id = R.drawable.backicon), contentDescription = "Back", tint = Color.Black) } },
                    backgroundColor = Color.White
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).pointerInput(Unit) { detectTapGestures(onTap = { lastUserInteraction = System.currentTimeMillis() }) }) {
                YearMonthSelector(yearMonth = currentYearMonth, onMonthChange = { newMonth -> currentYearMonth = newMonth })
                DateRangeSelector(yearMonth = currentYearMonth, onDateChange = { start, end -> startDate = start; endDate = end })
                Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(filteredReports) { report ->
                        ReportHeader(report = report)
                        Spacer(modifier = Modifier.height(8.dp))
                        DailyReportItemsCard(report = report, lastUserInteraction = lastUserInteraction)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReportHeader(report: DailyInspectionReport) {
    val checkedCount = report.items.count { it.status == InspectionStatus.CHECKED }
    val totalCount = report.items.size
    val allChecked = checkedCount == totalCount

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Text(text = "${report.date.dayOfMonth}일", fontWeight = FontWeight.Medium, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.background(color = Color.White, shape = RoundedCornerShape(percent = 50)).border(width = 1.dp, color = Color.LightGray, shape = RoundedCornerShape(percent = 50)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = if (allChecked) Color(0xFF6D7882) else Color(0xFFF97316))) { append(checkedCount.toString()) }
                withStyle(style = SpanStyle(color = Color(0xFF6D7882))) { append(" / ${totalCount}") }
            }, fontSize = 16.sp)
        }
    }
}

@Composable
fun DailyReportItemsCard(report: DailyInspectionReport, lastUserInteraction: Long) {
    val borderColor = Color(0xFFCDD1D5)
    // 카드 경계선이 툴팁 위에 노출되는 문제를 해결하기 위해 레이어 구조를 변경합니다.
    Box(modifier = Modifier.fillMaxWidth()) {
        // 1. 배경과 테두리를 별도 레이어로 구성하여 가장 아래에 배치
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)
        ) {
            report.items.forEachIndexed { index, item ->
                val itemShape = when {
                    report.items.size == 1 -> RoundedCornerShape(8.dp)
                    index == 0 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    index == report.items.size - 1 -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                
                // 2. 툴팁이 있는 아이템에 높은 zIndex를 부여하여 다른 요소보다 위에 그려지도록 함
                val itemZIndex = if (item.specialNote != null && item.status == InspectionStatus.UNCHECKED) 1f else 0f
                
                Box(modifier = Modifier.zIndex(itemZIndex)) {
                    InspectionItemView(item = item, lastUserInteraction = lastUserInteraction, shape = itemShape)
                }
                
                if (index < report.items.size - 1) {
                    Divider(color = Color(0xFFF1F3F5), thickness = 1.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun InspectionItemActions(item: InspectionItem, tooltipVisible: Boolean, onShowDialog: () -> Unit, onTooltipTap: () -> Unit, buttonBackgroundColor: Color, buttonContentColor: Color, buttonText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "tooltip_animation")
    val floatingOffset by infiniteTransition.animateFloat(initialValue = -5f, targetValue = 5f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000), repeatMode = RepeatMode.Reverse), label = "floating_offset")

    Box(modifier = Modifier.zIndex(1f)) {
        Button(
            onClick = { if (item.status == InspectionStatus.UNCHECKED) onShowDialog() },
            elevation = ButtonDefaults.elevation(0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = buttonBackgroundColor, contentColor = buttonContentColor),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.CenterEnd).height(25.dp)
        ) {
            val icon = if (item.status == InspectionStatus.UNCHECKED) Icons.Default.Notifications else Icons.Default.Check
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = buttonText, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }

        if (item.specialNote != null && item.status == InspectionStatus.UNCHECKED) {
            AnimatedVisibility(visible = tooltipVisible, exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).offset(y = (-40).dp).offset { IntOffset(0, floatingOffset.roundToInt()) }.zIndex(10f)) {
                Surface(onClick = onTooltipTap, shape = TooltipShape, color = Color(0xFFE6E8EA).copy(alpha = 0.9f), elevation = 2.dp) {
                    Text(text = item.specialNote,modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), fontSize = 12.sp,fontWeight = FontWeight.Normal, color = Color(0xFF000000))
                }
            }
        }
    }
}

@Composable
fun InspectionItemView(item: InspectionItem, lastUserInteraction: Long, shape: Shape = RoundedCornerShape(0.dp)) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogWasOpened by remember { mutableStateOf(false) }
    val creationTime = remember { System.currentTimeMillis() }
    var tooltipVisible by remember { mutableStateOf(true) }

    LaunchedEffect(lastUserInteraction) { if (lastUserInteraction > creationTime) tooltipVisible = false }

    val itemBackgroundColor = if (item.status == InspectionStatus.UNCHECKED && !dialogWasOpened) Color(0x1FFB923C) else Color.Transparent
    val buttonBackgroundColor = if (item.status == InspectionStatus.UNCHECKED) Color(0x33FB923C) else Color(0x3306D6A0)
    val buttonContentColor = if (item.status == InspectionStatus.UNCHECKED) Color(0xFFF97316) else Color(0xFF04BC93)
    val buttonText = if (item.status == InspectionStatus.UNCHECKED) "미점검" else "점검완료"

    if (showDialog) UncheckedItemDialog(onDismissRequest = { showDialog = false })

    Row(modifier = Modifier.fillMaxWidth().background(itemBackgroundColor, shape = shape).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.location, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(text = item.description, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        InspectionItemActions(item, tooltipVisible, { showDialog = true; dialogWasOpened = true; tooltipVisible = false }, { tooltipVisible = false }, buttonBackgroundColor, buttonContentColor, buttonText)
    }
}

@Composable
fun UncheckedItemDialog(onDismissRequest: () -> Unit) {
    val borderColor = Color(0xFFCDD1D5)
    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.width(330.dp).height(290.dp).border(1.dp, borderColor, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), elevation = 0.dp) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painter = painterResource(id = R.drawable.bell_icon), null, tint = Color(0xFFF97316), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "점검요청 재알림", fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "근로자에게 점검요청 재알림을 발송하였습니다.", color = Color(0xFF58616A), fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismissRequest, modifier = Modifier.width(260.dp).height(55.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF97316), contentColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                    Text(text = "확인", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun YearMonthSelector(yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(yearMonth.minusMonths(1)) }) { Icon(painter = painterResource(id = R.drawable.left), null, tint = Color.Unspecified, modifier = Modifier.offset(x = 50.dp)) }
        Text(text = "${yearMonth.year}년 ${yearMonth.monthValue}월", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onMonthChange(yearMonth.plusMonths(1)) }) { Icon(painter = painterResource(id = R.drawable.right), null, tint = Color.Unspecified, modifier = Modifier.offset(x = (-50).dp)) }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateRangeSelector(yearMonth: YearMonth, onDateChange: (LocalDate, LocalDate) -> Unit) {
    var startDateStr by remember { mutableStateOf("") }
    var endDateStr by remember { mutableStateOf("") }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    LaunchedEffect(yearMonth) {
        val firstDay = yearMonth.atDay(1); val lastDay = yearMonth.atEndOfMonth()
        startDateStr = firstDay.format(formatter); endDateStr = lastDay.format(formatter)
        onDateChange(firstDay, lastDay)
    }
    val context = LocalContext.current
    fun showDatePicker(isStart: Boolean, date: LocalDate) {
        DatePickerDialog(context, { _, y, m, d ->
            val new = LocalDate.of(y, m + 1, d)
            if (isStart) startDateStr = new.format(formatter) else endDateStr = new.format(formatter)
            onDateChange(LocalDate.parse(startDateStr, formatter), LocalDate.parse(endDateStr, formatter))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = startDateStr, onValueChange = {}, modifier = Modifier.weight(1f).height(50.dp), readOnly = true, trailingIcon = { IconButton(onClick = { showDatePicker(true, LocalDate.parse(startDateStr, formatter)) }) { Icon(painter = painterResource(id = R.drawable.calendar2), null, tint = Color.Unspecified) } })
        Icon(painter = painterResource(id = R.drawable.underbar), null, tint = Color.Unspecified)
        OutlinedTextField(value = endDateStr, onValueChange = {}, modifier = Modifier.weight(1f).height(50.dp), readOnly = true, trailingIcon = { IconButton(onClick = { showDatePicker(false, LocalDate.parse(endDateStr, formatter)) }) { Icon(painter = painterResource(id = R.drawable.calendar2), null, tint = Color.Unspecified) } })
    }
}
