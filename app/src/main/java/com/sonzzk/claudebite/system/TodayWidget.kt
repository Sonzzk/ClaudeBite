package com.sonzzk.claudebite.system

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sonzzk.claudebite.ClaudeBiteApp
import com.sonzzk.claudebite.MainActivity
import com.sonzzk.claudebite.R
import com.sonzzk.claudebite.domain.NutritionCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/** Home-screen widget: Claude-chan's face, calories left (or % if hidden) and protein. Tap to open the app. */
class TodayWidget : GlanceAppWidget() {

    private data class Snapshot(val eaten: Int, val budget: Int, val protein: Int, val proteinGoal: Int, val hide: Boolean, val streak: Int)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = runCatching { load(context) }.getOrNull()
        provideContent { Content(snap) }
    }

    private suspend fun load(context: Context): Snapshot {
        val c = (context.applicationContext as ClaudeBiteApp).container
        val today = LocalDate.now().toString()
        val entries = c.repository.entriesFor(today).first()
        val targets = NutritionCalculator.targets(c.profiles.profile.first())
        val burned = runCatching { c.tools.burnedOn(today).first() }.getOrDefault(0)
        return Snapshot(
            eaten = entries.sumOf { it.calories },
            budget = targets.calories + burned,
            protein = entries.sumOf { it.proteinG.toDouble() }.roundToInt(),
            proteinGoal = targets.proteinG,
            hide = c.profiles.settings.first().hideCalories,
            streak = c.repository.currentStreak(),
        )
    }

    @Composable
    private fun Content(s: Snapshot?) {
        val ink = ColorProvider(Color(0xFF2B2420))
        val soft = ColorProvider(Color(0xFF6B5E57))
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Color(0xFFFFF4EC)).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val face = when {
                s == null || s.eaten == 0 -> R.drawable.px_expr_00
                s.eaten > s.budget -> R.drawable.px_expr_05
                else -> R.drawable.px_expr_02
            }
            Image(ImageProvider(face), contentDescription = "Claude-chan", modifier = GlanceModifier.size(64.dp))
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                if (s == null) {
                    Text("Open ClaudeBite", style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                } else {
                    val left = s.budget - s.eaten
                    val headline = when {
                        s.hide -> "${(s.eaten * 100f / s.budget.coerceAtLeast(1)).roundToInt()}% of today"
                        left >= 0 -> "$left kcal left"
                        else -> "${-left} kcal extra"
                    }
                    Text(headline, style = TextStyle(color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (s.eaten.toFloat() / s.budget.coerceAtLeast(1)).coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                        color = ColorProvider(Color(0xFFD97757)),
                        backgroundColor = ColorProvider(Color(0xFFF3E7DC)),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        "Protein ${s.protein}/${s.proteinGoal} g" + if (s.streak > 0) "  ·  🔥 ${s.streak}" else "",
                        style = TextStyle(color = soft, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TodayWidget().updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
