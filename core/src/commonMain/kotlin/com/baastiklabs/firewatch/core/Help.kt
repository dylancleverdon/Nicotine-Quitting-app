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
            "What Firewatch does",
            "Firewatch shows you where you really are with nicotine, and helps you cut down at your own pace.\n\n" +
                "Log each piece, pouch, cigarette or vape in about two seconds. Firewatch does the maths.",
        ),
        Page(
            "Everything counts in pieces",
            "One piece is what a 4 mg nicotine gum delivers. Pouches, vapes and cigarettes are converted into pieces, " +
                "so everything adds up in one number.\n\nEvery number is an estimate, and it's labelled that way.",
        ),
        Page(
            "The battery",
            "The battery suggests spacing between pieces at your target pace. It's a suggestion, not an order.\n\n" +
                "It starts fresh every morning, and the wait is never longer than one normal gap.",
        ),
        Page(
            "Tiers",
            "After a week, Firewatch shows the level you're really at. When you've held a level for a while, " +
                "a step down is offered. You're never pushed.",
        ),
        Page(
            "No nagging",
            "No red screens, and no streaks that reset.\n\nThe one exception is Relapse prevention mode, which you " +
                "can turn on to get a reminder to chew at a steady gap. It's off unless you choose it.",
        ),
    )

    val why: List<Page> = listOf(
        Page("Measure, don't judge", "Honest numbers are more useful than guilt. Firewatch shows what happened, labels it as an estimate, and never tells you off."),
        Page("Pieces", "Gum, pouches, vapes and cigarettes deliver very different amounts of nicotine. Converting everything into one unit, the piece (a 4 mg gum), lets you compare days and products fairly. How much you actually absorb varies, so every figure is an estimate."),
        Page("One piece at a time", "Step-downs are small, usually one piece a day or less, because small steps are the ones that stick."),
        Page("The battery", "The battery is a suggestion for spacing, not a rule. It forgives every morning, so a hard day never carries over, and the wait is never longer than one normal gap, so it never turns into a slog."),
        Page("Stretch, pull and net", "Stretch is time you held off with a full battery. Pull is nicotine that came before the battery had room for it, including extra from a big or doubled dose. Net is stretch minus pull: the honest summary of the day."),
        Page("Why there's no step-down button", "A step down is offered once you've shown you can hold your current level, so each step is one you're ready for."),
        Page("Why stepping up is always allowed", "A tough stretch is normal, not a failure. Stepping up is always there, with no penalty."),
        Page("Why tapering to zero matters for your receptors", "Regular nicotine makes your brain grow extra nicotine receptors, and those extra receptors are a big part of what cravings feel like. While any nicotine keeps coming in (gum included) they stay raised. Each step down lets them settle a little, and they only fully return to typical once nicotine stops, over roughly 6 to 12 weeks. That's why the last small steps, and Clear Air itself, are worth so much."),
        Page("Relapse prevention mode", "Early on, going back to smoking or vaping is the biggest risk. Chewing on a steady schedule, before cravings start, keeps you ahead of them. That's why this mode is the one exception to Firewatch's no-reminders rule, and why it's only on if you turn it on."),
    )

    val articles: List<Article> = listOf(
        Article("log", HOW, "Log a dose", "On the Log tab, tap the product's button. That's it: it's logged at the current time.\n\nHold the button instead to set the time, amount and more before logging."),
        Article("edit", HOW, "Change a dose's time or amount, or undo it", "Right after logging, tap Undo on the \"Logged\" message.\n\nLater: tap the dose in the Today list on the Log tab, or open the Calendar, pick the day and tap it. You can change its time or amount, or delete it."),
        Article("friend", HOW, "Log a friend's vape", "Tap \"Friend's vape\" on the Log tab. Pick roughly how much you had and, if you know it, the strength. Firewatch logs it as a range, because the dose is unknown."),
        Article("sleep", HOW, "Use Good morning / Good night", "Tap Good morning when you get up and Good night when you go to bed. It's optional: without them, Firewatch uses your usual times from Settings. They make the battery and the day's figures more accurate."),
        Article("stepup", HOW, "Step up your taper", "In Settings, under your plan, tap \"Step back up a rung\". It's always allowed, and there's no penalty.\n\nThe Log tab also offers a step up, and says why, when today looks rough: pull reaching one full gap, several strong cravings (or two close together), more than a piece over today's target, lots of timer checks while the battery refills, or a craving that ended with a cigarette or vape. It also offers one when two or more of those have been building over the last 5 days. The point is to catch a rough patch before it becomes a relapse, and to find the level you're really at. \"I'm OK\" hides it until tomorrow."),
        Article("hidetimer", HOW, "Hide next piece timer", "Settings → Your plan → \"Hide next piece timer\" (off unless you turn it on). The Log tab then shows the battery bar with \"Tap to see your next piece time\"; a tap shows the time for 30 seconds.\n\nEach tap is counted. How often you check, especially while the battery is still refilling, is a useful signal: Insights → Patterns shows it, and it helps decide when to offer a step up or a step down."),
        Article("cravingended", MEANS, "How cravings ended", "Log a craving with one tap, then put the app down: there's nothing to press afterwards. Firewatch works out how it ended from what you log in the next 45 minutes:\n\n✓ Rode it out: no piece.\n✓ Waited for the right time: a piece, but only once the battery was full (or on schedule in Relapse prevention mode).\nEarly: gum, a pouch or another product before the battery was full.\nRelapse: a cigarette or vape.\n\nThe first two count as wins. Insights → Patterns shows the last 2 weeks."),
        Article("backup", HOW, "Export, import and move between phone and web", "Settings → Your data → Export saves a file with everything. Tap Import on the other device (phone or the web app) to bring it across. Both use the same file."),
        Article("widget", HOW, "Add a widget", "Long-press your home screen, tap Widgets, find Firewatch and drag one out. There's a quick-log widget and a craving widget."),
        Article("relapse", HOW, "Turn Relapse prevention mode on or off", "Tap \"Relapse prevention mode\" at the bottom of the Log tab. The same button turns it off. You can also switch it and choose the product it reminds you about in Settings.\n\nReminders come at your tier's gap (2 hours before you have a tier), never during sleeping hours. On the web app, reminders are Android-only for now."),
        Article("suggest", HOW, "Suggest something or report a bug", "At the very bottom of the Log tab, tap \"Suggest something / report a bug\". Pick Idea, Bug or Other, write it, and tap Send. Your name is optional.\n\nIt goes to the Firewatch maker's private suggestions list, with the app version and phone model so bugs make sense. If you're offline it's saved and sent next time Firewatch opens."),
        Article("battery", MEANS, "The battery, \"Next piece\" and \"Clear for one\"", "The battery refills at your target pace. \"Clear for one\" means it's full. \"Next piece around …\" means it's still refilling.\n\nIt starts fresh every morning, never goes into debt, and never makes you wait overnight."),
        Article("stretch", MEANS, "Stretch, pull and net", "Stretch: time you held off with a full battery. Pull: nicotine that came before the battery had room for it, including extra from a big or doubled dose. Net = stretch − pull. A negative net is shown in grey, never red.\n\nThey're paused on Relapse prevention mode days."),
        Article("tiers", MEANS, "Tiers and the 7-day average", "Your tier comes from your average pieces a day over the last 7 days. The tier names are fire-themed, from the biggest blaze down to embers and beyond."),
        Article("quality", MEANS, "Nicotine quality", "Quality scores how you take nicotine, on a food scale from 🥦 broccoli (90–100) to 🍔 burger (0–10). Gum and patches score best; cigarettes score worst."),
        Article("insights", MEANS, "The Insights charts", "Insights shows today's nicotine curve, cravings ahead, your receptors, stretch and pull, trends, your patterns by time and tag, and milestones. Every figure is an estimate."),
        Article("cravingsahead", MEANS, "Cravings ahead (the craving forecast)", "Insights → Cravings ahead shows the chance of a craving over the next 24 hours, and how strong one would probably be.\n\nIt learns from your own logged cravings (when they happen and how strong they are), your estimated nicotine level (cravings get likelier as it drops below your normal for that time of day), and recent step-downs. It assumes you take pieces when the battery suggests.\n\nWith fewer than 10 logged cravings it's still learning. The more cravings you log, the sharper it gets, and it tells you how often it's been right over the last 2 weeks. It's an estimate, not a promise, and it never sends alerts."),
        Article("receptors", MEANS, "Receptors (the healing chart)", "Insights → Receptors estimates your nicotine receptor load: 100% is typical of heavy regular use, 0% is a typical non-user.\n\nIt's built on brain-imaging research: regular nicotine raises the number of receptors, and after nicotine stops they return to typical over about 6 to 12 weeks. Your own logs set how much nicotine you've had each day.\n\nThe solid line is your past, the dashed line is where you'll be if you keep following the program (one rung down each hold period, then Clear Air), and the faint line is staying where you are. It's an estimate, not a medical measurement."),
        Article("nostepdown", QUESTIONS, "Why is there no step-down button?", "A step down is offered once you've held your current level for a while, so each step is one you're ready for."),
        Article("reset", QUESTIONS, "Why does the battery reset every morning?", "So a hard day never carries over. Every morning is a fresh start."),
        Article("estimate", QUESTIONS, "Why does it say \"estimate\" everywhere?", "How much nicotine you absorb depends on the product, how long you use it and more. Firewatch's figures are good estimates, not measurements."),
    )

    fun search(query: String): List<Article> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return articles
        return articles.filter { q in it.title.lowercase() || q in it.body.lowercase() }
    }
}
