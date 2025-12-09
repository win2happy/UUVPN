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
     * 解析VMess链接（完整支持所有参数）
     */
    private fun parseVmess(vmessUrl: String): Map<String, Any>? {
        try {
            val base64String = vmessUrl.substring(8) // 移除 "vmess://"
            val jsonString = String(Base64.decode(base64String, Base64.DEFAULT))
            val json = JSONObject(jsonString)
            
            val proxy = mutableMapOf<String, Any>()
            
            // 基本信息
            proxy["name"] = json.optString("ps", "VMess节点")
            proxy["type"] = "vmess"
            proxy["server"] = json.getString("add")
            proxy["port"] = json.optInt("port", 443)
            proxy["uuid"] = json.getString("id")
            proxy["alterId"] = json.optInt("aid", 0)
            
            // cipher - 加密方式
            val cipher = json.optString("scy", "auto")
            proxy["cipher"] = when (cipher) {
                "zero", "none" -> "none"
                "aes-128-gcm" -> "aes-128-gcm"
                "chacha20-poly1305" -> "chacha20-poly1305"
                "auto" -> "auto"
                else -> "auto"
            }
            
            // 网络类型
            val net = json.optString("net", "tcp")
            proxy["network"] = net
            
            // TLS/Reality
            val tls = json.optString("tls", "")
            if (tls.equals("tls", ignoreCase = true) || tls.equals("reality", ignoreCase = true)) {
                proxy["tls"] = true
                
                // SNI优先级: sni > host > add
                val sni = json.optString("sni", "")
                if (sni.isNotEmpty()) {
                    proxy["servername"] = sni
                } else {
                    val host = json.optString("host", "")
                    if (host.isNotEmpty()) {
                        proxy["servername"] = host.split(",")[0].trim()
                    } else {
                        proxy["servername"] = json.getString("add")
                    }
                }
                
                // ALPN
                val alpn = json.optString("alpn", "")
                if (alpn.isNotEmpty()) {
                    val alpnList = alpn.split(",").map { it.trim() }
                    if (alpnList.isNotEmpty()) {
                        proxy["alpn"] = alpnList
                    }
                }
                
                // Fingerprint
                val fp = json.optString("fp", "")
                if (fp.isNotEmpty()) {
                    proxy["client-fingerprint"] = fp
                }
            }
            
            // 根据网络类型设置选项
            when (net) {
                "ws" -> {
                    val wsOpts = mutableMapOf<String, Any>()
                    val path = json.optString("path", "/")
                    wsOpts["path"] = path
                    
                    val host = json.optString("host", "")
                    if (host.isNotEmpty()) {
                        wsOpts["headers"] = mapOf("Host" to host.split(",")[0].trim())
                    }
                    
                    // Max early data
                    val maxEarlyData = json.optInt("ed", 0)
                    if (maxEarlyData > 0) {
                        wsOpts["max-early-data"] = maxEarlyData
                    }
                    
                    proxy["ws-opts"] = wsOpts
                }
                
                "http", "h2" -> {
                    val httpOpts = mutableMapOf<String, Any>()
                    val path = json.optString("path", "/")
                    httpOpts["path"] = if (path.contains(",")) {
                        path.split(",").map { it.trim() }
                    } else {
                        listOf(path)
                    }
                    
                    val host = json.optString("host", "")
                    if (host.isNotEmpty()) {
                        httpOpts["host"] = if (host.contains(",")) {
                            host.split(",").map { it.trim() }
                        } else {
                            listOf(host)
                        }
                    }
                    
                    if (net == "h2") {
                        proxy["h2-opts"] = httpOpts
                    } else {
                        val method = json.optString("method", "GET")
                        httpOpts["method"] = method
                        httpOpts["headers"] = mapOf<String, List<String>>()
                        proxy["http-opts"] = httpOpts
                    }
                }
                
                "grpc" -> {
                    val grpcOpts = mutableMapOf<String, Any>()
                    val serviceName = json.optString("path", "")
                    if (serviceName.isEmpty()) {
                        grpcOpts["grpc-service-name"] = json.optString("serviceName", "")
                    } else {
                        grpcOpts["grpc-service-name"] = serviceName
                    }
                    proxy["grpc-opts"] = grpcOpts
                }
                
                "quic" -> {
                    // QUIC support
                    proxy["network"] = "quic"
                }
            }
            
            return proxy
        } catch (e: Exception) {
            Log.e(TAG, "解析VMess失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 解析VLess链接（完整支持所有参数）
     */
    private fun parseVless(vlessUrl: String): Map<String, Any>? {
        try {
            // vless://uuid@server:port?参数#备注
            val url = vlessUrl.substring(8) // 移除 "vless://"
            val parts = url.split("@")
            if (parts.size != 2) return null
            
            val uuid = parts[0]
            val remaining = parts[1]
            
            // 分离备注
            val withoutRemark = remaining.substringBefore("#")
            val remark = remaining.substringAfter("#", "VLess节点")
            
            val serverParts = withoutRemark.split("?")
            val serverAndPort = serverParts[0].split(":")
            if (serverAndPort.size != 2) return null
            
            val server = serverAndPort[0]
            val port = serverAndPort[1].toIntOrNull() ?: 443
            
            val proxy = mutableMapOf<String, Any>()
            proxy["name"] = URLDecoder.decode(remark, "UTF-8")
            proxy["type"] = "vless"
            proxy["server"] = server
            proxy["port"] = port
            proxy["uuid"] = uuid
            proxy["cipher"] = "none" // VLess默认使用none
            
            // 解析参数
            if (serverParts.size > 1) {
                val params = serverParts[1].split("&")
                var network = "tcp"
                var security = ""
                var sni = ""
                var path = ""
                var host = ""
                var serviceName = ""
                var alpn = ""
                var fp = ""
                var flow = ""
                
                for (param in params) {
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) {
                        val key = URLDecoder.decode(kv[0].trim(), "UTF-8")
                        val value = URLDecoder.decode(kv[1].trim(), "UTF-8")
                        
                        when (key) {
                            "security" -> {
                                security = value
                                if (value == "tls" || value == "reality") {
                                    proxy["tls"] = true
                                }
                            }
                            "type" -> {
                                network = value
                                proxy["network"] = value
                            }
                            "sni" -> sni = value
                            "path" -> path = value
                            "host" -> host = value
                            "serviceName" -> serviceName = value
                            "alpn" -> alpn = value
                            "fp" -> fp = value
                            "flow" -> flow = value
                            "headerType" -> {
                                // HTTP/2伪装类型
                            }
                            "encryption" -> {
                                // VLess encryption (通常是none)
                                proxy["cipher"] = value
                            }
                        }
                    }
                }
                
                // 设置SNI
                if (security == "tls" || security == "reality") {
                    if (sni.isNotEmpty()) {
                        proxy["servername"] = sni
                    } else if (host.isNotEmpty()) {
                        proxy["servername"] = host.split(",")[0].trim()
                    } else {
                        proxy["servername"] = server
                    }
                    
                    // ALPN
                    if (alpn.isNotEmpty()) {
                        val alpnList = alpn.split(",").map { it.trim() }
                        if (alpnList.isNotEmpty()) {
                            proxy["alpn"] = alpnList
                        }
                    }
                    
                    // Fingerprint
                    if (fp.isNotEmpty()) {
                        proxy["client-fingerprint"] = fp
                    }
                }
                
                // Flow控制（XTLS）
                if (flow.isNotEmpty()) {
                    proxy["flow"] = flow
                }
                
                // 根据网络类型设置选项
                when (network) {
                    "ws" -> {
                        val wsOpts = mutableMapOf<String, Any>()
                        wsOpts["path"] = path.ifEmpty { "/" }
                        if (host.isNotEmpty()) {
                            wsOpts["headers"] = mapOf("Host" to host.split(",")[0].trim())
                        }
                        proxy["ws-opts"] = wsOpts
                    }
                    
                    "http", "h2" -> {
                        val httpOpts = mutableMapOf<String, Any>()
                        val paths = if (path.contains(",")) {
                            path.split(",").map { it.trim() }
                        } else {
                            listOf(path.ifEmpty { "/" })
                        }
                        httpOpts["path"] = paths
                        
                        if (host.isNotEmpty()) {
                            val hosts = if (host.contains(",")) {
                                host.split(",").map { it.trim() }
                            } else {
                                listOf(host)
                            }
                            httpOpts["host"] = hosts
                        }
                        
                        if (network == "h2") {
                            proxy["h2-opts"] = httpOpts
                        } else {
                            proxy["http-opts"] = httpOpts
                        }
                    }
                    
                    "grpc" -> {
                        val grpcOpts = mutableMapOf<String, Any>()
                        val svcName = if (serviceName.isNotEmpty()) serviceName else path
                        if (svcName.isNotEmpty()) {
                            grpcOpts["grpc-service-name"] = svcName
                        }
                        proxy["grpc-opts"] = grpcOpts
                    }
                    
                    "quic" -> {
                        proxy["network"] = "quic"
                    }
                }
            }
            
            return proxy
        } catch (e: Exception) {
            Log.e(TAG, "解析VLess失败: ${e.message}", e)
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
            
            val password = URLDecoder.decode(parts[0], "UTF-8")
            val remaining = parts[1]
            
            // 分离备注
            val withoutRemark = remaining.substringBefore("#")
            val remark = remaining.substringAfter("#", "Trojan节点")
            
            val serverParts = withoutRemark.split("?")
            val serverAndPort = serverParts[0].split(":")
            if (serverAndPort.size != 2) return null
            
            val server = serverAndPort[0]
            val port = serverAndPort[1].toIntOrNull() ?: 443
            
            val proxy = mutableMapOf<String, Any>()
            proxy["name"] = URLDecoder.decode(remark, "UTF-8")
            proxy["type"] = "trojan"
            proxy["server"] = server
            proxy["port"] = port
            proxy["password"] = password
            proxy["sni"] = server // 默认使用server作为SNI
            
            // 解析参数
            if (serverParts.size > 1) {
                val params = serverParts[1].split("&")
                var network = ""
                var path = ""
                var host = ""
                
                for (param in params) {
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) {
                        val key = URLDecoder.decode(kv[0].trim(), "UTF-8")
                        val value = URLDecoder.decode(kv[1].trim(), "UTF-8")
                        
                        when (key) {
                            "sni" -> proxy["sni"] = value
                            "type" -> {
                                network = value
                                proxy["network"] = value
                            }
                            "path" -> path = value
                            "host" -> host = value
                            "security" -> {
                                // Trojan已经默认TLS，这里可以处理其他安全选项
                            }
                        }
                    }
                }
                
                // WebSocket选项
                if (network == "ws") {
                    val wsOpts = mutableMapOf<String, Any>()
                    wsOpts["path"] = path.ifEmpty { "/" }
                    if (host.isNotEmpty()) {
                        wsOpts["headers"] = mapOf("Host" to host)
                    }
                    proxy["ws-opts"] = wsOpts
                }
            }
            
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
     * 生成Clash配置文件（标准YAML格式）
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
        sb.appendLine("mixed-port: 7890")
        sb.appendLine("allow-lan: true")
        sb.appendLine("bind-address: '*'")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("ipv6: true")
        sb.appendLine("external-controller: 127.0.0.1:9090")
        sb.appendLine()
        
        // DNS配置
        sb.appendLine("dns:")
        sb.appendLine("  enable: true")
        sb.appendLine("  listen: 0.0.0.0:53")
        sb.appendLine("  ipv6: false")
        sb.appendLine("  default-nameserver:")
        sb.appendLine("    - 223.5.5.5")
        sb.appendLine("    - 119.29.29.29")
        sb.appendLine("  enhanced-mode: fake-ip")
        sb.appendLine("  fake-ip-range: 198.18.0.1/16")
        sb.appendLine("  fake-ip-filter:")
        sb.appendLine("    - '*.lan'")
        sb.appendLine("    - localhost.ptlogin2.qq.com")
        sb.appendLine("  nameserver:")
        sb.appendLine("    - https://doh.pub/dns-query")
        sb.appendLine("    - https://dns.alidns.com/dns-query")
        sb.appendLine("  fallback:")
        sb.appendLine("    - https://1.0.0.1/dns-query")
        sb.appendLine("    - https://public.dns.iij.jp/dns-query")
        sb.appendLine("  fallback-filter:")
        sb.appendLine("    geoip: true")
        sb.appendLine("    ipcidr:")
        sb.appendLine("      - 240.0.0.0/4")
        sb.appendLine()
        
        // 代理节点 - 使用标准YAML多行格式
        sb.appendLine("proxies:")
        for (proxy in proxies) {
            sb.append(proxyToYaml(proxy))
        }
        
        // 代理组
        sb.appendLine("proxy-groups:")
        
        // 主选择组
        sb.appendLine("  - name: 🚀 节点选择")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - ♻️ 自动选择")
        sb.appendLine("      - DIRECT")
        for (name in proxyNames) {
            sb.appendLine("      - ${escapeYamlString(name)}")
        }
        
        // 自动选择组
        sb.appendLine("  - name: ♻️ 自动选择")
        sb.appendLine("    type: url-test")
        sb.appendLine("    url: http://www.gstatic.com/generate_204")
        sb.appendLine("    interval: 300")
        sb.appendLine("    tolerance: 50")
        sb.appendLine("    proxies:")
        for (name in proxyNames) {
            sb.appendLine("      - ${escapeYamlString(name)}")
        }
        
        // 规则
        sb.appendLine("rules:")
        sb.appendLine("  - DOMAIN-SUFFIX,local,DIRECT")
        sb.appendLine("  - IP-CIDR,127.0.0.0/8,DIRECT")
        sb.appendLine("  - IP-CIDR,172.16.0.0/12,DIRECT")
        sb.appendLine("  - IP-CIDR,192.168.0.0/16,DIRECT")
        sb.appendLine("  - IP-CIDR,10.0.0.0/8,DIRECT")
        sb.appendLine("  - IP-CIDR,17.0.0.0/8,DIRECT")
        sb.appendLine("  - IP-CIDR,100.64.0.0/10,DIRECT")
        sb.appendLine("  - GEOIP,CN,DIRECT")
        sb.appendLine("  - MATCH,🚀 节点选择")
        
        return sb.toString()
    }

    /**
     * 将代理对象转换为标准YAML格式（多行格式，符合Clash规范）
     */
    private fun proxyToYaml(proxy: Map<String, Any>): String {
        val sb = StringBuilder()
        
        // 开始代理节点
        sb.appendLine("  - name: ${escapeYamlString(proxy["name"] as String)}")
        sb.appendLine("    type: ${proxy["type"]}")
        sb.appendLine("    server: ${proxy["server"]}")
        sb.appendLine("    port: ${proxy["port"]}")
        
        // 根据类型输出特定字段
        when (proxy["type"]) {
            "vmess" -> {
                sb.appendLine("    uuid: ${proxy["uuid"]}")
                sb.appendLine("    alterId: ${proxy["alterId"]}")
                sb.appendLine("    cipher: ${proxy["cipher"]}")
                
                val network = proxy["network"] as? String ?: "tcp"
                sb.appendLine("    network: $network")
                sb.appendLine("    udp: true")
                
                // TLS配置
                if (proxy["tls"] == true) {
                    sb.appendLine("    tls: true")
                    val servername = proxy["servername"] as? String
                    if (servername != null) {
                        sb.appendLine("    servername: $servername")
                    }
                    val alpn = proxy["alpn"] as? List<String>
                    if (alpn != null && alpn.isNotEmpty()) {
                        sb.appendLine("    alpn:")
                        alpn.forEach { sb.appendLine("      - $it") }
                    }
                    val fp = proxy["client-fingerprint"] as? String
                    if (fp != null) {
                        sb.appendLine("    client-fingerprint: $fp")
                    }
                    sb.appendLine("    skip-cert-verify: false")
                }
                
                // WebSocket配置
                if (network == "ws") {
                    val wsOpts = proxy["ws-opts"] as? Map<String, Any>
                    if (wsOpts != null) {
                        sb.appendLine("    ws-opts:")
                        sb.appendLine("      path: ${escapeYamlString(wsOpts["path"] as? String ?: "/")}")
                        val headers = wsOpts["headers"] as? Map<String, String>
                        if (headers != null && headers.isNotEmpty()) {
                            sb.appendLine("      headers:")
                            headers.forEach { (k, v) ->
                                sb.appendLine("        $k: ${escapeYamlString(v)}")
                            }
                        }
                        val maxEarlyData = wsOpts["max-early-data"] as? Int
                        if (maxEarlyData != null && maxEarlyData > 0) {
                            sb.appendLine("      max-early-data: $maxEarlyData")
                        }
                    }
                }
                
                // HTTP/H2配置
                if (network == "http") {
                    val httpOpts = proxy["http-opts"] as? Map<String, Any>
                    if (httpOpts != null) {
                        sb.appendLine("    http-opts:")
                        sb.appendLine("      method: ${httpOpts["method"] as? String ?: "GET"}")
                        val path = httpOpts["path"] as? List<String>
                        if (path != null && path.isNotEmpty()) {
                            sb.appendLine("      path:")
                            path.forEach { sb.appendLine("        - ${escapeYamlString(it)}") }
                        }
                        val headers = httpOpts["headers"] as? Map<String, List<String>>
                        if (headers != null && headers.isNotEmpty()) {
                            sb.appendLine("      headers:")
                            headers.forEach { (k, v) ->
                                sb.appendLine("        $k:")
                                v.forEach { value -> sb.appendLine("          - ${escapeYamlString(value)}") }
                            }
                        }
                    }
                } else if (network == "h2") {
                    val h2Opts = proxy["h2-opts"] as? Map<String, Any>
                    if (h2Opts != null) {
                        sb.appendLine("    h2-opts:")
                        val host = h2Opts["host"] as? List<String>
                        if (host != null && host.isNotEmpty()) {
                            sb.appendLine("      host:")
                            host.forEach { sb.appendLine("        - ${escapeYamlString(it)}") }
                        }
                        val path = h2Opts["path"] as? String ?: "/"
                        sb.appendLine("      path: ${escapeYamlString(path)}")
                    }
                }
                
                // GRPC配置
                if (network == "grpc") {
                    val grpcOpts = proxy["grpc-opts"] as? Map<String, Any>
                    if (grpcOpts != null) {
                        sb.appendLine("    grpc-opts:")
                        val serviceName = grpcOpts["grpc-service-name"] as? String
                        if (!serviceName.isNullOrEmpty()) {
                            sb.appendLine("      grpc-service-name: ${escapeYamlString(serviceName)}")
                        }
                    }
                }
            }
            
            "vless" -> {
                sb.appendLine("    uuid: ${proxy["uuid"]}")
                val flow = proxy["flow"] as? String
                if (!flow.isNullOrEmpty()) {
                    sb.appendLine("    flow: $flow")
                }
                
                val network = proxy["network"] as? String ?: "tcp"
                sb.appendLine("    network: $network")
                sb.appendLine("    udp: true")
                
                // TLS配置
                if (proxy["tls"] == true) {
                    sb.appendLine("    tls: true")
                    val servername = proxy["servername"] as? String
                    if (servername != null) {
                        sb.appendLine("    servername: $servername")
                    }
                    val alpn = proxy["alpn"] as? List<String>
                    if (alpn != null && alpn.isNotEmpty()) {
                        sb.appendLine("    alpn:")
                        alpn.forEach { sb.appendLine("      - $it") }
                    }
                    val fp = proxy["client-fingerprint"] as? String
                    if (fp != null) {
                        sb.appendLine("    client-fingerprint: $fp")
                    }
                    sb.appendLine("    skip-cert-verify: false")
                }
                
                // WebSocket配置
                if (network == "ws") {
                    val wsOpts = proxy["ws-opts"] as? Map<String, Any>
                    if (wsOpts != null) {
                        sb.appendLine("    ws-opts:")
                        sb.appendLine("      path: ${escapeYamlString(wsOpts["path"] as? String ?: "/")}")
                        val headers = wsOpts["headers"] as? Map<String, String>
                        if (headers != null && headers.isNotEmpty()) {
                            sb.appendLine("      headers:")
                            headers.forEach { (k, v) ->
                                sb.appendLine("        $k: ${escapeYamlString(v)}")
                            }
                        }
                    }
                }
                
                // GRPC配置
                if (network == "grpc") {
                    val grpcOpts = proxy["grpc-opts"] as? Map<String, Any>
                    if (grpcOpts != null) {
                        sb.appendLine("    grpc-opts:")
                        val serviceName = grpcOpts["grpc-service-name"] as? String
                        if (!serviceName.isNullOrEmpty()) {
                            sb.appendLine("      grpc-service-name: ${escapeYamlString(serviceName)}")
                        }
                    }
                }
                
                // H2配置
                if (network == "h2") {
                    val h2Opts = proxy["h2-opts"] as? Map<String, Any>
                    if (h2Opts != null) {
                        sb.appendLine("    h2-opts:")
                        val host = h2Opts["host"] as? List<String>
                        if (host != null && host.isNotEmpty()) {
                            sb.appendLine("      host:")
                            host.forEach { sb.appendLine("        - ${escapeYamlString(it)}") }
                        }
                        val path = h2Opts["path"] as? List<String>
                        if (path != null && path.isNotEmpty()) {
                            sb.appendLine("      path:")
                            path.forEach { sb.appendLine("        - ${escapeYamlString(it)}") }
                        }
                    }
                }
            }
            
            "trojan" -> {
                sb.appendLine("    password: ${escapeYamlString(proxy["password"] as String)}")
                
                val sni = proxy["sni"] as? String
                if (sni != null) {
                    sb.appendLine("    sni: $sni")
                }
                
                sb.appendLine("    udp: true")
                sb.appendLine("    skip-cert-verify: false")
                
                val network = proxy["network"] as? String
                if (network == "ws") {
                    sb.appendLine("    network: ws")
                    val wsOpts = proxy["ws-opts"] as? Map<String, Any>
                    if (wsOpts != null) {
                        sb.appendLine("    ws-opts:")
                        sb.appendLine("      path: ${escapeYamlString(wsOpts["path"] as? String ?: "/")}")
                        val headers = wsOpts["headers"] as? Map<String, String>
                        if (headers != null && headers.isNotEmpty()) {
                            sb.appendLine("      headers:")
                            headers.forEach { (k, v) ->
                                sb.appendLine("        $k: ${escapeYamlString(v)}")
                            }
                        }
                    }
                } else if (network == "grpc") {
                    sb.appendLine("    network: grpc")
                    val grpcOpts = proxy["grpc-opts"] as? Map<String, Any>
                    if (grpcOpts != null) {
                        sb.appendLine("    grpc-opts:")
                        val serviceName = grpcOpts["grpc-service-name"] as? String
                        if (!serviceName.isNullOrEmpty()) {
                            sb.appendLine("      grpc-service-name: ${escapeYamlString(serviceName)}")
                        }
                    }
                }
            }
            
            "ss" -> {
                sb.appendLine("    cipher: ${proxy["cipher"]}")
                sb.appendLine("    password: ${escapeYamlString(proxy["password"] as String)}")
                
                val udp = proxy["udp"] as? Boolean ?: true
                sb.appendLine("    udp: $udp")
                
                val plugin = proxy["plugin"] as? String
                if (plugin != null) {
                    sb.appendLine("    plugin: $plugin")
                    val pluginOpts = proxy["plugin-opts"] as? Map<String, Any>
                    if (pluginOpts != null) {
                        sb.appendLine("    plugin-opts:")
                        pluginOpts.forEach { (k, v) ->
                            sb.appendLine("      $k: ${if (v is String) escapeYamlString(v) else v}")
                        }
                    }
                }
            }
        }
        
        return sb.toString()
    }

    /**
     * 转义YAML字符串
     */
    private fun escapeYamlString(str: String): String {
        return if (str.contains(":") || str.contains("#") || str.contains("'") || 
                   str.contains("\"") || str.contains("[") || str.contains("]") ||
                   str.contains("{") || str.contains("}") || str.contains("&") ||
                   str.contains("*") || str.contains("!") || str.contains("|") ||
                   str.contains(">") || str.contains("@") || str.contains("`")) {
            "\"${str.replace("\"", "\\\"")}\""
        } else {
            str
        }
    }
}
