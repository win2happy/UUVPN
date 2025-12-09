package com.github.kr328.clash

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.PreferenceManager
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
    private lateinit var subscriptionTypeGroup: RadioGroup
    private lateinit var radioV2ray: RadioButton
    private lateinit var radioClash: RadioButton
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
            subscriptionTypeGroup = findViewById(com.github.kr328.clash.design.R.id.subscriptionTypeGroup)
            radioClash = findViewById(com.github.kr328.clash.design.R.id.radioClash)
            radioV2ray = findViewById(com.github.kr328.clash.design.R.id.radioV2ray)
            
            // 初始化PreferenceManager
            PreferenceManager.init(this)
            
            // 设置默认选中的订阅类型
            val savedType = PreferenceManager.subscriptionType
            when (savedType) {
                PreferenceManager.SUBSCRIPTION_TYPE_V2RAY -> radioV2ray.isChecked = true
                PreferenceManager.SUBSCRIPTION_TYPE_CLASH -> radioClash.isChecked = true
                else -> radioV2ray.isChecked = true
            }

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
        
        // 订阅类型选择
        subscriptionTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedType = when (checkedId) {
                radioClash.id -> "clash"
                radioV2ray.id -> "v2ray"
                else -> "clash"
            }
            
            android.util.Log.d("SimpleLogin", "选择的订阅类型: $selectedType")
            Toast.makeText(this, "已选择订阅类型: $selectedType", Toast.LENGTH_SHORT).show()
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

    private fun getSubscriptionTypeName(type: com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType): String {
        return when (type) {
            com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.CLASH -> "Clash"
            com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.V2RAY_BASE64 -> "V2Ray"
            com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.V2RAY_JSON -> "V2Ray JSON"
            com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.SHADOWSOCKS -> "Shadowsocks"
            com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.UNKNOWN -> "未知"
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
                val originalContent = connection.getInputStream().bufferedReader().use { it.readText() }
                val elapsed = System.currentTimeMillis() - startTime
                
                android.util.Log.d("SimpleLogin", "✓ 成功获取内容，大小: ${originalContent.length} 字节，耗时: ${elapsed}ms")
                
                // 步骤4: 检测订阅类型
                stepInfo = "检测订阅类型"
                val subscriptionType = com.github.kr328.clash.design.util.SubscriptionConverter.detectSubscriptionType(originalContent)
                android.util.Log.d("SimpleLogin", "检测到订阅类型: $subscriptionType")
                
                // 步骤5: 转换（如果需要）
                var finalContent = originalContent
                var convertedFrom: String? = null
                
                if (subscriptionType != com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.CLASH) {
                    stepInfo = "转换订阅格式"
                    android.util.Log.d("SimpleLogin", "开始自动转换订阅...")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SimpleLoginActivity,
                            "检测到 ${getSubscriptionTypeName(subscriptionType)} 格式，正在自动转换...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    try {
                        finalContent = com.github.kr328.clash.design.util.SubscriptionConverter.convertToClash(originalContent)
                        convertedFrom = getSubscriptionTypeName(subscriptionType)
                        android.util.Log.d("SimpleLogin", "✓ 转换成功，Clash配置长度: ${finalContent.length}")
                    } catch (e: Exception) {
                        android.util.Log.e("SimpleLogin", "✗ 转换失败: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                                .setTitle("❌ 订阅转换失败")
                                .setMessage(
                                    "检测到 ${getSubscriptionTypeName(subscriptionType)} 格式订阅，" +
                                    "但转换失败。\n\n" +
                                    "错误信息: ${e.message}\n\n" +
                                    "建议:\n" +
                                    "• 向服务商索取Clash格式订阅\n" +
                                    "• 使用在线订阅转换服务\n" +
                                    "• 检查订阅链接是否正确"
                                )
                                .setPositiveButton("确定", null)
                                .show()
                            
                            testConnectionButton.isEnabled = true
                            testConnectionButton.text = "🔍 测试订阅连接"
                        }
                        return@launch
                    }
                }
                
                // 验证最终配置
                val isValid = finalContent.contains("proxies:") || 
                              finalContent.contains("proxy-groups:") ||
                              finalContent.contains("\"proxies\"") ||
                              finalContent.contains("\"proxy-groups\"")
                
                withContext(Dispatchers.Main) {
                    if (isValid) {
                        android.util.Log.d("SimpleLogin", "✓ 验证成功: Clash配置有效")
                        
                        val resultMessage = buildString {
                            append("✅ 测试成功！\n\n")
                            append("📊 详细信息:\n")
                            append("• 连接时间: ${elapsed}ms\n")
                            append("• 原始大小: ${originalContent.length} 字节\n")
                            
                            if (convertedFrom != null) {
                                append("• 原始格式: $convertedFrom\n")
                                append("• 已自动转换为Clash格式\n")
                                append("• 转换后大小: ${finalContent.length} 字节\n")
                            } else {
                                append("• 配置格式: Clash（无需转换）\n")
                            }
                            
                            // 统计代理节点数量
                            val proxyCount = finalContent.lines().count { 
                                it.trim().startsWith("- name:") || 
                                it.trim().startsWith("\"name\":")
                            }
                            if (proxyCount > 0) {
                                append("• 节点数量: 约 $proxyCount 个\n")
                            }
                            
                            append("\n")
                            if (convertedFrom != null) {
                                append("✨ 已自动转换，可以直接使用！")
                            } else {
                                append("可以放心使用此订阅链接！")
                            }
                        }
                        
                        android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                            .setTitle(if (convertedFrom != null) "🔄 转换成功" else "✅ 测试成功")
                            .setMessage(resultMessage)
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        android.util.Log.w("SimpleLogin", "⚠ 验证失败")
                        android.util.Log.w("SimpleLogin", "内容预览: ${finalContent.take(200)}")
                        
                        val preview = finalContent.take(200).replace("<", "&lt;").replace(">", "&gt;")
                        
                        android.app.AlertDialog.Builder(this@SimpleLoginActivity)
                            .setTitle("⚠️ 订阅验证失败")
                            .setMessage(
                                "获取到订阅内容，但验证失败。\n\n" +
                                (if (convertedFrom != null) "尝试从 $convertedFrom 转换，但转换结果无效。\n\n" else "") +
                                "可能的原因:\n" +
                                "1. 订阅链接已过期或无效\n" +
                                "2. 服务器返回了错误页面\n" +
                                "3. 订阅格式不被支持\n\n" +
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
        
        // 获取并保存用户选择的订阅类型
        val subscriptionType = when {
            radioV2ray.isChecked -> PreferenceManager.SUBSCRIPTION_TYPE_V2RAY
            radioClash.isChecked -> PreferenceManager.SUBSCRIPTION_TYPE_CLASH
            else -> PreferenceManager.SUBSCRIPTION_TYPE_V2RAY
        }
        PreferenceManager.subscriptionType = subscriptionType
        
        android.util.Log.d("SimpleLogin", "开始验证订阅链接: $subscribeUrl, 类型: $subscriptionType")

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
                val originalContent = connection.getInputStream().bufferedReader().use { it.readText() }
                
                // 检测订阅类型
                val subscriptionType = com.github.kr328.clash.design.util.SubscriptionConverter.detectSubscriptionType(originalContent)
                android.util.Log.d("SimpleLogin", "订阅类型: $subscriptionType")
                
                // 如果不是Clash格式，尝试转换
                val finalContent = if (subscriptionType != com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.CLASH) {
                    android.util.Log.d("SimpleLogin", "检测到 ${getSubscriptionTypeName(subscriptionType)} 格式，尝试自动转换...")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SimpleLoginActivity,
                            "检测到 ${getSubscriptionTypeName(subscriptionType)} 格式，正在转换...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    try {
                        com.github.kr328.clash.design.util.SubscriptionConverter.convertToClash(originalContent)
                    } catch (e: Exception) {
                        android.util.Log.e("SimpleLogin", "转换失败: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@SimpleLoginActivity,
                                "订阅转换失败: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@withContext false
                    }
                } else {
                    originalContent
                }
                
                // 验证最终内容
                val isValid = finalContent.contains("proxies:") || 
                              finalContent.contains("proxy-groups:") ||
                              finalContent.contains("\"proxies\"") ||
                              finalContent.contains("\"proxy-groups\"")
                
                if (isValid && subscriptionType != com.github.kr328.clash.design.util.SubscriptionConverter.SubscriptionType.CLASH) {
                    android.util.Log.d("SimpleLogin", "✓ ${getSubscriptionTypeName(subscriptionType)} 转换为Clash成功")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SimpleLoginActivity,
                            "✓ 已自动转换为Clash格式",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (!isValid) {
                    android.util.Log.e("SimpleLogin", "订阅内容验证失败，内容前100字符: ${finalContent.take(100)}")
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
