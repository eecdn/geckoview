package com.litebrowser.app

/**
 * 标签页数据类
 */
data class TabData(
    val id: Int,
    val session: GeckoSession,
    var title: String = "新建标签",
    var url: String = "",
    var isBookmarked: Boolean = false
)
