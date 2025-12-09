package com.github.kr328.clash

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.SimplePreferenceManager
import com.github.kr328.clash.design.databinding.ActivitySubscriptionManagerBinding
import com.github.kr328.clash.design.manager.SubscriptionManager
import com.github.kr328.clash.design.util.TrafficStatsUtil
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
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

    private lateinit var binding: ActivitySubscriptionManagerBinding
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySubscriptionManagerBinding.inflate(this.layoutInflater, this.root, false)
        setContentView(binding.root)

        SimplePreferenceManager.init(this)

        setupViews()
        loadSubscriptionInfo()
    }

    private fun setupViews() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            finish()
        }

        // 更新订阅按钮
        binding.updateSubscriptionButton.setOnClickListener {
            updateSubscription()
        }

        // 导入新订阅按钮
        binding.importNewSubscriptionButton.setOnClickListener {
            showImportDialog()
        }

        // 重置流量统计按钮
        binding.resetTrafficButton.setOnClickListener {
            showResetTrafficDialog()
        }

        // 复制订阅链接按钮
        binding.copyUrlButton.setOnClickListener {
            copySubscriptionUrl()
        }
    }

    private fun loadSubscriptionInfo() {
        val sub = SimplePreferenceManager.subscriptionInfo
        val user = SimplePreferenceManager.localUser
        val stats = SimplePreferenceManager.trafficStats

        if (sub != null) {
            // 订阅信息
            binding.subscriptionNameText.text = sub.name
            binding.subscriptionUrlText.text = sub.url
            
            // 最后更新时间
            val updateTime = if (sub.lastUpdate > 0) {
                dateFormat.format(Date(sub.lastUpdate))
            } else {
                "未更新"
            }
            binding.lastUpdateText.text = "最后更新: $updateTime"

            // 流量信息
            if (sub.totalTraffic > 0) {
                binding.trafficUsageText.text = TrafficStatsUtil.getTrafficStatusDescription()
                binding.trafficProgressBar.max = 100
                binding.trafficProgressBar.progress = sub.trafficUsagePercent
            } else {
                binding.trafficUsageText.text = "流量无限制"
                binding.trafficProgressBar.progress = 0
            }

            // 过期时间
            if (sub.expireAt > 0) {
                val expireDate = dateFormat.format(Date(sub.expireAt))
                binding.expireTimeText.text = "过期时间: $expireDate"
                
                if (sub.isExpired) {
                    binding.expireTimeText.setTextColor(getColor(android.R.color.holor_red))
                }
            } else {
                binding.expireTimeText.text = "永久有效"
            }

            // 订阅状态
            val status = SubscriptionManager.getSubscriptionStatus()
            binding.subscriptionStatusText.text = "状态: $status"
            
            when {
                sub.isExpired || sub.isTrafficExceeded -> {
                    binding.subscriptionStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                else -> {
                    binding.subscriptionStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }
        } else {
            binding.subscriptionNameText.text = "未导入订阅"
            binding.subscriptionUrlText.text = "请导入订阅链接"
        }

        // 用户信息
        if (user != null) {
            binding.usernameText.text = "用户: ${user.username}"
            binding.deviceIdText.text = "设备ID: ${user.deviceId.substring(0, 8)}..."
        }

        // 本地流量统计
        binding.uploadText.text = "上传: ${TrafficStatsUtil.formatBytes(stats.uploadBytes)}"
        binding.downloadText.text = "下载: ${TrafficStatsUtil.formatBytes(stats.downloadBytes)}"
        binding.totalTrafficText.text = "总计: ${TrafficStatsUtil.formatBytes(stats.totalBytes)}"
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
