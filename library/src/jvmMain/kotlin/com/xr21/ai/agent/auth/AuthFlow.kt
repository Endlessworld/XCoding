package com.xr21.ai.agent.auth

import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

private val logger = KotlinLogging.logger {}

/**
 * 授权流程协调器。
 *
 * 流程：
 * 1. 检查本地授权码 → 若已存在直接返回
 * 2. 发送 requestPermissions → 客户端显示"登录"按钮
 * 3. 用户允许 → Agent 启动 HTTP 回调服务器 + 打开浏览器
 * 4. 等待回调 → 保存授权码
 */
object AuthFlow {

    /** 登录页面URL模板，{port} 会被替换为回调服务器端口 */
    private const val LOGIN_URL_TEMPLATE = "https://forum.xr21.me/user-sign/?tab=signin&redirect_to=http://localhost:{port}/callback"

    /** 等待授权回调的超时时间（毫秒） */
    private const val CALLBACK_TIMEOUT_MS = 120_000L

    /**
     * 执行授权流程。如果本地已有授权码则直接返回 true。
     *
     * @param clientOps 客户端会话操作接口
     * @return true=已授权（本地已有或本次授权成功），false=用户拒绝或超时
     */
    suspend fun ensureAuthorized(clientOps: ClientSessionOperations): Boolean {
        // 1. 检查本地授权码
        if (AuthManager.isAuthorized()) {
            logger.info { "Auth: 本地已存在授权码，跳过授权流程" }
            return true
        }

        logger.info { "Auth: 本地无授权码，发起授权请求" }

        // 2. 发送权限请求给客户端
        val toolCallUpdate = SessionUpdate.ToolCallUpdate(
            toolCallId = ToolCallId("auth-login-${System.currentTimeMillis()}"),
            title = "需要登录授权",
            kind = ToolKind.OTHER,
            status = ToolCallStatus.PENDING,
        )

        val permissionOptions = listOf(
            PermissionOption(
                optionId = PermissionOptionId("allow_login"),
                name = "允许登录",
                kind = PermissionOptionKind.ALLOW_ONCE
            ),
            PermissionOption(
                optionId = PermissionOptionId("cancel"),
                name = "取消",
                kind = PermissionOptionKind.REJECT_ONCE
            )
        )

        val response = clientOps.requestPermissions(toolCallUpdate, permissionOptions, null)

        // 3. 检查用户选择
        return when (val outcome = response.outcome) {
            is RequestPermissionOutcome.Cancelled -> {
                logger.info { "Auth: 用户取消了授权请求" }
                false
            }
            is RequestPermissionOutcome.Selected -> {
                if (outcome.optionId.value == "allow_login") {
                    logger.info { "Auth: 用户允许登录，启动浏览器授权" }
                    doBrowserAuth()
                } else {
                    logger.info { "Auth: 用户选择了取消" }
                    false
                }
            }
        }
    }

    /**
     * 执行浏览器授权：启动 HTTP 回调服务器 + 打开浏览器 + 等待回调。
     */
    private suspend fun doBrowserAuth(): Boolean = coroutineScope {
        // 先分配端口，用于构造登录URL
        val callbackPort = AuthManager.findFreePort()
        val loginUrl = LOGIN_URL_TEMPLATE.replace("{port}", callbackPort.toString())
        logger.info { "Auth: 回调端口=$callbackPort, 登录URL=$loginUrl" }

        // 在后台启动 HTTP 回调服务器等待
        val callbackDeferred = async(Dispatchers.IO) {
            AuthManager.startCallbackServerAndWait(port = callbackPort, timeoutSeconds = CALLBACK_TIMEOUT_MS / 1000)
        }

        // 打开浏览器
        AuthManager.openBrowser(loginUrl)

        // 等待回调结果
        val authCode = withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
            callbackDeferred.await()
        }

        if (authCode != null) {
            logger.info { "Auth: 收到授权码，保存到本地" }
            AuthManager.saveAuthCode(authCode)
            true
        } else {
            logger.warn { "Auth: 等待授权回调超时" }
            false
        }
    }
}
