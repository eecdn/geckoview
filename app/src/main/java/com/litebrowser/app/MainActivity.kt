package com.litebrowser.app

import android.annotation.SuppressLint
import android.content.Intent
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
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    companion object {
        private var runtime: GeckoRuntime? = null
    }

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

    private lateinit var tabManager: TabManager
    private lateinit var database: BrowserDatabase

    private var isUrlBarFocused = false
    private var currentUrl = ""
    private var isBookmarked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initDatabase()
        initRuntime()

        tabManager = TabManager(this)
        val startUrl = getStartUrl(intent)

        val tab = tabManager.createTab(runtime!!, startUrl)
        setupSession(tab)
        geckoView.setSession(tab.session)
        tabManager.switchToTab(tab.id)

        setupListeners()
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
                        .enhancedTrackingProtection(ContentBlocking.EtpLevel.STRICT)
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
                request: NavigationDelegate.LoadRequest
            ): GeckoResult<String>? {
                return GeckoResult.fromValue(null)
            }

            override fun onLocationChange(session: GeckoSession, url: String?) {
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
                error: Int,
                category: Int
            ): GeckoResult<String>? {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "加载失败: $uri", Toast.LENGTH_SHORT).show()
                }
                return GeckoResult.fromValue(null)
            }
        }

        tab.session.permissionDelegate = object : PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                return GeckoResult.fromValue(PermissionDelegate.PERMISSION_ALLOW)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                perm: PermissionDelegate.MediaPermission
            ): GeckoResult<Int>? {
                return GeckoResult.fromValue(PermissionDelegate.PERMISSION_ALLOW)
            }
        }
    }

    private fun setupListeners() {
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

        btnBack.setOnClickListener {
            tabManager.getActiveTab()?.session?.goBack()
        }

        btnForward.setOnClickListener {
            tabManager.getActiveTab()?.session?.goForward()
        }

        btnBookmark.setOnClickListener {
            toggleBookmark()
        }

        btnMenu.setOnClickListener {
            showPopupMenu(it)
        }
    }

    private fun loadUrlFromInput() {
        val input = urlInput.text.toString().trim()
        if (input.isEmpty()) return
        val url = processInput(input)
        loadUrl(url)
        urlInput.clearFocus()
    }

    private fun processInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("about:")) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }
        return getString(R.string.search_engine_url,
            java.net.URLEncoder.encode(trimmed, "UTF-8"))
    }

    private fun loadUrl(url: String) {
        val tab = tabManager.getActiveTab() ?: return
        tab.session.loadUri(url)
        currentUrl = url
        urlInput.setText(url)
    }

    private fun updateNavigationButtons() {
        val tab = tabManager.getActiveTab() ?: return
        tab.session.canGoBack().then { canGoBack ->
            runOnUiThread {
                btnBack.alpha = if (canGoBack != null && canGoBack) 1.0f else 0.3f
                btnBack.isEnabled = (canGoBack != null && canGoBack)
            }
            GeckoResult<Void>()
        }
        tab.session.canGoForward().then { canGoForward ->
            runOnUiThread {
                btnForward.alpha = if (canGoForward != null && canGoForward) 1.0f else 0.3f
                btnForward.isEnabled = (canGoForward != null && canGoForward)
            }
            GeckoResult<Void>()
        }
    }

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

    private fun closeTab(tabId: Int) {
        val newActiveTab = tabManager.closeTab(tabId) ?: return

        if (tabManager.getTabCount() == 0) {
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

    @Suppress("DEPRECATION")
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
        tabManager.getAllTabs().forEach { it.session.close() }
    }
}
