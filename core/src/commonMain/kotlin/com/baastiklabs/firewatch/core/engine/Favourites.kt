package com.baastiklabs.firewatch.core.engine

import com.baastiklabs.firewatch.core.records.FirewatchData
import kotlinx.datetime.TimeZone

/**
 * Insights → Favourites. D stars charts; starred charts collect in a Favourites section that opens
 * first, topped by one plain fact per favourite (facts only, never a comparison with the past).
 * Chart ids are stored in `Settings.favouriteCharts`; unknown ids are ignored.
 */
object Favourites {
    data class Chart(val id: String, val title: String, val section: String)
    data class Fact(val id: String, val title: String, val text: String)

    const val SECTION = "Favourites"

    /** Every chart that can be starred, in Insights order (both apps use these ids and titles). */
    val charts: List<Chart> = listOf(
        Chart("wave", "Blood-level wave", "Today"),
        Chart("volatility", "Nicotine volatility", "Today"),
        Chart("strip", "Dose strip", "Today"),
        Chart("review", "Yesterday in review", "Today"),
        Chart("cravingsahead", "Cravings ahead", "Cravings ahead"),
        Chart("receptors", "Receptors", "Receptors"),
        Chart("background", "Background level", "Receptors"),
        Chart("stretch", "Stretch & pull", "Stretch & pull"),
        Chart("netsplit", "Where your net comes from", "Stretch & pull"),
        Chart("known", "Days known", "Trends"),
        Chart("totals", "Daily totals", "Trends"),
        Chart("staircase", "Tier staircase", "Trends"),
        Chart("gaps", "Gap between pieces", "Trends"),
        Chart("heatmap", "Heatmap", "Patterns"),
        Chart("checking", "Checking", "Patterns"),
        Chart("wakefirst", "Wake to first piece", "Patterns"),
        Chart("longestgap", "Longest gap each day", "Gaps & spacing"),
        Chart("steady", "Holding steady", "Gaps & spacing"),
        Chart("clearhours", "Clear hours", "Gaps & spacing"),
        Chart("overnight", "Overnight gap", "Gaps & spacing"),
        Chart("peak", "Daily peak", "Amounts & peaks"),
        Chart("quality", "Nicotine quality", "Amounts & peaks"),
        Chart("byhour", "Doses by product over the day", "Mix"),
        Chart("mix", "Product mix", "Mix"),
        Chart("nextstep", "Next step down", "Forecasts"),
        Chart("eachstep", "If you take each step", "Forecasts"),
        Chart("paceplan", "Pace vs your plan", "Forecasts"),
        Chart("nicfree", "Days nicotine-free", "Forecasts"),
        Chart("journey", "Journey to Clear Air", "Forecasts"),
        Chart("taper", "Taper speed", "Forecasts"),
        Chart("arrivals", "Arrival dates", "Forecasts"),
        Chart("steadymonth", "Steady days by month", "Milestones"),
        Chart("insights", "Insights", "Milestones"),
        Chart("badges", "Badges", "Milestones"),
        Chart("recap", "Monthly recap", "Milestones"),
        Chart("countdown", "Clear Air countdown", "Milestones"),
        Chart("practice", "Practice pace runs", "Ladder"),
        Chart("history", "Level history", "Ladder"),
        Chart("ladder", "The ladder", "Ladder"),
    )

    fun chart(id: String): Chart? = charts.firstOrNull { it.id == id }

    /** D's favourites in Insights order (unknown ids dropped). */
    fun ids(data: FirewatchData): List<String> =
        charts.map { it.id }.filter { it in data.settings.favouriteCharts }

    /** Settings list with [id] starred or unstarred. */
    fun toggle(current: List<String>, id: String): List<String> =
        if (id in current) current - id else current + id

    /** One fact per favourite that has one, for the card at the top of Favourites. */
    fun facts(data: FirewatchData, now: Long, tz: TimeZone): List<Fact> {
        val ids = ids(data)
        if (ids.isEmpty()) return emptyList()
        val ins = Insights(data, tz, now)
        val y = ins.fullDays.lastOrNull()
        return ids.mapNotNull { id ->
            val text: String? = when (id) {
                "volatility" -> y?.let { "Yesterday: ${one(it.volatility)} mg per hour" }
                "strip" -> y?.let { "Doses yesterday: ${it.doses.size}" }
                "review" -> y?.let { "Pieces yesterday: ${one(it.pieces)}" }
                "stretch" -> ins.stretchPull.lastOrNull()?.takeIf { !it.paused }?.let { "Net yesterday: ${signed(it.netMin)}" }
                "known" -> "${Progress.knownDays(data, ins.today, tz)} of the last 7 days known"
                "totals" -> Progress.rollingAverage(data, ins.today, tz)?.let { "7-day average: ${one(it)} pieces a day" }
                "staircase" -> Progress.measuredRung(data, ins.today, tz)?.let { "Measured level: ${it.label}" }
                "gaps" -> ins.weeklyGaps().lastOrNull()?.let { "Average gap this week: ${Coaching.hm(it.second)}" }
                "wakefirst" -> y?.wakeToFirstMin?.let { "Wake to first piece yesterday: ${Coaching.hm(it)}" }
                "longestgap" -> y?.longestGapMin?.let { "Longest gap yesterday: ${Coaching.hm(it)}" }
                "steady" -> ins.steadyByMonth().sumOf { it.second }.takeIf { it > 0 }?.let { "Steady days: $it in total" }
                "clearhours" -> y?.let { "Clear hours yesterday: ${one(it.clearHours)}" }
                "overnight" -> ins.overnightGaps().lastOrNull()?.let { "Last overnight gap: ${Coaching.hm(it.second)}" }
                "peak" -> ins.dailyPeaks().lastOrNull { it.first < ins.today }?.let { "Peak yesterday: ${one(it.second)} mg" }
                "nextstep" -> Progress.stepDownProgress(data, now, tz)?.text
                "journey" -> ins.journey()?.let { "Journey to Clear Air: ${(it * 100).toInt()}%" }
                "taper" -> ins.taperSpeedText()
                "arrivals" -> ins.arrivals().firstOrNull { it.date != null && it.date > ins.today }?.let { "${it.rung.tier.title}: around ${shortDate(it.date!!)}" }
                "steadymonth" -> ins.steadyByMonth().lastOrNull()?.let { "Steady days in ${it.first}: ${it.second}" }
                "badges" -> ins.badges().size.takeIf { it > 0 }?.let { "Badges earned: $it" }
                else -> null
            }
            text?.let { Fact(id, chart(id)!!.title, it) }
        }
    }

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private fun shortDate(d: kotlinx.datetime.LocalDate) = "${d.dayOfMonth} ${months[d.monthNumber - 1]}"
    private fun round1(v: Double) = kotlin.math.round(v * 10) / 10
    private fun one(v: Double): String = round1(v).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
    private fun signed(m: Double): String = (if (m >= 0) "+" else "−") + Coaching.hm(kotlin.math.abs(m))
}
