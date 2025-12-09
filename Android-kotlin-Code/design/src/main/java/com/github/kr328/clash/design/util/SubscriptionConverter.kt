package com.github.kr328.clash.design.util

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * 订阅转换工具 - 支持V2Ray/SS/SSR到Clash的转换
 */
object SubscriptionConverter {

    private const val TAG = "SubscriptionConverter"

    /**
     * 检测订阅类型
     */
    enum class SubscriptionType {
        CLASH,          // Clash配置
        V2RAY_BASE64,   // V2Ray Base64编码
        V2RAY_JSON,     // V2Ray JSON格式
        SHADOWSOCKS,    // Shadowsocks
        UNKNOWN         // 未知格式
    }

    /**
     * 检测订阅内容类型
     */
    fun detectSubscriptionType(content: String): SubscriptionType {
        val trimmedContent = content.trim()
        
        return when {
            // Clash配置
            trimmedContent.contains("proxies:") ||
            trimmedContent.contains("proxy-groups:") ||
            trimmedContent.contains("\"proxies\"") ||
            trimmedContent.contains("\"proxy-groups\"") -> {
                Log.d(TAG, "检测到Clash配置")
                SubscriptionType.CLASH
            }
            
            // V2Ray JSON格式
            trimmedContent.startsWith("{") && 
            (trimmedContent.contains("\"outbounds\"") || 
             trimmedContent.contains("\"vnext\"")) -> {
                Log.d(TAG, "检测到V2Ray JSON配置")
                SubscriptionType.V2RAY_JSON
            }
            
            // V2Ray Base64编码（vmess://开头）
            trimmedContent.contains("vmess://") ||
            trimmedContent.contains("vless://") ||
            trimmedContent.contains("trojan://") -> {
                Log.d(TAG, "检测到V2Ray Base64配置")
                SubscriptionType.V2RAY_BASE64
            }
            
            // Shadowsocks（ss://开头）
            trimmedContent.contains("ss://") ||
            trimmedContent.contains("ssr://") -> {
                Log.d(TAG, "检测到Shadowsocks配置")
                SubscriptionType.SHADOWSOCKS
            }
            
            // 尝试Base64解码
            else -> {
                try {
                    val decoded = String(Base64.decode(trimmedContent, Base64.DEFAULT))
                    if (decoded.contains("vmess://") || 
                        decoded.contains("ss://") ||
                        decoded.contains("ssr://")) {
                        Log.d(TAG, "检测到Base64编码的订阅")
                        SubscriptionType.V2RAY_BASE64
                    } else {
                        Log.d(TAG, "未知的订阅格式")
                        SubscriptionType.UNKNOWN
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "未知的订阅格式")
                    SubscriptionType.UNKNOWN
                }
            }
        }
    }

    /**
     * 转换订阅为Clash配置
     */
    fun convertToClash(content: String): String {
        val type = detectSubscriptionType(content)
        
        return when (type) {
            SubscriptionType.CLASH -> {
                Log.d(TAG, "已经是Clash配置，无需转换")
                content
            }
            
            SubscriptionType.V2RAY_BASE64 -> {
                Log.d(TAG, "开始转换V2Ray Base64配置")
                convertV2RayBase64ToClash(content)
            }
            
            SubscriptionType.V2RAY_JSON -> {
                Log.d(TAG, "开始转换V2Ray JSON配置")
                convertV2RayJsonToClash(content)
            }
            
            SubscriptionType.SHADOWSOCKS -> {
                Log.d(TAG, "开始转换Shadowsocks配置")
                convertShadowsocksToClash(content)
            }
            
            SubscriptionType.UNKNOWN -> {
                Log.e(TAG, "无法识别的订阅格式")
                throw Exception("不支持的订阅格式")
            }
        }
    }

