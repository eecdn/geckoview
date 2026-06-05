package com.litebrowser.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 多标签管理器 - 管理所有标签页的创建、切换、关闭
 */
class TabManager(private val context: Context) {

    private val tabs = mutableListOf<TabData>()
    private var nextId = 0
    private var activeTabId = -1

    private val _activeTab = MutableStateFlow<TabData?>(null)
    val activeTab: StateFlow<TabData?> = _activeTab.asStateFlow()

    private val _tabCount = MutableStateFlow(0)
    val tabCount: StateFlow<Int> = _tabCount.asStateFlow()

    private val _showTabBar = MutableStateFlow(false)
    val showTabBar: StateFlow<Boolean> = _showTabBar.asStateFlow()

    /**
     * 创建新标签页
     */
    fun createTab(runtime: GeckoRuntime, url: String = ""): TabData {
        val session = GeckoSession()
        session.open(runtime)
        val tab = TabData(id = nextId++, session = session, url = url)
        tabs.add(tab)
        _tabCount.value = tabs.size

        if (url.isNotEmpty()) {
            session.loadUri(url)
        }

        if (tabs.size > 1) {
            _showTabBar.value = true
        }

        return tab
    }

    /**
     * 切换到指定标签页
     */
    fun switchToTab(tabId: Int): TabData? {
        val tab = tabs.find { it.id == tabId } ?: return null
        activeTabId = tab.id
        _activeTab.value = tab
        updateTabBarUI()
        return tab
    }

    /**
     * 关闭指定标签页
     * @return 关闭后应切换到的标签页，如果无标签则返回null
     */
    fun closeTab(tabId: Int): TabData? {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return null

        val closedTab = tabs.removeAt(index)
        closedTab.session.close()

        _tabCount.value = tabs.size

        if (tabs.size <= 1) {
            _showTabBar.value = false
        }

        // 如果关闭的是当前标签，切换到相邻标签
        return if (tabId == activeTabId) {
            if (tabs.isEmpty()) {
                _activeTab.value = null
                null
            } else {
                val newIndex = index.coerceAtMost(tabs.size - 1)
                switchToTab(tabs[newIndex].id)
            }
        } else {
            _activeTab.value
        }
    }

    /**
     * 获取当前活动标签
     */
    fun getActiveTab(): TabData? = tabs.find { it.id == activeTabId }

    /**
     * 获取所有标签
     */
    fun getAllTabs(): List<TabData> = tabs.toList()

    /**
     * 更新标签标题
     */
    fun updateTabTitle(tabId: Int, title: String) {
        tabs.find { it.id == tabId }?.title = title
        updateTabBarUI()
    }

    /**
     * 更新标签URL
     */
    fun updateTabUrl(tabId: Int, url: String) {
        tabs.find { it.id == tabId }?.url = url
    }

    /**
     * 更新标签栏UI
     */
    private fun updateTabBarUI() {
        // UI更新由MainActivity处理
    }

    /**
     * 获取标签数量
     */
    fun getTabCount(): Int = tabs.size
}
