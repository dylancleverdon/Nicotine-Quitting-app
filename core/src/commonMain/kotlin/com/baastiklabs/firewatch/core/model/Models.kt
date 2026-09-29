package com.baastiklabs.firewatch.core.model

import kotlinx.serialization.Serializable

/*
 * Data rules (see docs/data-format.md):
 * - Every field needs a default so older/newer data always decodes.
 * - Never rename or repurpose a field; add a new one instead.
 * - Times are epoch milliseconds (UTC).
 */

@Serializable
enum class ProductKind { GUM, POUCH, LOZENGE, PATCH, VAPE, CIGARETTE, OTHER }

/** How fast a product's nicotine reaches the blood. */
@Serializable
enum class SpeedProfile {
    /** Within minutes (vapes, cigarettes). */
    SPIKE,

    /** Builds over about half an hour (gum, pouches, lozenges). */
    BUILD,

    /** Slow and flat (patches). */
    FLAT,

    /**
     * Chew and park (gum): released in small bursts over the chewing time and absorbed slowly
     * through the cheek, so it peaks later and lower than a pouch. Older versions read it as BUILD.
     */
    CHEW,
}

@Serializable
data class Product(
    val id: String,
    val name: String,
    val kind: ProductKind = ProductKind.OTHER,
    /** Strength printed on the packaging. */
    val labelMg: Double = 0.0,
    /** Estimated fraction of the label strength that reaches the blood (0..1). */
    val absorption: Double = 0.5,
    val speed: SpeedProfile = SpeedProfile.BUILD,
    /** Shown as a big button on the home screen. */
    val onHome: Boolean = true,
    val order: Int = 0,
    val archived: Boolean = false,
    /** Set when this is a friend's product (doses count as borrowed). */
    val borrowedFrom: String? = null,
    val createdAt: Long = 0,
    /** What one unit (pouch, piece, lozenge, patch, session) costs, for "money saved". 0 = unknown. */
    val unitPrice: Double = 0.0,
    /** Units in a tin/box/pack, for "tins not bought". */
    val unitsPerPack: Int = 0,
)

/** How long a pouch/gum/lozenge stayed in. */
@Serializable
enum class Duration { FULL, HALF, QUICK }

@Serializable
data class Dose(
    val id: String,
    val productId: String,
    /** When it was taken. */
    val at: Long,
    // Snapshot of the product at log time, so history survives product edits and deletes.
    val productName: String = "",
    val kind: ProductKind = ProductKind.OTHER,
    val labelMg: Double = 0.0,
    val absorption: Double = 0.5,
    val speed: SpeedProfile = SpeedProfile.BUILD,
    // Log-time tweaks.
    val multiplier: Double = 1.0,
    val duration: Duration = Duration.FULL,
    val acidicDrink: Boolean = false,
    val tags: List<String> = emptyList(),
    /** For unknown doses (e.g. a friend's vape): a range of absorbed mg instead of one number. */
    val rangeLowMg: Double? = null,
    val rangeHighMg: Double? = null,
    val borrowed: Boolean = false,
    val note: String? = null,
    val loggedAt: Long = 0,
    /** Back-dated rough estimate (no real time). Counts toward totals, not toward timing stats. */
    val estimated: Boolean = false,
)

@Serializable
enum class CravingOutcome { OPEN, RODE_OUT, USED }

@Serializable
data class Craving(
    val id: String,
    val at: Long,
    /** 1..10, see CravingScale. */
    val intensity: Int = 5,
    val endedAt: Long? = null,
    val outcome: CravingOutcome = CravingOutcome.OPEN,
    val tags: List<String> = emptyList(),
)

@Serializable
enum class SleepKind { WAKE, SLEEP }

