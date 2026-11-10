package org.ward.adhkar

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.ward.adhkar.audio.WardSpeech
import org.ward.adhkar.audio.WardSounds
import org.ward.adhkar.data.Dhikr
import org.ward.adhkar.data.DhikrRepository
import org.ward.adhkar.data.Period
import org.ward.adhkar.data.WardStore
import org.ward.adhkar.data.WardNavigation
import org.ward.adhkar.ui.WardOrnament
import org.ward.adhkar.ui.ThumbRecitationCard
import kotlinx.coroutines.delay
import org.ward.adhkar.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.LocalTime

private val Night = Color(0xFF08130F)
private val Panel = Color(0xFF11241C)
private val Raised = Color(0xFF284336)
private val Gold = Color(0xFFDAB67A)
private val Cream = Color(0xFFF5EEDA)
private val Muted = Color(0xFFAAC0B2)
private val Line = Color(0xFF2C4A3B)
private val Success = Color(0xFF96D4A6)
private val Rounded = RoundedCornerShape(24.dp)

class MainActivity : ComponentActivity() {
    private var openedPeriod by mutableStateOf("morning")
    private var openSerial by mutableIntStateOf(0)

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
                    WardScreen(store, openedPeriod, openSerial)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open_period")?.let {
            openedPeriod = it
            openSerial++
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WardScreen(store: WardStore, openedPeriod: String, openSerial: Int) {
    val context = LocalContext.current
    val voice = remember { WardSpeech(context) }
    val sounds = remember { WardSounds() }
    DisposableEffect(voice, sounds) {
        onDispose { voice.release(); sounds.release() }
    }
    val corpus = remember { DhikrRepository.load(context) }
    var periodId by rememberSaveable { mutableStateOf(openedPeriod) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var screen by rememberSaveable { mutableStateOf("reader") }
    var showCatalog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var version by remember { mutableIntStateOf(0) }
    var pendingAdvanceId by remember { mutableStateOf<String?>(null) }

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
    LaunchedEffect(openedPeriod, openSerial) {
        // Tapping a reminder opens the first incomplete dhikr, including onNewIntent.
        val requested = Period.fromId(openedPeriod)
        periodId = requested.id
        selectedIndex = WardNavigation.firstUnfinished(
            corpus.filter { it.belongsTo(requested) }
        ) { store.count(requested, it.id, LocalDate.now()) >= it.target } ?: 0
        screen = "reader"
        showCatalog = false
    }

    val period = Period.fromId(periodId)
    val entries = remember(period, corpus) { corpus.filter { it.belongsTo(period) } }
    val index = selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val item = entries.getOrNull(index)
    LaunchedEffect(periodId, item?.id, screen, showCatalog) {
        if (screen != "reader" || showCatalog || voice.speaking) voice.stop()
    }
    @Suppress("UNUSED_VARIABLE") val snapshot = version
    val completed = entries.count { store.count(period, it.id, today) >= it.target }
    val progress = completed.toFloat() / entries.size.coerceAtLeast(1)

    LaunchedEffect(pendingAdvanceId, periodId, today) {
        val completedId = pendingAdvanceId ?: return@LaunchedEffect
        delay(520)
        if (entries.getOrNull(selectedIndex)?.id == completedId && store.autoAdvance) {
            val next = WardNavigation.nextUnfinished(entries, selectedIndex) {
                store.count(period, it.id, today) >= it.target
            }
            if (next != null) selectedIndex = next
        }
        pendingAdvanceId = null
    }

    BackHandler(screen != "reader" || showCatalog) {
        if (showCatalog) showCatalog = false else screen = "reader"
    }

    val wallpaper = remember { WardOrnament.load(context) }

    Box(Modifier.fillMaxSize().background(Night)) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.82f
            )
        }
        // A permanent scrim makes the verse readable over the supplied ornament.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Night.copy(alpha = .56f),
                        Night.copy(alpha = .78f),
                        Night.copy(alpha = .84f),
                        Night.copy(alpha = .73f)
                    )
                )
            )
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            when (screen) {
                "settings" -> SettingsScreen(store) { screen = "reader"; version++ }
                "stats" -> StatisticsScreen(store, corpus, today) { screen = "reader" }
                else -> {
                    Row(
                        Modifier.fillMaxWidth().height(54.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Raised, Panel)))
                                .border(1.5.dp, Gold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("۞", color = Gold, fontSize = 27.sp) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("وِرد", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("أذكارك اليومية", color = Cream, fontSize = 11.sp)
                        }
                        CircularIcon(Icons.Default.Insights, "الإحصاءات") { screen = "stats" }
                        Spacer(Modifier.width(8.dp))
                        CircularIcon(Icons.Default.Settings, "الإعدادات") { screen = "settings" }
                    }

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                            .background(Panel.copy(alpha = .90f))
                            .border(1.dp, Gold.copy(alpha = .40f), RoundedCornerShape(25.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Period.entries.forEach { candidate ->
                            val active = period == candidate
                            Box(
                                Modifier.weight(1f).height(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) Brush.horizontalGradient(
                                            listOf(Color(0xFFF0D29B), Gold)
                                        ) else Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.Transparent)
                                        )
                                    )
                                    .clickable {
                                        periodId = candidate.id
                                        selectedIndex = WardNavigation.firstUnfinished(
                                            corpus.filter { it.belongsTo(candidate) }
                                        ) { store.count(candidate, it.id, today) >= it.target } ?: 0
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (if (candidate == Period.MORNING) "☀ " else "☾ ") +
                                        "أذكار ${candidate.label}",
                                    color = if (active) Night else Cream,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))

                    if (item != null) {
                        ThumbRecitationCard(
                            dhikr = item, period = period,
                            ordinal = index + 1, total = entries.size,
                            count = store.count(period, item.id, today),
                            favorite = store.isFavorite(item.id),
                            fontScale = store.fontScale,
                            hapticsEnabled = store.hapticsEnabled,
                            canNext = index < entries.lastIndex,
                            canPrevious = index > 0,
                            onFavorite = { store.toggleFavorite(item.id); version++ },
                            onPrevious = {
                                pendingAdvanceId = null
                                selectedIndex = (index - 1).coerceAtLeast(0)
                            },
                            onNext = {
                                pendingAdvanceId = null
                                selectedIndex = (index + 1).coerceAtMost(entries.lastIndex)
                            },
                            onIncrement = {
                                val previous = store.count(period, item.id, today)
                                if (previous < item.target) {
                                    val newCount = previous + 1
                                    store.setCount(period, item.id, newCount, today)
                                    if (store.countSoundEnabled) sounds.play(newCount == item.target)
                                    version++
                                    if (newCount == item.target) {
                                        pendingAdvanceId = item.id
                                        val next = WardNavigation.nextUnfinished(entries, index) {
                                            store.count(period, it.id, today) >= it.target
                                        }
                                        if (next == null) {
                                            ReminderScheduler.cancelPending(context, period)
                                            ReminderScheduler.dismiss(context, period)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.height(8.dp))

                        // Everything secondary is placed beneath the reading and counting area.
                        Spacer(Modifier.height(2.dp))
                        Row(
                            Modifier.fillMaxWidth().height(72.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top
                        ) {
                            QuickCircle(Icons.Default.Settings, "الضبط") { screen = "settings" }
                            QuickCircle(Icons.Default.FormatListBulleted, "الأذكار") {
                                showCatalog = true
                            }
                            QuickCircle(
                                Icons.Default.Undo, "تراجع",
                                enabled = store.count(period, item.id, today) > 0
                            ) {
                                val old = store.count(period, item.id, today)
                                if (old > 0) {
                                    store.setCount(period, item.id, old - 1, today)
                                    version++
                                }
                            }
                            QuickCircle(
                                if (voice.speaking) Icons.Default.StopCircle
                                else Icons.Default.VolumeUp,
                                if (voice.speaking) "إيقاف" else "استماع",
                                enabled = voice.ready
                            ) {
                                if (voice.speaking) voice.stop()
                                else voice.speak(item.content(period), store.speechRate)
                            }
                            QuickCircle(Icons.Default.NotificationsActive, "التذكير") {
                                screen = "settings"
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        Text("لا توجد أذكار في هذه الفترة", color = Cream)
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(5.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Panel.copy(alpha = .92f))
                            .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "إنجاز وِرد ${period.label}",
                                color = Cream, fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$completed / ${entries.size}",
                                color = Gold, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = Gold, trackColor = Line
                        )
                    }
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
private fun CircularIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Raised, Panel)))
            .border(1.dp, Gold.copy(alpha = .70f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Gold, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun OrnateNavButton(
    icon: ImageVector, title: String, enabled: Boolean, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp)
                .shadow(8.dp, CircleShape, ambientColor = Gold.copy(alpha = .25f))
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (enabled) listOf(Color(0xFF3C5A43), Color(0xFF10251E))
                        else listOf(Panel, Night)
                    )
                )
                .border(2.dp, Gold.copy(alpha = if (enabled) .92f else .26f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = title },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    icon, null,
                    tint = if (enabled) Gold else Muted.copy(alpha = .45f),
                    modifier = Modifier.size(33.dp)
                )
                Text(
                    title, color = if (enabled) Cream else Muted.copy(alpha = .45f),
                    fontWeight = FontWeight.Bold, fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun QuickCircle(
    icon: ImageVector, title: String, enabled: Boolean = true, onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        Box(
            Modifier.size(48.dp)
                .shadow(5.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        if (enabled) listOf(Raised, Panel)
                        else listOf(Panel, Night)
                    )
                )
                .border(1.5.dp, Gold.copy(alpha = if (enabled) .8f else .28f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = title },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null, modifier = Modifier.size(23.dp),
                tint = if (enabled) Gold else Muted.copy(alpha = .45f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            title, color = if (enabled) Cream else Muted.copy(alpha = .45f),
            fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RecitationCard(
    dhikr: Dhikr, period: Period, ordinal: Int, total: Int,
    favorite: Boolean, fontScale: Float, onFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(dhikr.id, period) { scroll.scrollTo(0) }

    Column(
        modifier.fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Gold.copy(alpha = .3f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF0102B20), Color(0xF0081A13))
                )
            )
            .border(1.5.dp, Gold.copy(alpha = .85f), RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    dhikr.title, color = Gold, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1
                )
                Text("الذكر $ordinal من $total", color = Muted, fontSize = 11.sp)
            }
            CircularIcon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة", onFavorite
            )
            Spacer(Modifier.width(6.dp))
            CircularIcon(Icons.Default.ContentCopy, "نسخ الذكر") {
                clipboard.setText(AnnotatedString(dhikr.content(period) + "\nالمصدر: " + dhikr.source))
            }
            Spacer(Modifier.width(6.dp))
            CircularIcon(Icons.Default.Share, "مشاركة الذكر") {
                val message = dhikr.content(period) + "\n\nالمصدر: " + dhikr.source +
                    "\nمن تطبيق وِرد"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(send, "مشاركة الذكر"))
            }
        }
        Spacer(Modifier.height(5.dp))
        HorizontalDivider(color = Gold.copy(alpha = .28f))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(11.dp))
            SelectionContainer {
                Text(
                    dhikr.content(period),
                    color = Cream,
                    fontFamily = FontFamily.Serif,
                    fontSize = (22 * fontScale).sp,
                    lineHeight = (38 * fontScale).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(11.dp))
            if (dhikr.note.isNotEmpty()) {
                Text(
                    dhikr.note, color = Muted, fontSize = 12.sp,
                    textAlign = TextAlign.Center, lineHeight = 19.sp
                )
            }
        }
        HorizontalDivider(color = Gold.copy(alpha = .28f))
        Spacer(Modifier.height(6.dp))
        Text(
            "المصدر: ${dhikr.source}",
            color = Gold.copy(alpha = .92f), fontSize = 12.sp,
            lineHeight = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(), maxLines = 2
        )
    }
}

