package com.github.kr328.clash.design.model

import java.io.Serializable

/**
 * 简化的用户模型 - 移除V2Board依赖
 */
data class LocalUser(
    val deviceId: String,
    val username: String,
    val subscribeUrl: String,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

/**
 * 订阅信息
 */
data class SubscriptionInfo(
    val url: String,
    val name: String = "Default Subscription",
    val updateInterval: Long = 0, // 0表示不自动更新
    val lastUpdate: Long = 0,
    val totalTraffic: Long = 0, // 0表示无限制
    val usedTraffic: Long = 0,
    val expireAt: Long = 0 // 0表示不过期
) : Serializable {
    
    // 是否过期
    val isExpired: Boolean
        get() = expireAt > 0 && System.currentTimeMillis() > expireAt
    
    // 是否超过流量限制
    val isTrafficExceeded: Boolean
        get() = totalTraffic > 0 && usedTraffic >= totalTraffic
    
    // 剩余流量(字节)
    val remainingTraffic: Long
        get() = if (totalTraffic > 0) (totalTraffic - usedTraffic).coerceAtLeast(0) else Long.MAX_VALUE
    
    // 流量使用百分比
    val trafficUsagePercent: Int
        get() = if (totalTraffic > 0) ((usedTraffic * 100) / totalTraffic).toInt().coerceIn(0, 100) else 0
}

/**
 * 本地流量统计
 */
data class LocalTrafficStats(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = uploadBytes + downloadBytes,
    val sessionStartTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis()
) : Serializable {
    
    // 格式化显示
    fun formatUpload(): String = formatBytes(uploadBytes)
    fun formatDownload(): String = formatBytes(downloadBytes)
    fun formatTotal(): String = formatBytes(totalBytes)
    
    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
                else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
    }
}
