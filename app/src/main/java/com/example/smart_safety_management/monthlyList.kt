package com.example.smart_safety_management

import android.app.DatePickerDialog
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar

enum class InspectionStatus { CHECKED, UNCHECKED }

data class InspectionItem(val location: String, val description: String, val status: InspectionStatus, val specialNote: String? = null)

data class DailyInspectionReport(val date: LocalDate, val items: List<InspectionItem>)

@RequiresApi(Build.VERSION_CODES.O)
val mockReports = listOf(
    DailyInspectionReport(
        date = LocalDate.of(2026, 1, 7),
        items = listOf(
            InspectionItem("E구역 7열", "조명 미점검으로 인한 안전 문제 발생", InspectionStatus.UNCHECKED, "누르면 근로자에게 알림이 가요"),
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

@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
fun MonthlyListScreen() {
    var currentYearMonth by remember { mutableStateOf(YearMonth.of(2026, 1)) }
    var startDate by remember { mutableStateOf(currentYearMonth.atDay(1)) }
    var endDate by remember { mutableStateOf(currentYearMonth.atEndOfMonth()) }

    val filteredReports = mockReports.filter { it.date in startDate..endDate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("월별로 보기", fontWeight = FontWeight.Bold, color = Color.Black) },
                navigationIcon = { IconButton(onClick = { /* Handle back */ }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.Black) } },
                backgroundColor = Color.White
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            YearMonthSelector(yearMonth = currentYearMonth, onMonthChange = { newMonth -> currentYearMonth = newMonth })
            DateRangeSelector(yearMonth = currentYearMonth, onDateChange = { start, end ->
                startDate = start
                endDate = end
            })
            Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(filteredReports) { report ->
                    ReportHeader(report = report)
                    Spacer(modifier = Modifier.height(8.dp)) // 헤더와 카드 사이 간격 조정
                    DailyReportItemsCard(report = report)
                    Spacer(modifier = Modifier.height(16.dp))
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(text = "${report.date.dayOfMonth}일", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(color = Color.White, shape = RoundedCornerShape(percent = 50))
                .border(width = 1.dp, color = Color.LightGray, shape = RoundedCornerShape(percent = 50))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = if (allChecked) Color.Gray else Color(0xFFFFA726), fontWeight = FontWeight.Bold)) {
                        append(checkedCount.toString())
                    }
                    withStyle(style = SpanStyle(color = Color.Gray)) {
                        append("/${totalCount}")
                    }
                },
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun DailyReportItemsCard(report: DailyInspectionReport) {
    Card(elevation = 4.dp, shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            report.items.forEach { item ->
                InspectionItemView(item = item)
            }
        }
    }
}

@Composable
fun InspectionItemView(item: InspectionItem) {
    var showDialog by remember { mutableStateOf(false) }
    val itemBackgroundColor = if (item.status == InspectionStatus.UNCHECKED) Color(0xFFFFF8E1) else Color.Transparent
    val buttonBackgroundColor: Color
    val buttonContentColor: Color

    if (item.status == InspectionStatus.UNCHECKED) {
        buttonContentColor = Color(0xFFFFA726)
        buttonBackgroundColor = Color(0xFFFFF8E1)
    } else {
        buttonContentColor = Color(0xFF66BB6A)
        buttonBackgroundColor = Color(0xFFE8F5E9)
    }

    val buttonText = if (item.status == InspectionStatus.UNCHECKED) "미점검" else "점검완료"

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("점검요청 재알림", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = { Text("세부 내용을 입력하세요.") },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBackgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.location, fontWeight = FontWeight.Bold)
            Text(text = item.description, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box {
            Button(
                onClick = {
                    if (item.status == InspectionStatus.UNCHECKED) {
                        showDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = buttonBackgroundColor,
                    contentColor = buttonContentColor
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val icon = if (item.status == InspectionStatus.UNCHECKED) Icons.Default.Notifications else Icons.Default.Check
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = buttonText, fontWeight = FontWeight.Black)
            }

            if (item.specialNote != null && item.status == InspectionStatus.UNCHECKED) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.DarkGray,
                    elevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (-32).dp)
                ) {
                    Text(
                        text = item.specialNote,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun YearMonthSelector(yearMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(yearMonth.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Month") }
        Text(text = "${yearMonth.year}년 ${yearMonth.monthValue}월", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onMonthChange(yearMonth.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Month") }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateRangeSelector(yearMonth: YearMonth, onDateChange: (LocalDate, LocalDate) -> Unit) {
    var startDateStr by remember { mutableStateOf("") }
    var endDateStr by remember { mutableStateOf("") }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    LaunchedEffect(yearMonth) {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()
        startDateStr = firstDay.format(formatter)
        endDateStr = lastDay.format(formatter)
        onDateChange(firstDay, lastDay)
    }

    val context = LocalContext.current

    fun showDatePicker(isStartDate: Boolean, initialDate: LocalDate) {
        DatePickerDialog(context, { _, year, month, day ->
                val newDate = LocalDate.of(year, month + 1, day)
                if (isStartDate) {
                    startDateStr = newDate.format(formatter)
                } else {
                    endDateStr = newDate.format(formatter)
                }
                onDateChange(LocalDate.parse(startDateStr, formatter), LocalDate.parse(endDateStr, formatter))
            }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth
        ).show()
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(value = startDateStr, onValueChange = { }, modifier = Modifier.weight(1f).height(50.dp), readOnly = true, trailingIcon = { IconButton(onClick = { showDatePicker(true, LocalDate.parse(startDateStr, formatter)) }) { Icon(Icons.Default.DateRange, "Select Start Date") } })
        Text(text = "-")
        OutlinedTextField(value = endDateStr, onValueChange = { }, modifier = Modifier.weight(1f).height(50.dp), readOnly = true, trailingIcon = { IconButton(onClick = { showDatePicker(false, LocalDate.parse(endDateStr, formatter)) }) { Icon(Icons.Default.DateRange, "Select End Date") } })
    }
}
