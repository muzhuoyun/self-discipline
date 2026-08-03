package com.selfdiscipline.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfdiscipline.app.ai.AiStreamState
import com.selfdiscipline.app.data.AiKinds
import com.selfdiscipline.app.data.Category
import com.selfdiscipline.app.data.DailyRecord
import com.selfdiscipline.app.data.Metrics
import androidx.compose.material3.Surface
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 历史页：月历 + 趋势图 + AI 周报/月报 */
@Composable
fun HistoryScreen(
    vm: MainViewModel,
    onEditCategory: (LocalDate, Category) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReportHistory: () -> Unit,
) {
    val records by vm.records.collectAsState()
    val weeklyState by vm.weekly.collectAsState()
    val weeklyLabel by vm.weeklyLabel.collectAsState()
    val monthlyState by vm.monthly.collectAsState()
    val monthlyLabel by vm.monthlyLabel.collectAsState()
    val byDate = records.associateBy { it.date }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var dialogDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "历史",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "日历看全貌，趋势看变化，周报月报看成长",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenReportHistory) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = "报告历史",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 月历 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上一月")
                    }
                    Text(
                        "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下一月")
                    }
                    TextButton(onClick = { month = YearMonth.now() }) { Text("今天") }
                }
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAYS.forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val firstDay = month.atDay(1)
                val offset = (firstDay.dayOfWeek.value + 6) % 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (week in 0 until 6) {
                        Row(Modifier.fillMaxWidth()) {
                            for (i in 0 until 7) {
                                val date = firstDay.minusDays(offset.toLong()).plusDays((week * 7 + i).toLong())
                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (date.year == month.year && date.monthValue == month.monthValue) {
                                        DayCell(
                                            date = date,
                                            record = byDate[date.toString()],
                                            isToday = date == LocalDate.now(),
                                            onClick = { dialogDate = date },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LegendItem("精进", 85..100)
                    LegendItem("良好", 70..84)
                    LegendItem("待提升", 55..69)
                    LegendItem("调整", 0..54)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "点击任意日期可查看历史与 AI 评价",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---------- 趋势 ----------
        TrendSection(vm = vm, records = records)

        // ---------- AI 报告（周报 / 月报 Tab 切换） ----------
        var reportTab by remember { mutableStateOf(0) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column {
                TabRow(selectedTabIndex = reportTab) {
                    Tab(
                        selected = reportTab == 0,
                        onClick = { reportTab = 0 },
                        text = { Text("📅 周报") },
                    )
                    Tab(
                        selected = reportTab == 1,
                        onClick = { reportTab = 1 },
                        text = { Text("🗓 月报") },
                    )
                }
                if (reportTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "生成上一周的 AI 分析报告",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { vm.generateWeeklyReport() },
                            enabled = weeklyState !is AiStreamState.Loading && weeklyState !is AiStreamState.Streaming,
                        ) {
                            Text("生成上周周报")
                        }
                    }
                    ReportBody(label = weeklyLabel, state = weeklyState)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "生成上一个月的 AI 分析报告",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { vm.generateMonthlyReport() },
                            enabled = monthlyState !is AiStreamState.Loading && monthlyState !is AiStreamState.Streaming,
                        ) {
                            Text("生成上月月报")
                        }
                    }
                    ReportBody(label = monthlyLabel, state = monthlyState)
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    dialogDate?.let { d ->
        DayDetailDialog(
            vm = vm,
            date = d,
            record = byDate[d.toString()],
            onPick = { category ->
                dialogDate = null
                onEditCategory(d, category)
            },
            onDismiss = { dialogDate = null },
        )
    }
}

/** 一栏报告的正文状态 */
@Composable
private fun ReportBody(label: String, state: AiStreamState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        when (state) {
        is AiStreamState.Idle -> {
            Text(
                "生成上一周期（上周 / 上月）的 AI 报告。每次生成的报告都会存档。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is AiStreamState.Loading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("AI 正在整理数据…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is AiStreamState.Streaming -> {
            ReportText(label = label, text = (state as AiStreamState.Streaming).text)
        }
        is AiStreamState.Done -> {
            ReportText(label = label, text = (state as AiStreamState.Done).text, footer = "💾 已自动存档")
        }
        is AiStreamState.Error -> {
            Text(
                "生成失败：${(state as AiStreamState.Error).message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        }
    }
}


@Composable
private fun ReportText(label: String, text: String, footer: String? = null) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
        footer?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// ---------- 趋势区 ----------

/** 可查看趋势的指标 */
enum class TrendMetric(val label: String, val max: Int, val value: (DailyRecord) -> Int) {
    TOTAL("总分", 100, { Metrics.total(it) }),
    JIE_YIN("戒淫", 10, { Metrics.score(Category.JIE_YIN, it) }),
    JIE_CHAN("戒馋", 10, { Metrics.score(Category.JIE_CHAN, it) }),
    JIE_TAN("戒贪", 10, { Metrics.score(Category.JIE_TAN, it) }),
    XIU_YANG("修养", 10, { Metrics.score(Category.XIU_YANG, it) }),
    XIU_TI("修体", 10, { Metrics.score(Category.XIU_TI, it) }),
    XIU_XING("修行", 10, { Metrics.score(Category.XIU_XING, it) }),
    XIAO("孝", 10, { Metrics.score(Category.XIAO, it) }),
    CHENG("诚", 10, { Metrics.score(Category.CHENG, it) }),
    HE("和", 10, { Metrics.score(Category.HE, it) }),
    QIN("勤", 10, { Metrics.score(Category.QIN, it) }),
}

@Composable
private fun TrendSection(vm: MainViewModel, records: List<DailyRecord>) {
    val byDate = records.associateBy { it.date }
    var metric by remember { mutableStateOf(TrendMetric.TOTAL) }

    val today = LocalDate.now()
    val start = today.minusDays(29)
    val points = (0L..29L).map { days ->
        byDate[start.plusDays(days).toString()]?.let { metric.value(it) }
    }
    val values = points.filterNotNull()
    val average: Double? = if (values.isEmpty()) null else values.average()
    val target = if (metric == TrendMetric.TOTAL) 75 else 8

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${start.monthValue}月${start.dayOfMonth}日 ~ ${today.monthValue}月${today.dayOfMonth}日",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TrendMetric.entries) { m ->
                    FilterChip(
                        selected = metric == m,
                        onClick = { metric = m },
                        label = { Text(m.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (values.isEmpty()) {
                Text(
                    "近 30 天还没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                TrendChart(points = points, maxY = metric.max, average = average?.toFloat())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("近30天平均", ((average ?: 0.0) * 10).roundToInt() / 10.0)
                    StatItem("最高", values.max().toString())
                    StatItem("达标天数", "${values.count { it >= target }} 天")
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Any) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 手绘折线图：网格 + 折线 + 数据点 + 均值虚线 */
@Composable
private fun TrendChart(
    points: List<Int?>,
    maxY: Int,
    average: Float?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val labelSpace = 34.dp.toPx()
        val chartLeft = labelSpace
        val chartRight = size.width - 8.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 8.dp.toPx()
        val usableW = chartRight - chartLeft
        val usableH = chartBottom - chartTop

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
            val y = chartBottom - f * usableH
            drawLine(gridColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1.dp.toPx())
            val value = (maxY * f).roundToInt()
            val layout = textMeasurer.measure(
                AnnotatedString("$value"),
                style = TextStyle(fontSize = 10.sp, color = labelColor),
            )
            drawText(
                textMeasurer,
                AnnotatedString("$value"),
                topLeft = Offset(chartLeft - labelSpace + 2.dp.toPx(), y - layout.size.height / 2f),
                style = TextStyle(fontSize = 10.sp, color = labelColor),
            )
        }

        val valid = points.mapIndexedNotNull { i, v -> if (v != null) i to v else null }
        if (valid.isEmpty()) return@Canvas

        val xOf = { i: Int -> chartLeft + usableW * i / (points.size - 1).coerceAtLeast(1) }
        val yOf = { v: Int -> chartBottom - usableH * (v / maxY.toFloat()).coerceIn(0f, 1f) }

        val areaPath = Path().apply {
            moveTo(xOf(valid.first().first), chartBottom)
            valid.forEach { (i, v) -> lineTo(xOf(i), yOf(v)) }
            lineTo(xOf(valid.last().first), chartBottom)
            close()
        }
        drawPath(areaPath, lineColor.copy(alpha = 0.12f))

        val linePath = Path().apply {
            valid.forEachIndexed { idx, (i, v) ->
                if (idx == 0) moveTo(xOf(i), yOf(v)) else lineTo(xOf(i), yOf(v))
            }
        }
        drawPath(
            linePath,
            lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        valid.forEach { (i, v) ->
            drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }

        average?.let { avg ->
            val yAvg = chartBottom - usableH * (avg / maxY).coerceIn(0f, 1f)
            drawLine(
                avgColor,
                Offset(chartLeft, yAvg),
                Offset(chartRight, yAvg),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
            val avgText = AnnotatedString("均值 ${(avg * 10).roundToInt() / 10.0}")
            val avgLayout = textMeasurer.measure(avgText, style = TextStyle(fontSize = 10.sp, color = avgColor))
            drawText(
                textMeasurer,
                avgText,
                topLeft = Offset(chartRight - avgLayout.size.width, yAvg - avgLayout.size.height - 2.dp.toPx()),
                style = TextStyle(fontSize = 10.sp, color = avgColor),
            )
        }
    }
}

// ---------- 月历组件 ----------

@Composable
private fun DayCell(
    date: LocalDate,
    record: DailyRecord?,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val total = record?.let { Metrics.total(it) }
    val bg = if (record != null) calendarColor(total!!)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    var modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(10.dp))
        .background(bg)
        .clickable(onClick = onClick)
    if (isToday) {
        modifier = modifier.border(
            2.dp,
            MaterialTheme.colorScheme.primary,
            RoundedCornerShape(10.dp),
        )
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (record != null) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record != null) {
                Text(
                    "$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, range: IntRange) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(calendarColor(range.first))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$label ${range.first}~${range.last}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 某一天的详情弹窗：六类得分一览，点任意一项进入编辑 */
@Composable
private fun DayDetailDialog(
    vm: MainViewModel,
    date: LocalDate,
    record: DailyRecord?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    val aiChats by vm.aiChats.collectAsState()
    val today = LocalDate.now()
    val isToday = date == today
    // 过去 = 已归档；未来 = 再等 N 天
    val statusHint = when {
        date.isBefore(today) -> "🗂 已归档 · 只能查看，不能修改或补录"
        date.isAfter(today) ->
            "⏳ 还没到 · 再等 ${ChronoUnit.DAYS.between(today, date)} 天再来打卡"
        else -> null
    }
    // 该日期的 AI 打卡评价（每天只保留一份）
    val review = aiChats.lastOrNull {
        it.kind == AiKinds.REVIEW && it.date == date.toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}月${date.dayOfMonth}日 · ${date.weekdayCn}") },
        text = {
            Column {
                statusHint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (record == null) {
                    Text(
                        if (isToday) "今天还没有打分，点下面的项目开始。"
                        else "这一天没有打分记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    val total = Metrics.total(record)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "总分 $total / 100",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusLevel(total).color,
                        )
                        Spacer(Modifier.weight(1f))
                        StarRow(total / 6, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // 该日期的 AI 教练评价
                review?.let { r ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "🤖 教练点评",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                r.response,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Category.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { if (isToday) onPick(category) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                category.title,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            StarRow(record?.let { Metrics.score(category, it) } ?: 0, fontSize = 10.sp)
                        }
                        Text(
                            "${Metrics.score(category, record ?: DailyRecord(date.toString()))} / 10",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
