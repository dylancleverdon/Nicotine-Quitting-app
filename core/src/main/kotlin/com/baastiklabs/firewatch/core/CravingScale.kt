package com.baastiklabs.firewatch.core

/** The 1–10 craving intensity scale, each level tied to what it actually feels like. */
object CravingScale {
    data class Level(val value: Int, val name: String, val feels: String)

    val levels: List<Level> = listOf(
        Level(1, "Passing thought", "Crossed my mind for a second, then it was gone."),
        Level(2, "Noticed it", "I noticed it, but ignoring it takes no effort."),
        Level(3, "Mild pull", "A small tug. Doing something else makes it fade."),
        Level(4, "Nagging", "It keeps coming back. I catch myself thinking about it."),
        Level(5, "Distracting", "Hard to focus on what I'm doing. I keep checking the time."),
        Level(6, "Restless", "Fidgety, reaching for my pocket out of habit."),
        Level(7, "Strong", "Irritable or tense. Riding it out takes real effort."),
        Level(8, "Intense", "Hard to think about anything else. Everything is annoying."),
        Level(9, "Overwhelming", "Bargaining with myself, one step from giving in."),
        Level(10, "Worst ever", "Feels impossible to wait. It's all I can think about."),
    )

    fun level(value: Int): Level = levels[(value.coerceIn(1, 10)) - 1]
}
