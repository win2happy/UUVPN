package com.github.kr328.clash.design.manager

import android.content.Context
import com.github.kr328.clash.design.SimplePreferenceManager
import com.github.kr328.clash.design.model.SubscriptionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 订阅管理器 - 处理订阅链接的导入和更新
 */
object SubscriptionManager {

    /**
     * 导入订阅链接
     */
    suspend fun importSubscription(
        url: String,
        name: String = "Default Subscription"
    ): Result<SubscriptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                // 获取订阅内容
                val content = fetchSubscriptionContent(url)
                
                // 验证是否是有效的Clash配置
                if (!isValidClashConfig(content)) {
                    return@withContext Result.failure(Exception("不是有效的Clash配置文件"))
                }
                
                // 创建订阅信息
                val subscriptionInfo = SubscriptionInfo(
                    url = url,
                    name = name,
                    lastUpdate = System.currentTimeMillis()
                )
                
                // 保存订阅信息
                SimplePreferenceManager.subscriptionInfo = subscriptionInfo
                
                Result.success(subscriptionInfo)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 更新订阅
     */
    suspend fun updateSubscription(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val sub = SimplePreferenceManager.subscriptionInfo
                    ?: return@withContext Result.failure(Exception("未找到订阅信息"))
                
                // 获取最新内容
                val content = fetchSubscriptionContent(sub.url)
                
                // 验证配置
                if (!isValidClashConfig(content)) {
                    return@withContext Result.failure(Exception("订阅内容无效"))
                }
                
                // 更新最后更新时间
                SimplePreferenceManager.subscriptionInfo = sub.copy(
                    lastUpdate = System.currentTimeMillis()
                )
                
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取订阅内容
     */
    suspend fun fetchSubscriptionContent(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 30000  // 增加到30秒
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "ClashForAndroid/UUVPN")
                connection.setRequestProperty("Accept", "*/*")
                
                val content = connection.getInputStream().bufferedReader().use { it.readText() }
                
                android.util.Log.d("SubscriptionManager", "成功获取订阅内容，长度: ${content.length}")
                content
            } catch (e: Exception) {
                android.util.Log.e("SubscriptionManager", "获取订阅内容失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 验证是否是有效的Clash配置
     */
    private fun isValidClashConfig(content: String): Boolean {
        return try {
            // 检查必要的字段
            content.contains("proxies:") || 
            content.contains("proxy-groups:") ||
            content.contains("\"proxies\"") ||
            content.contains("\"proxy-groups\"") ||
            content.contains("rules:")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查订阅是否过期
     */
    fun isSubscriptionExpired(): Boolean {
        val sub = SimplePreferenceManager.subscriptionInfo ?: return true
        return sub.isExpired
    }

    /**
     * 检查流量是否超限
     */
    fun isTrafficExceeded(): Boolean {
        val sub = SimplePreferenceManager.subscriptionInfo ?: return false
        return sub.isTrafficExceeded
    }

    /**
     * 获取订阅状态描述
     */
    fun getSubscriptionStatus(): String {
        val sub = SimplePreferenceManager.subscriptionInfo ?: return "未导入订阅"
        
        return when {
            sub.isExpired -> "订阅已过期"
            sub.isTrafficExceeded -> "流量已用完"
            else -> "订阅正常"
        }
    }

    /**
     * 从URL解析订阅信息头
     * 有些订阅服务会在HTTP响应头中返回流量和过期信息
     */
    suspend fun parseSubscriptionHeaders(url: String): SubscriptionInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 30000  // 增加到30秒
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "ClashForAndroid/UUVPN")
                connection.setRequestProperty("Accept", "*/*")
                connection.connect()
                
                // 读取响应头
                val uploadBytes = connection.getHeaderField("subscription-userinfo")
                
                // 解析 subscription-userinfo 头
                // 格式: upload=0; download=0; total=107374182400; expire=1735689600
                var totalTraffic = 0L
                var usedTraffic = 0L
                var expireAt = 0L
                
                uploadBytes?.split(";")?.forEach { pair ->
                    val parts = pair.trim().split("=")
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().toLongOrNull() ?: 0
                        
                        when (key) {
                            "upload", "download" -> usedTraffic += value
                            "total" -> totalTraffic = value
                            "expire" -> expireAt = value * 1000 // 转换为毫秒
                        }
                    }
                }
                
                SubscriptionInfo(
                    url = url,
                    totalTraffic = totalTraffic,
                    usedTraffic = usedTraffic,
                    expireAt = expireAt,
                    lastUpdate = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
