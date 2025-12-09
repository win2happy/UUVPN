package com.github.kr328.clash

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SimplePreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 简化的登录界面 - 不依赖V2Board
 * 支持:
 * 1. 用户名 + 订阅链接登录
 * 2. 直接导入订阅链接
 */
class SimpleLoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var subscribeUrlEditText: EditText
    private lateinit var togglePasswordVisibility: ImageButton
    private lateinit var loginButton: Button
    private lateinit var quickImportButton: Button
    private lateinit var skipLoginButton: TextView
    
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.github.kr328.clash.design.R.layout.activity_simple_login)
        
        // 初始化视图
        usernameEditText = findViewById(com.github.kr328.clash.design.R.id.usernameEditText)
        subscribeUrlEditText = findViewById(com.github.kr328.clash.design.R.id.subscribeUrlEditText)
        togglePasswordVisibility = findViewById(com.github.kr328.clash.design.R.id.togglePasswordVisibility)
        loginButton = findViewById(com.github.kr328.clash.design.R.id.loginButton)
        quickImportButton = findViewById(com.github.kr328.clash.design.R.id.quickImportButton)
        skipLoginButton = findViewById(com.github.kr328.clash.design.R.id.skipLoginButton)

        SimplePreferenceManager.init(this)

        setupViews()
    }

    private fun setupViews() {
        // 切换订阅链接可见性
        togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            
            if (isPasswordVisible) {
                subscribeUrlEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePasswordVisibility.setImageResource(com.github.kr328.clash.design.R.drawable.visibility_24px)
            } else {
                subscribeUrlEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePasswordVisibility.setImageResource(com.github.kr328.clash.design.R.drawable.visibility_off_24px)
            }
            subscribeUrlEditText.setSelection(subscribeUrlEditText.text.length)
        }

        // 登录按钮
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val subscribeUrl = subscribeUrlEditText.text.toString().trim()

            when {
                username.isEmpty() -> {
                    Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                }
                subscribeUrl.isEmpty() -> {
                    Toast.makeText(this, "请输入订阅链接", Toast.LENGTH_SHORT).show()
                }
                !isValidSubscribeUrl(subscribeUrl) -> {
                    Toast.makeText(this, "订阅链接格式不正确", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    performLogin(username, subscribeUrl)
                }
            }
        }

        // 快速导入按钮 - 从剪贴板导入
        quickImportButton.setOnClickListener {
            importFromClipboard()
        }

        // 跳过登录 - 以访客模式使用
        skipLoginButton.setOnClickListener {
            Toast.makeText(this, "访客模式需要先导入订阅链接", Toast.LENGTH_LONG).show()
        }
    }

    private fun isValidSubscribeUrl(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            urlObj.protocol == "http" || urlObj.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    private fun performLogin(username: String, subscribeUrl: String) {
        loginButton.isEnabled = false
        loginButton.text = "验证订阅中..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 验证订阅链接是否可访问
                val isValid = validateSubscription(subscribeUrl)
                
                withContext(Dispatchers.Main) {
                    if (isValid) {
                        // 保存登录信息
                        SimplePreferenceManager.login(username, subscribeUrl)
                        
                        Toast.makeText(
                            this@SimpleLoginActivity,
                            "登录成功!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 跳转到主界面
                        val intent = Intent(this@SimpleLoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@SimpleLoginActivity,
                            "订阅链接无法访问，请检查网络或链接是否正确",
                            Toast.LENGTH_LONG
                        ).show()
                        loginButton.isEnabled = true
                        loginButton.text = "登录"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SimpleLoginActivity,
                        "登录失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    loginButton.isEnabled = true
                    loginButton.text = "登录"
                }
            }
        }
    }

    private suspend fun validateSubscription(url: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()
                
                val content = connection.getInputStream().bufferedReader().use { it.readText() }
                
                // 简单验证是否是Clash配置
                content.contains("proxies:") || 
                content.contains("proxy-groups:") ||
                content.contains("\"proxies\"") ||
                content.contains("\"proxy-groups\"")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun importFromClipboard() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                
                if (isValidSubscribeUrl(text)) {
                    subscribeUrlEditText.setText(text)
                    Toast.makeText(this, "已从剪贴板导入订阅链接", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "剪贴板中没有有效的订阅链接", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取剪贴板失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        // 禁用返回键
    }
}
