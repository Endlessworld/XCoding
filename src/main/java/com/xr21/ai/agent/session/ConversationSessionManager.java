package com.xr21.ai.agent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xr21.ai.agent.entity.ConversationMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对话会话管理器
 * 负责管理对话会话的自动保存和加载
 * 使用JSON格式存储，区分不同消息类型
 */
@Slf4j
@Component
public class ConversationSessionManager {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    private final Map<String, List<ConversationMessage>> sessionMessages;

    private final Map<String, Integer> sessionRounds;

    private final ScheduledExecutorService scheduler;

    private final String saveDirectory;

    private final long saveInterval = 30000;

    private final boolean autoSave = true;

    private Path savePath ;

    public ConversationSessionManager() {
        // 获取用户目录并设置保存路径为 .agi_working/conversations
        String userHome = System.getProperty("user.home");
        this.saveDirectory = userHome + File.separator + ".agi_working" + File.separator + "conversations";
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.sessionMessages = new ConcurrentHashMap<>();
        this.sessionRounds = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        init();
    }

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        try {
            savePath = Paths.get(saveDirectory);
            if (!Files.exists(savePath)) {
                Files.createDirectories(savePath);
            }
            log.info("对话会话保存目录: {}", savePath.toAbsolutePath());

            // 启动定期保存任务
            if (autoSave) {
                scheduler.scheduleAtFixedRate(this::saveAllSessions, saveInterval, saveInterval, TimeUnit.MILLISECONDS);
            }
        } catch (IOException e) {
            log.error("初始化对话会话管理器失败: {}", e.getMessage());
        }
    }

    /**
     * 销毁方法
     */
    @PreDestroy
    public void destroy() {
        saveAllSessions();
        scheduler.shutdown();
    }

    /**
     * 创建新会话
     */
    public String createSession() {
        String sessionId = "session_" + LocalDateTime.now().format(FILE_NAME_FORMATTER);
        sessionMessages.put(sessionId, new ArrayList<>());
        sessionRounds.put(sessionId, 0);
        log.info("创建新会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 获取当前会话ID，如果不存在则创建新会话
     */
    public String getOrCreateSession(String threadId) {
        String sessionId = threadId != null ? threadId : createSession();
        sessionMessages.putIfAbsent(sessionId, new ArrayList<>());
        sessionRounds.putIfAbsent(sessionId, 0);
        return sessionId;
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String sessionId, String content) {
        int round = sessionRounds.getOrDefault(sessionId, 0) + 1;
        ConversationMessage message = ConversationMessage.createUserMessage(sessionId, content, round);
        addMessage(sessionId, message);
        sessionRounds.put(sessionId, round);
    }

    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String sessionId, String content) {
        int round = sessionRounds.getOrDefault(sessionId, 0);
        ConversationMessage message = ConversationMessage.createAssistantMessage(sessionId, content, round);
        addMessage(sessionId, message);
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String sessionId, String content) {
        int round = sessionRounds.getOrDefault(sessionId, 0);
        ConversationMessage message = ConversationMessage.createSystemMessage(sessionId, content, round);
        addMessage(sessionId, message);
    }

    /**
     * 添加错误消息
     */
    public void addErrorMessage(String sessionId, String errorMessage) {
        int round = sessionRounds.getOrDefault(sessionId, 0);
        ConversationMessage message = ConversationMessage.createErrorMessage(sessionId, errorMessage, round);
        addMessage(sessionId, message);
    }

    /**
     * 添加工具调用消息
     */
    public void addToolCallMessage(String sessionId, String toolName, Map<String, Object> arguments, String callId) {
        int round = sessionRounds.getOrDefault(sessionId, 0);
        ConversationMessage message = ConversationMessage.createToolCallMessage(sessionId, toolName, arguments, callId, round);
        addMessage(sessionId, message);
    }

    /**
     * 添加工具响应消息
     */
    public void addToolResponseMessage(String sessionId, String toolName, String response, boolean success) {
        int round = sessionRounds.getOrDefault(sessionId, 0);
        ConversationMessage message = ConversationMessage.createToolResponseMessage(sessionId, toolName, response, success, round);
        addMessage(sessionId, message);
    }

    /**
     * 内部方法：添加消息到会话
     */
    private void addMessage(String sessionId, ConversationMessage message) {
        sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        log.debug("添加消息到会话 {}: type={}", sessionId, message.getType());
    }

    /**
     * 保存所有会话
     */
    public synchronized void saveAllSessions() {
        for (Map.Entry<String, List<ConversationMessage>> entry : sessionMessages.entrySet()) {
            String sessionId = entry.getKey();
            List<ConversationMessage> messages = entry.getValue();
            if (!messages.isEmpty()) {
                saveSession(sessionId, messages);
            }
        }
    }

    /**
     * 保存单个会话到JSON文件
     */
    public void saveSession(String sessionId, List<ConversationMessage> messages) {
        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String fileName = sessionId + "_" + date + ".json";
            Path filePath = savePath.resolve(fileName);

            // 创建会话记录对象
            ConversationSession session = ConversationSession.builder()
                    .sessionId(sessionId)
                    .createdAt(messages.get(0).getTimestamp())
                    .lastUpdated(LocalDateTime.now())
                    .messageCount(messages.size())
                    .messages(messages)
                    .build();

            // 按消息类型统计
            Map<ConversationMessage.MessageType, Long> typeCount = messages.stream()
                    .collect(Collectors.groupingBy(ConversationMessage::getType, Collectors.counting()));
            session.setTypeStatistics(typeCount);

            objectMapper.writeValue(filePath.toFile(), session);
            log.info("会话 {} 已保存到: {} (消息数: {})", sessionId, filePath, messages.size());
        } catch (IOException e) {
            log.error("保存会话 {} 失败: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 加载会话历史
     */
    public List<ConversationMessage> loadSessionHistory(String threadId) {
        String sessionId = getOrCreateSession(threadId);
        List<ConversationMessage> messages = sessionMessages.getOrDefault(sessionId, new ArrayList<>());

        // 尝试从文件加载
        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String fileName = sessionId + "_" + date + ".json";
            Path filePath = savePath.resolve(fileName);

            if (Files.exists(filePath)) {
                ConversationSession session = objectMapper.readValue(filePath.toFile(), ConversationSession.class);
                if (session.getMessages() != null) {
                    messages = session.getMessages();
                    sessionMessages.put(sessionId, messages);
                    // 更新会话轮次
                    int maxRound = messages.stream().mapToInt(ConversationMessage::getRound).max().orElse(0);
                    sessionRounds.put(sessionId, maxRound);
                    log.info("从文件加载会话 {}: {} 条消息", sessionId, messages.size());
                }
            }
        } catch (IOException e) {
            log.warn("加载会话历史失败: {}", e.getMessage());
        }

        return messages;
    }

    /**
     * 获取会话消息统计
     */
    public Map<String, Object> getSessionStatistics(String sessionId) {
        List<ConversationMessage> messages = sessionMessages.getOrDefault(sessionId, new ArrayList<>());
        Map<String, Object> stats = new HashMap<>();

        stats.put("sessionId", sessionId);
        stats.put("totalMessages", messages.size());
        stats.put("currentRound", sessionRounds.getOrDefault(sessionId, 0));

        // 按类型统计
        Map<ConversationMessage.MessageType, Long> typeCount = messages.stream()
                .collect(Collectors.groupingBy(ConversationMessage::getType, Collectors.counting()));
        stats.put("typeStatistics", typeCount);

        // 计算会话时长
        if (!messages.isEmpty()) {
            LocalDateTime start = messages.get(0).getTimestamp();
            LocalDateTime end = messages.get(messages.size() - 1).getTimestamp();
            long durationMinutes = java.time.Duration.between(start, end).toMinutes();
            stats.put("durationMinutes", durationMinutes);
        }

        return stats;
    }

    /**
     * 按消息类型获取消息
     */
    public Map<ConversationMessage.MessageType, List<ConversationMessage>> getMessagesByType(String sessionId) {
        List<ConversationMessage> messages = sessionMessages.getOrDefault(sessionId, new ArrayList<>());
        return messages.stream().collect(Collectors.groupingBy(ConversationMessage::getType));
    }

    /**
     * 导出会话为JSON字符串
     */
    public String exportSessionAsJson(String sessionId) {
        try {
            List<ConversationMessage> messages = sessionMessages.getOrDefault(sessionId, new ArrayList<>());
            ConversationSession session = ConversationSession.builder()
                    .sessionId(sessionId)
                    .createdAt(messages.isEmpty() ? LocalDateTime.now() : messages.get(0).getTimestamp())
                    .lastUpdated(LocalDateTime.now())
                    .messageCount(messages.size())
                    .messages(messages)
                    .build();
            return objectMapper.writeValueAsString(session);
        } catch (IOException e) {
            log.error("导出会话失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 清空会话
     */
    public void clearSession(String sessionId) {
        sessionMessages.remove(sessionId);
        sessionRounds.remove(sessionId);
        log.info("会话 {} 已清空", sessionId);
    }

    /**
     * 获取所有会话ID列表
     */
    public List<String> getAllSessionIds() {
        return new ArrayList<>(sessionMessages.keySet());
    }

    /**
     * 获取会话文件列表
     */
    public List<String> getSessionFiles() {
        try {
            return Files.walk(savePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("获取会话文件列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取会话文件简要信息列表
     */
    public List<SessionInfo> getSessionInfoList() {
        List<SessionInfo> sessionInfoList = new ArrayList<>();
        try {
            List<Path> sessionFiles = Files.walk(savePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        // 按修改时间倒序排序
                        try {
                            long timeA = Files.getLastModifiedTime(a).toMillis();
                            long timeB = Files.getLastModifiedTime(b).toMillis();
                            return Long.compare(timeB, timeA);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());

            for (Path filePath : sessionFiles) {
                try {
                    ConversationSession session = objectMapper.readValue(filePath.toFile(), ConversationSession.class);
                    SessionInfo info = new SessionInfo();
                    info.setSessionId(session.getSessionId());
                    info.setFilePath(filePath.toString());
                    info.setMessageCount(session.getMessageCount());
                    info.setCreatedAt(session.getCreatedAt() != null ? 
                        session.getCreatedAt().format(TIME_FORMATTER) : "未知");
                    info.setLastUpdated(session.getLastUpdated() != null ? 
                        session.getLastUpdated().format(TIME_FORMATTER) : "未知");
                    
                    // 提取会话简要描述（第一条用户消息）
                    if (session.getMessages() != null && !session.getMessages().isEmpty()) {
                        String firstUserMessage = session.getMessages().stream()
                                .filter(m -> m.getType() == ConversationMessage.MessageType.USER)
                                .findFirst()
                                .map(ConversationMessage::getContent)
                                .orElse("无用户消息");
                        
                        // 截取前50个字符
                        info.setBriefDescription(firstUserMessage.length() > 50 ? 
                            firstUserMessage.substring(0, 50) + "..." : firstUserMessage);
                    } else {
                        info.setBriefDescription("空会话");
                    }
                    
                    sessionInfoList.add(info);
                } catch (IOException e) {
                    log.warn("读取会话文件信息失败: {}", filePath);
                }
            }
        } catch (IOException e) {
            log.error("扫描会话目录失败: {}", e.getMessage());
        }
        return sessionInfoList;
    }

    /**
     * 根据会话ID加载会话
     */
    public boolean loadSessionById(String sessionId) {
        try {
            if (savePath == null) {
                return false;
            }
            // 查找对应的会话文件
            List<Path> matchingFiles = Files.walk(savePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.getFileName().toString().startsWith(sessionId + "_"))
                    .collect(Collectors.toList());

            if (matchingFiles.isEmpty()) {
                log.warn("未找到会话文件: {}", sessionId);
                return false;
            }

            // 加载第一个匹配的文件
            Path filePath = matchingFiles.get(0);
            ConversationSession session = objectMapper.readValue(filePath.toFile(), ConversationSession.class);
            
            if (session.getSessionId() != null && session.getMessages() != null) {
                sessionMessages.put(session.getSessionId(), session.getMessages());
                int maxRound = session.getMessages().stream()
                        .mapToInt(ConversationMessage::getRound)
                        .max()
                        .orElse(0);
                sessionRounds.put(session.getSessionId(), maxRound);
                log.info("加载会话: {} ({} 条消息)", session.getSessionId(), session.getMessages().size());
                return true;
            }
        } catch (IOException e) {
            log.error("加载会话失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 从文件加载会话
     */
    public boolean loadSessionFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("会话文件不存在: {}", filePath);
                return false;
            }

            ConversationSession session = objectMapper.readValue(file, ConversationSession.class);
            if (session.getSessionId() != null && session.getMessages() != null) {
                sessionMessages.put(session.getSessionId(), session.getMessages());
                int maxRound = session.getMessages().stream().mapToInt(ConversationMessage::getRound).max().orElse(0);
                sessionRounds.put(session.getSessionId(), maxRound);
                log.info("从文件加载会话: {} ({} 条消息)", session.getSessionId(), session.getMessages().size());
                return true;
            }
        } catch (IOException e) {
            log.error("加载会话文件失败: {}", e.getMessage());
        }
        return false;
    }
}
