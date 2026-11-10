package com.threadmill.connect.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF101418)
val CardInk = Color(0xFF1B222C)
val Mist = Color(0xFF9AA6B2)
val Paper = Color(0xFFF4F7FB)
val Coral = Color(0xFFFF8B7B)
val Teal = Color(0xFF7DDEC4)
val Sky = Color(0xFF8EB6FF)
val TrainingLime = Color(0xFFD6F25C)

enum class AccentChoice(val label: String, val color: Color) {
    Lime("Lime", Color(0xFFD6F25C)),
    Amber("Amber", Color(0xFFFFC857)),
    Orange("Orange", Color(0xFFFF9F43)),
    Pink("Pink", Color(0xFFFF7EB6)),
    Violet("Violet", Color(0xFFB79CFF)),
    Cyan("Cyan", Color(0xFF5CE1E6)),
}

private var accentState by mutableStateOf(AccentChoice.Lime.color)

val Accent: Color
    get() = accentState

fun applyAccent(choice: AccentChoice) {
    accentState = choice.color
}

fun stageColor(stage: com.threadmill.connect.model.Stage): Color = when (stage) {
    com.threadmill.connect.model.Stage.WarmUp -> Teal
    com.threadmill.connect.model.Stage.Training -> TrainingLime
    com.threadmill.connect.model.Stage.Recover -> Sky
    com.threadmill.connect.model.Stage.CoolDown -> Mist
}

@Composable
fun TreadmillTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Ink,
            primary = Accent,
            onPrimary = Color(0xFF172000),
            onBackground = Paper,
            onSurface = Paper,
        ),
        content = content,
    )
}
