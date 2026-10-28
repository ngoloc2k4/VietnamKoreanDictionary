package com.example.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

data class AppLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object AppLogManager {
    private const val MAX_LOGS = 500
    private val logs = ConcurrentLinkedDeque<AppLogEntry>()

    init {
        log("AppLogManager", "Khởi tạo hệ thống AppLogManager nội bộ", "INFO")
    }

    fun log(tag: String, message: String, level: String = "INFO") {
        val entry = AppLogEntry(tag = tag, level = level, message = message)
        logs.add(entry)
        while (logs.size > MAX_LOGS) {
            logs.pollFirst()
        }
    }

    fun getLogs(): List<AppLogEntry> = logs.toList()

    fun clear() {
        logs.clear()
        log("AppLogManager", "Đã xóa toàn bộ log debug", "INFO")
    }
}

object DebugHttpServer {
    private const val PORT = 8888
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun toggleServer(context: Context): Boolean {
        return if (_isRunning.value) {
            stop()
            false
        } else {
            start(context)
            true
        }
    }

    @Synchronized
    fun start(context: Context): Boolean {
        if (_isRunning.value) return true

        return try {
            val socket = ServerSocket(PORT)
            serverSocket = socket
            _isRunning.value = true

            val ip = getDeviceIpAddress(context)
            val url = "http://$ip:$PORT"
            _serverUrl.value = url
            AppLogManager.log("DebugHttpServer", "Server HTTP Debug khởi động tại $url (Cổng $PORT)", "INFO")

            serverJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch { handleClient(client, context) }
                    } catch (e: SocketException) {
                        break
                    } catch (e: Exception) {
                        AppLogManager.log("DebugHttpServer", "Lỗi chấp nhận kết nối: ${e.message}", "ERROR")
                    }
                }
            }
            true
        } catch (e: Exception) {
            AppLogManager.log("DebugHttpServer", "Không thể mở cổng $PORT: ${e.message}", "ERROR")
            _isRunning.value = false
            _serverUrl.value = ""
            false
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            _isRunning.value = false
            _serverUrl.value = ""
            AppLogManager.log("DebugHttpServer", "Server HTTP Debug đã tắt", "INFO")
        } catch (e: Exception) {
            AppLogManager.log("DebugHttpServer", "Lỗi khi tắt server: ${e.message}", "WARN")
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in queryString.split('&')) {
            val idx = pair.indexOf('=')
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            } else if (pair.isNotBlank()) {
                result[URLDecoder.decode(pair, "UTF-8")] = ""
            }
        }
        return result
    }

    private fun filterLogs(params: Map<String, String>): List<AppLogEntry> {
        var list = AppLogManager.getLogs()
        val since = params["since"]?.toLongOrNull()
        if (since != null) {
            list = list.filter { it.timestamp >= since }
        }
        val level = params["level"]?.uppercase(Locale.ROOT)
        if (!level.isNullOrBlank()) {
            list = list.filter { it.level.equals(level, ignoreCase = true) }
        }
        val tag = params["tag"]
        if (!tag.isNullOrBlank()) {
            list = list.filter { it.tag.contains(tag, ignoreCase = true) }
        }
        val limit = params["limit"]?.toIntOrNull() ?: 500
        return list.takeLast(limit)
    }

    private fun handleClient(client: Socket, context: Context) {
        try {
            client.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val output = client.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.ROOT)
            val rawPath = parts[1]
            val path = rawPath.substringBefore('?')
            val queryString = if (rawPath.contains('?')) rawPath.substringAfter('?') else ""
            val queryParams = parseQueryParams(queryString)

            if (method == "OPTIONS") {
                sendNoContent(output)
                return
            }

            when {
                path == "/api/logs" || path == "/logs.json" -> {
                    val filtered = filterLogs(queryParams)
                    val json = JSONObject().apply {
                        put("status", "ok")
                        put("count", filtered.size)
                        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("timestamp", System.currentTimeMillis())
                        val array = JSONArray()
                        for (entry in filtered) {
                            array.put(JSONObject().apply {
                                put("timestamp", entry.timestamp)
                                put("time", entry.formattedTime)
                                put("level", entry.level)
                                put("tag", entry.tag)
                                put("message", entry.message)
                            })
                        }
                        put("logs", array)
                    }
                    sendResponse(output, "application/json; charset=utf-8", json.toString(2))
                }
                path == "/logs" || path == "/logs.txt" -> {
                    val filtered = filterLogs(queryParams)
                    val logText = filtered.joinToString("\n") {
                        "[${it.formattedTime}] [${it.level}] [${it.tag}]: ${it.message}"
                    }
                    sendResponse(output, "text/plain; charset=utf-8", logText)
                }
                path == "/api/status" -> {
                    val report = StorageManager.getStorageReport(context)
                    val json = JSONObject().apply {
                        put("status", "running")
                        put("port", PORT)
                        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("androidVersion", Build.VERSION.RELEASE)
                        put("sdkInt", Build.VERSION.SDK_INT)
                        put("totalLogs", AppLogManager.getLogs().size)
                        put("databaseMb", report.databaseMb)
                        put("cacheMb", report.cacheMb)
                        put("freeBudgetMb", report.freeBudgetMb)
                        put("ipAddress", getDeviceIpAddress(context))
                    }
                    sendResponse(output, "application/json; charset=utf-8", json.toString(2))
                }
                path == "/clear" || path == "/api/clear" -> {
                    AppLogManager.clear()
                    if (path.startsWith("/api/")) {
                        sendResponse(output, "application/json; charset=utf-8", """{"status":"ok","message":"Logs cleared"}""")
                    } else {
                        sendRedirect(output, "/")
                    }
                }
                else -> {
                    val html = buildDashboardHtml(context)
                    sendResponse(output, "text/html; charset=utf-8", html)
                }
            }
        } catch (e: Exception) {
            // Client closed connection or timeout
        } finally {
            try { client.close() } catch (ignored: Exception) {}
        }
    }

    private fun sendNoContent(output: OutputStream) {
        val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))
        writer.print("HTTP/1.1 204 No Content\r\n")
        writer.print("Connection: close\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: *\r\n")
        writer.print("\r\n")
        writer.flush()
    }

    private fun sendResponse(output: OutputStream, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: *\r\n")
        writer.print("Cache-Control: no-cache, no-store, must-revalidate\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    private fun sendRedirect(output: OutputStream, location: String) {
        val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))
        writer.print("HTTP/1.1 302 Found\r\n")
        writer.print("Location: $location\r\n")
        writer.print("Content-Length: 0\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
    }

    private fun getDeviceIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    @Suppress("DEPRECATION")
                    return Formatter.formatIpAddress(ipInt)
                }
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "127.0.0.1"
    }

    private fun buildDashboardHtml(context: Context): String {
        val logs = AppLogManager.getLogs().reversed()
        val report = StorageManager.getStorageReport(context)

        return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>VietKor Debug Console (Port $PORT)</title>
            <style>
                :root {
                    --bg: #0f172a;
                    --card: #1e293b;
                    --border: #334155;
                    --text: #f8fafc;
                    --text-muted: #94a3b8;
                    --primary: #38bdf8;
                    --success: #4ade80;
                    --warn: #facc15;
                    --error: #f87171;
                }
                body {
                    margin: 0;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
                    background-color: var(--bg);
                    color: var(--text);
                    padding: 20px;
                }
                .container { max-width: 1200px; margin: 0 auto; }
                header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 15px; margin-bottom: 20px; }
                h1 { margin: 0; font-size: 20px; color: var(--primary); }
                .badges { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
                .badge { background: var(--card); border: 1px solid var(--border); padding: 8px 14px; border-radius: 8px; font-size: 13px; }
                .badge strong { color: var(--primary); }
                .actions { display: flex; gap: 10px; margin-bottom: 15px; }
                .btn {
                    background: var(--primary); color: #000; font-weight: bold; border: none;
                    padding: 8px 16px; border-radius: 6px; cursor: pointer; text-decoration: none; font-size: 13px;
                }
                .btn-secondary { background: var(--card); color: var(--text); border: 1px solid var(--border); }
                .log-box {
                    background: #090d16; border: 1px solid var(--border); border-radius: 8px;
                    padding: 15px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                    font-size: 13px; max-height: 600px; overflow-y: auto; line-height: 1.5;
                }
                .log-row { padding: 4px 0; border-bottom: 1px solid rgba(255,255,255,0.05); }
                .log-time { color: var(--text-muted); margin-right: 8px; }
                .log-level { font-weight: bold; margin-right: 8px; padding: 2px 6px; border-radius: 4px; font-size: 11px; }
                .log-tag { color: var(--primary); font-weight: 600; margin-right: 8px; }
                .level-INFO { background: rgba(56, 189, 248, 0.2); color: var(--primary); }
                .level-WARN { background: rgba(250, 204, 21, 0.2); color: var(--warn); }
                .level-ERROR { background: rgba(248, 113, 113, 0.2); color: var(--error); }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <div>
                        <h1>⚡ VietKor In-App Debug Server (Cổng $PORT)</h1>
                        <p style="margin: 4px 0 0 0; color: var(--text-muted); font-size: 13px;">
                            Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                        </p>
                    </div>
                    <div>
                        <span style="color: var(--success); font-weight: bold;">● Đang chạy</span>
                    </div>
                </header>

                <div class="badges">
                    <div class="badge">Kho từ: <strong>78,000 từ</strong></div>
                    <div class="badge">Dung lượng DB: <strong>${report.databaseMb} MB</strong></div>
                    <div class="badge">Bộ nhớ Cache: <strong>${report.cacheMb} MB</strong></div>
                    <div class="badge">Tổng số logs: <strong>${logs.size}</strong></div>
                    <div class="badge">Auto-refresh: <strong>3s</strong></div>
                </div>

                <div class="actions">
                    <button class="btn btn-secondary" onclick="location.reload()">🔄 Làm mới</button>
                    <a class="btn btn-secondary" href="/clear">🗑️ Xóa logs</a>
                    <a class="btn btn-secondary" href="/logs" target="_blank">📄 Text Logs</a>
                    <a class="btn btn-secondary" href="/api/logs" target="_blank">🤖 JSON API Logs</a>
                    <a class="btn btn-secondary" href="/api/status" target="_blank">📊 JSON Status</a>
                </div>

                <div style="background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 12px 16px; margin-bottom: 15px; font-size: 13px;">
                    <div style="color: var(--primary); font-weight: bold; margin-bottom: 6px;">🤖 Dành cho Agent / PC đọc Logs qua LAN:</div>
                    <div style="font-family: monospace; color: var(--text-muted); line-height: 1.6;">
                        • Lấy toàn bộ logs JSON: <code style="color: var(--text);">curl -s "http://${getDeviceIpAddress(context)}:$PORT/api/logs"</code><br/>
                        • Polling theo thời gian: <code style="color: var(--text);">curl -s "http://${getDeviceIpAddress(context)}:$PORT/api/logs?since=TIMESTAMP&level=ERROR"</code><br/>
                        • Lọc theo Tag hoặc Limit: <code style="color: var(--text);">curl -s "http://${getDeviceIpAddress(context)}:$PORT/api/logs?tag=Search&limit=50"</code>
                    </div>
                </div>

                <div class="log-box" id="logBox">
                    ${if (logs.isEmpty()) "<p style='color: var(--text-muted); margin: 0;'>Chưa có logs nào được ghi nhận.</p>" else logs.joinToString("") { entry ->
                        "<div class='log-row'>" +
                        "<span class='log-time'>${entry.formattedTime}</span>" +
                        "<span class='log-level level-${entry.level}'>${entry.level}</span>" +
                        "<span class='log-tag'>[${entry.tag}]</span>" +
                        "<span>${escapeHtml(entry.message)}</span>" +
                        "</div>"
                    }}
                </div>
            </div>
            <script>
                setTimeout(() => location.reload(), 3000);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