@Composable
private fun Counter(
    count: Int, target: Int, hapticsEnabled: Boolean,
    onIncrement: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val done = count >= target
    val remaining = (target - count).coerceAtLeast(0)
    val ratio by animateFloatAsState(
        (count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f),
        label = "counter_progress"
    )
    Box(
        Modifier.size(142.dp)
            .shadow(12.dp, CircleShape, ambientColor = Gold.copy(alpha = .30f))
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF345543), Color(0xFF11271E), Color(0xFF071611))
                )
            )
            .border(5.dp, Gold, CircleShape)
            .clickable(enabled = !done) {
                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onIncrement()
            }
            .semantics {
                contentDescription = if (done) "اكتمل الذكر"
                    else "العدّاد: $count من $target؛ اضغط مرة لكل تكرار"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 7.dp.toPx()
            val diameter = size.minDimension - 2 * inset
            drawArc(
                color = Gold.copy(alpha = .18f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Success,
                startAngle = -90f, sweepAngle = ratio * 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (done) {
                Icon(
                    Icons.Default.Check, null, tint = Gold,
                    modifier = Modifier.size(32.dp)
                )
                Text("اكتمل الذكر", color = Cream, fontSize = 13.sp)
            } else {
                Text(
                    "$remaining",
                    color = Cream, fontSize = 47.sp,
                    lineHeight = 48.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "اضغط للعدّ",
                    color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text("$count من $target", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: WardStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var morningEnabled by remember { mutableStateOf(store.isReminderEnabled(Period.MORNING)) }
    var eveningEnabled by remember { mutableStateOf(store.isReminderEnabled(Period.EVENING)) }
    var morningTime by remember { mutableStateOf(store.reminderTime(Period.MORNING)) }
    var eveningTime by remember { mutableStateOf(store.reminderTime(Period.EVENING)) }
    var followUp by remember { mutableStateOf(store.followUpEnabled) }
    var reminderTestMessage by remember { mutableStateOf<String?>(null) }
    var fontScale by remember { mutableFloatStateOf(store.fontScale) }
    var haptics by remember { mutableStateOf(store.hapticsEnabled) }
    var advance by remember { mutableStateOf(store.autoAdvance) }
    var soundEnabled by remember { mutableStateOf(store.countSoundEnabled) }
    var speechRate by remember { mutableFloatStateOf(store.speechRate) }
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
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(Rounded).background(Panel).padding(14.dp)) {
                PrefSwitch("تذكير إضافي بعد ساعة إذا لم يكتمل الورد", followUp) {
                    followUp = it
                    store.followUpEnabled = it
                    if (!it) ReminderScheduler.cancelFollowUps(context)
                }
                OutlinedButton(
                    onClick = {
                        if (!ReminderScheduler.hasPermission(context)) {
                            denied = true
                            reminderTestMessage = null
                        } else {
                            val chosen = if (morningEnabled) Period.MORNING else Period.EVENING
                            val shown = ReminderScheduler.show(context, chosen)
                            reminderTestMessage = if (shown) "أُرسل إشعار تجريبي إلى هاتفك."
                                else "وِرد هذه الفترة مكتمل اليوم؛ لا حاجة إلى تنبيه."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Icon(Icons.Default.NotificationsActive, null)
                    Spacer(Modifier.width(8.dp))
                    Text("جرّب إشعار التذكير")
                }
                reminderTestMessage?.let { message ->
                    Text(message, color = Success, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
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
            Text("الصوت والملاءمة", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(Rounded).background(Panel).padding(17.dp)) {
                PrefSwitch("نغمة هادئة عند العدّ", soundEnabled) {
                    soundEnabled = it; store.countSoundEnabled = it
                }
                Spacer(Modifier.height(8.dp))
                Text("سرعة القراءة الصوتية", color = Cream, fontSize = 16.sp)
                Slider(value = speechRate, onValueChange = {
                    speechRate = it; store.speechRate = it
                }, valueRange = .65f..1.20f)
                Text("سرعة النطق: ${(speechRate * 100).toInt()}٪", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, "ward_daily_reminders")
                    }
                    context.startActivity(intent)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Tune, null)
                    Spacer(Modifier.width(7.dp))
                    Text("اختيار نغمة التذكيرات من أندرويد")
                }
                Spacer(Modifier.height(10.dp))
                Text("قراءة الأذكار تعتمد على صوت عربي محلي مثبت في الهاتف. قد يخطئ النطق الآلي في التشكيل، وليس بديلًا عن قراءة شرعية معتمدة.",
                    color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("الخصوصية", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("لا يحتاج التطبيق إلى الإنترنت، ولا يجمع بيانات الاستخدام. تُحفظ الأذكار والإعدادات على جهازك.",
                color = Muted, fontSize = 13.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(18.dp))
            Text("وِرد 1.4.0 • مجموعة مختارة تحتاج تدقيقًا علميًا نهائيًا قبل النشر العام.",
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
