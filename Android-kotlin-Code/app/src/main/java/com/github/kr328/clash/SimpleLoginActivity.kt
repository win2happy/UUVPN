package com.github.kr328.clash

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SimplePreferenceManager
import com.github.kr328.clash.design.databinding.ActivitySimpleLoginBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
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

    private lateinit var binding: ActivitySimpleLoginBinding
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySimpleLoginBinding.inflate(this.layoutInflater, this.root, false)
        setContentView(binding.root)

        SimplePreferenceManager.init(this)

        setupViews()
    }

    private fun setupViews() {
        // 切换订阅链接可见性
        binding.togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            
            if (isPasswordVisible) {
                binding.subscribeUrlEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.togglePasswordVisibility.setImageResource(com.github.kr328.clash.design.R.drawable.visibility_24px)
            } else {
                binding.subscribeUrlEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.togglePasswordVisibility.setImageResource(com.github.kr328.clash.design.R.drawable.visibility_off_24px)
            }
            binding.subscribeUrlEditText.setSelection(binding.subscribeUrlEditText.text.length)
        }

        // 登录按钮
        binding.loginButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString().trim()
            val subscribeUrl = binding.subscribeUrlEditText.text.toString().trim()

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
        binding.quickImportButton?.setOnClickListener {
            importFromClipboard()
        }

        // 跳过登录 - 以访客模式使用
        binding.skipLoginButton?.setOnClickListener {
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
        binding.loginButton.isEnabled = false
        binding.loginButton.text = "验证订阅中..."

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
                        binding.loginButton.isEnabled = true
                        binding.loginButton.text = "登录"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SimpleLoginActivity,
                        "登录失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = "登录"
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
                    binding.subscribeUrlEditText.setText(text)
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
