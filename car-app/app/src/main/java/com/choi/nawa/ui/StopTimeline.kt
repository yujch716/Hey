package com.choi.nawa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.choi.nawa.data.StopKind
import com.choi.nawa.trip.ReplyLog
import com.choi.nawa.trip.StopProgress
import com.choi.nawa.trip.StopStatus
import com.choi.nawa.trip.TripState
import com.choi.nawa.ui.theme.Palette

/** Waiting time the server adds at each stop before a later one (same ETA rule as the server) */
private const val STOP_DWELL_S = 60

/**
 * Seconds until each stop still ahead: the navi's ETA for the current stop, then each later stop's
 * planned leg plus a minute at every stop before it. Empty until the navi has given an ETA.
 */
fun stopEtas(state: TripState): Map<Int, Int> {
    val current = state.current ?: return emptyMap()
    var t = when (current.status) {
        StopStatus.ARRIVED -> 0
        StopStatus.CURRENT -> state.legEtaSeconds ?: return emptyMap()
        else -> return emptyMap()
    }
    val etas = mutableMapOf(current.order to t)
    for (stop in state.stops.drop(state.currentIndex + 1)) {
        t += STOP_DWELL_S + stop.legSeconds
        etas[stop.order] = t
    }
    return etas
}

fun etaText(seconds: Int) = "약 ${maxOf(1, (seconds + 59) / 60)}분"

/** A passenger reply as a short bubble next to their stop */
fun replyShort(type: String) = when (type) {
    "out" -> "나왔대요"
    "late" -> "5분 늦는대요"
    "waitHere" -> "여기서 기다린대요"
    else -> "메시지"
}

/** The trip panel's stops as a vertical progress line: 출발 → 1 → 2 → 3, scrolled to the current stop. */
@Composable
fun StopTimeline(state: TripState, modifier: Modifier = Modifier) =
    StopTimeline(
        stops = state.stops,
        currentIndex = state.currentIndex,
        etas = stopEtas(state),
        latestReplies = state.replies.groupBy { it.stopOrder }.mapValues { (_, r) -> r.maxBy { it.atMs } },
        modifier = modifier,
    )

/**
 * Completed: dark node with a check, dimmed text, dark line behind it.
 * Current (heading or arrived): large yellow node, bold label, ETA. Upcoming: grey ring, small ETA.
 */
@Composable
fun StopTimeline(
    stops: List<StopProgress>,
    currentIndex: Int,
    etas: Map<Int, Int>,
    latestReplies: Map<Int, ReplyLog>,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    // Item 0 is 출발, so the current stop is item currentIndex + 1; keep the one before it in view too
    LaunchedEffect(currentIndex) { list.animateScrollToItem(currentIndex.coerceAtLeast(0)) }

    LazyColumn(modifier, state = list) {
        item(key = "start") {
            TimelineRow(above = null, below = stops.firstOrNull()?.let(::segmentInto), node = { NodeCircle(NodeStyle.START, 0) }) {
                Text("출발", Modifier.height(LargeNode).wrapContentHeight(), style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
            }
        }
        itemsIndexed(stops, key = { _, s -> s.order }) { i, stop ->
            val style = styleOf(stop)
            TimelineRow(
                above = segmentInto(stop),
                below = stops.getOrNull(i + 1)?.let(::segmentInto),
                node = { NodeCircle(style, stop.order) },
            ) {
                StopEntry(stop, style, etas[stop.order], latestReplies[stop.order])
            }
        }
    }
}

/** The line arriving at [stop]: driven (dark), being driven now (blue), still ahead (grey) */
private fun segmentInto(stop: StopProgress) = when (stop.status) {
    StopStatus.COMPLETED, StopStatus.ARRIVED -> Palette.Ink
    StopStatus.CURRENT -> Palette.Route
    StopStatus.UPCOMING -> Palette.Line
}

private fun styleOf(stop: StopProgress) = when (stop.status) {
    StopStatus.COMPLETED -> NodeStyle.DONE
    StopStatus.CURRENT, StopStatus.ARRIVED -> NodeStyle.CURRENT
    StopStatus.UPCOMING -> NodeStyle.UPCOMING
}

private val RailWidth = 84.dp
private val LargeNode = 64.dp

/** One row: the rail on the left (line in, node, line out) and the entry beside the node. */
@Composable
private fun TimelineRow(above: Color?, below: Color?, node: @Composable () -> Unit, entry: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(RailWidth).fillMaxHeight()) {
            Canvas(Modifier.fillMaxHeight().width(RailWidth)) {
                val x = size.width / 2
                val mid = (LargeNode / 2).toPx()
                val w = 6.dp.toPx()
                if (above != null) drawLine(above, Offset(x, 0f), Offset(x, mid), w)
                if (below != null) drawLine(below, Offset(x, mid), Offset(x, size.height), w)
            }
            Box(Modifier.width(RailWidth).height(LargeNode), contentAlignment = Alignment.Center) { node() }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 20.dp), content = entry)
    }
}

