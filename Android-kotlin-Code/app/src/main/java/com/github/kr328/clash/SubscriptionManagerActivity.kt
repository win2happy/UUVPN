package com.github.kr328.clash

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.SimplePreferenceManager
import com.github.kr328.clash.design.manager.SubscriptionManager
import com.github.kr328.clash.design.util.TrafficStatsUtil
import com.github.kr328.clash.utity.LoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 订阅管理界面
 * 功能:
 * - 查看订阅信息
 * - 更新订阅
 * - 导入新订阅
 * - 流量统计
 */
class SubscriptionManagerActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var usernameText: TextView
    private lateinit var deviceIdText: TextView
    private lateinit var subscriptionNameText: TextView
    private lateinit var subscriptionUrlText: TextView
    private lateinit var subscriptionStatusText: TextView
    private lateinit var lastUpdateText: TextView
    private lateinit var expireTimeText: TextView
    private lateinit var updateSubscriptionButton: Button
    private lateinit var copyUrlButton: Button
    private lateinit var trafficUsageText: TextView
    private lateinit var trafficProgressBar: ProgressBar
    private lateinit var uploadText: TextView
    private lateinit var downloadText: TextView
    private lateinit var totalTrafficText: TextView
    private lateinit var resetTrafficButton: Button
    private lateinit var importNewSubscriptionButton: Button
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.github.kr328.clash.design.R.layout.activity_subscription_manager)
        
        // 初始化视图
        backButton = findViewById(com.github.kr328.clash.design.R.id.backButton)
        usernameText = findViewById(com.github.kr328.clash.design.R.id.usernameText)
        deviceIdText = findViewById(com.github.kr328.clash.design.R.id.deviceIdText)
        subscriptionNameText = findViewById(com.github.kr328.clash.design.R.id.subscriptionNameText)
        subscriptionUrlText = findViewById(com.github.kr328.clash.design.R.id.subscriptionUrlText)
        subscriptionStatusText = findViewById(com.github.kr328.clash.design.R.id.subscriptionStatusText)
        lastUpdateText = findViewById(com.github.kr328.clash.design.R.id.lastUpdateText)
        expireTimeText = findViewById(com.github.kr328.clash.design.R.id.expireTimeText)
        updateSubscriptionButton = findViewById(com.github.kr328.clash.design.R.id.updateSubscriptionButton)
        copyUrlButton = findViewById(com.github.kr328.clash.design.R.id.copyUrlButton)
        trafficUsageText = findViewById(com.github.kr328.clash.design.R.id.trafficUsageText)
        trafficProgressBar = findViewById(com.github.kr328.clash.design.R.id.trafficProgressBar)
        uploadText = findViewById(com.github.kr328.clash.design.R.id.uploadText)
        downloadText = findViewById(com.github.kr328.clash.design.R.id.downloadText)
        totalTrafficText = findViewById(com.github.kr328.clash.design.R.id.totalTrafficText)
        resetTrafficButton = findViewById(com.github.kr328.clash.design.R.id.resetTrafficButton)
        importNewSubscriptionButton = findViewById(com.github.kr328.clash.design.R.id.importNewSubscriptionButton)

        SimplePreferenceManager.init(this)

        setupViews()
        loadSubscriptionInfo()
    }

    private fun setupViews() {
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 更新订阅按钮
        updateSubscriptionButton.setOnClickListener {
            updateSubscription()
        }

        // 导入新订阅按钮
        importNewSubscriptionButton.setOnClickListener {
            showImportDialog()
        }

        // 重置流量统计按钮
        resetTrafficButton.setOnClickListener {
            showResetTrafficDialog()
        }

        // 复制订阅链接按钮
        copyUrlButton.setOnClickListener {
            copySubscriptionUrl()
        }
    }

    private fun loadSubscriptionInfo() {
        val sub = SimplePreferenceManager.subscriptionInfo
        val user = SimplePreferenceManager.localUser
        val stats = SimplePreferenceManager.trafficStats

        if (sub != null) {
            // 订阅信息
            subscriptionNameText.text = sub.name
            subscriptionUrlText.text = sub.url
            
            // 最后更新时间
            val updateTime = if (sub.lastUpdate > 0) {
                dateFormat.format(Date(sub.lastUpdate))
            } else {
                "未更新"
            }
            lastUpdateText.text = "最后更新: $updateTime"

            // 流量信息
            if (sub.totalTraffic > 0) {
                trafficUsageText.text = TrafficStatsUtil.getTrafficStatusDescription()
                trafficProgressBar.max = 100
                trafficProgressBar.progress = sub.trafficUsagePercent
            } else {
                trafficUsageText.text = "流量无限制"
                trafficProgressBar.progress = 0
            }

            // 过期时间
            if (sub.expireAt > 0) {
                val expireDate = dateFormat.format(Date(sub.expireAt))
                expireTimeText.text = "过期时间: $expireDate"
                
                if (sub.isExpired) {
                    expireTimeText.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            } else {
                expireTimeText.text = "永久有效"
            }

            // 订阅状态
            val status = SubscriptionManager.getSubscriptionStatus()
            subscriptionStatusText.text = "状态: $status"
            
            when {
                sub.isExpired || sub.isTrafficExceeded -> {
                    subscriptionStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                else -> {
                    subscriptionStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }
        } else {
            subscriptionNameText.text = "未导入订阅"
            subscriptionUrlText.text = "请导入订阅链接"
        }

        // 用户信息
        if (user != null) {
            usernameText.text = "用户: ${user.username}"
            deviceIdText.text = "设备ID: ${user.deviceId.substring(0, 8)}..."
        }

        // 本地流量统计
        uploadText.text = "上传: ${TrafficStatsUtil.formatBytes(stats.uploadBytes)}"
        downloadText.text = "下载: ${TrafficStatsUtil.formatBytes(stats.downloadBytes)}"
        totalTrafficText.text = "总计: ${TrafficStatsUtil.formatBytes(stats.totalBytes)}"
    }

    private fun updateSubscription() {
        LoadingDialog.show(this, "正在更新订阅...")

        CoroutineScope(Dispatchers.IO).launch {
            val result = SubscriptionManager.updateSubscription()

            withContext(Dispatchers.Main) {
                LoadingDialog.hide()

                if (result.isSuccess) {
                    Toast.makeText(
                        this@SubscriptionManagerActivity,
                        "订阅更新成功",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadSubscriptionInfo()
                } else {
                    Toast.makeText(
                        this@SubscriptionManagerActivity,
                        "订阅更新失败: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showImportDialog() {
        val input = android.widget.EditText(this)
        input.hint = "请输入订阅链接"
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI

        AlertDialog.Builder(this)
            .setTitle("导入新订阅")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    importSubscription(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importSubscription(url: String) {
        LoadingDialog.show(this, "正在导入订阅...")

        CoroutineScope(Dispatchers.IO).launch {
            val result = SubscriptionManager.importSubscription(url)

            withContext(Dispatchers.Main) {
                LoadingDialog.hide()

                if (result.isSuccess) {
                    Toast.makeText(
                        this@SubscriptionManagerActivity,
                        "订阅导入成功",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // 更新用户的订阅URL
                    SimplePreferenceManager.updateSubscriptionUrl(url)
                    loadSubscriptionInfo()
                } else {
                    Toast.makeText(
                        this@SubscriptionManagerActivity,
                        "订阅导入失败: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showResetTrafficDialog() {
        AlertDialog.Builder(this)
            .setTitle("重置流量统计")
            .setMessage("确定要重置本地流量统计吗？")
            .setPositiveButton("确定") { _, _ ->
                TrafficStatsUtil.resetStats()
                Toast.makeText(this, "流量统计已重置", Toast.LENGTH_SHORT).show()
                loadSubscriptionInfo()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copySubscriptionUrl() {
        val sub = SimplePreferenceManager.subscriptionInfo
        if (sub != null) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("订阅链接", sub.url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "订阅链接已复制", Toast.LENGTH_SHORT).show()
        }
    }
}
