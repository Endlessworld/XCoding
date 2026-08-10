//! 数据模型层：消息、会话、Todo、Token、枚举

use chrono::Local;

/// 消息角色（对齐 Java 版 MessageRole）
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MessageRole {
    User,
    Assistant,
    System,
    ToolCall,
    ToolResult,
    Error,
}

impl MessageRole {
    pub fn label(self) -> &'static str {
        match self {
            MessageRole::User => "你",
            MessageRole::Assistant => "AI",
            MessageRole::System => "系统",
            MessageRole::ToolCall => "工具",
            MessageRole::ToolResult => "结果",
            MessageRole::Error => "错误",
        }
    }

    pub fn emoji(self) -> &'static str {
        match self {
            MessageRole::User => "👤",
            MessageRole::Assistant => "🤖",
            MessageRole::System => "⚙",
            MessageRole::ToolCall => "🔧",
            MessageRole::ToolResult => "📎",
            MessageRole::Error => "❌",
        }
    }
}

/// 单条聊天消息
#[derive(Debug, Clone)]
pub struct ChatMessage {
    pub id: String,
    pub role: MessageRole,
    pub timestamp: String, // HH:mm
    pub content: String,
    pub is_streaming: bool,
    pub is_expanded: bool,
    pub tool_call_id: Option<String>,
    pub tool_status: Option<String>,
}

impl ChatMessage {
    pub fn new(role: MessageRole, content: impl Into<String>) -> Self {
        Self {
            id: uuid_like(),
            role,
            timestamp: Local::now().format("%H:%M").to_string(),
            content: content.into(),
            is_streaming: false,
            is_expanded: true,
            tool_call_id: None,
            tool_status: None,
        }
    }

    pub fn streaming(role: MessageRole) -> Self {
        let mut m = Self::new(role, "");
        m.is_streaming = true;
        m
    }
}

/// 会话
#[derive(Debug, Clone)]
pub struct Session {
    pub id: String,
    pub name: String,
    pub messages: Vec<ChatMessage>,
}

impl Session {
    pub fn new() -> Self {
        Self {
            id: uuid_like(),
            name: "New Session".to_string(),
            messages: Vec::new(),
        }
    }

    pub fn auto_name(&mut self) {
        if let Some(first) = self.messages.iter().find(|m| m.role == MessageRole::User) {
            let name = first.content.trim().chars().take(20).collect::<String>();
            if !name.is_empty() {
                self.name = name;
            }
        }
    }
}

impl Default for Session {
    fn default() -> Self {
        Self::new()
    }
}

/// Todo 状态
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TodoStatus {
    Pending,
    InProgress,
    Completed,
    Failed,
    Skipped,
}

impl TodoStatus {
    pub fn icon(self) -> &'static str {
        match self {
            TodoStatus::Pending => "○",
            TodoStatus::InProgress => "◌",
            TodoStatus::Completed => "✓",
            TodoStatus::Failed => "✗",
            TodoStatus::Skipped => "—",
        }
    }
}

/// Todo 优先级
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TodoPriority {
    High,
    Medium,
    Low,
}

/// Todo 项
#[derive(Debug, Clone)]
pub struct TodoItem {
    pub id: String,
    pub content: String,
    pub status: TodoStatus,
    pub priority: TodoPriority,
}

impl TodoItem {
    pub fn new(content: impl Into<String>, status: TodoStatus, priority: TodoPriority) -> Self {
        Self {
            id: uuid_like(),
            content: content.into(),
            status,
            priority,
        }
    }
}

/// Token 用量
#[derive(Debug, Clone, Copy, Default)]
pub struct TokenUsage {
    pub prompt_tokens: u64,
    pub completion_tokens: u64,
    pub total_tokens: u64,
}

/// 连接状态
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ConnectionState {
    #[default]
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Error,
}

impl ConnectionState {
    pub fn label(self) -> &'static str {
        match self {
            ConnectionState::Disconnected => "○ 断开",
            ConnectionState::Connecting => "◌ 连接中",
            ConnectionState::Connected => "● 已连接",
            ConnectionState::Reconnecting => "◌ 重连中",
            ConnectionState::Error => "✕ 错误",
        }
    }
}

fn uuid_like() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("{:016x}", nanos & 0xFFFF_FFFF_FFFF_FFFF)
}
