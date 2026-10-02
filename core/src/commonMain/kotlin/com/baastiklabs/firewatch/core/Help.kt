package com.baastiklabs.firewatch.core

import kotlinx.serialization.Serializable

/**
 * Help, the welcome tour and "Why Firewatch works this way". Written once here and shown by both
 * the Android and web apps, so they never drift apart. Plain language; blank lines split paragraphs.
 */
object Help {
    @Serializable
    data class Article(val id: String, val section: String, val title: String, val body: String)

    @Serializable
    data class Page(val title: String, val body: String)

    const val HOW = "How to…"
    const val MEANS = "What it means"
    const val QUESTIONS = "Questions"

    val tour: List<Page> = listOf(
        Page(
            "Take control of your nicotine",
            "Firewatch shows you where your nicotine use really is, helps keep it from creeping up, and, if you want, helps you cut down.\n\n" +
                "Holding steady is a win. Cutting down is up to you.",
        ),
        Page(
            "Everything counts in pieces",
            "One piece is what a 4 mg nicotine gum delivers. Pouches, vapes and cigarettes are converted into pieces, " +
                "so everything adds up in one clear number.\n\nEvery number is an estimate, and it's labelled that way.",
        ),
        Page(
            "Your real level, fast",
            "You start at an early estimate of 8 pieces a day. As you log, it firms up to the level you're really at. " +
                "If a day runs heavier, Firewatch offers a step up the same day: knowing where you really are comes first.",
        ),
        Page(
            "Your two tools",
            "Spacing: the battery shows when your next piece fits your pace. It's a suggestion, not an order, and it starts fresh every morning.\n\n" +
                "Net: whether you're ahead of or behind that pace today, counting both timing and dose size. " +
                "Holding net at zero or better is how you stay in control.",
        ),
        Page(
            "A tool, not a judge",
            "No red screens, no streaks that reset, no telling you off. Miss a few days? Just pick up where you left off.\n\n" +
                "Clear Air (nicotine-free) is always within reach whenever you want it.",
        ),
    )

    val why: List<Page> = listOf(
        Page("Control, not pressure", "Firewatch's job is to put you in control. First it finds where your nicotine use really is. Then it helps you keep it there, or lower. Tapering is always offered, never pushed, and staying at a steady level is a real achievement."),
        Page("An honest number first", "Guessing is easy to get wrong. A clear number, even an estimate, tells you where you actually stand. That's why new users start at 8 a day and firm up fast, and why a step up is offered the moment the logs say you're heavier: a level you can actually hold beats a frustrating one you can't."),
        Page("Measure, don't judge", "Firewatch shows what happened, labels it as an estimate, and moves on. A miss is just a data point. There's no scolding and no spin."),
        Page("Pieces", "Gum, pouches, vapes and cigarettes deliver very different amounts of nicotine. Converting everything into one unit, the piece (a 4 mg gum), lets you compare days and products fairly. How much you actually absorb varies, so every figure is an estimate."),
        Page("One piece at a time", "Step-downs are small, usually one piece a day or less, because small steps are the ones that stick."),
        Page("The battery", "The battery is a suggestion for spacing, not a rule. It forgives every morning, so a hard day never carries over, and the wait is never longer than one normal gap."),
        Page("Stretch, pull and net", "Stretch is time you held off with a full battery. Pull is nicotine that came before the battery had room for it, including extra from a big or doubled dose. Net is stretch minus pull: the honest summary of the day."),
        Page("Why there's no step-down button", "A step down is offered once you've shown you can hold your current level, so each step is one you're ready for. You can always say \"Stay here\"."),
        Page("Why stepping up is always allowed", "Finding your real level is the first goal. Stepping up isn't a failure; it's the app getting more accurate."),
        Page("Why nothing is shareable", "Your numbers are yours. Sharing invites judgement, and judgement makes it harder to come back after a rough patch. Firewatch stays private so it can always be a safe place to pick back up."),
        Page("Why tapering to zero matters for your receptors", "Regular nicotine makes your brain grow extra nicotine receptors, and those extra receptors are a big part of what cravings feel like. While any nicotine keeps coming in (gum included) they stay raised. Each step down lets them settle a little, and they only fully return to typical once nicotine stops, over roughly 6 to 12 weeks. That's why Clear Air is always worth keeping within reach."),
        Page("Relapse prevention mode", "Early on, going back to smoking or vaping is the biggest risk. Chewing on a steady schedule, before cravings start, keeps you ahead of them. That's why this mode is the one exception to Firewatch's no-reminders rule, and why it's only on if you turn it on."),
    )

