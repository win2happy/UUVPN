package com.github.kr328.clash.design.util

import com.github.kr328.clash.design.SimplePreferenceManager
import com.github.kr328.clash.design.model.LocalTrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 流量统计工具类
 * 本地记录流量使用情况,不依赖后端API
 */
object TrafficStatsUtil {
    
    private var monitorJob: Job? = null
    private var lastUploadBytes = 0L
    private var lastDownloadBytes = 0L
    
    /**
     * 开始监控流量
     */
    fun startMonitoring(
        scope: CoroutineScope,
        onUpdate: ((upload: Long, download: Long, total: Long) -> Unit)? = null
    ) {
        stopMonitoring()
        
        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    // 获取当前流量统计
                    val stats = SimplePreferenceManager.trafficStats
                    
                    // 回调更新
                    onUpdate?.invoke(
                        stats.uploadBytes,
                        stats.downloadBytes,
                        stats.totalBytes
                    )
                    
                    // 每秒更新一次
                    delay(1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * 停止监控流量
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }
    
    /**
     * 记录流量使用
     * @param uploadDelta 本次上传字节数
     * @param downloadDelta 本次下载字节数
     */
    fun recordTraffic(uploadDelta: Long, downloadDelta: Long) {
        if (uploadDelta > 0 || downloadDelta > 0) {
            SimplePreferenceManager.updateTraffic(uploadDelta, downloadDelta)
        }
    }
    
    /**
     * 获取当前流量统计
     */
    fun getCurrentStats(): LocalTrafficStats {
        return SimplePreferenceManager.trafficStats
    }
    
    /**
     * 重置流量统计
     */
    fun resetStats() {
        SimplePreferenceManager.resetTrafficStats()
    }
    
    /**
     * 格式化字节数为可读字符串
     */
    fun formatBytes(bytes: Long): String {
        return LocalTrafficStats.formatBytes(bytes)
    }
    
    /**
     * 获取流量使用百分比
     */
    fun getUsagePercentage(): Int {
        val sub = SimplePreferenceManager.subscriptionInfo ?: return 0
        return sub.trafficUsagePercent
    }
    
    /**
     * 检查是否接近流量限制
     * @param threshold 阈值百分比 (0-100)
     */
    fun isNearLimit(threshold: Int = 90): Boolean {
        return getUsagePercentage() >= threshold
    }
    
    /**
     * 获取剩余流量
     */
    fun getRemainingTraffic(): Long {
        val sub = SimplePreferenceManager.subscriptionInfo ?: return Long.MAX_VALUE
        return sub.remainingTraffic
    }
    
    /**
     * 获取流量状态描述
     */
    fun getTrafficStatusDescription(): String {
        val sub = SimplePreferenceManager.subscriptionInfo
        
        return if (sub == null || sub.totalTraffic == 0L) {
            "无限制"
        } else {
            val used = formatBytes(sub.usedTraffic)
            val total = formatBytes(sub.totalTraffic)
            val percent = sub.trafficUsagePercent
            "$used / $total ($percent%)"
        }
    }
}
