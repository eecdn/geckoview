package com.litebrowser.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.HistoryDelegate
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError

/**
 * 精简浏览器主界面
 * 基于 GeckoView 内核，支持多标签、书签、历史记录
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private var runtime: GeckoRuntime? = null
    }

    // UI 组件
    private lateinit var geckoView: GeckoView
    private lateinit var urlInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var securityIcon: ImageView
    private lateinit var tabBar: LinearLayout
    private lateinit var tabBarScroll: android.widget.HorizontalScrollView
    private lateinit var tabDivider: View

    // 核心组件
    private lateinit var tabManager: TabManager
    private lateinit var database: BrowserDatabase

    // 状态
    private var isUrlBarFocused = false
    private var currentUrl = ""
    private var isBookmarked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 UI
        initViews()
        initDatabase()

        // 初始化 GeckoRuntime（全局唯一）
        initRuntime()

        // 初始化标签管理器
        tabManager = TabManager(this)

        // 处理外部打开的 URL
        val startUrl = getStartUrl(intent)

        // 创建第一个标签页
        val tab = tabManager.createTab(runtime!!, startUrl)
        setupSession(tab)
        geckoView.setSession(tab.session)
        tabManager.switchToTab(tab.id)

        // 设置事件监听
        setupListeners()

        // 更新UI
        updateNavigationButtons()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            val url = getStartUrl(it)
            if (url.isNotEmpty() && URLUtil.isNetworkUrl(url)) {
                loadUrl(url)
            }
        }
    }

    private fun initViews() {
        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.url_input)
        progressBar = findViewById(R.id.progress_bar)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnBookmark = findViewById(R.id.btn_bookmark)
        btnMenu = findViewById(R.id.btn_menu)
        securityIcon = findViewById(R.id.url_security_icon)
        tabBar = findViewById(R.id.tab_bar)
        tabBarScroll = findViewById(R.id.tab_bar_scroll)
        tabDivider = findViewById(R.id.tab_divider)
    }

    private fun initDatabase() {
        database = BrowserDatabase(this)
    }

    private fun initRuntime() {
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .contentBlocking(
                    ContentBlocking.Settings.Builder()
                        .categories(ContentBlocking.CAT_ALL)
                        .build()
                )
                .build()

            runtime = GeckoRuntime.create(this, settings)
        }
    }

    private fun getStartUrl(intent: Intent): String {
        if (intent.data != null) {
            return intent.data.toString()
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            return intent.data.toString()
        }
        return getString(R.string.default_homepage)
    }

    /**
     * 为标签页设置所有必要的 Delegate
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSession(tab: TabData) {
        tab.session.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val t = title ?: ""
                tabManager.updateTabTitle(tab.id, t)
                if (tabManager.getActiveTab()?.id == tab.id) {
                    runOnUiThread {
                        if (!isUrlBarFocused) {
                            urlInput.setText(t)
                        }
                        updateTabBar()
                    }
                }
            }

            override fun onContextMenu(session: GeckoSession, element: GeckoSession.ContentDelegate.ContextElement, context: Int, elementType: Int, uri: String?) {
                // 不处理上下文菜单
            }
        }

        tab.session.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String?) {
                runOnUiThread {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                    currentUrl = url ?: ""
                    if (!isUrlBarFocused) {
                        urlInput.setText(currentUrl)
                    }
                    updateSecurityIcon(currentUrl)
                    checkBookmarkStatus(currentUrl)
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    updateNavigationButtons()
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runOnUiThread {
                    progressBar.progress = progress
                    if (progress >= 100) {
                        progressBar.visibility = View.GONE
                    }
                }
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInfo) {
                runOnUiThread {
                    updateSecurityIcon(currentUrl, securityInfo.isSecure)
                }
            }
        }

        tab.session.navigationDelegate = object : NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                return GeckoResult.ALLOW
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                url?.let {
                    currentUrl = it
                    tabManager.updateTabUrl(tab.id, it)
                    runOnUiThread {
                        if (!isUrlBarFocused) {
                            urlInput.setText(it)
                        }
                        updateSecurityIcon(it)
                        checkBookmarkStatus(it)
                    }
                }
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String> {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "加载失败: ${error.category}", Toast.LENGTH_SHORT).show()
                }
                return GeckoResult(null)
            }
        }

        tab.session.historyDelegate = object : HistoryDelegate {
            override fun onHistoryStateChange(session: GeckoSession, historyList: GeckoSession.HistoryDelegate.HistoryList) {
                // 可选：处理历史状态变化
            }
        }

        tab.session.permissionDelegate = object : PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: PermissionDelegate.ContentPermission
            ): GeckoResult<Int> {
                // 自动允许所有权限请求（精简版策略）
                return GeckoResult.valueOf(PermissionDelegate.PERMISSION_ALLOW)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                perm: PermissionDelegate.MediaPermission
            ): GeckoResult<Int> {
                return GeckoResult.valueOf(PermissionDelegate.PERMISSION_ALLOW)
            }
        }
    }

    private fun setupListeners() {
        // 地址栏回车加载
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEND) {
                loadUrlFromInput()
                true
            } else {
                false
            }
        }

        urlInput.setOnFocusChangeListener { _, hasFocus ->
            isUrlBarFocused = hasFocus
            if (hasFocus) {
                urlInput.selectAll()
            }
        }

        // 返回按钮
        btnBack.setOnClickListener {
            tabManager.getActiveTab()?.session?.goBack()
        }

        // 前进按钮
        btnForward.setOnClickListener {
            tabManager.getActiveTab()?.session?.goForward()
        }

        // 书签按钮
        btnBookmark.setOnClickListener {
            toggleBookmark()
        }

        // 菜单按钮
        btnMenu.setOnClickListener {
            showPopupMenu(it)
        }
    }

    /**
     * 从地址栏输入加载URL
     */
    private fun loadUrlFromInput() {
        val input = urlInput.text.toString().trim()
        if (input.isEmpty()) return

        val url = processInput(input)
        loadUrl(url)
        urlInput.clearFocus()
    }

    /**
     * 处理用户输入 - 判断是URL还是搜索关键词
     */
    private fun processInput(input: String): String {
        val trimmed = input.trim()

        // 已经是完整URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("about:")) {
            return trimmed
        }

        // 看起来像域名
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        // 否则当作搜索
        return getString(R.string.search_engine_url,
            java.net.URLEncoder.encode(trimmed, "UTF-8"))
    }

    /**
     * 加载URL
     */
    private fun loadUrl(url: String) {
        val tab = tabManager.getActiveTab() ?: return
        tab.session.loadUri(url)
        currentUrl = url
        urlInput.setText(url)
    }

    /**
     * 更新导航按钮状态
     */
    private fun updateNavigationButtons() {
        val tab = tabManager.getActiveTab() ?: return
        tab.session.canGoBack().then { canGoBack ->
            runOnUiThread {
                btnBack.alpha = if (canGoBack) 1.0f else 0.3f
                btnBack.isEnabled = canGoBack
            }
            GeckoResult<Void>()
        }
        tab.session.canGoForward().then { canGoForward ->
            runOnUiThread {
                btnForward.alpha = if (canGoForward) 1.0f else 0.3f
                btnForward.isEnabled = canGoForward
            }
            GeckoResult<Void>()
        }
    }

    /**
     * 更新安全图标
     */
    private fun updateSecurityIcon(url: String, isSecure: Boolean = false) {
        if (url.startsWith("https://")) {
            securityIcon.visibility = View.VISIBLE
            securityIcon.setImageResource(if (isSecure) R.drawable.ic_lock else R.drawable.ic_globe)
        } else if (url.startsWith("http://")) {
            securityIcon.visibility = View.VISIBLE
            securityIcon.setImageResource(R.drawable.ic_globe)
        } else {
            securityIcon.visibility = View.GONE
        }
    }

    /**
     * 检查书签状态
     */
    private fun checkBookmarkStatus(url: String) {
        if (!URLUtil.isNetworkUrl(url)) return
        lifecycleScope.launch(Dispatchers.IO) {
            isBookmarked = database.isBookmarked(url)
            launch(Dispatchers.Main) {
                btnBookmark.setImageResource(
                    if (isBookmarked) R.drawable.ic_bookmark_filled
                    else R.drawable.ic_bookmark_outline
                )
            }
        }
    }

    /**
     * 切换书签状态
     */
    private fun toggleBookmark() {
        val url = currentUrl
        if (!URLUtil.isNetworkUrl(url)) return

        val tab = tabManager.getActiveTab()
        val title = tab?.title ?: url

        lifecycleScope.launch {
            if (isBookmarked) {
                database.removeBookmarkByUrl(url)
                isBookmarked = false
                btnBookmark.setImageResource(R.drawable.ic_bookmark_outline)
                Toast.makeText(this@MainActivity, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
            } else {
                database.addBookmark(title, url)
                isBookmarked = true
                btnBookmark.setImageResource(R.drawable.ic_bookmark_filled)
                Toast.makeText(this@MainActivity, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 显示弹出菜单
     */
    private fun showPopupMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.findViewById<TextView>(R.id.menu_new_tab).setOnClickListener {
            popupWindow.dismiss()
            openNewTab()
        }

        popupView.findViewById<TextView>(R.id.menu_bookmarks).setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, BookmarkActivity::class.java))
        }

        popupView.findViewById<TextView>(R.id.menu_history).setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        popupView.findViewById<TextView>(R.id.menu_refresh).setOnClickListener {
            popupWindow.dismiss()
            tabManager.getActiveTab()?.session?.reload()
        }

        popupWindow.showAsDropDown(anchor)
    }

    /**
     * 打开新标签页
     */
    private fun openNewTab(url: String = "") {
        val tab = tabManager.createTab(runtime!!, url)
        setupSession(tab)
        geckoView.releaseSession()
        geckoView.setSession(tab.session)
        tabManager.switchToTab(tab.id)
        updateTabBar()
        updateNavigationButtons()

        if (url.isEmpty()) {
            urlInput.text.clear()
            urlInput.requestFocus()
        }
    }

    /**
     * 更新标签栏UI
     */
    private fun updateTabBar() {
        val tabs = tabManager.getAllTabs()
        val activeTab = tabManager.getActiveTab()

        tabBar.removeAllViews()

        if (tabs.size <= 1) {
            tabBarScroll.visibility = View.GONE
            tabDivider.visibility = View.GONE
            return
        }

        tabBarScroll.visibility = View.VISIBLE
        tabDivider.visibility = View.VISIBLE

        for (tab in tabs) {
            val tabView = LayoutInflater.from(this)
                .inflate(R.layout.tab_item, tabBar, false) as LinearLayout

            val titleView = tabView.findViewById<TextView>(R.id.tab_title)
            val closeBtn = tabView.findViewById<ImageButton>(R.id.tab_close)

            titleView.text = tab.title.ifEmpty { "标签" }

            val isActive = tab.id == activeTab?.id
            tabView.setBackgroundResource(
                if (isActive) R.color.tab_active else R.color.tab_inactive
            )
            titleView.setTextColor(
                if (isActive) getColor(R.color.tab_text_active)
                else getColor(R.color.tab_text_inactive)
            )

            tabView.setOnClickListener {
                if (tab.id != activeTab?.id) {
                    switchTab(tab.id)
                }
            }

            closeBtn.setOnClickListener {
                closeTab(tab.id)
            }

            tabBar.addView(tabView)
        }
    }

    /**
     * 切换标签页
     */
    private fun switchTab(tabId: Int) {
        val tab = tabManager.switchToTab(tabId) ?: return
        geckoView.releaseSession()
        geckoView.setSession(tab.session)
        currentUrl = tab.url
        urlInput.setText(tab.url)
        updateTabBar()
        updateNavigationButtons()
        checkBookmarkStatus(tab.url)
    }

    /**
     * 关闭标签页
     */
    private fun closeTab(tabId: Int) {
        val newActiveTab = tabManager.closeTab(tabId) ?: return

        if (tabManager.getTabCount() == 0) {
            // 没有标签了，创建一个新的
            val tab = tabManager.createTab(runtime!!)
            setupSession(tab)
            geckoView.setSession(tab.session)
            tabManager.switchToTab(tab.id)
            urlInput.text.clear()
        } else {
            geckoView.releaseSession()
            geckoView.setSession(newActiveTab.session)
            currentUrl = newActiveTab.url
            urlInput.setText(newActiveTab.url)
            checkBookmarkStatus(newActiveTab.url)
        }

        updateTabBar()
        updateNavigationButtons()
    }

    override fun onBackPressed() {
        val tab = tabManager.getActiveTab()
        if (tab != null && tab.session.canGoBack()) {
            tab.session.goBack()
        } else if (tabManager.getTabCount() > 1) {
            closeTab(tab!!.id)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理所有标签页
        tabManager.getAllTabs().forEach { it.session.close() }
    }
}
