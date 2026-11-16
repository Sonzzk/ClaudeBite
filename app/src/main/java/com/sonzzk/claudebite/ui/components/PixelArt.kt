package com.sonzzk.claudebite.ui.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import com.sonzzk.claudebite.domain.MascotMood
import java.util.concurrent.ConcurrentHashMap

/** "pixel" (Amos's pixel sheet, the default) or "painted" (the v1 art). Provided at the app root from Settings. */
val LocalMascotStyle = staticCompositionLocalOf { "pixel" }

/** Settings (sounds, haptics, hide calories...) for any composable that needs them. */
val LocalAppSettings = androidx.compose.runtime.compositionLocalOf { com.sonzzk.claudebite.data.prefs.AppSettings() }

/**
 * Names of the pixel sprites in res/drawable-nodpi (installed by tools/sprites/install.py).
 * Full-body poses for big mascots, faces for small avatars. Several poses per mood so she can idle between them.
 */
object PixelArt {
    private fun pose(vararg i: Int) = i.map { "px_pose_%02d".format(it) }

    val poses: Map<MascotMood, List<String>> = mapOf(
        MascotMood.HAPPY to pose(0, 1, 11, 19),
        MascotMood.EXCITED to pose(4, 6, 9),
        MascotMood.THINKING to pose(34),
        MascotMood.WORKING to pose(34),
        MascotMood.PROUD to pose(31, 24),
        MascotMood.WORRIED to pose(20, 13),
        MascotMood.SLEEPY to pose(10),
        MascotMood.HUNGRY to pose(22, 30),
        MascotMood.WAVING to pose(26, 25, 2),
        MascotMood.LOVE to pose(15),
        MascotMood.SHY to pose(17, 5),
        MascotMood.CONFUSED to pose(28),
        MascotMood.COZY to pose(14, 23),
        MascotMood.CELEBRATE to pose(9, 4, 27),
        MascotMood.POUTY to pose(8),
    )

    val faces: Map<MascotMood, String> = mapOf(
        MascotMood.HAPPY to 0, MascotMood.EXCITED to 7, MascotMood.THINKING to 24, MascotMood.WORKING to 24,
        MascotMood.PROUD to 10, MascotMood.WORRIED to 23, MascotMood.SLEEPY to 20, MascotMood.HUNGRY to 6,
        MascotMood.WAVING to 2, MascotMood.LOVE to 30, MascotMood.SHY to 5, MascotMood.CONFUSED to 9,
        MascotMood.COZY to 21, MascotMood.CELEBRATE to 3, MascotMood.POUTY to 4,
    ).mapValues { "px_expr_%02d".format(it.value) }

    /** Moods that show the equipped outfit sprite (the outfit sprites are one happy pose each). */
    val outfitMoods = setOf(
        MascotMood.HAPPY, MascotMood.WAVING, MascotMood.PROUD, MascotMood.EXCITED,
        MascotMood.LOVE, MascotMood.SHY, MascotMood.CELEBRATE, MascotMood.HUNGRY,
    )

    private val ids = ConcurrentHashMap<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun id(context: Context, name: String): Int = ids.getOrPut(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    /** Sprite names to try, best first, for this outfit + mood. [variant] picks an idle pose. */
    fun candidates(outfit: String, mood: MascotMood, small: Boolean, variant: Int): List<String> {
        val outfitSprite = com.sonzzk.claudebite.domain.Outfits.byId(outfit)?.pixelSprite
        val list = mutableListOf<String>()
        if (small) {
            list += faces[mood] ?: "px_expr_00"
            list += "px_expr_00"
            return list
        }
        if (outfitSprite != null && mood in outfitMoods) list += outfitSprite
        val p = poses[mood].orEmpty()
        if (p.isNotEmpty()) list += p[Math.floorMod(variant, p.size)]
        list += "px_pose_00"
        return list
    }
}

/** Draws a pixel sprite crisply (nearest-neighbour scaling, no blur). */
@Composable
fun PixelSprite(name: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val id = remember(name) { PixelArt.id(context, name) }
    if (id == 0) return
    val bitmap: ImageBitmap = ImageBitmap.imageResource(id)
    val painter = remember(bitmap) { BitmapPainter(bitmap, filterQuality = FilterQuality.None) }
    Image(painter, contentDescription, modifier, contentScale = ContentScale.Fit)
}

/** "640 kcal", or an empty string when the user chose to hide calorie numbers. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun kcalLabel(kcal: Int): String = if (LocalAppSettings.current.hideCalories) "" else "$kcal kcal"
