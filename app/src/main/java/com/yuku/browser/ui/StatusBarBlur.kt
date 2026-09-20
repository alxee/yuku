package com.yuku.browser.ui

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/** Filter the AndroidView's normal rendering without recording/replaying its
 * children into additional RenderNodes. Replaying the live WebView at several
 * scales in one frame introduced page-wide flicker on the user's device.
 */
@Composable
internal fun Modifier.statusBarBlur(enabled: Boolean, stripPx: Float): Modifier {
    if (!enabled || stripPx <= 0f || Build.VERSION.SDK_INT < 31) return this
    val density = LocalDensity.current.density
    val effect = remember(stripPx, density) {
        val mask = RenderEffect.createShaderEffect(
            LinearGradient(
                0f, 0f, 0f, stripPx,
                StatusBarEffectStops.map { position ->
                    (255f * statusBarEffectStrength(position)).roundToInt() shl 24
                }.toIntArray(),
                StatusBarEffectStops,
                Shader.TileMode.CLAMP,
            ),
        )
        val source = RenderEffect.createOffsetEffect(0f, 0f)
        val sharp = RenderEffect.createBlendModeEffect(source, mask, BlendMode.DST_OUT)
        val blurred = RenderEffect.createBlurEffect(18f * density, 18f * density, Shader.TileMode.CLAMP)
        val soft = RenderEffect.createBlendModeEffect(blurred, mask, BlendMode.DST_IN)
        RenderEffect.createBlendModeEffect(sharp, soft, BlendMode.PLUS).asComposeRenderEffect()
    }
    return graphicsLayer { renderEffect = effect }
}
