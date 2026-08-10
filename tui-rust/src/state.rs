//! AppState：全局可变状态
//! 由 UI 线程串行修改（通过 TuiEvent 通道投递），避免锁竞争。

use crate::model::{
    ChatMessage, ConnectionState, MessageRole, Session, TodoItem, TodoStatus, TokenUsage,
};

/// 焦点面板
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum FocusPanel {
    Center,
    #[default]
    Input,
    Left,
}

/// 输入状态
#[derive(Debug, Clone, Default)]
pub struct InputState {
    pub buffer: String,
    pub cursor: usize,
    pub history: Vec<String>,
    pub history_index: Option<usize>,
    pub scroll_offset: usize,
    /// 进入历史导航前保存的草稿，返回时恢复
    pub draft: String,
}

impl InputState {
    /// 编辑时退出历史导航模式（清空索引与草稿）
    fn exit_history_nav(&mut self) {
        if self.history_index.is_some() {
            self.history_index = None;
            self.draft.clear();
        }
    }

    pub fn insert_char(&mut self, c: char) {
        self.exit_history_nav();
        self.buffer.insert(self.cursor.min(self.buffer.len()), c);
        self.cursor += 1;
    }

    pub fn insert_str(&mut self, s: &str) {
        self.exit_history_nav();
        let pos = self.cursor.min(self.buffer.len());
        self.buffer.insert_str(pos, s);
        self.cursor += s.len();
    }

    pub fn backspace(&mut self) {
        if self.cursor > 0 && self.cursor <= self.buffer.len() {
            self.buffer.remove(self.cursor - 1);
            self.cursor -= 1;
        }
    }

    pub fn delete_after(&mut self) {
        if self.cursor < self.buffer.len() {
            self.buffer.remove(self.cursor);
        }
    }

    pub fn cursor_left(&mut self) {
        self.cursor = self.cursor.saturating_sub(1);
    }

    pub fn cursor_right(&mut self) {
        if self.cursor < self.buffer.len() {
            self.cursor += 1;
        }
    }

    pub fn history_prev(&mut self) {
        if self.history.is_empty() {
            return;
        }
        let idx = match self.history_index {
            Some(i) if i > 0 => i - 1,
            _ => 0,
        };
        // 首次进入历史导航时保存当前草稿，以便 ↓ 返回
        if self.history_index.is_none() {
            self.draft = self.buffer.clone();
        }
        self.history_index = Some(idx);
        self.buffer = self.history[idx].clone();
        self.cursor = self.buffer.len();
    }

    pub fn history_next(&mut self) {
        match self.history_index {
            Some(i) if i + 1 < self.history.len() => {
                let idx = i + 1;
                self.history_index = Some(idx);
                self.buffer = self.history[idx].clone();
            }
            _ => {
                self.history_index = None;
                // 回到草稿（若有），否则清空
                self.buffer = std::mem::take(&mut self.draft);
            }
        }
        self.cursor = self.buffer.len();
    }

    /// 光标所在行号（按 \n 分割）
    pub fn cursor_line(&self) -> usize {
        self.buffer[..self.cursor.min(self.buffer.len())]
            .matches('\n')
            .count()
    }

    /// 光标在当前行的列偏移（字符数）
    pub fn cursor_col(&self) -> usize {
        let prefix = &self.buffer[..self.cursor.min(self.buffer.len())];
        match prefix.rfind('\n') {
            Some(idx) => prefix[idx + 1..].chars().count(),
            None => prefix.chars().count(),
        }
    }

    pub fn push_history(&mut self, text: &str) {
        if text.trim().is_empty() {
            return;
        }
        self.history.push(text.to_string());
        if self.history.len() > 100 {
            self.history.remove(0);
        }
        self.history_index = None;
        self.draft.clear();
    }
}

/// 滚动状态（Integer.MAX_VALUE 语义 -> usize::MAX 表示底部）
#[derive(Debug, Clone, Copy, Default)]
pub struct ScrollState {
    pub offset: usize,
}

impl ScrollState {
    pub const BOTTOM: usize = usize::MAX;

    pub fn scroll_up(&mut self, n: usize) {
        if self.offset != Self::BOTTOM {
            self.offset = self.offset.saturating_sub(n);
        }
    }

    pub fn scroll_down(&mut self, n: usize) {
        if self.offset != Self::BOTTOM {
            self.offset = self.offset.saturating_add(n);
        }
    }

    pub fn page_up(&mut self, page: usize) {
        self.scroll_up(page);
    }

    pub fn page_down(&mut self, page: usize) {
        self.scroll_down(page);
    }

    pub fn to_bottom(&mut self) {
        self.offset = Self::BOTTOM;
    }
}

/// 弹窗状态
#[derive(Debug, Clone, Default)]
pub struct PopupState {
    pub visible: bool,
    pub selected: usize,
    pub tab_index: usize, // 0=会话列表, 1=模型, 2=模式
}

/// 配置选项项
#[derive(Debug, Clone)]
pub struct ConfigOption {
    pub id: String,
    pub value: String,
}

/// AppState
#[derive(Debug, Default)]
pub struct AppState {
    pub sessions: Vec<Session>,
    pub current_session: usize,
    pub input: InputState,
    pub scroll: ScrollState,
    pub focus: FocusPanel,
    pub popup: PopupState,
    pub connection: ConnectionState,
    pub agent_name: String,
    pub agent_version: String,
    pub current_model: Option<String>,
    pub current_mode: Option<String>,
    pub available_models: Vec<String>,
    pub available_modes: Vec<String>,
    pub config_options: Vec<ConfigOption>,
    pub todos: Vec<TodoItem>,
    pub token_usage: TokenUsage,
    pub is_streaming: bool,
    pub error_message: Option<String>,
}

