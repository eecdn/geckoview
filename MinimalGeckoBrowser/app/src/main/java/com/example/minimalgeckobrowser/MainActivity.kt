package com.example.minimalgeckobrowser

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private lateinit var geckoRuntime: GeckoRuntime

    private lateinit var etUrl: EditText
    private lateinit var btnGo: Button
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var geckoViewContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews()

        // 初始化 GeckoView
        initGeckoView()

        // 设置按钮点击事件
        setupButtonListeners()

        // 加载默认页面
        loadUrl("https://hao.pcpos.cn")
    }

    private fun initViews() {
        etUrl = findViewById(R.id.etUrl)
        btnGo = findViewById(R.id.btnGo)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)
        geckoViewContainer = findViewById(R.id.geckoViewContainer)

        // 设置地址栏键盘事件
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrlFromEditText()
                true
            } else {
                false
            }
        }
    }

    private fun initGeckoView() {
        // 创建或获取 GeckoRuntime（单例）
        geckoRuntime = GeckoRuntime.getDefault(this)

        // 创建 GeckoSession
        geckoSession = GeckoSession()

        // 设置会话委托
        geckoSession.progressDelegate = createProgressDelegate()
        geckoSession.navigationDelegate = createNavigationDelegate()

        // 打开会话
        geckoSession.open(geckoRuntime)

        // 创建 GeckoView 并添加到容器
        geckoView = GeckoView(this)
        geckoViewContainer.addView(geckoView)
        geckoView.setSession(geckoSession)
    }

    private fun setupButtonListeners() {
        btnGo.setOnClickListener {
            loadUrlFromEditText()
        }

        btnBack.setOnClickListener {
            if (geckoSession.canGoBack) {
                geckoSession.goBack()
            }
        }

        btnForward.setOnClickListener {
            if (geckoSession.canGoForward) {
                geckoSession.goForward()
            }
        }

        btnRefresh.setOnClickListener {
            geckoSession.reload()
        }
    }

    private fun loadUrlFromEditText() {
        var url = etUrl.text.toString().trim()
        if (url.isNotEmpty()) {
            // 如果没有协议前缀，添加 https://
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            loadUrl(url)
        }
    }

    private fun loadUrl(url: String) {
        geckoSession.loadUri(url)
        etUrl.setText(url)
    }

    private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
        return object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                runOnUiThread {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
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
                }
            }
        }
    }

    private fun createNavigationDelegate(): GeckoSession.NavigationDelegate {
        return object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permits: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                runOnUiThread {
                    url?.let {
                        etUrl.setText(it)
                    }
                    updateNavigationButtons()
                }
            }
        }
    }

    private fun updateNavigationButtons() {
        btnBack.isEnabled = geckoSession.canGoBack
        btnForward.isEnabled = geckoSession.canGoForward
    }

    override fun onBackPressed() {
        if (geckoSession.canGoBack) {
            geckoSession.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        geckoSession.close()
    }
}