    /**
     * 转换V2Ray Base64格式到Clash
     */
    private fun convertV2RayBase64ToClash(content: String): String {
        val proxies = mutableListOf<Map<String, Any>>()
        val proxyNames = mutableListOf<String>()
        
        // 尝试Base64解码
        val decodedContent = try {
            if (content.contains("vmess://") || content.contains("vless://")) {
                content
            } else {
                String(Base64.decode(content.trim(), Base64.DEFAULT))
            }
        } catch (e: Exception) {
            content
        }
        
        // 按行分割
        val lines = decodedContent.split("\n", "\r\n", "\r")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            try {
                when {
                    // VMess协议
                    trimmedLine.startsWith("vmess://") -> {
                        val proxy = parseVmess(trimmedLine)
                        if (proxy != null) {
                            proxies.add(proxy)
                            proxyNames.add(proxy["name"] as String)
                        }
                    }
                    
                    // VLess协议
                    trimmedLine.startsWith("vless://") -> {
                        val proxy = parseVless(trimmedLine)
                        if (proxy != null) {
                            proxies.add(proxy)
                            proxyNames.add(proxy["name"] as String)
                        }
                    }
                    
                    // Trojan协议
                    trimmedLine.startsWith("trojan://") -> {
                        val proxy = parseTrojan(trimmedLine)
                        if (proxy != null) {
                            proxies.add(proxy)
                            proxyNames.add(proxy["name"] as String)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "解析节点失败: ${e.message}")
            }
        }
        
        if (proxies.isEmpty()) {
            throw Exception("未找到有效的代理节点")
        }
        
        Log.d(TAG, "成功解析 ${proxies.size} 个节点")
        
        // 生成Clash配置
        return generateClashConfig(proxies, proxyNames)
    }

    /**
     * 解析VMess链接
     */
    private fun parseVmess(vmessUrl: String): Map<String, Any>? {
        try {
            val base64String = vmessUrl.substring(8) // 移除 "vmess://"
            val jsonString = String(Base64.decode(base64String, Base64.DEFAULT))
            val json = JSONObject(jsonString)
            
            val proxy = mutableMapOf<String, Any>()
            proxy["name"] = json.optString("ps", "VMess节点")
            proxy["type"] = "vmess"
            proxy["server"] = json.getString("add")
            proxy["port"] = json.optInt("port", 443)
            proxy["uuid"] = json.getString("id")
            proxy["alterId"] = json.optInt("aid", 0)
            proxy["cipher"] = json.optString("scy", "auto")
            
            // 网络类型
            val net = json.optString("net", "tcp")
            proxy["network"] = net
            
            // TLS
            if (json.optString("tls", "").equals("tls", ignoreCase = true)) {
                proxy["tls"] = true
                val sni = json.optString("sni", "")
                if (sni.isNotEmpty()) {
                    proxy["servername"] = sni
                }
            }
            
            // WebSocket选项
            if (net == "ws") {
                val wsOpts = mutableMapOf<String, Any>()
                val path = json.optString("path", "/")
                wsOpts["path"] = path
                
                val host = json.optString("host", "")
                if (host.isNotEmpty()) {
                    wsOpts["headers"] = mapOf("Host" to host)
                }
                proxy["ws-opts"] = wsOpts
            }
            
            return proxy
        } catch (e: Exception) {
            Log.e(TAG, "解析VMess失败: ${e.message}")
            return null
        }
    }

    /**
     * 解析VLess链接
     */
    private fun parseVless(vlessUrl: String): Map<String, Any>? {
        try {
            // vless://uuid@server:port?参数#备注
            val url = vlessUrl.substring(8) // 移除 "vless://"
            val parts = url.split("@")
            if (parts.size != 2) return null
            
            val uuid = parts[0]
            val remaining = parts[1]
            
            val serverParts = remaining.split("?")
            val serverAndPort = serverParts[0].split(":")
            if (serverAndPort.size != 2) return null
            
            val server = serverAndPort[0]
            val port = serverAndPort[1].toIntOrNull() ?: 443
            
            val proxy = mutableMapOf<String, Any>()
            proxy["name"] = URLDecoder.decode(remaining.substringAfterLast("#", "VLess节点"), "UTF-8")
            proxy["type"] = "vless"
            proxy["server"] = server
            proxy["port"] = port
            proxy["uuid"] = uuid
            proxy["cipher"] = "none"
            
            // 解析参数
            if (serverParts.size > 1) {
                val params = serverParts[1].split("&")
                for (param in params) {
                    val kv = param.split("=")
                    if (kv.size == 2) {
                        when (kv[0]) {
                            "security" -> if (kv[1] == "tls") proxy["tls"] = true
                            "type" -> proxy["network"] = kv[1]
                            "sni" -> proxy["servername"] = kv[1]
                        }
                    }
                }
            }
            
            return proxy
        } catch (e: Exception) {
            Log.e(TAG, "解析VLess失败: ${e.message}")
            return null
        }
    }

    /**
     * 解析Trojan链接
     */
    private fun parseTrojan(trojanUrl: String): Map<String, Any>? {
        try {
            // trojan://password@server:port?参数#备注
            val url = trojanUrl.substring(9) // 移除 "trojan://"
            val parts = url.split("@")
            if (parts.size != 2) return null
            
            val password = parts[0]
            val remaining = parts[1]
            
            val serverParts = remaining.split("?")
            val serverAndPort = serverParts[0].split(":")
            if (serverAndPort.size != 2) return null
            
            val server = serverAndPort[0]
            val port = serverAndPort[1].toIntOrNull() ?: 443
            
            val proxy = mutableMapOf<String, Any>()
            proxy["name"] = URLDecoder.decode(remaining.substringAfterLast("#", "Trojan节点"), "UTF-8")
            proxy["type"] = "trojan"
            proxy["server"] = server
            proxy["port"] = port
            proxy["password"] = password
            proxy["sni"] = server
            
            return proxy
        } catch (e: Exception) {
            Log.e(TAG, "解析Trojan失败: ${e.message}")
            return null
        }
    }

    /**
     * 转换V2Ray JSON格式到Clash（简化实现）
     */
    private fun convertV2RayJsonToClash(content: String): String {
        // V2Ray JSON格式比较复杂，这里提供基础实现
        throw Exception("V2Ray JSON格式转换暂不支持，请使用链接格式的订阅")
    }

    /**
     * 转换Shadowsocks格式到Clash
     */
    private fun convertShadowsocksToClash(content: String): String {
        val proxies = mutableListOf<Map<String, Any>>()
        val proxyNames = mutableListOf<String>()
        
        val decodedContent = try {
            if (content.contains("ss://") || content.contains("ssr://")) {
                content
            } else {
                String(Base64.decode(content.trim(), Base64.DEFAULT))
            }
        } catch (e: Exception) {
            content
        }
        
        val lines = decodedContent.split("\n", "\r\n", "\r")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            try {
                if (trimmedLine.startsWith("ss://")) {
                    val proxy = parseShadowsocks(trimmedLine)
                    if (proxy != null) {
                        proxies.add(proxy)
                        proxyNames.add(proxy["name"] as String)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "解析SS节点失败: ${e.message}")
            }
        }
        
        if (proxies.isEmpty()) {
            throw Exception("未找到有效的Shadowsocks节点")
        }
        
        return generateClashConfig(proxies, proxyNames)
    }

    /**
     * 解析Shadowsocks链接
     */
    private fun parseShadowsocks(ssUrl: String): Map<String, Any>? {
        try {
            // ss://base64(method:password@server:port)#备注
            val url = ssUrl.substring(5) // 移除 "ss://"
            val parts = url.split("#")
            val encoded = parts[0]
            val name = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else "SS节点"
            
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
            val methodAndRest = decoded.split("@")
            if (methodAndRest.size != 2) return null
            
            val methodAndPassword = methodAndRest[0].split(":")
            if (methodAndPassword.size != 2) return null
            
            val serverAndPort = methodAndRest[1].split(":")
            if (serverAndPort.size != 2) return null
            
            val proxy = mutableMapOf<String, Any>()
            proxy["name"] = name
            proxy["type"] = "ss"
            proxy["server"] = serverAndPort[0]
            proxy["port"] = serverAndPort[1].toIntOrNull() ?: 8388
            proxy["cipher"] = methodAndPassword[0]
            proxy["password"] = methodAndPassword[1]
            
            return proxy
        } catch (e: Exception) {
            Log.e(TAG, "解析SS失败: ${e.message}")
            return null
        }
    }

    /**
     * 生成Clash配置文件
     */
    private fun generateClashConfig(
        proxies: List<Map<String, Any>>,
        proxyNames: List<String>
    ): String {
        val sb = StringBuilder()
        
        // 基础配置
        sb.appendLine("# Clash配置 - 由UUVPN自动转换生成")
        sb.appendLine("# 生成时间: ${System.currentTimeMillis()}")
        sb.appendLine()
        sb.appendLine("port: 7890")
        sb.appendLine("socks-port: 7891")
        sb.appendLine("allow-lan: false")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("external-controller: 127.0.0.1:9090")
        sb.appendLine()
        
        // 代理节点
        sb.appendLine("proxies:")
        for (proxy in proxies) {
            sb.append("  - ")
            sb.appendLine(proxyToYaml(proxy))
        }
        sb.appendLine()
        
        // 代理组
        sb.appendLine("proxy-groups:")
        sb.appendLine("  - name: \"🚀 节点选择\"")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - \"♻️ 自动选择\"")
        sb.appendLine("      - \"DIRECT\"")
        for (name in proxyNames) {
            sb.appendLine("      - \"$name\"")
        }
        sb.appendLine()
        
        sb.appendLine("  - name: \"♻️ 自动选择\"")
        sb.appendLine("    type: url-test")
        sb.appendLine("    proxies:")
        for (name in proxyNames) {
            sb.appendLine("      - \"$name\"")
        }
        sb.appendLine("    url: 'http://www.gstatic.com/generate_204'")
        sb.appendLine("    interval: 300")
        sb.appendLine()
        
        // 规则
        sb.appendLine("rules:")
        sb.appendLine("  - DOMAIN-SUFFIX,cn,DIRECT")
        sb.appendLine("  - GEOIP,CN,DIRECT")
        sb.appendLine("  - MATCH,🚀 节点选择")
        
        return sb.toString()
    }

    /**
     * 将代理对象转换为YAML格式字符串
     */
    private fun proxyToYaml(proxy: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.append("{")
        
        var first = true
        for ((key, value) in proxy) {
            if (!first) sb.append(", ")
            first = false
            
            sb.append("$key: ")
            when (value) {
                is String -> sb.append("\"$value\"")
                is Map<*, *> -> sb.append(mapToYaml(value as Map<String, Any>))
                else -> sb.append(value)
            }
        }
        
        sb.append("}")
        return sb.toString()
    }

    /**
     * 将Map转换为YAML格式
     */
    private fun mapToYaml(map: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.append("{")
        
        var first = true
        for ((key, value) in map) {
            if (!first) sb.append(", ")
            first = false
            
            sb.append("$key: ")
            when (value) {
                is String -> sb.append("\"$value\"")
                is Map<*, *> -> sb.append(mapToYaml(value as Map<String, Any>))
                else -> sb.append(value)
            }
        }
        
        sb.append("}")
        return sb.toString()
    }
}