impl AppState {
    pub fn new() -> Self {
        let mut s = Self::default();
        s.focus = FocusPanel::Input;
        s.sessions.push(Session::new());
        s.connection = ConnectionState::Connecting;
        s
    }

    pub fn current(&self) -> &Session {
        &self.sessions[self.current_session]
    }

    pub fn current_mut(&mut self) -> &mut Session {
        &mut self.sessions[self.current_session]
    }

    pub fn session_count(&self) -> usize {
        self.sessions.len()
    }

    // ---- 会话生命周期 ----

    pub fn new_session(&mut self) {
        self.sessions.push(Session::new());
        self.current_session = self.sessions.len() - 1;
        self.scroll.to_bottom();
        self.focus = FocusPanel::Input;
    }

    pub fn close_current_session(&mut self) {
        if self.sessions.len() <= 1 {
            self.sessions[0].messages.clear();
            self.sessions[0].name = "New Session".to_string();
            return;
        }
        self.sessions.remove(self.current_session);
        if self.current_session >= self.sessions.len() {
            self.current_session = self.sessions.len() - 1;
        }
        self.scroll.to_bottom();
    }

    pub fn clear_conversation(&mut self) {
        self.current_mut().messages.clear();
        self.scroll.to_bottom();
    }

    // ---- 消息 ----

    pub fn send_message(&mut self, text: String) {
        let trimmed = text.trim().to_string();
        if trimmed.is_empty() {
            return;
        }
        let user_msg = ChatMessage::new(MessageRole::User, trimmed.clone());
        self.current_mut().messages.push(user_msg);
        self.input.buffer.clear();
        self.input.cursor = 0;
        self.current_mut().auto_name();
        self.is_streaming = true;
        // 追加一个空的流式 assistant 消息
        let stream_msg = ChatMessage::streaming(MessageRole::Assistant);
        self.current_mut().messages.push(stream_msg);
        self.scroll.to_bottom();
    }

    pub fn append_streaming_content(&mut self, text: &str) {
        let session = self.current_mut();
        if let Some(msg) = session.messages.iter_mut().find(|m| m.is_streaming) {
            msg.content.push_str(text);
        } else {
            let mut m = ChatMessage::streaming(MessageRole::Assistant);
            m.content.push_str(text);
            session.messages.push(m);
        }
        self.scroll.to_bottom();
    }

    pub fn finish_streaming(&mut self) {
        for msg in self.current_mut().messages.iter_mut() {
            msg.is_streaming = false;
        }
        self.is_streaming = false;
    }

    pub fn add_tool_call(&mut self, id: String, content: String) {
        self.finish_streaming();
        let mut m = ChatMessage::new(MessageRole::ToolCall, content);
        m.tool_call_id = Some(id);
        m.tool_status = Some("IN_PROGRESS".to_string());
        self.current_mut().messages.push(m);
        self.scroll.to_bottom();
    }

    pub fn update_tool_call(&mut self, id: &str, status: &str, content: Option<&str>) {
        let session = self.current_mut();
        if let Some(msg) = session
            .messages
            .iter_mut()
            .find(|m| m.tool_call_id.as_deref() == Some(id))
        {
            msg.tool_status = Some(status.to_string());
            if let Some(c) = content {
                msg.content = c.to_string();
            }
        } else {
            self.finish_streaming();
            let mut m = ChatMessage::new(MessageRole::ToolCall, content.unwrap_or(""));
            m.tool_call_id = Some(id.to_string());
            m.tool_status = Some(status.to_string());
            self.current_mut().messages.push(m);
        }
        self.scroll.to_bottom();
    }

    pub fn add_tool_result(&mut self, content: String) {
        let truncated = if content.chars().count() > 500 {
            content.chars().take(500).collect::<String>()
        } else {
            content
        };
        let m = ChatMessage::new(MessageRole::ToolResult, truncated);
        self.current_mut().messages.push(m);
        self.scroll.to_bottom();
    }

    pub fn add_system_message(&mut self, content: String) {
        let m = ChatMessage::new(MessageRole::System, content);
        self.current_mut().messages.push(m);
        self.scroll.to_bottom();
    }

    pub fn add_error_message(&mut self, content: String) {
        let m = ChatMessage::new(MessageRole::Error, content.clone());
        self.current_mut().messages.push(m);
        self.error_message = Some(content);
        self.scroll.to_bottom();
    }

    // ---- Todo / Token ----

    pub fn clear_todos(&mut self) {
        self.todos.clear();
    }

    pub fn add_todo(&mut self, content: String, status: TodoStatus) {
        self.todos.push(TodoItem::new(content, status, crate::model::TodoPriority::Medium));
    }

    pub fn update_token_usage(&mut self, prompt: u64, completion: u64, total: u64) {
        self.token_usage.prompt_tokens = prompt;
        self.token_usage.completion_tokens = completion;
        self.token_usage.total_tokens = total;
    }

    // ---- 焦点 / 弹窗 ----

    pub fn focus_next(&mut self) {
        self.focus = match self.focus {
            FocusPanel::Center => FocusPanel::Input,
            FocusPanel::Input => FocusPanel::Center,
            FocusPanel::Left => FocusPanel::Center,
        };
    }

    pub fn focus_previous(&mut self) {
        self.focus = match self.focus {
            FocusPanel::Center => FocusPanel::Input,
            FocusPanel::Input => FocusPanel::Center,
            FocusPanel::Left => FocusPanel::Input,
        };
    }

    pub fn toggle_session_popup(&mut self) {
        self.popup.visible = !self.popup.visible;
        self.popup.selected = 0;
        self.popup.tab_index = 0;
        if self.popup.visible {
            self.focus = FocusPanel::Left;
        }
    }
}
