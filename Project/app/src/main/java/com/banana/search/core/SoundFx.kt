package com.banana.search.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

internal object SoundFx {
    private var pool: SoundPool? = null
    private var id = 0

    @Volatile
    private var enabled = true

    @Volatile
    private var ready = false

    fun install(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        p.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        id = p.load(ctx.applicationContext, com.banana.search.R.raw.click, 1)
        pool = p
    }

    fun setEnabled(v: Boolean) {
        enabled = v
    }

    fun play() {
        if (!enabled || !ready) return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }
}