@Composable
private fun StopEntry(stop: StopProgress, style: NodeStyle, eta: Int?, reply: ReplyLog?) {
    val current = style == NodeStyle.CURRENT
    val textColor = if (style == NodeStyle.DONE) Palette.Muted else Palette.Ink
    Row(Modifier.height(LargeNode), verticalAlignment = Alignment.CenterVertically) {
        KindIcon(stop.kind, textColor, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            if (stop.kind == StopKind.PICKUP) "픽업" else stop.label,
            Modifier.weight(1f),
            style = if (current) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
        when {
            style == NodeStyle.DONE -> Text("완료", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            stop.status == StopStatus.ARRIVED -> Text("도착", style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            current && eta != null -> Text(etaText(eta), style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
            current -> Text("가는 중", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            eta != null -> Text(etaText(eta), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        }
    }
    if (stop.passengerLocation != null && style != NodeStyle.DONE) {
        Text("위치 공유 중", style = MaterialTheme.typography.labelLarge, color = Palette.Route)
    }
    if (reply != null && style != NodeStyle.DONE) ReplyBubble(reply)
}

@Composable
private fun ReplyBubble(reply: ReplyLog) =
    Row(
        Modifier.padding(top = 6.dp).background(Palette.SignalSoft, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(replyShort(reply.type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Text(clock(reply.atMs), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    }

private enum class NodeStyle { START, DONE, CURRENT, UPCOMING }

@Composable
private fun NodeCircle(style: NodeStyle, number: Int) {
    when (style) {
        NodeStyle.START -> Box(Modifier.size(24.dp).background(Palette.Ink, CircleShape))
        NodeStyle.DONE -> Box(Modifier.size(46.dp).background(Palette.Ink, CircleShape), contentAlignment = Alignment.Center) {
            CheckMark(Palette.Card, Modifier.size(26.dp))
        }
        NodeStyle.CURRENT -> Box(
            Modifier.size(LargeNode).background(Palette.Signal, CircleShape).border(5.dp, Palette.SignalSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
        }
        NodeStyle.UPCOMING -> NumberCircle(number, 46.dp, Palette.Card, Palette.Line, Palette.Muted)
    }
}

/** A stop's number in a circle, also used by 운행 준비's stop list */
@Composable
fun StopNumber(number: Int, modifier: Modifier = Modifier) = NumberCircle(number, 52.dp, Palette.Ink, Palette.Ink, Palette.Card, modifier)

@Composable
private fun NumberCircle(number: Int, size: Dp, fill: Color, ring: Color, text: Color, modifier: Modifier = Modifier) =
    Box(modifier.size(size).background(fill, CircleShape).border(3.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = text)
    }

@Composable
private fun CheckMark(color: Color, modifier: Modifier) = Canvas(modifier) {
    val path = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.52f)
        lineTo(size.width * 0.42f, size.height * 0.74f)
        lineTo(size.width * 0.84f, size.height * 0.28f)
    }
    drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Pickup: a person. Waypoint: a flag. */
@Composable
fun KindIcon(kind: StopKind, color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    if (kind == StopKind.PICKUP) {
        drawCircle(color, radius = w * 0.2f, center = Offset(w / 2, h * 0.28f))
        drawArc(color, 180f, 180f, useCenter = true, topLeft = Offset(w * 0.14f, h * 0.56f), size = Size(w * 0.72f, h * 0.8f))
    } else {
        drawLine(color, Offset(w * 0.24f, h * 0.08f), Offset(w * 0.24f, h * 0.94f), 2.5.dp.toPx(), StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(w * 0.24f, h * 0.1f)
                lineTo(w * 0.86f, h * 0.3f)
                lineTo(w * 0.24f, h * 0.52f)
                close()
            },
            color,
        )
    }
}

fun clock(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(ms))
