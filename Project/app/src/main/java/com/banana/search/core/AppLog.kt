package com.banana.search.core

import android.content.Context
import android.util.Log
import com.banana.search.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {

    private const val TAG = "QSearch"
    private const val CAPACITY = 300

    private val buffer = ArrayDeque<String>(CAPACITY)
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    @Volatile
    var debugEnabled: Boolean = BuildConfig.DEBUG

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        i("AppLog", "logger ready")
    }

    fun i(tag: String, msg: String) {
        if (debugEnabled) Log.i(TAG, "[$tag] $msg")
        push("I/$tag", msg)
    }

    fun w(tag: String, msg: String) {
        if (debugEnabled) Log.w(TAG, "[$tag] $msg")
        push("W/$tag", msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (debugEnabled) Log.e(TAG, "[$tag] $msg", t)
        push("E/$tag", msg + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    @Synchronized
    private fun push(level: String, msg: String) {
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        buffer.addLast("${fmt.format(Date())} $level $msg")
    }

    @Synchronized
    fun dump(): String = buffer.joinToString("\n")

    @Synchronized
    fun clear() = buffer.clear()
}
