package com.example.audiary.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Screen readers and the system's Remove animations setting also stop the moving wall. */
@Composable fun motionAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var allowed by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled() && !manager.isTouchExplorationEnabled) }
    DisposableEffect(lifecycle, manager) {
        fun update() { allowed = ValueAnimator.areAnimatorsEnabled() && !manager.isTouchExplorationEnabled }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) update() }
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { update() }
        lifecycle.addObserver(observer)
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { lifecycle.removeObserver(observer); manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return allowed
}
