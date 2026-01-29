package com.xr21.ai.agent.gui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话运行状态
 */
enum class SessionStatus {
    IDLE,      // 空闲
    RUNNING,   // 运行中
    COMPLETED, // 已完成
    ERROR      // 错误
}

/**
 * 会话状态数据
 */
data class SessionState(
    val sessionId: String,
    val status: SessionStatus = SessionStatus.IDLE,
    val lastActiveTime: Long = System.currentTimeMillis()
)

/**
 * 会话状态追踪器
 * 单例模式，维护所有会话的运行状态
 */
class SessionStateTracker private constructor() {

    private val sessionStates = ConcurrentHashMap<String, SessionState>()

    // 可观察的状态流
    private val _statesFlow = MutableStateFlow<List<SessionState>>(emptyList())
    val statesFlow: StateFlow<List<SessionState>> = _statesFlow.asStateFlow()

    // 正在运行的会话ID列表
    private val _runningSessions = MutableStateFlow<Set<String>>(emptySet())
    val runningSessions: StateFlow<Set<String>> = _runningSessions.asStateFlow()

    companion object {
        @Volatile
        private var instance: SessionStateTracker? = null

        fun getInstance(): SessionStateTracker {
            return instance ?: synchronized(this) {
                instance ?: SessionStateTracker().also { instance = it }
            }
        }
    }

    /**
     * 更新会话状态
     */
    fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        val newState = SessionState(
            sessionId = sessionId,
            status = status,
            lastActiveTime = System.currentTimeMillis()
        )
        sessionStates[sessionId] = newState
        emitUpdate()
        updateRunningSessions()
    }

    /**
     * 获取单个会话状态
     */
    fun getSessionState(sessionId: String): SessionState? {
        return sessionStates[sessionId]
    }

    /**
     * 获取所有会话状态
     */
    fun getAllStates(): List<SessionState> {
        return sessionStates.values.toList()
    }

    /**
     * 获取正在运行的会话ID列表
     */
    fun getRunningSessionIds(): Set<String> {
        return sessionStates.filter { it.value.status == SessionStatus.RUNNING }.keys
    }

    /**
     * 检查会话是否正在运行
     */
    fun isSessionRunning(sessionId: String): Boolean {
        return sessionStates[sessionId]?.status == SessionStatus.RUNNING
    }

    /**
     * 清除会话状态
     */
    fun clearSessionState(sessionId: String) {
        sessionStates.remove(sessionId)
        emitUpdate()
        updateRunningSessions()
    }

    /**
     * 清除所有状态
     */
    fun clearAll() {
        sessionStates.clear()
        emitUpdate()
        updateRunningSessions()
    }

    /**
     * 注册新会话（初始状态为 IDLE）
     */
    fun registerSession(sessionId: String) {
        if (!sessionStates.containsKey(sessionId)) {
            sessionStates[sessionId] = SessionState(
                sessionId = sessionId,
                status = SessionStatus.IDLE,
                lastActiveTime = System.currentTimeMillis()
            )
            emitUpdate()
        }
    }

    /**
     * 批量注册会话
     */
    fun registerSessions(sessionIds: List<String>) {
        sessionIds.forEach { sessionId ->
            if (!sessionStates.containsKey(sessionId)) {
                sessionStates[sessionId] = SessionState(
                    sessionId = sessionId,
                    status = SessionStatus.IDLE,
                    lastActiveTime = System.currentTimeMillis()
                )
            }
        }
        emitUpdate()
    }

    private fun emitUpdate() {
        _statesFlow.value = sessionStates.values.toList()
    }

    private fun updateRunningSessions() {
        _runningSessions.value = sessionStates
            .filter { it.value.status == SessionStatus.RUNNING }
            .keys
    }
}
