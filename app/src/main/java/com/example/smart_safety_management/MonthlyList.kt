package com.example.smart_safety_management

import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.smart_safety_management.ui.theme.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class InspectionStatus { CHECKED, UNCHECKED }

data class InspectionItem(val checkId: String, val location: String, val description: String, val status: InspectionStatus, val specialNote: String? = null)

data class DailyInspectionReport(val date: LocalDate, val items: List<InspectionItem>)

internal fun dailyChecklistDisplayDate(checkDate: String?, createdAt: String?): LocalDate {
    val explicit = checkDate?.takeIf { it.length >= 10 }?.substring(0, 10)
    val fallback = createdAt?.takeIf { it.length >= 10 }?.substring(0, 10)
    return LocalDate.parse(explicit ?: fallback ?: LocalDate.now().toString(), DateTimeFormatter.ISO_DATE)
}

val TooltipShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val triangleWidth = with(density) { 12.dp.toPx() }
        val triangleHeight = with(density) { 8.dp.toPx() }
        val cornerRadius = with(density) { 8.dp.toPx() }
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
    var lastUserInteraction by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    
    // ✅ 서버 데이터 상태 관리
    var reports by remember { mutableStateOf<List<DailyInspectionReport>>(emptyList()) }
    val userId = UserSession.userId

    LaunchedEffect(isScrolling) { if (isScrolling) lastUserInteraction = System.currentTimeMillis() }

    // ✅ 월별 데이터 가져오기
    LaunchedEffect(currentYearMonth) {
        if (userId != null) {
            RetrofitClient.instance.getDailyChecks(userId, currentYearMonth.year, currentYearMonth.monthValue)
                .enqueue(object : Callback<GetDailyChecksResponse> {
                    override fun onResponse(call: Call<GetDailyChecksResponse>, response: Response<GetDailyChecksResponse>) {
                        if (response.isSuccessful) {
                            val checks = response.body()?.checks ?: emptyList()
                            val grouped = checks.groupBy {
                                try {
                                    // Prefer the selected checklist date over the creation timestamp.
                                    dailyChecklistDisplayDate(it.checkDate, it.createdAt)
                                } catch (e: Exception) {
                                    LocalDate.now()
                                }
                            }
                            reports = grouped.map { (date, dtos) ->
                                DailyInspectionReport(
                                    date = date,
                                    items = dtos.map { dto ->
                                        InspectionItem(
                                            checkId = dto.checkId.toString(),
                                            location = dto.location ?: "",
                                            description = dto.hazard ?: "",
                                            status = if (dto.status == "미점검") InspectionStatus.UNCHECKED else InspectionStatus.CHECKED,
                                            specialNote = if (dto.status == "미점검") "🔔 누르면 근로자에게 알림이 가요" else null
                                        )
                                    }
                                )
                            }.sortedBy { it.date }
                        }
                    }
                    override fun onFailure(call: Call<GetDailyChecksResponse>, t: Throwable) {}
                })
        }
    }

    val filteredReports = reports.filter { it.date in startDate..endDate }

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
            Column(modifier = Modifier
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        lastUserInteraction = System.currentTimeMillis()
                    })
                }) {
                YearMonthSelector(yearMonth = currentYearMonth, onMonthChange = { newMonth -> currentYearMonth = newMonth })
                DateRangeSelector(yearMonth = currentYearMonth, reports = reports, onDateChange = { start, end -> startDate = start; endDate = end })
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
        Box(modifier = Modifier
            .background(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(percent = 50)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)) {
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
        Box(modifier = Modifier
            .matchParentSize()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)))
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(1.dp)) {
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
            AnimatedVisibility(visible = tooltipVisible, exit = fadeOut(), modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-40).dp)
                .offset { IntOffset(0, floatingOffset.roundToInt()) }
                .zIndex(10f)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints); layout(
                    0,
                    0
                ) { placeable.place(-placeable.width, 0) }
                }) {
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
    val itemBackgroundColor = if (item.status == InspectionStatus.UNCHECKED && !dialogWasOpened) MaterialTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent
    val buttonBackgroundColor = if (item.status == InspectionStatus.UNCHECKED) if (MaterialTheme.colors.isLight) MaterialTheme.colors.primary.copy(alpha = 0.2f) else  Color(0xFFFB923C) else StatusGreen.copy(alpha = 0.2f)
    val buttonContentColor = if (item.status == InspectionStatus.UNCHECKED) if (MaterialTheme.colors.isLight) MaterialTheme.colors.primary else Color.Black else StatusGreenDark
    val buttonText = if (item.status == InspectionStatus.UNCHECKED) "미점검" else "점검완료"
    val context = LocalContext.current
    
    Row(modifier = Modifier
        .fillMaxWidth()
        .background(itemBackgroundColor, shape = shape)
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.location, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = Pretendard, color = MaterialTheme.colors.onSurface, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.description, fontSize = 14.sp, fontFamily = Pretendard, color = if (MaterialTheme.colors.isLight) TextGray60 else TextGray, maxLines = 2, modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.width(12.dp))
        InspectionItemActions(
            item = item, 
            tooltipVisible = tooltipVisible, 
            onShowDialog = { 
                val userId = UserSession.userId
                if (userId != null) {
                    val title = "안전감시단"
                    val content = "${item.location} 재점검이 필요합니다."
                    val request = SendGroupNotificationRequest(userId, title, content)
                    RetrofitClient.instance.sendGroupNotification(request).enqueue(object : Callback<Void> {
                        override fun onResponse(call: Call<Void>, response: Response<Void>) {
                            if (response.isSuccessful) {
                                showDialog = true
                                dialogWasOpened = true
                                tooltipVisible = false
                            } else {
                                ToastUtil.showShort(context, "알림 전송에 실패했습니다.")
                            }
                        }
                        override fun onFailure(call: Call<Void>, t: Throwable) {
                            ToastUtil.showShort(context, "네트워크 오류가 발생했습니다.")
                        }
                    })
                }
            }, 
            onTooltipTap = { tooltipVisible = false }, 
            buttonBackgroundColor = buttonBackgroundColor, 
            buttonContentColor = buttonContentColor, 
            buttonText = buttonText
        )
    }

    if (showDialog) UncheckedItemDialog(onDismissRequest = { showDialog = false })
}

