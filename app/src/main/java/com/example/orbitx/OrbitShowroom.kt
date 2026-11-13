package com.example.orbitx

import android.animation.ValueAnimator
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun rememberShowroomDrawable(): AnimatedImageDrawable? {
    val resources = LocalContext.current.resources
    var spin by remember(resources) { mutableStateOf<AnimatedImageDrawable?>(null) }

    LaunchedEffect(resources) {
        spin = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, R.drawable.orbit_ioniq5_spin))
                    as? AnimatedImageDrawable
            } catch (_: IOException) {
                null
            }
        }
    }
    return spin
}

@Composable
internal fun VehicleHeroImage(drawable: AnimatedImageDrawable?, parked: Boolean, modifier: Modifier) {
    if (drawable != null) {
        val animate = parked && ValueAnimator.areAnimatorsEnabled()
        AndroidView(
            modifier = modifier,
            factory = { context ->
                ImageView(context).apply {
                    contentDescription = "아이오닉 5 360도 외관"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable)
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                }
            },
            update = {
                if (animate && !drawable.isRunning) drawable.start()
                if (!animate && drawable.isRunning) drawable.stop()
            },
            onRelease = { view ->
                drawable.stop()
                view.setImageDrawable(null)
            },
        )
    }
}
