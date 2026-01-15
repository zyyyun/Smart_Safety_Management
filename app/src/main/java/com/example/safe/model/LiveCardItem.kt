package com.example.safe

import androidx.annotation.DrawableRes

data class LiveCardItem(
    val camId: String,
    val place: String,
    val tags: List<String>,
    @DrawableRes val thumbRes: Int,
    val location: String,
    @DrawableRes val overviewThumb: Int,
    @DrawableRes val siteThumb: Int,
    val captureThumbs: List<Int>,
    val isLive: Boolean
)