    val articles: List<Article> = listOf(
        Article("stretch", MEANS, "Stretch, pull and net", "Stretch: time you held off with a full battery, plus a bonus for pieces smaller than one (a gum 2 mg adds half a gap). Pull: time a piece came before the battery was full, plus extra for pieces bigger than one (a Zyn 6 mg adds about a fifth of a gap).\n\nNet = stretch − pull: time ahead of or behind your level's pace, whatever you use. Two gum 2 mg count the same as one gum 4 mg. A negative net is grey, never red. Paused on Relapse prevention mode days.\n\nMorning stretch: the battery is full when you wake up, so every minute before your first piece already counts as stretch. The Log tab shows it (\"Morning stretch: 1h 20m\") until your first piece."),
        Article("log", HOW, "Log a dose", "On the Log tab, tap the product's button. That's it: it's logged at the current time.\n\nHold the button instead to set the time, amount and more before logging."),
        Article("edit", HOW, "Change a dose's time or amount, or undo it", "Right after logging, tap Undo on the \"Logged\" message.\n\nLater: tap the dose in the Today list on the Log tab, or open the Calendar, pick the day and tap it. You can change its time or amount, or delete it."),
        Article("friend", HOW, "Log a friend's vape", "Tap \"Friend's vape\" on the Log tab. Pick roughly how much you had and, if you know it, the strength. Firewatch logs it as a range, because the dose is unknown."),
        Article("sleep", HOW, "Use Good morning / Good night", "Tap Good morning when you get up and Good night when you go to bed. It's optional: without them, Firewatch uses your usual times from Settings. They make the battery and the day's figures more accurate."),
        Article("stepup", HOW, "Step up your taper", "In Settings, under your plan, tap \"Step back up a rung\". It's always allowed, and there's no penalty.\n\nThe Log tab also offers a step up, and says why, when today looks rough: pull reaching one full gap, several strong cravings (or two close together), more than a piece over today's target, lots of timer checks while the battery refills, or a craving that ended with a cigarette or vape. It also offers one when two or more of those have been building over the last 5 days. The point is to catch a rough patch before it becomes a relapse, and to find the level you're really at. \"I'm OK\" hides it until tomorrow."),
        Article("hidetimer", HOW, "Hide next piece timer", "Settings → Your plan → \"Hide next piece timer\" (off unless you turn it on). The Log tab then shows the battery bar with \"Tap to see your next piece time\"; a tap shows the time for 30 seconds.\n\nEach tap is counted. How often you check, especially while the battery is still refilling, is a useful signal: Insights → Patterns shows it, and it helps decide when to offer a step up or a step down."),
        Article("cravingended", MEANS, "How cravings ended", "Log a craving with one tap, then put the app down: there's nothing to press afterwards. Firewatch works out how it ended from what you log in the next 45 minutes:\n\n✓ Rode it out: no piece.\n✓ Waited for the right time: a piece, but only once the battery was full (or on schedule in Relapse prevention mode).\nEarly: gum, a pouch or another product before the battery was full.\nRelapse: a cigarette or vape.\n\nThe first two count as wins. Insights → Patterns shows the last 2 weeks."),
        Article("backdate", HOW, "Add days you didn't log", "Calendar → pick the day → \"Add what you had\". Tap each thing you used that day, once per use. No times needed: Firewatch spreads them across your usual waking day. Want more accuracy? \"Add one at an exact time\" is there too.\n\nAfter a break of a couple of days or more, the Log tab says \"Welcome back\" and offers the same thing for each missed day. Back-dated doses count in your totals and level, not in timing stats."),
        Article("unknowndays", MEANS, "Days with nothing logged: ?, 🌿 and 👻", "Once a day is over, a day with nothing logged shows \"?\" on the calendar: Firewatch can't tell whether you had none or just didn't log. Until you say, it's left out of your figures (your level, Insights, steady days and the step-down count), so a forgotten day never flatters or hurts your numbers.\n\nTap the day on the calendar to choose: \"I had none\" makes it a clear day 🌿 (it counts as 0, and as a win), \"Add what you had\" back-dates it, and \"Don't log this day\" leaves it out for good 👻. Opening the app or logging a craving doesn't make a day clear: only \"I had none\" does."),
        Article("lighter", MEANS, "\"Your logs measure…\" and working from your measured level", "When your last 7 days measure two or more levels lighter than the one you're working at, Firewatch may offer to practice that pace (only a hold period after your last level change, with at least 6 of the last 7 days known). Practicing is just practice: you can stop any time and go back to your usual pace, with no step down.\n\nOnce practice pace at that level has covered at least 75% of your hold period's waking hours, with practice net at zero or better, Firewatch offers to work from that level instead: a more accurate level, not a taper step. Your step-down count then starts again. Turn these offers off in Settings → Your plan, or tick \"Don't ask me again\"."),
        Article("levelhistory", MEANS, "Level history", "Insights → Ladder → Level history lists every change to your level, newest first: steps down, steps up (with the reason) and each practice pace session with its practice net. The calendar marks the days your level changed with ▲ or ▼."),
        Article("early", MEANS, "The early estimate (your first week)", "New users start at 8 pieces a day, so the battery and the step-up offer work from day one. As you log, the estimate firms up each day toward your real level. After your first week, Firewatch shows your measured starting point and asks if you want to work from there."),
        Article("steady", MEANS, "Steady days and holding steady", "A steady day is one with net at zero or better and no cigarettes or vapes. Days added without times count if you stayed at or under your level, and a clear day 🌿 always counts. A day with nothing logged (\"?\") or left out (👻) doesn't. Your steady-days total only ever goes up: a rough day just doesn't add one. It's shown on the home card (you can hide it in Settings → Your plan), with milestones at 7, 30, 60, 90, 180 and 365.\n\nHolding a level is a win too: the Log tab shows how many days you've held your current level in total, and Insights → Going up has a Holding steady chart."),
        Article("practice", HOW, "Practice pace", "Practice pace tries a lighter pace without changing your level. The battery and the dose preview use the lighter pace's gap, and \"Practice net\" shows how it's going while it's on. Your level, tier, normal net, steady days and step-down count don't change.\n\nStart it in Settings → Your plan → Practice pace (one level lighter, or down to the level your logs measure), or from \"Try it for a day\" on a step-down offer. Choose \"Turn off at bedtime\" or \"Leave it on until I turn it off\"; you can change that, or tap \"Back to my pace\", any time. It's off while Relapse prevention mode is on.\n\nAfter a day at the next level's pace, the next morning asks: Step down, or Stay here. Either is fine."),
        Article("reference", HOW, "Change what counts as one piece", "Settings → Products and pieces → \"One piece is\". Pick a product, then choose \"From today on\" (past days keep the old piece size) or back-date the change to a date, which recalculates your past totals and may change your level."),
        Article("backup", HOW, "Export, import and move between phone and web", "Settings → Your data → Export saves a file with everything. Tap Import on the other device (phone or the web app) to bring it across. Both use the same file."),
        Article("widget", HOW, "Add a widget", "Long-press your home screen, tap Widgets, find Firewatch and drag one out. There's a quick-log widget and a craving widget."),
        Article("relapse", HOW, "Turn Relapse prevention mode on or off", "Tap \"Relapse prevention mode\" at the bottom of the Log tab. The same button turns it off. You can also switch it and choose the product it reminds you about in Settings.\n\nReminders come at your tier's gap (2 hours before you have a tier), never during sleeping hours. On the web app, reminders are Android-only for now."),
        Article("suggest", HOW, "Suggest something or report a bug", "At the very bottom of the Log tab, tap \"Suggest something / report a bug\". Pick Idea, Bug or Other, write it, and tap Send. Your name is optional.\n\nIt goes to the Firewatch maker's private suggestions list, with the app version and phone model so bugs make sense. If you're offline it's saved and sent next time Firewatch opens."),
        Article("battery", MEANS, "The battery, \"Next piece\" and \"Clear for one\"", "The battery is a simple timer. Every piece empties it, whatever its size, and it refills over one gap at your level (16 waking hours ÷ your pieces a day). \"Next piece around …\" is always one gap after your last piece; \"Clear for one\" means it's full.\n\nIt starts fresh every morning and never makes you wait overnight. Near bedtime there's a short note, but the guidance stays.\n\nUnder each product, a quiet line shows what logging it now would do to your net, e.g. \"+38m pull\" or \"+1h 36m stretch\"."),
        Article("tiers", MEANS, "Tiers and the 7-day average", "Your tier comes from your average pieces a day over the last 7 days. The tier names are fire-themed, from the biggest blaze down to embers and beyond."),
        Article("quality", MEANS, "Nicotine quality", "Quality scores how you take nicotine, on a food scale from 🥦 broccoli (90–100) to 🍔 burger (0–10). Gum and patches score best; cigarettes score worst."),
        Article("insights", MEANS, "The Insights charts", "Insights shows your nicotine curve for any day (tap ‹ and › to go back a day at a time), cravings ahead, your receptors, stretch and pull, trends, patterns and milestones. Touch and drag any chart to read the value at that point.\n\nWant more? Settings → Your plan → \"Detailed charts\" adds range choices (7, 30, 90 days, all) and earlier/later on multi-day charts. Every figure is an estimate."),
        Article("cravingsahead", MEANS, "Cravings ahead (the craving forecast)", "Insights → Cravings ahead shows the chance of a craving over the next 24 hours, and how strong one would probably be.\n\nIt learns from your own logged cravings (when they happen and how strong they are), your estimated nicotine level (cravings get likelier as it drops below your normal for that time of day), and recent step-downs. It assumes you take pieces when the battery suggests.\n\nWith fewer than 10 logged cravings it's still learning. The more cravings you log, the sharper it gets, and it tells you how often it's been right over the last 2 weeks. It's an estimate, not a promise, and it never sends alerts."),
        Article("receptors", MEANS, "Receptors (the healing chart)", "Insights → Receptors estimates your nicotine receptor load: 100% is typical of heavy regular use, 0% is a typical non-user.\n\nIt's built on brain-imaging research: regular nicotine raises the number of receptors, and after nicotine stops they return to typical over about 6 to 12 weeks. Your own logs set how much nicotine you've had each day.\n\nThe solid line is your past, the dashed line is where you'll be if you keep following the program (one rung down each hold period, then Clear Air), and the faint line is staying where you are. It's an estimate, not a medical measurement."),
        Article("volatility", MEANS, "What is volatility?", "Nicotine volatility is how fast and how much your nicotine level changes, in mg per hour. Firewatch looks at how steeply your estimated level is rising or falling at every minute; steep stretches count extra, steady or clear time counts close to zero.\n\nFast hits (a vape) and big doses score high. Slow, steady delivery (gum, evenly spaced small pieces) scores low, even at the same overall level. For one dose each: a vape scores about 4.4, a Zyn 6 mg about 2.1, and a gum 4 mg about 1.1.\n\nInsights → Today shows one number per day, and \"Show volatility\" adds a running line (the last 30 minutes) over the blood-level wave. Lower means steadier nicotine through the day. It's an estimate, like every figure."),
        Article("yesterday", MEANS, "Yesterday in review", "The card at the top of Insights sums up yesterday's waking day: pieces and net, volatility, your delivery mix, the longest gap between pieces, doses stacked while the last one was still peaking, and your morning stretch. Facts only, with no comparison to other days. With Coaching tips on, up to two tips appear under the facts."),
        Article("coaching", HOW, "Coaching tips", "Settings → Your plan → Coaching tips (off unless you turn it on). Practical tips based on your own logs: tips in Yesterday in review, and \"Bridge with gum\" on the Log tab when pouches are most of your use.\n\nNever a notification, never during a craving, at most one tip on the Log tab, and any tip can be dismissed for 2 weeks. Off means Firewatch just measures."),
        Article("gumchew", MEANS, "How gum is estimated (chew and park)", "Gum is chewed until it tingles, parked in the cheek, then chewed again, for about 30 minutes. Nicotine comes out in small bursts across that time and is absorbed slowly through the cheek, so it arrives later and more gently than a pouch of the same strength (peaking around 45 to 60 minutes).\n\nThe total absorbed is the same, so pieces, stretch, pull, net and tiers don't change. You can change a product's speed in Settings → Products."),
        Article("pouchvsgum", QUESTIONS, "Why can pouches be harder to cut down than gum?", "Stronger doses: a Zyn 6 mg is about 1.2 pieces, so each pouch delivers more than a 4 mg gum.\n\nSteady and effortless: a pouch releases on its own, while gum needs chew and park. That makes it easy to keep a pouch in for a long time, or reach for another without noticing.\n\nDiscreet and flavoured: usable anywhere, any time, so the habit fits more of the day.\n\nSpeed: a pouch arrives faster and more smoothly than gum's stepped release, but far slower than a vape. Nicotine volatility in Insights shows the difference day to day."),
        Article("speedreceptors", QUESTIONS, "Does delivery speed affect my receptors?", "The receptor healing estimate uses how much nicotine is in your body on average over 24 hours: more for longer means more load, less means healing.\n\nFaster delivery mostly strengthens the habit: a quick hit feels rewarding sooner, so the brain learns the routine faster. The estimate doesn't try to put a number on that, because there isn't a reliable way to.\n\nWhere speed shows up in Firewatch: nicotine volatility and spike share."),
        Article("nostepdown", QUESTIONS, "Why is there no step-down button?", "A step down is offered once you've held your current level for a while, so each step is one you're ready for.\n\nIt needs full days in a row at or under your level (the hold period in Settings → Your plan), counted from your last level change. Insights → Forecasts → \"Next step down\" shows how many you have, like \"1 of 3 days held\", and \"If you take each step\" starts from the same count. Today counts once it's over. Days with nothing logged (\"?\") or left out (👻) are skipped, not counted and not a reset."),
        Article("reset", QUESTIONS, "Why does the battery reset every morning?", "So a hard day never carries over. Every morning is a fresh start."),
        Article("estimate", QUESTIONS, "Why does it say \"estimate\" everywhere?", "How much nicotine you absorb depends on the product, how long you use it and more. Firewatch's figures are good estimates, not measurements."),
    )