/** A "good morning" or "good night" tap. */
@Serializable
data class SleepEvent(
    val id: String,
    val at: Long,
    val kind: SleepKind = SleepKind.WAKE,
)

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    /** The product that defines one "piece". Defaults to 4 mg gum. */
    val referenceProductId: String = DefaultProducts.GUM_4MG,
    /** Default wake/sleep times, in minutes after midnight. */
    val wakeMinutes: Int = 7 * 60,
    val sleepMinutes: Int = 23 * 60,
    /** Days to hold at or under the target before a step down is offered. */
    val holdDays: Int = 7,
    /** No "clear for one" in the last hour before bed. */
    val windDown: Boolean = true,
    /** Optional goal: minutes after waking before the first piece (0 = off). */
    val morningDelayMinutes: Int = 0,
    /** Last time a step-down offer was dismissed ("Not yet"), epoch ms. */
    val stepDownSnoozedAt: Long = 0,
    val currency: String = "$",
    /** A reward D is saving toward with the money not spent. */
    val rewardName: String = "",
    val rewardCost: Double = 0.0,
    /** Offer the optional daily check-in on the home screen. */
    val dailyCheckIn: Boolean = false,
    /** Step-up offers are snoozed until this time. */
    val stepUpSnoozedAt: Long = 0,
    /** Show the watch (Health Connect) overlay. */
    val watchOverlay: Boolean = false,
    /** Opt-in, neutral reminders (off by default). Never "time for your next piece". */
    val remindCheckIn: Boolean = false,
    val remindBackup: Boolean = false,
    /** First-piece goal as a time of day, minutes after midnight (-1 = off; else morningDelayMinutes). */
    val morningDelayClock: Int = -1,
    /** "system" (follow the phone), "12h" or "24h". */
    val timeFormat: String = "system",
    /** Relapse prevention mode: the product the reminders are for. */
    val relapseProductId: String = DefaultProducts.GUM_4MG,
    /** When the "try Relapse prevention mode" card was last dismissed (epoch ms). */
    val relapseCardDismissedAt: Long = 0,
    /** When the "ready to switch to tapering?" card was last dismissed (epoch ms). */
    val movingOnDismissedAt: Long = 0,
    /** The first-launch welcome tour has been seen. */
    val tourSeen: Boolean = false,
    /** Opt-in "Hide next piece timer": the time shows only on a tap, and each tap is counted. */
    val hideTimer: Boolean = false,
    /** "Welcome back" was answered (back-dated or "Not now") at this time. */
    val welcomeBackDismissedAt: Long = 0,
    /** Show the "✓ N steady days" line on the home card. */
    val showSteadyDays: Boolean = true,
    /** Hide the "+38m pull" / "+1h stretch" line on each product button (separate from hideTimer). */
    val hideDosePreview: Boolean = false,
    /** Opt-in Coaching tips (Yesterday in review tips, "Bridge with gum"). Off = just measures. */
    val coachingTips: Boolean = false,
    /** Tip id → when it was dismissed (hidden for 2 weeks). */
    val tipDismissedAt: Map<String, Long> = emptyMap(),
    /** The one-time "This is your net" card has been seen. */
    val netExplained: Boolean = false,
    /** Volatility line over the blood-level wave (Insights → Today). */
    val showVolatility: Boolean = false,
    /** The highest steady-days milestone already celebrated (7, 30, 60, 90, 180, 365). */
    val steadyMilestoneSeen: Int = 0,
    /** Opt-in: range choices and a date stepper on multi-day charts. */
    val detailedCharts: Boolean = false,
    /** Practice day: this waking day (ISO date) uses [practicePieces] for the battery. "" = none. */
    val practiceDate: String = "",
    val practicePieces: Double = 0.0,
)

/**
 * The product that counts as one piece changed. Doses from [from] on use [productId]; earlier ones
 * keep [previousProductId]. "From today on" sets [from] to the change time; back-dating sets it to
 * the start of the chosen day.
 */
@Serializable
data class RefChange(
    val id: String,
    val at: Long,
    val from: Long,
    val productId: String,
    val previousProductId: String,
)

/** A tap to see the hidden next-piece time. [charging]: the battery wasn't full yet. */
@Serializable
data class TimerCheck(
    val id: String,
    val at: Long,
    val charging: Boolean = false,
)

/**
 * A mode was switched on or off. The latest one is the current state; the history marks which
 * days were in the mode. [mode] is "relapse" (Relapse prevention mode).
 */
