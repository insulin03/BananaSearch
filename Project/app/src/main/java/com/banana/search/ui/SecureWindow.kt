package com.banana.search.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun SecureDialogWindow(enabled: Boolean = true) {
    if (!enabled) return
    val view = LocalView.current
    DisposableEffect(view) {

        var p: android.view.ViewParent? = view.parent
        var provider: DialogWindowProvider? = null
        while (p != null) {
            if (p is DialogWindowProvider) {
                provider = p
                break
            }
            p = p.parent
        }
        val window = provider?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {

            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
