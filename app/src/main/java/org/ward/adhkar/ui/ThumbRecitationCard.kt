package org.ward.adhkar.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ward.adhkar.data.Dhikr
import org.ward.adhkar.data.Period
import org.ward.adhkar.data.WardSwipe

private val Ink = Color(0xFF092018)
private val Panel = Color(0xFF122F24)
private val Raised = Color(0xFF355748)
private val Gold = Color(0xFFDAB67A)
private val Cream = Color(0xFFF5EEDA)
private val Muted = Color(0xFFAFC6B7)
private val Success = Color(0xFF96D4A6)

/**
 * The entire body counts a repetition. Small upper-edge controls navigate and the
 * card's scrollable text area remains readable for long adhkar.
 */
@Composable
fun ThumbRecitationCard(
    dhikr: Dhikr,
    period: Period,
    ordinal: Int,
    total: Int,
    count: Int,
    favorite: Boolean,
    fontScale: Float,
    hapticsEnabled: Boolean,
    canNext: Boolean,
    canPrevious: Boolean,
    onFavorite: () -> Unit,
    onIncrement: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val done = count >= dhikr.target
    val pulse = remember(dhikr.id) { Animatable(1f) }
    var pressToken by remember(dhikr.id) { mutableIntStateOf(0) }

    LaunchedEffect(dhikr.id, period) { scroll.scrollTo(0) }
    LaunchedEffect(pressToken) {
        if (pressToken > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
        }
    }
    val cardShape = RoundedCornerShape(25.dp)

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxSize()
                .padding(top = 20.dp)
                .graphicsLayer {
                    val inward = 1f - kotlin.math.abs(pulse.value * 2f - 1f)
                    scaleX = 1f - inward * .012f
                    scaleY = 1f - inward * .012f
                }
                .shadow(11.dp, cardShape, ambientColor = Gold.copy(alpha = .25f))
                .clip(cardShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF1375748), Color(0xF10B2B20), Color(0xF108211A))
                    )
                )
                .border(1.6.dp, Gold.copy(alpha = .87f), cardShape)
                .clickable(enabled = !done) {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    pressToken++
                    onIncrement()
                }
                .semantics {
                    contentDescription = if (done) "اكتمل الذكر، اسحب للتنقل"
                        else "اضغط البطاقة للعد، " + count + " من " + dhikr.target
                }
        ) {
            Box(
                Modifier.fillMaxSize().padding(8.dp)
                    .border(0.7.dp, Gold.copy(alpha = .20f), RoundedCornerShape(19.dp))
            )
            Column(
                Modifier.fillMaxSize()
                    .padding(start = 15.dp, end = 15.dp, top = 87.dp, bottom = 14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            dhikr.title, color = Gold, fontSize = 19.sp,
                            fontWeight = FontWeight.Bold, maxLines = 2,
                            lineHeight = 26.sp
                        )
                        Text("الذكر $ordinal من $total", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(5.dp))
                    CardAction(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        onFavorite
                    )
                    Spacer(Modifier.width(4.dp))
                    CardAction(Icons.Filled.ContentCopy, "نسخ الذكر") {
                        clipboard.setText(
                            AnnotatedString(dhikr.content(period) + "\nالمصدر: " + dhikr.source)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    CardAction(Icons.Filled.Share, "مشاركة الذكر") {
                        val message = dhikr.content(period) + "\n\nالمصدر: " +
                            dhikr.source + "\nمن تطبيق وِرد"
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message)
                                },
                                "مشاركة الذكر"
                            )
                        )
                    }
                }
                Spacer(Modifier.height(11.dp))
                HorizontalDivider(color = Gold.copy(alpha = .27f))
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        dhikr.content(period), color = Cream,
                        fontFamily = FontFamily.Serif,
                        fontSize = (23 * fontScale).sp,
                        lineHeight = (39 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    if (dhikr.note.isNotEmpty()) {
                        Text(
                            dhikr.note, color = Muted, fontSize = 12.sp,
                            lineHeight = 19.sp, textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "المصدر: " + dhikr.source, color = Gold,
                    fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    lineHeight = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Brush.horizontalGradient(listOf(Raised, Panel, Raised)))
                        .border(1.dp, Gold.copy(alpha = .74f), RoundedCornerShape(25.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (pressToken > 0 && pulse.value < 1f) {
                            val amount = 1f - pulse.value
                            drawCircle(
                                color = Gold.copy(alpha = amount * .68f),
                                radius = 15.dp.toPx() + pulse.value * 110.dp.toPx(),
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Gold.copy(alpha = amount * .16f),
                                radius = 8.dp.toPx() + pulse.value * 65.dp.toPx(),
                                center = center
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (done) Icons.Filled.CheckCircle else Icons.Filled.TouchApp,
                            null, tint = if (done) Success else Gold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (done) "اكتمل الذكر" else "اضغط البطاقة لإتمام الذكر",
                            color = Cream, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                }
            }
        }

        // Floating left counter: current repetitions / target repetitions.
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(start = 20.dp, top = 51.dp)
                .shadow(5.dp, RoundedCornerShape(19.dp))
                .clip(RoundedCornerShape(19.dp))
                .background(Panel)
                .border(1.5.dp, Gold.copy(alpha = .92f), RoundedCornerShape(19.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$count / " + dhikr.target,
                color = if (done) Success else Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
        }

        // Top edge: swipe up for next, swipe down for previous. Both sides also tap.
        val gestureShape = RoundedCornerShape(28.dp)
        Row(
            Modifier.align(Alignment.TopCenter)
                .width(192.dp).height(74.dp)
                .shadow(7.dp, gestureShape)
                .clip(gestureShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF536653), Color(0xFF213D31))
                    )
                )
                .border(1.dp, Gold.copy(alpha = .72f), gestureShape)
                .pointerInput(dhikr.id, canNext, canPrevious) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                        onDragEnd = {
                            when (WardSwipe.action(totalDrag, 42.dp.toPx())) {
                                WardSwipe.NEXT -> if (canNext) onNext()
                                WardSwipe.PREVIOUS -> if (canPrevious) onPrevious()
                            }
                            totalDrag = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            totalDrag += amount
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(enabled = canNext, onClick = onNext),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.KeyboardDoubleArrowUp, "التالي", tint = if (canNext) Gold else Muted)
                Text("التالي", color = if (canNext) Cream else Muted, fontSize = 12.sp)
            }
            Box(
                Modifier.width(1.dp).height(32.dp)
                    .background(Gold.copy(alpha = .65f))
            )
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(enabled = canPrevious, onClick = onPrevious),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.KeyboardDoubleArrowDown, "السابق",
                    tint = if (canPrevious) Gold else Muted
                )
                Text("السابق", color = if (canPrevious) Cream else Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(35.dp)
            .clip(CircleShape)
            .background(Panel.copy(alpha = .90f))
            .border(1.dp, Gold.copy(alpha = .65f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Gold, modifier = Modifier.size(19.dp))
    }
}