@Composable
fun UncheckedItemDialog(onDismissRequest: () -> Unit) { Dialog(onDismissRequest = onDismissRequest) { UncheckedItemDialogContent(onDismissRequest = onDismissRequest) } }

@Composable
fun UncheckedItemDialogContent(onDismissRequest: () -> Unit) {
    val cardBgColor = if (MaterialTheme.colors.isLight) Color.White else GrayBackground
    Card(modifier = Modifier
        .width(330.dp)
        .height(259.dp), shape = RoundedCornerShape(16.dp), elevation = 0.dp, backgroundColor = cardBgColor) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            Icon(painter = painterResource(id = R.drawable.bell_icon), null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(24.dp)); Text(text = "점검요청 재알림", fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, fontFamily = Pretendard, color = MaterialTheme.colors.onSurface)
            Spacer(modifier = Modifier.height(4.dp)); Text(text = "근로자에게 점검요청 재알림을 \n발송하였습니다.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center, fontFamily = Pretendard)
            Spacer(modifier = Modifier.height(24.dp)); Button(onClick = onDismissRequest, modifier = Modifier
            .width(290.dp)
            .height(55.dp), elevation = ButtonDefaults.elevation(0.dp, 0.dp), colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary), shape = RoundedCornerShape(12.dp)) { Text(text = "확인", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun YearMonthSelector(yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(yearMonth.minusMonths(1)) }) { Icon(painter = painterResource(id = R.drawable.left), contentDescription = null, tint = Color.Unspecified) }
        Spacer(modifier = Modifier.width(20.dp)); Text(text = "${yearMonth.year}년 ${yearMonth.monthValue}월", fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard, color = MaterialTheme.colors.onBackground)
        Spacer(modifier = Modifier.width(20.dp)); IconButton(onClick = { onMonthChange(yearMonth.plusMonths(1)) }) { Icon(painter = painterResource(id = R.drawable.right), contentDescription = null, tint = Color.Unspecified) }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateRangeSelector(yearMonth: YearMonth, reports: List<DailyInspectionReport>, onDateChange: (LocalDate, LocalDate) -> Unit) {
    val context = LocalContext.current
    var startDateStr by remember { mutableStateOf("") }; var endDateStr by remember { mutableStateOf("") }
    var showCustomPicker by remember { mutableStateOf(false) }; var pickingStartDate by remember { mutableStateOf(true) }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    LaunchedEffect(yearMonth) { val firstDay = yearMonth.atDay(1); val lastDay = yearMonth.atEndOfMonth(); startDateStr = firstDay.format(formatter); endDateStr = lastDay.format(formatter); onDateChange(firstDay, lastDay) }
    val iconTint = if (MaterialTheme.colors.isLight) Color.Unspecified else GrayBorder

    CompositionLocalProvider(LocalRippleTheme provides OrangeRippleTheme) {
        Row(Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = startDateStr, onValueChange = {}, modifier = Modifier
                .weight(1f)
                .height(50.dp), readOnly = true, textStyle = TextStyle(fontFamily = Pretendard, fontSize = 14.sp), trailingIcon = { IconButton(onClick = { pickingStartDate = true; showCustomPicker = true }) { Icon(painter = painterResource(id = R.drawable.calendar2), null, tint = iconTint) } }, colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colors.primary, unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), textColor = MaterialTheme.colors.onSurface))
            Icon(painter = painterResource(id = R.drawable.underbar), null, tint = MaterialTheme.colors.onSurface)
            OutlinedTextField(value = endDateStr, onValueChange = {}, modifier = Modifier
                .weight(1f)
                .height(50.dp), readOnly = true, textStyle = TextStyle(fontFamily = Pretendard, fontSize = 14.sp), trailingIcon = { IconButton(onClick = { pickingStartDate = false; showCustomPicker = true }) { Icon(painter = painterResource(id = R.drawable.calendar2), null, tint = iconTint) } }, colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colors.primary, unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f), textColor = MaterialTheme.colors.onSurface))
        }
    }

    if (showCustomPicker) {
        val initialDate = if (pickingStartDate) (if(startDateStr.isEmpty()) LocalDate.now() else LocalDate.parse(startDateStr, formatter)) else (if(endDateStr.isEmpty()) LocalDate.now() else LocalDate.parse(endDateStr, formatter))
        CustomDatePickerDialog(
            initialDate = initialDate,
            eventDates = reports.map { it.date }.toSet(),
            onDismiss = { showCustomPicker = false },
            onDateSelected = { selectedDate ->
                val currentStart = if (startDateStr.isNotEmpty()) LocalDate.parse(startDateStr, formatter) else null
                val currentEnd = if (endDateStr.isNotEmpty()) LocalDate.parse(endDateStr, formatter) else null

                if (pickingStartDate) {
                    if (currentEnd != null && selectedDate.isAfter(currentEnd)) {
                        ToastUtil.showShort(context, "시작일은 종료일보다 빨라야 합니다.")
                    } else {
                        startDateStr = selectedDate.format(formatter)
                        onDateChange(selectedDate, currentEnd ?: selectedDate)
                        showCustomPicker = false
                    }
                } else {
                    if (currentStart != null && selectedDate.isBefore(currentStart)) {
                        ToastUtil.showShort(context, "종료일은 시작일보다 늦어야 합니다.")
                    } else {
                        endDateStr = selectedDate.format(formatter)
                        onDateChange(currentStart ?: selectedDate, selectedDate)
                        showCustomPicker = false
                    }
                }
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CustomDatePickerDialog(initialDate: LocalDate, eventDates: Set<LocalDate> = emptySet(), onDismiss: () -> Unit, onDateSelected: (LocalDate) -> Unit) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }

    val dayOfWeekColor = Color(0xFF6D7882)
    val isLight = MaterialTheme.colors.isLight

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp), 
            color = if (isLight) Color.White else GrayBackground, 
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val navIconColor = Color(0xFFF97316)
                    IconButton(
                        onClick = { viewMonth = viewMonth.minusMonths(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(painterResource(id = R.drawable.left), null, tint = navIconColor, modifier = Modifier.size(32.dp))
                    }
                    
                    Spacer(modifier = Modifier.width(13.8.dp))
                    
                    YearDropdown(viewMonth.year, modifier = Modifier.weight(1.2f)) { viewMonth = viewMonth.withYear(it) }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    MonthDropdown(viewMonth.monthValue, modifier = Modifier.weight(0.8f)) { viewMonth = viewMonth.withMonth(it) }
                    
                    Spacer(modifier = Modifier.width(13.8.dp))
                    
                    IconButton(
                        onClick = { viewMonth = viewMonth.plusMonths(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(painterResource(id = R.drawable.right), null, tint = navIconColor, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.height(9.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val days = listOf("일", "월", "화", "수", "목", "금", "토")
                    days.forEach { day -> Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp, fontFamily = Pretendard, color = dayOfWeekColor) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                val firstDayOfMonth = viewMonth.atDay(1)
                val dayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
                val daysInMonth = viewMonth.lengthOfMonth()
                
                // 5주(35일)로 안 끝날 때만 6주(42일) 표시하도록 계산
                val totalDaysToShow = if (dayOfWeek + daysInMonth > 35) 42 else 35
                
                val calendarDays = mutableListOf<LocalDate>()
                var currentDay = firstDayOfMonth.minusDays(dayOfWeek.toLong())
                
                repeat(totalDaysToShow) {
                    calendarDays.add(currentDay)
                    currentDay = currentDay.plusDays(1)
                }

                Column {
                    calendarDays.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                val isCurrentMonth = date.month == viewMonth.month
                                val isSelected = date == selectedDate
                                val hasReport = eventDates.contains(date)
                                
                                Box(modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f), contentAlignment = Alignment.Center) {
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
                                                isCurrentMonth -> if (isLight) Color(0xFF58616A) else Color(0xFF8A949E)
                                                else -> if (isLight) Color(0xFFCDD1D5) else Color(0xFF33363D)
                                            },
                                            fontSize = 16.sp,
                                            fontFamily = Pretendard
                                        )
                                        if (hasReport && !isSelected) {
                                            Box(modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colors.primary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier
                        .weight(1f)
                        .height(48.dp), colors = ButtonDefaults.buttonColors(backgroundColor = if (isLight) Color(0xFFF4F5F6) else Color(0xFF131416)), elevation = ButtonDefaults.elevation(0.dp), shape = RoundedCornerShape(8.dp)) { Text("취소", color = if (isLight) Color(0xFF58616A) else Color(0xFF8A949E), fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
                    Button(onClick = { onDateSelected(selectedDate) }, modifier = Modifier
                        .weight(1f)
                        .height(48.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF97316)), elevation = ButtonDefaults.elevation(0.dp), shape = RoundedCornerShape(8.dp)) { Text("선택", color = if (isLight) Color(0xFFFFFFFF) else Color.Black, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
                }
            }
        }
    }
}

// 인원관리 스타일의 주황색 리플 테마 정의
@Immutable
private object OrangeRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor() = Color(0xFFFB923C)

    @Composable
    override fun rippleAlpha(): RippleAlpha {
        val isLight = MaterialTheme.colors.isLight
        val alpha = if (isLight) 0.12f else 0.36f
        return RippleAlpha(alpha, alpha, alpha, alpha)
    }
}

@Composable
fun YearDropdown(year: Int, modifier: Modifier = Modifier, onYearSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val isLight = MaterialTheme.colors.isLight
    val itemTextColor = if (isLight) Color(0xFF33363D) else Color(0xFFCDD1D5)
    val dropdownBorderColor = if (isLight) Color(0xFFCDD1D5) else Color(0xFF33363D)
    val dividerColor = if (isLight) Color(0xFFF4F5F6) else Color(0xFF131416)
    val dropdownBgColor = if (isLight) Color.White else GrayBackground
    val shadowColor = Color.Black.copy(alpha = if (isLight) 0.08f else 0.20f)
    
    Box(modifier = modifier.onGloballyPositioned { width = it.size.width }) {
        SelectorBox(
            text = "${year}년",
            isExpanded = expanded,
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        )
        MaterialTheme(
            colors = MaterialTheme.colors.copy(surface = dropdownBgColor),
            shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))
        ) {
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(with(density) { width.toDp() })
                    .height(153.dp)
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = shadowColor,
                        spotColor = shadowColor
                    )
                    .border(1.dp, dropdownBorderColor, RoundedCornerShape(8.dp)),
                offset = DpOffset(x = 0.dp, y = 0.dp),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                CompositionLocalProvider(LocalRippleTheme provides OrangeRippleTheme) {
                    val years = (2026..2100).toList()
                    years.forEachIndexed { index, y ->
                        DropdownMenuItem(onClick = { onYearSelected(y); expanded = false }) {
                            Text(
                                text = "${y}년",
                                fontFamily = Pretendard,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp,
                                color = itemTextColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (index < years.size - 1) {
                            Divider(color = dividerColor, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthDropdown(month: Int, modifier: Modifier = Modifier, onMonthSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val isLight = MaterialTheme.colors.isLight
    val itemTextColor = if (isLight) Color(0xFF33363D) else Color(0xFFCDD1D5)
    val dropdownBorderColor = if (isLight) Color(0xFFCDD1D5) else Color(0xFF33363D)
    val dividerColor = if (isLight) Color(0xFFF4F5F6) else Color(0xFF131416)
    val dropdownBgColor = if (isLight) Color.White else GrayBackground
    val shadowColor = Color.Black.copy(alpha = if (isLight) 0.08f else 0.20f)
    
    Box(modifier = modifier.onGloballyPositioned { width = it.size.width }) {
        SelectorBox(
            text = "${month}월",
            isExpanded = expanded,
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        )
        MaterialTheme(
            colors = MaterialTheme.colors.copy(surface = dropdownBgColor),
            shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(8.dp))
        ) {
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(with(density) { width.toDp() })
                    .height(153.dp)
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = shadowColor,
                        spotColor = shadowColor
                    )
                    .border(1.dp, dropdownBorderColor, RoundedCornerShape(8.dp)),
                offset = DpOffset(x = 0.dp, y = 0.dp),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                CompositionLocalProvider(LocalRippleTheme provides OrangeRippleTheme) {
                    val months = (1..12).toList()
                    months.forEachIndexed { index, m ->
                        DropdownMenuItem(onClick = { onMonthSelected(m); expanded = false }) {
                            Text(
                                text = "${m}월",
                                fontFamily = Pretendard,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp,
                                color = itemTextColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (index < months.size - 1) {
                            Divider(color = dividerColor, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectorBox(text: String, isExpanded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isLight = MaterialTheme.colors.isLight
    val textColor = if (isLight) Color(0xFF131416) else Color(0xFFF4F5F6)
    val shadowColor = Color.Black.copy(alpha = if (isLight) 0.08f else 0.20f)
    
    Box(
        modifier = modifier
            .height(51.dp)
            .then(
                if (isExpanded) {
                    Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(8.dp),
                            ambientColor = shadowColor,
                            spotColor = shadowColor
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                } else Modifier
            )
            .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text, 
                fontWeight = FontWeight.Bold, 
                fontSize = 24.sp, 
                fontFamily = Pretendard, 
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown, 
                contentDescription = null, 
                tint = textColor, 
                modifier = Modifier.requiredSize(24.dp)
            )
        }
    }
}
