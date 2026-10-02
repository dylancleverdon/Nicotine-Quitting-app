package com.baastiklabs.firewatch.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baastiklabs.firewatch.core.model.ProductKind
import kotlin.math.max
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Colours per delivery method, used consistently across charts. */
fun kindColor(kind: ProductKind): Color = com.baastiklabs.firewatch.ui.theme.kindHex(kind.name)

@Composable
private fun chartColors() = Triple(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.outlineVariant,
    MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Round axis values: 0 up to a "nice" top at or above [max], about [n] steps. */
fun niceTicks(max: Double, n: Int = 3): List<Double> {
    val m = if (max > 0 && max.isFinite()) max else 1.0
    val raw = m / n
    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val out = ArrayList<Double>()
    var v = 0.0
    while (v < m + step * 0.999) { out += Math.round(v * 1e6) / 1e6; v += step }
    return out
}

/** Whole-hour ticks between t0 and t1: position 0..1 and a label that follows the 12/24-hour setting. */
fun timeTicks(t0: Long, t1: Long, max: Int = 5): List<Pair<Float, String>> {
    val h = 3_600_000L
    val step = listOf(1, 2, 3, 4, 6, 12, 24).firstOrNull { (t1 - t0) / (it * h).toDouble() <= max } ?: 24
    val zone = java.time.ZoneId.systemDefault()
    var t = java.time.Instant.ofEpochMilli(t0).atZone(zone).withMinute(0).withSecond(0).withNano(0)
    val out = ArrayList<Pair<Float, String>>()
    while (t.toInstant().toEpochMilli() <= t1) {
        val ms = t.toInstant().toEpochMilli()
        if (ms >= t0 && t.hour % step == 0) out += ((ms - t0).toFloat() / (t1 - t0).coerceAtLeast(1)) to com.baastiklabs.firewatch.ui.Fmt.hourLabel(t.hour)
        t = t.plusHours(1)
    }
    return out
}

/** About [n] evenly spaced labels for index-based charts ([centred] for bars). */
fun indexTicks(labels: List<String>, n: Int = 4, centred: Boolean = false): List<Pair<Float, String>> {
    val len = labels.size
    if (len == 0) return emptyList()
    val k = minOf(n, len)
    return (0 until k).map { i -> Math.round(i * (len - 1).toDouble() / (k - 1).coerceAtLeast(1)).toInt() }.distinct()
        .map { i -> (if (centred) (i + 0.5f) / len else i.toFloat() / (len - 1).coerceAtLeast(1)) to labels[i] }
}

private const val Y_AXIS_DP = 40

/** Axis frame: y values down the left (when [fmt] is given), x labels underneath. */
@Composable
fun ChartFrame(
    ticks: List<Double>?,
    fmt: ((Double) -> String)?,
    height: Dp,
    x: List<Pair<Float, String>>,
    modifier: Modifier = Modifier,
    readout: ((Float) -> String?)? = null,
    content: @Composable () -> Unit,
) {
    val showY = ticks != null && fmt != null && ticks.isNotEmpty()
    // Screen readers get one line: the latest point.
    val summary = "Chart. Latest: " + (readout?.invoke(0.999f) ?: "")
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = false) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth()) {
            if (showY) {
                val top = ticks!!.last()
                val style = MaterialTheme.typography.labelSmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Layout(
                    content = { ticks.forEach { Text(fmt!!(it), style = style, color = color) } },
                    modifier = Modifier.width(Y_AXIS_DP.dp).height(height),
                ) { ms, c ->
                    val ps = ms.map { it.measure(Constraints()) }
                    layout(c.maxWidth, c.maxHeight) {
                        ps.forEachIndexed { i, p ->
                            val yc = (c.maxHeight * (1 - ticks[i] / top)).toInt()
                            p.place(c.maxWidth - p.width, (yc - p.height / 2).coerceIn(0, (c.maxHeight - p.height).coerceAtLeast(0)))
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            if (readout == null) Box(Modifier.weight(1f)) { content() }
            else Box(Modifier.weight(1f)) { Scrubbable(readout, content) }
        }
        if (x.isNotEmpty()) XAxis(x, Modifier.padding(start = if (showY) (Y_AXIS_DP + 6).dp else 0.dp))
    }
}

/** Touch and drag to read the value at that point; let go and it disappears. */
@Composable
private fun Scrubbable(readout: (Float) -> String?, content: @Composable () -> Unit) {
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val lineColor = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.fillMaxWidth().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pos = (down.position.x / size.width).coerceIn(0f, 1f)
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    pos = (c.position.x / size.width).coerceIn(0f, 1f)
                }
                pos = null
            }
        },
    ) {
        content()
        val p = pos
        val label = p?.let(readout)
        if (p != null && label != null) {
            Canvas(Modifier.matchParentSize()) {
                drawLine(lineColor.copy(alpha = 0.6f), Offset(p * size.width, 0f), Offset(p * size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            Layout(
                content = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { ms, c ->
                val t = ms.first().measure(Constraints())
                layout(c.maxWidth, t.height) {
                    val xc = (c.maxWidth * p).toInt()
                    t.place((xc - t.width / 2).coerceIn(0, (c.maxWidth - t.width).coerceAtLeast(0)), 0)
                }
            }
        }
    }
}

@Composable
private fun XAxis(labels: List<Pair<Float, String>>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(content = { labels.forEach { Text(it.second, style = style, color = color) } }, modifier = modifier.fillMaxWidth()) { ms, c ->
        val ps = ms.map { it.measure(Constraints()) }
        val h = ps.maxOfOrNull { it.height } ?: 0
        layout(c.maxWidth, h) {
            ps.forEachIndexed { i, p ->
                val xc = (c.maxWidth * labels[i].first).toInt()
                p.place((xc - p.width / 2).coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

private fun DrawScope.gridLines(ticks: List<Double>?, top: Double, color: Color) {
    ticks?.drop(1)?.forEach { t ->
        val y = size.height - (t / top).toFloat() * size.height
        drawLine(color.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y))
    }
}

@Composable
fun AxisLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * The blood-level wave: a filled curve, sleep shaded, a faint "typical day" line behind,
 * and a marker for now.
 */
@Composable
fun WaveChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    typical: List<Pair<Long, Double>> = emptyList(),
    shaded: List<Pair<Long, Long>> = emptyList(),
    now: Long? = null,
    height: Dp = 160.dp,
    compact: Boolean = false,
    /** Nicotine volatility line on its own (right) scale; empty = off. */
    overlay: List<Pair<Long, Double>> = emptyList(),
) {
    val (primary, grid, muted) = chartColors()
    val overlayColor = MaterialTheme.colorScheme.tertiary
    if (points.size < 2) return
    val oMax = if (overlay.size > 1) niceTicks(overlay.maxOf { it.second }.coerceAtLeast(1.0)).last() else 1.0
    val t0 = points.first().first
    val t1 = points.last().first
    val ticks = if (compact) null else niceTicks(max(points.maxOf { it.second }, typical.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.5))
    val yMax = ticks?.last() ?: (max(points.maxOf { it.second }, typical.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0) * 1.1)
    ChartFrame(ticks, { "${fmtNum(it)} mg" }, height, timeTicks(t0, t1, if (compact) 4 else 5), modifier, readout = { p ->
        val t = t0 + (p * (t1 - t0)).toLong()
        val pt = points.minByOrNull { kotlin.math.abs(it.first - t) }!!
        val vol = overlay.takeIf { it.size > 1 }?.minByOrNull { kotlin.math.abs(it.first - t) }?.let {  " · volatility ≈ ${fmtNum(Math.round(it.second * 10) / 10.0)} mg/h" } ?: ""
        "${com.baastiklabs.firewatch.ui.Fmt.time(pt.first)} · ≈ ${fmtNum(Math.round(pt.second * 10) / 10.0)} mg$vol"
    }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        fun x(t: Long) = ((t - t0).toFloat() / (t1 - t0).coerceAtLeast(1)) * size.width
        fun y(v: Double) = size.height - (v / yMax).toFloat() * size.height
        shaded.forEach { (a, b) ->
            val xa = x(a.coerceIn(t0, t1)); val xb = x(b.coerceIn(t0, t1))
            if (xb > xa) drawRect(grid.copy(alpha = 0.35f), Offset(xa, 0f), Size(xb - xa, size.height))
        }
        gridLines(ticks, yMax, grid)
        if (typical.size > 1) {
            val p = Path()
            typical.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
            drawPath(p, muted.copy(alpha = 0.5f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        val line = Path()
        points.forEachIndexed { i, (t, v) -> if (i == 0) line.moveTo(x(t), y(v)) else line.lineTo(x(t), y(v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(t1), size.height)
            lineTo(x(t0), size.height)
            close()
        }
        drawPath(fill, primary.copy(alpha = 0.25f))
        drawPath(line, primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        if (overlay.size > 1) {
            val o = Path()
            overlay.forEachIndexed { i, (t, v) ->
                val oy = size.height - (v / oMax).toFloat() * size.height
                if (i == 0) o.moveTo(x(t), oy) else o.lineTo(x(t), oy)
            }
            drawPath(o, overlayColor, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        now?.takeIf { it in t0..t1 }?.let {
            drawLine(muted, Offset(x(it), 0f), Offset(x(it), size.height), strokeWidth = 1.5.dp.toPx())
        }
    }
    }
    if (overlay.size > 1) {
        androidx.compose.material3.Text(
            "━ Volatility (mg/h), right scale 0–${fmtNum(oMax)}",
            style = MaterialTheme.typography.labelSmall,
            color = overlayColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Short number for axis labels: 0, 0.5, 2, 2.5, 10. */
fun fmtNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.getDefault(), "%.1f", v).trimEnd('0').trimEnd('.', ',')

data class Bar(val value: Double, val low: Double? = null, val high: Double? = null, val color: Color? = null)

/**
 * Bars (e.g. daily totals). Tier bands can be shaded behind, an average line drawn on top,
 * and uncertain bars get a hatched section from low to high.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    line: List<Double>? = null,
    bands: List<Pair<Double, Color>> = emptyList(),
    height: Dp = 160.dp,
    lineColor: Color? = null,
    yFmt: ((Double) -> String)? = null,
    xLabels: List<String>? = null,
) {
    val (primary, grid, muted) = chartColors()
    if (bars.isEmpty()) return
    val dataMax = bars.maxOf { max(it.value, it.high ?: 0.0) }.coerceAtLeast(line?.maxOrNull() ?: 0.0)
    val ticks = yFmt?.let { niceTicks(dataMax.coerceAtLeast(0.001)) }
    val yMax = ticks?.last() ?: (dataMax.coerceAtLeast(1.0) * 1.1)
    ChartFrame(ticks, yFmt, height, xLabels?.let { indexTicks(it, centred = true) } ?: emptyList(), modifier, readout = { p ->
        val i = (p * bars.size).toInt().coerceIn(0, bars.lastIndex)
        val v = Math.round(bars[i].value * 10) / 10.0
        listOfNotNull(xLabels?.getOrNull(i), yFmt?.invoke(v) ?: fmtNum(v), line?.getOrNull(i)?.let { "line ${fmtNum(Math.round(it * 10) / 10.0)}" }).joinToString(" · ")
    }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        gridLines(ticks, yMax, grid)
        fun y(v: Double) = size.height - (v / yMax).toFloat() * size.height
        var prev = 0.0
        bands.sortedBy { it.first }.forEach { (top, color) ->
            if (prev < yMax) drawRect(color, Offset(0f, y(minOf(top, yMax))), Size(size.width, y(prev) - y(minOf(top, yMax))))
            prev = top
        }
        val slot = size.width / bars.size
        val w = (slot * 0.7f).coerceAtLeast(1f)
        bars.forEachIndexed { i, b ->
            val left = i * slot + (slot - w) / 2
            val c = b.color ?: primary
            drawRect(c, Offset(left, y(b.value)), Size(w, size.height - y(b.value)))
            if (b.low != null && b.high != null && b.high > b.low) {
                hatch(left, y(b.high), w, y(b.low) - y(b.high), c)
            }
        }
        line?.takeIf { it.size > 1 }?.let { vs ->
            val p = Path()
            vs.forEachIndexed { i, v ->
                val px = i * slot + slot / 2
                if (i == 0) p.moveTo(px, y(v)) else p.lineTo(px, y(v))
            }
            drawPath(p, lineColor ?: muted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
    }
    }
}

private fun DrawScope.hatch(left: Float, top: Float, w: Float, h: Float, color: Color) {
    drawRect(color.copy(alpha = 0.15f), Offset(left, top), Size(w, h))
    clipRect(left, top, left + w, top + h) {
        var d = -h
        while (d < w) {
            drawLine(color, Offset(left + d, top + h), Offset(left + d + h, top), strokeWidth = 1.5f)
            d += 8f
        }
    }
}

/** A simple trend line with an optional filled area. */
@Composable
fun TrendLine(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    color: Color? = null,
    second: List<Double>? = null,
    secondColor: Color? = null,
    stepped: Boolean = false,
    invert: Boolean = false,
    yFmt: ((Double) -> String)? = null,
    xLabels: List<String>? = null,
    top: Double? = null,
) {
    val (primary, grid, muted) = chartColors()
    if (values.size < 2 && (second?.size ?: 0) < 2) return
    val all = values + (second ?: emptyList())
    val ticks = if (yFmt != null && !invert) niceTicks(top ?: (all.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0)) else null
    val yTop = ticks?.last() ?: ((all.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0) * 1.1)
    ChartFrame(ticks, yFmt, height, xLabels?.let { indexTicks(it) } ?: emptyList(), modifier, readout = { p ->
        val n = maxOf(values.size, second?.size ?: 0)
        val i = Math.round(p * (n - 1)).coerceIn(0, (n - 1).coerceAtLeast(0))
        fun f(v: Double?) = v?.let { yFmt?.invoke(Math.round(it * 10) / 10.0) ?: fmtNum(Math.round(it * 10) / 10.0) } ?: "–"
        listOfNotNull(xLabels?.getOrNull(i), f(values.getOrNull(i)) + (second?.let { " / " + f(it.getOrNull(i)) } ?: "")).joinToString(" · ")
    }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        gridLines(ticks, yTop, grid)
        fun y(v: Double): Float {
            val f = (v / yTop).toFloat()
            return if (invert) f * size.height else size.height - f * size.height
        }
        fun draw(vs: List<Double>, c: Color) {
            if (vs.size < 2) return
            val step = size.width / (vs.size - 1)
            val p = Path()
            vs.forEachIndexed { i, v ->
                val px = i * step
                if (i == 0) p.moveTo(px, y(v))
                else if (stepped) { p.lineTo(px, y(vs[i - 1])); p.lineTo(px, y(v)) } else p.lineTo(px, y(v))
            }
            drawPath(p, c, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        second?.let { draw(it, secondColor ?: muted) }
        draw(values, color ?: primary)
    }
    }
}

/** Hour of day (columns) against day of week (rows). */
@Composable
fun Heatmap(grid: Array<DoubleArray>, modifier: Modifier = Modifier) {
    val (primary, gridColor, _) = chartColors()
    val maxV = grid.maxOf { row -> row.maxOrNull() ?: 0.0 }.coerceAtLeast(0.001)
    val hours = listOf(0, 6, 12, 18).map { it / 24f to com.baastiklabs.firewatch.ui.Fmt.hourLabel(it) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(150.dp).width(14.dp), verticalArrangement = Arrangement.SpaceAround) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(150.dp)) {
            val cw = size.width / 24
            val ch = size.height / 7
            for (d in 0 until 7) for (h in 0 until 24) {
                val v = grid[d][h] / maxV
                drawRect(
                    if (v <= 0) gridColor.copy(alpha = 0.25f) else primary.copy(alpha = (0.15 + 0.85 * v).toFloat()),
                    Offset(h * cw + 1, d * ch + 1), Size(cw - 2, ch - 2),
                )
            }
        }
        }
        XAxis(hours, Modifier.padding(start = 20.dp))
    }
}

/** Each day a thin strip: dark where nicotine was in the system, light where clear. */
@Composable
fun DayBarcode(days: List<List<Boolean>>, modifier: Modifier = Modifier) {
    val (primary, _, _) = chartColors()
    val clear = MaterialTheme.colorScheme.tertiary
    val hours = listOf(0, 6, 12, 18).map { it / 24f to com.baastiklabs.firewatch.ui.Fmt.hourLabel(it) }
    ChartFrame(null, null, 0.dp, hours, modifier) {
    Canvas(Modifier.fillMaxWidth().height((days.size * 5).coerceIn(20, 400).dp)) {
        if (days.isEmpty()) return@Canvas
        val rowH = size.height / days.size
        days.forEachIndexed { r, cells ->
            val cw = size.width / cells.size
            cells.forEachIndexed { c, on ->
                drawRect(if (on) primary.copy(alpha = 0.9f) else clear.copy(alpha = 0.35f), Offset(c * cw, r * rowH), Size(cw + 0.5f, rowH - 1f))
            }
        }
    }
    }
}

/** Dots along a 24-hour line, sized by amount and coloured by product type. */
@Composable
fun DoseStrip(dots: List<Triple<Float, Double, Color>>, modifier: Modifier = Modifier) {
    val (_, grid, _) = chartColors()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val cy = size.height / 2
            drawLine(grid, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
            dots.forEach { (frac, pieces, color) ->
                drawCircle(color, radius = (4 + 6 * pieces.coerceAtMost(2.0)).dp.toPx() / 2, center = Offset(frac * size.width, cy))
            }
        }
        XAxis(listOf(0, 6, 12, 18).map { it / 24f to com.baastiklabs.firewatch.ui.Fmt.hourLabel(it) })
    }
}

/** Stacked bars of shares (e.g. product mix per week). */
@Composable
fun StackedBars(columns: List<List<Pair<Double, Color>>>, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val maxV = columns.maxOfOrNull { c -> c.sumOf { it.first } }?.coerceAtLeast(0.001) ?: return
    Canvas(modifier.fillMaxWidth().height(height)) {
        val slot = size.width / columns.size
        val w = slot * 0.7f
        columns.forEachIndexed { i, parts ->
            var bottom = size.height
            parts.forEach { (v, c) ->
                val h = (v / maxV).toFloat() * size.height
                drawRect(c, Offset(i * slot + (slot - w) / 2, bottom - h), Size(w, h))
                bottom -= h
            }
        }
    }
}

/**
 * The 24-hour craving forecast: likelihood as a filled curve (0–100%), a dot every hour coloured
 * by likely strength, sleep shaded, and a marker for now.
 */
@Composable
fun CravingForecastChart(
    points: List<com.baastiklabs.firewatch.core.engine.CravingPoint>,
    now: Long,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val (primary, grid, muted) = chartColors()
    if (points.size < 2) return
    val t0 = points.first().at
    val t1 = points.last().at
    val ticks = niceTicks(points.maxOf { it.likelihood }.coerceAtLeast(0.1))
    val yMax = ticks.last()
    ChartFrame(ticks, { "${Math.round(it * 100)}%" }, height, timeTicks(t0, t1), modifier, readout = { p ->
        val t = t0 + (p * (t1 - t0)).toLong()
        val pt = points.minByOrNull { kotlin.math.abs(it.at - t) }!!
        if (pt.asleep) "${com.baastiklabs.firewatch.ui.Fmt.time(pt.at)} · asleep"
        else "${com.baastiklabs.firewatch.ui.Fmt.time(pt.at)} · ${Math.round(pt.likelihood * 100)}% · strength ≈ ${Math.round(pt.strength)}"
    }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        gridLines(ticks, yMax, grid)
        fun x(t: Long) = ((t - t0).toFloat() / (t1 - t0).coerceAtLeast(1)) * size.width
        fun y(v: Double) = size.height - (v / yMax).toFloat() * size.height
        // Sleep shading.
        var start: Long? = null
        points.forEachIndexed { i, p ->
            if (p.asleep && start == null) start = p.at
            if ((!p.asleep || i == points.lastIndex) && start != null) {
                val xa = x(start!!); val xb = x(p.at)
                if (xb > xa) drawRect(grid.copy(alpha = 0.35f), Offset(xa, 0f), Size(xb - xa, size.height))
                start = null
            }
        }
        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.at), y(p.likelihood)) else line.lineTo(x(p.at), y(p.likelihood)) }
        val fill = Path().apply { addPath(line); lineTo(x(t1), size.height); lineTo(x(t0), size.height); close() }
        drawPath(fill, primary.copy(alpha = 0.2f))
        drawPath(line, primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        points.filterIndexed { i, p -> i % 4 == 0 && !p.asleep && p.likelihood > 0.02 }.forEach { p ->
            drawCircle(com.baastiklabs.firewatch.ui.theme.cravingColor(p.strength.toInt()), radius = 3.5.dp.toPx(), center = Offset(x(p.at), y(p.likelihood)))
        }
        if (now in t0..t1) drawLine(muted, Offset(x(now), 0f), Offset(x(now), size.height), strokeWidth = 1.5.dp.toPx())
    }
    }
}

/**
 * Receptor load over time (0 = typical non-user, 1 = typical heavy use): the past as a solid line,
 * the plan dashed, "stay here" faint and dashed, the typical non-user range shaded, today marked.
 */
@Composable
fun ReceptorChart(
    history: List<Double>,
    plan: List<Double>,
    stay: List<Double>,
    typical: Double,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    dates: List<String> = emptyList(),
) {
    val (primary, grid, muted) = chartColors()
    val tertiary = MaterialTheme.colorScheme.tertiary
    val total = history.size + maxOf(plan.size, stay.size)
    if (total < 2) return
    val ticks = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
    ChartFrame(ticks, { "${Math.round(it * 100)}%" }, height, indexTicks(dates), modifier, readout = { p ->
        val i = Math.round(p * (total - 1))
        val today = (history.size - 1).coerceAtLeast(0)
        val v = if (i < history.size) history[i] else plan.getOrNull(i - today) ?: stay.getOrNull(i - today)
        listOfNotNull(dates.getOrNull(i), v?.let { "≈ ${Math.round(it * 100)}%" + if (i >= history.size) " (plan)" else "" }).joinToString(" · ")
    }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        fun x(i: Int) = i.toFloat() / (total - 1).coerceAtLeast(1) * size.width
        fun y(v: Double) = size.height - (v.coerceIn(0.0, 1.0)).toFloat() * size.height
        drawRect(tertiary.copy(alpha = 0.15f), Offset(0f, y(typical)), Size(size.width, size.height - y(typical)))
        gridLines(ticks, 1.0, grid)
        fun draw(vs: List<Double>, offset: Int, color: Color, dashed: Boolean, width: Float) {
            if (vs.size < 2) return
            val p = Path()
            vs.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(offset + i), y(v)) else p.lineTo(x(offset + i), y(v)) }
            drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 9f)) else null))
        }
        val todayIndex = (history.size - 1).coerceAtLeast(0)
        draw(stay, todayIndex, muted.copy(alpha = 0.6f), dashed = true, width = 2.dp.toPx())
        draw(plan, todayIndex, primary, dashed = true, width = 2.5.dp.toPx())
        draw(history, 0, primary, dashed = false, width = 3.dp.toPx())
        drawLine(muted, Offset(x(todayIndex), 0f), Offset(x(todayIndex), size.height), strokeWidth = 1.5.dp.toPx())
    }
    }
}

/** Two series per day around zero (net from timing and from dose size): up = ahead, down = behind. */
@Composable
fun DivergingBars(a: List<Double>, b: List<Double>, labels: List<String>, fmt: (Double) -> String, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    if (a.isEmpty()) return
    val first = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (a.map { kotlin.math.abs(it) } + b.map { kotlin.math.abs(it) }).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    ChartFrame(null, null, height, indexTicks(labels, centred = true), modifier, readout = { p ->
        val i = (p * a.size).toInt().coerceIn(0, a.lastIndex)
        "${labels[i]} · timing ${fmt(a[i])} · dose size ${fmt(b[i])}"
    }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val mid = size.height / 2
            val slot = size.width / a.size
            val w = slot * 0.35f
            fun y(v: Double) = mid - (v / max).toFloat() * (mid - 2f)
            drawLine(grid, Offset(0f, mid), Offset(size.width, mid))
            a.forEachIndexed { i, v -> val top = minOf(mid, y(v)); drawRect(first, Offset(i * slot + slot * 0.1f, top), Size(w, kotlin.math.abs(y(v) - mid))) }
            b.forEachIndexed { i, v -> val top = minOf(mid, y(v)); drawRect(second, Offset(i * slot + slot * 0.5f, top), Size(w, kotlin.math.abs(y(v) - mid))) }
        }
    }
}
