package com.xr21.ai.agent.auth

import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 授权管理器：负责授权码的本地持久化存储和 HTTP 回调服务器的管理。
 */
object AuthManager {

    private val authDir: Path = Paths.get(System.getProperty("user.home"), ".agi_working", "auth")
    private val authFile: Path = authDir.resolve("auth_code.json")

    data class AuthInfo(
        val code: String,
        val timestamp: Long
    )

    /**
     * 检查本地是否已存储授权码。
     */
    fun isAuthorized(): Boolean {
        return try {
            Files.exists(authFile) && loadAuthInfo()?.code?.isNotBlank() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 加载本地授权信息。
     */
    fun loadAuthInfo(): AuthInfo? {
        if (!Files.exists(authFile)) return null
        return try {
            val content = Files.readString(authFile)
            val parts = content.split("\n", limit = 2)
            AuthInfo(parts[0].trim(), parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存授权码到本地。
     */
    fun saveAuthCode(code: String) {
        Files.createDirectories(authDir)
        Files.writeString(authFile, "$code\n${System.currentTimeMillis()}")
    }

    /**
     * 清除本地授权码。
     */
    fun clearAuthCode() {
        try {
            Files.deleteIfExists(authFile)
        } catch (_: Exception) {
        }
    }

    /**
     * 使用系统默认浏览器打开指定 URL。
     */
    fun openBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                // Linux/macOS fallback
                val os = System.getProperty("os.name").lowercase()
                val command = when {
                    os.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
                    os.contains("mac") -> arrayOf("open", url)
                    else -> arrayOf("xdg-open", url)
                }
                Runtime.getRuntime().exec(command)
            }
        } catch (e: Exception) {
            // Last resort
            try {
                Runtime.getRuntime().exec("open $url")
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 启动 HTTP 回调服务器，等待授权码回调。
     * 回调URL格式: http://localhost:{port}/callback?code=xxx
     *
     * @param port 回调服务器端口，0 表示自动分配
     * @param timeoutSeconds 等待超时时间（秒）
     * @return 授权码，超时返回 null
     */
    fun startCallbackServerAndWait(
        port: Int = 0,
        timeoutSeconds: Long = 120
    ): String? {
        val future = CompletableFuture<String>()
        val actualPort = if (port == 0) findFreePort() else port

        val server = HttpServer.create(InetSocketAddress(actualPort), 0)
        server.createContext("/callback") { exchange ->
            val query = exchange.requestURI.query ?: ""
            val code = extractParam(query, "code")
            val response = if (code != null) {
                """<html><body><h2>授权成功</h2><p>请返回 IDE 继续操作。</p></body></html>"""
            } else {
                """<html><body><h2>授权失败</h2><p>未收到授权码。</p></body></html>"""
            }
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
            if (code != null) {
                future.complete(code)
            }
        }
        server.executor = null
        server.start()

        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } finally {
            server.stop(0)
        }
    }

    /**
     * 获取回调服务器实际使用的端口（用于构造登录URL）。
     */
    fun findFreePort(): Int {
        return java.net.ServerSocket(0).use { it.localPort }
    }

    private fun extractParam(query: String, key: String): String? {
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it[0] == key }
            ?.getOrNull(1)
    }
}
