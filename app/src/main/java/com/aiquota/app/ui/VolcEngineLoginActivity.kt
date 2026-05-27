package com.aiquota.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aiquota.app.R

class VolcEngineLoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DIGEST = "extra_digest"
        const val EXTRA_CSRF_TOKEN = "extra_csrf_token"
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        progressBar = ProgressBar(this).apply { isIndeterminate = true }
        tvHint = TextView(this).apply {
            text = "请登录火山引擎账号，登录成功后返回即可"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
            setPadding(32, 24, 32, 8)
        }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
            addView(tvHint)
            addView(progressBar)
            addView(webView, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(layout)

        title = "火山引擎登录"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = android.view.View.GONE

                val cookies = CookieManager.getInstance().getCookie("https://console.volcengine.com")
                if (cookies != null) {
                    val digest = extractCookie(cookies, "digest")
                    val csrfToken = extractCookie(cookies, "csrfToken")
                    val accountId = extractCookie(cookies, "AccountID")

                    if (!digest.isNullOrEmpty() && !csrfToken.isNullOrEmpty()) {
                        val resultIntent = android.content.Intent().apply {
                            putExtra(EXTRA_DIGEST, digest)
                            putExtra(EXTRA_CSRF_TOKEN, csrfToken)
                            putExtra(EXTRA_ACCOUNT_ID, accountId ?: "")
                        }
                        setResult(RESULT_OK, resultIntent)
                        tvHint.text = "登录成功！请返回继续"
                        tvHint.setTextColor(getColor(R.color.success))
                    }
                }
            }
        }

        CookieManager.getInstance().removeAllCookies(null)
        webView.loadUrl("https://console.volcengine.com/ark/region:ark+cn-beijing/openManagement?LLM=%7B%7D&advancedActiveKey=subscribe")
    }

    private fun extractCookie(cookieString: String, name: String): String? {
        return cookieString.split(";")
            .map { it.trim() }
            .find { it.startsWith("$name=") }
            ?.substringAfter("=")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
