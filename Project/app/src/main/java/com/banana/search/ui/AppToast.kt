package com.banana.search.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

object AppToast {

    private var current: java.lang.ref.WeakReference<View>? = null

    fun show(context: Context, text: String) {
        val activity = context as? Activity

        val root = activity?.window?.decorView as? ViewGroup
        if (activity == null || root == null) {

            com.banana.search.core.AppLog.w("AppToast", "无前台窗口，提示未显示：$text")
            return
        }
        activity.runOnUiThread { attach(activity, root, text) }
    }

    private fun attach(activity: Activity, root: ViewGroup, text: String) {
        current?.get()?.let { old -> (old.parent as? ViewGroup)?.removeView(old) }
        current = null

        val density = activity.resources.displayMetrics.density
        val tv = TextView(activity).apply {
            this.text = text
            setTextColor(0xFFF5F1E4.toInt())
            textSize = 12.5f
            maxLines = 2
            gravity = Gravity.CENTER
            val h = (14f * density).toInt()
            val v = (9f * density).toInt()
            setPadding(h, v, h, v)
            background = GradientDrawable().apply {
                cornerRadius = 15f * density
                setColor(0xF2201B10.toInt())
                setStroke((1f * density).toInt().coerceAtLeast(1), 0x55FFD35C)
            }
            alpha = 0f
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = (96f * density).toInt() }

        runCatching { root.addView(tv, lp) }.onFailure {
            com.banana.search.core.AppLog.w("AppToast", "提示挂载失败：$text")
            return
        }
        current = java.lang.ref.WeakReference(tv)
        fade(activity, tv) { runCatching { root.removeView(tv) } }
    }

    private fun fade(activity: Activity, view: View, onGone: () -> Unit) {
        view.animate().alpha(1f).setDuration(110).withEndAction {
            view.postDelayed({
                view.animate().alpha(0f).setDuration(160).withEndAction {
                    if (current?.get() === view) current = null
                    onGone()
                }.start()
            }, 1600)
        }.start()
    }
}
