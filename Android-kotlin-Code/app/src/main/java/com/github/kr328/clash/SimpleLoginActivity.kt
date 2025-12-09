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
    private lateinit var testConnectionButton: Button
    private lateinit var quickImportButton: Button
    private lateinit var skipLoginButton: TextView
    
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(com.github.kr328.clash.design.R.layout.activity_simple_login)
            
            // 初始化偏好管理器
            SimplePreferenceManager.init(this)
            
            // 初始化视图
            usernameEditText = findViewById(com.github.kr328.clash.design.R.id.usernameEditText)
            subscribeUrlEditText = findViewById(com.github.kr328.clash.design.R.id.subscribeUrlEditText)
            togglePasswordVisibility = findViewById(com.github.kr328.clash.design.R.id.togglePasswordVisibility)
            loginButton = findViewById(com.github.kr328.clash.design.R.id.loginButton)
            testConnectionButton = findViewById(com.github.kr328.clash.design.R.id.testConnectionButton)
            quickImportButton = findViewById(com.github.kr328.clash.design.R.id.quickImportButton)
            skipLoginButton = findViewById(com.github.kr328.clash.design.R.id.skipLoginButton)

            setupViews()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            // 如果简化登录失败，回退到原登录
            finish()
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun setupViews() {
        // 切换订阅链接可见性
        togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            
            if (isPasswordVisible) {
                subscribeUrlEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePasswordVisibility.setImageResource(R.drawable.visibility_24px)
            } else {
                subscribeUrlEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePasswordVisibility.setImageResource(R.drawable.visibility_off_24px)
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

        // 测试连接按钮
        testConnectionButton.setOnClickListener {
            val subscribeUrl = subscribeUrlEditText.text.toString().trim()
            
            when {
                subscribeUrl.isEmpty() -> {
                    Toast.makeText(this, "请先输入订阅链接", Toast.LENGTH_SHORT).show()
                }
                !isValidSubscribeUrl(subscribeUrl) -> {
                    Toast.makeText(this, "订阅链接格式不正确", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    testConnection(subscribeUrl)
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

    private fun testConnection(url: String) {
        testConnectionButton.isEnabled = false
        testConnectionButton.text = "测试中..."
        
        android.util.Log.d("SimpleLogin", "开始测试订阅连接: $url")

        CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            var stepInfo = ""
            
            try {
                // 步骤1: 解析URL
                stepInfo = "解析URL"
                val urlObj = URL(url)
                android.util.Log.d("SimpleLogin", "✓ URL解析成功: ${urlObj.protocol}://${urlObj.host}")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SimpleLoginActivity,
                        "✓ URL格式正确\n协议: ${urlObj.protocol}\n主机: ${urlObj.host}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // 步骤2: 建立连接
                stepInfo = "建立连接"
                val connection = urlObj.openConnection()
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "ClashForAndroid/UUVPN")
                connection.setRequestProperty("Accept", "*/*")
                
                android.util.Log.d("SimpleLogin", "正在连接到服务器...")
                
                // 步骤3: 获取内容
                stepInfo = "获取订阅内容"
                val content = connection.getInputStream().bufferedReader().use { it.readText() }
                val elapsed = System.currentTimeMillis() - startTime
                
                android.util.Log.d("SimpleLogin", "✓ 成功获取内容，大小: ${content.length} 字节，耗时: ${elapsed}ms")
                
                // 步骤4: 验证内容
                stepInfo = "验证Clash配置"
                val isValid = content.contains("proxies:") || 
                              content.contains("proxy-groups:") ||
                              content.contains("\"proxies\"") ||
                              content.contains("\"proxy-groups\"") ||
                              content.contains("rules:")
                
                withContext(Dispatchers.Main) {
                    if (isValid) {
                        android.util.Log.d("SimpleLogin", "✓ 验证成功: 这是有效的Clash配置")
                        
                        val resultMessage = buildString {
                            append("✅ 测试成功！\n\n")
                            append("📊 详细信息:\n")
                            append("• 连接时间: ${elapsed}ms\n")
                            append("• 内容大小: ${content.length} 字节\n")
                            append("• 配置格式: Clash\n")
                            
                            // 统计代理节点数量
                            val proxyCount = content.lines().count { 
                                it.trim().startsWith("- name:") || it.trim().startsWith("\"name\":")
                            }
                            if (proxyCount > 0) {
                                append("• 节点数量: 约 $proxyCount 个\n")
                            }
                            
                            append("\n可以放心使用此订阅链接！")
                        }
                        
                        android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                            .setTitle("连接测试成功")
                            .setMessage(resultMessage)
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        android.util.Log.w("SimpleLogin", "⚠ 内容不是有效的Clash配置")
                        android.util.Log.w("SimpleLogin", "内容预览: ${content.take(200)}")
                        
                        val preview = content.take(200).replace("<", "&lt;").replace(">", "&gt;")
                        
                        android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                            .setTitle("⚠️ 订阅内容异常")
                            .setMessage(
                                "成功连接到服务器，但返回的内容不是有效的Clash配置文件。\n\n" +
                                "可能的原因:\n" +
                                "1. 这是其他格式的订阅（V2Ray/SS等）\n" +
                                "2. 订阅链接已过期或无效\n" +
                                "3. 服务器返回了错误页面\n\n" +
                                "内容预览:\n$preview..."
                            )
                            .setPositiveButton("确定", null)
                            .show()
                    }
                    
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "🔍 测试订阅连接"
                }
                
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("SimpleLogin", "✗ 域名解析失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                        .setTitle("❌ 域名解析失败")
                        .setMessage(
                            "无法解析订阅链接的域名。\n\n" +
                            "可能的原因:\n" +
                            "1. 网络未连接或DNS服务异常\n" +
                            "2. 域名不存在或已过期\n" +
                            "3. 网络运营商限制访问\n\n" +
                            "建议:\n" +
                            "• 检查网络连接\n" +
                            "• 尝试切换WiFi/移动数据\n" +
                            "• 确认订阅链接是否正确"
                        )
                        .setPositiveButton("确定", null)
                        .show()
                    
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "🔍 测试订阅连接"
                }
            } catch (e: java.net.SocketTimeoutException) {
                val elapsed = System.currentTimeMillis() - startTime
                android.util.Log.e("SimpleLogin", "✗ 连接超时 (${elapsed}ms): ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                        .setTitle("⏱️ 连接超时")
                        .setMessage(
                            "服务器响应超时（已等待 ${elapsed/1000} 秒）。\n\n" +
                            "可能的原因:\n" +
                            "1. 订阅服务器响应过慢\n" +
                            "2. 网络信号不稳定\n" +
                            "3. 服务器可能在国外，需要代理访问\n\n" +
                            "建议:\n" +
                            "• 重试几次\n" +
                            "• 更换网络环境\n" +
                            "• 联系订阅提供商确认服务器状态"
                        )
                        .setPositiveButton("确定", null)
                        .show()
                    
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "🔍 测试订阅连接"
                }
            } catch (e: java.io.IOException) {
                android.util.Log.e("SimpleLogin", "✗ 网络IO错误 (在$stepInfo): ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                        .setTitle("🌐 网络错误")
                        .setMessage(
                            "在${stepInfo}时发生网络错误。\n\n" +
                            "错误信息: ${e.message}\n\n" +
                            "可能的原因:\n" +
                            "1. 网络连接中断\n" +
                            "2. 服务器拒绝连接\n" +
                            "3. 防火墙或代理拦截\n\n" +
                            "建议:\n" +
                            "• 检查网络设置\n" +
                            "• 关闭VPN或代理后重试\n" +
                            "• 确认订阅服务可用"
                        )
                        .setPositiveButton("确定", null)
                        .show()
                    
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "🔍 测试订阅连接"
                }
            } catch (e: Exception) {
                android.util.Log.e("SimpleLogin", "✗ 未知错误: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                        .setTitle("❓ 未知错误")
                        .setMessage(
                            "测试过程中发生了未知错误。\n\n" +
                            "错误信息: ${e.message}\n" +
                            "错误类型: ${e.javaClass.simpleName}\n\n" +
                            "请将此信息反馈给开发者。"
                        )
                        .setPositiveButton("确定", null)
                        .show()
                    
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "🔍 测试订阅连接"
                }
            }
        }
    }

    private fun performLogin(username: String, subscribeUrl: String) {
        loginButton.isEnabled = false
        loginButton.text = "验证订阅中..."
        
        android.util.Log.d("SimpleLogin", "开始验证订阅链接: $subscribeUrl")

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
                connection.connectTimeout = 30000  // 增加到30秒
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "ClashForAndroid/UUVPN")
                connection.setRequestProperty("Accept", "*/*")
                
                // 连接并获取内容
                val content = connection.getInputStream().bufferedReader().use { it.readText() }
                
                // 验证是否是有效的Clash配置
                val isValid = content.contains("proxies:") || 
                              content.contains("proxy-groups:") ||
                              content.contains("\"proxies\"") ||
                              content.contains("\"proxy-groups\"") ||
                              content.contains("rules:")
                
                if (!isValid) {
                    android.util.Log.e("SimpleLogin", "订阅内容验证失败，内容前100字符: ${content.take(100)}")
                }
                
                isValid
            }
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("SimpleLogin", "域名解析失败: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SimpleLoginActivity,
                    "网络错误：域名无法解析，请检查网络连接",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("SimpleLogin", "连接超时: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SimpleLoginActivity,
                    "连接超时：订阅服务器响应太慢",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        } catch (e: java.io.IOException) {
            android.util.Log.e("SimpleLogin", "网络IO错误: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SimpleLoginActivity,
                    "网络错误：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("SimpleLogin", "订阅验证失败: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SimpleLoginActivity,
                    "验证失败：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
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
