package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.localDate
import com.baastiklabs.firewatch.core.pieces
import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.exp
import kotlin.math.ln

data class ReceptorPoint(val date: LocalDate, val load: Double)

data class ReceptorOutlook(
    /** Where the model thinks D has been, one point per full day. */
    val history: List<ReceptorPoint>,
    /** If D keeps following the program: one rung down each hold period, then Clear Air. */
    val plan: List<ReceptorPoint>,
    /** If D stayed on the current rung. */
    val stay: List<ReceptorPoint>,
    val todayLoad: Double,
    /** When the plan reaches Clear Air (null = beyond the projection). */
    val clearAirOnPlan: LocalDate?,
    /** When the plan brings the load into the typical non-user range. */
    val typicalOnPlan: LocalDate?,
    val typicalIfStay: LocalDate?,
)

/**
 * Receptor healing, as an estimate. Regular nicotine makes the brain grow extra nicotinic
 * receptors (brain-imaging studies find roughly a quarter to a third more in regular smokers);
 * after nicotine stops they return to typical levels over about 6–12 weeks. While nicotine keeps
 * coming in (gum included) they stay raised, roughly in step with how much.
 *
 * "Load" runs from 0 (typical non-user) to 1 (typical heavy regular use):
 * - Each day's average estimated nicotine in the body sets a level the receptors head toward
 *   (a saturating curve: more nicotine means more load, levelling off).
 * - The load moves toward it slowly: up over about a week, down with an ~18-day time constant
 *   (halving about every 12–13 days), so it's ~10% six weeks after stopping, ~1% by twelve.
 * Research averages plus D's logs; not a medical measurement.
 */
object Receptors {
    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN
    const val UP_DAYS = 7.0
    const val DOWN_DAYS = 18.0
    /** Below this counts as the typical non-user range. */
    const val TYPICAL = 0.10
    /** 24-hour average mg in the body for heavy regular use (about a pack a day): load 1.0. */
    const val HEAVY_MG = 2.4
    private const val K = 1.0
    const val PROJECTION_DAYS = 365

    /** Where the receptors head for a given 24-hour average level (mg in the body). */
    fun targetLoad(avgMg: Double): Double =
        if (avgMg <= 0.0) 0.0 else (avgMg / (avgMg + K) * (HEAVY_MG + K) / HEAVY_MG).coerceAtMost(1.0)

    /** One day's move from [load] toward [target]. */
    fun step(load: Double, target: Double): Double {
        val tau = if (target > load) UP_DAYS else DOWN_DAYS
        return load + (target - load) * (1 - exp(-1.0 / tau))
    }

    /** The 24-hour average estimated level for a steady [piecesPerDay], mg. */
    fun theoreticalMgPerPiece(referenceMg: Double): Double =
        referenceMg / (24 * 60 * ln(2.0) / Kinetics.HALF_LIFE_MIN)

    fun outlook(data: FirewatchData, now: Long, tz: TimeZone): ReceptorOutlook? {
        if (data.doses.isEmpty()) return null
        val today = now.localDate(tz)
        val first = data.doses.first().at.localDate(tz)
        val ref = data.referenceMg
        val doses = data.doses

        // Daily 24-hour averages (wake to next wake), full days only.
        val days = generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it < today }.toList()
        if (days.isEmpty()) return null
        val daily = days.map { d ->
            val w = Waking.day(data, d, tz)
            val nextWake = Waking.day(data, d.plus(1, DateTimeUnit.DAY), tz).wakeAt
            val relevant = doses.filter { it.at in (w.wakeAt - DAY)..nextWake }
            var sum = 0.0
            var n = 0
            var t = w.wakeAt
            while (t < nextWake) { sum += Kinetics.level(relevant, t); n++; t += 30 * MIN }
            val pieces = doses.filter { it.at >= w.wakeAt && it.at < nextWake }.sumOf { data.piecesOf(it) }
            Triple(d, if (n > 0) sum / n else 0.0, pieces)
        }

        // Start as if D arrived at the level their first week implies.
        var load = targetLoad(daily.take(7).map { it.second }.average())
        val history = daily.map { (d, mg, _) -> load = step(load, targetLoad(mg)); ReceptorPoint(d, load) }
        val todayLoad = history.last().load

        // D's own average level per piece a day (falls back to the textbook figure).
        val ratios = daily.takeLast(14).filter { it.third > 0.2 }.map { it.second / it.third }
        val perPiece = if (ratios.size >= 3) ratios.average() else theoreticalMgPerPiece(ref)

        val current = data.targetPieces
            ?: daily.takeLast(7).map { it.third }.average().let { Ladder.measured(it).pieces }
        val hold = data.settings.holdDays.coerceAtLeast(1)
        val lastChange = data.rungChanges.lastOrNull()?.at?.localDate(tz)
        var daysLeftOnRung = (hold - (lastChange?.daysUntil(today) ?: 0)).coerceAtLeast(1)

        val plan = ArrayList<ReceptorPoint>()
        val stay = ArrayList<ReceptorPoint>()
        var planLoad = todayLoad
        var stayLoad = todayLoad
        var rung = current
        var clearAir: LocalDate? = if (current <= 0.0) today else null
        var typicalPlan: LocalDate? = null
        var typicalStay: LocalDate? = null
        for (i in 0..PROJECTION_DAYS) {
            val d = today.plus(i, DateTimeUnit.DAY)
            if (i > 0 && rung > 0.0) {
                daysLeftOnRung--
                if (daysLeftOnRung <= 0) {
                    rung = Ladder.nextDown(rung).pieces
                    daysLeftOnRung = hold
                    if (rung <= 0.0 && clearAir == null) clearAir = d
                }
            }
            planLoad = step(planLoad, targetLoad(rung * perPiece))
            stayLoad = step(stayLoad, targetLoad(current * perPiece))
            plan += ReceptorPoint(d, planLoad)
            stay += ReceptorPoint(d, stayLoad)
            if (typicalPlan == null && planLoad < TYPICAL) typicalPlan = d
            if (typicalStay == null && stayLoad < TYPICAL) typicalStay = d
            // Stop a couple of weeks after the plan reaches the typical range.
            if (typicalPlan != null && typicalPlan.daysUntil(d) >= 14) break
        }
        return ReceptorOutlook(history, plan, stay, todayLoad, clearAir, typicalPlan, typicalStay)
    }
}