@Serializable
data class ModeChange(
    val id: String,
    val at: Long,
    val on: Boolean,
    val mode: String = "relapse",
)

/** Optional daily check-in: three quick 1–5 taps. */
@Serializable
data class CheckIn(
    val id: String,
    val at: Long,
    val craving: Int = 3,
    val mood: Int = 3,
    val sleep: Int = 3,
)

/** D moved the target to a new rung (pieces a day). The latest one is the current target. */
@Serializable
data class RungChange(
    val id: String,
    val at: Long,
    val pieces: Double,
    /** "start", "down" or "up". */
    val reason: String = "",
)

object DefaultProducts {
    const val ZYN_3MG = "zyn-3"
    const val ZYN_6MG = "zyn-6"
    const val GUM_2MG = "gum-2"
    const val GUM_4MG = "gum-4"
    const val CIGARETTE = "cigarette"

    /** Absorbed mg of the default reference piece (4 mg gum at about 50%). */
    const val REFERENCE_MG = 2.0

    fun all(): List<Product> = listOf(
        Product(ZYN_3MG, "Zyn 3 mg", ProductKind.POUCH, 3.0, defaultAbsorption(ProductKind.POUCH), SpeedProfile.BUILD, onHome = true, order = 0),
        Product(ZYN_6MG, "Zyn 6 mg", ProductKind.POUCH, 6.0, defaultAbsorption(ProductKind.POUCH), SpeedProfile.BUILD, onHome = true, order = 1),
        Product(GUM_2MG, "Nicotine gum 2 mg", ProductKind.GUM, 2.0, defaultAbsorption(ProductKind.GUM), SpeedProfile.CHEW, onHome = true, order = 2),
        Product(GUM_4MG, "Nicotine gum 4 mg", ProductKind.GUM, 4.0, defaultAbsorption(ProductKind.GUM), SpeedProfile.CHEW, onHome = true, order = 3),
        cigarette(),
    )

    /** Off the home screen by default; used by the back-dated week and available to add. */
    fun cigarette() = Product(CIGARETTE, "Cigarette", ProductKind.CIGARETTE, 10.0, defaultAbsorption(ProductKind.CIGARETTE), SpeedProfile.SPIKE, onHome = false, order = 50)

    /**
     * Research-based starting points; every product's value is editable.
     * Gum is commonly cited at about half its label amount. Pouches vary by brand and time in.
     * Lozenges deliver somewhat more than gum. Patches deliver most of their labelled dose.
     * A cigarette's label (nicotine content) is ~10x what's absorbed.
     */
    fun defaultAbsorption(kind: ProductKind): Double = when (kind) {
        ProductKind.GUM -> 0.5
        ProductKind.POUCH -> 0.4
        ProductKind.LOZENGE -> 0.6
        ProductKind.PATCH -> 0.8
        ProductKind.VAPE -> 0.5
        ProductKind.CIGARETTE -> 0.1
        ProductKind.OTHER -> 0.5
    }

    fun defaultSpeed(kind: ProductKind): SpeedProfile = when (kind) {
        ProductKind.VAPE, ProductKind.CIGARETTE -> SpeedProfile.SPIKE
        ProductKind.PATCH -> SpeedProfile.FLAT
        ProductKind.GUM -> SpeedProfile.CHEW
        else -> SpeedProfile.BUILD
    }

    /** Products that sit in the mouth, where "how long it stayed in" matters. */
    fun isOral(kind: ProductKind): Boolean =
        kind == ProductKind.GUM || kind == ProductKind.POUCH || kind == ProductKind.LOZENGE

    fun kindLabel(kind: ProductKind): String = when (kind) {
        ProductKind.GUM -> "Gum"
        ProductKind.POUCH -> "Pouch"
        ProductKind.LOZENGE -> "Lozenge"
        ProductKind.PATCH -> "Patch"
        ProductKind.VAPE -> "Vape"
        ProductKind.CIGARETTE -> "Cigarette"
        ProductKind.OTHER -> "Other"
    }
}
