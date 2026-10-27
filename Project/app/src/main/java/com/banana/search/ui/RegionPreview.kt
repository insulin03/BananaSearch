package com.banana.search.ui

import android.graphics.Bitmap
import android.graphics.Rect

data class RegionPreview(

    val crop: Bitmap,

    val region: Rect,

    val screenW: Int,
    val screenH: Int,

    val shotW: Int,
    val shotH: Int,
) {
    val cropW: Int get() = crop.width
    val cropH: Int get() = crop.height
}
