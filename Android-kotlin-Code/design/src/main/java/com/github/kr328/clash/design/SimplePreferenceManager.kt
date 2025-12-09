package com.github.kr328.clash.design

import android.content.Context
import android.content.SharedPreferences
import com.github.kr328.clash.design.model.LocalTrafficStats
import com.github.kr328.clash.design.model.LocalUser
import com.github.kr328.clash.design.model.SubscriptionInfo
import com.google.gson.Gson
import java.util.UUID

/**
 * 简化的偏好管理器 - 移除V2Board依赖
 */
object SimplePreferenceManager {
    private const val PREF_NAME = "simple_app_preferences"
    private const val KEY_LOCAL_USER = "local_user"
    private const val KEY_SUBSCRIPTION = "subscription_info"
    private const val KEY_TRAFFIC_STATS = "traffic_stats"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SELECTED_NODE = "selected_node_name"
    private const val KEY_MODE_NAME = "mode_name"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 清除所有数据
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // ========== 设备ID ==========
    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrEmpty()) {
                id = UUID.randomUUID().toString()
                deviceId = id
            }
            return id
        }
        set(value) {
            prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        }

    // ========== 登录状态 ==========
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
        }

    // ========== 用户信息 ==========
    var localUser: LocalUser?
        get() {
            val json = prefs.getString(KEY_LOCAL_USER, null)
            return if (json != null) {
                try {
                    gson.fromJson(json, LocalUser::class.java)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
        set(value) {
            if (value != null) {
                prefs.edit().putString(KEY_LOCAL_USER, gson.toJson(value)).apply()
            } else {
                prefs.edit().remove(KEY_LOCAL_USER).apply()
            }
        }

    // ========== 订阅信息 ==========
    var subscriptionInfo: SubscriptionInfo?
        get() {
            val json = prefs.getString(KEY_SUBSCRIPTION, null)
            return if (json != null) {
                try {
                    gson.fromJson(json, SubscriptionInfo::class.java)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
        set(value) {
            if (value != null) {
                prefs.edit().putString(KEY_SUBSCRIPTION, gson.toJson(value)).apply()
            } else {
                prefs.edit().remove(KEY_SUBSCRIPTION).apply()
            }
        }

    // ========== 流量统计 ==========
    var trafficStats: LocalTrafficStats
        get() {
            val json = prefs.getString(KEY_TRAFFIC_STATS, null)
            return if (json != null) {
                try {
                    gson.fromJson(json, LocalTrafficStats::class.java)
                } catch (e: Exception) {
                    LocalTrafficStats()
                }
            } else {
                LocalTrafficStats()
            }
        }
        set(value) {
            prefs.edit().putString(KEY_TRAFFIC_STATS, gson.toJson(value)).apply()
        }

    // 更新流量数据
    fun updateTraffic(uploadBytes: Long, downloadBytes: Long) {
        val current = trafficStats
        trafficStats = current.copy(
            uploadBytes = current.uploadBytes + uploadBytes,
            downloadBytes = current.downloadBytes + downloadBytes,
            totalBytes = current.totalBytes + uploadBytes + downloadBytes,
            lastUpdateTime = System.currentTimeMillis()
        )
        
        // 同时更新订阅的已使用流量
        subscriptionInfo?.let { sub ->
            subscriptionInfo = sub.copy(
                usedTraffic = sub.usedTraffic + uploadBytes + downloadBytes
            )
        }
    }

    // 重置流量统计
    fun resetTrafficStats() {
        trafficStats = LocalTrafficStats()
    }

    // ========== 节点选择 ==========
    var selectedNodeName: String
        get() = prefs.getString(KEY_SELECTED_NODE, "自动选择") ?: "自动选择"
        set(value) {
            prefs.edit().putString(KEY_SELECTED_NODE, value).apply()
        }

    // ========== 模式名称 ==========
    var modeName: String
        get() = prefs.getString(KEY_MODE_NAME, "智能模式") ?: "智能模式"
        set(value) {
            prefs.edit().putString(KEY_MODE_NAME, value).apply()
        }

    // ========== 便捷方法 ==========
    
    // 登录
    fun login(username: String, subscribeUrl: String) {
        val user = LocalUser(
            deviceId = deviceId,
            username = username,
            subscribeUrl = subscribeUrl
        )
        localUser = user
        isLoggedIn = true
        
        // 创建默认订阅信息
        if (subscriptionInfo == null) {
            subscriptionInfo = SubscriptionInfo(
                url = subscribeUrl,
                name = "$username's Subscription"
            )
        }
    }

    // 登出
    fun logout() {
        isLoggedIn = false
        localUser = null
        subscriptionInfo = null
        resetTrafficStats()
    }

    // 更新订阅URL
    fun updateSubscriptionUrl(newUrl: String) {
        localUser?.let { user ->
            localUser = user.copy(subscribeUrl = newUrl)
        }
        subscriptionInfo?.let { sub ->
            subscriptionInfo = sub.copy(
                url = newUrl,
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    // 检查订阅是否有效
    fun isSubscriptionValid(): Boolean {
        val sub = subscriptionInfo ?: return false
        return !sub.isExpired && !sub.isTrafficExceeded
    }
}
