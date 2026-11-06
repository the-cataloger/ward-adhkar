package org.ward.adhkar

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ward.adhkar.data.Dhikr
import org.ward.adhkar.data.DhikrRepository
import org.ward.adhkar.data.Period
import org.ward.adhkar.data.WardStore
import org.ward.adhkar.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.LocalTime

private val Night = Color(0xFF071510)
private val Panel = Color(0xFF10241C)
private val Raised = Color(0xFF19372B)
private val Gold = Color(0xFFE3C17E)
private val Cream = Color(0xFFF2EEE2)
private val Muted = Color(0xFFAAC0B2)
private val Line = Color(0xFF2C4A3B)
private val Success = Color(0xFF96D4A6)
private val Rounded = RoundedCornerShape(24.dp)

class MainActivity : ComponentActivity() {
    private var openedPeriod by mutableStateOf("morning")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedPeriod = intent?.getStringExtra("open_period") ?: "morning"
        ReminderScheduler.createChannel(this)
        ReminderScheduler.sync(this)
        val store = WardStore(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = Gold, onPrimary = Night, background = Night,
                    onBackground = Cream, surface = Panel, onSurface = Cream, secondary = Success
                )) {
                    WardScreen(store, openedPeriod)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_period")?.let { openedPeriod = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WardScreen(store: WardStore, openedPeriod: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val corpus = remember { DhikrRepository.load(context) }
    var periodId by rememberSaveable { mutableStateOf(openedPeriod) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var screen by rememberSaveable { mutableStateOf("reader") }
    var showCatalog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var version by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = LocalDate.now()
                version++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(openedPeriod) {
        if (periodId != openedPeriod) {
            periodId = openedPeriod
            selectedIndex = 0
            screen = "reader"
        }
    }

    val period = Period.fromId(periodId)
    val entries = remember(period, corpus) { corpus.filter { it.belongsTo(period) } }
    val index = selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val item = entries.getOrNull(index)
    @Suppress("UNUSED_VARIABLE") val snapshot = version
    val completed = entries.count { store.count(period, it.id, today) >= it.target }
    val progress = completed.toFloat() / entries.size.coerceAtLeast(1)

    BackHandler(screen != "reader" || showCatalog) {
        if (showCatalog) showCatalog = false else screen = "reader"
    }

    Column(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Night, Color(0xFF0B1D17), Night))
    ).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        when (screen) {
            "settings" -> SettingsScreen(store) { screen = "reader"; version++ }
            "stats" -> StatisticsScreen(store, corpus, today) { screen = "reader" }
            else -> {
                Row(Modifier.fillMaxWidth().height(75.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(53.dp).clip(CircleShape).background(Raised)
                        .border(1.dp, Gold.copy(alpha = .6f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("۞", fontSize = 35.sp, color = Gold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("وِرد", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("لحظات من السكينة كل يوم", color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { screen = "stats" }) {
                        Icon(Icons.Default.Insights, "الإحصاءات", tint = Gold)
                    }
                    IconButton(onClick = { screen = "settings" }) {
                        Icon(Icons.Default.Settings, "الإعدادات", tint = Gold)
                    }
                }

                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(Panel).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Period.entries.forEach { candidate ->
                        val active = period == candidate
                        Box(Modifier.weight(1f).height(47.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) Gold else Color.Transparent)
                            .clickable {
                                periodId = candidate.id
                                selectedIndex = 0
                            }, contentAlignment = Alignment.Center) {
                            Text((if (candidate == Period.MORNING) "☀ " else "☾ ") +
                                "أذكار ${candidate.label}", color = if (active) Night else Muted,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Panel).border(1.dp, Line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("إنجاز وِرد ${period.label}", color = Cream, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Text("$completed / ${entries.size}", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Gold, trackColor = Line)
                }
                Spacer(Modifier.height(12.dp))
                if (item != null) {
                    RecitationCard(item, period, index + 1, entries.size,
                        store.isFavorite(item.id), store.fontScale,
                        onFavorite = { store.toggleFavorite(item.id); version++ },
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp))
                    Counter(
                        count = store.count(period, item.id, today),
                        target = item.target,
                        hapticsEnabled = store.hapticsEnabled,
                        onIncrement = {
                            val previous = store.count(period, item.id, today)
                            if (previous < item.target) {
                                store.setCount(period, item.id, previous + 1, today)
                                version++
                                if (previous + 1 == item.target && store.autoAdvance) {
                                    val next = entries.indices.firstOrNull {
                                        it > index && store.count(period, entries[it].id, today) < entries[it].target
                                    }
                                    if (next != null) scope.launch {
                                        delay(650)
                                        selectedIndex = next
                                    }
                                }
                            }
                        },
                        onUndo = {
                            val previous = store.count(period, item.id, today)
                            if (previous > 0) {
                                store.setCount(period, item.id, previous - 1, today)
                                version++
                            }
                        }
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selectedIndex = (index - 1).coerceAtLeast(0) },
                            enabled = index > 0) { Text("❮ السابق") }
                        OutlinedButton(onClick = { showCatalog = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                            Icon(Icons.Default.FormatListBulleted, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("كل الأذكار", fontSize = 13.sp)
                        }
                        TextButton(onClick = { selectedIndex = (index + 1).coerceAtMost(entries.lastIndex) },
                            enabled = index < entries.lastIndex) { Text("التالي ❯") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCatalog && screen == "reader") {
        ModalBottomSheet(
            onDismissRequest = { showCatalog = false },
            containerColor = Panel, contentColor = Cream
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("فهرس أذكار ${period.label}", fontSize = 23.sp,
                    color = Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("البحث عن ذكر أو مصدر") },
                    leadingIcon = { Icon(Icons.Default.Search, null) })
                FilterChip(selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("المفضلة فقط") },
                    leadingIcon = { Icon(if (favoritesOnly) Icons.Default.Favorite
                        else Icons.Default.FavoriteBorder, null) })
                val visible = entries.withIndex().filter { (_, dhikr) ->
                    (!favoritesOnly || store.isFavorite(dhikr.id)) &&
                        (query.isBlank() || dhikr.title.contains(query, true) ||
                            dhikr.source.contains(query, true) ||
                            dhikr.content(period).contains(query, true))
                }
                if (visible.isEmpty()) {
                    Text("لا توجد نتائج مطابقة", modifier = Modifier.padding(16.dp), color = Muted)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 525.dp)) {
                    items(visible, key = { it.value.id }) { indexed ->
                        val dhikr = indexed.value
                        val tally = store.count(period, dhikr.id, today)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedIndex = indexed.index
                                showCatalog = false
                            }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(33.dp).clip(CircleShape)
                                .background(if (tally >= dhikr.target) Raised else Line),
                                contentAlignment = Alignment.Center) {
                                Text(if (tally >= dhikr.target) "✓" else "${indexed.index + 1}",
                                    color = Gold, fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(dhikr.title, color = Cream, modifier = Modifier.weight(1f),
                                fontSize = 14.sp)
                            Text("$tally/${dhikr.target}", color = Muted, fontSize = 12.sp)
                            IconButton(onClick = {
                                store.toggleFavorite(dhikr.id)
                                version++
                            }) {
                                Icon(if (store.isFavorite(dhikr.id)) Icons.Default.Favorite
                                    else Icons.Default.FavoriteBorder,
                                    "إضافة أو إزالة من المفضلة", tint = Gold)
                            }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun RecitationCard(
    dhikr: Dhikr, period: Period, ordinal: Int, total: Int,
    favorite: Boolean, fontScale: Float, onFavorite: () -> Unit, modifier: Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(dhikr.id, period) { scroll.scrollTo(0) }
    Column(modifier.fillMaxWidth().clip(Rounded)
        .background(Brush.verticalGradient(listOf(Raised, Panel)))
        .border(1.dp, Gold.copy(alpha = .2f), Rounded).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الذكر $ordinal من $total", color = Gold, fontSize = 12.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite, modifier = Modifier.size(37.dp)) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = Gold, modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(dhikr.content(period) + "\nالمصدر: " + dhikr.source))
            }, modifier = Modifier.size(37.dp)) {
                Icon(Icons.Default.ContentCopy, "نسخ الذكر", tint = Gold, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = {
                val message = dhikr.content(period) + "\n\nالمصدر: " + dhikr.source + "\nمن تطبيق وِرد"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(send, "مشاركة الذكر"))
            }, modifier = Modifier.size(37.dp)) {
                Icon(Icons.Default.Share, "مشاركة الذكر", tint = Gold, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(dhikr.title, modifier = Modifier.fillMaxWidth(), color = Gold,
            fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Text(dhikr.content(period), color = Cream, fontFamily = FontFamily.Serif,
                    fontSize = (22 * fontScale).sp, lineHeight = (38 * fontScale).sp,
                    textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Text("المصدر: ${dhikr.source}", color = Muted, fontSize = 12.sp,
                lineHeight = 20.sp, textAlign = TextAlign.Center)
            if (dhikr.note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(dhikr.note, color = Muted, fontSize = 12.sp,
                    lineHeight = 20.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Counter(
    count: Int, target: Int, hapticsEnabled: Boolean,
    onIncrement: () -> Unit, onUndo: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val done = count >= target
    val ratio by animateFloatAsState((count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f,1f),
        label = "counter_progress")
    Row(Modifier.fillMaxWidth().height(154.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onUndo, enabled = count > 0,
            modifier = Modifier.width(80.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Undo, null)
                Text("تراجع", fontSize = 12.sp)
            }
        }
        Box(Modifier.size(148.dp).clip(CircleShape)
            .background(if (done) Raised else Gold)
            .clickable(enabled = !done) {
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onIncrement()
            }.semantics {
                contentDescription = if (done) "اكتمل الذكر"
                    else "العدّاد؛ $count من $target، اضغط مرة لكل تكرار"
            }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                val inset = 6.dp.toPx()
                val diameter = size.minDimension - 2 * inset
                drawArc(color = if (done) Success else Night.copy(alpha = .2f),
                    startAngle = -90f, sweepAngle = 360f,
                    useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = w, cap = StrokeCap.Round))
                drawArc(color = if (done) Gold else Night,
                    startAngle = -90f, sweepAngle = ratio * 360f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (done) {
                    Icon(Icons.Default.Check, null, tint = Gold, modifier = Modifier.size(30.dp))
                    Text("اكتمل الذكر", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("${target - count}", color = Night, fontSize = 42.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 48.sp)
                    Text("اضغط للعدّ", color = Night, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold)
                    Text("$count من $target", color = Night.copy(alpha = .7f), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.width(80.dp))
    }
}

@Composable
private fun SettingsScreen(store: WardStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var morningEnabled by remember { mutableStateOf(store.isReminderEnabled(Period.MORNING)) }
    var eveningEnabled by remember { mutableStateOf(store.isReminderEnabled(Period.EVENING)) }
    var morningTime by remember { mutableStateOf(store.reminderTime(Period.MORNING)) }
    var eveningTime by remember { mutableStateOf(store.reminderTime(Period.EVENING)) }
    var fontScale by remember { mutableFloatStateOf(store.fontScale) }
    var haptics by remember { mutableStateOf(store.hapticsEnabled) }
    var advance by remember { mutableStateOf(store.autoAdvance) }
    var denied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Period?>(null) }

    fun update(period: Period, enabled: Boolean) {
        store.setReminderEnabled(period, enabled)
        if (period == Period.MORNING) morningEnabled = enabled else eveningEnabled = enabled
        ReminderScheduler.schedule(context, period)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        pending?.let { if (granted) update(it, true) }
        pending = null
    }

    fun toggle(period: Period, enabled: Boolean) {
        if (!enabled) { update(period, false); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            pending = period
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            denied = false
            update(period, true)
        }
    }

    fun pickTime(period: Period) {
        val current = store.reminderTime(period)
        TimePickerDialog(context, { _, hour, minute ->
            val next = LocalTime.of(hour, minute)
            store.setReminderTime(period, next)
            if (period == Period.MORNING) morningTime = next else eveningTime = next
            ReminderScheduler.schedule(context, period)
        }, current.hour, current.minute, false).show()
    }

    Column(Modifier.fillMaxSize()) {
        SubHeader("إعدادات وِرد", onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("التذكير اليومي", fontSize = 22.sp, color = Gold,
                fontWeight = FontWeight.Bold)
            Text("مواعيد محلية ثابتة؛ قد تتأخر الإشعارات بسبب توفير البطارية.",
                color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(15.dp))
            ReminderCard("☀", "أذكار الصباح", morningEnabled, morningTime,
                { toggle(Period.MORNING, it) }, { pickTime(Period.MORNING) })
            Spacer(Modifier.height(12.dp))
            ReminderCard("☾", "أذكار المساء", eveningEnabled, eveningTime,
                { toggle(Period.EVENING, it) }, { pickTime(Period.EVENING) })
            if (denied) Text("يُرجى السماح بالإشعارات من إعدادات الهاتف.",
                color = Gold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
            Spacer(Modifier.height(25.dp))
            Text("تفضيلات القراءة", fontSize = 21.sp, color = Gold,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(Rounded).background(Panel).padding(17.dp)) {
                Text("حجم خط الأذكار", color = Cream)
                Spacer(Modifier.height(10.dp))
                Text("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", color = Cream,
                    fontFamily = FontFamily.Serif, fontSize = (22 * fontScale).sp)
                Slider(value = fontScale, onValueChange = {
                    fontScale = it
                    store.fontScale = it
                }, valueRange = .8f..1.4f)
                PrefSwitch("اهتزاز خفيف عند العدّ", haptics) {
                    haptics = it; store.hapticsEnabled = it
                }
                PrefSwitch("انتقال تلقائي بعد اكتمال الذكر", advance) {
                    advance = it; store.autoAdvance = it
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("الخصوصية", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("لا يحتاج التطبيق إلى الإنترنت، ولا يجمع بيانات الاستخدام. تُحفظ الأذكار والإعدادات على جهازك.",
                color = Muted, fontSize = 13.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(18.dp))
            Text("وِرد 1.1.0 • مجموعة مختارة تحتاج تدقيقًا علميًا نهائيًا قبل النشر العام.",
                color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(35.dp))
        }
    }
}

@Composable
private fun PrefSwitch(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = Cream, fontSize = 14.sp)
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun ReminderCard(symbol: String, title: String, enabled: Boolean, time: LocalTime,
                         onToggle: (Boolean) -> Unit, onTime: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Rounded).background(Panel)
        .border(1.dp, Line, Rounded).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, fontSize = 25.sp, color = Gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (enabled) "مفعّل" else "متوقف", color = if (enabled) Success else Muted,
                    fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onTime, modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Icon(Icons.Default.Alarm, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("الموعد: ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("تعديل", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatisticsScreen(store: WardStore, corpus: List<Dhikr>,
                             today: LocalDate, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubHeader("إحصاءات وِرد", onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("إنجاز آخر سبعة أيام", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Period.entries.forEach { period ->
                val entries = corpus.filter { it.belongsTo(period) }
                val count = store.completedCount(period, entries, today)
                Column(Modifier.fillMaxWidth().clip(Rounded).background(Panel)
                    .border(1.dp, Line, Rounded).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("أذكار ${period.label}", color = Cream, fontSize = 18.sp,
                            modifier = Modifier.weight(1f))
                        Text("$count / ${entries.size}", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / entries.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = Gold, trackColor = Line)
                    Spacer(Modifier.height(15.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (daysAgo in 6 downTo 0) {
                            val date = today.minusDays(daysAgo.toLong())
                            val done = store.isSessionComplete(period, entries, date)
                            val partial = store.completedCount(period, entries, date) > 0
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (done) Gold else if (partial) Raised
                                        else Line.copy(alpha = .5f)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (done) "✓" else "${date.dayOfMonth}",
                                        fontSize = 12.sp, color = if (done) Night else Muted)
                                }
                                Text("${date.dayOfMonth}", fontSize = 10.sp, color = Muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Text("الذهبي: أُكملت جميع أذكار الفترة. الأخضر: إنجاز جزئي. جميع الإحصاءات محلية.",
                color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(74.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowForward, "رجوع", tint = Gold)
        }
        Text(title, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