    /** Shown when the steady-days line is tapped. */
    const val STEADY_EXPLAINER = "A steady day is one with net at zero or better and no cigarettes or vapes. Days added without times count if you stayed at or under your level."

    /** The one-time card the first time net appears. */
    const val NET_EXPLAINER = "This is your net: +40m means you're 40 minutes ahead of your pace today, counting both timing and dose size. Holding it at zero or better is how you stay in control."

    /** Practice pace: the two-line explainer behind the "?" on the battery row. */
    const val PRACTICE_EXPLAINER = "Practice pace tries a lighter pace without changing your level: the battery and dose preview use its gap, and practice net shows how it's going.\nStop any time in Settings → Your plan → Practice pace."

    const val PRACTICE_STOP_NOTE = "You can stop any time in Settings → Your plan → Practice pace."

    const val PRACTICE_RELAPSE_NOTE = "Practice pace is off while Relapse prevention mode is on, because its reminders follow your level's gap."

    const val HOLD_SHORT_NOTE = "Shorter holds mean faster steps. Each step is easier to keep with more days at it."

    const val UNKNOWN_DAYS_NOTE = "? = nothing logged that day. Tap one to mark it clear, add what you had, or leave it out. Until then it's left out of your figures."

    /** The lighter-level practice offer (§7), worded as D approved. */
    fun lighterOfferTitle(measuredLabel: String) = "Your logs measure $measuredLabel"
    fun lighterOfferBody(workingLabel: String, measuredTier: String) =
        "You're working at $workingLabel. Practicing $measuredTier pace for a while is a way to check it. If it suits you, you can then choose to work from $measuredTier: a more accurate level.\n\n" +
            "Practicing is just practice. You can stop any time and go back to your usual pace, with no step down."

    fun search(query: String): List<Article> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return articles
        return articles.filter { q in it.title.lowercase() || q in it.body.lowercase() }
    }
}
